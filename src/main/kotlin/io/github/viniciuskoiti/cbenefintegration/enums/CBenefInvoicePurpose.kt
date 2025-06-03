/*
 * Copyright (c) 2025 Vinícius Koiti Nakahara
 * Licensed under the MIT License (see LICENSE file)
 */

package io.github.viniciuskoiti.cbenefintegration.enums
enum class CBenefInvoicePurpose(val code: String, val description: String) {
    VENDA("01", "Venda"),
    TRANSFERENCIA("02", "Transferência"),
    DEVOLUCAO("03", "Devolução"),
    CONSIGNACAO("04", "Consignação"),
    DEMONSTRACAO("05", "Demonstração"),
    BRINDE("06", "Brinde"),
    AMOSTRA("07", "Amostra"),
    OUTROS("99", "Outros");

    companion object {
        fun fromCode(code: String): CBenefInvoicePurpose? {
            return entries.find { it.code == code }
        }
    }
}