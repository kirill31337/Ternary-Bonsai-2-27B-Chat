#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REV="9a9394a895b96003ca842a6041cb28ac49a108f7"
TAG="prism-b10709-9a9394a"
ARCHIVE="llama-prism-b10709-9a9394a-bin-android-arm64.tar.gz"
URL="https://github.com/PrismML-Eng/llama.cpp/releases/download/${TAG}/${ARCHIVE}"
ARCHIVE_SHA="12994f6ce697a8e4127d6f879a01cc5a90f6cb452cb70a3a69e20d2b8e04ee35"
CACHE="${BONSAI_CACHE_DIR:-$ROOT/.cache/prism-runtime}"
mkdir -p "$CACHE" "$ROOT/app/lib/arm64-v8a"
TAR="$CACHE/$ARCHIVE"
if [[ ! -f "$TAR" ]]; then
  echo "Downloading pinned Prism Android runtime $REV"
  curl -fL --retry 3 --retry-delay 2 "$URL" -o "$TAR"
fi
echo "$ARCHIVE_SHA  $TAR" | sha256sum -c -
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
tar -xzf "$TAR" -C "$TMP"
SRC="$TMP/llama-prism-b10709-9a9394a"
for f in \
  libggml-base.so libggml.so libllama.so libllama-common.so libllama-server-impl.so libmtmd.so \
  libggml-cpu-android_armv8.0_1.so libggml-cpu-android_armv8.2_1.so libggml-cpu-android_armv8.2_2.so \
  libggml-cpu-android_armv8.6_1.so libggml-cpu-android_armv9.0_1.so libggml-cpu-android_armv9.2_1.so libggml-cpu-android_armv9.2_2.so; do
  install -m 0644 "$SRC/$f" "$ROOT/app/lib/arm64-v8a/$f"
done
install -m 0755 "$SRC/llama-server" "$ROOT/app/lib/arm64-v8a/libllama_server_exec.so"
python3 - "$ROOT" <<'PY'
import hashlib, json, pathlib, sys
root=pathlib.Path(sys.argv[1])
expected=json.loads((root/'runtime-sha256.json').read_text())
for rel,want in sorted(expected.items()):
    if rel.endswith('libbonsai_app.so'):
        continue
    p=root/'app'/rel if not rel.startswith('app/') else root/rel
    # manifest keys are lib/arm64-v8a/... relative to app/
    if not p.exists():
        p=root/'app'/rel
    got=hashlib.sha256(p.read_bytes()).hexdigest()
    if got!=want:
        raise SystemExit(f'hash mismatch: {rel}\nexpected {want}\nactual   {got}')
    print('OK',rel,got)
PY
cp "$SRC/LICENSE" "$ROOT/app/assets/licenses/PrismML-llama.cpp-LICENSE.txt"
echo "Pinned Prism runtime installed and verified."
