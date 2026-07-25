package com.arifr1234.receipttolenghi.ui.modes

import android.util.Log
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arifr1234.receipttolenghi.printer.PrinterManager

@Composable
fun TestPrinterScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var printStatus by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Test Printer",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(
            onClick = {
                val printer = PrinterManager.printer
                if (printer == null) {
                    printStatus = "Printer not connected. Please connect first."
                } else {
                    try {
                        printer.printFormattedText(
                            "[C]<font size='big'>TEST PRINT</font>\n" +
                            "[C]Receipttolenghi App\n" +
                            "[L]\n" +
                            "[L]If you can read this,\n" +
                            "[L]the Bluetooth connection\n" +
                            "[L]is working perfectly.\n" +
                            "[L]\n"
                        )
                        printStatus = "Print command sent!"
                    } catch (e: Exception) {
                        Log.e("TestPrinterScreen", "Failed to print", e)
                        printStatus = "Failed to print. Check logs."
                    }
                }
            },
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text("Print Test Receipt")
        }

        if (printStatus.isNotEmpty()) {
            Text(
                text = printStatus,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Button(onClick = onNavigateBack) {
            Text("Back to Home")
        }
    }
}
