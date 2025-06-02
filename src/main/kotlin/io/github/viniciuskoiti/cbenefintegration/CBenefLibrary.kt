package io.github.viniciuskoiti.cbenefintegration

import io.github.viniciuskoiti.cbenefintegration.dto.CBenefSourceData
import io.github.viniciuskoiti.cbenefintegration.service.CBenefService
import org.springframework.stereotype.Component

@Component
class CBenefLibrary(
    private val cbenefService: CBenefService
) {

    suspend fun extractAllBenefits(useCache: Boolean = true): List<CBenefSourceData> {
        return if (useCache && cbenefService.isCacheEnabled()) {
            cbenefService.extractAllDocumentsWithCache()
        } else {
            cbenefService.extractAllDocuments()
        }
    }
    suspend fun extractBenefitsByState(stateCode: String): List<CBenefSourceData> {
        return cbenefService.extractByState(stateCode)
    }
    fun searchBenefits(
        code: String? = null,
        description: String? = null,
        state: String? = null,
        activeOnly: Boolean = true
    ): List<CBenefSourceData> {
        return cbenefService.searchBenefits(code, description, state, activeOnly)
    }
    fun findBenefitByCode(fullCode: String): CBenefSourceData? {
        return cbenefService.findBenefitByCode(fullCode)
    }
    fun getAvailableStates(): List<String> {
        return cbenefService.getAvailableStates()
    }
    fun isCacheEnabled(): Boolean {
        return cbenefService.isCacheEnabled()
    }
    fun getCacheStats(): Map<String, Any>? {
        return cbenefService.getCacheStats()
    }
    fun clearCache(): Boolean {
        return cbenefService.clearCache()
    }
    fun getFromCacheByState(stateCode: String): List<CBenefSourceData> {
        return cbenefService.getFromCacheByState(stateCode)
    }
    fun getAllFromCache(): List<CBenefSourceData> {
        return cbenefService.getAllFromCache()
    }
}