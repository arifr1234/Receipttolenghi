package com.arifr1234.receipttolenghi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.arifr1234.receipttolenghi.ui.bluetooth.BluetoothScreen
import com.arifr1234.receipttolenghi.ui.home.HomeScreen
import com.arifr1234.receipttolenghi.ui.modes.GeneralPrintScreen
import com.arifr1234.receipttolenghi.ui.modes.TestPrinterScreen
import com.arifr1234.receipttolenghi.ui.theme.ReceipttolenghiTheme
import com.arifr1234.receipttolenghi.utils.AppErrorHandler

class MainActivity : ComponentActivity() {
    data class AppMode(
        val name: String,
        val content: @Composable (modifier: Modifier, onNavigateBack: () -> Unit) -> Unit
    )

    private val APP_MODES = listOf(
        AppMode("Test Printer") { modifier, onNavigateBack ->
            TestPrinterScreen(onNavigateBack = onNavigateBack, modifier = modifier)
        },
        AppMode("General Print") { modifier, onNavigateBack ->
            GeneralPrintScreen(onNavigateBack = onNavigateBack, modifier = modifier)
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReceipttolenghiTheme(
                darkTheme = true,
                dynamicColor = false
            ) {
                var currentScreen by remember { mutableStateOf("home") }

                if (currentScreen != "home") {
                    BackHandler {
                        currentScreen = "home"
                    }
                }

                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    AppErrorHandler.errorMessages.collect { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    val selectedMode = APP_MODES.find { it.name == currentScreen }
                    
                    if (selectedMode != null) {
                        selectedMode.content(Modifier.padding(innerPadding)) { currentScreen = "home" }
                    } else {
                        when (currentScreen) {
                            "bluetooth" -> BluetoothScreen(
                                modifier = Modifier.padding(innerPadding),
                                onNavigateBack = { currentScreen = "home" }
                            )
                            else -> {
                                // Default fallback (home)
                                HomeScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    onNavigateToBluetooth = { currentScreen = "bluetooth" },
                                    onNavigateToMode = { mode -> currentScreen = mode },
                                    modes = APP_MODES.map { it.name }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}