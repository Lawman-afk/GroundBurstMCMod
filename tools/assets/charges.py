"""Placed charges (DESIGN.md §14, Phase 6 slice 6b): block models lying on the floor (the blockstate turns them to the
face they are stuck to), each with an armed twin whose red lamp blinks (a two-frame animated texture, the lamp lit
regardless of the light), and the Remote Detonator sprite.

- C4 Charge: one to four stacked off-white putty blocks in olive film, two bands of black tape round the stack, a
  grey detonator with red and black leads and its lamp.
- Breaching Charge: a dark rubber-backed plate with an orange det-cord frame round its edge, a lead to a detonator
  in the middle.
- Thermite Charge: a terracotta pot with a white band, a grey igniter cap, a silver magnesium-ribbon fuse; lit, it
  glows orange-hot through cracks.
- Timed Bomb: a steel box with a dark display on the front (the time is drawn by ChargeRenderer), three sticks of
  dynamite strapped on top, four coloured wires along the front edge, its lamp beside the display.
- Mines (slice 6c) are modelled sunk into the ground (elements below y 0): in the world only the top shows, while the
  item, and the bouncing mine's popped-up canister, show all of it.
  - Landmine: an olive disc with a steel pressure plate in the middle and a lamp on its rim.
  - Bouncing Mine: an olive canister with a yellow band, a gunmetal cap and three trigger prongs above the ground.
  - Claymore: a thin, curved olive box on two pairs of scissor legs, a sight on top, the red laser lens in the middle
    of its face over a yellow chevron; the lens is its lamp.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gz import palette as P  # noqa: E402
from gz import paths  # noqa: E402
from gz.model import BlockModel, write_json  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402
import grenades as G  # noqa: E402
import resources as R  # noqa: E402

PUTTY = fixed("c4_putty", "#5a5444", "#7e7862", "#a49e86", "#c6c0a6", "#dcd6be", "#ece8d4", "#faf8ec")
FILM = P.OLIVE
TAPE = G.TAR
LEAD_RED = P.WARNING_RED
STEEL = P.STEEL
GUNMETAL = P.GUNMETAL
CORD = fixed("det_cord", "#4a1e04", "#7a3408", "#aa4c0e", "#d46a16", "#ec8a2a", "#f6aa58", "#fcd4a0")
RUBBER = P.RUBBER
CLAY = fixed("thermite_pot", "#3a1a0e", "#5e2c16", "#84401e", "#a8582a", "#c4703a", "#d89058", "#ecbc90")
HOT = fixed("thermite_hot", "#5a1404", "#8a2808", "#c24a0c", "#ec7818", "#f8a030", "#fcc864", "#fff0c0")
LCD = fixed("lcd", "#040a06", "#08140c", "#0c1c12", "#10261a", "#163222", "#1e402c", "#2a5038")
WIRE_COLOURS = [P.WARNING_RED, fixed("wire_blue", "#0c1a3a", "#142a5e", "#1e3e86", "#2a56ae", "#3a6ad8", "#6a92ea", "#a8c0f6"),
                P.HAZARD_YELLOW, fixed("wire_green", "#0c2a10", "#14401a", "#1e5a26", "#2a7834", "#3aaa4a", "#6ac876", "#a8e8b0")]

LAMP_ON = (255, 70, 50, 255)
LAMP_OFF = (90, 16, 12, 255)
FACINGS = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180}, "east": {"x": 90, "y": 90},
           "west": {"x": 90, "y": 270}}
HORIZONTAL = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {}, "down": {}}

shaded = G.shaded
fill_all = G.fill_all


def lamp(m: BlockModel, frm, to, faces=("north", "south", "east", "west", "up")) -> None:
    """The armed lamp: lit regardless of the light around it."""
    m.box("lamp", frm, to, faces=faces, light=15)


def save(m: BlockModel, paint, armed_too: bool = True) -> list[str]:
    """Saves the model safe (lamp dark) and, if asked, an armed twin whose lamp blinks. Returns the model names."""
    names = [m.name]
    t = Tex(m.tex_w, m.tex_h)
    paint(m, t, LAMP_OFF)
    m.save(t)
    if armed_too:
        safe_name = m.name
        m.name = safe_name + "_armed"
        on, off = Tex(m.tex_w, m.tex_h), Tex(m.tex_w, m.tex_h)
        paint(m, on, LAMP_ON)
        paint(m, off, LAMP_OFF)
        m.save(on)
        m.bleed(off)
        strip = Tex(m.tex_w, m.tex_h * 2)
        strip.blit(on, 0, 0, blend=False)
        strip.blit(off, 0, m.tex_h, blend=False)
        tp = strip.save(m.texture_path())
        tp.with_name(tp.name + ".mcmeta").write_text(json.dumps({"animation": {"frametime": 10, "width": m.tex_w, "height": m.tex_h}}, indent=2) + "\n", encoding="utf-8")
        names.append(m.name)
        m.name = safe_name
    return names


def paint_lamp(m: BlockModel, t: Tex, colour) -> None:
    def fn(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, colour)
    m.paint_all(t, "lamp", fn)


# --- C4 --------------------------------------------------------------------------------------------------------------------
def c4(n: int) -> list[str]:
    m = BlockModel(f"c4_{n}", tex_size=(64, 64), kind="block")
    top = 2.5 * n
    for i in range(n):
        faces = tuple(d for d in ("north", "south", "east", "west", "up", "down")
                      if not (d == "up" and i < n - 1) and not (d == "down" and i > 0))
        m.box(f"brick{i}", (3, 2.5 * i, 5), (13, 2.5 * (i + 1), 11), faces=faces)
    for k, x in enumerate((4.5, 10.5)):
        m.box(f"tape{k}", (x, 0, 4.8), (x + 1, top + 0.2, 11.2), faces=("north", "south", "east", "west", "up"))
    m.box("detonator", (6, top, 6.5), (8.6, top + 1.2, 9.5), faces=("north", "south", "east", "west", "up"))
    m.box("lead_red", (8.6, top, 7.0), (10.3, top + 0.35, 7.4), faces=("north", "south", "east", "up"))
    m.box("lead_black", (8.6, top, 8.6), (10.3, top + 0.35, 9.0), faces=("north", "south", "east", "up"))
    lamp(m, (6.8, top + 1.2, 7.5), (7.8, top + 1.6, 8.5))
    m.layout()
    bricks = [f"brick{i}" for i in range(n)]

    def paint(m: BlockModel, t: Tex, lamp_colour) -> None:
        def brick(t: Tex, r, d):
            x, y, w, h = r
            if d in ("up", "down"):
                t.rect(x, y, w, h, FILM.base if d == "up" else FILM.deep)
                t.outline(x, y, w, h, FILM.shadow)
                return
            shaded(FILM)(t, r, d)
            if h >= 2:
                t.hline(x, y + h - 1, w, PUTTY.base)              # the putty showing where the film is folded
        fill_all(m, t, bricks, brick)
        fill_all(m, t, ["tape0", "tape1"], shaded(TAPE))
        m.paint_all(t, "detonator", shaded(GUNMETAL))
        m.paint_all(t, "lead_red", shaded(LEAD_RED))
        m.paint_all(t, "lead_black", shaded(RUBBER))
        paint_lamp(m, t, lamp_colour)
    m.fit_display(gui_rotation=(30, 225, 0), gui_margin=1.5)
    return save(m, paint)


# --- breaching charge ------------------------------------------------------------------------------------------------------
def breaching() -> list[str]:
    m = BlockModel("breaching_charge", tex_size=(64, 64), kind="block")
    m.box("plate", (1, 0, 1), (15, 0.75, 15))
    m.box("cord_n", (2, 0.75, 2), (14, 1.5, 3), faces=("north", "south", "east", "west", "up"))
    m.box("cord_s", (2, 0.75, 13), (14, 1.5, 14), faces=("north", "south", "east", "west", "up"))
    m.box("cord_w", (2, 0.75, 3), (3, 1.5, 13), faces=("east", "west", "up"))
    m.box("cord_e", (13, 0.75, 3), (14, 1.5, 13), faces=("east", "west", "up"))
    m.box("lead", (7.7, 0.75, 3), (8.3, 1.2, 6.5), faces=("east", "west", "up"))
    m.box("detonator", (6.5, 0.75, 6.5), (9.5, 2, 9.5), faces=("north", "south", "east", "west", "up"))
    lamp(m, (7.5, 2, 7.5), (8.5, 2.4, 8.5))
    m.layout()

    def paint(m: BlockModel, t: Tex, lamp_colour) -> None:
        def plate(t: Tex, r, d):
            shaded(RUBBER)(t, r, d)
            x, y, w, h = r
            if d == "up":
                t.outline(x + 1, y + 1, w - 2, h - 2, GUNMETAL.shadow)   # the plate's moulded rim, inside the cord
        m.paint_all(t, "plate", plate)

        def cord(t: Tex, r, d):
            x, y, w, h = r
            t.rect(x, y, w, h, CORD.base if d in ("up", "north", "west") else CORD.shadow)
            for i in range(0, w, 2):                                # the braid
                t.px(x + i, y, CORD.light)
        fill_all(m, t, ["cord_n", "cord_s", "cord_w", "cord_e", "lead"], cord)
        m.paint_all(t, "detonator", shaded(GUNMETAL))
        paint_lamp(m, t, lamp_colour)
    m.fit_display(gui_rotation=(30, 225, 0), gui_margin=1.5)
    return save(m, paint)


# --- thermite charge -------------------------------------------------------------------------------------------------------
def thermite(lit: bool) -> list[str]:
    name = "thermite_charge_lit" if lit else "thermite_charge"
    m = BlockModel(name, tex_size=(64, 64), kind="block")
    glow = 15 if lit else 0
    m.box("pot_core", (5, 0, 6), (11, 6.5, 10), light=glow)
    m.box("pot_n", (6, 0, 5), (10, 6.5, 6), faces=("north", "east", "west", "up", "down"), light=glow)
    m.box("pot_s", (6, 0, 10), (10, 6.5, 11), faces=("south", "east", "west", "up", "down"), light=glow)
    m.box("rim", (5.5, 6.5, 5.5), (10.5, 7.2, 10.5), faces=("north", "south", "east", "west", "up"))
    m.box("igniter", (6.5, 7.2, 6.5), (9.5, 8, 9.5), faces=("north", "south", "east", "west", "up"))
    m.box("fuse", (7.75, 8, 7.75), (8.25, 9.5, 8.25), faces=("north", "south", "east", "west", "up"))
    lamp(m, (8.6, 8, 8.6), (9.3, 8.4, 9.3))
    m.layout()
    pot = ["pot_core", "pot_n", "pot_s"]

    def paint(m: BlockModel, t: Tex, lamp_colour) -> None:
        def body(t: Tex, r, d):
            x, y, w, h = r
            if lit:
                t.rect(x, y, w, h, HOT.base if d in ("north", "west", "up") else HOT.shadow)
                for i in range(0, w, 3):                           # cracks glowing white-hot
                    t.vline(x + i, y + (i * 7) % max(1, h - 2), 2, HOT.highlight)
                return
            shaded(CLAY)(t, r, d)
            if d in ("north", "south", "east", "west") and h > 4:
                t.hline(x, y + 1, w, P.WHITE_CERAMIC[5])             # a white band near the top
        fill_all(m, t, pot, body)
        m.paint_all(t, "rim", shaded(HOT if lit else CLAY))
        m.paint_all(t, "igniter", shaded(STEEL))
        m.paint_all(t, "fuse", shaded(P.WHITE_CERAMIC))
        paint_lamp(m, t, lamp_colour)
    m.fit_display(gui_rotation=(30, 225, 0), gui_margin=1.5)
    return save(m, paint, armed_too=not lit)


# --- timed bomb ------------------------------------------------------------------------------------------------------------
def timed_bomb() -> list[str]:
    m = BlockModel("timed_bomb", tex_size=(64, 64), kind="block")
    m.box("case", (1, 0, 3), (15, 7, 13))
    m.box("display", (4, 2, 2.4), (12, 6, 3), faces=("north", "east", "west", "up", "down"))
    lamp(m, (12.6, 4.5, 2.6), (13.4, 5.5, 3), faces=("north", "east", "west", "up", "down"))
    for i, z in enumerate((4, 6.75, 9.5)):
        m.box(f"stick{i}", (2, 7, z), (14, 9.5, z + 2.5), faces=("north", "south", "east", "west", "up"))
    for k, x in enumerate((4, 11)):
        m.box(f"strap{k}", (x, 7, 3.8), (x + 1, 9.8, 12.2), faces=("north", "south", "east", "west", "up"))
    for i in range(4):
        m.box(f"wire{i}", (5.4 + i * 1.4, 7, 3.1), (6.3 + i * 1.4, 7.4, 3.9), faces=("north", "east", "west", "up"))
    m.layout()

    def paint(m: BlockModel, t: Tex, lamp_colour) -> None:
        def case(t: Tex, r, d):
            G.shaded(STEEL)(t, r, d)
            x, y, w, h = r
            if d in ("north", "south", "east", "west") and w > 4:
                t.rivet(x + 1, y + 1, STEEL)
                t.rivet(x + w - 2, y + 1, STEEL)
            if d == "south" and w > 8:
                t.stripes(x + 2, y + h - 3, w - 4, 2, P.HAZARD_YELLOW[4], P.HAZARD_BLACK[3], width=1)
        m.paint_all(t, "case", case)

        def display(t: Tex, r, d):
            x, y, w, h = r
            t.rect(x, y, w, h, LCD.base if d == "north" else GUNMETAL.shadow)
            if d == "north":
                t.outline(x, y, w, h, GUNMETAL.deep)
        m.paint_all(t, "display", display)

        def stick(t: Tex, r, d):
            x, y, w, h = r
            if d in ("east", "west"):
                t.rect(x, y, w, h, G.TAN.base)
                return
            t.rect(x, y, w, h, G.PAPER_RED.base if d in ("north", "up") else G.PAPER_RED.shadow)
            t.hline(x, y, w, G.PAPER_RED.light)
        fill_all(m, t, ["stick0", "stick1", "stick2"], stick)
        fill_all(m, t, ["strap0", "strap1"], shaded(GUNMETAL))
        for i in range(4):
            m.paint_all(t, f"wire{i}", shaded(WIRE_COLOURS[i]))
        paint_lamp(m, t, lamp_colour)
    m.fit_display(gui_rotation=(30, 225, 0), gui_margin=1.0)
    return save(m, paint)


# --- mines -----------------------------------------------------------------------------------------------------------------
def landmine() -> list[str]:
    m = BlockModel("landmine", tex_size=(64, 64), kind="block")
    body = G.prism(m, "body", 8, 8, 5, 3.5, -2, 0.75)
    plate = G.prism(m, "plate", 8, 8, 2, 1.2, 0.75, 1.5)
    lamp(m, (11.2, 0.75, 7.6), (12, 1.15, 8.4))
    m.layout()

    def paint(m: BlockModel, t: Tex, lamp_colour) -> None:
        def disc(t: Tex, r, d):
            shaded(P.OLIVE)(t, r, d)
            x, y, w, h = r
            if d == "up" and w > 4:
                t.outline(x, y, w, h, P.OLIVE.shadow)
        fill_all(m, t, body, disc)
        fill_all(m, t, plate, shaded(STEEL))
        paint_lamp(m, t, lamp_colour)
    m.fit_display(gui_rotation=(30, 225, 0), gui_margin=1.5)
    return save(m, paint)


def bouncing_mine() -> list[str]:
    m = BlockModel("bouncing_mine", tex_size=(64, 64), kind="block")
    body = G.prism(m, "can", 8, 8, 3, 2, -7, 0.5)
    m.box("cap", (5.5, 0.5, 5.5), (10.5, 1.5, 10.5), faces=("north", "south", "east", "west", "up"))
    for i, (x, z) in enumerate(((7.6, 6.4), (6.4, 8.8), (8.8, 8.8))):
        m.box(f"prong{i}", (x, 1.5, z), (x + 0.6, 2.5, z + 0.6), faces=("north", "south", "east", "west", "up"))
    lamp(m, (9.2, 1.5, 6.2), (9.9, 1.9, 6.9))
    m.layout()

    def paint(m: BlockModel, t: Tex, lamp_colour) -> None:
        def can(t: Tex, r, d):
            shaded(P.OLIVE)(t, r, d)
            x, y, w, h = r
            if d in ("north", "south", "east", "west") and h > 6:
                t.hline(x, y + 2, w, P.HAZARD_YELLOW[4])            # a yellow band near the top
        fill_all(m, t, body, can)
        m.paint_all(t, "cap", shaded(GUNMETAL))
        fill_all(m, t, ["prong0", "prong1", "prong2"], shaded(STEEL))
        paint_lamp(m, t, lamp_colour)
    m.fit_display(gui_rotation=(30, 225, 0), gui_margin=1.5)
    return save(m, paint)


def claymore() -> list[str]:
    """Thin and curved like the real thing (about 6 : 2.3 : 1 wide, high, deep): the face section bows forward, the
    wings sit 0.7 px further back. The wings keep their inner faces, so behind the face section, where they stand
    proud of it, there is no gap to see through."""
    m = BlockModel("claymore", tex_size=(64, 64), kind="block")
    m.box("front", (4, 1, 7), (12, 6, 9))
    m.box("wing_w", (2, 1, 7.7), (4, 6, 9.7))
    m.box("wing_e", (12, 1, 7.7), (14, 6, 9.7))
    legs = []
    for i, (x, z) in enumerate(((2.8, 8.0), (2.8, 9.0), (12.7, 8.0), (12.7, 9.0))):
        legs.append(f"leg{i}")
        m.box(legs[-1], (x, 0, z), (x + 0.5, 1, z + 0.5), faces=("north", "south", "east", "west"))
    m.box("sight", (7.5, 6, 7.4), (8.5, 7, 8.6), faces=("north", "south", "east", "west", "up"))
    lamp(m, (7.5, 4, 6.6), (8.5, 5, 7), faces=("north", "east", "west", "up", "down"))
    m.layout()

    def paint(m: BlockModel, t: Tex, lamp_colour) -> None:
        def body(t: Tex, r, d):
            shaded(P.OLIVE)(t, r, d)
            x, y, w, h = r
            if d == "north" and w >= 8:
                t.outline(x, y, w, h, P.OLIVE.light)                 # the moulded rim of the face
                t.decal("chevron5x3", x + (w - 5) // 2, y + h - 4, P.HAZARD_YELLOW[4])   # under the lens
        fill_all(m, t, ["front", "wing_w", "wing_e"], body)
        fill_all(m, t, legs, shaded(GUNMETAL))
        m.paint_all(t, "sight", shaded(GUNMETAL))
        paint_lamp(m, t, lamp_colour)
    m.fit_display(gui_rotation=(30, 225, 0), gui_margin=1.2)
    return save(m, paint)


# --- blockstates and item definitions --------------------------------------------------------------------------------------
def blockstates() -> None:
    v = {}
    for facing, rot in FACINGS.items():
        for armed in (False, True):
            for n in range(1, 5):
                v[f"armed={str(armed).lower()},candles={n},facing={facing}"] = {"model": f"groundzero:block/c4_{n}{'_armed' if armed else ''}", **rot}
    write_json(paths.ASSETS / "blockstates" / "c4.json", {"variants": v})
    v = {}
    for facing, rot in FACINGS.items():
        for armed in (False, True):
            v[f"armed={str(armed).lower()},facing={facing}"] = {"model": f"groundzero:block/breaching_charge{'_armed' if armed else ''}", **rot}
    write_json(paths.ASSETS / "blockstates" / "breaching_charge.json", {"variants": v})
    v = {}
    for facing, rot in FACINGS.items():
        for armed in (False, True):
            for lit in (False, True):
                model = "thermite_charge_lit" if lit else f"thermite_charge{'_armed' if armed else ''}"
                v[f"armed={str(armed).lower()},facing={facing},lit={str(lit).lower()}"] = {"model": f"groundzero:block/{model}", **rot}
    write_json(paths.ASSETS / "blockstates" / "thermite_charge.json", {"variants": v})
    v = {}
    for facing, rot in HORIZONTAL.items():
        for armed in (False, True):
            v[f"armed={str(armed).lower()},facing={facing}"] = {"model": f"groundzero:block/timed_bomb{'_armed' if armed else ''}", **rot}
    write_json(paths.ASSETS / "blockstates" / "timed_bomb.json", {"variants": v})
    for name in ("landmine", "bouncing_mine"):
        v = {}
        for facing in FACINGS:
            for armed in (False, True):
                v[f"armed={str(armed).lower()},facing={facing}"] = {"model": f"groundzero:block/{name}{'_armed' if armed else ''}"}
        write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": v})
    v = {}
    for facing, rot in HORIZONTAL.items():
        for armed in (False, True):
            v[f"armed={str(armed).lower()},facing={facing}"] = {"model": f"groundzero:block/claymore{'_armed' if armed else ''}", **rot}
    write_json(paths.ASSETS / "blockstates" / "claymore.json", {"variants": v})
    for item, model in (("c4", "c4_1"), ("breaching_charge", "breaching_charge"), ("thermite_charge", "thermite_charge"),
                        ("timed_bomb", "timed_bomb"), ("landmine", "landmine"), ("bouncing_mine", "bouncing_mine"),
                        ("claymore", "claymore")):
        write_json(paths.ASSETS / "items" / f"{item}.json", {"model": {"type": "minecraft:model", "model": f"groundzero:block/{model}"}})


# --- item sprites ----------------------------------------------------------------------------------------------------------
def _outline(t: Tex, c) -> None:
    filled = {(x, y) for y in range(16) for x in range(16) if t.get(x, y)[3] > 0}
    for (x, y) in list(filled):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if 0 <= n[0] < 16 and 0 <= n[1] < 16 and n not in filled:
                t.px(n[0], n[1], c)


def remote_detonator() -> Tex:
    """A hand-held radio box: dark grey body, a red firing button under a yellow guard, a little green display, a
    whip antenna with a ball on its tip."""
    t = Tex(16, 16)
    g, s = GUNMETAL, STEEL
    t.rect(5, 1, 1, 6, s.base)                                     # antenna
    t.px(5, 0, s.light)
    t.rect(4, 6, 8, 9, g.base)                                     # body
    t.vline(4, 6, 9, g.light)
    t.hline(4, 6, 8, g.light)
    t.vline(11, 6, 9, g.shadow)
    t.hline(4, 14, 8, g.shadow)
    t.rect(6, 8, 4, 3, P.HAZARD_YELLOW[3])                          # guard
    t.rect(7, 8, 2, 2, P.WARNING_RED[4])                           # button
    t.px(7, 8, P.WARNING_RED[6])
    t.rect(6, 12, 4, 1, LCD[5])                                    # display
    t.px(10, 7, (60, 220, 90, 255))                                # power light
    _outline(t, P.HAZARD_BLACK[1])
    return t


def main() -> None:
    names = []
    for n in range(1, 5):
        names += c4(n)
    names += breaching()
    names += thermite(False)
    names += thermite(True)
    names += timed_bomb()
    names += landmine()
    names += bouncing_mine()
    names += claymore()
    blockstates()
    R.save_item("remote_detonator", remote_detonator())
    print("charges written:", ", ".join(names))


if __name__ == "__main__":
    main()
