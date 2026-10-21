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
Casings: steel shells open at the top on a mounting ring, bigger by class, with yellow class bands (one Light, two Medium, three Heavy).
Fuzes: a steel nose fuze each, told apart by the head: Impact (a red plunger), Timed (a clock dial), Proximity (a dark
sensor dome with a green lamp), Remote (a whip antenna), Barometric (vent ports and a brass pressure ring).
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gz import palette as P  # noqa: E402
from gz.model import BlockModel  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402
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


def timed_fuze() -> None:
    m = model("timed_fuze")
    thread, body = fuze_base(m, 6, 2.5, 1.5)
    m.box("ring", (5.4, 4.5, 5.4), (10.6, 5.2, 10.6), faces=SIDES)
    m.box("dial", (5.5, 6, 5.5), (10.5, 6.4, 10.5))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    fill_all(m, t, thread, threads)

    fill_all(m, t, body, shaded(STEEL))

    def dial(t, r, d):
        x, y, w, h = r
        if d == "up":
            t.rect(x, y, w, h, P.WHITE_CERAMIC[6])                # the dial face
            cx, cy = x + w // 2, y + h // 2
            t.vline(cx, y + 1, cy - y, BLACK.base)                # the hand, from the centre to twelve
            t.hline(cx, cy, 2, BLACK.base)                        # and to three
            t.px(cx, y, P.WARNING_RED.base)                       # the zero mark
        else:
            t.rect(x, y, w, h, STEEL.shadow)
    m.paint_all(t, "dial", dial)

    def knurl(t, r, d):
        t.rect(*r, BLACK[4])
        for xx in range(r[0], r[0] + r[2], 2):
            t.px(xx, r[1], BLACK[6])
    m.paint_all(t, "ring", knurl)
    G.finish(m, t)


def proximity_fuze() -> None:
    m = model("proximity_fuze")
    thread, body = fuze_base(m, 6)
    dome = prism(m, "dome", 8, 8, 1.6, 1.0, 6, 7.5)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    fill_all(m, t, thread, threads)
    fill_all(m, t, body, banded(OLIVE, [(5, YELLOW.base)], 6, 2))

    def dome_paint(t, r, d):
        shaded(GLASS)(t, r, d)
        if d == "up":
            t.px(r[0] + r[2] // 2, r[1] + r[3] // 2, P.INDICATOR_GREEN[4])
        elif d in ("north", "west"):
            t.px(r[0], r[1], GLASS.highlight)
    fill_all(m, t, dome, dome_paint)
    G.finish(m, t)


def remote_fuze() -> None:
    m = model("remote_fuze")
    thread, body = fuze_base(m, 6)
    m.box("antenna", (7.75, 6, 7.75), (8.25, 12, 8.25), faces=("north", "south", "east", "west", "up"))
    m.box("tip", (7.55, 12, 7.55), (8.45, 12.8, 8.45))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    fill_all(m, t, thread, threads)
    fill_all(m, t, body, banded(STEEL, [(4.5, YELLOW.base), (5, YELLOW.base)], 6, 2))
    m.paint_all(t, "antenna", shaded(BLACK))
    m.paint_all(t, "tip", shaded(P.WARNING_RED))
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
         "chemical_agent_canister"]
CASINGS = ["light_warhead_casing", "medium_warhead_casing", "heavy_warhead_casing"]
FUZES = ["impact_fuze", "timed_fuze", "proximity_fuze", "remote_fuze", "barometric_fuze"]


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
    casing("light_warhead_casing", 1, 3, 2, 10, 4)
    casing("medium_warhead_casing", 2, 4, 2.5, 12, 5)
    casing("heavy_warhead_casing", 3, 4.5, 3, 13.5, 6.2)
    impact_fuze()
    timed_fuze()
    proximity_fuze()
    remote_fuze()
    barometric_fuze()
    G.item_defs(CORES + CASINGS + FUZES)
    print("components written")


if __name__ == "__main__":
    main()
