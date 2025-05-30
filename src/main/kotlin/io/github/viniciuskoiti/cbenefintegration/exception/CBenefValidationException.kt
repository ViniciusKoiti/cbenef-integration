package com.v1.nfe.integration.cbenef.exception

import com.v1.nfe.integration.cbenef.dto.ValidationResult

class CBenefValidationException(
    message: String,
    val validationResult: ValidationResult
) : CBenefException(message)
