package io.github.viniciuskoiti.cbenefintegration

import io.github.viniciuskoiti.cbenefintegration.client.StandaloneCBenefClient
import io.github.viniciuskoiti.cbenefintegration.dto.CBenefSourceData
import io.github.viniciuskoiti.cbenefintegration.enums.CBenefBenefitType
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    println("=== CBenef Integration - Exportador de Resultados ===\n")

    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    val nomeArquivo = "cbenef_resultados_$timestamp.txt"
    val arquivo = File(nomeArquivo)

    try {
        println("🚀 Inicializando cliente e preparando exportação...")
        val client = StandaloneCBenefClient()

        arquivo.bufferedWriter().use { writer ->

            // Cabeçalho do arquivo
            writer.write("=" * 80)
            writer.newLine()
            writer.write("RELATÓRIO COMPLETO CBenef Integration Library")
            writer.newLine()
            writer.write("Gerado em: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))}")
            writer.newLine()
            writer.write("=" * 80)
            writer.newLine()
            writer.newLine()

            // 1. Estados configurados
            println("🔍 Verificando estados configurados...")
            val estadosConfigurados = client.getEstadosDisponiveis()

            writer.write("1. ESTADOS CONFIGURADOS")
            writer.newLine()
            writer.write("-" * 30)
            writer.newLine()
            writer.write("Total de estados configurados: ${estadosConfigurados.size}")
            writer.newLine()
            writer.write("Estados: ${estadosConfigurados.joinToString(", ")}")
            writer.newLine()
            writer.newLine()

            if (estadosConfigurados.isEmpty()) {
                writer.write("❌ ERRO: Nenhum estado configurado!")
                writer.newLine()
                println("❌ Nenhum estado configurado!")
                return@runBlocking
            }

            // 2. Teste de conectividade
            println("📡 Testando conectividade...")
            writer.write("2. TESTE DE CONECTIVIDADE")
            writer.newLine()
            writer.write("-" * 30)
            writer.newLine()

            val estadosDisponiveis = mutableListOf<String>()
            val estadosIndisponiveis = mutableListOf<String>()

            estadosConfigurados.forEach { estado ->
                print("   Testando $estado... ")
                val disponivel = client.verificarDisponibilidade(estado)
                val status = if (disponivel) "✅ ONLINE" else "❌ OFFLINE"

                writer.write("$estado: $status")
                writer.newLine()

                if (disponivel) {
                    estadosDisponiveis.add(estado)
                    println("✅")
                } else {
                    estadosIndisponiveis.add(estado)
                    println("❌")
                }
            }

            writer.newLine()
            writer.write("Resumo conectividade:")
            writer.newLine()
            writer.write("• Estados ONLINE: ${estadosDisponiveis.size}")
            writer.newLine()
            writer.write("• Estados OFFLINE: ${estadosIndisponiveis.size}")
            writer.newLine()
            if (estadosIndisponiveis.isNotEmpty()) {
                writer.write("• Estados offline: ${estadosIndisponiveis.joinToString(", ")}")
                writer.newLine()
            }
            writer.newLine()

            if (estadosDisponiveis.isEmpty()) {
                writer.write("❌ ERRO: Nenhuma fonte disponível!")
                writer.newLine()
                println("❌ Nenhuma fonte disponível!")
                return@runBlocking
            }

            // 3. Extração de todos os estados
            println("📥 Extraindo benefícios de todos os estados...")
            writer.write("3. EXTRAÇÃO DE BENEFÍCIOS POR ESTADO")
            writer.newLine()
            writer.write("-" * 40)
            writer.newLine()

            val resultadosPorEstado = mutableMapOf<String, EstadoResultado>()
            var totalBeneficios = 0
            var tempoTotalExtracao = 0L
            var contadorEstadosComSucesso = 0

            estadosDisponiveis.forEach { estado ->
                print("   Processando $estado... ")
                writer.write("Processando estado: $estado")
                writer.newLine()

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

                    writer.write("✅ SUCESSO: ${beneficios.size} benefícios extraídos")
                    writer.newLine()
                    writer.write("   • Ativos: $ativos")
                    writer.newLine()
                    writer.write("   • Inativos: $inativos")
                    writer.newLine()
                    writer.write("   • Tempo: ${duracao}ms")
                    writer.newLine()
                    writer.newLine()

                    println("✅ ${beneficios.size} benefícios")

                } catch (e: Exception) {
                    val resultado = EstadoResultado(
                        sucesso = false,
                        erro = e.message ?: "Erro desconhecido"
                    )
                    resultadosPorEstado[estado] = resultado

                    writer.write("❌ ERRO: ${e.message}")
                    writer.newLine()
                    writer.newLine()

                    println("❌ Erro")
                }
            }

            // 4. Resumo consolidado
            writer.write("4. RESUMO CONSOLIDADO")
            writer.newLine()
            writer.write("-" * 25)
            writer.newLine()
            writer.write("Estados configurados:     ${estadosConfigurados.size}")
            writer.newLine()
            writer.write("Estados online:           ${estadosDisponiveis.size}")
            writer.newLine()
            writer.write("Estados com sucesso:      $contadorEstadosComSucesso")
            writer.newLine()
            writer.write("Estados com erro:         ${estadosDisponiveis.size - contadorEstadosComSucesso}")
            writer.newLine()
            writer.write("Total de benefícios:      $totalBeneficios")
            writer.newLine()
            writer.write("Tempo total de extração:  ${tempoTotalExtracao}ms")
            writer.newLine()

            if (contadorEstadosComSucesso > 0) {
                writer.write("Média por estado:         ${totalBeneficios / contadorEstadosComSucesso} benefícios")
                writer.newLine()
                writer.write("Tempo médio por estado:   ${tempoTotalExtracao / contadorEstadosComSucesso}ms")
                writer.newLine()
                if (totalBeneficios > 0) {
                    writer.write("Tempo médio por benefício: ${tempoTotalExtracao / totalBeneficios}ms")
                    writer.newLine()
                }
            }
            writer.newLine()

            // 5. Ranking detalhado
            val estadosComSucesso = resultadosPorEstado.filter { it.value.sucesso }

            if (estadosComSucesso.isNotEmpty()) {
                writer.write("5. RANKING DETALHADO POR ESTADO")
                writer.newLine()
                writer.write("-" * 35)
                writer.newLine()

                val ranking = estadosComSucesso.toList()
                    .sortedByDescending { it.second.total }

                ranking.forEachIndexed { index, (estado, resultado) ->
                    writer.write("${index + 1}º LUGAR: $estado")
                    writer.newLine()
                    writer.write("   • Total de benefícios: ${resultado.total}")
                    writer.newLine()
                    writer.write("   • Benefícios ativos: ${resultado.ativos} (${String.format("%.1f%%", resultado.percentualAtivos)})")
                    writer.newLine()
                    writer.write("   • Benefícios inativos: ${resultado.inativos}")
                    writer.newLine()
                    writer.write("   • Tempo de extração: ${resultado.duracaoMs}ms")
                    writer.newLine()

                    if (resultado.total > 0) {
                        writer.write("   • Tempo por benefício: ${resultado.duracaoMs / resultado.total}ms")
                        writer.newLine()
                    }
                    writer.newLine()
                }

                // 6. Análise por tipo de benefício
                writer.write("6. ANÁLISE POR TIPO DE BENEFÍCIO")
                writer.newLine()
                writer.write("-" * 35)
                writer.newLine()

                val todosBeneficios = estadosComSucesso.values.flatMap { it.beneficios }
                val porTipo = todosBeneficios.groupBy { it.benefitType ?: CBenefBenefitType.OUTROS }

                porTipo.toList()
                    .sortedByDescending { it.second.size }
                    .forEach { (tipo, lista) ->
                        val percentual = (lista.size.toDouble() / todosBeneficios.size) * 100
                        writer.write("${tipo.description}: ${lista.size} benefícios (${String.format("%.1f%%", percentual)})")
                        writer.newLine()

                        // Quebra por estado para este tipo
                        val porEstado = lista.groupBy { it.stateCode }
                        porEstado.forEach { (estado, beneficiosDoTipo) ->
                            writer.write("   └─ $estado: ${beneficiosDoTipo.size}")
                            writer.newLine()
                        }
                        writer.newLine()
                    }

                // 7. DETALHAMENTO COMPLETO POR ESTADO
                writer.write("7. DETALHAMENTO COMPLETO POR ESTADO")
                writer.newLine()
                writer.write("-" * 40)
                writer.newLine()

                ranking.forEach { (estado, resultado) ->
                    writer.write("ESTADO: $estado")
                    writer.newLine()
                    writer.write("=" * 50)
                    writer.newLine()
                    writer.write("Total de benefícios: ${resultado.total}")
                    writer.newLine()
                    writer.write("Benefícios ativos: ${resultado.ativos}")
                    writer.newLine()
                    writer.write("Benefícios inativos: ${resultado.inativos}")
                    writer.newLine()
                    writer.write("Tempo de extração: ${resultado.duracaoMs}ms")
                    writer.newLine()
                    writer.newLine()

                    if (resultado.beneficios.isNotEmpty()) {
                        writer.write("AMOSTRA DOS PRIMEIROS 10 BENEFÍCIOS:")
                        writer.newLine()
                        writer.write("-" * 40)
                        writer.newLine()

                        resultado.beneficios.take(10).forEachIndexed { index, beneficio ->
                            writer.write("${index + 1}. Código: ${beneficio.getFullCode()}")
                            writer.newLine()
                            writer.write("   Descrição: ${beneficio.description}")
                            writer.newLine()
                            writer.write("   Tipo: ${beneficio.benefitType?.description ?: "Não definido"}")
                            writer.newLine()
                            writer.write("   Status: ${if (beneficio.isActive()) "✅ ATIVO" else "❌ INATIVO"}")
                            writer.newLine()
                            writer.write("   Vigência: ${beneficio.startDate} até ${beneficio.endDate ?: "indefinido"}")
                            writer.newLine()

                            if (beneficio.applicableCSTs.isNotEmpty()) {
                                writer.write("   CSTs aplicáveis: ${beneficio.applicableCSTs.joinToString(", ")}")
                                writer.newLine()
                            }

                            if (beneficio.sourceMetadata.isNotEmpty()) {
                                writer.write("   Metadados: ${beneficio.sourceMetadata}")
                                writer.newLine()
                            }
                            writer.newLine()
                        }

                        if (resultado.beneficios.size > 10) {
                            writer.write("... e mais ${resultado.beneficios.size - 10} benefícios")
                            writer.newLine()
                        }
                        writer.newLine()

                        // Análise por tipo para este estado
                        writer.write("DISTRIBUIÇÃO POR TIPO DE BENEFÍCIO:")
                        writer.newLine()
                        writer.write("-" * 40)
                        writer.newLine()

                        val tiposPorEstado = resultado.beneficios.groupBy { it.benefitType ?: CBenefBenefitType.OUTROS }
                        tiposPorEstado.forEach { (tipo, beneficiosDoTipo) ->
                            val percentualEstado = (beneficiosDoTipo.size.toDouble() / resultado.total) * 100
                            writer.write("${tipo.description}: ${beneficiosDoTipo.size} (${String.format("%.1f%%", percentualEstado)})")
                            writer.newLine()
                        }
                        writer.newLine()

                    } else {
                        writer.write("Nenhum benefício extraído para este estado.")
                        writer.newLine()
                        writer.newLine()
                    }

                    writer.write("=" * 50)
                    writer.newLine()
                    writer.newLine()
                }

                // 8. Estados com problemas
                val estadosComErro = resultadosPorEstado.filter { !it.value.sucesso }
                if (estadosComErro.isNotEmpty()) {
                    writer.write("8. ESTADOS COM PROBLEMAS")
                    writer.newLine()
                    writer.write("-" * 25)
                    writer.newLine()

                    estadosComErro.forEach { (estado, resultado) ->
                        writer.write("Estado: $estado")
                        writer.newLine()
                        writer.write("Erro: ${resultado.erro}")
                        writer.newLine()
                        writer.newLine()
                    }
                }

                // 9. Análise de performance
                writer.write("9. ANÁLISE DE PERFORMANCE")
                writer.newLine()
                writer.write("-" * 25)
                writer.newLine()

                val temposPorEstado = estadosComSucesso.map { it.value.duracaoMs }
                val tempoMedio = temposPorEstado.average()
                val tempoMinimo = temposPorEstado.minOrNull() ?: 0
                val tempoMaximo = temposPorEstado.maxOrNull() ?: 0

                writer.write("Tempo médio de extração: ${String.format("%.0f", tempoMedio)}ms")
                writer.newLine()
                writer.write("Tempo mínimo: ${tempoMinimo}ms")
                writer.newLine()
                writer.write("Tempo máximo: ${tempoMaximo}ms")
                writer.newLine()
                writer.newLine()

                writer.write("Performance por estado:")
                writer.newLine()
                estadosComSucesso.toList()
                    .sortedBy { it.second.duracaoMs }
                    .forEach { (estado, resultado) ->
                        val eficiencia = if (resultado.total > 0) resultado.total.toDouble() / resultado.duracaoMs * 1000 else 0.0
                        writer.write("$estado: ${resultado.duracaoMs}ms (${String.format("%.1f", eficiencia)} benefícios/segundo)")
                        writer.newLine()
                    }
                writer.newLine()

                // 10. Recomendações
                writer.write("10. RECOMENDAÇÕES E OBSERVAÇÕES")
                writer.newLine()
                writer.write("-" * 35)
                writer.newLine()

                if (estadosIndisponiveis.isNotEmpty()) {
                    writer.write("• Verificar conectividade para: ${estadosIndisponiveis.joinToString(", ")}")
                    writer.newLine()
                }

                if (estadosComErro.isNotEmpty()) {
                    writer.write("• Investigar problemas de extração em: ${estadosComErro.keys.joinToString(", ")}")
                    writer.newLine()
                }

                val estadosComPoucosBeneficios = estadosComSucesso.filter { it.value.total < 10 }
                if (estadosComPoucosBeneficios.isNotEmpty()) {
                    writer.write("• Estados com poucos benefícios (possível problema de parsing): ${estadosComPoucosBeneficios.keys.joinToString(", ")}")
                    writer.newLine()
                }

                val tempoMedioMs = if (estadosComSucesso.isNotEmpty()) tempoTotalExtracao / estadosComSucesso.size else 0
                if (tempoMedioMs > 30000) {
                    writer.write("• Considerar otimização de performance (tempo médio: ${tempoMedioMs}ms)")
                    writer.newLine()
                }

                writer.write("• Cache habilitado: ${if (client.isCacheEnabled()) "✅ Sim" else "❌ Não - considere habilitar para melhor performance"}")
                writer.newLine()
                writer.write("• Total de benefícios disponíveis: $totalBeneficios")
                writer.newLine()
                writer.write("• Taxa de sucesso: ${String.format("%.1f%%", (contadorEstadosComSucesso.toDouble() / estadosDisponiveis.size) * 100)}")
                writer.newLine()
            }

            // Rodapé
            writer.newLine()
            writer.write("=" * 80)
            writer.newLine()
            writer.write("Relatório gerado com sucesso!")
            writer.newLine()
            writer.write("Arquivo: $nomeArquivo")
            writer.newLine()
            writer.write("Data/Hora: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))}")
            writer.newLine()
            writer.write("Estados processados: $contadorEstadosComSucesso/$estadosDisponiveis.size")
            writer.newLine()
            writer.write("Total de benefícios: $totalBeneficios")
            writer.newLine()
            writer.write("=" * 80)
        }

        println("\n✅ Relatório completo exportado para: $nomeArquivo")
        println("📊 Resumo final:")

        println("   • Arquivo gerado: $nomeArquivo")

    } catch (e: Exception) {
        println("❌ Erro durante execução: ${e.message}")
        e.printStackTrace()

        try {
            arquivo.appendText("\n\n❌ ERRO DURANTE EXECUÇÃO:\n${e.message}\n${e.stackTraceToString()}")
        } catch (ex: Exception) {
            println("Não foi possível salvar o erro no arquivo")
        }
    }
}

// Função auxiliar para repetir caracteres
private operator fun String.times(n: Int): String = this.repeat(n)