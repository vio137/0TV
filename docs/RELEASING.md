# Keep downloads working

The direct APK URL must name **both its release tag and its asset**. Do not use `releases/latest/download/` with a versioned filename: when a new release becomes latest, GitHub looks for the old filename in the new release and returns 404.

## When publishing a new APK

1. Build, sign and test the app. Preserve the signing identity for in-place updates. Keep signing keys and passwords out of Git.
2. Upload the versioned APK to its own GitHub release. Download it back and verify the complete file's SHA-256 and byte count.
3. Update `release.json`: version, version code, tag, asset, URL, bytes and SHA-256.
4. Update the README and both website download buttons, visible version/size and checksum. Pin all direct links to that same release tag. Update the website release-notes link too.
5. Update the guide and test notes for changed features. Do not carry a fixed old APK filename into the guide.
6. Run:

   ```sh
   python3 tools/check-publication.py --online
   ```

7. Open the real GitHub README on desktop and mobile. Click the APK button, check images, plain-link fallback and setup links. Check the live Pages site after deployment.

The publication workflow runs on relevant pushes, pull requests and published/edited releases. It checks local document/asset paths, consistent release details, the published release's metadata, whether a newer latest release exists, and the entire downloaded APK's size and SHA-256. It does not replace app tests, a visual review or physical-TV testing.

The README's plain download link works without the button image. The local SVG button avoids a dependency on a third-party badge service. A pinned old download stays usable when a newer release appears; the workflow flags that the visible release should be refreshed.
