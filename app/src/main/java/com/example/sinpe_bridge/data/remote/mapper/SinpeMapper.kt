package com.example.sinpe_bridge.data.remote.mapper

import com.example.sinpe_bridge.data.remote.dto.VoucherCreateDto
import com.example.sinpe_bridge.model.SinpePaymentItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun SinpePaymentItem.toDto(receiverName: String = "Carlos Robles"): VoucherCreateDto {
    val ts = this.sinpeMessage.timestampMs

    val fecha = this.sinpeMessage.fechaEditada ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts))
    val hora  = this.sinpeMessage.horaEditada ?: SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))

    return VoucherCreateDto(
        code        = this.sinpeMessage.referencia,
        voucherDate = fecha,
        amount      = this.sinpeMessage.monto.toInt(),
        details     = this.sinpeMessage.detalle,
        hour        = hora,
        sender      = this.sinpeMessage.nombrePagador,
        receiver    = receiverName
    )
}