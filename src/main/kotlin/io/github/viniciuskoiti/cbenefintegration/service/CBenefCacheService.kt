package io.github.viniciuskoiti.cbenefintegration.service

import com.v1.nfe.integration.cbenef.service.CBenefIntegrationService
import io.github.viniciuskoiti.cbenefintegration.config.CBenefProperties
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefExtractionResult
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Service
@ConditionalOnProperty("app.cbenef.cache.enabled", havingValue = "true")
class CBenefCacheService(
    private val integrationService: CBenefIntegrationService,
    private val properties: CBenefProperties
) {

    companion object {
        private val logger = LoggerFactory.getLogger(CBenefCacheService::class.java)
    }

    private val cache = ConcurrentHashMap<String, CachedResult>()

    data class CachedResult(
        val result: CBenefExtractionResult,
        val cachedAt: LocalDateTime,
        val ttlMinutes: Long = 1440
    ) {
        fun isExpired(): Boolean = LocalDateTime.now().isAfter(cachedAt.plusMinutes(ttlMinutes))
    }

    fun getByState(stateCode: String): CBenefExtractionResult? {
        val cached = cache[stateCode]
        if (cached != null && !cached.isExpired()) {
            logger.info("Cache hit para estado: $stateCode")
            return cached.result
        }

        if (cached?.isExpired() == true) {
            cache.remove(stateCode)
            logger.info("Cache expirado removido: $stateCode")
        }

        val result = integrationService.extractByState(stateCode)
        if (result?.isSuccess() == true) {
            val ttl = properties.cache.getTtlForState(stateCode)
            cache[stateCode] = CachedResult(result, LocalDateTime.now(), ttl)
            logger.info("Estado $stateCode cacheado por $ttl minutos")
        }

        return result
    }

    fun getAllStates(): Map<String, CBenefExtractionResult> {
        val availableStates = integrationService.getAvailableStates()

        return availableStates.associateWith { stateCode ->
            getByState(stateCode)
        }.filterValues { it != null }
            .mapValues { it.value!! }
    }

    fun getMultipleStates(stateCodes: List<String>): Map<String, CBenefExtractionResult> {
        return stateCodes.associateWith { stateCode ->
            getByState(stateCode)
        }.filterValues { it != null }
            .mapValues { it.value!! }
    }

    fun clearCache() {
        cache.clear()
        logger.info("Cache limpo completamente")
    }

    fun clearCacheForState(stateCode: String) {
        cache.remove(stateCode)
        logger.info("Cache do estado $stateCode limpo")
    }

    fun isCached(stateCode: String): Boolean {
        val cached = cache[stateCode]
        return cached != null && !cached.isExpired()
    }
    fun getStats(): Map<String, Any> {
        val validEntries = cache.filterValues { !it.isExpired() }
        val totalBenefits = validEntries.values.sumOf { it.result.extractedCount }

        return mapOf(
            "totalStatesCached" to validEntries.size,
            "totalBenefitsCached" to totalBenefits,
            "cacheEntries" to validEntries.map { (state, cached) ->
                mapOf(
                    "state" to state,
                    "benefitsCount" to cached.result.extractedCount,
                    "cachedAt" to cached.cachedAt,
                    "expiresAt" to cached.cachedAt.plusMinutes(cached.ttlMinutes),
                    "isExpired" to cached.isExpired()
                )
            }
        )
    }

    @Scheduled(fixedRateString = "#{@cbenefProperties.cache.cleanupIntervalHours * 3600000}")
    fun cleanupExpiredCache() {
        val expiredKeys = cache.entries
            .filter { it.value.isExpired() }
            .map { it.key }

        expiredKeys.forEach { cache.remove(it) }

        if (expiredKeys.isNotEmpty()) {
            logger.info("Limpeza automática: ${expiredKeys.size} entradas expiradas removidas")
        }
    }
}