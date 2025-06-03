/*
 * Copyright (c) 2025 Vinícius Koiti Nakahara
 * Licensed under the MIT License (see LICENSE file)
 */

package io.github.viniciuskoiti.cbenefintegration.enums

enum class CBenefBenefitType(val code: String, val description: String) {
    ISENCAO("1", "Isenção"),
    NAO_INCIDENCIA("2", "Não Incidência"),
    REDUCAO_BASE("3", "Redução de Base de Cálculo"),
    DIFERIMENTO("4", "Diferimento"),
    SUSPENSAO("5", "Suspensão"),
    ALIQUOTA_ZERO("6", "Alíquota Zero"),
    CREDITO_OUTORGADO("7", "Crédito Outorgado"),
    OUTROS("9", "Outros");

    companion object {
        fun fromCode(code: String): CBenefBenefitType? {
            return entries.find { it.code == code }
        }
    }
}