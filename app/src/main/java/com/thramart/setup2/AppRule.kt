package com.thramart.setup2

data class AppRule(
    val packageName: String,
    val label: String,
    var enabled: Boolean = false,
    var startMinutes: Int = 0,
    var endMinutes: Int = 1439
) {
    fun isAllowedNow(nowMinutes: Int): Boolean {
        if (!enabled) return false
        return if (startMinutes <= endMinutes) {
            nowMinutes in startMinutes..endMinutes
        } else {
            // Overnight window, e.g. 22:00 -> 06:00
            nowMinutes >= startMinutes || nowMinutes <= endMinutes
        }
    }
}
