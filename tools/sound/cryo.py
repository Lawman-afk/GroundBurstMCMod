"""The cryogenic warhead's deep freeze (DESIGN.md §16.16), from real recordings where the world makes the sound and
synthesis where nothing could be recorded.

- Its burst (7 s): a deep thump as the heat sink lets go, then the roar of its cryogen boiling off (liquid nitrogen
  poured out, played slow: a far bigger dump, its dewar valve's whistle notched out), ice cracking all through it (the
  cracks played slow and heavy), a rumble under it all (synthesized: the recorded ones hum) and a long cold tail.
- Its front (two, 2.4 s): the crackle of the ground freezing round you as the front rolls past, a rush of cold air
  swelling and passing; ice cracking quickly, close by.
- A spire (two, 2.6 s): a crunch as it breaks the ground, a deep groan of ice growing (the cracks slowed right down) and a
  glassy shimmer over it.
- Its cold snap's wind (a 4 s loop): synthesized, thinner and higher than the storm's: a whistle round corners drifting
  in and out, a low moan in gusts and the hiss of blown snow.

Every cut names its recording (sources/ledger.json, credited on the CurseForge page) and where in it.

    tools/.venv/Scripts/python.exe tools/sound/cryo.py      (writes them; recipes.py builds them with the rest)
"""
from __future__ import annotations

import numpy as np
from scipy import signal

import sources as R
import synth as S

# (recording, from s, to s, the quiet stretch its background is measured on[, options: hp/lp Hz, fade s, gain dB])
CUTS = {
    "boil": ("liquid_nitrogen", 10.0, 17.0, None, {"fade": 0.6}),        # liquid nitrogen pouring out, boiling off
    "boil_short": ("liquid_nitrogen", 20.0, 22.5, None, {"fade": 0.5}),   # ... a shorter rush
    "cracks": ("ice_cracking", 26.0, 33.5, None, {"fade": 0.6}),        # ice cracking, crack on crack
    "cracks_b": ("ice_cracking", 8.6, 14.6, None, {"fade": 0.5}),       # ... another run, its loudest crack first
    "groan": ("ice_cracking", 17.6, 21.4, None, {"fade": 0.5}),         # a few slower cracks (slowed: ice groaning)
    "groan_b": ("ice_cracking", 36.0, 40.0, None, {"fade": 0.5}),
    "crack": ("ice_cracking", 8.85, 9.45, None, {"fade": 0.25}),        # one sharp crack
}


def take(name: str, speed: float = 1.0, aligned: bool = False) -> np.ndarray:
    return R.take(CUTS, name, speed, aligned)


def at(x: np.ndarray, db: float) -> np.ndarray:
    return R.match(x, db)


def boil(name: str) -> np.ndarray:
    """A nitrogen cut with its dewar valve's whistle (about 2.2-2.4 kHz in the recording) notched out: slowed down it
    would sing through the burst as one long tone."""
    x = take(name)
    for band in ((2080, 2580),) + (((312, 362),) if name == "boil_short" else ()):     # (and a hum in the short one)
        x = signal.sosfiltfilt(signal.butter(4, band, btype="bandstop", fs=S.SR, output="sos"), x)
    return x


def envelope(n: int, attack: float, hold: float, release: float) -> np.ndarray:
    """A swell: up over {attack} s, held {hold} s, then falling away over {release} s (a raised-cosine tail)."""
    t = np.arange(n) / S.SR
    up = np.clip(t / max(attack, 1e-3), 0, 1)
    down = np.clip((t - attack - hold) / max(release, 1e-3), 0, 1)
    return up * (0.5 * (1 + np.cos(np.pi * down)))


def shimmer(seconds: float, seed: int, base: float = 2600.0) -> np.ndarray:
    """A glassy shimmer: a few high partials (inharmonic, as a struck crystal's) each ringing down from its own start."""
    n = int(seconds * S.SR)
    t = np.arange(n) / S.SR
    rng = np.random.default_rng(seed)
    out = np.zeros(n)
    for k in range(7):
        f = base * (1 + k * 0.53 + rng.uniform(-0.05, 0.05))
        start = rng.uniform(0.05, seconds * 0.6)
        tt = np.clip(t - start, 0, None)
        out += np.sin(2 * np.pi * f * tt + rng.uniform(0, 6.28)) * np.exp(-tt / rng.uniform(0.25, 0.6)) * (t >= start) * rng.uniform(0.4, 1.0)
    return out / 7


def burst() -> np.ndarray:
    """The burst: a thump, the cryogen's roar swelling and dying away, ice cracking all through it, a rumble under it."""
    n = 7.0
    m = int(n * S.SR)
    thump = S.lowpass(S.white(1.2, 3101), 120)[: int(1.2 * S.SR)] * S.exp_decay(int(1.2 * S.SR), 0.22) * 3.0
    drop = np.sin(2 * np.pi * np.cumsum(70 - 38 * np.clip(np.arange(int(1.2 * S.SR)) / S.SR / 0.5, 0, 1)) / S.SR)
    drop *= S.exp_decay(int(1.2 * S.SR), 0.3) * 1.4
    roar = R.varispeed(boil("boil"), 0.55)[:m]
    roar = S.lowpass(roar, 5200) * envelope(len(roar), 0.12, 1.2, 4.6)
    cracks = R.varispeed(take("cracks"), 0.78)[: int(6.4 * S.SR)]
    cracks = cracks * envelope(len(cracks), 0.05, 2.2, 3.6)
    rumble = S.lowpass(S.brown(n, 3103), 140)[:m] * envelope(m, 0.08, 1.5, 4.2)      # a heavy rumble (synthesized: no hum)
    x = R.layer(n, (at(thump + drop, -14), 0.0, 1.0), (at(roar, -20), 0.04, 1.0), (at(cracks, -22), 0.35, 1.0),
                (at(rumble, -24), 0.0, 1.0))
    x = S.reverb(x, seconds=2.4, damping_hz=2600, mix=0.22, seed=3102)[:m]
    return R.finish(R.tail_fade(x, 1.2), -1.0, 0.6)


def front(variant: int) -> np.ndarray:
    """The front rolling past: a rush of cold air swelling and passing, the ground crackling as it freezes round you."""
    n = 2.4
    m = int(n * S.SR)
    cracks = R.varispeed(take("cracks" if variant == 0 else "cracks_b"), 1.3 if variant == 0 else 1.2)[:m]
    cracks = cracks * envelope(len(cracks), 0.35, 0.4, 1.4)
    rush = R.varispeed(boil("boil_short"), 0.9)[:m]
    rush = S.bandpass(rush, 300, 6000) * envelope(len(rush), 0.5, 0.2, 1.5)
    x = R.layer(n, (at(rush, -24), 0.0, 1.0), (at(cracks, -21), 0.08, 1.0))
    x = S.reverb(x, seconds=1.2, damping_hz=3500, mix=0.15, seed=3110 + variant)[:m]
    return R.finish(R.tail_fade(x, 0.5), -1.0, 0.3)


def spire(variant: int) -> np.ndarray:
    """A spire bursting up: a crunch, a deep groan of ice growing, a glassy shimmer over it."""
    n = 2.6
    m = int(n * S.SR)
    crunch = take("crack", 0.8 if variant == 0 else 0.7, aligned=True)
    groan = R.varispeed(take("groan" if variant == 0 else "groan_b"), 0.5)[:m]
    groan = S.lowpass(groan, 2400) * envelope(len(groan), 0.1, 1.0, 1.4)
    glass = shimmer(n, 3120 + variant, 2400 if variant == 0 else 2900)
    x = R.layer(n, (at(crunch, -18), 0.0, 1.0), (at(groan, -20), 0.05, 1.0), (at(glass, -30), 0.15, 1.0))
    x = S.reverb(x, seconds=1.4, damping_hz=4000, mix=0.2, seed=3130 + variant)[:m]
    return R.finish(R.tail_fade(x, 0.5), -1.0, 0.3)


def wind() -> np.ndarray:
    """The cold snap's wind, a 4 s loop: a thin whistle (three resonances, each drifting in and out on its own slow
    swell, all repeating in the loop), a low moan in gusts, the hiss of blown snow."""
    length = 4.0
    total = 0.25 + length + 0.3
    t = S.t_axis(total)
    gust = np.clip(0.55 + 0.45 * S.lowpass(S.white(total, 3141), 0.7, order=2) * 16, 0.2, 1.3)
    whistle = sum(S.resonator(S.white(total, 3142 + k), f, q=34) * a * (0.55 + 0.45 * np.sin(2 * np.pi * r * t + ph))
                  for k, (f, a, r, ph) in enumerate(((1250, 0.09, 0.25, 0.0), (1720, 0.065, 0.5, 2.0), (2380, 0.04, 0.25, 4.0))))
    whistle = whistle * (0.35 + 0.65 * gust)
    moan = S.lowpass(S.pink(total, 3146), 520) * gust * 0.5
    snow = S.highpass(S.white(total, 3147), 4200) * 0.05 * (0.4 + 0.6 * gust)
    x = moan + whistle + snow
    return S.normalize(S.seamless_loop(S.saturate(x, 1.1), length, 0.3, 0.25), -4)


#: How loud each is (its loudest 3 s, LUFS): the burst a little louder than the antimatter warhead's, the rest as the
#: storm's own sounds are.
LOUDNESS = {"exotic/cryo_icbm_burst": -16.5, "exotic/cryo_front1": -19.5, "exotic/cryo_front2": -19.5,
            "exotic/cryo_spire1": -19.0, "exotic/cryo_spire2": -19.0, "exotic/cryo_wind": -19.0}


def cryo_set() -> dict[str, np.ndarray]:
    made = {"exotic/cryo_icbm_burst": burst(), "exotic/cryo_front1": front(0), "exotic/cryo_front2": front(1),
            "exotic/cryo_spire1": spire(0), "exotic/cryo_spire2": spire(1), "exotic/cryo_wind": wind()}
    return {rel: R.loud_as(x, LOUDNESS[rel]) for rel, x in made.items()}


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from gb import paths  # noqa: E402
    for rel, x in cryo_set().items():
        print(S.save_ogg(x, paths.SOUNDS / f"{rel}.ogg"))
