package io.github.viniciuskoiti.cbenefintegration.exception

import io.github.viniciuskoiti.cbenefintegration.exception.CBenefException

class CBenefExtractionException(
    val stateCode: String,
    message: String,
    cause: Throwable? = null
) : CBenefException("Erro na extração do estado $stateCode: $message", cause)