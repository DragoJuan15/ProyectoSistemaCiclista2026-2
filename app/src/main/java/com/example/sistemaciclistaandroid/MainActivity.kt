
package com.example.sistemaciclistaandroid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager as AndroidCameraManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.SurfaceView

import java.util.concurrent.atomic.AtomicBoolean

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

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

    // -------------------------------------------------
    // CONTROLADORES GENERALES
    // -------------------------------------------------

    val mainHandler = remember {
        Handler(Looper.getMainLooper())
    }

    val cameraManager = remember {
        CameraManager(context)
    }

    val yoloDetector = remember {
        YoloDetector(context)
    }

    val audioManager = remember {
        AudioManager(context)
    }

    val detectionLogger = remember {
        DetectionLogger(context)
    }

    var logMessage by remember {
        mutableStateOf("Registro detenido")
    }

    // -------------------------------------------------
    // PERMISO PARA REALIZAR LLAMADAS
    // -------------------------------------------------

    val emergencyNumber = "6862140152"

    var callPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var emergencyCallMessage by remember {
        mutableStateOf("Llamada de emergencia preparada")
    }

    val callPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->

            callPermissionGranted = granted

            emergencyCallMessage = if (granted) {
                "Permiso para llamadas concedido"
            } else {
                "Permiso CALL_PHONE denegado. No se podrán realizar llamadas automáticas."
            }
        }

    // Solicita el permiso antes de utilizar el sistema.
    LaunchedEffect(Unit) {

        if (!callPermissionGranted) {
            callPermissionLauncher.launch(
                Manifest.permission.CALL_PHONE
            )
        }
    }

    // -------------------------------------------------
    // CONTROL AUTOMÁTICO DE LA LINTERNA
    // -------------------------------------------------

    val torchManager = remember {
        context.getSystemService(Context.CAMERA_SERVICE)
                as AndroidCameraManager
    }

    val torchCameraId = remember {

        try {

            torchManager.cameraIdList.firstOrNull { id ->

                val characteristics =
                    torchManager.getCameraCharacteristics(id)

                characteristics.get(
                    CameraCharacteristics.LENS_FACING
                ) == CameraCharacteristics.LENS_FACING_BACK &&
                        characteristics.get(
                            CameraCharacteristics.FLASH_INFO_AVAILABLE
                        ) == true
            }

        } catch (exception: Exception) {

            exception.printStackTrace()
            null
        }
    }

    var torchEnabled by remember {
        mutableStateOf(false)
    }

    var torchMessage by remember {
        mutableStateOf(
            if (torchCameraId != null) {
                "Linterna apagada"
            } else {
                "Este teléfono no tiene flash disponible"
            }
        )
    }

    // -------------------------------------------------
    // DETECCIÓN DE POSIBLES ACCIDENTES
    // -------------------------------------------------

    var accidentDetected by remember {
        mutableStateOf(false)
    }

    var accidentMessage by remember {
        mutableStateOf("Sin accidentes detectados")
    }

    val accidentMonitor = remember {

        AccidentSensorMonitor(context) { message ->

            mainHandler.post {

                // Evita procesar repetidamente el mismo accidente.
                if (!accidentDetected) {

                    accidentDetected = true
                    accidentMessage = message

                    detectionLogger.logEvent(
                        event = "POSIBLE_ACCIDENTE",
                        description = message
                    )

                    // ---------------------------------
                    // ENCENDER LINTERNA AUTOMÁTICAMENTE
                    // ---------------------------------

                    try {

                        if (torchCameraId != null) {

                            torchManager.setTorchMode(
                                torchCameraId,
                                true
                            )

                            torchEnabled = true

                            torchMessage =
                                "Linterna encendida por posible accidente"
                        }

                    } catch (exception: Exception) {

                        torchMessage =
                            "Error al encender linterna: ${exception.message}"

                        exception.printStackTrace()
                    }

                    // ---------------------------------
                    // REALIZAR LLAMADA AUTOMÁTICA
                    // ---------------------------------

                    val permission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CALL_PHONE
                    )

                    if (
                        permission == PackageManager.PERMISSION_GRANTED
                    ) {

                        try {

                            val intent = Intent(
                                Intent.ACTION_CALL,
                                Uri.parse("tel:$emergencyNumber")
                            )

                            detectionLogger.logEvent(
                                event = "LLAMADA_EMERGENCIA",
                                description = "Solicitud de llamada a $emergencyNumber"
                            )

                            val savedFile = detectionLogger.stopSession()

                            logMessage = if (savedFile != null) {
                                "Registro guardado: ${savedFile.name}"
                            } else {
                                "Registro detenido"
                            }

                            context.startActivity(intent)

                            emergencyCallMessage =
                                "Solicitud de llamada enviada a Android: $emergencyNumber"

                        } catch (exception: Exception) {

                            emergencyCallMessage =
                                "Error al realizar la llamada: ${exception.message}"

                            exception.printStackTrace()
                        }

                    } else {

                        callPermissionGranted = false

                        emergencyCallMessage =
                            "No se pudo llamar: falta el permiso CALL_PHONE"
                    }
                }
            }
        }
    }

    // Iniciar y detener los sensores con esta pantalla.
    DisposableEffect(accidentMonitor) {

        accidentMonitor.start()

        onDispose {
            accidentMonitor.stop()
        }
    }

    // -------------------------------------------------
    // ANALIZADORES DE RIESGO
    // -------------------------------------------------

    val riskAnalyzer = remember {
        RiskAnalyzer()
    }

    // Un analizador independiente por cámara USB.
    val usbRiskAnalyzers = remember {
        List(3) { RiskAnalyzer() }
    }

    // Sincronización de inferencia YOLO.
    val inferenceLock = remember {
        Any()
    }

    // Evitar procesamiento simultáneo de una cámara.
    val usbFrameBusy = remember {
        Array(3) { AtomicBoolean(false) }
    }

    // -------------------------------------------------
    // ESTADO DE LA CÁMARA DEL TELÉFONO
    // -------------------------------------------------

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

    var cameraRunning by remember {
        mutableStateOf(false)
    }

    var cameraMessage by remember {
        mutableStateOf("Cámara detenida")
    }

    // -------------------------------------------------
    // SELECTOR DE MODELOS
    // -------------------------------------------------

    var selectedModel by remember {
        mutableStateOf(YoloDetector.ModelType.COLA)
    }

    var modelMessage by remember {
        mutableStateOf("Modelo sin cargar")
    }

    var modelDetails by remember {
        mutableStateOf("Sin información")
    }

    var isLoadingModel by remember {
        mutableStateOf(false)
    }

    var modelReady by remember {
        mutableStateOf(false)
    }

    // -------------------------------------------------
    // DETECCIONES INDEPENDIENTES
    // -------------------------------------------------

    var phoneDetections by remember {
        mutableStateOf<List<Detection>>(emptyList())
    }

    val usbDetections = remember {
        mutableStateListOf<List<Detection>>(
            emptyList(),
            emptyList(),
            emptyList()
        )
    }

    val usbRiskLevels = remember {
        mutableStateListOf(
            RiskAnalyzer.RiskLevel.NORMAL,
            RiskAnalyzer.RiskLevel.NORMAL,
            RiskAnalyzer.RiskLevel.NORMAL
        )
    }

    val usbRiskMessages = remember {
        mutableStateListOf(
            "Sin evaluar",
            "Sin evaluar",
            "Sin evaluar"
        )
    }

    val usbFps = remember {
        mutableStateListOf(0f, 0f, 0f)
    }

    val usbInferenceTimes = remember {
        mutableStateListOf(0L, 0L, 0L)
    }

    // Métricas de la cámara del teléfono.
    var fps by remember {
        mutableStateOf(0f)
    }

    var inferenceTime by remember {
        mutableStateOf(0L)
    }

    // -------------------------------------------------
    // CÁMARAS USB UVC
    // -------------------------------------------------

    val usbCameraMessages = remember {
        mutableStateListOf(
            "Esperando cámara 1...",
            "Esperando cámara 2...",
            "Esperando cámara 3..."
        )
    }

    val usbPreviewViews = remember {
        List(3) {
            SurfaceView(context)
        }
    }

    val usbCameraController = remember {

        UsbCameraController(

            context = context,

            onFrame = { cameraIndex, bytes, width, height, format ->

                if (
                    cameraIndex in 0..2 &&
                    modelReady &&
                    usbFrameBusy[cameraIndex].compareAndSet(false, true)
                ) {

                    Thread {

                        try {

                            val startTime =
                                SystemClock.elapsedRealtimeNanos()

                            val frame =
                                CameraFrameProcessor.processUsb(
                                    bytes,
                                    width,
                                    height,
                                    format
                                )

                            val results = synchronized(inferenceLock) {
                                yoloDetector.detect(frame)
                            }

                            val risk =
                                usbRiskAnalyzers[cameraIndex].analyze(results)

                            val elapsed =
                                (
                                        SystemClock.elapsedRealtimeNanos() -
                                                startTime
                                        ) / 1_000_000L

                            val currentFps = if (elapsed > 0L) {
                                1000f / elapsed.toFloat()
                            } else {
                                0f
                            }

                            detectionLogger.logFrame(
                                cameraName = "USB_${cameraIndex + 1}",
                                detections = results,
                                risk = risk,
                                inferenceMs = elapsed,
                                fps = currentFps
                            )

                            risk.soundId?.let { soundId ->
                                audioManager.playSound(soundId)
                            }

                            mainHandler.post {

                                usbDetections[cameraIndex] = results

                                usbRiskLevels[cameraIndex] = risk.level

                                usbRiskMessages[cameraIndex] = risk.message

                                usbInferenceTimes[cameraIndex] = elapsed

                                usbFps[cameraIndex] =
                                    if (elapsed > 0L) {
                                        1000f / elapsed.toFloat()
                                    } else {
                                        0f
                                    }
                            }

                        } catch (exception: Exception) {

                            exception.printStackTrace()

                            mainHandler.post {

                                usbCameraMessages[cameraIndex] =
                                    "Error: ${exception.message ?: "desconocido"}"
                            }

                        } finally {

                            usbFrameBusy[cameraIndex].set(false)
                        }

                    }.start()
                }
            },

            onStatus = { cameraIndex, message ->

                mainHandler.post {

                    if (cameraIndex in 0..2) {
                        usbCameraMessages[cameraIndex] = message
                    }
                }
            }
        )
    }

    // Vinculación de superficies USB.
    DisposableEffect(usbCameraController, usbPreviewViews) {

        usbCameraController.bindSurfaces(
            usbPreviewViews.map { it.holder }
        )

        onDispose {
            usbCameraController.release()
        }
    }

    // -------------------------------------------------
    // PERMISO DE CÁMARA DEL TELÉFONO
    // -------------------------------------------------

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->

            cameraPermissionGranted = granted

            cameraMessage = if (granted) {
                "Permiso concedido. Puedes iniciar la cámara."
            } else {
                "Se necesita permiso para utilizar la cámara."
            }
        }

    // -------------------------------------------------
    // CARGA DE SONIDOS
    // -------------------------------------------------

    LaunchedEffect(Unit) {
        audioManager.loadAllSounds()
    }

    // -------------------------------------------------
    // CARGA DEL MODELO YOLO
    // -------------------------------------------------

    LaunchedEffect(selectedModel) {

        modelReady = false
        isLoadingModel = true

        modelMessage = "Cargando modelo..."
        modelDetails = "Inspeccionando tensores ONNX..."

        phoneDetections = emptyList()
        riskAnalyzer.reset()

        for (index in 0..2) {

            usbDetections[index] = emptyList()

            usbRiskAnalyzers[index].reset()

            usbRiskLevels[index] =
                RiskAnalyzer.RiskLevel.NORMAL

            usbRiskMessages[index] = "Sin evaluar"

            usbFps[index] = 0f

            usbInferenceTimes[index] = 0L
        }

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

                inspection.outputShapes.forEach {
                        (outputName, shape) ->

                    appendLine("Salida: $outputName")

                    appendLine(
                        "Dimensiones: ${shape.contentToString()}"
                    )
                }

                inspection.info?.let { info ->

                    appendLine()

                    appendLine("Versión: ${info.version}")

                    appendLine("Tamaño de imagen: ${info.imageSize}")

                    appendLine(
                        "Clases: ${info.classes.joinToString()}"
                    )

                    appendLine("Split: ${info.split}")
                }
            }

            modelReady = true

        } catch (exception: Exception) {

            modelMessage = "Error al cargar el modelo"

            modelDetails =
                exception.message ?: "Error desconocido"

            modelReady = false
        }

        isLoadingModel = false
    }

    // -------------------------------------------------
    // LIBERACIÓN DE RECURSOS
    // -------------------------------------------------

    DisposableEffect(Unit) {

        onDispose {

            detectionLogger.stopSession()

            // Apagar la linterna al salir de la pantalla.
            try {

                if (torchCameraId != null) {

                    torchManager.setTorchMode(
                        torchCameraId,
                        false
                    )
                }

            } catch (exception: Exception) {

                exception.printStackTrace()
            }

            torchEnabled = false

            cameraManager.release()

            audioManager.release()

            yoloDetector.close()
        }
    }

    // -------------------------------------------------
    // INTERFAZ
    // -------------------------------------------------

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

        // -------------------------------------------------
        // SELECTOR DE MODELOS
        // -------------------------------------------------

        Text(
            text = "Modelo de inteligencia artificial",
            style = MaterialTheme.typography.titleMedium
        )

        var menuExpanded by remember {
            mutableStateOf(false)
        }

        Box {

            OutlinedButton(
                onClick = {
                    menuExpanded = true
                },

                modifier = Modifier.fillMaxWidth(),

                enabled = !isLoadingModel
            ) {

                Text(
                    when (selectedModel) {

                        YoloDetector.ModelType.COLA ->
                            "V10 Cola"

                        YoloDetector.ModelType.BLOQUES ->
                            "V10 Bloques"

                        YoloDetector.ModelType.FINAL ->
                            "V10 FINAL"
                    }
                )
            }

            DropdownMenu(
                expanded = menuExpanded,

                onDismissRequest = {
                    menuExpanded = false
                }
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

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                text = modelDetails,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }

        HorizontalDivider()

        // -------------------------------------------------
        // ESTADO DE EMERGENCIA
        // -------------------------------------------------

        Text(
            text = "Sistema de emergencia",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = if (accidentDetected) {
                "POSIBLE ACCIDENTE DETECTADO"
            } else {
                "Sin accidentes detectados"
            },
            style = MaterialTheme.typography.bodyLarge
        )

        Text(
            text = accidentMessage,
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = emergencyCallMessage,
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "Número configurado: $emergencyNumber",
            style = MaterialTheme.typography.bodySmall
        )

        Text(
            text = torchMessage,
            style = MaterialTheme.typography.bodyMedium
        )

        HorizontalDivider()

        // -------------------------------------------------
        // CÁMARA DEL TELÉFONO
        // -------------------------------------------------

        Text(
            text = "Cámara del teléfono",
            style = MaterialTheme.typography.titleMedium
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {

            AndroidView(
                factory = {
                    previewView
                },

                modifier = Modifier.fillMaxSize()
            )

            DetectionOverlay(
                detections = phoneDetections,
                modifier = Modifier.fillMaxSize()
            )
        }

        Text(
            text = cameraMessage,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = logMessage,
            style = MaterialTheme.typography.bodySmall
        )


        Button(
            onClick = {

                if (!cameraPermissionGranted) {

                    permissionLauncher.launch(
                        Manifest.permission.CAMERA
                    )

                } else if (!cameraRunning) {

                    if (!modelReady) {

                        cameraMessage =
                            "Espera a que el modelo termine de cargar."

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
                                            synchronized(inferenceLock) {
                                                yoloDetector.detect(frame)
                                            }

                                        val risk =
                                            riskAnalyzer.analyze(results)

                                        riskLevel = risk.level
                                        riskMessage = risk.message

                                        risk.soundId?.let { soundId ->
                                            audioManager.playSound(soundId)
                                        }

                                        val elapsed =
                                            (
                                                    SystemClock.elapsedRealtimeNanos() -
                                                            startTime
                                                    ) / 1_000_000L

                                        val currentFps = if (elapsed > 0L) {
                                            1000f / elapsed.toFloat()
                                        } else {
                                            0f
                                        }

                                        detectionLogger.logFrame(
                                            cameraName = "TELEFONO",
                                            detections = results,
                                            risk = risk,
                                            inferenceMs = elapsed,
                                            fps = currentFps
                                        )

                                        inferenceTime = elapsed

                                        phoneDetections = results

                                        fps = if (elapsed > 0L) {
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

                                val file = detectionLogger.startSession()

                                logMessage = if (file != null) {
                                    "Registrando datos: ${file.name}"
                                } else {
                                    "Error al iniciar el registro"
                                }

                                cameraMessage =
                                    "Cámara iniciada. YOLO activo."
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

                    detectionLogger.logEvent(
                        event = "CAMARA_DETENIDA",
                        description = "El usuario detuvo la cámara"
                    )

                    val savedFile = detectionLogger.stopSession()

                    logMessage = if (savedFile != null) {
                        "Registro guardado: ${savedFile.name}"
                    } else {
                        "Registro detenido"
                    }

                    riskAnalyzer.reset()

                    audioManager.stopAll()

                    riskLevel =
                        RiskAnalyzer.RiskLevel.NORMAL

                    riskMessage = "Sin evaluar"

                    cameraRunning = false

                    phoneDetections = emptyList()

                    cameraMessage = "Cámara detenida"
                }
            },

            modifier = Modifier.fillMaxWidth()

        ) {

            Text(
                when {

                    !cameraPermissionGranted ->
                        "Conceder permiso de cámara"

                    cameraRunning ->
                        "Detener cámara"

                    else ->
                        "Iniciar cámara"
                }
            )
        }

        HorizontalDivider()

        // -------------------------------------------------
        // CÁMARAS USB UVC
        // -------------------------------------------------

        Text(
            text = "Cámaras USB UVC",
            style = MaterialTheme.typography.titleMedium
        )

        usbPreviewViews.forEachIndexed { index, preview ->

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    Text(
                        text = "Cámara USB ${index + 1}",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {

                        AndroidView(
                            factory = {
                                preview
                            },

                            modifier = Modifier.fillMaxSize()
                        )

                        DetectionOverlay(
                            detections = usbDetections[index],
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Text(
                        text = usbCameraMessages[index],
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "Riesgo: ${usbRiskLevels[index].name}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "Evaluación: ${usbRiskMessages[index]}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "Objetos detectados: ${usbDetections[index].size}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "FPS: ${"%.1f".format(usbFps[index])}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "Procesamiento: ${usbInferenceTimes[index]} ms",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        HorizontalDivider()

        // -------------------------------------------------
        // RESULTADOS DE DETECCIÓN
        // -------------------------------------------------

        Text(
            text = "Resultados de detección",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "Cámara del teléfono",
            style = MaterialTheme.typography.titleSmall
        )

        Text("Nivel de riesgo: ${riskLevel.name}")

        Text("Estado: $riskMessage")

        Text("Objetos detectados: ${phoneDetections.size}")

        Text("FPS: ${"%.1f".format(fps)}")

        Text("Procesamiento: ${inferenceTime} ms")

        phoneDetections.forEach { detection ->

            Text(
                "${detection.className}: " +
                        "${"%.1f".format(detection.confidence * 100)}%"
            )
        }

        HorizontalDivider()

        // Resultados independientes de las cámaras USB.
        usbDetections.forEachIndexed { index, cameraDetections ->

            Text(
                text = "Cámara USB ${index + 1}",
                style = MaterialTheme.typography.titleSmall
            )

            Text("Nivel de riesgo: ${usbRiskLevels[index].name}")

            Text("Estado: ${usbRiskMessages[index]}")

            Text("Objetos detectados: ${cameraDetections.size}")

            Text("FPS: ${"%.1f".format(usbFps[index])}")

            Text("Procesamiento: ${usbInferenceTimes[index]} ms")

            cameraDetections.forEach { detection ->

                Text(
                    "${detection.className}: " +
                            "${"%.1f".format(detection.confidence * 100)}%"
                )
            }

            HorizontalDivider()
        }

        // -------------------------------------------------
        // ESTADO DEL SISTEMA
        // -------------------------------------------------

        Text(
            text = "Estado del sistema",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            "Cámara del teléfono: " +
                    if (cameraRunning) "Activa" else "Inactiva"
        )

        Text(
            "Linterna: " +
                    if (torchEnabled) "Encendida" else "Apagada"
        )

        Text(
            "Sensores de accidente: " +
                    if (accidentMonitor.hasRequiredSensors()) {
                        "Acelerómetro y giroscopio disponibles"
                    } else {
                        "Falta uno o más sensores"
                    }
        )

        Text(
            "Permiso de llamadas: " +
                    if (callPermissionGranted) "Concedido" else "No concedido"
        )

        usbCameraMessages.forEachIndexed { index, message ->
            Text("USB ${index + 1}: $message")
        }

        Text("Modelo: $modelMessage")

        Text(
            "Inferencia: " +
                    if (modelReady) "Disponible" else "No disponible"
        )

        HorizontalDivider()
    }
}
