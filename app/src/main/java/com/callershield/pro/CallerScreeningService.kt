package com.callershield.pro

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService

class CallerScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val handle = details.handle
        val number = handle?.schemeSpecificPart.orEmpty()
        val prefs = getSharedPreferences("caller_shield", MODE_PRIVATE)

        val isIncoming = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            details.callDirection == Call.Details.DIRECTION_INCOMING
        val blockSpam = isIncoming &&
            prefs.getBoolean("block_spam", true) &&
            number.isNotBlank() &&
            SpamStore.contains(this, number)

        val blockPrivate = isIncoming &&
            prefs.getBoolean("block_private", false) &&
            number.isBlank()

        val blockUnknown = isIncoming &&
            prefs.getBoolean("block_unknown", false) &&
            number.isNotBlank() &&
            !SpamStore.isInContacts(this, number)

        val shouldBlock = blockSpam || blockPrivate || blockUnknown
        val response = CallResponse.Builder()
            .setDisallowCall(shouldBlock)
            .setRejectCall(shouldBlock)
            .setSkipCallLog(false)
            .setSkipNotification(shouldBlock)
            .build()

        respondToCall(details, response)
    }
}
