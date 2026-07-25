package com.arifr1234.receipttolenghi.printer

import android.annotation.SuppressLint
import android.util.Log
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PrinterManager {
    var printer: EscPosPrinter? = null
        private set

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
