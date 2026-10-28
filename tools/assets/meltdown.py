"""Reactor meltdown assets (DESIGN.md §7): Corium (molten core remains, animated), Cooled Corium, and the Radiation
Sickness effect icon.

Corium: plates of dark, glassy crust floating on a glowing melt. The cracks between plates glow yellow-orange at
their hearts and deep red at their edges; four frames shift the heat along the cracks so it seems to seethe. Cooled
Corium: the same plates set solid, black-brown and glassy, the cracks gone dull with a few faint green flecks.
The effect icon: a green radiation trefoil on a dark disc, in the style of vanilla's 18 px effect icons.
"""
from __future__ import annotations

import json
import math
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gb import paths  # noqa: E402
from gb.model import write_json  # noqa: E402
from gb.palette import fixed  # noqa: E402
from gb.tex import Tex  # noqa: E402

CRUST = fixed("corium_crust", "#100c0b", "#1c1513", "#2a201c", "#3a2c26", "#4c3a31", "#614a3e", "#7a5e4c")
MELT = fixed("corium_melt", "#4a0c04", "#7a1606", "#b02a08", "#e0500c", "#ff8418", "#ffbc40", "#fff0a0")
SLAG = fixed("corium_slag", "#0b0a0a", "#141211", "#1e1b19", "#2a2522", "#38312d", "#4a413b", "#605650")
GREEN = (120, 210, 90, 255)

FRAMES = 4
SITES = [(2.5, 3.0), (9.0, 2.0), (14.0, 6.5), (4.5, 9.5), (10.5, 9.0), (2.0, 14.5), (8.0, 14.0), (14.5, 13.0), (12.5, 0.5)]


def _cells(x: float, y: float) -> tuple[float, float, int]:
    """Distances to the nearest and second-nearest plate centres (tiling), and the nearest plate's index."""
    best, second, index = 99.0, 99.0, 0
    for i, (sx, sy) in enumerate(SITES):
        for ox in (-16, 0, 16):
            for oy in (-16, 0, 16):
                d = math.hypot(x - sx - ox, y - sy - oy)
                if d < best:
                    best, second, index = d, best, i
                elif d < second:
                    second = d
    return best, second, index


def corium_frame(frame: int) -> Tex:
    t = Tex(16, 16)
    rng = random.Random(70 + frame)
    for y in range(16):
        for x in range(16):
            d1, d2, i = _cells(x + 0.5, y + 0.5)
            edge = d2 - d1                                # 0 on a crack, larger inside a plate
            # Heat runs along the cracks and pulses from frame to frame (a wave travelling round the tile).
            wave = 0.5 + 0.5 * math.sin((x * 0.7 + y * 0.45) - frame * math.pi / 2)
            if edge < 1.1:
                heat = (1.1 - edge) / 1.1 * (0.7 + 0.45 * wave)
                t.px(x, y, MELT[min(6, max(1, int(1 + heat * 5.5)))])
            else:
                # A crust plate: lit toward its upper left, a glint or two, darkening toward its rim.
                shade = 2 + (1 if (x + y + i) % 5 == 0 else 0) + (1 if edge > 2.6 else 0)
                t.px(x, y, CRUST[shade])
                if edge < 1.6 and wave > 0.6:
                    t.px(x, y, MELT[1])                    # the plate's rim glows dull red
    for _ in range(2):
        t.px(rng.randrange(16), rng.randrange(16), CRUST[6])
    return t


def corium() -> Tex:
    sheet = Tex(16, 16 * FRAMES)
    for f in range(FRAMES):
        frame = corium_frame(f)
        for y in range(16):
            for x in range(16):
                sheet.px(x, f * 16 + y, frame.get(x, y))
    return sheet


def cooled_corium() -> Tex:
    t = Tex(16, 16)
    rng = random.Random(77)
    for y in range(16):
        for x in range(16):
            d1, d2, i = _cells(x + 0.5, y + 0.5)
            edge = d2 - d1
            if edge < 0.9:
                t.px(x, y, SLAG[0])                        # set cracks
            else:
                shade = 2 + (1 if (x + 2 * y + i) % 6 == 0 else 0) + (1 if edge > 2.8 else 0)
                t.px(x, y, SLAG[shade])
                if edge < 1.6:
                    t.px(x, y, SLAG[1])                    # each plate's rim, sunk toward the crack
                elif edge < 2.2 and (x + y) % 2 == 0:
                    t.px(x, y, SLAG[4])                    # and a lighter lip just inside it
    for _ in range(4):                                     # glassy glints
        t.px(rng.randrange(16), rng.randrange(16), SLAG[6])
    for x, y in ((4, 6), (11, 12), (13, 3)):               # faint green flecks
        t.px(x, y, (*GREEN[:3], 150))
    return t


def effect_icon() -> Tex:
    t = Tex(18, 18)
    t.disc(9, 9, 8.2, (24, 30, 22, 255))
    t.disc(9, 9, 7.2, (38, 50, 34, 255))
    t.trefoil(9, 9, 6.2, GREEN)
    return t


def block(name: str, tex: Tex, animated: bool = False) -> None:
    path = paths.TEXTURES / "block" / f"{name}.png"
    tex.save(path)
    if animated:
        Path(str(path) + ".mcmeta").write_text(json.dumps({"animation": {"frametime": 6, "interpolate": True}}, indent=2) + "\n",
                                               encoding="utf-8")
    write_json(paths.MODELS / "block" / f"{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"groundburst:block/{name}"}})
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"groundburst:block/{name}"}}})
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:block/{name}"}})
    print(path)


def main() -> None:
    block("corium", corium(), animated=True)
    block("cooled_corium", cooled_corium())
    icon = paths.TEXTURES / "mob_effect" / "radiation_sickness.png"
    effect_icon().save(icon)
    print(icon)


if __name__ == "__main__":
    main()
