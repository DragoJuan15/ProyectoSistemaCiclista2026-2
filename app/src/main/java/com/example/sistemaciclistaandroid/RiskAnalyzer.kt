
package com.example.sistemaciclistaandroid

class RiskAnalyzer {

    enum class RiskLevel {
        NORMAL,
        PRECAUCION,
        CASI_ACCIDENTE,
        PELIGRO
    }

    data class RiskResult(
        val level: RiskLevel,
        val message: String,
        val soundId: Int?
    )

    private data class ObjectHistory(
        var previousArea: Float = 0f,
        var consecutiveFrames: Int = 0
    )

    private val history = mutableMapOf<Int, ObjectHistory>()

    private var lastSoundTime = 0L
    private var lastLevel = RiskLevel.NORMAL

    private val normalCooldown = 3500L
    private val nearCooldown = 1800L
    private val dangerCooldown = 3000L

    fun analyze(
        detections: List<Detection>,
        currentTime: Long = System.currentTimeMillis()
    ): RiskResult {

        if (detections.isEmpty()) {
            history.clear()
            lastLevel = RiskLevel.NORMAL

            return RiskResult(
                RiskLevel.NORMAL,
                "Camino sin objetos detectados",
                null
            )
        }

        var highestRisk = RiskLevel.NORMAL
        var selectedSound: Int? = null
        var selectedMessage = "Objetos detectados"

        val activeClasses = detections.map { it.classId }.toSet()
        history.keys.retainAll(activeClasses)

        for (detection in detections) {

            if (detection.classId !in 0..2) continue

            val area = detection.width * detection.height

            val objectHistory = history.getOrPut(detection.classId) {
                ObjectHistory()
            }

            val growing = objectHistory.previousArea > 0f &&
                    area > objectHistory.previousArea * 1.12f

            objectHistory.consecutiveFrames++

            val centered = detection.centerX in 0.25f..0.75f
            val inPath = detection.centerY > 0.35f

            val risk = when {
                area >= 0.20f && centered && inPath ->
                    RiskLevel.PELIGRO

                area >= 0.10f && centered && inPath && growing ->
                    RiskLevel.CASI_ACCIDENTE

                area >= 0.05f && centered && inPath ->
                    RiskLevel.PRECAUCION

                else ->
                    RiskLevel.NORMAL
            }

            val stableRisk = if (objectHistory.consecutiveFrames >= 3) {
                risk
            } else {
                RiskLevel.NORMAL
            }

            if (stableRisk.ordinal > highestRisk.ordinal) {
                highestRisk = stableRisk

                selectedSound = when (stableRisk) {
                    RiskLevel.PELIGRO -> AudioManager.PELIGRO
                    RiskLevel.CASI_ACCIDENTE -> AudioManager.CASI_ACCIDENTE
                    RiskLevel.PRECAUCION -> when (detection.classId) {
                        0 -> AudioManager.VEHICULO
                        1 -> AudioManager.PEATON
                        else -> AudioManager.OBJETO
                    }
                    else -> null
                }

                selectedMessage = when (stableRisk) {
                    RiskLevel.PELIGRO -> "RIESGO CRÍTICO"
                    RiskLevel.CASI_ACCIDENTE -> "POSIBLE CASI ACCIDENTE"
                    RiskLevel.PRECAUCION ->
                        "Precaución: ${detection.className}"
                    else -> "Objetos detectados"
                }
            }

            objectHistory.previousArea = area
        }

        val cooldown = when (highestRisk) {
            RiskLevel.PELIGRO -> dangerCooldown
            RiskLevel.CASI_ACCIDENTE -> nearCooldown
            RiskLevel.PRECAUCION -> normalCooldown
            RiskLevel.NORMAL -> 0L
        }

        val levelChanged = highestRisk != lastLevel
        val cooldownFinished = currentTime - lastSoundTime >= cooldown

        val soundToPlay = if (
            selectedSound != null &&
            (levelChanged || cooldownFinished)
        ) {
            lastSoundTime = currentTime
            selectedSound
        } else {
            null
        }

        lastLevel = highestRisk

        return RiskResult(
            highestRisk,
            selectedMessage,
            soundToPlay
        )
    }

    fun reset() {
        history.clear()
        lastSoundTime = 0L
        lastLevel = RiskLevel.NORMAL
    }
}
