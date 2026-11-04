
package com.example.sistemaciclistaandroid

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.driver.UsbSerialDriver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class OrangeUsbManager(context: Context) {

    companion object {
        const val VID = 0x1D6B
        const val PID = 0x0104

        private const val ACTION_USB_PERMISSION =
            "com.example.sistemaciclistaandroid.USB_PERMISSION"

        private const val BAUD_RATE = 115200
        private const val READ_TIMEOUT = 500
    }

    private val appContext = context.applicationContext

    private val usbManager =
        appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _status = MutableStateFlow("USB detenido")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages.asStateFlow()

    private val _orangeReady = MutableStateFlow(false)
    val orangeReady: StateFlow<Boolean> = _orangeReady.asStateFlow()

    private var port: UsbSerialPort? = null
    private var readerThread: Thread? = null

    private val running = AtomicBoolean(false)

    @Volatile
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED,
                        false
                    )

                    if (granted) {
                        scan()
                    } else {
                        _status.value = "Permiso USB denegado"
                    }
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> scan()

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    disconnect()
                    _status.value = "Orange Pi desconectada"
                    scan()
                }
            }
        }
    }

    fun start() {
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(ACTION_USB_PERMISSION)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }

            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            receiverRegistered = true
        }

        scan()
    }

    @Synchronized
    fun scan() {
        if (port != null) return

        val devices = usbManager.deviceList.values.toList()

        if (devices.isEmpty()) {
            _connected.value = false
            _orangeReady.value = false
            _status.value = "Android no detecta dispositivos USB"
            return
        }

        val details = devices.joinToString("\n") {
            "${it.deviceName} | VID: ${
                "0x%04X".format(it.vendorId)
            } | PID: ${
                "0x%04X".format(it.productId)
            }"
        }

        val device = devices.firstOrNull {
            it.vendorId == VID && it.productId == PID
        }

        if (device == null) {
            _status.value = "USB detectado, pero no coincide con Orange Pi:\n$details"
            return
        }

        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            return
        }

        val driver = UsbSerialProber.getDefaultProber()
            .findAllDrivers(usbManager)
            .firstOrNull {
                it.device.deviceId == device.deviceId
            }

        if (driver == null) {
            _status.value =
                "Orange Pi detectada, pero no hay controlador serial compatible."
            return
        }

        connect(driver)
    }

    private fun requestPermission(device: UsbDevice) {
        val intent = Intent(ACTION_USB_PERMISSION)
            .setPackage(appContext.packageName)

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }

        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            intent,
            flags
        )

        _status.value = "Solicitando permiso USB..."
        usbManager.requestPermission(device, pendingIntent)
    }

    @Synchronized
    private fun connect(driver: UsbSerialDriver) {
        if (port != null) return

        val connection = usbManager.openDevice(driver.device)

        if (connection == null) {
            _status.value = "No se pudo abrir el dispositivo"
            return
        }

        val newPort = driver.ports.firstOrNull()

        if (newPort == null) {
            connection.close()
            _status.value = "No hay puerto serial disponible"
            return
        }

        try {
            newPort.open(connection)

            newPort.setParameters(
                BAUD_RATE,
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )

            try {
                newPort.setDTR(true)
                newPort.setRTS(true)
            } catch (_: Exception) {
            }

            port = newPort
            _connected.value = true
            _status.value = "Orange Pi conectada. Esperando mensaje..."

            startReader(newPort)

        } catch (e: Exception) {
            try {
                newPort.close()
            } catch (_: Exception) {
            }

            connection.close()
            _status.value = "Error serial: ${e.message}"
        }
    }

    private fun startReader(activePort: UsbSerialPort) {
        if (!running.compareAndSet(false, true)) return

        readerThread = thread(
            name = "orange-usb-reader",
            isDaemon = true
        ) {
            val buffer = ByteArray(512)
            val pending = StringBuilder()

            try {
                while (running.get()) {
                    val count = activePort.read(buffer, READ_TIMEOUT)

                    if (count <= 0) continue

                    pending.append(
                        String(buffer, 0, count, Charsets.UTF_8)
                    )

                    var index = pending.indexOf("\n")

                    while (index >= 0) {
                        val line = pending.substring(0, index)
                            .trim()
                            .trim('\r')

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
            } catch (e: IOException) {
                if (running.get()) {
                    _status.value = "Conexión perdida: ${e.message}"
                }
            } catch (e: Exception) {
                if (running.get()) {
                    _status.value = "Error de lectura: ${e.message}"
                }
            } finally {
                disconnect()
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

    @Synchronized
    private fun disconnect() {
        running.set(false)

        try {
            port?.close()
        } catch (_: Exception) {
        }

        port = null
        _connected.value = false
        _orangeReady.value = false
        readerThread = null
    }

    fun stop() {
        disconnect()

        if (receiverRegistered) {
            try {
                appContext.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }

            receiverRegistered = false
        }

        _status.value = "USB detenido"
    }
}
