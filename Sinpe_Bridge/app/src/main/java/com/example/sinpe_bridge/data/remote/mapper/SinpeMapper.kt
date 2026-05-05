package com.example.sinpe_bridge.data.remote.mapper

import com.example.sinpe_bridge.data.remote.dto.SinpeValidacionDto
import com.example.sinpe_bridge.model.SinpePaymentItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun SinpePaymentItem.toDto(): SinpeValidacionDto {
    val ts = this.sinpeMessage.timestampMs

    val fecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ts))
    val hora  = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
    val iso   = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date(ts))

    return SinpeValidacionDto(
        id = this.id,
        monto = this.sinpeMessage.monto,
        nombrePagador = this.sinpeMessage.nombrePagador,
        referencia = this.sinpeMessage.referencia,
        detalle = this.sinpeMessage.detalle,
        fecha = fecha,
        hora = hora,
        fechaHoraISO = iso,
        timestampMs = ts,
        textoOriginal = this.sinpeMessage.textoOriginal
    )
}