# Bonsai Local 1.1.0

- Model loading and inference run in a foreground service with an ongoing notification and Stop action.
- Backgrounding or recreating the window retains the current chat document and stream.
- Back from chat opens model controls; returning to chat preserves the conversation.
- Removed device-specific recommendations and shortened the interface.
- Native runtime, package name, model storage and update signing certificate remain unchanged.

Android 9+ ARM64. Model weights are downloaded separately. Force-stop or OS process termination cannot preserve a model in RAM. Host regression suites and APK signing/packaging checks passed; physical-device background behavior has not been tested in this build environment.
