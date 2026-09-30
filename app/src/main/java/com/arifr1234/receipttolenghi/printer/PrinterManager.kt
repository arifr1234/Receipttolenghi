package com.arifr1234.receipttolenghi.printer

import android.annotation.SuppressLint
import com.arifr1234.receipttolenghi.utils.AppNotifier
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object PrinterManager {
    private var printer: EscPosPrinter? = null

    private val printScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val printMutex = Mutex()

    fun isConnected(): Boolean = printer != null

    private fun getPrinterOrThrow(): EscPosPrinter {
        return printer ?: throw IllegalStateException("Printer not connected. Please connect first.")
    }

    fun printAsync(block: (EscPosPrinter) -> Unit) {
        printScope.launch {
            runCatching {
                AppNotifier.runWithGlobalLog {
                    printMutex.withLock {
                        val p = getPrinterOrThrow()
                        block(p)
                    }
                }
            }.onFailure { throwable ->
                if (throwable is com.dantsu.escposprinter.exceptions.EscPosConnectionException) {
                    printer = null
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(isMock: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            AppNotifier.runWithGlobalLog("Error connecting to printer") {
                disconnect() // Always attempt a fresh connection
                
                val connection = if (isMock) {
                    MockConnection()
                } else {
                    BluetoothPrintersConnections.selectFirstPaired()
                }
                
                if (connection != null) {
                    // Initialize the printer globally
                    printer = EscPosPrinter(connection, 203, 48f, 32)
                    val printerType = if (isMock) "Mock Printer" else "Printer"
                    AppNotifier.showMessage("Connected to $printerType!")
                    true
                } else {
                    AppNotifier.handleError(IllegalStateException("No paired printer found"))
                    false
                }
            }
        }.getOrDefault(false)
    }

    fun disconnect() {
        runCatching {
            AppNotifier.runWithGlobalLog("Error disconnecting from printer") {
                printer?.disconnectPrinter()
            }
        }.also { printer = null }
    }
}
