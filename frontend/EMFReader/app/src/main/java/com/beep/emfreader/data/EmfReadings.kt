package com.beep.emfreader.data

/**
 * One sample from the sensor.
 *
 * [signalPercent] and [alertLevel] are carried on the wire by the Arduino,
 * which owns the classification. They default to the same rules the sketch
 * uses so that previews and unit tests can build a sample from just the three
 * channel values, but a parsed reading always takes both from the line.
 *
 * Mirrors the classification in EMF_Wire_Fault_Detector_v11.ino: the headline
 * signal is the stronger of the two sensing paths (QMC5883P field strength
 * vs. op-amp antenna), and jitter is what separates a steady signal from an
 * erratic one.
 */
enum class EmfAlertLevel { CLEAR, WIRE, FAULT }

const val LOW_THRESHOLD = 15
const val FAULT_THRESHOLD = 50

data class EmfReadings(
    val fieldPercent: Int = 0,
    val opampPercent: Int = 0,
    val jitterPercent: Int = 0,
    /** `max(field, opamp)` on the sketch. Sent explicitly by the sensor. */
    val signalPercent: Int = maxOf(fieldPercent, opampPercent),
    val alertLevel: EmfAlertLevel = classify(signalPercent, jitterPercent)
) {
    companion object {
        /** The sketch's decision, kept for defaults only. Parsed lines bring their own. */
        fun classify(signalPercent: Int, jitterPercent: Int): EmfAlertLevel = when {
            signalPercent < LOW_THRESHOLD -> EmfAlertLevel.CLEAR
            jitterPercent >= FAULT_THRESHOLD -> EmfAlertLevel.FAULT
            else -> EmfAlertLevel.WIRE
        }
    }
}