"""Missile defence (DESIGN.md §18.2, Phase 10): the Interceptor Battery and the Interceptor Missile.

- Interceptor Battery (GeckoLib, two blocks tall): a gunmetal plinth under a NATO-green equipment cabinet (louvred
  sides, a front door with its status lamps: power, ready, engaged), a steel deck on top; on the deck a turret
  (bone "turret", turned by the block entity toward its target) on a turntable ring, two cheeks holding the launcher's
  trunnions, an electronics box at its back carrying a spinning search antenna ("antenna", turning while it is
  powered); the launcher (bone "launcher", raised by the block entity) a box of four cells two by two, a hazard band
  round its mouth and an electro-optical seeker on top with a red lens; in each cell an interceptor's nose ("cell0" to
  "cell3", shown while loaded). Front = north.
- Interceptor Missile (item): a slim white missile, a black radome nose, four steering canards behind it, a red band,
  four grey tail fins and a dark nozzle. The interceptor in the air is this, drawn larger (InterceptorRenderer).
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import numpy as np  # noqa: E402

from gb import machine_kit as K  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb import paths  # noqa: E402
from gb.model import write_json  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.model import BlockModel  # noqa: E402
from gb.tex import Tex  # noqa: E402
import grenades as G  # noqa: E402
import machines as MC  # noqa: E402
import warheads as W  # noqa: E402

NAME = "interceptor_battery"
SIDES = ("north", "south", "east", "west")
#: The turret's turntable top, the launcher's trunnion height, the launcher's mouth (z) and its back.
RING_TOP, TRUNNION, MOUTH, BACK = 17.5, 24.0, -10.0, 4.0
#: The four cells' middles on the mouth (x, y): two by two round the trunnion line.
CELLS = [(-2.6, TRUNNION + 2.2), (2.6, TRUNNION + 2.2), (-2.6, TRUNNION - 2.2), (2.6, TRUNNION - 2.2)]


def build() -> tuple[GeoModel, Tex, Tex, dict]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("frame")
    g.bone("turret", pivot=(0, 16, 0), parent="frame")
    g.bone("antenna", pivot=(0, 22.5, 6.5), parent="turret")
    g.bone("launcher", pivot=(0, TRUNNION, 0), parent="turret")
    for i, (x, y) in enumerate(CELLS):
        g.bone(f"cell{i}", pivot=(x, y, MOUTH), parent="launcher")
    F, T, L = "frame", "turret", "launcher"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    # --- the pedestal: plinth, cabinet, deck ---------------------------------------------------------------------------
    add("plinth", g.box("plinth", (-8, 0, -8), (8, 2, 8), faces=SIDES + ("up",), bone=F))
    add("cabinet", g.box("cabinet", (-7, 2, -7), (7, 14, 7), faces=SIDES, bone=F))
    add("deck", g.box("deck", (-7.5, 14, -7.5), (7.5, 16, 7.5), faces=SIDES + ("up", "down"), bone=F))
    add("conduit", g.box("conduit", (-1.5, 2, 7), (1.5, 13, 8), faces=("south", "east", "west", "up"), bone=F))
    add("lamps", g.box("lamps", (2, 10.5, -7.3), (5.5, 12, -7), faces=("north", "east", "west", "up", "down"), bone=F))
    # --- the turret: turntable, cheeks, electronics box and its antenna ---------------------------------------------
    add("ring", g.box("ring", (-6, 16, -6), (6, RING_TOP, 6), faces=SIDES + ("up",), bone=T))
    for side, x0 in (("w", -7.0), ("e", 5.5)):
        add("cheek", g.box(f"cheek_{side}", (x0, RING_TOP, -2.5), (x0 + 1.5, 26.5, 2.5), faces=SIDES + ("up",), bone=T))
        add("hub", g.box(f"hub_{side}", (x0 - 0.4 if side == "w" else x0 + 1.5, TRUNNION - 1.2, -1.2),
                         (x0 if side == "w" else x0 + 1.9, TRUNNION + 1.2, 1.2), faces=SIDES + ("up", "down"), bone=T))
    add("ebox", g.box("ebox", (-4, RING_TOP, 5.5), (4, 20.5, 7.5), faces=SIDES + ("up",), bone=T))
    add("mast", g.box("mast", (-0.5, 20.5, 6.0), (0.5, 22.5, 7.0), faces=SIDES, bone=T))
    add("antenna", g.box("antenna_bar", (-3, 22.5, 6.1), (3, 23.5, 6.9), faces=SIDES + ("up", "down"), bone="antenna"))
    # --- the launcher: its box, its seeker, the interceptors' noses in its cells -------------------------------------
    add("box", g.box("box", (-5.5, TRUNNION - 4.5, MOUTH), (5.5, TRUNNION + 4.5, BACK), bone=L))
    add("seeker", g.box("seeker", (-2, TRUNNION + 4.5, MOUTH + 1.5), (2, TRUNNION + 6.5, MOUTH + 5), faces=SIDES + ("up",), bone=L))
    add("lens", g.box("lens", (-1.2, TRUNNION + 4.9, MOUTH + 1.2), (1.2, TRUNNION + 6.1, MOUTH + 1.5), faces=("north",), bone=L))
    for i, (x, y) in enumerate(CELLS):
        add("nose", g.box(f"nose{i}", (x - 1.4, y - 1.4, MOUTH - 0.6), (x + 1.4, y + 1.4, MOUTH), faces=("north", "east", "west", "up", "down"),
                          bone=f"cell{i}"))
    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, glow, parts)
    return g, tex, glow, parts


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The battery's surfaces, in model space (tools/gb/machine_kit.py): a gunmetal plinth banded in hazard paint, a
    NATO-green cabinet in panels with louvred sides and a door with its lamps and data plate, a steel deck; a gunmetal
    turntable, green cheeks on steel hubs, a dark electronics box, a steel antenna; the green launcher in panels, a
    hazard band round its mouth, the four cells' dark bores, its seeker dark with a red lens; the noses white with black
    radomes."""
    k = K.Kit(g, parts, seed=811)
    k.plinth(tex)
    k.paint(tex, "cabinet", k.NATO)
    k.panels(tex, "cabinet", inset=1, min_size=6, only=("north", "south"))
    k.ribs(tex, "cabinet", every=2.0, only=("east", "west"))
    g.paint_all(tex, parts["cabinet"][0], lambda t, r, d: K.plate(t, r[0] + 2, r[1] + 2, 5, 2) if d == "north" else None)
    k.paint(tex, "deck", k.STEEL)
    k.overlay(tex, "deck", lambda f, img: M.seams(f, img, every=5, axis="u", offset=2.5, light=False), only=("up",))
    k.paint(tex, "conduit", k.DARK)
    k.paint(tex, "lamps", k.BLACK)

    def lamp_glyph(t, r, d):
        if d == "north":
            K.lamps(t, r[0] + 1, r[1] + r[3] // 2, (P.INDICATOR_GREEN.light, P.NUCLEAR_AMBER.light, P.INDICATOR_RED.light))
    g.paint_all(tex, parts["lamps"][0], lamp_glyph)
    g.paint_all(glow, parts["lamps"][0], lamp_glyph)

    k.paint(tex, "ring", k.GUN)
    k.overlay(tex, "ring", lambda f, img: M.rivets(f, img, every=3, inset=1) if f.vertical else None)
    k.paint(tex, "cheek", k.NATO)
    k.paint(tex, "hub", k.STEEL)
    k.paint(tex, "ebox", k.DARK)
    k.overlay(tex, "ebox", lambda f, img: M.darken(img, (np.floor(f.plane_uv()[0]) % 2) == 0, 0.7) if f.d == "south" else None)
    k.paint(tex, ["mast", "antenna"], k.STEEL)

    k.paint(tex, "box", k.NATO)
    k.panels(tex, "box", inset=1, min_size=6, only=("east", "west", "up", "down"))

    def mouth(f, img):                                           # a hazard band round the mouth, the four bores dark
        if f.d != "north":
            return
        r = K.ring(f)
        M.paste(img, k.on(f, k.HAZARD), r < 1)
        for x, y in CELLS:
            bore = (np.abs(f.X - x) < 1.8) & (np.abs(f.Y - y) < 1.8)
            lip = (np.abs(f.X - x) < 2.2) & (np.abs(f.Y - y) < 2.2) & ~bore
            img[bore, :3] = P.BLACK_PAINT.deep[:3]
            M.darken(img, lip, 0.55)
    k.overlay(tex, "box", mouth)
    k.overlay(tex, "box", lambda f, img: M.paste(img, k.on(f, k.HAZARD), f.border["top"] < 1), only=("east", "west"))
    k.paint(tex, "seeker", k.DARK)

    def lens(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.INDICATOR_RED[2])
        if w >= 2:
            t.px(x + w // 2, y + h // 2, P.INDICATOR_RED.light)
    g.paint_all(tex, parts["lens"][0], lens)
    g.paint_all(glow, parts["lens"][0], lens)
    k.paint(tex, "nose", k.WHITE)

    def radome(t, r, d):                                         # the nose's black radome on its face
        x, y, w, h = r
        if d == "north" and w >= 2 and h >= 2:
            t.rect(x + 1, y + 1, max(1, w - 2), max(1, h - 2), P.BLACK_PAINT[1])
            t.px(x + 1, y + 1, P.BLACK_PAINT[3])
    for b in parts["nose"]:
        g.paint_all(tex, b, radome)


def animations() -> list[Animation]:
    """Working (2 s, looped while it is powered): its search antenna turns. Idle: it rests."""
    work = Animation("working", 2.0, loop=True)
    work.rotate("antenna", {0: (0, 0, 0), 1: (0, 180, 0), 2: (0, 360, 0)})
    idle = Animation("idle", 1.0, loop="hold_on_last_frame")
    idle.rotate("antenna", {0: (0, 0, 0), 1: (0, 0, 0)})
    return [work, idle]


# --- the Interceptor Missile (item) ----------------------------------------------------------------------------------------

def interceptor_missile() -> None:
    """A slim white missile standing on its tail: dark nozzle, grey tail fins, a red band, steering canards, a black
    radome nose (the material library)."""
    m = BlockModel("interceptor_missile", tex_size=(64, 64), kind="item", render_type="minecraft:cutout")
    m.open_ok = set()
    R = 1.25
    groups: dict[str, list[str]] = {}
    lips: dict[str, tuple[bool, bool]] = {}

    def add(group, names, top=True, bottom=False):
        groups.setdefault(group, []).extend(names)
        for n in names:
            lips[n] = (top, bottom)
    add("nozzle", W.section(m, "nozzle0_", 0.8, 0.6, 1.6, W.sides_for(0.8), caps=("down",), stagger="full"), False, True)
    add("body", W.section(m, "body0_", R, 1.6, 12.0, W.sides_for(R), caps=("down",), stagger="full"), False, True)
    rings, top, _ = W.nose(m, "radome", R, 12.0, 3.4, "ogive", 4, min_r=0.45)
    add("radome", [n for ring in rings for n in ring], True, False)
    for axis in ("x", "z"):
        lo, hi = (8 - 3.0, 8 + 3.0)
        frm, to = ((lo, 1.7, 7.8), (hi, 4.6, 8.2)) if axis == "x" else ((7.8, 1.85, lo), (8.2, 4.6, hi))
        m.box(f"fin_{axis}", frm, to)
        groups.setdefault("fin", []).append(f"fin_{axis}")
        cfrm, cto = ((8 - 2.0, 9.6, 7.85), (8 + 2.0, 10.6, 8.15)) if axis == "x" else ((7.85, 9.75, 8 - 2.0), (8.15, 10.6, 8 + 2.0))
        m.box(f"canard_{axis}", cfrm, cto)
        groups.setdefault("canard", []).append(f"canard_{axis}")
    while True:
        try:
            m.layout()
            break
        except ValueError:
            m.tex_w *= 2
            m.tex_h = m.tex_w
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=827, ao_reach=1.2, turned=True)
    for n, (lit, base) in lips.items():
        W.round_faces(k.S, [n], lit, base, False)
    k.paint(t, groups["nozzle"], W.metal(W.GUNMETAL, 0.3))
    k.paint(t, groups["body"], M.white_paint(tone=0.8, wear=0.05))
    red = M.painted(M.RED_PAINT_RAMP, tone=0.5, wear=0.05)
    k.overlay(t, groups["body"], lambda f, img: M.paste(img, k.on(f, red), (f.Y >= 7.5) & (f.Y < 8.5)) if f.d in SIDES else None)
    k.overlay(t, groups["body"], lambda f, img: M.darken(img, (f.Y >= 5.5) & (f.Y < 6.0), 0.8) if f.d in SIDES else None)
    k.paint(t, groups["radome"], M.painted(M.BLACK_RAMP, tone=0.42, wear=0.05))
    k.paint(t, groups["fin"] + groups["canard"], M.painted(M.GREY_PAINT_RAMP, tone=0.5, wear=0.15))
    # As grenades.finish, but in the inventory it lies across the slot corner to corner, nose up and right (upright it
    # was a sliver).
    m.fit_display(gui_rotation=(0, 0, -45), gui_margin=0.5)
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
    m.save(t)
    write_json(paths.ASSETS / "items" / "interceptor_missile.json",
               {"model": {"type": "minecraft:model", "model": "groundburst:item/interceptor_missile"}})


def main() -> None:
    model, texture, glowmask, _ = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
    MC.gecko_machine(NAME, tall=True)
    interceptor_missile()
    print("interceptor battery written")


if __name__ == "__main__":
    main()
