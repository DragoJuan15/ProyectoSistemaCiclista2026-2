
package com.example.sistemaciclistaandroid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun OrangeBluetoothPanel() {

    val context = LocalContext.current

    val bluetooth = remember {
        OrangeBluetoothManager(context)
    }

    var devices by remember {
        mutableStateOf<List<OrangeBluetoothDevice>>(emptyList())
    }

    var deviceMessage by remember {
        mutableStateOf("Todavía no se han cargado dispositivos")
    }

    val status by bluetooth.status.collectAsState()
    val connected by bluetooth.connected.collectAsState()
    val orangeReady by bluetooth.orangeReady.collectAsState()
    val messages by bluetooth.messages.collectAsState()

    fun loadDevices() {
        try {
            devices = bluetooth.getPairedDevices()
            deviceMessage = if (devices.isEmpty()) {
                "No hay dispositivos emparejados. Vincula primero la Orange Pi desde los ajustes Bluetooth."
            } else {
                "Dispositivos emparejados: ${devices.size}"
            }
        } catch (e: Exception) {
            deviceMessage = e.message ?: "No se pudieron cargar los dispositivos"
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                loadDevices()
            } else {
                deviceMessage = "Permiso Bluetooth denegado"
            }
        }

    fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permission = Manifest.permission.BLUETOOTH_CONNECT

            if (
                ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(permission)
                return
            }
        }

        loadDevices()
    }

    DisposableEffect(bluetooth) {
        onDispose {
            bluetooth.stop()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Text(
            "Comunicación con Orange Pi",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            "Bluetooth: ${if (connected) "Conectado" else "Desconectado"}"
        )

        Text("Estado: $status")

        Text(
            "Identificación: ${
                if (orangeReady) "ORANGE_PI_CONECTADO recibido"
                else "Esperando identificación"
            }"
        )

        OutlinedButton(
            onClick = { requestBluetoothPermission() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !connected
        ) {
            Text("Buscar dispositivos emparejados")
        }

        Text(
            deviceMessage,
            style = MaterialTheme.typography.bodySmall
        )

        devices.forEach { device ->

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        device.name,
                        style = MaterialTheme.typography.titleSmall
                    )

                    Text(
                        device.address,
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedButton(
                        onClick = {
                            bluetooth.connect(device.address)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !connected
                    ) {
                        Text("Conectar con este dispositivo")
                    }
                }
            }
        }

        if (connected) {
            OutlinedButton(
                onClick = { bluetooth.disconnect() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Desconectar")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Último mensaje",
                    style = MaterialTheme.typography.labelMedium
                )

                Text(
                    messages.firstOrNull() ?: "Sin mensajes recibidos"
                )
            }
        }

        if (messages.size > 1) {
            Text(
                "Historial",
                style = MaterialTheme.typography.labelMedium
            )

            messages.drop(1).forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
