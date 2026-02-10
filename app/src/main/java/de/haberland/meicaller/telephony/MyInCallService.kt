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

/**
 * Service that handles the lifecycle of phone calls.
 * This service is bound by the system when a call is active.
 * it manages the ringing state and launches the InCall UI.
 */
class MyInCallService : InCallService() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()
        // Register this service instance with the repository
        CallRepo.setService(this)

        // Initialize vibrator for incoming calls
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
        // Store the new call in the repository
        CallRepo.setCall(call)
        CallRepo.refreshFromService(this)

        // Start ringing if the call is currently in the ringing state
        @Suppress("DEPRECATION")
        if (call.state == Call.STATE_RINGING) {
            startRinging()
        }

        // Register a callback to stop ringing once the call state changes
        call.registerCallback(
            object : Call.Callback() {
                override fun onStateChanged(
                    call: Call,
                    state: Int,
                ) {
                    if (state != Call.STATE_RINGING) stopRinging()
                }
            },
        )

        // Automatically launch the in-call UI activity
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

    /**
     * Plays the default system ringtone and starts vibration.
     */
    private fun startRinging() {
        stopRinging()

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val rt = RingtoneManager.getRingtone(this, uri)
        ringtone = rt

        try {
            rt.audioAttributes =
                AudioAttributes
                    .Builder()
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
                // Repeating vibration pattern: 0ms delay, 900ms on, 700ms off
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

    /**
     * Stops any active ringtone or vibration.
     */
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
