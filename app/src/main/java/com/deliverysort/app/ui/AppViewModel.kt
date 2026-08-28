package com.kvelidelivery.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvelidelivery.app.data.Driver
import com.kvelidelivery.app.data.ListProcessor
import com.kvelidelivery.app.data.Person
import com.kvelidelivery.app.data.TimeSlotGroup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class Screen {
    data object Help : Screen()
    data object DriverSelect : Screen()
    data object InputList : Screen()
    data object Processing : Screen()
    data object Result : Screen()
}

class AppViewModel : ViewModel() {

    var currentScreen by mutableStateOf<Screen>(Screen.Help)
        private set

    var selectedDriver by mutableStateOf<Driver?>(null)
        private set

    var inputText by mutableStateOf("")
        private set

    var people by mutableStateOf<List<Person>>(emptyList())
        private set

    var structured by mutableStateOf<List<TimeSlotGroup>>(emptyList())
        private set

    var undefinedPeople by mutableStateOf<List<Person>>(emptyList())
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    val todayDate: String
        get() {
            val now = LocalDateTime.now()
            val days = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
            val day = days[now.dayOfWeek.value - 1]
            return "$day ${now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}"
        }

    val timeSlots: List<String>
        get() = ListProcessor.getTimeSlots()

    fun skipHelp() {
        currentScreen = Screen.DriverSelect
    }

    fun selectDriver(driver: Driver) {
        selectedDriver = driver
        currentScreen = Screen.InputList
    }

    fun updateInput(text: String) {
        inputText = text
    }

    fun processList() {
        if (inputText.trim().length < 20) {
            errorMessage = "Слишком короткий текст. Пришли полный список."
            return
        }
        errorMessage = null
        currentScreen = Screen.Processing
        viewModelScope.launch {
            delay(1200) // nice animation time
            try {
                val parsed = ListProcessor.parseInput(inputText)
                if (parsed.isEmpty()) {
                    errorMessage = "Не удалось распознать ни одного человека. Проверьте формат списка."
                    currentScreen = Screen.InputList
                    return@launch
                }
                people = parsed
                rebuildStructured()
                currentScreen = Screen.Result
            } catch (e: Exception) {
                errorMessage = "Ошибка: ${e.message}"
                currentScreen = Screen.InputList
            }
        }
    }

    private fun rebuildStructured() {
        val drv = selectedDriver ?: Driver.ONE
        structured = ListProcessor.buildStructuredOutput(people, drv)
        undefinedPeople = ListProcessor.getUndefined(people)
    }

    fun toggleDelivered(personId: String) {
        people = people.map {
            if (it.id == personId) it.copy(isDelivered = !it.isDelivered) else it
        }
        rebuildStructured()
    }

    fun movePersonToDriver(personId: String, targetDriver: Driver) {
        val targetDistricts = when (targetDriver) {
            Driver.ONE -> listOf("СЗР", "ЮЗР")
            Driver.TWO -> listOf("ЦЕНТР", "НОВЫЙ", "НЧК", "БОГДАНКА")
            Driver.THREE -> listOf("НЮР", "КУГЕСИ", "БОГДАНКА")
        }
        // Assign first suitable or keep and force
        people = people.map {
            if (it.id == personId) {
                val newDist = if (it.district in targetDistricts) it.district
                else targetDistricts.firstOrNull() ?: it.district
                it.copy(
                    district = newDist ?: "СЗР",
                    assignedDriver = targetDriver.number
                )
            } else it
        }
        rebuildStructured()
    }

    fun movePersonToDistrict(personId: String, district: String) {
        people = people.map {
            if (it.id == personId) it.copy(district = district, assignedDriver = null) else it
        }
        rebuildStructured()
    }

    fun finishTrip() {
        people = emptyList()
        structured = emptyList()
        undefinedPeople = emptyList()
        inputText = ""
        selectedDriver = null
        errorMessage = null
        currentScreen = Screen.DriverSelect
    }

    fun goBackToInput() {
        currentScreen = Screen.InputList
    }
}
