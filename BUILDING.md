# Building Bonsai Local

## Intentionally absent from Git

- GGUF/mmproj model weights
- Prism runtime `.so` binaries
- Android signing keystores/passwords
- user API keys and user data

## Prerequisites

- Linux x86_64
- JDK 17+ (21 tested)
- Python 3 + pytest
- Node.js (for UI contract tests)
- clang/lld with AArch64 Android target support
- g++ with C++17 support (host logger regression)
- Android SDK platform 35 or newer
- Apktool 3.x
- Android SDK build-tools 35.0.0 (`d8.jar` and `apksigner.jar`)
- Android NDK r29 (`29.0.14206865`), CMake, Ninja
- Vulkan SDK 1.4.357.1 for Linux (host shader compiler and headers)

## Fetch pinned runtime

```bash
bash scripts/fetch_prism_runtime.sh
```

The script downloads the pinned upstream Prism Android runtime and validates
its archive/library hashes against `runtime-sha256.json`.

Build the optional Vulkan plugin from the **same** pinned Prism commit:

```bash
export ANDROID_NDK="$ANDROID_SDK_ROOT/ndk/29.0.14206865"
export VULKAN_SDK=/path/to/1.4.357.1/x86_64
export VULKAN_SDK_VERSION=1.4.357.1
export LD_LIBRARY_PATH="$VULKAN_SDK/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
bash scripts/build_vulkan_runtime.sh
```

The plugin is deliberately named `libbonsai_vulkan.so`. GPU launches load it
explicitly through `GGML_BACKEND_PATH`; CPU launches do not initialize it.
The build verifies its generated provenance before packaging. All original CPU
runtime libraries remain unchanged. CI and release workflows build the plugin
from source, using the NDK's Android Vulkan loader and static C++ runtime.

## Build

```bash
export APKTOOL_JAR=/path/to/apktool.jar
export ANDROID_TOOLS_JAR="$ANDROID_SDK_ROOT/build-tools/35.0.0/lib/d8.jar:$ANDROID_SDK_ROOT/build-tools/35.0.0/lib/apksigner.jar"
export ANDROID_JAR="$ANDROID_SDK_ROOT/platforms/android-35/android.jar"

# Optional: enforce signing-certificate continuity against an installed build
export PREVIOUS_APK=/path/to/previous.apk

bash build_apk.sh
bash tests/run_all.sh
```

Output: `build/BonsaiLocal-1.2.1-arm64.apk`.

The APK targets API 35 (Android 15) and retains minimum API 28 (Android 9).
`resources.arsc` must remain uncompressed and its ZIP payload must be 4-byte
aligned. The packaging test inspects the final signed APK for both requirements.

For managed/UI-only updates with **unchanged native source**, set
`REUSE_NATIVE_FROM_APK=/path/to/verified-previous.apk` to retain its exact ARM64
native libraries instead of recompiling the launcher. Set `PREVIOUS_APK` too
to verify the update uses the same signing certificate. Do not use this option
after native code changes.

The JNI regression suite compiles the logger from the pinned Prism source in
`.cache/vulkan/llama.cpp`. Set `PRISM_SOURCE` if that checkout is elsewhere.
It verifies the real default/trace logging behavior as well as launcher behavior.

Host tests cover lifecycle state transitions, retained screens and native
integration. They do not substitute for Android device testing, especially
vendor battery restrictions or low-memory process termination.

The public tree intentionally contains no production signing key. See
[RELEASE_SIGNING.md](RELEASE_SIGNING.md).
