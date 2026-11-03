"""Artillery (DESIGN.md §20.1): the guns firing, from real recordings, and a shell coming in.

- The mortar firing (two takes, 1.6 s each): an M-327 mortar's two shots, each with a deep thump laid under its crack
  (the charge going off in the tube) and a short open-air tail.
- The 155 mm howitzer firing (two takes, about 2.5 s): a K-55's rounds (a 155 mm self-propelled howitzer), each with a
  deeper thump and a slow shove of air under it and a longer open-air tail.
- The Atomic Cannon firing (about 5 s): a heavy gun's deep boom slowed to 0.7 (lower and longer: a far bigger charge),
  its next round slower still under it, a falling whump and a slow shove for the mass of air it pushes, the K-55's
  crack on top so it still cracks, and the boom coming back off far ground 1.3 s later.
- A shell coming in (2.2 s, its last two seconds): a falling whistle with a little warble over rushing air, louder as
  it comes down, cut off where it lands (the burst is its own sound). Synthesized, as the falling bomb's is (the
  recording of falling bombs carries other bombs in its air).

Every cut names its recording (sources/ledger.json, credited on the CurseForge page) and where in it.

    tools/.venv/Scripts/python.exe tools/sound/artillery.py      (writes them; recipes.py builds them with the rest)
"""
from __future__ import annotations

import numpy as np

import sources as R
import synth as S

# (recording, from s, to s, the quiet stretch its background is measured on[, options: hp/lp Hz, fade s, gain dB])
CUTS = {
    "shot_a": ("mortar_shots", 0.0, 1.55, None, {"fade": 0.45}),          # an M-327 mortar firing: crack, ring, tail
    "shot_b": ("mortar_shots", 2.0, 3.55, None, {"fade": 0.45}),          # ... its second shot
    "k55_a": ("artillery_k55", 6.00, 7.92, None, {"fade": 0.5}),          # a K-55 (155 mm) firing: crack, rolling tail
    "k55_b": ("artillery_k55", 7.98, 9.92, None, {"fade": 0.5}),          # ... its next round (before the video's cut)
    "army_b": ("artillery_army", 3.04, 5.04, None, {"fade": 0.5}),        # a heavy gun firing: one deep boom
    "army_c": ("artillery_army", 5.06, 6.91, None, {"fade": 0.5}),        # ... its next round
}

LOUDNESS = {"artillery/mortar_fire1": -14.5, "artillery/mortar_fire2": -14.5, "artillery/shell_incoming": -19.0,
            "artillery/howitzer_fire1": -12.5, "artillery/howitzer_fire2": -12.5, "artillery/atomic_fire": -11.0}


def take(name: str, speed: float = 1.0, aligned: bool = False) -> np.ndarray:
    return R.take(CUTS, name, speed, aligned)


def thump(seconds: float, hz: float, seed: int, decay: float = 0.09) -> np.ndarray:
    """The charge in the tube: a deep thud, quick to rise and dying over {decay} s (longer for a bigger charge)."""
    n = int(seconds * S.SR)
    body = S.lowpass(S.brown(seconds, seed), hz) * S.exp_decay(n, decay)
    tt = S.t_axis(seconds)
    drop = np.sin(2 * np.pi * np.cumsum(70 * np.exp(-tt / 0.08) + 38) / S.SR) * S.exp_decay(n, decay * 4 / 3) * 0.6
    return S.normalize(body + drop, -3)


def whump(seconds: float, f0: float, f1: float, decay: float) -> np.ndarray:
    """The low whump of a great mass of air pushed out at once: a sine falling in pitch, saturated."""
    return S.saturate(S.sine_sweep(seconds, f0, f1, curve=2.5) * S.exp_decay(int(seconds * S.SR), decay) * 1.6, 1.8)


def mortar_fire(cut: str, seed: int) -> np.ndarray:
    shot = take(cut)
    total = len(shot) / S.SR + 0.25
    low = thump(0.7, 160, seed)
    mixed = R.layer(total, (shot, 0.0, 1.0), (R.match(low, -20), 0.0, 1.0))
    tail = S.reverb(mixed, seconds=0.9, damping_hz=2500, mix=0.18, seed=seed + 1)
    return R.tail_fade(S.normalize(tail, -1), 0.3)


def howitzer_fire(cut: str, seed: int) -> np.ndarray:
    shot = take(cut, aligned=True)
    ref = R.rms_db(shot)
    total = len(shot) / S.SR + 0.9
    low = thump(1.1, 120, seed, decay=0.15)
    shove = S.lowpass(R.friedlander(0.012, 0.5), 500, 2)
    mixed = R.layer(total, (shot, 0.0, 1.0), (R.match(low, ref - 4), 0.0, 1.0), (R.match(shove, ref - 7), 0.0, 1.0))
    wet = S.reverb(mixed, seconds=1.8, damping_hz=2000, mix=0.2, seed=seed + 1)
    return R.finish(R.punch(wet, 2), tail=0.7)


def atomic_fire(seed: int) -> np.ndarray:
    boom = take("army_b", 0.7, aligned=True)
    under = take("army_c", 0.55, aligned=True)
    crack = take("k55_a", aligned=True)
    ref = R.rms_db(boom)
    total = 6.0
    body = R.layer(total, (boom, 0.0, 1.0), (R.match(under, ref - 5), 0.015, 1.0), (R.match(crack, ref - 7), 0.0, 1.0),
                   (R.match(whump(2.4, 90, 36, 0.45), ref - 2), 0.0, 1.0),
                   (R.match(S.lowpass(R.friedlander(0.03, 1.2), 220, 2), ref - 4), 0.0, 1.0))
    echo = R.match(R.air(body, 900), ref - 12)
    x = R.layer(total, (body, 0.0, 1.0), (echo, 1.3, 1.0))
    wet = S.reverb(x, seconds=3.2, damping_hz=1500, mix=0.25, seed=seed + 1, predelay=0.04)
    return R.finish(R.punch(R.maximize(R.tame_lows(wet), 2), 2), tail=1.6)


def shell_incoming() -> np.ndarray:
    n = 2.2
    tt = S.t_axis(n)
    freq = 1250 + 900 * np.exp(-tt / 1.1) + 18 * np.sin(2 * np.pi * 7 * tt)
    tone = np.sin(2 * np.pi * np.cumsum(freq) / S.SR)
    tone = tone + 0.25 * np.sin(2 * np.pi * np.cumsum(freq * 2.01) / S.SR)
    air = S.bandpass(S.white(n, 610), 900, 6500)
    rush = S.sweep_lowpass(S.pink(n, 611), 1500, 7000)
    swell = 0.15 + 0.85 * np.clip(tt / n, 0, 1) ** 2.2
    x = (0.5 * tone + 0.35 * air + 0.3 * rush) * swell
    return S.fade(S.normalize(x, -2), 0.25, 0.04)


def artillery_set() -> dict[str, np.ndarray]:
    made = {"artillery/mortar_fire1": mortar_fire("shot_a", 620), "artillery/mortar_fire2": mortar_fire("shot_b", 630),
            "artillery/howitzer_fire1": howitzer_fire("k55_a", 640), "artillery/howitzer_fire2": howitzer_fire("k55_b", 650),
            "artillery/atomic_fire": atomic_fire(660), "artillery/shell_incoming": shell_incoming()}
    return {rel: R.loud_as(x, LOUDNESS[rel]) for rel, x in made.items()}


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from gb import paths  # noqa: E402
    for rel, x in artillery_set().items():
        print(S.save_ogg(x, paths.SOUNDS / f"{rel}.ogg"))
