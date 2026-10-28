# rc7 verification

- Version 1.0.0-rc7 / versionCode 7, app.tvtidy.cleaner, min Android 8, target SDK 36.
- Original signing certificate SHA-256: 3b5057c1ad574820ca80269cca1c0e798ae439e71f43729f3b7d54ca90fe9729.
- APK: 24,594,054 bytes; SHA-256: 509f11536f5ba22034a8f4b0e681d44c83072744708dbad3132ba41840c2e760.
- Core 310, ADB 14, app 154 tests pass (478 total, zero failures/errors).
- Cache tests cover per-device isolation, persistence across app instances, renamed/readdressed devices with stable serial, fallback identity for missing serial, corrupt data and no repeat ADB capture for cached devices. Connection-change test verifies stale capture does not survive a TV change.
- Actual Compose renders checked at 360dp and 320dp: TV bezel, saved-image fixture, Android waiting symbol, no timestamp/Refresh, storage and RAM chips preserved.
- Clock-stepped rendered-pixel test proves the Android symbol animates before capture and a saved screenshot does not animate. Remove animations is respected.
- Preview image files are synthetic fixtures, not captures from the user's TV. Physical phone/TV capture remains unverified. Firmware and protected content may prevent capture.
- No physical TV settings, launcher or DNS were changed. This is a phone-only update; previous App2TV, launcher, DNS and backup features remain in place.

## Previous release verification

# rc6 verification

- Version 1.0.0-rc6 / versionCode 6, app.tvtidy.cleaner, min Android 8, target SDK 36.
- APK: 24,594,054 bytes. SHA-256 `beee0864c990027ae7b6a6aee29975791b222ec60d06b616f9c959e412e7548a`.
- Original signing certificate SHA-256 `3b5057c1ad574820ca80269cca1c0e798ae439e71f43729f3b7d54ca90fe9729` verified with apksigner.
- Automated tests: core 310; ADB 14; app 150. Capture tests cover PNG validation, missing/oversized/truncated files, cleanup on failure, bounded decode, all-black rejection, and disconnect during capture.
- Actual Compose renders inspected at 360dp and 320dp widths with capture, loading and unavailable states. Screen contents are synthetic fixtures, not a physical TV capture. RAM/storage chips preserved.
- New screenshot path has not been verified on a physical phone and TV. Capture availability and protected-content behavior depend on firmware. No physical TV settings were changed for rc6.
- rc5 App2TV transfer and launcher remote-key hardware limits still apply. Bundled launchers are unchanged.

## Previous release verification

# Testing and release scope

## Release candidate 1.0.0-rc5

- Companion: app.tvtidy.cleaner, versionCode 5, original signing certificate unchanged.
- APK: 24,577,670 bytes; SHA-256 `606b7b4b5e88c91f9b94ab91e2f75ea5f608ccff6b444ce47fef66c00a07e901`.
- Core tests: 307 pass. Companion tests: 144 pass. Launcher tests pass, including exact sequence matching and real MainActivity key-event toggles. The restored rendered frame matches the original frame byte for byte.
- App2TV handles base plus split APKs and explains installer failures. Compatibility checks are not a guarantee that a phone app runs correctly on a TV.
- No physical phone-to-TV transfer or real-phone app list/icon check has been verified. Phone extraction, TV transfer and installer success are covered by separate automated fixtures, not a physical end-to-end test.
- The cracked-screen effect was rendered with real key events in a JVM test. It has not been exercised with a physical remote.
- Bundled 0TV Home: app.tvtidy.home versionCode 11, original certificate unchanged. Installing the phone update alone does not update the TV launcher. Reconnect, open Home screen, choose 0TV Home and press Use 0TV Home.

## Previous release candidate 1.0.0-rc4

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
