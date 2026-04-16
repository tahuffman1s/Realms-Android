#!/usr/bin/env bash
# Realms of Fate — development environment setup for Arch Linux
# Run once on a fresh machine: ./setup-env.sh
set -euo pipefail

echo "=== Realms of Fate — Environment Setup ==="

# ---- JDK 17 + 21 ----
echo "[1/6] Installing JDK 17 and 21..."
sudo pacman -S --needed --noconfirm jdk17-openjdk jdk21-openjdk

echo "[2/6] Setting JDK 21 as default..."
sudo archlinux-java set java-21-openjdk
java -version

# ---- Node.js (for context7 MCP plugin) ----
echo "[3/6] Installing Node.js..."
sudo pacman -S --needed --noconfirm nodejs npm

# ---- Android SDK ----
echo "[4/6] Installing Android SDK..."
sudo pacman -S --needed --noconfirm android-sdk android-sdk-platform-tools android-sdk-build-tools android-sdk-cmdline-tools-latest

# Fix ownership so sdkmanager works without sudo
sudo chown -R "$USER" /opt/android-sdk

# Install required platform and build tools
echo "[5/6] Installing Android SDK platform 34..."
sdkmanager --sdk_root=/opt/android-sdk "platforms;android-34" "build-tools;34.0.0" "platform-tools"

# Add SDK tools to fish PATH (idempotent)
if command -v fish &>/dev/null; then
    fish -c 'fish_add_path /opt/android-sdk/cmdline-tools/latest/bin /opt/android-sdk/platform-tools'
    echo "Added Android SDK tools to fish PATH."
fi

# Write local.properties
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "sdk.dir=/opt/android-sdk" > "$SCRIPT_DIR/local.properties"

echo "[6/6] Verifying..."
echo "Java:    $(java -version 2>&1 | head -1)"
echo "SDK:     $(ls /opt/android-sdk/platforms/ 2>/dev/null | tr '\n' ' ')"
echo "Build:   $(ls /opt/android-sdk/build-tools/ 2>/dev/null | tr '\n' ' ')"
echo ""
echo "=== Setup complete. Run ./gradlew assembleDebug to build. ==="
