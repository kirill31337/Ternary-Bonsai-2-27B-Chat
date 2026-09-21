#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
SRC="${PRISM_SOURCE:-$PWD/.cache/vulkan/llama.cpp}"
REV=9a9394a895b96003ca842a6041cb28ac49a108f7
if [[ ! -f "$SRC/common/log.cpp" ]]; then
  echo 'Build the Vulkan plugin first or set PRISM_SOURCE to the pinned Prism checkout.' >&2
  exit 2
fi
test "$(git -C "$SRC" rev-parse HEAD)" = "$REV"
mkdir -p build
g++ -std=c++17 -pthread -I"$SRC/common" -I"$SRC/include" -I"$SRC/ggml/include" \
  "$SRC/common/log.cpp" tests/prism_log_fixture.cpp -o build/prism_log_fixture
build/prism_log_fixture > build/prism-log-default.txt 2>&1
build/prism_log_fixture --log-verbosity 4 > build/prism-log-trace.txt 2>&1
python3 - <<'PY'
from pathlib import Path
quiet=Path('build/prism-log-default.txt').read_text()
trace=Path('build/prism-log-trace.txt').read_text()
assert 'n_ctx_slot' in quiet and 'Vulkan0' not in quiet
assert 'offloaded 8/65' in trace and 'Vulkan0 model buffer' in trace and 'Vulkan0 compute buffer' in trace
print('Pinned Prism logger: GPU evidence suppressed at default level, present at level 4')
PY
