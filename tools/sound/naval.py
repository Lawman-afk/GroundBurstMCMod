"""The underwater blast (DESIGN.md §14.6): the naval set, heard at four distances as the blast sets are (blasts.py).

A charge going off under water is heard from the air as a deep, muffled thud (the water takes the crack out of it: the
shock reaches the air through the surface), the surface tearing open, the roar of the column of spray going up and the
rain of its water falling back into the sea for seconds after. Made from real recordings (sources/ledger.json), all
already credited: the 4 kg TNT charge, low-passed and slowed, with a blast wave's pulse under it (the thud); a muddy splat
slowed right down (the surface tearing); a fire extinguisher's rush, slowed (the column going up); a shower running (the
water falling back), two takes for its weight; air blown through a straw into water, slowed and dulled (the bubbles
boiling up). Further off the air takes its highs (sources.air) and the falling water fades under the thud; each band is
as loud as the medium blast's of the same distance (a sea mine is a medium blast's size).

    tools\\.venv\\Scripts\\python.exe tools\\sound\\naval.py
"""
from __future__ import annotations

import numpy as np
import soundfile as sf

import sources as R
import synth as S

CUTS = {
    "tnt": ("tnt_landmines", 2.0, 9.0, (0.3, 1.9)),             # 4 kg of TNT: its blast and roar
    "splat": ("mud_splat", 0.0, 0.4, None),                     # a muddy splat
    "rush": ("extinguisher", 0.5, 2.3, None),                   # a fire extinguisher's first burst
    "fall": ("shower", 0.4, 8.0, None),                         # a shower running
    "fall2": ("shower", 2.6, 8.2, None),                        # ... further on, for a second layer
    "bubbles": ("bubbles_glass", 2.0, 9.0, None),               # air through a straw into water
}


def thud(speed: float, lp: float) -> np.ndarray:
    """The charge itself heard through the water: the TNT blast low-passed and slowed, a blast wave's pulse under it."""
    x = S.lowpass(R.take(CUTS, "tnt", speed=speed, aligned=True), lp, 4)
    pulse = R.friedlander(0.11 / speed, 1.4, lowpass_hz=lp * 0.5)
    return R.punch(S.mix((R.match(x, -18), 1.0), (R.match(pulse, -20), 1.0)), 6)


def shaped(x: np.ndarray, points: list[tuple[float, float]]) -> np.ndarray:
    return x * S.breakpoints(len(x), points)


def column(total: float, thud_speed: float, thud_lp: float, splash: float, rush: float, fall: float, bubbles: float,
           fall_len: float) -> np.ndarray:
    """One hearing of it: the thud, then (at their gains, 0: left out) the surface tearing, the column's rush, its water
    falling back for {fall_len} s and the bubbles."""
    parts = [(R.match(thud(thud_speed, thud_lp), -12), 0.0, 1.0)]
    if splash:
        x = S.lowpass(R.varispeed(R.take(CUTS, "splat"), 0.4), 3000, 4)
        parts.append((R.match(x, -21), 0.06, splash))
    if rush:
        x = S.bandpass(R.take(CUTS, "rush", speed=0.7), 220, 5500, 2)
        x = shaped(x, [(0, 0), (0.25, 1), (1.2, 0.8), (2.4, 0)])
        parts.append((R.match(x, -28), 0.15, rush))
    if fall:
        a = S.highpass(R.take(CUTS, "fall", speed=0.85), 150, 2)
        b = S.highpass(R.take(CUTS, "fall2", speed=0.7), 120, 2)
        x = S.mix((a, 1.0), (b, 0.8))
        x = shaped(x, [(0, 0), (0.6, 1), (0.6 + fall_len * 0.4, 0.85), (0.6 + fall_len, 0)])
        parts.append((R.match(x, -28), 0.85, fall))
    if bubbles:
        x = S.lowpass(R.take(CUTS, "bubbles", speed=0.55), 900, 4)
        x = shaped(x, [(0, 0), (0.4, 1), (3.0, 0)])
        parts.append((R.match(x, -31), 0.3, bubbles))
    return R.layer(total, *parts)


def reference(band: str) -> float:
    """The medium blast's loudness at this band (what the game plays the naval set beside)."""
    import loudness  # noqa: PLC0415
    from gb import paths  # noqa: PLC0415
    x, sr = sf.read(paths.SOUNDS / "blast" / f"medium_{band}.ogg")
    return loudness.measure_array(x if x.ndim == 1 else x.mean(axis=1), sr)["S-max"]


def naval() -> dict[str, np.ndarray]:
    near = column(6.5, 0.7, 420, 1.0, 1.0, 1.0, 0.6, 4.6)
    mid = R.air(column(5.5, 0.66, 340, 0.6, 0.7, 0.75, 0.0, 3.8), 150)
    far = R.air(column(4.5, 0.62, 260, 0.0, 0.3, 0.35, 0.0, 3.0), 420)
    very_far = R.air(S.lowpass(column(3.5, 0.58, 180, 0.0, 0.0, 0.0, 0.0, 1.0), 160, 4), 1200)
    out = {}
    for band, x in (("near", near), ("mid", mid), ("far", far), ("very_far", very_far)):
        x = R.loud_as(R.finish(x, -1.5), reference(band) - 1.0, ceiling_db=-1.5)
        out[f"blast/naval_{band}"] = R.finish(x, -1.5, tail=0.6)
    return out


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from gb import paths  # noqa: E402
    for rel, x in naval().items():
        p = S.save_ogg(x, paths.SOUNDS / f"{rel}.ogg")
        print(f"{rel}: {len(x) / S.SR:.1f} s, peak {20 * np.log10(np.max(np.abs(x)) + 1e-12):.1f} dB, rms {R.rms_db(x):.1f} dB")
