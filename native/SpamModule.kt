package com.spamshield

import android.app.role.RoleManager
import android.content.Context
import com.facebook.react.bridge.*

class SpamModule(ctx: ReactApplicationContext) : ReactContextBaseJavaModule(ctx) {
    override fun getName() = "SpamShield"
    private val p get() = reactApplicationContext.getSharedPreferences("spamshield", Context.MODE_PRIVATE)
    private fun set() = (p.getStringSet("blocklist", emptySet()) ?: emptySet()).toMutableSet()

    @ReactMethod fun requestScreeningRole(pr: Promise) {
        val rm = reactApplicationContext.getSystemService(RoleManager::class.java)
        val a = reactApplicationContext.currentActivity
        if (a == null || !rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) { pr.resolve(false); return }
        a.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 4242)
        pr.resolve(true)
    }
    @ReactMethod fun isScreeningActive(pr: Promise) {
        val rm = reactApplicationContext.getSystemService(RoleManager::class.java)
        pr.resolve(rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING))
    }
    @ReactMethod fun getBlocklist(pr: Promise) { pr.resolve(Arguments.fromList(set().sorted())) }
    @ReactMethod fun addBlocked(n: String, pr: Promise) { p.edit().putStringSet("blocklist", set().apply { add(n) }).apply(); pr.resolve(true) }
    @ReactMethod fun removeBlocked(n: String, pr: Promise) { p.edit().putStringSet("blocklist", set().apply { remove(n) }).apply(); pr.resolve(true) }
    @ReactMethod fun setBlockUnknown(v: Boolean, pr: Promise) { p.edit().putBoolean("blockUnknown", v).apply(); pr.resolve(true) }
    @ReactMethod fun getBlockUnknown(pr: Promise) { pr.resolve(p.getBoolean("blockUnknown", false)) }
    @ReactMethod fun clearLog(pr: Promise) { p.edit().remove("log").apply(); pr.resolve(true) }
    @ReactMethod fun getLog(pr: Promise) {
        pr.resolve(Arguments.fromList((p.getStringSet("log", emptySet()) ?: emptySet()).sortedDescending()))
    }
}
