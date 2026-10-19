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


# --- Machines -------------------------------------------------------------------------------------------------------------
# Looping work sounds, 2 s each. Every periodic event has a period dividing 2 s so the loop stays in time.
LOOP = 2.0
PRE, XF = 0.25, 0.15
TOTAL = PRE + LOOP + XF


def _pulses(times: list[float], shape: np.ndarray, total: float = TOTAL) -> np.ndarray:
    out = np.zeros(int(total * S.SR))
    for t in times:
        i = int((t + PRE) * S.SR)
        seg = shape[max(0, -i):]
        i = max(0, i)
        seg = seg[: max(0, len(out) - i)]
        out[i:i + len(seg)] += seg
    return out


def generator_loop() -> np.ndarray:
    """Single-cylinder engine at ~480 rpm (8 firing strokes/s): thudding exhaust pulses over a 40 Hz block
    resonance, valve-train tick, and the firebox crackling behind it."""
    stroke = S.lowpass(S.white(0.12, 31), 260) * S.exp_decay(int(0.12 * S.SR), 0.03)
    stroke = S.saturate(stroke * 3, 2) + S.tone(0.12, 42) * S.exp_decay(int(0.12 * S.SR), 0.05) * 0.8
    rng = np.random.default_rng(32)
    times = [k / 8 + rng.normal(0, 0.004) for k in range(-1, int(TOTAL * 8))]
    chug = _pulses([t for t in times if t >= -PRE], stroke)
    tick = _pulses([k / 8 + 0.06 for k in range(int(TOTAL * 8))], S.bandpass(S.white(0.01, 33), 2500, 6000) * 0.15)
    hum = S.lowpass(S.brown(TOTAL, 34), 180) * 0.25
    crackle = S.highpass(S.grains(TOTAL, 14, 4, 35), 1500) * 0.18
    x = S.mix((chug, 1.0), (tick, 1.0), (hum, 1.0), (crackle, 1.0))
    return S.normalize(S.seamless_loop(x, LOOP, XF, PRE), -6)


def crusher_loop() -> np.ndarray:
    """Toothed rollers at 1 rev/s with 8 teeth (8 Hz bite), grinding rock noise, rock crunches, and the drive
    motor's 120 Hz whine with harmonics."""
    t = S.t_axis(TOTAL)
    grind = S.bandpass(S.brown(TOTAL, 41) + S.pink(TOTAL, 42) * 0.3, 120, 1400)
    bite = 0.6 + 0.4 * np.abs(np.sin(np.pi * 8 * t)) ** 3
    crunch = S.bandpass(S.grains(TOTAL, 22, 12, 43), 900, 5000) * 0.5
    clunks = _pulses([k / 2 + 0.17 for k in range(-1, int(TOTAL * 2))],
                     S.lowpass(S.white(0.08, 44), 500) * S.exp_decay(int(0.08 * S.SR), 0.02) * 1.2)
    motor = (S.tone(TOTAL, 120) * 0.5 + S.tone(TOTAL, 240) * 0.25 + S.tone(TOTAL, 360) * 0.12) * 0.25
    x = S.mix((grind * bite, 1.0), (crunch, 1.0), (clunks, 1.0), (motor, 1.0))
    return S.normalize(S.seamless_loop(S.saturate(x * 1.3, 1.5), LOOP, XF, PRE), -6)


def arc_loop() -> np.ndarray:
    """Electric arc furnace: a saturated 100 Hz buzz rich in odd harmonics, a low roar from the melt, and
    irregular spitting crackles as the arc wanders."""
    t = S.t_axis(TOTAL)
    buzz = sum(np.sin(2 * np.pi * 100 * k * t) / k for k in (1, 3, 5, 7, 9))
    wander = 0.7 + 0.3 * np.sin(2 * np.pi * 0.5 * t) * np.sin(2 * np.pi * 1.5 * t + 0.7)
    buzz = S.saturate(buzz * wander * 1.5, 2.5) * 0.35
    roar = S.lowpass(S.brown(TOTAL, 51), 300) * 0.6
    spit = S.highpass(S.grains(TOTAL, 30, 3, 52), 2500) * 0.6
    snaps = S.highpass(S.grains(TOTAL, 3, 25, 53), 800) * 0.8
    x = S.mix((buzz, 1.0), (roar, 1.0), (spit, 1.0), (snaps, 1.0))
    return S.normalize(S.seamless_loop(x, LOOP, XF, PRE), -6)


def press_loop() -> np.ndarray:
    """Hydraulic press, one 2 s stroke (matches the model's ram animation): pump whine rising as pressure
    builds, the ram's heavy thump at 1.0 s, a hiss of released pressure, then the pump settling."""
    n = int(TOTAL * S.SR)
    t = S.t_axis(TOTAL) - PRE
    phase = np.mod(t, LOOP)
    freq = 180 + 140 * np.clip(phase / 1.0, 0, 1) * (phase < 1.0) + 180 * (phase >= 1.0) * np.exp(-(phase - 1.0) * 3)
    whine = np.sin(2 * np.pi * np.cumsum(freq) / S.SR) * 0.18
    whine += S.bandpass(S.pink(TOTAL, 61), 300, 900) * 0.15
    thump = S.mix((S.saturate(S.lowpass(S.white(0.25, 62), 180) * S.exp_decay(int(0.25 * S.SR), 0.05) * 4, 2), 1.0),
                  (S.tone(0.4, 55) * S.exp_decay(int(0.4 * S.SR), 0.12), 1.0))
    clank = S.bandpass(S.white(0.15, 63), 1200, 4000) * S.exp_decay(int(0.15 * S.SR), 0.03) * 0.4
    hiss = S.highpass(S.white(0.5, 64), 2500) * S.breakpoints(int(0.5 * S.SR), [(0, 0), (0.03, 0.5), (0.5, 0)])
    hits = [1.0 + k * LOOP for k in range(-1, 2)]
    x = S.mix((whine, 1.0), (_pulses(hits, thump), 1.0), (_pulses(hits, clank), 1.0),
              (_pulses([h + 0.12 for h in hits], hiss), 1.0))
    return S.normalize(S.seamless_loop(x[:n], LOOP, XF, PRE), -4)


def separator_loop() -> np.ndarray:
    """Electrolysis cell: steady bubbling (many small resonant pops), a soft 50 Hz transformer hum, a gentle
    water slosh underneath."""
    pops = np.zeros(int(TOTAL * S.SR))
    rng = np.random.default_rng(71)
    t = 0.0
    while t < TOTAL:
        t += rng.exponential(1 / 26)
        i = int(t * S.SR)
        f = rng.uniform(500, 1600)
        n = int(0.03 * S.SR)
        pop = np.sin(2 * np.pi * np.cumsum(np.linspace(f, f * 1.6, n)) / S.SR) * S.exp_decay(n, 0.008) * rng.uniform(0.3, 1.0)
        seg = pop[: max(0, len(pops) - i)]
        pops[i:i + len(seg)] += seg
    hum = (S.tone(TOTAL, 50) * 0.5 + S.tone(TOTAL, 100) * 0.3 + S.tone(TOTAL, 150) * 0.1) * 0.25
    slosh = S.lowpass(S.brown(TOTAL, 72), 220) * 0.4
    x = S.mix((pops, 0.8), (hum, 1.0), (slosh, 1.0))
    return S.normalize(S.seamless_loop(x, LOOP, XF, PRE), -7)


def condenser_loop() -> np.ndarray:
    """Cryogenic condenser: compressor drone with a 4 Hz piston pulse, a steady high cold-gas hiss, and a brief
    vapour vent sigh twice per loop."""
    t = S.t_axis(TOTAL)
    drone = (S.tone(TOTAL, 60, "saw") * 0.3 + S.tone(TOTAL, 120) * 0.2)
    drone = S.lowpass(drone, 400) * (0.75 + 0.25 * np.sin(2 * np.pi * 4 * t) ** 2)
    hiss = S.bandpass(S.white(TOTAL, 81), 3000, 9000) * 0.18
    sigh = S.highpass(S.white(0.6, 82), 1200) * S.breakpoints(int(0.6 * S.SR), [(0, 0), (0.08, 0.6), (0.6, 0)])
    vents = _pulses([0.3, 1.3, -0.7, 2.3], sigh)
    x = S.mix((drone, 1.0), (hiss, 1.0), (vents, 0.8))
    return S.normalize(S.seamless_loop(x, LOOP, XF, PRE), -7)


def machine_loops() -> dict[str, np.ndarray]:
    return {
        "machine/combustion_generator": generator_loop(),
        "machine/ore_crusher": crusher_loop(),
        "machine/arc_furnace": arc_loop(),
        "machine/industrial_press": press_loop(),
        "machine/electrolytic_separator": separator_loop(),
        "machine/cryogenic_condenser": condenser_loop(),
    }


RECIPES = [geiger_clicks, nuke_set, machine_loops]


def build_sounds() -> list[Path]:
    written = []
    for recipe in RECIPES:
        for rel, samples in recipe().items():
            written.append(S.save_ogg(samples, paths.SOUNDS / f"{rel}.ogg"))
    return written


if __name__ == "__main__":
    for p in build_sounds():
        print(p)
