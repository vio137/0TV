#!/usr/bin/env python3
"""Check public documentation, local assets and the published APK. No dependencies."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]

def targets(text):
    return re.findall(r'(?:href|src)=[\"\']([^\"\']+)', text) + re.findall(r'!?\[[^\]\n]*\]\(([^\s)]+)\)', text)

def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': '0TV-publication-check', 'Accept': 'application/vnd.github+json'}), timeout=60)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--online', action='store_true', help='verify GitHub release metadata and download/hash the complete APK')
    args = parser.parse_args()
    errors = []
    release = json.loads((ROOT / 'release.json').read_text())
    expected = f"https://github.com/vio137/0TV/releases/download/{release['tag']}/{release['asset']}"
    if release['url'] != expected:
        errors.append('release.json: URL, tag and asset disagree')
    files = [ROOT / 'README.md', *sorted((ROOT / 'docs').glob('*.md')), *sorted((ROOT / 'website').glob('*.html'))]
    local_count = 0
    for file in files:
        text = file.read_text()
        for target in targets(text):
            # A latest release redirect cannot safely name a versioned asset.
            if '/releases/latest/download/' in target:
                errors.append(f'{file.relative_to(ROOT)}: fragile latest/download link: {target}')
            u = urllib.parse.urlsplit(target)
            if u.scheme or u.netloc or not u.path:
                continue
            path = (file.parent / urllib.parse.unquote(u.path)).resolve()
            if not path.is_relative_to(ROOT) or not path.exists():
                errors.append(f'{file.relative_to(ROOT)}: missing local target: {target}')
            else:
                local_count += 1
        if file.name == 'README.md' or file == ROOT / 'website/index.html':
            for value in [release['url'], release['version'], release['sha256'], f"{release['bytes']:,}"]:
                if value not in text:
                    errors.append(f'{file.relative_to(ROOT)}: missing current release value: {value}')
            apk_links = [t for t in targets(text) if t.endswith('.apk')]
            if not apk_links or any(t != release['url'] for t in apk_links):
                errors.append(f'{file.relative_to(ROOT)}: download buttons disagree with release.json')
    if args.online:
        with get(f"https://api.github.com/repos/vio137/0TV/releases/tags/{release['tag']}") as response:
            metadata = json.load(response)
        assets = [a for a in metadata['assets'] if a['name'] == release['asset']]
        if len(assets) != 1 or assets[0]['size'] != release['bytes'] or assets[0]['browser_download_url'] != release['url']:
            errors.append('Published release asset differs from release.json')
        with get('https://api.github.com/repos/vio137/0TV/releases/latest') as response:
            latest = json.load(response)
        if latest['tag_name'] != release['tag']:
            errors.append('A newer release exists: update release.json, README and website together')
        digest = hashlib.sha256()
        size = 0
        with get(release['url']) as response:
            while chunk := response.read(1024 * 1024):
                digest.update(chunk)
                size += len(chunk)
        if size != release['bytes'] or digest.hexdigest() != release['sha256']:
            errors.append('Downloaded APK size or SHA-256 mismatch')
        else:
            print(f'PASS: complete APK downloaded, {size:,} bytes, SHA-256 {digest.hexdigest()}')
    if errors:
        print('\n'.join('FAIL: ' + e for e in errors), file=sys.stderr)
        return 1
    print(f'PASS: {len(files)} public documents, {local_count} local links/assets, consistent release details')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
