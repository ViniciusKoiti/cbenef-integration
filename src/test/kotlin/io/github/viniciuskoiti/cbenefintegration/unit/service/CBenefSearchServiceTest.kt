package io.github.viniciuskoiti.cbenefintegration.unit.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.*
import com.v1.nfe.integration.cbenef.service.CBenefIntegrationService
import io.github.viniciuskoiti.cbenefintegration.config.CBenefProperties
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefExtractionResult
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefSourceData
import io.github.viniciuskoiti.cbenefintegration.service.CBenefCacheService
import io.github.viniciuskoiti.cbenefintegration.service.CBenefSearchService
import java.time.LocalDate

class CBenefSearchServiceTest : BehaviorSpec({

    lateinit var mockIntegrationService: CBenefIntegrationService
    lateinit var mockCacheService: CBenefCacheService
    lateinit var searchService: CBenefSearchService

    val sampleBenefits = listOf(
        CBenefSourceData("SC", "850001", "Isenção medicamentos", LocalDate.now()),
        CBenefSourceData("SC", "850002", "Redução alimentícios", LocalDate.now().minusDays(30), LocalDate.now().minusDays(5)), // Este está expirado
        CBenefSourceData("ES", "860001", "Diferimento transferências", LocalDate.now()),
        CBenefSourceData("RJ", "870001", "Crédito outorgado", LocalDate.now())
    )

    fun setupDefaultServiceBehavior() {
        every { mockIntegrationService.getAvailableStates() } returns listOf("SC", "ES", "RJ")
    }

    fun setupMockForAllStates() {
        every { mockIntegrationService.getAvailableStates() } returns listOf("SC", "ES", "RJ")
        every { mockCacheService.getByState("SC") } returns CBenefExtractionResult.success(
            "SC", "Test", sampleBenefits.filter { it.stateCode == "SC" }
        )
        every { mockCacheService.getByState("ES") } returns CBenefExtractionResult.success(
            "ES", "Test", sampleBenefits.filter { it.stateCode == "ES" }
        )
        every { mockCacheService.getByState("RJ") } returns CBenefExtractionResult.success(
            "RJ", "Test", sampleBenefits.filter { it.stateCode == "RJ" }
        )
    }

    fun setupMockForSpecificState(stateCode: String) {
        every { mockCacheService.getByState(stateCode) } returns CBenefExtractionResult.success(
            stateCode, "Test", sampleBenefits.filter { it.stateCode == stateCode }
        )
    }

    beforeEach  {
        mockIntegrationService = mockk()
        mockCacheService = mockk()

        every { mockCacheService.getByState(any()) } returns null
        every { mockIntegrationService.getAvailableStates() } returns emptyList()

        searchService = CBenefSearchService(mockIntegrationService, mockCacheService)
    }

    afterSpec {
        unmockkAll()
    }

    Given("um serviço de busca configurado") {

        When("busca benefícios por código") {
            Then("deve encontrar benefícios que contêm o código") {
                every { mockIntegrationService.getAvailableStates() } returns listOf("SC", "ES")
                every { mockCacheService.getByState("SC") } returns CBenefExtractionResult.success(
                    "SC", "Test", sampleBenefits.filter { it.stateCode == "SC" }
                )
                every { mockCacheService.getByState("ES") } returns CBenefExtractionResult.success(
                    "ES", "Test", sampleBenefits.filter { it.stateCode == "ES" }
                )

                val result = searchService.searchBenefits(code = "850001")

                result shouldHaveSize 1
                result.first().code shouldBe "850001"
                result.first().stateCode shouldBe "SC"

                verify { mockIntegrationService.getAvailableStates() }
                verify { mockCacheService.getByState("SC") }
                verify { mockCacheService.getByState("ES") }
            }
        }

        When("busca benefícios por descrição") {
            Then("deve encontrar benefícios que contêm a descrição") {
                setupMockForAllStates()

                val result = searchService.searchBenefits(description = "medicamentos")

                result shouldHaveSize 1
                result.first().description shouldBe "Isenção medicamentos"
            }
        }

        When("busca apenas benefícios ativos") {
            Then("deve filtrar benefícios expirados") {
                setupMockForAllStates()

                val allResults = searchService.searchBenefits(activeOnly = false)
                val activeResults = searchService.searchBenefits(activeOnly = true)

                allResults shouldHaveSize 4 // Todos
                activeResults shouldHaveSize 3 // Sem o expirado
                activeResults.all { it.isActive() } shouldBe true
            }
        }

        When("busca benefícios por estado específico") {
            Then("deve retornar apenas benefícios do estado") {
                setupMockForAllStates()

                val scResults = searchService.searchBenefits(state = "SC")
                val esResults = searchService.searchBenefits(state = "ES")

                scResults shouldHaveSize 1
                scResults.all { it.stateCode == "SC" } shouldBe true

                esResults shouldHaveSize 1
                esResults.all { it.stateCode == "ES" } shouldBe true
            }
        }

        When("combina múltiplos filtros") {
            Then("deve aplicar todos os filtros") {
                setupMockForAllStates()

                val result = searchService.searchBenefits(
                    description = "Isenção",
                    state = "SC",
                    activeOnly = true
                )

                result shouldHaveSize 1
                result.first().description shouldBe "Isenção medicamentos"
                result.first().stateCode shouldBe "SC"
                result.first().isActive() shouldBe true
            }
        }

        When("busca benefício por código completo") {
            Then("deve encontrar benefício específico") {
                setupMockForSpecificState("SC")

                val found = searchService.findBenefitByCode("SC850001")
                val notFound = searchService.findBenefitByCode("XX999999")

                found.shouldNotBeNull()
                found.getFullCode() shouldBe "SC850001"
                notFound.shouldBeNull()

                verify { mockCacheService.getByState("SC") }
            }
        }

        When("não há cache disponível") {
            Then("deve usar integration service diretamente") {
                val serviceWithoutCache = CBenefSearchService(mockIntegrationService, null)

                every { mockIntegrationService.getAvailableStates() } returns listOf("SC")
                every { mockIntegrationService.extractByState("SC") } returns CBenefExtractionResult.success(
                    "SC", "Direct", sampleBenefits.filter { it.stateCode == "SC" }
                )

                val result = serviceWithoutCache.searchBenefits(state = "SC")

                result shouldHaveSize 1
                verify { mockIntegrationService.extractByState("SC") }
                verify(exactly = 0) { mockCacheService.getByState(any()) }
            }
        }

        When("estado não tem dados") {
            Then("deve retornar lista vazia") {
                every { mockIntegrationService.getAvailableStates() } returns listOf("XX")
                every { mockCacheService.getByState("XX") } returns null

                val result = searchService.searchBenefits(state = "XX")

                result.shouldBeEmpty()
            }
        }

        When("extraction result não é sucesso") {
            Then("deve ignorar dados inválidos") {
                every { mockIntegrationService.getAvailableStates() } returns listOf("SC")
                every { mockCacheService.getByState("SC") } returns CBenefExtractionResult.error("SC", "Erro")

                val result = searchService.searchBenefits(state = "SC")

                result.shouldBeEmpty()
            }
        }
    }
})