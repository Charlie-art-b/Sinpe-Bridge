package com.example.sinpe_bridge.data.remote.api

import android.util.Log
import com.example.sinpe_bridge.data.remote.mapper.toDto
import com.example.sinpe_bridge.data.remote.dto.SinpeResponseDto
import com.example.sinpe_bridge.model.SinpePaymentItem
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object BackendClient {

    private const val BASE_URL = "http://10.0.2.2:8080"
    private const val ENDPOINT = "/api/validar-sinpe"
    private const val TIMEOUT_MS = 30_000

    private val gson = Gson()

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

            // 🔄 Convertir a DTO
            val dto = item.toDto()
            val json = gson.toJson(dto)

            Log.d("BackendClient", "Enviando DTO: $json")

            // 📤 Enviar request
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(json)
                writer.flush()
            }

            val responseCode = connection.responseCode
            Log.d("BackendClient", "Respuesta HTTP: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                Log.d("BackendClient", "Body: $responseBody")

                val response = gson.fromJson(responseBody, SinpeResponseDto::class.java)
                response.valido

            } else {
                val errorBody =
                    connection.errorStream?.bufferedReader()?.readText() ?: "sin detalle"
                throw Exception("Error HTTP $responseCode: $errorBody")
            }

        } catch (e: Exception) {
            Log.e("BackendClient", "Error enviando SINPE", e)
            false
        } finally {
            connection.disconnect()
        }
    }
}