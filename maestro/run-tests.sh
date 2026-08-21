#!/bin/bash
# Maestro Test Suite Runner
# FastMediaSorter v2
# Usage: ./maestro/run-tests.sh [smoke|critical|all]

SUITE=${1:-all}
MAESTRO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$MAESTRO_DIR")"

echo "🚀 FastMediaSorter v2 - Maestro E2E Tests"
echo "=========================================="
echo ""

# Check if Maestro is installed
if ! command -v maestro &> /dev/null; then
    echo "❌ Maestro CLI not found!"
    echo ""
    echo "Please install Maestro from: https://maestro.mobile.dev"
    echo ""
    echo "macOS/Linux (Homebrew):"
    echo "  brew tap mobile-dev-inc/tap"
    echo "  brew install maestro"
    echo ""
    echo "Linux/macOS (curl):"
    echo '  curl -Ls "https://get.maestro.mobile.dev" | bash'
    echo ""
    echo "Note: 'npm install -g maestro-cli' installs WRONG package!"
    exit 1
fi

echo "✓ Maestro CLI found"

# Check if device is connected
DEVICE_COUNT=$(adb devices | grep -w "device" | wc -l)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "❌ No Android device/emulator found!"
    echo ""
    echo "Please connect a device or start an emulator:"
    echo "  - adb devices"
    exit 1
fi

echo "✓ Android device found"

# Check if app is installed
if ! adb shell pm list packages | grep -q "com.sza.fastmediasorter"; then
    echo "⚠ FastMediaSorter not installed on device"
    echo ""
    echo "Building and installing app..."
    
    # Build debug APK
    cd "$PROJECT_ROOT"
    BUILD_DATE=$(date +%y%m%d%H%M)
    VERSION_NAME="${BUILD_DATE:0:1}.${BUILD_DATE:1:2}.${BUILD_DATE:3:4}.${BUILD_DATE:7:3}"
    APP_VERSION_CODE="${BUILD_DATE:0:8}${BUILD_DATE:8:1}"
    ./gradlew assembleStandardDebug "-Pfms.versionCode=${APP_VERSION_CODE}" "-Pfms.versionName=${VERSION_NAME}"
    
    if [ $? -ne 0 ]; then
        echo "❌ Build failed!"
        exit 1
    fi
    
    # Find APK
    APK=$(find "$PROJECT_ROOT/app_v2/build/outputs/apk/standard/debug" -name "*.apk" | head -n 1)
    
    if [ -z "$APK" ]; then
        echo "❌ APK not found!"
        exit 1
    fi
    
    # Install APK
    adb install -r "$APK"
    
    if [ $? -ne 0 ]; then
        echo "❌ Installation failed!"
        exit 1
    fi
    
    echo "✓ App installed"
else
    echo "✓ App already installed"
fi

echo ""
echo "Running test suite: $SUITE"
echo ""

# Run tests based on suite parameter
case "${SUITE,,}" in
    smoke)
        TEST_PATH="$MAESTRO_DIR/smoke"
        echo "Running smoke tests..."
        ;;
    critical)
        TEST_PATH="$MAESTRO_DIR/critical"
        echo "Running critical path tests..."
        ;;
    all)
        TEST_PATH="$MAESTRO_DIR"
        echo "Running all tests..."
        ;;
    *)
        echo "❌ Unknown test suite: $SUITE"
        echo "Valid options: smoke, critical, all"
        exit 1
        ;;
esac

echo ""

# Run Maestro tests
START_TIME=$(date +%s)
maestro test "$TEST_PATH"
EXIT_CODE=$?
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "=========================================="
echo "Test Duration: $(printf '%02d:%02d' $((DURATION/60)) $((DURATION%60)))"

if [ $EXIT_CODE -eq 0 ]; then
    echo "✅ All tests passed!"
else
    echo "❌ Some tests failed!"
fi

echo ""
exit $EXIT_CODE
