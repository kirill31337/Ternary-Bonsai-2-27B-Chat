# Bonsai Local 1.2.1

- Fixes false GPU startup rejection in 1.2.0. The pinned Prism runtime emits llama/ggml INFO messages at verbosity 4; the default level 3 suppressed the GPU-buffer evidence required by the app. GPU launches now request level 4 with plain log output.
- Keeps the GPU verification: positive Vulkan model and compute buffers plus a positive engine-reported offload count are still required.
- Saves “Check engine” output to a separate log so device detection does not overwrite the model loading failure.
- Diagnostics retain both model-startup evidence and recent errors, and show the device-test log separately.
- Preserves CPU defaults, optional PTQ1_0 Vulkan, the existing inference binaries, package identity and signing certificate.

Install over the existing app. Select **PTQ1_0 → GPU · Vulkan → 8 layers**, then load the model again. The previous failed attempt does not mean the phone lacks Vulkan support.

The logger failure was reproduced using the actual pinned Prism logger and the JNI launcher. This does not replace testing inference speed, memory use and stability on the phone.

Validation: the full host suite passed, including the real pinned Prism logger regression, 29 signed-APK checks, 40 startup UI checks, 29 background lifecycle assertions and 11 RuntimeService assertions. The certificate matches 1.2.0; all inference libraries, including the Vulkan plugin, remain byte-identical.

APK SHA-256: `17634a8fbec5d23b8116d1f5560169c6f37e4331ed98373c042376f23229e650`.
