# Building 0TV

## Layout

| Path | Purpose |
|---|---|
| `app/` | Kotlin/Compose phone companion |
| `core/` | Backup, cleanup, restore, protection, launcher handover and simulated-TV tests |
| `adb/` | ADB transport and tests |
| `launcher-fork/launcher/` | Redesigned 0TV Home fork of cleanyourtv v3.0.0 |
| `upstream/tv-detox/` | Recovered tv-detox 1.5.7 source snapshot for the bundled Simple launcher |
| `tools/` | Pinned inputs and launcher build helpers |
| `website/` | Static project website, no framework or build step |

## Phone companion

Needs JDK 21, Android SDK platform 37, appropriate Android build tools and the Gradle 9.3.1 wrapper. Gradle downloads dependencies on first use. Set `ANDROID_HOME` and write `sdk.dir` in your ignored `local.properties` if needed.

```sh
git clone https://github.com/vio137/0TV.git
cd 0TV
sh tools/bootstrap-inputs.sh
./gradlew :core:test
./gradlew :adb:test
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

The bootstrap script fetches cleanyourtv lists and themes at `095b71ad93d99ea28d94d97b06385709d354b795` and verifies launcher tag v3.0.0 resolves to `67dc5bee169038ed065a48ef837cf7fed5cf40f4`. These pins preserve the inputs, rather than silently following upstream main.

A release build uses `./gradlew :app:assembleRelease`. Without ignored `keys/signing.properties` it falls back to a development/debug signing identity. No private keys or signing passwords are supplied. A locally signed build is not a compatible in-place update of the public APK unless its signing identity matches. Do not uninstall a working app and erase its records merely to try a local build.

## Redesigned 0TV Home

```sh
sh tools/build-0tv-home.sh
# or, from launcher-fork/launcher:
./gradlew -PlauncherApplicationId=app.tvtidy.home :app:testDebugUnitTest :app:assembleRelease
```

Use the SDK/build tools required by that Gradle project. The fork keeps its own Gradle wrapper. The package flag matters: it installs alongside, not over, `com.gathin.tvhome`. The phone app bundles the finished APK at `app/src/main/assets/launcher/tvtidy-home.apk`.

## Other bundled launchers

- Original Dock-style Home: upstream cleanyourtv tag v3.0.0, commit `67dc5bee169038ed065a48ef837cf7fed5cf40f4`. `tools/build-styled-home.sh` builds it after bootstrap. Preserve its MIT notice.
- Simple: `upstream/tv-detox` is the source snapshot for version 1.5.7 / versionCode 20. The upstream commit was not recorded. It was recovered from the earlier build checkpoint whose embedded `tvhome.apk` is byte-identical to this release's asset: SHA-256 `172a2e75768b833802913b7d2a949e7c23f9a503e35e70fc740a77d1afddb4fe`. This is provenance, not an independent security audit or a bit-for-bit rebuild claim.
- `tools/build-simple-home.sh` builds an unsigned Simple APK from that source using JDK 17, SDK platform 34, build-tools 34.0.0 and d8 from build-tools 36.0.0. It does not overwrite the shipped asset. Sign your own output before installing it.

Bundled assets preserve the shipped release. Rebuilding launchers changes signatures unless you provide the appropriate signing identity. Publishing source does not publish signing keys.

## Dependencies and licensing

Versions are declared in `gradle/libs.versions.toml` and the module build files. See [third-party licences](THIRD_PARTY_LICENSES.md) and [NOTICE](../NOTICE). spake2-java 1.1.1 release source is at commit `809ad7aa731f475c07763b1ad2c165ae192dfb5f` in MuntashirAkon/spake2-java. The combined APK is conveyed under GPL-3.0; separate components keep their licences.

## Website

Serve `website/` with any static HTTP server. No dependency install or build is needed. GitHub Pages deploys that directory using `.github/workflows/pages.yml`. Paths are relative so the site works under `/0TV/`. Test mobile layouts, keyboard controls and reduced motion before changing the presentation.

## Scope of this publication

The public rc4 APK is the already-built release, not rebuilt as part of website publication. Source code, input pins and instructions are supplied for reuse. A bit-for-bit signed rebuild is not promised. Hardware testing limits are documented in [TESTING.md](TESTING.md).
