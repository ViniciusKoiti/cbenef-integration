package com.v1.nfe.integration.cbenef.exception

class CBenefExtractionException(
    val stateCode: String,
    message: String,
    cause: Throwable? = null
) : CBenefException("Erro na extração do estado $stateCode: $message", cause)