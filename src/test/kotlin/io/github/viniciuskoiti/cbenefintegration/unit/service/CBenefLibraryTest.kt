package io.github.viniciuskoiti.cbenefintegration.unit

import io.github.viniciuskoiti.cbenefintegration.CBenefLibrary
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefSourceData
import io.github.viniciuskoiti.cbenefintegration.enums.CBenefBenefitType
import io.github.viniciuskoiti.cbenefintegration.service.CBenefService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

class CBenefLibraryTest : BehaviorSpec({

    // Declaração de variáveis como lateinit var para evitar problemas de nullabilidade
    lateinit var mockCBenefService: CBenefService
    lateinit var cbenefLibrary: CBenefLibrary

    val sampleBenefitsSC = listOf(
        CBenefSourceData(
            stateCode = "SC",
            code = "850001",
            description = "Isenção de ICMS para medicamentos essenciais",
            startDate = LocalDate.now().minusDays(30),
            endDate = LocalDate.now().plusDays(30),
            benefitType = CBenefBenefitType.ISENCAO
        ),
        CBenefSourceData(
            stateCode = "SC",
            code = "850002",
            description = "Redução de base de cálculo para alimentícios",
            startDate = LocalDate.now().minusDays(30),
            endDate = null,
            benefitType = CBenefBenefitType.REDUCAO_BASE
        )
    )

    val sampleBenefitsES = listOf(
        CBenefSourceData(
            stateCode = "ES",
            code = "860001",
            description = "Isenção para exportação de produtos",
            startDate = LocalDate.now().minusDays(30),
            endDate = LocalDate.now().plusDays(30),
            benefitType = CBenefBenefitType.ISENCAO
        )
    )

    val allSampleBenefits = sampleBenefitsSC + sampleBenefitsES

    beforeEach {
        clearAllMocks() // Limpa todos os mocks antes de cada teste
        mockCBenefService = mockk(relaxed = true)
        cbenefLibrary = CBenefLibrary(mockCBenefService)
    }

    afterEach {
        clearAllMocks() // Limpa mocks após cada teste
    }

    Given("CBenef Library como facade principal") {

        When("extrair todos os benefícios com cache padrão") {
            Then("deve usar cache por padrão") {
                runBlocking {
                    // Arrange
                    coEvery { mockCBenefService.extractAllDocumentsWithCache() } returns allSampleBenefits
                    every { mockCBenefService.isCacheEnabled() } returns true

                    // Act
                    val result = cbenefLibrary.extractAllBenefits()

                    // Assert
                    result shouldHaveSize 3
                    result.filter { it.stateCode == "SC" } shouldHaveSize 2
                    result.filter { it.stateCode == "ES" } shouldHaveSize 1

                    coVerify(exactly = 1) { mockCBenefService.extractAllDocumentsWithCache() }
                    coVerify(exactly = 0) { mockCBenefService.extractAllDocuments() }
                }
            }
        }

        When("extrair todos os benefícios forçando sem cache") {
            Then("deve usar extração direta") {
                runBlocking {
                    // Arrange
                    coEvery { mockCBenefService.extractAllDocuments() } returns allSampleBenefits

                    // Act
                    val result = cbenefLibrary.extractAllBenefits(useCache = false)

                    // Assert
                    result shouldHaveSize 3

                    coVerify(exactly = 1) { mockCBenefService.extractAllDocuments() }
                    coVerify(exactly = 0) { mockCBenefService.extractAllDocumentsWithCache() }
                }
            }
        }

        When("cache não está habilitado") {
            Then("deve usar extração direta mesmo com useCache=true") {
                runBlocking {
                    // Arrange
                    every { mockCBenefService.isCacheEnabled() } returns false
                    coEvery { mockCBenefService.extractAllDocuments() } returns allSampleBenefits

                    // Act
                    val result = cbenefLibrary.extractAllBenefits(useCache = true)

                    // Assert
                    result shouldHaveSize 3

                    coVerify(exactly = 1) { mockCBenefService.extractAllDocuments() }
                    coVerify(exactly = 0) { mockCBenefService.extractAllDocumentsWithCache() }
                }
            }
        }
    }

    Given("Extração por estado específico") {

        When("extrair benefícios de estado válido") {
            Then("deve retornar benefícios do estado") {
                runBlocking {
                    // Arrange
                    coEvery { mockCBenefService.extractByState("SC") } returns sampleBenefitsSC

                    // Act
                    val result = cbenefLibrary.extractBenefitsByState("SC")

                    // Assert
                    result shouldHaveSize 2
                    result.all { it.stateCode == "SC" } shouldBe true
                    result.map { it.code } shouldContain "850001"
                    result.map { it.code } shouldContain "850002"

                    coVerify(exactly = 1) { mockCBenefService.extractByState("SC") }
                }
            }
        }

        When("extrair benefícios de estado inexistente") {
            Then("deve retornar lista vazia") {
                runBlocking {
                    // Arrange
                    coEvery { mockCBenefService.extractByState("XX") } returns emptyList()

                    // Act
                    val result = cbenefLibrary.extractBenefitsByState("XX")

                    // Assert
                    result shouldHaveSize 0

                    coVerify(exactly = 1) { mockCBenefService.extractByState("XX") }
                }
            }
        }

        When("extrair com falha no serviço") {
            Then("deve propagar exceção") {
                runBlocking {
                    // Arrange
                    val expectedException = RuntimeException("Erro de rede")
                    coEvery { mockCBenefService.extractByState("SC") } throws expectedException

                    // Act & Assert
                    try {
                        cbenefLibrary.extractBenefitsByState("SC")
                        // Se chegou aqui, o teste deve falhar
                        throw AssertionError("Deveria ter lançado exceção")
                    } catch (e: RuntimeException) {
                        e.message shouldBe "Erro de rede"
                    }

                    coVerify(exactly = 1) { mockCBenefService.extractByState("SC") }
                }
            }
        }
    }

    Given("Operações de busca") {

        When("buscar benefícios sem filtros") {
            Then("deve retornar todos os benefícios ativos") {
                // Arrange
                every {
                    mockCBenefService.searchBenefits(null, null, null, true)
                } returns allSampleBenefits

                // Act
                val result = cbenefLibrary.searchBenefits()

                // Assert
                result shouldHaveSize 3

                verify(exactly = 1) { mockCBenefService.searchBenefits(null, null, null, true) }
            }
        }

        When("buscar benefícios com código") {
            Then("deve filtrar por código") {
                // Arrange
                val filteredResults = listOf(sampleBenefitsSC.first())
                every {
                    mockCBenefService.searchBenefits("850001", null, null, true)
                } returns filteredResults

                // Act
                val result = cbenefLibrary.searchBenefits(code = "850001")

                // Assert
                result shouldHaveSize 1
                result.first().code shouldBe "850001"

                verify(exactly = 1) { mockCBenefService.searchBenefits("850001", null, null, true) }
            }
        }

        When("buscar benefícios com descrição") {
            Then("deve filtrar por descrição") {
                // Arrange
                val filteredResults = allSampleBenefits.filter {
                    it.description.contains("isenção", ignoreCase = true)
                }
                every {
                    mockCBenefService.searchBenefits(null, "isenção", null, true)
                } returns filteredResults

                // Act
                val result = cbenefLibrary.searchBenefits(description = "isenção")

                // Assert
                result shouldHaveSize 2 // SC medicamentos + ES exportação
                result.all { it.description.contains("isenção", ignoreCase = true) } shouldBe true

                verify(exactly = 1) { mockCBenefService.searchBenefits(null, "isenção", null, true) }
            }
        }

        When("buscar benefícios por estado") {
            Then("deve filtrar por estado") {
                // Arrange
                every {
                    mockCBenefService.searchBenefits(null, null, "SC", true)
                } returns sampleBenefitsSC

                // Act
                val result = cbenefLibrary.searchBenefits(state = "SC")

                // Assert
                result shouldHaveSize 2
                result.all { it.stateCode == "SC" } shouldBe true

                verify(exactly = 1) { mockCBenefService.searchBenefits(null, null, "SC", true) }
            }
        }

        When("buscar incluindo benefícios inativos") {
            Then("deve incluir todos os benefícios") {
                // Arrange
                val expiredBenefit = CBenefSourceData(
                    stateCode = "SC",
                    code = "850999",
                    description = "Benefício expirado",
                    startDate = LocalDate.now().minusDays(60),
                    endDate = LocalDate.now().minusDays(30),
                    benefitType = CBenefBenefitType.ISENCAO
                )
                val allBenefits = allSampleBenefits + listOf(expiredBenefit)

                every {
                    mockCBenefService.searchBenefits(null, null, null, false)
                } returns allBenefits

                // Act
                val result = cbenefLibrary.searchBenefits(activeOnly = false)

                // Assert
                result shouldHaveSize 4
                // Assumindo que CBenefSourceData tem um método isActive()
                // result.any { !it.isActive() } shouldBe true

                verify(exactly = 1) { mockCBenefService.searchBenefits(null, null, null, false) }
            }
        }

        When("combinar múltiplos filtros") {
            Then("deve aplicar todos os filtros") {
                // Arrange
                val filteredResult = listOf(sampleBenefitsSC.first())
                every {
                    mockCBenefService.searchBenefits("850", "medicamentos", "SC", true)
                } returns filteredResult

                // Act
                val result = cbenefLibrary.searchBenefits(
                    code = "850",
                    description = "medicamentos",
                    state = "SC",
                    activeOnly = true
                )

                // Assert
                result shouldHaveSize 1
                result.first().description shouldContain "medicamentos"

                verify(exactly = 1) { mockCBenefService.searchBenefits("850", "medicamentos", "SC", true) }
            }
        }
    }

    Given("Busca por código completo") {

        When("buscar benefício existente") {
            Then("deve retornar o benefício") {
                // Arrange
                val benefit = sampleBenefitsSC.first()
                every { mockCBenefService.findBenefitByCode("SC850001") } returns benefit

                // Act
                val result = cbenefLibrary.findBenefitByCode("SC850001")

                // Assert
                result.shouldNotBeNull()
                // Assumindo que existe um método getFullCode() ou similar
                // result.getFullCode() shouldBe "SC850001"
                result.description shouldContain "medicamentos"

                verify(exactly = 1) { mockCBenefService.findBenefitByCode("SC850001") }
            }
        }

        When("buscar benefício inexistente") {
            Then("deve retornar null") {
                // Arrange
                every { mockCBenefService.findBenefitByCode("XX999999") } returns null

                // Act
                val result = cbenefLibrary.findBenefitByCode("XX999999")

                // Assert
                result.shouldBeNull()

                verify(exactly = 1) { mockCBenefService.findBenefitByCode("XX999999") }
            }
        }

        When("buscar com código malformado") {
            Then("deve delegar para o serviço") {
                // Arrange
                every { mockCBenefService.findBenefitByCode("INVALID") } returns null

                // Act
                val result = cbenefLibrary.findBenefitByCode("INVALID")

                // Assert
                result.shouldBeNull()

                verify(exactly = 1) { mockCBenefService.findBenefitByCode("INVALID") }
            }
        }
    }

    Given("Operações de informação e configuração") {

        When("obter estados disponíveis") {
            Then("deve retornar lista de estados") {
                // Arrange
                val availableStates = listOf("SC", "ES", "RJ")
                every { mockCBenefService.getAvailableStates() } returns availableStates

                // Act
                val result = cbenefLibrary.getAvailableStates()

                // Assert
                result shouldHaveSize 3
                result shouldContain "SC"
                result shouldContain "ES"
                result shouldContain "RJ"

                verify(exactly = 1) { mockCBenefService.getAvailableStates() }
            }
        }

        When("verificar se cache está habilitado") {
            Then("deve retornar status do cache") {
                // Arrange
                every { mockCBenefService.isCacheEnabled() } returns true

                // Act
                val result = cbenefLibrary.isCacheEnabled()

                // Assert
                result shouldBe true

                verify(exactly = 1) { mockCBenefService.isCacheEnabled() }
            }
        }

        When("cache não está habilitado") {
            Then("deve retornar false") {
                // Arrange
                every { mockCBenefService.isCacheEnabled() } returns false

                // Act
                val result = cbenefLibrary.isCacheEnabled()

                // Assert
                result shouldBe false

                verify(exactly = 1) { mockCBenefService.isCacheEnabled() }
            }
        }
    }

    Given("Operações de cache") {

        When("obter estatísticas do cache") {
            Then("deve retornar informações do cache") {
                // Arrange
                val stats = mapOf(
                    "totalStatesCached" to 2,
                    "totalBenefitsCached" to 5,
                    "cacheEntries" to listOf(
                        mapOf("state" to "SC", "benefitsCount" to 2),
                        mapOf("state" to "ES", "benefitsCount" to 3)
                    )
                )
                every { mockCBenefService.getCacheStats() } returns stats

                // Act
                val result = cbenefLibrary.getCacheStats()

                // Assert
                result.shouldNotBeNull()
                result shouldContainKey "totalStatesCached"
                result shouldContainKey "totalBenefitsCached"
                result["totalStatesCached"] shouldBe 2

                verify(exactly = 1) { mockCBenefService.getCacheStats() }
            }
        }

        When("cache não disponível") {
            Then("deve retornar null") {
                // Arrange
                every { mockCBenefService.getCacheStats() } returns null

                // Act
                val result = cbenefLibrary.getCacheStats()

                // Assert
                result.shouldBeNull()

                verify(exactly = 1) { mockCBenefService.getCacheStats() }
            }
        }

        When("limpar cache") {
            Then("deve limpar e retornar sucesso") {
                // Arrange
                every { mockCBenefService.clearCache() } returns true

                // Act
                val result = cbenefLibrary.clearCache()

                // Assert
                result shouldBe true

                verify(exactly = 1) { mockCBenefService.clearCache() }
            }
        }

        When("obter dados do cache por estado") {
            Then("deve retornar dados cacheados") {
                // Arrange
                every { mockCBenefService.getFromCacheByState("SC") } returns sampleBenefitsSC

                // Act
                val result = cbenefLibrary.getFromCacheByState("SC")

                // Assert
                result shouldHaveSize 2
                result.all { it.stateCode == "SC" } shouldBe true

                verify(exactly = 1) { mockCBenefService.getFromCacheByState("SC") }
            }
        }

        When("obter todos os dados do cache") {
            Then("deve retornar todos os dados cacheados") {
                // Arrange
                every { mockCBenefService.getAllFromCache() } returns allSampleBenefits

                // Act
                val result = cbenefLibrary.getAllFromCache()

                // Assert
                result shouldHaveSize 3

                verify(exactly = 1) { mockCBenefService.getAllFromCache() }
            }
        }
    }

    Given("Cenários de integração completa") {

        When("fluxo completo: extrair, buscar e cachear") {
            Then("deve funcionar em sequência") {
                runBlocking {
                    // Arrange
                    every { mockCBenefService.isCacheEnabled() } returns true
                    coEvery { mockCBenefService.extractAllDocumentsWithCache() } returns allSampleBenefits
                    every { mockCBenefService.searchBenefits("850", null, null, true) } returns sampleBenefitsSC
                    every { mockCBenefService.findBenefitByCode("SC850001") } returns sampleBenefitsSC.first()

                    // Act
                    val allBenefits = cbenefLibrary.extractAllBenefits()
                    val searchResults = cbenefLibrary.searchBenefits(code = "850")
                    val specificBenefit = cbenefLibrary.findBenefitByCode("SC850001")

                    // Assert
                    allBenefits shouldHaveSize 3
                    searchResults shouldHaveSize 2
                    specificBenefit.shouldNotBeNull()

                    coVerify(exactly = 1) { mockCBenefService.extractAllDocumentsWithCache() }
                    verify(exactly = 1) { mockCBenefService.searchBenefits("850", null, null, true) }
                    verify(exactly = 1) { mockCBenefService.findBenefitByCode("SC850001") }
                }
            }
        }
    }
})