
package com.example.sistemaciclistaandroid

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

class AccidentSensorMonitor(
    context: Context,
    private val onPossibleAccident: (String) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gyroscope =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f

    private var accelerationSpikeTime = 0L
    private var rotationSpikeTime = 0L
    private var lastAccidentTime = 0L

    private var running = false

    // Umbrales iniciales; deberán calibrarse con pruebas reales.
    private val accelerationThreshold = 35f
    private val rotationThreshold = 5f

    // Los dos eventos deben ocurrir dentro de esta ventana.
    private val coincidenceWindow = 500L

    // Evita repetir la misma alerta continuamente.
    private val accidentCooldown = 30000L

    fun start() {

        if (running) return

        running = true

        accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }

        gyroscope?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    fun stop() {

        running = false
        sensorManager.unregisterListener(this)
    }

    fun hasRequiredSensors(): Boolean {
        return accelerometer != null && gyroscope != null
    }

    override fun onSensorChanged(event: SensorEvent?) {

        if (!running || event == null) return

        val now = SystemClock.elapsedRealtime()

        when (event.sensor.type) {

            Sensor.TYPE_ACCELEROMETER -> {

                // Separar aproximadamente la gravedad
                // de la aceleración producida por el movimiento.
                val alpha = 0.8f

                gravityX =
                    alpha * gravityX +
                            (1f - alpha) * event.values[0]

                gravityY =
                    alpha * gravityY +
                            (1f - alpha) * event.values[1]

                gravityZ =
                    alpha * gravityZ +
                            (1f - alpha) * event.values[2]

                val linearX = event.values[0] - gravityX
                val linearY = event.values[1] - gravityY
                val linearZ = event.values[2] - gravityZ

                val accelerationMagnitude = sqrt(
                    linearX * linearX +
                            linearY * linearY +
                            linearZ * linearZ
                )

                if (accelerationMagnitude >= accelerationThreshold) {
                    accelerationSpikeTime = now
                }
            }

            Sensor.TYPE_GYROSCOPE -> {

                val rotationMagnitude = sqrt(
                    event.values[0] * event.values[0] +
                            event.values[1] * event.values[1] +
                            event.values[2] * event.values[2]
                )

                if (rotationMagnitude >= rotationThreshold) {
                    rotationSpikeTime = now
                }
            }
        }

        val impactDetected =
            accelerationSpikeTime > 0L &&
                    now - accelerationSpikeTime <= coincidenceWindow

        val rotationDetected =
            rotationSpikeTime > 0L &&
                    now - rotationSpikeTime <= coincidenceWindow

        val cooldownFinished =
            now - lastAccidentTime >= accidentCooldown

        if (
            impactDetected &&
            rotationDetected &&
            cooldownFinished
        ) {

            lastAccidentTime = now

            accelerationSpikeTime = 0L
            rotationSpikeTime = 0L

            onPossibleAccident(
                "Se detectó un impacto y una rotación brusca. " +
                        "Se requiere verificar un posible accidente."
            )
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {
        // No se requiere una acción adicional.
    }
}
