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


# --- Conventional blasts ---------------------------------------------------------------------------------------------
# One recipe, three sizes. Each size is heard near, mid, far and very far (DESIGN.md §13): the client picks the band by
# distance and delays it by the speed of sound.
BLAST_SIZES = {
    # length, crack (s, gain), thump (f0, f1, s, decay), roar (level, s, lowpass from, to), debris (crackle, clods), reverb (s, mix)
    "small": dict(length=2.2, crack=(0.12, 1.1), thump=(130, 48, 0.7, 0.18), roar=(0.55, 1.4, 4200, 700), debris=(0.25, 0.15), reverb=(1.2, 0.22)),
    "medium": dict(length=3.8, crack=(0.18, 1.0), thump=(95, 34, 1.4, 0.4), roar=(0.8, 3.0, 3400, 320), debris=(0.4, 0.4), reverb=(2.0, 0.28)),
    "large": dict(length=6.0, crack=(0.22, 0.95), thump=(78, 26, 2.4, 0.7), roar=(0.9, 5.2, 2800, 200), debris=(0.45, 0.6), reverb=(2.6, 0.3)),
}


def blast_source(size: str, seed: int) -> np.ndarray:
    """A conventional explosion heard close by: a supersonic crack, a low thump falling in pitch, a roar that rolls
    and darkens, then the patter and thuds of falling debris; bigger sizes are longer, lower and heavier."""
    c = BLAST_SIZES[size]
    length = c["length"]
    n = int(length * S.SR)
    t = S.t_axis(length)
    crack_len, crack_gain = c["crack"]
    crack = S.white(crack_len, seed) * S.exp_decay(int(crack_len * S.SR), crack_len / 7)
    crack = S.saturate(S.highpass(crack, 150) * 3 * crack_gain, 3)
    f0, f1, thump_len, decay = c["thump"]
    thump = S.saturate(S.sine_sweep(thump_len, f0, f1, curve=2.5) * S.exp_decay(int(thump_len * S.SR), decay) * 1.6, 1.8)
    level, roar_len, lp0, lp1 = c["roar"]
    roar = S.brown(length, seed + 1) * 0.8 + S.pink(length, seed + 2) * 0.4
    roar = S.sweep_lowpass(roar, lp0, lp1)
    flutter = 1 + 0.2 * np.sin(2 * np.pi * 2.1 * t + 0.7) * np.sin(2 * np.pi * 0.43 * t)
    roar = roar * S.breakpoints(n, [(0, 0), (0.015, 1.0), (roar_len * 0.25, 0.7), (roar_len, 0.12), (length, 0)]) * flutter * level
    crackle, clods = c["debris"]
    debris = S.bandpass(S.grains(length, 50, 5, seed + 3, decay=length * 0.35), 900, 6500) * crackle
    thuds = S.lowpass(S.grains(length, 5, 35, seed + 4, decay=length * 0.4), 450) * clods
    debris = S.place(debris, 0.25, length) + S.place(thuds, 0.5, length)
    x = S.mix((S.place(crack, 0, length), 0.9), (S.place(thump, 0, length), 1.0), (roar, 1.0), (debris, 0.6))
    rv, mix = c["reverb"]
    x = S.reverb(x, rv, 2400, mix, seed=seed + 5)
    # Headroom: the OGG encoder overshoots a peak-normalised crack by up to a decibel.
    return S.limit(S.normalize(x, -1.5), -2.5)


def blast_sets() -> dict[str, np.ndarray]:
    out = {}
    for i, size in enumerate(BLAST_SIZES):
        near = blast_source(size, 300 + i * 10)
        tail = BLAST_SIZES[size]["length"] * 0.15
        out[f"blast/{size}_near"] = S.trim_silence(S.fade(near, 0, tail))
        out[f"blast/{size}_mid"] = S.trim_silence(S.fade(S.limit(S.normalize(S.distant(near, "mid"), 0), -1.5), 0, tail * 1.4))
        out[f"blast/{size}_far"] = S.trim_silence(S.fade(S.limit(S.normalize(S.distant(near, "far"), 0), -2.0), 0, tail * 1.8))
        out[f"blast/{size}_very_far"] = S.trim_silence(S.fade(S.limit(S.normalize(S.distant(near, "very_far"), 0), -3.0), 0, tail * 2.2))
    return out


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


def chemical_plant_loop() -> np.ndarray:
    """Chemical plant: stirrer motor drone (90 Hz with harmonics) and the paddle's 2 Hz swish through liquid, slow
    deep bubbling from two tanks, a pipe gurgle, and a valve hiss once per 2 s loop (matches the model's cycle)."""
    t = S.t_axis(TOTAL)
    motor = (S.tone(TOTAL, 90) * 0.4 + S.tone(TOTAL, 180) * 0.2 + S.tone(TOTAL, 270) * 0.08) * 0.3
    swish = S.bandpass(S.pink(TOTAL, 91), 200, 900) * (0.4 + 0.6 * np.sin(2 * np.pi * 2 * t) ** 4) * 0.5
    blubs = np.zeros(int(TOTAL * S.SR))
    rng = np.random.default_rng(92)
    tt = 0.0
    while tt < TOTAL:
        tt += rng.exponential(1 / 9)
        i = int(tt * S.SR)
        f = rng.uniform(140, 420)
        n = int(0.09 * S.SR)
        blub = np.sin(2 * np.pi * np.cumsum(np.linspace(f, f * 1.8, n)) / S.SR) * S.exp_decay(n, 0.025) * rng.uniform(0.4, 1.0)
        seg = blub[: max(0, len(blubs) - i)]
        blubs[i:i + len(seg)] += seg
    gurgle = S.lowpass(S.brown(TOTAL, 93), 260) * 0.35
    hiss = S.bandpass(S.white(0.45, 94), 2000, 7000) * S.breakpoints(int(0.45 * S.SR), [(0, 0), (0.05, 0.4), (0.45, 0)])
    x = S.mix((motor, 1.0), (swish, 1.0), (blubs, 0.7), (gurgle, 1.0), (_pulses([1.4, -0.6, 3.4], hiss), 1.0))
    return S.normalize(S.seamless_loop(x, LOOP, XF, PRE), -7)


def water_pump_loop() -> np.ndarray:
    """Water pump: motor hum and fan whirr, a soft 4 Hz impeller pulse, water rushing through the outlets."""
    t = S.t_axis(TOTAL)
    motor = (S.tone(TOTAL, 100) * 0.4 + S.tone(TOTAL, 200) * 0.2 + S.tone(TOTAL, 300) * 0.07) * 0.3
    whirr = S.bandpass(S.white(TOTAL, 101), 600, 2200) * (0.6 + 0.4 * np.sin(2 * np.pi * 12 * t) ** 2) * 0.12
    rush = S.bandpass(S.pink(TOTAL, 102), 250, 1800) * (0.55 + 0.45 * np.sin(2 * np.pi * 4 * t) ** 2) * 0.5
    gurgle = S.lowpass(S.brown(TOTAL, 103), 300) * 0.3
    x = S.mix((motor, 1.0), (whirr, 1.0), (rush, 1.0), (gurgle, 1.0))
    return S.normalize(S.seamless_loop(x, LOOP, XF, PRE), -8)


def circuit_fabricator_loop() -> np.ndarray:
    """Circuit Fabricator, one 2 s pick-and-place cycle (matches the gantry animation): a clean-box blower hum, servo
    whirs while the gantry travels (0.5-0.95 s and 1.45-1.9 s), a soft vacuum-gripper tick at the pick (0.15 s) and a
    placement click at 1.15 s."""
    t = S.t_axis(TOTAL) - PRE
    phase = np.mod(t, LOOP)
    blower = S.bandpass(S.pink(TOTAL, 111), 300, 2500) * 0.08 + (S.tone(TOTAL, 140) * 0.3 + S.tone(TOTAL, 280) * 0.1) * 0.15
    moving = ((phase > 0.5) & (phase < 0.95)) | ((phase > 1.45) & (phase < 1.9))
    env = np.convolve(moving.astype(float), np.ones(800) / 800, mode="same")
    servo_f = 520 + 160 * np.sin(np.pi * np.clip((np.mod(phase, 0.95) - 0.5) / 0.45, 0, 1))
    servo = (np.sin(2 * np.pi * np.cumsum(servo_f) / S.SR) * 0.5 + np.sin(2 * np.pi * np.cumsum(servo_f * 2) / S.SR) * 0.2) * env * 0.35
    tick = S.highpass(S.white(0.02, 112), 2500) * S.exp_decay(int(0.02 * S.SR), 0.003) * 0.25
    click = S.mix((S.bandpass(S.white(0.04, 113), 1500, 6000) * S.exp_decay(int(0.04 * S.SR), 0.006), 1.0),
                  (S.tone(0.04, 1800) * S.exp_decay(int(0.04 * S.SR), 0.01) * 0.4, 1.0)) * 0.4
    hits = [k * LOOP for k in range(-1, 2)]
    x = S.mix((blower, 1.0), (servo, 1.0), (_pulses([h + 0.15 for h in hits], tick), 1.0),
              (_pulses([h + 1.15 for h in hits], click), 1.0), (_pulses([h + 0.32 for h in hits], tick * 0.6), 1.0))
    return S.normalize(S.seamless_loop(x, LOOP, XF, PRE), -8)


def gas_centrifuge_loop() -> np.ndarray:
    """Gas Centrifuge: the high whine of a rotor at speed (900 Hz and a 903 Hz partner beating at 3 Hz, plus its
    octave), a soft motor hum underneath and a faint airy hiss from the casing. Frequencies fit whole cycles in 2 s."""
    t = S.t_axis(TOTAL)
    whine = (np.sin(2 * np.pi * 900 * t) + 0.8 * np.sin(2 * np.pi * 903 * t)) * 0.18 + np.sin(2 * np.pi * 1800 * t) * 0.06
    whine += np.sin(2 * np.pi * 2700 * t) * 0.02
    hum = (S.tone(TOTAL, 50) * 0.5 + S.tone(TOTAL, 100) * 0.25) * 0.25
    hiss = S.bandpass(S.white(TOTAL, 121), 4000, 9000) * 0.05
    x = S.mix((whine, 1.0), (hum, 1.0), (hiss, 1.0))
    return S.normalize(S.seamless_loop(x, LOOP, XF, PRE), -9)


def fusion_fuel_synthesizer_loop() -> np.ndarray:
    """Fusion Fuel Synthesizer, one 2 s press cycle (matches the ram animation): a gas-feed hiss, the pump's whine
    climbing as pressure builds, the pellet press clunking down at 1.0 s, then a sharp pressure release."""
    t = S.t_axis(TOTAL) - PRE
    phase = np.mod(t, LOOP)
    feed = S.bandpass(S.white(TOTAL, 131), 2500, 7000) * 0.06
    freq = 140 + 120 * np.clip(phase / 1.0, 0, 1) * (phase < 1.0) + 120 * (phase >= 1.0) * np.exp(-(phase - 1.0) * 4)
    pump = (np.sin(2 * np.pi * np.cumsum(freq) / S.SR) * 0.5 + np.sin(2 * np.pi * np.cumsum(freq * 2) / S.SR) * 0.15) * 0.25
    clunk = S.mix((S.saturate(S.lowpass(S.white(0.18, 132), 260) * S.exp_decay(int(0.18 * S.SR), 0.035) * 4, 2), 1.0),
                  (S.tone(0.3, 70) * S.exp_decay(int(0.3 * S.SR), 0.08), 0.9),
                  (S.bandpass(S.white(0.12, 133), 1800, 5000) * S.exp_decay(int(0.12 * S.SR), 0.02) * 0.5, 1.0))
    release = S.highpass(S.white(0.4, 134), 2000) * S.breakpoints(int(0.4 * S.SR), [(0, 0), (0.02, 0.45), (0.4, 0)])
    hits = [1.0 + k * LOOP for k in range(-1, 2)]
    x = S.mix((feed, 1.0), (pump, 1.0), (_pulses(hits, clunk), 1.0), (_pulses([h + 0.25 for h in hits], release), 1.0))
    return S.normalize(S.seamless_loop(x, LOOP, XF, PRE), -5)


def canning_machine_loop() -> np.ndarray:
    """Canning Machine, one 2 s canister (matches the nozzle animation): the nozzle seats on the neck with a soft
    clunk at 0.3 s, fluid gurgles into the canister under a feed-pump hum, a valve snaps shut at 1.6 s and the line
    vents with a short hiss as the nozzle lifts."""
    t = S.t_axis(TOTAL) - PRE
    phase = np.mod(t, LOOP)
    pump = (S.tone(TOTAL, 90) * 0.5 + S.tone(TOTAL, 180) * 0.2) * 0.12
    filling = ((phase > 0.3) & (phase < 1.6)).astype(float)
    filling = S.lowpass(filling, 30)                                         # soften the gate edges
    gurgle = S.bandpass(S.grains(TOTAL, 28, 6, 211), 250, 1400) * 1.1 * filling
    flow = S.bandpass(S.pink(TOTAL, 212), 400, 2500) * 0.25 * filling
    seat = S.mix((S.lowpass(S.white(0.08, 213), 500) * S.exp_decay(int(0.08 * S.SR), 0.015) * 1.4, 1.0),
                 (S.tone(0.12, 160) * S.exp_decay(int(0.12 * S.SR), 0.03) * 0.5, 1.0))
    snap = S.bandpass(S.white(0.03, 214), 1500, 6000) * S.exp_decay(int(0.03 * S.SR), 0.005) * 0.5
    vent = S.highpass(S.white(0.3, 215), 2500) * S.breakpoints(int(0.3 * S.SR), [(0, 0), (0.02, 0.1), (0.3, 0)])
    starts = [k * LOOP for k in range(-1, 2)]
    x = S.mix((pump, 1.0), (gurgle, 1.0), (flow, 1.0), (_pulses([s + 0.3 for s in starts], seat), 1.0),
              (_pulses([s + 1.6 for s in starts], snap), 1.0), (_pulses([s + 1.62 for s in starts], vent), 1.0))
    return S.normalize(S.seamless_loop(x, LOOP, XF, PRE), -7)


def exotic_matter_fabricator_loop() -> np.ndarray:
    """Exotic Matter Fabricator: a deep 45 Hz throb (with partials small speakers can carry) pulsing once a second, a magnetic 100 Hz buzz, a glassy shimmer of
    high partials drifting in and out of phase (every rate fits the 2 s loop), and sparse static crackles."""
    t = S.t_axis(TOTAL)
    throb = (np.sin(2 * np.pi * 45 * t) + 0.6 * np.sin(2 * np.pi * 90 * t) + 0.25 * np.sin(2 * np.pi * 135 * t))         * (0.6 + 0.4 * np.sin(2 * np.pi * 1.0 * t) ** 2) * 0.4
    buzz = S.lowpass(S.tone(TOTAL, 100, "saw"), 600) * 0.07
    shimmer = sum(np.sin(2 * np.pi * f * t + 2 * np.sin(2 * np.pi * r * t)) * a
                  for f, r, a in ((1320, 0.5, 0.05), (1980, 1.0, 0.04), (2640, 1.5, 0.03), (3300, 0.5, 0.02)))
    shimmer *= 0.6 + 0.4 * np.sin(2 * np.pi * 0.5 * t) ** 2
    crackle = S.highpass(S.grains(TOTAL, 6, 3, 141), 3000) * 0.25
    x = S.mix((throb, 1.0), (buzz, 1.0), (shimmer, 1.0), (crackle, 1.0))
    return S.normalize(S.seamless_loop(x, LOOP, XF, PRE), -7)


def diesel_generator_loop() -> np.ndarray:
    """Diesel Generator: a twin at 2 rev/s (matches the 0.5 s piston animation), firing alternately: deep knocking
    combustion thumps at 4 Hz with diesel clatter, a low block resonance, and exhaust breath."""
    stroke = S.saturate(S.lowpass(S.white(0.14, 151), 200) * S.exp_decay(int(0.14 * S.SR), 0.035) * 4, 2.2)
    stroke = stroke + S.tone(0.14, 36) * S.exp_decay(int(0.14 * S.SR), 0.06) * 0.9
    clatter = S.bandpass(S.white(0.03, 152), 1800, 5000) * S.exp_decay(int(0.03 * S.SR), 0.006) * 0.35
    rng = np.random.default_rng(153)
    times = [k / 4 + rng.normal(0, 0.003) for k in range(-1, int(TOTAL * 4) + 1)]
    thumps = _pulses(times, stroke)
    knocks = _pulses([t + 0.01 for t in times], clatter)
    block = S.lowpass(S.brown(TOTAL, 154), 140) * 0.35
    breath = S.bandpass(S.pink(TOTAL, 155), 300, 1500) * 0.08
    x = S.mix((thumps, 1.0), (knocks, 1.0), (block, 1.0), (breath, 1.0))
    return S.normalize(S.seamless_loop(x, LOOP, XF, PRE), -5)


def rtg_loop() -> np.ndarray:
    """RTG: nearly silent. A faint thermoelectric converter hum and, now and then, the tick of decay picked up by
    the casing's own counter (sparse, irregular clicks)."""
    hum = (S.tone(TOTAL, 60) * 0.5 + S.tone(TOTAL, 180) * 0.15) * 0.12
    air = S.bandpass(S.pink(TOTAL, 161), 200, 1200) * 0.03
    click = S.resonator(np.r_[1.0, -0.6, np.zeros(int(0.02 * S.SR))], 2900, q=8) * 0.5
    rng = np.random.default_rng(162)
    ticks = sorted(rng.uniform(-PRE, LOOP + XF, 7))
    x = S.mix((hum, 1.0), (air, 1.0), (_pulses(list(ticks), click), 1.0))
    return S.normalize(S.seamless_loop(x, LOOP, XF, PRE), -22)


def battery_charger_loop() -> np.ndarray:
    """Battery Charger: transformer hum (100 Hz), a thin high coil whine, and a soft relay tick each second when the
    LED chase restarts (matches the 1 s animation)."""
    t = S.t_axis(TOTAL)
    hum = (np.sin(2 * np.pi * 100 * t) * 0.5 + np.sin(2 * np.pi * 200 * t) * 0.25 + np.sin(2 * np.pi * 300 * t) * 0.1) * 0.2
    whine = np.sin(2 * np.pi * 7500 * t) * 0.012 * (0.7 + 0.3 * np.sin(2 * np.pi * 1.0 * t) ** 2)
    tick = S.bandpass(S.white(0.02, 171), 1500, 6000) * S.exp_decay(int(0.02 * S.SR), 0.004) * 0.25
    x = S.mix((hum, 1.0), (whine, 1.0), (_pulses([0.1 + k for k in range(-1, 3)], tick), 1.0))
    return S.normalize(S.seamless_loop(x, LOOP, XF, PRE), -12)


def decontamination_shower_loop() -> np.ndarray:
    """Decontamination Shower: a hard spray of water (broadband hiss shaped like falling water) with splashes on the
    grate, a pump hum, and a gurgle from the drain."""
    t = S.t_axis(TOTAL)
    spray = S.bandpass(S.white(TOTAL, 181), 900, 9000) * (0.85 + 0.15 * np.sin(2 * np.pi * 1.5 * t)) * 0.35
    splash = S.bandpass(S.grains(TOTAL, 40, 8, 182), 600, 5000) * 0.35
    pump = (S.tone(TOTAL, 120) * 0.4 + S.tone(TOTAL, 240) * 0.15) * 0.15
    gurgle = S.lowpass(S.brown(TOTAL, 183), 250) * 0.3
    x = S.mix((spray, 1.0), (splash, 1.0), (pump, 1.0), (gurgle, 1.0))
    return S.normalize(S.seamless_loop(x, LOOP, XF, PRE), -8)


def fission_reactor_loop() -> np.ndarray:
    """Fission Reactor: a deep, steady plant hum (coolant pumps at 50 Hz with harmonics), water rushing through the
    core, a low transformer drone, and the odd soft relay tick from the control-rod drives."""
    t = S.t_axis(TOTAL)
    pumps = (np.sin(2 * np.pi * 50 * t) * 0.5 + np.sin(2 * np.pi * 100 * t) * 0.3 + np.sin(2 * np.pi * 150 * t) * 0.12) * 0.3
    flow = S.bandpass(S.pink(TOTAL, 191), 150, 1200) * (0.8 + 0.2 * np.sin(2 * np.pi * 0.5 * t)) * 0.35
    drone = S.lowpass(S.brown(TOTAL, 192), 90) * 0.3
    tick = S.bandpass(S.white(0.02, 193), 1500, 5000) * S.exp_decay(int(0.02 * S.SR), 0.004) * 0.2
    x = S.mix((pumps, 1.0), (flow, 1.0), (drone, 1.0), (_pulses([0.7, -1.3, 2.7], tick), 1.0))
    return S.normalize(S.seamless_loop(x, LOOP, XF, PRE), -7)


def particle_accelerator_loop() -> np.ndarray:
    """Particle Accelerator: a heavy magnet-supply buzz, a high RF cavity tone that swells as the bunch passes the
    injector once a second (matches the 1 s lap), and a faint vacuum-pump chatter."""
    t = S.t_axis(TOTAL)
    supply = S.lowpass(S.tone(TOTAL, 120, "saw"), 900) * 0.15 + np.sin(2 * np.pi * 60 * t) * 0.2
    lap = np.exp(-((np.mod(t - PRE, 1.0) - 0.0) ** 2) / 0.01) + np.exp(-((np.mod(t - PRE, 1.0) - 1.0) ** 2) / 0.01)
    rf = (np.sin(2 * np.pi * 2200 * t) * 0.06 + np.sin(2 * np.pi * 4400 * t) * 0.02) * (0.4 + 0.6 * lap)
    swish = S.bandpass(S.white(TOTAL, 201), 2000, 8000) * (0.05 + 0.25 * lap)
    vacuum = S.bandpass(S.grains(TOTAL, 18, 5, 202), 400, 2500) * 0.08
    x = S.mix((supply, 1.0), (rf, 1.0), (swish, 1.0), (vacuum, 1.0))
    return S.normalize(S.seamless_loop(x, LOOP, XF, PRE), -7)


def fusion_reactor_loop() -> np.ndarray:
    """Fusion Reactor: a deep magnet-supply hum (the field coils), a hissing plasma roar that swells twice a second as
    the bright streak laps the ring (matches the 0.5 s animation), and the chatter of the cryogenic pumps."""
    t = S.t_axis(TOTAL)
    hum = (np.sin(2 * np.pi * 50 * t) * 0.45 + np.sin(2 * np.pi * 100 * t) * 0.3 + np.sin(2 * np.pi * 150 * t) * 0.12) * 0.3
    lap = 0.75 + 0.25 * np.sin(2 * np.pi * 2.0 * (t - PRE)) ** 2
    roar = S.bandpass(S.pink(TOTAL, 221), 300, 5000) * lap * 0.35
    sizzle = S.highpass(S.white(TOTAL, 222), 5000) * 0.05 * lap
    pumps = S.bandpass(S.grains(TOTAL, 12, 6, 223), 600, 2400) * 0.12
    x = S.mix((hum, 1.0), (roar, 1.0), (sizzle, 1.0), (pumps, 1.0))
    return S.normalize(S.seamless_loop(x, LOOP, XF, PRE), -6)


def singularity_reactor_loop() -> np.ndarray:
    """Singularity Reactor: a very low throb pulsing once a second, a glassy shimmer bent downward as if pulled in,
    the electric whine of the five containment emitters, and a faint grainy rush of infalling matter."""
    t = S.t_axis(TOTAL)
    throb = (np.sin(2 * np.pi * 38 * t) + 0.6 * np.sin(2 * np.pi * 76 * t) + 0.3 * np.sin(2 * np.pi * 114 * t)) \
        * (0.55 + 0.45 * np.sin(2 * np.pi * 1.0 * t) ** 2) * 0.35
    shimmer = sum(np.sin(2 * np.pi * f * t - 3 * np.sin(2 * np.pi * r * t)) * a
                  for f, r, a in ((880, 0.5, 0.04), (1320, 1.0, 0.03), (1760, 1.5, 0.02)))
    whine = np.sin(2 * np.pi * 3400 * t) * 0.012 * (0.8 + 0.2 * np.sin(2 * np.pi * 2.0 * t))
    rush = S.bandpass(S.pink(TOTAL, 231), 150, 900) * 0.18
    x = S.mix((throb, 1.0), (shimmer, 1.0), (whine, 1.0), (rush, 1.0))
    return S.normalize(S.seamless_loop(x, LOOP, XF, PRE), -6)


def singularity_collapse() -> dict[str, np.ndarray]:
    """The core collapses: a rising inward rush (reversed swell), a sudden cut, then a heavy sub-bass thump with a
    rolling, darkening rumble and a glassy ring as the cage lets go."""
    length = 4.0
    n = int(length * S.SR)
    swell_len = 1.2
    m = int(swell_len * S.SR)
    swell = S.bandpass(S.pink(swell_len, 241), 200, 6000) * np.linspace(0, 1, m) ** 3
    swell = swell + S.sine_sweep(swell_len, 60, 900, curve=3.0) * np.linspace(0, 1, m) ** 2 * 0.3
    thump = S.saturate(S.sine_sweep(2.0, 80, 22, curve=2.0) * S.exp_decay(int(2.0 * S.SR), 0.6) * 1.6, 1.8)
    rumble = S.sweep_lowpass(S.brown(2.6, 242), 1800, 120) * S.breakpoints(int(2.6 * S.SR), [(0, 0), (0.03, 1.0), (1.0, 0.5), (2.6, 0)])
    ring = (np.sin(2 * np.pi * 1180 * S.t_axis(2.6)) + 0.5 * np.sin(2 * np.pi * 1770 * S.t_axis(2.6))) * S.exp_decay(int(2.6 * S.SR), 0.7) * 0.08
    x = S.mix((S.place(swell, 0.0, length), 1.0), (S.place(thump, swell_len, length), 1.0),
              (S.place(rumble, swell_len, length), 0.6), (S.place(ring, swell_len + 0.05, length), 1.0))
    x = S.reverb(x, seconds=2.0, mix=0.25)[:n]
    return {"reactor/singularity_collapse": S.normalize(S.fade(x, 0.0, 0.3), -1)}


def reactor_alarm() -> dict[str, np.ndarray]:
    """The Fission Reactor's critical klaxon (played every 2 s while the core is critical): two harsh alternating tones,
    low then high, twice, with a buzzy edge from odd harmonics and a hall echo, so it carries and is unmistakable."""
    length = 1.9
    n = int(length * S.SR)
    out = np.zeros(n)
    beat = 0.42
    for k, freq in enumerate((520.0, 690.0, 520.0, 690.0)):
        t = S.t_axis(beat)
        vib = 1 + 0.006 * np.sin(2 * np.pi * 7 * t)
        phase = 2 * np.pi * freq * np.cumsum(vib) / S.SR
        tone = np.sin(phase) + 0.45 * np.sin(3 * phase) + 0.22 * np.sin(5 * phase) + 0.1 * np.sin(7 * phase)
        env = S.adsr(len(t), 0.012, 0.05, 0.85, 0.05)
        out += S.place(S.saturate(tone * env * 0.6, 1.6), k * beat, length)
    out = S.bandpass(out, 220, 5000)
    out = S.reverb(out, seconds=1.2, mix=0.22)[:n]
    return {"reactor/alarm": S.normalize(S.fade(out, 0.0, 0.08), -3)}


def _ring(seconds: float, modes: list[tuple[float, float, float]], seed: int) -> np.ndarray:
    """A struck piece of metal: a click of noise exciting a few decaying resonant modes (freq Hz, decay s, gain)."""
    n = int(seconds * S.SR)
    t = S.t_axis(seconds)
    hit = S.highpass(S.white(seconds, seed), 1500) * S.exp_decay(n, 0.003)
    x = hit * 0.6
    for freq, decay, gain in modes:
        x = x + np.sin(2 * np.pi * freq * t) * np.exp(-t / decay) * gain
    return x


def ordnance_sounds() -> dict[str, np.ndarray]:
    """Thrown explosives (Phase 6): the pin pulled, a fuse lit, a sticky grenade slapping on, smoke pouring out."""
    out = {}
    # Pin: a scrape and one bright click as the pin comes out (a single click: a second, lower one sounded wrong).
    pull = S.bandpass(S.white(0.08, 401), 2000, 7000) * S.exp_decay(int(0.08 * S.SR), 0.02) * 0.6
    click = _ring(0.2, [(3100, 0.02, 0.5), (5200, 0.012, 0.3)], 402)
    x = S.mix((S.place(pull, 0.0, 0.3), 1.0), (S.place(click, 0.07, 0.3), 1.0))
    out["ordnance/grenade_pin"] = S.normalize(S.fade(x, 0, 0.05), -3)
    # Dynamite: a match struck, then the fuse sputtering and fizzing for its three seconds.
    strike = S.bandpass(S.white(0.25, 430), 1500, 8000) * S.breakpoints(int(0.25 * S.SR), [(0, 0), (0.02, 1), (0.25, 0)])
    fizz_len = 3.0
    fizz = S.bandpass(S.white(fizz_len, 431), 2500, 9000) * 0.35
    crackle = S.grains(fizz_len, rate=55, grain_ms=3, seed=432) * 0.5
    sputter = 0.7 + 0.3 * np.sin(2 * np.pi * 9 * S.t_axis(fizz_len)) * np.sin(2 * np.pi * 2.3 * S.t_axis(fizz_len))
    x = S.mix((S.place(strike, 0, 3.3), 1.0), (S.place((fizz + crackle) * sputter, 0.15, 3.3), 1.0))
    out["ordnance/dynamite_light"] = S.normalize(S.fade(x, 0, 0.2), -4)
    # Sticky: a wet slap (a low thump and a short squelch).
    thump = S.lowpass(S.white(0.3, 440), 400) * S.exp_decay(int(0.3 * S.SR), 0.03) * 1.5
    squelch = S.bandpass(S.white(0.3, 441), 300, 1800) * S.breakpoints(int(0.3 * S.SR), [(0, 0), (0.01, 1), (0.08, 0.4), (0.3, 0)])
    out["ordnance/sticky_stick"] = S.normalize(S.fade(S.mix((thump, 1.0), (squelch, 0.7)), 0, 0.05), -3)
    # Smoke: a steady pressurised hiss with a little flutter (played every 3 s while the grenade pours smoke).
    hiss_len = 3.2
    hiss = S.bandpass(S.pink(hiss_len, 450), 1200, 7000)
    flutter = 0.8 + 0.2 * np.sin(2 * np.pi * 13 * S.t_axis(hiss_len))
    out["ordnance/smoke_hiss"] = S.normalize(S.fade(hiss * flutter, 0.15, 0.3), -6)

    # Placed charges (slice 6b).
    def beep(seconds: float, freq: float) -> np.ndarray:
        n = int(seconds * S.SR)
        x = S.tone(seconds, freq, "square") * 0.35 + S.tone(seconds, freq * 2, "sine") * 0.15
        return S.lowpass(x, 6000) * S.adsr(n, 0.004, 0.01, 0.9, 0.01)

    # Arming: a switch click, then two rising beeps.
    click = _ring(0.08, [(2400, 0.01, 0.4), (4100, 0.006, 0.2)], 460)
    x = S.mix((S.place(click, 0.0, 0.42), 0.4), (S.place(beep(0.09, 1800), 0.08, 0.42), 0.8), (S.place(beep(0.12, 2400), 0.22, 0.42), 0.8))
    out["ordnance/charge_arm"] = S.normalize(S.fade(x, 0, 0.03), -4)
    # Made safe: the click, then two falling beeps.
    x = S.mix((S.place(click, 0.0, 0.42), 0.4), (S.place(beep(0.09, 2400), 0.08, 0.42), 0.8), (S.place(beep(0.12, 1600), 0.22, 0.42), 0.8))
    out["ordnance/charge_disarm"] = S.normalize(S.fade(x, 0, 0.03), -4)
    # The timed bomb's beep: short and piercing.
    out["ordnance/bomb_beep"] = S.normalize(S.fade(beep(0.09, 2900), 0, 0.01), -5)
    # Thermite: a roaring, crackling burn (played every second while it burns).
    burn_len = 1.2
    roar = S.lowpass(S.brown(burn_len, 470), 900) * 1.2
    fizz = S.bandpass(S.white(burn_len, 471), 2500, 9000) * 0.5
    crackle = S.grains(burn_len, rate=90, grain_ms=2, seed=472) * 0.6
    x = (roar + fizz + crackle) * (0.85 + 0.15 * np.sin(2 * np.pi * 7 * S.t_axis(burn_len)))
    out["ordnance/thermite_burn"] = S.normalize(S.fade(x, 0.05, 0.15), -3)
    # The detonator: a heavy switch, then a short burst of radio squelch.
    thunk = _ring(0.1, [(900, 0.02, 0.6), (2100, 0.01, 0.3)], 480)
    squelch = S.bandpass(S.white(0.18, 481), 1200, 4500) * S.breakpoints(int(0.18 * S.SR), [(0, 0), (0.01, 1), (0.15, 0.7), (0.18, 0)]) * 0.5
    x = S.mix((S.place(thunk, 0.0, 0.3), 1.0), (S.place(squelch, 0.08, 0.3), 1.0))
    out["ordnance/detonator_click"] = S.normalize(S.fade(x, 0, 0.03), -4)
    # A wire cut: a sharp snip of the cutters.
    snip = _ring(0.12, [(3600, 0.012, 0.5), (5800, 0.008, 0.3), (1500, 0.02, 0.2)], 490)
    out["ordnance/wire_snip"] = S.normalize(S.fade(snip, 0, 0.03), -4)
    # Mines (slice 6c): the plate clicking down under a boot, and the bouncing mine's launch charge thumping it up.
    clk = _ring(0.15, [(1700, 0.015, 0.6), (3900, 0.008, 0.3), (700, 0.03, 0.3)], 500)
    out["ordnance/mine_click"] = S.normalize(S.fade(clk, 0, 0.03), -3)
    thump = S.lowpass(S.white(0.35, 510), 500) * S.exp_decay(int(0.35 * S.SR), 0.04) * 1.4
    hiss = S.bandpass(S.white(0.35, 511), 1500, 6000) * S.exp_decay(int(0.35 * S.SR), 0.08) * 0.4
    out["ordnance/mine_pop"] = S.normalize(S.fade(S.mix((thump, 1.0), (hiss, 1.0)), 0, 0.05), -2)
    # Bombs (slice 6d): the thermobaric's fuel cloud bursting out (a dull thud and a rushing roar that swells), and the
    # bunker buster grinding down through rock.
    n = 0.9
    whump = S.lowpass(S.white(n, 520), 200) * S.exp_decay(int(n * S.SR), 0.08) * 1.6
    rush = S.sweep_lowpass(S.pink(n, 521), 600, 4500) * S.breakpoints(int(n * S.SR), [(0, 0), (0.05, 0.6), (0.5, 1), (0.9, 0)])
    out["ordnance/fuel_disperse"] = S.normalize(S.fade(S.mix((whump, 1.0), (rush, 0.8)), 0, 0.1), -2)
    n = 2.0
    grind = S.bandpass(S.white(n, 530), 150, 1400) * (0.7 + 0.3 * np.sin(2 * np.pi * 23 * S.t_axis(n)))
    crunch = S.grains(n, rate=40, grain_ms=6, seed=531) * 0.8
    rumble = S.lowpass(S.brown(n, 532), 120) * 1.4
    out["ordnance/bomb_drill"] = S.normalize(S.fade(S.mix((grind, 1.0), (crunch, 0.8), (rumble, 1.0)), 0.05, 0.4), -3)
    # A falling bomb's whistle: a falling tone with a slight warble over rushing air, louder as it comes down.
    n = 3.5
    tt = S.t_axis(n)
    freq = 1700 * np.exp(-tt / 3.2) + 450 + 12 * np.sin(2 * np.pi * 6 * tt)
    tone = np.sin(2 * np.pi * np.cumsum(freq) / S.SR) * 0.5
    air = S.bandpass(S.white(n, 540), 800, 5000) * 0.25
    swell = np.clip(tt / n, 0, 1) ** 1.5 * 0.8 + 0.2
    out["ordnance/bomb_whistle"] = S.normalize(S.fade((tone + air) * swell, 0.3, 0.15), -2)
    return out


def machine_loops() -> dict[str, np.ndarray]:
    return {
        "machine/combustion_generator": generator_loop(),
        "machine/ore_crusher": crusher_loop(),
        "machine/arc_furnace": arc_loop(),
        "machine/industrial_press": press_loop(),
        "machine/electrolytic_separator": separator_loop(),
        "machine/cryogenic_condenser": condenser_loop(),
        "machine/chemical_plant": chemical_plant_loop(),
        "machine/water_pump": water_pump_loop(),
        "machine/circuit_fabricator": circuit_fabricator_loop(),
        "machine/gas_centrifuge": gas_centrifuge_loop(),
        "machine/fusion_fuel_synthesizer": fusion_fuel_synthesizer_loop(),
        "machine/canning_machine": canning_machine_loop(),
        "machine/exotic_matter_fabricator": exotic_matter_fabricator_loop(),
        "machine/diesel_generator": diesel_generator_loop(),
        "machine/rtg": rtg_loop(),
        "machine/battery_charger": battery_charger_loop(),
        "machine/decontamination_shower": decontamination_shower_loop(),
        "machine/fission_reactor": fission_reactor_loop(),
        "machine/fusion_reactor": fusion_reactor_loop(),
        "machine/singularity_reactor": singularity_reactor_loop(),
        "machine/particle_accelerator": particle_accelerator_loop(),
    }


def _air_raid_siren() -> np.ndarray:
    """A mechanical air-raid siren for the whole 10 s countdown: a motor spinning two slotted rotors up from a growl to a
    howl (the rotors a minor third apart, so it sounds as an ominous chord), holding, dipping into one wail, then winding
    down as the motor coasts. Each rotor chops air: a buzzy, harmonic-rich tone with breath in it. Heard outdoors, with
    a slap of echo off the surroundings."""
    length = 10.0
    t = S.t_axis(length)
    top = 440.0
    # The motor's speed (as the lower rotor's pitch): spin up, hold, one wail down and back, then coast down.
    spin = np.where(t < 3.0, 70 + (top - 70) * (1 - np.exp(-t / 0.9)) / (1 - np.exp(-3.0 / 0.9)), top)
    wail = np.where((t > 5.2) & (t < 7.2), 0.25 * np.sin(np.pi * (t - 5.2) / 2.0) ** 2, 0.0)
    coast = np.where(t > 7.6, np.exp(-(t - 7.6) / 1.1), 1.0)
    f = spin * (1 - wail) * coast * (1 + 0.004 * np.sin(2 * np.pi * 0.7 * t))
    out = np.zeros_like(t)
    for ratio, gain, seed in ((1.0, 1.0, 630), (1.2, 0.8, 631)):
        phase = 2 * np.pi * np.cumsum(f * ratio) / S.SR
        tone = np.zeros_like(t)
        for k in range(1, 11):                             # the chopped-air tone: rounded, a little buzz on top
            tone += np.sin(k * phase + 0.3 * k) / k ** 1.45
        breath = S.bandpass(S.white(length, seed), 700, 4000) * (0.5 + 0.5 * np.sin(phase)) ** 8 * 0.3
        out += (S.saturate(tone * 0.45, 1.15) + breath) * gain
    loud = np.clip(f / top, 0, 1) ** 1.6                     # louder as it spins faster
    out = S.bandpass(out * loud, 120, 6500)
    out = S.echo(out, delay=0.21, feedback=0.3, repeats=3, lp_hz=2500)[: len(t)]
    out = S.reverb(out, seconds=1.8, damping_hz=3500, mix=0.25)[: len(t)]
    return S.normalize(S.fade(out, 0.05, 0.6), -2)


def launch_sounds() -> dict[str, np.ndarray]:
    """Launching (DESIGN.md §17): the launch siren, the missile lighting on the pad, its engine in flight."""
    out = {}
    out["launch/siren"] = _air_raid_siren()
    # Ignition: a deep crack, then a huge tearing roar with crackle that builds and holds.
    n = 5.0
    t = S.t_axis(n)
    crack = S.lowpass(S.white(n, 610), 600) * S.exp_decay(int(n * S.SR), 0.15) * 1.5
    roar = S.bandpass(S.brown(n, 611), 30, 900) * 1.4
    hiss = S.bandpass(S.white(n, 612), 1500, 7000) * 0.35
    crackle = S.grains(n, rate=120, grain_ms=3, seed=613) * 0.5
    build = np.clip(t / 1.2, 0, 1) ** 0.7
    out["launch/missile_launch"] = S.normalize(S.fade(S.saturate(crack + (roar + hiss + crackle) * build, 1.6), 0.0, 0.8), -1)
    # Engine: the roar from a distance, a steady loopable 2 s.
    n = 2.0
    roar = S.bandpass(S.brown(n, 620), 30, 700) * 1.3 + S.bandpass(S.pink(n, 621), 400, 3000) * 0.3
    out["launch/missile_engine"] = S.normalize(S.fade(roar, 0.1, 0.3), -3)
    return out


RECIPES = [geiger_clicks, nuke_set, blast_sets, machine_loops, singularity_collapse, reactor_alarm, ordnance_sounds, launch_sounds]


def build_sounds() -> list[Path]:
    written = []
    for recipe in RECIPES:
        for rel, samples in recipe().items():
            written.append(S.save_ogg(samples, paths.SOUNDS / f"{rel}.ogg"))
    return written


if __name__ == "__main__":
    for p in build_sounds():
        print(p)
