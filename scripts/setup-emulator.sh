#!/bin/bash
set -e
export ANDROID_HOME="$HOME/devtools/android-sdk"
export JAVA_HOME="$HOME/devtools/jdk17"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
echo "[1/3] 安装 emulator + system image..."
sdkmanager --install "emulator" "system-images;android-35;google_apis;x86_64" > /dev/null
echo "[2/3] 创建 AVD..."
echo no | avdmanager create avd -n appdian -k "system-images;android-35;google_apis;x86_64" -d pixel_6 --force > /dev/null
echo "[3/3] 完成"
