package com.example.sinpe_bridge.utils

import android.util.Log
import com.example.sinpe_bridge.model.SinpeMessage

object SinpeParser {

    private const val TAG = "SinpeParser"

    /**
     * @param texto       Cuerpo del SMS.
     * @param timestampMs Timestamp real de recepción (epoch ms).
     *                    Desde historial: columna "date" de content://sms
     *                    Desde BroadcastReceiver: System.currentTimeMillis()
     */
    fun parsearSms(texto: String, timestampMs: Long = System.currentTimeMillis()): SinpeMessage? {
        return try {
            val textoLimpio = texto
                .replace("\n", " ").replace("\r", " ")
                .replace(Regex("\\s+"), " ").trim()

            Log.d(TAG, "Parseando SMS: $textoLimpio")

            if (!esMensajeSinpe(textoLimpio)) {
                Log.w(TAG, "No es un SMS de SINPE")
                return null
            }

            val monto = extraerMonto(textoLimpio) ?: run {
                Log.w(TAG, "No se pudo extraer monto"); return null
            }
            val pagador = extraerPagador(textoLimpio) ?: run {
                Log.w(TAG, "No se pudo extraer pagador"); return null
            }
            val referencia = extraerReferencia(textoLimpio) ?: run {
                Log.w(TAG, "No se pudo extraer referencia"); return null
            }

            val detalle = extraerDetalle(textoLimpio) ?: ""

            SinpeMessage(
                monto = monto,
                nombrePagador = pagador,
                referencia = referencia,
                detalle = detalle,
                timestampMs = timestampMs,
                textoOriginal = texto
            ).also {
                Log.d(TAG, "OK -> ₡$monto | $pagador | $detalle | $referencia | ts=$timestampMs")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error al parsear: ${e.message}", e); null
        }
    }

    fun esMensajeSinpe(texto: String): Boolean {
        val lower = texto.lowercase()
        // Aceptamos "referencia" o "comprobante"
        return (lower.contains("ha recibido") || lower.contains("recibido")) &&
                (lower.contains("sinpe") || lower.contains("colones")) &&
                (lower.contains("referencia") || lower.contains("comprobante"))
    }

    private fun extraerMonto(texto: String): Double? {
        val match = Regex("""[Hh]a\s+recibido\s+([\d.,]+)\s+colones""", RegexOption.IGNORE_CASE)
            .find(texto) ?: return null
        return parsearNumero(match.groupValues[1])
    }

    private fun parsearNumero(str: String): Double? {
        return try {
            val tienePunto = str.contains('.')
            val tieneComa  = str.contains(',')
            val normalizado = when {
                tienePunto && tieneComa ->
                    if (str.lastIndexOf('.') > str.lastIndexOf(','))
                        str.replace(",", "")
                    else
                        str.replace(".", "").replace(",", ".")
                tieneComa && !tienePunto ->
                    if (str.substringAfterLast(",").length == 3) str.replace(",", "")
                    else str.replace(",", ".")
                tienePunto && !tieneComa ->
                    if (str.substringAfterLast(".").length == 3) str.replace(".", "")
                    else str
                else -> str
            }
            normalizado.toDouble()
        } catch (e: NumberFormatException) {
            Log.e(TAG, "No se pudo convertir '$str'"); null
        }
    }

    private fun extraerPagador(texto: String): String? {
        // Opción 1 (BN): SINPE MOVIL de [PAGADOR].
        val matchBN = Regex("""SINPE\s+MOVIL\s+de\s+([^.]+)\.""", RegexOption.IGNORE_CASE).find(texto)
        if (matchBN != null) return matchBN.groupValues[1].trim()

        // Opción 2 (Davivienda): de [PAGADOR] por SINPE Movil
        val matchDav = Regex("""de\s+([^.]+?)\s+por\s+SINPE\s+MOVIL""", RegexOption.IGNORE_CASE).find(texto)
        if (matchDav != null) return matchDav.groupValues[1].trim()

        return null
    }

    /**
     * Extrae el detalle del pago: texto entre el punto después del nombre y "Referencia".
     * Ejemplos: "Transferencia_SINP", "PAGO", "Pago -64582529", "POR GUAPI"
     */
    private fun extraerDetalle(texto: String): String? {
        val match = Regex(
            """SINPE\s+MOVIL\s+de\s+[^.]+\.\s*([^.]+)\.\s*[Rr]eferencia""",
            RegexOption.IGNORE_CASE
        ).find(texto) ?: return null
        val detalle = match.groupValues[1].trim()
        return detalle.ifEmpty { null }
    }

    private fun extraerReferencia(texto: String): String? {
        // Buscamos "Referencia" o "Comprobante" seguido de números (y permitimos que no termine en punto)
        val match = Regex("""(?:[Rr]eferencia|[Cc]omprobante)\s+([\dOo\s]+)""").find(texto) ?: return null
        return match.groupValues[1]
            .replace(" ", "").replace("O", "0").replace("o", "0").trim()
    }
}