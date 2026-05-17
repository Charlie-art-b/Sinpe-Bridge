package com.example.sinpe_bridge.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sinpe_bridge.model.EstadoValidacion
import com.example.sinpe_bridge.model.SinpePaymentItem
import com.example.sinpe_bridge.repository.SinpeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SinpeUiState(
    val pagos: List<SinpePaymentItem> = emptyList(),
    val seleccionadoId: String? = null,
    val mensajeError: String? = null,
    val ultimoResultado: ResultadoValidacion? = null
)

data class ResultadoValidacion(
    val id: String,
    val aceptado: Boolean
)

class SinpeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SinpeUiState())
    val uiState: StateFlow<SinpeUiState> = _uiState.asStateFlow()

    init {
        // Observar los pagos del repositorio en tiempo real
        viewModelScope.launch {
            SinpeRepository.pagos.collect { listaPagos ->
                _uiState.update { it.copy(pagos = listaPagos) }
            }
        }
    }

    /** El usuario selecciona un pago de la lista */
    fun seleccionar(id: String) {
        _uiState.update {
            // Si ya está seleccionado, lo deseleccionamos (null)
            val nuevoId = if (it.seleccionadoId == id) null else id
            it.copy(
                seleccionadoId = nuevoId,
                mensajeError = null,
                ultimoResultado = null
            )
        }
    }

    /** El usuario presiona "Validar" */
    fun validar() {
        val idSeleccionado = _uiState.value.seleccionadoId ?: return
        val pago = _uiState.value.pagos.find { it.id == idSeleccionado } ?: return

        // Evitar múltiples envíos del mismo ítem si ya está en proceso
        if (pago.estado == EstadoValidacion.ENVIANDO) return

        // Solo se pueden validar pagos pendientes o con error anterior.
        if (pago.estado == EstadoValidacion.ACEPTADO) {
            _uiState.update { it.copy(mensajeError = "Este pago ya fue aceptado.") }
            return
        }

        if (pago.estado == EstadoValidacion.RECHAZADO) {
            val cincoMinutosEnMs = 5 * 60 * 1000
            val tiempoTranscurrido = System.currentTimeMillis() - pago.sinpeMessage.timestampMs
            if (tiempoTranscurrido < cincoMinutosEnMs) {
                val faltanMinutos = ((cincoMinutosEnMs - tiempoTranscurrido) / 60000) + 1
                _uiState.update { it.copy(mensajeError = "Pago rechazado. Podrá re-validar en $faltanMinutos min.") }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(mensajeError = null) }

            try {
                // El BackendClient ahora tiene un timeout de 2 minutos.
                // El repositorio cambia el estado a ENVIANDO internamente.
                val resultadoFinal = SinpeRepository.validarConBackend(pago)

                _uiState.update {
                    it.copy(
                        mensajeError = null,
                        ultimoResultado = ResultadoValidacion(idSeleccionado, resultadoFinal)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        mensajeError = "⏳ Error de conexión o tiempo de espera agotado (2 min)."
                    )
                }
            }
        }
    }

    /** Limpia el resultado del último diálogo mostrado */
    fun limpiarResultado() {
        _uiState.update { it.copy(ultimoResultado = null) }
    }

    fun limpiarError() {
        _uiState.update { it.copy(mensajeError = null) }
    }

    fun eliminar() {
        val id = _uiState.value.seleccionadoId ?: return
        SinpeRepository.eliminarPago(id)
        _uiState.update { it.copy(seleccionadoId = null) }
    }
}