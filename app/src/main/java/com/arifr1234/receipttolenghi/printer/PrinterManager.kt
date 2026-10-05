package com.arifr1234.receipttolenghi.printer

import android.annotation.SuppressLint
import com.arifr1234.receipttolenghi.utils.AppNotifier
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.DeviceConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object PrinterManager {
    const val PRINTER_DPI = 203
    const val PRINTER_WIDTH_MM = 48f
    const val PRINTER_NBR_CHARACTERS_PER_LINE = 32

    val MAX_PRINTER_WIDTH_PX: Int by lazy {
        EscPosPrinter(null as DeviceConnection?, PRINTER_DPI, PRINTER_WIDTH_MM, PRINTER_NBR_CHARACTERS_PER_LINE).printerWidthPx
    }

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

    val isConnecting = kotlinx.coroutines.flow.MutableStateFlow(false)

    @SuppressLint("MissingPermission")
    suspend fun connect(isMock: Boolean = false, silent: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (isConnecting.value) return@withContext false
        isConnecting.value = true

        val result = runCatching {
            AppNotifier.runWithGlobalLog("Error connecting to printer") {
                disconnect() // Always attempt a fresh connection
                
                val connection = if (isMock) {
                    MockConnection()
                } else {
                    BluetoothPrintersConnections.selectFirstPaired()
                }
                
                if (connection != null) {
                    // Initialize the printer globally
                    printer = EscPosPrinter(connection, PRINTER_DPI, PRINTER_WIDTH_MM, PRINTER_NBR_CHARACTERS_PER_LINE)
                    val printerType = if (isMock) "Mock Printer" else "Printer"
                    AppNotifier.showMessage("Connected to $printerType!")
                    true
                } else {
                    if (!silent) AppNotifier.handleError(IllegalStateException("No paired printer found"))
                    false
                }
            }
        }.getOrDefault(false)
        
        isConnecting.value = false
        result
    }

    fun disconnect() {
        runCatching {
            AppNotifier.runWithGlobalLog("Error disconnecting from printer") {
                printer?.disconnectPrinter()
            }
        }.also { printer = null }
    }
}
