"""Visual review of sounds: waveform + log-frequency spectrogram per file, stacked into one PNG.

    tools\\.venv\\Scripts\\python.exe tools\\sound\\review.py blast/nuke_near blast/nuke_far geiger/click1
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import soundfile as sf
from PIL import Image, ImageDraw
from scipy import signal

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from gb import paths  # noqa: E402

W, WAVE_H, SPEC_H = 1000, 90, 180


def _colormap(v: np.ndarray) -> np.ndarray:
    """Black -> purple -> orange -> yellow-white."""
    stops = np.array([[0, 0, 0], [60, 10, 90], [190, 40, 60], [250, 140, 30], [255, 240, 180]], dtype=float)
    pos = np.clip(v, 0, 1) * (len(stops) - 1)
    i = np.clip(pos.astype(int), 0, len(stops) - 2)
    f = (pos - i)[..., None]
    return (stops[i] * (1 - f) + stops[i + 1] * f).astype(np.uint8)


def panel(path: Path) -> Image.Image:
    x, sr = sf.read(path)
    if x.ndim > 1:
        x = x.mean(axis=1)
    dur = len(x) / sr
    img = Image.new("RGB", (W, WAVE_H + SPEC_H + 22), (20, 22, 26))
    d = ImageDraw.Draw(img)
    peak = np.max(np.abs(x))
    rms = np.sqrt(np.mean(x ** 2))
    d.text((6, 4), f"{path.relative_to(paths.SOUNDS)}   {dur:.2f}s   peak {20 * np.log10(peak + 1e-12):.1f} dBFS   "
                   f"rms {20 * np.log10(rms + 1e-12):.1f} dBFS   {'CLIPPING' if peak >= 0.999 else ''}", fill=(220, 220, 220))
    # Waveform (min/max per column)
    cols = np.array_split(x, W)
    mid = 22 + WAVE_H // 2
    for i, c in enumerate(cols):
        if len(c):
            lo, hi = c.min(), c.max()
            d.line([(i, mid - hi * WAVE_H / 2), (i, mid - lo * WAVE_H / 2)], fill=(120, 200, 255))
    # Spectrogram (log frequency 20 Hz .. 20 kHz)
    f, t, Z = signal.stft(x, sr, nperseg=2048, noverlap=1536)
    mag = 20 * np.log10(np.abs(Z) + 1e-9)
    mag = np.clip((mag - (mag.max() - 80)) / 80, 0, 1)
    logf = np.geomspace(20, sr / 2, SPEC_H)
    rows = np.interp(logf, f, np.arange(len(f))).astype(int)
    spec = mag[rows][::-1]
    spec_img = Image.fromarray(_colormap(spec)).resize((W, SPEC_H), Image.BILINEAR)
    img.paste(spec_img, (0, 22 + WAVE_H))
    for hz in (50, 100, 500, 1000, 5000, 10000):
        y = 22 + WAVE_H + SPEC_H - int(np.searchsorted(logf, hz) * SPEC_H / len(logf))
        d.text((W - 48, y - 6), f"{hz if hz < 1000 else str(hz // 1000) + 'k'}", fill=(200, 200, 200))
        d.line([(W - 54, y), (W - 50, y)], fill=(200, 200, 200))
    return img


def main() -> None:
    names = sys.argv[1:]
    panels = [panel(paths.SOUNDS / f"{n}.ogg") for n in names]
    sheet = Image.new("RGB", (W, sum(p.height + 6 for p in panels)), (10, 10, 12))
    y = 0
    for p in panels:
        sheet.paste(p, (0, y))
        y += p.height + 6
    out = paths.PREVIEWS / "sound_review.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
