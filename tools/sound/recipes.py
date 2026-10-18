"""Sound recipes. Each recipe returns {relative_path: samples}; build_sounds() writes them as OGG.

Paths are relative to assets/groundzero/sounds/ (e.g. "blast/nuke_near" -> sounds/blast/nuke_near.ogg).
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gz import paths  # noqa: E402
import synth as S  # noqa: E402


# --- Geiger counter ---------------------------------------------------------------------------------------------------

def geiger_clicks() -> dict[str, np.ndarray]:
    """Four slightly different clicks: a sharp ionisation pulse through a tiny speaker (resonant 2.4-3.4 kHz)
    with a short body. The Geiger HUD triggers them at a Poisson rate that tracks the dose rate."""
    out = {}
    for i in range(4):
        n = int(0.035 * S.SR)
        pulse = np.zeros(n)
        pulse[0] = 1.0
        pulse[1] = -0.6
        noise = S.white(0.035, seed=100 + i) * S.exp_decay(n, 0.0025)
        body = S.resonator(pulse + noise * 0.4, 2400 + i * 330, q=8) * 2.5
        crack = S.highpass(noise, 3000) * 0.5
        x = S.mix((body, 1.0), (crack, 1.0)) * S.exp_decay(n, 0.006)
        out[f"geiger/click{i + 1}"] = S.normalize(S.fade(x, 0, 0.004), -3)
    return out


# --- Nuclear detonation -------------------------------------------------------------------------------------------------

def nuke_source(seed: int = 1, length: float = 9.0) -> np.ndarray:
    """Close-range nuclear blast: sharp supersonic crack, sub-bass thump, a roaring rumble that rolls and
    darkens for seconds, and debris patter. Distance variants are derived from this."""
    n = int(length * S.SR)
    t = S.t_axis(length)

    # 1) Crack: broadband impulse, very fast attack, saturated.
    crack_len = 0.25
    crack = S.white(crack_len, seed) * S.exp_decay(int(crack_len * S.SR), 0.035)
    crack = S.saturate(S.highpass(crack, 120) * 3, 3)

    # 2) Thump: sine falling 70 -> 24 Hz with a body that decays over ~2 s.
    thump = S.sine_sweep(3.0, 70, 24, curve=2.5) * S.exp_decay(int(3.0 * S.SR), 0.9)
    thump = S.saturate(thump * 1.6, 1.8)

    # 3) Roar: brown + pink noise, darkening over time, slowly flutters (turbulence), long decay.
    roar = S.brown(length, seed + 1) * 0.8 + S.pink(length, seed + 2) * 0.35
    roar = S.sweep_lowpass(roar, 2600, 180)
    flutter = 1 + 0.25 * np.sin(2 * np.pi * 1.7 * t + 1.3) * np.sin(2 * np.pi * 0.31 * t)
    roar_env = S.breakpoints(n, [(0, 0), (0.02, 1.0), (0.6, 0.85), (2.5, 0.5), (6.0, 0.15), (length, 0)])
    roar = roar * roar_env * flutter

    # 4) Debris: crackle that thins out, plus heavier thuds of falling material.
    debris = S.bandpass(S.grains(length, 60, 6, seed + 3, decay=2.2), 800, 6000) * 0.35
    clods = S.lowpass(S.grains(length, 6, 40, seed + 4, decay=3.0), 400) * 0.6
    debris = S.place(debris, 0.4, length) + S.place(clods, 0.8, length)

    x = S.mix((S.place(crack, 0, length), 0.9), (S.place(thump, 0, length), 1.0), (roar, 0.9), (debris, 0.5))
    x = S.reverb(x, 2.8, 2200, 0.3, seed=seed + 5)
    return S.limit(S.normalize(x, 0), -1.0)


def nuke_set() -> dict[str, np.ndarray]:
    near = nuke_source()
    return {
        "blast/nuke_near": S.trim_silence(S.fade(near, 0, 0.6)),
        "blast/nuke_mid": S.trim_silence(S.fade(S.limit(S.normalize(S.distant(near, "mid"), 0), -1.5), 0, 1.0)),
        "blast/nuke_far": S.trim_silence(S.fade(S.limit(S.normalize(S.distant(near, "far"), 0), -2.0), 0, 1.5)),
        "blast/nuke_very_far": S.trim_silence(S.fade(S.limit(S.normalize(S.distant(near, "very_far"), 0), -3.0), 0, 2.0)),
    }


RECIPES = [geiger_clicks, nuke_set]


def build_sounds() -> list[Path]:
    written = []
    for recipe in RECIPES:
        for rel, samples in recipe().items():
            written.append(S.save_ogg(samples, paths.SOUNDS / f"{rel}.ogg"))
    return written


if __name__ == "__main__":
    for p in build_sounds():
        print(p)
