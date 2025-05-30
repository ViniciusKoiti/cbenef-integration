
package io.github.viniciuskoiti.cbenefintegration.core

import com.v1.nfe.integration.cbenef.config.CBenefProperties
import com.v1.nfe.integration.cbenef.dto.CBenefExtractionResult
import com.v1.nfe.integration.cbenef.dto.CBenefSourceData
import com.v1.nfe.integration.cbenef.dto.ValidationResult
import com.v1.nfe.integration.cbenef.enums.DocumentFormat
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