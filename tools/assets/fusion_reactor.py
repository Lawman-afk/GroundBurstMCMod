"""Fusion Reactor, formed (GeckoLib model + animation), DESIGN.md §7 / §23.3. A three-layer octagonal ring 9 blocks
across round a central solenoid, drawn from its controller at the front centre of the bottom layer: in the north-facing
frame (origin = controller block bottom centre) the ring spans x -72..72, z -8..136, y 0..48. A game abstraction, not
a reactor design.

Like the Particle Accelerator (same footprint), everything follows the blocks: the ring is a stepped octagon of 32
cells a layer (three straight cells on each side, a five-cell staircase at each corner) and the model is a true octagon
through it, 16 px wide, its corner cells cut to the slopes (MultiblockPartBlock.CUT).

Look: a gunmetal plinth ring with hazard edging (bottom layer). On it the vacuum vessel, a polished steel octagonal
tube of D-shaped section, with a viewport in each straight cell (the reactor glass blocks) through which the plasma
shows. Twenty-eight toroidal field coils wrap the vessel: copper D-loops in dark coil cases, four along each straight
side (two flanking the viewport) and three on each diagonal. In the middle stands the central solenoid, a copper-wound column with steel end caps. On the
plinth's outer faces: the control cabinet at the front (the controller), the blue fluid inlet on the east side and the
red energy terminal at the back. While running, the plasma ring glows pink
inside the vessel (bone "plasma") and a bright streak laps the ring twice a second (bone "streak").
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402

NAME = "fusion_reactor"
CLEAR = (0, 0, 0, 0)
PLASMA = fixed("fusion_plasma", "#3a0a2a", "#6a1048", "#a01c6a", "#d8348e", "#f468b4", "#ff9ed2", "#ffe0f0")
FLUID_BLUE = fixed("reactor_fluid_blue", "#0c1a3a", "#12285a", "#1a3a7e", "#2452a6", "#3a6ec8", "#6a98e0", "#a8c8f4")
# Octagon corners of the ring's centre line, clockwise seen from above starting at the front-west corner.
V = [(-24.0, 0.0), (24.0, 0.0), (64.0, 40.0), (64.0, 88.0), (24.0, 128.0), (-24.0, 128.0), (-64.0, 88.0), (-64.0, 40.0)]
SOLENOID = (0.0, 64.0)
PLINTH_TOP = 14.0
# The vessel's rounded section, bottom to top: (width, y0, y1). Each piece reaches 0.5 px into the next so the
# diagonal pieces (set 0.2 px inside the straight ones' planes) never leave a gap.
SECTION = [("skirt", 8.0, PLINTH_TOP, 18.5), ("vessel", 11.0, 18.0, 36.0), ("shoulder", 8.0, 35.5, 40.0), ("cap", 5.0, 39.5, 42.5)]
COIL_TOP, COIL_T, COIL_LEG = 46.0, 4.0, 2.0
PLASMA_Y0, PLASMA_Y1, PLASMA_W = 24.0, 31.0, 4.0
# Diagonal pieces sit this much inside the straight ones' planes, so where they overlap at a corner no two faces share
# a plane.
DIAG = 0.2


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def heading(a, b) -> float:
    """Direction from a to b in degrees, measured from +x (east) toward +z (south)."""
    return math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))


def along(a, b, dist):
    (ax, az), (bx, bz) = a, b
    n = math.hypot(bx - ax, bz - az)
    return ax + (bx - ax) * dist / n, az + (bz - az) * dist / n


def mitre(width: float) -> float:
    """How far a piece of the given width runs past the octagon corner so its outer edges meet the next piece's."""
    return width / 2 * math.tan(math.radians(22.5))


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(256, 512))
    g.bone("frame")
    g.bone("plasma", pivot=(0, PLASMA_Y0, 64), parent="frame")
    g.bone("streak", pivot=(0, PLASMA_Y0, 0), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}
    first: dict[str, object] = {}

    def key(group, box):
        return f"{group}:{tuple(round(b - a, 3) for a, b in zip(box.frm, box.to))}:{sorted(box.faces)}"

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    def shared(group, box):
        """Boxes of one kind, size and face set share the first one's texture."""
        add(group, box)
        k = key(group, box)
        if k in first:
            for d in box.faces:
                g.share(box, d, first[k], d)
        else:
            first[k] = box
        return box

    def is_first(group, box) -> bool:
        return first.get(key(group, box)) is box

    def oriented(group, name, centre, length, width, y0, y1, angle, faces=None, share=True, bone=F, shade=True):
        """A box centred on {centre} with its length along {angle} (degrees from +x toward +z)."""
        cx, cz = centre
        frm, to = (cx - length / 2, y0, cz - width / 2), (cx + length / 2, y1, cz + width / 2)
        a = ((angle + 90) % 180) - 90                     # the same box either way round
        rot = None if abs(a) < 1e-6 else {"origin": (cx, y0, cz), "axis": "y", "angle": -a}
        kw = {"rotation": rot, "bone": bone, "shade": shade}
        if faces:
            kw["faces"] = faces
        box = g.box(name, frm, to, **kw)
        return shared(group, box) if share else add(group, box)

    def ring(group, prefix, width, y0, y1, faces=None, bone=F, shade=True):
        """One piece per side of the octagon, mitred so the outer edges meet in sharp corners."""
        for i in range(8):
            a, b = V[i], V[(i + 1) % 8]
            mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
            n = math.hypot(b[0] - a[0], b[1] - a[1])
            diagonal = abs(heading(a, b) % 90) > 1
            lo, hi = (y0 + DIAG, y1 - DIAG) if diagonal else (y0, y1)
            oriented(group, f"{prefix}{i}", mid, n + 2 * mitre(width), width, lo, hi, heading(a, b), faces=faces,
                     share=False, bone=bone, shade=shade)

    # --- plinth, vessel (body and rounded cap), plasma ---------------------------------------------------------------
    ring("plinth", "plinth", 16.0, 0.0, PLINTH_TOP)
    # The vessel sits on the plinth and its pieces' ends are buried in the next piece; only the belly shows an underside.
    for group, width, y0, y1 in SECTION:
        faces = ("north", "south", "up", "down") if group == "vessel" else ("north", "south") if group == "skirt" else ("north", "south", "up")
        ring(group, group, width, y0, y1, faces=faces)
    ring("plasma", "plasma", PLASMA_W, PLASMA_Y0, PLASMA_Y1, faces=("north", "south", "up", "down"), bone="plasma", shade=False)
    add("streak", g.box("streak_head", (-2.5, PLASMA_Y0 - 0.5, -2.5), (2.5, PLASMA_Y1 + 0.5, 2.5), shade=False, bone="streak"))

    # --- toroidal field coils: four along each straight side (two flanking the viewport), three on each diagonal ----
    coils = []
    for i in range(8):
        a, b = V[i], V[(i + 1) % 8]
        n = math.hypot(b[0] - a[0], b[1] - a[1])
        ang = heading(a, b)
        if abs(ang % 90) > 1:
            coils += [(along(a, b, n * f), ang) for f in (0.25, 0.5, 0.75)]
        else:
            coils += [(along(a, b, n / 2 + s), ang) for s in (-19.0, -5.5, 5.5, 19.0)]
    for j, (c, ang) in enumerate(coils):
        rad = math.radians(ang + 90)                       # across the ring
        for side, off in (("in", -(8 - COIL_LEG / 2)), ("out", 8 - COIL_LEG / 2)):
            cx, cz = c[0] + math.cos(rad) * off, c[1] + math.sin(rad) * off
            oriented("coil_leg", f"coil{j}_{side}", (cx, cz), COIL_T, COIL_LEG, PLINTH_TOP, COIL_TOP - 3, ang,
                     faces=("north", "south", "east", "west"))
        oriented("coil_top", f"coil{j}_top", c, COIL_T, 16.0, COIL_TOP - 3, COIL_TOP, ang)

    # --- central solenoid: a rounded copper column with steel end caps ------------------------------------------------
    sx, sz = SOLENOID
    add("solenoid", g.box("solenoid_x", (sx - 6, 3, sz - 4), (sx + 6, 45, sz + 4), faces=("north", "south", "east", "west"), bone=F))
    add("solenoid", g.box("solenoid_z", (sx - 4, 3, sz - 6), (sx + 4, 45, sz + 6), faces=("north", "south", "east", "west"), bone=F))
    add("sol_cap", g.box("sol_base", (sx - 7, 0, sz - 7), (sx + 7, 3, sz + 7), faces=("north", "south", "east", "west", "up"), bone=F))
    add("sol_cap", g.box("sol_top", (sx - 7, 45, sz - 7), (sx + 7, 48, sz + 7), bone=F))

    # --- the port cells: fittings on the plinth's outer faces ---------------------------------------------------------
    add("cabinet", g.box("cabinet", (-7, 1, -8.6), (7, 12.5, -8), faces=("north", "east", "west", "up", "down"), bone=F))
    lamps = add("lamps", g.box("lamps", (2.5, 9, -8.8), (6, 10.5, -8.6), faces=("north",), shade=False, bone=F))
    add("fluid_inlet", g.box("fluid_inlet", (72, 2, 58), (72.6, 12, 70), faces=("east", "north", "south", "up", "down"), bone=F))
    add("terminal", g.box("terminal", (-7, 2, 136), (7, 12, 136.6), faces=("south", "east", "west", "up", "down"), bone=F))
    for i, x in enumerate((-2.4, 0.0, 2.4)):
        add("insulator", g.box(f"insulator{i}", (x - 0.8, PLINTH_TOP, 133.6), (x + 0.8, PLINTH_TOP + 2.5, 135.2),
                               faces=("north", "south", "east", "west", "up"), bone=F))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    steel, gun = P.STEEL, P.GUNMETAL

    def plinth(t, r, d):
        x, y, w, h = r
        if d == "up":
            t.rect(x, y, w, h, gun[3])
            for yy in range(y + 1, y + h, 4):
                for xx in range(x + 1 + (yy // 4) % 2 * 2, x + w, 4):
                    t.px(xx, yy, gun[4])                                   # tread plate
            t.outline(x, y, w, h, gun[2])
        elif d == "down":
            t.rect(x, y, w, h, gun[2])
        else:
            t.rect(x, y, w, h, gun[3])
            for xx in range(x + 15, x + w - 1, 16):
                t.vline(xx, y + 3, h - 3, gun[2])                          # plate seams
            t.stripes(x, y, w, 2, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
    for b in parts["plinth"]:
        g.paint_all(tex, b, plinth)

    def vessel(window: bool):
        def paint(t, r, d):
            x, y, w, h = r
            t.rect(x, y, w, h, steel[tone(d) + 1])
            if d == "up":
                return
            t.hline(x, y, w, steel[6])                                     # polished top edge
            for xx in range(x + 7, x + w - 1, 8):
                t.vline(xx, y + 1, h - 1, steel[3])                        # panel seams
            if window:
                # The viewport: a framed opening in the middle of the piece, between the two flanking coils.
                wx, ww = x + w // 2 - 4, 8
                wy, wh = y + 5, 10
                t.rect(wx - 1, wy - 1, ww + 2, wh + 2, gun[2])
                t.rect(wx, wy, ww, wh, CLEAR)
                for k in range(3):
                    t.px(wx + 1 + k, wy + wh - 2 - k, (250, 220, 236, 255))  # glint on the glass edge
        return paint
    for b in parts["vessel"]:
        straight = not b.rotation or abs(abs(b.rotation["angle"]) - 90) < 1
        g.paint_all(tex, b, vessel(straight))
    for group in ("skirt", "shoulder", "cap"):
        for b in parts[group]:
            g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d) + (1 if d == "up" else 0)]),
                                                 t.hline(r[0], r[1], r[2], steel[5]) if d != "down" else None))

    def plasma(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, PLASMA[4])
        for xx in range(x, x + w, 3):
            t.px(xx, y + (xx // 3) % max(1, h), PLASMA[6])                 # filaments
    for b in parts["plasma"]:
        g.paint_all(tex, b, plasma)
        g.paint_all(glow, b, plasma)

    def streak(t, r, d):
        t.rect(*r, PLASMA[6])
        t.rect(r[0] + 1, r[1] + 1, max(1, r[2] - 2), max(1, r[3] - 2), (255, 255, 255, 255))
    g.paint_all(tex, parts["streak"][0], streak)
    g.paint_all(glow, parts["streak"][0], streak)

    def coil_leg(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, gun[tone(d)])
        if d in ("east", "west") or w >= 3:
            for yy in range(y + 1, y + h - 1, 2):
                t.hline(x, yy, w, P.COPPER[4] if (yy // 2) % 2 else P.COPPER[3])   # windings showing through the case
            t.vline(x, y, h, gun[1])
            t.vline(x + w - 1, y, h, gun[1])
    for b in parts["coil_leg"]:
        if is_first("coil_leg", b):
            g.paint_all(tex, b, coil_leg)

    def coil_top(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, gun[tone(d) + 1])
        if d == "up":
            t.hline(x, y + h // 2, w, P.CRYO[3])                           # nitrogen line along the top
        elif min(w, h) >= 3:
            t.outline(x, y, w, h, gun[1])
            t.hline(x + 1, y + 1, w - 2, P.COPPER[4])
    for b in parts["coil_top"]:
        if is_first("coil_top", b):
            g.paint_all(tex, b, coil_top)

    def solenoid(t, r, d):
        x, y, w, h = r
        for yy in range(y, y + h):
            t.hline(x, yy, w, P.COPPER[4] if yy % 2 else P.COPPER[3])
        for yy in range(y + 5, y + h, 12):
            t.hline(x, yy, w, steel[4])                                    # clamp bands
        t.vline(x, y, h, P.COPPER[5])
    for b in parts["solenoid"]:
        g.paint_all(tex, b, solenoid)
    for b in parts["sol_cap"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d) + 1]), t.outline(*r, steel[2]) if min(r[2], r[3]) >= 4 else None))

    def cabinet(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.OLIVE[tone(d) + 1])
        if d != "north":
            return
        t.outline(x, y, w, h, P.OLIVE[1])
        t.rect(x + 2, y + 2, 6, 4, P.BLACK_PAINT.deep)                     # CRT
        for xx in range(x + 3, x + 7):
            t.px(xx, y + 3 + (xx % 2), PLASMA[5])
        t.rect(x + 2, y + 7, 4, 3, P.WHITE_CERAMIC[5])                     # ignition dial
        t.px(x + 3, y + 8, P.BLACK_PAINT[1])
        t.stripes(x + 1, y + h - 2, w - 2, 1, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
    g.paint_all(tex, parts["cabinet"][0], cabinet)

    def lamps_paint(t, r, d):
        for i, c in enumerate((P.INDICATOR_GREEN.light, P.NUCLEAR_AMBER.light, PLASMA[5])):
            t.px(r[0] + i, r[1], c)
    g.paint_all(tex, lamps, lambda t, r, d: (t.rect(*r, P.BLACK_PAINT[2]), lamps_paint(t, r, d)))
    g.paint_all(glow, lamps, lamps_paint)

    def socket(face, stripe, ramp):
        def paint(t, r, d):
            x, y, w, h = r
            t.rect(x, y, w, h, ramp[tone(d)])
            if d == face:
                t.stripes(x, y, w, h, stripe, P.HAZARD_BLACK.base, width=2)
                t.rect(x + 3, y + 3, w - 6, h - 6, P.BLACK_PAINT.deep)
                t.outline(x + 3, y + 3, w - 6, h - 6, gun[2])
        return paint
    g.paint_all(tex, parts["fluid_inlet"][0], socket("east", FLUID_BLUE[4], gun))
    g.paint_all(tex, parts["terminal"][0], socket("south", P.WARNING_RED[3], P.WARNING_RED))
    for b in parts["insulator"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, P.WARNING_RED[4]) if d == "up" else
                    (t.rect(*r, P.WHITE_CERAMIC[tone(d) + 1]), t.hline(r[0], r[1] + 1, r[2], P.WHITE_CERAMIC[2])))
    return g, tex, glow


def animations() -> list[Animation]:
    """The streak laps the octagon twice a second at an even speed; the plasma ring breathes."""
    path = [(0.0, 0.0)] + V[1:] + [V[0], (0.0, 0.0)]
    total = sum(math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in zip(path, path[1:]))
    keys, dist = {}, 0.0
    for i, pt in enumerate(path):
        if i:
            dist += math.hypot(pt[0] - path[i - 1][0], pt[1] - path[i - 1][1])
        keys[round(dist / total * 0.5, 4)] = (pt[0], 0.0, pt[1])
    w = Animation("working", 0.5, loop=True)
    w.move("streak", keys)
    return [w]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
