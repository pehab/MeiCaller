package de.haberland.meicaller.telephony

import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.Call
import android.telecom.CallEndpoint
import android.telecom.InCallService
import de.haberland.meicaller.ui.InCallActivity

class MyInCallService : InCallService() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()
        CallRepo.setService(this)

        vibrator =
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    }

    override fun onDestroy() {
        stopRinging()
        CallRepo.clearService(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallRepo.setCall(call)
        CallRepo.refreshFromService(this)

        // Klingeln starten, wenn Call bereits "ringing" ist
        @Suppress("DEPRECATION") // call.state is flagged deprecated in some Java stubs; safe to use here
        if (call.state == Call.STATE_RINGING) {
            startRinging()
        }

        // State-Callback: Klingeln stoppen sobald nicht mehr ringing
        call.registerCallback(
            object : Call.Callback() {
                override fun onStateChanged(call: Call, state: Int) {
                    if (state != Call.STATE_RINGING) stopRinging()
                }
            },
        )

        // Call UI starten
        val i =
            Intent(this, InCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        startActivity(i)
    }

    override fun onCallRemoved(call: Call) {
        stopRinging()
        CallRepo.clearIfSame(call)
        super.onCallRemoved(call)
    }

    // ---- Modern audio callbacks (API 34+) ----
    override fun onMuteStateChanged(isMuted: Boolean) {
        super.onMuteStateChanged(isMuted)
        CallRepo.onMuteStateChanged(isMuted)
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        super.onCallEndpointChanged(callEndpoint)
        CallRepo.onCurrentEndpointChanged(callEndpoint)
    }

    override fun onAvailableCallEndpointsChanged(availableCallEndpoints: MutableList<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(availableCallEndpoints)
        CallRepo.onAvailableEndpointsChanged(availableCallEndpoints)
    }

    private fun startRinging() {
        stopRinging()

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val rt = RingtoneManager.getRingtone(this, uri)
        ringtone = rt

        try {
            rt.audioAttributes =
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
        } catch (_: Throwable) {
        }

        try {
            rt.play()
        } catch (_: Throwable) {
        }

        val v = vibrator
        if (v != null && v.hasVibrator()) {
            try {
                val effect =
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 900, 700),
                        0,
                    )
                v.vibrate(effect)
            } catch (_: Throwable) {
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
