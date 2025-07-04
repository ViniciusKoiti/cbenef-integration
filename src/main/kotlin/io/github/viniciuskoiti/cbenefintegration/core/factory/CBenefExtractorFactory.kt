/*
 * Copyright (c) 2025 Vinícius Koiti Nakahara
 * Licensed under the MIT License (see LICENSE file)
 */

package io.github.viniciuskoiti.cbenefintegration.core.factory


import io.github.viniciuskoiti.cbenefintegration.client.CBenefAvailabilityClient
import io.github.viniciuskoiti.cbenefintegration.client.CBenefDownloadClient
import io.github.viniciuskoiti.cbenefintegration.config.CBenefProperties
import io.github.viniciuskoiti.cbenefintegration.core.CBenefExtractor
import io.github.viniciuskoiti.cbenefintegration.core.extractor.ESCBenefExtractor
import io.github.viniciuskoiti.cbenefintegration.core.extractor.PRCBenefExtractor
import io.github.viniciuskoiti.cbenefintegration.core.extractor.RJCBenefExtractor
import io.github.viniciuskoiti.cbenefintegration.core.extractor.SCCBenefExtractor
import org.springframework.stereotype.Component

@Component
class CBenefExtractorFactory(
    private val config: CBenefProperties,
    private val downloadClient: CBenefDownloadClient,
    private val availabilityClient: CBenefAvailabilityClient
) {

    fun createExtractor(stateCode: String): CBenefExtractor? {
        return when (stateCode.uppercase()) {
            "SC" -> SCCBenefExtractor(config, downloadClient, availabilityClient)
            "ES" -> ESCBenefExtractor(config, downloadClient, availabilityClient)
            "RJ" -> RJCBenefExtractor(config, downloadClient, availabilityClient)
            "PR" -> PRCBenefExtractor(config, downloadClient, availabilityClient)
            else -> null
        }
    }

    fun getAvailableExtractors(): List<CBenefExtractor> {
        return config.getEnabledStates()
            .mapNotNull { createExtractor(it) }
            .sortedBy { it.getPriority() }
    }
}