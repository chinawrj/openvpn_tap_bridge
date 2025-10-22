#!/bin/bash
# Android Studio JDK 构建脚本
# 使用 Android Studio 内置的 JDK 来编译项目

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

echo "使用 Android Studio JDK: $JAVA_HOME"
echo "Java 版本:"
"$JAVA_HOME/bin/java" -version

echo ""
echo "开始构建项目..."
./gradlew "$@"
