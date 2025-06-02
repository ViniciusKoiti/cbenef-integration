package io.github.viniciuskoiti.cbenefintegration.unit.service

import io.github.viniciuskoiti.cbenefintegration.service.CBenefIntegrationService
import io.github.viniciuskoiti.cbenefintegration.config.CBenefProperties
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefExtractionResult
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefSourceData
import io.github.viniciuskoiti.cbenefintegration.dto.CacheProperties
import io.github.viniciuskoiti.cbenefintegration.service.CBenefCacheService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate

class CBenefCacheServiceTest : BehaviorSpec({

    var mockIntegrationService: CBenefIntegrationService? = null
    var mockProperties: CBenefProperties?
    var mockCacheProperties: CacheProperties?
    var cBenefCacheService: CBenefCacheService? = null

    val sampleBenefitsSC = listOf(
        CBenefSourceData("SC", "850001", "Isenção medicamentos", LocalDate.now()),
        CBenefSourceData("SC", "850002", "Redução alimentícios", LocalDate.now())
    )

    val sampleBenefitsES = listOf(
        CBenefSourceData("ES", "860001", "Diferimento transferências", LocalDate.now())
    )

    val sampleBenefitsRJ = listOf(
        CBenefSourceData("RJ", "870001", "Benefício fiscal RJ", LocalDate.now())
    )

    beforeEach {
        mockCacheProperties = mockk(relaxed = true) {
            every { getTtlForState(any()) } returns 1440L
        }

        mockProperties = mockk(relaxed = true) {
            every { cache } returns mockCacheProperties!!
        }

        mockIntegrationService = mockk(relaxed = true) {
            every { getAvailableStates() } returns listOf("SC", "ES", "RJ")
            every { extractByState(any()) } returns null // Default
        }

        cBenefCacheService = CBenefCacheService(mockIntegrationService!!, mockProperties!!)
    }

    Given("Um serviço de cache configurado") {

        When("buscar por estado único") {
            Then("deve cachear resultado na segunda chamada") {
                // Arrange
                val mockResult = CBenefExtractionResult.success("SC", "Test", sampleBenefitsSC)
                every { mockIntegrationService!!.extractByState("SC") } returns mockResult

                // Act
                val firstCall = cBenefCacheService!!.getByState("SC")
                val secondCall = cBenefCacheService!!.getByState("SC")

                // Assert
                firstCall shouldBe mockResult
                secondCall shouldBe mockResult
                verify(exactly = 1) { mockIntegrationService!!.extractByState("SC") }
            }
        }

        When("buscar todos os estados") {
            Then("deve retornar apenas resultados bem-sucedidos") {
                // Arrange
                val resultSC = CBenefExtractionResult.success("SC", "SC Source", sampleBenefitsSC)
                val resultES = CBenefExtractionResult.success("ES", "ES Source", sampleBenefitsES)
                val resultRJ = CBenefExtractionResult.error("RJ", "Fonte indisponível")

                every { mockIntegrationService!!.extractByState("SC") } returns resultSC
                every { mockIntegrationService!!.extractByState("ES") } returns resultES
                every { mockIntegrationService!!.extractByState("RJ") } returns resultRJ

                // Act
                val result = cBenefCacheService!!.getAllStates()

                // Assert
                result shouldHaveSize 3 // Apenas SC e ES (sucessos)
                result shouldContainKey "SC"
                result shouldContainKey "ES"
                result["SC"] shouldBe resultSC
                result["ES"] shouldBe resultES

                verify(exactly = 1) { mockIntegrationService!!.extractByState("SC") }
                verify(exactly = 1) { mockIntegrationService!!.extractByState("ES") }
                verify(exactly = 1) { mockIntegrationService!!.extractByState("RJ") }
            }
        }

        When("buscar múltiplos estados específicos") {
            Then("deve retornar apenas os solicitados") {
                // Arrange
                val resultSC = CBenefExtractionResult.success("SC", "SC Source", sampleBenefitsSC)
                val resultES = CBenefExtractionResult.success("ES", "ES Source", sampleBenefitsES)

                every { mockIntegrationService!!.extractByState("SC") } returns resultSC
                every { mockIntegrationService!!.extractByState("ES") } returns resultES

                // Act
                val result = cBenefCacheService!!.getMultipleStates(listOf("SC", "ES"))

                // Assert
                result shouldHaveSize 2
                result shouldContainKey "SC"
                result shouldContainKey "ES"

                verify(exactly = 1) { mockIntegrationService!!.extractByState("SC") }
                verify(exactly = 1) { mockIntegrationService!!.extractByState("ES") }
                verify(exactly = 0) { mockIntegrationService!!.extractByState("RJ") }
            }
        }
    }

    Given("Cenários de erro") {

        When("extração falhar") {
            Then("não deve cachear e deve tentar novamente") {
                // Arrange
                val errorResult = CBenefExtractionResult.error("SC", "Erro na fonte")
                every { mockIntegrationService!!.extractByState("SC") } returns errorResult

                // Act
                val firstCall = cBenefCacheService!!.getByState("SC")
                val secondCall = cBenefCacheService!!.getByState("SC")

                // Assert
                firstCall shouldBe errorResult
                secondCall shouldBe errorResult

                // Deve chamar 2 vezes pois não cacheia erros
                verify(exactly = 2) { mockIntegrationService!!.extractByState("SC") }
            }
        }

        When("serviço retornar null") {
            Then("deve retornar null") {
                // Arrange
                every { mockIntegrationService!!.extractByState("SC") } returns null

                // Act
                val result = cBenefCacheService!!.getByState("SC")

                // Assert
                result shouldBe null
                verify(exactly = 1) { mockIntegrationService!!.extractByState("SC") }
            }
        }
    }

    Given("Operações de limpeza") {

        When("limpar todo o cache") {
            Then("deve forçar nova extração") {
                // Arrange
                val mockResult = CBenefExtractionResult.success("SC", "Test Source", sampleBenefitsSC)
                every { mockIntegrationService!!.extractByState("SC") } returns mockResult

                // Primeira chamada (cacheia)
                cBenefCacheService!!.getByState("SC")

                // Act
                cBenefCacheService!!.clearCache()
                val resultAfterClear = cBenefCacheService!!.getByState("SC")

                // Assert
                resultAfterClear shouldBe mockResult
                verify(exactly = 2) { mockIntegrationService!!.extractByState("SC") }
            }
        }

        When("limpar cache específico") {
            Then("deve afetar apenas o estado especificado") {
                // Arrange
                val mockResultSC = CBenefExtractionResult.success("SC", "SC Source", sampleBenefitsSC)
                val mockResultES = CBenefExtractionResult.success("ES", "ES Source", sampleBenefitsES)

                every { mockIntegrationService!!.extractByState("SC") } returns mockResultSC
                every { mockIntegrationService!!.extractByState("ES") } returns mockResultES

                // Cacheia ambos
                cBenefCacheService!!.getByState("SC")
                cBenefCacheService!!.getByState("ES")

                // Act
                cBenefCacheService!!.clearCacheForState("SC")
                val resultSCAfterClear = cBenefCacheService!!.getByState("SC")
                val resultESAfterClear = cBenefCacheService!!.getByState("ES")

                // Assert
                resultSCAfterClear shouldBe mockResultSC
                resultESAfterClear shouldBe mockResultES

                verify(exactly = 2) { mockIntegrationService!!.extractByState("SC") } // 2x: cache + after clear
                verify(exactly = 1) { mockIntegrationService!!.extractByState("ES") } // 1x: ainda no cache
            }
        }
    }

    Given("Verificações de estado") {

        When("verificar se está no cache") {
            Then("deve retornar status correto") {
                // Arrange
                val mockResult = CBenefExtractionResult.success("SC", "Test Source", sampleBenefitsSC)
                every { mockIntegrationService!!.extractByState("SC") } returns mockResult

                // Act & Assert
                cBenefCacheService!!.isCached("SC") shouldBe false
                cBenefCacheService!!.getByState("SC")
                cBenefCacheService!!.isCached("SC") shouldBe true
            }
        }

        When("obter estatísticas do cache") {
            Then("deve retornar informações corretas") {
                // Arrange
                val mockResultSC = CBenefExtractionResult.success("SC", "SC Source", sampleBenefitsSC)
                val mockResultES = CBenefExtractionResult.success("ES", "ES Source", sampleBenefitsES)

                every { mockIntegrationService!!.extractByState("SC") } returns mockResultSC
                every { mockIntegrationService!!.extractByState("ES") } returns mockResultES

                // Act
                cBenefCacheService!!.getByState("SC")
                cBenefCacheService!!.getByState("ES")
                val stats = cBenefCacheService!!.getStats()

                // Assert
                stats shouldContainKey "totalStatesCached"
                stats shouldContainKey "totalBenefitsCached"
                stats shouldContainKey "cacheEntries"

                stats["totalStatesCached"] shouldBe 2
                stats["totalBenefitsCached"] shouldBe 3 // 2 SC + 1 ES
            }
        }
    }
})