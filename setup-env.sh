#!/usr/bin/env bash
# Realms of Fate — development environment setup for Arch Linux
# Run once on a fresh machine: ./setup-env.sh
#
# All components are installed via pacman/AUR.
set -euo pipefail

echo "=== Realms of Fate — Environment Setup ==="

# ---- Prerequisites ----
echo "[1/6] Installing prerequisites..."
sudo pacman -S --needed --noconfirm \
    base-devel \
    git \
    unzip

# ---- JDK 17 + 21 ----
echo "[2/6] Installing JDK 17 and 21..."
sudo pacman -S --needed --noconfirm jdk17-openjdk jdk21-openjdk
sudo archlinux-java set java-21-openjdk
java -version

# ---- Node.js (for context7 MCP plugin) ----
echo "[3/6] Installing Node.js..."
sudo pacman -S --needed --noconfirm nodejs npm

# ---- Gradle + Android SDK ----
echo "[4/6] Installing Gradle and Android SDK..."
sudo pacman -S --needed --noconfirm \
    gradle \
    android-sdk \
    android-sdk-platform-tools \
    android-sdk-build-tools \
    android-sdk-cmdline-tools-latest

# Platform 34 is in the AUR (not in the main repos).
echo "[5/6] Installing Android platform 34 (AUR)..."
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

# Add SDK tools to fish PATH (idempotent)
if command -v fish &>/dev/null; then
    fish -c 'fish_add_path /opt/android-sdk/cmdline-tools/latest/bin /opt/android-sdk/platform-tools'
fi

# Write local.properties so Gradle finds the SDK
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "sdk.dir=/opt/android-sdk" > "$SCRIPT_DIR/local.properties"

# ---- Verify ----
echo "[6/6] Verifying..."
echo "Java:         $(java -version 2>&1 | head -1)"
echo "Gradle:       $(gradle --version 2>/dev/null | grep '^Gradle' || echo 'not found')"
echo "Platforms:    $(ls /opt/android-sdk/platforms/ 2>/dev/null | tr '\n' ' ')"
echo "Build tools:  $(ls /opt/android-sdk/build-tools/ 2>/dev/null | tr '\n' ' ')"
echo "Node:         $(node --version 2>/dev/null || echo 'not found')"
echo ""
echo "=== Setup complete. Run 'gradle assembleDebug' to build. ==="
