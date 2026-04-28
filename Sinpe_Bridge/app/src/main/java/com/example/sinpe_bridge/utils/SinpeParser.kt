package com.example.sinpe_bridge.utils

import android.util.Log
import com.example.sinpe_bridge.model.SinpeMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Parser para SMS reales de SINPE Móvil del Banco Nacional (BN).
 *
 * Formatos reales observados:
 *
 * Formato 1 (con número de teléfono):
 * "Ha recibido 6.000,00 colones por BN SINPE MOVIL de Camacho Naomi.
 *  Pago -64582529. Referencia 20260313151830109677588823."
 *
 * Formato 2 (con Transferencia_SINPE):
 * "Ha recibido 650 colones por BN SINPE MOVIL de HERNANDEZ RAMIREZ NAIDELYN FIORELLA.
 *  Transferencia_SINPE. Referencia 202604151528300930 7412481."
 */
object SinpeParser {

    private const val TAG = "SinpeParser"

    /**
     * Parsea el texto del SMS y retorna un SinpeMessage, o null si no se puede parsear.
     */
    fun parsearSms(texto: String): SinpeMessage? {
        return try {
            // Limpiar saltos de línea y espacios extras
            val textoLimpio = texto.replace("\n", " ").replace("\r", " ")
                .replace(Regex("\\s+"), " ").trim()

            Log.d(TAG, "Parseando SMS: $textoLimpio")

            // Verificar que es un mensaje de SINPE BN
            if (!esMensajeSinpe(textoLimpio)) {
                Log.w(TAG, "No es un SMS de SINPE")
                return null
            }

            val monto = extraerMonto(textoLimpio) ?: run {
                Log.w(TAG, "No se pudo extraer monto")
                return null
            }

            val pagador = extraerPagador(textoLimpio) ?: run {
                Log.w(TAG, "No se pudo extraer pagador")
                return null
            }

            val referencia = extraerReferencia(textoLimpio) ?: run {
                Log.w(TAG, "No se pudo extraer referencia")
                return null
            }

            val fechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(Date())

            val resultado = SinpeMessage(
                monto = monto,
                nombrePagador = pagador,
                referencia = referencia,
                fechaHora = fechaHora,
                textoOriginal = texto
            )

            Log.d(TAG, "Parseado OK -> Monto: $monto | Pagador: $pagador | Ref: $referencia")
            resultado

        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado al parsear: ${e.message}", e)
            null
        }
    }

    /**
     * Detecta si el texto es un SMS de SINPE Móvil del BN.
     */
    fun esMensajeSinpe(texto: String): Boolean {
        val lower = texto.lowercase()
        return (lower.contains("ha recibido") || lower.contains("recibido")) &&
                (lower.contains("sinpe") || lower.contains("colones")) &&
                lower.contains("referencia")
    }

    /**
     * Extrae el monto del mensaje.
     *
     * Soporta formatos:
     *   - "6.000,00 colones"   → 6000.0
     *   - "650 colones"        → 650.0
     *   - "1.500.000,50"       → 1500000.5
     */
    private fun extraerMonto(texto: String): Double? {
        // Patrón: número (con puntos y comas como separadores) seguido de "colones"
        val patron = Regex(
            """[Hh]a\s+recibido\s+([\d.,]+)\s+colones""",
            RegexOption.IGNORE_CASE
        )
        val match = patron.find(texto) ?: return null
        val montoStr = match.groupValues[1]

        return parsearNumero(montoStr)
    }

    /**
     * Convierte "6.000,00" o "650" o "1.500.000,50" a Double.
     * En Costa Rica: punto = separador de miles, coma = decimal.
     */
    private fun parsearNumero(str: String): Double? {
        return try {
            // Eliminar puntos de miles, reemplazar coma decimal por punto
            val normalizado = str.replace(".", "").replace(",", ".")
            normalizado.toDouble()
        } catch (e: NumberFormatException) {
            Log.e(TAG, "No se pudo convertir '$str' a número")
            null
        }
    }

    /**
     * Extrae el nombre del pagador.
     *
     * Patrón: "... SINPE MOVIL de [NOMBRE]. Pago..." o "... SINPE MOVIL de [NOMBRE]. Transferencia..."
     */
    private fun extraerPagador(texto: String): String? {
        // El nombre está entre "SINPE MOVIL de " y el siguiente punto
        val patron = Regex(
            """SINPE\s+MOVIL\s+de\s+([^.]+)\.""",
            RegexOption.IGNORE_CASE
        )
        val match = patron.find(texto) ?: return null
        return match.groupValues[1].trim()
    }

    /**
     * Extrae el número de referencia.
     *
     * El número de referencia viene después de "Referencia " y puede tener un espacio
     * en medio (como en "202604151528300930 7412481"), se elimina el espacio.
     */
    private fun extraerReferencia(texto: String): String? {
        val patron = Regex(
            """[Rr]eferencia\s+([\d\s]+)\."""
        )
        val match = patron.find(texto) ?: return null
        // Eliminar espacios internos en la referencia
        return match.groupValues[1].replace(" ", "").trim()
    }
}