#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${APKTOOL_JAR:?Set APKTOOL_JAR to a compatible Apktool 3.x jar}"
: "${ANDROID_TOOLS_JAR:?Set ANDROID_TOOLS_JAR to the retained jadx fat jar containing D8 and apksig}"
mkdir -p build build/dex build/tools
if [[ ${COMPILE_JAVA:-1} == 1 ]]; then
  python3 make_api_stubs.py
  rm -rf build/stubs build/classes build/dex build/java-decoded
  mkdir -p build/stubs build/classes build/dex
  if [[ -n ${ANDROID_JAR:-} ]]; then
    # Only declarations of the retained, smali-assembled app classes are needed.
    javac --release 8 -Xlint:-options -cp "$ANDROID_JAR" -d build/stubs $(find build/api-signatures/com -name '*.java')
    jar cf build/app-signatures.jar -C build/stubs .
    APIS="$ANDROID_JAR:build/app-signatures.jar"
    LIBS=(--lib "$ANDROID_JAR" --lib build/app-signatures.jar)
  else
    # Emergency compile-only signatures; NEVER included in packaged classes.
    javac --release 8 -Xlint:-options -d build/stubs $(find build/api-signatures -name '*.java')
    jar cf build/api-signatures.jar -C build/stubs .
    APIS=build/api-signatures.jar
    LIBS=(--lib build/api-signatures.jar --lib "${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}")
  fi
  javac --release 8 -Xlint:-options -cp "$APIS" -d build/classes $(find java -name '*.java')
  jar cf build/transfers.jar -C build/classes .
  java -cp "$ANDROID_TOOLS_JAR" com.android.tools.r8.D8 --min-api 28 --no-desugaring "${LIBS[@]}" --output build/dex build/transfers.jar
  # Decode a DEX-only archive with Apktool. -r disables resource decoding.
  python3 - <<'PY'
import zipfile
with zipfile.ZipFile('build/java-only.apk','w') as z:
 z.write('build/dex/classes.dex','classes.dex')
PY
  java -jar "$APKTOOL_JAR" d -r -f build/java-only.apk -o build/java-decoded
  # Generated adapters are separate from MainActivity, Bridge, and diagnostics.
  find app/smali/com/prismml/bonsailocal/repair \( -name 'Transfer*.smali' -o -name 'ModelCatalog*.smali' -o -name 'RuntimeOptions*.smali' -o -name 'LocalBenchmark*.smali' -o -name 'ModelFiles*.smali' -o -name 'MiniJson*.smali' -o -name 'WebNet*.smali' -o -name 'WebTools*.smali' -o -name 'McpServer*.smali' -o -name 'Extensions*.smali' -o -name 'ChatBootstrap*.smali' -o -name 'BonsaiChromeClient*.smali' -o -name 'KeyVault*.smali' -o -name 'LocalWebClient*.smali' -o -name 'RuntimeService*.smali' -o -name 'RuntimeEnvironment*.smali' -o -name 'RuntimeSnapshot*.smali' -o -name 'UiSession*.smali' -o -name 'WindowLayout*.smali' \) -delete
  cp build/java-decoded/smali/com/prismml/bonsailocal/repair/*.smali app/smali/com/prismml/bonsailocal/repair/
fi
# Rebuild the small launcher; keep all Prism inference libraries byte-for-byte.
if [[ -n ${REUSE_NATIVE_FROM_APK:-} ]]; then
  # For managed/UI-only changes; retain the exact tested native runtime.
  python3 - <<'PY_NATIVE'
import os, pathlib, zipfile
with zipfile.ZipFile(os.environ['REUSE_NATIVE_FROM_APK']) as z:
    for name in z.namelist():
        if name.startswith('lib/arm64-v8a/') and name.endswith('.so'):
            path=pathlib.Path('app')/name
            path.parent.mkdir(parents=True,exist_ok=True)
            path.write_bytes(z.read(name))
PY_NATIVE
else
  python3 build_native.py
fi
java -jar "$APKTOOL_JAR" b app -o build/unsigned.apk
javac -cp "$ANDROID_TOOLS_JAR" -d build/tools tools/SignApk.java
KEYSTORE="${SIGNING_KEYSTORE:-signing/development.keystore}"
ALIAS="${SIGNING_ALIAS:-bonsai-recovery-dev}"
STOREPASS="${SIGNING_STOREPASS:-android}"
KEYPASS="${SIGNING_KEYPASS:-$STOREPASS}"
PREV="${PREVIOUS_APK:--}"
if [[ ! -f "$KEYSTORE" ]]; then
  echo "Signing keystore not found: $KEYSTORE" >&2
  echo "Set SIGNING_KEYSTORE/SIGNING_* or create an ephemeral test key for CI." >&2
  exit 2
fi
java -cp "$ANDROID_TOOLS_JAR:build/tools" SignApk build/unsigned.apk build/BonsaiLocal-1.1.1-arm64.apk "$KEYSTORE" "$ALIAS" "$STOREPASS" "$KEYPASS" "$PREV"
TEST_APK=build/BonsaiLocal-1.1.1-arm64.apk python3 -m pytest -q
node tests/ui_state_test.cjs
