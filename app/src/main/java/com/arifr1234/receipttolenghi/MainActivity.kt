package com.arifr1234.receipttolenghi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.arifr1234.receipttolenghi.ui.bluetooth.BluetoothScreen
import com.arifr1234.receipttolenghi.ui.home.HomeScreen
import com.arifr1234.receipttolenghi.ui.modes.TestPrinterScreen
import com.arifr1234.receipttolenghi.ui.theme.ReceipttolenghiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReceipttolenghiTheme(
                darkTheme = true,
                dynamicColor = false
            ) {
                var currentScreen by remember { mutableStateOf("home") }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (currentScreen) {
                        "home" -> HomeScreen(
                            modifier = Modifier.padding(innerPadding),
                            onNavigateToBluetooth = { currentScreen = "bluetooth" },
                            onNavigateToMode = { mode -> currentScreen = mode }
                        )
                        "bluetooth" -> BluetoothScreen(
                            modifier = Modifier.padding(innerPadding),
                            onNavigateBack = { currentScreen = "home" }
                        )
                        "Mode 1" -> TestPrinterScreen(
                            modifier = Modifier.padding(innerPadding),
                            onNavigateBack = { currentScreen = "home" }
                        )
                        else -> {
                            // Default fallback
                            HomeScreen(
                                modifier = Modifier.padding(innerPadding),
                                onNavigateToBluetooth = { currentScreen = "bluetooth" },
                                onNavigateToMode = { mode -> currentScreen = mode }
                            )
                        }
                    }
                }
            }
        }
    }
}