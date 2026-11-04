package com.example.sistemaciclistaandroid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

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

    fun process(image: ImageProxy): ProcessedFrame {
        val bitmap = yuvToBitmap(image)

        val rotation = image.imageInfo.rotationDegrees
        val rotatedBitmap = rotateBitmap(bitmap, rotation)

        val originalWidth = rotatedBitmap.width
        val originalHeight = rotatedBitmap.height

        val scale = minOf(
            MODEL_SIZE.toFloat() / originalWidth,
            MODEL_SIZE.toFloat() / originalHeight
        )

        val resizedWidth = (originalWidth * scale).toInt()
        val resizedHeight = (originalHeight * scale).toInt()

        val padX = (MODEL_SIZE - resizedWidth) / 2f
        val padY = (MODEL_SIZE - resizedHeight) / 2f

        val resizedBitmap = Bitmap.createScaledBitmap(
            rotatedBitmap,
            resizedWidth,
            resizedHeight,
            true
        )

        val inputBitmap = Bitmap.createBitmap(
            MODEL_SIZE,
            MODEL_SIZE,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(inputBitmap)
        canvas.drawColor(Color.rgb(PAD_COLOR, PAD_COLOR, PAD_COLOR))
        canvas.drawBitmap(resizedBitmap, padX, padY, paint)

        val input = bitmapToNchw(inputBitmap)

        if (bitmap !== rotatedBitmap) bitmap.recycle()
        if (rotatedBitmap !== bitmap) rotatedBitmap.recycle()
        resizedBitmap.recycle()
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

    private fun yuvToBitmap(image: ImageProxy): Bitmap {
        val width = image.width
        val height = image.height

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride

        val yPixelStride = yPlane.pixelStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        val pixels = IntArray(width * height)

        for (row in 0 until height) {
            for (col in 0 until width) {
                val yIndex = row * yRowStride + col * yPixelStride

                val uvRow = row / 2
                val uvCol = col / 2

                val uIndex = uvRow * uRowStride + uvCol * uPixelStride
                val vIndex = uvRow * vRowStride + uvCol * vPixelStride

                val y = (yBuffer.get(yIndex).toInt() and 0xFF) - 16
                val u = (uBuffer.get(uIndex).toInt() and 0xFF) - 128
                val v = (vBuffer.get(vIndex).toInt() and 0xFF) - 128

                val yValue = maxOf(0, y)

                val r = ((298 * yValue + 409 * v + 128) shr 8)
                    .coerceIn(0, 255)
                val g = ((298 * yValue - 100 * u - 208 * v + 128) shr 8)
                    .coerceIn(0, 255)
                val b = ((298 * yValue + 516 * u + 128) shr 8)
                    .coerceIn(0, 255)

                pixels[row * width + col] =
                    Color.rgb(r, g, b)
            }
        }

        return Bitmap.createBitmap(
            pixels,
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
    }

    private fun rotateBitmap(
        bitmap: Bitmap,
        degrees: Int
    ): Bitmap {
        if (degrees == 0) return bitmap

        val matrix = Matrix().apply {
            postRotate(degrees.toFloat())
        }

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    private fun bitmapToNchw(bitmap: Bitmap): FloatArray {
        val size = MODEL_SIZE
        val input = FloatArray(3 * size * size)

        val pixels = IntArray(size * size)
        bitmap.getPixels(
            pixels,
            0,
            size,
            0,
            0,
            size,
            size
        )

        val planeSize = size * size

        for (i in pixels.indices) {
            val pixel = pixels[i]

            input[i] = Color.red(pixel) / 255.0f
            input[planeSize + i] = Color.green(pixel) / 255.0f
            input[2 * planeSize + i] = Color.blue(pixel) / 255.0f
        }

        return input
    }
}
