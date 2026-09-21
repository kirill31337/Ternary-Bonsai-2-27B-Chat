# Android 15 compatibility

Goal: remove the obsolete-target cause of the Play Protect installation warning,
while retaining Android 9 support, the installed signing identity and active chat.

1. Add regression coverage for system-bar/keyboard insets, retained WebViews,
   and timely foreground-service shutdown on Android 15 transfer timeout.
2. Target API 35. Apply insets at the Activity content boundary. Pause transfers
   on the dataSync timeout without waiting for network I/O or prematurely
   releasing the single-worker reservation. Publish version 1.1.1 metadata.
3. Compile with the official API 35 SDK, run host and packaging checks, inspect
   the final manifest and signing certificate, and independently review changes.
4. Push source changes and deliver the signed APK. The existing README download
   remains on the published 1.1.0 release until a new release is actually uploaded.

Limits: no phone/emulator is attached. Host checks cannot establish device UI or
Play Protect verdicts. Other Play Protect checks are separate from target age.

## Validation completed

- New regression cases failed before the changes and pass afterwards.
- Official API 35 compilation and the full `tests/run_all.sh` suite passed.
- 27 APK checks, 29 retained-session/inset assertions and six transfer-service
  scenarios passed, alongside the existing native, transfer, web and UI suites.
- Decoded signed APK: min SDK 28, target SDK 35, version code 111 / name 1.1.1.
- The previous signing certificate matches; all 15 native libraries are unchanged.
- Independent static review found no actionable issues. Device checks remain pending.
- APK SHA-256: `3454a222bfcbb6e909b919240d882688d819d39f308cbdb70b8615fc58603703`.
