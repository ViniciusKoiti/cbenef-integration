/*
 * Copyright (c) 2025 Vinícius Koiti Nakahara
 * Licensed under the MIT License (see LICENSE file)
 */

package io.github.viniciuskoiti.cbenefintegration.service

import io.github.viniciuskoiti.cbenefintegration.dto.CBenefSourceData
import org.springframework.stereotype.Service


@Service
class CBenefSearchService(
    private val integrationService: CBenefIntegrationService,
    private val cacheService: CBenefCacheService?
) {

    fun searchBenefits(
        code: String? = null,
        description: String? = null,
        state: String? = null,
        activeOnly: Boolean = true
    ): List<CBenefSourceData> {

        val statesToSearch = if (state != null) {
            listOf(state)
        } else {
            integrationService.getAvailableStates()
        }

        val allBenefits = mutableListOf<CBenefSourceData>()

        statesToSearch.forEach { stateCode ->
            val result = if (cacheService != null) {
                cacheService.getByState(stateCode)
            } else {
                integrationService.extractByState(stateCode)
            }

            result?.let { extractionResult ->
                if (extractionResult.isSuccess()) {
                    allBenefits.addAll(extractionResult.data)
                }
            }
        }

        return allBenefits.filter { benefit ->
            var matches = true

            if (code != null) {
                matches = matches && (benefit.code.contains(code, ignoreCase = true) ||
                        benefit.getFullCode().contains(code, ignoreCase = true))
            }

            if (description != null) {
                matches = matches && benefit.description.contains(description, ignoreCase = true)
            }

            if (activeOnly) {
                matches = matches && benefit.isActive()
            }

            matches
        }
    }

    fun findBenefitByCode(fullCode: String): CBenefSourceData? {
        if (fullCode.length < 3) return null

        val stateCode = fullCode.take(2)
        val code = fullCode.drop(2)

        val result = if (cacheService != null) {
            cacheService.getByState(stateCode)
        } else {
            integrationService.extractByState(stateCode)
        }

        return result?.data?.find { it.code == code }
    }
}