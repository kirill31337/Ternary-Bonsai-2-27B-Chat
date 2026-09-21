# Bonsai Local

<p align="center"><img src="branding/bonsai-local-icon.png" width="144" alt="Bonsai Local icon"></p>
<p align="center"><b>Run Ternary-Bonsai-2-27B locally on Android.</b></p>

Bonsai Local is an independent Android client for Prism ML's
**Ternary-Bonsai-2-27B** GGUF model. It runs the model on-device through a
pinned PrismML fork of llama.cpp and adds model management, resumable downloads,
vision, optional web tools and a local benchmark.

## Download APK

**[Download Bonsai Local 1.1.2 for Android · ARM64 · 62 MiB](https://github.com/kirill31337/Ternary-Bonsai-2-27B-Chat/releases/download/1.1.2/BonsaiLocal-1.1.2-arm64.apk)**

[Latest release](https://github.com/kirill31337/Ternary-Bonsai-2-27B-Chat/releases/latest) · [All versions](https://github.com/kirill31337/Ternary-Bonsai-2-27B-Chat/releases) · [Русский](README_RU.md)

Requires Android 9 or newer and an ARM64 device. Install the APK, then download
or import a model inside the app. Model weights are downloaded separately.

> **Unofficial community project.** Not affiliated with or endorsed by Prism ML,
> ggml-org, Alibaba/Qwen, Hugging Face, DuckDuckGo or Brave. Product/model names
> are used only to describe compatibility and origin.

## Features

- On-device 27B GGUF inference on Android ARM64.
- Background model loading and inference with an ongoing notification.
- Retained chat and controls across window recreation; Back opens model controls.
- `PQ2_0` and `PTQ1_0` packs.
- 4K–256K context presets, FP16/Q4 KV cache, configurable CPU threads.
- Optional Q8 vision `mmproj` for image input.
- Optional `web_search` + `web_fetch` via a loopback MCP bridge.
- Up to 8 HTTP range connections with pause/resume and SHA-256 verification.
- Import an already-downloaded GGUF/mmproj via Android's system picker.
- Delete PTQ1_0, PQ2_0 and mmproj independently.
- Local prompt/generation tokens-per-second benchmark.
- Model weights are **not** bundled in the APK or repository.

## Starting settings

`PQ2_0 · 16K context · FP16 KV · 4 generation threads / 8 prompt threads · reasoning off`

Longer context and Q4 KV are available but trade speed against memory and may
behave differently by device and workload.

## Model downloads

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
