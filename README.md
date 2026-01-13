# 😈 Evil VisionCamera 😈

**A modified, unstable, and dangerous fork of VisionCamera designed to bypass Android Manufacturer Vendor Locking.**

<div>
  <img align="right" width="35%" src="docs/static/img/example.png">
</div>

### Why "Evil"?

Standard Android Camera APIs often hide auxiliary lenses (Ultra-wide, Telephoto, Macro) behind "hidden" IDs (e.g., ID 2, 21, 52, 100) to prevent third-party apps from using them. This library breaks those chains and exposes cameras your manufacturer doesn't want you to see.

### 💀 The "Evil" Mechanism

1.  **Brute-Force Discovery**:
    *   Iterates Camera IDs from `0` to `150`.
    *   Ignores `INFO_SUPPORTED_HARDWARE_LEVEL` safety checks.
    *   Bypasses CameraX for initial discovery to access raw `CameraCharacteristics` via legacy `CameraManager`.
2.  **Evil Session Bypass (The Viewfinder Fix)**:
    *   Standard `CameraX` (Google's library) rejects these hidden IDs during lifecycle binding.
    *   This fork detects `CameraX` rejection and automatically falls back to a raw **Camera2 Evil Session**.
    *   The Evil Session manually pipes the camera feed into the `PreviewView`, giving you a working viewfinder for cameras that "don't exist".

### ⚠️ WARNING: HIGHLY UNSTABLE

**This library is NOT safe for production.**
*   **Crash Risk**: It may crash your app immediately upon launch depending on the hardware.
*   **Limited Features**: "Evil" cameras (the hidden ones) only support **Preview**. Photo/Video capture use cases are currently disabled when an "Evil" bypass is active.
*   **Min SDK**: Requires Android **API 24 (Android 7.0)** or higher.

---

### 📦 Installation

This is a developer-only fork. It is not on npm.

#### 1. Add as a dependency
Install it via file path in your `package.json`:
```json
"dependencies": {
  "react-native-vision-camera": "./path-to-evil-vision-camera/package"
}
```

#### 2. Build for Android
Use the included uprising script to compile the native library (Linux/WSL recommended):
```bash
./evilUprising.sh
```

---

### 🚀 Usage within React Native

Usage is the same as standard `react-native-vision-camera`, but when you look at the `useCameraDevices()` list, you'll see new IDs like `2`, `21`, or `100` labeled as `BACK (EVIL_CAMERA2)`.

```tsx
const devices = useCameraDevices()
const evilCamera = devices.find(d => d.id === '2') // Find the hidden ultra-wide

return (
  <Camera
    style={StyleSheet.absoluteFill}
    device={evilCamera}
    isActive={true}
  />
)
```

### 📱 Expo Support
Compatible with **Custom Development Builds** only.
*   ❌ **Expo Go**: Will crash.
*   ✅ **Dev Client**: Build your own with `npx expo run:android`.

---

*“With great power comes great instability.”*
