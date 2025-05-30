package io.github.viniciuskoiti.cbenefintegration.dto

import io.github.viniciuskoiti.cbenefintegration.dto.ValidationError

data class ValidationResult(
    val isValid: Boolean,
    val validRecords: Int,
    val invalidRecords: Int,
    val errors: List<ValidationError> = emptyList()
)