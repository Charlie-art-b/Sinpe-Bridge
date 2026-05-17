package com.example.sinpe_bridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.sinpe_bridge.model.SinpeMessage
import com.example.sinpe_bridge.repository.SinpeRepository
import com.google.gson.Gson

/**
 * Foreground Service que mantiene la app activa y procesa los SMS de SINPE Móvil.
 * Al recibir un SMS parseado, lo publica en SinpeRepository para que la UI lo muestre.
 */
class SinpeForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "sinpe_bridge_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START_SERVICE"
        const val ACTION_STOP = "ACTION_STOP_SERVICE"
        const val ACTION_PROCESS_SMS = "ACTION_PROCESS_SMS"

        fun startService(context: Context) {
            val intent = Intent(context, SinpeForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SinpeForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d("SinpeService", "Servicio creado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification())
                Log.d("SinpeService", "Servicio iniciado en foreground")
            }

            ACTION_STOP -> {
                stopForeground(true)
                stopSelf()
                Log.d("SinpeService", "Servicio detenido")
                return START_NOT_STICKY
            }

            ACTION_PROCESS_SMS -> {
                val json = intent.getStringExtra("sinpe_message_json")
                if (json != null) procesarSmsDesdeJson(json)
            }
        }
        return START_STICKY
    }

    /**
     * Parsea el JSON del SMS y lo publica en el repositorio.
     * La UI reactiva (Compose) lo mostrará automáticamente.
     */
    private fun procesarSmsDesdeJson(json: String) {
        try {
            val sinpeMessage = Gson().fromJson(json, SinpeMessage::class.java)

            Log.d("SinpeService", "SMS SINPE recibido — Ref: ${sinpeMessage.referencia}")

            // Publicar al repositorio: la UI se actualiza sola vía StateFlow
            SinpeRepository.agregarPago(sinpeMessage)

            // Actualizar notificación con el último pago
            actualizarNotificacion(sinpeMessage)

        } catch (e: Exception) {
            Log.e("SinpeService", "Error al convertir JSON del SMS: ${e.message}")
        }
    }

    private fun actualizarNotificacion(mensaje: SinpeMessage) {
        val montoFormateado = "₡${String.format("%,.0f", mensaje.monto)}"
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nuevo SINPE recibido")
            .setContentText("$montoFormateado de ${mensaje.nombrePagador}")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notif)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SINPE Bridge Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servicio para procesar pagos SINPE Móvil automáticamente"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SINPE Bridge Activo")
            .setContentText("Escuchando pagos SINPE Móvil…")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SinpeService", "Servicio destruido")
    }
}