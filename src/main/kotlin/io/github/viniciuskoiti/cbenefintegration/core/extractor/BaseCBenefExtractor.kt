package io.github.viniciuskoiti.cbenefintegration.core.extractor
import io.github.viniciuskoiti.cbenefintegration.client.CBenefAvailabilityClient
import io.github.viniciuskoiti.cbenefintegration.client.CBenefDownloadClient
import io.github.viniciuskoiti.cbenefintegration.config.CBenefProperties
import io.github.viniciuskoiti.cbenefintegration.core.CBenefExtractor
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefExtractionResult
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefSourceData
import io.github.viniciuskoiti.cbenefintegration.dto.ValidationResult
import io.github.viniciuskoiti.cbenefintegration.dto.ValidationError
import java.io.InputStream

abstract class BaseCBenefExtractor(
    override val config: CBenefProperties,
    protected val downloadClient: CBenefDownloadClient,
    protected val availabilityClient: CBenefAvailabilityClient
) : CBenefExtractor {

    override fun extract(): CBenefExtractionResult {
        return try {
            if (!isEnabled()) {
                return CBenefExtractionResult.error(stateCode, "Estado $stateCode desabilitado")
            }

            if (!isSourceAvailable()) {
                return CBenefExtractionResult.unavailable(stateCode)
            }

            val response = downloadClient.downloadDocument(stateCode)
            val extractedData = extractFromDocument(response.body())
            val validationResult = validateExtractedData(extractedData)

            if (validationResult.invalidRecords > 0) {
                validationResult.errors.forEach { error ->
                    println("Erro validação [$stateCode]: Registro ${error.recordIndex}, Campo '${error.field}': ${error.message}")
                }
            }

            CBenefExtractionResult.success(stateCode, sourceName, extractedData)

        } catch (e: Exception) {
            CBenefExtractionResult.error(stateCode, e.message)
        }
    }


    override fun isSourceAvailable(): Boolean = availabilityClient.checkSourceAvailability(stateCode)


    override fun validateExtractedData(data: List<CBenefSourceData>): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        var validRecords = 0

        data.forEachIndexed { index, record ->
            var recordValid = true

            if (record.code.isBlank()) {
                errors.add(ValidationError(index, "code", record.code, "Código não pode ser vazio"))
                recordValid = false
            }

            if (record.description.isBlank()) {
                errors.add(ValidationError(index, "description", record.description, "Descrição não pode ser vazia"))
                recordValid = false
            }

            if (record.endDate != null && record.endDate.isBefore(record.startDate)) {
                errors.add(ValidationError(index, "endDate", record.endDate.toString(), "Data fim não pode ser anterior à data início"))
                recordValid = false
            }

            if (record.stateCode != stateCode) {
                errors.add(ValidationError(index, "stateCode", record.stateCode, "Código do estado não confere"))
                recordValid = false
            }

            if (recordValid) validRecords++
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            validRecords = validRecords,
            invalidRecords = data.size - validRecords,
            errors = errors
        )
    }

    protected abstract fun extractFromDocument(inputStream: InputStream): List<CBenefSourceData>
}