package com.kvelidelivery.app.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.io.ByteArrayOutputStream

class EmployeeStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    data class EmployeeRecord(
        val name: String?,
        val role: String?,
        val address: String,
        val district: String?,
        val addedAt: Long = System.currentTimeMillis()
    )

    private fun normalizeKey(name: String?, address: String): String {
        val n = (name ?: "").lowercase().trim().replace('ё', 'е')
        val a = address.lowercase().trim().replace('ё', 'е')
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[()\\[\\]{}.,;:]"), "")
        return "$n|$a"
    }

    fun loadAll(): List<EmployeeRecord> {
        val json = prefs.getString(KEY_RECORDS, "[]") ?: "[]"
        val result = mutableListOf<EmployeeRecord>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                result.add(
                    EmployeeRecord(
                        name = if (o.isNull("name") || o.optString("name").isEmpty()) null else o.optString("name"),
                        role = if (o.isNull("role") || o.optString("role").isEmpty()) null else o.optString("role"),
                        address = o.optString("address"),
                        district = if (o.isNull("district") || o.optString("district").isEmpty()) null else o.optString("district"),
                        addedAt = o.optLong("addedAt", 0L)
                    )
                )
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun saveAll(records: List<EmployeeRecord>) {
        val arr = JSONArray()
        for (r in records) {
            arr.put(JSONObject().apply {
                put("name", r.name)
                put("role", r.role)
                put("address", r.address)
                put("district", r.district)
                put("addedAt", r.addedAt)
            })
        }
        prefs.edit().putString(KEY_RECORDS, arr.toString()).apply()
    }

    fun recordPeople(people: List<Person>) {
        if (people.isEmpty()) return
        val existing = loadAll().toMutableList()
        val keys = existing.map { normalizeKey(it.name, it.address) }.toMutableSet()
        var changed = false
        for (p in people) {
            val addr = p.address.trim()
            if (addr.isBlank()) continue
            val key = normalizeKey(p.name, addr)
            if (key in keys) continue
            existing.add(
                EmployeeRecord(
                    name = p.name?.takeIf { it.isNotBlank() },
                    role = p.role?.takeIf { it.isNotBlank() },
                    address = addr,
                    district = p.district?.takeIf { it.isNotBlank() }
                )
            )
            keys.add(key)
            changed = true
        }
        if (changed) saveAll(existing)
    }

    fun findDistrictByKnown(name: String?, address: String?): String? {
        if (address.isNullOrBlank() && name.isNullOrBlank()) return null
        val records = loadAll()
        if (records.isEmpty()) return null

        val addrNorm = address?.lowercase()?.trim()?.replace('ё', 'е')
            ?.replace(Regex("\\s+"), " ") ?: ""
        val nameNorm = name?.lowercase()?.trim()?.replace('ё', 'е') ?: ""

        if (addrNorm.isNotEmpty()) {
            for (r in records) {
                val rAddr = r.address.lowercase().trim().replace('ё', 'е').replace(Regex("\\s+"), " ")
                if (rAddr == addrNorm && !r.district.isNullOrBlank()) return r.district
            }
            for (r in records) {
                val rAddr = r.address.lowercase().trim().replace('ё', 'е').replace(Regex("\\s+"), " ")
                if ((addrNorm.contains(rAddr) || rAddr.contains(addrNorm)) &&
                    rAddr.length >= 5 && !r.district.isNullOrBlank()
                ) {
                    return r.district
                }
            }
        }

        if (nameNorm.isNotEmpty() && nameNorm.length >= 2) {
            val byName = records.filter {
                val rn = it.name?.lowercase()?.trim()?.replace('ё', 'е') ?: ""
                rn == nameNorm || (rn.isNotEmpty() && (nameNorm.contains(rn) || rn.contains(nameNorm)))
            }
            val districts = byName.mapNotNull { it.district }.distinct()
            if (districts.size == 1) return districts.first()
            if (addrNorm.isNotEmpty()) {
                for (r in byName) {
                    val rAddr = r.address.lowercase().trim().replace('ё', 'е')
                    if (addrNorm.contains(rAddr) || rAddr.contains(addrNorm)) {
                        if (!r.district.isNullOrBlank()) return r.district
                    }
                }
            }
        }
        return null
    }

    fun clear() {
        prefs.edit().remove(KEY_RECORDS).apply()
    }

    fun count(): Int = loadAll().size

    fun exportToExcel(): File? {
        val records = loadAll()
        if (records.isEmpty()) return null

        val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outFile = File(dir, "baza_sotrudnikov_$ts.xlsx")

        try {
            ZipOutputStream(FileOutputStream(outFile)).use { zos ->
                // [Content_Types].xml
                zos.putNextEntry(ZipEntry("[Content_Types].xml"))
                zos.write(
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""".toByteArray(Charsets.UTF_8)
                )
                zos.closeEntry()

                // _rels/.rels
                zos.putNextEntry(ZipEntry("_rels/.rels"))
                zos.write(
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)
                )
                zos.closeEntry()

                // xl/_rels/workbook.xml.rels
                zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
                zos.write(
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)
                )
                zos.closeEntry()

                // xl/workbook.xml
                zos.putNextEntry(ZipEntry("xl/workbook.xml"))
                zos.write(
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="База сотрудников" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>""".toByteArray(Charsets.UTF_8)
                )
                zos.closeEntry()

                // xl/styles.xml (заголовки: жирный шрифт + выравнивание по центру)
                zos.putNextEntry(ZipEntry("xl/styles.xml"))
                zos.write(
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><name val="Calibri"/></font>
  </fonts>
  <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
  <borders count="1"><border/></borders>
  <cellStyleXfs count="1"><xf/></cellStyleXfs>
  <cellXfs count="2">
    <xf/>
    <xf fontId="1" applyFont="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
  </cellXfs>
</styleSheet>""".toByteArray(Charsets.UTF_8)
                )
                zos.closeEntry()

                // Shared strings
                val strings = mutableListOf<String>()
                fun si(s: String): Int {
                    val idx = strings.indexOf(s)
                    if (idx >= 0) return idx
                    strings.add(s)
                    return strings.size - 1
                }

                val headers = listOf("Имя", "Роль", "Адрес", "Район", "Добавлено")
                headers.forEach { si(it) }

                val dateFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                val districtOrder = listOf(
                    "СЗР", "ЮЗР", "ЦЕНТР", "НОВЫЙ", "НЧК", "НЮР", "КУГЕСИ", "БОГДАНКА"
                )
                fun districtRank(d: String?): Int {
                    if (d.isNullOrBlank()) return 1000
                    val idx = districtOrder.indexOf(d)
                    return if (idx >= 0) idx else 500
                }
                val sortedRecords = records.sortedWith(
                    compareBy<EmployeeRecord> { districtRank(it.district) }
                        .thenBy { it.district ?: "" }
                        .thenBy { it.name ?: "" }
                        .thenBy { it.address }
                )
                val rowsData = sortedRecords.map { r ->
                    listOf(
                        r.name ?: "",
                        r.role ?: "",
                        r.address,
                        r.district ?: "",
                        if (r.addedAt > 0) dateFmt.format(Date(r.addedAt)) else ""
                    )
                }
                rowsData.forEach { row -> row.forEach { si(it) } }

                // xl/sharedStrings.xml
                zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
                val sbStr = StringBuilder()
                sbStr.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                sbStr.append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${strings.size}" uniqueCount="${strings.size}">""")
                for (s in strings) {
                    val esc = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                    sbStr.append("""<si><t>$esc</t></si>""")
                }
                sbStr.append("</sst>")
                zos.write(sbStr.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // xl/worksheets/sheet1.xml
                zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                val sb = StringBuilder()
                sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")

                val colWidths = (0 until 5).map { col ->
                    val maxLen = maxOf(
                        headers[col].length,
                        rowsData.maxOfOrNull { it[col].length } ?: 0
                    )
                    (maxLen + 3).coerceIn(8, 60)
                }
                sb.append("<cols>")
                colWidths.forEachIndexed { i, w ->
                    sb.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""")
                }
                sb.append("</cols>")

                sb.append("<sheetData>")

                sb.append("""<row r="1">""")
                headers.forEachIndexed { col, h ->
                    val ref = cellRef(col, 0)
                    val idx = si(h)
                    sb.append("""<c r="$ref" t="s" s="1"><v>$idx</v></c>""")
                }
                sb.append("</row>")

                rowsData.forEachIndexed { rowIdx, row ->
                    val rNum = rowIdx + 2
                    sb.append("""<row r="$rNum">""")
                    row.forEachIndexed { col, value ->
                        val ref = cellRef(col, rowIdx + 1)
                        val idx = si(value)
                        sb.append("""<c r="$ref" t="s"><v>$idx</v></c>""")
                    }
                    sb.append("</row>")
                }

                val lastRow = rowsData.size + 1
                sb.append("</sheetData>")
                sb.append("""<autoFilter ref="A1:E$lastRow"/>""")
                sb.append("</worksheet>")
                zos.write(sb.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            return outFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun cellRef(col: Int, row: Int): String {
        var c = col
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + c % 26).toChar())
            c = c / 26 - 1
        } while (c >= 0)
        return "${sb}${row + 1}"
    }

    data class ImportResult(
        val success: Boolean,
        val message: String,
        val imported: Int = 0,
        val skipped: Int = 0,
        val total: Int = 0
    )

    fun importFromExcel(uri: Uri): ImportResult {
        return try {
            val stream = appContext.contentResolver.openInputStream(uri)
                ?: return ImportResult(false, "Не удалось открыть файл")
            stream.use { input ->
                importFromStream(input)
            }
        } catch (e: Exception) {
            ImportResult(false, "Ошибка чтения файла: ${e.message}")
        }
    }

    fun importFromStream(input: InputStream): ImportResult {
        val entries = mutableMapOf<String, ByteArray>()
        try {
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name
                        if (name == "xl/sharedStrings.xml" ||
                            name == "xl/worksheets/sheet1.xml" ||
                            name.startsWith("xl/worksheets/")
                        ) {
                            val bos = ByteArrayOutputStream()
                            zis.copyTo(bos)
                            entries[name] = bos.toByteArray()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            return ImportResult(false, "Файл не похож на Excel (.xlsx): ${e.message}")
        }

        val sharedStrings = parseSharedStrings(
            entries["xl/sharedStrings.xml"]?.toString(Charsets.UTF_8) ?: ""
        )

        val sheetXml = entries["xl/worksheets/sheet1.xml"]?.toString(Charsets.UTF_8)
            ?: entries.entries.firstOrNull { it.key.startsWith("xl/worksheets/") }?.value?.toString(Charsets.UTF_8)
            ?: return ImportResult(false, "В файле нет листа с данными (sheet1)")

        val rows = parseSheetRows(sheetXml, sharedStrings)
        if (rows.isEmpty()) {
            return ImportResult(false, "Таблица пуста или не удалось прочитать строки")
        }

        val header = rows.first().map { it.trim().lowercase() }
        val colName = findColumn(header, listOf("имя", "name", "фио", "сотрудник"))
        val colRole = findColumn(header, listOf("роль", "role", "должность", "тип"))
        val colAddress = findColumn(header, listOf("адрес", "address", "адресс"))
        val colDistrict = findColumn(header, listOf("район", "district", "зона"))
        val colAdded = findColumn(header, listOf("добавлено", "added", "дата", "addedat"))

        if (colAddress < 0) {
            return ImportResult(
                false,
                "Не найден столбец «Адрес». Ожидаются заголовки: Имя, Роль, Адрес, Район, Добавлено"
            )
        }

        val knownHeaders = listOf(colName, colRole, colAddress, colDistrict).count { it >= 0 }
        if (knownHeaders < 1) {
            return ImportResult(
                false,
                "Таблица не соответствует формату базы сотрудников. Нужны столбцы: Имя, Роль, Адрес, Район"
            )
        }

        val dateFormats = listOf(
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()),
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        )

        val existing = loadAll().toMutableList()
        val keys = existing.map { normalizeKey(it.name, it.address) }.toMutableSet()
        var imported = 0
        var skipped = 0

        for (i in 1 until rows.size) {
            val row = rows[i]
            fun cell(col: Int): String =
                if (col >= 0 && col < row.size) row[col].trim() else ""

            val address = cell(colAddress)
            if (address.isBlank()) {
                skipped++
                continue
            }
            val name = cell(colName).takeIf { it.isNotBlank() }
            val role = cell(colRole).takeIf { it.isNotBlank() }
            val district = cell(colDistrict).takeIf { it.isNotBlank() }
            val addedRaw = cell(colAdded)
            var addedAt = 0L
            if (addedRaw.isNotBlank()) {
                for (fmt in dateFormats) {
                    try {
                        val d = fmt.parse(addedRaw)
                        if (d != null) {
                            addedAt = d.time
                            break
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            val key = normalizeKey(name, address)
            if (key in keys) {
                skipped++
                continue
            }
            existing.add(
                EmployeeRecord(
                    name = name,
                    role = role,
                    address = address,
                    district = district,
                    addedAt = if (addedAt > 0) addedAt else System.currentTimeMillis()
                )
            )
            keys.add(key)
            imported++
        }

        if (imported > 0) {
            saveAll(existing)
        }

        val total = existing.size
        val msg = when {
            imported == 0 && skipped > 0 ->
                "Новых записей нет (пропущено $skipped: пустой адрес или уже есть в базе). Всего в базе: $total"
            imported == 0 ->
                "Не найдено ни одной валидной строки с адресом"
            else ->
                "Импортировано: $imported, пропущено: $skipped. Всего в базе: $total"
        }
        return ImportResult(
            success = imported > 0 || (imported == 0 && skipped > 0 && total > 0),
            message = msg,
            imported = imported,
            skipped = skipped,
            total = total
        )
    }

    private fun findColumn(header: List<String>, aliases: List<String>): Int {
        for ((i, h) in header.withIndex()) {
            val n = h.lowercase().trim()
            if (aliases.any { it == n || n.contains(it) }) return i
        }
        return -1
    }

    private fun parseSharedStrings(xml: String): List<String> {
        if (xml.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            var inT = false
            val sb = StringBuilder()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "t" || parser.name?.endsWith(":t") == true) {
                            inT = true
                            sb.clear()
                        } else if (parser.name == "si" || parser.name?.endsWith(":si") == true) {
                            sb.clear()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inT) sb.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "t" || parser.name?.endsWith(":t") == true) {
                            inT = false
                        } else if (parser.name == "si" || parser.name?.endsWith(":si") == true) {
                            result.add(sb.toString())
                            sb.clear()
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun parseSheetRows(xml: String, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            var event = parser.eventType
            var currentRow: MutableList<String>? = null
            var currentCellRef: String? = null
            var currentType: String? = null
            var inV = false
            val cellValue = StringBuilder()

            fun colIndexFromRef(ref: String): Int {
                var col = 0
                for (ch in ref) {
                    if (ch.isLetter()) {
                        col = col * 26 + (ch.uppercaseChar() - 'A' + 1)
                    } else break
                }
                return col - 1
            }

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name ?: ""
                        when {
                            name == "row" || name.endsWith(":row") -> {
                                currentRow = mutableListOf()
                            }
                            name == "c" || name.endsWith(":c") -> {
                                currentCellRef = parser.getAttributeValue(null, "r")
                                currentType = parser.getAttributeValue(null, "t")
                                cellValue.clear()
                            }
                            name == "v" || name.endsWith(":v") || name == "t" || name.endsWith(":t") -> {
                                inV = true
                                cellValue.clear()
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inV) cellValue.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name ?: ""
                        when {
                            name == "v" || name.endsWith(":v") || name == "t" || name.endsWith(":t") -> {
                                inV = false
                            }
                            name == "c" || name.endsWith(":c") -> {
                                val row = currentRow
                                if (row != null) {
                                    val colIdx = currentCellRef?.let { colIndexFromRef(it) } ?: row.size
                                    while (row.size <= colIdx) row.add("")
                                    val raw = cellValue.toString()
                                    val value = if (currentType == "s") {
                                        val idx = raw.toIntOrNull()
                                        if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else raw
                                    } else {
                                        raw
                                    }
                                    row[colIdx] = value
                                }
                                currentCellRef = null
                                currentType = null
                                cellValue.clear()
                            }
                            name == "row" || name.endsWith(":row") -> {
                                currentRow?.let { rows.add(it.toList()) }
                                currentRow = null
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
        }
        return rows
    }

    companion object {
        private const val PREFS_NAME = "kveli_employee_base"
        private const val KEY_RECORDS = "records"
    }
}