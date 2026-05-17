package com.example.sinpe_bridge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.sinpe_bridge.service.SinpeForegroundService
import com.example.sinpe_bridge.utils.SinpeParser
import com.google.gson.Gson

/**
 * Receptor de SMS entrantes.
 * Filtra cualquier mensaje que contenga "sinpe movil" o "sinpe móvil"
 * sin importar banco, remitente ni capitalización.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    private val gson = Gson()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            for (sms in messages) {
                Log.d("SmsReceiver", "=== SMS ENTRANTE ===")
                Log.d("SmsReceiver", "Remitente : '${sms.originatingAddress}'")
                Log.d("SmsReceiver", "Texto     : '${sms.messageBody?.take(80)}'")
            }

            // Agrupar partes del mismo SMS por remitente (SMS largos vienen en PDUs)
            val mensajesAgrupados = mutableMapOf<String, StringBuilder>()
            for (sms in messages) {
                val remitente = sms.originatingAddress ?: "desconocido"
                mensajesAgrupados.getOrPut(remitente) { StringBuilder() }
                    .append(sms.messageBody)
            }

            for ((remitente, cuerpo) in mensajesAgrupados) {
                val textoCompleto = cuerpo.toString()
                val textoNormalizado = textoCompleto.sinAcentos()

                val esSinpeMovil = textoNormalizado.contains("sinpe movil", ignoreCase = true)

                Log.d("SmsReceiver", "Remitente '$remitente' | esSinpeMovil=$esSinpeMovil")

                if (esSinpeMovil) {
                    Log.d("SmsReceiver", "Procesando SMS SINPE de '$remitente'")

                    val sinpeMessage = SinpeParser.parsearSms(
                        texto = textoCompleto,
                        timestampMs = System.currentTimeMillis()
                    )

                    if (sinpeMessage != null) {
                        Log.d("SmsReceiver", "Parseado OK — Monto: ${sinpeMessage.monto} | Ref: ${sinpeMessage.referencia}")

                        val json = gson.toJson(sinpeMessage)
                        val serviceIntent = Intent(context, SinpeForegroundService::class.java).apply {
                            action = SinpeForegroundService.ACTION_PROCESS_SMS
                            putExtra("sinpe_message_json", json)
                        }
                        context.startService(serviceIntent)

                    } else {
                        Log.w("SmsReceiver", "No se pudo parsear — texto: $textoCompleto")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error al procesar SMS", e)
        }
    }

    /**
     * Elimina tildes y diacríticos para comparación robusta.
     * Ej: "SINPE Móvil" → "sinpe movil" antes de comparar.
     */
    private fun String.sinAcentos(): String {
        return this
            .replace('á', 'a').replace('Á', 'a')
            .replace('é', 'e').replace('É', 'e')
            .replace('í', 'i').replace('Í', 'i')
            .replace('ó', 'o').replace('Ó', 'o')
            .replace('ú', 'u').replace('Ú', 'u')
            .replace('ü', 'u').replace('Ü', 'u')
            .replace('ñ', 'n').replace('Ñ', 'n')
    }
}