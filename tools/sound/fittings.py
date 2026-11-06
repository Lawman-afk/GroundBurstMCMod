"""The bunker fittings' sounds (DESIGN.md §22.5), made from real recordings (sources/ledger.json, all credited): the Steel
Locker's door (its latch's clack and a light steel door's creak, opening; slammed and latched, shutting: a big steel door
quickened to a locker's size); the Pipe Valve's handwheel (a ratchet's pawl and a slow metal grind under a squeal of its
gland); the Fuse Box's breaker thrown (a heavy toggle slowed, a thump under it); the Bunker Lamp's switch; the Fire
Extinguisher's burst of gas.

    tools\\.venv\\Scripts\\python.exe tools\\sound\\fittings.py
"""
from __future__ import annotations

import numpy as np

import sources as R
import synth as S

CUTS = {
    "latch": ("metal_parts", 6.45, 6.9, None, {"hp": 300}),                  # small metal parts: the latch
    "latch2": ("metal_parts", 21.5, 21.85, None, {"hp": 300}),
    "creak": ("heavy_metal_door", 1.0, 2.95, None, {"hp": 120, "lp": 4200}),  # a steel door's hinges
    "slam": ("door_slam", 0.2, 1.6, None, {"hp": 90, "lp": 3600}),           # a steel door slammed, off-mic
    "grind": ("metal_grinding", 3.0, 3.6, None, {"hp": 160, "lp": 2400}),    # metal scraped slowly: the stem in its gland
    "pawl": ("ratchet", 33.2, 34.5, None, {"hp": 200}),                      # a ratchet wrench: the wheel's turn
    "flip": ("arming_switch", 6.62, 6.84, (0.5, 3.0), {"fade": 0.08}),       # a heavy toggle: the breaker
    "click": ("switches", 0.40, 0.62, None, {"hp": 150, "fade": 0.08}),      # a switch thrown
    "clang": ("metal_clang", 0.0, 1.6, None, {"lp": 5000}),                  # a pan struck: sheet steel ringing
    "puff": ("extinguisher", 0.66, 1.06, None, {"fade": 0.1}),               # a fire extinguisher's burst of gas
    "spray": ("extinguisher", 4.4, 6.6, None),                               # ... and its long spray
}


def shaped(x: np.ndarray, points: list[tuple[float, float]]) -> np.ndarray:
    """{x} under a gain envelope through (seconds, gain) points."""
    return x * S.breakpoints(len(x), points)


def thump(seed: int, gain: float = 1.0, lp: float = 160) -> np.ndarray:
    """A short, dull, low thud under a clack."""
    n = 0.2
    return S.lowpass(S.white(n, seed), lp) * S.exp_decay(int(n * S.SR), 0.035) * 2.4 * gain


def locker_open() -> np.ndarray:
    """The locker's latch lifted with a clack, its light door creaking open on its hinges."""
    total = 0.85
    latch = R.match(R.take(CUTS, "latch", speed=1.15, aligned=True), -24)
    creak = R.match(shaped(R.take(CUTS, "creak", speed=1.75), [(0, 0), (0.05, 1), (0.45, 0.8), (0.6, 0)]), -28)
    x = R.layer(total, (latch, 0.0, 1.0), (thump(901, 0.25, 220), 0.0, 1.0), (creak, 0.1, 1.0))
    return R.finish(x, -3.0, tail=0.2)


def locker_close() -> np.ndarray:
    """The door swung shut: sheet steel slammed into its frame (a big door's slam, quickened: a light door's rattle),
    its latch dropping home."""
    total = 0.9
    clang = R.match(R.take(CUTS, "clang", speed=1.6, aligned=True), -27)
    slam = R.match(shaped(R.take(CUTS, "slam", speed=1.7, aligned=True), [(0, 1), (0.3, 0.7), (0.8, 0)]), -29)
    latch = R.match(R.take(CUTS, "latch2", speed=1.1, aligned=True), -25)
    x = R.layer(total, (clang, 0.0, 1.0), (thump(921, 0.8, 260), 0.0, 1.0), (slam, 0.0, 1.0), (latch, 0.1, 1.0))
    return R.finish(x, -3.0, tail=0.3)


def valve_turn() -> np.ndarray:
    """The handwheel given a turn: the wheel's clicks, the stem grinding in its gland, a squeal of dry packing."""
    total = 0.85
    pawl = R.match(shaped(R.take(CUTS, "pawl", speed=0.9), [(0, 0), (0.04, 1), (0.55, 0.9), (0.7, 0)]), -29)
    grind = R.match(shaped(R.take(CUTS, "grind", speed=0.8), [(0, 0), (0.06, 1), (0.55, 0.8), (0.72, 0)]), -31)
    squeal = R.match(shaped(R.take(CUTS, "creak", speed=2.3), [(0, 0), (0.08, 1), (0.4, 0.6), (0.6, 0)]), -33)
    x = R.layer(total, (pawl, 0.0, 1.0), (grind, 0.0, 1.0), (squeal, 0.12, 1.0))
    return R.finish(x, -4.0, tail=0.2)


def breaker() -> np.ndarray:
    """A heavy breaker thrown: the toggle's snap, slowed to a big lever's, a thump under it."""
    flip = R.match(R.take(CUTS, "flip", speed=0.72, aligned=True), -20)
    return R.finish(R.layer(0.45, (flip, 0.0, 1.0), (thump(911, 0.6), 0.0, 1.0)), -3.0, tail=0.15)


def lamp_switch() -> np.ndarray:
    """A small toggle switch on the lamp's box."""
    return R.finish(R.match(R.take(CUTS, "click", speed=1.1, aligned=True), -20), -6.0, tail=0.08)


def spray() -> np.ndarray:
    """A short burst of gas from the extinguisher's horn."""
    puff = R.match(R.take(CUTS, "puff", aligned=True), -21)
    body = R.match(shaped(R.take(CUTS, "spray"), [(0, 0), (0.1, 1), (0.6, 0.85), (0.85, 0)]), -25)
    return R.finish(R.layer(0.95, (puff, 0.0, 1.0), (body, 0.04, 1.0)), -7.0, tail=0.25)


def fittings() -> dict[str, np.ndarray]:
    return {
        "door/steel_locker_open": locker_open(),
        "door/steel_locker_close": locker_close(),
        "fitting/pipe_valve_turn": valve_turn(),
        "fitting/fuse_box_switch": breaker(),
        "fitting/lamp_switch": lamp_switch(),
        "fitting/extinguisher_spray": spray(),
    }


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from gb import paths  # noqa: E402
    for rel, x in fittings().items():
        S.save_ogg(x, paths.SOUNDS / f"{rel}.ogg")
        print(f"{rel}: {len(x) / S.SR:.2f} s, peak {20 * np.log10(np.max(np.abs(x)) + 1e-12):.1f} dB, rms {R.rms_db(x):.1f} dB")
