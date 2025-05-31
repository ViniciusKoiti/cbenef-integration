package io.github.viniciuskoiti.cbenefintegration.unit.extractor

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.mockk.*
import io.github.viniciuskoiti.cbenefintegration.core.extractor.SCCBenefExtractor
import io.github.viniciuskoiti.cbenefintegration.config.CBenefProperties
import io.github.viniciuskoiti.cbenefintegration.client.CBenefDownloadClient
import io.github.viniciuskoiti.cbenefintegration.client.CBenefAvailabilityClient
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefSourceData
import io.github.viniciuskoiti.cbenefintegration.enums.CBenefBenefitType
import io.github.viniciuskoiti.cbenefintegration.enums.ExtractionStatus
import io.kotest.matchers.string.shouldStartWith
import java.io.ByteArrayInputStream
import java.net.http.HttpResponse
import java.time.LocalDate

class SCCBenefExtractorTest : BehaviorSpec({

    lateinit var mockConfig: CBenefProperties
    lateinit var mockDownloadClient: CBenefDownloadClient
    lateinit var mockAvailabilityClient: CBenefAvailabilityClient
    lateinit var extractor: SCCBenefExtractor

    beforeSpec {
        mockConfig = mockk<CBenefProperties>()
        mockDownloadClient = mockk<CBenefDownloadClient>()
        mockAvailabilityClient = mockk<CBenefAvailabilityClient>()

        // Configurar comportamentos básicos
        every { mockConfig.isStateEnabled("SC") } returns true
        every { mockConfig.getSourceUrl("SC") } returns "https://example.com/sc-cbenef.pdf"
        every { mockConfig.getConnectionTimeout("SC") } returns 30000
        every { mockConfig.getReadTimeout("SC") } returns 60000
        every { mockConfig.getMaxRetries("SC") } returns 3
        every { mockConfig.getCustomHeaders("SC") } returns emptyMap()
        every { mockConfig.getPriority("SC") } returns 1
        extractor = spyk(SCCBenefExtractor(mockConfig, mockDownloadClient, mockAvailabilityClient))
    }

    afterSpec {
        unmockkAll()
    }

    Given("um extrator de SC configurado") {

        When("verifica disponibilidade da fonte") {
            every { mockAvailabilityClient.checkSourceAvailability("SC") } returns true

            Then("deve retornar verdadeiro") {
                extractor.isSourceAvailable() shouldBe true
                verify { mockAvailabilityClient.checkSourceAvailability("SC") }
            }
        }

        When("propriedades básicas são verificadas") {
            Then("deve retornar valores corretos") {
                extractor.stateCode shouldBe "SC"
                extractor.supportedFormats.shouldNotBeEmpty()
                extractor.sourceName.shouldNotBeEmpty()
                extractor.getDisplayName().shouldNotBeEmpty()
            }
        }

        When("extrai dados com fonte disponível") {
            val mockResponse = mockk<HttpResponse<java.io.InputStream>>(relaxed = true)

            val realInputStream = ByteArrayInputStream("dummy content".toByteArray())

            val expectedBenefits = listOf(
                CBenefSourceData(
                    stateCode = "SC",
                    code = "850001",
                    description = "Isenção ICMS - Produtos alimentícios",
                    startDate = LocalDate.of(2023, 1, 1),
                    endDate = LocalDate.of(2025, 12, 31),
                    benefitType = CBenefBenefitType.ISENCAO
                ),
                CBenefSourceData(
                    stateCode = "SC",
                    code = "850002",
                    description = "Redução base de cálculo - Energia elétrica",
                    startDate = LocalDate.of(2023, 3, 15),
                    endDate = null,
                    benefitType = CBenefBenefitType.REDUCAO_BASE
                )
            )

            every { mockAvailabilityClient.checkSourceAvailability("SC") } returns true
            every { mockDownloadClient.downloadDocument("SC") } returns mockResponse
            every { mockResponse.body() } returns realInputStream

            every { extractor.extractFromDocument(any()) } returns expectedBenefits

            Then("deve extrair benefícios corretamente") {
                val result = extractor.extract()

                result.status shouldBe ExtractionStatus.SUCCESS
                result.stateCode shouldBe "SC"
                result.data.shouldNotBeEmpty()
                result.data shouldHaveSize 2

                result.data.forEach { benefit ->
                    benefit.stateCode shouldBe "SC"
                    benefit.code.shouldNotBeEmpty()
                    benefit.description.shouldNotBeEmpty()
                    benefit.getFullCode().shouldStartWith("SC")
                }

                verify { mockAvailabilityClient.checkSourceAvailability("SC") }
                verify { mockDownloadClient.downloadDocument("SC") }
                verify { extractor.extractFromDocument(any()) }
            }
        }

        When("fonte está indisponível") {
            every { mockAvailabilityClient.checkSourceAvailability("SC") } returns false

            Then("deve retornar resultado de fonte indisponível") {
                val result = extractor.extract()

                result.status shouldBe ExtractionStatus.SOURCE_UNAVAILABLE
                result.data shouldHaveSize 0

                verify { mockAvailabilityClient.checkSourceAvailability("SC") }
            }
        }
    }
})

private fun createMockPdfStream(): ByteArrayInputStream {
    // Criar um stream simples para teste
    val content = """
        CBenef Santa Catarina
        SC850001 Isenção ICMS - Produtos alimentícios 01/01/2023 31/12/2025
        SC850002 Redução base de cálculo - Energia elétrica 15/03/2023
    """.trimIndent()

    return ByteArrayInputStream(content.toByteArray())
}