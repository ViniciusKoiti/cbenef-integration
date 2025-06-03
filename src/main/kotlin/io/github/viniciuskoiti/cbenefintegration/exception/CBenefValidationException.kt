/*
 * Copyright (c) 2025 Vinícius Koiti Nakahara
 * Licensed under the MIT License (see LICENSE file)
 */

package io.github.viniciuskoiti.cbenefintegration.exception

import io.github.viniciuskoiti.cbenefintegration.dto.ValidationResult
class CBenefValidationException(
    message: String,
    val validationResult: ValidationResult
) : CBenefException(message)
