#!/bin/bash

# Local Build Script für Zomdroid
# Dieses Script simuliert den GitHub Actions Build lokal

set -e

echo "🚀 Starting Zomdroid local build..."

# Farben für Output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

echo_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

echo_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

echo_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Check Prerequisites
echo_info "Checking prerequisites..."

# Check Java version
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -ge 17 ]; then
        echo_success "Java $JAVA_VERSION found"
    else
        echo_warning "Java 17+ required, found Java $JAVA_VERSION"
    fi
else
    echo_error "Java not found"
    exit 1
fi

# Check Android SDK
if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
    echo_success "Android SDK found at $ANDROID_HOME"
else
    echo_warning "ANDROID_HOME not set or directory not found"
    echo_info "Set ANDROID_HOME environment variable to Android SDK path"
fi

# Check Gradle Wrapper
if [ -f "./gradlew" ]; then
    echo_success "Gradle wrapper found"
    chmod +x ./gradlew
else
    echo_error "gradlew not found. Run from project root directory."
    exit 1
fi

# Clean previous builds
echo_info "Cleaning previous builds..."
./gradlew clean

# Validate Gradle wrapper
echo_info "Validating Gradle wrapper..."
if command -v gradle &> /dev/null; then
    gradle wrapper --validate-certs || echo_warning "Gradle wrapper validation failed"
fi

# Lint check
echo_info "Running lint checks..."
if ./gradlew lintDebug; then
    echo_success "Lint checks passed"
else
    echo_warning "Lint checks failed, continuing..."
fi

# Unit tests
echo_info "Running unit tests..."
if ./gradlew testDebugUnitTest; then
    echo_success "Unit tests passed"
else
    echo_warning "Unit tests failed, continuing..."
fi

# Build debug APK
echo_info "Building debug APK..."
if ./gradlew assembleDebug; then
    echo_success "Debug APK built successfully"
    
    # Find and display APK location
    DEBUG_APK=$(find app/build/outputs/apk/debug -name "*.apk" 2>/dev/null | head -n1)
    if [ -n "$DEBUG_APK" ]; then
        echo_success "Debug APK location: $DEBUG_APK"
        
        # Get APK size
        APK_SIZE=$(du -h "$DEBUG_APK" | cut -f1)
        echo_info "APK size: $APK_SIZE"
    fi
else
    echo_error "Debug APK build failed"
    exit 1
fi

# Try to build release APK
echo_info "Attempting to build release APK..."
if ./gradlew assembleRelease; then
    echo_success "Release APK built successfully"
    
    # Find and display APK location
    RELEASE_APK=$(find app/build/outputs/apk/release -name "*.apk" 2>/dev/null | head -n1)
    if [ -n "$RELEASE_APK" ]; then
        echo_success "Release APK location: $RELEASE_APK"
        
        # Get APK size
        APK_SIZE=$(du -h "$RELEASE_APK" | cut -f1)
        echo_info "APK size: $APK_SIZE"
    fi
else
    echo_warning "Release APK build failed (this is expected without signing configuration)"
fi

# Generate build report
echo_info "Generating build report..."
BUILD_REPORT_DIR="build-reports/$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BUILD_REPORT_DIR"

# Copy important build outputs
if [ -d "app/build/reports" ]; then
    cp -r app/build/reports/* "$BUILD_REPORT_DIR/" 2>/dev/null || true
fi

# Generate dependency report
echo_info "Generating dependency report..."
./gradlew dependencies > "$BUILD_REPORT_DIR/dependencies.txt" 2>/dev/null || true

# Build summary
echo ""
echo_success "🎉 Build completed successfully!"
echo_info "Build artifacts:"
echo "  📁 Build reports: $BUILD_REPORT_DIR"

if [ -n "$DEBUG_APK" ]; then
    echo "  📱 Debug APK: $DEBUG_APK"
fi

if [ -n "$RELEASE_APK" ]; then
    echo "  📱 Release APK: $RELEASE_APK"
fi

echo ""
echo_info "Next steps:"
echo "  1. Install APK on Android device: adb install $DEBUG_APK"
echo "  2. Check lint report: $BUILD_REPORT_DIR/lint-results-debug.html"
echo "  3. Review test results: $BUILD_REPORT_DIR/tests/"
echo ""
echo_success "Local build script completed! 🚀"
