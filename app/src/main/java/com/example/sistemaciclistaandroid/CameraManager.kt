package com.example.sistemaciclistaandroid

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(context: Context) {

    private val appContext = context.applicationContext

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private var imageAnalyzer: ImageAnalysis? = null

    /**
     * Inicia la cámara trasera y conecta la vista previa
     * con el analizador de imágenes.
     *
     * El callback recibe cada imagen y debe procesarla
     * de forma síncrona. ImageProxy se cierra automáticamente
     * al terminar el callback.
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onImageAvailable: (ImageProxy) -> Unit,
        onCameraReady: (() -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {

        val providerFuture =
            ProcessCameraProvider.getInstance(appContext)

        providerFuture.addListener({

            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .build()

                analysis.setAnalyzer(cameraExecutor) { imageProxy ->

                    try {
                        onImageAvailable(imageProxy)
                    } catch (exception: Exception) {
                        exception.printStackTrace()
                    } finally {
                        imageProxy.close()
                    }
                }

                imageAnalyzer = analysis

                val cameraSelector =
                    CameraSelector.DEFAULT_BACK_CAMERA

                provider.unbindAll()

                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    analysis
                )

                onCameraReady?.invoke()

            } catch (exception: Exception) {
                onError?.invoke(exception)
                exception.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(appContext))
    }

    /**
     * Detiene la cámara y libera los recursos asociados.
     */
    fun stopCamera() {
        imageAnalyzer?.clearAnalyzer()
        imageAnalyzer = null

        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    /**
     * Libera también el hilo de procesamiento.
     */
    fun release() {
        stopCamera()

        if (!cameraExecutor.isShutdown) {
            cameraExecutor.shutdown()
        }
    }
}
