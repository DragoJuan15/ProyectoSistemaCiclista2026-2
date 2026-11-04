package com.example.sistemaciclistaandroid

/**
 * Representa un objeto detectado por YOLO11.
 *
 * Las coordenadas del recuadro están normalizadas
 * entre 0.0 y 1.0 respecto a la imagen.
 */
data class Detection(
    val classId: Int,
    val className: String,
    val confidence: Float,

    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float
) {
    /**
     * Centro horizontal del objeto.
     */
    val centerX: Float
        get() = (x1 + x2) / 2f

    /**
     * Centro vertical del objeto.
     */
    val centerY: Float
        get() = (y1 + y2) / 2f

    /**
     * Ancho del recuadro.
     */
    val width: Float
        get() = x2 - x1

    /**
     * Alto del recuadro.
     */
    val height: Float
        get() = y2 - y1
}
