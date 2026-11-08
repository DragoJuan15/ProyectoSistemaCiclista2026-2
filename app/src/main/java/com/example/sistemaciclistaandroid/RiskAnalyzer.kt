
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
        val soundId: Int?,
        val riskArea: Float? = null,
        val riskClassId: Int? = null,
        val riskClassName: String? = null,
        val riskGrowing: Boolean? = null,
        val consecutiveFrames: Int = 0
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

    private val dangerArea = 0.15f
    private val nearArea = 0.07f
    private val cautionArea = 0.03f

    private val growthThreshold = 1.05f
    private val requiredFrames = 2

    fun analyze(
        detections: List<Detection>,
        currentTime: Long = System.currentTimeMillis()
    ): RiskResult {

        if (detections.isEmpty()) {

            history.clear()
            lastLevel = RiskLevel.NORMAL

            return RiskResult(
                level = RiskLevel.NORMAL,
                message = "Camino sin objetos detectados",
                soundId = null
            )
        }

        var highestRisk = RiskLevel.NORMAL
        var selectedSound: Int? = null
        var selectedMessage = "Objetos detectados"

        var selectedArea: Float? = null
        var selectedClassId: Int? = null
        var selectedClassName: String? = null
        var selectedGrowing: Boolean? = null
        var selectedFrames = 0

        val activeClasses = detections
            .filter {
                it.classId == 0 ||
                        it.classId == 1 ||
                        it.classId == 3
            }
            .map { it.classId }
            .toSet()

        history.keys.retainAll(activeClasses)

        for (detection in detections) {

            if (detection.classId !in setOf(0, 1, 3)) {
                continue
            }

            val area = detection.width * detection.height

            val objectHistory = history.getOrPut(detection.classId) {
                ObjectHistory()
            }

            val growing =
                objectHistory.previousArea > 0f &&
                        area > objectHistory.previousArea * growthThreshold

            objectHistory.consecutiveFrames++

            val centered = detection.centerX in 0.15f..0.85f
            val inPath = detection.centerY > 0.25f

            val risk = when {

                area >= dangerArea && centered && inPath ->
                    RiskLevel.PELIGRO

                area >= nearArea && centered && inPath ->
                    RiskLevel.CASI_ACCIDENTE

                area >= cautionArea && centered && inPath ->
                    RiskLevel.PRECAUCION

                else ->
                    RiskLevel.NORMAL
            }

            val stableRisk =
                if (objectHistory.consecutiveFrames >= requiredFrames) {
                    risk
                } else {
                    RiskLevel.NORMAL
                }

            if (stableRisk.ordinal > highestRisk.ordinal) {

                highestRisk = stableRisk

                selectedArea = area
                selectedClassId = detection.classId
                selectedClassName = detection.className
                selectedGrowing = growing
                selectedFrames = objectHistory.consecutiveFrames

                selectedSound = when (stableRisk) {

                    RiskLevel.PELIGRO ->
                        AudioManager.PELIGRO

                    RiskLevel.CASI_ACCIDENTE ->
                        AudioManager.CASI_ACCIDENTE

                    RiskLevel.PRECAUCION -> when (detection.classId) {
                        0 -> AudioManager.VEHICULO
                        1 -> AudioManager.PEATON
                        else -> AudioManager.OBJETO
                    }

                    else -> null
                }

                selectedMessage = when (stableRisk) {

                    RiskLevel.PELIGRO ->
                        "RIESGO CRÍTICO"

                    RiskLevel.CASI_ACCIDENTE ->
                        "POSIBLE CASI ACCIDENTE"

                    RiskLevel.PRECAUCION ->
                        "Precaución: ${detection.className}"

                    else ->
                        "Objetos detectados"
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

        val cooldownFinished =
            currentTime - lastSoundTime >= cooldown

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
            level = highestRisk,
            message = selectedMessage,
            soundId = soundToPlay,
            riskArea = selectedArea,
            riskClassId = selectedClassId,
            riskClassName = selectedClassName,
            riskGrowing = selectedGrowing,
            consecutiveFrames = selectedFrames
        )
    }

    fun reset() {

        history.clear()

        lastSoundTime = 0L

        lastLevel = RiskLevel.NORMAL
    }
}
