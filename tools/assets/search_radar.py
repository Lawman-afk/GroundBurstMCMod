"""The Search Radar and its wreck (DESIGN.md §22.2): the long-range search antenna of the Cold War air-defence
stations (the AN/FPS-20's "orange peel"), cosmetic, turning while it has a redstone signal; and the same antenna as the
world's old sites have it, toppled off its head and rusting.

The reflector is a parabolic cylinder eleven blocks wide and four high at its middle, its outline an ellipse (an orange
peel): eleven vertical strips set round the curve, each turned to face the feed horn at the focus, three blocks in
front on a boom. Ribs run across its back, struts reach back to the post on the turning head, the head sits on a
bearing drum on the block. Facing north (its front, the feed, to -z), as every model of the mod.

    tools/.venv/Scripts/python.exe tools/assets/search_radar.py
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

from gb import materials as M  # noqa: E402
from gb import paths  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.model import write_json  # noqa: E402
from gb.tex import Tex  # noqa: E402

#: The reflector: strips across, their width, the focal length, the middle of its height and its height there (pixels).
STRIPS, STRIP_W, FOCUS, MID_Y, HEIGHT = 11, 16, 52.0, 44.0, 64.0
BACK_Z = 14.0                                       # the reflector's middle stands this far behind the head's axis


def strip(i: int) -> tuple[float, float, float, float]:
    """Strip {i}: its middle's x and z, its height and the way it is turned about y (degrees) to face the focus."""
    x = (i - (STRIPS - 1) / 2) * STRIP_W
    z = BACK_Z - x * x / (4 * FOCUS)
    h = max(34.0, HEIGHT * math.sqrt(max(0.0, 1 - (x / (STRIPS * STRIP_W / 2 + 6)) ** 2)))
    turn = math.degrees(math.atan(x / (2 * FOCUS)))  # the slope of z = -x^2/4f is -x/2f: the strip turned to lie along it
    return x, z, h, turn


def reflector(g: GeoModel, parent: str, missing: tuple[int, ...] = (), bent: dict[int, float] | None = None) -> tuple[list, list]:
    """The strips (each a bone on the curve) with their back ribs; returns the strip boxes and the rib boxes."""
    faces, ribs = [], []
    for i in range(STRIPS):
        if i in missing:
            continue
        x, z, h, turn = strip(i)
        extra = (bent or {}).get(i, 0.0)
        bone = f"strip{i}"
        g.bone(bone, pivot=(x, MID_Y, z), parent=parent, rotation=(extra, turn, 0))
        w = STRIP_W / 2 + 0.3                          # a hair over half, so the turned strips close up
        g.box(f"s{i}", (x - w, MID_Y - h / 2, z - 0.75), (x + w, MID_Y + h / 2, z + 0.75), bone=bone)
        faces.append(f"s{i}")
        for k, dy in enumerate((-h / 3, 0.0, h / 3)):
            g.box(f"r{i}_{k}", (x - w + 0.5, MID_Y + dy - 1, z + 0.75), (x + w - 0.5, MID_Y + dy + 1, z + 2.75),
                  faces=("south", "up", "down", "east", "west"), bone=bone)
            ribs.append(f"r{i}_{k}")
    return faces, ribs


def head(g: GeoModel, parent: str) -> list[str]:
    """The turntable on the bearing, the post up to the reflector's middle, struts back to it, the boom and the horn."""
    g.box("turntable", (-6, 6, -6), (6, 10, 6), bone=parent)
    g.box("post", (-3, 10, -1), (3, MID_Y - 6, 5), faces=("north", "south", "east", "west"), bone=parent)
    g.box("yoke", (-5, MID_Y - 6, -2), (5, MID_Y + 2, 8), bone=parent)
    for n, a, b in (("strut_w", (-40, MID_Y - 2, 8), (-5, MID_Y + 1, 11)), ("strut_e", (5, MID_Y - 2, 8), (40, MID_Y + 1, 11))):
        g.box(n, a, b, faces=("north", "south", "up", "down"), bone=parent)
    focus_z = BACK_Z - FOCUS
    g.box("boom", (-1, MID_Y - 1, focus_z + 6), (1, MID_Y + 1, -2), faces=("east", "west", "up", "down"), bone=parent)
    g.box("boom_stay", (-0.5, MID_Y - 13, focus_z + 10), (0.5, MID_Y - 12, -2), faces=("east", "west", "up", "down"), bone=parent)
    g.box("horn", (-4, MID_Y - 5, focus_z - 2), (4, MID_Y + 5, focus_z + 6), bone=parent)
    return ["turntable", "post", "yoke", "strut_w", "strut_e", "boom", "boom_stay", "horn"]


def fallen_reflector(g: GeoModel, parent: str, z0: float, missing: tuple[int, ...] = (), bent: dict[int, float] | None = None
                     ) -> tuple[list, list]:
    """The reflector torn off and lying on its back on the ground: each strip laid along z, the curve now a trough
    (its ends risen as the strips roll up the sides), its middle at {z0}; some strips gone, some bent."""
    faces, ribs = [], []
    for i in range(STRIPS):
        if i in missing:
            continue
        x, _, h, turn = strip(i)
        flat = 3.0 * FOCUS                               # lying on its back it flattens out: a shallow trough
        y = 1.5 + x * x / (4 * flat)
        roll = math.degrees(math.atan(x / (2 * flat))) + (bent or {}).get(i, 0.0)
        bone = f"strip{i}"
        g.bone(bone, pivot=(x, y, z0), parent=parent, rotation=(0, 0, roll))
        w = STRIP_W / 2 + 0.3
        g.box(f"s{i}", (x - w, y - 0.75, z0 - h / 2), (x + w, y + 0.75, z0 + h / 2), bone=bone)
        faces.append(f"s{i}")
        for k, dz in enumerate((-h / 3, h / 3)):
            g.box(f"r{i}_{k}", (x - w + 0.5, y - 2.75, z0 + dz - 1), (x + w - 0.5, y - 0.75, z0 + dz + 1),
                  faces=("north", "south", "east", "west", "down"), bone=bone)
            ribs.append(f"r{i}_{k}")
    return faces, ribs


def build(wreck: bool) -> GeoModel:
    name = "search_radar_wreck" if wreck else "search_radar"
    g = GeoModel(name, tex_size=(256, 256))
    g.bone("base")
    g.box("drum", (-7, 0, -7), (7, 6, 7), bone="base")
    g.box("cable", (5, 0.5, -2), (8, 3, 2), faces=("north", "south", "east", "up"), bone="base")
    if not wreck:
        g.bone("head", pivot=(0, 6, 0), parent="base")
        g.bone("reflector", pivot=(0, MID_Y, BACK_Z), parent="head", rotation=(-6, 0, 0))      # tilted up a little
        metal = head(g, "head")
        faces, ribs = reflector(g, "reflector")
    else:
        # Fallen from its tower: the head on its side where it landed, its boom snapped short and pointing at the sky;
        # the reflector torn off and lying on its back in the grass behind it, turned askew, two strips gone and one
        # bent up out of the line.
        g.bone("head", pivot=(0, 6, 0), parent="base", rotation=(0, 35, 78))
        metal = head(g, "head")
        g.bone("reflector", pivot=(0, 0, 70), parent="base", rotation=(0, 18, 0))
        faces, ribs = fallen_reflector(g, "reflector", z0=70.0, missing=(2, 9), bent={1: 24.0, 8: -14.0})
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)
    S = M.Surfacer(g, seed=211)
    grey = M.painted(M.GREY_PAINT_RAMP, tone=0.52, wear=0.3 if wreck else 0.15, rust=0.45 if wreck else 0.05)
    S.paint(tex, ["drum", "cable"], M.painted(M.ARMOUR_RAMP, tone=0.5, wear=0.2, rust=0.4 if wreck else 0.05))
    S.paint(tex, metal, grey)
    S.paint(tex, ribs, M.galvanized(tone=0.5, rust=0.4 if wreck else 0.0))
    S.paint(tex, faces, M.white_paint(tone=0.74, wear=0.25 if wreck else 0.05, rust=0.35 if wreck else 0.0,
                                     grime=0.2 if wreck else 0.06))
    # The reflector's face is perforated: a grid of slots darkened on its concave (north) side.
    S.overlay(tex, faces, lambda f, img: M.grid(f, img, every=2.0, depth=0.82), only=("north",))
    g.save(tex)
    return g


def icon(wreck: bool) -> None:
    """The item's picture: the reflector seen from the front on its head (or fallen back, rusty)."""
    img = np.zeros((16, 16, 4), np.uint8)
    white = np.array([214, 214, 206, 255], np.uint8)
    shade = np.array([168, 170, 166, 255], np.uint8)
    grey = np.array([88, 92, 98, 255], np.uint8)
    dark = np.array([52, 55, 60, 255], np.uint8)
    rust = np.array([128, 70, 40, 255], np.uint8)
    for x in range(16):
        t = (x - 7.5) / 8.0
        half = 3.4 * math.sqrt(max(0.0, 1 - t * t)) + 0.6
        sag = 1.6 * t * t                                   # the ends of the curve come forward and down a little
        y0, y1 = 6 - half + sag, 6 + half + sag
        for y in range(16):
            if y0 <= y + 0.5 <= y1:
                img[y, x] = white if (x + y) % 2 or not wreck else rust
                if y + 0.5 > y1 - 1.2:
                    img[y, x] = shade
    img[10:13, 7:9] = grey                                  # the post
    img[13:16, 5:11] = dark                                 # the drum
    img[6, 3:13] = np.where(np.arange(3, 13)[:, None] % 2 == 0, shade, img[6, 3:13])
    if wreck:
        img = np.array(Image.fromarray(img).rotate(-28, resample=Image.NEAREST, center=(8, 12)))
    name = "search_radar_wreck" if wreck else "search_radar"
    Image.fromarray(img, "RGBA").save(paths.TEXTURES / "item" / f"{name}.png")
    write_json(paths.MODELS / "item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"groundburst:item/{name}"}})
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:item/{name}"}})


def block_files(name: str) -> None:
    """The block itself draws nothing (its block entity draws the model): a model with only its particle."""
    write_json(paths.MODELS / "block" / f"{name}.json", {"textures": {"particle": "groundburst:block/steel_panel"}})
    variants = {}
    for facing in ("north", "east", "south", "west"):
        for active in (("false", "true") if name == "search_radar" else (None,)):
            key = f"facing={facing}" + (f",active={active}" if active else "")
            variants[key] = {"model": f"groundburst:block/{name}"}
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": variants})


def main() -> None:
    build(False)
    build(True)
    turning = Animation("working", 6.0, loop=True)          # one turn every six seconds, as the stations' antennas
    turning.rotate("head", {0: (0, 0, 0), 6.0: (0, 360, 0)})
    idle = Animation("idle", 0.05, loop=False)
    idle.rotate("head", {0: (0, 0, 0)})
    print(save_animations("search_radar", [turning, idle]))
    print(save_animations("search_radar_wreck", [Animation("idle", 0.05, loop=False)]))
    icon(False)
    icon(True)
    block_files("search_radar")
    block_files("search_radar_wreck")
    print("search radar written")


if __name__ == "__main__":
    main()
