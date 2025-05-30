package com.v1.nfe.integration.cbenef.client

import com.v1.nfe.integration.cbenef.config.CBenefConfig
import org.springframework.stereotype.Component
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class CBenefHttpClient(private val config: CBenefConfig) {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(config.connectionTimeout))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun sendRequest(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        timeout: Long = config.readTimeout
    ): HttpResponse<InputStream> {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis(timeout))
            .header("User-Agent", config.userAgent)

        headers.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        val request = when (method.uppercase()) {
            "HEAD" -> requestBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody()).build()
            "GET" -> requestBuilder.GET().build()
            else -> throw IllegalArgumentException("Método HTTP não suportado: $method")
        }

        return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
    }

    fun sendGetRequest(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeout: Long = 10000
    ): HttpResponse<Void> {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .timeout(Duration.ofMillis(timeout))
            .header("User-Agent", config.userAgent)

        headers.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        val request = requestBuilder.build()
        return httpClient.send(request, HttpResponse.BodyHandlers.discarding())
    }
}