package com.example.sinpe_bridge.model


data class SinpeMessage(
    val monto: Double,
    val nombrePagador: String,
    val referencia: String,
    val detalle: String,            // ej: "Transferencia_SINP", "PAGO", "POR GUAPI"
    val timestampMs: Long,
    val textoOriginal: String
)