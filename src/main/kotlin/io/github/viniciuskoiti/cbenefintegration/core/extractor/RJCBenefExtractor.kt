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
class RJCBenefExtractor(
    config: CBenefProperties,
    downloadClient: CBenefDownloadClient,
    availabilityClient: CBenefAvailabilityClient
) : BaseCBenefExtractor(config, downloadClient, availabilityClient) {

    override val stateCode: String = "RJ"
    override val supportedFormats: List<DocumentFormat> = listOf(DocumentFormat.PDF)

    companion object {
        private val logger = LoggerFactory.getLogger(RJCBenefExtractor::class.java)
    }

    override suspend fun getLastModified(): LocalDateTime? {
        return availabilityClient.getLastModified(stateCode)
    }

    override fun extractFromDocument(inputStream: InputStream): List<CBenefSourceData> {
        return try {
            PDDocument.load(inputStream).use { document ->
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)

                logger.info("Extraindo dados do PDF RJ - ${text.length} caracteres")
                extractDataFromPdfText(text)
            }
        } catch (e: Exception) {
            logger.error("Erro ao processar PDF do RJ", e)
            emptyList()
        }
    }

    private fun extractDataFromPdfText(text: String): List<CBenefSourceData> {
        val extractedData = mutableListOf<CBenefSourceData>()
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val lines = text.split("\n")

        logger.info("Total de linhas no PDF RJ: ${lines.size}")

        // Padrões baseados na estrutura real do documento RJ
        val mainPattern = Pattern.compile(
            """^(RJ\d{6})\s+(SIM)?\s*(SIM)?\s*(\d{2}/\d{2}/\d{4})(?:\s+(\d{2}/\d{2}/\d{4}))?\s+(.+)$"""
        )

        val fallbackPattern = Pattern.compile(
            """^(RJ\d{6})\s+(.+)$"""
        )

        var foundCodes = 0
        var processedLines = 0

        for (i in lines.indices) {
            val line = lines[i].trim()

            if (shouldSkipLine(line)) {
                continue
            }

            processedLines++

            // Tenta primeiro o padrão principal (estrutura completa)
            val mainMatcher = mainPattern.matcher(line)
            if (mainMatcher.find()) {
                try {
                    val benefit = extractFromMainPattern(mainMatcher, line, dateFormatter)
                    if (benefit != null) {
                        extractedData.add(benefit)
                        foundCodes++
                        logger.debug("Código RJ encontrado (main): ${benefit.getFullCode()} - ${benefit.description}")
                        continue
                    }
                } catch (e: Exception) {
                    logger.warn("Erro ao processar linha RJ ${i} (main): '${line.take(100)}'", e)
                }
            }

            // Fallback para linhas que não seguem o padrão completo
            val fallbackMatcher = fallbackPattern.matcher(line)
            if (fallbackMatcher.find()) {
                try {
                    val benefit = extractFromFallbackPattern(
                        fallbackMatcher,
                        lines,
                        i,
                        dateFormatter
                    )
                    if (benefit != null) {
                        extractedData.add(benefit)
                        foundCodes++
                        logger.debug("Código RJ encontrado (fallback): ${benefit.getFullCode()} - ${benefit.description}")
                    }
                } catch (e: Exception) {
                    logger.warn("Erro ao processar linha RJ ${i} (fallback): '${line.take(100)}'", e)
                }
            }
        }

        logger.info("RJ - Linhas processadas: $processedLines, códigos encontrados: $foundCodes")

        if (foundCodes < 10) {
            logger.warn("Poucos códigos RJ encontrados ($foundCodes). Verificar formato do documento.")
            logSampleLines(lines)
        }

        return extractedData.distinctBy { it.getFullCode() }
    }

    private fun extractFromMainPattern(
        matcher: java.util.regex.Matcher,
        line: String,
        dateFormatter: DateTimeFormatter
    ): CBenefSourceData? {

        val fullCode = matcher.group(1) ?: return null
        val code = fullCode.substring(2) // Remove "RJ"

        // Extrai CSTs aplicáveis baseado nos grupos "SIM"
        val applicableCSTs = mutableListOf<String>()
        if (matcher.group(2) == "SIM") applicableCSTs.add("00") // CST 00
        if (matcher.group(3) == "SIM") applicableCSTs.add("10") // CST 10
        // Adicionar mais CSTs conforme mapeamento real das colunas

        // Extrai datas
        val startDateStr = matcher.group(4)
        val endDateStr = matcher.group(5) // Pode ser null
        val description = matcher.group(6)?.trim() ?: ""

        val (startDate, endDate) = parseDates(startDateStr, endDateStr, dateFormatter)
        val benefitType = determineBenefitType(description)

        return CBenefSourceData(
            stateCode = stateCode,
            code = code,
            description = description.ifBlank { "Benefício fiscal ICMS - $fullCode" },
            startDate = startDate,
            endDate = endDate,
            benefitType = benefitType,
            applicableCSTs = applicableCSTs,
            cstSpecific = applicableCSTs.isNotEmpty(),
            sourceMetadata = mapOf(
                "extractionMethod" to "PDF_RJ_MAIN_PATTERN",
                "sourceUrl" to sourceUrl,
                "documentType" to "PDF_RJ_TABLE",
                "fullCode" to fullCode,
                "applicableCSTs" to applicableCSTs.joinToString(","),
                "originalLine" to line
            )
        )
    }

    private fun extractFromFallbackPattern(
        matcher: java.util.regex.Matcher,
        allLines: List<String>,
        lineIndex: Int,
        dateFormatter: DateTimeFormatter
    ): CBenefSourceData? {

        val fullCode = matcher.group(1) ?: return null
        val code = fullCode.substring(2) // Remove "RJ"
        val description = matcher.group(2)?.trim() ?: ""

        // Busca datas nas linhas adjacentes
        val (startDate, endDate) = extractDatesFromContext(allLines, lineIndex, dateFormatter)

        // Busca informações de CST no contexto (se disponível)
        val applicableCSTs = extractCSTsFromContext(allLines, lineIndex)

        val benefitType = determineBenefitType(description)

        return CBenefSourceData(
            stateCode = stateCode,
            code = code,
            description = description.ifBlank { "Benefício fiscal ICMS - $fullCode" },
            startDate = startDate,
            endDate = endDate,
            benefitType = benefitType,
            applicableCSTs = applicableCSTs,
            cstSpecific = applicableCSTs.isNotEmpty(),
            sourceMetadata = mapOf(
                "extractionMethod" to "PDF_RJ_FALLBACK_PATTERN",
                "sourceUrl" to sourceUrl,
                "documentType" to "PDF_RJ_FALLBACK",
                "fullCode" to fullCode,
                "applicableCSTs" to applicableCSTs.joinToString(","),
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
                logger.warn("Erro ao parsear data de início '$startDateStr'", e)
                LocalDate.of(2019, 4, 1) // Data padrão RJ
            }
        } else {
            LocalDate.of(2019, 4, 1)
        }

        val endDate = if (!endDateStr.isNullOrBlank()) {
            try {
                val parsed = LocalDate.parse(endDateStr, formatter)
                // Valida se data fim é posterior à data início
                if (parsed.isAfter(startDate)) parsed else null
            } catch (e: Exception) {
                logger.warn("Erro ao parsear data fim '$endDateStr'", e)
                null
            }
        } else {
            null // Sem data fim = ativo indefinidamente
        }

        return Pair(startDate, endDate)
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
            Pair(LocalDate.of(2019, 4, 1), null)
        }
    }

    private fun extractCSTsFromContext(
        allLines: List<String>,
        startIndex: Int
    ): List<String> {

        val applicableCSTs = mutableListOf<String>()

        for (i in (startIndex - 1)..(startIndex + 1)) {
            if (i >= 0 && i < allLines.size) {
                val line = allLines[i]
                if (line.contains("SIM")) {
                    if (!applicableCSTs.contains("00")) applicableCSTs.add("00")
                    if (!applicableCSTs.contains("10")) applicableCSTs.add("10")
                }
            }
        }

        return applicableCSTs
    }

    private fun determineBenefitType(description: String): CBenefBenefitType {
        val lowerDesc = description.lowercase()

        return when {
            lowerDesc.contains("isenção") || lowerDesc.contains("isent") -> CBenefBenefitType.ISENCAO
            lowerDesc.contains("não incidência") || lowerDesc.contains("não tributad") -> CBenefBenefitType.NAO_INCIDENCIA
            lowerDesc.contains("redução") || lowerDesc.contains("reduz") -> CBenefBenefitType.REDUCAO_BASE
            lowerDesc.contains("diferimento") || lowerDesc.contains("diferir") -> CBenefBenefitType.DIFERIMENTO
            lowerDesc.contains("suspensão") || lowerDesc.contains("suspend") -> CBenefBenefitType.SUSPENSAO
            lowerDesc.contains("crédito") -> CBenefBenefitType.CREDITO_OUTORGADO
            lowerDesc.contains("alíquota zero") || lowerDesc.contains("zero") -> CBenefBenefitType.ALIQUOTA_ZERO
            lowerDesc.contains("ampliação") -> CBenefBenefitType.OUTROS
            lowerDesc.contains("transferência") -> CBenefBenefitType.CREDITO_OUTORGADO
            lowerDesc.contains("tributação") -> CBenefBenefitType.OUTROS
            else -> CBenefBenefitType.OUTROS
        }
    }

    private fun shouldSkipLine(line: String): Boolean {
        val trimmedLine = line.trim()

        return trimmedLine.isEmpty() ||
                trimmedLine.length < 8 || // RJ + 6 dígitos = 8 chars mínimo
                trimmedLine.startsWith("CÓDIGO") ||
                trimmedLine.startsWith("CST") ||
                trimmedLine.startsWith("DATA") ||
                trimmedLine.startsWith("Tabela") ||
                trimmedLine.startsWith("SEFAZ") ||
                trimmedLine.matches(Regex("^\\d+$")) || // Apenas números
                trimmedLine.matches(Regex("^[\\s\\-_=X]+$")) || // Apenas separadores
                trimmedLine.contains("SECRETARIA") ||
                trimmedLine.contains("FAZENDA") ||
                trimmedLine.contains("GOVERNO") ||
                trimmedLine.contains("DESCRIÇÃO") ||
                trimmedLine.contains("OBSERVAÇÃO") ||
                trimmedLine.contains("atualizada em") ||
                trimmedLine.contains("SEM PREENCHIMENTO") ||
                trimmedLine.contains("Informar apenas")
    }

    private fun logSampleLines(lines: List<String>) {
        logger.info("Amostra de linhas do PDF RJ:")
        lines.take(30).forEachIndexed { index, line ->
            if (!shouldSkipLine(line) && line.contains("RJ")) {
                logger.info("Linha $index: '$line'")
            }
        }
    }
}