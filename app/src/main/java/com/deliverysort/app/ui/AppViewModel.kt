package com.kvelidelivery.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kvelidelivery.app.data.Driver
import com.kvelidelivery.app.data.ListProcessor
import com.kvelidelivery.app.data.Person
import com.kvelidelivery.app.data.SessionStore
import com.kvelidelivery.app.data.TimeSlotGroup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class Screen {
    data object Help : Screen()
    data object DriverSelect : Screen()
    data object InputList : Screen()
    data object Processing : Screen()
    data object Result : Screen()
    data object Finished : Screen()

    fun name(): String = when (this) {
        Help -> "Help"
        DriverSelect -> "DriverSelect"
        InputList -> "InputList"
        Processing -> "Processing"
        Result -> "Result"
        Finished -> "Finished"
    }

    companion object {
        fun fromName(name: String?): Screen = when (name) {
            "DriverSelect" -> DriverSelect
            "InputList" -> InputList
            "Result" -> Result
            "Processing" -> Result
            "Finished" -> Help
            else -> Help
        }
    }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SessionStore(application)

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
            val now = java.time.LocalDateTime.now()
            val days = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
            val day = days[now.dayOfWeek.value - 1]
            return "$day ${now.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}"
        }

    val timeSlots: List<String>
        get() = ListProcessor.getTimeSlots()

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val session = store.load() ?: return
        selectedDriver = Driver.entries.find { it.number == session.driverNumber }
        inputText = session.inputText
        people = session.people
        if (people.isNotEmpty()) {
            rebuildStructured()
        }
        val restored = Screen.fromName(session.screen)
        // Если есть список — сразу в результат; если выбран водитель — ввод; иначе справка/выбор
        currentScreen = when {
            people.isNotEmpty() && selectedDriver != null -> Screen.Result
            selectedDriver != null && inputText.isNotBlank() -> Screen.InputList
            selectedDriver != null -> Screen.InputList
            restored == Screen.DriverSelect -> Screen.DriverSelect
            else -> Screen.Help
        }
    }

    private fun persist() {
        // Не пишем во время Processing/Finished — save() сам нормализует
        store.save(
            screen = currentScreen.name(),
            driverNumber = selectedDriver?.number,
            inputText = inputText,
            people = people
        )
    }

    fun skipHelp() {
        currentScreen = Screen.DriverSelect
        persist()
    }

    fun selectDriver(driver: Driver) {
        selectedDriver = driver
        currentScreen = Screen.InputList
        persist()
    }

    fun updateInput(text: String) {
        inputText = text
        persist()
    }

    fun processList() {
        if (inputText.trim().length < 20) {
            errorMessage = "Слишком короткий текст. Пришли полный список."
            return
        }
        errorMessage = null
        currentScreen = Screen.Processing
        viewModelScope.launch {
            delay(1200)
            try {
                val parsed = ListProcessor.parseInput(inputText)
                if (parsed.isEmpty()) {
                    errorMessage = "Не удалось распознать ни одного человека. Проверьте формат списка."
                    currentScreen = Screen.InputList
                    persist()
                    return@launch
                }
                people = parsed
                rebuildStructured()
                currentScreen = Screen.Result
                persist()
            } catch (e: Exception) {
                errorMessage = "Ошибка: ${e.message}"
                currentScreen = Screen.InputList
                persist()
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
        persist()
    }

    fun movePersonToDriver(personId: String, targetDriver: Driver) {
        val targetDistricts = when (targetDriver) {
            Driver.ONE -> listOf("СЗР", "ЮЗР")
            Driver.TWO -> listOf("ЦЕНТР", "НОВЫЙ", "НЧК", "БОГДАНКА")
            Driver.THREE -> listOf("НЮР", "КУГЕСИ", "БОГДАНКА")
        }
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
        persist()
    }

    fun movePersonToDistrict(personId: String, district: String) {
        people = people.map {
            if (it.id == personId) it.copy(district = district, assignedDriver = null) else it
        }
        rebuildStructured()
        persist()
    }

    fun movePersonToTimeSlot(personId: String, timeSlot: String) {
        people = people.map {
            if (it.id == personId) it.copy(timeGroup = timeSlot) else it
        }
        rebuildStructured()
        persist()
    }

    fun finishTrip() {
        currentScreen = Screen.Finished
        viewModelScope.launch {
            delay(2200)
            people = emptyList()
            structured = emptyList()
            undefinedPeople = emptyList()
            inputText = ""
            selectedDriver = null
            errorMessage = null
            store.clear()
            currentScreen = Screen.Help
        }
    }

    fun goBackToInput() {
        currentScreen = Screen.InputList
        persist()
    }

    fun resetToDriverSelect() {
        // Назад с экрана ввода — не сбрасываем всю поездку, только возвращаем к выбору водителя
        currentScreen = Screen.DriverSelect
        persist()
    }
}
