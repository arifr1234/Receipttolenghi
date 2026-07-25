package com.arifr1234.receipttolenghi.ui.modes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.arifr1234.receipttolenghi.printer.PrinterManager

@Composable
fun TestPrinterScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
                PrinterManager.printAsync(context) { printer ->
                    printer.printFormattedText(
                        "[C]<font size='big'>TEST PRINT</font>\n" +
                        "[C]Receipttolenghi App\n" +
                        "[L]\n" +
                        "[L]If you can read this,\n" +
                        "[L]the Bluetooth connection\n" +
                        "[L]is working perfectly.\n" +
                        "[L]\n"
                    )
                }
            },
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text("Print Test Receipt")
        }

        Button(onClick = onNavigateBack) {
            Text("Back to Home")
        }
    }
}
