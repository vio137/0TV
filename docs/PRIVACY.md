# Privacy and network use

This 0TV build has no analytics, crash reporter, automatic update check, rating prompt or automatic feedback upload. No app account is required.

| Feature | Destination | When |
|---|---|---|
| ADB connection | The TV's LAN address and debugging port | When you connect and use TV tools |
| Optional Cobalt download | GitHub API and TizenTubeCobalt release assets | When you choose the separate install |
| Optional DNS filtering | AdGuard DNS standard or family hostname | After you explicitly enable it on a compatible TV |
| Feedback/log sharing | Android share sheet destination you choose | Only when you choose to share |

INTERNET permission is needed for local ADB and the optional release download. Local network access is not the same as “no networking”. DNS filtering sends the TV's DNS queries to a third party and can affect other TV apps.

The app records recovery state on the phone and TV. Device-bound checks use Android user 0 and an Android-ID hash. A hash does not make every device identifier perfectly anonymous or hardware-unique.

Log redaction attempts to remove network names, IP/MAC addresses and serials. Inspect exported logs yourself before posting them. Do not assume redaction catches every private value.

The website is a static GitHub Pages site. It loads its font, images and scripts locally and has no analytics, tracking pixels, cookies or third-party JavaScript. GitHub hosts the page and downloads and can receive normal web request metadata. Optional app services have their own privacy terms.

Signing keys, device/network addresses, private test logs and connection credentials are not distributed with this repository. TV screenshots mask the network name. Phone images are rendered UI previews rather than physical-phone photos.
