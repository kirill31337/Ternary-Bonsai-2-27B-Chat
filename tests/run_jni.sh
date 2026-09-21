#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/host-classes
bash tests/build_prism_log_fixture.sh
export BONSAI_TEST_PRISM_LOGGER="$PWD/build/prism_log_fixture"
clang -shared -fPIC -pthread -O1 -g -std=c11 -Wall -Wextra -Werror \
  -Inative/jni native/bonsai_app.c tests/host_log.c -o build/libbonsai_host.so
javac -d build/host-classes tests/jvm/com/prismml/bonsailocal/repair/*.java
java -Xcheck:jni -Dbonsai.lib="$PWD/build/libbonsai_host.so" \
  -cp build/host-classes com.prismml.bonsailocal.repair.Bridge

for t in OptionsRegression ShutdownRegression VisionRegression GpuRegression; do
 java -Xcheck:jni -Dbonsai.lib="$PWD/build/libbonsai_host.so" -cp build/host-classes "com.prismml.bonsailocal.repair.$t"
done
