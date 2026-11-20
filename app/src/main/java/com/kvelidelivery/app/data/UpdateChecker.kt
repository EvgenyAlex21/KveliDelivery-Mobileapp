package com.kvelidelivery.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Проверка обновлений с Google Drive (или любого публичного URL).
 *
 * На Google Диске нужно положить два файла (оба — «доступ по ссылке»):
 *
 * 1) version.json  (текстовый):
 *    {
 *      "versionCode": 2,
 *      "versionName": "1.1",
 *      "apkUrl": "https://drive.google.com/uc?export=download&id=ID_APK_ФАЙЛА",
 *      "changelog": "Что нового..."
 *    }
 *
 * 2) APK-файл приложения
 *
 * Прямая ссылка на файл Google Drive:
 *   https://drive.google.com/uc?export=download&id=FILE_ID
 * (FILE_ID берётся из ссылки «Поделиться»).
 *
 * Локальная БД (EmployeeStore / SessionStore) хранится в SharedPreferences
 * и НЕ стирается при обновлении APK поверх существующего пакета.
 */
class UpdateChecker(private val context: Context) {

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val changelog: String
    )

    sealed class CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult()
        object UpToDate : CheckResult()
        data class Error(val message: String) : CheckResult()
    }

    sealed class DownloadResult {
        data class Success(val file: File) : DownloadResult()
        data class Error(val message: String) : DownloadResult()
    }

    /** URL файла version.json на Google Drive. */
    companion object {
        // Пример: https://drive.google.com/uc?export=download&id=ID_ФАЙЛА_version.json
        const val VERSION_JSON_URL =
            "https://drive.google.com/uc?export=download&id=1xByySUGJbWfgZxfDRpNCg2dHKYlLiDBf"

        private const val CONNECT_TIMEOUT = 12_000
        private const val READ_TIMEOUT = 30_000
    }

    fun currentVersionCode(): Int {
        return try {
            val pm = context.packageManager
            val pkg = context.packageName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0).versionCode
            }
        } catch (_: Exception) {
            1
        }
    }

    fun currentVersionName(): String {
        return try {
            val pm = context.packageManager
            val pkg = context.packageName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName ?: "?"
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0).versionName ?: "?"
            }
        } catch (_: Exception) {
            "?"
        }
    }

    suspend fun checkForUpdate(): CheckResult = withContext(Dispatchers.IO) {
        try {
            if (VERSION_JSON_URL.contains("ЗАМЕНИТЕ")) {
                return@withContext CheckResult.Error(
                    "URL обновлений не настроен. Укажите ID файла version.json в UpdateChecker."
                )
            }
            val body = httpGetString(resolveDriveUrl(VERSION_JSON_URL))
                ?: return@withContext CheckResult.Error("Не удалось загрузить version.json")

            val json = JSONObject(body.trim())
            val remoteCode = json.optInt("versionCode", 0)
            val remoteName = json.optString("versionName", "")
            val apkUrl = json.optString("apkUrl", "")
            val changelog = json.optString("changelog", "")

            if (remoteCode <= 0 || apkUrl.isBlank()) {
                return@withContext CheckResult.Error("Некорректный version.json")
            }

            val localCode = currentVersionCode()
            if (remoteCode > localCode) {
                CheckResult.Available(
                    UpdateInfo(remoteCode, remoteName, apkUrl, changelog)
                )
            } else {
                CheckResult.UpToDate
            }
        } catch (e: Exception) {
            CheckResult.Error(e.message ?: "Ошибка проверки обновлений")
        }
    }

    suspend fun downloadApk(
        apkUrl: String,
        onProgress: (Int) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val fileId = extractDriveFileId(apkUrl)
            // Список URL, которые пробуем по очереди
            val candidates = mutableListOf<String>()
            if (fileId != null) {
                // 1) Современный usercontent + confirm=t (обходит virus-scan для публичных файлов)
                candidates += "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
                // 2) Классический uc + confirm=t
                candidates += "https://drive.google.com/uc?export=download&confirm=t&id=$fileId"
                // 3) Исходный resolve
                candidates += resolveDriveUrl(apkUrl)
            } else {
                candidates += resolveDriveUrl(apkUrl)
            }

            var lastError = "Не удалось скачать APK"
            for (url in candidates) {
                val result = tryDownloadOnce(url, fileId, onProgress)
                when (result) {
                    is DownloadResult.Success -> return@withContext result
                    is DownloadResult.Error -> lastError = result.message
                }
            }
            DownloadResult.Error(lastError)
        } catch (e: Exception) {
            DownloadResult.Error(e.message ?: "Ошибка скачивания")
        }
    }

    private fun tryDownloadOnce(
        url: String,
        fileId: String?,
        onProgress: (Int) -> Unit
    ): DownloadResult {
        val conn = openConnection(url)
        conn.instanceFollowRedirects = false
        conn.connect()

        var current = conn
        var hops = 0
        val cookies = mutableListOf<String>()
        while (hops < 8) {
            val code = current.responseCode
            val setCookie = current.headerFields["Set-Cookie"]
            setCookie?.forEach { c ->
                val part = c.substringBefore(';')
                if (part.isNotBlank()) cookies.add(part)
            }
            if (code in 300..399) {
                val loc = current.getHeaderField("Location") ?: break
                current.disconnect()
                current = openConnection(loc)
                if (cookies.isNotEmpty()) {
                    current.setRequestProperty("Cookie", cookies.joinToString("; "))
                }
                current.instanceFollowRedirects = false
                current.connect()
                hops++
                continue
            }
            break
        }

        val contentType = (current.contentType ?: "").lowercase()
        val isHtml = contentType.contains("text/html") || contentType.contains("text/plain")

        if (isHtml) {
            val html = try {
                current.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (_: Exception) {
                current.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            }
            current.disconnect()

            val id = fileId
                ?: Regex("""name=["']id["']\s+value=["']([0-9A-Za-z_-]+)["']""").find(html)?.groupValues?.get(1)
                ?: Regex("""[?&]id=([0-9A-Za-z_-]+)""").find(html)?.groupValues?.get(1)
            val confirm = Regex("""name=["']confirm["']\s+value=["']([^"']+)["']""").find(html)?.groupValues?.get(1)
                ?: Regex("""confirm=([0-9A-Za-z_-]+)""").find(html)?.groupValues?.get(1)
                ?: "t"
            val uuid = Regex("""name=["']uuid["']\s+value=["']([^"']+)["']""").find(html)?.groupValues?.get(1)
                ?: Regex("""uuid=([0-9A-Za-z_-]+)""").find(html)?.groupValues?.get(1)

            if (id.isNullOrBlank()) {
                return DownloadResult.Error(
                    "Google Drive вернул страницу подтверждения. " +
                        "Проверьте, что APK доступен «всем по ссылке»."
                )
            }

            val confirmed = buildString {
                append("https://drive.usercontent.google.com/download?id=$id&export=download&confirm=$confirm")
                if (!uuid.isNullOrBlank()) append("&uuid=$uuid")
            }
            return downloadBinary(confirmed, cookies, onProgress)
        }

        return downloadFromConnection(current, onProgress)
    }

    private fun downloadBinary(
        url: String,
        cookies: List<String>,
        onProgress: (Int) -> Unit
    ): DownloadResult {
        val conn = openConnection(url)
        if (cookies.isNotEmpty()) {
            conn.setRequestProperty("Cookie", cookies.joinToString("; "))
        }
        conn.instanceFollowRedirects = true
        conn.connect()
        val contentType = (conn.contentType ?: "").lowercase()
        if (contentType.contains("text/html")) {
            conn.disconnect()
            return DownloadResult.Error(
                "Google Drive всё ещё отдаёт HTML вместо APK. " +
                    "Попробуйте перезалить файл или использовать другой хостинг."
            )
        }
        return downloadFromConnection(conn, onProgress)
    }

    private fun downloadFromConnection(
        conn: HttpURLConnection,
        onProgress: (Int) -> Unit
    ): DownloadResult {
        val total = conn.contentLengthLong
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val outFile = File(dir, "kveli_update.apk")
        if (outFile.exists()) outFile.delete()

        val stream = try {
            conn.inputStream
        } catch (_: Exception) {
            conn.errorStream
        } ?: return DownloadResult.Error("Пустой ответ сервера")

        stream.use { input ->
            FileOutputStream(outFile).use { output ->
                val buffer = ByteArray(16 * 1024)
                var downloaded = 0L
                var lastPct = -1
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0) {
                        val pct = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(pct)
                        }
                    }
                }
                output.flush()
            }
        }
        conn.disconnect()
        onProgress(100)

        if (!outFile.exists() || outFile.length() < 50_000) {
            // Возможно, скачали HTML-ошибку
            val head = outFile.takeIf { it.exists() }?.readText(Charsets.UTF_8)?.take(200) ?: ""
            if (head.contains("<html", ignoreCase = true) || head.contains("<!DOCTYPE", ignoreCase = true)) {
                outFile.delete()
                return DownloadResult.Error(
                    "Скачалась HTML-страница вместо APK. Google Drive блокирует прямую загрузку."
                )
            }
            return DownloadResult.Error("Файл APK слишком маленький или повреждён (${outFile.length()} байт)")
        }
        val magic = ByteArray(2)
        outFile.inputStream().use { it.read(magic) }
        if (magic[0] != 0x50.toByte() || magic[1] != 0x4B.toByte()) {
            outFile.delete()
            return DownloadResult.Error("Скачанный файл не является APK (нет ZIP-заголовка)")
        }
        return DownloadResult.Success(outFile)
    }

    private fun extractDriveFileId(url: String): String? {
        Regex("""/file/d/([0-9A-Za-z_-]+)""").find(url)?.groupValues?.get(1)?.let { return it }
        Regex("""[?&]id=([0-9A-Za-z_-]+)""").find(url)?.groupValues?.get(1)?.let { return it }
        Regex("""/d/([0-9A-Za-z_-]+)""").find(url)?.groupValues?.get(1)?.let { return it }
        // Если передали просто ID
        if (url.matches(Regex("""^[0-9A-Za-z_-]{20,}$"""))) return url
        return null
    }

    fun installApk(file: File): Boolean {
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settings = Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(settings)
                    return false
                }
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun resolveDriveUrl(url: String): String {
        val idFromPath = Regex("""/file/d/([0-9A-Za-z_-]+)""").find(url)?.groupValues?.get(1)
        if (idFromPath != null && !url.contains("export=download")) {
            return "https://drive.google.com/uc?export=download&id=$idFromPath"
        }
        val idFromQuery = Regex("""[?&]id=([0-9A-Za-z_-]+)""").find(url)?.groupValues?.get(1)
        if (idFromQuery != null && !url.contains("export=download")) {
            return "https://drive.google.com/uc?export=download&id=$idFromQuery"
        }
        return url
    }

    private fun openConnection(urlStr: String): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "KveliDelivery-UpdateChecker/1.0")
        return conn
    }

    private fun httpGetString(urlStr: String): String? {
        val conn = openConnection(urlStr)
        return try {
            conn.connect()
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
