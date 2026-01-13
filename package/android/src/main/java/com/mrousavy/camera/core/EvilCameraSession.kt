package com.mrousavy.camera.core

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import com.mrousavy.camera.core.types.RecordVideoOptions
import com.mrousavy.camera.core.types.TakePhotoOptions
import com.mrousavy.camera.core.types.Video
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * A "Evil" Session that bypasses CameraX and uses the Camera2 API directly.
 * This is used for cameras that CameraX refuses to open (hidden/auxIds).
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
  private var previewSurface: Surface? = null
  
  // Photo
  private var imageReader: ImageReader? = null
  
  // Video
  private var mediaRecorder: MediaRecorder? = null
  private var isRecording = false

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
    
    // 1. Photo ImageReader
    val photoSize = android.util.Size(4032, 3024) // Default high res, should be configurable
    imageReader = ImageReader.newInstance(photoSize.width, photoSize.height, ImageFormat.JPEG, 2)
    imageReader?.setOnImageAvailableListener({ reader ->
      val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
      // Handle the image in takePhoto
    }, handler)

    // 2. Request Surface from PreviewView
    val previewSize = android.util.Size(1920, 1080) 
    val request = SurfaceRequest(previewSize, device, false)
    surfaceRequest = request
    
    surfaceProvider.onSurfaceRequested(request)
    
    request.provideSurface(
      { surface, _ -> 
        // Surface ready!
        this.previewSurface = surface
        createCaptureSession(device, surface)
      }, 
      executor
    )
  }

  private fun createCaptureSession(device: CameraDevice, surface: Surface) {
    try {
      Log.i(TAG, "Creating Evil Capture Session...")
      val photoSurface = imageReader?.surface ?: throw Error("ImageReader surface was null!")
      val outputs = mutableListOf(surface, photoSurface)
      
      // We might need to add a Video surface here if we want to support switching to recording without re-configuring
      // but Camera2 often prefers shared surfaces or re-configuration. For now, we'll re-configure for video if needed.
      
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

  suspend fun takePhoto(options: TakePhotoOptions): Photo {
    val session = captureSession ?: throw Error("Capture Session not ready!")
    val device = cameraDevice ?: throw Error("Camera Device not ready!")
    val reader = imageReader ?: throw Error("ImageReader not ready!")
    
    Log.i(TAG, "Taking photo...")
    
    val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
    captureBuilder.addTarget(reader.surface)
    // TODO: Configure flash, focus etc based on options
    
    val photoFile = options.file.file
    
    return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
      reader.setOnImageAvailableListener({ r ->
        val image = r.acquireLatestImage()
        if (image != null) {
          val buffer = image.planes[0].buffer
          val bytes = ByteArray(buffer.remaining())
          buffer.get(bytes)
          FileOutputStream(photoFile).use { it.write(bytes) }
          image.close()
          
          Log.i(TAG, "Photo saved to ${photoFile.absolutePath}")
          continuation.resume(Photo(photoFile.absolutePath, image.width, image.height, com.mrousavy.camera.core.types.Orientation.PORTRAIT, false)) {
              // On cancel
          }
        }
      }, handler)
      
      session.capture(captureBuilder.build(), null, handler)
    }
  }

  fun startRecording(options: RecordVideoOptions, onRecordCallback: (video: Video) -> Unit, onError: (error: Throwable) -> Unit) {
    Log.i(TAG, "Starting video recording...")
    val device = cameraDevice ?: return
    val previewSurface = previewSurface ?: return

    try {
      isRecording = true
      
      val videoFile = options.file.file
      
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          mediaRecorder = MediaRecorder(cameraManager.context)
      } else {
          @Suppress("DEPRECATION")
          mediaRecorder = MediaRecorder()
      }
      
      val recorder = mediaRecorder!!
      recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
      recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
      recorder.setOutputFile(videoFile.absolutePath)
      recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
      recorder.setVideoSize(1920, 1080)
      recorder.setVideoFrameRate(30)
      recorder.setVideoEncodingBitRate(10_000_000)
      recorder.prepare()
      
      val recordingSurface = recorder.surface

      // Re-configure session with recording surface
      val photoSurface = imageReader?.surface ?: throw Error("ImageReader surface was null!")
      val outputs = listOf(previewSurface, photoSurface, recordingSurface)
      
      device.createCaptureSession(outputs, object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
          captureSession = session
          val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
          builder.addTarget(previewSurface)
          builder.addTarget(recordingSurface)
          session.setRepeatingRequest(builder.build(), null, handler)
          
          recorder.start()
          Log.i(TAG, "Recording started!")
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
          Log.e(TAG, "Failed to re-configure session for recording!")
          isRecording = false
          onError(Error("Failed to configure recording session"))
        }
      }, handler)

    } catch (e: Throwable) {
      Log.e(TAG, "Failed to start recording!", e)
      isRecording = false
      onError(e)
    }
  }

  fun stopRecording() {
    Log.i(TAG, "Stopping video recording...")
    if (!isRecording) return
    
    try {
      mediaRecorder?.stop()
      mediaRecorder?.release()
      mediaRecorder = null
      isRecording = false
      
      // Re-re-configure session back to preview/photo only? 
      // Or just leave it as is. Re-configuring is safer to stop the recording surface.
      val device = cameraDevice ?: return
      val previewSurface = previewSurface ?: return
      createCaptureSession(device, previewSurface)
      
    } catch (e: Throwable) {
      Log.e(TAG, "Failed to stop recording!", e)
    }
  }

  override fun close() {
    Log.i(TAG, "Closing Evil Session...")
    try {
      if (isRecording) stopRecording()
      captureSession?.close()
      captureSession = null
      cameraDevice?.close()
      cameraDevice = null
      surfaceRequest?.willNotProvideSurface()
      surfaceRequest = null
      imageReader?.close()
      imageReader = null
    } catch (e: Throwable) {
      Log.e(TAG, "Error closing session", e)
    }
  }
}
