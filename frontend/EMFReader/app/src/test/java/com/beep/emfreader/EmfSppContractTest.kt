package com.beep.emfreader

import com.beep.emfreader.data.EmfAlertLevel
import com.beep.emfreader.data.EmfReadings
import com.beep.emfreader.data.EmfSppContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The line contract, checked against the exact strings the sketch produces.
 *
 * The worked examples come from EMF_Wire_Fault_Detector_v11.ino
 * (`sendStatusToEsp`: `STATUS,signal,fieldPct,opampPct,jitterPct`), with
 * legacy debug lines from EMF_Reader_8.ino and v3 kept for backward
 * compatibility.
 */
class EmfSppContractTest {

    @Test
    fun parsesTheDocumentedExampleLine() {
        val readings = requireNotNull(EmfSppContract.parseLine("WIRE,44,12,44,20"))

        assertEquals(12, readings.fieldPercent)
        assertEquals(44, readings.opampPercent)
        assertEquals(20, readings.jitterPercent)
        assertEquals(44, readings.signalPercent)
        assertEquals(EmfAlertLevel.WIRE, readings.alertLevel)
    }

    @Test
    fun carriesTheStatusWordFromTheSensor() {
        assertEquals(EmfAlertLevel.CLEAR, parse("CLEAR,0,0,0,0").alertLevel)
        assertEquals(EmfAlertLevel.WIRE, parse("WIRE,44,12,44,20").alertLevel)
        assertEquals(EmfAlertLevel.FAULT, parse("FAULT,70,70,88,71").alertLevel)
    }

    /** `println` sends CRLF, and the relay trims, but never assume it arrived clean. */
    @Test
    fun toleratesCarriageReturnAndSurroundingSpace() {
        assertEquals(EmfAlertLevel.WIRE, parse("WIRE,44,12,44,20\r").alertLevel)
        assertEquals(EmfAlertLevel.WIRE, parse("  WIRE,44,12,44,20  ").alertLevel)
        assertEquals(44, parse("WIRE,44,12,44,20\r").signalPercent)
    }

    @Test
    fun rejectsAnythingThatIsNotAReading() {
        // The relay's own startup banner, a partial line, and a status word
        // from a different sketch version must all be ignored.
        assertNull(EmfSppContract.parseLine("ESP32 ready. Pair with 'EMF_Wire_Detector'."))
        assertNull(EmfSppContract.parseLine("WIRE,44,12"))
        assertNull(EmfSppContract.parseLine("WIRE,44,12,44,20,extra"))
        assertNull(EmfSppContract.parseLine("NOISE,44,12,44,20"))
        assertNull(EmfSppContract.parseLine(""))
    }

    // ---- The debug format, which is what a debug-wired board sends ----

    @Test
    fun parsesTheSketchDebugLine() {
        // Exactly what EMF_Reader_8.ino writes to Serial, and what was
        // observed arriving over the air.
        val readings = requireNotNull(
            EmfSppContract.parseLine("hallPct=12 opampPct=44 jitterAvg=8 jitterPct=20  signal=44")
        )

        assertEquals(12, readings.fieldPercent)
        assertEquals(44, readings.opampPercent)
        assertEquals(20, readings.jitterPercent)
        assertEquals(44, readings.signalPercent)
        assertEquals(EmfAlertLevel.WIRE, readings.alertLevel)
    }

    @Test
    fun parsesTheAllZeroesLineSeenFromTheBoard() {
        val readings = requireNotNull(
            EmfSppContract.parseLine("hallPct=0 opampPct=0 jitterAvg=0 jitterPct=0  signal=0")
        )

        assertEquals(0, readings.fieldPercent)
        assertEquals(0, readings.opampPercent)
        assertEquals(0, readings.jitterPercent)
        assertEquals(EmfAlertLevel.CLEAR, readings.alertLevel)
    }

    /** `jitterAvg` is a raw count, not a percentage, so a large value is normal. */
    @Test
    fun ignoresTheRawJitterAverage() {
        val readings = requireNotNull(
            EmfSppContract.parseLine("hallPct=5 opampPct=4 jitterAvg=612 jitterPct=9 signal=5")
        )

        assertEquals(9, readings.jitterPercent)
        assertEquals(EmfAlertLevel.CLEAR, readings.alertLevel)
    }

    @Test
    fun recomputesSignalWhenTheLineOmitsIt() {
        val readings = requireNotNull(
            EmfSppContract.parseLine("hallPct=30 opampPct=62 jitterPct=10")
        )

        assertEquals(62, readings.signalPercent)
    }

    // ---- v3 sketch (EMF_Wire_Fault_Detector_v3.ino, switch-type Hall) ----

    @Test
    fun parsesTheV3DebugLine() {
        // Exactly what v3 writes to Serial: binary magnet + raw p2p, no signal.
        val readings = requireNotNull(
            EmfSppContract.parseLine("p2p=41 opampPct=44 jitterAvg=8 jitterPct=20 magnet=0")
        )

        assertEquals(0, readings.fieldPercent)
        assertEquals(44, readings.opampPercent)
        assertEquals(20, readings.jitterPercent)
        assertEquals(44, readings.signalPercent)
        assertEquals(EmfAlertLevel.WIRE, readings.alertLevel)
    }

    @Test
    fun v3MagnetNearMapsToFullFieldButSignalFollowsOpampOnly() {
        // v3 classifies on op-amp only, so magnet=1 with quiet op-amp stays CLEAR.
        val readings = requireNotNull(
            EmfSppContract.parseLine("p2p=12 opampPct=0 jitterAvg=3 jitterPct=5 magnet=1")
        )

        assertEquals(100, readings.fieldPercent)
        assertEquals(0, readings.signalPercent)
        assertEquals(EmfAlertLevel.CLEAR, readings.alertLevel)
    }

    @Test
    fun v3StatusLineWithBinaryFieldParses() {
        val readings = parse("WIRE,44,0,44,20")
        assertEquals(0, readings.fieldPercent)
        assertEquals(44, readings.signalPercent)
        assertEquals(EmfAlertLevel.WIRE, readings.alertLevel)
    }

    // ---- v11 sketch (EMF_Wire_Fault_Detector_v11.ino, QMC5883P field) ----

    @Test
    fun parsesTheV11StatusLine() {
        // Exactly what v11 sendStatusToEsp writes: STATUS,signal,fieldPct,opampPct,jitterPct.
        val readings = requireNotNull(EmfSppContract.parseLine("WIRE,44,0,44,20"))

        assertEquals(0, readings.fieldPercent)
        assertEquals(44, readings.opampPercent)
        assertEquals(20, readings.jitterPercent)
        assertEquals(44, readings.signalPercent)
        assertEquals(EmfAlertLevel.WIRE, readings.alertLevel)
    }

    @Test
    fun parsesTheV11DebugLine() {
        // Exactly what v11 writes to Serial: percentages plus raw diagnostics.
        // No signal=, so signal recomputes as max(field, opamp).
        val readings = requireNotNull(
            EmfSppContract.parseLine(
                "p2p=41 opampPct=44 field=12 mag=12345 xyz=100,200,300 " +
                    "fails=0 same=0 reinit=0 fieldPct=12 jitterPct=20"
            )
        )

        assertEquals(12, readings.fieldPercent)
        assertEquals(44, readings.opampPercent)
        assertEquals(20, readings.jitterPercent)
        assertEquals(44, readings.signalPercent)
        assertEquals(EmfAlertLevel.WIRE, readings.alertLevel)
    }

    @Test
    fun v11DebugLineWithCommasStillRoutesToDebugParser() {
        // The xyz=x,y,z token embeds commas; routing is by `=`, so this must
        // decode as a debug line rather than fail as a status line.
        val readings = requireNotNull(
            EmfSppContract.parseLine(
                "p2p=12 opampPct=0 field=5 mag=9999 xyz=-1,-2,-3 " +
                    "fails=0 same=0 reinit=0 fieldPct=5 jitterPct=5"
            )
        )

        assertEquals(5, readings.fieldPercent)
        assertEquals(5, readings.signalPercent)
        assertEquals(EmfAlertLevel.CLEAR, readings.alertLevel)
    }

    @Test
    fun v11RawFieldCountsAreNotPercentages() {
        // field= is a raw count; without fieldPct= there is no field reading.
        assertNull(
            EmfSppContract.parseLine(
                "p2p=41 opampPct=44 field=12 mag=12345 xyz=1,2,3 " +
                    "fails=0 same=0 reinit=0 jitterPct=20"
            )
        )
    }

    @Test
    fun v11FieldPctWinsOverLegacyHallPct() {
        val readings = requireNotNull(
            EmfSppContract.parseLine("hallPct=9 fieldPct=31 opampPct=10 jitterPct=4 signal=31")
        )

        assertEquals(31, readings.fieldPercent)
    }

    @Test
    fun rejectsInvalidMagnetValues() {
        assertNull(EmfSppContract.parseLine("p2p=41 opampPct=44 jitterPct=20 magnet=2"))
        assertNull(EmfSppContract.parseLine("p2p=41 opampPct=44 jitterPct=20 magnet=x"))
        assertNull(EmfSppContract.parseLine("p2p=41 opampPct=44 jitterPct=20"))
    }

    @Test
    fun rejectsTheRawAdcTraceAndPartialDebugLines() {
        // The sketch's raw trace shares the link but carries no percentages.
        assertNull(EmfSppContract.parseLine("RAW hallPin=1005"))
        assertNull(EmfSppContract.parseLine("RAW opampRaw=1023"))
        assertNull(EmfSppContract.parseLine("hallPct=12 opampPct=44"))
        assertNull(EmfSppContract.parseLine("hallPct=aa opampPct=44 jitterPct=20 signal=44"))
    }

    /** The two formats must never be confused for one another. */
    @Test
    fun theFormatsAreDisambiguatedByPunctuation() {
        assertEquals(EmfAlertLevel.WIRE, parse("WIRE,44,12,44,20").alertLevel)
        assertEquals(
            EmfAlertLevel.CLEAR,
            parse("hallPct=0 opampPct=0 jitterAvg=0 jitterPct=0  signal=0").alertLevel
        )
    }

    @Test
    fun rejectsNonNumericOrOutOfRangeFields() {
        assertNull(EmfSppContract.parseLine("WIRE,aa,12,44,20"))
        assertNull(EmfSppContract.parseLine("WIRE,,12,44,20"))
        // The sketch clamps to 0-100, so anything outside is corruption.
        assertNull(EmfSppContract.parseLine("WIRE,44,101,44,20"))
        assertNull(EmfSppContract.parseLine("WIRE,-1,12,44,20"))
    }

    @Test
    fun acceptsTheBoundariesOfTheClampedRange() {
        assertEquals(0, parse("CLEAR,0,0,0,0").fieldPercent)
        assertEquals(100, parse("FAULT,100,100,100,100").jitterPercent)
    }

    /**
     * The wire is authoritative for classification, so the app must not
     * re-derive the alert level and overrule it.
     */
    @Test
    fun theSensorsVerdictWins() {
        // Field 12 is below the 15% clear threshold, but the sketch sent WIRE
        // because it classified on its own signal value.
        val readings = parse("WIRE,44,12,44,20")
        assertEquals(EmfAlertLevel.WIRE, readings.alertLevel)
        assertEquals(EmfAlertLevel.CLEAR, EmfReadings.classify(12, 20))
    }

    /** The fallback used when a sample is built without a parsed line. */
    @Test
    fun fallbackClassificationMatchesTheSketchRules() {
        assertEquals(EmfAlertLevel.CLEAR, EmfReadings.classify(14, 49))
        assertEquals(EmfAlertLevel.WIRE, EmfReadings.classify(15, 49))
        assertEquals(EmfAlertLevel.FAULT, EmfReadings.classify(15, 50))
    }

    @Test
    fun defaultsSignalToTheStrongerChannel() {
        val readings = EmfReadings(fieldPercent = 30, opampPercent = 62, jitterPercent = 10)
        assertEquals(62, readings.signalPercent)
    }

    private fun parse(line: String): EmfReadings = requireNotNull(EmfSppContract.parseLine(line))
}