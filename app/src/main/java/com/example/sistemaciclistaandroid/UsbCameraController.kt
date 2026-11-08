
package com.example.sistemaciclistaandroid

import android.content.Context
import android.hardware.usb.UsbDevice
import android.view.Surface
import android.view.SurfaceHolder
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.UVCCamera

class UsbCameraController(
    context: Context,
    private val onFrame: (
        cameraIndex: Int,
        bytes: ByteArray,
        width: Int,
        height: Int,
        format: String
    ) -> Unit,
    private val onStatus: (cameraIndex: Int, message: String) -> Unit
) {

    companion object {
        private const val CAMERA_COUNT = 3
    }

    private val appContext = context.applicationContext

    private val lock = Any()

    @Volatile
    private var released = false

    private var nextPermissionIndex = 0
    private var permissionInProgress = false

    private inner class CameraSlot(val index: Int) {

        val helper: ICameraHelper = CameraHelper()

        var attachCounter = 0
        var selectedDevice: UsbDevice? = null
        var surface: Surface? = null

        @Volatile
        var cameraOpen = false

        @Volatile
        var width = 640

        @Volatile
        var height = 480

        init {
            helper.setStateCallback(object : ICameraHelper.StateCallback {

                override fun onAttach(device: UsbDevice) {
                    synchronized(lock) {
                        if (released) return

                        attachCounter++

                        // Cada helper recibe los mismos eventos USB.
                        // El helper 0 toma la primera cámara,
                        // el helper 1 la segunda y el helper 2 la tercera.
                        if (attachCounter == index + 1) {
                            selectedDevice = device

                            onStatus(
                                index,
                                "Cámara USB ${index + 1} detectada."
                            )

                            requestNextPermission()
                        }
                    }
                }

                override fun onDeviceOpen(
                    device: UsbDevice,
                    isFirstOpen: Boolean
                ) {
                    if (released) return

                    onStatus(
                        index,
                        "Permiso concedido. Abriendo cámara ${index + 1}..."
                    )

                    helper.openCamera()

                    synchronized(lock) {
                        if (permissionInProgress &&
                            nextPermissionIndex == index
                        ) {
                            permissionInProgress = false
                            nextPermissionIndex++
                            requestNextPermission()
                        }
                    }
                }

                override fun onCameraOpen(device: UsbDevice) {
                    if (released) return

                    cameraOpen = true

                    val previewSize = helper.getPreviewSize()

                    if (previewSize != null) {
                        width = previewSize.width
                        height = previewSize.height
                    }

                    helper.setFrameCallback(
                        IFrameCallback { buffer ->
                            if (released || !cameraOpen) {
                                return@IFrameCallback
                            }

                            try {
                                val source = buffer.duplicate()
                                val bytes = ByteArray(source.remaining())
                                source.get(bytes)

                                onFrame(
                                    index,
                                    bytes,
                                    width,
                                    height,
                                    "NV21"
                                )
                            } catch (exception: Exception) {
                                exception.printStackTrace()
                            }
                        },
                        UVCCamera.PIXEL_FORMAT_NV21
                    )

                    helper.startPreview()

                    surface?.let {
                        if (it.isValid) {
                            helper.addSurface(it, false)
                        }
                    }

                    onStatus(
                        index,
                        "Cámara USB ${index + 1} activa: ${width}x${height}"
                    )
                }

                override fun onCameraClose(device: UsbDevice) {
                    cameraOpen = false
                    onStatus(index, "Vista previa cerrada.")
                }

                override fun onDeviceClose(device: UsbDevice) {
                    cameraOpen = false
                    onStatus(index, "Dispositivo USB cerrado.")
                }

                override fun onDetach(device: UsbDevice) {
                    cameraOpen = false

                    synchronized(lock) {
                        if (selectedDevice?.deviceId == device.deviceId) {
                            selectedDevice = null
                        }
                    }

                    onStatus(index, "Cámara USB desconectada.")
                }

                override fun onCancel(device: UsbDevice) {
                    onStatus(
                        index,
                        "Permiso rechazado para cámara ${index + 1}."
                    )

                    synchronized(lock) {
                        if (permissionInProgress &&
                            nextPermissionIndex == index
                        ) {
                            permissionInProgress = false
                            nextPermissionIndex++
                            requestNextPermission()
                        }
                    }
                }
            })
        }
    }

    private val slots = List(CAMERA_COUNT) { CameraSlot(it) }

    /**
     * Solicita los permisos en orden:
     * cámara 1 -> cámara 2 -> cámara 3.
     *
     * Solo se inicia una solicitud a la vez.
     */
    private fun requestNextPermission() {
        synchronized(lock) {
            if (released || permissionInProgress) return

            if (nextPermissionIndex >= CAMERA_COUNT) {
                return
            }

            val slot = slots[nextPermissionIndex]
            val device = slot.selectedDevice

            if (device == null) {
                return
            }

            permissionInProgress = true

            onStatus(
                slot.index,
                "Solicitando permiso USB (${slot.index + 1}/$CAMERA_COUNT)..."
            )

            try {
                slot.helper.selectDevice(device)
            } catch (exception: Exception) {
                exception.printStackTrace()

                permissionInProgress = false
                nextPermissionIndex++

                onStatus(
                    slot.index,
                    "Error al solicitar permiso USB."
                )

                requestNextPermission()
            }
        }
    }

    fun bindSurfaces(holders: List<SurfaceHolder>) {
        slots.forEachIndexed { index, slot ->
            if (index >= holders.size) return@forEachIndexed

            val holder = holders[index]

            holder.addCallback(object : SurfaceHolder.Callback {

                override fun surfaceCreated(holder: SurfaceHolder) {
                    slot.surface = holder.surface

                    if (slot.cameraOpen && holder.surface.isValid) {
                        slot.helper.addSurface(holder.surface, false)
                    }
                }

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                    // El tamaño del flujo lo administra UVCAndroid.
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    slot.surface?.let {
                        if (it.isValid) {
                            slot.helper.removeSurface(it)
                        }
                    }

                    slot.surface = null
                }
            })

            if (holder.surface?.isValid == true) {
                slot.surface = holder.surface

                if (slot.cameraOpen) {
                    slot.helper.addSurface(holder.surface, false)
                }
            }
        }
    }

    fun release() {
        synchronized(lock) {
            if (released) return
            released = true
            permissionInProgress = false
        }

        slots.forEach { slot ->
            try {
                slot.cameraOpen = false

                slot.surface?.let {
                    if (it.isValid) {
                        slot.helper.removeSurface(it)
                    }
                }

                slot.surface = null
                slot.helper.release()

            } catch (exception: Exception) {
                exception.printStackTrace()
            }
        }
    }
}
