package io.github.viniciuskoiti.cbenefintegration.exception

import io.github.viniciuskoiti.cbenefintegration.exception.CBenefException

class CBenefSourceUnavailableException(
    val stateCode: String,
    val sourceUrl: String,
    message: String = "Fonte indisponível"
) : CBenefException("Fonte do estado $stateCode indisponível ($sourceUrl): $message")
