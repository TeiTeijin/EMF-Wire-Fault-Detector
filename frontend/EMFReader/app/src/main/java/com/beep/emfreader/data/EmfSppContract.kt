package com.beep.emfreader.data

import java.util.UUID

object EmfSppContract {

    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    const val RELAY_NAME = "EMF_Wire_Detector"

    const val SEND_INTERVAL_MILLIS = 500L

    const val FIRST_LINE_TIMEOUT_MILLIS = 6_000L

    const val FIELD_COUNT = 5

    const val MAX_PERCENT = 100

    const val MAX_LINE_LENGTH = 160

    const val LINE_TERMINATOR = '\n'

    const val SEP = ','

    const val ASSIGN = '='

    enum class Status {
        CLEAR,
        WIRE,
        FAULT;

        companion object {
            fun fromToken(token: String): Status? = entries.firstOrNull { it.name == token }
        }
    }

    fun parseLine(line: String): EmfReadings? =
        if (line.contains(ASSIGN)) parseDebugLine(line) else parseStatusLine(line)

    fun parseStatusLine(line: String): EmfReadings? {
        val fields = line.trim().split(SEP)
        if (fields.size != FIELD_COUNT) return null

        val status = Status.fromToken(fields[STATUS_INDEX].trim()) ?: return null

        val numbers = IntArray(FIELD_COUNT - 1)
        for (i in numbers.indices) {
            val value = fields[i + 1].trim().toIntOrNull() ?: return null
            if (value !in 0..MAX_PERCENT) return null
            numbers[i] = value
        }

        return EmfReadings(
            fieldPercent = numbers[SIGNAL_FIELD],
            opampPercent = numbers[SIGNAL_OPAMP],
            jitterPercent = numbers[SIGNAL_JITTER],
            signalPercent = numbers[SIGNAL_SIGNAL],
            alertLevel = status.toAlertLevel()
        )
    }

    fun parseDebugLine(line: String): EmfReadings? {
        val fields = HashMap<String, String>()
        for (token in line.trim().split(WHITESPACE)) {
            val split = token.indexOf(ASSIGN)
            if (split <= 0) continue
            fields[token.substring(0, split)] = token.substring(split + 1)
        }

        val hasMagnet = fields.containsKey(KEY_MAGNET)
        if (hasMagnet && fields[KEY_MAGNET]?.toIntOrNull() !in listOf(0, 1)) return null

        val field = fields.percent(KEY_FIELD)
            ?: fields.percent(KEY_HALL)
            ?: fields[KEY_MAGNET]?.toIntOrNull()?.times(MAX_PERCENT)
            ?: return null
        val opamp = fields.percent(KEY_OPAMP) ?: return null
        val jitter = fields.percent(KEY_JITTER) ?: return null
        val signal = fields.percent(KEY_SIGNAL) ?: if (hasMagnet) opamp else maxOf(field, opamp)

        return EmfReadings(
            fieldPercent = field,
            opampPercent = opamp,
            jitterPercent = jitter,
            signalPercent = signal
        )
    }

    private fun Map<String, String>.percent(key: String): Int? {
        val raw = this[key]?.toIntOrNull() ?: return null
        return raw.takeIf { it in 0..MAX_PERCENT }
    }

    private val WHITESPACE = Regex("\\s+")

    private const val STATUS_INDEX = 0
    private const val SIGNAL_SIGNAL = 0
    private const val SIGNAL_FIELD = 1
    private const val SIGNAL_OPAMP = 2
    private const val SIGNAL_JITTER = 3

    private const val KEY_FIELD = "fieldPct"
    private const val KEY_HALL = "hallPct"
    private const val KEY_MAGNET = "magnet"
    private const val KEY_OPAMP = "opampPct"
    private const val KEY_JITTER = "jitterPct"
    private const val KEY_SIGNAL = "signal"

    private fun Status.toAlertLevel(): EmfAlertLevel = when (this) {
        Status.CLEAR -> EmfAlertLevel.CLEAR
        Status.WIRE -> EmfAlertLevel.WIRE
        Status.FAULT -> EmfAlertLevel.FAULT
    }
}
