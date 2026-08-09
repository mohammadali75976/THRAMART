package com.thramart.setup2

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object RuleStore {
    private const val PREFS = "kiosk_setup2"
    private const val KEY_RULES = "rules"
    private const val KEY_PIN = "admin_pin"
    private const val DEFAULT_PIN = "2580"

    fun loadRules(context: Context): MutableList<AppRule> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RULES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                AppRule(
                    packageName = o.getString("package"),
                    label = o.optString("label", o.getString("package")),
                    enabled = o.optBoolean("enabled", false),
                    startMinutes = o.optInt("start", 0),
                    endMinutes = o.optInt("end", 1439)
                )
            }
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun saveRules(context: Context, rules: List<AppRule>) {
        val arr = JSONArray()
        rules.forEach { rule ->
            arr.put(JSONObject().apply {
                put("package", rule.packageName)
                put("label", rule.label)
                put("enabled", rule.enabled)
                put("start", rule.startMinutes)
                put("end", rule.endMinutes)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_RULES, arr.toString()).apply()
    }

    fun getPin(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN

    fun setPin(context: Context, pin: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PIN, pin).apply()
    }
}
