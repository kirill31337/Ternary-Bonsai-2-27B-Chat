#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${ANDROID_NDK:?Set ANDROID_NDK to Android NDK r29}"
: "${VULKAN_SDK:?Set VULKAN_SDK to the host Vulkan SDK}"
REV=9a9394a895b96003ca842a6041cb28ac49a108f7
CACHE="${BONSAI_CACHE_DIR:-$ROOT/.cache}/vulkan"
SRC="$CACHE/llama.cpp"
mkdir -p "$CACHE" "$ROOT/app/lib/arm64-v8a" "$ROOT/build"
if [[ ! -d "$SRC/.git" ]]; then
  git init "$SRC"
  git -C "$SRC" remote add origin https://github.com/PrismML-Eng/llama.cpp.git
fi
git -C "$SRC" fetch --depth 1 origin "$REV"
git -C "$SRC" checkout --detach "$REV"
cmake -S "$SRC" -B "$CACHE/build" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_static \
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_RPATH='$ORIGIN' -DCMAKE_BUILD_WITH_INSTALL_RPATH=ON \
  -DCMAKE_SHARED_LINKER_FLAGS='-Wl,-z,max-page-size=16384' \
  -DCMAKE_PREFIX_PATH="$VULKAN_SDK" -DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH \
  -DVulkan_INCLUDE_DIR="$VULKAN_SDK/include" \
  -DVulkan_LIBRARY="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/28/libvulkan.so" \
  -DVulkan_GLSLC_EXECUTABLE="$VULKAN_SDK/bin/glslc" \
  -DGGML_VULKAN=ON -DGGML_BACKEND_DL=ON -DGGML_CPU=OFF -DGGML_NATIVE=OFF \
  -DGGML_OPENMP=OFF -DLLAMA_BUILD_COMMON=OFF -DLLAMA_BUILD_TESTS=OFF \
  -DLLAMA_BUILD_TOOLS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_MTMD=OFF \
  -DLLAMA_OPENSSL=OFF
cmake --build "$CACHE/build" --target ggml-vulkan -j "${CMAKE_BUILD_PARALLEL_LEVEL:-2}"
# Deliberately avoid libggml-vulkan.so: CPU launches must not discover or initialize it.
install -m 0644 "$CACHE/build/bin/libggml-vulkan.so" "$ROOT/app/lib/arm64-v8a/libbonsai_vulkan.so"
"$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded "$ROOT/app/lib/arm64-v8a/libbonsai_vulkan.so"
python3 - "$ROOT" "$REV" <<'PY'
import hashlib,json,os,pathlib,sys
root=pathlib.Path(sys.argv[1]);lib=root/'app/lib/arm64-v8a/libbonsai_vulkan.so'
record={'prismCommit':sys.argv[2],'androidAbi':'arm64-v8a','minSdk':28,'quantization':'PTQ1_0',
        'vulkanSdk':os.environ.get('VULKAN_SDK_VERSION','unknown'),'sha256':hashlib.sha256(lib.read_bytes()).hexdigest()}
(root/'build/vulkan-runtime.json').write_text(json.dumps(record,indent=2)+'\n')
PY
