# Background session implementation plan

> Implementation: execute inline, then request one independent review.

**Goal:** Keep model loading and chat alive when the Android window is backgrounded, restore the same screen, simplify universal app copy, and deliver a signed 1.1.0 APK.

**Architecture:** A private foreground RuntimeService owns the inference lifetime and CPU wake lock. Separate management and chat WebViews are retained independently of Activity destruction, with a replaceable Activity reference for UI operations. Only explicit stop ends inference; reopening does not reload an existing document.

**Constraints:** Preserve package, chat origin, storage, model files and signing certificate. Never upload signing material. Old APK upload is cancelled. Do not publish the new APK in Git. Keep existing native libraries unchanged.

## Task 1: Background inference and retained screen

- [x] Add executable host regressions for runtime activity classification and retaining the same WebView across Activity recreation, including late detach from an obsolete Activity.
- [x] Run regressions before implementation and confirm missing behavior fails.
- [x] Add RuntimeSnapshot, RuntimeEnvironment, RuntimeService and UiSession; route start/stop/self-test through the service.
- [x] Replace lifecycle resets in MainActivity.smali with restore/detach, make Back return from chat to controls and background the task from controls, and use weak Activity references for JavaScript/attachment adapters.
- [x] Declare specialUse FGS, notification permission request and singleTop launcher behavior.
- [x] Run host regressions, existing Java/JS suites and packaging checks.

## Task 2: Minimal universal interface

- [x] Remove Samsung/Galaxy and fixed-device recommendations from app and readmes.
- [x] Keep model selection, load/chat/stop, progress and errors prominent; collapse optional tools, storage and diagnostics. Retain concise privacy and memory information.
- [x] Preserve element IDs and behavior; run actual shipped JavaScript tests and inspect the HTML.

## Task 3: Signed APK

- [x] Bump version code/name to 110/1.1.0 and add a documented option to reuse verified native binaries when native sources are unchanged.
- [x] Build with the Android SDK and historical private key; verify signature continuity against 1.0.0.
- [x] Verify APK CRC, manifest/service declarations, DEX classes, native hashes and release contents.
- [x] Request independent review, fix material findings, and deliver the APK after saving it.

## Review focus

Late Activity destruction must not detach the replacement window. Foreground failure must never launch unprotected inference. Stop must wait for native termination before releasing resources. Cached chat must never regain the privileged management bridge. Device process termination cannot preserve RAM; do not claim that it does. The signing key and model weights must remain private/external.

## Progress

- Baseline: current Activity always calls home() on creation; no inference foreground service exists. Historical signing key and Android toolchain recovered. Local Java compiler is available via its JDK module. Native compiler is absent; native code will remain unchanged and packaged libraries will be hash-verified against 1.0.0.

- Independent review found inaccessible model controls after entering the retained chat. Fixed using separate retained management/chat documents, with a bridge only on management. Added executable navigation and consent-refresh regressions. The reviewer deferred signature/native checks to release verification and device power/renderer behavior to physical-device testing; these limits are retained.
- Red/green checks cover notification-refresh failure, Activity replacement, retained conversation and explicit return to controls. Full host JNI/Java/JS suite passed. No physical Android device is available.
