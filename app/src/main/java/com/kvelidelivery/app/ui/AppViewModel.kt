package com.kvelidelivery.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.kvelidelivery.app.data.Driver
import com.kvelidelivery.app.data.EmployeeStore
import com.kvelidelivery.app.data.ListProcessor
import com.kvelidelivery.app.data.Person
import com.kvelidelivery.app.data.SessionStore
import com.kvelidelivery.app.data.TimeSlotGroup
import com.kvelidelivery.app.data.UpdateChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

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
    private val employeeStore = EmployeeStore(application)
    private val updateChecker = UpdateChecker(application)

    var currentScreen by mutableStateOf<Screen>(Screen.Help)
        private set

    var employeeBaseCount by mutableStateOf(0)
        private set

    var lastExportMessage by mutableStateOf<String?>(null)
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

    var extendedSlots by mutableStateOf(false)
        private set

    var updateAvailable by mutableStateOf<UpdateChecker.UpdateInfo?>(null)
        private set
    var updateDialogVisible by mutableStateOf(false)
        private set
    var updateDownloading by mutableStateOf(false)
        private set
    var updateProgress by mutableStateOf(0)
        private set
    var updateError by mutableStateOf<String?>(null)
        private set
    var updateSkippedThisSession by mutableStateOf(false)
        private set

    val todayDate: String
        get() {
            val now = java.time.LocalDateTime.now()
            val days = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
            val day = days[now.dayOfWeek.value - 1]
            return "$day ${now.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}"
        }

    val timeSlots: List<String>
        get() = ListProcessor.getEffectiveTimeSlots(
            extendedSlots,
            people.map { it.timeGroup }
        )

    val appVersionLabel: String
        get() = "v${updateChecker.currentVersionName()} (${updateChecker.currentVersionCode()})"

    init {
        ListProcessor.employeeStore = employeeStore
        employeeBaseCount = employeeStore.count()
        restoreSession()
        checkForUpdates()
    }

    private fun restoreSession() {
        val session = store.load() ?: return
        selectedDriver = Driver.entries.find { it.number == session.driverNumber }
        inputText = session.inputText
        people = session.people
        extendedSlots = session.extendedSlots
        if (people.isNotEmpty()) {
            rebuildStructured()
        }
        val restored = Screen.fromName(session.screen)
        currentScreen = when {
            people.isNotEmpty() && selectedDriver != null -> Screen.Result
            selectedDriver != null && inputText.isNotBlank() -> Screen.InputList
            selectedDriver != null -> Screen.InputList
            restored == Screen.DriverSelect -> Screen.DriverSelect
            else -> Screen.Help
        }
    }

    private fun persist() {
        store.save(
            screen = currentScreen.name(),
            driverNumber = selectedDriver?.number,
            inputText = inputText,
            people = people,
            extendedSlots = extendedSlots
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

    fun updateExtendedSlots(enabled: Boolean) {
        extendedSlots = enabled
        if (people.isNotEmpty()) rebuildStructured()
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
                val parsed = ListProcessor.parseInput(inputText, extendedSlots)
                if (parsed.isEmpty()) {
                    errorMessage = "Не удалось распознать ни одного человека. Проверьте формат списка."
                    currentScreen = Screen.InputList
                    persist()
                    return@launch
                }
                people = parsed
                employeeStore.recordPeople(parsed)
                employeeBaseCount = employeeStore.count()
                val hasNight = parsed.any { it.timeGroup in listOf("00:00", "01:00") }
                val isWeekendNight = ListProcessor.getTimeSlots(false).size >= 4
                if (hasNight || isWeekendNight || extendedSlots) {
                    extendedSlots = true
                }
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
        structured = ListProcessor.buildStructuredOutput(people, drv, extendedSlots)
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
        people = people.map {
            if (it.id == personId) {
                it.copy(assignedDriver = targetDriver.number)
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

    fun updatePerson(
        personId: String,
        name: String?,
        address: String,
        district: String?,
        role: String?,
        timeGroup: String
    ) {
        val clampedTime = ListProcessor.clampSlotToAllowed(
            timeGroup,
            extendedSlots,
            people.map { it.timeGroup }
        )
        val resolvedDistrict = district?.takeIf { it.isNotBlank() }
            ?: ListProcessor.findDistrict(address)
        people = people.map {
            if (it.id == personId) {
                it.copy(
                    name = name?.takeIf { n -> n.isNotBlank() },
                    address = address.trim(),
                    district = resolvedDistrict,
                    role = role?.takeIf { r -> r.isNotBlank() },
                    timeGroup = clampedTime
                )
            } else it
        }
        people.find { it.id == personId }?.let { employeeStore.recordPeople(listOf(it)) }
        employeeBaseCount = employeeStore.count()
        rebuildStructured()
        persist()
    }

    fun addPerson(
        name: String?,
        address: String,
        district: String?,
        role: String?,
        timeGroup: String
    ) {
        val addr = address.trim()
        if (addr.isBlank()) return
        val clampedTime = ListProcessor.clampSlotToAllowed(
            timeGroup,
            extendedSlots,
            people.map { it.timeGroup }
        )
        val resolvedDistrict = district?.takeIf { it.isNotBlank() }
            ?: ListProcessor.findDistrict(addr)
        val person = Person(
            id = java.util.UUID.randomUUID().toString(),
            name = name?.takeIf { it.isNotBlank() },
            address = addr,
            district = resolvedDistrict,
            role = role?.takeIf { it.isNotBlank() },
            timeGroup = clampedTime,
            raw = listOfNotNull(name?.takeIf { it.isNotBlank() }, addr).joinToString(" - "),
            orderIndex = (people.maxOfOrNull { it.orderIndex } ?: -1) + 1
        )
        people = people + person
        employeeStore.recordPeople(listOf(person))
        employeeBaseCount = employeeStore.count()
        rebuildStructured()
        persist()
    }

    fun reorderPerson(personId: String, direction: Int) {
        val person = people.find { it.id == personId } ?: return
        val group = people
            .filter { it.district == person.district && it.timeGroup == person.timeGroup }
            .sortedBy { it.orderIndex }
        val idx = group.indexOfFirst { it.id == personId }
        if (idx < 0) return
        val targetIdx = idx + direction
        if (targetIdx < 0 || targetIdx >= group.size) return

        val a = group[idx]
        val b = group[targetIdx]
        val orderA = a.orderIndex
        val orderB = b.orderIndex
        val newOrderA = if (orderA != orderB) orderB else orderA + direction
        val newOrderB = if (orderA != orderB) orderA else orderB - direction

        people = people.map {
            when (it.id) {
                a.id -> it.copy(orderIndex = newOrderA)
                b.id -> it.copy(orderIndex = newOrderB)
                else -> it
            }
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
            extendedSlots = false
            store.clear()
            currentScreen = Screen.Help
        }
    }

    fun goBackToInput() {
        currentScreen = Screen.InputList
        persist()
    }

    fun resetToDriverSelect() {
        currentScreen = Screen.DriverSelect
        persist()
    }

    fun exportEmployeeBase(): Boolean {
        lastExportMessage = null
        val file = employeeStore.exportToExcel()
        if (file == null || !file.exists()) {
            lastExportMessage = if (employeeStore.count() == 0) {
                "База сотрудников пуста"
            } else {
                "Не удалось создать файл Excel"
            }
            return false
        }
        return try {
            val uri: Uri = try {
                FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.fileprovider",
                    file
                )
            } catch (_: Exception) {
                Uri.fromFile(file)
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "База сотрудников Квели")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Сохранить / Открыть базу сотрудников")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(chooser)
            lastExportMessage = "Экспортировано: ${file.name} (${employeeStore.count()} записей)"
            true
        } catch (e: Exception) {
            lastExportMessage = "Ошибка: ${e.message}"
            false
        }
    }

    fun clearExportMessage() {
        lastExportMessage = null
    }

    fun checkForUpdates() {
        if (updateSkippedThisSession) return
        viewModelScope.launch {
            when (val result = updateChecker.checkForUpdate()) {
                is UpdateChecker.CheckResult.Available -> {
                    updateAvailable = result.info
                    updateDialogVisible = true
                    updateError = null
                }
                is UpdateChecker.CheckResult.UpToDate -> {
                    updateAvailable = null
                    updateDialogVisible = false
                }
                is UpdateChecker.CheckResult.Error -> {
                    updateAvailable = null
                    updateDialogVisible = false
                }
            }
        }
    }

    fun skipUpdate() {
        updateDialogVisible = false
        updateSkippedThisSession = true
        updateDownloading = false
        updateProgress = 0
        updateError = null
    }

    fun startDownloadUpdate() {
        val info = updateAvailable ?: return
        updateDownloading = true
        updateProgress = 0
        updateError = null
        viewModelScope.launch {
            when (val result = updateChecker.downloadApk(info.apkUrl) { pct ->
                updateProgress = pct
            }) {
                is UpdateChecker.DownloadResult.Success -> {
                    updateDownloading = false
                    val ok = updateChecker.installApk(result.file)
                    if (!ok) {
                        updateError =
                            "Разрешите установку из неизвестных источников в настройках, затем нажмите «Скачать» снова."
                        updateDownloading = false
                    } else {
                        updateDialogVisible = false
                    }
                }
                is UpdateChecker.DownloadResult.Error -> {
                    updateDownloading = false
                    updateError = result.message
                }
            }
        }
    }

    fun dismissUpdateError() {
        updateError = null
    }
}
