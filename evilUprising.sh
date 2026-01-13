#!/bin/bash

# 1. Go into the package directory
cd package

# 2. Install JS dependencies (ignoring peer dependency conflicts for docs)
# echo "Installing dependencies..."
# npm install --legacy-peer-deps

# 3. Go to the Android folder
cd android

# 4. Fix gradlew execution (permissions + line endings)
echo "Fixing gradlew permissions..."
chmod +x gradlew
# Attempt to fix Windows CRLF line endings if present (requires sed)
sed -i 's/\r$//' gradlew || true

# 5. Run the Gradle build
echo "Building Android library..."
./gradlew build