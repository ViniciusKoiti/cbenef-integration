package io.github.viniciuskoiti.cbenefintegration.core.extractor

import io.github.viniciuskoiti.cbenefintegration.client.CBenefAvailabilityClient
import io.github.viniciuskoiti.cbenefintegration.client.CBenefDownloadClient
import io.github.viniciuskoiti.cbenefintegration.config.CBenefProperties
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefSourceData
import io.github.viniciuskoiti.cbenefintegration.enums.CBenefBenefitType
import io.github.viniciuskoiti.cbenefintegration.enums.DocumentFormat
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@Component
class ESCBenefExtractor(
    config: CBenefProperties,
    downloadClient: CBenefDownloadClient,
    availabilityClient: CBenefAvailabilityClient
) : BaseCBenefExtractor(config, downloadClient, availabilityClient) {

    override val stateCode: String = "ES"
    override val supportedFormats: List<DocumentFormat> = listOf(DocumentFormat.PDF)

    companion object {
        private val logger = LoggerFactory.getLogger(ESCBenefExtractor::class.java)
    }

    override suspend fun getLastModified(): LocalDateTime? {
        return availabilityClient.getLastModified(stateCode)
    }

    override fun extractFromDocument(inputStream: InputStream): List<CBenefSourceData> {
        return try {
            PDDocument.load(inputStream).use { document ->
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)

                logger.info("Texto extraído do PDF tem ${text.length} caracteres")

                extractDataFromPdfText(text)
            }
        } catch (e: Exception) {
            logger.error("Erro ao processar PDF do ES", e)
            emptyList()
        }
    }

    private fun extractDataFromPdfText(text: String): List<CBenefSourceData> {
        val extractedData = mutableListOf<CBenefSourceData>()
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val lines = text.split("\n")

        logger.info("Total de linhas no PDF: ${lines.size}")

        // Padrões específicos para o ES - baseado na estrutura da tabela
        val patterns = listOf(
            // Padrão principal: ES + 6 dígitos + dados em colunas
            Pattern.compile("""^(ES\d{6})\s+.*?\s+(\d{2}/\d{2}/\d{4})(?:\s+(\d{2}/\d{2}/\d{4}))?\s+(.+?)\s+(.+?)\s+(.+?)$"""),

            // Padrão alternativo para códigos em linha separada
            Pattern.compile("""^(ES\d{6})\s*$"""),

            // Padrão mais flexível
            Pattern.compile(""".*?(ES\d{6}).*?""")
        )

        var foundCodes = 0
        var processedLines = 0

        for (i in lines.indices) {
            val line = lines[i].trim()

            if (shouldSkipLine(line)) {
                continue
            }

            processedLines++

            val benefit = extractBenefitFromTableLine(line, lines, i, dateFormatter)
            if (benefit != null) {
                extractedData.add(benefit)
                foundCodes++
                logger.debug("Código ES encontrado: ${benefit.getFullCode()} - ${benefit.description}")
                continue
            }

            for (pattern in patterns) {
                val matcher = pattern.matcher(line)
                if (matcher.find()) {
                    try {
                        val extractedBenefit = extractBenefitFromMatch(
                            matcher,
                            line,
                            lines,
                            i,
                            dateFormatter
                        )

                        if (extractedBenefit != null) {
                            extractedData.add(extractedBenefit)
                            foundCodes++
                            logger.debug("Código ES encontrado (fallback): ${extractedBenefit.getFullCode()}")
                        }
                        break
                    } catch (e: Exception) {
                        logger.warn("Erro ao processar linha ${i}: '${line.take(100)}'", e)
                    }
                }
            }
        }

        logger.info("Processadas $processedLines linhas, encontrados $foundCodes códigos CBenef ES")

        if (foundCodes < 5) {
            logger.warn("Poucos códigos ES encontrados! Possível problema na extração.")
            logSampleLines(lines)
        }

        return extractedData.distinctBy { it.getFullCode() }
    }

    private fun extractBenefitFromTableLine(
        line: String,
        allLines: List<String>,
        lineIndex: Int,
        dateFormatter: DateTimeFormatter
    ): CBenefSourceData? {

        // Verifica se a linha contém código ES seguido de dados tabulares
        val tablePattern = Pattern.compile(
            """^(ES\d{6})\s+(SIM|NÃO)?\s*(?:(SIM|NÃO)\s+)*(\d{2}/\d{2}/\d{4})(?:\s+(\d{2}/\d{2}/\d{4}))?\s+(.+?)\s+(.+?)\s+(.+?)$"""
        )

        val matcher = tablePattern.matcher(line)
        if (!matcher.find()) return null

        val fullCode = matcher.group(1) ?: return null
        val code = fullCode.substring(2) // Remove "ES"

        val startDateStr = matcher.group(4)
        val endDateStr = matcher.group(5)
        val description = matcher.group(6)?.trim() ?: ""
        val legalBasis = matcher.group(7)?.trim() ?: ""
        val observation = matcher.group(8)?.trim() ?: ""

        // Parse das datas
        val startDate = try {
            if (!startDateStr.isNullOrBlank()) {
                LocalDate.parse(startDateStr, dateFormatter)
            } else {
                LocalDate.of(2024, 1, 1) // Data padrão para ES
            }
        } catch (e: Exception) {
            LocalDate.of(2024, 1, 1)
        }

        val endDate = try {
            if (!endDateStr.isNullOrBlank()) {
                LocalDate.parse(endDateStr, dateFormatter)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

        // Melhora a descrição se estiver muito curta
        val finalDescription = if (description.length < 10) {
            findDescriptionInContext(allLines, lineIndex, fullCode)
        } else {
            description
        }

        val benefitType = determineBenefitType(finalDescription, observation)

        return CBenefSourceData(
            stateCode = stateCode,
            code = code,
            description = finalDescription.ifBlank { "Benefício fiscal ICMS - $fullCode" },
            startDate = startDate,
            endDate = endDate,
            benefitType = benefitType,
            sourceMetadata = mapOf(
                "extractionMethod" to "PDF_TABLE_EXTRACTION",
                "sourceUrl" to sourceUrl,
                "documentType" to "PDF_TABULAR",
                "fullCode" to fullCode,
                "legalBasis" to legalBasis,
                "observation" to observation,
                "lineIndex" to lineIndex.toString()
            )
        )
    }

    private fun extractBenefitFromMatch(
        matcher: java.util.regex.Matcher,
        currentLine: String,
        allLines: List<String>,
        lineIndex: Int,
        dateFormatter: DateTimeFormatter
    ): CBenefSourceData? {

        val fullCode = matcher.group(1) ?: return null
        val code = fullCode.substring(2) // Remove "ES"

        val description = findDescriptionInContext(allLines, lineIndex, fullCode)
        val (startDate, endDate) = extractDatesFromContext(currentLine, allLines, lineIndex, dateFormatter)
        val benefitType = determineBenefitType(description, "")

        return CBenefSourceData(
            stateCode = stateCode,
            code = code,
            description = description.ifBlank { "Benefício fiscal ICMS - $fullCode" },
            startDate = startDate,
            endDate = endDate,
            benefitType = benefitType,
            sourceMetadata = mapOf(
                "extractionMethod" to "PDF_FALLBACK_EXTRACTION",
                "sourceUrl" to sourceUrl,
                "documentType" to "PDF_STRUCTURED",
                "fullCode" to fullCode,
                "lineIndex" to lineIndex.toString()
            )
        )
    }

    private fun findDescriptionInContext(
        allLines: List<String>,
        startIndex: Int,
        fullCode: String
    ): String {
        val contextLines = mutableListOf<String>()

        // Verifica a linha atual primeiro
        val currentLine = allLines.getOrNull(startIndex)?.trim() ?: ""
        if (currentLine.contains(fullCode)) {
            val afterCode = currentLine.substringAfter(fullCode).trim()
            if (afterCode.isNotBlank()) {
                contextLines.add(afterCode)
            }
        }

        // Busca nas próximas linhas se necessário
        if (contextLines.isEmpty() || contextLines.joinToString(" ").length < 20) {
            for (i in (startIndex + 1) until minOf(startIndex + 4, allLines.size)) {
                val line = allLines[i].trim()
                if (line.isNotEmpty() &&
                    !line.startsWith("ES") &&
                    !shouldSkipLine(line) &&
                    !line.matches(Regex("\\d{2}/\\d{2}/\\d{4}"))) {
                    contextLines.add(line)
                    if (contextLines.joinToString(" ").length > 50) break
                }
            }
        }

        return contextLines.joinToString(" ")
            .replace(Regex("\\d{2}/\\d{2}/\\d{4}"), "") // Remove datas
            .replace(Regex("(SIM|NÃO|Art\\.|Convênio|ICMS).*"), "") // Remove dados técnicos
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(200) // Limita tamanho
    }

    private fun extractDatesFromContext(
        currentLine: String,
        allLines: List<String>,
        lineIndex: Int,
        formatter: DateTimeFormatter
    ): Pair<LocalDate, LocalDate?> {

        val datePattern = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})")
        val dates = mutableListOf<String>()

        // Busca datas na linha atual
        val currentMatcher = datePattern.matcher(currentLine)
        while (currentMatcher.find()) {
            dates.add(currentMatcher.group(1))
        }

        // Se não encontrou, busca nas próximas linhas
        if (dates.isEmpty()) {
            for (i in 1..3) {
                if (lineIndex + i < allLines.size) {
                    val nextMatcher = datePattern.matcher(allLines[lineIndex + i])
                    while (nextMatcher.find()) {
                        dates.add(nextMatcher.group(1))
                    }
                    if (dates.isNotEmpty()) break
                }
            }
        }

        val startDate = if (dates.isNotEmpty()) {
            try {
                LocalDate.parse(dates.first(), formatter)
            } catch (e: Exception) {
                LocalDate.of(2024, 1, 1) // Data padrão ES
            }
        } else {
            LocalDate.of(2024, 1, 1)
        }

        val endDate = if (dates.size > 1) {
            try {
                LocalDate.parse(dates.last(), formatter)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        return Pair(startDate, endDate)
    }

    private fun determineBenefitType(description: String, observation: String): CBenefBenefitType {
        val fullText = "$description $observation".lowercase()

        return when {
            fullText.contains("isenção") || fullText.contains("isenta") -> CBenefBenefitType.ISENCAO
            fullText.contains("não incidência") || fullText.contains("não tributada") -> CBenefBenefitType.NAO_INCIDENCIA
            fullText.contains("redução") || fullText.contains("reduz") -> CBenefBenefitType.REDUCAO_BASE
            fullText.contains("diferimento") || fullText.contains("diferir") -> CBenefBenefitType.DIFERIMENTO
            fullText.contains("suspensão") || fullText.contains("suspend") -> CBenefBenefitType.SUSPENSAO
            fullText.contains("crédito") -> CBenefBenefitType.CREDITO_OUTORGADO
            fullText.contains("alíquota zero") || fullText.contains("zero") -> CBenefBenefitType.ALIQUOTA_ZERO
            else -> CBenefBenefitType.OUTROS
        }
    }

    private fun shouldSkipLine(line: String): Boolean {
        val trimmedLine = line.trim()

        return trimmedLine.isEmpty() ||
                trimmedLine.length < 5 ||
                trimmedLine.startsWith("Cbenef") ||
                trimmedLine.startsWith("Aplica ao") ||
                trimmedLine.startsWith("CST") ||
                trimmedLine.startsWith("DATA") ||
                trimmedLine.startsWith("TABELA") ||
                trimmedLine.matches(Regex("^\\d+$")) || // Apenas números
                trimmedLine.matches(Regex("^[\\s\\-_=]+$")) || // Apenas separadores
                trimmedLine.contains("SECRETARIA") ||
                trimmedLine.contains("FAZENDA") ||
                trimmedLine.contains("GOVERNO") ||
                trimmedLine.contains("DESCRIÇÃO") ||
                trimmedLine.contains("CAPITULAÇÃO") ||
                trimmedLine.contains("OBSERVAÇÃO")
    }

    private fun logSampleLines(lines: List<String>) {
        logger.info("Amostra de linhas do PDF ES:")
        lines.take(30).forEachIndexed { index, line ->
            if (!shouldSkipLine(line)) {
                logger.info("Linha $index: '$line'")
            }
        }
    }
}