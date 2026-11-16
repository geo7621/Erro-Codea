package com.obdstudio.app.core

import java.util.Locale

/**
 * Finds CAN arbitration IDs returning a valid generic OBD Mode 01 PID 00 reply.
 * A responding CAN ID is NOT evidence that a particular ABS/TCM/SRS module is accessible.
 * This is deliberately read-only; there are no physical UDS requests or security services.
 */
object CanResponderCodec {
    fun responseIds(raw: String): List<String> {
        val ids = linkedSetOf<String>()
        for (line in raw.uppercase(Locale.US).split('\r', '\n')) {
            val cleaned = line.trim().replace(Regex(" +"), " ")
            if (cleaned.isEmpty()) continue
            val header = Regex("^(?:([0-9A-F]{3}|[0-9A-F]{8})\\s+|((?:[0-9A-F]{2}\\s+){3}[0-9A-F]{2})\\s+)(.+)$").matchEntire(cleaned)
            val compact = Regex("^([0-9A-F]{3}|[0-9A-F]{8})([0-9A-F]{10,})$").matchEntire(cleaned.replace(" ", ""))
            val id = (header?.groupValues?.get(1)?.ifBlank { header.groupValues[2] }?.replace(" ", "")
                ?: compact?.groupValues?.get(1)) ?: continue
            val data = (header?.groupValues?.get(3) ?: compact?.groupValues?.get(2) ?: "")
                .filter { it in '0'..'9' || it in 'A'..'F' }
            val isObdResponse = Regex("4100[0-9A-F]{8}").containsMatchIn(data)
            if (!isObdResponse) continue
            if (id.length == 3 && id.toInt(16) in 0x7E8..0x7EF) ids.add(id)
            else if (id.length == 8 && id.startsWith("18DAF1")) ids.add(id)
        }
        return ids.toList().sorted()
    }
}
