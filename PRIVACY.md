# Privacy

Bonsai Local is designed for **on-device inference**.

## Data that stays on the phone

- Prompts and generated answers are processed by the local llama.cpp runtime.
- GGUF inference does not require a cloud inference service.
- Chat history managed by the bundled Web UI stays in the app/WebView storage
  unless the user explicitly exports or shares it.
- Model and mmproj files remain in app storage after download/import.
- A Brave Search API key, if supplied, is encrypted with Android Keystore and
  is not intentionally exposed to the language model as plaintext.

## When the network is used

The network is used only for features that need it:

1. model/mmproj downloads explicitly initiated by the user from the pinned
   Hugging Face publisher repository; and
2. optional web tools explicitly enabled by the user.

For `web_search`, the chosen provider receives the search query. For
`web_fetch`, the requested public HTTPS site receives the page request. A
model-generated search query may contain personal information if it appears in
the prompt/context, so do not enable web tools for sensitive conversations
unless you accept that risk.

The app does not intentionally upload complete chat history, local files or
model weights to DuckDuckGo or Brave automatically.

## Android permissions

- `INTERNET`: model downloads and optional web tools.
- foreground-service/data-sync permissions: resilient long downloads.
- notification permission when Android requires it for visible transfer state.
- wake lock: keep an explicitly started transfer alive.

File import uses Android's system document picker instead of broad storage
permission.

## External providers

Hugging Face, DuckDuckGo and Brave are independent services with their own
terms and privacy policies. Bonsai Local is not affiliated with them.
