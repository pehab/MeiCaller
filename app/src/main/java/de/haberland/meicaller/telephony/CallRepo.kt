package de.haberland.meicaller.telephony

import android.telecom.Call
import android.telecom.InCallService
import java.lang.ref.WeakReference

object CallRepo {
    @Volatile private var callRef: WeakReference<Call>? = null
    @Volatile private var serviceRef: WeakReference<InCallService>? = null

    fun setService(service: InCallService) {
        serviceRef = WeakReference(service)
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
}

