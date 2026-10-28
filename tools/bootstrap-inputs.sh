#!/bin/sh
# Fetches the pinned upstream inputs the source archive leaves out. See docs/REPRODUCIBLE.md.
set -e
cd "$(dirname "$0")/.."
mkdir -p upstream
[ -d upstream/cleanyourtv/.git ] || git clone https://github.com/Gathin23/cleanyourtv.git upstream/cleanyourtv
git -C upstream/cleanyourtv checkout -q 095b71ad93d99ea28d94d97b06385709d354b795   # lists and themes
# launcher source for tools/build-styled-home.sh: tag v3.0.0 = 67dc5bee169038ed065a48ef837cf7fed5cf40f4
git -C upstream/cleanyourtv rev-parse v3.0.0^{commit} | grep -q 67dc5bee169038ed065a48ef837cf7fed5cf40f4
echo "inputs ready; build: ./gradlew :app:assembleRelease (set sdk.dir in local.properties)"
