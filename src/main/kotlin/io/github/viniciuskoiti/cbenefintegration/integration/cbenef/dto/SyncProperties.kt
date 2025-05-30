package com.v1.nfe.integration.cbenef.dto

import jakarta.validation.constraints.NotBlank

data class SyncProperties(
    var enabled: Boolean = false,

    @field:NotBlank
    var cron: String = "0 0 2 * * ?",

    var initialSync: Boolean = false,

    var useCache: Boolean = false,

    var specificStates: List<String> = emptyList(),

    var parallel: Boolean = true
) {

    fun getStatesToSync(availableStates: List<String>): List<String> {
        return if (specificStates.isNotEmpty()) {
            specificStates.filter { availableStates.contains(it) }
        } else {
            availableStates
        }
    }
}