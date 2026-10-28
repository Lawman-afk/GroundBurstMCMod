"""Fuel Channel (DESIGN.md §23.3): the block that stands in a Fission Reactor's core in columns, one fuel rod a column.

A fuel assembly seen whole: four stainless corner posts, a bundle of zircaloy-clad fuel pins between them (four to a
side, dark water between the pins), and spacer-grid bands round the bundle near the top and the bottom of every block,
so a column of them reads as one long assembly with a grid every block. Painted with the material library (DESIGN.md §4.3:
every model). Two models: as built, and lit (the `lit` state while its reactor runs), where the water between the pins
glows Cherenkov blue and the bundle is drawn at full brightness.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\fuel_channel.py
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402

from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb import paths  # noqa: E402
from gb.model import BlockModel, write_json  # noqa: E402
from gb.palette import fixed  # noqa: E402
from gb.tex import Tex  # noqa: E402

ZIRCALOY = fixed("zircaloy", "#262b28", "#38403b", "#4d5650", "#646d66", "#7e877e", "#9da59b", "#c3cabf")
POSTS = (("post_nw", 2, 2), ("post_ne", 12, 2), ("post_sw", 2, 12), ("post_se", 12, 12))


def _ramp(r) -> np.ndarray:
    return np.array([s[:3] for s in r.shades], dtype=np.float32)


def build(lit: bool) -> BlockModel:
    name = "fuel_channel_lit" if lit else "fuel_channel"
    m = BlockModel(name, tex_size=(64, 64), kind="block")
    for post, x, z in POSTS:
        m.box(post, (x, 0, z), (x + 2, 16, z + 2))
    m.box("bundle", (4, 0, 4), (12, 16, 12), light=15 if lit else 0)
    grids = []
    for level, (y0, y1) in (("lo", (1, 3)), ("hi", (13, 15))):
        for side, frm, to in (("n", (4, y0, 2), (12, y1, 4)), ("s", (4, y0, 12), (12, y1, 14)),
                              ("w", (2, y0, 4), (4, y1, 12)), ("e", (12, y0, 4), (14, y1, 12))):
            grids.append(m.box(f"grid_{level}_{side}", frm, to).name)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    S = M.Surfacer(m, seed=211, ao_reach=2.0)
    S.paint(t, [p for p, _, _ in POSTS], M.steel(tone=0.6, brushed=0.06))
    S.overlay(t, [p for p, _, _ in POSTS], lambda f, img: M.darken(img, f.border["top"] < 1, 0.8) if f.d == "up" else None)
    zirc = M.Material(ramp=_ramp(ZIRCALOY), tone=0.62, mottle=0.02, grain=0.02, dither=0.4, edge_light=0.12, edge_dark=0.1,
                      ao=0.25, grime=0.0, dust=0.0, brushed=0.04)
    S.paint(t, grids, zirc)

    def straps(f, img):                                          # the spacer grid's straps, every other texel
        a, _ = f.plane_uv()
        if f.vertical:
            M.darken(img, (np.floor(a) % 2) == 1, 0.72)
    S.overlay(t, grids, straps)
    S.paint(t, "bundle", zirc)
    glow = _ramp(P.CHERENKOV)

    def pins(f, img):
        a, b = f.plane_uv()
        if f.vertical:
            gap = (np.floor(a) % 2) == 1
        else:
            gap = ((np.floor(a) % 2) == 1) | ((np.floor(b) % 2) == 1)
        if lit:
            img[gap, :3] = glow[4] if f.vertical else glow[5]   # the water between the pins glows
            pin = ~gap
            img[pin, :3] = np.clip(img[pin, :3] * 0.45 + glow[1] * 0.45, 0, 255)   # the pins dark against it
        else:
            img[gap, :3] = _ramp(ZIRCALOY)[0] * 0.8               # dark water between the pins
            if not f.vertical:
                M.lighten(img, ~gap, 1.12, 6)                     # the pins' end caps catch the light
    S.overlay(t, "bundle", pins)
    m.particle = f"{paths.MODID}:block/{name}"
    m.fit_display(gui_rotation=(30, 225, 0))
    m.save(t)
    return m


if __name__ == "__main__":
    build(False)
    build(True)
    write_json(paths.ASSETS / "blockstates" / "fuel_channel.json", {"variants": {
        "lit=false": {"model": f"{paths.MODID}:block/fuel_channel"},
        "lit=true": {"model": f"{paths.MODID}:block/fuel_channel_lit"},
    }})
    write_json(paths.ASSETS / "items" / "fuel_channel.json", {"model": {"type": "minecraft:model", "model": f"{paths.MODID}:block/fuel_channel"}})
    print("fuel channel written")
