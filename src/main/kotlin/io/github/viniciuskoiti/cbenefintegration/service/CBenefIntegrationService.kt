package io.github.viniciuskoiti.cbenefintegration.service

import io.github.viniciuskoiti.cbenefintegration.core.factory.CBenefExtractorFactory
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefExtractionResult
import io.github.viniciuskoiti.cbenefintegration.exception.CBenefExtractionException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

@Service
class CBenefIntegrationService(
    private val extractorFactory: CBenefExtractorFactory
) {

    companion object {
        private val logger = LoggerFactory.getLogger(CBenefIntegrationService::class.java)
    }

    private val executorService = Executors.newFixedThreadPool(3)

    fun getAvailableStates(): List<String> {
        return extractorFactory.getAvailableExtractors()
            .filter { it.isEnabled() }
            .map { it.stateCode }
    }

    fun extractByState(stateCode: String): CBenefExtractionResult? {
        return try {
            logger.info("Extraindo estado $stateCode")

            val extractor = extractorFactory.createExtractor(stateCode) ?: run {
                logger.warn("Extrator não encontrado para estado: $stateCode")
                return null
            }

            if (!extractor.isEnabled()) {
                logger.warn("Extrator desabilitado para estado: $stateCode")
                return null
            }

            extractor.extract()

        } catch (e: CBenefExtractionException) {
            logger.error("Erro na extração do estado $stateCode", e)
            CBenefExtractionResult.error(stateCode, e.message)
        } catch (e: Exception) {
            logger.error("Erro inesperado na extração do estado $stateCode", e)
            CBenefExtractionResult.error(stateCode, "Erro inesperado: ${e.message}")
        }
    }

    fun extractAllStates(): Map<String, CBenefExtractionResult> {
        val availableStates = getAvailableStates()
        logger.info("Extraindo todos os estados: ${availableStates.joinToString(",")}")

        val futures = availableStates.map { stateCode ->
            CompletableFuture.supplyAsync({
                stateCode to extractByState(stateCode)
            }, executorService)
        }

        return CompletableFuture.allOf(*futures.toTypedArray())
            .thenApply {
                futures.mapNotNull { future ->
                    val (state, result) = future.get()
                    result?.let { state to it }
                }.toMap()
            }.get()
    }

    fun extractMultipleStates(stateCodes: List<String>): Map<String, CBenefExtractionResult> {
        logger.info("Extraindo estados específicos: ${stateCodes.joinToString(",")}")

        val futures = stateCodes.map { stateCode ->
            CompletableFuture.supplyAsync({
                stateCode to extractByState(stateCode)
            }, executorService)
        }

        return CompletableFuture.allOf(*futures.toTypedArray())
            .thenApply {
                futures.mapNotNull { future ->
                    val (state, result) = future.get()
                    result?.let { state to it }
                }.toMap()
            }.get()
    }

    fun getExtractorInfo(stateCode: String): Map<String, Any>? {
        val extractor = extractorFactory.createExtractor(stateCode) ?: return null

        return mapOf(
            "stateCode" to extractor.stateCode,
            "sourceName" to extractor.sourceName,
            "sourceUrl" to extractor.sourceUrl,
            "supportedFormats" to extractor.supportedFormats,
            "isEnabled" to extractor.isEnabled(),
            "priority" to extractor.getPriority(),
            "displayName" to extractor.getDisplayName(),
            "connectionTimeout" to extractor.connectionTimeout,
            "readTimeout" to extractor.readTimeout,
            "maxRetries" to extractor.maxRetries
        )
    }

    fun shutdown() {
        executorService.shutdown()
    }
}