# 😈 Evil Features Usage Guide

Welcome to the "Evil" side of `react-native-vision-camera`. This fork allows you to bypass the limitations of CameraX and access **hidden**, **auxiliary**, or **native** cameras that are usually restricted on certain Android devices (e.g., infrared lenses, macro lenses, or logic-locked sub-cameras).

---

## 🚀 1. Setup & Patching

To enable "Evil Mode", you must first apply the patch to your `node_modules`.

1. Ensure you have `react-native-vision-camera` installed in your project.
2. Run the patch script from the root of this repository:
   ```bash
   ./patch/apply-evil.patch.sh
   ```
3. **CRITICAL**: Rebuild your Android app to include the modified Kotlin files:
   ```bash
   npx expo run:android  # or npx react-native run-android
   ```

---

## 🔍 2. Discovering "Evil" Devices

Standard `react-native-vision-camera` only shows devices supported by CameraX. Our patched version exposes **all** physical cameras detected by the Android `CameraManager` (IDs 0-150).

### Using `useCameraDevices()`
You can use the standard hook. Evil cameras will have a special `name` suffix: `(EVIL_CAMERA2)`.

```typescript
import { useCameraDevices } from 'react-native-vision-camera';

const devices = useCameraDevices();

// Filter for evil cameras only
const evilCameras = devices.filter(d => d.name.includes('EVIL_CAMERA2'));

console.log('Found evil cameras:', evilCameras.map(d => d.id));
```

### Identification in JS
Evil cameras are identified by their `id` (e.g., `"2"`, `"3"`, `"100"`). They often don't have full CameraX metadata, so they might fall back to basic format reporting.

---

## 📸 3. Using Evil Mode

When you pass an `id` to the `<Camera>` component that is **not** recognized by CameraX, the library automatically switches to the `EvilCameraSession`.

### Basic Implementation
```tsx
import { Camera, useCameraDevice } from 'react-native-vision-camera';

function MyEvilCamera() {
  // Directly use a hidden ID if you know it, or find it via useCameraDevices()
  const device = useCameraDevice('3'); // Example hidden ID

  if (device == null) return <NoCameraDeviceError />;
  
  return (
    <Camera
      style={StyleSheet.absoluteFill}
      device={device}
      isActive={true}
      photo={true}
      video={true}
    />
  );
}
```

---

## ✅ 4. Supported Features in Evil Mode

Evil Mode works by using the **Camera2 API** directly, bypassing the high-level CameraX abstraction.

| Feature | Supported | Notes |
| :--- | :---: | :--- |
| **Preview** | ✅ | Direct Surface mapping from Camera2. |
| **Photo** | ✅ | Captured via `ImageReader` at max resolution. |
| **Video** | ✅ | Recorded via `MediaRecorder` at 1080p/30fps. |
| **Flash** | ❌ | Currently manual/fixed (WIP). |
| **Focus** | ❌ | Auto-focus enabled by default if hardware supports it. |
| **Zoom** | ❌ | Coming soon. |

> [!IMPORTANT]
> Video recording in Evil Mode requires a re-configuration of the camera session. You might see a slight flicker when calling `startRecording()`.

---

## 🛠 5. Troubleshooting

### "Camera not found"
Ensure you rebuilt the app after applying the patch. The JavaScript code needs the updated `CameraDevicesManager.kt` to see the hidden IDs.

### "Black Screen"
Some hidden cameras are hardware-locked or require specific permissions. If an ID is found but shows a black screen, it might be a proprietary sensor that doesn't output standard YUV/JPEG frames.

### "App Crashes on Start"
Check your `adb logcat`. Look for `EvilCameraSession` tags. This usually happens if the device is already in use or if the requested resolution is not supported by that specific auxiliary lens.
