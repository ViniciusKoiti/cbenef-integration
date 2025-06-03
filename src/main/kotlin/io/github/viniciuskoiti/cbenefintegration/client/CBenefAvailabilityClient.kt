
/*
 * Copyright (c) 2025 Vinícius Koiti Nakahara
 * Licensed under the MIT License (see LICENSE file)
 */

package io.github.viniciuskoiti.cbenefintegration.client

import io.github.viniciuskoiti.cbenefintegration.config.CBenefProperties
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class CBenefAvailabilityClient(
    private val httpClient: CBenefHttpClient,
    private val config: CBenefProperties
) {

    fun checkSourceAvailability(stateCode: String): Boolean {
        return try {
            val sourceUrl = config.getSourceUrl(stateCode) ?: return false
            val response = httpClient.sendGetRequest(sourceUrl)
            response.statusCode() in 200..299
        } catch (e: Exception) {
            false
        }
    }

    fun getLastModified(stateCode: String): LocalDateTime? {
        return try {
            val sourceUrl = config.getSourceUrl(stateCode) ?: return null
            val response = httpClient.sendGetRequest(sourceUrl)

            response.headers().firstValue("Last-Modified")
                .map { DateTimeFormatter.RFC_1123_DATE_TIME.parse(it) }
                .map { LocalDateTime.from(it) }
                .orElse(null)
        } catch (e: Exception) {
            null
        }
    }

    fun isSourceHealthy(stateCode: String): Boolean {
        return try {
            val sourceUrl = config.getSourceUrl(stateCode) ?: return false
            val startTime = System.currentTimeMillis()
            val response = httpClient.sendGetRequest(sourceUrl, timeout = 5000)
            val responseTime = System.currentTimeMillis() - startTime

            response.statusCode() in 200..299 && responseTime < 10000
        } catch (e: Exception) {
            false
        }
    }
}
