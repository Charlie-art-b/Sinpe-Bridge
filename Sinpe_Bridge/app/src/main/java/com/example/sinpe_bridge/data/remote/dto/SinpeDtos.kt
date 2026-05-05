package com.example.sinpe_bridge.data.remote.dto

data class SinpeValidacionDto(
    val id: String,
    val monto: Double,
    val nombrePagador: String,
    val referencia: String,
    val detalle: String,
    val fecha: String,
    val hora: String,
    val fechaHoraISO: String,
    val timestampMs: Long,
    val textoOriginal: String
)

data class SinpeResponseDto(
    val valido: Boolean,
    val motivo: String? = null
)