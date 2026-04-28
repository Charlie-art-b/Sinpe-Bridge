package com.example.sinpe_bridge

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.sinpe_bridge.utils.SinpeParser

data class SmsRaw(
    val remitente: String,
    val cuerpo: String,
    val fecha: String
)

class DiagnosticoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DiagnosticoScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticoScreen() {
    val context = LocalContext.current
    var smsList by remember { mutableStateOf<List<SmsRaw>>(emptyList()) }
    var tienePermiso by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var mensajeEstado by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        tienePermiso = granted
        mensajeEstado = if (granted) "Permiso concedido" else "Permiso DENEGADO"
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Diagnóstico SMS") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Estado de permisos
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (tienePermiso) Color(0xFFD4EDDA) else Color(0xFFF8D7DA)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (tienePermiso) "✓ Permiso READ_SMS concedido"
                        else "✗ Sin permiso READ_SMS",
                        fontWeight = FontWeight.Medium,
                        color = if (tienePermiso) Color(0xFF155724) else Color(0xFF721C24)
                    )
                }
            }

            if (!tienePermiso) {
                Button(
                    onClick = { launcher.launch(Manifest.permission.READ_SMS) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Solicitar permiso READ_SMS")
                }
            }

            // Botón: leer SMS del historial
            Button(
                onClick = {
                    smsList = leerSmsBN(context)
                    mensajeEstado = "Encontrados ${smsList.size} SMS de SINPE en el historial"
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = tienePermiso
            ) {
                Text("Leer SMS de SINPE del historial")
            }

            if (mensajeEstado.isNotEmpty()) {
                Text(
                    text = mensajeEstado,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Lista de resultados
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(smsList) { sms ->
                    SmsCard(sms)
                }
            }
        }
    }
}

@Composable
fun SmsCard(sms: SmsRaw) {
    val parseado = SinpeParser.parsearSms(sms.cuerpo)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (parseado != null) Color(0xFF28A745) else Color(0xFFDC3545)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "De: ${sms.remitente}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = sms.fecha,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Texto original del SMS
            Text(
                text = sms.cuerpo,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )

            Divider(modifier = Modifier.padding(vertical = 4.dp))

            // Resultado del parser
            if (parseado != null) {
                Text("✓ Parser OK", fontSize = 12.sp, color = Color(0xFF155724), fontWeight = FontWeight.Medium)
                Text("Monto   : ₡${String.format("%,.0f", parseado.monto)}", fontSize = 12.sp)
                Text("Pagador : ${parseado.nombrePagador}", fontSize = 12.sp)
                Text("Ref     : ${parseado.referencia}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            } else {
                Text("✗ Parser FALLÓ — regex no coincidió", fontSize = 12.sp, color = Color(0xFF721C24), fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * Lee los SMS del historial del dispositivo filtrando por contenido "sinpe" o remitente conocido.
 */
fun leerSmsBN(context: android.content.Context): List<SmsRaw> {
    val lista = mutableListOf<SmsRaw>()

    try {
        val uri = Uri.parse("content://sms/inbox")
        val cursor: Cursor? = context.contentResolver.query(
            uri,
            arrayOf("address", "body", "date"),
            null,
            null,
            "date DESC"
        )

        cursor?.use {
            val colAddress = it.getColumnIndex("address")
            val colBody = it.getColumnIndex("body")
            val colDate = it.getColumnIndex("date")

            while (it.moveToNext()) {
                val remitente = it.getString(colAddress) ?: ""
                val cuerpo = it.getString(colBody) ?: ""
                val fechaMs = it.getLong(colDate)
                val fecha = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(fechaMs))

                // Incluir si el texto parece SINPE o si el remitente es el del BN
                if (SinpeParser.esMensajeSinpe(cuerpo) ||
                    remitente.contains("60405995") ||
                    remitente.contains("50501") ||
                    remitente.contains("sinpe", ignoreCase = true) ||
                    remitente.contains("BN", ignoreCase = true)
                ) {
                    lista.add(SmsRaw(remitente, cuerpo, fecha))
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("Diagnostico", "Error leyendo SMS: ${e.message}", e)
    }

    return lista
}