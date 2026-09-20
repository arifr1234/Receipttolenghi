package com.arifr1234.receipttolenghi.printer

import com.dantsu.escposprinter.connection.DeviceConnection

class MockConnection : DeviceConnection() {
    private var connected = false

    override fun connect(): DeviceConnection {
        connected = true
        return this
    }

    override fun disconnect(): DeviceConnection {
        connected = false
        return this
    }

    override fun write(bytes: ByteArray?) {
        // Do nothing, just discard the bytes
    }

    override fun send() {
        // Do nothing
    }

    override fun send(addTimeToWait: Int) {
        // Do nothing
    }
}
