package com.example.sinpe_bridge.data.remote.api

import android.util.Log
import com.example.sinpe_bridge.data.remote.mapper.toDto
import com.example.sinpe_bridge.data.remote.dto.VoucherResponseDto
import com.example.sinpe_bridge.model.SinpePaymentItem
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object BackendClient {

    private const val BASE_URL  = "http://192.168.0.5:5062"
    private const val ENDPOINT  = "/api/voucher/create"
    private const val TIMEOUT_MS = 120000

    private val gson = Gson()

    suspend fun enviarSinpe(item: SinpePaymentItem): Boolean = withContext(Dispatchers.IO) {
        val fullUrl = "$BASE_URL$ENDPOINT"
        Log.d("BackendClient", "--- INICIANDO PETICIÓN ---")
        Log.d("BackendClient", "1. Preparando URL: $fullUrl")
        
        val url = URL(fullUrl)
        Log.d("BackendClient", "2. Abriendo conexión...")
        val connection = url.openConnection() as HttpURLConnection

        try {
            Log.d("BackendClient", "3. Configurando headers y timeouts (Timeout: $TIMEOUT_MS ms)...")
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            Log.d("BackendClient", "4. Headers seteados correctamente.")

            val dto  = item.toDto()
            val json = gson.toJson(dto)
            Log.d("BackendClient", "5. JSON a enviar: $json")

            Log.d("BackendClient", "6. Escribiendo en el output stream...")
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(json)
                writer.flush()
            }
            Log.d("BackendClient", "7. Body enviado con éxito.")

            Log.d("BackendClient", "8. Obteniendo código de respuesta...")
            val responseCode = connection.responseCode
            Log.d("BackendClient", "9. Respuesta HTTP recibida: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                Log.d("BackendClient", "10. Body de respuesta exitosa: $responseBody")

                val response = gson.fromJson(responseBody, VoucherResponseDto::class.java)
                Log.d("BackendClient", "11. Deserialización OK. isSuccess: ${response.isSuccess}")
                response.isSuccess

            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "sin detalle"
                Log.e("BackendClient", "12. ERROR: Código $responseCode. Detalle: $errorBody")
                throw Exception("Error HTTP $responseCode: $errorBody")
            }

        } catch (e: Exception) {
            Log.e("BackendClient", "13. EXCEPCIÓNn CRÍTICA en enviarSinpe", e)
            Log.e("BackendClient", "Stacktrace completo: ${Log.getStackTraceString(e)}")
            false
        } finally {
            Log.d("BackendClient", "14. Cerrando conexión.")
            connection.disconnect()
            Log.d("BackendClient", "--- FIN DE PETICIÓN ---")
        }
    }
}