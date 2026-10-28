# 0TV field guide

[Download the APK](https://github.com/vio137/0TV/releases/latest) · [Project website](https://vio137.github.io/0TV/) · [Recovery limits](RECOVERY.md)

0TV is an Android **phone** companion for Android TV / Google TV. The release candidate is not a promise that every TV firmware works. Read the recovery notes before making changes.

## 1. Install on your phone

1. Use an Android 8.0+ phone with ARM64 or ARMv7 support.
2. Download `0TV-1.0.0-rc4.apk` from this repository's releases.
3. Open the file in your browser or Files app. If Android asks, allow **Install unknown apps** for that app. Install 0TV, then turn that permission off again if you do not need it.
4. To update an existing 0TV install, install over it. Avoid uninstalling first: local recovery history may be lost. An update needs a compatible package identity and signing key.

This is a sideloaded APK, not a Google Play listing. Only install software you trust. If Android refuses the update, do not delete your existing app and its data just to get past the warning. Check the package, version and signer first.

## 2. Prepare the TV

- Put phone and TV on the same home network. Ethernet on the TV is fine if it shares that LAN. Guest networks and client isolation can prevent discovery or connection.
- On the TV, open Settings > About and select Build repeatedly to enable Developer options. Exact menu paths vary.
- Enable USB debugging or Wireless debugging, whichever the TV offers. Some older boxes use network ADB on port 5555; some firmware does not expose network debugging at all.
- Keep the TV remote nearby for approval. Never expose ADB to the public internet, disable authentication, or accept unfamiliar requests.

## 3. Connect and approve

1. Open 0TV > **Connect your TV**.
2. Select the discovered TV. If discovery misses it, enter the current address from the TV's network settings.
3. Approve the debugging prompt on the TV. Only approve the phone you intend to use.
4. With Android Wireless debugging, read the current pairing code and pairing port from the TV's **Pair device with pairing code** screen. The connection port may differ. Do not reuse an old code or confuse the two ports.

## 4. Review before cleanup

0TV reads the TV and saves a backup before the review. Check the selected packages and keep apps you use. Normal cleanup switches selected apps off with Android's disable-user mechanism. Protected system packages and Home handover have separate guards.

Confirm only changes you understand. Per-model upstream lists are inputs, not a certification of your particular firmware. Separate removal tools are more destructive than disabling. Do not infer that all removed data can come back.

If the connection drops, reconnect to the same TV, inspect the recorded progress and use restore where appropriate. Do not repeat a destructive action blindly.

## 5. Choose a Home

Choose **0TV Home** inside the app to install the redesigned launcher shown on the website. The phone app handles its bundled APK and checks Home handover. Follow TV-side prompts.

- **0TV Home**: redesigned fork, package `app.tvtidy.home`.
- **Original Dock-style Home**: upstream cleanyourtv launcher, a different choice.
- **Simple**: separate tv-detox launcher, not the redesigned 0TV Home.
- Any Play Store-based launcher flow depends on the TV's store and its availability. 0TV does not host third-party proprietary launcher APKs.

Use the TV remote to move focus and press OK. App banners depend on the apps actually installed. Multiple installed launchers may both be named Home. A TV reporting an invalid date can have the clock/header suppressed; check the TV's time settings rather than assuming the layout is broken.

## 6. Everyday tools

- **Remote**: D-pad and supported system keys. Firmware may ignore some commands.
- **Text input**: the basic input path is limited to supported characters. It is not a universal Unicode keyboard.
- **Apps**: launch installed TV apps from the companion.
- **Send files**: transfer chosen files to the TV. Check the destination and result; do not assume an APK is installed merely because it was sent.
- **Check my TV**: compare recorded cleanup with current state, including apps a firmware update turned back on.
- **Storage/cache tools**: read the confirmation. Clearing cache is different from clearing app data or removing an app. Do not promise a speedup or a fixed amount of recovered space.

## 7. Optional DNS filtering

The **Block ads** option uses Android Private DNS / DNS-over-TLS on compatible Android 9+ TVs. It is separate from normal cleanup.

- Standard: `dns.adguard-dns.com`.
- Family filter: `family.adguard-dns.com`.
- The TV's DNS lookups go to AdGuard, a third party, after enabling it.
- DNS filtering does not remove YouTube in-video ads and may interfere with apps or connectivity.
- Turn it off through the app to restore the saved previous setting, subject to intact records and matching-TV checks. If the TV is unreachable, use its network settings and manufacturer guidance.

## 8. Optional TizenTube Cobalt

This is a separate, optional install from the TizenTubeCobalt GitHub releases. The app checks the download's published digest, package and signing identity before sending it to the TV. Compatibility is not universal. It is not needed for normal cleanup or the launcher.

**Restore does not uninstall or roll back TizenTube/Cobalt.** Manage it separately in the TV's app settings. 0TV is not affiliated with YouTube or the upstream project.

## 9. App2TV: send an app from your phone to the TV

**Purpose.** Many apps are not in the Android TV app store. If the app is on your phone, App2TV can copy it to your TV and install it there.

**How it works.**
1. Install the app on your phone first. It has to be on the phone.
2. Open My TV, then App2TV. Every app on the phone is listed, apps you installed first, then system apps. Search or pick one.
3. 0TV asks the TV if it can take the app: Android version, 32-bit or 64-bit, free space, and whether the TV already has a newer copy. If not, it says why in plain English and sends nothing.
4. 0TV reads the app's files from the phone (the main file plus any extra parts), copies them to the TV over the existing connection, and the TV's own installer installs them. Then 0TV checks the app is really on the TV and removes the temporary files.
5. If the install fails, 0TV reads the TV's answer and explains it, for example "the TV ran out of space" or "the app is built for a newer Android than this TV".

**Limits.** The app keeps its own signature, and 0TV never uninstalls, wipes data, downgrades or bypasses a signature check. System apps are only replaced if the TV allows it, and an app that is part of the TV's own system is refused. Many phone apps need a touch screen and may not work with a remote, even if they install. Apps built only for 64-bit phones cannot run on a 32-bit TV.

## Easter egg

On the TV's 0TV Home screen, press Up, Up, Down, Down, Left, Right, Left, Right on the remote. The screen looks cracked. Press the same sequence again to undo it. It only works in 0TV Home.

## 10. Restore and recovery

Use **Restore a TV** / **Restore Everything** after reconnecting to the same TV. Keep the phone app and backup records. See [RECOVERY.md](RECOVERY.md) for the conditions and limits. Do not rely on the phrase “Everything” as a guarantee of complete reversal.

## Troubleshooting

| Symptom | Check |
|---|---|
| No TV appears | Same LAN, no guest isolation, debugging enabled; try the current address manually. |
| No approval prompt | Check debugging settings on the TV. Toggle that setting off/on if appropriate and reconnect. Do not disable authentication. |
| Pairing fails | Read the fresh code and pairing port. The connection port is a separate value. |
| TV refuses an action | Stop and inspect the error. Firmware, user/profile and protected-package checks can refuse changes. |
| Keys do nothing | Reconnect and try supported commands. Some firmware ignores keys. Keep the physical remote. |
| Blank Home | Use the physical Home/Settings key if available, select the prior launcher or use rollback. See recovery notes. |
| Clock absent | Check whether the TV reports a valid date. The launcher hides invalid-time header elements. |
| Restore refuses | Wrong Android user, changed Android ID, missing or corrupt records can fail closed. Do not bypass the device check. |

## Report a problem

[Open an issue](https://github.com/vio137/0TV/issues) with TV model, Android version, app version, steps, expected result and actual result. Feedback in this build creates a file for Android's share sheet; it does not upload automatically. Inspect logs and screenshots before posting them publicly. Remove network names, addresses, account details and anything personal.

## Selected-TV screen preview

Open My TV after connecting. An animated Android symbol is shown inside a TV frame until the first usable ADB screenshot arrives. That screenshot replaces the symbol and is saved privately on the phone for that device. Later visits and app restarts reuse it without a fresh capture. It is a saved still, not a live view; there is no timestamp or Refresh button. Failed, invalid or all-black frames leave the placeholder. Protected video may be missing. Switching TV cannot show the other device's capture. The cache is excluded from Android backup and logs and is never uploaded automatically. Temporary TV capture files are removed after reading, best effort if the connection drops. Android's Remove animations setting keeps the placeholder static. Physical-TV capture is not yet verified.
