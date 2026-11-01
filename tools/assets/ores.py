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

from gb import paths  # noqa: E402
from gb import vanilla as V  # noqa: E402
from gb.model import write_json  # noqa: E402
from gb.palette import RGBA, fixed  # noqa: E402
from gb.tex import Tex  # noqa: E402

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


def side_top_bottom(side: str, top: str, bottom: str) -> dict[str, str]:
    return {"north": side, "south": side, "east": side, "west": side, "up": top, "down": bottom}


STONE = all_faces("minecraft:block/stone")
DEEPSLATE = all_faces("minecraft:block/deepslate")
NETHERRACK = all_faces("minecraft:block/netherrack")
SANDSTONE = side_top_bottom("minecraft:block/sandstone", "minecraft:block/sandstone_top", "minecraft:block/sandstone_bottom")


def _ore(name: str, host: dict[str, str], paint: Callable[[Tex], None], glint: Tex | None = None) -> Path:
    ore = Tex(16, 16)
    paint(ore)
    ore.save(paths.TEXTURES / "block" / "ore" / f"{name}.png")
    glint_id = None
    if glint is not None:
        glint.save(paths.TEXTURES / "block" / "ore" / f"{name}_glint.png")
        glint_id = f"groundburst:block/ore/{name}_glint"
    return write_json(paths.MODELS / "block" / f"{name}.json", ore_model(name, host, f"groundburst:block/ore/{name}", glint_id))


# --- vanilla-style overlays: small lit mineral clusters (see gb/vanilla.py) -------------------------------------------

def _clusters(ramp, stamps, seed, rim=None, accent=None):
    return V.ore_overlay(ramp, stamps, seed, rim=rim, accent=accent)


def build_uranium() -> list[Path]:
    out = []
    stamps = ["round", "blob3", "pair", "bar", "tall", "ell", "speck", "blob3", "pair", "speck"]
    for variant, host, seed in (("uranium_ore", STONE, 11), ("deepslate_uranium_ore", DEEPSLATE, 23)):
        def autunite(t, rng, painted):
            # Yellow autunite crusts on a few clusters: pitchblende's tell-tale secondary mineral.
            for x, y in rng.sample(sorted(painted), 4):
                t.px(x, y, AUTUNITE[5] if rng.random() < 0.5 else AUTUNITE[4])
        ore, highlights = _clusters(PITCHBLENDE, stamps, seed, accent=autunite)
        glint = Tex(16, 16)
        rng = random.Random(seed + 1)
        for x, y in rng.sample(sorted(highlights), min(3, len(highlights))):
            ore.px(x, y, PITCHBLENDE.shine)
            glint.px(x, y, URANIUM_GLINT)
        out.append(_ore(variant, host, lambda t, ore=ore: t.blit(ore, 0, 0), glint))
    return out


def build_others() -> list[Path]:
    import minerals as M
    out = []
    common = ["round", "blob3", "bar", "pair", "tall", "ell", "speck", "blob3", "pair", "speck"]
    plans = [
        # name, host, ramp, stamps, seed, rim, tone shift
        ("lithium_ore", STONE, M.SPODUMENE, ["prism", "prism_s", "prism_s", "prism", "prism_s", "pair", "speck", "speck"], 51, None, 0),
        ("deepslate_lithium_ore", DEEPSLATE, M.SPODUMENE, ["prism", "prism_s", "prism", "prism_s", "prism_s", "pair", "speck"], 52, None, 0),
        ("sulfur_ore", STONE, M.SULFUR, common, 31, None, 0),
        ("deepslate_sulfur_ore", DEEPSLATE, M.SULFUR, common, 32, None, 0),
        ("nether_sulfur_ore", NETHERRACK, M.SULFUR, common + ["round", "blob3"], 33, None, 0),
        ("saltpeter_ore", STONE, M.NITER, ["crust", "crust_s", "crust", "crust_s", "crust_s", "pair", "speck", "speck"], 41, M.NITER[1], 1),
        ("deepslate_saltpeter_ore", DEEPSLATE, M.NITER, ["crust", "crust_s", "crust_s", "crust", "pair", "speck", "speck"], 42, M.NITER[1], 1),
        ("sandstone_saltpeter_ore", SANDSTONE, M.NITER, ["crust", "crust", "crust_s", "crust_s", "pair", "speck"], 43, M.NITER[1], 1),
        ("tungsten_ore", STONE, M.WOLFRAMITE, ["tab", "tab", "bar", "tall", "pair", "ell", "speck", "speck"], 61, None, 0),
        ("deepslate_tungsten_ore", DEEPSLATE, M.WOLFRAMITE, ["tab", "bar", "tab", "tall", "pair", "ell", "speck"], 62, None, 0),
        ("lead_ore", STONE, M.GALENA, ["cube", "cube_s", "cube", "cube_s", "cube_s", "pair", "speck", "speck"], 71, M.GALENA[0], 1),
        ("deepslate_lead_ore", DEEPSLATE, M.GALENA, ["cube", "cube_s", "cube_s", "cube", "cube_s", "speck", "speck"], 72, M.GALENA[0], 1),
        ("rare_earth_ore", STONE, M.MONAZITE, common, 81, None, 0),
        ("deepslate_rare_earth_ore", DEEPSLATE, M.MONAZITE, common, 82, None, 0),
    ]
    for name, host, ramp, stamps, seed, rim, shift in plans:
        ore, _ = V.ore_overlay(ramp, stamps, seed, rim=rim, shift=shift)
        out.append(_ore(name, host, lambda t, ore=ore: t.blit(ore, 0, 0)))

    def zoned(t, rng, painted):
        # Fluorite's colour zoning: a third of the cubes grew green rather than purple.
        crystals = _components(painted)
        rng.shuffle(crystals)
        for crystal in crystals[: max(1, len(crystals) // 3)]:
            for (x, y) in crystal:
                c = t.get(x, y)[:3]
                for i in range(7):
                    if M.FLUORITE[i][:3] == c:
                        t.px(x, y, M.FLUORITE_GREEN[i])
                        break
    cubes = ["cube", "cube_s", "cube", "cube_s", "cube_s", "pair", "speck", "speck"]
    for name, host, seed in (("fluorite_ore", STONE, 91), ("deepslate_fluorite_ore", DEEPSLATE, 92)):
        ore, _ = V.ore_overlay(M.FLUORITE, cubes, seed, rim=M.FLUORITE[1], accent=zoned)
        out.append(_ore(name, host, lambda t, ore=ore: t.blit(ore, 0, 0)))
    return out


def _components(pts: set[tuple[int, int]]) -> list[list[tuple[int, int]]]:
    """The 4-connected groups of pixels in `pts`, each sorted (so a seeded shuffle picks the same ones)."""
    left, out = set(pts), []
    while left:
        stack, group = [min(left)], []
        left.discard(stack[0])
        while stack:
            x, y = stack.pop()
            group.append((x, y))
            for q in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if q in left:
                    left.discard(q)
                    stack.append(q)
        out.append(sorted(group))
    return sorted(out)


ALL_ORES = ["uranium_ore", "deepslate_uranium_ore", "lithium_ore", "deepslate_lithium_ore", "sulfur_ore",
            "deepslate_sulfur_ore", "nether_sulfur_ore", "saltpeter_ore", "deepslate_saltpeter_ore",
            "sandstone_saltpeter_ore", "tungsten_ore", "deepslate_tungsten_ore", "lead_ore", "deepslate_lead_ore",
            "rare_earth_ore", "deepslate_rare_earth_ore", "fluorite_ore", "deepslate_fluorite_ore"]


def review_sheet(names: list[str], out_name: str, cell: int = 200) -> Path:
    """Front-face close-up + iso view of each block side by side, labelled."""
    sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "preview"))
    import numpy as np
    from PIL import Image, ImageDraw
    import render as R
    cols = 4
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * cell * 2, rows * (cell + 16)), (30, 32, 36))
    d = ImageDraw.Draw(sheet)
    light = np.array([0.4, 0.8, 0.45]); light /= np.linalg.norm(light)
    for i, n in enumerate(names):
        model = R.load_model(f"groundburst:block/{n}")
        quads = R.build_quads(model)
        c, e = R.model_bounds(quads)
        x0, y0 = (i % cols) * cell * 2, (i // cols) * (cell + 16)
        for k, (pitch, yaw, ext) in enumerate(((30, 225, e), (0, 180, 16.2))):
            tile = R._checker(cell).convert("RGBA")
            tile.alpha_composite(R.render(quads, cell, pitch, yaw, c, ext, light_dir=light))
            sheet.paste(tile.convert("RGB"), (x0 + k * cell, y0))
        d.text((x0 + 4, y0 + cell + 2), n, fill=(220, 220, 220))
    out = paths.PREVIEWS / out_name
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    return out


if __name__ == "__main__":
    for p in build_uranium() + build_others():
        print(p)
    print(review_sheet(ALL_ORES, "ores_review.png"))
