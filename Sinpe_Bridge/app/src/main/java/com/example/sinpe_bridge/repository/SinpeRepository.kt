package com.example.sinpe_bridge.repository

import android.util.Log
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

    /**
     * Agrega un nuevo pago recibido por SMS. El más reciente queda primero.
     */
    fun agregarPago(mensaje: SinpeMessage) {
        val nuevo = SinpePaymentItem(sinpeMessage = mensaje)
        _pagos.update { lista ->
            listOf(nuevo) + lista  // más reciente primero
        }
        Log.d("SinpeRepository", "Pago agregado: ${nuevo.id} - Ref: ${mensaje.referencia}")
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