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
- Android SDK platform 35 or newer
- Apktool 3.x
- a fat Java tool jar containing D8 + apksig (the historical build uses jadx-all)

## Fetch pinned runtime

```bash
bash scripts/fetch_prism_runtime.sh
```

The script downloads the pinned upstream Prism Android runtime and validates
its archive/library hashes against `runtime-sha256.json`.

## Build

```bash
export APKTOOL_JAR=/path/to/apktool.jar
export ANDROID_TOOLS_JAR=/path/to/jadx-all.jar
export ANDROID_JAR="$ANDROID_SDK_ROOT/platforms/android-35/android.jar"

# Optional: enforce signing-certificate continuity against an installed build
export PREVIOUS_APK=/path/to/previous.apk

bash build_apk.sh
bash tests/run_all.sh
```

Output: `build/BonsaiLocal-1.1.1-arm64.apk`.

The APK targets API 35 (Android 15) and retains minimum API 28 (Android 9).

For managed/UI-only updates with **unchanged native source**, set
`REUSE_NATIVE_FROM_APK=/path/to/verified-previous.apk` to retain its exact ARM64
native libraries instead of recompiling the launcher. Set `PREVIOUS_APK` too
to verify the update uses the same signing certificate. Do not use this option
after native code changes.

Host tests cover lifecycle state transitions, retained screens and native
integration. They do not substitute for Android device testing, especially
vendor battery restrictions or low-memory process termination.

The public tree intentionally contains no production signing key. See
[RELEASE_SIGNING.md](RELEASE_SIGNING.md).
