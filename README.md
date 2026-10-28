<div align="center">
<img src="website/assets/icon.png" width="88" alt="0TV">

# 0TV

### Less noise. More television.

A calmer Android TV starts on your phone.<br>
Review preloaded apps, keep a backup, and make room for a Home you actually want.

<a href="https://github.com/vio137/0TV/releases/latest/download/0TV-1.0.0-rc7.apk"><img src="https://img.shields.io/badge/DOWNLOAD_0TV_APK-Android_8%2B-bcff69?style=for-the-badge&labelColor=14212c&color=bcff69" alt="DOWNLOAD 0TV APK - Android 8+"></a>

**[Visit the project website ↗](https://vio137.github.io/0TV/)** · **[Install guide](docs/GUIDE.md)** · **[Latest release](https://github.com/vio137/0TV/releases/latest)**

`1.0.0-rc7` · `Android phone companion` · `Free & open source`

<img src="website/assets/tv-home.png" width="100%" alt="Real 0TV Home capture on Xiaomi Mi Box 4 showing Netflix focus and installed app banners">

<sub>Real Xiaomi Mi Box 4 capture. Network name masked. Installed apps vary.</sub>
</div>

---

## Your screen. Your rules.

| Review & tidy | A new Home | Everyday tools |
|---|---|---|
| Connect over your home network. Review selected apps before normal cleanup disables them. Backup first, with protected-package guards. | Choose **0TV Home** in the phone app for the redesigned launcher. Large app banners, visible remote focus and a calm layout. | Phone remote, supported text input, app launching, file sending and checks for apps that came back after an update. |

**One phone APK.** The companion handles installation of the chosen bundled launcher on the TV. Do not install the phone APK as though it were a standalone TV launcher.

**App2TV.** Many apps are missing from the TV's app store. Install the app on your phone, open App2TV in My TV, pick it from the list, and 0TV copies it to the TV and installs it over the existing connection. It checks the TV first and explains any failure in plain English. See the guide for how it works and its limits.

**Easter egg.** On 0TV Home, press Up, Up, Down, Down, Left, Right, Left, Right on the remote for a cracked-screen look. Press it again to undo.

Normal cleanup disables selected packages. Separate removal tools have stronger recovery limits. Optional DNS filtering and optional TizenTube Cobalt / J10TV installation are separate choices, not hidden cleanup steps.

## Get started

1. **Install on your phone.** Download the APK above on an Android 8.0+ ARM phone. Open it and allow installation from your browser/Files app if Android asks.
2. **Prepare the TV.** Use the same home network. Enable the TV's Developer options and USB/Wireless debugging as available.
3. **Connect and approve.** Choose the TV in 0TV or enter its current address. Approve the prompt with the TV remote. Wireless debugging uses a fresh pairing code and port.
4. **Review.** Let the backup complete. Check what you want to keep before confirming changes.
5. **Choose 0TV Home.** Select that exact choice for the redesigned screen. Simple and the original Dock-style choice are different launchers.
6. **Keep recovery records.** Do not uninstall the phone app casually. Read the restore limits before making changes.

[Full illustrated website guide](https://vio137.github.io/0TV/#guide) · [Detailed written guide](docs/GUIDE.md) · [Recovery and troubleshooting](docs/RECOVERY.md)

## See it in use

<table><tr><td width="30%" align="center"><img src="website/assets/phone-welcome.png" width="230" alt="Rendered 0TV phone welcome UI"><br><sub>Phone UI render, not a hardware capture.</sub></td><td width="70%"><img src="website/assets/tv-library.png" alt="Real Mi Box 4 capture of scrolled 0TV Home app library"><br><sub>Real TV capture. Network name masked.</sub><br><br>0TV Home was tested on a Xiaomi Mi Box 4 running Android 9 for remote focus, navigation, scrolling and actual installed-app banners. The full phone-side cleanup/restore flow has not been proven end to end on hardware.</td></tr></table>

## Read this before changing your TV

- **Restore is not a universal undo.** It depends on recorded backups, device checks and prior state. Permanently deleted app data cannot be recovered.
- Uninstalling the phone app can remove its local history. Keep the app and records when updating.
- Restore does **not** roll back a TizenTube/Cobalt or J10TV install.
- Optional Block ads uses AdGuard Private DNS (or a DNS-over-TLS server you enter) on compatible Android 9+ TVs. The app reads the TV's actual setting and checks that names resolve. DNS lookups then go to a third party. It does not block YouTube in-video ads.
- TV firmware, network isolation and debugging support affect compatibility. Never expose ADB to the public internet or disable its approval checks.
- This is a release candidate. See the [test scope](docs/TESTING.md).

## Compatibility

- **Phone:** Android 8.0+ (API 26), ARM64 / ARMv7 release APK.
- **Primary TV targets:** Android TV and Google TV with accessible ADB debugging over the home network.
- **Fire TV:** limited cleanup/remote path; replacement of Amazon's Home is not promised.
- **Samsung Tizen / LG webOS:** not supported Android TV targets.
- Per-model lists do not guarantee the behavior of every firmware or device.

## Privacy

No account, analytics, automatic update check or automatic feedback upload in this build. The main connection is local ADB. Optional Cobalt and J10TV downloads contact GitHub. Optional DNS filtering uses AdGuard. Feedback opens Android's share sheet; you choose what to send and where.

The website has no analytics or third-party scripts. [Privacy and network details](docs/PRIVACY.md).

## Verify the download

| Item | Value |
|---|---|
| Version | 1.0.0-rc7 / versionCode 7 |
| Package | `app.tvtidy.cleaner` |
| APK size | 24,594,054 bytes |
| SHA-256 | `509f11536f5ba22034a8f4b0e681d44c83072744708dbad3132ba41840c2e760` |

```sh
sha256sum 0TV-1.0.0-rc7.apk
```

Download from [this repository's releases](https://github.com/vio137/0TV/releases/latest), not a third-party reupload. Keep an existing installation's recovery history when updating.

## For builders

[Building from source](docs/BUILDING.md) covers dependencies, pinned inputs, both launcher builds and signing limits.

```sh
git clone https://github.com/vio137/0TV.git
cd 0TV
sh tools/bootstrap-inputs.sh
./gradlew :core:test :adb:test :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

JDK 21 and Android SDK platform 37 are needed for the companion. The launcher fork has its own Gradle project. Signing keys are not included. Locally signed builds cannot update the published APK in place unless the signing identity matches.

| Folder | Contents |
|---|---|
| `app`, `core`, `adb` | Companion UI, domain logic, transport and tests |
| `launcher-fork/launcher` | Redesigned 0TV Home source |
| `upstream/tv-detox` | Recovered Simple 1.5.7 source snapshot, upstream commit unknown |
| `website` | Responsive static website, motion, guides and real screenshots |
| `docs` | Setup, privacy, recovery, test scope and build instructions |

## Author and credits

**0TV design and customizations: ARNAB MANDAL**<br>
[arnabmandal.com](https://arnabmandal.com) · [arnab@ik.me](mailto:arnab@ik.me) · [github.com/vio137](https://github.com/vio137)

Built on [TidyTV](https://github.com/jolan94/tidytv), [cleanyourtv](https://github.com/Gathin23/cleanyourtv) by Gathin, and [tv-detox](https://github.com/Ansub/tv-detox) by Ansub. Their MIT copyright notices remain. Kadb is Apache-2.0; spake2-java is GPL-3.0. The combined APK is conveyed under **GPL-3.0**, and individual components retain their own licences.

[NOTICE](NOTICE) · [Third-party licences](docs/THIRD_PARTY_LICENSES.md) · [GPL-3.0](LICENSE) · [Inherited TidyTV MIT licence](docs/LICENSE-TIDYTV-MIT.txt)

Not affiliated with Google, Amazon, Netflix, YouTube or TV manufacturers. App logos shown on the TV identify installed apps; trademarks belong to their owners.

## Feedback

[Open an issue](https://github.com/vio137/0TV/issues) with the model, Android version, app version and steps to reproduce. Inspect and redact logs and screenshots before posting anything public.

### Selected-TV screen preview (rc7)

My TV uses a TV frame around its preview. An animated Android symbol appears until the first successful ADB screenshot; that still is then saved privately on the phone for the selected device and reused on later visits and app restarts. No timestamp or Refresh button is shown. The cache is not a live view, is excluded from backup and logs, and is never uploaded automatically. Failed, invalid or all-black captures keep the placeholder. Protected content may not appear. Physical-TV capture remains unverified.
