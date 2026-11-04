
package com.example.sistemaciclistaandroid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun OrangeUsbPanel() {

    val context = LocalContext.current
    val usb = remember { OrangeUsbManager(context) }

    DisposableEffect(usb) {
        usb.start()

        onDispose {
            usb.stop()
        }
    }

    val status by usb.status.collectAsState()
    val connected by usb.connected.collectAsState()
    val orangeReady by usb.orangeReady.collectAsState()
    val messages by usb.messages.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Text(
            text = "Comunicación Orange Pi",
            style = MaterialTheme.typography.titleMedium
        )

        Text("USB: ${if (connected) "Conectado" else "Desconectado"}")

        Text(
            "Estado: $status",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            "Identificación: ${
                if (orangeReady) "ORANGE_PI_CONECTADO recibido"
                else "Esperando identificación"
            }"
        )

        OutlinedButton(
            onClick = { usb.scan() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Buscar Orange Pi")
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {

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
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
