package de.haberland.meicaller.telephony

import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.Call
import android.telecom.InCallService
import de.haberland.meicaller.ui.InCallActivity

class MyInCallService : InCallService() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()
        CallRepo.setService(this)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31): VibratorManager ist der saubere Weg
            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onDestroy() {
        stopRinging()
        CallRepo.clearService(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallRepo.setCall(call)

        // Klingeln starten, wenn Call bereits "ringing" ist
        if (call.state == Call.STATE_RINGING) {
            startRinging()
        }

        // State-Callback: Klingeln stoppen sobald nicht mehr ringing
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state != Call.STATE_RINGING) {
                    stopRinging()
                }
            }
        })

        // Call UI starten
        val i = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(i)
    }

    override fun onCallRemoved(call: Call) {
        stopRinging()
        CallRepo.clearIfSame(call)
        super.onCallRemoved(call)
    }

    private fun startRinging() {
        // Falls aus irgendeinem Grund schon was läuft -> reset
        stopRinging()

        // 🔊 System-Ringtone (respektiert Lautlos/Vibration/Volume)
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val rt = RingtoneManager.getRingtone(this, uri)
        ringtone = rt

        try {
            rt.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        } catch (_: Throwable) {
            // Manche Geräte sind zickig, dann trotzdem versuchen zu spielen
        }

        try {
            rt.play()
        } catch (_: Throwable) {
            // ignore
        }

        // 📳 Vibration (nur wenn vorhanden)
        val v = vibrator
        if (v != null && v.hasVibrator()) {
            try {
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 900, 700), // on/off pattern
                    0 // repeat
                )
                v.vibrate(effect)
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    private fun stopRinging() {
        try {
            ringtone?.stop()
        } catch (_: Throwable) {
        }
        ringtone = null

        try {
            vibrator?.cancel()
        } catch (_: Throwable) {
        }
    }
}
