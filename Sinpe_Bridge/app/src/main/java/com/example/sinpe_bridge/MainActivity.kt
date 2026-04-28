package com.example.sinpe_bridge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.sinpe_bridge.model.EstadoValidacion
import com.example.sinpe_bridge.model.SinpePaymentItem
import com.example.sinpe_bridge.repository.SinpeRepository
import com.example.sinpe_bridge.service.SinpeForegroundService
import com.example.sinpe_bridge.presentation.viewmodel.ResultadoValidacion
import com.example.sinpe_bridge.presentation.viewmodel.SinpeViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: SinpeViewModel by viewModels()

    private val permisosSms = arrayOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS
    )

    // Launcher para solicitar permisos en tiempo de ejecución
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permisos ->
        val todosOk = permisos.all { it.value }
        if (todosOk) {
            // Permisos concedidos: cargar historial y arrancar servicio
            cargarHistorialSinpe()
            SinpeForegroundService.startService(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (tieneTodosLosPermisos()) {
            // Ya tiene permisos: cargar historial inmediatamente
            cargarHistorialSinpe()
            SinpeForegroundService.startService(this)
        } else {
            // Solicitar permisos al usuario
            requestPermissionsLauncher.launch(permisosSms)
        }

        setContent {
            MaterialTheme(
                colorScheme = dynamicLightColorScheme(this)
            ) {
                SinpeBridgeApp(viewModel)
            }
        }
    }

    private fun tieneTodosLosPermisos(): Boolean {
        return permisosSms.all { permiso ->
            ContextCompat.checkSelfPermission(this, permiso) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Lee los SMS de SINPE del historial del dispositivo y los carga en el repositorio.
     * Así la lista aparece al abrir la app sin necesidad de recibir un SMS nuevo.
     */
    private fun cargarHistorialSinpe() {
        val smsList = leerSmsBN(this)
        smsList.forEach { smsRaw ->
            val mensaje = com.example.sinpe_bridge.utils.SinpeParser.parsearSms(smsRaw.cuerpo)
            if (mensaje != null) {
                SinpeRepository.agregarPago(mensaje)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pantalla raíz
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SinpeBridgeApp(viewModel: SinpeViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Diálogo de resultado
    uiState.ultimoResultado?.let { resultado ->
        ResultadoDialog(
            resultado = resultado,
            onDismiss = { viewModel.limpiarResultado() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SINPE Bridge",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    // Indicador "Activo"
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2E7D32))
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Activo",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomValidarBar(
                habilitado = uiState.seleccionadoId != null && !uiState.validando,
                validando = uiState.validando,
                onValidar = { viewModel.validar() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Banner de error
            AnimatedVisibility(
                visible = uiState.mensajeError != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                uiState.mensajeError?.let { error ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.limpiarError() }) {
                                Text("Cerrar", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Lista o estado vacío
            if (uiState.pagos.isEmpty()) {
                EstadoVacio(modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    text = "Pagos recibidos · ${uiState.pagos.size}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.pagos,
                        key = { it.id }
                    ) { pago ->
                        SinpeItemCard(
                            item = pago,
                            seleccionado = pago.id == uiState.seleccionadoId,
                            onClick = { viewModel.seleccionar(pago.id) }
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Card de pago individual
// ---------------------------------------------------------------------------

@Composable
fun SinpeItemCard(
    item: SinpePaymentItem,
    seleccionado: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (seleccionado)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outlineVariant

    val borderWidth = if (seleccionado) 2.dp else 0.5.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(borderWidth, borderColor),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Ícono de selección
            Icon(
                imageVector = if (seleccionado)
                    Icons.Filled.CheckCircle
                else
                    Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (seleccionado)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .size(22.dp)
                    .padding(top = 2.dp)
            )

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Nombre del pagador
                Text(
                    text = item.sinpeMessage.nombrePagador,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                // Monto
                Text(
                    text = formatearMonto(item.sinpeMessage.monto),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(4.dp))

                // Referencia
                Text(
                    text = "REF: ${item.sinpeMessage.referencia.take(20)}…",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Fecha/hora del SMS y timestamp de recepción
                Text(
                    text = item.sinpeMessage.fechaHora,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(8.dp))

            // Badge de estado
            EstadoBadge(estado = item.estado)
        }
    }
}

// ---------------------------------------------------------------------------
// Badge de estado
// ---------------------------------------------------------------------------

@Composable
fun EstadoBadge(estado: EstadoValidacion) {
    val (texto, containerColor, contentColor) = when (estado) {
        EstadoValidacion.PENDIENTE -> Triple(
            "Pendiente",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        EstadoValidacion.ENVIANDO -> Triple(
            "Enviando…",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        EstadoValidacion.ACEPTADO -> Triple(
            "Aceptado",
            Color(0xFFD4EDDA),
            Color(0xFF155724)
        )
        EstadoValidacion.RECHAZADO -> Triple(
            "Rechazado",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        EstadoValidacion.ERROR -> Triple(
            "Error",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Surface(
        shape = RoundedCornerShape(99.dp),
        color = containerColor
    ) {
        Text(
            text = texto,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Barra inferior de validación
// ---------------------------------------------------------------------------

@Composable
fun BottomValidarBar(
    habilitado: Boolean,
    validando: Boolean,
    onValidar: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = onValidar,
                enabled = habilitado,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (validando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Esperando respuesta del servidor…", fontSize = 14.sp)
                } else {
                    Text("Validar SINPE seleccionado", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Diálogo de resultado
// ---------------------------------------------------------------------------

@Composable
fun ResultadoDialog(
    resultado: ResultadoValidacion,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val (icono, titulo, subtitulo, colorIcono) = if (resultado.aceptado) {
                    listOf(
                        "✓",
                        "SINPE Válido",
                        "El pago fue verificado exitosamente por el servidor.",
                        Color(0xFF2E7D32)
                    )
                } else {
                    listOf(
                        "✗",
                        "SINPE Rechazado",
                        "El servidor indica que este pago no es legítimo.",
                        MaterialTheme.colorScheme.error
                    )
                }

                // Ícono grande
                Surface(
                    shape = CircleShape,
                    color = if (resultado.aceptado) Color(0xFFD4EDDA) else MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = icono as String,
                            fontSize = 28.sp,
                            color = colorIcono as Color
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = titulo as String,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = subtitulo as String,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Aceptar")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Estado vacío
// ---------------------------------------------------------------------------

@Composable
fun EstadoVacio(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("₡", fontSize = 48.sp, color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Sin pagos aún",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Los SMS de SINPE aparecerán aquí automáticamente",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.outline,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun formatearMonto(monto: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale("es", "CR"))
    formatter.minimumFractionDigits = 0
    formatter.maximumFractionDigits = 0
    return "₡${formatter.format(monto)}"
}

private fun tiempoRelativo(timestamp: Long): String {
    val diffMs = System.currentTimeMillis() - timestamp
    val diffMin = (diffMs / 60_000).toInt()
    return when {
        diffMin < 1 -> "ahora"
        diffMin < 60 -> "hace $diffMin min"
        diffMin < 1440 -> "hace ${diffMin / 60} h"
        else -> {
            val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}