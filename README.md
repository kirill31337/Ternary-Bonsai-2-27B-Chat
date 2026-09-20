# Bonsai Local

<p align="center"><b>Run Ternary-Bonsai-2-27B locally on Android.</b></p>

Bonsai Local is an independent Android client for the Prism ML
**Ternary-Bonsai-2-27B** GGUF model. It runs the model on-device through a
pinned PrismML fork of llama.cpp, supports PQ2_0/PTQ1_0, optional vision,
optional web tools, resumable multi-connection downloads, model deletion and
an in-app performance benchmark.

> **Unofficial community project.** This application is not affiliated with or
> endorsed by Prism ML, ggml-org, Alibaba/Qwen, Hugging Face, DuckDuckGo or
> Brave. The model name is used descriptively to identify compatibility. The legacy
> Android application ID is retained only so existing experimental installs can update;
> it does not indicate Prism ML ownership or affiliation.

## Highlights

- On-device 27B GGUF inference on Android ARM64.
- `PQ2_0` and `PTQ1_0` model formats.
- Context presets from 4K to 256K, FP16/Q4 KV cache and configurable CPU threads.
- Optional `mmproj-Q8_0` vision module for image input.
- Optional `web_search` + `web_fetch` tools over a loopback MCP bridge.
- Up to 8 HTTP range connections with pause/resume and SHA-256 verification.
- Import already-downloaded GGUF/mmproj files with the system file picker.
- Remove PTQ1_0, PQ2_0 and mmproj independently.
- Local benchmark with prompt and generation tokens/sec.
- No model weights bundled in the APK or repository.

## Recommended first run

For a 16 GB Galaxy S26 Ultra, a conservative starting point is:

- PQ2_0
- 16K context
- FP16 KV
- 4 generation threads / 8 prompt-processing threads
- reasoning off

The app exposes higher context sizes and Q4 KV, but those trade speed for
memory and are device/workload dependent.

## Downloading models

The app is pinned to the publisher repository and revision recorded in
`ModelCatalog.java`. Files are downloaded directly from Hugging Face only
after the user requests them, and are verified by size and SHA-256.

Model weights are governed by the upstream model repository's license and
NOTICE; see [Third-party notices](THIRD_PARTY_NOTICES.md).

## Privacy

Local inference stays on the device. Network use is limited to explicit model
file downloads and optional web tools. See [PRIVACY.md](PRIVACY.md).

## Source build

This repository intentionally does **not** contain model weights, production
signing keys or vendored Prism runtime binaries. The runtime is downloaded
from the pinned upstream Prism release by `scripts/fetch_prism_runtime.sh`.

The historical 0.x builds used an emergency Apktool-based pipeline. Version
1.0 keeps that pipeline reproducible while removing private signing material
from the public source tree. See [BUILDING.md](BUILDING.md).

## License

Application code: Apache License 2.0. Bundled/fetched third-party components
retain their own licenses. See [LICENSE](LICENSE), [NOTICE](NOTICE), and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
