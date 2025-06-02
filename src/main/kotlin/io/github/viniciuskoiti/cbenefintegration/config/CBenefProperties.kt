package io.github.viniciuskoiti.cbenefintegration.config

import io.github.viniciuskoiti.cbenefintegration.dto.CacheProperties
import io.github.viniciuskoiti.cbenefintegration.dto.ConnectionProperties
import io.github.viniciuskoiti.cbenefintegration.dto.SyncProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import jakarta.validation.Valid

@Component
@ConfigurationProperties(prefix = "app.cbenef")
@Validated
data class CBenefProperties(

    @field:Valid
    @NestedConfigurationProperty
    var connection: ConnectionProperties = ConnectionProperties(),

    @field:Valid
    @NestedConfigurationProperty
    var cache: CacheProperties = CacheProperties(),

    @field:Valid
    @NestedConfigurationProperty
    var sync: SyncProperties = SyncProperties(),

    @field:Valid
    var states: Map<String, StateConfig> = getDefaultStatesConfig()

) {

    fun  getEnabledStates(): List<String> {
        return states.filter { it.value.enabled }.keys.sorted()
    }

    fun isStateEnabled(stateCode: String): Boolean {
        return states[stateCode]?.enabled ?: false
    }

    fun getConnectionTimeout(stateCode: String): Long {
        return states[stateCode]?.customTimeout ?: connection.timeout
    }

    fun getReadTimeout(stateCode: String): Long {
        return states[stateCode]?.customReadTimeout ?: connection.readTimeout
    }

    fun getCustomHeaders(stateCode: String): Map<String, String> {
        return states[stateCode]?.customHeaders ?: emptyMap()
    }

    fun getMaxRetries(stateCode: String): Int {
        return states[stateCode]?.customMaxRetries ?: connection.maxRetries
    }

    fun getSourceUrl(stateCode: String): String? {
        return states[stateCode]?.sourceUrl
    }

    fun getPriority(stateCode: String): Int {
        return states[stateCode]?.priority ?: 99
    }

    fun isCacheEnabled(): Boolean{
        return cache.enabled;
    }

    companion object {
        fun getDefaultStatesConfig(): Map<String, StateConfig> {
            return mapOf(
                "SC" to StateConfig(
                    enabled = true,
                    priority = 1,
                    sourceUrl = "https://www.sef.sc.gov.br/api-portal/Documento/Ver/1188",
                    customTimeout = 15000,
                    customHeaders = mapOf(
                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                    )
                ),
                "ES" to StateConfig(
                    enabled = true,
                    priority = 2,
                    sourceUrl = "https://sefaz.es.gov.br/Media/Sefaz/Receita%20Estadual/GEFIS/cBenef%20ES%20V6.pdf",
                    customTimeout = 45000,
                    customReadTimeout = 90000,
                    customHeaders = mapOf(
                        "Accept" to "application/pdf"
                    )
                ),
                "RJ" to StateConfig(
                    enabled = true,
                    priority = 3,
                    sourceUrl = "https://portal.fazenda.rj.gov.br/dfe/wp-content/uploads/sites/17/2023/10/Tabela-codigo-de-beneficio-X-CST.pdf",
                    customTimeout = 60000,
                    customReadTimeout = 120000,
                    customMaxRetries = 5,
                    customHeaders = mapOf(
                        "Accept" to "application/pdf",
                        "Referer" to "https://portal.fazenda.rj.gov.br"
                    )
                ),
                "PR" to StateConfig(
                    enabled = false,
                    priority = 4,
                    sourceUrl = "http://sped.fazenda.pr.gov.br/sites/sped/arquivos_restritos/files/documento/2025-04/TABELA_5_2_COMPLETA.pdf",
                    customTimeout = 30000,
                    customHeaders = mapOf("Accept" to "application/pdf")
                ),
                "RS" to StateConfig(
                    enabled = false,
                    priority = 5,
                    sourceUrl = null,
                    customHeaders = mapOf(
                        "Accept" to "application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    )
                ),
                "GO" to StateConfig(
                    enabled = false,
                    priority = 6,
                    sourceUrl = "https://appasp.economia.go.gov.br/legislacao/arquivos/Secretario/IN/IN_1518_2022.htm",
                    customTimeout = 25000,
                    customHeaders = mapOf("Accept" to "text/html")
                ),
                "DF" to StateConfig(
                    enabled = false,
                    priority = 7,
                    sourceUrl = null
                )
            )
        }
    }
}