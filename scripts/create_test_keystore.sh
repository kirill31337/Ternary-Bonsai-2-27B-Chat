#!/usr/bin/env bash
set -euo pipefail
OUT="${1:-build/test-release.jks}"
mkdir -p "$(dirname "$OUT")"
rm -f "$OUT"
keytool -genkeypair -noprompt -keystore "$OUT" -storetype PKCS12 \
  -storepass android -keypass android -alias bonsai-ci-test \
  -keyalg RSA -keysize 2048 -validity 2 \
  -dname "CN=Bonsai Local CI Test,O=Untrusted Test Build,C=XX"
echo "$OUT"
