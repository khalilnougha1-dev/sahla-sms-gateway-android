package dev.sahla.gateway

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LocalMessage(val address: String, val body: String, val direction: String, val status: String, val time: Long)

object MessageLog {
    private const val PREFS = "sahla_messages"
    fun add(context: Context, message: LocalMessage) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val items = read(context).toMutableList()
        items.add(0, message)
        val arr = JSONArray()
        items.take(100).forEach { arr.put(JSONObject().put("address", it.address).put("body", it.body).put("direction", it.direction).put("status", it.status).put("time", it.time)) }
        prefs.edit().putString("items", arr.toString()).apply()
    }
    fun read(context: Context): List<LocalMessage> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("items", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> val o=arr.getJSONObject(i); LocalMessage(o.optString("address"),o.optString("body"),o.optString("direction"),o.optString("status"),o.optLong("time")) }
        } catch (_: Exception) { emptyList() }
    }
}
