/*
 * Copyright (c) 2025 Vinícius Koiti Nakahara
 * Licensed under the MIT License (see LICENSE file)
 */

package io.github.viniciuskoiti.cbenefintegration.client

import io.github.viniciuskoiti.cbenefintegration.dto.CBenefSourceData
import kotlinx.coroutines.runBlocking
class SyncCBenefClient {
    private val asyncClient = StandaloneCBenefClient()

    fun getEstadosDisponiveis(): List<String> = asyncClient.getEstadosDisponiveis()
    fun verificarDisponibilidade(estado: String): Boolean = asyncClient.verificarDisponibilidade(estado)
    fun buscarPorCodigo(codigo: String): CBenefSourceData? = asyncClient.buscarPorCodigo(codigo)
    fun buscarBeneficios(
        codigo: String? = null,
        descricao: String? = null,
        estado: String? = null,
        apenasAtivos: Boolean = true
    ): List<CBenefSourceData> = asyncClient.buscarBeneficios(codigo, descricao, estado, apenasAtivos)

    fun extrairPorEstado(estado: String): List<CBenefSourceData> {
        return runBlocking {
            asyncClient.extrairPorEstado(estado)
        }
    }

    fun extrairTodosOsBeneficios(): List<CBenefSourceData> = runBlocking {
        asyncClient.extrairTodosOsBeneficios()
    }
}