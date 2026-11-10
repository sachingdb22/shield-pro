package com.callershield.pro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

data class SpamReport(
    val number: String,
    val category: String,
    val note: String,
    val date: String
)

object SpamReportStore {
    private const val PREFS = "caller_shield"
    private const val KEY_REPORTS = "spam_reports"

    fun add(context: Context, number: String, category: String, note: String) {
        val reports = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_REPORTS, "[]"))
        reports.put(JSONObject().apply {
            put("number", number)
            put("category", category)
            put("note", note)
            put("date", DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date()))
        })
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REPORTS, reports.toString())
            .apply()
    }

    fun getReports(context: Context): List<SpamReport> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_REPORTS, "[]")
        val reports = JSONArray(raw)
        return (0 until reports.length()).map { index ->
            val report = reports.getJSONObject(index)
            SpamReport(
                number = report.getString("number"),
                category = report.getString("category"),
                note = report.optString("note"),
                date = report.getString("date")
            )
        }
    }
}
