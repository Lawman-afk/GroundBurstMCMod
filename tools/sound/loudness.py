"""How loud and how heavy sounds are, side by side: checks that a family keeps its order (a bigger blast must sound bigger).

The game plays every blast set at the same volume for a band (ClientBlasts.BAND_VOLUME), so a nuke sounds bigger than a
large blast only if its file is louder and heavier. For each file:

    peak     the highest sample (dBFS)
    M-max    the loudest 400 ms (LUFS, ITU-R BS.1770: K-weighted; how hard it hits)
    S-max    the loudest 3 s (LUFS; the body)
    weight   the loudest 1 s of the band 30-150 Hz (dBFS; the whump and the roar's depth)
    bite     the loudest 400 ms of the band 1-5 kHz (dBFS; the crack)
    loud     how long it stays within 10 LU of its loudest 400 ms (s; a bigger blast roars longer)
    len      its length (s)

    python loudness.py <files or directories...> [--ladder]

With --ladder, blast files (<set>_<band>.ogg) are grouped by band and each set checked against the one below it.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy import signal

SETS = ["small", "medium", "large", "nuke", "tsar"]
BANDS = ["near", "mid", "far", "very_far"]


def k_weight(x: np.ndarray, sr: int) -> np.ndarray:
    """BS.1770's K-weighting: the head's high shelf, then the RLB high-pass (coefficients for any sample rate)."""
    f0, g, q = 1681.974450955533, 3.999843853973347, 0.7071752369554196
    k = np.tan(np.pi * f0 / sr)
    vh, vb = 10 ** (g / 20), (10 ** (g / 20)) ** 0.4996667741545416
    a0 = 1 + k / q + k * k
    b = [(vh + vb * k / q + k * k) / a0, 2 * (k * k - vh) / a0, (vh - vb * k / q + k * k) / a0]
    a = [1.0, 2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0]
    y = signal.lfilter(b, a, x)
    f0, q = 38.13547087602444, 0.5003270373238773
    k = np.tan(np.pi * f0 / sr)
    a0 = 1 + k / q + k * k
    return signal.lfilter([1.0, -2.0, 1.0], [1.0, 2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0], y)


def windows(power: np.ndarray, sr: int, seconds: float, hop: float = 0.1) -> np.ndarray:
    """Mean power in every {seconds} window, {hop} s apart (a sound shorter than one window is one window, padded)."""
    n, h = int(seconds * sr), int(hop * sr)
    if len(power) < n:
        power = np.pad(power, (0, n - len(power)))
    c = np.concatenate([[0.0], np.cumsum(power)])
    starts = np.arange(0, len(power) - n + 1, h)
    return (c[starts + n] - c[starts]) / n


def lufs(ms: np.ndarray) -> np.ndarray:
    return -0.691 + 10 * np.log10(np.maximum(ms, 1e-12))


def band_db(x: np.ndarray, sr: int, lo: float, hi: float, seconds: float) -> float:
    sos = signal.butter(4, [lo, hi], btype="bandpass", fs=sr, output="sos")
    y = signal.sosfilt(sos, x)
    return float(10 * np.log10(max(windows(y * y, sr, seconds).max(), 1e-12)))


def measure(path: Path) -> dict[str, float]:
    x, sr = sf.read(str(path), always_2d=True)
    return measure_array(x.mean(axis=1), sr)


def measure_array(x: np.ndarray, sr: int) -> dict[str, float]:
    """The same measures of a sound not yet saved (mono)."""
    kw = k_weight(x, sr)
    m = lufs(windows(kw * kw, sr, 0.4))
    s = lufs(windows(kw * kw, sr, 3.0))
    return {"peak": float(20 * np.log10(max(np.abs(x).max(), 1e-12))), "M-max": float(m.max()), "S-max": float(s.max()),
            "weight": band_db(x, sr, 30, 150, 1.0), "bite": band_db(x, sr, 1000, 5000, 0.4),
            "loud": float(np.count_nonzero(m >= m.max() - 10) * 0.1), "len": len(x) / sr}


COLUMNS = ["peak", "M-max", "S-max", "weight", "bite", "loud", "len"]


def row(name: str, v: dict[str, float]) -> str:
    return f"{name:<22}" + "".join(f"{v[c]:>8.1f}" for c in COLUMNS)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("paths", nargs="+")
    ap.add_argument("--ladder", action="store_true")
    args = ap.parse_args()
    files: list[Path] = []
    for p in map(Path, args.paths):
        files += sorted(p.glob("*.ogg")) if p.is_dir() else [p]
    found = {f.stem: measure(f) for f in files}
    print(f"{'':<22}" + "".join(f"{c:>8}" for c in COLUMNS))
    if not args.ladder:
        for name, v in found.items():
            print(row(name, v))
        return
    for band in BANDS:
        below = None
        for st in SETS:
            v = found.get(f"{st}_{band}")
            if v is None:
                continue
            flags = ""
            if below is not None:
                weaker = [c for c in ("M-max", "S-max", "weight", "loud") if v[c] < below[c]]
                flags = ("   weaker than the set below in: " + ", ".join(weaker)) if weaker else ""
            print(row(f"{st}_{band}", v) + flags)
            below = v
        print()


if __name__ == "__main__":
    sys.exit(main())
