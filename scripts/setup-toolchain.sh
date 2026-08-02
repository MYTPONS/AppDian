#!/bin/bash
# 应用大典 toolchain 安装脚本（用户目录安装，无需 sudo）
set -e

DEVTOOLS="$HOME/devtools"
mkdir -p "$DEVTOOLS"
cd "$DEVTOOLS"

echo "[1/5] 下载 JDK 17 (Temurin, TUNA 镜像)..."
if [ ! -d "$DEVTOOLS/jdk17" ]; then
  curl -sL -o jdk17.tar.gz "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/linux/OpenJDK17U-jdk_x64_linux_hotspot_17.0.20_8.tar.gz"
  mkdir -p jdk17
  tar -xzf jdk17.tar.gz -C jdk17 --strip-components=1
  rm -f jdk17.tar.gz
fi
"$DEVTOOLS/jdk17/bin/java" -version

echo "[2/5] 下载 Android cmdline-tools..."
mkdir -p "$DEVTOOLS/android-sdk/cmdline-tools"
if [ ! -d "$DEVTOOLS/android-sdk/cmdline-tools/latest" ]; then
  curl -sL -o cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  unzip -q cmdtools.zip -d "$DEVTOOLS/android-sdk/cmdline-tools"
  mv "$DEVTOOLS/android-sdk/cmdline-tools/cmdline-tools" "$DEVTOOLS/android-sdk/cmdline-tools/latest"
  rm -f cmdtools.zip
fi

echo "[3/5] 配置 sdkmanager..."
export ANDROID_HOME="$DEVTOOLS/android-sdk"
export JAVA_HOME="$DEVTOOLS/jdk17"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
yes | sdkmanager --licenses > /dev/null 2>&1 || true

echo "[4/5] 安装 platform / build-tools / platform-tools..."
sdkmanager --install "platforms;android-35" "build-tools;35.0.0" "platform-tools" > /dev/null

echo "[5/5] 完成."
ls "$ANDROID_HOME"
