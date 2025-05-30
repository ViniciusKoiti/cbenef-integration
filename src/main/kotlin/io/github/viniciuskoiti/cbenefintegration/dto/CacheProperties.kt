package com.v1.nfe.integration.cbenef.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class CacheProperties(
    var enabled: Boolean = false,

    @field:Min(60)
    var ttlMinutes: Long = 1440,

    @field:Min(10)
    @field:Max(10000)
    var maxSize: Int = 1000,

    @field:Min(1)
    @field:Max(24)
    var cleanupIntervalHours: Int = 1,

    var stateSpecificTtl: Map<String, Long> = mapOf(
        "SC" to 720,
        "ES" to 720,
        "RJ" to 720
    )
) {

    fun getTtlForState(stateCode: String): Long {
        return stateSpecificTtl[stateCode] ?: ttlMinutes
    }

    fun isStateSpecificTtl(stateCode: String): Boolean {
        return stateSpecificTtl.containsKey(stateCode)
    }
}