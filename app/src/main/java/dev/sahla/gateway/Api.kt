package dev.sahla.gateway

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

object Api {
    data class Pending(val id: String, val recipient: String, val body: String, val simSlot: Int?)

    private fun open(base: String, path: String, method: String, apiKey: String?): HttpURLConnection {
        return (URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Content-Type", "application/json")
            if (apiKey != null) setRequestProperty("x-api-key", apiKey)
        }
    }

    private fun send(conn: HttpURLConnection, payload: JSONObject?): Pair<Int, String> {
        if (payload != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
        conn.disconnect()
        return code to text
    }

    fun pair(base: String, code: String, model: String, androidVersion: String, simCount: Int): Result<Pair<String, String>> = runCatching {
        val payload = JSONObject().put("code", code).put("model", model)
            .put("androidVersion", androidVersion).put("simCount", simCount).put("batteryLevel", 100)
        val (status, text) = send(open(base, "/api/public/v1/pair", "POST", null), payload)
        if (status !in 200..299) error(JSONObject(text).optJSONObject("error")?.optString("message") ?: "Pairing failed [$status]")
        val data = JSONObject(text).getJSONObject("data")
        data.getString("deviceId") to data.getString("apiKey")
    }

    fun poll(base: String, apiKey: String, deviceId: String): Result<List<Pending>> = runCatching {
        val payload = JSONObject().put("deviceId", deviceId).put("batteryLevel", 100).put("signalStrength", 4).put("limit", 10)
        val (status, text) = send(open(base, "/api/public/v1/gateway-poll", "POST", apiKey), payload)
        if (status !in 200..299) error("Poll failed [$status]: $text")
        val arr: JSONArray = JSONObject(text).optJSONObject("data")?.optJSONArray("messages") ?: JSONArray()
        (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            Pending(m.getString("id"), m.getString("recipient"), m.getString("body"), if (m.isNull("simSlot")) null else m.getInt("simSlot"))
        }
    }

    fun reportStatus(base: String, apiKey: String, messageId: String, status: String, error: String? = null): Result<Unit> = runCatching {
        val payload = JSONObject().put("messageId", messageId).put("status", status)
        if (error != null) payload.put("errorReason", error)
        val (code, text) = send(open(base, "/api/public/v1/message-status", "POST", apiKey), payload)
        if (code !in 200..299) error("Status report failed [$code]: $text")
    }
}
