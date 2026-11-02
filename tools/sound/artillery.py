"""Artillery (DESIGN.md §20.1): the guns firing, from real recordings, and a shell coming in.

- The mortar firing (two takes, 1.6 s each): an M-327 mortar's two shots, each with a deep thump laid under its crack
  (the charge going off in the tube) and a short open-air tail.
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
}

LOUDNESS = {"artillery/mortar_fire1": -14.5, "artillery/mortar_fire2": -14.5, "artillery/shell_incoming": -19.0}


def take(name: str, speed: float = 1.0, aligned: bool = False) -> np.ndarray:
    return R.take(CUTS, name, speed, aligned)


def thump(seconds: float, hz: float, seed: int) -> np.ndarray:
    """The charge in the tube: a deep thud, quick to rise and quick to die."""
    n = int(seconds * S.SR)
    body = S.lowpass(S.brown(seconds, seed), hz) * S.exp_decay(n, 0.09)
    tt = S.t_axis(seconds)
    drop = np.sin(2 * np.pi * np.cumsum(70 * np.exp(-tt / 0.08) + 38) / S.SR) * S.exp_decay(n, 0.12) * 0.6
    return S.normalize(body + drop, -3)


def mortar_fire(cut: str, seed: int) -> np.ndarray:
    shot = take(cut)
    total = len(shot) / S.SR + 0.25
    low = thump(0.7, 160, seed)
    mixed = R.layer(total, (shot, 0.0, 1.0), (R.match(low, -20), 0.0, 1.0))
    tail = S.reverb(mixed, seconds=0.9, damping_hz=2500, mix=0.18, seed=seed + 1)
    return R.tail_fade(S.normalize(tail, -1), 0.3)


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
            "artillery/shell_incoming": shell_incoming()}
    return {rel: R.loud_as(x, LOUDNESS[rel]) for rel, x in made.items()}


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from gb import paths  # noqa: E402
    for rel, x in artillery_set().items():
        print(S.save_ogg(x, paths.SOUNDS / f"{rel}.ogg"))
