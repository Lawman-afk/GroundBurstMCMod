"""Capacitor Banks, three tiers (GeckoLib block models + animation), DESIGN.md §7. One block each.

Look: a riveted cabinet on a skid with a deck of four capacitor cans on top, joined by copper bus bars. The front
carries an eight-segment charge gauge (bones "bar0".."bar7", bottom to top; the renderer hides the bars above the
charge) running red, amber, green, an output terminal (red cap: the front is the output face) and a status lamp
that blinks while power moves (bone "lamp"). Tiers share the shape and differ in finish:
basic = olive cabinet, copper cans; advanced = gunmetal cabinet with brass trim, blue-banded cans;
superconducting = black cabinet ringed with frosted cryo-cyan coolant bands, white cans with cyan field glow;
creative (not craftable) = violet cabinet with brass trim, white cans with violet bands.
"""
from __future__ import annotations

import sys
from dataclasses import dataclass
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402

from gb import machine_kit as K  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.palette import Ramp  # noqa: E402
from gb.tex import Tex  # noqa: E402

BAR_COLORS = [P.WARNING_RED, P.WARNING_RED, P.NUCLEAR_AMBER, P.NUCLEAR_AMBER, P.HAZARD_YELLOW, P.INDICATOR_GREEN, P.INDICATOR_GREEN, P.INDICATOR_GREEN]
CANS = [(-3.6, -3.6), (3.6, -3.6), (-3.6, 3.6), (3.6, 3.6)]
DECK_Y = 13.0


@dataclass
class Tier:
    name: str
    body: Ramp
    trim: Ramp
    can: Ramp
    band: Ramp
    cryo: bool = False


TIERS = [
    Tier("capacitor_bank", P.OLIVE, P.STEEL, P.COPPER, P.COPPER),
    Tier("advanced_capacitor_bank", P.GUNMETAL, P.BRASS, P.STEEL, P.CHERENKOV),
    Tier("superconducting_capacitor_bank", P.BLACK_PAINT, P.STEEL, P.WHITE_CERAMIC, P.CRYO, cryo=True),
    Tier("creative_capacitor_bank", P.SINGULARITY, P.BRASS, P.WHITE_CERAMIC, P.ANTIMATTER),
]


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build(tier: Tier) -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(tier.name, tex_size=(128, 128))
    g.bone("frame")
    g.bone("lamp", pivot=(4.5, 10.5, -7.2), parent="frame")
    for i in range(8):
        g.bone(f"bar{i}", pivot=(0, 3.6 + i * 1.1, -7.3), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("skid", g.box("skid", (-8, 0, -8), (8, 1.5, 8), bone=F))
    add("body", g.box("body", (-7, 1.5, -7), (7, DECK_Y, 7), bone=F))
    add("deck", g.box("deck", (-7.5, DECK_Y, -7.5), (7.5, DECK_Y + 0.6, 7.5), bone=F))
    for i, (cx, cz) in enumerate(CANS):
        add("can", g.box(f"can{i}", (cx - 2.2, DECK_Y + 0.6, cz - 2.2), (cx + 2.2, DECK_Y + 2.4, cz + 2.2), faces=("north", "south", "east", "west", "up"), bone=F))
        add("terminal", g.box(f"term{i}", (cx - 0.5, DECK_Y + 2.4, cz - 0.5), (cx + 0.5, DECK_Y + 2.9, cz + 0.5), faces=("north", "south", "east", "west", "up"), bone=F))
    add("bus", g.box("bus_n", (-3.6, DECK_Y + 2.9, -3.9), (3.6, DECK_Y + 3.2, -3.3), bone=F))
    add("bus", g.box("bus_s", (-3.6, DECK_Y + 2.9, 3.3), (3.6, DECK_Y + 3.2, 3.9), bone=F))
    if tier.cryo:
        for i, y in enumerate((4.0, 8.0)):
            add("coolant", g.box(f"coolant{i}", (-7.15, y, -7.15), (7.15, y + 1.2, 7.15), faces=("north", "south", "east", "west"), bone=F))
    # Front: gauge window, bars, output terminal, status lamp.
    add("gauge", g.box("gauge", (-2.2, 3.0, -7.3), (2.2, 12.4, -7.0), faces=("north", "east", "west", "up", "down"), bone=F))
    bars = []
    for i in range(8):
        y = 3.6 + i * 1.1
        bars.append(add("bar", g.box(f"bar_box{i}", (-1.5, y, -7.5), (1.5, y + 0.8, -7.4), faces=("north",), shade=False, bone=f"bar{i}")))
    add("terminal_out", g.box("out_post", (3.6, 3.0, -7.8), (5.4, 4.8, -7.0), faces=("north", "east", "west", "up", "down"), bone=F))
    lamp = add("lamp", g.box("lamp_box", (4.0, 10.0, -7.4), (5.0, 11.0, -7.2), faces=("north",), shade=False, bone="lamp"))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, glow, parts, tier)
    return g, tex, glow


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict, tier: Tier) -> None:
    """A bank's surfaces, in model space (tools/gb/machine_kit.py; the machines' remaster): its tier's cabinet paint in
    a bolted panel with louvred heat sinks, trim of its tier's metal round the deck; the capacitor cans with a rating
    band, brass terminals and copper bus bars; the front's gauge window, charge bars, red output post and lamp."""
    k = K.Kit(g, parts, seed=333)
    k.plinth(tex, key="skid")
    body = M.painted(K.ramp_of(tier.body), tone=0.42 if tier.cryo else 0.5, wear=0.18)
    metal = lambda ramp, tone: M.Material(ramp=K.ramp_of(ramp), tone=tone, mottle=0.03, grain=0.03, dither=0.4, edge_light=0.16,   # noqa: E731
                                          edge_dark=0.1, ao=0.4, grime=0.03, dust=0.0, brushed=0.04)
    trim = metal(tier.trim, 0.55)
    k.paint(tex, "body", body)
    k.panels(tex, "body", inset=1, min_size=8)

    def louvres(f, img):
        a, b = f.plane_uv()
        inside = (f.border["left"] >= 3) & (f.border["right"] >= 3) & (f.border["top"] >= 3) & (f.border["bottom"] >= 3)
        M.darken(img, inside & ((np.floor(b) % 2) == 0), 0.45)
        M.lighten(img, inside & ((np.floor(b) % 2) == 1), 1.1, 4)
    k.overlay(tex, "body", louvres, only=("east", "west", "south"))
    k.paint(tex, "deck", trim)
    can = metal(tier.can, 0.6) if tier.can is not P.WHITE_CERAMIC else M.white_paint(tone=0.8, wear=0.05)
    k.paint(tex, "can", can)
    k.overlay(tex, "can", lambda f, img: M.paste(img, k.on(f, M.painted(K.ramp_of(tier.band), tone=0.6, wear=0.0)),
                                                 np.abs(f.border["top"] - f.border["bottom"]) <= 0.5) if f.vertical else None)
    k.overlay(tex, "can", lambda f, img: M.darken(img, K.ring(f) == 0, 0.7), only=("up",))
    k.paint(tex, "terminal", k.BRASS)
    k.paint(tex, "bus", k.COPPER)
    for b in parts.get("coolant", []):
        def band(t, r, d):
            x, y, w, h = r
            for xx in range(w):
                for yy in range(h):
                    t.px(x + xx, y + yy, P.CRYO[5] if (xx * 3 + yy) % 5 == 0 else P.CRYO[4])
        g.paint_all(tex, b, band)
        g.paint_all(glow, b, lambda t, r, d: t.hline(r[0], r[1], r[2], P.CRYO[3]))
    k.paint(tex, "gauge", trim)
    g.paint_all(tex, parts["gauge"][0], lambda t, r, d: (t.rect(*r, P.BLACK_PAINT[1]), t.outline(*r, tier.trim[4])) if d == "north" else None)
    for i, b in enumerate(parts["bar"]):
        ramp = BAR_COLORS[i]
        g.paint_all(tex, b, lambda t, r, d, ramp=ramp: (t.rect(*r, ramp[4]), t.hline(r[0], r[1], r[2], ramp[6])))
        g.paint_all(glow, b, lambda t, r, d, ramp=ramp: (t.rect(*r, ramp[4]), t.hline(r[0], r[1], r[2], ramp[6])))
    k.paint(tex, "terminal_out", k.RED)

    def lamp_paint(t, r, d):
        t.rect(*r, P.CHERENKOV.light)
    g.paint_all(tex, parts["lamp"][0], lamp_paint)
    g.paint_all(glow, parts["lamp"][0], lamp_paint)


def animations() -> list[Animation]:
    """While power moves, the status lamp blinks (scaled away for half of each 0.6 s cycle)."""
    w = Animation("working", 0.6, loop=True)
    w.scale("lamp", {0: (1, 1, 1), 0.29: (1, 1, 1), 0.3: (0, 0, 0), 0.59: (0, 0, 0), 0.6: (1, 1, 1)})
    return [w]


if __name__ == "__main__":
    for tier in TIERS:
        model, texture, glowmask = build(tier)
        for p in model.save(texture, glowmask):
            print(p)
        print(save_animations(tier.name, animations()))
