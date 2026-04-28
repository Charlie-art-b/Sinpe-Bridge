package com.example.sinpe_bridge.repository

import android.util.Log
import com.example.sinpe_bridge.model.SinpePaymentItem
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente HTTP que envía el SINPE al backend y devuelve el resultado de validación.
 *
 * Protocolo esperado:
 *   POST /api/validar-sinpe
 *   Content-Type: application/json
 *   Body: { "id", "monto", "nombrePagador", "referencia", "fechaHora", "textoOriginal" }
 *
 *   Response 200: { "valido": true/false, "motivo": "..." }
 *   Response 4xx/5xx: se lanza excepción
 */
object BackendClient {

    // ⚙️  Cambia esta URL por la de tu servidor
    private const val BASE_URL = "http://10.0.2.2:8080"  // localhost desde emulador
    private const val ENDPOINT = "/api/validar-sinpe"
    private const val TIMEOUT_MS = 30_000

    private val gson = Gson()

    /**
     * Envía el pago al backend. Corre en IO dispatcher.
     * @return true si es válido, false si es rechazado
     * @throws Exception si hay error de red o respuesta inesperada
     */
    suspend fun enviarSinpe(item: SinpePaymentItem): Boolean = withContext(Dispatchers.IO) {
        val url = URL("$BASE_URL$ENDPOINT")
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }

            // Construir payload
            val payload = mapOf(
                "id" to item.id,
                "monto" to item.sinpeMessage.monto,
                "nombrePagador" to item.sinpeMessage.nombrePagador,
                "referencia" to item.sinpeMessage.referencia,
                "fechaHora" to item.sinpeMessage.fechaHora,
                "textoOriginal" to item.sinpeMessage.textoOriginal
            )
            val json = gson.toJson(payload)

            // Enviar
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(json)
                writer.flush()
            }

            val responseCode = connection.responseCode
            Log.d("BackendClient", "Respuesta HTTP: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                Log.d("BackendClient", "Body: $responseBody")

                val respuesta = gson.fromJson(responseBody, BackendResponse::class.java)
                respuesta.valido
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "sin detalle"
                throw Exception("Error HTTP $responseCode: $errorBody")
            }

        } finally {
            connection.disconnect()
        }
    }

    /** Modelo de respuesta del backend */
    private data class BackendResponse(
        val valido: Boolean,
        val motivo: String? = null
    )
}