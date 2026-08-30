package com.kvelidelivery.app.data

import java.time.LocalDateTime
import java.time.DayOfWeek
import java.util.UUID
import kotlin.math.max

object ListProcessor {

    private val ADDRESS_INDEX: Map<String, String> by lazy { buildAddressIndex() }

    private fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        var t = text.lowercase().trim()
        t = t.replace(Regex("[()\\[\\]{}]"), " ")
        t = t.replace(Regex("[-–—]"), " ")
        t = t.replace(Regex("\\s+"), " ")
        t = t.replace('ё', 'е')
        return t.trim()
    }

    private fun buildAddressIndex(): Map<String, String> {
        val index = mutableMapOf<String, String>()
        for ((district, addresses) in AddressData.DISTRICTS) {
            for (addr in addresses) {
                val norm = normalize(addr)
                index[norm] = district
                var short = norm.replace(Regex("\\b(проспект|бульвар|улица|ул\\.?|пр\\.?|б-р|музыканта)\\b"), "").trim()
                short = short.replace(Regex("\\s+"), " ")
                if (short.isNotEmpty() && short != norm) {
                    index[short] = district
                }
            }
        }
        return index
    }

    fun getTimeSlots(extended: Boolean = false): List<String> {
        if (extended) return listOf("22:00", "23:00", "00:00", "01:00")
        val weekday = LocalDateTime.now().dayOfWeek
        return if (weekday == DayOfWeek.FRIDAY || weekday == DayOfWeek.SATURDAY) {
            listOf("22:00", "23:00", "00:00", "01:00")
        } else {
            listOf("22:00", "23:00")
        }
    }

    fun getDefaultMainTime(extended: Boolean = false): String = getTimeSlots(extended).last()

    fun getDeliveryDefaultTime(): String = "22:00"

    fun hourToSlot(h: Int): String? = when (h) {
        22 -> "22:00"
        0, 24 -> "00:00"
        1 -> "01:00"
        23 -> "23:00"
        else -> null
    }

    fun clampSlotToAllowed(slot: String?, extended: Boolean): String {
        val allowed = getTimeSlots(extended)
        if (slot.isNullOrBlank()) return getDefaultMainTime(extended)
        if (slot in allowed) return slot
        return allowed.last()
    }

    private fun partialRatio(s1: String, s2: String): Int {
        if (s1.isEmpty() || s2.isEmpty()) return 0
        val shorter = if (s1.length < s2.length) s1 else s2
        val longer = if (s1.length < s2.length) s2 else s1
        var best = 0
        for (i in 0..longer.length - shorter.length) {
            var matches = 0
            for (j in shorter.indices) {
                if (shorter[j] == longer[i + j]) matches++
            }
            best = max(best, (matches * 100) / shorter.length)
        }
        if (longer.contains(shorter)) best = max(best, 90)
        return best
    }

    private fun tokenSetRatio(s1: String, s2: String): Int {
        val tokens1 = s1.split(" ").filter { it.isNotEmpty() }.toSet()
        val tokens2 = s2.split(" ").filter { it.isNotEmpty() }.toSet()
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0
        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size
        return (intersection * 100) / union
    }

    fun findDistrict(addressStr: String?): String? {
        if (addressStr.isNullOrBlank()) return null
        val norm = normalize(addressStr)

        if ("кугеси" in norm) return "КУГЕСИ"
        if (listOf("богдан", "б.х", "бх", "кошкино").any { it in norm }) return "БОГДАНКА"
        if ("лебедева" in norm) return "СЗР"
        if (("500" in norm && "чебоксар" in norm) || "500лет" in norm.replace(" ", "")) return "СЗР"
        if (("50" in norm || "50лет" in norm.replace(" ", "") || "50-лет" in norm) &&
            ("октябр" in norm || norm.contains(Regex("50\\s*лет\\s*\\d")) || "50лет" in norm.replace(" ", ""))
        ) return "ЦЕНТР"
        if (("трактор" in norm || "прт" in norm) && Regex("\\b16\\b").containsMatchIn(norm)) return "НЮР"
        if ("324" in norm && ("стрелк" in norm || "дивиз" in norm)) return "НЮР"
        if (("универ" in norm || "университетская" in norm) &&
            ("38/2" in norm || "38к2" in norm || "38 к2" in norm)
        ) return "СЗР"
        if ("газировка" in norm) return "НЮР"
        if ("кома" in norm && ("лен" in norm || Regex("\\d").containsMatchIn(norm))) return "НЮР"
        if (Regex("лен(инского)?\\s*ком").containsMatchIn(norm)) return "НЮР"

        if (norm in ADDRESS_INDEX) return ADDRESS_INDEX[norm]

        for ((syn, full) in AddressData.SYNONYMS) {
            if (syn in norm) {
                val candidate = norm.replace(syn, normalize(full))
                if (candidate in ADDRESS_INDEX) return ADDRESS_INDEX[candidate]
                for ((addrNorm, dist) in ADDRESS_INDEX) {
                    if (syn in addrNorm || normalize(full) in addrNorm) {
                        if (partialRatio(norm, addrNorm) > 68) return dist
                    }
                }
            }
        }

        var bestScore = 0
        var bestDist: String? = null
        for ((addrNorm, dist) in ADDRESS_INDEX) {
            val score = tokenSetRatio(norm, addrNorm)
            if (score > bestScore && score >= 62) {
                bestScore = score
                bestDist = dist
            }
        }
        if (bestDist != null) return bestDist

        bestScore = 0
        bestDist = null
        for ((addrNorm, dist) in ADDRESS_INDEX) {
            val score = partialRatio(norm, addrNorm)
            if (score > bestScore && score >= 72) {
                bestScore = score
                bestDist = dist
            }
        }
        if (bestDist != null) return bestDist

        if ("советская" in norm) return "КУГЕСИ"
        if ("питер" in norm) return "СЗР"
        if ("черныш" in norm) return "ЮЗР"
        if ("неон" in norm) return "СЗР"
        if ("газировка" in norm) return "НЮР"
        if ("кома" in norm && ("лен" in norm || Regex("\\d").containsMatchIn(norm))) return "НЮР"
        return null
    }

    private fun extractInlineTime(text: String): String? {
        val patterns = listOf(
            Regex("\\(до\\s*(\\d{1,2})\\)", RegexOption.IGNORE_CASE),
            Regex("до\\s*(\\d{1,2})", RegexOption.IGNORE_CASE),
            Regex("\\bд[\\s.]*(\\d{1,2})\\b", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val m = p.find(text)
            if (m != null) {
                val h = m.groupValues[1].toIntOrNull() ?: continue
                hourToSlot(h)?.let { return it }
            }
        }
        return null
    }

    private fun splitLineByTimes(line: String): List<Pair<String, String?>> {
        val re = Regex("""(?i)(.+?)(?:\s+до\s*(\d{1,2})(?=\s|$))""")
        val matches = re.findAll(line).toList()
        if (matches.isEmpty()) return listOf(line to null)

        val result = mutableListOf<Pair<String, String?>>()
        var lastEnd = 0
        for (m in matches) {
            val chunk = m.groupValues[1].trim()
            val h = m.groupValues[2].toIntOrNull()
            val slot = h?.let { hourToSlot(it) }
            lastEnd = m.range.last + 1
            if (chunk.isEmpty() || chunk.length < 3) continue
            val low = normalize(chunk)
            val isJunk = low in listOf("девочки", "мальчики", "все", "остальные", "люди", "далее") ||
                (!low.any { it.isDigit() } && low.length < 6 && AddressData.STREET_HINTS.none { it in low })
            if (!isJunk) result.add(chunk to slot)
        }
        if (lastEnd < line.length) {
            val tail = line.substring(lastEnd).trim()
            if (tail.length > 2) {
                val parts = splitMultiplePeople(tail)
                for (part in parts) result.add(part to null)
            }
        }
        if (result.isEmpty()) return listOf(line to null)
        return result
    }

    private fun splitMultiplePeople(text: String): List<String> {
        val t = text.trim()
        if (t.length < 8) return listOf(t)
        val parts = t.split(Regex("""(?<=\d)\s+(?=[А-Яа-яЁёA-Za-z])"""))
            .map { it.trim() }
            .filter { it.length > 2 }
        return if (parts.size >= 2) parts else listOf(t)
    }

    private fun parsePersonLine(line: String): Pair<String?, String?> {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null to null

        val splitRegex = Regex("\\s*[-–—:]\\s*")
        if (splitRegex.containsMatchIn(trimmed)) {
            val parts = trimmed.split(splitRegex, limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                var name = parts[0].trim()
                var addr = parts[1].trim()
                addr = addr.replace(Regex("""\s*\(до\s*\d+\)\s*$""", RegexOption.IGNORE_CASE), "").trim()
                val nameIsLatin = name.matches(Regex("""^[A-Za-z0-9_.]+$"""))
                val wordsA = addr.split(" ")
                if (nameIsLatin && wordsA.size >= 2) {
                    val w0 = wordsA[0]
                    val rest = wordsA.drop(1).joinToString(" ")
                    val restLow = rest.lowercase().replace('ё', 'е')
                    if (w0.any { it in 'А'..'я' || it == 'Ё' || it == 'ё' } &&
                        (AddressData.STREET_HINTS.any { it.length > 3 && it in restLow } || rest.any { it.isDigit() })
                    ) {
                        name = w0
                        addr = rest
                    }
                }
                return name to addr
            }
        }

        if (trimmed.matches(Regex("""^(\d+\s*(т|эт|этаж|терраса)|ранер|ранеры|ранеры).*""", RegexOption.IGNORE_CASE))) {
            return null to null
        }

        val words = trimmed.split(" ")
        if (words.size == 1) return null to trimmed

        var addrStart: Int? = null
        for ((i, w) in words.withIndex()) {
            val wLow = w.lowercase().replace('ё', 'е')
            val isStreet = wLow in AddressData.STREET_HINTS ||
                    AddressData.STREET_HINTS.any { (it == wLow) || (it.length > 3 && it in wLow) }
            if (w.any { it.isDigit() } || isStreet) {
                addrStart = i
                break
            }
        }

        return when {
            addrStart != null && addrStart > 0 -> {
                val name = words.take(addrStart).joinToString(" ")
                var addr = words.drop(addrStart).joinToString(" ")
                addr = addr.replace(Regex("\\s*\\(до\\s*\\d+\\)\\s*$", RegexOption.IGNORE_CASE), "").trim()
                name.trim() to addr.trim()
            }
            addrStart == 0 -> null to trimmed
            words.size >= 2 -> words[0] to words.drop(1).joinToString(" ")
            else -> null to trimmed
        }
    }

    fun parseInput(text: String, extendedSlots: Boolean = false): List<Person> {
        var cleaned = text.replace(Regex("\\[\\d{2}\\.\\d{2}\\.\\d{4}\\s+\\d{1,2}:\\d{2}\\]\\s*"), "")
        val lines = cleaned.trim().lines()
        val people = mutableListOf<Person>()
        var currentRole: String? = null
        var currentTime: String? = null
        val mainTime = getDefaultMainTime(extendedSlots)

        val sectionKeywords = mapOf(
            "клининг" to "клин",
            "развоз" to "офф",
            "раннер" to "офф-ран",
            "ранеры" to "офф-ран",
            "караоке" to "кар",
            "доставка" to "дост",
            "кухня" to "кух",
            "хостес" to "хост",
            "офики" to "офф",
            "вип" to "офф",
            "бар" to "бар"
        )

        val timePatterns = listOf(
            Regex("до\\s*22\\s*:?") to "22:00",
            Regex("до\\s*00\\s*:?") to "00:00",
            Regex("до\\s*01\\s*:?") to "01:00",
            Regex("до\\s*23\\s*:?") to "23:00",
            Regex("^22(:00)?\\s*$") to "22:00",
            Regex("^00(:00)?\\s*$") to "00:00",
            Regex("^01(:00)?\\s*$") to "01:00",
            Regex("^23(:00)?\\s*$") to "23:00"
        )

        for (rawLine in lines) {
            var line = rawLine.trim()
            if (line.isEmpty()) continue

            val normLine = normalize(line)

            if (normLine.matches(Regex("^(до\\s*)?\\d{1,2}(:\\d{2})?\\s*:?\\s*$"))) {
                for ((pat, tval) in timePatterns) {
                    if (pat.containsMatchIn(normLine)) {
                        currentTime = tval
                        break
                    }
                }
                continue
            }

            var roleFound: String? = null
            for ((key, role) in sectionKeywords) {
                val keyPos = normLine.indexOf(key)
                if (keyPos >= 0 && (normLine.length < 120 || keyPos < 30)) {
                    roleFound = role
                    break
                }
            }
            if (roleFound != null) {
                currentRole = roleFound
                var line2 = line.replace(
                    Regex("(?:клининг|развоз|раннер|ранеры|караоке|доставка|кухня|хостес|офики|вип|бар)\\s*:?\\s*", RegexOption.IGNORE_CASE),
                    " "
                )
                line2 = line2.replace(Regex("\\s+"), " ").trim()
                line2 = line2.replace(Regex("^[А-Яа-яA-Za-zЁё][А-Яа-яA-Za-zЁё0-9_.]*\\s*:\\s*"), "").trim()
                val looksLikePeople = line2.any { it.isDigit() } ||
                    AddressData.STREET_HINTS.any { it.length > 3 && it in normalize(line2) }
                if (!looksLikePeople) {
                    val m = Regex("^(?:до|д)[\\s.]*(\\d{1,2})", RegexOption.IGNORE_CASE).find(line2)
                    if (m != null) {
                        currentTime = hourToSlot(m.groupValues[1].toIntOrNull() ?: -1) ?: currentTime
                    } else {
                        currentTime = extractInlineTime(line2)
                    }
                    line2 = line2.replace(Regex("^(?:до|д)[\\s.]*\\d{1,2}\\s*", RegexOption.IGNORE_CASE), "").trim()
                }
                line2 = line2.replace(Regex("^[.\\s]+"), "").trim()
                if (line2.isEmpty() || line2.length < 3) continue
                line = line2
            }
            if (line.matches(Regex("^[A-Za-z][A-Za-z0-9_.]*\\s*:.*")) &&
                sectionKeywords.keys.none { it in normalize(line) }
            ) {
                currentRole = null
                val after = line.replace(Regex("^[A-Za-z][A-Za-z0-9_.]*\\s*:\\s*"), "").trim()
                if (after.isNotEmpty() && after.length > 3) {
                    line = after
                } else continue
            }

            if (line.endsWith(":") && line.length < 40) continue
            if (line.matches(Regex("^(\\d+\\s*(т|эт|этаж|терраса)|ранер|ранеры|клининг|развоз|кухня|хостес|бар|караоке|доставка|офики|вип)\\s*:?\\s*$", RegexOption.IGNORE_CASE))) {
                val n = normalize(line)
                currentRole = when {
                    "ран" in n -> "офф-ран"
                    "клининг" in n -> "клин"
                    "кухн" in n -> "кух"
                    "хостес" in n -> "хост"
                    "бар" in n -> "бар"
                    "караоке" in n -> "кар"
                    "доставк" in n -> "дост"
                    else -> "офф"
                }
                continue
            }
            if (normalize(line).matches(Regex("^(до\\s*)?\\d{1,2}(:\\d{2})?\\s*$"))) continue
            if (line.length < 4) continue

            line = line.replace(Regex("^до\\s*\\d{1,2}\\s*", RegexOption.IGNORE_CASE), "").trim()
            if (line.isEmpty()) continue

            val timeMarks = Regex("(?i)до\\s*\\d{1,2}").findAll(line).count()
            val timedChunks = if (timeMarks >= 2 || (timeMarks == 1 && line.length > 28)) {
                splitLineByTimes(line)
            } else {
                listOf(line to null)
            }

            val candidates = mutableListOf<Triple<String?, String, String>>()
            val chunkTimes = mutableMapOf<String, String?>()

            for ((chunk, chunkTime) in timedChunks) {
                val subLines = mutableListOf<String>()
                if (chunk.length > 35 && chunk.count { it.isDigit() } >= 2) {
                    val rawParts = chunk.split(Regex("(?<=\\d)\\s+(?=[А-ЯЁA-Z])"))
                    val parts = mutableListOf<String>()
                    for (part in rawParts) {
                        val pp = part.trim()
                        if (pp.length < 3) {
                            if (parts.isNotEmpty()) parts[parts.lastIndex] = parts.last() + " " + pp
                            continue
                        }
                        parts.add(pp)
                    }
                    if (parts.isEmpty()) subLines.add(chunk) else subLines.addAll(parts)
                } else {
                    subLines.add(chunk)
                }
                for (pp0 in subLines) {
                    val pp = pp0.trim()
                    if (pp.isEmpty() || normalize(pp).matches(Regex("^(до\\s*\\d+|22|00|01|23)$"))) continue
                    val (n, a) = parsePersonLine(pp)
                    if (a != null && a.length > 1) {
                        candidates.add(Triple(n, a, pp))
                        if (chunkTime != null) chunkTimes[pp] = chunkTime
                    }
                }
            }

            for ((name, address, rawP) in candidates) {
                if (address.isBlank()) continue
                val addrNorm = normalize(address)
                if (addrNorm.length < 2) continue
                if (addrNorm.matches(Regex("^(до\\s*)?\\d{1,2}(:\\d{2})?$"))) continue
                if (addrNorm in listOf("клининг", "развоз", "кухня", "хостес", "бар", "караоке", "доставка", "ранеры", "ранер", "офики", "вип", "терраса", "этаж")) continue
                if (listOf("добрый вечер", "добрый день", "привет", "здравствуй", "как дела").any { it in addrNorm }) continue
                if (listOf("добрый вечер", "добрый день", "привет").any { it in normalize(rawP) }) continue

                val hasDigit = address.any { it.isDigit() }
                val hasStreet = AddressData.STREET_HINTS.any { it in addrNorm } || AddressData.SYNONYMS.keys.any { it in addrNorm }
                if (!hasDigit && !hasStreet && addrNorm !in ADDRESS_INDEX && addrNorm.length < 8) continue

                val inlineT = chunkTimes[rawP] ?: extractInlineTime(rawP) ?: extractInlineTime(address)
                var addressForParse = address
                addressForParse = addressForParse.replace(Regex("(?i)\\s*до\\s*\\d{1,2}\\s*$"), "").trim()
                val rawTime = when {
                    inlineT != null -> inlineT
                    currentRole == "дост" && currentTime == null -> getDeliveryDefaultTime()
                    else -> currentTime ?: mainTime
                }
                val timeGroup = clampSlotToAllowed(rawTime, extendedSlots)

                var addressClean = addressForParse.ifBlank { address }
                addressClean = addressClean.replace(Regex("\\d+\\s*чел\\.?\\s*", RegexOption.IGNORE_CASE), "")
                addressClean = addressClean.replace(Regex("\\bгражд\\.?\\b", RegexOption.IGNORE_CASE), "гражданская")
                addressClean = addressClean.replace(Regex("\\bлубумб[аыу]?\\b", RegexOption.IGNORE_CASE), "лумумбы")
                addressClean = addressClean.replace(Regex("\\b50-лет\\b", RegexOption.IGNORE_CASE), "50 лет октября")
                addressClean = addressClean.replace(Regex("\\bстрелковач\\b", RegexOption.IGNORE_CASE), "стрелковая")
                addressClean = addressClean.replace(Regex("\\bгостело\\b", RegexOption.IGNORE_CASE), "гастелло")
                addressClean = addressClean.replace(Regex("(?i)лен\\s*кома"), "ленинского комсомола")
                addressClean = addressClean.replace(Regex("(?i)\\bкома\\s*(\\d+)"), "ленинского комсомола $1")
                addressClean = addressClean.replace(Regex("(?i)\\bкома(\\d+)"), "ленинского комсомола $1")
                addressClean = addressClean.replace(Regex("[\\p{So}\\p{Cn}\\p{Cs}\\p{Sk}]+"), "") 
                addressClean = addressClean.replace(Regex("чебоксары[,\\s]*", RegexOption.IGNORE_CASE), "").trim()
                addressClean = addressClean.replace(Regex("\\s*\\([^)]*(?:чел|своим|факт|скорее)[^)]*\\)?\\s*", RegexOption.IGNORE_CASE), " ").trim()
                addressClean = addressClean.replace(Regex("\\s+"), " ").trim(' ', '.')

                var district = findDistrict(addressClean)
                if (district == null && listOf("богдан", "б.х", "бх").any { it in normalize(addressClean) }) {
                    district = "БОГДАНКА"
                }

                var nPeople = 1
                val mN = Regex("(\\d+)\\s*чел", RegexOption.IGNORE_CASE).find(normalize(rawP))
                if (mN != null) {
                    nPeople = max(1, mN.groupValues[1].toIntOrNull() ?: 1)
                }

                for (i in 0 until nPeople) {
                    people.add(
                        Person(
                            id = UUID.randomUUID().toString(),
                            name = if (nPeople > 1 && name == null) null else name,
                            address = addressClean.ifBlank { address },
                            district = district,
                            role = currentRole,
                            timeGroup = timeGroup,
                            raw = if (i == 0) rawP else "$rawP #${i + 1}",
                            orderIndex = people.size
                        )
                    )
                }
            }
            if (timedChunks.any { it.second != null }) {
                currentTime = null
            }
        }
        return people
    }

    fun buildStructuredOutput(
        people: List<Person>,
        selectedDriver: Driver,
        extendedSlots: Boolean = false
    ): List<TimeSlotGroup> {
        val configured = getTimeSlots(extendedSlots)
        val fromPeople = people.map { it.timeGroup }.filter { it.isNotBlank() }.distinct()
        val slots = (configured + fromPeople)
            .distinct()
            .sortedBy { SLOT_ORDER.indexOf(it).let { i -> if (i < 0) 100 else i } }
        val byTime = people.groupBy { it.timeGroup }
        val undef = people.filter { it.district == null }

        val orderDriver1 = listOf("СЗР", "ЮЗР")
        val orderDriver2 = listOf("ЦЕНТР", "НОВЫЙ", "НЧК")
        val orderDriver3 = listOf("НЮР", "КУГЕСИ")
        val bog = "БОГДАНКА"

        fun groupByDistrict(plist: List<Person>): Map<String, List<Person>> {
            return plist.groupBy { it.district ?: "НЕОПР" }
        }

        fun sortPeople(plist: List<Person>): List<Person> {
            return plist.sortedWith(compareBy(
                { it.orderIndex },
                { (it.name ?: "").lowercase().replace('ё', 'е') },
                { it.address.lowercase().replace('ё', 'е') }
            ))
        }

        val result = mutableListOf<TimeSlotGroup>()

        for (slot in slots) {
            val slotPeople = byTime[slot] ?: continue
            if (slotPeople.isEmpty()) continue

            val groups = groupByDistrict(slotPeople.filter { it.district != null })

            val c2 = orderDriver2.sumOf { groups[it]?.size ?: 0 }
            val c3 = orderDriver3.sumOf { groups[it]?.size ?: 0 }
            val bogTo2 = c2 <= c3

            val driverOrder = when (selectedDriver) {
                Driver.ONE -> listOf(Driver.ONE, Driver.TWO, Driver.THREE)
                Driver.TWO -> listOf(Driver.TWO, Driver.ONE, Driver.THREE)
                Driver.THREE -> listOf(Driver.THREE, Driver.ONE, Driver.TWO)
            }

            val driverSections = mutableListOf<DriverSection>()

            for (drv in driverOrder) {
                val districtsForDriver = when (drv) {
                    Driver.ONE -> orderDriver1
                    Driver.TWO -> if (bogTo2) listOf(bog) + orderDriver2 else orderDriver2
                    Driver.THREE -> if (!bogTo2) listOf(bog) + orderDriver3 else orderDriver3
                }

                val districtGroups = mutableListOf<DistrictGroup>()
                var total = 0
                for (d in districtsForDriver) {
                    val plist = groups[d]
                    if (plist != null && plist.isNotEmpty()) {
                        val sorted = sortPeople(plist)
                        val activeCount = sorted.count { !it.isDelivered }
                        districtGroups.add(DistrictGroup(d, sorted, activeCount))
                        total += activeCount
                    }
                }
                if (districtGroups.isNotEmpty()) {
                    driverSections.add(DriverSection(drv, districtGroups, total))
                }
            }

            if (driverSections.isNotEmpty()) {
                result.add(TimeSlotGroup(slot, driverSections))
            }
        }

        if (undef.isNotEmpty()) {
            val undefGroup = DistrictGroup("НЕОПРЕДЕЛЕНО", undef)
            val undefSection = DriverSection(
                driver = Driver.ONE, 
                districts = listOf(undefGroup),
                totalCount = undef.size
            )
            if (result.isNotEmpty()) {
                // We handle undefined in UI separately
            }
        }

        return result
    }

    fun getUndefined(people: List<Person>): List<Person> = people.filter { it.district == null }
}
