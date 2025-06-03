/*
 * Copyright (c) 2025 Vinícius Koiti Nakahara
 * Licensed under the MIT License (see LICENSE file)
 */
package io.github.viniciuskoiti.cbenefintegration.core

import io.github.viniciuskoiti.cbenefintegration.config.CBenefProperties
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefExtractionResult
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefSourceData
import io.github.viniciuskoiti.cbenefintegration.dto.ValidationResult
import io.github.viniciuskoiti.cbenefintegration.enums.DocumentFormat
import java.time.LocalDateTime
interface CBenefExtractor {
    val stateCode: String
    val supportedFormats: List<DocumentFormat>
    val config: CBenefProperties
    val sourceName: String
        get() = "SEFAZ $stateCode - CBenef"
    val sourceUrl: String
        get() = config.getSourceUrl(stateCode)
            ?: throw IllegalStateException("URL não configurada para estado $stateCode")
    val connectionTimeout: Long
        get() = config.getConnectionTimeout(stateCode)
    val readTimeout: Long
        get() = config.getReadTimeout(stateCode)
    val maxRetries: Int
        get() = config.getMaxRetries(stateCode)
    val customHeaders: Map<String, String>
        get() = config.getCustomHeaders(stateCode)
    fun extract(): CBenefExtractionResult
    fun isSourceAvailable(): Boolean
    suspend fun getLastModified(): LocalDateTime?
    fun validateExtractedData(data: List<CBenefSourceData>): ValidationResult
    fun isEnabled(): Boolean = config.isStateEnabled(stateCode)
    fun getPriority(): Int = config.getPriority(stateCode)
    fun getDisplayName(): String = "CBenef $stateCode (${supportedFormats.joinToString(",")})"
}