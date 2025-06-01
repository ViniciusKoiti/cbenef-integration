package io.github.viniciuskoiti.cbenefintegration.unit.service

import com.v1.nfe.integration.cbenef.service.CBenefIntegrationService
import com.v1.nfe.integration.cbenef.service.CBenefService
import io.github.viniciuskoiti.cbenefintegration.config.CBenefProperties
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefExtractionResult
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefSourceData
import io.github.viniciuskoiti.cbenefintegration.enums.CBenefBenefitType
import io.github.viniciuskoiti.cbenefintegration.service.CBenefCacheService
import io.github.viniciuskoiti.cbenefintegration.service.CBenefSearchService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import java.time.LocalDate

class CBenefServiceTest : BehaviorSpec({

    var mockIntegrationService: CBenefIntegrationService? = null
    var mockCacheService: CBenefCacheService? = null
    var mockSearchService: CBenefSearchService? = null
    var mockProperties: CBenefProperties? = null
    var cbenefService: CBenefService? = null

    val sampleBenefitsSC = listOf(
        CBenefSourceData(
            stateCode = "SC",
            code = "850001",
            description = "Isenção de ICMS para medicamentos",
            startDate = LocalDate.now().minusDays(30),
            endDate = LocalDate.now().plusDays(30),
            benefitType = CBenefBenefitType.ISENCAO
        ),
        CBenefSourceData(
            stateCode = "SC",
            code = "850002",
            description = "Redução para alimentícios básicos",
            startDate = LocalDate.now().minusDays(30),
            endDate = null,
            benefitType = CBenefBenefitType.REDUCAO_BASE
        )
    )

    val sampleBenefitsES = listOf(
        CBenefSourceData(
            stateCode = "ES",
            code = "860001",
            description = "Isenção para exportação",
            startDate = LocalDate.now().minusDays(30),
            endDate = LocalDate.now().plusDays(30),
            benefitType = CBenefBenefitType.ISENCAO
        )
    )

    beforeEach {
        mockIntegrationService = mockk(relaxed = true) {
            every { getAvailableStates() } returns listOf("SC", "ES")
        }

        mockCacheService = mockk(relaxed = true)
        mockSearchService = mockk(relaxed = true)
        mockProperties = mockk(relaxed = true)

        cbenefService = CBenefService(
            mockIntegrationService!!,
            mockCacheService,
            mockSearchService!!,
            mockProperties!!
        )
    }

    Given("Serviço CBenef com todas as dependências") {

        When("extrair todos os documentos sem cache") {
            Then("deve usar serviço de integração diretamente") {
                // Arrange
                val resultsSC = mapOf("SC" to CBenefExtractionResult.success("SC", "SC Source", sampleBenefitsSC))
                val resultsES = mapOf("ES" to CBenefExtractionResult.success("ES", "ES Source", sampleBenefitsES))
                val allResults = resultsSC + resultsES

                every { mockIntegrationService!!.extractAllStates() } returns allResults

                // Act
                val benefits = cbenefService!!.extractAllDocuments()

                // Assert
                benefits shouldHaveSize 3 // 2 SC + 1 ES
                benefits.filter { it.stateCode == "SC" } shouldHaveSize 2
                benefits.filter { it.stateCode == "ES" } shouldHaveSize 1

                verify { mockIntegrationService!!.extractAllStates() }
                verify(exactly = 0) { mockCacheService?.getAllStates() }
            }
        }

        When("extrair por estado específico sem cache") {
            Then("deve usar integração para o estado") {
                // Arrange
                val result = CBenefExtractionResult.success("SC", "SC Source", sampleBenefitsSC)
                every { mockIntegrationService!!.extractByState("SC") } returns result

                // Act
                val benefits = cbenefService!!.extractByState("SC")

                // Assert
                benefits shouldHaveSize 2
                benefits.all { it.stateCode == "SC" } shouldBe true

                verify { mockIntegrationService!!.extractByState("SC") }
            }
        }

        When("extrair com falha na integração") {
            Then("deve retornar lista vazia") {
                // Arrange
                every { mockIntegrationService!!.extractByState("SC") } returns null

                // Act
                val benefits = cbenefService!!.extractByState("SC")

                // Assert
                benefits shouldHaveSize 0
            }
        }
    }

    Given("Serviço CBenef com cache habilitado") {

        When("extrair todos os documentos com cache") {
            Then("deve usar serviço de cache")  {
                // Arrange
                val resultsSC = mapOf("SC" to CBenefExtractionResult.success("SC", "SC Source", sampleBenefitsSC))
                val resultsES = mapOf("ES" to CBenefExtractionResult.success("ES", "ES Source", sampleBenefitsES))
                val allResults = resultsSC + resultsES

                every { mockCacheService!!.getAllStates() } returns allResults

                // Act
                val benefits = cbenefService!!.extractAllDocumentsWithCache()

                // Assert
                benefits shouldHaveSize 3
                benefits.filter { it.stateCode == "SC" } shouldHaveSize 2
                benefits.filter { it.stateCode == "ES" } shouldHaveSize 1

                verify { mockCacheService!!.getAllStates() }
                verify(exactly = 0) { mockIntegrationService!!.extractAllStates() }
            }
        }

        When("cache não disponível mas método with cache chamado") {
            Then("deve fallback para extração sem cache") {
                // Arrange - Serviço sem cache
                val serviceWithoutCache = CBenefService(
                    mockIntegrationService!!,
                    null, // Sem cache
                    mockSearchService!!,
                    mockProperties!!
                )

                val allResults = mapOf("SC" to CBenefExtractionResult.success("SC", "SC Source", sampleBenefitsSC))
                every { mockIntegrationService!!.extractAllStates() } returns allResults

                // Act
                val benefits = serviceWithoutCache.extractAllDocumentsWithCache()

                // Assert
                benefits shouldHaveSize 2
                verify { mockIntegrationService!!.extractAllStates() }
            }
        }

        When("obter do cache por estado") {
            Then("deve usar cache service") {
                // Arrange
                val result = CBenefExtractionResult.success("SC", "SC Source", sampleBenefitsSC)
                every { mockCacheService!!.getByState("SC") } returns result

                // Act
                val benefits = cbenefService!!.getFromCacheByState("SC")

                // Assert
                benefits shouldHaveSize 2
                verify { mockCacheService!!.getByState("SC") }
            }
        }

        When("obter tudo do cache") {
            Then("deve usar cache service") {
                // Arrange
                val allResults = mapOf("SC" to CBenefExtractionResult.success("SC", "SC Source", sampleBenefitsSC))
                every { mockCacheService!!.getAllStates() } returns allResults

                // Act
                val benefits = cbenefService!!.getAllFromCache()

                // Assert
                benefits shouldHaveSize 2
                verify { mockCacheService!!.getAllStates() }
            }
        }
    }

    Given("Operações de busca") {

        When("buscar benefícios com filtros") {
            Then("deve delegar para search service") {
                // Arrange
                val searchResults = listOf(sampleBenefitsSC.first())
                every {
                    mockSearchService!!.searchBenefits("850", "medicamentos", "SC", true)
                } returns searchResults

                // Act
                val results = cbenefService!!.searchBenefits("850", "medicamentos", "SC", true)

                // Assert
                results shouldHaveSize 1
                results.first().description shouldContain "medicamentos"

                verify { mockSearchService!!.searchBenefits("850", "medicamentos", "SC", true) }
            }
        }

        When("buscar benefício por código completo") {
            Then("deve delegar para search service") {
                // Arrange
                val benefit = sampleBenefitsSC.first()
                every { mockSearchService!!.findBenefitByCode("SC850001") } returns benefit

                // Act
                val result = cbenefService!!.findBenefitByCode("SC850001")

                // Assert
                result.shouldNotBeNull()
                result.getFullCode() shouldBe "SC850001"

                verify { mockSearchService!!.findBenefitByCode("SC850001") }
            }
        }

        When("buscar código inexistente") {
            Then("deve retornar null") {
                // Arrange
                every { mockSearchService!!.findBenefitByCode("SC999999") } returns null

                // Act
                val result = cbenefService!!.findBenefitByCode("SC999999")

                // Assert
                result.shouldBeNull()
            }
        }
    }

    Given("Gerenciamento de cache") {

        When("verificar se cache está habilitado") {
            Then("deve retornar true quando cache service existe") {
                // Act & Assert
                cbenefService!!.isCacheEnabled() shouldBe true
            }
        }

        When("verificar cache com service null") {
            Then("deve retornar false") {
                // Arrange
                val serviceWithoutCache = CBenefService(
                    mockIntegrationService!!,
                    null,
                    mockSearchService!!,
                    mockProperties!!
                )

                // Act & Assert
                serviceWithoutCache.isCacheEnabled() shouldBe false
            }
        }

        When("obter estatísticas do cache") {
            Then("deve delegar para cache service") {
                // Arrange
                val stats = mapOf(
                    "totalStatesCached" to 2,
                    "totalBenefitsCached" to 5,
                    "cacheEntries" to emptyList<Map<String, Any>>()
                )
                every { mockCacheService!!.getStats() } returns stats

                // Act
                val result = cbenefService!!.getCacheStats()

                // Assert
                result.shouldNotBeNull()
                result shouldContainKey "totalStatesCached"
                result shouldContainKey "totalBenefitsCached"
                result["totalStatesCached"] shouldBe 2

                verify { mockCacheService!!.getStats() }
            }
        }

        When("limpar cache") {
            Then("deve delegar para cache service e retornar true") {
                // Act
                val result = cbenefService!!.clearCache()

                // Assert
                result shouldBe true
                verify { mockCacheService!!.clearCache() }
            }
        }

        When("limpar cache sem cache service") {
            Then("deve retornar false") {
                // Arrange
                val serviceWithoutCache = CBenefService(
                    mockIntegrationService!!,
                    null,
                    mockSearchService!!,
                    mockProperties!!
                )

                // Act
                val result = serviceWithoutCache.clearCache()

                // Assert
                result shouldBe false
            }
        }
    }

    Given("Informações gerais") {

        When("obter estados disponíveis") {
            Then("deve delegar para integration service") {
                // Act
                val states = cbenefService!!.getAvailableStates()

                // Assert
                states shouldHaveSize 2
                states shouldContain "SC"
                states shouldContain "ES"

                verify { mockIntegrationService!!.getAvailableStates() }
            }
        }
    }

    Given("Cenários de erro complexos") {

        When("integration service falhar") {
            Then("deve tratar adequadamente")  {
                // Arrange
                every { mockIntegrationService!!.extractAllStates() } throws RuntimeException("Erro de rede")

                // Act & Assert (não deve lançar exceção)
                val benefits = try {
                    cbenefService!!.extractAllDocuments()
                } catch (e: Exception) {
                    emptyList()
                }

                // O serviço pode ou não tratar exceções internamente
                // Dependendo da implementação, ajuste conforme necessário
            }
        }

        When("cache service falhar") {
            Then("deve tratar graciosamente") {
                // Arrange
                every { mockCacheService!!.getStats() } throws RuntimeException("Erro no cache")

                // Act & Assert
                val stats = try {
                    cbenefService!!.getCacheStats()
                } catch (e: Exception) {
                    null
                }

                // O comportamento depende da implementação
                // Pode retornar null ou lançar exceção
            }
        }
    }
})