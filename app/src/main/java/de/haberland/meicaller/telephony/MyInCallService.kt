package de.haberland.meicaller.telephony

import android.content.Intent
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

    override fun onCreate() {
        super.onCreate()
        // Register this service instance with the repository
        CallRepo.setService(this)
    }

    override fun onDestroy() {
        CallRepo.clearService(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        // Store the new call in the repository
        CallRepo.setCall(call)
        CallRepo.refreshFromService(this)

        // Automatically launch the in-call UI activity. The system will handle ringing.
        val i = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(i)
    }

    override fun onCallRemoved(call: Call) {
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
}
