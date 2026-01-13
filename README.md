<a href="https://margelo.com">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="./docs/static/img/banner-dark.png" />
    <source media="(prefers-color-scheme: light)" srcset="./docs/static/img/banner-light.png" />
    <img alt="VisionCamera" src="./docs/static/img/banner-light.png" />
  </picture>
</a>

<br />

# 😈 Evil VisionCamera 😈

**A modified, unstable, and dangerous fork of VisionCamera designed to bypass Android Manufacturer Vendor Locking.**

<div>
  <img align="right" width="35%" src="docs/static/img/example.png">
</div>

### Why "Evil"?

Standard Android Camera APIs often hide auxiliary lenses (Ultra-wide, Telephoto, Macro) behind "hidden" IDs (e.g., ID 2, 21, 52, 100) to prevent third-party apps from using them. This library breaks those chains.

**This fork implements Brute-Force Discovery:**
*   Iterates Camera IDs from `0` to `150`.
*   Ignores `INFO_SUPPORTED_HARDWARE_LEVEL` safety checks.
*   Bypasses CameraX for initial discovery to access raw `CameraCharacteristics`.

### ⚠️ WARNING: HIGHLY UNSTABLE

**This library is NOT safe for production.**
*   It may crash your app immediately upon launch.
*   It utilizes undocumented camera IDs that may not return valid stream configurations.
*   Using "Evil" cameras disables standard CameraX features (Extensions, High-Speed Video).

---

### Features

VisionCamera is a powerful, high-performance Camera library for React Native. It features:

* 📸 Photo and Video capture
* 👁️ QR/Barcode scanner
* 📱 **Unrestricted Device Access** (Access hidden Samsung/Xiaomi lenses)
* 🎞️ Customizable resolutions and aspect-ratios (4k/8k images)
* ⏱️ Customizable FPS (30..240 FPS)
* 🧩 [Frame Processors](https://react-native-vision-camera.com/docs/guides/frame-processors) (JS worklets to run facial recognition, AI object detection, realtime video chats, ...)
* 🎨 Drawing shapes, text, filters or shaders onto the Camera
* 🔍 Smooth zooming (Reanimated)
* ⏯️ Fast pause and resume
* 🌓 HDR & Night modes
* ⚡ Custom C++/GPU accelerated video pipeline (OpenGL)

### Installation

```sh
npm i react-native-vision-camera
cd ios && pod install
```

### Usage

The "Evil" cameras will appear in the device list just like normal cameras. You can filter them by ID or physical device type.

```tsx
function App() {
  // Get ALL devices, including hidden ones
  const devices = useCameraDevices()
  const device = devices.find((d) => d.id === "2") // Likely a telephoto lens on Samsung

  if (device == null) return <NoCameraErrorView />
  return (
    <Camera
      style={StyleSheet.absoluteFill}
      device={device}
      isActive={true}
    />
  )
}
```

### Documentation

* [Guides](https://react-native-vision-camera.com/docs/guides)
* [API](https://react-native-vision-camera.com/docs/api)
* [Example](./example/)

### Socials

* 🐦 [**Follow me on Twitter**](https://twitter.com/mrousavy)
* 💬 [**Join the Margelo Community Discord**](https://margelo.com/discord)
