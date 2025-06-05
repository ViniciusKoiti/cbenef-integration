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
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@Component
class PRCBenefExtractor(
    config: CBenefProperties,
    downloadClient: CBenefDownloadClient,
    availabilityClient: CBenefAvailabilityClient
) : BaseCBenefExtractor(config, downloadClient, availabilityClient) {

    override val stateCode: String = "PR"
    override val supportedFormats: List<DocumentFormat> = listOf(DocumentFormat.PDF)

    companion object {
        private val logger = LoggerFactory.getLogger(PRCBenefExtractor::class.java)
    }

    override suspend fun getLastModified(): LocalDateTime? {
        return availabilityClient.getLastModified(stateCode)
    }

    override fun extractFromDocument(inputStream: InputStream): List<CBenefSourceData> {
        return try {
            PDDocument.load(inputStream).use { document ->
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)

                logger.info("Extraindo dados do PDF PR - ${text.length} caracteres")
                extractDataFromPdfText(text)
            }
        } catch (e: Exception) {
            logger.error("Erro ao processar PDF do PR", e)
            emptyList()
        }
    }

    private fun extractDataFromPdfText(text: String): List<CBenefSourceData> {
        val extractedData = mutableListOf<CBenefSourceData>()
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val lines = text.split("\n")

        logger.info("Total de linhas no PDF PR: ${lines.size}")

        // Padrões baseados na estrutura da "TABELA 5.2" do Paraná
        val patterns = listOf(
            // Padrão principal: Código PR + dados em colunas
            Pattern.compile("""^(PR\d{6})\s+(.+?)\s+(\d{2}/\d{2}/\d{4})(?:\s+(\d{2}/\d{2}/\d{4}))?\s+(.+?)$"""),

            // Padrão para códigos em tabela estruturada
            Pattern.compile("""^(PR\d{6})\s+([^0-9]+?)\s+(.+)$"""),

            // Padrão mais flexível para capturar códigos PR
            Pattern.compile(""".*?(PR\d{6}).*?""")
        )

        var foundCodes = 0
        var processedLines = 0

        for (i in lines.indices) {
            val line = lines[i].trim()

            if (shouldSkipLine(line)) {
                continue
            }

            processedLines++

            // Tenta extração usando padrão de tabela estruturada primeiro
            val benefit = extractFromTableStructure(line, lines, i, dateFormatter)
            if (benefit != null) {
                extractedData.add(benefit)
                foundCodes++
                logger.debug("Código PR encontrado (tabela): ${benefit.getFullCode()} - ${benefit.description}")
                continue
            }

            // Fallback para padrões regex
            for (pattern in patterns) {
                val matcher = pattern.matcher(line)
                if (matcher.find()) {
                    try {
                        val extractedBenefit = extractFromPattern(
                            matcher,
                            line,
                            lines,
                            i,
                            dateFormatter
                        )

                        if (extractedBenefit != null) {
                            extractedData.add(extractedBenefit)
                            foundCodes++
                            logger.debug("Código PR encontrado (pattern): ${extractedBenefit.getFullCode()}")
                        }
                        break
                    } catch (e: Exception) {
                        logger.warn("Erro ao processar linha PR ${i}: '${line.take(100)}'", e)
                    }
                }
            }
        }

        logger.info("PR - Linhas processadas: $processedLines, códigos encontrados: $foundCodes")

        if (foundCodes < 5) {
            logger.warn("Poucos códigos PR encontrados ($foundCodes). Verificar formato do documento.")
            logSampleLines(lines)
        }

        return extractedData.distinctBy { it.getFullCode() }
    }

    private fun extractFromTableStructure(
        line: String,
        allLines: List<String>,
        lineIndex: Int,
        dateFormatter: DateTimeFormatter
    ): CBenefSourceData? {

        // Verifica se linha contém código PR no formato de tabela
        val tablePattern = Pattern.compile(
            """^(PR\d{6})\s+(.+?)\s+(\d{2}/\d{2}/\d{4})(?:\s+(\d{2}/\d{2}/\d{4}))?\s*(.*)$"""
        )

        val matcher = tablePattern.matcher(line)
        if (!matcher.find()) return null

        val fullCode = matcher.group(1) ?: return null
        val code = fullCode.substring(2) // Remove "PR"

        val description = matcher.group(2)?.trim() ?: ""
        val startDateStr = matcher.group(3)
        val endDateStr = matcher.group(4) // Pode ser null
        val additionalInfo = matcher.group(5)?.trim() ?: ""

        // Parse das datas
        val (startDate, endDate) = parseDates(startDateStr, endDateStr, dateFormatter)

        // Melhora descrição se necessário
        val finalDescription = enhanceDescription(description, additionalInfo, allLines, lineIndex)

        val benefitType = determineBenefitType(finalDescription, additionalInfo)

        return CBenefSourceData(
            stateCode = stateCode,
            code = code,
            description = finalDescription.ifBlank { "Benefício fiscal ICMS - $fullCode" },
            startDate = startDate,
            endDate = endDate,
            benefitType = benefitType,
            sourceMetadata = mapOf(
                "extractionMethod" to "PDF_PR_TABLE_STRUCTURE",
                "sourceUrl" to sourceUrl,
                "documentType" to "PDF_PR_TABELA_5_2",
                "fullCode" to fullCode,
                "additionalInfo" to additionalInfo,
                "lineIndex" to lineIndex.toString()
            )
        )
    }

    private fun extractFromPattern(
        matcher: java.util.regex.Matcher,
        currentLine: String,
        allLines: List<String>,
        lineIndex: Int,
        dateFormatter: DateTimeFormatter
    ): CBenefSourceData? {

        val fullCode = matcher.group(1) ?: return null
        val code = fullCode.substring(2) // Remove "PR"

        // Extrai descrição do contexto
        val description = extractDescriptionFromContext(currentLine, allLines, lineIndex, fullCode)

        // Extrai datas do contexto
        val (startDate, endDate) = extractDatesFromContext(allLines, lineIndex, dateFormatter)

        val benefitType = determineBenefitType(description, "")

        return CBenefSourceData(
            stateCode = stateCode,
            code = code,
            description = description.ifBlank { "Benefício fiscal ICMS - $fullCode" },
            startDate = startDate,
            endDate = endDate,
            benefitType = benefitType,
            sourceMetadata = mapOf(
                "extractionMethod" to "PDF_PR_PATTERN_FALLBACK",
                "sourceUrl" to sourceUrl,
                "documentType" to "PDF_PR_FALLBACK",
                "fullCode" to fullCode,
                "lineIndex" to lineIndex.toString()
            )
        )
    }

    private fun parseDates(
        startDateStr: String?,
        endDateStr: String?,
        formatter: DateTimeFormatter
    ): Pair<LocalDate, LocalDate?> {

        val startDate = if (!startDateStr.isNullOrBlank()) {
            try {
                LocalDate.parse(startDateStr, formatter)
            } catch (e: Exception) {
                logger.warn("Erro ao parsear data de início PR '$startDateStr'", e)
                LocalDate.of(2019, 1, 1) // Data padrão PR
            }
        } else {
            LocalDate.of(2019, 1, 1)
        }

        val endDate = if (!endDateStr.isNullOrBlank()) {
            try {
                val parsed = LocalDate.parse(endDateStr, formatter)
                if (parsed.isAfter(startDate)) parsed else null
            } catch (e: Exception) {
                logger.warn("Erro ao parsear data fim PR '$endDateStr'", e)
                null
            }
        } else {
            null
        }

        return Pair(startDate, endDate)
    }

    private fun enhanceDescription(
        baseDescription: String,
        additionalInfo: String,
        allLines: List<String>,
        lineIndex: Int
    ): String {
        var description = baseDescription

        // Se descrição muito curta, busca contexto
        if (description.length < 20) {
            val contextLines = mutableListOf<String>()

            // Próximas 2 linhas para buscar continuação da descrição
            for (i in 1..2) {
                if (lineIndex + i < allLines.size) {
                    val nextLine = allLines[lineIndex + i].trim()
                    if (nextLine.isNotEmpty() &&
                        !nextLine.startsWith("PR") &&
                        !shouldSkipLine(nextLine) &&
                        !nextLine.matches(Regex("\\d{2}/\\d{2}/\\d{4}"))) {
                        contextLines.add(nextLine)
                    }
                }
            }

            if (contextLines.isNotEmpty()) {
                description = "$description ${contextLines.joinToString(" ")}"
            }
        }

        // Adiciona informação adicional se relevante
        if (additionalInfo.isNotEmpty() && !description.contains(additionalInfo, ignoreCase = true)) {
            description = "$description - $additionalInfo"
        }

        return description
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(250) // Limita tamanho
    }

    private fun extractDescriptionFromContext(
        currentLine: String,
        allLines: List<String>,
        lineIndex: Int,
        fullCode: String
    ): String {
        // Remove código da linha atual
        var description = currentLine
            .replace(fullCode, "")
            .replace(Regex("\\d{2}/\\d{2}/\\d{4}"), "") // Remove datas
            .replace(Regex("\\s+"), " ")
            .trim()

        // Se muito curta, busca nas próximas linhas
        if (description.length < 15) {
            val contextLines = mutableListOf<String>()

            for (i in 1..3) {
                if (lineIndex + i < allLines.size) {
                    val nextLine = allLines[lineIndex + i].trim()
                    if (nextLine.isNotEmpty() &&
                        !nextLine.startsWith("PR") &&
                        !shouldSkipLine(nextLine)) {
                        contextLines.add(nextLine)
                    }
                }
            }

            description = contextLines.joinToString(" ")
                .replace(Regex("\\d{2}/\\d{2}/\\d{4}"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        return description.take(200)
    }

    private fun extractDatesFromContext(
        allLines: List<String>,
        startIndex: Int,
        formatter: DateTimeFormatter
    ): Pair<LocalDate, LocalDate?> {

        val datePattern = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})")
        val dates = mutableListOf<String>()

        // Busca datas na linha atual e próximas 2 linhas
        for (i in startIndex until minOf(startIndex + 3, allLines.size)) {
            val line = allLines[i]
            val matcher = datePattern.matcher(line)
            while (matcher.find()) {
                dates.add(matcher.group(1))
            }
        }

        return if (dates.isNotEmpty()) {
            parseDates(dates.first(), dates.getOrNull(1), formatter)
        } else {
            Pair(LocalDate.of(2019, 1, 1), null)
        }
    }

    private fun determineBenefitType(description: String, additionalInfo: String): CBenefBenefitType {
        val fullText = "$description $additionalInfo".lowercase()

        return when {
            fullText.contains("isenção") || fullText.contains("isent") -> CBenefBenefitType.ISENCAO
            fullText.contains("não incidência") || fullText.contains("não tributad") -> CBenefBenefitType.NAO_INCIDENCIA
            fullText.contains("redução") || fullText.contains("reduz") -> CBenefBenefitType.REDUCAO_BASE
            fullText.contains("diferimento") || fullText.contains("diferir") -> CBenefBenefitType.DIFERIMENTO
            fullText.contains("suspensão") || fullText.contains("suspend") -> CBenefBenefitType.SUSPENSAO
            fullText.contains("crédito") -> CBenefBenefitType.CREDITO_OUTORGADO
            fullText.contains("alíquota zero") || fullText.contains("zero") -> CBenefBenefitType.ALIQUOTA_ZERO
            fullText.contains("substituição") -> CBenefBenefitType.OUTROS
            fullText.contains("monofásica") -> CBenefBenefitType.OUTROS
            else -> CBenefBenefitType.OUTROS
        }
    }

    private fun shouldSkipLine(line: String): Boolean {
        val trimmedLine = line.trim()

        return trimmedLine.isEmpty() ||
                trimmedLine.length < 8 ||
                trimmedLine.startsWith("CÓDIGO") ||
                trimmedLine.startsWith("Tabela") ||
                trimmedLine.startsWith("TABELA") ||
                trimmedLine.startsWith("CST") ||
                trimmedLine.startsWith("DATA") ||
                trimmedLine.startsWith("SPED") ||
                trimmedLine.startsWith("SEFAZ") ||
                trimmedLine.startsWith("Página") ||
                trimmedLine.matches(Regex("^\\d+$")) ||
                trimmedLine.matches(Regex("^[\\s\\-_=]+$")) ||
                trimmedLine.contains("SECRETARIA") ||
                trimmedLine.contains("FAZENDA") ||
                trimmedLine.contains("GOVERNO") ||
                trimmedLine.contains("DESCRIÇÃO") ||
                trimmedLine.contains("VIGÊNCIA") ||
                trimmedLine.contains("OBSERVAÇÃO") ||
                trimmedLine.contains("Sistema Público") ||
                trimmedLine.contains("Escrituração Digital")
    }

    private fun logSampleLines(lines: List<String>) {
        logger.info("Amostra de linhas do PDF PR:")
        lines.take(30).forEachIndexed { index, line ->
            if (!shouldSkipLine(line) && (line.contains("PR") || line.length > 20)) {
                logger.info("Linha $index: '$line'")
            }
        }
    }
}