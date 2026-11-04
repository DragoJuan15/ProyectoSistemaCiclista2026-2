package com.example.sistemaciclistaandroid

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import org.json.JSONObject
import ai.onnxruntime.OnnxTensor
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

class YoloDetector(context: Context) {

    enum class ModelType(
        val modelFile: String,
        val infoFile: String,
        val displayName: String
    ) {
        COLA(
            "bestCola.onnx",
            "bestCola.json",
            "V10 Cola"
        ),
        BLOQUES(
            "bestBloques.onnx",
            "bestBloques.json",
            "V10 Bloques"
        ),
        FINAL(
            "bestFinal.onnx",
            "bestFinal.json",
            "V10 FINAL"
        )
    }

    data class ModelInfo(
        val version: String,
        val model: String,
        val imageSize: Int,
        val classes: List<String>,
        val split: String,
        val isFinal: Boolean
    )

    data class ModelInspection(
        val modelName: String,
        val inputName: String,
        val inputShape: LongArray,
        val outputShapes: Map<String, LongArray>,
        val info: ModelInfo
    )

    private val appContext = context.applicationContext
    private val environment = OrtEnvironment.getEnvironment()

    private var currentSession: OrtSession? = null
    private var currentModel: ModelType? = null
    private var currentInfo: ModelInfo? = null

    /**
     * Carga un modelo ONNX desde assets.
     */
    @Synchronized
    fun loadModel(type: ModelType): ModelInspection {

        if (currentModel == type && currentSession != null) {
            return inspectCurrentModel()
        }

        val modelBytes = appContext.assets
            .open(type.modelFile)
            .use { it.readBytes() }

        val options = OrtSession.SessionOptions()

        val newSession = try {
            environment.createSession(modelBytes, options)
        } finally {
            options.close()
        }

        val newInfo = try {
            readModelInfo(type.infoFile)
        } catch (exception: Exception) {
            newSession.close()
            throw exception
        }

        currentSession?.close()

        currentSession = newSession
        currentModel = type
        currentInfo = newInfo

        return inspectCurrentModel()
    }

    /**
     * Lee los metadatos JSON correspondientes al modelo.
     */
    private fun readModelInfo(fileName: String): ModelInfo {

        val jsonText = appContext.assets
            .open(fileName)
            .bufferedReader()
            .use { it.readText() }

        val json = JSONObject(jsonText)

        val classArray = json.getJSONArray("clases")
        val classes = List(classArray.length()) { index ->
            classArray.getString(index)
        }

        return ModelInfo(
            version = json.optString("version"),
            model = json.optString("modelo"),
            imageSize = json.optInt("imgsz", 960),
            classes = classes,
            split = json.optString("split"),
            isFinal = json.optBoolean("final", false)
        )
    }

    /**
     * Inspecciona las dimensiones reales del modelo cargado.
     */
    @Synchronized
    fun inspectCurrentModel(): ModelInspection {

        val session = currentSession
            ?: error("No hay ningún modelo cargado.")

        val type = currentModel
            ?: error("No se ha seleccionado un modelo.")

        val info = currentInfo
            ?: error("No se encontraron los metadatos del modelo.")

        val inputName = session.inputNames.firstOrNull()
            ?: error("El modelo no tiene entradas.")

        val inputInfo = session.inputInfo[inputName]?.info as? TensorInfo
            ?: error("La entrada del modelo no es un tensor.")

        val outputShapes = session.outputInfo.mapValues { (_, nodeInfo) ->
            val tensorInfo = nodeInfo.info as? TensorInfo
                ?: error("Una salida del modelo no es un tensor.")

            tensorInfo.shape
        }

        return ModelInspection(
            modelName = type.displayName,
            inputName = inputName,
            inputShape = inputInfo.shape,
            outputShapes = outputShapes,
            info = info
        )
    }

    /**
     * Devuelve los metadatos del modelo actualmente seleccionado.
     */
    fun getCurrentInfo(): ModelInfo? = currentInfo

    /**
     * Indica qué modelo está cargado.
     */
    fun getCurrentModel(): ModelType? = currentModel


    fun detect(
        frame: ProcessedFrame,
        confidenceThreshold: Float = 0.35f,
        iouThreshold: Float = 0.45f
    ): List<Detection> {

        val session = currentSession ?: return emptyList()
        val info = currentInfo ?: return emptyList()

        val inputName = session.inputNames.first()

        val shape = longArrayOf(
            1,
            3,
            frame.imageSize.toLong(),
            frame.imageSize.toLong()
        )

        val inputTensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(frame.input),
            shape
        )

        try {
            session.run(mapOf(inputName to inputTensor)).use { result ->

                val outputName = session.outputNames.first()
                val outputTensor = result.get(outputName).get()
                val output = outputTensor.value as Array<Array<FloatArray>>

                val candidates = mutableListOf<Detection>()

                val candidateCount = output[0][0].size
                val classNames = info.classes

                for (i in 0 until candidateCount) {

                    var bestClass = -1
                    var bestConfidence = 0f

                    for (classIndex in classNames.indices) {
                        val confidence = output[0][4 + classIndex][i]

                        if (confidence > bestConfidence) {
                            bestConfidence = confidence
                            bestClass = classIndex
                        }
                    }

                    if (bestClass < 0 || bestConfidence < confidenceThreshold) {
                        continue
                    }

                    // Formato esperado: centro X, centro Y, ancho y alto.
                    val centerX = output[0][0][i]
                    val centerY = output[0][1][i]
                    val width = output[0][2][i]
                    val height = output[0][3][i]

                    val x1 = (centerX - width / 2f - frame.padX) / frame.scale
                    val y1 = (centerY - height / 2f - frame.padY) / frame.scale
                    val x2 = (centerX + width / 2f - frame.padX) / frame.scale
                    val y2 = (centerY + height / 2f - frame.padY) / frame.scale

                    val normalizedX1 =
                        (x1 / frame.originalWidth).coerceIn(0f, 1f)
                    val normalizedY1 =
                        (y1 / frame.originalHeight).coerceIn(0f, 1f)
                    val normalizedX2 =
                        (x2 / frame.originalWidth).coerceIn(0f, 1f)
                    val normalizedY2 =
                        (y2 / frame.originalHeight).coerceIn(0f, 1f)

                    if (normalizedX2 <= normalizedX1 ||
                        normalizedY2 <= normalizedY1
                    ) continue

                    candidates.add(
                        Detection(
                            classId = bestClass,
                            className = classNames[bestClass],
                            confidence = bestConfidence,
                            x1 = normalizedX1,
                            y1 = normalizedY1,
                            x2 = normalizedX2,
                            y2 = normalizedY2
                        )
                    )
                }

                return applyNms(candidates, iouThreshold)
            }
        } finally {
            inputTensor.close()
        }
    }

    private fun applyNms(
        detections: List<Detection>,
        iouThreshold: Float
    ): List<Detection> {

        val result = mutableListOf<Detection>()

        for (classId in detections.map { it.classId }.distinct()) {

            val remaining = detections
                .filter { it.classId == classId }
                .sortedByDescending { it.confidence }
                .toMutableList()

            while (remaining.isNotEmpty()) {
                val best = remaining.removeAt(0)
                result.add(best)

                val iterator = remaining.iterator()

                while (iterator.hasNext()) {
                    val other = iterator.next()

                    if (calculateIoU(best, other) > iouThreshold) {
                        iterator.remove()
                    }
                }
            }
        }

        return result.sortedByDescending { it.confidence }
    }

    private fun calculateIoU(
        a: Detection,
        b: Detection
    ): Float {

        val intersectionX1 = max(a.x1, b.x1)
        val intersectionY1 = max(a.y1, b.y1)
        val intersectionX2 = min(a.x2, b.x2)
        val intersectionY2 = min(a.y2, b.y2)

        val intersectionWidth = max(0f, intersectionX2 - intersectionX1)
        val intersectionHeight = max(0f, intersectionY2 - intersectionY1)

        val intersection = intersectionWidth * intersectionHeight

        val areaA = a.width * a.height
        val areaB = b.width * b.height

        val union = areaA + areaB - intersection

        return if (union <= 0f) 0f else intersection / union
    }


    /**
     * Libera los recursos de ONNX Runtime.
     */

    @Synchronized
    fun close() {
        currentSession?.close()
        currentSession = null
        currentModel = null
        currentInfo = null
    }
}
