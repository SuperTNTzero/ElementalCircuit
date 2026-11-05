"""Offline source-based sound editing. Run with --tooling build/audio-tooling.
Requires numpy, scipy, soundfile/Vorbis. Inputs, SHA-256 and licenses are pinned
in art/audio/recipes.json; no network, synthesizer or encoder in the mod runtime.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--tooling', type=Path)
args = parser.parse_args()
if args.tooling:
    sys.path.insert(0, str(args.tooling.resolve()))
import numpy as np
import soundfile as sf
from scipy.signal import butter, sosfilt, resample_poly, resample

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / 'art/audio'
ASSETS = ROOT / 'src/main/resources/assets/elementalcircuits'
CONFIG = json.loads((ART / 'recipes.json').read_text(encoding='utf-8'))
assert CONFIG['version'] == 2
RATE = CONFIG['sample_rate']
OUT = ASSETS / 'sounds/spell'
OUT.mkdir(parents=True, exist_ok=True)
cache, used = {}, set()


def source(key):
    if key in cache:
        return cache[key].copy()
    spec = CONFIG['sources'][key]
    path = ART / 'sources' / spec['file']
    assert hashlib.sha256(path.read_bytes()).hexdigest() == spec['sha256'], path
    wave, rate = sf.read(path, dtype='float64', always_2d=True)
    assert np.isfinite(wave).all() and rate >= 22050
    mono = np.mean(wave, axis=1)
    channel_rms = np.sqrt(np.mean(wave ** 2, axis=0))
    if np.sqrt(np.mean(mono ** 2)) < .45 * max(channel_rms):
        mono = wave[:, int(np.argmax(channel_rms))]
    mono = resample_poly(mono, RATE, rate)
    mono = sosfilt(butter(2, 45, btype='highpass', fs=RATE, output='sos'), mono)
    peak = np.max(np.abs(mono))
    assert peak > .0001
    audible = np.flatnonzero(np.abs(mono) >= peak * .012)
    mono = mono[max(0, audible[0] - round(.002 * RATE)):audible[-1] + 1]
    mono /= max(np.sqrt(np.mean(mono ** 2)), .001)
    cache[key] = mono
    return mono.copy()


def layer(spec, samples):
    key = spec['source']
    used.add(key)
    wave = source(key)
    if spec.get('reverse', False):
        wave = wave[::-1].copy()
    pitch = 2 ** (spec.get('semitones', 0) / 12)
    wave = resample(wave, max(32, round(len(wave) / pitch)))
    wave = sosfilt(butter(2, spec['lowpass_hz'], fs=RATE, output='sos'), wave)
    offset = round(spec.get('delay', 0) * RATE)
    wave = wave[:max(0, samples - offset)]
    if len(wave) == 0:
        return np.zeros(samples)
    t = np.arange(len(wave)) / RATE
    wave *= np.minimum(1, t / .0025)
    wave *= np.minimum(1, (len(wave) - np.arange(len(wave))) / (.055 * RATE))
    wave *= np.exp(-t * spec.get('decay', 1.5))
    result = np.zeros(samples)
    result[offset:offset + len(wave)] = wave * spec['gain']
    return result


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


metrics, sounds, decoded_clips = [], {}, {}
for event, recipe in CONFIG['events'].items():
    clips = []
    for variant, layers in enumerate(recipe['variants']):
        name = event + ('' if variant == 0 else '_' + str(variant + 1))
        count = round(RATE * recipe['seconds'])
        wave = sum((layer(spec, count) for spec in layers), np.zeros(count))
        dry = wave.copy()
        for delay, gain in ((.037, .09), (.071, .045)):
            n = round(delay * RATE)
            wave[n:] += dry[:-n] * gain
        wave = np.tanh(wave * .35)
        wave *= min(recipe['rms'] / max(.001, np.sqrt(np.mean(wave ** 2))),
                    .73 / max(.001, np.max(np.abs(resample_poly(wave, 4, 1)))))
        wave[-round(.035 * RATE):] *= np.linspace(1, 0, round(.035 * RATE))
        file = OUT / (name + '.ogg')
        sf.write(file, wave, RATE, format='OGG', subtype='VORBIS')
        decoded, rate = sf.read(file)
        true_peak = float(np.max(np.abs(resample_poly(decoded, 4, 1))))
        # Lossy encoding can overshoot between samples: measure decoded PCM too.
        if true_peak > .82:
            wave *= .79 / true_peak
            sf.write(file, wave, RATE, format='OGG', subtype='VORBIS')
            decoded, rate = sf.read(file)
            true_peak = float(np.max(np.abs(resample_poly(decoded, 4, 1))))
        rms = float(np.sqrt(np.mean(decoded ** 2)))
        onset = int(np.flatnonzero(np.abs(decoded) > np.max(np.abs(decoded)) * .05)[0]) / RATE
        assert rate == RATE and decoded.ndim == 1 and np.isfinite(decoded).all()
        assert true_peak < .90 and .025 < rms < .21 and onset < .025, (name, true_peak, rms, onset)
        assert file.stat().st_size < 64000
        decoded_clips[name] = decoded
        clips.append({'name': 'elementalcircuits:spell/' + name, 'attenuation_distance': 24,
                      'preload': True, 'weight': 1})
        metrics.append({'name': name, 'event': event, 'seconds': len(decoded) / RATE,
                        'channels': 1, 'sample_rate': RATE, 'bytes': file.stat().st_size,
                        'sha256': sha(file), 'true_peak_4x': round(true_peak, 6),
                        'rms_dbfs': round(20 * np.log10(rms), 2), 'onset_ms': round(onset * 1000, 2),
                        'sources': [spec['source'] for spec in layers],
                        'license': 'CC-BY-3.0' if any(CONFIG['packs'][CONFIG['sources'][s['source']]['pack']]['license'] == 'CC-BY-3.0' for s in layers) else 'CC0-1.0'})
    sounds[event] = {'subtitle': 'subtitles.elementalcircuits.' + recipe['kind'], 'sounds': clips}

assert len(sounds) == 28 and len(metrics) == 74
assert {p.stem for p in OUT.glob('*.ogg')} == set(decoded_clips), 'Unexpected old audio in runtime directory'
(ASSETS / 'sounds.json').write_text(json.dumps(sounds, indent=2) + '\n', encoding='utf-8')
provenance = {'version': 2, 'sample_rate': RATE, 'sources': {k: CONFIG['sources'][k] for k in sorted(used)},
              'packs': CONFIG['packs'], 'assets': metrics,
              'modifications': 'Stereo to mono, onset/tail trimming, pitch/EQ edits, layered mixing, '
                               'short reflections, dynamics control, fade and OGG/Vorbis encoding.'}
(ASSETS / 'audio/sources.json').write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
report = {'version': 2, 'events': len(sounds), 'variants': len(metrics), 'sample_rate': RATE,
          'source_files': len(used), 'total_bytes': sum(m['bytes'] for m in metrics), 'assets': metrics}
(ROOT / 'docs/metrics/casting-audio-assets.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')

preview = ART / 'previews'
preview.mkdir(parents=True, exist_ok=True)
reel, chapters, cursor = [], [], 0
silence = np.zeros(round(RATE * .24))
for element in CONFIG['elements']:
    chapters.append({'element': element, 'start_seconds': round(cursor / RATE, 2), 'order': ['cast', 'impact', 'shield']})
    for kind in ('cast', 'impact', 'shield'):
        wave = decoded_clips[kind + '_' + element]
        reel.extend((wave, silence)); cursor += len(wave) + len(silence)
sf.write(preview / 'element-showcase.wav', np.concatenate(reel), RATE, subtype='PCM_16')
(preview / 'chapters.json').write_text(json.dumps(chapters, indent=2) + '\n', encoding='utf-8')
ab = []
for element in ('fire', 'water', 'lightning', 'shadow'):
    for folder in (ART / 'reference/0.22.0', OUT):
        for kind in ('cast', 'impact'):
            wave, rate = sf.read(folder / (kind + '_' + element + '.ogg'))
            ab.extend((resample_poly(wave, RATE, rate), silence))
    ab.append(np.zeros(round(.5 * RATE)))
sf.write(preview / 'before-after.wav', np.concatenate(ab), RATE, subtype='PCM_16')
print('AUDIO_ASSETS_PASS:', len(metrics), 'mono 44100 Hz source-based Vorbis variants;', report['total_bytes'], 'bytes;', len(used), 'verified source files')
