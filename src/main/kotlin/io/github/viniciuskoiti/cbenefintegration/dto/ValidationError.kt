package com.v1.nfe.integration.cbenef.dto

data class ValidationError(
    val recordIndex: Int,
    val field: String,
    val value: String?,
    val message: String
)