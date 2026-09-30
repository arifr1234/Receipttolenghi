package com.arifr1234.receipttolenghi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
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
import java.io.File
import com.arifr1234.receipttolenghi.ui.modes.GeneralPrintScreen
import com.arifr1234.receipttolenghi.ui.modes.TestPrinterScreen
import com.arifr1234.receipttolenghi.ui.modes.general.PlaylistManager
import com.arifr1234.receipttolenghi.ui.theme.ReceipttolenghiTheme
import com.arifr1234.receipttolenghi.utils.AppNotifier

enum class AppRoute(val title: String) {
    HOME("Home"),
    BLUETOOTH("Bluetooth"),
    TEST_PRINTER("Test Printer"),
    GENERAL_PRINT("General Print")
}

class MainActivity : ComponentActivity() {
    data class AppMode(
        val route: AppRoute,
        val content: @Composable (modifier: Modifier, onNavigateBack: () -> Unit) -> Unit
    )

    private val APP_MODES = listOf(
        AppMode(AppRoute.TEST_PRINTER) { modifier, onNavigateBack ->
            TestPrinterScreen(onNavigateBack = onNavigateBack, modifier = modifier)
        },
        AppMode(AppRoute.GENERAL_PRINT) { modifier, onNavigateBack ->
            GeneralPrintScreen(onNavigateBack = onNavigateBack, modifier = modifier)
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlaylistManager.init(File(filesDir, "playlists"))
        enableEdgeToEdge()
        setContent {
            ReceipttolenghiTheme(
                darkTheme = true,
                dynamicColor = false
            ) {
                var currentScreen by remember { mutableStateOf(AppRoute.HOME) }

                if (currentScreen != AppRoute.HOME) {
                    BackHandler {
                        currentScreen = AppRoute.HOME
                    }
                }

                LaunchedEffect(Unit) {
                    val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    } else {
                        androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.BLUETOOTH) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    if (hasPermission) {
                        com.arifr1234.receipttolenghi.printer.PrinterManager.connect(silent = true)
                    }
                }

                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    AppNotifier.messages.collect { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { 
                        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.imePadding()) { snackbarData ->
                            val customVisuals = snackbarData.visuals as? com.arifr1234.receipttolenghi.utils.AppMessage
                            val isError = customVisuals?.isError == true
                            
                            val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState()

                            LaunchedEffect(dismissState.currentValue) {
                                if (dismissState.currentValue != androidx.compose.material3.SwipeToDismissBoxValue.Settled) {
                                    snackbarData.dismiss()
                                }
                            }

                            androidx.compose.material3.SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {},
                                content = {
                                    androidx.compose.material3.Snackbar(
                                        snackbarData = snackbarData,
                                        containerColor = if (isError) androidx.compose.material3.MaterialTheme.colorScheme.errorContainer else androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = if (isError) androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer else androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    val selectedMode = APP_MODES.find { it.route == currentScreen }
                    
                    if (selectedMode != null) {
                        selectedMode.content(Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)) { currentScreen = AppRoute.HOME }
                    } else {
                        when (currentScreen) {
                            AppRoute.BLUETOOTH -> BluetoothScreen(
                                modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
                                onNavigateBack = { currentScreen = AppRoute.HOME }
                            )
                            else -> {
                                // Default fallback (home)
                                HomeScreen(
                                    modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
                                    onNavigateToBluetooth = { currentScreen = AppRoute.BLUETOOTH },
                                    onNavigateToMode = { route -> currentScreen = route },
                                    modes = APP_MODES.map { it.route }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}