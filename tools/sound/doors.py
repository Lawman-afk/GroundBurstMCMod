"""The bunker doors' sounds (DESIGN.md §22.3), made from real recordings (sources/ledger.json, all credited).

The Blast Door: its handwheel turning the bolts back (a ratchet's pawl slowed to a heavy wheel's, over a slow metal
grind) and the three bolts drawing with clacks; the leaf swinging on its hinges (a heavy steel door's creak, slowed, over
a low rumble); the leaf meeting its frame (a big steel door slammed off-mic, slowed: a heavy thud and its echo); the
handwheel throwing the bolts home (the wheel again, the clacks louder, with a ringing clang). The Bunker Hatch and the
Pressure Door: the same recordings, shorter and lighter (a quick turn of the hatch's wheel, the dogs' clack, the hinges'
groan, the lid or the leaf slammed home).

    tools\\.venv\\Scripts\\python.exe tools\\sound\\doors.py
"""
from __future__ import annotations

import numpy as np

import sources as R
import synth as S

CUTS = {
    "grind": ("metal_grinding", 3.0, 3.6, None, {"hp": 120, "lp": 1800}),     # metal scraped slowly: the wheel's shaft
    "grind2": ("metal_grinding", 6.0, 6.6, None, {"hp": 120, "lp": 1800}),
    "pawl": ("ratchet", 33.2, 34.5, None, {"hp": 200}),                       # a ratchet wrench: the wheel's pawl
    "pawl2": ("ratchet", 30.0, 31.3, None, {"hp": 200}),
    "clack1": ("metal_parts", 6.7, 7.05, None, {"hp": 150}),                  # small metal parts: the bolts
    "clack2": ("metal_parts", 21.5, 21.85, None, {"hp": 150}),
    "clack3": ("metal_parts", 27.7, 28.05, None, {"hp": 150}),
    "creak": ("heavy_metal_door", 1.0, 2.95, None, {"hp": 60, "lp": 2600}),   # a big steel door opening: the hinges
    "slam": ("door_slam", 0.2, 2.9, None, {"lp": 2200}),                      # a big steel door closing, off-mic
    "clang": ("metal_clang", 0.0, 1.6, None, {"lp": 3000}),                   # a pan struck: the bolts ringing home
}


def shaped(x: np.ndarray, points: list[tuple[float, float]]) -> np.ndarray:
    """{x} under a gain envelope through (seconds, gain) points."""
    return x * S.breakpoints(len(x), points)


def thump(seed: int, gain: float = 1.0) -> np.ndarray:
    """The weight under a bolt's clack: a short, dull, low thud."""
    n = 0.25
    return S.lowpass(S.white(n, seed), 140) * S.exp_decay(int(n * S.SR), 0.04) * 2.4 * gain


def wheel(grind: str, pawl: str, clacks: list[float], clack_db: float, ring: bool, seed: int) -> np.ndarray:
    """The handwheel turned: the shaft's grind and the pawl's clicks for its turn and a half, then the bolts sliding
    (clacks at {clacks} seconds, each with a dull thud under it) and, thrown home, the frame ringing."""
    total = 1.35
    bed = R.match(shaped(R.take(CUTS, grind, speed=0.5), [(0, 0), (0.08, 1), (0.95, 0.9), (1.2, 0)]), -33)
    clicks = R.match(shaped(R.take(CUTS, pawl, speed=0.72), [(0, 0), (0.05, 1), (0.9, 1), (1.15, 0)]), -27)
    parts = [(bed, 0.0, 1.0), (clicks, 0.0, 1.0)]
    for i, at in enumerate(clacks):
        c = R.match(R.take(CUTS, f"clack{i % 3 + 1}", speed=0.55, aligned=True), clack_db)
        parts.append((c, at, 1.0))
        parts.append((thump(seed + i, 0.5 if not ring else 0.8), at, 1.0))
    if ring:
        parts.append((R.match(R.take(CUTS, "clang", speed=0.5, aligned=True), clack_db - 9), clacks[-1], 1.0))
    return R.finish(R.layer(total, *parts), -3.0, tail=0.35)


def swing() -> np.ndarray:
    """The leaf swinging: the hinges' creak (slowed: a heavier door) with a low rumble under it, as long as it swings."""
    creak = shaped(R.take(CUTS, "creak", speed=0.86), [(0, 0), (0.18, 1), (1.8, 0.9), (2.27, 0)])
    rumble = S.lowpass(S.brown(2.3, 811), 110) * S.breakpoints(int(2.3 * S.SR), [(0, 0), (0.3, 1), (1.9, 1), (2.3, 0)])
    x = R.layer(2.4, (R.match(creak, -24), 0.0, 1.0), (R.match(rumble, -31), 0.0, 1.0))
    return R.finish(x, -3.0, tail=0.2)


def seat() -> np.ndarray:
    """The leaf meeting its frame: a heavy thud, slowed, and its echo."""
    x = R.punch(R.match(R.take(CUTS, "slam", speed=0.8, aligned=True), -17), 4)
    return R.finish(x, -2.0, tail=0.4)


def hatch_open() -> np.ndarray:
    """The Bunker Hatch opening: its wheel given a quick turn (the pawl), the dogs drawn with a clack, the lid's hinges
    groaning as it is heaved up, and the lid knocking onto its stop."""
    total = 1.25
    pawl = R.match(shaped(R.take(CUTS, "pawl2", speed=0.95), [(0, 0), (0.03, 1), (0.3, 0.8), (0.38, 0)]), -28)
    clack = R.match(R.take(CUTS, "clack2", speed=0.7, aligned=True), -24)
    creak = R.match(shaped(R.take(CUTS, "creak", speed=1.05), [(0, 0), (0.08, 1), (0.6, 0.8), (0.75, 0)]), -26)
    stop = R.match(R.take(CUTS, "clang", speed=0.62, aligned=True), -27)
    x = R.layer(total, (pawl, 0.0, 1.0), (clack, 0.34, 1.0), (thump(841, 0.4), 0.34, 1.0), (creak, 0.36, 1.0), (stop, 1.0, 1.0))
    return R.finish(x, -3.0, tail=0.3)


def hatch_close() -> np.ndarray:
    """The Bunker Hatch shutting: the lid dropped onto its coaming (a steel door's slam, lighter and sooner done), the
    dogs thrown with a clack."""
    total = 1.4
    slam = R.punch(R.match(R.take(CUTS, "slam", speed=1.12, aligned=True), -19), 3)
    clack = R.match(R.take(CUTS, "clack3", speed=0.7, aligned=True), -24)
    x = R.layer(total, (slam, 0.0, 1.0), (clack, 0.22, 1.0), (thump(851, 0.5), 0.22, 1.0))
    return R.finish(x, -3.0, tail=0.4)


def door_open() -> np.ndarray:
    """The Pressure Door opening: its dog lever thrown up with a clack and a thud, its heavy hinges creaking."""
    total = 1.0
    clack = R.match(R.take(CUTS, "clack1", speed=0.8, aligned=True), -23)
    creak = R.match(shaped(R.take(CUTS, "creak", speed=1.15), [(0, 0), (0.06, 1), (0.55, 0.7), (0.7, 0)]), -26)
    x = R.layer(total, (clack, 0.0, 1.0), (thump(861, 0.6), 0.0, 1.0), (creak, 0.12, 1.0))
    return R.finish(x, -3.0, tail=0.25)


def door_close() -> np.ndarray:
    """The Pressure Door shutting: a heavy steel door slammed into its frame, the dog lever thrown down after it."""
    total = 1.5
    slam = R.punch(R.match(R.take(CUTS, "slam", speed=1.02, aligned=True), -18), 3)
    clack = R.match(R.take(CUTS, "clack1", speed=0.75, aligned=True), -23)
    x = R.layer(total, (slam, 0.0, 1.0), (clack, 0.3, 1.0), (thump(871, 0.6), 0.3, 1.0))
    return R.finish(x, -3.0, tail=0.4)


def doors() -> dict[str, np.ndarray]:
    return {
        "door/blast_door_unlock": wheel("grind", "pawl", [0.98, 1.06, 1.15], -24, False, 820),
        "door/blast_door_lock": wheel("grind2", "pawl2", [0.94, 1.0, 1.08], -20, True, 830),
        "door/blast_door_swing": swing(),
        "door/blast_door_seat": seat(),
        "door/bunker_hatch_open": hatch_open(),
        "door/bunker_hatch_close": hatch_close(),
        "door/pressure_door_open": door_open(),
        "door/pressure_door_close": door_close(),
    }


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from gb import paths  # noqa: E402
    for rel, x in doors().items():
        p = S.save_ogg(x, paths.SOUNDS / f"{rel}.ogg")
        print(f"{rel}: {len(x) / S.SR:.2f} s, peak {20 * np.log10(np.max(np.abs(x)) + 1e-12):.1f} dB, rms {R.rms_db(x):.1f} dB")
