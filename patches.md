# 🩹 Evil Patch List

Copy these files to your `node_modules/react-native-vision-camera` to manually patch the module.

---

## 📂 Files to Copy

| File | Path (Relative to `package/`) |
|---|---|
| `CameraDevicesManager.kt` | `android/src/main/java/com/mrousavy/camera/react/CameraDevicesManager.kt` |
| `CameraDeviceDetails.kt` | `android/src/main/java/com/mrousavy/camera/core/CameraDeviceDetails.kt` |
| `CameraSession.kt` | `android/src/main/java/com/mrousavy/camera/core/CameraSession.kt` |
| `EvilCameraSession.kt` **(NEW)** | `android/src/main/java/com/mrousavy/camera/core/EvilCameraSession.kt` |
| `build.gradle` | `android/build.gradle` |
| `gradle-wrapper.properties` | `android/gradle/wrapper/gradle-wrapper.properties` |

---

## 👩‍💻 Patching Instructions

1.  Create a `patch/` folder wherever you like.
2.  Copy the 6 files listed above from this repo's `package/` folder into your `patch/` folder (preserving the directory structure).
3.  Go to your app's `node_modules/react-native-vision-camera/`.
4.  Overwrite the corresponding files with the ones from your `patch/` folder.
5.  Rebuild with `npx expo run:android` or `npx react-native run-android`.
