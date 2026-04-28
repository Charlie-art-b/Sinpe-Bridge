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
    val validando: Boolean = false,
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
        if (_uiState.value.validando) return  // bloquear durante validación
        _uiState.update {
            it.copy(
                seleccionadoId = id,
                mensajeError = null,
                ultimoResultado = null
            )
        }
    }

    /** El usuario presiona "Validar" */
    fun validar() {
        val idSeleccionado = _uiState.value.seleccionadoId ?: return
        val pago = _uiState.value.pagos.find { it.id == idSeleccionado } ?: return

        // Solo se pueden validar pagos pendientes o con error anterior
        if (pago.estado == EstadoValidacion.ACEPTADO || pago.estado == EstadoValidacion.RECHAZADO) {
            _uiState.update { it.copy(mensajeError = "Este pago ya fue validado anteriormente.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(validando = true, mensajeError = null) }

            try {
                val esValido = SinpeRepository.validarConBackend(pago)
                _uiState.update {
                    it.copy(
                        validando = false,
                        ultimoResultado = ResultadoValidacion(idSeleccionado, esValido)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        validando = false,
                        mensajeError = "Error de conexión: ${e.message}"
                    )
                }
            }
        }
    }

    /** Limpia el resultado del último diálogo mostrado */
    fun limpiarResultado() {
        _uiState.update { it.copy(ultimoResultado = null) }
    }

    /** Limpia el mensaje de error */
    fun limpiarError() {
        _uiState.update { it.copy(mensajeError = null) }
    }
}