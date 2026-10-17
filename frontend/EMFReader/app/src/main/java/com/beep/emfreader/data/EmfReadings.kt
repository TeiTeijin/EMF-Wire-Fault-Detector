package com.beep.emfreader.data

enum class EmfAlertLevel { CLEAR, WIRE, FAULT }

const val LOW_THRESHOLD = 15
const val FAULT_THRESHOLD = 50

data class EmfReadings(
    val fieldPercent: Int = 0,
    val opampPercent: Int = 0,
    val jitterPercent: Int = 0,
    val signalPercent: Int = maxOf(fieldPercent, opampPercent),
    val alertLevel: EmfAlertLevel = classify(signalPercent, jitterPercent)
) {
    companion object {
        fun classify(signalPercent: Int, jitterPercent: Int): EmfAlertLevel = when {
            signalPercent < LOW_THRESHOLD -> EmfAlertLevel.CLEAR
            jitterPercent >= FAULT_THRESHOLD -> EmfAlertLevel.FAULT
            else -> EmfAlertLevel.WIRE
        }
    }
}
