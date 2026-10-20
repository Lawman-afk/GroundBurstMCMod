"""Singularity Reactor, formed (GeckoLib model + animation), DESIGN.md §7 / §23.3. A 5x5x5 cage drawn from its
controller in the middle of the front of the floor: in the north-facing frame (origin = controller block bottom centre)
it spans x -40..40, z -8..72, y 0..80. A game abstraction.

Look: a gunmetal floor slab with an olive deck and a hazard ring round the chamber, steel corner posts, and leaded
glass walls in a steel grid (rails top and bottom, mullions and transoms on the block lines) so the chamber shows
through. A containment emitter sits in the middle of each wall and in the centre of the roof slab, a dark housing with a
violet lens facing the core. In the middle of the empty chamber the core cradle: two brass gimbal rings round the
core. The seed rests in the cradle while it is in its slot (bone "core", a black sphere with a violet photon ring);
while the cage holds it violet beams run from the five lenses to it (bone "beams"), and while it has matter to eat an
accretion disk spins round it in three sizes ("disk0".."disk2", one shown at a time). On the floor's outer faces: the control cabinet at the front (the controller), the yellow item hatch
on the west side and the red energy terminal on the east side; any other outer floor cell that is an Item Port shows a
yellow hatch too (bones "hatch_<col>_<row>").
"""
from __future__ import annotations

import sys
import math
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402

NAME = "singularity_reactor"
CLEAR = (0, 0, 0, 0)
GLASS = fixed("lead_glass", "#141a1e", "#1e262c", "#2a343c", "#36424c", "#4a5864", "#6a7a88", "#a4b4c0")
ACCRETION = fixed("accretion", "#2a0a3a", "#4a1466", "#7a2cff", "#b066ff", "#ff9a5c", "#ffc890", "#fff0e0")
CX, CY, CZ = 0.0, 40.0, 32.0            # the chamber's centre (the core)
FLOOR, ROOF = 16.0, 64.0
LENS_OUT = 0.5                           # how far each lens stands proud of its cell, into the chamber
EMIT_Y = 35.5                            # the wall emitters' lens height (low in their cell, on the lower transom)
DISKS = [3.6, 4.6, 5.5]                  # accretion disk half-widths, small to large (inside the gimbal rings)


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


# The four walls: wall plane centre, the along-wall axis and the inward direction. u runs along the wall (-24..24 is
# the three middle cells), d is depth from the wall's centre plane, positive inward.
WALLS = {
    "front": lambda u, d: (u, CZ - 32 + d),
    "back": lambda u, d: (-u, CZ + 32 - d),
    "west": lambda u, d: (-32 + d, CZ + u),
    "east": lambda u, d: (32 - d, CZ - u),
}
INWARD = {"front": "south", "back": "north", "west": "east", "east": "west"}


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(256, 512))
    g.bone("frame")
    g.bone("core", pivot=(CX, CY, CZ), parent="frame")
    g.bone("beams", pivot=(CX, CY, CZ), parent="frame")
    for i in range(3):
        g.bone(f"disk{i}", pivot=(CX, CY, CZ), parent="frame")
    g.exclusive = [{f"disk{i}" for i in range(3)}]
    F = "frame"
    parts: dict[str, list] = {}
    first: dict[str, object] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    def shared(group, box):
        """Boxes of one kind and size share the first one's texture."""
        add(group, box)
        k = f"{group}:{tuple(round(b - a, 3) for a, b in zip(box.frm, box.to))}:{sorted(box.faces)}"
        if k in first:
            for f in box.faces:
                g.share(box, f, first[k], f)
        else:
            first[k] = box
        return box

    def beam(w):
        """A thin beam from the wall's lens up to the core: a box along the inward direction, tilted about the wall's
        own axis. Its ends rest on the lens and inside the core, so only its four long faces are drawn."""
        start = 8 + LENS_OUT
        run, rise = 32 - start, CY - EMIT_Y
        length = math.hypot(run, rise) - 2.0
        tilt = math.degrees(math.atan2(rise, run))
        (ax, az), (bx, bz) = WALLS[w](-0.35, start), WALLS[w](0.35, start + length)
        ox, oz = WALLS[w](0, start)
        frm, to = (min(ax, bx), EMIT_Y - 0.35, min(az, bz)), (max(ax, bx), EMIT_Y + 0.35, max(az, bz))
        # Which way the tilt turns depends on which way the wall faces.
        axis, sign = {"front": ("x", -1), "back": ("x", 1), "west": ("z", 1), "east": ("z", -1)}[w]
        across = ("east", "west") if axis == "x" else ("north", "south")
        add("beam", g.box(f"beam_{w}", frm, to, faces=(*across, "up", "down"), shade=False, bone="beams",
                          rotation={"origin": (ox, EMIT_Y, oz), "axis": axis, "angle": sign * tilt}))

    def wall_box(group, name, wall, u0, u1, y0, y1, d0, d1, faces=None, bone=F, shade=True, share=True):
        (ax, az), (bx, bz) = WALLS[wall](u0, d0), WALLS[wall](u1, d1)
        frm, to = (min(ax, bx), y0, min(az, bz)), (max(ax, bx), y1, max(az, bz))
        kw = {"bone": bone, "shade": shade}
        if faces:
            kw["faces"] = faces
        box = g.box(name, frm, to, **kw)
        return shared(group, box) if share else add(group, box)

    # --- floor, roof, corner posts -----------------------------------------------------------------------------------
    add("floor", g.box("floor", (-40, 0, -8), (40, FLOOR, 72), bone=F))
    add("roof", g.box("roof", (-40, ROOF, -8), (40, ROOF + 16, 72), bone=F))
    for i, (x, z) in enumerate(((-40, -8), (28, -8), (-40, 60), (28, 60))):
        shared("post", g.box(f"post{i}", (x, FLOOR, z), (x + 12, ROOF, z + 12), faces=("north", "south", "east", "west"), bone=F))

    # --- walls: glass in a steel grid, an emitter in the middle of each --------------------------------------------
    sides = ("north", "south", "east", "west")
    for w in WALLS:
        wall_box("pane", f"pane_{w}", w, -28, 28, FLOOR + 2, ROOF - 2, -0.1, 0.1, faces=(INWARD[w],), share=False)
        # Rails sit on the floor and under the roof: no face against either.
        for k, (y, cap) in enumerate(((FLOOR, "up"), (ROOF - 2, "down"))):
            wall_box("rail", f"rail_{w}{k}", w, -30, 30, y, y + 2, -1.5, 1.5, faces=(*sides, cap))
        for k, u in enumerate((-8.0, 8.0)):
            wall_box("mullion", f"mullion_{w}{k}", w, u - 0.75, u + 0.75, FLOOR + 2, ROOF - 2, -0.75, 0.75, faces=sides)
        for k, y in enumerate((32.0, 48.0)):
            wall_box("transom", f"transom_{w}{k}", w, -28, 28, y - 0.75, y + 0.75, -0.85, 0.85, faces=(*sides, "up", "down"))
        # A slim housing sitting on the lower transom of the emitter's cell, so the straight-on view of the core is
        # clear glass; its lens and beam angle up to the core.
        wall_box("emitter", f"emitter_{w}", w, -3, 3, EMIT_Y - 2.5, EMIT_Y + 2.5, -4, 5)
        wall_box("lens", f"lens_{w}", w, -2, 2, EMIT_Y - 1.5, EMIT_Y + 1.5, 5, 8 + LENS_OUT, faces=(*sides, "up", "down"))
        beam(w)
    add("lens", g.box("lens_roof", (CX - 2.5, ROOF - LENS_OUT - 1, CZ - 2.5), (CX + 2.5, ROOF, CZ + 2.5), faces=(*sides, "down"), bone=F))
    add("beam", g.box("beam_roof", (CX - 0.35, CY + 2.5, CZ - 0.35), (CX + 0.35, ROOF - LENS_OUT - 1, CZ + 0.35), faces=sides,
                      shade=False, bone="beams"))

    # --- the cradle: two gimbal rings round the core -----------------------------------------------------------------
    for k, (r, t) in enumerate(((7.0, 1.0), (6.6, 0.8))):
        lo, hi = CY - r, CY + r
        if k == 0:                                         # ring in the x-y plane
            boxes = [((CX - r, hi - t, CZ - 0.5), (CX + r, hi, CZ + 0.5)), ((CX - r, lo, CZ - 0.5), (CX + r, lo + t, CZ + 0.5)),
                     ((CX - r, lo + t, CZ - 0.5), (CX - r + t, hi - t, CZ + 0.5)), ((CX + r - t, lo + t, CZ - 0.5), (CX + r, hi - t, CZ + 0.5))]
        else:                                              # ring in the z-y plane, a touch smaller so no faces meet
            boxes = [((CX - 0.4, hi - t, CZ - r), (CX + 0.4, hi, CZ + r)), ((CX - 0.4, lo, CZ - r), (CX + 0.4, lo + t, CZ + r)),
                     ((CX - 0.4, lo + t, CZ - r), (CX + 0.4, hi - t, CZ - r + t)), ((CX - 0.4, lo + t, CZ + r - t), (CX + 0.4, hi - t, CZ + r))]
        for j, (a, b) in enumerate(boxes):
            add("gimbal", g.box(f"gimbal{k}_{j}", a, b, bone=F))

    # --- the core and its accretion disks ---------------------------------------------------------------------------
    add("core", g.box("core_ball", (CX - 2.5, CY - 2.5, CZ - 2.5), (CX + 2.5, CY + 2.5, CZ + 2.5), shade=False, bone="core"))
    add("photon", g.box("photon_ring", (CX - 3.2, CY - 0.5, CZ - 3.2), (CX + 3.2, CY + 0.5, CZ + 3.2), faces=sides, shade=False, bone="core"))
    for i, r in enumerate(DISKS):
        add(f"disk{i}", g.box(f"disk{i}_a", (CX - r, CY - 0.25, CZ - r), (CX + r, CY + 0.25, CZ + r), shade=False, bone=f"disk{i}"))

    # --- the port cells: fittings on the floor's outer faces ----------------------------------------------------------
    add("cabinet", g.box("cabinet", (-7, 1, -8.6), (7, 13, -8), faces=("north", "east", "west", "up", "down"), bone=F))
    lamps = add("lamps", g.box("lamps", (2.5, 10, -8.8), (6, 11.5, -8.6), faces=("north",), shade=False, bone=F))
    add("item_hatch", g.box("item_hatch", (-40.6, 2, 26), (-40, 14, 38), faces=("west", "north", "south", "up", "down"), bone=F))
    add("terminal", g.box("terminal", (40, 2, 26), (40.6, 14, 38), faces=("east", "north", "south", "up", "down"), bone=F))
    # Any other floor block may be an Item Port: each outer floor cell has a hatch (bone "hatch_<col>_<row>") that the
    # renderer shows only when that cell is one.
    for col in range(5):
        for row in range(5):
            if not (col in (0, 4) or row in (0, 4)) or (col, row) in ((2, 0), (0, 2), (4, 2)):
                continue
            x0, z0 = (col - 2) * 16.0 - 8, row * 16.0 - 8
            bone = f"hatch_{col}_{row}"
            g.bone(bone, pivot=(x0 + 8, 0, z0 + 8), parent=F)
            if row == 0:
                frm, to, face = (x0 + 3, 3, z0 - 0.6), (x0 + 13, 13, z0), "north"
            elif row == 4:
                frm, to, face = (x0 + 3, 3, z0 + 16), (x0 + 13, 13, z0 + 16.6), "south"
            elif col == 0:
                frm, to, face = (x0 - 0.6, 3, z0 + 3), (x0, 13, z0 + 13), "west"
            else:
                frm, to, face = (x0 + 16, 3, z0 + 3), (x0 + 16.6, 13, z0 + 13), "east"
            sides_ = {"north": ("north", "east", "west", "up", "down"), "south": ("south", "east", "west", "up", "down"),
                      "west": ("west", "north", "south", "up", "down"), "east": ("east", "north", "south", "up", "down")}[face]
            box = shared(f"hatch_{face}", g.box(f"hatch_{col}_{row}", frm, to, faces=sides_, bone=bone))
            box.__dict__["hatch_face"] = face

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    steel, gun, olive = P.STEEL, P.GUNMETAL, P.OLIVE

    def slab(top_ring: bool):
        def paint(t, r, d):
            x, y, w, h = r
            if d in ("up", "down"):
                t.rect(x, y, w, h, olive[tone(d)])
                t.outline(x, y, w, h, gun[2])
                if top_ring and d == "up" or not top_ring and d == "down":
                    # The chamber's footprint: a hazard ring round the middle three cells.
                    cx0, cy0 = x + w // 2 - 24, y + h // 2 - 24
                    t.stripes(cx0, cy0, 48, 2, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
                    t.stripes(cx0, cy0 + 46, 48, 2, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
                    t.stripes(cx0, cy0, 2, 48, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
                    t.stripes(cx0 + 46, cy0, 2, 48, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
                for xx in range(x + 15, x + w - 1, 16):
                    t.vline(xx, y + 1, h - 2, olive[2])                    # deck plate seams
                if not top_ring and d == "up":
                    # The roof emitter's back plate in the centre cell.
                    px, py = x + w // 2 - 7, y + h // 2 - 7
                    t.rect(px, py, 14, 14, P.BLACK_PAINT[2])
                    t.outline(px, py, 14, 14, P.BRASS[3])
                    t.rect(px + 5, py + 5, 4, 4, P.SINGULARITY[3])
                    for bx, by in ((1, 1), (12, 1), (1, 12), (12, 12)):
                        t.px(px + bx, py + by, P.BRASS[5])
                return
            t.rect(x, y, w, h, gun[3])
            for xx in range(x + 15, x + w - 1, 16):
                t.vline(xx, y + 2, h - 4, gun[2])
            t.stripes(x, y if top_ring else y + h - 2, w, 2, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
        return paint
    g.paint_all(tex, parts["floor"][0], slab(True))
    g.paint_all(tex, parts["roof"][0], slab(False))
    for b in parts["post"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d)]), t.vline(r[0], r[1], r[3], steel[5]),
                                             [t.px(r[0] + r[2] // 2, yy, steel[6]) for yy in range(r[1] + 3, r[1] + r[3], 8)]))

    def pane(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, CLEAR)
        for k in range(min(w, h) // 4):
            t.px(x + 3 + k, y + h - 6 - k, (*GLASS[6][:3], 255))           # glints
            t.px(x + w - 12 + k, y + 8 - k // 2, (*GLASS[5][:3], 255))
    for b in parts["pane"]:
        g.paint_all(tex, b, pane)
    for group in ("rail", "mullion", "transom"):
        for b in parts[group]:
            g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d) + (1 if group == "rail" else 0)]),
                                                 t.hline(r[0], r[1], r[2], steel[5]) if d not in ("up", "down") and r[3] > 2 else None))

    def emitter(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.BLACK_PAINT[tone(d)])
        if min(w, h) >= 5:
            t.outline(x, y, w, h, P.BRASS[3])
            for xx, yy in ((x + 1, y + 1), (x + w - 2, y + 1), (x + 1, y + h - 2), (x + w - 2, y + h - 2)):
                t.px(xx, yy, P.BRASS[5])
    for b in parts["emitter"]:
        g.paint_all(tex, b, emitter)

    def lens(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.SINGULARITY[3])
        if min(w, h) >= 3:
            t.rect(x + 1, y + 1, w - 2, h - 2, P.SINGULARITY[4])
            t.rect(x + w // 2 - 1, y + h // 2 - 1, 2, 2, P.ANTIMATTER[6])
    for b in parts["lens"]:
        g.paint_all(tex, b, lens)
        g.paint_all(glow, b, lens)

    def beam(t, r, d):
        t.rect(*r, P.ANTIMATTER[5])
        if r[3] > 2:
            t.vline(r[0], r[1], r[3], P.ANTIMATTER[6])
    for b in parts["beam"]:
        g.paint_all(tex, b, beam)
        g.paint_all(glow, b, beam)
    for b in parts["gimbal"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, P.BRASS[tone(d) + 1]), t.hline(r[0], r[1], r[2], P.BRASS[6]) if d == "up" else None))

    def core(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, (6, 4, 10, 255))
        t.px(x, y, P.SINGULARITY[2])                                       # lensing at the rim
        t.px(x + w - 1, y + h - 1, P.SINGULARITY[2])
    g.paint_all(tex, parts["core"][0], core)

    def photon(t, r, d):
        t.rect(*r, ACCRETION[6])
        t.hline(r[0], r[1], r[2], ACCRETION[5])
    g.paint_all(tex, parts["photon"][0], photon)
    g.paint_all(glow, parts["photon"][0], photon)

    def disk(t, r, d):
        x, y, w, h = r
        if d not in ("up", "down"):
            t.rect(x, y, w, h, ACCRETION[4])
            return
        cx, cy = x + w / 2, y + h / 2
        for yy in range(h):
            for xx in range(w):
                dist = max(abs(x + xx + 0.5 - cx), abs(y + yy + 0.5 - cy)) / (w / 2)
                swirl = (xx * 3 + yy * 5) % 7 == 0
                c = ACCRETION[6] if dist < 0.55 else ACCRETION[5] if dist < 0.75 else ACCRETION[4] if dist < 0.9 else ACCRETION[3]
                t.px(x + xx, y + yy, ACCRETION[3] if swirl and dist > 0.5 else c)
    for i in range(3):
        g.paint_all(tex, parts[f"disk{i}"][0], disk)
        g.paint_all(glow, parts[f"disk{i}"][0], disk)

    def cabinet(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, olive[tone(d) + 1])
        if d != "north":
            return
        t.outline(x, y, w, h, olive[1])
        t.rect(x + 2, y + 2, 6, 4, P.BLACK_PAINT.deep)                     # CRT
        for xx in range(x + 3, x + 7):
            t.px(xx, y + 3 + (xx % 2), P.SINGULARITY[4])
        t.rect(x + 2, y + 7, 4, 3, P.WHITE_CERAMIC[5])                     # cage dial
        t.px(x + 3, y + 8, P.BLACK_PAINT[1])
        t.stripes(x + 1, y + h - 2, w - 2, 1, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
    g.paint_all(tex, parts["cabinet"][0], cabinet)

    def lamps_paint(t, r, d):
        for i, c in enumerate((P.INDICATOR_GREEN.light, P.NUCLEAR_AMBER.light, P.SINGULARITY[5])):
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
    g.paint_all(tex, parts["item_hatch"][0], socket("west", P.HAZARD_YELLOW.base, gun))
    for face in ("north", "south", "west", "east"):
        for b in parts.get(f"hatch_{face}", []):
            if first.get(f"hatch_{face}:{tuple(round(bb - a, 3) for a, bb in zip(b.frm, b.to))}:{sorted(b.faces)}") is b:
                g.paint_all(tex, b, socket(face, P.HAZARD_YELLOW.base, gun))
    g.paint_all(tex, parts["terminal"][0], socket("east", P.WARNING_RED[3], P.WARNING_RED))
    return g, tex, glow


def animations() -> list[Animation]:
    """The accretion disks spin round the core."""
    w = Animation("working", 2.0, loop=True)
    for i in range(3):
        w.rotate(f"disk{i}", {0: (0, 0, 0), 1: (0, 180, 0), 2: (0, 360, 0)})
    return [w]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
