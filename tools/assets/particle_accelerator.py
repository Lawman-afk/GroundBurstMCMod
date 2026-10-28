"""Particle Accelerator, formed (GeckoLib model + animation), DESIGN.md §8 / §23.3. A two-layer octagonal ring 9x9
blocks across, drawn from its controller at the front centre of the upper layer: in the north-facing frame (origin =
controller block bottom centre) the ring spans x -72..72, z -8..136, y -16..16.

The ring follows its blocks, so everything you can see is a block you can click. The structure is a stepped octagon
of 32 cells per layer (3 straight cells on each side, a 5-cell staircase at each corner); the beam line is a true
octagon through it: straight runs along the side cells' centre lines and 45-degree runs through the staircases. The
plinth is a true octagonal ring 16 px wide all the way round: its sloped corner faces cut across the staircase cells
(whose formed blocks are cut to the same slope, see MultiblockPartBlock.CUT) and poke at most 3.3 px past the stair
corners.

Look: a steel-plated octagonal plinth with hazard edging (bottom layer). On it the beam line: a steel beam pipe, a
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

import numpy as np  # noqa: E402

from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.tex import Tex  # noqa: E402

NAME = "particle_accelerator"
CTRL_COL = 4
BEAM_Y = 7.0
# Octagon corners of the beam line, clockwise seen from above starting at the front-west corner.
V = [(-24.0, 0.0), (24.0, 0.0), (64.0, 40.0), (64.0, 88.0), (24.0, 128.0), (-24.0, 128.0), (-64.0, 88.0), (-64.0, 40.0)]
SPECIAL = {(4, 0): "controller", (5, 0): "injector", (4, 8): "target", (0, 4): "energy", (8, 4): "items"}
# Magnet widths: the diagonal band the beam line may use is 11.3 px wide, so nothing on the line is wider than 11.
DIPOLE_L, DIPOLE_W = 18.0, 11.0
BEND_L, BEND_W = 9.0, 11.0
QUAD_L, QUAD_W = 6.0, 11.0
# The diagonal plinth pieces sit this much lower than the straight ones, so where they overlap at the corners their
# tops are not in one plane.
DIAG_TOP = -0.2
# How far each plinth piece runs past the beam line's corner so their outer edges meet in a sharp octagon corner:
# 8 px (half the ring width) x tan(22.5 degrees).
MITRE = 8.0 * math.tan(math.radians(22.5))


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

    # --- plinth (bottom layer): an octagonal ring 16 px wide, one slab per side of the octagon -----------------------
    for i in range(8):
        a, b = V[i], V[(i + 1) % 8]
        mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        n = math.hypot(b[0] - a[0], b[1] - a[1])
        diagonal = abs(heading(a, b) % 90) > 1
        top, bottom = (DIAG_TOP, -16 - DIAG_TOP) if diagonal else (0.0, -16.0)
        oriented("plinth", f"plinth{i}", mid, n + 2 * MITRE, 16.0, bottom, top, heading(a, b), share=False)

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
        oriented("bend", f"bend{i}", v, BEND_L, BEND_W, DIAG_TOP, 13, ang)
        oriented("bend_coil", f"bend{i}_coil", v, BEND_L + 1.0, BEND_W - 3, 13, 14, ang)

    # --- dipoles on the diagonals, quadrupoles on the straight sides, flanges where the pipe shows --------------------
    flanges = []
    for i in range(8):
        a, b = V[i], V[(i + 1) % 8]
        n = math.hypot(b[0] - a[0], b[1] - a[1])
        ang = heading(a, b)
        if abs(ang % 90) > 1:
            for k, dist in enumerate((17.0, n - 17.0)):
                oriented("dipole", f"dip{i}_{k}", along(a, b, dist), DIPOLE_L - 4, DIPOLE_W - 1, DIAG_TOP, 12.5, ang)
                for s in (-1, 1):
                    oriented("coil", f"dip{i}_{k}_coil{s}", along(a, b, dist + s * (DIPOLE_L / 2 - 1)), 2.0, DIPOLE_W, DIAG_TOP, 13, ang)
            flanges.append(along(a, b, n / 2) + (ang,))
        else:
            for k, dist in enumerate((11.5, n - 11.5)):
                c = along(a, b, dist)
                if SPECIAL.get(cell_of(*c)) == "injector":
                    continue                                   # the injector takes this cell
                oriented("quad", f"quad{i}_{k}", c, QUAD_L, QUAD_W, 0, 13, ang)
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
    paint(g, tex, glow, parts)
    return g, tex, glow


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The accelerator's surfaces, in model space (tools/gb/materials.py; the remaster after slice 9b): a dark steel
    plinth plated in block-long sections, a tread-plate top and a hazard band; a polished beam pipe; red dipoles and
    bending magnets, yellow quadrupoles, copper coils wound in turns; the special cells' cabinets and sockets; the
    glowing source, window and bunch as they were."""
    S = M.Surfacer(g, seed=131)
    gun = P.GUNMETAL
    names = lambda key: [b.name for b in parts.get(key, [])]                      # noqa: E731
    dark = M.painted(M.ARMOUR_RAMP, tone=0.48, wear=0.2, rust=0.05)

    S.paint(tex, names("plinth"), dark)

    def plinth(f, img):
        if f.d == "up":                                                        # tread plate
            a, b = f.plane_uv()
            bar = ((np.floor(a) % 4) == 1) & ((np.floor(b + 2 * (np.floor(a / 4) % 2)) % 4) == 1)
            M.paste(img, S.material_on(f, M.steel(tone=0.55, brushed=0.03)), np.ones(f.shape, bool))
            M.lighten(img, bar, 1.28, 8)
            M.darken(img, np.roll(bar, 1, axis=0) & ~bar, 0.7)
            r = np.minimum(np.minimum(f.border["top"], f.border["bottom"]), np.minimum(f.border["left"], f.border["right"]))
            M.darken(img, r < 1, 0.7)
        elif f.vertical:
            M.seams(f, img, every=16, axis="u", offset=15)                        # plate seams
            M.paste(img, S.material_on(f, M.stripes(width=2, wear=0.3)), f.border["top"] < 2)
    S.overlay(tex, names("plinth"), plinth)
    S.paint(tex, names("pipe"), M.steel(tone=0.7, brushed=0.08))
    red = M.painted(M.RED_PAINT_RAMP, tone=0.45, wear=0.15)
    S.paint(tex, names("bend") + names("dipole"), red)
    S.paint(tex, names("quad"), M.painted(M.YELLOW_PAINT_RAMP, tone=0.6, wear=0.2))
    copper = M.Material(ramp=M.COPPER_RAMP, tone=0.55, mottle=0.02, grain=0.02, dither=0.4, edge_light=0.16, edge_dark=0.1, ao=0.3,
                        grime=0.0, dust=0.0, brushed=0.05)
    S.paint(tex, names("bend_coil") + names("coil"), copper)
    S.overlay(tex, names("bend_coil"), lambda f, img: M.darken(img, (np.floor(f.plane_uv()[0]) % 2) == 1, 0.72), only=("up",))
    S.overlay(tex, names("coil"), lambda f, img: M.darken(img, (np.floor(f.Y) % 2) == 1, 0.72) if f.vertical else None)
    S.paint(tex, names("flange") + names("pickup") + names("target") + names("item_box") + names("injector"),
            M.painted(M.ARMOUR_RAMP, tone=0.55, wear=0.2))

    S.paint(tex, names("cabinet"), M.painted(M.OLIVE_RAMP, tone=0.55, wear=0.3))

    def cabinet(t, r, d):
        x, y, w, h = r
        if d == "north":
            t.rect(x + 2, y + 2, 6, 4, P.BLACK_PAINT.deep)                 # CRT
            for xx in range(x + 3, x + 7):
                t.px(xx, y + 3 + (xx % 2), P.INDICATOR_GREEN[3])
            t.rect(x + 2, y + 8, 4, 3, P.WHITE_CERAMIC[5])                # dial
            t.px(x + 3, y + 9, P.BLACK_PAINT[1])
            t.stripes(x + 1, y + h - 2, w - 2, 1, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
    g.paint_all(tex, names("cabinet")[0], cabinet)

    def lamps(t, r, d):
        for i, c in enumerate((P.INDICATOR_GREEN.light, P.NUCLEAR_AMBER.light, P.INDICATOR_RED.light)):
            t.px(r[0] + i, r[1], c)
    g.paint_all(tex, names("lamps")[0], lambda t, r, d: (t.rect(*r, P.BLACK_PAINT[2]), lamps(t, r, d)))
    g.paint_all(glow, names("lamps")[0], lamps)

    S.overlay(tex, names("injector"), lambda f, img: M.paste(img, S.material_on(f, M.stripes(width=2, wear=0.3)), f.border["bottom"] < 2),
              only=("north", "south", "east", "west"))
    S.paint(tex, names("inlet"), M.steel(tone=0.55))
    g.paint_all(tex, names("inlet")[0], lambda t, r, d: (t.rect(r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, P.BLACK_PAINT.deep),
                                                          t.rect(r[0] + 2, r[1] + 2, r[2] - 4, r[3] - 4, P.CHERENKOV[3])) if d == "north" else None)

    def source(t, r, d):
        t.rect(*r, P.CHERENKOV[4])
        t.rect(r[0] + 1, r[1] + 1, max(1, r[2] - 2), max(1, r[3] - 2), P.CHERENKOV.shine)
    g.paint_all(tex, names("source")[0], source)
    g.paint_all(glow, names("source")[0], source)

    def window(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.ANTIMATTER[4])
        t.rect(x + 2, y + 2, w - 4, h - 4, P.ANTIMATTER[6])
    g.paint_all(tex, names("window")[0], window)
    g.paint_all(glow, names("window")[0], window)

    def socket(face, ramp):
        band = M.Striped(ramp=ramp, tone=0.6, dither=0.4, wear=0.3, under=M.STEEL_RAMP[2:7], width=2,
                         other=M.painted(M.BLACK_RAMP, tone=0.5, wear=0.3))

        def fn(f, img):
            r = np.minimum(np.minimum(f.border["top"], f.border["bottom"]), np.minimum(f.border["left"], f.border["right"]))
            M.paste(img, S.material_on(f, band), r < 3)
            img[r >= 3, :3] = P.BLACK_PAINT.deep[:3]
            M.darken(img, r == 3, 0.5)
            M.lighten(img, (r >= 3) & ((f.border["bottom"] == 3) | (f.border["right"] == 3)), 1.0, 24)
        return fn
    S.paint(tex, names("terminal"), M.painted(M.RED_PAINT_RAMP, tone=0.45, wear=0.2))
    S.overlay(tex, names("terminal"), socket("west", M.RED_PAINT_RAMP), only=("west",))
    S.overlay(tex, names("item_box"), socket("east", M.YELLOW_PAINT_RAMP), only=("east",))
    S.paint(tex, names("insulator"), M.white_paint(tone=0.8))
    S.paint(tex, names("insulator"), M.painted(M.RED_PAINT_RAMP, tone=0.6, wear=0.0), only=("up",))
    S.overlay(tex, names("insulator"), lambda f, img: M.darken(img, (np.floor(f.Y * 2) % 2) == 0, 0.82) if f.vertical else None)

    def bunch(t, r, d):
        t.rect(*r, P.ANTIMATTER[5])
        t.rect(r[0] + 1, r[1] + 1, max(1, r[2] - 2), max(1, r[3] - 2), P.ANTIMATTER.shine)
    for b in names("bunch"):
        g.paint_all(tex, b, bunch)
        g.paint_all(glow, b, bunch)


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
