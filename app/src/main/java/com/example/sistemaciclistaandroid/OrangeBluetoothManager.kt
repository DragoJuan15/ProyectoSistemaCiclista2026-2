
package com.example.sistemaciclistaandroid

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

data class OrangeBluetoothDevice(
    val name: String,
    val address: String
)

class OrangeBluetoothManager(context: Context) {

    companion object {
        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val adapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    private val _status = MutableStateFlow("Bluetooth detenido")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _orangeReady = MutableStateFlow(false)
    val orangeReady: StateFlow<Boolean> = _orangeReady.asStateFlow()

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages.asStateFlow()

    @Volatile
    private var socket: BluetoothSocket? = null

    @Volatile
    private var running = false

    fun getPairedDevices(): List<OrangeBluetoothDevice> {
        val bluetooth = adapter
            ?: throw IOException("Este teléfono no tiene Bluetooth")

        if (!bluetooth.isEnabled) {
            throw IOException("Activa el Bluetooth del teléfono")
        }

        return bluetooth.bondedDevices
            .map {
                OrangeBluetoothDevice(
                    name = it.name ?: "Dispositivo sin nombre",
                    address = it.address
                )
            }
            .sortedBy { it.name }
    }

    fun connect(address: String) {
        if (_connected.value || running) return

        val bluetooth = adapter

        if (bluetooth == null) {
            _status.value = "Bluetooth no disponible"
            return
        }

        if (!bluetooth.isEnabled) {
            _status.value = "Activa el Bluetooth del teléfono"
            return
        }

        _status.value = "Conectando con Orange Pi..."
        _orangeReady.value = false

        thread(name = "orange-bluetooth-connect") {
            var newSocket: BluetoothSocket? = null

            try {
                val device: BluetoothDevice =
                    bluetooth.getRemoteDevice(address)

                newSocket =
                    device.createRfcommSocketToServiceRecord(SPP_UUID)

                newSocket.connect()

                socket = newSocket
                running = true
                _connected.value = true
                _status.value = "Conectado. Esperando mensajes..."

                val input = newSocket.inputStream
                val buffer = ByteArray(512)
                val pending = StringBuilder()

                while (running) {
                    val count = input.read(buffer)

                    if (count <= 0) break

                    pending.append(
                        String(buffer, 0, count, Charsets.UTF_8)
                    )

                    var index = pending.indexOf("\n")

                    while (index >= 0) {
                        val line = pending.substring(0, index)
                            .trim()
                            .trimEnd('\r')

                        pending.delete(0, index + 1)

                        if (line.isNotEmpty()) {
                            publish(line)
                        }

                        index = pending.indexOf("\n")
                    }

                    if (pending.length > 2048) {
                        pending.clear()
                    }
                }

            } catch (e: SecurityException) {
                _status.value = "Falta permiso Bluetooth: ${e.message}"
            } catch (e: Exception) {
                _status.value = "Error Bluetooth: ${e.message}"
            } finally {
                running = false

                try {
                    newSocket?.close()
                } catch (_: Exception) {
                }

                socket = null
                _connected.value = false
                _orangeReady.value = false

                if (_status.value.startsWith("Conectado")) {
                    _status.value = "Conexión finalizada"
                }
            }
        }
    }

    private fun publish(message: String) {
        _messages.value = (listOf(message) + _messages.value).take(20)

        if (message == "ORANGE_PI_CONECTADO") {
            _orangeReady.value = true
            _status.value = "Orange Pi conectada y comunicándose"
        } else {
            _status.value = "Dato recibido: $message"
        }
    }

    fun disconnect() {
        running = false

        try {
            socket?.close()
        } catch (_: Exception) {
        }

        socket = null
        _connected.value = false
        _orangeReady.value = false
        _status.value = "Bluetooth desconectado"
    }

    fun stop() {
        disconnect()
    }
}
