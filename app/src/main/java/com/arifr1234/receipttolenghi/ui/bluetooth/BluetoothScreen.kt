package com.arifr1234.receipttolenghi.ui.bluetooth

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arifr1234.receipttolenghi.printer.PrinterManager
import com.arifr1234.receipttolenghi.utils.AppNotifier
import kotlinx.coroutines.launch

@Composable
fun BluetoothScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isConnecting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grantedMap ->
        val allGranted = grantedMap.values.all { it }
        if (allGranted) {
            coroutineScope.launch {
                isConnecting = true
                PrinterManager.connect()
                isConnecting = false
            }
        } else {
            AppNotifier.handleError(SecurityException("Permissions not granted."))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Bluetooth Connection",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(
            onClick = { launcher.launch(permissions) },
            enabled = !isConnecting,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(if (isConnecting) "Connecting..." else "Connect to First Paired Printer")
        }

        Button(
            onClick = {
                coroutineScope.launch {
                    isConnecting = true
                    PrinterManager.connect(isMock = true)
                    isConnecting = false
                }
            },
            enabled = !isConnecting,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text("Connect to Mock Printer")
        }

        Button(onClick = onNavigateBack) {
            Text("Back to Home")
        }
    }
}
