#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/test-classes
javac --release 8 -Xlint:-options -d build/test-classes java/com/prismml/bonsailocal/repair/TransferEngine.java $(find tests/java -name '*.java')
for test in TransferEngineTest TransferLargeFileTest TransferPauseTest; do
  java -Dfile.encoding=UTF-8 -cp build/test-classes "com.prismml.bonsailocal.repair.$test"
done
python3 tests/run_service_host.py
bash tests/run_jni.sh
python3 tests/run_adapter_host.py
mkdir -p build/options-test
javac -d build/options-test java/com/prismml/bonsailocal/repair/{ModelCatalog,RuntimeOptions,LocalBenchmark}.java tests/options/com/prismml/bonsailocal/repair/*.java
for test in ModelOptionsTest GpuOptionsTest BenchmarkTest; do
 java -cp build/options-test "com.prismml.bonsailocal.repair.$test"
done
TEST_APK="${TEST_APK:-build/BonsaiLocal-1.2.0-arm64.apk}" python3 -m pytest -q
node tests/ui_state_test.cjs

python3 tests/run_extensions_host.py
python3 tests/run_background_host.py
python3 tests/run_runtime_service_host.py
