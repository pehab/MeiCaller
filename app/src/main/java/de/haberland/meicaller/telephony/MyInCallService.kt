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
        CallRepo.setService(this)
    }

    override fun onDestroy() {
        CallRepo.clearService(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        // Store the new call in the repository
        CallRepo.addOrUpdateCall(call)
        
        // Refresh the whole state to be sure we have everything
        CallRepo.refreshFromService(this)

        // Launch the UI. If it's already running, the new intent will reach it.
        val i = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(i)
    }

    override fun onCallRemoved(call: Call) {
        CallRepo.removeCall(call)
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
