/*
 * Copyright (c) 2025 Vinícius Koiti Nakahara
 * Licensed under the MIT License (see LICENSE file)
 */

package io.github.viniciuskoiti.cbenefintegration.dto

import io.github.viniciuskoiti.cbenefintegration.dto.ValidationError

data class ValidationResult(
    val isValid: Boolean,
    val validRecords: Int,
    val invalidRecords: Int,
    val errors: List<ValidationError> = emptyList()
)