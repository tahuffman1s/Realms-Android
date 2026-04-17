#!/usr/bin/env bash
# Realms of Fate — development environment setup for Arch Linux
# Run once on a fresh machine: ./setup-env.sh
#
# All components are installed via pacman/AUR.
set -euo pipefail

echo "=== Realms of Fate — Environment Setup ==="

# ---- Prerequisites ----
echo "[1/8] Installing prerequisites..."
sudo pacman -S --needed --noconfirm \
    base-devel \
    git \
    unzip

# ---- JDK 17 + 21 ----
echo "[2/8] Installing JDK 17 and 21..."
sudo pacman -S --needed --noconfirm jdk17-openjdk jdk21-openjdk
sudo archlinux-java set java-21-openjdk
java -version

# ---- Node.js (for context7 MCP plugin) ----
echo "[3/8] Installing Node.js..."
sudo pacman -S --needed --noconfirm nodejs npm

# ---- Gradle + Android SDK ----
echo "[4/8] Installing Gradle and Android SDK..."
sudo pacman -S --needed --noconfirm \
    gradle \
    android-sdk \
    android-sdk-platform-tools \
    android-sdk-build-tools \
    android-sdk-cmdline-tools-latest

# Platform 34 is in the AUR (not in the main repos).
echo "[5/8] Installing Android platform 34 (AUR)..."
if command -v paru &>/dev/null; then
    paru -S --needed --noconfirm android-platform-34
elif command -v yay &>/dev/null; then
    yay -S --needed --noconfirm android-platform-34
else
    echo "ERROR: No AUR helper found. Install one first, then run:"
    echo "  paru -S android-platform-34"
    exit 1
fi

# Accept SDK licenses so Gradle can build.
SDKMANAGER=/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager
yes | "$SDKMANAGER" --sdk_root=/opt/android-sdk --licenses >/dev/null 2>&1 || true

# ---- Emulator ----
echo "[6/8] Installing Android Emulator..."
EMU_DIR=/opt/android-sdk/emulator
IMG_DIR="/opt/android-sdk/system-images/android-36.1/google_apis/x86_64"

if [ -d "$EMU_DIR" ] && [ -d "$IMG_DIR" ]; then
    echo "  Emulator and system image already installed, skipping."
else
    sudo "$SDKMANAGER" --sdk_root=/opt/android-sdk --install "emulator" "system-images;android-36.1;google_apis;x86_64"
fi

# Create a default AVD if one doesn't exist
if avdmanager list avd 2>/dev/null | grep -q "Name: Pixel7"; then
    echo "  AVD Pixel7 already exists, skipping."
else
    echo "no" | avdmanager create avd -n "Pixel7" -k "system-images;android-36.1;google_apis;x86_64" -d "pixel_7" >/dev/null 2>&1
    echo "  Created AVD: Pixel7 (Pixel 7, Android 16 / API 36.1, x86_64)"
fi

# Add SDK tools to fish PATH (idempotent)
if command -v fish &>/dev/null; then
    fish -c 'fish_add_path /opt/android-sdk/cmdline-tools/latest/bin /opt/android-sdk/platform-tools /opt/android-sdk/emulator'
fi

# Write local.properties so Gradle finds the SDK
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "sdk.dir=/opt/android-sdk" > "$SCRIPT_DIR/local.properties"

# ---- KVM check ----
echo "[7/8] Checking KVM for emulator acceleration..."
if [ -r /dev/kvm ]; then
    echo "  KVM is available and accessible."
elif [ -e /dev/kvm ]; then
    echo "  KVM exists but is not readable by your user. Fix with:"
    echo "    sudo usermod -aG kvm $(whoami) && newgrp kvm"
else
    echo "  WARNING: /dev/kvm not found. The emulator will be very slow without KVM."
    echo "  Ensure virtualization (AMD-V / VT-x) is enabled in BIOS."
fi

# ---- Verify ----
echo "[8/8] Verifying..."
echo "Java:         $(java -version 2>&1 | head -1)"
echo "Gradle:       $(gradle --version 2>/dev/null | grep '^Gradle' || echo 'not found')"
echo "Platforms:    $(ls /opt/android-sdk/platforms/ 2>/dev/null | tr '\n' ' ')"
echo "Build tools:  $(ls /opt/android-sdk/build-tools/ 2>/dev/null | tr '\n' ' ')"
echo "Emulator:     $(emulator -version 2>/dev/null | head -1 || echo 'not found')"
echo "AVDs:         $(avdmanager list avd 2>/dev/null | grep 'Name:' | sed 's/.*Name: //' | tr '\n' ' ' || echo 'none')"
echo "Node:         $(node --version 2>/dev/null || echo 'not found')"
echo ""
echo "=== Setup complete. ==="
echo "  Build:     gradle assembleDebug"
echo "  Emulator:  emulator -avd Pixel7"
echo "  Deploy:    gradle installDebug"
