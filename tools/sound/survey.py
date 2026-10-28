"""Looks at recordings before they are used (the sound remaster, DESIGN.md §13.4): for each, its length, rate and channels,
how loud it peaks, how much of it is clipped (flat-topped at full scale, as a camera mic overloaded by a blast is), the
loudest bangs in it (onsets: where, how loud, how sharp), and a sheet of waveform over spectrogram with a time scale, so
the parts worth cutting can be found by eye (a way to read a sound without hearing it).

    tools\\.venv\\Scripts\\python.exe tools\\sound\\survey.py tnt_landmines mortar_line ...      (keys from sources/ledger.json)
    tools\\.venv\\Scripts\\python.exe tools\\sound\\survey.py --file some/path.ogg

Writes tools/out/previews/survey_<name>.png (one sheet per call: every file listed, stacked).
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np
import soundfile as sf
from PIL import Image, ImageDraw
from scipy import signal

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from gb import paths  # noqa: E402

HERE = Path(__file__).resolve().parent
RAW = HERE / "sources" / "raw"
W, WAVE_H, SPEC_H, AXIS_H, HEAD_H = 1400, 90, 200, 16, 34


def colormap(v: np.ndarray) -> np.ndarray:
    stops = np.array([[0, 0, 0], [60, 10, 90], [190, 40, 60], [250, 140, 30], [255, 240, 180]], dtype=float)
    pos = np.clip(v, 0, 1) * (len(stops) - 1)
    i = np.clip(pos.astype(int), 0, len(stops) - 2)
    f = (pos - i)[..., None]
    return (stops[i] * (1 - f) + stops[i + 1] * f).astype(np.uint8)


WINDOWS: dict[Path, tuple[float, float]] = {}


def load(path: Path) -> tuple[np.ndarray, int]:
    x, sr = sf.read(Path(str(path).split("#")[0]), always_2d=True)
    x = x.astype(np.float64)
    if path in WINDOWS:                                       # key@from-to: just that stretch
        a, b = WINDOWS[path]
        x = x[int(a * sr):int(b * sr)]
    return x, sr


def clipped(x: np.ndarray) -> float:
    """The share of samples sitting flat at their channel's peak (runs of 3 or more): overload."""
    total = 0
    for c in range(x.shape[1]):
        ch = x[:, c]
        peak = np.max(np.abs(ch))
        if peak <= 0:
            continue
        hot = np.abs(ch) >= peak * 0.995
        runs = np.convolve(hot.astype(int), np.ones(3, dtype=int), mode="same") >= 3
        total += int(np.count_nonzero(runs & hot))
    return total / x.size


def onsets(mono: np.ndarray, sr: int, count: int = 8) -> list[tuple[float, float, float]]:
    """The loudest bangs: (time s, peak dBFS, rise ms) from a 5 ms envelope, at least 0.4 s apart."""
    hop = max(1, int(0.005 * sr))
    env = np.array([np.max(np.abs(mono[i:i + hop])) for i in range(0, len(mono) - hop, hop)])
    if len(env) < 3:
        return []
    smooth = np.convolve(env, np.ones(3) / 3, mode="same")
    rise = np.maximum(0, np.diff(smooth, prepend=smooth[0]))
    order = np.argsort(-(rise * env))
    picked: list[int] = []
    for i in order:
        if len(picked) >= count:
            break
        if env[i] < 0.02:
            continue
        if all(abs(i - j) * hop / sr >= 0.4 for j in picked):
            picked.append(int(i))
    out = []
    for i in sorted(picked):
        a = max(0, i - 20)
        b = min(len(env) - 1, i + 40)
        top = a + int(np.argmax(env[a:b + 1]))
        base = env[a:top + 1]
        start = a + int(np.argmax(base >= 0.1 * env[top])) if len(base) else top
        out.append((top * hop / sr, 20 * np.log10(env[top] + 1e-12), (top - start) * hop / sr * 1000))
    return out


def panel(name: str, path: Path, marks: list[tuple[float, float, float]]) -> Image.Image:
    x, sr = load(path)
    mono = x.mean(axis=1)
    dur = len(mono) / sr
    img = Image.new("RGB", (W, HEAD_H + WAVE_H + SPEC_H + AXIS_H), (20, 22, 26))
    d = ImageDraw.Draw(img)
    peak = np.max(np.abs(x))
    clip = clipped(x)
    d.text((6, 3), f"{name}   {dur:.2f} s   {sr} Hz   {x.shape[1]} ch   peak {20 * np.log10(peak + 1e-12):.1f} dBFS   "
                   f"clipped {clip * 100:.2f}%", fill=(230, 230, 230))
    d.text((6, 18), "bangs: " + "   ".join(f"{t:.2f}s {p:.0f}dB" for t, p, _ in marks), fill=(170, 200, 230))
    top = HEAD_H
    cols = np.array_split(mono, W)
    mid = top + WAVE_H // 2
    for i, c in enumerate(cols):
        if len(c):
            lo, hi = c.min(), c.max()
            d.line([(i, mid - hi * WAVE_H / 2), (i, mid - lo * WAVE_H / 2)], fill=(120, 200, 255))
    f, t, Z = signal.stft(mono, sr, nperseg=2048, noverlap=1536)
    mag = 20 * np.log10(np.abs(Z) + 1e-9)
    mag = np.clip((mag - (mag.max() - 80)) / 80, 0, 1)
    logf = np.geomspace(20, sr / 2, SPEC_H)
    rows = np.interp(logf, f, np.arange(len(f))).astype(int)
    spec = mag[rows][::-1]
    img.paste(Image.fromarray(colormap(spec)).resize((W, SPEC_H), Image.BILINEAR), (0, top + WAVE_H))
    for hz in (50, 100, 500, 1000, 5000, 10000):
        y = top + WAVE_H + SPEC_H - int(np.searchsorted(logf, hz) * SPEC_H / len(logf))
        d.text((W - 40, y - 6), f"{hz if hz < 1000 else str(hz // 1000) + 'k'}", fill=(220, 220, 220))
    # Time scale: a tick every second (every 10 s past a minute), labelled.
    step = 1 if dur <= 20 else 5 if dur <= 90 else 10
    y0 = top + WAVE_H + SPEC_H
    for s in np.arange(0, dur + 1e-9, step):
        xpx = int(s / dur * (W - 1))
        d.line([(xpx, y0), (xpx, y0 + 4)], fill=(200, 200, 200))
        d.text((xpx + 2, y0 + 3), f"{s:g}", fill=(200, 200, 200))
    for t0, _, _ in marks:
        xpx = int(t0 / dur * (W - 1))
        d.line([(xpx, top), (xpx, top + WAVE_H)], fill=(255, 200, 60))
    return img


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("keys", nargs="*")
    ap.add_argument("--file", action="append", default=[])
    ap.add_argument("--out", default=None)
    args = ap.parse_args()
    ledger = {s["key"]: s for s in json.loads((HERE / "sources" / "ledger.json").read_text(encoding="utf-8"))["sources"]}
    items = []
    for k in args.keys:
        key, _, span = k.partition("@")
        path = RAW / (key + Path(ledger[key]["file"]).suffix)
        if span:
            a, b = (float(v) for v in span.split("-"))
            path = Path(str(path) + f"#{a:g}-{b:g}")      # (a distinct key for the window; read from the real file)
            WINDOWS[path] = (a, b)
        items.append((k, path))
    items += [(Path(f).stem, Path(f)) for f in args.file]
    panels = []
    for name, path in items:
        x, sr = load(path)
        marks = onsets(x.mean(axis=1), sr)
        print(f"{name}: {len(x) / sr:.2f} s, {sr} Hz, {x.shape[1]} ch, peak {20 * np.log10(np.max(np.abs(x)) + 1e-12):.1f} dBFS, "
              f"clipped {clipped(x) * 100:.2f}%")
        for t0, p, r in marks:
            print(f"    bang at {t0:7.2f} s  {p:6.1f} dBFS  rise {r:5.1f} ms")
        panels.append(panel(name, path, marks))
    sheet = Image.new("RGB", (W, sum(p.height + 6 for p in panels)), (10, 10, 12))
    y = 0
    for p in panels:
        sheet.paste(p, (0, y))
        y += p.height + 6
    out = paths.PREVIEWS / (args.out or ("survey_" + (items[0][0] if len(items) == 1 else "set") + ".png"))
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
