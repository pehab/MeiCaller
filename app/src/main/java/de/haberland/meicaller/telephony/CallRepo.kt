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
 * A thread-safe singleton repository to hold and manage the global call state.
 * This object centralizes the active `Call` and `InCallService` instances,
 * using WeakReferences to prevent memory leaks.
 * It provides reactive state flows for UI components to observe call status (e.g., mute, audio route).
 */
object CallRepo {
    @Volatile private var callRef: WeakReference<Call>? = null
    @Volatile private var serviceRef: WeakReference<InCallService>? = null

    // ---- Public UI state ----

    /** Flow representing the current mute state of the call. */
    private val mutableIsMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = mutableIsMuted.asStateFlow()

    /** Flow representing the currently active audio endpoint (e.g., Speaker, Earpiece). API 34+ only. */
    private val mutableCurrentEndpoint = MutableStateFlow<CallEndpoint?>(null)
    val currentEndpoint: StateFlow<CallEndpoint?> = mutableCurrentEndpoint.asStateFlow()

    private val mutableAvailableEndpoints = MutableStateFlow<List<CallEndpoint>>(emptyList())

    /** Flow representing the legacy audio route. Used on API < 34. */
    private val mutableLegacyAudioRoute = MutableStateFlow(CallAudioState.ROUTE_EARPIECE)
    val legacyAudioRoute: StateFlow<Int> = mutableLegacyAudioRoute.asStateFlow()

    // ---- Lifecycle Management ----

    /** Sets the active InCallService instance and refreshes the audio state. */
    fun setService(service: InCallService) {
        serviceRef = WeakReference(service)
        refreshFromService(service)
    }

    /** Clears the service reference if it matches the provided instance. */
    fun clearService(service: InCallService) {
        val current = serviceRef?.get()
        if (current === service) serviceRef = null
    }

    /** Sets the active call instance. */
    fun setCall(call: Call) {
        callRef = WeakReference(call)
    }

    /** Clears the call reference if it matches the provided instance. */
    fun clearIfSame(call: Call) {
        val current = callRef?.get()
        if (current === call) callRef = null
    }

    /** Returns the current Call instance, or null if not available. */
    fun call(): Call? = callRef?.get()

    /** Returns the current InCallService instance, or null if not available. */
    fun service(): InCallService? = serviceRef?.get()

    // ---- Service -> Repo Updates (called from MyInCallService) ----

    /** Called by the service when the mute state changes. */
    fun onMuteStateChanged(isMuted: Boolean) {
        mutableIsMuted.value = isMuted
    }

    /** Called by the service when the current audio endpoint changes (API 34+). */
    fun onCurrentEndpointChanged(endpoint: CallEndpoint?) {
        mutableCurrentEndpoint.value = endpoint
    }

    /** Called by the service when the list of available endpoints changes (API 34+). */
    fun onAvailableEndpointsChanged(endpoints: List<CallEndpoint>) {
        mutableAvailableEndpoints.value = endpoints
    }

    /**
     * Refreshes the initial audio state from the service.
     * This uses the modern CallEndpoint API on 34+ and falls back to the deprecated CallAudioState on older versions.
     */
    fun refreshFromService(service: InCallService) {
        if (Build.VERSION.SDK_INT >= 34) {
            mutableCurrentEndpoint.value = service.currentCallEndpoint
            // available endpoints come ONLY via onAvailableCallEndpointsChanged callback
        } else {
            @Suppress("DEPRECATION")
            val cas = service.callAudioState
            mutableIsMuted.value = cas?.isMuted == true
            mutableLegacyAudioRoute.value = cas?.route ?: CallAudioState.ROUTE_EARPIECE
        }
    }

    // ---- UI Commands ----

    /** Sets the mute state on the InCallService. */
    fun setMuted(
        service: InCallService,
        muted: Boolean,
    ) {
        service.setMuted(muted)
        mutableIsMuted.value = muted
    }

    /** Toggles the speakerphone on or off, handling API level differences. */
    fun toggleSpeaker(
        context: Context,
        service: InCallService,
    ) {
        if (Build.VERSION.SDK_INT >= 34) {
            toggleSpeakerApi34(context, service)
        } else {
            toggleSpeakerLegacy(service)
        }
    }

    /** Toggles speakerphone using the modern CallEndpoint API (34+). */
    @androidx.annotation.RequiresApi(34)
    private fun toggleSpeakerApi34(
        context: Context,
        service: InCallService,
    ) {
        val current = mutableCurrentEndpoint.value ?: service.currentCallEndpoint
        val available = mutableAvailableEndpoints.value
        if (available.isEmpty()) return

        val targetType = if (current.endpointType == CallEndpoint.TYPE_SPEAKER) {
            CallEndpoint.TYPE_EARPIECE
        } else {
            CallEndpoint.TYPE_SPEAKER
        }
        val target = available.firstOrNull { it.endpointType == targetType } ?: return

        val executor = ContextCompat.getMainExecutor(context)

        service.requestCallEndpointChange(
            target,
            executor,
            object : OutcomeReceiver<Void, CallEndpointException> {
                override fun onResult(result: Void?) {
                    refreshFromService(service)
                }

                override fun onError(error: CallEndpointException) {
                    // ignore (optional: log)
                }
            },
        )
    }

    /** Toggles speakerphone using the legacy setAudioRoute API (< 34). */
    private fun toggleSpeakerLegacy(service: InCallService) {
        val current = mutableLegacyAudioRoute.value
        val newRoute =
            if (current == CallAudioState.ROUTE_SPEAKER) {
                CallAudioState.ROUTE_EARPIECE
            } else {
                CallAudioState.ROUTE_SPEAKER
            }

        @Suppress("DEPRECATION")
        service.setAudioRoute(newRoute)

        mutableLegacyAudioRoute.value = newRoute
    }
}
