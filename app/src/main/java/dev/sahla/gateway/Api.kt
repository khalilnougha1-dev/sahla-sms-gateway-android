package dev.sahla.gateway

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** Thin HTTP client for the Sahla SMS gateway endpoints. */
object Api {

    data class Pending(val id: String, val recipient: String, val body: String, val simSlot: Int?)

    private fun open(base: String, path: String, method: String, apiKey: String?): HttpURLConnection {
        val conn = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("Content-Type", "application/json")
        if (apiKey != null) conn.setRequestProperty("x-api-key", apiKey)
        return conn
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

    /** POST /api/public/v1/pair — exchanges a pairing code for a device API key. */
    fun pair(
        base: String,
        code: String,
        model: String,
        androidVersion: String,
        simCount: Int,
    ): Result<Pair<String, String>> = runCatching {
        val payload = JSONObject()
            .put("code", code)
            .put("model", model)
            .put("androidVersion", androidVersion)
            .put("simCount", simCount)
        val (status, text) = send(open(base, "/api/public/v1/pair", "POST", null), payload)
        if (status !in 200..299) error("Pairing failed [$status]: $text")
        val data = JSONObject(text).getJSONObject("data")
        data.getString("deviceId") to data.getString("apiKey")
    }

    /** GET /api/public/v1/gateway-poll — pulls messages queued for this device. */
    fun poll(base: String, apiKey: String): Result<List<Pending>> = runCatching {
        val (status, text) = send(open(base, "/api/public/v1/gateway-poll", "GET", apiKey), null)
        if (status !in 200..299) error("Poll failed [$status]: $text")
        val arr: JSONArray = JSONObject(text).optJSONObject("data")?.optJSONArray("messages")
            ?: JSONObject(text).optJSONArray("messages")
            ?: JSONArray()
        (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            Pending(
                id = m.getString("id"),
                recipient = m.getString("recipient"),
                body = m.getString("body"),
                simSlot = if (m.isNull("simSlot")) null else m.getInt("simSlot"),
            )
        }
    }

    /** POST /api/public/v1/message-status — reports sent / delivered / failed back to the dashboard. */
    fun reportStatus(
        base: String,
        apiKey: String,
        messageId: String,
        status: String,
        error: String? = null,
    ): Result<Unit> = runCatching {
        val payload = JSONObject().put("messageId", messageId).put("status", status)
        if (error != null) payload.put("errorReason", error)
        val (code, text) = send(open(base, "/api/public/v1/message-status", "POST", apiKey), payload)
        if (code !in 200..299) error("Status report failed [$code]: $text")
    }
}
