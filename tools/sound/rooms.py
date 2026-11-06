"""The command room's sounds (DESIGN.md §22.4), made from real recordings (sources/ledger.json, all credited): the Filing
Cabinet's drawer rolling out to its stop and rolled shut with a thump (an electric gate's sliding run, quickened and
filtered to a drawer's size; small metal parts and a steel door's slam, small, for its stops); the Tape Computer's motors
spinning the reels up and down (stepper motors), and its switch.

    tools\\.venv\\Scripts\\python.exe tools\\sound\\rooms.py
"""
from __future__ import annotations

import numpy as np

import sources as R
import synth as S

CUTS = {
    "slide": ("sliding_gate", 10.8, 11.7, None, {"hp": 280, "lp": 5200}),    # a gate's run: the drawer's runners
    "slide2": ("sliding_gate", 18.0, 18.9, None, {"hp": 280, "lp": 5200}),
    "stop": ("metal_parts", 6.7, 7.05, None, {"hp": 300}),                   # the drawer meeting its stop
    "thump": ("door_slam", 0.2, 1.4, None, {"lp": 2600}),                    # the drawer shut, small
    "motor": ("cnc_steppers", 25.2, 26.5, None, {"hp": 120, "lp": 6000}),    # stepper motors: the reels' drive
    "switch": ("switches", 0.32, 0.7, None, {"hp": 200}),                    # a toggle switch
}


def shaped(x: np.ndarray, points: list[tuple[float, float]]) -> np.ndarray:
    return x * S.breakpoints(len(x), points)


def drawer_open() -> np.ndarray:
    """The top drawer rolled out on its runners to its stop."""
    run = R.match(shaped(R.take(CUTS, "slide", speed=1.35), [(0, 0), (0.04, 1), (0.5, 0.9), (0.62, 0)]), -26)
    stop = R.match(R.take(CUTS, "stop", speed=0.9, aligned=True), -25)
    return R.finish(R.layer(0.9, (run, 0.0, 1.0), (stop, 0.56, 1.0)), -3.0, tail=0.2)


def drawer_close() -> np.ndarray:
    """The drawer rolled back in and shut with a thump."""
    run = R.match(shaped(R.take(CUTS, "slide2", speed=1.5), [(0, 0), (0.04, 1), (0.45, 0.9), (0.55, 0)]), -26)
    thump = R.match(R.take(CUTS, "thump", speed=1.9, aligned=True), -22)
    return R.finish(R.layer(1.0, (run, 0.0, 1.0), (thump, 0.48, 1.0)), -3.0, tail=0.3)


def tape_whirr() -> np.ndarray:
    """The tape drive's motors spinning the reels up, running and winding down."""
    x = shaped(R.take(CUTS, "motor", speed=1.0), [(0, 0), (0.25, 1), (0.95, 1), (1.3, 0)])
    return R.finish(R.match(x, -24), -6.0, tail=0.15)


def tape_switch() -> np.ndarray:
    """A heavy toggle switch."""
    return R.finish(R.match(R.take(CUTS, "switch", aligned=True), -20), -4.0, tail=0.1)


def rooms() -> dict[str, np.ndarray]:
    return {
        "door/filing_cabinet_open": drawer_open(),
        "door/filing_cabinet_close": drawer_close(),
        "machine/tape_whirr": tape_whirr(),
        "machine/tape_switch": tape_switch(),
    }


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from gb import paths  # noqa: E402
    for rel, x in rooms().items():
        S.save_ogg(x, paths.SOUNDS / f"{rel}.ogg")
        print(f"{rel}: {len(x) / S.SR:.2f} s, peak {20 * np.log10(np.max(np.abs(x)) + 1e-12):.1f} dB, rms {R.rms_db(x):.1f} dB")
