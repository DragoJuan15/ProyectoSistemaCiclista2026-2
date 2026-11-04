
package com.example.sistemaciclistaandroid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                CiclistaApp()
            }
        }
    }
}

@Composable
fun CiclistaApp() {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraManager = remember { CameraManager(context) }
    val yoloDetector = remember { YoloDetector(context) }
    val audioManager = remember { AudioManager(context) }

    val riskAnalyzer = remember { RiskAnalyzer() }

    var riskMessage by remember {
        mutableStateOf("Sin evaluar")
    }

    var riskLevel by remember {
        mutableStateOf(RiskAnalyzer.RiskLevel.NORMAL)
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var cameraRunning by remember { mutableStateOf(false) }
    var cameraMessage by remember { mutableStateOf("Cámara detenida") }

    var selectedModel by remember {
        mutableStateOf(YoloDetector.ModelType.COLA)
    }

    var modelMessage by remember { mutableStateOf("Modelo sin cargar") }
    var modelDetails by remember { mutableStateOf("Sin información") }
    var isLoadingModel by remember { mutableStateOf(false) }
    var modelReady by remember { mutableStateOf(false) }

    var detections by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var fps by remember { mutableStateOf(0f) }
    var inferenceTime by remember { mutableStateOf(0L) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted

        cameraMessage = if (granted) {
            "Permiso concedido. Puedes iniciar la cámara."
        } else {
            "Se necesita permiso para utilizar la cámara."
        }
    }

    LaunchedEffect(Unit) {
        audioManager.loadAllSounds()
    }

    // Carga del modelo seleccionado.
    LaunchedEffect(selectedModel) {

        modelReady = false
        isLoadingModel = true
        modelMessage = "Cargando modelo..."
        modelDetails = "Inspeccionando tensores ONNX..."
        detections = emptyList()

        try {
            val inspection = withContext(Dispatchers.IO) {
                yoloDetector.loadModel(selectedModel)
                yoloDetector.inspectCurrentModel()
            }

            modelMessage = "Modelo cargado correctamente"

            modelDetails = buildString {
                appendLine("Modelo: ${inspection.modelName}")
                appendLine("Entrada: ${inspection.inputName}")
                appendLine(
                    "Dimensiones de entrada: " +
                            inspection.inputShape.contentToString()
                )
                appendLine("Salidas:")

                inspection.outputShapes.forEach { (outputName, shape) ->
                    appendLine("Salida: $outputName")
                    appendLine("Dimensiones: ${shape.contentToString()}")
                }

                inspection.info?.let { info ->
                    appendLine()
                    appendLine("Versión: ${info.version}")
                    appendLine("Tamaño de imagen: ${info.imageSize}")
                    appendLine("Clases: ${info.classes.joinToString()}")
                    appendLine("Split: ${info.split}")
                }
            }

            modelReady = true

        } catch (exception: Exception) {
            modelMessage = "Error al cargar el modelo"
            modelDetails = exception.message ?: "Error desconocido"
            modelReady = false
        }

        isLoadingModel = false
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.release()
            audioManager.release()
            yoloDetector.close()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = "Sistema de Monitoreo Ciclista",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "Cámara y análisis de modelos YOLO11",
            style = MaterialTheme.typography.bodyMedium
        )

        HorizontalDivider()

        // Selector de modelos.
        Text(
            text = "Modelo de inteligencia artificial",
            style = MaterialTheme.typography.titleMedium
        )

        var menuExpanded by remember { mutableStateOf(false) }

        Box {
            OutlinedButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoadingModel
            ) {
                Text(
                    when (selectedModel) {
                        YoloDetector.ModelType.COLA -> "V10 Cola"
                        YoloDetector.ModelType.BLOQUES -> "V10 Bloques"
                        YoloDetector.ModelType.FINAL -> "V10 FINAL"
                    }
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("V10 Cola") },
                    onClick = {
                        selectedModel = YoloDetector.ModelType.COLA
                        menuExpanded = false
                    }
                )

                DropdownMenuItem(
                    text = { Text("V10 Bloques") },
                    onClick = {
                        selectedModel = YoloDetector.ModelType.BLOQUES
                        menuExpanded = false
                    }
                )

                DropdownMenuItem(
                    text = { Text("V10 FINAL") },
                    onClick = {
                        selectedModel = YoloDetector.ModelType.FINAL
                        menuExpanded = false
                    }
                )
            }
        }

        Text(
            text = modelMessage,
            style = MaterialTheme.typography.bodyMedium
        )

        if (isLoadingModel) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = modelDetails,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }

        HorizontalDivider()

        // Vista de cámara y recuadros.
        Text(
            text = "Cámara",
            style = MaterialTheme.typography.titleMedium
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            DetectionOverlay(
                detections = detections,
                modifier = Modifier.fillMaxSize()
            )
        }

        Text(
            text = cameraMessage,
            style = MaterialTheme.typography.bodyMedium
        )

        Button(
            onClick = {
                if (!cameraPermissionGranted) {

                    permissionLauncher.launch(Manifest.permission.CAMERA)

                } else if (!cameraRunning) {

                    if (!modelReady) {
                        cameraMessage = "Espera a que el modelo termine de cargar."
                    } else {

                        cameraManager.startCamera(
                            lifecycleOwner = lifecycleOwner,
                            previewView = previewView,

                            onImageAvailable = { image ->

                                if (modelReady) {
                                    try {
                                        val startTime =
                                            SystemClock.elapsedRealtimeNanos()

                                        val frame =
                                            CameraFrameProcessor.process(image)

                                        val results =
                                            yoloDetector.detect(frame)

                                        val risk = riskAnalyzer.analyze(results)

                                        riskLevel = risk.level
                                        riskMessage = risk.message

                                        risk.soundId?.let { soundId ->
                                            audioManager.playSound(soundId)
                                        }

                                        val elapsed =
                                            (SystemClock.elapsedRealtimeNanos() -
                                                    startTime) / 1_000_000L

                                        inferenceTime = elapsed

                                        // Actualización de interfaz.
                                        detections = results

                                        // FPS aproximados según el tiempo
                                        // total de procesamiento.
                                        fps = if (elapsed > 0) {
                                            1000f / elapsed.toFloat()
                                        } else {
                                            0f
                                        }

                                    } catch (exception: Exception) {
                                        exception.printStackTrace()
                                    }
                                }
                            },

                            onCameraReady = {
                                cameraRunning = true
                                cameraMessage = "Cámara iniciada. YOLO activo."
                            },

                            onError = { error ->
                                cameraRunning = false
                                cameraMessage =
                                    "Error de cámara: ${error.message}"
                            }
                        )
                    }

                } else {

                    cameraManager.stopCamera()
                    riskAnalyzer.reset()
                    audioManager.stopAll()
                    riskLevel = RiskAnalyzer.RiskLevel.NORMAL
                    riskMessage = "Sin evaluar"
                    cameraRunning = false
                    detections = emptyList()
                    cameraMessage = "Cámara detenida"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    !cameraPermissionGranted -> "Conceder permiso de cámara"
                    cameraRunning -> "Detener cámara"
                    else -> "Iniciar cámara"
                }
            )
        }

        HorizontalDivider()

        // Métricas de procesamiento.
        Text(
            text = "Resultados de detección",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "Evaluación de seguridad",
            style = MaterialTheme.typography.titleMedium
        )

        Text("Nivel: ${riskLevel.name}")
        Text("Estado: $riskMessage")

        Text("Objetos detectados: ${detections.size}")

        Text("FPS de procesamiento: ${"%.1f".format(fps)}")

        Text("Tiempo de procesamiento: ${inferenceTime} ms")

        detections.forEach { detection ->
            Text(
                "${detection.className}: " +
                        "${"%.1f".format(detection.confidence * 100)}%"
            )
        }

        HorizontalDivider()

        Text(
            text = "Estado del sistema",
            style = MaterialTheme.typography.titleMedium
        )

        Text("Cámara: ${if (cameraRunning) "Activa" else "Inactiva"}")
        Text("Modelo: $modelMessage")
        Text("Inferencia: ${if (modelReady) "Disponible" else "No disponible"}")

        HorizontalDivider()

        // Comunicación USB con Orange Pi.
        OrangeBluetoothPanel()
    }
}
