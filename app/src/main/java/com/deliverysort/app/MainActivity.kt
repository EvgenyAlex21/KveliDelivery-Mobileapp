package com.kvelidelivery.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
                onSelect = { viewModel.selectDriver(it) }
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
                        onFinish = { viewModel.finishTrip() }
                    )
                }
            }
            is Screen.Finished -> FinishedScreen()
        }
    }
}
