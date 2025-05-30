package com.v1.nfe.integration.cbenef.dto

import com.v1.nfe.integration.cbenef.enums.ExtractionStatus
import java.time.LocalDateTime

data class CBenefExtractionResult(
    val stateCode: String,
    val sourceName: String,
    val extractionDate: LocalDateTime,
    val status: ExtractionStatus,
    val data: List<CBenefSourceData>,
    val errorMessage: String? = null,
    val extractedCount: Int = data.size,
    val metadata: Map<String, Any> = emptyMap()
) {
    fun isSuccess() = status == ExtractionStatus.SUCCESS

    companion object {
        fun success(stateCode: String, sourceName: String, data: List<CBenefSourceData>) =
            CBenefExtractionResult(
                stateCode = stateCode,
                sourceName = sourceName,
                extractionDate = LocalDateTime.now(),
                status = ExtractionStatus.SUCCESS,
                data = data
            )

        fun error(stateCode: String, errorMessage: String?) =
            CBenefExtractionResult(
                stateCode = stateCode,
                sourceName = "Unknown",
                extractionDate = LocalDateTime.now(),
                status = ExtractionStatus.ERROR,
                data = emptyList(),
                errorMessage = errorMessage
            )

        fun unavailable(stateCode: String) =
            CBenefExtractionResult(
                stateCode = stateCode,
                sourceName = "Unknown",
                extractionDate = LocalDateTime.now(),
                status = ExtractionStatus.SOURCE_UNAVAILABLE,
                data = emptyList()
            )
    }
}