package com.example.sinpe_bridge.model

import java.util.UUID

data class SinpePaymentItem(
    val id: String = UUID.randomUUID().toString(),
    val sinpeMessage: SinpeMessage,
    val timestamp: Long = System.currentTimeMillis(),
    val estado: EstadoValidacion = EstadoValidacion.PENDIENTE
)

enum class EstadoValidacion {
    PENDIENTE,
    ENVIANDO,
    ACEPTADO,
    RECHAZADO,
    ERROR
}