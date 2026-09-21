# Bonsai Local

<p align="center"><img src="branding/bonsai-local-icon.png" width="144" alt="Bonsai Local icon"></p>
<p align="center"><b>Run Ternary-Bonsai-2-27B locally on Android.</b></p>

Bonsai Local is an independent Android client for Prism ML's
**Ternary-Bonsai-2-27B** GGUF model. It runs the model on-device through a
pinned PrismML fork of llama.cpp and adds model management, resumable downloads,
vision, optional web tools and a local benchmark.

> **Unofficial community project.** Not affiliated with or endorsed by Prism ML,
> ggml-org, Alibaba/Qwen, Hugging Face, DuckDuckGo or Brave. Product/model names
> are used only to describe compatibility and origin.

## Features

- On-device 27B GGUF inference on Android ARM64.
- `PQ2_0` and `PTQ1_0` packs.
- 4K–256K context presets, FP16/Q4 KV cache, configurable CPU threads.
- Optional Q8 vision `mmproj` for image input.
- Optional `web_search` + `web_fetch` via a loopback MCP bridge.
- Up to 8 HTTP range connections with pause/resume and SHA-256 verification.
- Import an already-downloaded GGUF/mmproj via Android's system picker.
- Delete PTQ1_0, PQ2_0 and mmproj independently.
- Local prompt/generation tokens-per-second benchmark.
- Model weights are **not** bundled in the APK or repository.

## Recommended start for 16 GB RAM

`PQ2_0 · 16K context · FP16 KV · 4 generation threads / 8 prompt threads · reasoning off`

Longer context and Q4 KV are available but trade speed against memory and may
behave differently by device and workload.

## Downloads

The app downloads model files directly from the pinned publisher repository on
Hugging Face only after the user requests it and verifies size/SHA-256.

## Privacy & security

Inference is local. Network access is used for explicitly requested model
files and optional web tools. See [PRIVACY.md](PRIVACY.md) and
[SECURITY.md](SECURITY.md).

## Licensing

Application code: Apache-2.0. Third-party components retain their own licenses.
Model weights are not redistributed by this project. See [LICENSE](LICENSE),
[NOTICE](NOTICE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Build

See [BUILDING.md](BUILDING.md). The public repository intentionally excludes
model weights, Prism runtime binaries and private signing keys; pinned runtime
binaries are fetched during the build.
