package io.github.viniciuskoiti.cbenefintegration.config


import io.github.viniciuskoiti.cbenefintegration.client.CBenefAvailabilityClient
import io.github.viniciuskoiti.cbenefintegration.client.CBenefDownloadClient
import io.github.viniciuskoiti.cbenefintegration.client.CBenefHttpClient
import com.v1.nfe.integration.cbenef.config.CBenefProperties
import io.github.viniciuskoiti.cbenefintegration.core.factory.CBenefExtractorFactory
import com.v1.nfe.integration.cbenef.service.CBenefCacheService
import com.v1.nfe.integration.cbenef.service.CBenefIntegrationService
import com.v1.nfe.integration.cbenef.service.CBenefSearchService
import com.v1.nfe.integration.cbenef.service.CBenefService
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(CBenefProperties::class)
@ConditionalOnProperty(
    prefix = "app.cbenef",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class CBenefAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun cbenefHttpClient(properties: CBenefProperties): CBenefHttpClient {
        return CBenefHttpClient(properties)
    }

    @Bean
    @ConditionalOnMissingBean
    fun cbenefDownloadClient(
        httpClient: CBenefHttpClient,
        properties: CBenefProperties
    ): CBenefDownloadClient {
        return CBenefDownloadClient(httpClient, properties)
    }

    @Bean
    @ConditionalOnMissingBean
    fun cbenefAvailabilityClient(
        httpClient: CBenefHttpClient,
        properties: CBenefProperties
    ): CBenefAvailabilityClient {
        return CBenefAvailabilityClient(httpClient, properties)
    }

    @Bean
    @ConditionalOnMissingBean
    fun cbenefExtractorFactory(
        properties: CBenefProperties,
        downloadClient: CBenefDownloadClient,
        availabilityClient: CBenefAvailabilityClient
    ): CBenefExtractorFactory {
        return CBenefExtractorFactory(properties, downloadClient, availabilityClient)
    }

    @Bean
    @ConditionalOnMissingBean
    fun cbenefIntegrationService(
        extractorFactory: CBenefExtractorFactory
    ): CBenefIntegrationService {
        return CBenefIntegrationService(extractorFactory)
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty("app.cbenef.cache.enabled", havingValue = "true")
    fun cbenefCacheService(
        integrationService: CBenefIntegrationService,
        properties: CBenefProperties
    ): CBenefCacheService {
        return CBenefCacheService(integrationService, properties)
    }

    @Bean
    @ConditionalOnMissingBean
    fun cbenefSearchService(
        integrationService: CBenefIntegrationService,
        cacheService: CBenefCacheService?
    ): CBenefSearchService {
        return CBenefSearchService(integrationService, cacheService)
    }

    @Bean
    @ConditionalOnMissingBean
    fun cbenefService(
        integrationService: CBenefIntegrationService,
        cacheService: CBenefCacheService?,
        searchService: CBenefSearchService,
        properties: CBenefProperties
    ): CBenefService {
        return CBenefService(integrationService, cacheService, searchService, properties)
    }
}