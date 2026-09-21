# Third-party notices

Bonsai Local is an independent Android application. Original application code
in this repository is licensed under Apache-2.0. The application interoperates
with, downloads, or redistributes the components below under their own terms.

## PrismML-Eng/llama.cpp runtime

- Fork: `PrismML-Eng/llama.cpp`
- Parent project: `ggml-org/llama.cpp`
- Pinned commit: `9a9394a895b96003ca842a6041cb28ac49a108f7`
- License: MIT
- Purpose: local GGUF inference, llama-server and Web UI.

The public source repository does not vendor the runtime binaries. Build scripts
fetch the exact upstream Android artifact and verify its SHA-256. Release APKs
contain the required ARM64 libraries. The MIT license text is retained in
`third_party/llama.cpp/LICENSE` and in the APK assets.

The pinned runtime source also includes or builds with third-party components.
Notable redistribution notices include:

- **nlohmann/json** — Copyright (c) 2013-2025 Niels Lohmann — MIT.
  License: `third_party/llama.cpp/LICENSE-jsonhpp`.
- **cpp-httplib** — Copyright (c) 2017 yhirose — MIT.
  License: `third_party/llama.cpp/LICENSE-cpp-httplib`.
- **BoringSSL / fiat-crypto** — Apache-2.0 and retained upstream notices.
  The Prism Android release is built with BoringSSL support; consumers should
  preserve the upstream BoringSSL license and notices when rebuilding or
  redistributing a modified runtime.
- Additional vendored/hash/UI dependencies retain the licenses present in the
  pinned llama.cpp source tree. This project does not relicense them.

When changing the pinned runtime, re-run the license audit workflow before a
release.

## Ternary-Bonsai-2-27B model

- Publisher: Prism ML
- Repository: `prism-ml/Ternary-Bonsai-2-27B-gguf`
- Pinned revision used by the downloader:
  `6ed5e12bf84b7a63069882c91dd9e9218647d17b`
- License shown by the publisher repository: Apache License 2.0
- Base model identified by the model card: `Qwen/Qwen3.8-27B`, Apache-2.0

The model repository includes a NOTICE stating that the software is copyright
2026-present Prism ML, Inc. and requests attribution such as **"Created using
Bonsai by Prism ML."** This repository and the app retain that attribution.

**Model weights and mmproj files are not bundled in this repository or APK.**
They are downloaded directly from the publisher only after the user requests
them. Users who redistribute the weights should also redistribute the
publisher's LICENSE/NOTICE and review the then-current model repository terms.

## External network services

Optional web search can use DuckDuckGo or Brave Search API. They are external
services, not bundled dependencies. Their own terms, privacy policies, quotas
and pricing may apply. Bonsai Local does not claim affiliation with them.

## Branding and trademarks

The Bonsai Local icon is an original project mark and is not copied from the
Prism ML Bonsai logo. "Bonsai", "Prism ML", "Qwen", "Hugging Face",
"DuckDuckGo" and "Brave" are used only where needed to describe compatibility,
origin or an optional provider. No endorsement or affiliation is claimed.

The legacy Android application identifier contains `prismml` solely for update
compatibility with earlier experimental APKs. It is not a brand claim.

This file is a practical attribution record, not legal advice.

## OpenJDK JNI headers

`native/jni/jni.h` and `native/jni/jni_md.h` are retained build-time headers
from OpenJDK. Their file headers identify GPL v2 with the Classpath Exception.
The accompanying distribution copyright/license record is retained at
`third_party/openjdk/COPYRIGHT`. These headers are used only to compile the
small JNI launcher and are not used to relicense Bonsai Local application code.
