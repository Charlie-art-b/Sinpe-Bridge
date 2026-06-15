package com.example.sinpe_bridge.repository

import android.util.Log
import com.example.sinpe_bridge.data.remote.api.BackendClient
import com.example.sinpe_bridge.model.EstadoValidacion
import com.example.sinpe_bridge.model.SinpeMessage
import com.example.sinpe_bridge.model.SinpePaymentItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Repositorio singleton que mantiene el estado reactivo de todos los pagos SINPE.
 * El ForegroundService y la UI comparten esta misma instancia.
 */
object SinpeRepository {

    private val _pagos = MutableStateFlow<List<SinpePaymentItem>>(emptyList())
    val pagos: StateFlow<List<SinpePaymentItem>> = _pagos.asStateFlow()

    fun agregarPago(mensaje: SinpeMessage) {
        _pagos.update { lista ->
            // Evitar duplicados por referencia
            if (lista.any { it.sinpeMessage.referencia == mensaje.referencia }) {
                lista
            } else {
                val nuevo = SinpePaymentItem(sinpeMessage = mensaje)
                Log.d("SinpeRepository", "Pago agregado: ${nuevo.id} - Ref: ${mensaje.referencia}")
                (listOf(nuevo) + lista)
                    .sortedByDescending { it.sinpeMessage.timestampMs }
            }
        }
    }

    /**
     * Carga un lote de pagos del historial de una sola vez, ordenados de más
     * reciente a más antiguo por timestampMs. Evita duplicados por referencia.
     */
    fun cargarLote(mensajes: List<SinpeMessage>) {
        _pagos.update { lista ->
            val existentes = lista.map { it.sinpeMessage.referencia }.toSet()
            // Filtramos los que ya existen Y evitamos duplicados dentro del nuevo lote
            val nuevos = mensajes
                .filter { it.referencia !in existentes }
                .distinctBy { it.referencia }
                .map { SinpePaymentItem(sinpeMessage = it) }

            (lista + nuevos)
                .sortedByDescending { it.sinpeMessage.timestampMs }
        }
        Log.d("SinpeRepository", "Lote procesado. Pagos en total: ${_pagos.value.size}")
    }

    /**
     * Elimina un pago de la lista.
     */
    fun eliminarPago(id: String) {
        _pagos.update { lista ->
            lista.filter { it.id != id }
        }
        Log.d("SinpeRepository", "Pago eliminado: $id")
    }

    /**
     * Actualiza los datos de un pago específico.
     */
    fun actualizarPago(id: String, nuevoMensaje: SinpeMessage) {
        _pagos.update { lista ->
            lista.map { if (it.id == id) it.copy(sinpeMessage = nuevoMensaje) else it }
        }
        Log.d("SinpeRepository", "Pago actualizado: $id")
    }

    /**
     * Cambia el estado de un pago específico.
     */
    fun actualizarEstado(id: String, estado: EstadoValidacion) {
        _pagos.update { lista ->
            lista.map { if (it.id == id) it.copy(estado = estado) else it }
        }
        Log.d("SinpeRepository", "Estado actualizado: $id -> $estado")
    }

    /**
     * Envía el pago seleccionado al backend y espera respuesta.
     * Retorna true si el backend lo acepta, false si lo rechaza.
     * Lanza excepción si hay error de red.
     */
    suspend fun validarConBackend(item: SinpePaymentItem): Boolean {
        actualizarEstado(item.id, EstadoValidacion.ENVIANDO)

        return try {
            val resultado = BackendClient.enviarSinpe(item)
            val nuevoEstado = if (resultado) EstadoValidacion.ACEPTADO else EstadoValidacion.RECHAZADO
            actualizarEstado(item.id, nuevoEstado)
            resultado
        } catch (e: Exception) {
            Log.e("SinpeRepository", "Error al validar con backend: ${e.message}")
            actualizarEstado(item.id, EstadoValidacion.ERROR)
            throw e
        }
    }
}