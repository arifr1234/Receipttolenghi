package com.arifr1234.receipttolenghi.printer

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import kotlinx.coroutines.CoroutineExceptionHandler
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

    private fun getPrinterOrThrow(): EscPosPrinter {
        return printer ?: throw IllegalStateException("Printer not connected. Please connect first.")
    }

    fun printAsync(context: Context, block: (EscPosPrinter) -> Unit) {
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Log.e("PrinterManager", "Print error", throwable)
            Handler(Looper.getMainLooper()).post {
                val msg = if (throwable is IllegalStateException) throwable.message else "A printing error occurred"
                Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show()
            }
        }

        printScope.launch(exceptionHandler) {
            printMutex.withLock {
                val p = getPrinterOrThrow()
                block(p)
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        if (printer != null) return@withContext true
        try {
            val connection = BluetoothPrintersConnections.selectFirstPaired()
            if (connection != null) {
                // Initialize the printer globally
                printer = EscPosPrinter(connection, 203, 48f, 32)
                true
            } else {
                Log.e("PrinterManager", "No paired printer found")
                false
            }
        } catch (e: Exception) {
            Log.e("PrinterManager", "Error connecting to printer", e)
            false
        }
    }

    fun disconnect() {
        try {
            printer?.disconnectPrinter()
        } catch (e: Exception) {
            Log.e("PrinterManager", "Error disconnecting", e)
        } finally {
            printer = null
        }
    }
}
