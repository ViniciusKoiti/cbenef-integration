package com.v1.nfe.integration.cbenef.config

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class StateConfig(
    var enabled: Boolean = false,

    @field:Min(1)
    @field:Max(26)
    var priority: Int = 5,

    var sourceUrl: String? = null,

    var customTimeout: Long? = null,
    var customReadTimeout: Long? = null,
    var customMaxRetries: Int? = null,

    var customHeaders: Map<String, String> = emptyMap(),

    var fallbackUrls: List<String> = emptyList(),

    var forceCache: Boolean = false,

    var customCacheTtl: Long? = null
) {

    fun shouldUseCache(globalCacheEnabled: Boolean): Boolean {
        return globalCacheEnabled || forceCache
    }

    fun getCacheTtl(defaultTtl: Long): Long {
        return customCacheTtl ?: defaultTtl
    }
}