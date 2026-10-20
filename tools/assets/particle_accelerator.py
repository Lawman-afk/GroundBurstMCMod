"""Particle Accelerator, formed (GeckoLib model + animation), DESIGN.md §8 / §23.3. A two-layer octagonal ring 9x9
blocks across, drawn from its controller at the front centre of the upper layer: in the north-facing frame (origin =
controller block bottom centre) the ring spans x -72..72, z -8..136, y -16..16.

The ring follows its blocks exactly, so everything you can see is a block you can click. The structure is a stepped
octagon of 32 cells per layer (3 straight cells on each side, a 5-cell staircase at each corner); the beam line is a
true octagon through it: straight runs along the side cells' centre lines and 45-degree runs through the staircases,
which stay inside the staircase cells because the line keeps to the band where those cells overlap.

Look: a steel-plated stepped plinth with hazard edging (bottom layer). On it the beam line: a steel beam pipe, a
red bending magnet at each of the eight corners (turned to the bisector), two long red dipoles with copper coil ends on
every diagonal, short yellow focusing quadrupoles on the straight sides, flanges where the pipe shows. The control
cabinet (green CRT) stands in front of the beam pipe at the front centre, with a beam pickup on the pipe behind it; the
injector (blue source glow) sits beside it; the target chamber (violet window) at the back centre; the energy terminal
(red-hatched socket, ceramic insulators) in the middle of the west side and the item hatch (yellow-hatched) in the
middle of the east side. While running a glowing bunch (bone "bunch") laps the ring once a second, flashing where the
pipe is bare.
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.tex import Tex  # noqa: E402

NAME = "particle_accelerator"
CTRL_COL = 4
BEAM_Y = 7.0
# Octagon corners of the beam line, clockwise seen from above starting at the front-west corner.
V = [(-24.0, 0.0), (24.0, 0.0), (64.0, 40.0), (64.0, 88.0), (24.0, 128.0), (-24.0, 128.0), (-64.0, 88.0), (-64.0, 40.0)]
# Pattern cells per row (front row first): the stepped octagon. Mirrors GZMultiblocks.PARTICLE_ACCELERATOR.
ROWS = [(2, 3, 4, 5, 6), (1, 2, 6, 7), (0, 1, 7, 8), (0, 8), (0, 8), (0, 8), (0, 1, 7, 8), (1, 2, 6, 7), (2, 3, 4, 5, 6)]
SPECIAL = {(4, 0): "controller", (5, 0): "injector", (4, 8): "target", (0, 4): "energy", (8, 4): "items"}
# Magnet widths: the diagonal band the beam line may use is 11.3 px wide, so nothing on the line is wider than 11.
DIPOLE_L, DIPOLE_W = 18.0, 11.0
BEND_L, BEND_W = 9.0, 11.0
QUAD_L, QUAD_W = 6.0, 11.0


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def cell_box(col: int, row: int) -> tuple[float, float, float, float]:
    x0 = (col - CTRL_COL) * 16.0 - 8
    z0 = row * 16.0 - 8
    return x0, x0 + 16, z0, z0 + 16


def cell_of(x: float, z: float) -> tuple[int, int]:
    return int(math.floor((x + 8) / 16)) + CTRL_COL, int(math.floor((z + 8) / 16))


def along(a, b, dist):
    """The point {dist} px from a toward b."""
    (ax, az), (bx, bz) = a, b
    n = math.hypot(bx - ax, bz - az)
    return ax + (bx - ax) * dist / n, az + (bz - az) * dist / n


def heading(a, b) -> float:
    """Direction from a to b in degrees, measured from +x (east) toward +z (south)."""
    return math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(256, 512))
    g.bone("frame")
    g.bone("bunch", pivot=(0, BEAM_Y, 0), parent="frame")
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

    def oriented(group, name, centre, length, width, y0, y1, angle, faces=None, share=True):
        """A box centred on {centre} with its length along {angle} (degrees from +x toward +z)."""
        cx, cz = centre
        frm, to = (cx - length / 2, y0, cz - width / 2), (cx + length / 2, y1, cz + width / 2)
        a = ((angle + 90) % 180) - 90                     # the same box either way round
        rot = None if abs(a) < 1e-6 else {"origin": (cx, y0, cz), "axis": "y", "angle": -a}
        kw = {"rotation": rot, "bone": F}
        if faces:
            kw["faces"] = faces
        box = g.box(name, frm, to, **kw)
        return shared(group, box) if share else add(group, box)

    # --- plinth (bottom layer): the stepped octagon of steel plating, contiguous cells in a row merged ---------------
    for row, cols in enumerate(ROWS):
        runs, start = [], cols[0]
        for a, b in zip(cols, cols[1:] + (None,)):
            if b != a + 1:
                runs.append((start, a))
                start = b
        for c0, c1 in runs:
            x0, _, z0, z1 = cell_box(c0, row)
            x1 = cell_box(c1, row)[1]
            add("plinth", g.box(f"plinth_{row}_{c0}", (x0, -16, z0), (x1, 0, z1), bone=F))

    # --- beam pipe: one piece per side of the octagon (the corner magnets hide the joints) ---------------------------
    p = 1.5
    for i in range(8):
        a, b = V[i], V[(i + 1) % 8]
        mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        # 2 px short at each end: the pieces stop inside the corner magnet instead of crossing each other there.
        oriented("pipe", f"pipe{i}", mid, math.hypot(b[0] - a[0], b[1] - a[1]) - 4, 2 * p, BEAM_Y - p, BEAM_Y + p, heading(a, b),
                 faces=("north", "south", "up", "down"), share=False)

    # --- bending magnets at the corners, turned to the bisector of the two sides -------------------------------------
    for i, v in enumerate(V):
        h0, h1 = heading(V[i - 1], v), heading(v, V[(i + 1) % 8])
        if h1 - h0 > 180:
            h1 -= 360
        elif h0 - h1 > 180:
            h1 += 360
        ang = (h0 + h1) / 2
        oriented("bend", f"bend{i}", v, BEND_L, BEND_W, 1, 13, ang)
        oriented("bend_coil", f"bend{i}_coil", v, BEND_L + 1.0, BEND_W - 3, 13, 14, ang)

    # --- dipoles on the diagonals, quadrupoles on the straight sides, flanges where the pipe shows --------------------
    flanges = []
    for i in range(8):
        a, b = V[i], V[(i + 1) % 8]
        n = math.hypot(b[0] - a[0], b[1] - a[1])
        ang = heading(a, b)
        if abs(ang % 90) > 1:
            for k, dist in enumerate((17.0, n - 17.0)):
                oriented("dipole", f"dip{i}_{k}", along(a, b, dist), DIPOLE_L - 4, DIPOLE_W - 1, 1.5, 12.5, ang)
                for s in (-1, 1):
                    oriented("coil", f"dip{i}_{k}_coil{s}", along(a, b, dist + s * (DIPOLE_L / 2 - 1)), 2.0, DIPOLE_W, 1, 13, ang)
            flanges.append(along(a, b, n / 2) + (ang,))
        else:
            for k, dist in enumerate((11.5, n - 11.5)):
                c = along(a, b, dist)
                if SPECIAL.get(cell_of(*c)) == "injector":
                    continue                                   # the injector takes this cell
                oriented("quad", f"quad{i}_{k}", c, QUAD_L, QUAD_W, 1, 13, ang)
                flanges.append(along(a, b, dist + (QUAD_L / 2 + 2.0) * (1 if k == 0 else -1)) + (ang,))
    for j, (fx, fz, ang) in enumerate(flanges):
        oriented("flange", f"flange{j}", (fx, fz), 1.0, 5.0, BEAM_Y - 2.5, BEAM_Y + 2.5, ang)

    # --- the special cells -------------------------------------------------------------------------------------------
    for (col, row), kind in SPECIAL.items():
        x0, x1, z0, z1 = cell_box(col, row)
        cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
        if kind == "controller":
            add("cabinet", g.box("cabinet", (cx - 7, 0, z0 + 0.5), (cx + 7, 14, cz - 3.5), bone=F))
            add("pickup", g.box("pickup", (cx - 2, BEAM_Y - 2.5, cz - 2.5), (cx + 2, BEAM_Y + 2.5, cz + 2.5), bone=F))
            add("lamps", g.box("lamps", (cx + 2.5, 10.5, z0 + 0.3), (cx + 6, 12, z0 + 0.5), faces=("north",), shade=False, bone=F))
        elif kind == "injector":
            add("injector", g.box("injector", (cx - 6.5, 0, cz - 5.5), (cx + 6.5, 12, cz + 5.5), bone=F))
            add("inlet", g.box("inlet", (cx - 3, 3, z0 + 0.5), (cx + 3, 10, cz - 5.5), faces=("north", "east", "west", "up", "down"), bone=F))
            add("source", g.box("source", (cx - 3, 12, cz - 3), (cx + 3, 15, cz + 3), shade=False, bone=F))
        elif kind == "target":
            add("target", g.box("target", (cx - 7, 0, cz - 6), (cx + 7, 14, z1 - 0.5), bone=F))
            add("window", g.box("window", (cx - 4, 4, z1 - 0.5), (cx + 4, 11, z1 - 0.3), faces=("south",), shade=False, bone=F))
        elif kind == "energy":
            add("terminal", g.box("terminal", (x0 + 0.5, 0, cz - 7), (cx + 6, 14, cz + 7), bone=F))
            for i, dz in enumerate((-4.0, 0.0, 4.0)):
                add("insulator", g.box(f"insulator{i}", (cx - 1, 14, cz + dz - 1), (cx + 1, 16, cz + dz + 1),
                                       faces=("north", "south", "east", "west", "up"), bone=F))
        elif kind == "items":
            add("item_box", g.box("item_box", (cx - 6, 0, cz - 7), (x1 - 0.5, 14, cz + 7), bone=F))

    # --- the bunch -----------------------------------------------------------------------------------------------------
    add("bunch", g.box("bunch_head", (-1.8, BEAM_Y - 1.8, -1.8), (1.8, BEAM_Y + 1.8, 1.8), shade=False, bone="bunch"))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    steel, gun, red = P.STEEL, P.GUNMETAL, P.PRIMER_RED

    def plinth(t, r, d):
        x, y, w, h = r
        if d == "up":
            t.rect(x, y, w, h, steel[3])
            for yy in range(y + 1, y + h, 4):
                for xx in range(x + 1 + (yy // 4) % 2 * 2, x + w, 4):
                    t.px(xx, yy, steel[4])                                 # tread plate
            t.outline(x, y, w, h, steel[2])
        elif d == "down":
            t.rect(x, y, w, h, gun[2])
        else:
            t.rect(x, y, w, h, gun[3])
            for xx in range(x + 15, x + w - 1, 16):
                t.vline(xx, y + 3, h - 3, gun[2])                          # plate seams between blocks
            t.stripes(x, y, w, 2, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
    for b in parts["plinth"]:
        g.paint_all(tex, b, plinth)
    for b in parts["pipe"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d) + 1]), t.hline(r[0], r[1], r[2], steel[6]) if d == "up" else None))

    def yoke(ramp):
        def paint(t, r, d):
            x, y, w, h = r
            t.rect(x, y, w, h, ramp[tone(d)])
            if min(w, h) >= 4:
                t.outline(x, y, w, h, ramp[1])
                t.hline(x + 1, y + 1, w - 2, ramp[5])
        return paint
    for group, ramp in (("bend", red), ("dipole", red), ("quad", P.HAZARD_YELLOW)):
        for b in parts[group]:
            if is_first(group, b):
                g.paint_all(tex, b, yoke(ramp))
    for b in parts["bend_coil"]:
        if is_first("bend_coil", b):
            g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, P.COPPER[tone(d)]),
                                                 [t.vline(xx, r[1], r[3], P.COPPER[2]) for xx in range(r[0], r[0] + r[2], 2)] if d == "up" else None))
    for b in parts["coil"]:
        if is_first("coil", b):
            g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, P.COPPER[tone(d) + 1]),
                                                 [t.hline(r[0], yy, r[2], P.COPPER[2]) for yy in range(r[1] + 1, r[1] + r[3], 2)]
                                                 if d not in ("up", "down") else None))
    for b in parts["flange"]:
        if is_first("flange", b):
            g.paint_all(tex, b, lambda t, r, d: t.rect(*r, gun[tone(d) + 1]))

    def cabinet(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.OLIVE[tone(d)])
        if d in ("up", "down"):
            return
        t.outline(x, y, w, h, P.OLIVE[1])
        if d == "north":
            t.rect(x + 2, y + 2, 6, 4, P.BLACK_PAINT.deep)                 # CRT
            for xx in range(x + 3, x + 7):
                t.px(xx, y + 3 + (xx % 2), P.INDICATOR_GREEN[3])
            t.rect(x + 2, y + 8, 4, 3, P.WHITE_CERAMIC[5])                # dial
            t.px(x + 3, y + 9, P.BLACK_PAINT[1])
            t.stripes(x + 1, y + h - 2, w - 2, 1, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
    g.paint_all(tex, parts["cabinet"][0], cabinet)
    g.paint_all(tex, parts["pickup"][0], lambda t, r, d: (t.rect(*r, gun[tone(d) + 1]), t.outline(*r, gun[1]) if min(r[2], r[3]) >= 4 else None))

    def lamps(t, r, d):
        for i, c in enumerate((P.INDICATOR_GREEN.light, P.NUCLEAR_AMBER.light, P.INDICATOR_RED.light)):
            t.px(r[0] + i, r[1], c)
    g.paint_all(tex, parts["lamps"][0], lambda t, r, d: (t.rect(*r, P.BLACK_PAINT[2]), lamps(t, r, d)))
    g.paint_all(glow, parts["lamps"][0], lamps)

    def injector(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, gun[tone(d)])
        if d != "up" and min(w, h) >= 4:
            t.outline(x, y, w, h, gun[1])
            t.stripes(x, y + h - 2, w, 2, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
    g.paint_all(tex, parts["injector"][0], injector)
    g.paint_all(tex, parts["inlet"][0], lambda t, r, d: (t.rect(*r, steel[tone(d)]),
                                                         (t.rect(r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, P.BLACK_PAINT.deep),
                                                          t.rect(r[0] + 2, r[1] + 2, r[2] - 4, r[3] - 4, P.CHERENKOV[3])) if d == "north" else None))

    def source(t, r, d):
        t.rect(*r, P.CHERENKOV[4])
        t.rect(r[0] + 1, r[1] + 1, max(1, r[2] - 2), max(1, r[3] - 2), P.CHERENKOV.shine)
    g.paint_all(tex, parts["source"][0], source)
    g.paint_all(glow, parts["source"][0], source)
    g.paint_all(tex, parts["target"][0], lambda t, r, d: (t.rect(*r, gun[tone(d)]), t.outline(*r, gun[1]) if min(r[2], r[3]) >= 4 else None))

    def window(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.ANTIMATTER[4])
        t.rect(x + 2, y + 2, w - 4, h - 4, P.ANTIMATTER[6])
    g.paint_all(tex, parts["window"][0], window)
    g.paint_all(glow, parts["window"][0], window)

    def socket_face(t, r, stripe):
        x, y, w, h = r
        t.stripes(x, y, w, h, stripe, P.HAZARD_BLACK.base, width=2)
        t.rect(x + 3, y + 3, w - 6, h - 6, P.BLACK_PAINT.deep)
        t.outline(x + 3, y + 3, w - 6, h - 6, gun[2])

    def terminal(t, r, d):
        t.rect(*r, P.WARNING_RED[tone(d)])
        if d == "west":
            socket_face(t, r, P.WARNING_RED[3])
        elif min(r[2], r[3]) >= 4:
            t.outline(*r, P.WARNING_RED[1])
    g.paint_all(tex, parts["terminal"][0], terminal)
    for b in parts["insulator"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, P.WARNING_RED[4]) if d == "up" else
                    (t.rect(*r, P.WHITE_CERAMIC[tone(d) + 1]), t.hline(r[0], r[1] + 1, r[2], P.WHITE_CERAMIC[2])))

    def item_box(t, r, d):
        t.rect(*r, gun[tone(d)])
        if d == "east":
            socket_face(t, r, P.HAZARD_YELLOW.base)
        elif min(r[2], r[3]) >= 4:
            t.outline(*r, gun[1])
    g.paint_all(tex, parts["item_box"][0], item_box)

    def bunch(t, r, d):
        t.rect(*r, P.ANTIMATTER[5])
        t.rect(r[0] + 1, r[1] + 1, max(1, r[2] - 2), max(1, r[3] - 2), P.ANTIMATTER.shine)
    for b in parts["bunch"]:
        g.paint_all(tex, b, bunch)
        g.paint_all(glow, b, bunch)
    return g, tex, glow


def animations() -> list[Animation]:
    """One lap a second round the octagon, starting at the front centre, at an even speed."""
    path = [(0.0, 0.0)] + V[1:] + [V[0], (0.0, 0.0)]
    total = sum(math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in zip(path, path[1:]))
    keys, dist = {}, 0.0
    for i, pt in enumerate(path):
        if i:
            dist += math.hypot(pt[0] - path[i - 1][0], pt[1] - path[i - 1][1])
        keys[round(dist / total, 4)] = (pt[0], 0.0, pt[1])
    w = Animation("working", 1.0, loop=True)
    w.move("bunch", keys)
    return [w]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
