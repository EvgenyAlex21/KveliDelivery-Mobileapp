package com.kvelidelivery.app.data

data class Person(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String?,
    var address: String,
    var district: String?,
    var role: String?,
    var timeGroup: String,
    var raw: String,
    var isDelivered: Boolean = false,
    var assignedDriver: Int? = null, 
    var orderIndex: Int = 0
)

enum class Driver(val number: Int, val title: String, val districts: List<String>, val description: String) {
    ONE(1, "Водитель №1", listOf("СЗР", "ЮЗР"), "СЗР + ЮЗР"),
    TWO(2, "Водитель №2", listOf("ЦЕНТР", "НОВЫЙ", "НЧК"), "ЦЕНТР + НОВЫЙ + НЧК (+ БОГДАНКА)"),
    THREE(3, "Водитель №3", listOf("НЮР", "КУГЕСИ"), "НЮР + КУГЕСИ (+ БОГДАНКА)")
}

/**
 * Режим временных слотов.
 * AUTO — по дню недели: Пн–Чт + Вс → до 23:00; Пт–Сб → до 01:00.
 * UNTIL_23 — принудительно 22:00, 23:00.
 * UNTIL_01 — принудительно 22:00, 23:00, 00:00, 01:00.
 */
enum class SlotMode {
    AUTO,
    UNTIL_23,
    UNTIL_01
}

data class TimeSlotGroup(
    val time: String,
    val drivers: List<DriverSection>
)

data class DriverSection(
    val driver: Driver,
    val districts: List<DistrictGroup>,
    val totalCount: Int
)

data class DistrictGroup(
    val district: String,
    val people: List<Person>,
    val activeCount: Int = people.count { !it.isDelivered }
)
