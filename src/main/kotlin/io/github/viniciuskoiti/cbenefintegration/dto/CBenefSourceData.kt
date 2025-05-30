package io.github.viniciuskoiti.cbenefintegration.dto

import io.github.viniciuskoiti.cbenefintegration.enums.CBenefBenefitType
import io.github.viniciuskoiti.cbenefintegration.enums.CBenefInvoicePurpose
import java.time.LocalDate

data class CBenefSourceData(
    val stateCode: String,
    val code: String,           // Código sem UF (ex: B010001)
    val description: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val invoicePurpose: CBenefInvoicePurpose? = null,
    val benefitType: CBenefBenefitType? = null,
    val applicableCSTs: List<String> = emptyList(),
    val cstSpecific: Boolean = false,
    val notes: String? = null,
    val sourceMetadata: Map<String, String> = emptyMap()
) {
    fun getFullCode(): String = "${stateCode}${code}"

    fun isActive(referenceDate: LocalDate = LocalDate.now()): Boolean {
        return referenceDate >= startDate && (endDate == null || referenceDate <= endDate)
    }

    fun isApplicableForCST(cst: String): Boolean {
        return !cstSpecific || applicableCSTs.contains(cst)
    }

    fun isApplicableForProduct(cst: String, referenceDate: LocalDate = LocalDate.now()): Boolean {
        return isActive(referenceDate) && isApplicableForCST(cst)
    }
}
