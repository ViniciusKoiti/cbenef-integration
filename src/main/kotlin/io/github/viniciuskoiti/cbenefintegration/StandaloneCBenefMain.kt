package io.github.viniciuskoiti.cbenefintegration

import io.github.viniciuskoiti.cbenefintegration.client.StandaloneCBenefClient
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("=== CBenef Integration Library - Standalone Example ===\n")

    try {
        println("🚀 Inicializando cliente standalone...")
        val client = StandaloneCBenefClient()
        println()

        println("🔍 Verificando estados disponíveis...")
        val estados = client.getEstadosDisponiveis()
        println("Estados disponíveis: $estados")

        if (estados.isEmpty()) {
            println("❌ Nenhum estado configurado! Verifique as configurações.")
            return@runBlocking
        }

        println("\n📡 Testando conectividade...")
        val estadosDisponiveis = mutableListOf<String>()

        estados.forEach { estado ->
            print("   $estado: ")
            val disponivel = client.verificarDisponibilidade(estado)
            println(if (disponivel) "✅ Online" else "❌ Offline")

            if (disponivel) {
                estadosDisponiveis.add(estado)
            }
        }

        if (estadosDisponiveis.isEmpty()) {
            println("❌ Nenhuma fonte disponível no momento!")
            println("💡 Possíveis causas:")
            println("   • Problemas de conectividade")
            println("   • Fontes temporariamente indisponíveis")
            println("   • Configurações de proxy/firewall")
            return@runBlocking
        }

        val estadoTeste = estadosDisponiveis.first()
        println("\n📥 Extraindo benefícios de $estadoTeste...")

        val inicioExtração = System.currentTimeMillis()
        val beneficios = client.extrairPorEstado(estadoTeste)
        val duracaoExtração = System.currentTimeMillis() - inicioExtração

        println("✅ Extração concluída em ${duracaoExtração}ms")
        println("📊 Total de benefícios $estadoTeste: ${beneficios.size}")

        if (beneficios.isNotEmpty()) {
            // Estatísticas
            val ativos = beneficios.count { it.isActive() }
            val inativos = beneficios.size - ativos

            println("   └─ Ativos: $ativos | Inativos: $inativos")

            // Distribuição por tipo
            val porTipo = beneficios.groupBy { it.benefitType }
            println("   └─ Por tipo:")
            porTipo.forEach { (tipo, lista) ->
                println("      • ${tipo ?: "Não definido"}: ${lista.size}")
            }

            println("\n📋 Primeiros 3 benefícios:")
            beneficios.take(3).forEachIndexed { index, beneficio ->
                println("${index + 1}. ${beneficio.getFullCode()}: ${beneficio.description}")
                println("   └─ Ativo: ${if (beneficio.isActive()) "✅" else "❌"}")
                println("   └─ Tipo: ${beneficio.benefitType ?: "Não definido"}")
                println("   └─ Vigência: ${beneficio.startDate} até ${beneficio.endDate ?: "indefinido"}")

                // Testar CSTs comuns
                val cstsTest = listOf("00", "10", "20", "40", "41", "60", "70", "90")
                val cstsAplicaveis = cstsTest.filter { beneficio.isApplicableForCST(it) }
                if (cstsAplicaveis.isNotEmpty()) {
                    println("   └─ CSTs aplicáveis: ${cstsAplicaveis.joinToString(", ")}")
                }
                println()
            }
        } else {
            println("⚠️ Nenhum benefício extraído. Possíveis causas:")
            println("   • Documento vazio ou formato não suportado")
            println("   • Erro no parsing do conteúdo")
            println("   • Mudanças no formato da fonte")
        }

        // 4. Buscar benefício específico
        println("🎯 Buscando benefício específico...")
        val codigoTeste = if (beneficios.isNotEmpty()) {
            beneficios.first().getFullCode()
        } else {
            "${estadoTeste}850001" // Código padrão para teste
        }

        val beneficio = client.buscarPorCodigo(codigoTeste)
        if (beneficio != null) {
            println("✅ Encontrado: ${beneficio.getFullCode()}")
            println("   └─ Descrição: ${beneficio.description}")
            println("   └─ Aplicável para CST 40: ${beneficio.isApplicableForCST("40")}")
            println("   └─ Aplicável para CST 00: ${beneficio.isApplicableForCST("00")}")
        } else {
            println("❌ Benefício $codigoTeste não encontrado")
        }

        // 5. Buscar por descrição
        println("\n🔍 Buscando por descrição...")
        val termoBusca = "isen" // Termo que provavelmente existe
        val iniciBusca = System.currentTimeMillis()
        val beneficiosIsenção = client.buscarBeneficios(
            descricao = termoBusca,
            estado = estadoTeste
        )
        val duracaoBusca = System.currentTimeMillis() - iniciBusca

        println("✅ Busca por '$termoBusca' concluída em ${duracaoBusca}ms")
        println("📊 Benefícios encontrados: ${beneficiosIsenção.size}")

        beneficiosIsenção.take(3).forEach { beneficioEncontrado ->
            println("   • ${beneficioEncontrado.getFullCode()}: ${beneficioEncontrado.description}")
        }

        if (beneficiosIsenção.size > 3) {
            println("   ... e mais ${beneficiosIsenção.size - 3} benefícios")
        }

        // 6. Teste de performance simples
        if (beneficios.size > 10) { // Reduzido threshold para testar com poucos dados
            println("\n⚡ Teste de performance de busca...")
            val termosBusca = listOf("redução", "isenção", "diferimento", "crédito")

            termosBusca.forEach { termo ->
                val inicio = System.currentTimeMillis()
                val resultados = client.buscarBeneficios(descricao = termo, estado = estadoTeste)
                val duracao = System.currentTimeMillis() - inicio

                println("   '$termo': ${resultados.size} resultados em ${duracao}ms")
            }
        }

        // 7. Resumo final
        println("\n📈 Resumo da execução:")
        println("   • Estados testados: ${estados.size}")
        println("   • Estados online: ${estadosDisponiveis.size}")
        println("   • Benefícios extraídos: ${beneficios.size}")
        println("   • Tempo de extração: ${duracaoExtração}ms")
        println("   • Média por benefício: ${if (beneficios.isNotEmpty()) "${duracaoExtração / beneficios.size}ms" else "N/A"}")
        println("   • Cache habilitado: ${client.isCacheEnabled()}")

        println("\n✅ Exemplo standalone concluído com sucesso!")

    } catch (e: Exception) {
        println("❌ Erro durante execução: ${e.message}")
        println("🔍 Stack trace completo:")
        e.printStackTrace()

        println("\n💡 Possíveis soluções:")
        println("   • Verificar conexão com a internet")
        println("   • Verificar se as URLs das fontes estão acessíveis")
        println("   • Verificar configurações de proxy/firewall")
        println("   • Tentar novamente em alguns minutos")
    }
}