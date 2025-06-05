# CBenef Integration Library

[![GitHub release](https://img.shields.io/github/v/release/ViniciusKoiti/cbenef-integration)](https://github.com/ViniciusKoiti/cbenef-integration/releases)
[![GitHub packages](https://img.shields.io/badge/GitHub-Packages-blue.svg)](https://github.com/ViniciusKoiti/cbenef-integration/packages)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.25-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.0-brightgreen.svg)](https://spring.io/projects/spring-boot)

Biblioteca Kotlin/Spring Boot para integração com dados CBenef (Código de Benefício/Incentivo Fiscal) dos estados brasileiros. Extrai automaticamente informações fiscais de documentos PDF das SEFAZs estaduais.

## 💡 Uso Recomendado: Sincronização + Banco de Dados

**A forma IDEAL de usar esta biblioteca é para sincronização periódica com seu banco de dados:**

- 🔄 **Sincronização Agendada**: Use a biblioteca em jobs/cron para extrair dados atualizados
- 💾 **Persistência Local**: Salve os benefícios no SEU banco de dados
- ⚡ **Consultas Rápidas**: Sua aplicação consulta o banco local, não as fontes externas
- 🧠 **Zero Overhead**: Sem consumo de memória em runtime da aplicação
- 📊 **Controle Total**: Você decide quando e como atualizar os dados

```kotlin
// ✅ RECOMENDADO - Sincronização periódica
@Scheduled(cron = "0 0 2 * * ?") // Todo dia às 2h
suspend fun sincronizarBeneficios() {
   val beneficios = cbenefLibrary.extractAllBenefits(useCache = false)
   beneficioRepository.saveAll(beneficios.map { it.toEntity() })
}

// ❌ NÃO RECOMENDADO - Consulta direta em runtime
val benefits = cbenefLibrary.extractAllBenefits() // Lento + Memória
```

## ⚠️ Por que NÃO usar cache interno da biblioteca:

- 🧠 **CONSUMO DE MEMÓRIA**: Mantém TODOS os benefícios carregados permanentemente na RAM
- 💥 **OutOfMemoryError**: Pode esgotar heap em aplicações com múltiplos usuários
- ⚡ **Performance**: Cache grande é mais lento que consulta ao banco local
- 🔒 **Sem Controle**: Você não controla quando/como os dados são atualizados

## 🚀 Instalação

### JitPack (Recomendado - Sem autenticação)
```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.ViniciusKoiti:cbenef-integration:v1.2.0")
}
```

### GitHub Packages
```kotlin
repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/ViniciusKoiti/cbenef-integration")
        credentials {
            username = "seu_github_username"
            password = "seu_github_token" // Personal Access Token
        }
    }
}

dependencies {
    implementation("io.github.viniciuskoiti:cbenef-integration:1.2.0-SNAPSHOT")
}
```

### Gradle (Local Build)
```bash
git clone https://github.com/ViniciusKoiti/cbenef-integration.git
cd cbenef-integration
./gradlew publishToMavenLocal

# No seu projeto:
dependencies {
    implementation("io.github.viniciuskoiti:cbenef-integration:1.2.0-SNAPSHOT")
}
```

> 📋 **Nota**: Em breve disponível no Maven Central para instalação sem autenticação

## 📊 Estados Suportados

| Estado | Status | Formato | Última Atualização | Benefícios Típicos |
|--------|--------|---------|-------------------|-------------------|
| 🟢 **SC** | Ativo | PDF | Sempre atualizado | ~150 benefícios |
| 🟢 **ES** | Ativo | PDF | Sempre atualizado | ~80 benefícios |
| 🟢 **RJ** | Ativo | PDF | Sempre atualizado | ~120 benefícios |
| 🟢 **PR** | Ativo | PDF | Sempre atualizado | ~200 benefícios |
| 🟡 **RS** | Configurado | Excel | Aguardando ativação | - |
| 🟡 **GO** | Configurado | HTML | Aguardando ativação | - |
| 🔴 **DF** | Planejado | PDF | Em desenvolvimento | - |

**Total: ~550 benefícios fiscais ativos** extraídos automaticamente! 🎉

## 📊 Arquitetura Recomendada

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   SEFAZ PDFs    │ ── │ CBenef Library   │ ── │ Seu Banco │
│ (SC/ES/RJ/PR)   │    │ (Sincronização)  │    │ (Consultas)     │
└─────────────────┘    └──────────────────┘    └─────────────────┘
       ↑                        ↑                       ↑
   Fontes Externas        Job Agendado            Runtime Rápido
   (Lentas/Instáveis)     (1x por dia)           (Milissegundos)
```

### Vantagens desta Abordagem:

- ⚡ **Performance**: Consultas em milissegundos no banco local
- 🛡️ **Resilência**: Aplicação funciona mesmo se SEFAZs estiverem offline
- 🎯 **Controle**: Você decide quando atualizar e como tratar erros
- 💾 **Otimização**: Índices, queries otimizadas, relacionamentos
- 📊 **Analytics**: Histórico, auditoria, relatórios customizados

## 🔧 Uso Básico

### Sincronização Completa com Banco

```kotlin
@Service
class CBenefSyncService(
    private val cbenefLibrary: CBenefLibrary,
    private val beneficioRepository: BeneficioRepository
) {
    
    @Scheduled(cron = "0 0 2 * * ?") // Todo dia às 2h da manhã
    suspend fun sincronizarTodosEstados() {
        val estadosDisponiveis = cbenefLibrary.getAvailableStates() // [SC, ES, RJ, PR]
        
        estadosDisponiveis.forEach { estado ->
            try {
                syncEstado(estado)
            } catch (e: Exception) {
                // Log erro mas continua outros estados
                logger.error("Erro ao sincronizar estado $estado", e)
            }
        }
    }
    
    private suspend fun syncEstado(estado: String) {
        val beneficios = cbenefLibrary.extractBenefitsByState(estado)
        
        if (beneficios.isNotEmpty()) {
            // Remove benefícios antigos do estado
            beneficioRepository.deleteByStateCode(estado)
            
            // Salva novos benefícios
            val entities = beneficios.map { it.toBeneficioEntity() }
            beneficioRepository.saveAll(entities)
            
            logger.info("Estado $estado: ${beneficios.size} benefícios sincronizados")
        }
    }
}

@Entity
data class BeneficioEntity(
    @Id val id: String, // stateCode + code
    val stateCode: String,
    val code: String,
    val description: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val benefitType: String?,
    val applicableCSTs: String, // JSON ou CSV
    val isActive: Boolean,
    val lastSync: LocalDateTime = LocalDateTime.now()
)

// Extension function para conversão
fun CBenefSourceData.toBeneficioEntity() = BeneficioEntity(
    id = getFullCode(),
    stateCode = stateCode,
    code = code,
    description = description,
    startDate = startDate,
    endDate = endDate,
    benefitType = benefitType?.description,
    applicableCSTs = applicableCSTs.joinToString(","),
    isActive = isActive()
)
```

### Consultas Rápidas no Runtime

```kotlin
@RestController
class BeneficioController(
    private val beneficioRepository: BeneficioRepository
) {
    
    @GetMapping("/beneficios/{estado}")
    fun consultarBeneficios(@PathVariable estado: String): List<BeneficioEntity> {
        // ⚡ Consulta rápida no banco local
        return beneficioRepository.findByStateCodeAndIsActive(estado, true)
    }
    
    @GetMapping("/beneficios/codigo/{codigo}")
    fun consultarPorCodigo(@PathVariable codigo: String): BeneficioEntity? {
        // ⚡ Busca direta por ID
        return beneficioRepository.findById(codigo).orElse(null)
    }
    
    @GetMapping("/beneficios/search")
    fun buscarBeneficios(
        @RequestParam(required = false) descricao: String?,
        @RequestParam(required = false) estado: String?
    ): List<BeneficioEntity> {
        // ⚡ Query otimizada no banco
        return beneficioRepository.findByFilters(descricao, estado)
    }
}
```

### Cliente Standalone (Sem Spring)

```kotlin
import io.github.viniciuskoiti.cbenefintegration.client.StandaloneCBenefClient

suspend fun main() {
    val client = StandaloneCBenefClient()
    
    // Verificar estados disponíveis
    val estados = client.getEstadosDisponiveis()
    println("Estados disponíveis: $estados") // [SC, ES, RJ, PR]
    
    // Extrair benefícios do Paraná
    val beneficiosPR = client.extrairPorEstado("PR")
    println("Benefícios PR: ${beneficiosPR.size}")
    
    // Buscar benefício específico de Santa Catarina
    val beneficio = client.buscarPorCodigo("SC850001")
    println("Benefício encontrado: ${beneficio?.description}")
    
    // Extrair todos os estados (pode demorar)
    val todosBeneficios = client.extrairTodosOsBeneficios()
    println("Total de benefícios: ${todosBeneficios.size}") // ~550
}
```

## 📊 Tipos de Resposta

### 1. Sucesso - Lista de Benefícios
```kotlin
data class CBenefSourceData(
    val stateCode: String,           // "SC", "ES", "RJ", "PR"
    val code: String,                // "850001" (sem UF)
    val description: String,         // "Isenção ICMS medicamentos"
    val startDate: LocalDate,        // Data de início
    val endDate: LocalDate?,         // Data fim (null = indefinido)
    val benefitType: CBenefBenefitType?, // Tipo do benefício
    val applicableCSTs: List<String>, // CSTs aplicáveis
    val cstSpecific: Boolean,        // Se é específico para CSTs
    val notes: String?,              // Observações
    val sourceMetadata: Map<String, String> // Metadados da extração
)

// Métodos úteis
fun getFullCode(): String        // Retorna "SC850001", "PR123456", etc.
fun isActive(): Boolean          // Se está ativo hoje
fun isApplicableForCST(cst: String): Boolean
```

### 2. Estados Disponíveis
```kotlin
val estados: List<String> = listOf("SC", "ES", "RJ", "PR") // ✅ 4 estados ativos
```

### 3. Erro - Lista Vazia
```kotlin
// Em caso de erro, retorna lista vazia
val beneficios: List<CBenefSourceData> = emptyList()
```

### 4. Verificação de Disponibilidade
```kotlin
val disponivel: Boolean = client.verificarDisponibilidade("PR") // true/false
```

## 🔍 Operações de Busca

```kotlin
// Buscar todos os benefícios ativos (SEM cache)
val todosBeneficios = cbenefLibrary.extractAllBenefits(useCache = false)

// Buscar por estado específico
val beneficiosPR = cbenefLibrary.extractBenefitsByState("PR")

// Buscar com filtros
val resultados = cbenefLibrary.searchBenefits(
    code = "850",                    // Código contém "850"
    description = "medicamentos",    // Descrição contém "medicamentos"
    state = "SC",                   // Apenas SC
    activeOnly = true               // Apenas ativos
)

// Buscar benefício específico por código completo
val beneficio = cbenefLibrary.findBenefitByCode("PR123456")
```

## ⚙️ Configuração Avançada

### application.yml
```yaml
app:
  cbenef:
    # ⚠️ MANTER CACHE DESABILITADO EM PRODUÇÃO
    cache:
      enabled: false
      
    connection:
      timeout: 30000
      readTimeout: 60000
      maxRetries: 3
      userAgent: "MeuApp/1.0"
      
    states:
      SC:
        enabled: true
        priority: 1
        customTimeout: 15000
      ES:
        enabled: true
        priority: 2
        customTimeout: 45000
      RJ:
        enabled: true
        priority: 3
        customTimeout: 60000
      PR:
        enabled: true
        priority: 4
        customTimeout: 45000
```

## 🛡️ Tratamento de Erros

```kotlin
@Service
class CBenefService(private val cbenefLibrary: CBenefLibrary) {
    
    suspend fun obterBeneficiosComTratamento(estado: String): Result<List<CBenefSourceData>> {
        return try {
            val beneficios = cbenefLibrary.extractBenefitsByState(estado)
            
            when {
                beneficios.isEmpty() -> {
                    // Log: Estado sem dados ou fonte indisponível
                    Result.failure(Exception("Nenhum benefício encontrado para $estado"))
                }
                beneficios.size < 10 -> {
                    // Log: Possível problema na extração
                    Result.success(beneficios) // Mas monitore
                }
                else -> Result.success(beneficios)
            }
        } catch (e: Exception) {
            // Log do erro para monitoramento
            Result.failure(e)
        }
    }
}
```

## 🔧 Monitoramento e Logs

```kotlin
// Verificar status dos estados
val estadosDisponiveis = cbenefLibrary.getAvailableStates() // [SC, ES, RJ, PR]
estadosDisponiveis.forEach { estado ->
    val disponivel = client.verificarDisponibilidade(estado)
    println("$estado: ${if (disponivel) "✅ Online" else "❌ Offline"}")
}

// Estatísticas (apenas se cache estiver habilitado)
val stats = cbenefLibrary.getCacheStats()
if (stats != null) {
    println("Estados em cache: ${stats["totalStatesCached"]}")
    println("Benefícios em cache: ${stats["totalBenefitsCached"]}")
}
```

## 📝 Exemplos de Uso por Cenário

### E-commerce/ERP - Consulta de Benefícios
```kotlin
@Service
class BeneficioFiscalService(private val cbenefLibrary: CBenefLibrary) {
    
    suspend fun consultarBeneficio(codigoProduto: String, uf: String): CBenefSourceData? {
        // Sempre busca dados frescos
        val beneficios = cbenefLibrary.extractBenefitsByState(uf)
        
        return beneficios.find { beneficio ->
            beneficio.isActive() && 
            beneficio.description.contains(codigoProduto, ignoreCase = true)
        }
    }
}
```

### Relatórios Fiscais - Análise Completa
```kotlin
@Service
class RelatorioFiscalService(private val cbenefLibrary: CBenefLibrary) {

   suspend fun gerarRelatorioCompleto(): RelatorioFiscal {
      val todosBeneficios = cbenefLibrary.extractAllBenefits(useCache = false)

      return RelatorioFiscal(
         totalBeneficios = todosBeneficios.size, // ~550
         beneficiosAtivos = todosBeneficios.count { it.isActive() },
         beneficiosPorEstado = todosBeneficios.groupBy { it.stateCode }
            .mapValues { it.value.size }, // SC: ~150, ES: ~80, RJ: ~120, PR: ~200
         dataExtracao = LocalDateTime.now()
      )
   }
}
```

## 🚨 Troubleshooting

### Problemas Comuns

1. **Lista vazia retornada**
   - ✅ Verificar conectividade com internet
   - ✅ Confirmar se URL da SEFAZ está acessível
   - ✅ Verificar logs para erros de parsing

2. **Timeout na extração**
   - ✅ Aumentar `customTimeout` na configuração
   - ✅ Verificar proxy/firewall corporativo
   - ✅ PDFs do governo podem ser lentos (especialmente PR)

3. **Dados inconsistentes**
   - ✅ **NÃO use cache em produção**
   - ✅ Sempre extrair dados frescos
   - ✅ Validar benefícios com `isActive()`

4. **Problemas específicos por estado:**
   - **SC**: PDF pode ter mudanças de layout
   - **ES**: Versões do PDF (V6, V7) podem variar
   - **RJ**: Tabela CST pode ter novos formatos
   - **PR**: TABELA 5.2 pode ser reorganizada

## 🆕 Novidades na v1.2.0

- ✅ **Novo Estado: Paraná (PR)** - Suporte completo à TABELA 5.2
- ⚡ **Performance Melhorada** - Otimizações na extração de PDFs
- 🛡️ **Robustez Aumentada** - Melhor tratamento de erros
- 📊 **Mais Benefícios** - ~200 benefícios adicionais do PR

## 🤝 Contribuição

Contribuições são bem-vindas! Por favor:

1. Fork o projeto
2. Crie uma branch para sua feature (`git checkout -b feature/nova-funcionalidade`)
3. Commit suas mudanças (`git commit -am 'Adiciona nova funcionalidade'`)
4. Push para a branch (`git push origin feature/nova-funcionalidade`)
5. Abra um Pull Request

### Estados em Desenvolvimento

Quer ajudar a implementar outros estados? Veja nossa [roadmap](https://github.com/ViniciusKoiti/cbenef-integration/issues):

- 🔄 **RS** (Rio Grande do Sul) - Excel format
- 🔄 **GO** (Goiás) - HTML format
- 🔄 **DF** (Distrito Federal) - Aguardando URL
- 💡 **SP** (São Paulo) - Análise de viabilidade

## 📄 Licença

Copyright © 2025 Vinícius Koiti Nakahara

Licensed under the [MIT License](LICENSE).

## 👨‍💻 Autor

**Vinícius Koiti Nakahara**
- GitHub: [@ViniciusKoiti](https://github.com/ViniciusKoiti)
- Email: viniciusnakahara@gmail.com

## ⭐ Support

Se este projeto te ajudou, considere dar uma ⭐!

---

**Aviso Legal**: Esta biblioteca extrai dados de fontes públicas das SEFAZs. Sempre valide informações fiscais com órgãos competentes antes de tomar decisões baseadas nos dados extraídos.