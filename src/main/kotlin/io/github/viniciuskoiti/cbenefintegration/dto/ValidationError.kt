/*
 * Copyright (c) 2025 Vinícius Koiti Nakahara
 * Licensed under the MIT License (see LICENSE file)
 */

package io.github.viniciuskoiti.cbenefintegration.dto

data class ValidationError(
    val recordIndex: Int,
    val field: String,
    val value: String?,
    val message: String
)