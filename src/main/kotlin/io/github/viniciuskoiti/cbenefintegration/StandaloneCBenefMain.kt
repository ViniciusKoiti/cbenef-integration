package io.github.viniciuskoiti.cbenefintegration

import io.github.viniciuskoiti.cbenefintegration.client.StandaloneCBenefClient
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefSourceData
import io.github.viniciuskoiti.cbenefintegration.enums.CBenefBenefitType
import kotlinx.coroutines.runBlocking

data class EstadoResultado(
    val beneficios: List<CBenefSourceData> = emptyList(),
    val duracaoMs: Long = 0,
    val ativos: Int = 0,
    val inativos: Int = 0,
    val sucesso: Boolean = false,
    val erro: String? = null
) {
    val total: Int get() = beneficios.size
    val percentualAtivos: Double get() = if (total > 0) (ativos.toDouble() / total) * 100 else 0.0
}

fun main() = runBlocking {
    println("=== CBenef Integration Library - Teste Completo de Todos os Estados ===\n")

    try {
        println("🚀 Inicializando cliente standalone...")
        val client = StandaloneCBenefClient()
        println()

        // 1. Verificar estados configurados
        println("🔍 Verificando estados configurados...")
        val estadosConfigurados = client.getEstadosDisponiveis()
        println("Estados configurados: ${estadosConfigurados.joinToString(", ")}")

        if (estadosConfigurados.isEmpty()) {
            println("❌ Nenhum estado configurado! Verifique as configurações.")
            return@runBlocking
        }
        println()

        // 2. Testar conectividade de todos os estados
        println("📡 Testando conectividade de todos os estados...")
        val estadosDisponiveis = mutableListOf<String>()
        val estadosIndisponiveis = mutableListOf<String>()

        estadosConfigurados.forEach { estado ->
            print("   $estado: ")
            val disponivel = client.verificarDisponibilidade(estado)
            if (disponivel) {
                println("✅ Online")
                estadosDisponiveis.add(estado)
            } else {
                println("❌ Offline")
                estadosIndisponiveis.add(estado)
            }
        }

        println("\n📊 Resumo de conectividade:")
        println("   • Online: ${estadosDisponiveis.size} estados")
        println("   • Offline: ${estadosIndisponiveis.size} estados")

        if (estadosIndisponiveis.isNotEmpty()) {
            println("   • Estados offline: ${estadosIndisponiveis.joinToString(", ")}")
        }

        if (estadosDisponiveis.isEmpty()) {
            println("\n❌ Nenhuma fonte disponível no momento!")
            println("💡 Possíveis causas:")
            println("   • Problemas de conectividade")
            println("   • Fontes temporariamente indisponíveis")
            println("   • Configurações de proxy/firewall")
            return@runBlocking
        }
        println()

        // 3. Extrair benefícios de TODOS os estados disponíveis
        println("📥 Extraindo benefícios de todos os estados disponíveis...")
        println("Estados a processar: ${estadosDisponiveis.joinToString(", ")}")
        println()

        val resultadosPorEstado = mutableMapOf<String, EstadoResultado>()
        var totalBeneficios = 0
        var tempoTotalExtracao = 0L
        var contadorEstadosComSucesso = 0

        estadosDisponiveis.forEach { estado ->
            print("   Processando $estado... ")

            try {
                val inicio = System.currentTimeMillis()
                val beneficios = client.extrairPorEstado(estado)
                val duracao = System.currentTimeMillis() - inicio

                val ativos = beneficios.count { it.isActive() }
                val inativos = beneficios.size - ativos

                val resultado = EstadoResultado(
                    beneficios = beneficios,
                    duracaoMs = duracao,
                    ativos = ativos,
                    inativos = inativos,
                    sucesso = true
                )

                resultadosPorEstado[estado] = resultado
                totalBeneficios += beneficios.size
                tempoTotalExtracao += duracao
                contadorEstadosComSucesso++

                println("✅ ${beneficios.size} benefícios (${ativos} ativos, ${inativos} inativos) em ${duracao}ms")

            } catch (e: Exception) {
                val resultado = EstadoResultado(
                    sucesso = false,
                    erro = e.message ?: "Erro desconhecido"
                )
                resultadosPorEstado[estado] = resultado
                println("❌ Erro: ${e.message}")
            }
        }

        println()

        // 4. Resumo consolidado
        println("📊 RESUMO CONSOLIDADO DA EXTRAÇÃO:")
        println("=" * 50)
        println("Estados configurados:     ${estadosConfigurados.size}")
        println("Estados online:           ${estadosDisponiveis.size}")
        println("Estados com sucesso:      $contadorEstadosComSucesso")
        println("Estados com erro:         ${estadosDisponiveis.size - contadorEstadosComSucesso}")
        println("Total de benefícios:      $totalBeneficios")
        println("Tempo total de extração:  ${tempoTotalExtracao}ms")

        if (contadorEstadosComSucesso > 0) {
            println("Média por estado:         ${totalBeneficios / contadorEstadosComSucesso} benefícios")
            println("Tempo médio por estado:   ${tempoTotalExtracao / contadorEstadosComSucesso}ms")
            if (totalBeneficios > 0) {
                println("Tempo médio por benefício: ${tempoTotalExtracao / totalBeneficios}ms")
            }
        }
        println()

        // 5. Ranking por quantidade de benefícios
        val estadosComSucesso = resultadosPorEstado.filter { it.value.sucesso }

        if (estadosComSucesso.isNotEmpty()) {
            println("🏆 RANKING POR QUANTIDADE DE BENEFÍCIOS:")
            println("-" * 45)

            val ranking = estadosComSucesso.toList()
                .sortedByDescending { it.second.total }

            ranking.forEachIndexed { index, (estado, resultado) ->
                val posicao = "${index + 1}º".padEnd(3)
                val estadoFormatado = estado.padEnd(4)
                val total = resultado.total.toString().padStart(5)
                val ativos = resultado.ativos.toString().padStart(4)
                val percentual = String.format("%.1f%%", resultado.percentualAtivos).padStart(6)
                val tempo = "${resultado.duracaoMs}ms".padStart(8)

                println("   $posicao $estadoFormatado: $total benefícios ($ativos ativos, $percentual) - $tempo")
            }
            println()

            // 6. Análise por tipo de benefício
            println("📈 ANÁLISE POR TIPO DE BENEFÍCIO:")
            println("-" * 40)

            val todosBeneficios = estadosComSucesso.values.flatMap { it.beneficios }
            val porTipo = todosBeneficios.groupBy { it.benefitType ?: CBenefBenefitType.OUTROS }

            porTipo.toList()
                .sortedByDescending { it.second.size }
                .forEach { (tipo, lista) ->
                    val percentual = (lista.size.toDouble() / todosBeneficios.size) * 100
                    println("   ${tipo.description.padEnd(25)}: ${lista.size.toString().padStart(5)} (${String.format("%.1f%%", percentual)})")
                }
            println()

            // 7. Detalhes por estado (primeiros benefícios de cada um)
            println("📋 AMOSTRA DE BENEFÍCIOS POR ESTADO:")
            println("-" * 45)

            ranking.take(3).forEach { (estado, resultado) ->
                println("🔸 $estado - ${resultado.total} benefícios:")

                if (resultado.beneficios.isNotEmpty()) {
                    resultado.beneficios.take(2).forEach { beneficio ->
                        val status = if (beneficio.isActive()) "✅" else "❌"
                        val vigencia = if (beneficio.endDate != null) {
                            "${beneficio.startDate} até ${beneficio.endDate}"
                        } else {
                            "desde ${beneficio.startDate}"
                        }

                        println("   • ${beneficio.getFullCode()}: ${beneficio.description.take(60)}${if (beneficio.description.length > 60) "..." else ""}")
                        println("     └─ $status ${beneficio.benefitType?.description ?: "Tipo não definido"} | $vigencia")
                    }

                    if (resultado.beneficios.size > 2) {
                        println("   ... e mais ${resultado.beneficios.size - 2} benefícios")
                    }
                } else {
                    println("   Nenhum benefício extraído")
                }
                println()
            }

            // 8. Teste de funcionalidades de busca
            println("🔍 TESTE DAS FUNCIONALIDADES DE BUSCA:")
            println("-" * 42)

            // Busca por descrição
            val termosBusca = listOf("isenção", "redução", "diferimento", "crédito")

            println("Busca por termos comuns:")
            termosBusca.forEach { termo ->
                val inicio = System.currentTimeMillis()
                val resultados = client.buscarBeneficios(descricao = termo)
                val duracao = System.currentTimeMillis() - inicio

                println("   '$termo': ${resultados.size} resultados em ${duracao}ms")
            }
            println()

            // Busca por código específico
            if (todosBeneficios.isNotEmpty()) {
                val codigoTeste = todosBeneficios.first().getFullCode()
                val beneficioEspecifico = client.buscarPorCodigo(codigoTeste)

                println("Busca por código específico:")
                println("   Código testado: $codigoTeste")
                println("   Resultado: ${if (beneficioEspecifico != null) "✅ Encontrado" else "❌ Não encontrado"}")

                if (beneficioEspecifico != null) {
                    println("   Descrição: ${beneficioEspecifico.description}")
                    println("   Aplicável CST 00: ${beneficioEspecifico.isApplicableForCST("00")}")
                    println("   Aplicável CST 40: ${beneficioEspecifico.isApplicableForCST("40")}")
                }
                println()
            }

            // 9. Estados com problemas
            val estadosComErro = resultadosPorEstado.filter { !it.value.sucesso }
            if (estadosComErro.isNotEmpty()) {
                println("⚠️  ESTADOS COM PROBLEMAS:")
                println("-" * 30)

                estadosComErro.forEach { (estado, resultado) ->
                    println("   $estado: ${resultado.erro}")
                }
                println()
            }

            // 10. Recomendações
            println("💡 RECOMENDAÇÕES:")
            println("-" * 20)

            if (estadosIndisponiveis.isNotEmpty()) {
                println("   • Verificar conectividade para: ${estadosIndisponiveis.joinToString(", ")}")
            }

            if (estadosComErro.isNotEmpty()) {
                println("   • Investigar problemas de extração em: ${estadosComErro.keys.joinToString(", ")}")
            }

            val estadosComPoucosBeneficios = estadosComSucesso.filter { it.value.total < 10 }
            if (estadosComPoucosBeneficios.isNotEmpty()) {
                println("   • Estados com poucos benefícios podem ter problemas de parsing: ${estadosComPoucosBeneficios.keys.joinToString(", ")}")
            }

            val tempoMedio = if (estadosComSucesso.isNotEmpty()) tempoTotalExtracao / estadosComSucesso.size else 0
            if (tempoMedio > 30000) {
                println("   • Considerar otimização de performance (tempo médio: ${tempoMedio}ms)")
            }

            println("   • Cache habilitado: ${if (client.isCacheEnabled()) "✅ Sim" else "❌ Não - considere habilitar para melhor performance"}")

        } else {
            println("❌ Nenhum estado foi processado com sucesso!")
        }

        println("\n" + "=" * 50)
        println("✅ Teste completo de todos os estados finalizado!")
        println("📊 Dados consolidados: $contadorEstadosComSucesso/$estadosDisponiveis.size estados com sucesso")
        println("📈 Total de benefícios disponíveis: $totalBeneficios")

    } catch (e: Exception) {
        println("❌ Erro crítico durante execução: ${e.message}")
        println("\n🔍 Stack trace completo:")
        e.printStackTrace()

        println("\n💡 Possíveis soluções:")
        println("   • Verificar conexão com a internet")
        println("   • Verificar se as URLs das fontes estão acessíveis")
        println("   • Verificar configurações de proxy/firewall")
        println("   • Verificar logs para mais detalhes sobre erros específicos")
        println("   • Tentar novamente em alguns minutos")
    }
}

private operator fun String.times(n: Int): String = this.repeat(n)