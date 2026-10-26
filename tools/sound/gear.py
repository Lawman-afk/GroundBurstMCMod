"""The radiation instruments' and medicine's sounds (DESIGN.md §21.8, Phase 12 slice 12a), from real recordings where
they exist and synthesized where the thing is electronic.

- geiger/alarm: a dosimeter's piezo alarm, two quick high beeps (synthesized: a piezo is a near-square tone at one
  pitch, nothing a recording would add to).
- geiger/switch: a real rotary switch thrown (the alarm's setting).
- gear/lead_container_open, gear/lead_container_close: a heavy lid lifting and seating, a real steel door's latch and
  thud played faster (a smaller, thicker thing).
- gear/injector: an autoinjector firing, a real switch's spring click with a short burst of gas behind it.

Every cut names its recording (sources/ledger.json); each sound is brought to a loudness beside its neighbours in the
game (the detonator's click, the charge's beeps, the bench's clank).
"""
from __future__ import annotations

import numpy as np

import sources as R
import synth as S

# (recording, from s, to s, the quiet stretch its background is measured on[, options: hp/lp Hz, fade s, gain dB])
CUTS = {
    "switch": ("switches", 0.40, 0.62, None, {"hp": 150, "fade": 0.08}),         # a switch thrown
    "spring": ("switches", 0.82, 1.05, None, {"hp": 150, "fade": 0.08}),         # ... and back
    "lid": ("heavy_metal_door", 6.35, 7.8, None, {"hp": 40}),                    # a latch's clack and a heavy thud
    "gas": ("extinguisher", 0.66, 1.06, None, {"fade": 0.1}),                    # a burst of gas
}
#: Each sound's loudness (its loudest 3 s, LUFS).
LOUDNESS = {"geiger/alarm": -26.0, "geiger/switch": -35.0, "gear/lead_container_open": -30.0,
            "gear/lead_container_close": -29.0, "gear/injector": -32.0}


def take(name: str, speed: float = 1.0, aligned: bool = False) -> np.ndarray:
    return R.take(CUTS, name, speed, aligned)


def at(x: np.ndarray, db: float) -> np.ndarray:
    return R.match(x, db)


def alarm() -> np.ndarray:
    """Two quick beeps of a piezo sounder (3.1 kHz, its odd harmonics, a hard but clickless edge), 0.25 s."""
    # Its first odd harmonics summed (a square wave drawn sample by sample folds its upper harmonics back down as
    # stray tones; these stay where they belong).
    beep = S.tone(0.07, 3100) + S.tone(0.07, 9300) / 3 + S.tone(0.07, 15500) / 5
    beep = S.fade(beep * 0.6, 0.004, 0.008)
    return R.layer(0.25, (beep, 0.0, 1.0), (beep, 0.11, 1.0))


def switch() -> np.ndarray:
    """A rotary switch thrown: its detent's click."""
    return R.layer(0.25, (take("switch", 1.1, aligned=True), 0.0, 1.0))


def lid(opening: bool) -> np.ndarray:
    """A lead lid: lifting, the latch's clack first and a lighter knock; seating, the thud of the heavy lid home."""
    x = take("lid", 1.45 if opening else 1.25, aligned=True)
    n = 0.45 if opening else 0.55
    return R.tail_fade(R.layer(n, (x, 0.0, 1.0)), 0.15)


def injector() -> np.ndarray:
    """An autoinjector: its spring's click, then a short burst of gas as the dose goes in."""
    click = at(take("spring", 1.2, aligned=True), -18)
    gas = at(S.highpass(take("gas", 1.6), 900), -30)
    return R.tail_fade(R.layer(0.4, (click, 0.0, 1.0), (gas, 0.03, 1.0)), 0.1)


def gear_set() -> dict[str, np.ndarray]:
    """Every sound of slice 12a's gear, at its loudness."""
    out = {"geiger/alarm": alarm(), "geiger/switch": switch(), "gear/lead_container_open": lid(True),
           "gear/lead_container_close": lid(False), "gear/injector": injector()}
    return {rel: R.loud_as(x, LOUDNESS[rel]) for rel, x in out.items()}


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from gz import paths  # noqa: E402
    for rel, x in gear_set().items():
        S.save_ogg(x, paths.SOUNDS / f"{rel}.ogg")
        print(f"{rel}: {len(x) / S.SR:.2f} s")
