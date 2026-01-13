#!/bin/bash

# 😈 Evil Patcher Script
# This script replaces files in node_modules/react-native-vision-camera
# with the Evil Camera modifications.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TARGET_DIR="$PROJECT_ROOT/node_modules/react-native-vision-camera"

echo "😈 Evil Patcher Starting..."
echo "Source: $SCRIPT_DIR/android"
echo "Target: $TARGET_DIR"

# Check if target exists
if [ ! -d "$TARGET_DIR" ]; then
    echo "❌ Error: node_modules/react-native-vision-camera not found!"
    echo "   Make sure you run this from your project's patch folder"
    echo "   or that react-native-vision-camera is installed."
    exit 1
fi

# Copy all patched Kotlin files
echo "📂 Copying patched files..."

# Core module files
cp -v "$SCRIPT_DIR/android/src/main/java/com/mrousavy/camera/core/CameraDeviceDetails.kt" "$TARGET_DIR/android/src/main/java/com/mrousavy/camera/core/CameraDeviceDetails.kt"
cp -v "$SCRIPT_DIR/android/src/main/java/com/mrousavy/camera/core/CameraSession.kt" "$TARGET_DIR/android/src/main/java/com/mrousavy/camera/core/CameraSession.kt"
cp -v "$SCRIPT_DIR/android/src/main/java/com/mrousavy/camera/core/EvilCameraSession.kt" "$TARGET_DIR/android/src/main/java/com/mrousavy/camera/core/EvilCameraSession.kt"
cp -v "$SCRIPT_DIR/android/src/main/java/com/mrousavy/camera/core/CameraSession+Photo.kt" "$TARGET_DIR/android/src/main/java/com/mrousavy/camera/core/CameraSession+Photo.kt"
cp -v "$SCRIPT_DIR/android/src/main/java/com/mrousavy/camera/core/CameraSession+Video.kt" "$TARGET_DIR/android/src/main/java/com/mrousavy/camera/core/CameraSession+Video.kt"
cp -v "$SCRIPT_DIR/android/src/main/java/com/mrousavy/camera/core/CameraSession+Configuration.kt" "$TARGET_DIR/android/src/main/java/com/mrousavy/camera/core/CameraSession+Configuration.kt"

# React module files
cp -v "$SCRIPT_DIR/android/src/main/java/com/mrousavy/camera/react/CameraDevicesManager.kt" "$TARGET_DIR/android/src/main/java/com/mrousavy/camera/react/CameraDevicesManager.kt"

echo ""
echo "✅ Evil Patch Applied Successfully!"
echo "😈 Now rebuild your app with: npx expo run:android"
