package com.kvelidelivery.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
            // Если несколько — предпочитаем тот, что совпадает с адресом
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

                // xl/styles.xml (минимальный)
                zos.putNextEntry(ZipEntry("xl/styles.xml"))
                zos.write(
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
  <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
  <borders count="1"><border/></borders>
  <cellStyleXfs count="1"><xf/></cellStyleXfs>
  <cellXfs count="1"><xf/></cellXfs>
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
                // Порядок районов для сортировки в Excel
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
                sb.append("<sheetData>")

                // Header row
                sb.append("""<row r="1">""")
                headers.forEachIndexed { col, h ->
                    val ref = cellRef(col, 0)
                    val idx = si(h)
                    sb.append("""<c r="$ref" t="s"><v>$idx</v></c>""")
                }
                sb.append("</row>")

                // Data rows
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

                // Автофильтр по всем столбцам (A1:E{lastRow})
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

    companion object {
        private const val PREFS_NAME = "kveli_employee_base"
        private const val KEY_RECORDS = "records"
    }
}
