/*
 * Copyright (c) 2025 Vinícius Koiti Nakahara
 * Licensed under the MIT License (see LICENSE file)
 */
package io.github.viniciuskoiti.cbenefintegration.exception

class CBenefSourceUnavailableException(
    val stateCode: String,
    val sourceUrl: String,
    message: String = "Fonte indisponível"
) : CBenefException("Fonte do estado $stateCode indisponível ($sourceUrl): $message")
