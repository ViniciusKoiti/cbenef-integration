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

                logger.debug("Extraindo dados do PDF RJ - ${text.length} caracteres")
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

        logger.debug("Total de linhas no PDF RJ: ${lines.size}")

        // Padrões específicos para o RJ - baseado na estrutura tabular código x CST
        val patterns = listOf(
            // Padrão principal: RJ + 6 dígitos seguido de CSTs em colunas
            Pattern.compile("""^(RJ\d{6})\s+.*?\s+(.+?)\s+(.+?)$"""),

            // Padrão para códigos isolados
            Pattern.compile("""^(RJ\d{6})\s*$"""),

            // Padrão mais flexível
            Pattern.compile(""".*?(RJ\d{6}).*?""")
        )

        var foundCodes = 0
        var processedLines = 0

        for (i in lines.indices) {
            val line = lines[i].trim()

            if (shouldSkipLine(line)) {
                continue
            }

            processedLines++

            // Tenta extrair dados usando estrutura específica do RJ
            val benefit = extractBenefitFromRJTableLine(line, lines, i, dateFormatter)
            if (benefit != null) {
                extractedData.add(benefit)
                foundCodes++
                continue
            }

            // Fallback para padrões gerais
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
                        }
                        break
                    } catch (e: Exception) {
                        logger.warn("Erro ao processar linha RJ ${i}: '${line.take(100)}'", e)
                    }
                }
            }
        }

        logger.debug("RJ - Linhas processadas: $processedLines, códigos encontrados: $foundCodes")

        if (foundCodes < 5) {
            logger.warn("Poucos códigos RJ encontrados ($foundCodes). Verificar formato do documento.")
        }

        return extractedData.distinctBy { it.getFullCode() }
    }

    private fun extractBenefitFromRJTableLine(
        line: String,
        allLines: List<String>,
        lineIndex: Int,
        dateFormatter: DateTimeFormatter
    ): CBenefSourceData? {

        // Padrão específico para tabela RJ - código seguido de CSTs e descrição
        val rjTablePattern = Pattern.compile(
            """^(RJ\d{6})\s+([X\s]+)\s+([X\s]+)\s+([X\s]+)\s+([X\s]+)\s+([X\s]+)\s+([X\s]+)\s+([X\s]+)\s+(.+)$"""
        )

        val matcher = rjTablePattern.matcher(line)
        if (!matcher.find()) return null

        val fullCode = matcher.group(1) ?: return null
        val code = fullCode.substring(2) // Remove "RJ"

        // Extrai quais CSTs são aplicáveis (baseado nas marcações X)
        val applicableCSTs = mutableListOf<String>()
        val cstColumns = listOf("00", "10", "20", "30", "40", "41", "50", "51", "60", "70", "90")

        for (i in 2..8) { // Grupos 2-8 correspondem às colunas CST
            val cstValue = matcher.group(i)?.trim()
            if (!cstValue.isNullOrBlank() && cstValue.contains("X")) {
                applicableCSTs.add(cstColumns.getOrNull(i - 2) ?: "")
            }
        }

        val description = matcher.group(9)?.trim() ?: ""

        // Busca informações adicionais nas linhas adjacentes
        val (startDate, endDate, legalBasis) = extractAdditionalInfo(allLines, lineIndex, fullCode, dateFormatter)

        val benefitType = determineBenefitType(description, legalBasis)

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
                "extractionMethod" to "PDF_RJ_TABLE_EXTRACTION",
                "sourceUrl" to sourceUrl,
                "documentType" to "PDF_CST_TABLE",
                "fullCode" to fullCode,
                "legalBasis" to legalBasis,
                "applicableCSTs" to applicableCSTs.joinToString(","),
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
        val code = fullCode.substring(2) // Remove "RJ"

        val description = findDescriptionInContext(allLines, lineIndex, fullCode)
        val (startDate, endDate, legalBasis) = extractAdditionalInfo(allLines, lineIndex, fullCode, dateFormatter)
        val benefitType = determineBenefitType(description, legalBasis)

        return CBenefSourceData(
            stateCode = stateCode,
            code = code,
            description = description.ifBlank { "Benefício fiscal ICMS - $fullCode" },
            startDate = startDate,
            endDate = endDate,
            benefitType = benefitType,
            sourceMetadata = mapOf(
                "extractionMethod" to "PDF_RJ_FALLBACK_EXTRACTION",
                "sourceUrl" to sourceUrl,
                "documentType" to "PDF_STRUCTURED",
                "fullCode" to fullCode,
                "legalBasis" to legalBasis,
                "lineIndex" to lineIndex.toString()
            )
        )
    }

    private fun extractAdditionalInfo(
        allLines: List<String>,
        startIndex: Int,
        fullCode: String,
        dateFormatter: DateTimeFormatter
    ): Triple<LocalDate, LocalDate?, String> {

        val datePattern = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})")
        val dates = mutableListOf<String>()
        var legalBasis = ""

        // Busca informações na linha atual e adjacentes
        for (i in startIndex until minOf(startIndex + 3, allLines.size)) {
            val line = allLines[i]

            // Busca datas
            val dateMatcher = datePattern.matcher(line)
            while (dateMatcher.find()) {
                dates.add(dateMatcher.group(1))
            }

            // Busca base legal (Convênio, Lei, Decreto, etc.)
            if (line.contains("Convênio", ignoreCase = true) ||
                line.contains("Lei", ignoreCase = true) ||
                line.contains("Decreto", ignoreCase = true) ||
                line.contains("Art", ignoreCase = true)) {
                legalBasis = line.trim().take(100)
            }
        }

        val startDate = if (dates.isNotEmpty()) {
            try {
                LocalDate.parse(dates.first(), dateFormatter)
            } catch (e: Exception) {
                LocalDate.of(2019, 9, 2) // Data padrão RJ (obrigatório desde 02/09/2019)
            }
        } else {
            LocalDate.of(2019, 9, 2)
        }

        val endDate = if (dates.size > 1) {
            try {
                LocalDate.parse(dates.last(), dateFormatter)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        return Triple(startDate, endDate, legalBasis)
    }

    private fun findDescriptionInContext(
        allLines: List<String>,
        startIndex: Int,
        fullCode: String
    ): String {
        val contextLines = mutableListOf<String>()

        // Verifica a linha atual
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
                    !line.startsWith("RJ") &&
                    !shouldSkipLine(line) &&
                    !line.matches(Regex("\\d{2}/\\d{2}/\\d{4}"))) {
                    contextLines.add(line)
                    if (contextLines.joinToString(" ").length > 50) break
                }
            }
        }

        return contextLines.joinToString(" ")
            .replace(Regex("\\d{2}/\\d{2}/\\d{4}"), "") // Remove datas
            .replace(Regex("(Convênio|Lei|Decreto|Art\\.).*"), "") // Remove dados legais
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(200)
    }

    private fun determineBenefitType(description: String, legalBasis: String): CBenefBenefitType {
        val fullText = "$description $legalBasis".lowercase()

        return when {
            fullText.contains("isenção") || fullText.contains("isent") -> CBenefBenefitType.ISENCAO
            fullText.contains("não incidência") || fullText.contains("não tributad") -> CBenefBenefitType.NAO_INCIDENCIA
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
                trimmedLine.startsWith("Código") ||
                trimmedLine.startsWith("CST") ||
                trimmedLine.startsWith("Tabela") ||
                trimmedLine.startsWith("SEFAZ") ||
                trimmedLine.matches(Regex("^\\d+$")) || // Apenas números
                trimmedLine.matches(Regex("^[\\s\\-_=X]+$")) || // Apenas separadores e X
                trimmedLine.contains("SECRETARIA") ||
                trimmedLine.contains("FAZENDA") ||
                trimmedLine.contains("GOVERNO") ||
                trimmedLine.contains("BENEFÍCIO") ||
                trimmedLine.contains("DESCRIÇÃO")
    }
}