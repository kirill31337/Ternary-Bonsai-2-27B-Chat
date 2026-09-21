# Bonsai Local 1.2.0

- Adds an optional experimental **GPU · Vulkan** switch before loading PTQ1_0, with 8/16/32/all layer presets. CPU stays the default; PQ2_0 stays on CPU.
- Builds the Android Vulkan plugin from the same pinned Prism runtime revision, including PTQ1_0 support. Existing CPU inference binaries are preserved.
- Saves the GPU setting, locks it during loading/inference and reports startup errors without silently presenting CPU fallback as GPU success.
- Requires positive Vulkan model and compute buffers before opening the chat. The displayed layer count is reported by the engine; some operations may still use CPU.
- Preserves background loading/chat, package identity, signing certificate, Android 9 minimum and the resource packaging fix from 1.1.2.

Start with **PTQ1_0 → GPU · Vulkan → 8 layers**. Compare CPU/GPU in the local benchmark using the same model and context. If Vulkan fails to load, stop the runtime and turn GPU off.

This is an experimental option inside the regular app. Phone GPU compatibility, performance, memory use and heat must be measured on the actual device. No on-device speedup is claimed.

Validation: the full host test suite passed, including 29 signed-APK checks, 40 startup UI checks, 29 background lifecycle assertions and 11 RuntimeService assertions. The APK certificate matches 1.1.2. All 14 previous inference libraries match 1.1.2 byte-for-byte; the JNI launcher was rebuilt and the optional Vulkan plugin was added. The signed APK is 82,668,108 bytes.

SHA-256: `f35fd95de194a36cf7dc2f58aca7a29d6da661f30aa2398dd7a6272ff6286d10`.
