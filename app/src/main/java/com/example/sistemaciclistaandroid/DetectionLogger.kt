
package com.example.sistemaciclistaandroid

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DetectionLogger(context: Context) {

    private val appContext = context.applicationContext

    private var writer: BufferedWriter? = null
    private var currentFile: File? = null

    private val lock = Any()

    private val dateFormat =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val fileDateFormat =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    @Volatile
    private var active = false

    fun startSession(): File? {

        synchronized(lock) {

            if (active) return currentFile

            return try {

                val directory = File(
                    appContext.filesDir,
                    "registros_ciclista"
                )

                if (!directory.exists()) {
                    directory.mkdirs()
                }

                val fileName =
                    "detecciones_${fileDateFormat.format(Date())}.csv"

                val file = File(directory, fileName)

                writer = BufferedWriter(FileWriter(file, true))
                currentFile = file

                writer?.apply {
                    write(
                        "fecha_hora,camera,class_id,class_name," +
                                "confidence,x1,y1,x2,y2,width,height," +
                                "risk_level,risk_message,risk_class_id," +
                                "risk_class_name,risk_area,risk_growing," +
                                "risk_consecutive_frames,inference_ms,fps,event"
                    )
                    newLine()
                    flush()
                }

                active = true

                file

            } catch (exception: Exception) {

                exception.printStackTrace()

                writer = null
                currentFile = null
                active = false

                null
            }
        }
    }

    fun logFrame(
        cameraName: String,
        detections: List<Detection>,
        risk: RiskAnalyzer.RiskResult,
        inferenceMs: Long,
        fps: Float
    ) {

        synchronized(lock) {

            if (!active || writer == null) return

            try {

                val timestamp = dateFormat.format(Date())

                if (detections.isEmpty()) {

                    writeRow(
                        listOf(
                            timestamp,
                            cameraName,
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            risk.level.name,
                            risk.message,
                            risk.riskClassId?.toString() ?: "",
                            risk.riskClassName ?: "",
                            risk.riskArea?.toString() ?: "",
                            risk.riskGrowing?.toString() ?: "",
                            risk.consecutiveFrames.toString(),
                            inferenceMs.toString(),
                            String.format(Locale.US, "%.2f", fps),
                            "SIN_DETECCIONES"
                        )
                    )

                } else {

                    detections.forEach { detection ->

                        val x1 =
                            detection.centerX - detection.width / 2f

                        val y1 =
                            detection.centerY - detection.height / 2f

                        val x2 =
                            detection.centerX + detection.width / 2f

                        val y2 =
                            detection.centerY + detection.height / 2f

                        writeRow(
                            listOf(
                                timestamp,
                                cameraName,
                                detection.classId.toString(),
                                detection.className,
                                String.format(
                                    Locale.US,
                                    "%.5f",
                                    detection.confidence
                                ),
                                x1.toString(),
                                y1.toString(),
                                x2.toString(),
                                y2.toString(),
                                detection.width.toString(),
                                detection.height.toString(),
                                risk.level.name,
                                risk.message,
                                risk.riskClassId?.toString() ?: "",
                                risk.riskClassName ?: "",
                                risk.riskArea?.toString() ?: "",
                                risk.riskGrowing?.toString() ?: "",
                                risk.consecutiveFrames.toString(),
                                inferenceMs.toString(),
                                String.format(Locale.US, "%.2f", fps),
                                "DETECCION"
                            )
                        )
                    }
                }

                writer?.flush()

            } catch (exception: Exception) {

                exception.printStackTrace()
            }
        }
    }

    fun logEvent(event: String, description: String) {

        synchronized(lock) {

            if (!active || writer == null) return

            try {

                writeRow(
                    listOf(
                        dateFormat.format(Date()),
                        "SISTEMA",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        description,
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        event
                    )
                )

                writer?.flush()

            } catch (exception: Exception) {

                exception.printStackTrace()
            }
        }
    }

    private fun writeRow(values: List<String>) {

        val line = values.joinToString(",") { value ->

            "\"" + value
                .replace("\"", "\"\"")
                .replace("\n", " ")
                .replace("\r", " ") + "\""
        }

        writer?.write(line)
        writer?.newLine()
    }

    fun stopSession(): File? {

        synchronized(lock) {

            if (!active) return currentFile

            val file = currentFile

            try {

                writer?.flush()
                writer?.close()

            } catch (exception: Exception) {

                exception.printStackTrace()

            } finally {

                writer = null
                active = false
            }

            return file
        }
    }

    fun isRecording(): Boolean = active

    fun getCurrentFile(): File? = currentFile
}
