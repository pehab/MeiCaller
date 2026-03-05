package de.haberland.meicaller.telephony

import android.content.Context
import android.os.Build
import android.os.OutcomeReceiver
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.InCallService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * A thread-safe singleton repository to hold and manage global call states.
 * Supports multiple active calls (e.g., for Call Waiting).
 */
object CallRepo {
    private val mutableCalls = MutableStateFlow<List<Call>>(emptyList())
    val calls: StateFlow<List<Call>> = mutableCalls.asStateFlow()

    @Volatile private var serviceRef: WeakReference<InCallService>? = null

    // ---- Public UI state ----

    private val mutableIsMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = mutableIsMuted.asStateFlow()

    private val mutableCurrentEndpoint = MutableStateFlow<CallEndpoint?>(null)
    val currentEndpoint: StateFlow<CallEndpoint?> = mutableCurrentEndpoint.asStateFlow()

    private val mutableAvailableEndpoints = MutableStateFlow<List<CallEndpoint>>(emptyList())

    private val mutableLegacyAudioRoute = MutableStateFlow(CallAudioState.ROUTE_EARPIECE)
    val legacyAudioRoute: StateFlow<Int> = mutableLegacyAudioRoute.asStateFlow()

    // ---- Lifecycle Management ----

    fun setService(service: InCallService) {
        serviceRef = WeakReference(service)
        refreshFromService(service)
    }

    fun clearService(service: InCallService) {
        if (serviceRef?.get() === service) serviceRef = null
    }

    /** Adds or updates a call in the list. */
    fun addOrUpdateCall(call: Call) {
        val currentList = mutableCalls.value.toMutableList()
        if (!currentList.contains(call)) {
            currentList.add(call)
            mutableCalls.value = currentList
        }
    }

    /** Removes a call from the list. */
    fun removeCall(call: Call) {
        val currentList = mutableCalls.value.toMutableList()
        if (currentList.remove(call)) {
            mutableCalls.value = currentList
        }
    }

    /** Helper to get the "primary" call (usually the active one or the newest incoming one). */
    fun getPrimaryCall(): Call? {
        val list = mutableCalls.value
        if (list.isEmpty()) return null
        
        // Prefer ringing call if exists, else active, else first in list
        return list.find { getCallState(it) == Call.STATE_RINGING }
            ?: list.find { getCallState(it) == Call.STATE_ACTIVE }
            ?: list.firstOrNull()
    }

    /** 
     * Helper to get the state of a call. 
     * it.state is deprecated in favor of it.details.state.
     */
    private fun getCallState(call: Call): Int {
        return call.details?.state ?: Call.STATE_NEW
    }

    fun service(): InCallService? = serviceRef?.get()

    // ---- Service -> Repo Updates ----

    fun onMuteStateChanged(isMuted: Boolean) {
        mutableIsMuted.value = isMuted
    }

    fun onCurrentEndpointChanged(endpoint: CallEndpoint?) {
        mutableCurrentEndpoint.value = endpoint
    }

    fun onAvailableEndpointsChanged(endpoints: List<CallEndpoint>) {
        mutableAvailableEndpoints.value = endpoints
    }

    fun refreshFromService(service: InCallService) {
        // Sync the call list from the service's internal list
        mutableCalls.value = service.calls ?: emptyList()

        if (Build.VERSION.SDK_INT >= 34) {
            mutableCurrentEndpoint.value = service.currentCallEndpoint
        } else {
            @Suppress("DEPRECATION")
            val cas = service.callAudioState
            mutableIsMuted.value = cas?.isMuted == true
            mutableLegacyAudioRoute.value = cas?.route ?: CallAudioState.ROUTE_EARPIECE
        }
    }

    // ---- UI Commands ----

    fun setMuted(service: InCallService, muted: Boolean) {
        service.setMuted(muted)
        mutableIsMuted.value = muted
    }

    fun toggleSpeaker(context: Context, service: InCallService) {
        if (Build.VERSION.SDK_INT >= 34) {
            toggleSpeakerApi34(context, service)
        } else {
            toggleSpeakerLegacy(service)
        }
    }

    @androidx.annotation.RequiresApi(34)
    private fun toggleSpeakerApi34(context: Context, service: InCallService) {
        val current = mutableCurrentEndpoint.value ?: service.currentCallEndpoint
        val available = mutableAvailableEndpoints.value
        if (available.isEmpty()) return

        val targetType = if (current.endpointType == CallEndpoint.TYPE_SPEAKER) {
            CallEndpoint.TYPE_EARPIECE
        } else {
            CallEndpoint.TYPE_SPEAKER
        }
        val target = available.firstOrNull { it.endpointType == targetType } ?: return

        service.requestCallEndpointChange(
            target,
            ContextCompat.getMainExecutor(context),
            object : OutcomeReceiver<Void, CallEndpointException> {
                override fun onResult(result: Void?) { refreshFromService(service) }
                override fun onError(error: CallEndpointException) {}
            },
        )
    }

    private fun toggleSpeakerLegacy(service: InCallService) {
        @Suppress("DEPRECATION")
        val current = service.callAudioState?.route ?: CallAudioState.ROUTE_EARPIECE
        val newRoute = if (current == CallAudioState.ROUTE_SPEAKER) {
            CallAudioState.ROUTE_EARPIECE
        } else {
            CallAudioState.ROUTE_SPEAKER
        }
        @Suppress("DEPRECATION")
        service.setAudioRoute(newRoute)
        mutableLegacyAudioRoute.value = newRoute
    }
}
