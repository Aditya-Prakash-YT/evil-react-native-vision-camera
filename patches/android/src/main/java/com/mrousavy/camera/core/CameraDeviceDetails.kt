package com.mrousavy.camera.core

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import android.util.Range
import android.util.Size
import android.util.SizeF
import androidx.camera.camera2.internal.Camera2CameraInfoImpl
import androidx.camera.core.CameraInfo
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.impl.CameraInfoInternal
import androidx.camera.core.impl.capability.PreviewCapabilitiesImpl
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.video.Quality.ConstantQuality
import androidx.camera.video.Recorder
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.mrousavy.camera.core.extensions.id
import com.mrousavy.camera.core.types.AutoFocusSystem
import com.mrousavy.camera.core.types.DeviceType
import com.mrousavy.camera.core.types.HardwareLevel
import com.mrousavy.camera.core.types.Orientation
import com.mrousavy.camera.core.types.Position
import com.mrousavy.camera.core.types.VideoStabilizationMode
import com.mrousavy.camera.core.utils.CamcorderProfileUtils
import com.mrousavy.camera.react.extensions.toJSValue
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

@SuppressLint("RestrictedApi")
@Suppress("FoldInitializerAndIfToElvis")
class CameraDeviceDetails(
  private val cameraId: String,
  private val characteristics: CameraCharacteristics,
  private val cameraInfo: CameraInfo? = null,
  extensionsManager: ExtensionsManager? = null
) {
  companion object {
    private const val TAG = "CameraDeviceDetails"
  }

  // Generic props available on all implementations
  // Generic props available on all implementations
  private val position = Position.fromLensFacing(cameraInfo?.lensFacing ?: characteristics.get(CameraCharacteristics.LENS_FACING)!!)
  private val name = "$cameraId ($position) ${cameraInfo?.implementationType ?: "EVIL_CAMERA2"}"
  private val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
  private val minZoom = cameraInfo?.zoomState?.value?.minZoomRatio ?: characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MIN_ZOOM_RATIOS)?.get(0) ?: 1f
  private val maxZoom = cameraInfo?.zoomState?.value?.maxZoomRatio ?: characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
  private val minExposure = cameraInfo?.exposureState?.exposureCompensationRange?.lower ?: characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.lower ?: 0
  private val maxExposure = cameraInfo?.exposureState?.exposureCompensationRange?.upper ?: characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.upper ?: 0
  private val supportsFocus = getSupportsFocus()
  private val supportsRawCapture = false
  private val supportsDepthCapture = false
  private val autoFocusSystem = if (supportsFocus) AutoFocusSystem.CONTRAST_DETECTION else AutoFocusSystem.NONE
  private val previewCapabilities = if (cameraInfo != null) PreviewCapabilitiesImpl.from(cameraInfo) else null
  private val videoCapabilities = if (cameraInfo != null) Recorder.getVideoCapabilities(cameraInfo, Recorder.VIDEO_CAPABILITIES_SOURCE_CAMCORDER_PROFILE) else null
  private val supports10BitHdr = getSupports10BitHDR()
  private val sensorRotationDegrees = cameraInfo?.sensorRotationDegrees ?: characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
  private val sensorOrientation = Orientation.fromRotationDegrees(sensorRotationDegrees)

  // CameraX internal props
  private val cameraInfoInternal = cameraInfo as? CameraInfoInternal

  // Camera2 specific props
  private val camera2Details = cameraInfo as? Camera2CameraInfoImpl
  private val physicalDeviceIds = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) characteristics.physicalCameraIds else emptySet()
  private val isMultiCam = physicalDeviceIds.size > 1
  private val cameraHardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
  private val hardwareLevel = HardwareLevel.fromCameraHardwareLevel(
    cameraHardwareLevel ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
  )
  private val minFocusDistance = getMinFocusDistanceCm()
  private val isoRange = getIsoRange()
  private val maxFieldOfView = getMaxFieldOfView()

  // Extensions
  private val supportsHdrExtension = if (extensionsManager != null && cameraInfo != null) extensionsManager.isExtensionAvailable(cameraInfo.cameraSelector, ExtensionMode.HDR) else false
  private val supportsLowLightBoostExtension = if (extensionsManager != null && cameraInfo != null) extensionsManager.isExtensionAvailable(cameraInfo.cameraSelector, ExtensionMode.NIGHT) else false

  fun toMap(): ReadableMap {
    val deviceTypes = getDeviceTypes()
    val formats = getFormats()

    val map = Arguments.createMap()
    map.putString("id", cameraId)
    map.putArray("physicalDevices", deviceTypes.toJSValue())
    map.putString("position", position.unionValue)
    map.putString("name", name)
    map.putBoolean("hasFlash", hasFlash)
    map.putBoolean("hasTorch", hasFlash)
    map.putDouble("minFocusDistance", minFocusDistance)
    map.putBoolean("isMultiCam", isMultiCam)
    map.putBoolean("supportsRawCapture", supportsRawCapture)
    map.putBoolean("supportsLowLightBoost", supportsLowLightBoostExtension)
    map.putBoolean("supportsFocus", supportsFocus)
    map.putDouble("minZoom", minZoom.toDouble())
    map.putDouble("maxZoom", maxZoom.toDouble())
    map.putDouble("neutralZoom", 1.0) // Zoom is always relative to 1.0 on Android
    map.putInt("minExposure", minExposure)
    map.putInt("maxExposure", maxExposure)
    map.putString("hardwareLevel", hardwareLevel.unionValue)
    map.putString("sensorOrientation", sensorOrientation.unionValue)
    map.putArray("formats", formats)
    return map
  }

  /**
   * Get a list of formats (or "possible stream resolution combinations") that this device supports.
   *
   * This filters all resolutions according to the
   * [Camera2 "StreamConfigurationMap" documentation](https://developer.android.com/reference/android/hardware/camera2/params/StreamConfigurationMap)
   */
  private fun getFormats(): ReadableArray {
    val array = Arguments.createArray()

    if (videoCapabilities == null || cameraInfoInternal == null) {
      // Fallback for Evil Cameras (No CameraX capabilities)
      val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return array
      val outputSizes = map.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
      
      outputSizes.forEach { size ->
        val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
        val minFps = fpsRanges.minOfOrNull { it.lower } ?: 0
        val maxFps = fpsRanges.maxOfOrNull { it.upper } ?: 30
        
        val formatMap = Arguments.createMap()
        formatMap.putInt("photoHeight", size.height)
        formatMap.putInt("photoWidth", size.width)
        formatMap.putInt("videoHeight", size.height)
        formatMap.putInt("videoWidth", size.width)
        formatMap.putInt("minFps", minFps)
        formatMap.putInt("maxFps", maxFps)
        formatMap.putInt("minISO", isoRange.lower)
        formatMap.putInt("maxISO", isoRange.upper)
        formatMap.putDouble("fieldOfView", maxFieldOfView)
        formatMap.putBoolean("supportsVideoHdr", false)
        formatMap.putBoolean("supportsPhotoHdr", false)
        formatMap.putBoolean("supportsDepthCapture", false)
        formatMap.putString("autoFocusSystem", autoFocusSystem.unionValue)
        formatMap.putArray("videoStabilizationModes", createStabilizationModes())
        
        array.pushMap(formatMap)
      }
      return array
    }

    val dynamicRangeProfiles = videoCapabilities.supportedDynamicRanges

    dynamicRangeProfiles.forEach { dynamicRange ->
      try {
        val qualities = videoCapabilities.getSupportedQualities(dynamicRange)
        val videoSizes = qualities.map { it as ConstantQuality }.flatMap { it.typicalSizes }
        val photoSizes = (
          cameraInfoInternal.getSupportedHighResolutions(ImageFormat.JPEG) union
            cameraInfoInternal.getSupportedResolutions(ImageFormat.JPEG)
          ).toList()
        val fpsRanges = cameraInfo!!.supportedFrameRateRanges
        val minFps = fpsRanges.minOf { it.lower }
        val maxFps = fpsRanges.maxOf { it.upper }

        videoSizes.forEach { videoSize ->
          try {
            // not every size supports the maximum FPS range
            val maxFpsForSize = CamcorderProfileUtils.getMaximumFps(cameraId, videoSize) ?: maxFps
            // if the FPS range for this size is even smaller than min FPS, we need to clamp that as well.
            val minFpsForSize = min(minFps, maxFpsForSize)
            val fpsRange = Range(minFpsForSize, maxFpsForSize)

            photoSizes.forEach { photoSize ->
              try {
                val map = buildFormatMap(photoSize, videoSize, fpsRange)
                array.pushMap(map)
              } catch (error: Throwable) {
                Log.w(TAG, "Photo size ${photoSize.width}x${photoSize.height} cannot be used as a format!", error)
              }
            }
          } catch (error: Throwable) {
            Log.w(TAG, "Video size ${videoSize.width}x${videoSize.height} cannot be used as a format!", error)
          }
        }
      } catch (error: Throwable) {
        Log.w(TAG, "Dynamic Range Profile $dynamicRange cannot be used as a format!", error)
      }
    }

    return array
  }

  private fun buildFormatMap(photoSize: Size, videoSize: Size, fpsRange: Range<Int>): ReadableMap {
    val map = Arguments.createMap()
    map.putInt("photoHeight", photoSize.height)
    map.putInt("photoWidth", photoSize.width)
    map.putInt("videoHeight", videoSize.height)
    map.putInt("videoWidth", videoSize.width)
    map.putInt("minFps", fpsRange.lower)
    map.putInt("maxFps", fpsRange.upper)
    map.putInt("minISO", isoRange.lower)
    map.putInt("maxISO", isoRange.upper)
    map.putDouble("fieldOfView", maxFieldOfView)
    map.putBoolean("supportsVideoHdr", supports10BitHdr)
    map.putBoolean("supportsPhotoHdr", supportsHdrExtension)
    map.putBoolean("supportsDepthCapture", supportsDepthCapture)
    map.putString("autoFocusSystem", autoFocusSystem.unionValue)
    map.putArray("videoStabilizationModes", createStabilizationModes())
    return map
  }

  private fun getSupports10BitHDR(): Boolean =
    videoCapabilities?.supportedDynamicRanges?.any { range ->
      range.is10BitHdr || range == DynamicRange.HDR_UNSPECIFIED_10_BIT
    } ?: false

  private fun getSupportsFocus(): Boolean {
    // If we have CameraX info, use it
    if (cameraInfo != null) {
      val point = SurfaceOrientedMeteringPointFactory(1.0f, 1.0f).createPoint(0.5f, 0.5f)
      val action = FocusMeteringAction.Builder(point)
      return cameraInfo.isFocusMeteringSupported(action.build())
    }
    // Fallback: check characteristics
    val minFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
    return minFocusDistance != null && minFocusDistance > 0
  }

  private fun getMinFocusDistanceCm(): Double {
    val distance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
    if (distance == null || distance == 0f) return 0.0
    if (distance.isNaN() || distance.isInfinite()) return 0.0
    // distance is in "diopters", meaning 1/meter. Convert to meters, then centi-meters
    return 1.0 / distance * 100.0
  }

  private fun getIsoRange(): Range<Int> {
    val range = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
    return range ?: Range(0, 0)
  }

  private fun createStabilizationModes(): ReadableArray {
    val modes = mutableSetOf(VideoStabilizationMode.OFF)
    if (videoCapabilities != null && videoCapabilities.isStabilizationSupported) {
      modes.add(VideoStabilizationMode.CINEMATIC)
    }
    if (previewCapabilities != null && previewCapabilities.isStabilizationSupported) {
      modes.add(VideoStabilizationMode.CINEMATIC_EXTENDED)
    }

    val array = Arguments.createArray()
    modes.forEach { mode ->
      array.pushString(mode.unionValue)
    }
    return array
  }

  private fun getDeviceTypes(): List<DeviceType> {
    val defaultList = listOf(DeviceType.WIDE_ANGLE)

    // For Evil Camera (native Camera2), we might be able to find physical ids
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P && physicalDeviceIds.isNotEmpty()) {
      // TODO: Can we get characteristics for physical cameras easily here without CameraX?
      // For now, return default if we can't easily map physical IDs to types
      return defaultList
    }

    val inputSensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return defaultList
    val inputFocalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: return defaultList
    val fov = getMaxFieldOfView(inputFocalLengths, inputSensorSize)

    return when {
      fov > 94 -> listOf(DeviceType.ULTRA_WIDE_ANGLE)
      fov in 60f..94f -> listOf(DeviceType.WIDE_ANGLE)
      fov < 60f -> listOf(DeviceType.TELEPHOTO)
      else -> defaultList
    }
  }

  private fun getFieldOfView(focalLength: Float, sensorSize: SizeF): Double {
    if ((sensorSize.width == 0f) || (sensorSize.height == 0f)) {
      return 0.0
    }
    val sensorDiagonal = sqrt((sensorSize.width * sensorSize.width + sensorSize.height * sensorSize.height).toDouble())
    val fovRadians = 2.0 * atan2(sensorDiagonal, (2.0 * focalLength))
    return Math.toDegrees(fovRadians)
  }

  private fun getMaxFieldOfView(focalLengths: FloatArray, sensorSize: SizeF): Double {
    val smallestFocalLength = focalLengths.minOrNull() ?: return 0.0
    return getFieldOfView(smallestFocalLength, sensorSize)
  }

  private fun getMaxFieldOfView(): Double {
    val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return 0.0
    val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: return 0.0
    return getMaxFieldOfView(focalLengths, sensorSize)
  }
}
