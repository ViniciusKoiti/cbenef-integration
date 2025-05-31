package io.github.viniciuskoiti.cbenefintegration.client

import io.github.viniciuskoiti.cbenefintegration.config.CBenefProperties
import io.github.viniciuskoiti.cbenefintegration.core.factory.CBenefExtractorFactory
import io.github.viniciuskoiti.cbenefintegration.service.CBenefSearchService
import com.v1.nfe.integration.cbenef.service.CBenefIntegrationService
import com.v1.nfe.integration.cbenef.service.CBenefService
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefSourceData
class StandaloneCBenefClient {

    private val properties: CBenefProperties = CBenefProperties().apply {
        connection.timeout = 30000
        connection.readTimeout = 60000
        connection.userAgent = "StandaloneApp/1.0"
        connection.maxRetries = 3

        cache.enabled = false
    }
    private val httpClient: CBenefHttpClient = CBenefHttpClient(properties)
    private val downloadClient: CBenefDownloadClient = CBenefDownloadClient(httpClient, properties)
    private val availabilityClient: CBenefAvailabilityClient = CBenefAvailabilityClient(httpClient, properties)
    private val extractorFactory: CBenefExtractorFactory =
        CBenefExtractorFactory(properties, downloadClient, availabilityClient)
    private val integrationService: CBenefIntegrationService = CBenefIntegrationService(extractorFactory)
    private val searchService: CBenefSearchService = CBenefSearchService(integrationService, null)
    private val cbenefService: CBenefService = CBenefService(integrationService, null, searchService, properties)

    suspend fun extrairTodosOsBeneficios(): List<CBenefSourceData> {
        return cbenefService.extractAllDocuments()
    }

    suspend fun extrairPorEstado(estado: String): List<CBenefSourceData> {
        return cbenefService.extractByState(estado)
    }

    fun buscarBeneficios(
        codigo: String? = null,
        descricao: String? = null,
        estado: String? = null,
        apenasAtivos: Boolean = true
    ): List<CBenefSourceData> {
        return cbenefService.searchBenefits(codigo, descricao, estado, apenasAtivos)
    }

    fun buscarPorCodigo(codigoCompleto: String): CBenefSourceData? {
        return cbenefService.findBenefitByCode(codigoCompleto)
    }

    fun getEstadosDisponiveis(): List<String> {
        return cbenefService.getAvailableStates()
    }

    fun verificarDisponibilidade(estado: String): Boolean {
        return availabilityClient.checkSourceAvailability(estado)
    }

    fun isCacheEnabled(): Boolean{
        return properties.isCacheEnabled()
    }
}
