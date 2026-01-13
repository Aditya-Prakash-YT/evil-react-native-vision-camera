package com.mrousavy.camera.react

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import android.hardware.camera2.CameraCharacteristics
import com.mrousavy.camera.core.CameraDeviceDetails
import com.mrousavy.camera.core.CameraQueues
import com.mrousavy.camera.core.extensions.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch

class CameraDevicesManager(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
  companion object {
    private const val TAG = "CameraDevices"
  }
  private val executor = CameraQueues.cameraExecutor
  private val coroutineScope = CoroutineScope(executor.asCoroutineDispatcher())
  private val cameraManager = reactContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
  private var cameraProvider: ProcessCameraProvider? = null
  private var extensionsManager: ExtensionsManager? = null

  private val callback = object : CameraManager.AvailabilityCallback() {
    private var deviceIds = cameraManager.cameraIdList.toMutableList()

    // Check if device is still physically connected (even if onCameraUnavailable() is called)
    private fun isDeviceConnected(cameraId: String): Boolean =
      try {
        cameraManager.getCameraCharacteristics(cameraId)
        true
      } catch (_: Throwable) {
        false
      }

    override fun onCameraAvailable(cameraId: String) {
      Log.i(TAG, "Camera #$cameraId is now available.")
      if (!deviceIds.contains(cameraId)) {
        deviceIds.add(cameraId)
        sendAvailableDevicesChangedEvent()
      }
    }

    override fun onCameraUnavailable(cameraId: String) {
      Log.i(TAG, "Camera #$cameraId is now unavailable.")
      if (deviceIds.contains(cameraId) && !isDeviceConnected(cameraId)) {
        deviceIds.remove(cameraId)
        sendAvailableDevicesChangedEvent()
      }
    }
  }

  override fun getName(): String = TAG

  // Init cameraProvider + manager as early as possible
  init {
    coroutineScope.launch {
      try {
        Log.i(TAG, "Initializing ProcessCameraProvider...")
        cameraProvider = ProcessCameraProvider.getInstance(reactContext).await(executor)
        Log.i(TAG, "Initializing ExtensionsManager...")
        extensionsManager = ExtensionsManager.getInstanceAsync(reactContext, cameraProvider!!).await(executor)
        Log.i(TAG, "Successfully initialized!")
      } catch (error: Throwable) {
        Log.e(TAG, "Failed to initialize ProcessCameraProvider/ExtensionsManager! Error: ${error.message}", error)
      }
    }
  }

  // Note: initialize() will be called after getConstants on new arch!
  override fun initialize() {
    super.initialize()
    cameraManager.registerAvailabilityCallback(callback, null)
    sendAvailableDevicesChangedEvent()
  }

  override fun invalidate() {
    cameraManager.unregisterAvailabilityCallback(callback)
    super.invalidate()
  }

  private fun getEvilCameraIds(): List<Pair<String, CameraCharacteristics>> {
    val evilCameras = mutableListOf<Pair<String, CameraCharacteristics>>()
    for (i in 0..150) {
      val id = i.toString()
      try {
        val characteristics = cameraManager.getCameraCharacteristics(id)
        evilCameras.add(id to characteristics)
      } catch (e: Throwable) {
        // Camera ID doesn't exist or is restricted.
        // Some devices might throw IllegalArgumentException, others might throw SecurityException.
        // We just ignore it and continue.
      }
    }
    return evilCameras
  }

  private fun getDevicesJson(): ReadableArray {
    val devices = Arguments.createArray()
    val cameraProvider = cameraProvider ?: return devices
    val extensionsManager = extensionsManager ?: return devices

    val addedIds = mutableSetOf<String>()

    // 1. Add "Safe" (CameraX) Devices
    cameraProvider.availableCameraInfos.forEach { cameraInfo ->
        try {
            val id = cameraInfo.id
            if (id != null) {
                // We need to fetch characteristics manually because CameraInfo doesn't expose them reliably for our new constructor
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val device = CameraDeviceDetails(id, characteristics, cameraInfo, extensionsManager)
                devices.pushMap(device.toMap())
                addedIds.add(id)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to add CameraX device: ${cameraInfo.id}", e)
        }
    }

    // 2. Add "Evil" (Hidden/Native) Devices
    val evilCameras = getEvilCameraIds()
    evilCameras.forEach { (id, characteristics) ->
        if (!addedIds.contains(id)) {
            Log.i(TAG, "Found hidden/evil camera: $id")
            try {
                // These devices have no CameraX CameraInfo/Extensions support
                val device = CameraDeviceDetails(id, characteristics, null, null)
                devices.pushMap(device.toMap())
                addedIds.add(id)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to add evil camera: $id", e)
            }
        }
    }

    return devices
  }

  fun sendAvailableDevicesChangedEvent() {
    val eventEmitter = reactContext.getJSModule(RCTDeviceEventEmitter::class.java)
    val devices = getDevicesJson()
    eventEmitter.emit("CameraDevicesChanged", devices)
  }

  override fun getConstants(): MutableMap<String, Any?> {
    val devices = getDevicesJson()
    val preferredDevice = if (devices.size() > 0) devices.getMap(0) else null

    return mutableMapOf(
      "availableCameraDevices" to devices,
      "userPreferredCameraDevice" to preferredDevice?.toHashMap()
    )
  }

  // Required for NativeEventEmitter, this is just a dummy implementation:
  @Suppress("unused", "UNUSED_PARAMETER")
  @ReactMethod
  fun addListener(eventName: String) {}

  @Suppress("unused", "UNUSED_PARAMETER")
  @ReactMethod
  fun removeListeners(count: Int) {}
}
