#!/usr/bin/env python3
"""Bundle the brand icons tidytv shows next to app names.

Reads every slug from AppLabels.kt, downloads the matching Simple Icons SVG (CC0 artwork) and
brand colour from jsDelivr, and writes app/src/main/assets/icons.json:
    {"netflix": {"path": "<svg path data, 24x24 viewBox>", "hex": "E50914"}, ...}
Slugs Simple Icons doesn't have are skipped; the app shows initials for those.

Usage: python3 -I tools/gen-icons.py
"""
import json
import re
import sys
import urllib.request
from pathlib import Path

VERSION = "16.35.0"
ROOT = Path(__file__).resolve().parents[1]
LABELS = ROOT / "core/src/main/kotlin/dev/tidytv/core/lists/AppLabels.kt"
OUT = ROOT / "app/src/main/assets/icons.json"
CDN = f"https://cdn.jsdelivr.net/npm/simple-icons@{VERSION}"


def fetch(url):
    with urllib.request.urlopen(url, timeout=30) as r:
        return r.read().decode("utf-8")


def main():
    src = LABELS.read_text()
    slugs = sorted(set(re.findall(r'Known\("[^"]*", "([a-z0-9]+)"\)', src)) |
                   set(re.findall(r'to "([a-z0-9]+)"', src)))
    data = json.loads(fetch(f"{CDN}/data/simple-icons.json"))
    entries = data if isinstance(data, list) else data.get("icons", [])
    hexes = {}
    for e in entries:
        slug = e.get("slug") or re.sub(r"[^a-z0-9]", "", e["title"].lower())
        hexes[slug] = e["hex"]
    out = {}
    for slug in slugs:
        try:
            svg = fetch(f"{CDN}/icons/{slug}.svg")
        except Exception as ex:  # not in Simple Icons: initials in the app
            print(f"skip {slug}: {ex}", file=sys.stderr)
            continue
        m = re.search(r'<path d="([^"]+)"', svg)
        if not m:
            print(f"skip {slug}: no path", file=sys.stderr)
            continue
        out[slug] = {"path": m.group(1), "hex": hexes.get(slug, "1B1B3A")}
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, separators=(",", ":"), sort_keys=True))
    print(f"wrote {len(out)} of {len(slugs)} icons to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
