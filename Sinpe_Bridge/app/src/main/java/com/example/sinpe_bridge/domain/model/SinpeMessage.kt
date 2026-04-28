package com.example.sinpe_bridge.model

/**
 * Modelo que representa la información extraída de un SMS de SINPE Móvil
 */
data class SinpeMessage(
    val monto: Double,
    val nombrePagador: String,
    val referencia: String,
    val fechaHora: String,
    val textoOriginal: String
)