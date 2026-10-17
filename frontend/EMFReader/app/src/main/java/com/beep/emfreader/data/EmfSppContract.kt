package com.beep.emfreader.data

import java.util.UUID

/**
 * The wire contract between the app and the sensor.
 *
 * The sensing and classification happen on an Arduino, which is wired to an
 * ESP32. The ESP32 forwards the Arduino's text output to the phone over
 * Bluetooth Classic (SPP). Nothing in this chain has a handshake: the sketch
 * starts sending the moment it boots, so there is no START to wait for.
 *
 * Two line formats arrive over that link, and the app accepts both.
 *
 * **The status line**, which is the contract
 * EMF_Wire_Fault_Detector_v11.ino defines for this purpose on `espSerial`:
 *
 *     STATUS,signal,fieldPct,opampPct,jitterPct
 *     e.g. WIRE,44,0,44,20
 *
 * The field channel is the QMC5883P magnetometer path (change in field
 * strength from the calibrated rest value); the op-amp channel is the
 * antenna path. `signal` is `max(field, opamp)` on the sketch.
 *
 * **The debug line**, which is the sketch's ordinary `Serial` output and is
 * what a board wired for debugging actually transmits. Three variants exist:
 *
 *     hallPct=12 opampPct=44 jitterAvg=8 jitterPct=20  signal=44   (EMF_Reader_8, analog Hall)
 *     p2p=41 opampPct=44 jitterAvg=8 jitterPct=20 magnet=0         (v3, switch-type Hall)
 *     p2p=41 opampPct=44 field=12 mag=12345 xyz=1,2,3 fails=0 same=0 reinit=0 fieldPct=12 jitterPct=20  (v11, QMC5883P)
 *
 * `signal=` is optional; v3 and v11 omit it and the app recomputes
 * `max(field, opamp)` (v3 magnet lines default to `opamp`, matching v3 which
 * classifies on op-amp only). Raw counts (`p2p`, `field`, `mag`, `xyz`,
 * `fails`, `same`, `reinit`, `jitterAvg`) are never percentages and are
 * skipped — notably `field=` is a raw count while `fieldPct=` is the 0-100
 * value the app uses.
 *
 * Routing is by `=`: any line containing `=` is a debug line, anything else
 * is tried as a status line. This matters because the v11 debug line embeds
 * commas inside `xyz=x,y,z`, so splitting on commas alone would misroute it.
 *
 * Only the status line carries a verdict. On a debug line the app applies the
 * sketch's own rules, so a re-tuned threshold on the Arduino is honoured
 * either way. Both formats already carry percentages, so neither is scaled or
 * based again here.
 */
object EmfSppContract {

    /** The Serial Port Profile service every Bluetooth Classic relay exposes. */
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /** Bluetooth name the relay firmware advertises (`SerialBT.begin` in the sketch). */
    const val RELAY_NAME = "EMF_Wire_Detector"

    /** How often the sketch reports, used to describe the cadence. */
    const val SEND_INTERVAL_MILLIS = 500L

    /**
     * A silent socket is not the same as a working one. The ESP32 accepts the
     * RFCOMM connection even when the Arduino is unpowered, so without this the
     * screen would sit on zeroes forever looking healthy.
     */
    const val FIRST_LINE_TIMEOUT_MILLIS = 6_000L

    /** STATUS plus the four numbers. */
    const val FIELD_COUNT = 5

    /** The sketch clamps every reported channel with `constrain(..., 0, 100)`. */
    const val MAX_PERCENT = 100

    /** Cap on an unterminated line, so a chatty peer cannot grow the buffer forever.
     * Sized for the v11 debug line, which carries xyz/fails/same/reinit diagnostics. */
    const val MAX_LINE_LENGTH = 160

    /** Terminator the relay's `println` produces. */
    const val LINE_TERMINATOR = '\n'

    const val SEP = ','

    const val ASSIGN = '='

    /** The alert word the sketch writes first on every status line. */
    enum class Status {
        CLEAR,
        WIRE,
        FAULT;

        companion object {
            fun fromToken(token: String): Status? = entries.firstOrNull { it.name == token }
        }
    }

    /**
     * Decodes one line, or returns null when it is not a reading.
     *
     * Anything unexpected is dropped rather than shown as a plausible number: the
     * relay's startup banner, the sketch's raw-adc trace, a partial line, or a
     * status word from a different sketch version should all leave the last good
     * reading on screen instead of replacing it with noise.
     *
     * Routed by `=`: the v11 debug line contains commas inside `xyz=x,y,z`,
     * so a comma check alone would misroute it to the status parser.
     */
    fun parseLine(line: String): EmfReadings? =
        if (line.contains(ASSIGN)) parseDebugLine(line) else parseStatusLine(line)

    /**
     * `STATUS,signal,fieldPct,opampPct,jitterPct`, where the status is the
     * sketch's own verdict and is carried through untouched.
     */
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

    /**
     * The sketch's debug output. Three variants are accepted:
     *
     *     hallPct=12 opampPct=44 jitterAvg=8 jitterPct=20  signal=44  (analog Hall)
     *     p2p=41 opampPct=44 jitterAvg=8 jitterPct=20 magnet=0        (v3 switch Hall)
     *     p2p=41 opampPct=44 field=12 mag=12345 xyz=1,2,3 fails=0 same=0 reinit=0 fieldPct=12 jitterPct=20  (v11 QMC5883P)
     *
     * `jitterAvg`, `p2p`, `field`, `mag`, `xyz`, `fails`, `same` and `reinit`
     * are raw counts/diagnostics rather than percentages and are skipped.
     * `magnet=0/1` (v3) maps to `field 0/100`. `fieldPct=` (v11) is preferred;
     * `hallPct=` is kept for older sketches. `signal` is used when present;
     * otherwise v3 magnet lines default to `opamp` (v3 classifies on op-amp
     * only) while all other lines fall back to `max(field, opamp)`, matching
     * what v11 sends explicitly on the status line.
     */
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

    /** Clamped percentage field, or null when absent or outside the sketch's range. */
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