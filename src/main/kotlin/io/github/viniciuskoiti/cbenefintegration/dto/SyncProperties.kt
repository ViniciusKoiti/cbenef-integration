/*
 * Copyright (c) 2025 Vinícius Koiti Nakahara
 * Licensed under the MIT License (see LICENSE file)
 */

package io.github.viniciuskoiti.cbenefintegration.dto

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