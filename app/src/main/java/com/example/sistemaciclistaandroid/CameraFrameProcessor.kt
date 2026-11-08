package com.example.sistemaciclistaandroid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** Imagen ya preparada para la entrada NCHW del modelo YOLO. */
data class ProcessedFrame(
    val input: FloatArray,
    val imageSize: Int,
    val originalWidth: Int,
    val originalHeight: Int,
    val scale: Float,
    val padX: Float,
    val padY: Float
)

object CameraFrameProcessor {
    private const val MODEL_SIZE = 960
    private const val PAD_COLOR = 114
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Procesamiento de la cámara trasera mediante CameraX. */
    fun process(image: ImageProxy): ProcessedFrame {
        val bitmap = yuvToBitmap(image)
        val rotated = rotateBitmap(bitmap, image.imageInfo.rotationDegrees)
        return processBitmap(rotated).also {
            if (rotated !== bitmap) rotated.recycle()
            bitmap.recycle()
        }
    }

    /**
     * Procesamiento de AUSBC. El callback de AUSBC entrega NV21 o RGBA.
     * formatName debe ser el nombre de IPreviewDataCallBack.DataFormat.
     */
    fun processUsb(
        data: ByteArray,
        width: Int,
        height: Int,
        formatName: String
    ): ProcessedFrame {
        require(width > 0 && height > 0) { "Dimensiones USB inválidas: ${width}x${height}" }

        val bitmap = when (formatName.uppercase()) {
            "NV21" -> nv21ToBitmap(data, width, height)
            "RGBA" -> rgbaToBitmap(data, width, height)
            else -> throw IllegalArgumentException(
                "Formato USB no compatible: $formatName. AUSBC debe entregar NV21 o RGBA."
            )
        }
        return processBitmap(bitmap).also { bitmap.recycle() }
    }

    private fun processBitmap(source: Bitmap): ProcessedFrame {
        val originalWidth = source.width
        val originalHeight = source.height
        val scale = minOf(
            MODEL_SIZE.toFloat() / originalWidth,
            MODEL_SIZE.toFloat() / originalHeight
        )
        val resizedWidth = (originalWidth * scale).toInt().coerceAtLeast(1)
        val resizedHeight = (originalHeight * scale).toInt().coerceAtLeast(1)
        val padX = (MODEL_SIZE - resizedWidth) / 2f
        val padY = (MODEL_SIZE - resizedHeight) / 2f

        val resized = Bitmap.createScaledBitmap(source, resizedWidth, resizedHeight, true)
        val inputBitmap = Bitmap.createBitmap(MODEL_SIZE, MODEL_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(inputBitmap).apply {
            drawColor(Color.rgb(PAD_COLOR, PAD_COLOR, PAD_COLOR))
            drawBitmap(resized, padX, padY, paint)
        }
        val input = bitmapToNchw(inputBitmap)
        if (resized !== source) resized.recycle()
        inputBitmap.recycle()

        return ProcessedFrame(
            input = input,
            imageSize = MODEL_SIZE,
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            scale = scale,
            padX = padX,
            padY = padY
        )
    }

    private fun nv21ToBitmap(data: ByteArray, width: Int, height: Int): Bitmap {
        val expected = width * height * 3 / 2
        require(data.size >= expected) {
            "Buffer NV21 incompleto: ${data.size} bytes; se requieren al menos $expected"
        }
        val yuv = YuvImage(data, android.graphics.ImageFormat.NV21, width, height, null)
        val output = ByteArrayOutputStream()
        check(yuv.compressToJpeg(Rect(0, 0, width, height), 95, output)) {
            "No se pudo convertir el fotograma NV21"
        }
        val bytes = output.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("No se pudo decodificar el fotograma NV21")
    }

    private fun rgbaToBitmap(data: ByteArray, width: Int, height: Int): Bitmap {
        val expected = width * height * 4
        require(data.size >= expected) {
            "Buffer RGBA incompleto: ${data.size} bytes; se requieren al menos $expected"
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(data, 0, expected))
        return bitmap
    }

    private fun yuvToBitmap(image: ImageProxy): Bitmap {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val pixels = IntArray(width * height)

        for (row in 0 until height) {
            for (col in 0 until width) {
                val yIndex = row * yPlane.rowStride + col * yPlane.pixelStride
                val uvRow = row / 2
                val uvCol = col / 2
                val uIndex = uvRow * uPlane.rowStride + uvCol * uPlane.pixelStride
                val vIndex = uvRow * vPlane.rowStride + uvCol * vPlane.pixelStride
                val y = (yBuffer.get(yIndex).toInt() and 0xFF) - 16
                val u = (uBuffer.get(uIndex).toInt() and 0xFF) - 128
                val v = (vBuffer.get(vIndex).toInt() and 0xFF) - 128
                val yy = maxOf(0, y)
                val r = ((298 * yy + 409 * v + 128) shr 8).coerceIn(0, 255)
                val g = ((298 * yy - 100 * u - 208 * v + 128) shr 8).coerceIn(0, 255)
                val b = ((298 * yy + 516 * u + 128) shr 8).coerceIn(0, 255)
                pixels[row * width + col] = Color.rgb(r, g, b)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun bitmapToNchw(bitmap: Bitmap): FloatArray {
        val input = FloatArray(3 * MODEL_SIZE * MODEL_SIZE)
        val pixels = IntArray(MODEL_SIZE * MODEL_SIZE)
        bitmap.getPixels(pixels, 0, MODEL_SIZE, 0, 0, MODEL_SIZE, MODEL_SIZE)
        val planeSize = MODEL_SIZE * MODEL_SIZE
        for (i in pixels.indices) {
            val pixel = pixels[i]
            input[i] = Color.red(pixel) / 255.0f
            input[planeSize + i] = Color.green(pixel) / 255.0f
            input[2 * planeSize + i] = Color.blue(pixel) / 255.0f
        }
        return input
    }
}
