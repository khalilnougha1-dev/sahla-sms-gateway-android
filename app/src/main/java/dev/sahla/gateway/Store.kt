package dev.sahla.gateway

import android.content.Context

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("sahla", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("baseUrl", DEFAULT_BASE) ?: DEFAULT_BASE
        set(value) = prefs.edit().putString("baseUrl", value.trimEnd('/')).apply()
    var apiKey: String?
        get() = prefs.getString("apiKey", null)
        set(value) = prefs.edit().putString("apiKey", value).apply()
    var deviceId: String?
        get() = prefs.getString("deviceId", null)
        set(value) = prefs.edit().putString("deviceId", value).apply()
    var deviceName: String
        get() = prefs.getString("deviceName", android.os.Build.MODEL) ?: android.os.Build.MODEL
        set(value) = prefs.edit().putString("deviceName", value).apply()
    var gatewayEnabled: Boolean
        get() = prefs.getBoolean("gatewayEnabled", true)
        set(value) = prefs.edit().putBoolean("gatewayEnabled", value).apply()
    var receiveSms: Boolean
        get() = prefs.getBoolean("receiveSms", true)
        set(value) = prefs.edit().putBoolean("receiveSms", value).apply()
    var stickyNotification: Boolean
        get() = prefs.getBoolean("stickyNotification", true)
        set(value) = prefs.edit().putBoolean("stickyNotification", value).apply()
    var defaultSimSlot: Int
        get() = prefs.getInt("defaultSimSlot", 1)
        set(value) = prefs.edit().putInt("defaultSimSlot", value).apply()
    var sendDelaySeconds: Int
        get() = prefs.getInt("sendDelay", 5)
        set(value) = prefs.edit().putInt("sendDelay", value.coerceIn(0, 60)).apply()

    val paired: Boolean get() = !apiKey.isNullOrBlank() && !deviceId.isNullOrBlank()
    fun clear() = prefs.edit().remove("apiKey").remove("deviceId").apply()

    companion object { const val DEFAULT_BASE = "https://sms.sahlapay.dz" }
}
