
package com.example.sistemaciclistaandroid

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class AudioManager(context: Context) {

    companion object {
        const val VEHICULO = 1
        const val PEATON = 2
        const val OBJETO = 3
        const val CASI_ACCIDENTE = 4
        const val PELIGRO = 5
    }

    private val appContext = context.applicationContext

    private val soundPool: SoundPool

    private val sounds = mutableMapOf<Int, Int>()
    private val loadedSounds = mutableSetOf<Int>()

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(attributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                synchronized(loadedSounds) {
                    loadedSounds.add(sampleId)
                }
            }
        }
    }

    fun loadSound(resourceId: Int, soundId: Int) {
        val sampleId = soundPool.load(appContext, resourceId, 1)
        sounds[soundId] = sampleId
    }

    fun loadAllSounds() {
        if (sounds.isNotEmpty()) return

        loadSound(R.raw.alerta_vehiculo, VEHICULO)
        loadSound(R.raw.alerta_peaton, PEATON)
        loadSound(R.raw.alerta_objeto, OBJETO)
        loadSound(R.raw.alerta_casi_accidente, CASI_ACCIDENTE)
        loadSound(R.raw.alerta_peligro, PELIGRO)
    }

    fun playSound(soundId: Int) {
        val sampleId = sounds[soundId] ?: return

        val isLoaded = synchronized(loadedSounds) {
            loadedSounds.contains(sampleId)
        }

        if (!isLoaded) return

        soundPool.play(
            sampleId,
            1f,
            1f,
            1,
            0,
            1f
        )
    }

    fun stopAll() {
        soundPool.autoPause()
    }

    fun release() {
        soundPool.release()
        sounds.clear()

        synchronized(loadedSounds) {
            loadedSounds.clear()
        }
    }
}
