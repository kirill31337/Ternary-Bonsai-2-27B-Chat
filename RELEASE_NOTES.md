# Bonsai Local 1.1.1

- Targets Android 15 (API 35), removing the obsolete-target cause of the Play Protect warning.
- Keeps chat, controls and diagnostics clear of system bars, display cutouts and the keyboard on Android 15+.
- Pauses model downloads safely when Android 15 ends the dataSync foreground-service time budget.
- Preserves active chat retention and the separate foreground service for model loading and inference.
- Keeps Android 9+ ARM64 support, package identity, signing certificate, model storage and native runtime.

Host regression suites and APK signing/packaging checks passed. Device installation, keyboard layout and the final Play Protect verdict require testing on a phone; other Play Protect checks remain independent of the target SDK.
