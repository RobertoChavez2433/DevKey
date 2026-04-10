package dev.devkey.keyboard.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import dev.devkey.keyboard.Keyboard
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.ui.keyboard.KeyCodes
import kotlin.math.min
import kotlin.math.pow

internal class FeedbackManager(
    private val context: Context,
    private val vibrator: Vibrator?,
    private val settings: SettingsRepository
) {
    private var audioManager: AudioManager? = null
    private var silentMode = false
    var vibrateOn = false
    var vibrateLen = 0
    var soundOn = false

    val ringerModeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateRingerMode()
        }
    }

    fun updateRingerMode() {
        if (audioManager == null) {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }
        audioManager?.let {
            silentMode = it.ringerMode != AudioManager.RINGER_MODE_NORMAL
        }
    }

    fun playKeyClick(primaryCode: Int) {
        if (audioManager == null) updateRingerMode()
        if (soundOn && !silentMode) {
            val sound = when (primaryCode) {
                Keyboard.KEYCODE_DELETE -> AudioManager.FX_KEYPRESS_DELETE
                KeyCodes.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                KeyCodes.ASCII_SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audioManager!!.playSoundEffect(sound, getKeyClickVolume())
        }
    }

    fun vibrate() {
        if (!vibrateOn) return
        vibrate(vibrateLen)
    }

    fun vibrate(len: Int) {
        vibrator?.vibrate(VibrationEffect.createOneShot(len.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun getKeyClickVolume(): Float {
        if (audioManager == null) return 0.0f
        val method = settings.keyClickMethod
        if (method == 0) return FX_VOLUME
        var targetVol = settings.keyClickVolume
        if (method > 1) {
            val mediaMax = audioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val mediaVol = audioManager!!.getStreamVolume(AudioManager.STREAM_MUSIC)
            val channelVol = mediaVol.toFloat() / mediaMax
            when (method) {
                2 -> targetVol *= channelVol
                3 -> {
                    if (channelVol == 0f) return 0.0f
                    targetVol = min(targetVol / channelVol, 1.0f)
                }
            }
        }
        return 10.0f.pow(FX_VOLUME_RANGE_DB * (targetVol - 1) / 20)
    }

    companion object {
        private const val FX_VOLUME = -1.0f
        private const val FX_VOLUME_RANGE_DB = 72.0f
    }
}
