package com.chiiii5640.thsrapp.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

interface HttpClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse
    suspend fun postForm(url: String, body: Map<String, String>, headers: Map<String, String> = emptyMap()): HttpResponse
}

data class HttpResponse(
    val code: Int,
    val body: String,
) {
    val isSuccessful: Boolean get() = code in 200..299
}

class UrlConnectionHttpClient : HttpClient {
    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse =
        request(url = url, method = "GET", headers = headers)

    override suspend fun postForm(
        url: String,
        body: Map<String, String>,
        headers: Map<String, String>,
    ): HttpResponse {
        val encoded = body.entries.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }
        return request(
            url = url,
            method = "POST",
            headers = headers + ("Content-Type" to "application/x-www-form-urlencoded"),
            body = encoded,
        )
    }

    private suspend fun request(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String? = null,
    ): HttpResponse = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            doInput = true
        }
        if (body != null) {
            connection.doOutput = true
            OutputStreamWriter(connection.outputStream).use { it.write(body) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        HttpResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
    }
}

private fun String.urlEncode(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
