package com.example.sistemaciclistaandroid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.sp

@Composable
fun DetectionOverlay(
    detections: List<Detection>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {

        Canvas(modifier = Modifier.fillMaxSize()) {

            val canvasWidth = size.width
            val canvasHeight = size.height

            detections.forEach { detection ->

                // Las coordenadas de Detection están normalizadas.
                val left = detection.x1 * canvasWidth
                val top = detection.y1 * canvasHeight
                val right = detection.x2 * canvasWidth
                val bottom = detection.y2 * canvasHeight

                val boxWidth = (right - left).coerceAtLeast(0f)
                val boxHeight = (bottom - top).coerceAtLeast(0f)

                val color = when (detection.classId) {
                    0 -> Color.Red
                    1 -> Color.Yellow
                    2 -> Color.Cyan
                    else -> Color.Green
                }

                // Dibuja el recuadro.
                drawRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight),
                    style = Stroke(width = 4f)
                )

                // Etiqueta y confianza.
                val label = String.format(
                    "%s %.1f%%",
                    detection.className,
                    detection.confidence * 100f
                )

                val textPaint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.WHITE
                    textSize = 34f
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }

                val backgroundPaint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.BLACK
                    alpha = 190
                }

                val textWidth = textPaint.measureText(label)
                val textHeight = textPaint.textSize

                val labelTop = (top - textHeight - 12f)
                    .coerceAtLeast(0f)

                // Fondo de la etiqueta.
                drawContext.canvas.nativeCanvas.drawRect(
                    left,
                    labelTop,
                    left + textWidth + 16f,
                    labelTop + textHeight + 12f,
                    backgroundPaint
                )

                // Texto.
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    left + 8f,
                    labelTop + textHeight,
                    textPaint
                )
            }
        }
    }
}
