# Testing and release scope

## Release candidate 1.0.0-rc4

- Companion package: `app.tvtidy.cleaner`, versionCode 4, minSdk 26, targetSdk 36.
- Published APK SHA-256: `11ec7586bae60d2710e8cb3c9d18fb384e1ddcfdfa31242712fcb7fde2e7d724`.
- Size: 24,543,858 bytes.
- Signing certificate SHA-256: `3b5057c1ad574820ca80269cca1c0e798ae439e71f43729f3b7d54ca90fe9729` (same as earlier releases, so it installs as an update).

### What changed from rc3

- Block ads reads the TV's actual Private DNS mode and server and shows them, separate from a live name-lookup check.
- "Turn off" returns the TV to Automatic when the saved previous value was itself AdGuard or your own server.
- You can enter your own DNS-over-TLS server name. If the TV cannot resolve names through it, the setting is rolled back.
- Preview images for 0TV Home and Projectivy. The Projectivy image is a screenshot from its Google Play listing, credited to Spocky; no reuse licence is claimed.

### What was exercised

Core tests: 297 passed. Companion unit and rendered-screen tests: 138 passed. The DNS setting behaviour was observed on a Xiaomi Mi Box 4 (Android 9) over ADB. The 0TV Home launcher was exercised on the same TV; public TV captures come from it and the network name is masked.

### What remains unproven

- Full phone-side cleanup and restore end to end on physical hardware.
- Physical-phone haptics.
- Every TV, firmware, Android version, debugging mode or supported-looking model.
- Reliable recovery after data deletion, phone uninstall, lost/corrupt records or a changed device identity.

Code-rendered phone UI previews are not physical-phone screenshots. Simulated TV/unit tests do not replace hardware testing. No performance gains, storage savings or universal compatibility are promised.
