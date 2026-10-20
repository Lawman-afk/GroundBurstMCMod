"""Thrown explosives (DESIGN.md §14, Phase 6 slice 6a): 3D item models (in hand, in the GUI and tumbling in flight).

Units: 16 = one block. Each grenade is about 9 units tall and drawn bigger than life, like vanilla's items. Round
bodies are "plus" prisms: a full-width core box and two side boxes against it (never overlapping, so no coplanar faces
fight), which reads as a rounded can at this size. Faces are painted one model pixel per texel.

- Dynamite: three red paper sticks taped together with black tape, a short fuse out of the top.
- Frag Grenade: an olive pineapple body (raised segments, grooves), a yellow band, a steel fuze head, the spoon down
  its side and a brass pin ring.
- Sticky Grenade: a dark gunmetal can on a glossy black tar-coated base, a grey cap with a red button.
- Incendiary Grenade: a red can with a white flame stencil on a white band, steel fuze head, spoon and pin.
- Smoke Grenade: a grey can with a green cap and vent holes on top, a white band, spoon and pin.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz import paths  # noqa: E402
from gz.model import BlockModel, write_json  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402

OLIVE = P.OLIVE
STEEL = P.STEEL
BRASS = P.BRASS
RUBBER = P.RUBBER
YELLOW = P.HAZARD_YELLOW
PAPER_RED = fixed("dynamite_red", "#3a0a08", "#6a1410", "#9a2018", "#c22e22", "#d84a36", "#e8705a", "#f6a490")
TAN = fixed("dynamite_end", "#3a2c1c", "#5e4a30", "#86704c", "#a88e64", "#c4aa80", "#dcc6a0", "#f0e2c4")
GUNMETAL = P.GUNMETAL
TAR = fixed("tar", "#050505", "#0b0b0c", "#121214", "#1a1a1d", "#25252a", "#3a3a42", "#6a6a78")
CAN_RED = P.WARNING_RED
SMOKE_GREY = fixed("smoke_can", "#1e2224", "#30363a", "#454c50", "#5c6468", "#757d82", "#939a9e", "#b8bec2")
CAP_GREEN = fixed("smoke_cap", "#0c2410", "#14381a", "#1e5426", "#2a7034", "#3a8c46", "#5aa862", "#8acc8e")
WHITE = P.WHITE_CERAMIC


def prism(m: BlockModel, name: str, cx: float, cz: float, r: float, w: float, y0: float, y1: float) -> list[str]:
    """A rounded column: a core box (full width in x) and two side boxes (full width in z) against it."""
    m.box(f"{name}_core", (cx - r, y0, cz - w), (cx + r, y1, cz + w))
    m.box(f"{name}_n", (cx - w, y0, cz - r), (cx + w, y1, cz - w), faces=("north", "east", "west", "up", "down"))
    m.box(f"{name}_s", (cx - w, y0, cz + w), (cx + w, y1, cz + r), faces=("south", "east", "west", "up", "down"))
    return [f"{name}_core", f"{name}_n", f"{name}_s"]


def spoon_and_pin(m: BlockModel, top: float, side: float) -> None:
    """The spoon (lever) down the +x side from the fuze head, and the pin ring on the -x side."""
    m.box("spoon", (side, top - 5.5, 7.5), (side + 0.6, top + 0.6, 8.5), faces=("north", "south", "east", "up", "down"))
    m.box("spoon_arm", (9, top, 7.5), (side, top + 0.6, 8.5), faces=("north", "south", "up", "down"))
    m.box("pin", (5.6, top - 0.2, 7.9), (7, top + 0.1, 8.1), faces=("north", "south", "up", "down", "west"))
    m.box("ring", (4.0, top - 0.9, 7.9), (5.6, top + 0.7, 8.1))


def fill_all(m: BlockModel, t: Tex, boxes: list[str], fn) -> None:
    for b in boxes:
        m.paint_all(t, b, fn)


def finish(m: BlockModel, t: Tex) -> None:
    """Display transforms: fitted in the GUI; held and thrown at sizes chosen for a hand-sized can."""
    m.fit_display(gui_rotation=(25, 45, 0), gui_margin=0.5)
    lo, hi = m.bounds()
    c = [(lo[i] + hi[i]) / 2 for i in range(3)]

    def ctx(scale: float, base: tuple[float, float, float], rotation=(0, 0, 0)) -> dict:
        return {"rotation": list(rotation), "translation": [round(base[i] - (c[i] - 8) * scale, 3) for i in range(3)],
                "scale": [scale] * 3}

    m.display["ground"] = ctx(0.55, (0, 0, 0))
    m.display["fixed"] = ctx(0.9, (0, 0, 0))
    m.display["thirdperson_righthand"] = ctx(0.55, (0, 2.5, 1.5), (0, 90, 0))
    m.display["thirdperson_lefthand"] = ctx(0.55, (0, 2.5, 1.5), (0, 90, 0))
    m.display["firstperson_righthand"] = ctx(0.4, (1.5, 3.5, 0), (0, -25, 0))
    m.display["firstperson_lefthand"] = ctx(0.4, (1.5, 3.5, 0), (0, 155, 0))
    m.display["head"] = ctx(0.8, (0, 6, 0))
    m.save(t)


# --- painting helpers -----------------------------------------------------------------------------------------------------
def shaded(ramp, top_light: bool = True):
    """Plain shading by face direction: lit from the top left."""
    def fn(t: Tex, r, d):
        x, y, w, h = r
        tone = {"up": ramp.light, "north": ramp.base, "west": ramp.base, "south": ramp.shadow, "east": ramp.shadow,
                "down": ramp.deep}[d]
        t.rect(x, y, w, h, tone)
        if d in ("north", "south", "east", "west") and h > 2 and top_light:
            t.hline(x, y, w, ramp.light if d in ("north", "west") else ramp.base)
            t.hline(x, y + h - 1, w, ramp.deep)
    return fn


def steel_parts(m: BlockModel, t: Tex) -> None:
    fill_all(m, t, ["spoon", "spoon_arm", "pin"], shaded(STEEL))
    def ring(t: Tex, r, d):
        x, y, w, h = r
        if d in ("north", "south"):
            t.outline(x, y, w, h, BRASS.base if d == "north" else BRASS.shadow)   # a hollow ring: the middle stays clear
            t.px(x, y, BRASS.highlight)
        else:
            t.rect(x, y, w, h, BRASS.shadow)
    m.paint_all(t, "ring", ring)


# --- the grenades ---------------------------------------------------------------------------------------------------------
def frag() -> None:
    m = BlockModel("frag_grenade", tex_size=(64, 32), kind="item", render_type="minecraft:cutout")
    body = prism(m, "body", 8, 8, 3, 2, 2, 9)
    m.box("shoulder", (6.5, 9, 6.5), (9.5, 10, 9.5))
    m.box("fuze", (7, 10, 7), (9, 11.5, 9))
    spoon_and_pin(m, 11.0, 11)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)

    def pineapple(t: Tex, r, d):
        x, y, w, h = r
        if d in ("up", "down"):
            t.rect(x, y, w, h, OLIVE.shadow if d == "down" else OLIVE.base)
            return
        lit = d in ("north", "west")
        for i in range(w):
            for j in range(h):
                groove = (j % 2 == 1) or (i % 2 == 1)    # a grid of raised segments, one pixel each
                if groove:
                    c = OLIVE.shadow if lit else OLIVE.deep
                else:
                    c = OLIVE.highlight if lit and (i + j) % 4 == 0 else OLIVE.light if lit else OLIVE.base
                t.px(x + i, y + j, c)
        t.hline(x, y, w, YELLOW.base)                      # the yellow band just under the shoulder
    fill_all(m, t, body, pineapple)
    m.paint_all(t, "shoulder", shaded(OLIVE))
    m.paint_all(t, "fuze", shaded(STEEL))
    steel_parts(m, t)
    finish(m, t)


def dynamite() -> None:
    m = BlockModel("dynamite", tex_size=(32, 32), kind="item")
    sticks = [("stick_a", 5.5, 6), ("stick_b", 8.5, 6), ("stick_c", 7, 8.5)]
    for name, x, z in sticks:
        m.box(name, (x, 2, z), (x + 2, 10, z + 2))
    tapes = []
    for name, x, z in sticks:                          # tape round each stick (so the gaps between them stay open)
        for k, (y0, y1) in enumerate(((3, 4), (7.5, 8.5))):
            tapes.append(f"{name}_tape{k}")
            m.box(tapes[-1], (x - 0.2, y0, z - 0.2), (x + 2.2, y1, z + 2.2))
    m.box("fuse", (7.8, 10, 9.3), (8.2, 12, 9.7))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)

    def stick(t: Tex, r, d):
        x, y, w, h = r
        if d in ("up", "down"):
            t.rect(x, y, w, h, TAN.base)                   # the paper end, crimped
            t.px(x, y, TAN.highlight)
            return
        t.rect(x, y, w, h, PAPER_RED.base if d in ("north", "west") else PAPER_RED.shadow)
        t.vline(x, y, h, PAPER_RED.light if d in ("north", "west") else PAPER_RED.base)
        t.hline(x, y, w, TAN.shadow)
        t.hline(x, y + h - 1, w, TAN.shadow)
    for name, _, _ in sticks:
        m.paint_all(t, name, stick)
    fill_all(m, t, tapes, shaded(TAR))
    m.paint_all(t, "fuse", shaded(WHITE))
    finish(m, t)


def sticky() -> None:
    m = BlockModel("sticky_grenade", tex_size=(32, 32), kind="item")
    can = prism(m, "can", 8, 8, 2.5, 1.5, 4, 9)
    base = prism(m, "tar", 8, 8, 3, 2, 2, 4)
    m.box("cap", (7, 9, 7), (9, 10.2, 9))
    m.box("button", (7.5, 10.2, 7.5), (8.5, 10.7, 8.5))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    def metal(t: Tex, r, d):
        shaded(STEEL)(t, r, d)
        x, y, w, h = r
        if d not in ("up", "down"):
            t.hline(x, y + 1, w, YELLOW.base)             # a thin yellow warning stripe round the top
    fill_all(m, t, can, metal)

    def tar(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, TAR.base)
        if d in ("north", "west", "up"):
            t.px(x + min(1, w - 1), y, TAR.shine)          # a glossy glint
        if h >= 2 and d not in ("up", "down"):
            t.px(x + w // 2, y, TAR.light)                 # a drip creeping up the can
    fill_all(m, t, base, tar)
    m.paint_all(t, "cap", shaded(STEEL))
    m.paint_all(t, "button", shaded(P.INDICATOR_RED))
    finish(m, t)


def can_grenade(name: str, ramp, band, decal: str | None, cap, vents: bool) -> None:
    m = BlockModel(name, tex_size=(32, 32), kind="item", render_type="minecraft:cutout")
    can = prism(m, "can", 8, 8, 2.5, 1.5, 2, 9.5)
    m.box("cap", (6.5, 9.5, 6.5), (9.5, 10.3, 9.5))
    spoon_and_pin(m, 10.3, 10.5)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)

    def body(t: Tex, r, d):
        x, y, w, h = r
        shaded(ramp)(t, r, d)
        if d in ("north", "south", "east", "west"):
            t.hline(x, y + 1, w, band)                     # a thin stencil stripe round the top
            if decal and d in ("north", "south") and w >= 5:
                t.decal(decal, x + (w - 5) // 2, y + 2, band)   # the stencil itself, white on the can
    fill_all(m, t, can, body)

    def cap_paint(t: Tex, r, d):
        shaded(cap)(t, r, d)
        if vents and d == "up":
            x, y, w, h = r
            t.px(x + w // 2, y + h // 2, cap.outline)     # the vent the smoke pours out of
    m.paint_all(t, "cap", cap_paint)
    steel_parts(m, t)
    finish(m, t)


def item_defs(names: list[str]) -> None:
    for n in names:
        write_json(paths.ASSETS / "items" / f"{n}.json", {"model": {"type": "minecraft:model", "model": f"groundzero:item/{n}"}})


def main() -> None:
    frag()
    dynamite()
    sticky()
    can_grenade("incendiary_grenade", CAN_RED, WHITE.light, "flame5x7", STEEL, vents=False)
    can_grenade("smoke_grenade", SMOKE_GREY, WHITE.light, None, CAP_GREEN, vents=True)
    item_defs(["frag_grenade", "dynamite", "sticky_grenade", "incendiary_grenade", "smoke_grenade"])
    print("grenades written")


if __name__ == "__main__":
    main()
