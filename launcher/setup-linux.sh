#!/usr/bin/env bash
# ==============================================================================
# Automated System-Wide Installation Script
# Components: OpenJDK 17 & Android SDK (platform-34, build-tools 34.0.0)
# Target Environment: Ubuntu / Debian (WSL2 / Linux)
# ==============================================================================

set -euo pipefail

# Ensure script is executed as root
if [ "$(id -u)" -ne 0 ]; then
    echo "[-] Error: This installation script must be run as root (e.g. using sudo)." >&2
    exit 1
fi

echo "[+] Starting system-wide installation of OpenJDK 17 and Android SDK..."

# ------------------------------------------------------------------------------
# 1. Install OpenJDK 17 and prerequisite utilities
# ------------------------------------------------------------------------------
echo "[+] Step 1: Installing OpenJDK 17, unzip, curl, and wget via apt..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y --no-install-recommends \
    openjdk-17-jdk \
    unzip \
    curl \
    wget \
    ca-certificates

# Verify Java 17 path
JAVA_17_PATH="/usr/lib/jvm/java-17-openjdk-amd64"
if [ ! -d "$JAVA_17_PATH" ]; then
    # Fallback search if path differs slightly
    DETECTED_JAVA=$(readlink -f /usr/bin/java 2>/dev/null | sed 's:/bin/java::' || true)
    if [ -n "$DETECTED_JAVA" ] && [ -d "$DETECTED_JAVA" ]; then
        JAVA_17_PATH="$DETECTED_JAVA"
    fi
fi

echo "[+] Using JAVA_HOME: $JAVA_17_PATH"
export JAVA_HOME="$JAVA_17_PATH"
export PATH="$JAVA_HOME/bin:$PATH"

# Set as default java alternative if multiple exist
if command -v update-java-alternatives >/dev/null 2>&1; then
    update-java-alternatives -s java-1.17.0-openjdk-amd64 || true
fi

echo "[+] Java version:"
java -version

# ------------------------------------------------------------------------------
# 2. Setup Android SDK Directory Structure
# ------------------------------------------------------------------------------
ANDROID_SDK_ROOT="/opt/android-sdk"
CMDLINE_TOOLS_DIR="$ANDROID_SDK_ROOT/cmdline-tools/latest"
ZIP_FILE="/tmp/cmdline-tools-15859902.zip"
DOWNLOAD_URL="https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip"

echo "[+] Step 2: Setting up Android SDK root at $ANDROID_SDK_ROOT..."
mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"

# Download cmdline-tools if not already cached in /tmp
if [ ! -f "$ZIP_FILE" ]; then
    echo "[+] Downloading Android Command Line Tools..."
    curl -fLo "$ZIP_FILE" "$DOWNLOAD_URL"
else
    echo "[+] Found existing archive at $ZIP_FILE."
fi

# Extract into cmdline-tools/latest
# The archive contains a top-level directory named 'cmdline-tools'
# Android SDK requires the tools to live in $ANDROID_HOME/cmdline-tools/latest
echo "[+] Extracting Command Line Tools to $CMDLINE_TOOLS_DIR..."
rm -rf "$CMDLINE_TOOLS_DIR"
TMP_EXTRACT="/tmp/cmdline-tools-extracted"
rm -rf "$TMP_EXTRACT"
mkdir -p "$TMP_EXTRACT"
unzip -q -o "$ZIP_FILE" -d "$TMP_EXTRACT"
mv "$TMP_EXTRACT/cmdline-tools" "$CMDLINE_TOOLS_DIR"
rm -rf "$TMP_EXTRACT"

# Grant group permissions (users / sudo group)
chown -R root:users "$ANDROID_SDK_ROOT" 2>/dev/null || chown -R root:sudo "$ANDROID_SDK_ROOT" || true
chmod -R 775 "$ANDROID_SDK_ROOT"

# Set environment for current script execution
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
export PATH="$CMDLINE_TOOLS_DIR/bin:$ANDROID_HOME/platform-tools:$PATH"

# ------------------------------------------------------------------------------
# 3. Accept Licenses & Install SDK Packages
# ------------------------------------------------------------------------------
echo "[+] Step 3: Accepting Android SDK licenses..."
yes | "$CMDLINE_TOOLS_DIR/bin/sdkmanager" --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null 2>&1 || true

echo "[+] Step 4: Installing platform-tools, platforms;android-34, build-tools;34.0.0..."
"$CMDLINE_TOOLS_DIR/bin/sdkmanager" --sdk_root="$ANDROID_SDK_ROOT" \
    "platform-tools" \
    "platforms;android-34" \
    "build-tools;34.0.0"

# Re-apply permissive group permissions so non-root users can build without issues
chown -R root:users "$ANDROID_SDK_ROOT" 2>/dev/null || chown -R root:sudo "$ANDROID_SDK_ROOT" || true
chmod -R 775 "$ANDROID_SDK_ROOT"

# ------------------------------------------------------------------------------
# 4. Configure System-Wide Environment Variables
# ------------------------------------------------------------------------------
echo "[+] Step 5: Configuring system-wide environment profile..."

PROFILE_FILE="/etc/profile.d/android-sdk.sh"
cat << 'EOF' > "$PROFILE_FILE"
# ==============================================================================
# System-wide Environment Settings for OpenJDK 17 and Android SDK
# ==============================================================================
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
export ANDROID_HOME="/opt/android-sdk"
export ANDROID_SDK_ROOT="/opt/android-sdk"

# Add tools to PATH if not already present
for dir in "$JAVA_HOME/bin" \
           "$ANDROID_HOME/cmdline-tools/latest/bin" \
           "$ANDROID_HOME/platform-tools" \
           "$ANDROID_HOME/build-tools/34.0.0"; do
    case ":$PATH:" in
        *":$dir:"*) ;;
        *) PATH="$PATH:$dir" ;;
    esac
done
export PATH
EOF

chmod 644 "$PROFILE_FILE"

# Ensure /etc/bash.bashrc sources /etc/profile.d/android-sdk.sh for interactive shells
if ! grep -q "android-sdk.sh" /etc/bash.bashrc; then
    cat << 'EOF' >> /etc/bash.bashrc

# Android SDK & Java 17 environment
if [ -f /etc/profile.d/android-sdk.sh ]; then
    . /etc/profile.d/android-sdk.sh
fi
EOF
fi

# Ensure /etc/environment contains JAVA_HOME and ANDROID_HOME
if ! grep -q "JAVA_HOME=" /etc/environment; then
    echo 'JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"' >> /etc/environment
fi
if ! grep -q "ANDROID_HOME=" /etc/environment; then
    echo 'ANDROID_HOME="/opt/android-sdk"' >> /etc/environment
    echo 'ANDROID_SDK_ROOT="/opt/android-sdk"' >> /etc/environment
fi

# ------------------------------------------------------------------------------
# 5. Verification
# ------------------------------------------------------------------------------
echo ""
echo "=============================================================================="
echo "                   VERIFYING SYSTEM-WIDE INSTALLATION                         "
echo "=============================================================================="

echo "[✓] Java version:"
"$JAVA_17_PATH/bin/java" -version

echo "[✓] Javac version:"
"$JAVA_17_PATH/bin/javac" -version

echo "[✓] Sdkmanager version:"
"$CMDLINE_TOOLS_DIR/bin/sdkmanager" --version

echo "[✓] ADB version:"
"$ANDROID_SDK_ROOT/platform-tools/adb" version

echo "[✓] AAPT2 version:"
"$ANDROID_SDK_ROOT/build-tools/34.0.0/aapt2" version

echo ""
echo "[✓] Installed SDK Packages:"
"$CMDLINE_TOOLS_DIR/bin/sdkmanager" --sdk_root="$ANDROID_SDK_ROOT" --list_installed

echo "=============================================================================="
echo "[+] SUCCESS: OpenJDK 17 and Android SDK (Platform 34, Build-tools 34.0.0)"
echo "    have been successfully installed system-wide in /opt/android-sdk."
echo "=============================================================================="
