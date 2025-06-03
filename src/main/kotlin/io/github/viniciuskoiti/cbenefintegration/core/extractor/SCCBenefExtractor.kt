/*
 * Copyright (c) 2025 Vinícius Koiti Nakahara
 * Licensed under the MIT License (see LICENSE file)
 */

package io.github.viniciuskoiti.cbenefintegration.core.extractor

import io.github.viniciuskoiti.cbenefintegration.client.CBenefAvailabilityClient
import io.github.viniciuskoiti.cbenefintegration.client.CBenefDownloadClient
import io.github.viniciuskoiti.cbenefintegration.config.CBenefProperties
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefSourceData
import io.github.viniciuskoiti.cbenefintegration.enums.CBenefBenefitType
import io.github.viniciuskoiti.cbenefintegration.enums.DocumentFormat
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Component
import java.io.InputStream
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@Component
class SCCBenefExtractor(
    config: CBenefProperties,
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

    public override fun extractFromDocument(inputStream: InputStream): List<CBenefSourceData> {
        return try {
            PDDocument.load(inputStream).use { document ->
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)

                logger.info("Texto extraído do PDF tem ${text.length} caracteres")

                extractDataFromPdfText(text)
            }
        } catch (e: Exception) {
            logger.error("Erro ao processar PDF do SC", e)
            emptyList()
        }
    }

    private fun extractDataFromPdfText(text: String): List<CBenefSourceData> {
        val extractedData = mutableListOf<CBenefSourceData>()
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val lines = text.split("\n")

        logger.info("Total de linhas no PDF: ${lines.size}")

        val patterns = listOf(
            Pattern.compile("""(SC\d{6})\s+(.+?)(?:\s+(\d{2}/\d{2}/\d{4}))?(?:\s+(\d{2}/\d{2}/\d{4}))?"""),

            Pattern.compile("""^(SC\d{6})$"""),

            Pattern.compile(""".*?(SC\d{6}).*?""")
        )

        var foundCodes = 0
        var processedLines = 0

        for (i in lines.indices) {
            val line = lines[i].trim()

            if (shouldSkipLine(line)) {
                continue
            }

            processedLines++

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
                            logger.debug("Código encontrado: ${extractedBenefit.getFullCode()} - ${extractedBenefit.description}")
                        }
                        break // Para no primeiro match
                    } catch (e: Exception) {
                        logger.warn("Erro ao processar linha ${i}: '${line.take(100)}'", e)
                    }
                }
            }
        }

        logger.info("Processadas $processedLines linhas, encontrados $foundCodes códigos CBenef")

        if (foundCodes < 10) {
            logger.warn("Poucos códigos encontrados! Possível problema na extração.")
            logger.info("Amostra de linhas processadas:")
            lines.take(20).forEachIndexed { index, line ->
                if (!shouldSkipLine(line)) {
                    logger.info("Linha $index: '$line'")
                }
            }
        }

        return extractedData.distinctBy { it.getFullCode() } // Remove duplicatas
    }

    private fun shouldSkipLine(line: String): Boolean {
        val trimmedLine = line.trim()

        return trimmedLine.isEmpty() ||
                trimmedLine.length < 5 ||
                trimmedLine.startsWith("Página") ||
                trimmedLine.startsWith("Tabela") ||
                trimmedLine.matches(Regex("^\\d+$")) || // Apenas números
                trimmedLine.matches(Regex("^[\\s\\-_=]+$")) || // Apenas separadores
                trimmedLine.contains("SECRETARIA") ||
                trimmedLine.contains("FAZENDA") ||
                trimmedLine.contains("GOVERNO")
    }

    private fun extractBenefitFromMatch(
        matcher: java.util.regex.Matcher,
        currentLine: String,
        allLines: List<String>,
        lineIndex: Int,
        dateFormatter: DateTimeFormatter
    ): CBenefSourceData? {

        val fullCode = matcher.group(1) ?: return null
        val code = fullCode.substring(2) // Remove "SC"

        var description = extractDescriptionFromLines(currentLine, allLines, lineIndex, fullCode)

        if (description.length < 10) {
            description = findDescriptionInNextLines(allLines, lineIndex, fullCode)
        }

        val (startDate, endDate) = extractDatesFromContext(currentLine, allLines, lineIndex, dateFormatter)

        val benefitType = determineBenefitType(description)

        return CBenefSourceData(
            stateCode = stateCode,
            code = code,
            description = description.ifBlank { "Benefício fiscal ICMS - SC$code" },
            startDate = startDate,
            endDate = endDate,
            benefitType = benefitType,
            sourceMetadata = mapOf(
                "extractionMethod" to "PDF_ENHANCED_EXTRACTION",
                "sourceUrl" to sourceUrl,
                "documentType" to "PDF_STRUCTURED",
                "fullCode" to fullCode,
                "lineIndex" to lineIndex.toString()
            )
        )
    }

    private fun extractDescriptionFromLines(
        currentLine: String,
        allLines: List<String>,
        lineIndex: Int,
        fullCode: String
    ): String {
        var description = currentLine
            .replace(fullCode, "") // Remove código
            .replace(Regex("\\d{2}/\\d{2}/\\d{4}"), "") // Remove datas
            .replace(Regex("RICMS/SC-\\d+.*"), "") // Remove referências legais
            .replace(Regex("Art\\.\\s*\\d+.*"), "") // Remove artigos
            .replace(Regex("\\s+"), " ") // Normaliza espaços
            .trim()

        // Se descrição muito curta, busca contexto nas linhas adjacentes
        if (description.length < 20) {
            val contextLines = mutableListOf<String>()

            // Linha anterior
            if (lineIndex > 0) {
                contextLines.add(allLines[lineIndex - 1])
            }

            // Próximas 2 linhas
            for (i in 1..2) {
                if (lineIndex + i < allLines.size) {
                    val nextLine = allLines[lineIndex + i].trim()
                    if (nextLine.isNotEmpty() && !nextLine.startsWith("SC") && !shouldSkipLine(nextLine)) {
                        contextLines.add(nextLine)
                    }
                }
            }

            if (contextLines.isNotEmpty()) {
                description = contextLines.joinToString(" ")
                    .replace(Regex("\\d{2}/\\d{2}/\\d{4}"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
        }

        // Limita tamanho da descrição
        val words = description.split(" ").filter { it.isNotBlank() }
        description = words.take(15).joinToString(" ")

        return if (description.length > 5) description else ""
    }

    private fun findDescriptionInNextLines(
        allLines: List<String>,
        startIndex: Int,
        fullCode: String
    ): String {
        val contextLines = mutableListOf<String>()

        for (i in (startIndex + 1) until minOf(startIndex + 5, allLines.size)) {
            val line = allLines[i].trim()
            if (line.isNotEmpty() &&
                !line.startsWith("SC") &&
                !shouldSkipLine(line) &&
                !line.matches(Regex("\\d{2}/\\d{2}/\\d{4}"))) {
                contextLines.add(line)
            }
        }

        return contextLines.joinToString(" ")
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

        val currentMatcher = datePattern.matcher(currentLine)
        while (currentMatcher.find()) {
            dates.add(currentMatcher.group(1))
        }

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
                // Usa a PRIMEIRA data como início (não a última)
                LocalDate.parse(dates.first(), formatter)
            } catch (e: Exception) {
                LocalDate.of(2023, 1, 1) // Data padrão mais conservadora
            }
        } else {
            LocalDate.of(2023, 1, 1)
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

    private fun determineBenefitType(description: String): CBenefBenefitType {
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