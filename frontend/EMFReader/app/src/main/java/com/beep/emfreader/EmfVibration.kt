package com.beep.emfreader

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object EmfVibration {

    private const val FAULT_BUZZ_MILLIS = 400L

    fun buzz(context: Context) {
        if (isSilenced(context)) return
        val vibrator = vibrator(context)?.takeIf { it.hasVibrator() } ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(FAULT_BUZZ_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(FAULT_BUZZ_MILLIS)
        }
    }

    private fun vibrator(context: Context): Vibrator? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
            context.getSystemService(Vibrator::class.java)

        else -> {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun isSilenced(context: Context): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return audio.ringerMode == AudioManager.RINGER_MODE_SILENT
    }
}
