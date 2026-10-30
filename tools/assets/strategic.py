"""Whole weapons (DESIGN.md §15.2, Phase 8 slice 8c): the ICBM Assembly Hall.

- Crane Rail (part block): a yellow box girder with a steel rail along its top and hazard-striped ends.
- Controller (block): the machine cabinet, its placard a missile under a crane hook.
- The formed hall (GeckoLib), 7 blocks wide, 13 deep and 6 high: a concrete floor with yellow bay lines and hazard
  stripes all the way round its edge; six grey steel columns four high carrying a yellow crane girder down each long side; a
  twin-girder bridge crane on them with its trolley, hoist drum and a hook block on two cables; two cradles on the floor;
  a control desk at the controller; the Energy Port and the Item Port as plates in the floor where their blocks are
  (after the in-game look: a cabinet stood up where the port is a floor block), a pallet of crates beside them. The intercontinental missile
  lies on the cradles, nose to the front, laid section by section while the hall works (bones sec1-sec3, then the
  warhead, its own model drawn by the game at the bone "warhead"); the crane runs back and forth over it and lowers its
  hook ("working").
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import numpy as np  # noqa: E402

from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb import paths  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.tex import Tex  # noqa: E402
import launch as L  # noqa: E402
import structures as ST  # noqa: E402

STEEL = P.STEEL
YELLOW, GREEN = P.HAZARD_YELLOW, P.INDICATOR_GREEN

#: The floor's top, the lying missile's axis height, and where its tail lies (pixels; the controller's block is at 0).
FLOOR_Y, AXIS_Y, Z_TAIL = 14, 38, 188
#: The crane parks over the missile's tail; its cables run from the trolley down to the hook block.
CRANE_Z, TROLLEY_Y, HOOK_TOP = 184, 105, 72


def crane_rail() -> Tex:
    """The part block: a yellow box girder, a steel rail along its top (bright where the wheels run), a bolted web,
    hazard stripes at its ends."""
    f = M.tile_face()
    img = np.clip(M.painted(M.YELLOW_PAINT_RAMP, tone=0.6, wear=0.0, rust=0.0).render(f, 21), 0, 255).astype(np.uint8)
    col, row = M.grid_xy()
    rail = np.clip(M.steel(tone=0.55, brushed=0.08).render(f, 22), 0, 255).astype(np.uint8)
    img[row < 3] = rail[row < 3]
    M.lighten(img, row == 0, 1.25, 10)                          # the running surface
    M.darken(img, row == 3, 0.5)                                # the rail's shadow on the girder
    M.darken(img, row >= 13, 0.8)                               # the bottom flange
    M.lighten(img, row == 13, 1.15, 4)
    ends = ((col < 3) | (col > 12)) & (row >= 4) & (row <= 12)
    img[ends] = M.hazard_tile(width=2, seed=23)[ends]
    for x in (5, 8, 11):                                        # bolt heads on the web
        M.lighten(img, (col == x) & (row == 8), 1.3, 14)
        M.darken(img, (col == x) & (row == 9), 0.65)
    return M.to_tex(img)


def hall_placard(t: Tex) -> None:
    """The controller's placard: a crane hook over a missile lying on its side."""
    t.rect(4, 9, 6, 4, YELLOW[4])
    t.hline(4, 12, 6, P.BLACK_PAINT[1])                       # the missile
    t.px(9, 11, P.BLACK_PAINT[1])
    t.vline(6, 9, 2, P.BLACK_PAINT[1])                       # the hook's cable
    t.px(7, 10, P.BLACK_PAINT[1])


def hall() -> None:
    g = GeoModel("strategic_assembly_hall", tex_size=(512, 1024))
    g.bone("frame")
    g.bone("crane", pivot=(0, 92, CRANE_Z), parent="frame")
    g.bone("cable", pivot=(0, TROLLEY_Y, CRANE_Z), parent="crane")
    g.bone("hook", pivot=(0, HOOK_TOP, CRANE_Z), parent="crane")
    # The missile on the cradles: the game draws its sections from its own model (MachineGeoRenderer's missile
    # layer), lying along the hall, its tail here.
    g.bone("missile", pivot=(0, AXIS_Y, Z_TAIL), parent="frame")
    F = "frame"
    sides = ("north", "south", "east", "west")
    g.box("floor", (-56, 0, -8), (56, FLOOR_Y, 200), faces=("up",) + sides, bone=F)
    columns = []
    for side, x0 in (("w", -52), ("e", 44)):
        for zc in (0, 96, 192):
            columns.append(f"col_{side}{zc}")
            g.box(columns[-1], (x0, FLOOR_Y, zc - 4), (x0 + 8, 80, zc + 4), faces=sides, bone=F)
    girders, rails = [], []
    for side, x0 in (("w", -54), ("e", 42)):
        girders.append(f"girder_{side}")
        g.box(girders[-1], (x0, 80, -8), (x0 + 12, 90, 200), faces=sides + ("up", "down"), bone=F)
        rails.append(f"rail_{side}")
        g.box(rails[-1], (x0 + 4, 90, -8), (x0 + 8, 92, 200), faces=sides + ("up",), bone=F)
    cradles = []
    for i, zc in enumerate((128, 95)):                       # under the first stage clear of its fins, and the second
        for n, a, b, faces in ((f"cradle{i}_base", (-14, FLOOR_Y, zc - 5), (14, 18, zc + 5), sides + ("up",)),
                               (f"cradle{i}_mid", (-8, 18, zc - 4), (8, 26, zc + 4), ("north", "south", "up")),
                               (f"cradle{i}_pad_w", (-14, 18, zc - 5), (-8, 30, zc + 5), ("north", "south", "west", "up")),
                               (f"cradle{i}_pad_e", (8, 18, zc - 5), (14, 30, zc + 5), ("north", "south", "east", "up"))):
            cradles.append(n)
            g.box(n, a, b, faces=faces, bone=F)
    g.box("desk", (-7, FLOOR_Y, -6), (7, 26, 2), faces=sides + ("up",), bone=F)
    g.box("screen", (-6, 26, -5), (6, 31, -3), faces=sides + ("up",), bone=F)
    # The ports are floor blocks (the back row's middle and two west of it): plates flush with the floor over them.
    g.box("energy_port", (-7, FLOOR_Y, 185), (7, FLOOR_Y + 0.5, 199), faces=sides + ("up",), bone=F)
    g.box("item_port", (-39, FLOOR_Y, 185), (-25, FLOOR_Y + 0.5, 199), faces=sides + ("up",), bone=F)
    g.box("pallet", (26, FLOOR_Y, 186), (38, 16, 198), faces=sides + ("up",), bone=F)
    g.box("crate_a", (27, 16, 187), (33, 22, 193), faces=sides + ("up",), bone=F)
    g.box("crate_b", (33, 16, 191), (37, 21, 197), faces=sides + ("up",), bone=F)
    # The bridge crane: end trucks on the rails, twin girders across, the trolley and its drum on top; the hook block
    # hangs on two cables between the girders.
    crane = []
    for n, a, b in (("truck_w", (-55, 92, CRANE_Z - 12), (-41, 98, CRANE_Z + 12)), ("truck_e", (41, 92, CRANE_Z - 12), (55, 98, CRANE_Z + 12)),
                    ("bridge_n", (-50, 98, CRANE_Z - 8), (50, 105, CRANE_Z - 4)), ("bridge_s", (-50, 98, CRANE_Z + 4), (50, 105, CRANE_Z + 8)),
                    ("trolley", (-9, TROLLEY_Y, CRANE_Z - 9), (9, 111, CRANE_Z + 9)), ("drum", (-6, 111, CRANE_Z - 4), (6, 115, CRANE_Z + 4))):
        crane.append(n)
        g.box(n, a, b, faces=sides + ("up", "down"), bone="crane")
    for n, x in (("cable_w", -2.5), ("cable_e", 1.5)):
        g.box(n, (x, HOOK_TOP, CRANE_Z - 0.5), (x + 1, TROLLEY_Y, CRANE_Z + 0.5), faces=sides, bone="cable")
    g.box("hook_block", (-4, HOOK_TOP - 8, CRANE_Z - 3), (4, HOOK_TOP, CRANE_Z + 3), faces=sides + ("up", "down"), bone="hook")
    g.box("hook", (-1, HOOK_TOP - 14, CRANE_Z - 1), (1, HOOK_TOP - 8, CRANE_Z + 1), faces=sides + ("down",), bone="hook")
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)
    paint_hall(g, tex, columns, girders, rails, cradles)
    g.save(tex)
    # (The Tsar's vents were all its glow mask lit: the Tsar's own model lights them now.)
    (paths.ASSETS / "textures" / "block" / "strategic_assembly_hall_glowmask.png").unlink(missing_ok=True)
    # Working (12 s, looped while the hall runs): the crane runs from over the tail to the first stage and lowers its
    # hook, on to the third stage and lowers it again, and back; the cables pay out as the hook drops.
    work = Animation("working", 12.0, loop=True)
    work.move("crane", {0: (0, 0, 0), 2.5: (0, 0, -40), 5: (0, 0, -40), 7.2: (0, 0, -120), 9.6: (0, 0, -120), 11.6: (0, 0, 0), 12: (0, 0, 0)},
              easing="easeInOutSine")
    drop1, drop2 = 10, 12
    hook = {0: (0, 0, 0), 2.6: (0, 0, 0), 3.4: (0, -drop1, 0), 4.2: (0, -drop1, 0), 4.9: (0, 0, 0), 7.3: (0, 0, 0), 8.1: (0, -drop2, 0),
            8.9: (0, -drop2, 0), 9.5: (0, 0, 0), 12: (0, 0, 0)}
    work.move("hook", hook)
    rest = TROLLEY_Y - HOOK_TOP
    work.scale("cable", {t: (1, (rest - v[1]) / rest, 1) for t, v in hook.items()})
    idle = Animation("idle", 1.0, loop="hold_on_last_frame")
    idle.move("crane", {0: (0, 0, 0), 1: (0, 0, 0)})
    print(save_animations("strategic_assembly_hall", [work, idle]))


def paint_hall(g: GeoModel, tex: Tex, columns, girders, rails, cradles) -> None:
    """The hall's surfaces, in model space (tools/gb/materials.py): a sealed concrete floor cut into slabs at the column
    lines, worn yellow bay lines and hazard bands all round it; grey steel columns, yellow crane girders bolted along their sides,
    rails polished where the wheels run; dark cradles with yellow cheeks and rubber saddles."""
    S = M.Surfacer(g, seed=43)
    S.paint(tex, "floor", M.concrete(tone=0.66, mottle=0.028, grain=0.035))
    S.paint(tex, "floor", M.concrete(tone=0.6), only=("north", "south", "east", "west"))
    line = M.painted(M.YELLOW_PAINT_RAMP, tone=0.62, wear=0.0, grime=0.0, edge_light=0.0, edge_dark=0.0)
    band = M.stripes(width=3, wear=0.4)

    def floor(f, img):
        joint = np.abs(((f.Z - 0.5) + 24) % 48 - 24) < 0.25          # saw cuts at the column lines and between
        M.darken(img, joint, 0.62)
        bay = (np.abs(np.abs(f.X) - 20) < 0.75) & (f.Z > -2) & (f.Z < 194)
        M.paste(img, S.material_on(f, line), bay & ~M.scuffed(f, 0.35, seed=5))
        # Hazard bands all the way round (after the user's look: at first only across the open ends).
        M.paste(img, S.material_on(f, band), (f.Z < -5) | (f.Z > 197) | (np.abs(f.X) > 53))
    S.overlay(tex, "floor", floor, only=("up",))

    grey = M.painted(M.GREY_PAINT_RAMP, tone=0.56, wear=0.3, rust=0.1)
    S.paint(tex, columns, grey)

    def web(f, img):                                                    # the I-beam's web between its flanges
        zc = (f.box.frm[2] + f.box.to[2]) / 2
        M.darken(img, np.abs(f.Z - zc) < 1, 0.6)
        M.darken(img, (np.abs(f.Z - zc) >= 1) & (np.abs(f.Z - zc) < 2), 0.84)
    S.overlay(tex, columns, web, only=("east", "west"))

    yellow = M.painted(M.YELLOW_PAINT_RAMP, tone=0.6, wear=0.35, rust=0.12, streaks=0.05)
    hazard = M.stripes(width=2, wear=0.4, under=M.STEEL_RAMP[2:7])
    S.paint(tex, girders, yellow)
    S.paint(tex, girders, hazard, only=("north", "south"))
    S.overlay(tex, girders, lambda f, img: M.rivets(f, img, every=8, inset=1), only=("east", "west"))
    S.paint(tex, rails, M.steel(tone=0.5, brushed=0.08, rust=0.1))

    def running(f, img):                                                # bright where the crane's wheels run
        xc = (f.box.frm[0] + f.box.to[0]) / 2
        M.lighten(img, np.abs(f.X - xc) < 1, 1.25, 12)
    S.overlay(tex, rails, running, only=("up",))

    body = [n for n in cradles if "pad" not in n]
    S.paint(tex, body, M.painted(M.ARMOUR_RAMP, tone=0.6, wear=0.3, rust=0.1))
    S.paint(tex, [n for n in cradles if "pad" in n], M.painted(M.YELLOW_PAINT_RAMP, tone=0.58, wear=0.45))
    S.paint(tex, [n for n in cradles if n.endswith("mid")], M.rubber(tone=0.5), only=("up",))

    S.paint(tex, "desk", M.painted(M.OLIVE_RAMP, tone=0.55, wear=0.3))
    S.paint(tex, "screen", M.painted(M.ARMOUR_RAMP, tone=0.5, wear=0.2))

    def desk(t: Tex, r, d):
        x, y, w, h = r
        if d == "north" and w > 6:
            t.rect(x + 1, y + 1, w - 2, min(3, h - 2), P.BLACK_PAINT.deep)
            for xx in range(x + 1, x + w - 1):
                t.px(xx, y + 2, GREEN[4] if xx % 3 else YELLOW[4])
    g.paint_all(tex, "desk", desk)

    def screen(t: Tex, r, d):
        x, y, w, h = r
        if d == "north" and w > 4:
            t.rect(x + 1, y + 1, w - 2, max(1, h - 2), P.BLACK_PAINT.deep)
            for xx in range(x + 1, x + w - 1):
                t.px(xx, y + 1 + (xx % 2), GREEN[4])
    g.paint_all(tex, "screen", screen)

    # The ports: dark steel hatches in the floor, a painted border (red for power, hazard for items) and a glyph.
    plate = M.steel(tone=0.4, brushed=0.04, rust=0.08)
    for name, border in (("energy_port", M.painted(M.RED_PAINT_RAMP, tone=0.58, wear=0.3)), ("item_port", M.stripes(width=2, wear=0.3))):
        S.paint(tex, name, plate)

        def rim(f, img, border=border):
            ring = np.minimum(np.minimum(f.border["top"], f.border["bottom"]), np.minimum(f.border["left"], f.border["right"]))
            M.paste(img, S.material_on(f, border), ring < 2)
            M.darken(img, ring == 2, 0.55)
        S.overlay(tex, name, rim, only=("up",))

    def bolt(t: Tex, r, d):                                             # the Energy Port's lightning bolt
        if d == "up":
            cx, cy = r[0] + r[2] // 2, r[1] + r[3] // 2
            for dx, dy in ((1, -3), (0, -2), (-1, -1), (0, -1), (1, -1), (0, 0), (-1, 1), (-2, 2)):
                t.px(cx + dx, cy + dy, YELLOW[4])

    def grate(t: Tex, r, d):                                            # the Item Port's grated hatch
        if d == "up":
            cx, cy = r[0] + r[2] // 2, r[1] + r[3] // 2
            for yy in range(cy - 3, cy + 4, 2):
                t.hline(cx - 3, yy, 7, STEEL[5])
                t.hline(cx - 3, yy + 1, 7, P.BLACK_PAINT.deep)
    g.paint_all(tex, "energy_port", bolt)
    g.paint_all(tex, "item_port", grate)

    S.paint(tex, "pallet", M.wood(tone=0.55))
    S.overlay(tex, "pallet", lambda f, img: M.seams(f, img, every=3, axis="u", light=False), only=("up",))
    crate = M.painted(M.OLIVE_RAMP, tone=0.5, wear=0.35, under=M.WOOD_RAMP[2:6])
    S.paint(tex, ["crate_a", "crate_b"], crate)

    # The crane: yellow, hazard-striped at its ends, the girders bolted along; a grey trolley, the drum wound with rope.
    S.paint(tex, ["truck_w", "truck_e", "bridge_n", "bridge_s", "hook_block"], yellow)
    S.paint(tex, ["truck_w", "truck_e"], hazard, only=("north", "south"))
    S.paint(tex, "hook_block", hazard, only=("north", "south", "east", "west"))
    S.overlay(tex, ["bridge_n", "bridge_s"], lambda f, img: M.rivets(f, img, every=6, inset=1), only=("north", "south"))
    S.paint(tex, "trolley", M.painted(M.GREY_PAINT_RAMP, tone=0.42, wear=0.3, rust=0.1))
    S.paint(tex, "drum", M.steel(tone=0.55))
    S.overlay(tex, "drum", lambda f, img: M.darken(img, (np.floor(f.X) % 2) == 0, 0.72), only=("north", "south", "up"))
    S.paint(tex, ["cable_w", "cable_e"], M.Material(ramp=M.BLACK_RAMP, tone=0.45, dither=0.3, edge_light=0.25))
    S.paint(tex, "hook", M.steel(tone=0.35))


def main() -> None:
    ST.part_block("crane_rail", crane_rail(), cutout=False)
    ST.controller("strategic_assembly_hall", ST.controller_front(hall_placard), ST.controller_top(), ST.casing())
    hall()
    print("strategic written")


if __name__ == "__main__":
    main()
