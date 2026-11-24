package com.kvelidelivery.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kvelidelivery.app.ui.AppViewModel
import com.kvelidelivery.app.ui.Screen
import com.kvelidelivery.app.ui.screens.*
import com.kvelidelivery.app.ui.theme.KveliDeliveryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KveliDeliveryTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KveliDeliveryApp()
                }
            }
        }
    }
}

@Composable
fun KveliDeliveryApp(viewModel: AppViewModel = viewModel()) {
    val updateInfo = viewModel.updateAvailable
    if (viewModel.updateDialogVisible && updateInfo != null) {
        UpdateDialog(
            info = updateInfo,
            currentVersionLabel = viewModel.appVersionLabel,
            downloading = viewModel.updateDownloading,
            progress = viewModel.updateProgress,
            error = viewModel.updateError,
            onDownload = { viewModel.startDownloadUpdate() },
            onSkip = { viewModel.skipUpdate() },
            onDismissError = { viewModel.dismissUpdateError() }
        )
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importEmployeeBase(uri)
        }
    }

    AnimatedContent(
        targetState = viewModel.currentScreen,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "screen"
    ) { screen ->
        when (screen) {
            is Screen.Help -> HelpScreen(onSkip = { viewModel.skipHelp() })
            is Screen.DriverSelect -> DriverSelectScreen(
                todayDate = viewModel.todayDate,
                timeSlots = viewModel.timeSlots,
                slotMode = viewModel.slotMode,
                onSlotModeChange = { viewModel.updateSlotMode(it) },
                onSelect = { viewModel.selectDriver(it) },
                employeeBaseCount = viewModel.employeeBaseCount,
                baseMessage = viewModel.lastBaseMessage,
                onSaveEmployeeBase = { viewModel.saveEmployeeBaseToDownloads() },
                onImportEmployeeBase = {
                    importLauncher.launch(
                        arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel",
                            "application/octet-stream",
                            "*/*"
                        )
                    )
                },
                onViewEmployeeBase = { viewModel.viewEmployeeBase() },
                onDismissBaseMessage = { viewModel.clearBaseMessage() }
            )
            is Screen.InputList -> {
                val driver = viewModel.selectedDriver
                if (driver != null) {
                    InputScreen(
                        selectedDriver = driver,
                        todayDate = viewModel.todayDate,
                        timeSlots = viewModel.timeSlots,
                        inputText = viewModel.inputText,
                        errorMessage = viewModel.errorMessage,
                        onInputChange = { viewModel.updateInput(it) },
                        onProcess = { viewModel.processList() },
                        onBack = { viewModel.resetToDriverSelect() }
                    )
                }
            }
            is Screen.Processing -> ProcessingScreen()
            is Screen.Result -> {
                val driver = viewModel.selectedDriver
                if (driver != null) {
                    ResultScreen(
                        selectedDriver = driver,
                        todayDate = viewModel.todayDate,
                        timeSlots = viewModel.timeSlots,
                        structured = viewModel.structured,
                        undefinedPeople = viewModel.undefinedPeople,
                        onToggleDelivered = { viewModel.toggleDelivered(it) },
                        onMoveToDriver = { id, drv -> viewModel.movePersonToDriver(id, drv) },
                        onMoveToDistrict = { id, dist -> viewModel.movePersonToDistrict(id, dist) },
                        onMoveToTimeSlot = { id, slot -> viewModel.movePersonToTimeSlot(id, slot) },
                        onReorder = { id, dir -> viewModel.reorderPerson(id, dir) },
                        onUpdatePerson = { id, name, address, district, role, timeGroup ->
                            viewModel.updatePerson(id, name, address, district, role, timeGroup)
                        },
                        onAddPerson = { name, address, district, role, timeGroup ->
                            viewModel.addPerson(name, address, district, role, timeGroup)
                        },
                        onFinish = { viewModel.finishTrip() }
                    )
                }
            }
            is Screen.Finished -> FinishedScreen()
        }
    }
}