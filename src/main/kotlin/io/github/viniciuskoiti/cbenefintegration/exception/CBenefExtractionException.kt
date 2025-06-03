/*
 * Copyright (c) 2025 Vinícius Koiti Nakahara
 * Licensed under the MIT License (see LICENSE file)
 */

package io.github.viniciuskoiti.cbenefintegration.exception
class CBenefExtractionException(
    val stateCode: String,
    message: String,
    cause: Throwable? = null
) : CBenefException("Erro na extração do estado $stateCode: $message", cause)