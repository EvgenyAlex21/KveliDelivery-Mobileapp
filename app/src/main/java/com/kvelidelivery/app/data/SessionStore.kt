package com.kvelidelivery.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Сохранение состояния поездки между запусками приложения.
 * Сброс только при «Завершить поездку».
 */
class SessionStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class Session(
        val screen: String,
        val driverNumber: Int?,
        val inputText: String,
        val people: List<Person>
    )

    fun save(
        screen: String,
        driverNumber: Int?,
        inputText: String,
        people: List<Person>
    ) {
        val screenToSave = when (screen) {
            "Processing" -> "Result"
            "Finished" -> "Help"
            else -> screen
        }
        val arr = JSONArray()
        for (p in people) {
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("address", p.address)
                put("district", p.district)
                put("role", p.role)
                put("timeGroup", p.timeGroup)
                put("raw", p.raw)
                put("isDelivered", p.isDelivered)
                put("assignedDriver", p.assignedDriver)
                put("orderIndex", p.orderIndex)
            })
        }
        prefs.edit()
            .putString(KEY_SCREEN, screenToSave)
            .putInt(KEY_DRIVER, driverNumber ?: -1)
            .putString(KEY_INPUT, inputText)
            .putString(KEY_PEOPLE, arr.toString())
            .apply()
    }

    fun load(): Session? {
        if (!prefs.contains(KEY_SCREEN)) return null
        val screen = prefs.getString(KEY_SCREEN, null) ?: return null
        val driverNum = prefs.getInt(KEY_DRIVER, -1).let { if (it < 0) null else it }
        val input = prefs.getString(KEY_INPUT, "") ?: ""
        val peopleJson = prefs.getString(KEY_PEOPLE, "[]") ?: "[]"
        val people = mutableListOf<Person>()
        try {
            val arr = JSONArray(peopleJson)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                people.add(
                    Person(
                        id = o.optString("id"),
                        name = if (o.isNull("name")) null else o.optString("name"),
                        address = o.optString("address"),
                        district = if (o.isNull("district") || o.optString("district").isEmpty()) null else o.optString("district"),
                        role = if (o.isNull("role") || o.optString("role").isEmpty()) null else o.optString("role"),
                        timeGroup = o.optString("timeGroup", "23:00"),
                        raw = o.optString("raw"),
                        isDelivered = o.optBoolean("isDelivered", false),
                        assignedDriver = if (o.isNull("assignedDriver")) null else o.optInt("assignedDriver"),
                        orderIndex = o.optInt("orderIndex", 0)
                    )
                )
            }
        } catch (_: Exception) {
            // ignore corrupt data
        }
        return Session(screen, driverNum, input, people)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "razvoz_kveli_session"
        private const val KEY_SCREEN = "screen"
        private const val KEY_DRIVER = "driver"
        private const val KEY_INPUT = "input"
        private const val KEY_PEOPLE = "people"
    }
}
