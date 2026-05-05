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
 * Receptor de SMS entrantes filtrado por remitentes conocidos del BN SINPE.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    private val gson = Gson()

    companion object {
        // Remitentes conocidos del Banco Nacional para SINPE Móvil
        // Agrega aquí cualquier otro número/shortcode que identifiques
        private val REMITENTES_SINPE = setOf(
            "50501",     // shortcode BN SINPE Móvil
            "505",
            "BN",
            "BNCR",
            "SINPE",
            "BACCR",
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            // ⚠️ Loguear TODOS los SMS que llegan — sirve para identificar
            // el remitente exacto del BN en tu celular específico
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

                val esRemitenteConocido = REMITENTES_SINPE.any { conocido ->
                    remitente.equals(conocido, ignoreCase = true) ||
                            remitente.contains(conocido, ignoreCase = true)
                }

                val esTextoSinpe = SinpeParser.esMensajeSinpe(textoCompleto)

                Log.d("SmsReceiver", "Remitente '$remitente' | conocido=$esRemitenteConocido | textoSINPE=$esTextoSinpe")

                // Procesar si el remitente es conocido O si el texto parece SINPE
                if (esRemitenteConocido || esTextoSinpe) {
                    Log.d("SmsReceiver", "Procesando SMS de SINPE de: $remitente")

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
}