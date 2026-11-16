package com.obdstudio.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/** ELM327 Bluetooth Classic SPP. Not compatible with BLE-only dongles. */
class BluetoothSppTransport(private val context: Context) : DiagnosticTransport {
    private val ioMutex = Mutex()
    @Volatile private var socket: BluetoothSocket? = null
    private val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission") // The Activity requests BLUETOOTH_CONNECT before calling.
    override suspend fun open(address: String) = withContext(Dispatchers.IO) {
        close()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            ?: error("This phone has no Bluetooth adapter")
        require(adapter.isEnabled) { "Enable Bluetooth in Android settings" }
        val remote = adapter.getRemoteDevice(address)
        val freshSocket = remote.createRfcommSocketToServiceRecord(spp)
        try {
            freshSocket.connect()
            socket = freshSocket
        } catch (t: Throwable) {
            runCatching { freshSocket.close() }
            throw t
        }
    }

    override suspend fun request(command: String, timeoutMs: Long): String = ioMutex.withLock {
        withContext(Dispatchers.IO) {
            val s = socket ?: error("Bluetooth adapter not connected")
            val input = s.inputStream
            val output = s.outputStream
            // Discard any leftover prompt after an interrupted command.
            val scratch = ByteArray(512)
            while (input.available() > 0) input.read(scratch, 0, minOf(scratch.size, input.available()))
            output.write((command.trim() + "\r").toByteArray(Charsets.US_ASCII))
            output.flush()
            val start = SystemClock.elapsedRealtime()
            val buffer = ByteArrayOutputStream()
            while (SystemClock.elapsedRealtime() - start < timeoutMs) {
                if (input.available() > 0) {
                    val count = input.read(scratch, 0, minOf(scratch.size, input.available()))
                    if (count < 0) error("Adapter closed the connection")
                    if (count > 0) {
                        buffer.write(scratch, 0, count)
                        if (buffer.size() > 16_384) error("Adapter response exceeded 16 KB")
                        if (buffer.toString("US-ASCII").contains('>')) return@withContext buffer.toString("US-ASCII")
                    }
                } else {
                    Thread.sleep(25)
                }
            }
            error("Adapter timeout on $command")
        }
    }

    override fun close() {
        val previous = socket
        socket = null
        runCatching { previous?.close() }
    }
}
