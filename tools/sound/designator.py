"""The Laser Designator's sounds (DESIGN.md §17.13), synthesized, all from its small speaker and its electronics:

- launch/designator_on: switched on (or its mode switched): the button's click, the laser's capacitor charging (a faint
  whine rising out of hearing), the ready chirp: two short tones, rising. Played lower for call-for-fire mode.
- launch/designator_lock: a lock: three quick pips (the half-way warning, the "no launcher reaches it" answer, and the
  designator taking a station's network).
- launch/designator_fire: fire called: the radio's squelch opening on the network, a long confirm tone and two high pips,
  the squelch closing.
"""
from __future__ import annotations

import numpy as np

import sources as R
import synth as S

#: Each sound's loudness (its loudest 3 s, LUFS): a handheld's beeps, quieter than the station's.
LOUDNESS = {"launch/designator_on": -24.0, "launch/designator_lock": -23.0, "launch/designator_fire": -21.0}


def tones(total: float, spans: list[tuple[float, float, float]], edge: float = 0.004) -> np.ndarray:
    """A small speaker's square-ish beeps: (start s, end s, Hz) each, its edges eased so they do not click."""
    n = int(total * S.SR)
    f = np.zeros(n)
    env = np.zeros(n)
    e = max(1, int(edge * S.SR))
    for a, b, hz in spans:
        i0, i1 = int(a * S.SR), min(n, int(b * S.SR))
        f[i0:i1] = hz
        seg = np.ones(i1 - i0)
        seg[:e] *= np.linspace(0, 1, e)
        seg[-e:] *= np.linspace(1, 0, e)
        env[i0:i1] = seg
    phase = 2 * np.pi * np.cumsum(f) / S.SR
    wave = sum(np.sin(k * phase) / k * (k * f < 0.45 * S.SR) for k in (1, 3, 5, 7, 9))
    return S.bandpass(S.saturate(wave * env * 0.5, 1.4), 500, 5200)


def click(seconds: float, seed: int, freq: float = 3200, tau: float = 0.006) -> np.ndarray:
    """A small rubber-sealed button: a soft tick and its plastic ringing briefly."""
    n = int(seconds * S.SR)
    t = S.t_axis(seconds)
    tick = S.bandpass(S.white(seconds, seed), 1500, 8000)[:n] * S.exp_decay(n, 0.0025) * 0.5
    return tick + 0.25 * np.sin(2 * np.pi * freq * t) * np.exp(-t / tau)


def room(x: np.ndarray, seconds: float = 0.25, mix: float = 0.06) -> np.ndarray:
    return S.reverb(x, seconds=seconds, damping_hz=5000, mix=mix)[: len(x)]


def designator_on() -> np.ndarray:
    total = 0.55
    n = int(total * S.SR)
    t = S.t_axis(total)
    # The capacitor charging: a thin whine sweeping 4 to 11 kHz, swelling then gone.
    sweep = 4000 + 7000 * np.clip(t / 0.42, 0, 1) ** 1.6
    whine = np.sin(2 * np.pi * np.cumsum(sweep) / S.SR) * S.breakpoints(n, [(0.0, 0.0), (0.05, 0.0), (0.12, 1.0), (0.4, 0.6), (0.46, 0.0), (total, 0.0)])
    chirp = tones(total, [(0.30, 0.36, 1760.0), (0.39, 0.47, 2350.0)])
    x = S.mix((S.place(click(0.05, 1901), 0.0, total), 0.9), (whine, 0.035), (chirp, 0.5))
    return S.fade(room(x), 0.0, 0.04)


def designator_lock() -> np.ndarray:
    total = 0.4
    pips = tones(total, [(0.0, 0.055, 2200.0), (0.09, 0.145, 2200.0), (0.18, 0.26, 2640.0)])
    return S.fade(room(pips), 0.0, 0.04)


def squelch(seconds: float, seed: int) -> np.ndarray:
    """A radio's squelch: a burst of band-limited hiss, cut off sharp."""
    n = int(seconds * S.SR)
    hiss = S.bandpass(S.white(seconds, seed), 900, 3800)[:n]
    return hiss * S.breakpoints(n, [(0.0, 0.0), (0.005, 1.0), (seconds * 0.7, 0.8), (seconds, 0.0)])


def designator_fire() -> np.ndarray:
    total = 1.05
    beeps = tones(total, [(0.12, 0.42, 1600.0), (0.5, 0.56, 2400.0), (0.62, 0.68, 2400.0)])
    hiss = S.mix((S.place(squelch(0.1, 1911), 0.0, total), 1.0), (S.place(squelch(0.12, 1912), 0.8, total), 0.8))
    tail = S.place(click(0.04, 1913, freq=2100), 0.92, total)
    x = S.mix((beeps, 0.55), (hiss, 0.09), (tail, 0.5))
    return S.fade(room(x, 0.3, 0.08), 0.0, 0.05)


def designator_set() -> dict[str, np.ndarray]:
    """Every designator sound, at its loudness."""
    made = {"launch/designator_on": designator_on(), "launch/designator_lock": designator_lock(), "launch/designator_fire": designator_fire()}
    return {rel: R.loud_as(x, LOUDNESS[rel]) for rel, x in made.items()}


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from gb import paths  # noqa: E402
    for rel, x in designator_set().items():
        S.save_ogg(x, paths.SOUNDS / f"{rel}.ogg")
        print(f"{rel}: {len(x) / S.SR:.2f} s")
