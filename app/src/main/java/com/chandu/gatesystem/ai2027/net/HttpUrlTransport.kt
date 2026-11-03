package com.chandu.gatesystem.ai2027.net

import com.chandu.gatesystem.ai2027.domain.AiTransport
import com.chandu.gatesystem.ai2027.domain.TransportResponse
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Plain HttpURLConnection transport. Only the app token is sent; no provider keys exist on the device. */
class HttpUrlTransport(private val connectTimeoutMs: Int = 10_000, private val readTimeoutMs: Int = 90_000) : AiTransport {
    @Throws(IOException::class)
    override fun post(url: String, bearerToken: String, jsonBody: String): TransportResponse =
        call(url, "POST", bearerToken, jsonBody)

    @Throws(IOException::class)
    override fun get(url: String, bearerToken: String?): TransportResponse = call(url, "GET", bearerToken, null)

    private fun call(url: String, method: String, token: String?, body: String?): TransportResponse {
        val conn = try { URL(url).openConnection() as HttpURLConnection } catch (e: Exception) { throw IOException("Bad URL: ${e.message}") }
        try {
            conn.requestMethod = method
            conn.connectTimeout = connectTimeoutMs; conn.readTimeout = readTimeoutMs
            conn.setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: return TransportResponse(code, ""))
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return TransportResponse(code, text)
        } finally { conn.disconnect() }
    }
}
