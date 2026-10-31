"""Offline original synthetic sound assets. Requires numpy, scipy, soundfile (Vorbis).
Optional --tooling build/audio-tooling loads a project-local soundfile installation.
No synthesis or encoder is shipped in the mod runtime.
"""
import argparse
import json
from pathlib import Path
import sys

parser = argparse.ArgumentParser()
parser.add_argument('--tooling', type=Path)
args = parser.parse_args()
if args.tooling:
    sys.path.insert(0, str(args.tooling.resolve()))
import numpy as np
import soundfile as sf
from scipy.signal import butter, sosfilt

root = Path(__file__).resolve().parents[1]
assets = root / 'src/main/resources/assets/elementalcircuits'
palette = json.loads((assets / 'audio/palette.json').read_text(encoding='utf-8'))
assert palette['version'] == 1
rate = palette['sample_rate']
output = assets / 'sounds/spell'
output.mkdir(parents=True, exist_ok=True)
sounds = {}
metrics = []


def bake(name, kind, element, voice, index):
    length = palette['durations'][kind]
    t = np.arange(round(rate * length)) / rate
    rng = np.random.default_rng(palette['seed'] + index)
    f = voice['frequency']
    sweep = {'start': 0.9, 'cast': -0.45, 'impact': -0.75, 'shield': 0.15}[kind]
    phase = 2 * np.pi * f * (t + sweep * t * t / (2 * length))
    tone = np.sin(phase) + 0.3 * np.sin(phase * 2.01) + 0.12 * np.sin(phase * 3.99)
    noise = sosfilt(butter(2, min(6500, f * 5 + 500), fs=rate, output='sos'), rng.normal(0, 1, len(t)))
    if element == 'water':
        tone = np.sin(phase + 5 * np.sin(2 * np.pi * 19 * t)) * (0.6 + 0.4 * np.sin(2 * np.pi * 32 * t) ** 2)
    elif element == 'fire':
        noise *= 0.5 + 2 * (rng.random(len(t)) > 0.98)
    elif element == 'lightning':
        noise *= (0.25 + 0.75 * np.sin(2 * np.pi * 53 * t) ** 8)
    elif element == 'light':
        tone = np.sin(phase) + 0.45 * np.sin(phase * 1.5) + 0.2 * np.sin(phase * 2.5)
    elif element == 'shadow':
        tone *= np.sin(2 * np.pi * 17 * t)
        noise *= np.minimum(1, t / length * 3)
    elif element == 'arcane':
        tone = np.sin(phase + 2 * np.sin(phase * 0.503))
    elif element == 'earth':
        tone += 0.5 * np.sin(phase * 0.5)
    attack = 0.018 if kind in ('start', 'shield') else 0.006
    envelope = np.minimum(1, t / attack) * np.exp(-t / length * (2.6 if kind == 'shield' else 4.5))
    envelope *= np.minimum(1, (length - t) / 0.035)
    wave = ((1 - voice['noise']) * tone + voice['noise'] * noise) * envelope
    wave *= 0.65 / max(0.001, np.max(np.abs(wave)))
    file = output / (name + '.ogg')
    sf.write(file, wave, rate, format='OGG', subtype='VORBIS')
    decoded, decoded_rate = sf.read(file)
    assert decoded_rate == rate and len(decoded) == len(wave) and np.isfinite(decoded).all()
    assert np.max(np.abs(decoded)) < 0.95 and np.sqrt(np.mean(decoded**2)) > 0.015
    sounds[name] = {'subtitle': 'subtitles.elementalcircuits.' + kind,
                    'sounds': [{'name': 'elementalcircuits:spell/' + name, 'attenuation_distance': 24, 'preload': True}]}
    metrics.append({'name': name, 'seconds': length, 'channels': 1, 'sample_rate': rate,
                    'bytes': file.stat().st_size, 'peak': round(float(np.max(np.abs(decoded))), 5)})


bake('circuit_start', 'start', 'pure', palette['elements']['pure'], 0)
for k, kind in enumerate(('cast', 'impact', 'shield')):
    for i, (element, voice) in enumerate(palette['elements'].items()):
        bake(kind + '_' + element, kind, element, voice, 1 + k * 9 + i)
(assets / 'sounds.json').write_text(json.dumps(sounds, indent=2) + '\n', encoding='utf-8')
for locale in ('zh_cn', 'en_us'):
    file = assets / 'lang' / (locale + '.json')
    data = json.loads(file.read_text(encoding='utf-8'))
    labels = ('法阵起动', '法术释放', '法术命中', '屏障展开') if locale == 'zh_cn' else ('Circuit starts', 'Spell casts', 'Spell hits', 'Shield unfolds')
    for kind, text in zip(('start', 'cast', 'impact', 'shield'), labels):
        data['subtitles.elementalcircuits.' + kind] = text
    file.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
report = root / 'docs/metrics/casting-audio-assets.json'
report.parent.mkdir(parents=True, exist_ok=True)
report.write_text(json.dumps({'version': 1, 'assets': metrics, 'total_bytes': sum(m['bytes'] for m in metrics)}, indent=2) + '\n', encoding='utf-8')
print('AUDIO_ASSETS_PASS:', len(metrics), 'mono OGG/Vorbis files;', sum(m['bytes'] for m in metrics), 'bytes')
