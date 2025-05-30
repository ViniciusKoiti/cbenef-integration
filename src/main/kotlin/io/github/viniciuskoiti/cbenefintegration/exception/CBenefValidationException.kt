package io.github.viniciuskoiti.cbenefintegration.exception

import io.github.viniciuskoiti.cbenefintegration.dto.ValidationResult
import io.github.viniciuskoiti.cbenefintegration.exception.CBenefException

class CBenefValidationException(
    message: String,
    val validationResult: ValidationResult
) : CBenefException(message)
