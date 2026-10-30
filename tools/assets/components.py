"""Warhead components (DESIGN.md §9.2-9.4, Phase 7): item models for the payload cores, casings and fuzes that the
Ordnance Assembly Bench puts together. Each core wears its warhead's colours, so a core reads as the warhead it makes.

Payload cores:
- HE Charge: an olive explosive drum, a yellow band, a steel detonator well on top.
- Heavy HE Charge: a bigger olive drum, two yellow bands, lifting lugs.
- Thermobaric Charge: a gunmetal fuel canister, two orange bands, a burster tube with a red cap.
- Cluster Dispenser: a plate of four yellow bomblets held by corner posts.
- Penetrator Charge: a slim dark slug with a stepped tungsten point, a yellow band.
- Incendiary Charge: a silver napalm canister, a red band, a brass valve with a red handwheel.
- Massive Ordnance Charge: a fat olive drum with an orange cap and band.
- Gas / Smoke / Cryo / EMP Canister Dispensers: four canisters (the grenades' cans) strapped on a plate.
- Toxic Waste Tank: a dark drum with a glowing green band, a hazard-striped rim and a spout.
- Chemical Agent Canister: a pale grey pressure bottle with two green bands and a brass valve.
- Antimatter Trap (Phase 11): a short white ceramic canister on a dark base, two violet coils glowing between dark collars
  (its superconducting magnets), a dark cap round a glowing port: the Antimatter Warhead in small. Painted with the
  material library, as every new model is (the cores before it are plainly shaded).
- Singularity Core (Phase 11): a small black sphere, its violet core glowing through a slit, in a cage of two hoops (a
  steel one standing, a brass one round its waist) on a dark base: the Singularity Warhead in small. Material library.
Casings: steel shells open at the top on a mounting ring, bigger by class, with yellow class bands (one Light, two Medium, three Heavy).
Fuzes: a steel nose fuze each, told apart by the head: Impact (a red plunger), Barometric (vent ports and a brass
pressure ring).
ICBM Assembly Hall parts (slice 8c):
- Fusion Secondary: a dark depleted-uranium capsule, a pale lithium-deuteride band showing round its middle, end caps,
  the plutonium spark plug's copper terminal standing out of the top.
- Cobalt-59 Jacket: a sleeve of riveted cobalt-blue plates round an open middle.
- Intercontinental Booster: three olive stages narrowing upward on four nozzles, the missile's white and yellow bands.
- Tertiary Stage (8d, the Tsar's): a fat lead radiation case, two dark slots where the two secondaries' pale lithium
  deuteride shows, a hazard band, a gunmetal cap with the two spark plugs' copper terminals, a yellow stencil 3.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import palette as P  # noqa: E402
from gb.model import BlockModel  # noqa: E402
from gb.palette import fixed  # noqa: E402
from gb.tex import Tex  # noqa: E402
import grenades as G  # noqa: E402

prism, shaded, fill_all = G.prism, G.shaded, G.fill_all
OLIVE, STEEL, GUN, BRASS, YELLOW, BLACK = P.OLIVE, P.STEEL, P.GUNMETAL, P.BRASS, P.HAZARD_YELLOW, P.HAZARD_BLACK
ORANGE = fixed("bomb_orange", "#3e1a06", "#6a2c0a", "#9a4210", "#c45c1a", "#de7a30", "#ee9c5a", "#f8c898")
CASING = fixed("cluster_casing", "#5a5e62", "#7a7e82", "#9a9ea2", "#b8bcc0", "#d0d4d6", "#e2e4e6", "#f2f4f4")
DARK = fixed("penetrator_dark", "#16181a", "#202326", "#2c3034", "#3a3e44", "#4a4f56", "#5e646c", "#767c84")
WASTE = fixed("waste_drum", "#14160e", "#1e2214", "#2a301c", "#363e24", "#46502e", "#58643a", "#6e7a4a")
SHELL = fixed("casing_steel", "#2c3034", "#454a50", "#62686e", "#80868c", "#9ca2a8", "#b8bec2", "#d6dadc")
GLASS = fixed("sensor_glass", "#05080a", "#0c1216", "#141e24", "#1e2c34", "#2a3c46", "#3a525e", "#5a7684")
SIDES = ("north", "south", "east", "west")
WHITE_STRIPE = P.WHITE_CERAMIC[5]


def model(name: str, size: int = 64) -> BlockModel:
    return BlockModel(name, tex_size=(size, size), kind="item", render_type="minecraft:cutout")


def banded(ramp, bands: list[tuple[int, tuple]], body_top: float, body_y0: float, letter: str | None = None, letter_colour=None):
    """Shading for a round body, with coloured bands at heights (model y) and an optional stencil letter on the front."""
    def fn(t: Tex, r, d):
        shaded(ramp)(t, r, d)
        x, y, w, h = r
        if d in SIDES:
            for by, colour in bands:
                row = y + max(0, min(h - 1, int(round((body_top - by) * h / (body_top - body_y0) - 0.5))))
                t.hline(x, row, w, colour)
            if letter and d == "north" and w >= 4:
                t.text(x + (w - Tex.text_width(letter)) // 2, y + 1, letter, letter_colour)
    return fn


# --- payload cores ----------------------------------------------------------------------------------------------------------
def he_charge() -> None:
    m = model("he_charge")
    body = prism(m, "body", 8, 8, 3.5, 2.5, 2, 10)
    m.box("well", (7, 10, 7), (9, 10.6, 9))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    fill_all(m, t, body, banded(OLIVE, [(8, YELLOW.base)], 10, 2))
    m.paint_all(t, "well", lambda t, r, d: (shaded(STEEL)(t, r, d), t.px(r[0] + r[2] // 2, r[1] + r[3] // 2, BLACK.base) if d == "up" else None))
    G.finish(m, t)


def heavy_he_charge() -> None:
    m = model("heavy_he_charge")
    body = prism(m, "body", 8, 8, 4.5, 3, 1, 11)
    for i, x in enumerate((5.5, 9.5)):
        m.box(f"lug{i}", (x, 11, 7.5), (x + 1, 12.2, 8.5), faces=("north", "south", "east", "west", "up"))
    m.box("well", (7, 11, 7), (9, 11.6, 9))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    fill_all(m, t, body, banded(OLIVE, [(8.5, YELLOW.base), (9.7, YELLOW.base)], 11, 1))
    fill_all(m, t, ["lug0", "lug1"], shaded(STEEL))
    m.paint_all(t, "well", shaded(STEEL))
    G.finish(m, t)


def thermobaric_charge() -> None:
    m = model("thermobaric_charge")
    body = prism(m, "body", 8, 8, 4, 2.5, 2, 10)
    tube = prism(m, "tube", 8, 8, 1.2, 0.6, 10, 12.5)
    m.box("tube_cap", (7.2, 12.5, 7.2), (8.8, 13.2, 8.8))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    fill_all(m, t, body, banded(GUN, [(4, ORANGE[4]), (8, ORANGE[4])], 10, 2))
    fill_all(m, t, tube, shaded(STEEL))
    m.paint_all(t, "tube_cap", shaded(P.WARNING_RED))
    G.finish(m, t)


def cluster_dispenser() -> None:
    m = model("cluster_dispenser")
    m.box("plate", (3, 2, 3), (13, 3, 13))
    bomblets = []
    for i, (x, z) in enumerate(((5.75, 5.75), (10.25, 5.75), (5.75, 10.25), (10.25, 10.25))):
        bomblets += prism(m, f"b{i}", x, z, 1.6, 1.0, 3, 8)
        m.box(f"rib{i}", (x - 0.4, 8, z - 0.4), (x + 0.4, 9.2, z + 0.4), faces=("north", "south", "east", "west", "up"))
    for i, (x, z) in enumerate(((3, 3), (12, 3), (3, 12), (12, 12))):
        m.box(f"post{i}", (x, 3, z), (x + 1, 9.5, z + 1), faces=("north", "south", "east", "west", "up"))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)

    def plate(t, r, d):
        shaded(CASING)(t, r, d)
        if d in SIDES:
            x, y, w, h = r
            for xx in range(x + 1, x + w - 1, 3):
                t.px(xx, y + h // 2, BLACK[3])                    # the black dashes of the cluster casing
    m.paint_all(t, "plate", plate)
    fill_all(m, t, bomblets, banded(YELLOW, [(4, BLACK[3])], 8, 3))
    fill_all(m, t, [f"rib{i}" for i in range(4)], shaded(P.WHITE_CERAMIC))
    fill_all(m, t, [f"post{i}" for i in range(4)], shaded(CASING))
    G.finish(m, t)


def penetrator_charge() -> None:
    m = model("penetrator_charge")
    body = prism(m, "body", 8, 8, 2, 1.2, 1, 10.5)
    point = prism(m, "point", 8, 8, 1.4, 0.8, 10.5, 12.5)
    m.box("tip", (7.5, 12.5, 7.5), (8.5, 14, 8.5))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    fill_all(m, t, body, banded(DARK, [(3, YELLOW.base)], 10.5, 1))
    fill_all(m, t, point + ["tip"], shaded(P.TUNGSTEN))
    G.finish(m, t)


def incendiary_charge() -> None:
    m = model("incendiary_charge")
    body = prism(m, "body", 8, 8, 3.5, 2.5, 2, 9.5)
    m.box("valve", (7.25, 9.5, 7.25), (8.75, 11, 8.75))
    m.box("wheel", (6.2, 11, 6.2), (9.8, 11.5, 9.8))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    fill_all(m, t, body, banded(STEEL, [(7.5, P.WARNING_RED.base)], 9.5, 2))
    m.paint_all(t, "valve", shaded(BRASS))

    def wheel(t, r, d):
        x, y, w, h = r
        if d == "up":
            t.outline(x, y, w, h, P.WARNING_RED.base)              # a spoked handwheel
            t.hline(x, y + h // 2, w, P.WARNING_RED.shadow)
            t.vline(x + w // 2, y, h, P.WARNING_RED.shadow)
        else:
            t.rect(x, y, w, h, P.WARNING_RED.shadow)
    m.paint_all(t, "wheel", wheel)
    G.finish(m, t)


def massive_ordnance_charge() -> None:
    m = model("massive_ordnance_charge")
    body = prism(m, "body", 8, 8, 5.5, 3.5, 1, 10.5)
    cap = prism(m, "cap", 8, 8, 4, 2.5, 10.5, 12)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    fill_all(m, t, body, banded(OLIVE, [(9, ORANGE[4])], 10.5, 1))
    fill_all(m, t, cap, shaded(ORANGE))
    G.finish(m, t)


def ball(m: BlockModel, name: str, r: float, y0: float) -> list[str]:
    """A rough sphere of stacked "plus" prisms, radius r, its bottom at y0."""
    boxes = []
    rows = [(0.0, 0.25, 0.6), (0.25, 0.75, 0.92), (0.75, 1.25, 1.0), (1.25, 1.75, 0.92), (1.75, 2.0, 0.6)]
    for i, (a, b, k) in enumerate(rows):
        boxes += prism(m, f"{name}{i}", 8, 8, round(r * k, 2), round(r * k * 0.66, 2), y0 + a * r, y0 + b * r)
    return boxes


def tactical_fission_core() -> None:
    """A small plutonium pit in its lens shell: a steel ball with a hazard band, on a base plate with a trefoil tag."""
    m = model("tactical_fission_core")
    m.box("plate", (4, 1, 4), (12, 2, 12))
    shell = ball(m, "shell", 3.2, 2)
    m.box("tag", (7, 2, 11.6), (9, 4, 12), faces=("south", "north", "east", "west", "up"))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    m.paint_all(t, "plate", shaded(GUN))
    fill_all(m, t, shell, shaded(STEEL))

    def equator(t: Tex, r, d):                           # the ball's widest row: a yellow hazard band round it
        shaded(STEEL)(t, r, d)
        if d in SIDES:
            x, y, w, h = r
            t.rect(x, y, w, h, YELLOW.base)
            for xx in range(x, x + w, 3):
                t.px(xx, y + h // 2, BLACK.base)
    fill_all(m, t, [n for n in shell if n.startswith("shell2")], equator)
    m.paint_all(t, "tag", lambda t, r, d: (t.rect(*r, YELLOW.base), t.px(r[0] + r[2] // 2, r[1] + r[3] // 2, BLACK.base)))
    G.finish(m, t)


def fission_core() -> None:
    """An implosion sphere: a ball tiled with high-explosive lenses (tan and brown), detonator leads, in a cradle."""
    m = model("fission_core")
    m.box("cradle", (3, 2, 6), (13, 3.5, 10))
    m.box("foot_w", (3, 1, 4), (5, 2, 12))
    m.box("foot_e", (11, 1, 4), (13, 2, 12))
    shell = ball(m, "lens", 4.6, 2.2)
    m.box("leads", (7.4, 11.4, 7.4), (8.6, 12.6, 8.6))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    for n in ("cradle", "foot_w", "foot_e"):
        m.paint_all(t, n, shaded(GUN))
    TAN = fixed("lens_tan", "#3a2a14", "#5a4220", "#7a5c2e", "#9a7840", "#b89656", "#d0b274", "#e6d0a0")

    def lenses(t: Tex, r, d):                             # lens tiles with dark seams between them
        x, y, w, h = r
        t.rect(x, y, w, h, TAN[{"up": 5, "down": 2, "north": 4, "west": 4}.get(d, 3)])
        for xx in range(x + 2, x + w, 3):
            t.vline(xx, y, h, TAN[1])
        if d in SIDES:
            for yy in range(y + 1, y + h, 3):
                t.hline(x, yy, w, TAN[1])
    fill_all(m, t, shell, lenses)
    m.paint_all(t, "leads", shaded(P.COPPER))
    G.finish(m, t)


def boosted_fission_core() -> None:
    """A lens sphere on its cradle with the boost gas beside it: a tritium bottle and a deuterium bottle, piped in."""
    m = model("boosted_fission_core")
    m.box("cradle", (3, 2, 6), (13, 3.5, 10))
    m.box("foot_w", (3, 1, 4), (5, 2, 12))
    m.box("foot_e", (11, 1, 4), (13, 2, 12))
    shell = ball(m, "lens", 4.2, 2.6)
    bottles = prism(m, "bottle_t", 3.2, 12.2, 1.2, 0.8, 3.5, 8) + prism(m, "bottle_d", 12.8, 12.2, 1.2, 0.8, 3.5, 8)
    m.box("pipe", (3.6, 8, 11.8), (12.4, 8.6, 12.6))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    for n in ("cradle", "foot_w", "foot_e"):
        m.paint_all(t, n, shaded(GUN))
    TAN = fixed("lens_tan_b", "#3a2a14", "#5a4220", "#7a5c2e", "#9a7840", "#b89656", "#d0b274", "#e6d0a0")

    def lenses(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, TAN[{"up": 5, "down": 2, "north": 4, "west": 4}.get(d, 3)])
        for xx in range(x + 2, x + w, 3):
            t.vline(xx, y, h, TAN[1])
        if d in SIDES:
            for yy in range(y + 1, y + h, 3):
                t.hline(x, yy, w, TAN[1])
    fill_all(m, t, shell, lenses)
    fill_all(m, t, [b for b in bottles if b.startswith("bottle_t")], banded(P.CHERENKOV, [(6, WHITE_STRIPE)], 8, 3.5))
    fill_all(m, t, [b for b in bottles if b.startswith("bottle_d")], banded(STEEL, [(6, P.CHERENKOV[4])], 8, 3.5))
    m.paint_all(t, "pipe", shaded(P.COPPER))
    G.finish(m, t)


def ball_at(m: BlockModel, name: str, cx: float, cz: float, r: float, y0: float) -> list[str]:
    """As ball(), centred on (cx, cz)."""
    boxes = []
    rows = [(0.0, 0.25, 0.6), (0.25, 0.75, 0.92), (0.75, 1.25, 1.0), (1.25, 1.75, 0.92), (1.75, 2.0, 0.6)]
    for i, (a, b, k) in enumerate(rows):
        boxes += prism(m, f"{name}{i}", cx, cz, round(r * k, 2), round(r * k * 0.66, 2), y0 + a * r, y0 + b * r)
    return boxes


def twin_boosted_core() -> None:
    """The Tsar's primary: two boosted lens spheres side by side on one long cradle, a copper firing bus over both (so
    they go off together), a tritium bottle behind each, piped in."""
    m = model("twin_boosted_core")
    m.box("cradle", (1.6, 2, 6.2), (14.4, 3.4, 9.8))
    m.box("foot_w", (1.6, 1, 4.6), (3.4, 2, 11.4))
    m.box("foot_e", (12.6, 1, 4.6), (14.4, 2, 11.4))
    shells = ball_at(m, "lens_w", 4.85, 8, 3.1, 3.4) + ball_at(m, "lens_e", 11.15, 8, 3.1, 3.4)
    bottles = prism(m, "bottle_w", 5, 12.6, 1.1, 0.75, 3.4, 8.4) + prism(m, "bottle_e", 11, 12.6, 1.1, 0.75, 3.4, 8.4)
    m.box("bus", (4.4, 9.6, 7.5), (11.6, 10.4, 8.5))
    m.box("lead_w", (4.6, 8.8, 7.6), (5.4, 9.6, 8.4), faces=("north", "south", "east", "west"))
    m.box("lead_e", (10.6, 8.8, 7.6), (11.4, 9.6, 8.4), faces=("north", "south", "east", "west"))
    m.box("pipe", (4.6, 7.4, 12.0), (11.4, 8.0, 12.8))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    for n in ("cradle", "foot_w", "foot_e"):
        m.paint_all(t, n, shaded(GUN))
    TAN = fixed("lens_tan_t", "#3a2a14", "#5a4220", "#7a5c2e", "#9a7840", "#b89656", "#d0b274", "#e6d0a0")

    def lenses(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, TAN[{"up": 5, "down": 2, "north": 4, "west": 4}.get(d, 3)])
        for xx in range(x + 2, x + w, 3):
            t.vline(xx, y, h, TAN[1])
        if d in SIDES:
            for yy in range(y + 1, y + h, 3):
                t.hline(x, yy, w, TAN[1])
    fill_all(m, t, shells, lenses)
    fill_all(m, t, bottles, banded(P.CHERENKOV, [(6, WHITE_STRIPE)], 8.4, 3.4))
    for n in ("bus", "lead_w", "lead_e", "pipe"):
        m.paint_all(t, n, shaded(P.COPPER))
    # A yellow and black band along the cradle's front: it is the bigger bomb's primary.
    x, y, w, h = m.rect("cradle", "north")
    for i in range(w):
        t.px(x + i, y + h // 2, YELLOW[4] if (i // 2) % 2 == 0 else BLACK[2])
    G.finish(m, t)


def neutron_core() -> None:
    """A small pit in a cage of tungsten reflector bars, glowing cyan through the gaps, on a base plate."""
    m = model("neutron_core")
    m.box("plate", (4.5, 1, 4.5), (11.5, 2, 11.5))
    pit = ball(m, "pit", 2.2, 3.4)
    bars = []
    for i, (x, z) in enumerate(((5, 5), (10, 5), (5, 10), (10, 10))):
        m.box(f"bar{i}", (x, 2, z), (x + 1, 9, z + 1))
        bars.append(f"bar{i}")
    m.box("ring_lo", (4.6, 3, 4.6), (11.4, 3.8, 11.4), faces=("north", "south", "east", "west", "up", "down"))
    m.box("ring_hi", (4.6, 7.4, 4.6), (11.4, 8.2, 11.4), faces=("north", "south", "east", "west", "up", "down"))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    m.paint_all(t, "plate", shaded(GUN))
    fill_all(m, t, pit, shaded(P.CHERENKOV))
    fill_all(m, t, bars + ["ring_lo", "ring_hi"], shaded(P.TUNGSTEN))
    G.finish(m, t)


def antimatter_trap() -> None:
    """Antimatter in a Penning trap: the warhead's capsule in small, its round parts true 16-sided prisms of planks
    (warheads.section) painted with the material library; the coils and the port emit light."""
    import numpy as np
    import warheads as W
    from gb import machine_kit as K
    from gb import materials as M
    m = model("antimatter_trap")
    m.open_ok = set()
    groups: dict[str, list[str]] = {}
    lips: dict[str, tuple[bool, bool, bool]] = {}

    def sec(group, r, y0, y1, caps, top=True, bottom=False, foot=False):
        names = W.section(m, f"{group}{len(groups.get(group, []))}", r, y0, y1, W.sides_for(r), caps=caps)
        groups.setdefault(group, []).extend(names)
        for n in names:
            lips[n] = (top, bottom, foot)
    sec("base", 4.2, 0.0, 1.2, ("up", "down"), bottom=True)
    sec("body", 3.2, 1.2, 9.5, ("up",), top=False, foot=True)
    for y in (2.6, 6.2):
        sec("collar", 3.85, y, y + 0.5, ("up", "down"), bottom=True)
        sec("coil", 3.65, y + 0.5, y + 1.5, (), top=False)
        sec("collar", 3.85, y + 1.5, y + 2.0, ("up", "down"), bottom=True)
    sec("cap", 2.3, 9.5, 10.3, ("up",))
    sec("port", 1.2, 10.3, 10.8, ("up",))
    for n in groups["coil"] + groups["port"]:
        m.get(n).light_emission = 15
    while True:
        try:
            m.layout()
            break
        except ValueError:
            m.tex_w *= 2
            m.tex_h = m.tex_w
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=611, ao_reach=1.5, turned=True)
    for n, (top, bottom, foot) in lips.items():
        W.round_faces(k.S, [n], top, bottom, foot)
    lit = dict(mottle=0.04, grain=0.03, dither=0.4, edge_light=0.0, edge_dark=0.0, ao=0.0, grime=0.0, dust=0.0, facing=W.ROUND)
    k.paint(t, groups["base"], W.BASE)
    k.paint(t, groups["body"], W.paint(W.WHITE, 0.62, 0.08))
    k.paint(t, groups["collar"], W.metal(W.GUNMETAL, 0.34))
    k.paint(t, groups["coil"], M.Material(ramp=W.ramp9(W.VIOLET), tone=0.66, **lit))
    k.paint(t, groups["cap"], W.metal(W.GUNMETAL, 0.4))
    k.paint(t, groups["port"], M.Material(ramp=W.ramp9(W.VIOLET), tone=0.86, **lit))

    def windings(f, img):                                      # the coils' turns, as the warhead's
        if f.d in W.SIDES:
            col = np.floor((W.angle_of(f) + 360) % 360 / 360 * 24).astype(int)
            M.lighten(img, col % 2 == 0, 1.14, 6)
    k.overlay(t, groups["coil"], windings)
    k.overlay(t, groups["body"], lambda f, img: W.decal(f, img, ["YKY", "KYK", "YKY"], {"Y": W.YELLOW[4], "K": W.VIOLET[2]}, 5.6))
    G.finish(m, t)


def singularity_core() -> None:
    """A singularity seed in its containment: the warhead's gyroscope in small, a black sphere in bands from a true
    circle's profile with a lit violet slit above its waist, in two hoops of turned planks (warheads.hoop's way)."""
    import math
    import numpy as np
    import warheads as W
    from gb import machine_kit as K
    from gb import materials as M
    m = model("singularity_core")
    m.open_ok = set()
    groups: dict[str, list[str]] = {}
    lips: dict[str, tuple[bool, bool, bool]] = {}

    def add(group, names, top=True, bottom=False):
        groups.setdefault(group, []).extend(names)
        for n in names:
            lips[n] = (top, bottom, False)
    add("base", W.section(m, "base0_", 3.6, 0.0, 1.0, W.sides_for(3.6), caps=("up", "down"), stagger="full"), True, True)
    cy, R = 5.8, 3.2
    bands = [(2.8, 3.4), (3.4, 4.1), (4.1, 4.8), (4.8, 5.6), (5.6, 6.4), ("slit", 6.4, 7.0), (7.0, 7.6), (7.6, 8.3), (8.3, 8.8)]
    radii = [2.55 if b[0] == "slit" else round(math.sqrt(max(0.3, R * R - (cy - (b[0] + b[1]) / 2) ** 2)), 3) for b in bands]
    for i, band in enumerate(bands):
        if band[0] == "slit":
            add("slit", W.section(m, f"slit{i}_", radii[i], band[1], band[2], W.sides_for(radii[i]), caps=()), False)
            continue
        below = radii[i - 1] if i > 0 else 0.0
        above = radii[i + 1] if i + 1 < len(radii) else 0.0
        caps = tuple(c for c, on in (("up", radii[i] > above), ("down", radii[i] > below)) if on)
        stagger = "full" if len(caps) == 2 else "base" if caps == ("down",) and W.sides_for(radii[i]) == 16 else None
        add("body", W.section(m, f"band{i}_", radii[i], band[0], band[1], W.sides_for(radii[i]), caps=caps, stagger=stagger),
            "up" in caps, "down" in caps)
    for group, R2, axis in (("steel", 4.4, "x"), ("brass", 3.75, "y")):
        chord = 2 * R2 * math.tan(math.pi / 12) + 0.04
        names = []
        for i in range(12):
            h = 0.35 - (0.03 if i % 2 else 0.0)
            if axis == "y":
                frm, to = (8 + R2 - 0.25, cy - h, 8 - chord / 2), (8 + R2 + 0.25, cy + h, 8 + chord / 2)
            else:
                frm, to = (8 - h, cy + R2 - 0.25, 8 - chord / 2), (8 + h, cy + R2 + 0.25, 8 + chord / 2)
            m.box(f"{group}{i}", frm, to, rotation={"origin": (8, cy, 8), "axis": axis, "angle": 30.0 * i})
            names.append(f"{group}{i}")
        groups[group] = names
    for n in groups["slit"]:
        m.get(n).light_emission = 15
    while True:
        try:
            m.layout()
            break
        except ValueError:
            m.tex_w *= 2
            m.tex_h = m.tex_w
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=613, ao_reach=1.5, turned=True)
    for n, (top, bottom, foot) in lips.items():
        W.round_faces(k.S, [n], top, bottom, foot)
    lit = dict(mottle=0.04, grain=0.03, dither=0.4, edge_light=0.0, edge_dark=0.0, ao=0.0, grime=0.0, dust=0.0, facing=W.ROUND)
    k.paint(t, groups["base"], W.BASE)
    k.paint(t, groups["body"], M.Material(ramp=W.ramp9(W.VOID), tone=0.42, mottle=0.03, grain=0.02, dither=0.35, edge_light=0.08,
                                          edge_dark=0.05, ao=0.3, grime=0.0, dust=0.0, facing=W.ROUND))
    k.paint(t, groups["slit"], M.Material(ramp=W.ramp9(W.VIOLET), tone=0.72, **lit))
    k.paint(t, groups["steel"], W.metal(W.STEEL, 0.6))
    k.paint(t, groups["brass"], W.BRASS)

    def swirl(f, img):                                         # the core turning in the slit
        if f.d in W.SIDES:
            col = np.floor((W.angle_of(f) + 360) % 360 / 360 * 20).astype(int)
            M.darken(img, col % 3 == 0, 0.72)
    k.overlay(t, groups["slit"], swirl)
    G.finish(m, t)


def storm_core() -> None:
    """A Tesla storm generator (DESIGN.md §16.14): the storm warhead in small, a copper-wound drum between dark collars
    with four capacitor tubes glowing blue round it in a steel hoop, a neck up to a polished ring (the top load) and a
    copper electrode."""
    import math
    import numpy as np
    import warheads as W
    from gb import machine_kit as K
    from gb import materials as M
    m = model("storm_core")
    m.open_ok = set()
    groups: dict[str, list[str]] = {}
    lips: dict[str, tuple[bool, bool, bool]] = {}

    def add(group, names, top=True, bottom=False):
        groups.setdefault(group, []).extend(names)
        for n in names:
            lips[n] = (top, bottom, False)
    add("base", W.section(m, "base0_", 3.6, 0.0, 1.0, W.sides_for(3.6), caps=("up", "down"), stagger="full"), True, True)
    add("body", W.section(m, "body0_", 2.1, 1.0, 6.6, W.sides_for(2.1), caps=("up",), stagger="full"), True, False)
    add("coil", W.section(m, "coil0_", 2.45, 2.0, 5.6, W.sides_for(2.45), caps=()), False, False)
    for i, (y0, y1) in enumerate(((1.6, 2.0), (5.6, 6.0))):
        add("collar", W.section(m, f"collar{i}_", 2.6, y0, y1, W.sides_for(2.6), caps=("up", "down"), stagger="full"), True, True)
    for k, a in enumerate((45, 135, 225, 315)):
        cx, cz = 8 + 3.0 * math.sin(math.radians(a)), 8 + 3.0 * math.cos(math.radians(a))
        add("tube", W.section(m, f"tube{k}_", 0.45, 1.0, 6.5, 8, cx=cx, cz=cz, caps=("up",)), True, False)
    for group, R2, cy, h, w in (("hoop", 3.0, 6.8, 0.25, 0.25), ("torus", 1.7, 8.4, 0.5, 0.55)):
        chord = 2 * R2 * math.tan(math.pi / 12) + 0.04
        names = []
        for i in range(12):
            hh = h - (0.03 if i % 2 else 0.0)
            m.box(f"{group}{i}", (8 + R2 - w, cy - hh, 8 - chord / 2), (8 + R2 + w, cy + hh, 8 + chord / 2),
                  rotation={"origin": (8, cy, 8), "axis": "y", "angle": 30.0 * i})
            names.append(f"{group}{i}")
        groups[group] = names
    add("neck", W.section(m, "neck0_", 0.7, 6.6, 8.2, W.sides_for(0.7), caps=("up",)), True, False)
    add("electrode", W.section(m, "electrode0_", 0.4, 8.2, 9.6, W.sides_for(0.4), caps=("up",)), True, False)
    for n in groups["tube"]:
        m.get(n).light_emission = 15
    while True:
        try:
            m.layout()
            break
        except ValueError:
            m.tex_w *= 2
            m.tex_h = m.tex_w
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=617, ao_reach=1.5, turned=True)
    for n, (top, bottom, foot) in lips.items():
        W.round_faces(k.S, [n], top, bottom, foot)
    lit = dict(mottle=0.04, grain=0.03, dither=0.4, edge_light=0.0, edge_dark=0.0, ao=0.0, grime=0.0, dust=0.0, facing=W.ROUND)
    k.paint(t, groups["base"], W.BASE)
    k.paint(t, groups["body"], W.metal(W.GUNMETAL, 0.4))
    k.paint(t, groups["coil"], W.metal(P.COPPER, 0.6))
    k.paint(t, groups["collar"], W.metal(W.GUNMETAL, 0.32))
    k.paint(t, groups["tube"], M.Material(ramp=W.ramp9(P.TESLA), tone=0.72, **lit))
    k.paint(t, groups["hoop"], W.metal(W.STEEL, 0.55))
    k.paint(t, groups["torus"], W.metal(W.STEEL, 0.68))
    k.paint(t, groups["neck"], W.metal(W.GUNMETAL, 0.45))
    k.paint(t, groups["electrode"], W.metal(P.COPPER, 0.64))

    def windings(f, img):                                     # the coil's turns
        if f.d in W.SIDES:
            row = np.floor(f.Y * 2).astype(int)
            M.lighten(img, row % 2 == 0, 1.12, 6)
    k.overlay(t, groups["coil"], windings)
    G.finish(m, t)


def dirty_payload() -> None:
    """A rusty drum of nuclear waste with a trefoil band, an olive HE brick taped to its side."""
    m = model("dirty_payload")
    drum = prism(m, "drum", 8, 8, 3.4, 2.3, 1, 10)
    m.box("lid", (5.2, 10, 5.2), (10.8, 10.6, 10.8))
    m.box("brick", (11.4, 2, 6.5), (13, 7, 9.5), faces=("north", "south", "east", "up", "down"))
    m.box("tape", (11.2, 4, 6.3), (13.2, 5, 9.7), faces=("north", "south", "east", "up", "down"))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    RUST = fixed("waste_rust_c", "#2a1206", "#46200c", "#663014", "#86441c", "#a35a28", "#bd7638", "#d69a5c")
    fill_all(m, t, drum, banded(RUST, [(8, YELLOW.base), (7.4, BLACK.base), (3, YELLOW.base)], 10, 1))
    m.paint_all(t, "lid", shaded(RUST))
    m.paint_all(t, "brick", shaded(OLIVE))
    m.paint_all(t, "tape", shaded(YELLOW))
    G.finish(m, t)


DU = fixed("du_dark", "#16181a", "#202326", "#2a2e31", "#363b3f", "#454b50", "#585f65", "#70787e")
COBALT = fixed("cobalt_plate", "#0a1430", "#102050", "#183070", "#204496", "#2c5cbc", "#4a7ed8", "#7aa6ee")


def fusion_secondary() -> None:
    """The thermonuclear second stage: a uranium tamper capsule with a lithium-deuteride band, its spark plug's terminal."""
    m = model("fusion_secondary")
    body = prism(m, "tamper", 8, 8, 3.6, 2.4, 2, 12.5)
    caps = prism(m, "cap_lo", 8, 8, 3.0, 2.0, 1, 2) + prism(m, "cap_hi", 8, 8, 3.0, 2.0, 12.5, 13.5)
    m.box("plug", (7.3, 13.5, 7.3), (8.7, 15.5, 8.7))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    lid = P.WHITE_CERAMIC
    fill_all(m, t, body, banded(DU, [(8.4, lid[4]), (7.9, lid[5]), (7.4, lid[4]), (6.9, lid[3])], 12.5, 2))
    fill_all(m, t, caps, shaded(GUN))
    m.paint_all(t, "plug", shaded(P.COPPER))
    G.finish(m, t)


def cobalt_jacket() -> None:
    """A sleeve of riveted cobalt plates round an open middle: the side plates full length, the end plates between them."""
    m = model("cobalt_jacket")
    walls = {"wall_w": ((3.4, 2, 3.4), (4.6, 13, 12.6), ("north", "south", "east", "west", "up", "down")),
             "wall_e": ((11.4, 2, 3.4), (12.6, 13, 12.6), ("north", "south", "east", "west", "up", "down")),
             "wall_n": ((4.6, 2, 3.4), (11.4, 13, 4.6), ("north", "south", "up", "down")),
             "wall_s": ((4.6, 2, 11.4), (11.4, 13, 12.6), ("north", "south", "up", "down"))}
    for n, (a, b, faces) in walls.items():
        m.box(n, a, b, faces=faces)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)

    def plates(t: Tex, r, d):
        x, y, w, h = r
        shaded(COBALT)(t, r, d)
        if d in SIDES and h > 4:
            for row in (y + h // 3, y + 2 * h // 3):         # the seams between the plates
                t.hline(x, row, w, COBALT[1])
            for xx in range(x + 1, x + w - 1, 3):            # rivets along the seams
                t.px(xx, y + h // 3 - 1, COBALT[5])
                t.px(xx, y + 2 * h // 3 - 1, COBALT[5])
    for n in walls:
        m.paint_all(t, n, plates)
    G.finish(m, t)


def intercontinental_booster() -> None:
    """Three solid-fuel stages narrowing upward on four nozzles, in the intercontinental missile's olive livery."""
    m = model("intercontinental_booster")
    s1 = prism(m, "stage1", 8, 8, 3.4, 2.3, 2, 8.6)
    s2 = prism(m, "stage2", 8, 8, 2.9, 1.95, 8.6, 12.6)
    s3 = prism(m, "stage3", 8, 8, 2.4, 1.6, 12.6, 15.2)
    nozzles = []
    for i, (x, z) in enumerate(((6.1, 6.1), (8.7, 6.1), (6.1, 8.7), (8.7, 8.7))):
        nozzles.append(f"nozzle{i}")
        m.box(nozzles[-1], (x, 0.6, z), (x + 1.2, 2, z + 1.2), faces=("north", "south", "east", "west", "down"))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    fill_all(m, t, s1, banded(OLIVE, [(5.6, P.WHITE_CERAMIC[5]), (5.1, P.WHITE_CERAMIC[5])], 8.6, 2))
    fill_all(m, t, s2, banded(OLIVE, [(10.8, YELLOW[4])], 12.6, 8.6))
    fill_all(m, t, s3, banded(OLIVE, [(14.0, P.WHITE_CERAMIC[5])], 15.2, 12.6))
    fill_all(m, t, nozzles, shaded(GUN))
    G.finish(m, t)


def tertiary_stage() -> None:
    """The Tsar's third stage: two fusion secondaries in a lead radiation channel, their lithium deuteride showing
    through two slots, their spark plugs' terminals out of the cap."""
    m = model("tertiary_stage")
    base = prism(m, "base", 8, 8, 3.6, 2.4, 1, 2)
    case = prism(m, "case", 8, 8, 4.2, 2.8, 2, 13.4)
    cap = prism(m, "cap", 8, 8, 3.2, 2.1, 13.4, 14.4)
    for i, x in enumerate((6.0, 8.8)):
        m.box(f"plug{i}", (x, 14.4, 7.4), (x + 1.2, 16, 8.6))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    lid = P.WHITE_CERAMIC

    def lead_case(t: Tex, r, d):
        x, y, w, h = r
        tone = {"up": 5, "north": 4, "west": 4, "south": 3, "east": 3, "down": 2}[d]     # dull lead, a step lighter
        t.rect(x, y, w, h, P.LEAD[tone])
        if d in SIDES and h > 2:
            t.hline(x, y, w, P.LEAD[tone + 1])
            t.hline(x, y + h - 1, w, P.LEAD[tone - 1])
        if d not in SIDES or h < 8:
            return
        def row(by: float) -> int:
            return y + max(0, min(h - 1, int(round((13.4 - by) * h / (13.4 - 2) - 0.5))))
        for by in (9.6, 5.2):                              # the two slots: a dark frame round a pale lithium band
            p = row(by)
            t.hline(x, p - 1, w, P.LEAD[2])
            t.hline(x, p, w, lid[4])
            t.hline(x, p + 1, w, P.LEAD[2])
        for i in range(w):                                 # the hazard band under the cap
            t.px(x + i, row(12.4), YELLOW[4] if (i // 2) % 2 == 0 else BLACK[2])
        if d == "north" and w >= 4:
            t.text(x + (w - Tex.text_width("3")) // 2, row(7.9) - 2, "3", YELLOW[4])
    fill_all(m, t, case, lead_case)
    fill_all(m, t, base + cap, shaded(GUN))
    for i in range(2):
        m.paint_all(t, f"plug{i}", shaded(P.COPPER))
    G.finish(m, t)


def canister_dispenser(name: str, can, cap, strap, lamp=None) -> None:
    m = model(name)
    m.box("plate", (3.5, 2, 3.5), (12.5, 3, 12.5))
    cans = []
    caps = []
    for i, (x, z) in enumerate(((6, 6), (10, 6), (6, 10), (10, 10))):
        cans += prism(m, f"can{i}", x, z, 1.5, 0.9, 3, 9)
        m.box(f"cap{i}", (x - 0.9, 9, z - 0.9), (x + 0.9, 9.8, z + 0.9))
        caps.append(f"cap{i}")
    m.box("strap", (4.3, 6, 4.3), (11.7, 6.8, 11.7), faces=SIDES)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    m.paint_all(t, "plate", shaded(STEEL))
    fill_all(m, t, cans, shaded(can))

    def cap_paint(t, r, d):
        shaded(cap)(t, r, d)
        if lamp and d == "up":
            t.px(r[0] + r[2] // 2, r[1] + r[3] // 2, lamp)
    fill_all(m, t, caps, cap_paint)
    m.paint_all(t, "strap", shaded(strap))
    G.finish(m, t)


def toxic_waste_tank() -> None:
    m = model("toxic_waste_tank")
    body = prism(m, "body", 8, 8, 3.5, 2.5, 2, 10)
    m.box("spout", (9.5, 10, 7.25), (11, 11.2, 8.75))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)

    def drum_paint(t, r, d):
        banded(WASTE, [(6, P.TOXIN[5]), (6.6, P.TOXIN[4])], 10, 2)(t, r, d)
        x, y, w, h = r
        if d in SIDES:
            t.stripes(x, y, w, 1, YELLOW.base, BLACK.base, width=1)       # hazard rim
        elif d == "up":
            t.outline(x, y, w, h, WASTE[1])
    fill_all(m, t, body, drum_paint)
    m.paint_all(t, "spout", shaded(STEEL))
    G.finish(m, t)


def chemical_agent_canister() -> None:
    m = model("chemical_agent_canister")
    body = prism(m, "body", 8, 8, 2.5, 1.5, 2, 10)
    shoulder = prism(m, "shoulder", 8, 8, 1.5, 0.8, 10, 11)
    m.box("valve", (7.4, 11, 7.4), (8.6, 12.4, 8.6))
    m.box("knob", (6.6, 12.4, 7.6), (9.4, 12.9, 8.4))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    fill_all(m, t, body, banded(P.WHITE_CERAMIC, [(5, P.INDICATOR_GREEN[3]), (7, P.INDICATOR_GREEN[3])], 10, 2))
    fill_all(m, t, shoulder, shaded(P.WHITE_CERAMIC))
    fill_all(m, t, ["valve", "knob"], shaded(BRASS))
    G.finish(m, t)


# --- casings ------------------------------------------------------------------------------------------------------------------
def casing(name: str, bands: int, r: float, w: float, top: float, fin: float) -> None:
    m = model(name)
    body = prism(m, "shell", 8, 8, r, w, 3, top)
    m.box("ring", (8 - r - 0.2, 2, 8 - w - 0.2), (8 + r + 0.2, 3, 8 + w + 0.2))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)

    def shell(stencil: bool):
        def paint(t, r_, d):
            x, y, wd, h = r_
            if d == "up":
                t.rect(x, y, wd, h, SHELL.deep)                   # open at the top: dark inside
                t.outline(x, y, wd, h, SHELL.light)
                return
            shaded(SHELL)(t, r_, d)
            if d in SIDES:
                t.hline(x, y + h - 2, wd, SHELL.shadow)           # weld seam above the tail
                for k in range(bands):                            # class bands: one Light, two Medium, three Heavy
                    t.hline(x, y + 1 + 2 * k, wd, YELLOW.base)
        return paint
    fill_all(m, t, body, shell(True))
    m.paint_all(t, "ring", shaded(STEEL))                     # the mounting ring that seats it on the missile
    G.finish(m, t)


def strategic_casing() -> None:
    """The Strategic Warhead Casing (Phase 8, class XL): a broad lead-lined shell, dark grey, a yellow-and-black hazard
    band round its shoulder and a radiation tag on its front, on the mounting ring that seats it on a Strategic missile."""
    m = model("strategic_warhead_casing")
    body = prism(m, "shell", 8, 8, 5, 3.3, 3, 14)
    m.box("ring", (2.8, 2, 4.5), (13.2, 3, 11.5))
    m.box("tag", (7, 6, 13), (9, 8.4, 13.4), faces=("south", "east", "west", "up", "down"))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    LEAD = P.LEAD

    def shell(t, r_, d):
        x, y, wd, h = r_
        if d == "up":
            t.rect(x, y, wd, h, LEAD.deep)                         # open at the top: lead-lined inside
            t.outline(x, y, wd, h, LEAD.light)
            return
        shaded(LEAD)(t, r_, d)
        if d in SIDES:
            t.hline(x, y + h - 2, wd, LEAD.shadow)                 # weld seam above the tail
            for xx in range(x, x + wd):                            # the hazard band round the shoulder
                t.px(xx, y + 1, YELLOW.base if (xx // 2) % 2 == 0 else BLACK.base)
                t.px(xx, y + 2, YELLOW.base if (xx // 2) % 2 == 0 else BLACK.base)
    fill_all(m, t, body, shell)
    m.paint_all(t, "ring", shaded(STEEL))
    m.paint_all(t, "tag", lambda t, r, d: (t.rect(*r, YELLOW.base), t.px(r[0] + r[2] // 2, r[1] + r[3] // 2, BLACK.base)))
    G.finish(m, t)


# --- fuzes --------------------------------------------------------------------------------------------------------------------
def fuze_base(m: BlockModel, body_top: float, r: float = 2.2, w: float = 1.3) -> tuple[list[str], list[str]]:
    thread = prism(m, "thread", 8, 8, r + 0.2, w + 0.1, 1, 2)
    body = prism(m, "body", 8, 8, r, w, 2, body_top)
    return thread, body


def threads(t: Tex, r, d):
    shaded(BRASS)(t, r, d)
    if d in SIDES:
        for xx in range(r[0], r[0] + r[2], 2):
            t.px(xx, r[1], BRASS.shadow)


def impact_fuze() -> None:
    m = model("impact_fuze")
    thread, body = fuze_base(m, 5)
    nose = prism(m, "nose", 8, 8, 1.4, 0.8, 5, 7)
    m.box("plunger", (7.5, 7, 7.5), (8.5, 8, 8.5))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    fill_all(m, t, thread, threads)
    fill_all(m, t, body + nose, shaded(STEEL))
    m.paint_all(t, "plunger", shaded(P.WARNING_RED))
    G.finish(m, t)


def barometric_fuze() -> None:
    m = model("barometric_fuze")
    thread, body = fuze_base(m, 6.5, 2.4, 1.4)
    collar = prism(m, "collar", 8, 8, 2.7, 1.6, 4.2, 5)
    m.box("cap", (7, 6.5, 7), (9, 7.3, 9))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    fill_all(m, t, thread, threads)

    def ported(t, r, d):
        shaded(GUN)(t, r, d)
        x, y, w, h = r
        if d in SIDES and w >= 3:
            t.px(x + w // 2, y + h - 3, BLACK.base)              # a vent port on each face
            t.px(x + w // 2, y + 1, BLACK.base)
    fill_all(m, t, body, ported)
    fill_all(m, t, collar, shaded(BRASS))
    m.paint_all(t, "cap", shaded(STEEL))
    G.finish(m, t)


CORES = ["he_charge", "heavy_he_charge", "thermobaric_charge", "cluster_dispenser", "penetrator_charge", "incendiary_charge",
         "massive_ordnance_charge", "gas_dispenser", "smoke_dispenser", "cryo_dispenser", "emp_dispenser", "toxic_waste_tank",
         "chemical_agent_canister", "tactical_fission_core", "fission_core", "boosted_fission_core", "neutron_core", "dirty_payload",
         "antimatter_trap", "singularity_core", "storm_core"]
CASINGS = ["light_warhead_casing", "medium_warhead_casing", "heavy_warhead_casing", "strategic_warhead_casing"]
FUZES = ["impact_fuze", "barometric_fuze"]
STRATEGIC = ["fusion_secondary", "cobalt_jacket", "intercontinental_booster", "tertiary_stage", "twin_boosted_core"]


def main() -> None:
    he_charge()
    heavy_he_charge()
    thermobaric_charge()
    cluster_dispenser()
    penetrator_charge()
    incendiary_charge()
    massive_ordnance_charge()
    canister_dispenser("gas_dispenser", G.KHAKI, YELLOW, YELLOW)
    canister_dispenser("smoke_dispenser", G.SMOKE_GREY, G.CAP_GREEN, P.WHITE_CERAMIC)
    canister_dispenser("cryo_dispenser", G.CRYO_CAN, P.WHITE_CERAMIC, P.WHITE_CERAMIC)
    canister_dispenser("emp_dispenser", G.EMP_CAN, G.EMP_CAN, P.COPPER, lamp=(90, 200, 255, 255))
    toxic_waste_tank()
    chemical_agent_canister()
    tactical_fission_core()
    fission_core()
    boosted_fission_core()
    neutron_core()
    dirty_payload()
    antimatter_trap()
    singularity_core()
    storm_core()
    casing("light_warhead_casing", 1, 3, 2, 10, 4)
    casing("medium_warhead_casing", 2, 4, 2.5, 12, 5)
    casing("heavy_warhead_casing", 3, 4.5, 3, 13.5, 6.2)
    strategic_casing()
    impact_fuze()
    barometric_fuze()
    fusion_secondary()
    cobalt_jacket()
    intercontinental_booster()
    tertiary_stage()
    twin_boosted_core()
    G.item_defs(CORES + CASINGS + FUZES + STRATEGIC)
    print("components written")


if __name__ == "__main__":
    main()
