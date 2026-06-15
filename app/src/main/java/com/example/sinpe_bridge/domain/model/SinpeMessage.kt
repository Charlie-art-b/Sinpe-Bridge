package com.example.sinpe_bridge.model

data class SinpeMessage(
    val monto: Double,
    val nombrePagador: String,
    val referencia: String,
    val detalle: String,
    val timestampMs: Long,
    val textoOriginal: String,
    // Nuevos campos para permitir edición total
    val fechaEditada: String? = null, // Formato yyyy-MM-dd
    val horaEditada: String? = null   // Formato HH:mm:ss
)
