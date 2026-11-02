"""The Particle Uplink Cannon (DESIGN.md §19.6), from real recordings of electricity, lightning and fire where the world
makes such sounds, and synthesis where nothing could be recorded (a beam from orbit).

- Its charged core (2 s): the buzz of an arc climbing a Jacob's ladder and high-voltage sparks snapping, swelling and
  dying; the cannon plays it every few seconds while it waits, charged.
- Its uplink (4.5 s): a charging whine climbing over the transformer's hum, then the discharge, a lightning crack with
  sparks spraying, and a roar going up and away (a jet's afterburner swept up in pitch as it fades: the beam climbing
  to the satellite).
- Its strike (5 s): the beam coming down from orbit: a close lightning crack, a thunderclap's boom under it slowed and
  darkened, a heavy thump, the ground starting to sizzle; a long rolling tail.
- Its burn (a 4 s loop): a deep roar (the afterburner slowed and darkened) over a drone, sparks crackling all through
  it and the sizzle of melting ground (arc welding).
- Its end (3 s): a falling whine as the beam dies, the last sparks and sizzle fading.

Every cut names its recording (sources/ledger.json, credited on the CurseForge page) and where in it.

    tools/.venv/Scripts/python.exe tools/sound/uplink.py      (writes them; recipes.py builds them with the rest)
"""
from __future__ import annotations

import numpy as np

import sources as R
import synth as S

# (recording, from s, to s, the quiet stretch its background is measured on[, options: hp/lp Hz, fade s, gain dB])
CUTS = {
    "ladder": ("jacobs_ladder", 1.2, 3.6, None, {"fade": 0.4}),            # an arc climbing the ladder, buzzing
    "ladder_b": ("jacobs_ladder", 20.0, 24.5, None, {"fade": 0.4}),        # ... a quieter, steadier climb
    "sparks": ("hv_sparks", 3.0, 7.5, None, {"fade": 0.3}),                 # high-voltage sparks snapping
    "sparks_b": ("hv_sparks", 36.0, 41.0, None, {"fade": 0.3}),
    "hum": ("transformer_hum", 4.0, 9.0, None, {"fade": 0.3}),              # a transformer's hum
    "crack": ("lightning_close_b", 1.2, 4.6, None, {"fade": 0.8}),          # a lightning strike close by: its crack
    "strike": ("lightning_close_a", 1.6, 9.0, None, {"fade": 1.5}),         # ... another, and its rumble
    "boom": ("thunder_close_neumann", 1.6, 9.5, None, {"fade": 2.0}),       # a thunderclap close by
    "roar": ("afterburner", 0.0, 5.6, None, {"fade": 0.4}),                 # a jet's afterburner, its roar
    "sizzle": ("arc_welding", 10.8, 15.2, None, {"fade": 0.3}),             # arc welding: a steady sizzle
    "sizzle_b": ("arc_welding", 25.6, 29.8, None, {"fade": 0.3}),
}


def take(name: str, speed: float = 1.0, aligned: bool = False) -> np.ndarray:
    return R.take(CUTS, name, speed, aligned)


def at(x: np.ndarray, db: float) -> np.ndarray:
    return R.match(x, db)


def envelope(n: int, attack: float, hold: float, release: float) -> np.ndarray:
    """A swell: up over {attack} s, held {hold} s, then falling away over {release} s (a raised-cosine tail)."""
    t = np.arange(n) / S.SR
    up = np.clip(t / max(attack, 1e-3), 0, 1)
    down = np.clip((t - attack - hold) / max(release, 1e-3), 0, 1)
    return up * (0.5 * (1 + np.cos(np.pi * down)))


def whine(seconds: float, f0: float, f1: float, seed: int) -> np.ndarray:
    """A charging (or dying) whine gliding from f0 to f1: a few harmonics, a little roughness, as a capacitor bank's."""
    n = int(seconds * S.SR)
    t = np.arange(n) / S.SR
    f = f0 * (f1 / f0) ** (t / seconds)
    phase = 2 * np.pi * np.cumsum(f) / S.SR
    rng = np.random.default_rng(seed)
    x = np.sin(phase) + 0.35 * np.sin(2 * phase + 0.3) + 0.15 * np.sin(3 * phase + 1.1)
    x *= 1 + 0.08 * S.lowpass(rng.standard_normal(n), 40, order=2) * 10
    return x


def drone(seconds: float, seed: int) -> np.ndarray:
    """The beam's drone: a deep hum (three partials) breathing slowly and wavering in pitch (never one steady tone), its
    edge roughened."""
    t = S.t_axis(seconds)
    swell = 0.75 + 0.25 * np.sin(2 * np.pi * 0.5 * t) * np.sin(2 * np.pi * 0.25 * t + 1.0)
    wobble = 1 + 0.035 * np.sin(2 * np.pi * 0.75 * t) + 0.02 * np.sin(2 * np.pi * 1.75 * t + 0.4)
    phase = 2 * np.pi * np.cumsum(58 * wobble) / S.SR
    x = (np.sin(phase) + 0.55 * np.sin(2.007 * phase + 0.7) + 0.2 * np.sin(3.01 * phase)) * swell
    return S.saturate(x + 0.15 * S.lowpass(S.brown(seconds, seed), 300), 1.6)


def charged() -> np.ndarray:
    """The charged core: an arc's buzz and sparks snapping, swelling in and dying away."""
    n = 2.0
    m = int(n * S.SR)
    buzz = take("ladder")[:m]
    buzz = S.highpass(buzz, 380) * envelope(len(buzz), 0.25, 0.8, 0.8)     # (its mains hum's low partials out: no steady tone)
    snaps = take("sparks", 1.05)[:m]
    snaps = S.highpass(snaps, 900) * envelope(len(snaps), 0.15, 0.9, 0.8)
    x = R.layer(n, (at(buzz, -27), 0.0, 1.0), (at(snaps, -22), 0.05, 1.0))          # (the snaps over the arc's buzz)
    x = S.reverb(x, seconds=0.9, damping_hz=4500, mix=0.14, seed=4101)[:m]
    return R.finish(R.tail_fade(x, 0.4), -1.0, 0.2)


def uplink() -> np.ndarray:
    """The uplink: a whine climbing over the hum, then the discharge's crack and spray of sparks, and a roar going up."""
    n = 4.5
    m = int(n * S.SR)
    charge = whine(0.85, 160, 1500, 4111) * envelope(int(0.85 * S.SR), 0.5, 0.25, 0.1)
    hum = take("hum")[: int(1.2 * S.SR)]
    hum = hum * envelope(len(hum), 0.7, 0.2, 0.3)
    crack = take("crack", 0.92, aligned=True)
    crack = crack * envelope(len(crack), 0.002, 0.6, 2.4)
    spray = S.highpass(take("sparks_b")[: int(1.6 * S.SR)], 1200)
    spray = spray * envelope(len(spray), 0.01, 0.3, 1.2)
    up = R.glide(S.lowpass(take("roar"), 6000), 0.7, 1.45)[: int(3.6 * S.SR)]
    up = up * envelope(len(up), 0.08, 0.6, 2.8)
    x = R.layer(n, (at(hum, -30), 0.0, 1.0), (at(charge, -24), 0.0, 1.0), (at(crack, -15), 0.82, 1.0), (at(spray, -24), 0.84, 1.0),
                (at(up, -19), 0.86, 1.0))
    x = S.reverb(x, seconds=2.0, damping_hz=3800, mix=0.2, seed=4112)[:m]
    return R.finish(R.tail_fade(x, 0.8), -1.0, 0.4)


def strike() -> np.ndarray:
    """The strike: a crack, a boom slowed and darkened under it, a thump, the ground starting to sizzle; a rolling tail."""
    n = 5.0
    m = int(n * S.SR)
    crack = take("strike", aligned=True)[:m]
    crack = crack * envelope(len(crack), 0.002, 1.0, 3.5)
    boom = S.lowpass(take("boom", 0.72, aligned=True), 900)[:m]
    boom = boom * envelope(len(boom), 0.01, 1.2, 3.4)
    thump = S.lowpass(S.white(1.0, 4121), 110)[: int(1.0 * S.SR)] * S.exp_decay(int(1.0 * S.SR), 0.18) * 3.0
    sizzle = S.highpass(take("sizzle"), 700)[:m]
    sizzle = sizzle * envelope(len(sizzle), 0.4, 1.6, 2.0)
    x = R.layer(n, (at(thump, -15), 0.0, 1.0), (at(crack, -15), 0.0, 1.0), (at(boom, -18), 0.02, 1.0), (at(sizzle, -27), 0.25, 1.0))
    x = S.reverb(x, seconds=2.6, damping_hz=3000, mix=0.2, seed=4122)[:m]
    return R.finish(R.tail_fade(x, 1.2), -1.0, 0.6)


def beam_loop() -> np.ndarray:
    """The burn, a 4 s loop: a deep roar and drone, sparks crackling through it, the sizzle of melting ground."""
    length = 4.0
    total = 0.25 + length + 0.3
    k = int(total * S.SR)
    roar = R.varispeed(S.lowpass(take("roar"), 3200), 0.62)
    roar = np.concatenate([roar, roar])[:k]
    sparks = S.bandpass(np.concatenate([take("sparks"), take("sparks_b")]), 600, 9000)[:k]
    sizzle = S.highpass(np.concatenate([take("sizzle_b"), take("sizzle")]), 900)[:k]
    hum = drone(total, 4131)[:k]
    x = at(roar, -20) + at(hum, -29) + at(sparks, -27) + at(sizzle, -29)
    return S.normalize(S.seamless_loop(x, length, 0.3, 0.25), -2)


def fade() -> np.ndarray:
    """The beam dying: a whine falling away, the last sparks and sizzle fading."""
    n = 3.0
    m = int(n * S.SR)
    fall = whine(1.6, 1300, 110, 4141) * envelope(int(1.6 * S.SR), 0.02, 0.5, 1.0)
    sparks = S.highpass(take("sparks", 0.9)[:m], 800) * envelope(m, 0.01, 0.4, 1.6)
    sizzle = S.highpass(take("sizzle_b")[:m], 900) * envelope(m, 0.01, 0.6, 1.8)
    roar = R.glide(S.lowpass(take("roar"), 2500), 0.7, 0.35)[:m]
    roar = roar * envelope(len(roar), 0.005, 0.2, 1.4)
    x = R.layer(n, (at(roar, -20), 0.0, 1.0), (at(fall, -25), 0.0, 1.0), (at(sparks, -27), 0.0, 1.0), (at(sizzle, -29), 0.0, 1.0))
    x = S.reverb(x, seconds=1.6, damping_hz=3500, mix=0.18, seed=4142)[:m]
    return R.finish(R.tail_fade(x, 0.8), -1.0, 0.4)


#: How loud each is (its loudest 3 s, LUFS): the strike as loud as a big blast's near sound, the uplink a little under
#: it, the burn's loop and its end under that, the charged crackle a machine's.
LOUDNESS = {"superweapon/particle_charged": -22.0, "superweapon/particle_uplink": -17.0, "superweapon/particle_strike": -15.5,
            "superweapon/particle_beam_loop": -18.5, "superweapon/particle_fade": -20.0}


def uplink_set() -> dict[str, np.ndarray]:
    made = {"superweapon/particle_charged": charged(), "superweapon/particle_uplink": uplink(), "superweapon/particle_strike": strike(),
            "superweapon/particle_beam_loop": beam_loop(), "superweapon/particle_fade": fade()}
    return {rel: R.loud_as(x, LOUDNESS[rel]) for rel, x in made.items()}


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from gb import paths  # noqa: E402
    for rel, x in uplink_set().items():
        print(S.save_ogg(x, paths.SOUNDS / f"{rel}.ogg"))
