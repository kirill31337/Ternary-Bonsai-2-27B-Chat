#!/usr/bin/env python3
"""Refuse to package a missing, stale or untracked experimental GPU backend."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
plugin = root / 'app/lib/arm64-v8a/libbonsai_vulkan.so'
provenance = root / 'build/vulkan-runtime.json'
if not plugin.is_file() or not provenance.is_file():
    raise SystemExit('Build the GPU plugin first: bash scripts/build_vulkan_runtime.sh')
data = json.loads(provenance.read_text())
assert data['prismCommit'] == '9a9394a895b96003ca842a6041cb28ac49a108f7'
assert data['androidAbi'] == 'arm64-v8a' and data['minSdk'] == 28
assert data['quantization'] == 'PTQ1_0'
assert data['sha256'] == hashlib.sha256(plugin.read_bytes()).hexdigest()
(root / 'app/assets/vulkan-runtime.json').write_text(json.dumps(data, indent=2) + '\n')
print('Verified optional Vulkan backend:', data['sha256'])
