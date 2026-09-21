# Bonsai Local 1.1.2

- Fixes APK resource packaging: `resources.arsc` is stored without compression, as required for installation when targeting Android 11 or newer.
- Adds a regression check of the signed APK's resource-table compression and 4-byte payload alignment. Signature verification alone did not catch the 1.1.1 installation blocker.
- Retains target API 35, minimum API 28, background chat behavior, package identity and the existing signing certificate.

Validation: 28 APK checks and 36 UI checks passed. The signed resource payload is uncompressed and 4-byte aligned. The certificate matches 1.1.0; the DEX and all 15 native libraries match 1.1.1 byte-for-byte.

Install as an update; removing the previous app is not required. Physical-device installation remains to be verified.
