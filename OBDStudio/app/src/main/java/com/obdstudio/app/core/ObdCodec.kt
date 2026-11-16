package com.obdstudio.app.core

import java.util.Locale

data class MilStatus(val illuminated: Boolean, val confirmedCodeCount: Int)

/** Defensive parser for ELM327 prompt-delimited responses (single responder / ordinary PID frames). */
object ObdCodec {
    private fun hexOnly(input: String) = input.uppercase(Locale.US).filter { it in '0'..'9' || it in 'A'..'F' }

    fun payload(raw: String, responseMode: Int, pid: Int): List<Int>? {
        val pattern = "%02X%02X".format(Locale.US, responseMode, pid)
        for (line in raw.lines()) {
            val bytes = hexOnly(line)
            val pos = bytes.indexOf(pattern)
            if (pos == -1) continue
            val trailing = bytes.substring(pos + pattern.length)
            if (trailing.length < 2) continue
            return trailing.chunked(2).filter { it.length == 2 }.map { it.toInt(16) }
        }
        return null
    }

    fun hasModeResponse(raw: String, responseMode: Int): Boolean {
        val marker = "%02X".format(Locale.US, responseMode)
        return raw.lines().any { hexOnly(it).contains(marker) }
    }

    /** Bitmask from PID 00 / 20 / 40. ELM echoes, SEARCHING lines and CAN headers are ignored. */
    fun supportedPids(raw: String, block: Int = 0): Set<Int> {
        val b = payload(raw, 0x41, block) ?: return emptySet()
        if (b.size < 4) return emptySet()
        return (1..32).filter { offset ->
            val group = (offset - 1) / 8
            val bit = 7 - ((offset - 1) % 8)
            (b[group] and (1 shl bit)) != 0
        }.map { it + block }.toSet()
    }

    fun pidValue(raw: String, pid: Int): Double? {
        val values = payload(raw, 0x41, pid) ?: return null
        if (values.isEmpty()) return null
        val a = values[0].toDouble()
        val b = if (values.size > 1) values[1].toDouble() else 0.0
        return when (pid) {
            0x0C -> if (values.size > 1) (a * 256 + b) / 4.0 else null
            0x0D -> a
            0x05, 0x0F, 0x46, 0x5C -> a - 40
            0x04, 0x11, 0x2F, 0x2C, 0x52 -> a * 100 / 255
            0x0B, 0x33 -> a
            0x0A -> a * 3
            0x10 -> if (values.size > 1) (a * 256 + b) / 100.0 else null
            0x1F, 0x31 -> if (values.size > 1) a * 256 + b else null
            0x5E -> if (values.size > 1) (a * 256 + b) / 20.0 else null
            0x06, 0x07, 0x08, 0x09, 0x2D -> (a - 128) * 100 / 128
            0x0E -> a / 2 - 64
            0x42 -> if (values.size > 1) (a * 256 + b) / 1000 else null
            else -> null
        }
    }

    /** Standard Mode 01 PID 01, byte A: MIL bit and confirmed-emissions DTC count. */
    fun milStatus(raw: String): MilStatus? {
        val data = payload(raw, 0x41, 0x01) ?: return null
        if (data.size < 4) return null
        return MilStatus((data[0] and 0x80) != 0, data[0] and 0x7F)
    }

    fun adapterVoltage(raw: String): Double? =
        Regex("([0-9]+(?:\\.[0-9]+)?)\\s*V", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.get(1)?.toDoubleOrNull()

    /** Mode 03 / 07 / 0A codes. Handles common single-frame / ELM-formatted responses. */
    fun troubleCodes(raw: String, responseMode: Int): List<String> {
        val marker = "%02X".format(Locale.US, responseMode)
        val codes = linkedSetOf<String>()
        for (line in raw.lines()) {
            // Do not parse lines unrelated to a matching OBD response.
            val bytes = hexOnly(line)
            val at = bytes.indexOf(marker)
            if (at < 0) continue
            val data = bytes.substring(at + marker.length)
            for (pair in data.chunked(4)) {
                if (pair.length != 4 || pair == "0000") continue
                val first = pair.substring(0, 2).toIntOrNull(16) ?: continue
                val second = pair.substring(2, 4).toIntOrNull(16) ?: continue
                val system = "PCBU"[(first and 0xC0) shr 6]
                val digit = (first and 0x30) shr 4
                codes += "$system$digit${"%X".format(first and 0x0F)}${"%02X".format(second)}"
            }
        }
        return codes.toList()
    }
}

object TroubleCodeDescriptions {
    private val explanations = mapOf(
        "P0101" to "Mass or volume air flow circuit range/performance",
        "P0113" to "Intake air temperature circuit high input",
        "P0128" to "Coolant thermostat below regulating temperature",
        "P0133" to "Oxygen sensor circuit slow response (Bank 1, Sensor 1)",
        "P0171" to "System too lean (Bank 1)",
        "P0174" to "System too lean (Bank 2)",
        "P0300" to "Random/multiple cylinder misfire detected",
        "P0301" to "Cylinder 1 misfire detected",
        "P0302" to "Cylinder 2 misfire detected",
        "P0420" to "Catalyst system efficiency below threshold (Bank 1)",
        "P0442" to "Evaporative emission control system small leak",
        "P0455" to "Evaporative emission control system gross leak",
        "P0700" to "Transmission control system requested MIL (not a full TCM scan)"
    )
    fun explain(code: String) = explanations[code] ?: "No detailed offline description yet — retain the code for investigation"
}
