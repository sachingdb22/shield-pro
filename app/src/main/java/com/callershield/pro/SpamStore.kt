package com.callershield.pro

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.Log

object SpamStore {
    private const val PREFS = "caller_shield"
    private const val KEY_NUMBERS = "spam_numbers"

    fun add(context: Context, number: String) {
        val normalized = normalize(number)
        require(normalized.any { it.isDigit() }) { "A phone number must contain digits" }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val numbers = prefs.getStringSet(KEY_NUMBERS, emptySet())?.toMutableSet() ?: mutableSetOf()
        numbers.add(normalized)
        prefs.edit().putStringSet(KEY_NUMBERS, numbers).apply()
    }

    fun remove(context: Context, number: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val numbers = prefs.getStringSet(KEY_NUMBERS, emptySet())?.toMutableSet() ?: return
        numbers.removeAll { PhoneNumberUtils.compare(it, normalize(number)) }
        prefs.edit().putStringSet(KEY_NUMBERS, numbers).apply()
    }

    fun contains(context: Context, number: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val numbers = prefs.getStringSet(KEY_NUMBERS, emptySet()) ?: emptySet()
        val candidate = normalize(number)
        return numbers.any { PhoneNumberUtils.compare(it, candidate) }
    }

    fun getNumbers(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_NUMBERS, emptySet())?.toSet() ?: emptySet()
    }

    fun isInContacts(context: Context, number: String): Boolean {
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) return true

        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        return try {
            val cursor = context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.NUMBER),
                null,
                null,
                null
            ) ?: return allowOnLookupFailure()
            cursor.use {
                while (it.moveToNext()) {
                    if (PhoneNumberUtils.compare(it.getString(0), number)) return true
                }
            }
            false
        } catch (error: SecurityException) {
            Log.w(TAG, "Contact lookup permission was revoked; allowing call", error)
            true
        }
    }

    private fun normalize(number: String): String =
        number.filter { it.isDigit() || it == '+' }

    private fun allowOnLookupFailure(): Boolean {
        Log.w(TAG, "Contact lookup returned no cursor; allowing call")
        return true
    }

    private const val TAG = "CallerShield"
}
