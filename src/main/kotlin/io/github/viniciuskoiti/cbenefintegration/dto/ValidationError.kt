package io.github.viniciuskoiti.cbenefintegration.dto

data class ValidationError(
    val recordIndex: Int,
    val field: String,
    val value: String?,
    val message: String
)