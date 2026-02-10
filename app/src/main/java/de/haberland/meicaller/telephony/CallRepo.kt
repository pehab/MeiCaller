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

object CallRepo {
    @Volatile private var callRef: WeakReference<Call>? = null

    @Volatile private var serviceRef: WeakReference<InCallService>? = null

    // ---- Public UI state ----
    private val mutableIsMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = mutableIsMuted.asStateFlow()

    private val mutableCurrentEndpoint = MutableStateFlow<CallEndpoint?>(null)
    val currentEndpoint: StateFlow<CallEndpoint?> = mutableCurrentEndpoint.asStateFlow()

    private val mutableAvailableEndpoints = MutableStateFlow<List<CallEndpoint>>(emptyList())

    // Legacy fallback for < 34 (UI uses this only for "speaker?" boolean)
    private val mutableLegacyAudioRoute = MutableStateFlow(CallAudioState.ROUTE_EARPIECE)
    val legacyAudioRoute: StateFlow<Int> = mutableLegacyAudioRoute.asStateFlow()

    // ---- Existing refs ----
    fun setService(service: InCallService) {
        serviceRef = WeakReference(service)
        refreshFromService(service)
    }

    fun clearService(service: InCallService) {
        val current = serviceRef?.get()
        if (current === service) serviceRef = null
    }

    fun setCall(call: Call) {
        callRef = WeakReference(call)
    }

    fun clearIfSame(call: Call) {
        val current = callRef?.get()
        if (current === call) callRef = null
    }

    fun call(): Call? = callRef?.get()

    fun service(): InCallService? = serviceRef?.get()

    // ---- Service -> Repo updates (call these from MyInCallService overrides) ----
    fun onMuteStateChanged(isMuted: Boolean) {
        mutableIsMuted.value = isMuted
    }

    fun onCurrentEndpointChanged(endpoint: CallEndpoint?) {
        mutableCurrentEndpoint.value = endpoint
    }

    fun onAvailableEndpointsChanged(endpoints: List<CallEndpoint>) {
        mutableAvailableEndpoints.value = endpoints
    }

    /**
     * Best-effort refresh of initial audio state after service is set.
     * API 34+: we can read current endpoint, but NOT the available endpoints (those come via callback).
     * <34: use deprecated callAudioState as fallback.
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

    // ---- Commands used by UI ----
    fun setMuted(
        service: InCallService,
        muted: Boolean,
    ) {
        service.setMuted(muted)
        mutableIsMuted.value = muted
    }

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

    @androidx.annotation.RequiresApi(34)
    private fun toggleSpeakerApi34(
        context: Context,
        service: InCallService,
    ) {
        val current = mutableCurrentEndpoint.value ?: service.currentCallEndpoint
        val available = mutableAvailableEndpoints.value
        if (available.isEmpty()) return

        val wantSpeaker = current.endpointType != CallEndpoint.TYPE_SPEAKER
        val targetType = if (wantSpeaker) CallEndpoint.TYPE_SPEAKER else CallEndpoint.TYPE_EARPIECE
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
