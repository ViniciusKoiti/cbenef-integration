package com.v1.nfe.integration.cbenef.dto

data class ValidationResult(
    val isValid: Boolean,
    val validRecords: Int,
    val invalidRecords: Int,
    val errors: List<ValidationError> = emptyList()
)