package com.v1.nfe.integration.cbenef.service

import io.github.viniciuskoiti.cbenefintegration.config.CBenefProperties
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefSourceData
import io.github.viniciuskoiti.cbenefintegration.service.CBenefCacheService
import io.github.viniciuskoiti.cbenefintegration.service.CBenefSearchService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CBenefService(
    private val integrationService: CBenefIntegrationService,
    private val cacheService: CBenefCacheService?,
    private val searchService: CBenefSearchService,
    private val properties: CBenefProperties
) {

    companion object {
        private val logger = LoggerFactory.getLogger(CBenefService::class.java)
    }

    suspend fun extractAllDocuments(): List<CBenefSourceData> {
        logger.info("Extraindo todos os documentos (sem cache)")

        val results = integrationService.extractAllStates()
        return results.values
            .filter { it.isSuccess() }
            .flatMap { it.data }
    }
    suspend fun extractByState(stateCode: String): List<CBenefSourceData> {
        logger.info("Extraindo estado $stateCode (sem cache)")

        val result = integrationService.extractByState(stateCode)
        return if (result?.isSuccess() == true) result.data else emptyList()
    }
    suspend fun extractAllDocumentsWithCache(): List<CBenefSourceData> {
        if (cacheService == null) {
            logger.info("Cache não habilitado - usando extração sem cache")
            return extractAllDocuments()
        }

        logger.info("Extraindo todos os documentos (com cache)")
        val results = cacheService.getAllStates()
        return results.values
            .filter { it.isSuccess() }
            .flatMap { it.data }
    }
    fun getAllFromCache(): List<CBenefSourceData> {
        return cacheService?.getAllStates()?.values
            ?.filter { it.isSuccess() }
            ?.flatMap { it.data }
            ?: emptyList()
    }
    fun getFromCacheByState(stateCode: String): List<CBenefSourceData> {
        val result = cacheService?.getByState(stateCode)
        return if (result?.isSuccess() == true) result.data else emptyList()
    }
    fun searchBenefits(
        code: String? = null,
        description: String? = null,
        state: String? = null,
        activeOnly: Boolean = true
    ): List<CBenefSourceData> {
        return searchService.searchBenefits(code, description, state, activeOnly)
    }

    fun findBenefitByCode(fullCode: String): CBenefSourceData? {
        return searchService.findBenefitByCode(fullCode)
    }

    fun isCacheEnabled(): Boolean = cacheService != null

    fun getCacheStats(): Map<String, Any>? = cacheService?.getStats()

    fun clearCache(): Boolean {
        cacheService?.clearCache()
        return cacheService != null
    }

    fun getAvailableStates(): List<String> = integrationService.getAvailableStates()
}