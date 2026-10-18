"""Ore textures and models.

Ores are three coplanar layers (like vanilla grass): the vanilla host rock, our mineral overlay (cutout), and an
optional glint layer with light emission for minerals that glow faintly. Using the vanilla host texture keeps ores
matching whatever resource pack the player uses, and nothing from vanilla is copied into the mod.
"""
from __future__ import annotations

import math
import random
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import paths  # noqa: E402
from gz.model import write_json  # noqa: E402
from gz.palette import RGBA, fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402

DIRS = ("north", "south", "east", "west", "up", "down")

# --- mineral painters ---------------------------------------------------------------------------------------------

PITCHBLENDE = fixed("pitchblende", "#0d120d", "#16211a", "#1f3022", "#2b4430", "#3b5e3c", "#5b8a4c", "#a8e070")
AUTUNITE = fixed("autunite", "#4a4c12", "#6a7016", "#8c961e", "#b0bc2a", "#cbd648", "#dfe87e", "#f0f5b8")
URANIUM_GLINT: RGBA = (178, 240, 112, 255)


def _blob_mask(cx: float, cy: float, lobes: list[tuple[float, float, float]]) -> set[tuple[int, int]]:
    """Union of small discs: a bubbly (botryoidal) mass. lobes = (dx, dy, r)."""
    pts: set[tuple[int, int]] = set()
    for dx, dy, r in lobes:
        ox, oy = cx + dx, cy + dy
        for y in range(int(oy - r - 1), int(oy + r + 2)):
            for x in range(int(ox - r - 1), int(ox + r + 2)):
                if (x + 0.5 - ox) ** 2 + (y + 0.5 - oy) ** 2 <= r * r and 0 <= x < 16 and 0 <= y < 16:
                    pts.add((x, y))
    return pts


def _shade_mass(t: Tex, pts: set[tuple[int, int]], ramp, lobes_centres: list[tuple[float, float, float]]) -> list[tuple[int, int]]:
    """Shades a mass so every lobe reads as a little dome lit from the top-left; returns highlight pixels."""
    highlights = []
    light = (-0.55, -0.6, 0.58)  # from the top-left, toward the viewer
    for (x, y) in pts:
        # Each pixel belongs to its nearest lobe; treat the lobe as a dome and light its normal.
        best = None
        for (lx, ly, r) in lobes_centres:
            dx, dy = (x + 0.5 - lx) / max(r, 0.5), (y + 0.5 - ly) / max(r, 0.5)
            d = math.hypot(dx, dy)
            if best is None or d < best[0]:
                best = (d, dx, dy)
        d, dx, dy = best  # type: ignore[misc]
        nz = math.sqrt(max(0.0, 1 - min(d, 1.0) ** 2))
        lum = dx * light[0] + dy * light[1] + nz * light[2]
        if lum > 0.62:
            tone = 5
            highlights.append((x, y))
        elif lum > 0.42:
            tone = 4
        elif lum > 0.2:
            tone = 3
        elif lum > -0.1:
            tone = 2
        else:
            tone = 1
        t.px(x, y, ramp[tone])
    # Dark outline under/right of the mass so it sits *in* the rock.
    for (x, y) in pts:
        for ox, oy in ((1, 0), (0, 1), (1, 1)):
            q = (x + ox, y + oy)
            if q not in pts and 0 <= q[0] < 16 and 0 <= q[1] < 16 and t.get(*q)[3] == 0:
                t.px(*q, (*ramp[0][:3], 200))
    return highlights


@dataclass
class Deposit:
    """One mineral mass: centre and lobes (dx, dy, r)."""
    cx: float
    cy: float
    lobes: list[tuple[float, float, float]]


def paint_pitchblende(deposits: list[Deposit], crusts: list[tuple[int, int]], glint_pick: int, seed: int) -> tuple[Tex, Tex]:
    ore, glint = Tex(16, 16), Tex(16, 16)
    all_highlights = []
    for dep in deposits:
        pts = _blob_mask(dep.cx, dep.cy, dep.lobes)
        centres = [(dep.cx + dx, dep.cy + dy, r) for dx, dy, r in dep.lobes]
        all_highlights += _shade_mass(ore, pts, PITCHBLENDE, centres)
    # Yellow autunite crusts: the secondary uranium mineral that gives pitchblende its tell-tale colour.
    for x, y in crusts:
        ore.px(x, y, AUTUNITE.light)
        ore.px(x + 1, y, AUTUNITE.base)
        ore.px(x, y + 1, AUTUNITE.shadow)
    # A few lit shoulders become glints (emissive layer), chosen deterministically.
    rng = random.Random(seed)
    for x, y in rng.sample(sorted(all_highlights), min(glint_pick, len(all_highlights))):
        ore.px(x, y, PITCHBLENDE.shine)
        glint.px(x, y, URANIUM_GLINT)
    return ore, glint


# --- layouts (hand-placed so each ore reads as a designed deposit, and stone/deepslate differ) ---------------------

URANIUM_STONE = [
    Deposit(4.0, 4.0, [(0, 0, 1.5), (1.3, 0.7, 1.1)]),
    Deposit(11.5, 3.0, [(0, 0, 1.2), (0.6, 1.0, 0.9)]),
    Deposit(6.0, 11.0, [(0, 0, 1.6), (1.6, -0.3, 1.2), (0.4, 1.4, 1.0)]),
    Deposit(12.5, 11.0, [(0, 0, 1.3), (-0.5, 1.2, 0.9)]),
    Deposit(1.8, 9.0, [(0, 0, 0.75)]),
    Deposit(9.5, 7.2, [(0, 0, 0.7)]),
]
URANIUM_STONE_CRUSTS = [(5, 1), (13, 4), (8, 13), (10, 9)]

URANIUM_DEEPSLATE = [
    Deposit(4.5, 3.5, [(0, 0, 1.6), (1.6, 0.6, 1.1)]),
    Deposit(12.0, 5.5, [(0, 0, 1.4), (-0.6, 1.3, 1.0)]),
    Deposit(4.0, 11.5, [(0, 0, 1.4), (1.4, 0.8, 1.0)]),
    Deposit(11.0, 12.5, [(0, 0, 1.5), (1.4, -0.5, 1.0)]),
    Deposit(8.5, 8.0, [(0, 0, 0.75)]),
    Deposit(1.5, 7.5, [(0, 0, 0.7)]),
]
URANIUM_DEEPSLATE_CRUSTS = [(6, 1), (13, 7), (5, 13), (12, 10)]


# --- model writing ----------------------------------------------------------------------------------------------------

def ore_model(name: str, host: dict[str, str], overlay: str, glint: str | None, glint_light: int = 10) -> dict:
    """host maps face -> vanilla texture id (use the same id for all faces for plain rock)."""
    textures = {"particle": host["north"], "ore": overlay}
    face_keys = {}
    for d in DIRS:
        key = "host_" + host[d].split("/")[-1]
        textures[key] = host[d]
        face_keys[d] = key
    if glint:
        textures["glint"] = glint

    def layer(tex_for: Callable[[str], str], light: int = 0) -> dict:
        el = {"from": [0, 0, 0], "to": [16, 16, 16],
              "faces": {d: {"uv": [0, 0, 16, 16], "texture": "#" + tex_for(d), "cullface": d} for d in DIRS}}
        if light:
            el["light_emission"] = light
        return el

    elements = [layer(lambda d: face_keys[d]), layer(lambda d: "ore")]
    if glint:
        elements.append(layer(lambda d: "glint", glint_light))
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": textures, "elements": elements}


def all_faces(tex_id: str) -> dict[str, str]:
    return {d: tex_id for d in DIRS}


def build_uranium() -> list[Path]:
    out = []
    for variant, deposits, crusts, host, seed in (
        ("uranium_ore", URANIUM_STONE, URANIUM_STONE_CRUSTS, all_faces("minecraft:block/stone"), 11),
        ("deepslate_uranium_ore", URANIUM_DEEPSLATE, URANIUM_DEEPSLATE_CRUSTS, all_faces("minecraft:block/deepslate"), 23),
    ):
        ore, glint = paint_pitchblende(deposits, crusts, glint_pick=3, seed=seed)
        ore.save(paths.TEXTURES / "block" / "ore" / f"{variant}.png")
        glint.save(paths.TEXTURES / "block" / "ore" / f"{variant}_glint.png")
        model = ore_model(variant, host, f"groundzero:block/ore/{variant}", f"groundzero:block/ore/{variant}_glint")
        out.append(write_json(paths.MODELS / "block" / f"{variant}.json", model))
    return out


if __name__ == "__main__":
    for p in build_uranium():
        print(p)
