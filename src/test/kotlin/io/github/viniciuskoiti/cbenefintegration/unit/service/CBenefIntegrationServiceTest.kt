package io.github.viniciuskoiti.cbenefintegration.unit.service

import io.github.viniciuskoiti.cbenefintegration.core.CBenefExtractor
import io.github.viniciuskoiti.cbenefintegration.core.factory.CBenefExtractorFactory
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefExtractionResult
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefSourceData
import io.github.viniciuskoiti.cbenefintegration.enums.DocumentFormat
import io.github.viniciuskoiti.cbenefintegration.enums.ExtractionStatus
import io.github.viniciuskoiti.cbenefintegration.exception.CBenefExtractionException
import com.v1.nfe.integration.cbenef.service.CBenefIntegrationService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import java.time.LocalDateTime

class CBenefIntegrationServiceTest : BehaviorSpec({

    var mockExtractorFactory: CBenefExtractorFactory? = null
    var mockExtractorSC: CBenefExtractor? = null
    var mockExtractorES: CBenefExtractor? = null
    var integrationService: CBenefIntegrationService? = null

    val sampleBenefitsSC = listOf(
        CBenefSourceData("SC", "850001", "Isenção medicamentos", LocalDate.now()),
        CBenefSourceData("SC", "850002", "Redução alimentícios", LocalDate.now())
    )

    val sampleBenefitsES = listOf(
        CBenefSourceData("ES", "860001", "Diferimento transferências", LocalDate.now())
    )

    beforeEach {
        // Criar mocks dos extractors
        mockExtractorSC = mockk(relaxed = true) {
            every { stateCode } returns "SC"
            every { isEnabled() } returns true
            every { getPriority() } returns 1
            every { getDisplayName() } returns "CBenef SC (PDF)"
            every { supportedFormats } returns listOf(DocumentFormat.PDF)
            every { sourceName } returns "SEFAZ SC - CBenef"
            every { sourceUrl } returns "https://test.sc.gov.br/cbenef.pdf"
            every { connectionTimeout } returns 30000L
            every { readTimeout } returns 60000L
            every { maxRetries } returns 3
        }

        mockExtractorES = mockk(relaxed = true) {
            every { stateCode } returns "ES"
            every { isEnabled() } returns true
            every { getPriority() } returns 2
            every { getDisplayName() } returns "CBenef ES (PDF)"
            every { supportedFormats } returns listOf(DocumentFormat.PDF)
            every { sourceName } returns "SEFAZ ES - CBenef"
            every { sourceUrl } returns "https://test.es.gov.br/cbenef.pdf"
            every { connectionTimeout } returns 30000L
            every { readTimeout } returns 60000L
            every { maxRetries } returns 3
        }

        mockExtractorFactory = mockk(relaxed = true) {
            every { createExtractor("SC") } returns mockExtractorSC
            every { createExtractor("ES") } returns mockExtractorES
            every { createExtractor("RJ") } returns null // Estado não suportado
            every { getAvailableExtractors() } returns listOf(mockExtractorSC!!, mockExtractorES!!)
        }

        integrationService = CBenefIntegrationService(mockExtractorFactory!!)
    }

    Given("Serviço de integração configurado") {

        When("obter estados disponíveis") {
            Then("deve retornar apenas estados habilitados") {
                // Act
                val states = integrationService!!.getAvailableStates()

                // Assert
                states shouldHaveSize 2
                states shouldContain "SC"
                states shouldContain "ES"
                states shouldNotContain "RJ"
            }
        }

        When("extrair por estado existente") {
            Then("deve retornar resultado bem-sucedido") {
                // Arrange
                val expectedResult = CBenefExtractionResult.success("SC", "SEFAZ SC", sampleBenefitsSC)
                every { mockExtractorSC!!.extract() } returns expectedResult

                // Act
                val result = integrationService!!.extractByState("SC")

                // Assert
                result.shouldNotBeNull()
                result shouldBe expectedResult
                result.status shouldBe ExtractionStatus.SUCCESS
                result.data shouldHaveSize 2
                verify { mockExtractorSC!!.extract() }
            }
        }

        When("extrair por estado inexistente") {
            Then("deve retornar null") {
                // Act
                val result = integrationService!!.extractByState("RJ")

                // Assert
                result.shouldBeNull()
            }
        }

        When("extrair por estado desabilitado") {
            Then("deve retornar null") {
                // Arrange
                every { mockExtractorSC!!.isEnabled() } returns false

                // Act
                val result = integrationService!!.extractByState("SC")

                // Assert
                result.shouldBeNull()
                verify(exactly = 0) { mockExtractorSC!!.extract() }
            }
        }
    }

    Given("Cenários de erro na extração") {

        When("extrator lançar CBenefExtractionException") {
            Then("deve retornar resultado de erro") {
                // Arrange
                val exception = CBenefExtractionException("SC", "Erro na fonte")
                every { mockExtractorSC!!.extract() } throws exception

                // Act
                val result = integrationService!!.extractByState("SC")

                // Assert
                result.shouldNotBeNull()
                result.status shouldBe ExtractionStatus.ERROR
                result.errorMessage shouldContain "Erro na fonte"
                result.data shouldHaveSize 0
            }
        }

        When("extrator lançar exceção genérica") {
            Then("deve retornar resultado de erro inesperado") {
                // Arrange
                val exception = RuntimeException("Erro inesperado")
                every { mockExtractorSC!!.extract() } throws exception

                // Act
                val result = integrationService!!.extractByState("SC")

                // Assert
                result.shouldNotBeNull()
                result.status shouldBe ExtractionStatus.ERROR
                result.errorMessage shouldContain "Erro inesperado"
            }
        }
    }

    Given("Extração de múltiplos estados") {

        When("extrair todos os estados") {
            Then("deve retornar mapa com todos os resultados") {
                // Arrange
                val resultSC = CBenefExtractionResult.success("SC", "SEFAZ SC", sampleBenefitsSC)
                val resultES = CBenefExtractionResult.success("ES", "SEFAZ ES", sampleBenefitsES)

                every { mockExtractorSC!!.extract() } returns resultSC
                every { mockExtractorES!!.extract() } returns resultES

                // Act
                val results = integrationService!!.extractAllStates()

                // Assert
                results shouldHaveSize 2
                results shouldContainKey "SC"
                results shouldContainKey "ES"
                results["SC"] shouldBe resultSC
                results["ES"] shouldBe resultES
            }
        }

        When("extrair estados específicos") {
            Then("deve retornar apenas os solicitados") {
                // Arrange
                val resultSC = CBenefExtractionResult.success("SC", "SEFAZ SC", sampleBenefitsSC)
                val resultES = CBenefExtractionResult.success("ES", "SEFAZ ES", sampleBenefitsES)

                every { mockExtractorSC!!.extract() } returns resultSC
                every { mockExtractorES!!.extract() } returns resultES

                // Act
                val results = integrationService!!.extractMultipleStates(listOf("SC", "ES"))

                // Assert
                results shouldHaveSize 2
                results shouldContainKey "SC"
                results shouldContainKey "ES"
                verify { mockExtractorSC!!.extract() }
                verify { mockExtractorES!!.extract() }
            }
        }

        When("alguns estados falharem") {
            Then("deve retornar apenas sucessos") {
                // Arrange
                val resultSC = CBenefExtractionResult.success("SC", "SEFAZ SC", sampleBenefitsSC)
                val exception = CBenefExtractionException("ES", "Fonte indisponível")

                every { mockExtractorSC!!.extract() } returns resultSC
                every { mockExtractorES!!.extract() } throws exception

                // Act
                val results = integrationService!!.extractAllStates()

                // Assert
                results shouldHaveSize 2 // SC success + ES error
                results shouldContainKey "SC"
                results shouldContainKey "ES"
                results["SC"]?.status shouldBe ExtractionStatus.SUCCESS
                results["ES"]?.status shouldBe ExtractionStatus.ERROR
            }
        }
    }

    Given("Informações do extrator") {

        When("obter informações de estado válido") {
            Then("deve retornar detalhes do extrator") {
                // Act
                val info = integrationService!!.getExtractorInfo("SC")

                // Assert
                info.shouldNotBeNull()
                info shouldContainKey "stateCode"
                info shouldContainKey "sourceName"
                info shouldContainKey "sourceUrl"
                info shouldContainKey "supportedFormats"
                info shouldContainKey "isEnabled"
                info shouldContainKey "priority"
                info shouldContainKey "displayName"
                info shouldContainKey "connectionTimeout"
                info shouldContainKey "readTimeout"
                info shouldContainKey "maxRetries"

                info["stateCode"] shouldBe "SC"
                info["sourceName"] shouldBe "SEFAZ SC - CBenef"
                info["isEnabled"] shouldBe true
                info["priority"] shouldBe 1
            }
        }

        When("obter informações de estado inexistente") {
            Then("deve retornar null") {
                // Act
                val info = integrationService!!.getExtractorInfo("RJ")

                // Assert
                info.shouldBeNull()
            }
        }
    }

    Given("Gerenciamento de recursos") {

        When("finalizar serviço") {
            Then("deve encerrar executor corretamente") {
                integrationService!!.shutdown()

            }
        }
    }
})