# Third-party licences in the TV Tidy APK

Licence names are taken from each library's published Maven POM (read from the Gradle cache
on 28 Oct 2026). Full licence texts are in the upstream projects and the library jars; this
file lists what ships and what it obliges.

| Component | Version | Licence per POM |
| --- | --- | --- |
| AndroidX, Kotlin, Compose | per Gradle lock | Apache-2.0 |
| Kadb (kadb, kadb-android) | 2.1.5 | Apache-2.0 |
| spake2-java | 1.1.1 | **GPL-3.0** (POM and the GitHub repo page). The repo's master LICENSE file text is LGPL-3.0, so the upstream licensing is inconsistent |
| curve25519-elisabeth, ed25519-elisabeth | 0.1.0 | MIT |
| Bouncy Castle (bcprov, bcpkix, bcutil) | 1.86 | Bouncy Castle Licence (MIT-style) |
| Conscrypt (android) | 2.8.0 | Apache-2.0 |
| Okio | 3.18.2 | Apache-2.0 |
| jspecify, JetBrains annotations | 1.0.0, 13.0/23.0.0 | Apache-2.0 |
| Guava listenablefuture | 1.0 | no licence in POM (Guava is Apache-2.0) |
| cleanyourtv, tv-detox, TidyTV | see NOTICE | MIT |
| Simple Icons | see NOTICE | CC0-1.0 |
| Nunito font | see NOTICE | SIL OFL 1.1 |

## spake2-java: resolved against the exact artifact
- Release tag 1.1.1 = commit 809ad7aa731f475c07763b1ad2c165ae192dfb5f. Its LICENSE file is the
  GNU GPL v3 text, the published POM says GPL-3.0, and the repo default branch (main) has the same.
  An LGPL-3.0 LICENSE text exists only on a stale `master` branch; it does not apply to 1.1.1.
- Source headers say "Copyright (C) 2021 Muntashir Al-Islam, Modified 2025 Flyfish233,
  licensed according to the LICENSE file", so the code derives from GPL-licensed work.
- The 1.1.1 jar carries no licence file of its own (META-INF holds only the manifest).

## Packaging decision (not legal advice; some ambiguity remains)
The APK combines MIT, Apache-2.0, MIT-style, CC0, OFL and GPL-3.0 components. These are
compatible in one direction: the combined binary is conveyed under GPL-3.0. So:
- LICENSE-GPL-3.0.txt is included in the source, and NOTICE states that the APK as a whole is
  conveyed under GPL-3.0 while each component keeps its own notice.
- The complete corresponding source is: the source archive plus the pinned inputs in
  REPRODUCIBLE.md plus the dependencies at the versions listed above, all of which are public.
- No further restrictions are added. Any recipient may get the source on request.
- Not resolved: the bundled tvhome.apk and cleanyourtv-home.apk are separate MIT programs
  (separate APKs, installed as separate apps), not linked into the phone app. The author-signed
  upstream binaries cannot be reproduced bit for bit without their key.
- Public hosting was not authorised, so the artifacts stay private; GPL obligations apply
  when the APK is given to someone, together with the source.
