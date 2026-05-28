package com.example.sinpe_bridge.data.remote.mapper

import com.example.sinpe_bridge.data.remote.dto.VoucherCreateDto
import com.example.sinpe_bridge.model.SinpePaymentItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun SinpePaymentItem.toDto(receiverName: String = android.os.Build.MODEL): VoucherCreateDto {
    val ts = this.sinpeMessage.timestampMs

    val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts))
    val hora  = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

    return VoucherCreateDto(
        code        = this.id,
        voucherDate = fecha,
        amount      = this.sinpeMessage.monto.toInt(),
        details     = this.sinpeMessage.detalle,
        hour        = hora,
        sender      = this.sinpeMessage.nombrePagador,
        receiver    = receiverName
    )
}