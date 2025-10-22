#!/bin/bash
# Android Studio JDK Build Script
# Use Android Studio built-in JDK to compile the project

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

echo "Using Android Studio JDK: $JAVA_HOME"
echo "Java version:"
"$JAVA_HOME/bin/java" -version

echo ""
echo "Starting project build..."
./gradlew "$@"
