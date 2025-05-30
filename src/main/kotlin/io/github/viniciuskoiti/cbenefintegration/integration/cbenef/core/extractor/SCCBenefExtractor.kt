package com.v1.nfe.integration.cbenef.core.extractor

import com.v1.nfe.integration.cbenef.client.CBenefAvailabilityClient
import com.v1.nfe.integration.cbenef.client.CBenefDownloadClient
import com.v1.nfe.integration.cbenef.config.CBenefConfig
import com.v1.nfe.integration.cbenef.dto.CBenefSourceData
import com.v1.nfe.integration.cbenef.enums.CBenefBenefitType
import com.v1.nfe.integration.cbenef.enums.DocumentFormat
import org.springframework.stereotype.Component
import java.io.InputStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@Component
class SCCBenefExtractor(
    config: CBenefConfig,
    downloadClient: CBenefDownloadClient,
    availabilityClient: CBenefAvailabilityClient
) : BaseCBenefExtractor(config, downloadClient, availabilityClient) {

    override val stateCode: String = "SC"
    override val supportedFormats: List<DocumentFormat> = listOf(DocumentFormat.PDF)

    companion object {
        private val logger = LoggerFactory.getLogger(SCCBenefExtractor::class.java)
    }
    override suspend fun getLastModified(): LocalDateTime? {
        return availabilityClient.getLastModified(stateCode)
    }

    override fun extractFromDocument(inputStream: InputStream): List<CBenefSourceData> {
        return try {
            PDDocument.load(inputStream).use { document ->
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)

                extractDataFromPdfText(text)
            }
        } catch (e: Exception) {
            logger.error("Erro ao processar PDF do SC", e)
            return emptyList()
        }
    }

    private fun extractDataFromPdfText(text: String): List<CBenefSourceData> {
        val extractedData = mutableListOf<CBenefSourceData>()
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        val lines = text.split("\n")

        val cbenefPattern = Pattern.compile(
            """(SC\d{6})\s+(.+?)\s+(\d{2}/\d{2}/\d{4})(?:\s+(\d{2}/\d{2}/\d{4}))?""",
            Pattern.MULTILINE
        )

        for (line in lines) {
            if (line.contains("Tabela 5.2A") ||
                line.contains("Página") ||
                line.contains("Benefício") ||
                line.contains("Tributo") ||
                line.isBlank()) {
                continue
            }

            val matcher = cbenefPattern.matcher(line)
            if (matcher.find()) {
                try {
                    val fullCode = matcher.group(1) // SC850001, etc
                    val code = fullCode.substring(2) // Remove "SC"

                    var description = extractDescription(line)

                    // Extrai datas
                    val dateMatches = extractDates(line, dateFormatter)
                    val startDate = dateMatches.first
                    val endDate = dateMatches.second

                    // Determina o tipo de benefício baseado no código
                    val benefitType = determineBenefitType(description)

                    extractedData.add(
                        CBenefSourceData(
                            stateCode = stateCode,
                            code = code,
                            description = description,
                            startDate = startDate,
                            endDate = endDate,
                            benefitType = benefitType,
                            sourceMetadata = mapOf(
                                "extractionMethod" to "PDF_TABLE_EXTRACTION",
                                "sourceUrl" to sourceUrl,
                                "documentType" to "PDF_STRUCTURED",
                                "fullCode" to fullCode
                            )
                        )
                    )
                } catch (e: Exception) {
                    logger.warn("Falha ao processar linha SC: '${line.take(100)}'", e)
                }
            }
        }

        return extractedData
    }

    private fun extractDescription(line: String): String {
        var description = line
            .replace(Regex("SC\\d{6}"), "") // Remove código SC
            .replace(Regex("\\d{2}/\\d{2}/\\d{4}"), "") // Remove datas
            .replace(Regex("RICMS/SC-\\d+.*?Art\\. \\d+.*?SC\\d{6}"), "") // Remove referências legais
            .replace(Regex("\\s+"), " ") // Normaliza espaços
            .trim()

        val words = description.split(" ")
        description = words.take(15).joinToString(" ") // Limita a 15 palavras

        return if (description.length > 5) description else "Benefício fiscal ICMS"
    }

    private fun extractDates(line: String, formatter: DateTimeFormatter): Pair<LocalDate, LocalDate?> {
        val datePattern = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})")
        val matcher = datePattern.matcher(line)

        val dates = mutableListOf<String>()
        while (matcher.find()) {
            dates.add(matcher.group(1))
        }

        val startDate = if (dates.isNotEmpty()) {
            LocalDate.parse(dates.last(), formatter) // Última data como início
        } else {
            LocalDate.of(2023, 5, 1) // Data padrão se não encontrar
        }

        val endDate = if (dates.size > 1 && dates[dates.size - 2] != dates.last()) {
            try {
                LocalDate.parse(dates[dates.size - 2], formatter)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        return Pair(startDate, endDate)
    }

    private fun determineBenefitType(description: String): CBenefBenefitType? {
        return when {
            description.contains("Isenção", ignoreCase = true) -> CBenefBenefitType.ISENCAO
            description.contains("Não Incidência", ignoreCase = true) -> CBenefBenefitType.NAO_INCIDENCIA
            description.contains("Redução", ignoreCase = true) -> CBenefBenefitType.REDUCAO_BASE
            description.contains("Diferimento", ignoreCase = true) -> CBenefBenefitType.DIFERIMENTO
            description.contains("Suspensão", ignoreCase = true) -> CBenefBenefitType.SUSPENSAO
            description.contains("Crédito", ignoreCase = true) -> CBenefBenefitType.CREDITO_OUTORGADO
            description.contains("Alíquota Zero", ignoreCase = true) -> CBenefBenefitType.ALIQUOTA_ZERO
            else -> CBenefBenefitType.OUTROS
        }
    }
}