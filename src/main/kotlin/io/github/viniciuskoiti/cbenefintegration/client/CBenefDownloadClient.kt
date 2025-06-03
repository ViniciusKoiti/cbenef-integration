/*
 * Copyright (c) 2025 Vinícius Koiti Nakahara
 * Licensed under the MIT License (see LICENSE file)
 */

package io.github.viniciuskoiti.cbenefintegration.client

import io.github.viniciuskoiti.cbenefintegration.config.CBenefProperties
import io.github.viniciuskoiti.cbenefintegration.exception.CBenefSourceUnavailableException
import org.springframework.stereotype.Service
import java.io.InputStream
import java.net.http.HttpResponse

@Service
class CBenefDownloadClient(
    private val httpClient: CBenefHttpClient,
    private val config: CBenefProperties
) {

    fun downloadDocument(stateCode: String): HttpResponse<InputStream> {
        val sourceUrl = config.getSourceUrl(stateCode)
            ?: throw CBenefSourceUnavailableException(stateCode, "N/A", "URL não configurada")

        var lastException: Exception? = null
        val maxRetries = config.getMaxRetries(stateCode)

        val urlsToTry = listOf(sourceUrl) + (config.states[stateCode]?.fallbackUrls ?: emptyList())

        for (url in urlsToTry) {
            repeat(maxRetries) { attempt ->
                try {
                    val response = httpClient.sendRequest(
                        url = url,
                        headers = config.getCustomHeaders(stateCode),
                        timeout = config.getReadTimeout(stateCode)
                    )

                    if (response.statusCode() in 200..299) {
                        return response
                    } else {
                        throw Exception("HTTP ${response.statusCode()}")
                    }

                } catch (e: Exception) {
                    lastException = e
                    if (attempt < maxRetries - 1) {
                        Thread.sleep(config.connection.retryDelay * (attempt + 1)) // Backoff exponencial
                    }
                }
            }
        }

        throw CBenefSourceUnavailableException(
            stateCode,
            sourceUrl,
            "Falha após $maxRetries tentativas: ${lastException?.message}"
        )
    }



}
