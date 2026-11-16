package com.obdstudio.app.bluetooth

/** Future Wi-Fi and professional adapters implement the same transport API. */
interface DiagnosticTransport {
    suspend fun open(address: String)
    suspend fun request(command: String, timeoutMs: Long = 4500L): String
    fun close()
}
