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
 * Filtra mensajes de SINPE Móvil BN y los pasa al ForegroundService.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    private val gson = Gson()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            // Agrupar partes del mismo SMS (los SMS largos llegan en varios PDUs)
            val mensajesAgrupados = mutableMapOf<String, StringBuilder>()

            for (sms in messages) {
                val remitente = sms.originatingAddress ?: "desconocido"
                mensajesAgrupados.getOrPut(remitente) { StringBuilder() }
                    .append(sms.messageBody)
            }

            for ((remitente, cuerpo) in mensajesAgrupados) {
                val textoCompleto = cuerpo.toString()
                Log.d("SmsReceiver", "SMS de: $remitente")
                Log.d("SmsReceiver", "Texto: $textoCompleto")

                if (SinpeParser.esMensajeSinpe(textoCompleto)) {
                    Log.d("SmsReceiver", "SMS de SINPE detectado")

                    val sinpeMessage = SinpeParser.parsearSms(textoCompleto)

                    if (sinpeMessage != null) {
                        Log.d("SmsReceiver", "Parseado OK - Ref: ${sinpeMessage.referencia}")

                        val json = gson.toJson(sinpeMessage)
                        val serviceIntent = Intent(context, SinpeForegroundService::class.java).apply {
                            action = SinpeForegroundService.ACTION_PROCESS_SMS
                            putExtra("sinpe_message_json", json)
                        }
                        context.startService(serviceIntent)

                    } else {
                        Log.w("SmsReceiver", "No se pudo parsear el SMS de SINPE")
                        Log.w("SmsReceiver", "Texto original: $textoCompleto")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error al procesar SMS", e)
        }
    }
}