package com.v1.nfe.integration.cbenef.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class ConnectionProperties(
    @field:Min(1000)
    @field:Max(300000)
    var timeout: Long = 30000,

    @field:Min(1000)
    @field:Max(600000)
    var readTimeout: Long = 60000,

    @field:NotBlank
    var userAgent: String = "CBenef-Library/1.0",

    @field:Min(1)
    @field:Max(10)
    var maxRetries: Int = 3,

    @field:Min(100)
    @field:Max(30000)
    var retryDelay: Long = 1000,

    @field:Min(1)
    @field:Max(10)
    var maxConcurrentExtractions: Int = 3
)