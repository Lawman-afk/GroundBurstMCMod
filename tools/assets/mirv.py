"""MIRVs (DESIGN.md §17.14): the two buses, the re-entry vehicle every RV flies as, the RV Decoy and the bus's screen.

Each bus is its missile's head (missiles.py mounts it as it does a warhead), so its block model is the payload shroud
the missile flies under, standing on the post-boost bus: a gunmetal mounting ring, the bus's equipment section ringed
with its attitude thrusters, then the shroud, split in two down its sides (it is thrown off before the RVs leave),
up to a tangent-ogive nose and a graphite tip.

- Strategic MIRV Bus: the big one (after the MX Peacekeeper's shroud): white, a black separation band at its foot,
  black seams down its two halves, a dark tip, a red stencil line.
- Heavy MIRV Bus: smaller, light grey, the yellow band of high explosive (its RVs are conventional), a black
  separation band, an olive bus under it.
- Re-entry vehicle (the look of every RV in flight: missiles.py's "re_entry_vehicle"): a slim cone (the Mk 12A's) of
  dark ablative heat shield on a flat aft cover, a graphite nose tip.
- RV Decoy: the same cone in crinkled copper film over a light frame (what radar sees is the cone).
- The bus's screen: the bus deck (its mounts, drawn by the screen from the cell on the texture's right), the load bar's
  frame, over the inventory.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\mirv.py
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb import paths  # noqa: E402
from gb.model import write_json  # noqa: E402
from gb.tex import Tex  # noqa: E402
import charges as C  # noqa: E402
import gui as GUI  # noqa: E402
import warheads as W  # noqa: E402

SIDES = W.SIDES


def _save(w: W.Build, look: W.Look) -> list[str]:
    """As a warhead is saved, but with no armed twin (nothing on it blinks)."""
    W.finish_model(w.m)
    w.layout()

    def painter(m, t: Tex, lamp_colour) -> None:
        k = w.kit()
        for group, names in w.groups.items():
            mat = look.materials.get(group) or look.materials.get("body")
            k.paint(t, names, mat)
            for fn in look.overlays.get(group, []):
                k.overlay(t, names, lambda f, img, fn=fn: fn(k, f, img))
    return C.save(w.m, painter, armed_too=False)


def seams(k, f, img) -> None:
    """The shroud's two halves meet down its sides (east and west): a dark line down the facet facing each way."""
    if f.d in SIDES:
        bearing = W.angle_of(f)
        mid = W.facet_mid(f)
        for side in (90.0, -90.0):
            on = np.abs(((bearing - side + 180) % 360) - 180) < 12
            M.darken(img, mid & on, 0.45)


def thrusters(y: float):
    """The bus's attitude thrusters: a small dark nozzle on every other facet at height y."""
    def fn(k, f, img):
        if f.d in SIDES:
            bearing = W.angle_of(f)
            every = (np.round(bearing / 45.0) % 2 == 0)
            row = np.floor(f.Y) == math.floor(y)
            mid = W.facet_mid(f) | np.roll(W.facet_mid(f), 1, axis=1)
            img[mid & row & every, :3] = P.BLACK_PAINT.deep[:3]
            M.lighten(img, mid & (np.floor(f.Y) == math.floor(y) + 1) & every, 1.15, 6)
    return fn


def shroud(name: str, r: float, hb: float, bus_h: float, skirt_h: float, nose_len: float, steps: int, shell: M.Material,
           bus: M.Material, marks) -> list[str]:
    """A bus under its shroud: its mounting ring, the bus's equipment section a little proud of the shroud, the shroud's
    straight skirt, an ogive nose and a tip. {marks}(look, foot) adds its bands and stencils."""
    w = W.Build(name)
    w.ring("base", r + 0.5, 0, hb, caps=("up", "down"), top=True, bottom=False, foot=True, stagger="base")
    y = hb
    w.ring("bus", r + 0.25, y, y + bus_h, caps=("up",), top=True)
    y += bus_h
    w.ring("shroud", r, y, y + skirt_h, caps=(), top=False)
    top = w.nose("nose", r, y + skirt_h, nose_len, "ogive", steps)
    W.tip(w, 0.9, top, 1.0, "tip")
    look = W.Look(base=W.BASE, bus=bus, shroud=shell, nose=shell, tip=W.metal(W.DARK, 0.4))
    look.on("base", lambda k, f, img: W.bolts(f, img, hb * 0.5))
    look.on("bus", thrusters(hb + bus_h * 0.5))
    look.on("shroud", seams).on("nose", seams)
    marks(look, y)
    return _save(w, look)


def strategic_bus() -> list[str]:
    white = W.paint(W.WHITE, 0.72, 0.18)
    black = W.paint(W.BLACK, 0.5, 0.15)

    def marks(look, foot):
        look.on("shroud", lambda k, f, img: W.band(k, f, img, foot, foot + 1, black))
        look.on("shroud", lambda k, f, img: W.stencil(f, img, (foot + 4.0,), W.RED[3], 7, "xx.xxxx.x"))
    return shroud("strategic_mirv_bus", 6.0, 1.5, 4.0, 6.0, 17.0, 17, white, W.metal(W.GUNMETAL, 0.42), marks)


def heavy_bus() -> list[str]:
    grey = W.paint(W.CASING, 0.6, 0.2)
    black = W.paint(W.BLACK, 0.5, 0.15)
    yellow = W.paint(W.YELLOW, 0.625, 0.25)

    def marks(look, foot):
        look.on("shroud", lambda k, f, img: W.band(k, f, img, foot, foot + 1, black))
        look.on("shroud", lambda k, f, img: W.band(k, f, img, foot + 2, foot + 3, yellow))
        look.on("shroud", lambda k, f, img: W.stencil(f, img, (foot + 4.0,), W.BLACK[2], 5, "x.xx.x"))
    return shroud("heavy_mirv_bus", 4.6, 1.2, 3.0, 4.0, 12.0, 12, grey, W.paint(W.OLIVE, 0.45), marks)


def cone(w: W.Build, group: str, r0: float, r1: float, y0: float, n: int) -> float:
    """A re-entry vehicle's cone, a pixel-tall ring at a time from radius r0 at y0 down to a blunt r1 (each ring at
    least 0.12 inside the one under it, as a nose's are), its rings' lips unlit so it reads as one smooth taper.
    Returns its top."""
    for i in range(n):
        r = r0 + (r1 - r0) * (i + 0.5) / n
        above = r0 + (r1 - r0) * (i + 1.5) / n if i + 1 < n else 0.0
        stagger = "alternate" if W.sides_for(r) == 16 and above >= 0.55 * r else "full"
        names = W.section(w.m, f"{group}{i}", round(r, 3), y0 + i, y0 + i + 1, W.sides_for(r), caps=("up",), stagger=stagger)
        w.add(group, names, False, False, False)
    w.last_r = r1
    return y0 + n


def reentry_vehicle() -> list[str]:
    """Every RV in flight: a slim cone of dark ablative heat shield on its aft cover, blunt-nosed with a graphite cap."""
    w = W.Build("re_entry_vehicle")
    r = 3.0
    w.ring("aft", r, 0, 1.0, caps=("up", "down"), top=True, bottom=True, foot=False, stagger="base")
    top = cone(w, "cone", r - 0.12, 0.85, 1.0, 16)
    W.tip(w, 0.6, top, 0.6, "tip")
    ablative = W.paint(W.ABLATIVE, 0.375, 0.08, mottle=0.06, grain=0.06, dither=0.6)
    look = W.Look(aft=W.metal(W.GUNMETAL, 0.36), cone=ablative, tip=W.metal(W.DARK, 0.45))
    look.on("aft", lambda k, f, img: W.bolts(f, img, 0.5))
    look.on("cone", lambda k, f, img: W.band(k, f, img, 2, 3, W.paint(W.ABLATIVE, 0.55, 0.1)))
    return _save(w, look)


def decoy() -> list[str]:
    """The RV Decoy: the RV's cone in crinkled copper film (a light frame inside), a thin aft ring."""
    w = W.Build("rv_decoy")
    r = 3.0
    w.ring("aft", r, 0, 0.8, caps=("up", "down"), top=True, bottom=True, foot=False, stagger="base")
    top = cone(w, "cone", r - 0.12, 0.85, 0.8, 15)
    W.tip(w, 0.55, top, 0.5, "tip")
    film = W.metal(P.COPPER, 0.62, brushed=0.0)

    def crinkle(k, f, img):
        n = M.fbm(f.X * 1.7, f.Y * 1.7, f.Z * 1.7, 2.0, 41, 3)
        M.lighten(img, n > 0.62, 1.3, 14)
        M.darken(img, n < 0.36, 0.72)
    look = W.Look(aft=W.metal(W.STEEL, 0.5), cone=film, tip=W.metal(W.STEEL, 0.55))
    look.on("cone", crinkle)
    return _save(w, look)


# --- the bus's screen ------------------------------------------------------------------------------------------------------------

RV_GHOST = ["................", "................", "........#.......", ".......###......", ".......###......", "......#####.....",
            "......#####.....", ".....#######....", ".....#######....", "....#########...", "....#########...", "...###########..",
            "...###########..", "...###########..", "................", "................"]


def screen() -> None:
    """The bus deck (dark steel, a hazard-striped lip) the screen sets its mounts on, the load bar's frame, the
    inventory; at (176, 0) the mount cell: a slot with a ghosted RV in it."""
    t = Tex(256, 256)
    G = GUI.PANEL
    GUI.window(t, H=173, inv_y=91)
    dx, dy, dw, dh = 8, 21, 82, 46
    t.rect(dx, dy, dw, dh, G[1])
    t.outline(dx, dy, dw, dh, G.outline)
    t.hline(dx + 1, dy + 1, dw - 2, G.deep)
    for i in range(dw - 2):                                         # the deck's lip: hazard stripes along its foot
        c = P.HAZARD_YELLOW[4] if ((i + 3) // 3) % 2 == 0 else P.HAZARD_BLACK[2]
        t.px(dx + 1 + i, dy + dh - 3, c)
        t.px(dx + 1 + i, dy + dh - 2, c)
    for x, y in ((dx + 2, dy + 2), (dx + dw - 4, dy + 2)):
        t.rivet(x, y, G, size=2)
    # The load bar's frame (the screen fills it).
    bx, by, bw, bh = 98, 30, 70, 7
    t.rect(bx, by, bw, bh, G.deep)
    t.outline(bx, by, bw, bh, G.outline)
    t.hline(bx + 1, by + bh - 1, bw - 2, G.light)
    # The mount cell, with its ghosted RV.
    GUI.slot(t, 176, 0)
    for yy, row in enumerate(RV_GHOST):
        for xx, c in enumerate(row):
            if c == "#":
                t.px(177 + xx, 1 + yy, G[2])
    for row in range(3):                                             # the player's inventory and hotbar
        for col in range(9):
            GUI.slot(t, 7 + col * 18, 90 + row * 18)
    for col in range(9):
        GUI.slot(t, 7 + col * 18, 148)
    t.save(paths.TEXTURES / "gui" / "mirv_bus.png")


def main() -> None:
    names = strategic_bus() + heavy_bus() + reentry_vehicle() + decoy()
    for n in names:
        area = W.check_caps(n)
        if area > 0:
            print(f"  cap flicker [{n}]: {area:.2f} px2 seen from above")
        for issue in W.check_faces(n)[:6]:
            print(f"  face flicker [{n}]: {issue}")
    for name in ("strategic_mirv_bus", "heavy_mirv_bus", "rv_decoy"):
        write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:block/{name}"}})
    screen()
    print("mirv written:", ", ".join(names))


if __name__ == "__main__":
    main()
