# Testing and release scope

## Release candidate 1.0.0-rc3 / b16

- Companion package: `app.tvtidy.cleaner`, versionCode 3, minSdk 26, targetSdk 36.
- Published APK SHA-256: `452222ef35c9a893cdadb400df652fec32818dc475d74223b207c7707dca0f77`.
- Size: 24,475,832 bytes.
- Signing certificate SHA-256: `3b5057c1ad574820ca80269cca1c0e798ae439e71f43729f3b7d54ca90fe9729`.
- Bundled redesigned launcher SHA-256: `09f2d94ebc3b32e375b71fd9feac1aedde16495d65dd8ea4041e836d0c373386`.

### What was exercised

The 0TV Home launcher was exercised on a Xiaomi Mi Box 4, Android 9, ARMv7, 1920×1080. Remote focus, navigation, scrolling and real installed-app banners were checked. Public TV captures come from that session; the network name is masked. The existing default launcher was not silently changed as part of publishing.

Build-time reported test results: companion 133 passed, core 293 passed, ADB 14 passed. Fork snapshot: 245 passed. Lint on the preceding b15 build reported 0 errors and 20 warnings; it was not rerun for the b16 asset-only delta. These are build records, not a claim that every flow was exercised on physical hardware.

### What remains unproven

- Full phone-side cleanup and restore end to end on physical hardware.
- Physical-phone haptics.
- Every TV, firmware, Android version, debugging mode or supported-looking model.
- Reliable recovery after data deletion, phone uninstall, lost/corrupt records or a changed device identity.

Code-rendered phone UI previews are not physical-phone screenshots. Simulated TV/unit tests do not replace hardware testing. No performance gains, storage savings or universal compatibility are promised.
