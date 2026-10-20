"""Particle Accelerator, formed (GeckoLib model + animation), DESIGN.md §8 / §23.3. A 9x9x3 hollow ring drawn from its
controller, which sits in the middle layer of the front row, one block west of the injector: in the north-facing frame
(origin = controller block bottom centre) the ring spans x -56..88, z -8..136, y -16..32.

Look: a steel-plated plinth ring with hazard edging; on it the beam line, a square ring of beam pipe threaded through
28 red dipole magnets (copper windings on top, bending magnets at the corners) with short bare lengths of pipe and
flanges between them. The injector (blue source glow), the controller's control cabinet, the violet target chamber
and the energy terminal sit in the ring; an open steel truss (closed at the corners) rides on top. While running, a glowing particle bunch
(bone "bunch") races round the ring once a second, flashing in the gaps between magnets.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.tex import Tex  # noqa: E402

NAME = "particle_accelerator"
CTRL_COL = 3
BEAM_Y = 8.0
X_MIN, X_MAX, Z_MIN, Z_MAX = -56.0, 88.0, -8.0, 136.0
CENTRE_W, CENTRE_E, CENTRE_N, CENTRE_S = -48.0, 80.0, 0.0, 128.0     # ring centre line


def cell_centre(col: int, row: int) -> tuple[float, float]:
    return (col - CTRL_COL) * 16.0, row * 16.0


def ring_cells() -> list[tuple[int, int]]:
    out = []
    for c in range(9):
        out += [(c, 0), (c, 8)]
    for r in range(1, 8):
        out += [(0, r), (8, r)]
    return out


SPECIAL = {(3, 0): "controller", (4, 0): "injector", (4, 8): "target", (0, 4): "energy"}


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(256, 512))
    g.bone("frame")
    g.bone("bunch", pivot=(CENTRE_W, BEAM_Y, CENTRE_N), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    first: dict[str, object] = {}

    def shared(group, box):
        """Boxes of one kind and size share the first one's texture."""
        key = f"{group}:{tuple(round(b - a, 3) for a, b in zip(box.frm, box.to))}:{sorted(box.faces)}"
        add(group, box)
        if key in first:
            for d in box.faces:
                g.share(box, d, first[key], d)
        else:
            first[key] = box
        return box

    # Plinth ring (bottom layer): steel plating.
    add("plinth", g.box("plinth_n", (X_MIN, -16, Z_MIN), (X_MAX, 0, Z_MIN + 16), bone=F))
    add("plinth", g.box("plinth_s", (X_MIN, -16, Z_MAX - 16), (X_MAX, 0, Z_MAX), bone=F))
    add("plinth", g.box("plinth_w", (X_MIN, -16, Z_MIN + 16), (X_MIN + 16, 0, Z_MAX - 16), bone=F))
    add("plinth", g.box("plinth_e", (X_MAX - 16, -16, Z_MIN + 16), (X_MAX, 0, Z_MAX - 16), bone=F))
    # Beam pipe: front and back runs end to end, the side runs fit between them.
    p = 1.5
    add("pipe", g.box("pipe_n", (CENTRE_W - p, BEAM_Y - p, CENTRE_N - p), (CENTRE_E + p, BEAM_Y + p, CENTRE_N + p), bone=F))
    add("pipe", g.box("pipe_s", (CENTRE_W - p, BEAM_Y - p, CENTRE_S - p), (CENTRE_E + p, BEAM_Y + p, CENTRE_S + p), bone=F))
    add("pipe", g.box("pipe_w", (CENTRE_W - p, BEAM_Y - p, CENTRE_N + p), (CENTRE_W + p, BEAM_Y + p, CENTRE_S - p),
                      faces=("east", "west", "up", "down"), bone=F))
    add("pipe", g.box("pipe_e", (CENTRE_E - p, BEAM_Y - p, CENTRE_N + p), (CENTRE_E + p, BEAM_Y + p, CENTRE_S - p),
                      faces=("east", "west", "up", "down"), bone=F))
    # Magnets and the special cells.
    for col, row in ring_cells():
        cx, cz = cell_centre(col, row)
        kind = SPECIAL.get((col, row))
        corner = col in (0, 8) and row in (0, 8)
        along_x = row in (0, 8) and not corner
        if kind is None:
            if corner:
                shared("bend", g.box(f"bend_{col}_{row}", (cx - 6.5, 1, cz - 6.5), (cx + 6.5, 15, cz + 6.5), bone=F))
            elif along_x:
                shared("magnet_x", g.box(f"mag_{col}_{row}", (cx - 6, 2, cz - 6.5), (cx + 6, 14, cz + 6.5), bone=F))
            else:
                shared("magnet_z", g.box(f"mag_{col}_{row}", (cx - 6.5, 2, cz - 6), (cx + 6.5, 14, cz + 6), bone=F))
            # Flanges on the bare pipe either side of each magnet.
            for s in (-1, 1):
                if along_x:
                    shared("flange_x", g.box(f"fl_{col}_{row}_{s}", (cx + s * 7.25 - 0.5, BEAM_Y - 2.5, cz - 2.5), (cx + s * 7.25 + 0.5, BEAM_Y + 2.5, cz + 2.5), bone=F))
                elif not corner:
                    shared("flange_z", g.box(f"fl_{col}_{row}_{s}", (cx - 2.5, BEAM_Y - 2.5, cz + s * 7.25 - 0.5), (cx + 2.5, BEAM_Y + 2.5, cz + s * 7.25 + 0.5), bone=F))
        elif kind == "controller":
            add("cabinet", g.box("cabinet", (cx - 7, 0, cz - 7), (cx + 7, 15, cz + 7), bone=F))
        elif kind == "injector":
            add("injector", g.box("injector", (cx - 7, 0, cz - 7), (cx + 7, 14, cz + 7), bone=F))
            add("source", g.box("source", (cx - 3, 14, cz - 3), (cx + 3, 17, cz + 3), shade=False, bone=F))
        elif kind == "target":
            add("target", g.box("target", (cx - 7, 0, cz - 7), (cx + 7, 15, cz + 7), bone=F))
            add("window", g.box("window", (cx - 4, 4, cz + 7), (cx + 4, 12, cz + 7.3), faces=("south",), shade=False, bone=F))
        elif kind == "energy":
            add("terminal_box", g.box("terminal_box", (cx - 7, 0, cz - 7), (cx + 7, 14, cz + 7), bone=F))
            for i, dz in enumerate((-3.5, 0.0, 3.5)):
                add("insulator", g.box(f"insulator{i}", (cx - 9.5, 6, cz + dz - 1), (cx - 7, 9, cz + dz + 1), faces=("west", "north", "south", "up", "down"), bone=F))
    # Top truss (top layer): an outer and an inner loop of 2x2 chords at the bottom (y 16) and top (y 30) of the layer,
    # closed at every corner; at each cell boundary a post at both loops and a crossbar between them at both levels,
    # so the corner cells are closed squares like the rest. Node start positions along x and z: the outer edges, the
    # inner edges (which are also the corner cells' inner boundaries) and the boundaries between the other cells.
    xs = [X_MIN, X_MIN + 14] + [X_MIN + 16 * k - 1 for k in range(2, 8)] + [X_MAX - 16, X_MAX - 2]
    zs = [Z_MIN, Z_MIN + 14] + [Z_MIN + 16 * k - 1 for k in range(2, 8)] + [Z_MAX - 16, Z_MAX - 2]
    XO0, XI0, XI1, XO1 = xs[0], xs[1], xs[-2], xs[-1]
    ZO0, ZI0, ZI1, ZO1 = zs[0], zs[1], zs[-2], zs[-1]
    along_x = ("north", "south", "up", "down")
    along_z = ("east", "west", "up", "down")

    def run_x(name, x0, x1, z, y0):
        shared("chord", g.box(name, (x0, y0, z), (x1, y0 + 2, z + 2), faces=along_x, bone=F))

    def run_z(name, x, z0, z1, y0):
        shared("chord", g.box(name, (x, y0, z0), (x + 2, y0 + 2, z1), faces=along_z, bone=F))

    for y0 in (16.0, 30.0):
        lv = int(y0)
        # Outer loop: front and back full width, the sides between them.
        run_x(f"ch_on_{lv}", X_MIN, X_MAX, ZO0, y0)
        run_x(f"ch_os_{lv}", X_MIN, X_MAX, ZO1, y0)
        run_z(f"ch_ow_{lv}", XO0, ZO0 + 2, ZO1, y0)
        run_z(f"ch_oe_{lv}", XO1, ZO0 + 2, ZO1, y0)
        # Inner loop: front and back from outer side to outer side, the sides in three pieces around them.
        run_x(f"ch_in_{lv}", XO0 + 2, XO1, ZI0, y0)
        run_x(f"ch_is_{lv}", XO0 + 2, XO1, ZI1, y0)
        for side, x in (("w", XI0), ("e", XI1)):
            run_z(f"ch_i{side}a_{lv}", x, ZO0 + 2, ZI0, y0)
            run_z(f"ch_i{side}b_{lv}", x, ZI0 + 2, ZI1, y0)
            run_z(f"ch_i{side}c_{lv}", x, ZI1 + 2, ZO1, y0)
        # Crossbars at the boundaries between the non-corner cells.
        for k, x in enumerate(xs[2:-2]):
            run_z(f"bar_n{k}_{lv}", x, ZO0 + 2, ZI0, y0)
            run_z(f"bar_s{k}_{lv}", x, ZI1 + 2, ZO1, y0)
        for k, z in enumerate(zs[2:-2]):
            run_x(f"bar_w{k}_{lv}", XO0 + 2, XI0, z, y0)
            run_x(f"bar_e{k}_{lv}", XI1 + 2, XO1, z, y0)
    # Posts at every node of both loops.
    nodes = set()
    for x in xs:
        for z in (ZO0, ZI0, ZI1, ZO1):
            nodes.add((x, z))
    for z in zs:
        for x in (XO0, XI0, XI1, XO1):
            nodes.add((x, z))
    for n, (x, z) in enumerate(sorted(nodes)):
        shared("post", g.box(f"post{n}", (x, 18, z), (x + 2, 30, z + 2), faces=("north", "south", "east", "west"), bone=F))
    # The bunch and its trail.
    add("bunch", g.box("bunch_head", (CENTRE_W - 2, BEAM_Y - 2, CENTRE_N - 2), (CENTRE_W + 2, BEAM_Y + 2, CENTRE_N + 2), shade=False, bone="bunch"))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    steel, gun = P.STEEL, P.GUNMETAL

    def plating(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, steel[tone(d)])
        if d == "up":
            for yy in range(y + 2, y + h - 1, 4):
                for xx in range(x + 2 + (yy // 4) % 2 * 2, x + w - 1, 4):
                    t.px(xx, yy, steel[5])
            return
        t.stripes(x, y, w, 2, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
        for xx in range(x + 7, x + w - 1, 16):
            t.vline(xx, y + 2, h - 2, steel[2])                             # plate seams
    for b in parts["plinth"]:
        g.paint_all(tex, b, plating)
    for b in parts["pipe"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d) + 1]), t.hline(r[0], r[1], r[2], steel[6]) if d != "up" and r[3] > 1 else None))

    def magnet(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.PRIMER_RED[tone(d)])
        t.outline(x, y, w, h, P.PRIMER_RED[1])
        if d == "up":
            for xx in range(x + 2, x + w - 2, 2):
                t.vline(xx, y + 2, h - 4, P.COPPER[4] if xx % 4 else P.COPPER[3])   # windings
        elif h >= 8:
            t.rect(x + w // 2 - 2, y + h // 2 - 2, 4, 4, P.BLACK_PAINT[1])         # where the pipe enters
    for key in ("magnet_x", "magnet_z", "bend"):
        for b in parts.get(key, []):
            if first.get(f"{key}:{tuple(round(bb - aa, 3) for aa, bb in zip(b.frm, b.to))}:{sorted(b.faces)}") is b:
                g.paint_all(tex, b, magnet)
    for key in ("flange_x", "flange_z"):
        for b in parts.get(key, []):
            if first.get(f"{key}:{tuple(round(bb - aa, 3) for aa, bb in zip(b.frm, b.to))}:{sorted(b.faces)}") is b:
                g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d) + 1]), t.px(r[0], r[1], P.CHERENKOV[4])))

    def cabinet(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.OLIVE[tone(d)])
        if d == "north":
            t.outline(x, y, w, h, P.OLIVE[1])
            t.rect(x + 2, y + 2, 7, 5, P.BLACK_PAINT.deep)
            for xx in range(x + 3, x + 8):
                t.px(xx, y + 4 + (xx % 2), P.INDICATOR_GREEN[3])
            t.rect(x + 2, y + 9, 4, 3, P.BLACK_PAINT[1])
            t.outline(x + 2, y + 9, 4, 3, P.CHERENKOV[3])
            t.stripes(x + 1, y + h - 2, w - 2, 1, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
    g.paint_all(tex, parts["cabinet"][0], cabinet)

    def injector(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, gun[tone(d) + 1])
        t.outline(x, y, w, h, gun[1])
        if d == "north":
            t.stripes(x + 1, y + h - 3, w - 2, 2, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
            t.rect(x + w // 2 - 2, y + 2, 4, 4, P.CHERENKOV[3])
    g.paint_all(tex, parts["injector"][0], injector)

    def source(t, r, d):
        t.rect(*r, P.CHERENKOV[5])
        if d == "up":
            t.rect(r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, P.CHERENKOV.shine)
    g.paint_all(tex, parts["source"][0], source)
    g.paint_all(glow, parts["source"][0], source)

    def target(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, gun[tone(d)])
        t.outline(x, y, w, h, gun[1])
        if d == "up":
            t.rect(x + 3, y + 3, w - 6, h - 6, P.ANTIMATTER[2])
            t.outline(x + 3, y + 3, w - 6, h - 6, steel[5])
    g.paint_all(tex, parts["target"][0], target)

    def window(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.ANTIMATTER[4])
        t.rect(x + 2, y + 2, w - 4, h - 4, P.ANTIMATTER[6])
    g.paint_all(tex, parts["window"][0], window)
    g.paint_all(glow, parts["window"][0], window)
    g.paint_all(tex, parts["terminal_box"][0], lambda t, r, d: (t.rect(*r, P.WARNING_RED[tone(d)]), t.outline(*r, P.WARNING_RED[1]) if min(r[2], r[3]) >= 4 else None))
    for b in parts["insulator"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, P.WHITE_CERAMIC[tone(d) + 1]),
                                             [t.vline(xx, r[1], r[3], P.WHITE_CERAMIC[2]) for xx in range(r[0] + 1, r[0] + r[2], 2)] if d in ("up", "down", "north", "south") else None))
    for b in parts["chord"]:
        if b is first.get(f"chord:{tuple(round(bb - aa, 3) for aa, bb in zip(b.frm, b.to))}:{sorted(b.faces)}"):
            g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d)]), t.hline(r[0], r[1], r[2], steel[5]) if d in ("north", "south", "east", "west") else None))
    for b in parts["post"]:
        if b is first.get(f"post:{tuple(round(bb - aa, 3) for aa, bb in zip(b.frm, b.to))}:{sorted(b.faces)}"):
            g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[3]), t.vline(r[0], r[1], r[3], steel[5])))

    def bunch(t, r, d):
        t.rect(*r, P.ANTIMATTER[5])
        t.rect(r[0] + 1, r[1] + 1, max(1, r[2] - 2), max(1, r[3] - 2), P.ANTIMATTER.shine)
    for b in parts["bunch"]:
        g.paint_all(tex, b, bunch)
        g.paint_all(glow, b, bunch)
    return g, tex, glow


def animations() -> list[Animation]:
    """One lap a second: along the front, down the east side, back along the rear, up the west side."""
    w = Animation("working", 1.0, loop=True)
    dx, dz = CENTRE_E - CENTRE_W, CENTRE_S - CENTRE_N
    w.move("bunch", {0: (0, 0, 0), 0.25: (dx, 0, 0), 0.5: (dx, 0, dz), 0.75: (0, 0, dz), 1.0: (0, 0, 0)})
    return [w]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
