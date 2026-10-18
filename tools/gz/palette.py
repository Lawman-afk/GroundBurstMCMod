"""Colour ramps for every material in the mod.

Ramps are generated in OKLCH so steps look evenly spaced, with hue shifting: shadows lean cool (toward purple-blue, reached via red for warm hues so yellows never go green),
highlights lean warm (toward yellow), and chroma dips at both ends. Each ramp is ordered dark -> light and is
indexed with the semantic names below, so painting code reads like `steel.shadow` rather than `steel[1]`.
"""
from __future__ import annotations

import math
from dataclasses import dataclass

RGBA = tuple[int, int, int, int]


# --- OKLab conversions -------------------------------------------------------------------------------------

def _srgb_to_linear(c: float) -> float:
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _linear_to_srgb(c: float) -> float:
    c = max(0.0, min(1.0, c))
    return 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055


def rgb_to_oklch(rgb: tuple[int, int, int]) -> tuple[float, float, float]:
    r, g, b = (_srgb_to_linear(v / 255) for v in rgb)
    l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    l_, m_, s_ = (math.copysign(abs(v) ** (1 / 3), v) for v in (l, m, s))
    L = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_
    a = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_
    bb = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_
    return L, math.hypot(a, bb), math.degrees(math.atan2(bb, a)) % 360


def oklch_to_rgb(L: float, C: float, H: float) -> tuple[int, int, int]:
    a = C * math.cos(math.radians(H))
    b = C * math.sin(math.radians(H))
    l_ = L + 0.3963377774 * a + 0.2158037573 * b
    m_ = L - 0.1055613458 * a - 0.0638541728 * b
    s_ = L - 0.0894841775 * a - 1.2914855480 * b
    l, m, s = l_ ** 3, m_ ** 3, s_ ** 3
    r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    return tuple(int(round(_linear_to_srgb(v) * 255)) for v in (r, g, bl))  # type: ignore[return-value]


def hex_rgb(h: str) -> tuple[int, int, int]:
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def _toward(h: float, target: float, amount: float) -> float:
    d = ((target - h + 180) % 360) - 180
    return (h + d * amount) % 360


# --- Ramps ---------------------------------------------------------------------------------------------------

@dataclass(frozen=True)
class Ramp:
    """Seven shades, dark -> light."""
    name: str
    shades: tuple[RGBA, ...]

    def __getitem__(self, i: int) -> RGBA:
        return self.shades[max(0, min(len(self.shades) - 1, i))]

    def __len__(self) -> int:
        return len(self.shades)

    # Semantic accessors (indices into the 7-step ramp)
    @property
    def outline(self) -> RGBA: return self.shades[0]
    @property
    def deep(self) -> RGBA: return self.shades[1]
    @property
    def shadow(self) -> RGBA: return self.shades[2]
    @property
    def base(self) -> RGBA: return self.shades[3]
    @property
    def light(self) -> RGBA: return self.shades[4]
    @property
    def highlight(self) -> RGBA: return self.shades[5]
    @property
    def shine(self) -> RGBA: return self.shades[6]


def ramp(name: str, base_hex: str, spread: float = 0.30, hue_shift: float = 0.25, chroma_falloff: float = 0.45,
         cool_hue: float = 285.0, warm_hue: float = 85.0, steps: int = 7, alpha: int = 255) -> Ramp:
    """Builds a ramp around `base_hex` (which becomes the middle shade).

    spread         total lightness range either side of the base (OKLab L units)
    hue_shift      0..1: how far shadows rotate toward `cool_hue` and highlights toward `warm_hue`
    chroma_falloff 0..1: chroma reduction at the extremes
    """
    L0, C0, H0 = rgb_to_oklch(hex_rgb(base_hex))
    mid = (steps - 1) / 2
    out = []
    for i in range(steps):
        t = (i - mid) / mid  # -1 .. 1
        L = max(0.03, min(0.98, L0 + t * spread))
        target = warm_hue if t > 0 else cool_hue
        H = _toward(H0, target, abs(t) * hue_shift) if C0 > 0.01 else H0
        C = C0 * (1 - chroma_falloff * abs(t) ** 1.5)
        out.append((*oklch_to_rgb(L, C, H), alpha))
    return Ramp(name, tuple(out))


def fixed(name: str, *hexes: str) -> Ramp:
    """A hand-picked ramp (exactly 7 colours, dark -> light)."""
    if len(hexes) != 7:
        raise ValueError("fixed ramps need 7 colours")
    return Ramp(name, tuple((*hex_rgb(h), 255) for h in hexes))


# Structural / paint materials
GUNMETAL = ramp("gunmetal", "#4a5058", spread=0.30, hue_shift=0.15)
STEEL = ramp("steel", "#8a9097", spread=0.32, hue_shift=0.12)
OLIVE = ramp("olive", "#5d6b3a", spread=0.28)
NATO_GREEN = ramp("nato_green", "#4b5a3c", spread=0.27)
DESERT_TAN = ramp("desert_tan", "#b39a6c", spread=0.30)
RUST = ramp("rust", "#8a4b2a", spread=0.30, hue_shift=0.2)
PRIMER_RED = ramp("primer_red", "#8c3a2e", spread=0.26)
TUNGSTEN = ramp("tungsten", "#6f7a8a", spread=0.32, hue_shift=0.1)
LEAD = ramp("lead", "#5f6673", spread=0.28, hue_shift=0.08)
COPPER = ramp("copper", "#b4673d", spread=0.32, hue_shift=0.2)
BRASS = ramp("brass", "#b39445", spread=0.32, hue_shift=0.15)
CONCRETE = ramp("concrete", "#9a9890", spread=0.30, hue_shift=0.1)
OCHRE = ramp("ochre", "#c09a3c", spread=0.30)            # fission-bomb yellow-ochre
WHITE_CERAMIC = ramp("white_ceramic", "#d9dde0", spread=0.22, hue_shift=0.1)
BLACK_PAINT = ramp("black_paint", "#2a2c30", spread=0.20, hue_shift=0.1)
RUBBER = ramp("rubber", "#2b2b2b", spread=0.16, hue_shift=0.05)

HAZARD_YELLOW = fixed("hazard_yellow", "#4a3a0c", "#7a5f10", "#b08a14", "#e0b21c", "#f2cc3a", "#f8de70", "#fff0b0")
WARNING_RED = fixed("warning_red", "#3a0c0c", "#651414", "#931c1c", "#c22a24", "#dc4a3c", "#ec7462", "#f8a898")
HAZARD_BLACK = fixed("hazard_black", "#0e0f10", "#17181a", "#202226", "#2a2c30", "#383b40", "#4a4e54", "#5e636a")

# Energy / emissive ramps (lighter, high chroma; used on glow layers)
NUCLEAR_AMBER = ramp("nuclear_amber", "#ffa620", spread=0.25, hue_shift=0.1, chroma_falloff=0.3)
URANIUM_GLOW = ramp("uranium_glow", "#8ef04a", spread=0.25, hue_shift=0.05, chroma_falloff=0.3)
CHERENKOV = ramp("cherenkov", "#3a9cff", spread=0.25, hue_shift=0.05, chroma_falloff=0.3)
ANTIMATTER = ramp("antimatter", "#c7a0ff", spread=0.25, hue_shift=0.05, chroma_falloff=0.3)
SINGULARITY = ramp("singularity", "#7a2cff", spread=0.30, hue_shift=0.05, chroma_falloff=0.3)
CRYO = ramp("cryo", "#7fe6ff", spread=0.25, hue_shift=0.05, chroma_falloff=0.3)
TOXIN = ramp("toxin", "#7ad62a", spread=0.25, hue_shift=0.05, chroma_falloff=0.3)
ANTHRAX_GAMMA = ramp("anthrax_gamma", "#b24bff", spread=0.25, hue_shift=0.05, chroma_falloff=0.3)
TESLA = ramp("tesla", "#5ac8ff", spread=0.25, hue_shift=0.05, chroma_falloff=0.3)
EMP = ramp("emp", "#b8f0ff", spread=0.2, hue_shift=0.05, chroma_falloff=0.3)
INDICATOR_RED = ramp("indicator_red", "#ff3a2a", spread=0.25, hue_shift=0.05, chroma_falloff=0.3)
INDICATOR_GREEN = ramp("indicator_green", "#4aff6a", spread=0.25, hue_shift=0.05, chroma_falloff=0.3)

ALL: dict[str, Ramp] = {r.name: r for r in [
    GUNMETAL, STEEL, OLIVE, NATO_GREEN, DESERT_TAN, RUST, PRIMER_RED, TUNGSTEN, LEAD, COPPER, BRASS, CONCRETE,
    OCHRE, WHITE_CERAMIC, BLACK_PAINT, RUBBER, HAZARD_YELLOW, WARNING_RED, HAZARD_BLACK, NUCLEAR_AMBER,
    URANIUM_GLOW, CHERENKOV, ANTIMATTER, SINGULARITY, CRYO, TOXIN, ANTHRAX_GAMMA, TESLA, EMP, INDICATOR_RED,
    INDICATOR_GREEN,
]}

TRANSPARENT: RGBA = (0, 0, 0, 0)


def mix(a: RGBA, b: RGBA, t: float) -> RGBA:
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))  # type: ignore[return-value]
