"""Whole weapons (DESIGN.md §15.2, Phase 8 slice 8c): the Strategic Assembly Hall.

- Crane Rail (part block): a yellow box girder with a steel rail along its top and hazard-striped ends.
- Controller (block): the machine cabinet, its placard a missile under a crane hook.
- The formed hall (GeckoLib), 7 blocks wide, 13 deep and 6 high: a concrete floor with yellow bay lines and hazard
  stripes at its open ends; six grey steel columns four high carrying a yellow crane girder down each long side; a
  twin-girder bridge crane on them with its trolley, hoist drum and a hook block on two cables; two cradles on the floor;
  a control desk at the controller; the Energy Port and the Item Port as plates in the floor where their blocks are
  (after the in-game look: a cabinet stood up where the port is a floor block), a pallet of crates beside them. The intercontinental missile
  lies on the cradles, nose to the front, laid section by section while the hall works (bones sec1-sec3, then the
  warhead's shroud, nose_<warhead>); the crane runs back and forth over it and lowers its hook ("working").
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gz import palette as P  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.tex import Tex  # noqa: E402
import launch as L  # noqa: E402
import structures as ST  # noqa: E402

STEEL, GUN, OLIVE, CONCRETE = P.STEEL, P.GUNMETAL, P.OLIVE, P.CONCRETE
YELLOW, BLACK, RED, GREEN = P.HAZARD_YELLOW, P.HAZARD_BLACK, P.WARNING_RED, P.INDICATOR_GREEN
TOWER = L.TOWER
tone, shaded = L.tone, L.shaded

#: The floor's top, the lying missile's axis height, and where its tail lies (pixels; the controller's block is at 0).
FLOOR_Y, AXIS_Y, Z_TAIL = 14, 38, 188
#: The crane parks over the missile's tail; its cables run from the trolley down to the hook block.
CRANE_Z, TROLLEY_Y, HOOK_TOP = 184, 105, 72
#: The lying missile's faces: its item model's up (the nose end) faces the hall's front, its item z becomes height.
FACE = {"up": "north", "down": "south", "north": "down", "south": "up", "east": "east", "west": "west"}


def crane_rail() -> Tex:
    """The part block: a yellow box girder, a steel rail along its top, hazard stripes at its ends."""
    t = Tex(16, 16)
    t.rect(0, 0, 16, 16, YELLOW[3])
    t.rect(0, 0, 16, 3, STEEL[4])                            # the rail
    t.hline(0, 3, 16, STEEL[2])
    t.rect(0, 13, 16, 3, YELLOW[2])                          # the bottom flange
    t.stripes(0, 4, 3, 9, YELLOW[4], BLACK[2], width=2)
    t.stripes(13, 4, 3, 9, YELLOW[4], BLACK[2], width=2)
    for x in range(4, 13, 4):
        t.px(x, 8, YELLOW[1])                                # bolt heads on the web
    return t


def hall_placard(t: Tex) -> None:
    """The controller's placard: a crane hook over a missile lying on its side."""
    t.rect(4, 9, 6, 4, YELLOW[4])
    t.hline(4, 12, 6, P.BLACK_PAINT[1])                       # the missile
    t.px(9, 11, P.BLACK_PAINT[1])
    t.vline(6, 9, 2, P.BLACK_PAINT[1])                       # the hook's cable
    t.px(7, 10, P.BLACK_PAINT[1])


def lying(frm, to):
    """An item-pixel box of the intercontinental missile, lying on the cradles: x stays x, its z becomes height, its
    length (y) runs toward the front, the tail at Z_TAIL."""
    x0, x1 = (frm[0] - 8) * 4, (to[0] - 8) * 4
    y0, y1 = AXIS_Y + (frm[2] - 8) * 4, AXIS_Y + (to[2] - 8) * 4
    z0, z1 = Z_TAIL - (to[1] + L.ICBM_TAIL) * 4, Z_TAIL - (frm[1] + L.ICBM_TAIL) * 4
    return (x0, y0, z0), (x1, y1, z1)


def section_of(name: str) -> int:
    """Which of the crane's first three loads a booster part comes in (the fourth is the warhead's nose)."""
    if name.startswith("nozzle") or name.startswith("fin"):
        return 1
    return L.ICBM_LOAD[name.rsplit("_", 1)[0]]


def hall() -> None:
    g = GeoModel("strategic_assembly_hall", tex_size=(512, 1024))
    g.bone("frame")
    g.bone("crane", pivot=(0, 92, CRANE_Z), parent="frame")
    g.bone("cable", pivot=(0, TROLLEY_Y, CRANE_Z), parent="crane")
    g.bone("hook", pivot=(0, HOOK_TOP, CRANE_Z), parent="crane")
    for k in (1, 2, 3):
        g.bone(f"sec{k}", parent="frame")
    for nose in L.ICBM_NOSES:
        g.bone(f"nose_{nose}", parent="frame")
    g.exclusive = [{f"nose_{nose}" for nose in L.ICBM_NOSES}]
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
    # The missile on the cradles, by the crane's loads; the nose once for each warhead it may carry (the Tsar's is its
    # bomb, wider than the stages).
    missile = []
    for name, frm, to, faces, kind in L.icbm_parts():
        a, b = lying(frm, to)
        g.box(f"m_{name}", a, b, faces=tuple(FACE[f] for f in faces), bone=f"sec{section_of(name)}")
        missile.append((f"m_{name}", kind, "none"))
    for nose in L.ICBM_NOSES:
        for name, frm, to, faces, kind in L.icbm_nose_parts(nose):
            a, b = lying(frm, to)
            g.box(f"n_{nose}_{name}", a, b, faces=tuple(FACE[f] for f in faces), bone=f"nose_{nose}")
            missile.append((f"n_{nose}_{name}", kind, nose))
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)

    def floor(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, CONCRETE[tone(d) + 1])
        if d != "up":
            t.hline(x, y, w, CONCRETE[5])
            return
        t.speckle(x, y, w, h, [CONCRETE[3], CONCRETE[5]], seed=x * 7 + y, density=0.05)
        for u in (36, w - 37):                                 # the bay's yellow lines, down the hall
            t.vline(x + u, y + 6, h - 12, YELLOW[4])
        for row in (y, y + 1, y + h - 2, y + h - 1):           # hazard stripes at the open ends
            for i in range(w):
                t.px(x + i, row, YELLOW[4] if ((i + row) // 3) % 2 == 0 else BLACK[3])
    g.paint_all(tex, "floor", floor)

    def column(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, TOWER[tone(d) + 1])
        if d in ("east", "west") and w > 4:
            t.rect(x + w // 2 - 1, y, 2, h, TOWER[2])           # the I-beam's web between its flanges
        t.hline(x, y, w, TOWER[5])
    for n in columns:
        g.paint_all(tex, n, column)

    def girder(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, YELLOW[tone(d)])
        if d in ("north", "south"):
            t.stripes(x, y, w, h, YELLOW[4], BLACK[2], width=2)
        elif d in ("east", "west") and h > 4:
            t.hline(x, y + h - 1, w, YELLOW[1])                  # the bottom flange's shadow
            for xx in range(x + 3, x + w, 8):
                t.px(xx, y + h // 2, YELLOW[1])                  # bolt heads
    for n in girders:
        g.paint_all(tex, n, girder)
    for n in rails:
        g.paint_all(tex, n, shaded(STEEL))
    for n in cradles:
        g.paint_all(tex, n, lambda t, r, d: (t.rect(*r, GUN[tone(d)]), t.hline(r[0], r[1], r[2], YELLOW[4]) if d == "up" and "pad" in n else None))

    def desk(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, OLIVE[tone(d) + 1])
        if d == "north" and w > 6:
            t.rect(x + 1, y + 1, w - 2, min(3, h - 2), P.BLACK_PAINT.deep)
            for xx in range(x + 1, x + w - 1):
                t.px(xx, y + 2, GREEN[4] if xx % 3 else YELLOW[4])
    g.paint_all(tex, "desk", desk)

    def screen(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, GUN[tone(d)])
        if d == "north" and w > 4:
            t.rect(x + 1, y + 1, w - 2, max(1, h - 2), P.BLACK_PAINT.deep)
            for xx in range(x + 1, x + w - 1):
                t.px(xx, y + 1 + (xx % 2), GREEN[4])
    g.paint_all(tex, "screen", screen)
    def port_plate(edge, glyph):
        def fn(t: Tex, r, d):
            x, y, w, h = r
            t.rect(x, y, w, h, GUN[tone(d)])
            if d != "up":
                return
            t.outline(x, y, w, h, edge)                          # a painted border round the hatch
            t.outline(x + 1, y + 1, w - 2, h - 2, BLACK[2])
            glyph(t, x + w // 2, y + h // 2)
        return fn

    def bolt(t: Tex, cx, cy):                                    # the Energy Port's lightning bolt
        for i, (dx, dy) in enumerate(((1, -3), (0, -2), (-1, -1), (0, -1), (1, -1), (0, 0), (-1, 1), (-2, 2))):
            t.px(cx + dx, cy + dy, YELLOW[4])

    def grate(t: Tex, cx, cy):                                   # the Item Port's grated hatch
        for yy in range(cy - 4, cy + 5, 2):
            t.hline(cx - 4, yy, 9, STEEL[4])
    g.paint_all(tex, "energy_port", port_plate(RED[3], bolt))
    g.paint_all(tex, "item_port", port_plate(YELLOW[3], grate))
    g.paint_all(tex, "pallet", shaded(P.WOOD) if hasattr(P, "WOOD") else shaded(OLIVE))
    g.paint_all(tex, "crate_a", shaded(OLIVE))
    g.paint_all(tex, "crate_b", shaded(OLIVE))
    for n in ("truck_w", "truck_e"):
        g.paint_all(tex, n, lambda t, r, d: (t.rect(*r, YELLOW[tone(d)]), t.stripes(r[0], r[1], r[2], r[3], YELLOW[4], BLACK[2], width=2)
                                             if d in ("north", "south") else None))
    for n in ("bridge_n", "bridge_s"):
        g.paint_all(tex, n, lambda t, r, d: (t.rect(*r, YELLOW[tone(d)]), t.hline(r[0], r[1] + r[3] - 1, r[2], YELLOW[1]) if d in sides else None))
    g.paint_all(tex, "trolley", shaded(GUN))
    g.paint_all(tex, "drum", shaded(STEEL))
    for n in ("cable_w", "cable_e"):
        g.paint_all(tex, n, lambda t, r, d: t.rect(*r, BLACK[1]))
    g.paint_all(tex, "hook_block", lambda t, r, d: (t.rect(*r, YELLOW[tone(d)]), t.stripes(*r, YELLOW[4], BLACK[2], width=1) if d in sides else None))
    g.paint_all(tex, "hook", shaded(STEEL))
    glow = Tex(g.tex_w, g.tex_h, L.CLEAR)                    # the Tsar's vents (the renderer draws it lit)
    for name, kind, nose in missile:
        g.paint_all(tex, name, L.icbm_paint(kind, nose, lying=True))
        if kind == "tsar_vent":
            g.paint_all(glow, name, L.tsar_vent_glow(lying=True))
    g.save(tex, glow)
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


def main() -> None:
    ST.part_block("crane_rail", crane_rail(), cutout=False)
    ST.controller("strategic_assembly_hall", ST.controller_front(hall_placard), ST.controller_top(), ST.casing())
    hall()
    print("strategic written")


if __name__ == "__main__":
    main()
