package dev.sahla.gateway

import android.content.Context

/** Local persistence for the base URL, device id and device API key. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("sahla", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("baseUrl", DEFAULT_BASE)!!
        set(value) = prefs.edit().putString("baseUrl", value.trimEnd('/')).apply()

    var apiKey: String?
        get() = prefs.getString("apiKey", null)
        set(value) = prefs.edit().putString("apiKey", value).apply()

    var deviceId: String?
        get() = prefs.getString("deviceId", null)
        set(value) = prefs.edit().putString("deviceId", value).apply()

    val paired: Boolean get() = !apiKey.isNullOrBlank()

    fun clear() = prefs.edit().remove("apiKey").remove("deviceId").apply()

    companion object {
        const val DEFAULT_BASE = "https://sahla-sms-gateway.lovable.app"
    }
}
