package com.spamshield

import android.telecom.Call
import android.telecom.CallScreeningService

class SpamScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val num = details.handle?.schemeSpecificPart ?: ""
        val p = getSharedPreferences("spamshield", MODE_PRIVATE)
        val list = p.getStringSet("blocklist", emptySet()) ?: emptySet()
        val spam = (num.isEmpty() && p.getBoolean("blockUnknown", false)) || (num.isNotEmpty() && list.any {
            if (it.endsWith("*")) num.startsWith(it.dropLast(1)) else num.endsWith(it.takeLast(10))
        })
        val r = CallResponse.Builder()
        if (spam) {
            r.setDisallowCall(true).setRejectCall(true).setSkipNotification(true)
            val log = (p.getStringSet("log", emptySet()) ?: emptySet()).toMutableSet()
            log.add("${System.currentTimeMillis()}|$num")
            p.edit().putStringSet("log", log).apply()
        }
        respondToCall(details, r.build())
    }
}
