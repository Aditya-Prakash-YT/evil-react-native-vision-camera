package com.mrousavy.camera.core

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import com.mrousavy.camera.core.CameraSession
import java.io.Closeable
import java.util.concurrent.Executors

/**
 * A "Evil" Session that bypasses CameraX and uses the Camera2 API directly.
 * This is used for cameras that CameraX refuses to open (hidden/auxIds).
 *
 * ⚠️ ONLY SUPPORTS PREVIEW.
 */
class EvilCameraSession(
  private val cameraManager: CameraManager,
  private val cameraId: String,
  private val surfaceProvider: Preview.SurfaceProvider,
  private val callback: CameraSession.Callback
) : Closeable {
  companion object {
    private const val TAG = "EvilCameraSession"
  }

  private val executor = Executors.newSingleThreadExecutor()
  private val handler = Handler(Looper.getMainLooper())
  
  private var cameraDevice: CameraDevice? = null
  private var captureSession: CameraCaptureSession? = null
  private var surfaceRequest: SurfaceRequest? = null
  private var surface: Surface? = null

  init {
    openCamera()
  }

  @SuppressLint("MissingPermission")
  private fun openCamera() {
    try {
      Log.i(TAG, "Opening Evil Camera #$cameraId...")
      cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
          Log.i(TAG, "Evil Camera #$cameraId opened!")
          cameraDevice = camera
          initializeSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
          Log.i(TAG, "Evil Camera #$cameraId disconnected!")
          close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
          Log.e(TAG, "Evil Camera #$cameraId error: $error")
          // TODO: Map error code to CameraError
          close()
        }
      }, handler)
    } catch (e: Throwable) {
      Log.e(TAG, "Failed to open evil camera!", e)
      callback.onError(e)
    }
  }

  private fun initializeSession() {
    val device = cameraDevice ?: return
    
    // 1. Request Surface from PreviewView
    // We pretend to be CameraX requesting a 1080p surface (or whatever default)
    // In a real impl we'd check configuration.format
    val size = android.util.Size(1920, 1080) 
    val request = SurfaceRequest(size, device, false)
    surfaceRequest = request
    
    surfaceProvider.onSurfaceRequested(request)
    
    request.provideSurface(
      { surface, _ -> 
        // Surface ready!
        this.surface = surface
        createCaptureSession(device, surface)
      }, 
      executor
    )
  }

  private fun createCaptureSession(device: CameraDevice, surface: Surface) {
    try {
      Log.i(TAG, "Creating Evil Capture Session...")
      val outputs = listOf(surface)
      device.createCaptureSession(outputs, object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
          Log.i(TAG, "Evil Capture Session configured!")
          captureSession = session
          startPreview(session, surface)
          callback.onInitialized()
          callback.onStarted()
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
          Log.e(TAG, "Evil Capture Session configuration failed!")
        }
      }, handler)
    } catch (e: Throwable) {
      Log.e(TAG, "Failed to create capture session!", e)
    }
  }

  private fun startPreview(session: CameraCaptureSession, surface: Surface) {
    try {
      val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW) ?: return
      builder.addTarget(surface)
      session.setRepeatingRequest(builder.build(), null, handler)
    } catch (e: Throwable) {
      Log.e(TAG, "Failed to start preview!", e)
    }
  }

  override fun close() {
    Log.i(TAG, "Closing Evil Session...")
    try {
      captureSession?.close()
      captureSession = null
      cameraDevice?.close()
      cameraDevice = null
      surfaceRequest?.willNotProvideSurface()
      surfaceRequest = null
    } catch (e: Throwable) {
      Log.e(TAG, "Error closing session", e)
    }
  }
}
