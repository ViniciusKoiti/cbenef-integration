package com.v1.nfe.integration.cbenef.exception

class CBenefSourceUnavailableException(
    val stateCode: String,
    val sourceUrl: String,
    message: String = "Fonte indisponível"
) : CBenefException("Fonte do estado $stateCode indisponível ($sourceUrl): $message")
