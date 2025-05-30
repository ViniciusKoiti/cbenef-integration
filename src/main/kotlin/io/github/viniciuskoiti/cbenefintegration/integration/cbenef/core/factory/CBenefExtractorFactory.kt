package com.v1.nfe.integration.cbenef.core.factory


import com.v1.nfe.integration.cbenef.client.CBenefAvailabilityClient
import com.v1.nfe.integration.cbenef.client.CBenefDownloadClient
import com.v1.nfe.integration.cbenef.config.CBenefConfig
import com.v1.nfe.integration.cbenef.core.CBenefExtractor
import com.v1.nfe.integration.cbenef.core.extractor.SCCBenefExtractor
import org.springframework.stereotype.Component

@Component
class CBenefExtractorFactory(
    private val config: CBenefConfig,
    private val downloadClient: CBenefDownloadClient,
    private val availabilityClient: CBenefAvailabilityClient
) {

    fun createExtractor(stateCode: String): CBenefExtractor? {
        return when (stateCode.uppercase()) {
            "SC" -> SCCBenefExtractor(config, downloadClient, availabilityClient)
            // "ES" -> ESCBenefExtractor(config, downloadClient, availabilityClient)
            // "RJ" -> RJCBenefExtractor(config, downloadClient, availabilityClient)
            else -> null
        }
    }

    fun getAvailableExtractors(): List<CBenefExtractor> {
        return config.getEnabledStates()
            .mapNotNull { createExtractor(it) }
            .sortedBy { it.getPriority() }
    }
}