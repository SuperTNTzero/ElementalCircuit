"""Download only explicitly reviewed CC0 art archives; never execute archive contents."""
from pathlib import Path
from urllib.request import Request, urlopen
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[2]
DEST = ROOT / 'art/vfx/sources'
SOURCES = [
    ('nyrthos', 'Mikodrak', '2D Spell Effects', 'https://opengameart.org/content/2d-spell-effects', 'https://opengameart.org/sites/default/files/FX.zip'),
    ('pure', 'Cethiel', 'Pure Projectile - Magic Effect', 'https://opengameart.org/content/pure-projectile-magic-effect', 'https://opengameart.org/sites/default/files/Pure%20Projectile%20Effect.zip'),
    ('water', 'Cethiel', 'Water Magic Effect', 'https://opengameart.org/content/water-magic-effect', 'https://opengameart.org/sites/default/files/Water_Effect.zip'),
    ('fire', 'Cethiel', 'Fire Wrath - Magic Effect', 'https://opengameart.org/content/fire-wrath-magic-effect', 'https://opengameart.org/sites/default/files/Fire%20Wrath%20-%20Magic%20Effect.zip'),
    ('cosmic', 'Cethiel', 'Cosmic Time - Magic Effect', 'https://opengameart.org/content/cosmic-time-magic-effect', 'https://opengameart.org/sites/default/files/Cosmic%20Time%20-%20Magic%20Effect.zip'),
    ('circles', 'Luke.RUSTLTD', '4 summoning circles', 'https://opengameart.org/content/4-summoning-circles', 'https://opengameart.org/sites/default/files/summoning_circles.zip'),
]

def download(url):
    with urlopen(Request(url, headers={'User-Agent': 'Mozilla/5.0 (Elemental Circuits asset provenance)'}), timeout=45) as stream:
        data = stream.read(20_000_001)
        if len(data) > 20_000_000:
            raise ValueError('Asset exceeds reviewed size budget')
        return data

def main():
    DEST.mkdir(parents=True, exist_ok=True)
    records = []
    for key, author, title, page, url in SOURCES:
        target = DEST / (key + '.zip')
        if not target.exists():
            target.write_bytes(download(url))
        with zipfile.ZipFile(target) as archive:
            files = archive.namelist()
            print(key, len(files), files[:12], flush=True)
        evidence = DEST / (key + '-license.html')
        if not evidence.exists():
            evidence.write_bytes(download(page))
        records.append(dict(id=key, author=author, title=title, page=page, download=url, license='CC0-1.0',
            licenseUrl='https://creativecommons.org/publicdomain/zero/1.0/', sha256=hashlib.sha256(target.read_bytes()).hexdigest()))
    (DEST.parent / 'sources.json').write_text(json.dumps(dict(version=1, checked='2026-11-01', sources=records), ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

if __name__ == '__main__':
    main()
