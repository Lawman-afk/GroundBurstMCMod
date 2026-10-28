"""Diesel Generator (GeckoLib block model + animation), DESIGN.md §7. One block.

Look: a twin-cylinder stationary engine on the shared hazard-striped plinth. Olive crankcase with a radiator grille on
the front, two cylinder barrels on top whose piston crowns (bones "piston0", "piston1") rise and fall in turn, a
spoked flywheel on the east side ("flywheel"), a fuel tank along the west side with a fuel-coloured band, and an
exhaust stack at the back corner fed by a manifold from both barrels that puffs smoke while running ("puff0".."puff2"). A small panel with a lamp and a
gauge faces the front.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402

from gb import machine_kit as K  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.palette import fixed  # noqa: E402
from gb.tex import Tex  # noqa: E402

NAME = "diesel_generator"
SMOKE = fixed("smoke", "#1c1c1e", "#2c2c30", "#3e3e44", "#55555c", "#6e6e76", "#8a8a92", "#a8a8b0")
CYL_X = (-2.6, 1.6)            # barrel centres (x); z centre 0.5
BARREL_TOP = 12.0
FLY_C = (5.4, 6.5, 0.5)         # flywheel centre


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("frame")
    for i, x in enumerate(CYL_X):
        g.bone(f"piston{i}", pivot=(x, BARREL_TOP, 0.5), parent="frame")
    g.bone("flywheel", pivot=FLY_C, parent="frame")
    for i in range(3):
        g.bone(f"puff{i}", pivot=(-6, 15, 6), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("plinth", g.box("plinth", (-8, 0, -8), (8, 2, 8), bone=F))
    add("crankcase", g.box("crankcase", (-5, 2, -4.5), (4.4, 9, 5.5), bone=F))
    add("deck", g.box("deck", (-5.4, 9, -3.5), (4.8, 9.8, 4.5), bone=F))
    for i, x in enumerate(CYL_X):
        add("barrel", g.box(f"barrel{i}", (x - 1.7, 9.8, -1.2), (x + 1.7, BARREL_TOP, 2.2), faces=("north", "south", "east", "west", "up"), bone=F))
        add("crown", g.box(f"crown{i}", (x - 1.1, BARREL_TOP - 1.0, -0.6), (x + 1.1, BARREL_TOP + 0.3, 1.6), faces=("north", "south", "east", "west", "up"), bone=f"piston{i}"))
        add("injector", g.box(f"injector{i}", (x - 0.3, BARREL_TOP + 0.3, 0.2), (x + 0.3, BARREL_TOP + 1.2, 0.8), faces=("north", "south", "east", "west", "up"), bone=f"piston{i}"))
    # Flywheel: an octagon from a square and a 45-degree square, with a hub; it turns about the east-west axis.
    cx, cy, cz = FLY_C
    add("fly", g.box("fly_a", (cx - 0.4, cy - 3.2, cz - 3.2), (cx + 0.4, cy + 3.2, cz + 3.2), bone="flywheel"))
    add("fly", g.box("fly_b", (cx - 0.3, cy - 3.2, cz - 3.2), (cx + 0.3, cy + 3.2, cz + 3.2), rotation={"origin": FLY_C, "axis": "x", "angle": 45}, bone="flywheel"))
    add("hub", g.box("hub", (cx + 0.4, cy - 0.8, cz - 0.8), (cx + 0.9, cy + 0.8, cz + 0.8), faces=("north", "south", "east", "up", "down"), bone="flywheel"))
    add("shaft", g.box("shaft", (4.4, cy - 0.5, cz - 0.5), (cx - 0.4, cy + 0.5, cz + 0.5), faces=("north", "south", "up", "down"), bone=F))
    # Fuel tank along the west side.
    add("tank", g.box("tank", (-7.6, 2, -5.5), (-5, 6.5, 3.5), bone=F))
    add("tank_cap", g.box("tank_cap", (-6.8, 6.5, -4.2), (-5.8, 7.1, -3.2), faces=("north", "south", "east", "west", "up"), bone=F))
    # Exhaust stack at the back-west corner with a rain cap.
    add("stack", g.box("stack", (-7.0, 2, 4.6), (-5.5, 14.5, 6.2), faces=("north", "south", "east", "west"), bone=F))
    add("stack_cap", g.box("stack_cap", (-7.4, 14.5, 4.2), (-5.1, 15.0, 6.6), bone=F))
    # Exhaust manifold lying on the crankcase's back ledge behind the deck: it runs from the stack to a capped end
    # behind the far barrel, and each barrel's exhaust port runs along the deck straight into it.
    add("pipe", g.box("manifold", (-5.5, 9.0, 4.5), (CYL_X[1] + 0.4, 10.8, 5.5), faces=("north", "south", "up"), bone=F))
    add("pipe_cap", g.box("manifold_cap", (CYL_X[1] + 0.4, 9.0, 4.5), (CYL_X[1] + 1.0, 11.0, 5.6), faces=("north", "south", "east", "west", "up"), bone=F))
    for i, x in enumerate(CYL_X):
        add("pipe", g.box(f"port{i}", (x - 0.4, 9.8, 2.2), (x + 0.4, 10.6, 4.5), faces=("east", "west", "up"), bone=F))
    for i in range(3):
        px, pz = (-6.2, -5.6, -6.6)[i], (5.6, 5.0, 5.2)[i]
        add("puff", g.box(f"puff_box{i}", (px - 0.6, 15.3 + i * 0.3, pz - 0.6), (px + 0.6, 16.3 + i * 0.3, pz + 0.6), faces=("north", "south", "east", "west", "up"), bone=f"puff{i}"))
    # Front control panel with lamp and gauge.
    add("panel", g.box("panel", (-3.0, 3, -5.6), (1.5, 7.5, -4.5), faces=("north", "east", "west", "up", "down"), bone=F))
    lamp = add("lamp", g.box("lamp", (0.2, 6.0, -5.8), (1.0, 6.8, -5.6), faces=("north",), shade=False, bone=F))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, glow, parts)
    return g, tex, glow


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The diesel's surfaces, in model space (tools/gb/machine_kit.py; the machines' remaster): an olive crankcase with
    a radiator grille on its front and bolted sides; a gunmetal deck; finned steel cylinder barrels with bright crowns
    and brass injectors; a dark spoked flywheel; a red fuel tank with a yellow band; a rusted, sooty exhaust stack."""
    k = K.Kit(g, parts, seed=315)
    k.plinth(tex)
    k.paint(tex, "crankcase", k.OLIVE)
    k.panels(tex, "crankcase", inset=1, min_size=5, only=("east", "west"))

    def radiator(f, img):
        a, b = f.plane_uv()
        inside = (K.ring(f) >= 1) & (f.border["bottom"] >= 2)
        img[inside, :3] = np.clip(img[inside, :3] * 0.32, 0, 255)
        M.lighten(img, inside & ((np.floor(b) % 2) == 1), 2.6, 14)            # slats over the dark core
    k.overlay(tex, "crankcase", radiator, only=("north",))
    k.paint(tex, ["deck", "panel", "pipe_cap"], k.GUN)
    k.paint(tex, ["barrel", "crown", "hub", "shaft"], k.STEEL)
    k.ribs(tex, "barrel", every=2, k=0.6)
    k.overlay(tex, "barrel", lambda f, img: (img.__setitem__((K.ring(f) >= 1, slice(0, 3)), P.BLACK_PAINT[1][:3])), only=("up",))
    k.paint(tex, "injector", k.BRASS)
    k.paint(tex, "fly", k.DARK)

    def fly(t, r, d):
        x, y, w, h = r
        if d in ("east", "west") and min(w, h) >= 5:
            t.line(x + 1, y + 1, x + w - 2, y + h - 2, P.GUNMETAL[2])              # spokes
            t.line(x + w - 2, y + 1, x + 1, y + h - 2, P.GUNMETAL[2])
            t.px(x + 1, y + h // 2, P.WARNING_RED.base)                          # balance mark: shows the turn
    for b in parts["fly"]:
        g.paint_all(tex, b, fly)
    k.paint(tex, "tank", M.painted(M.RED_PAINT_RAMP, tone=0.42, wear=0.3))
    k.overlay(tex, "tank", lambda f, img: M.paste(img, k.on(f, k.YELLOW), np.abs(f.border["top"] - f.border["bottom"]) <= 1)
              if f.vertical and f.shape[0] >= 4 else None, only=("west", "north", "south"))
    k.paint(tex, "tank_cap", k.BRASS)
    rusted = M.painted(M.ARMOUR_RAMP, tone=0.4, wear=0.5, rust=0.6)
    k.paint(tex, ["stack", "pipe"], rusted)
    k.soot(tex, "stack", reach=3, strength=0.8)
    k.paint(tex, "stack_cap", k.BLACK)
    for b in parts["puff"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, SMOKE[5] if d == "up" else SMOKE[4]))

    def panel(t, r, d):
        x, y, w, h = r
        if d == "north":
            K.dial(t, x + 1, y + 1, 2)
    g.paint_all(tex, parts["panel"][0], panel)

    def lamp_paint(t, r, d):
        t.rect(*r, P.NUCLEAR_AMBER.light)
    g.paint_all(tex, parts["lamp"][0], lamp_paint)
    g.paint_all(glow, parts["lamp"][0], lamp_paint)


def animations() -> list[Animation]:
    """0.5 s per revolution: the crowns rise and fall a half-turn apart, the flywheel turns once, smoke puffs."""
    w = Animation("working", 0.5, loop=True)
    w.move("piston0", {0: (0, 0, 0), 0.25: (0, -1.0, 0), 0.5: (0, 0, 0)}, easing="easeInOutSine")
    w.move("piston1", {0: (0, -1.0, 0), 0.25: (0, 0, 0), 0.5: (0, -1.0, 0)}, easing="easeInOutSine")
    w.rotate("flywheel", {0: (0, 0, 0), 0.5: (-360, 0, 0)})
    for i in range(3):
        keys_m, keys_s = {}, {}
        for k in range(3):
            t = round(k * 0.25, 2)
            phase = ((t + i / 3 * 0.5) % 0.5) / 0.5
            keys_m[t] = (0, phase * 2.5, 0)
            s = 0.4 + phase
            keys_s[t] = (s, s, s)
        w.move(f"puff{i}", keys_m)
        w.scale(f"puff{i}", keys_s)
    return [w]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
