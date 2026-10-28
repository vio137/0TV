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

## spake2-java 1.1.1
The release tag 1.1.1 is commit 809ad7aa731f475c07763b1ad2c165ae192dfb5f.
Its release licence is GPL-3.0; a stale master branch's LGPL text does not apply to this release.
Source: https://github.com/MuntashirAkon/spake2-java/tree/809ad7aa731f475c07763b1ad2c165ae192dfb5f
The source headers name Muntashir Al-Islam and Flyfish233. The combined phone APK is conveyed under GPL-3.0.

## Source and notices
See BUILDING.md for the source layout and pinned inputs. The source repository includes the companion,
0TV Home fork and recovered tv-detox source. Separate launcher APKs retain their MIT notices.
Signing identities are not included; a local build is not promised to match the published APK byte for byte.
Root LICENSE contains GPL-3.0. The inherited TidyTV MIT text remains in LICENSE-TIDYTV-MIT.txt.
Other projects retain their own licence terms and copyright notices.
