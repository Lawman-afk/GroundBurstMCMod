"""Exotic Matter Fabricator (GeckoLib block model + animation), DESIGN.md §8. Two blocks tall.

Look: a heavy field-generator housing (copper windings behind vents, a front panel with a violet warning placard) on
the shared hazard-striped plinth, four corner posts and a canopy with a second emitter overhead. Between the emitters
a violet exotic shard with a black core (bone "shard") floats inside a magnetic cage of three nested rings, each
built from twelve segments: the outer ring (bone "ring_a") tumbles about the vertical axis, the middle ring
("ring_b") about east-west, the inner ring ("ring_c") about north-south. The radii are chosen so that the rings, the
shard and the posts never touch in any pose. Glowing motes ("sparks") orbit the shard while it runs.
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402

from gb import machine_kit as K  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.palette import fixed  # noqa: E402
from gb.tex import Tex  # noqa: E402

NAME = "exotic_matter_fabricator"
C = 18.0                        # cage centre height
SEGMENTS = 12
THICK = 0.8                     # ring cross-section
# (bone, radius, plane): the ring lies in that plane and tumbles about the bone's animated axis.
RINGS = [("ring_a", 6.5, "xy"), ("ring_b", 5.0, "xz"), ("ring_c", 3.6, "yz")]
SHARD = fixed("exotic_violet", "#12041e", "#2a0a46", "#4a1678", "#6e26ac", "#9444d8", "#b878f0", "#e4c8ff")
HOUSING_Y = 8.0
CANOPY_Y = 28.5


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def ring_segment(g: GeoModel, bone: str, radius: float, plane: str, k: int):
    """Segment k of a ring: the same box at the ring's 'top', turned k * 30 degrees about the ring's normal."""
    half = radius * math.tan(math.pi / SEGMENTS) + 0.15          # slight overlap so the joints close
    t = THICK / 2                                                  # radial half-thickness
    # Across the ring plane, odd segments are thinner: neighbours' flat sides then never share a plane (they would
    # flicker where the joints overlap), and the ring reads as alternating coil packs.
    n = THICK / 2 if k % 2 == 0 else THICK / 2 - 0.12
    if plane == "xy":          # normal z: top is +y
        frm, to, axis = (-half, C + radius - t, -n), (half, C + radius + t, n), "z"
    elif plane == "xz":        # normal y: 'top' is +z
        frm, to, axis = (-half, C - n, radius - t), (half, C + n, radius + t), "y"
    else:                      # normal x: top is +y
        frm, to, axis = (-n, C + radius - t, -half), (n, C + radius + t, half), "x"
    angle = k * 360.0 / SEGMENTS
    if angle > 180:
        angle -= 360
    rot = None if k == 0 else {"origin": (0, C, 0), "axis": axis, "angle": angle}
    return g.box(f"{bone}_seg{k}", frm, to, rotation=rot, bone=bone)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("frame")
    for bone, _, _ in RINGS:
        g.bone(bone, pivot=(0, C, 0), parent="frame")
    g.bone("shard", pivot=(0, C, 0), parent="frame")
    g.bone("sparks", pivot=(0, C, 0), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("plinth", g.box("plinth", (-8, 0, -8), (8, 3, 8), bone=F))
    add("housing", g.box("housing", (-7, 3, -7), (7, HOUSING_Y, 7), bone=F))
    add("emitter", g.box("pad_lo", (-2.5, HOUSING_Y, -2.5), (2.5, HOUSING_Y + 1, 2.5), bone=F))
    add("lens", g.box("lens_lo", (-1.5, HOUSING_Y + 1, -1.5), (1.5, HOUSING_Y + 1.4, 1.5), faces=("north", "south", "east", "west", "up"), shade=False, bone=F))
    for i, (x, z) in enumerate(((-7.5, -7.5), (6.25, -7.5), (-7.5, 6.25), (6.25, 6.25))):
        add("post", g.box(f"post{i}", (x, 3, z), (x + 1.25, CANOPY_Y, z + 1.25), faces=("north", "south", "east", "west"), bone=F))
    add("canopy", g.box("canopy", (-7.5, CANOPY_Y, -7.5), (7.5, CANOPY_Y + 1.5, 7.5), bone=F))
    add("cap", g.box("cap", (-3, CANOPY_Y + 1.5, -3), (3, CANOPY_Y + 2.5, 3), bone=F))
    add("emitter", g.box("pad_hi", (-2.5, CANOPY_Y - 1, -2.5), (2.5, CANOPY_Y, 2.5), faces=("north", "south", "east", "west", "down"), bone=F))
    add("lens", g.box("lens_hi", (-1.5, CANOPY_Y - 1.4, -1.5), (1.5, CANOPY_Y - 1, 1.5), faces=("north", "south", "east", "west", "down"), shade=False, bone=F))
    # The three rings; every segment after the first reuses the first one's texture.
    # Even and odd segments each reuse their first segment's texture.
    for bone, radius, plane in RINGS:
        first: dict[int, object] = {}
        for k in range(SEGMENTS):
            seg = add(bone, ring_segment(g, bone, radius, plane, k))
            if k % 2 not in first:
                first[k % 2] = seg
            else:
                for d in seg.faces:
                    g.share(seg, d, first[k % 2], d)
    # Shard: a rotated square body with tapering tips, black core showing through the facets.
    add("shard", g.box("shard_body", (-1.2, C - 2, -1.2), (1.2, C + 2, 1.2), rotation={"origin": (0, C, 0), "axis": "y", "angle": 45}, shade=False, bone="shard"))
    add("shard", g.box("shard_tip_hi", (-0.7, C + 2, -0.7), (0.7, C + 2.6, 0.7), rotation={"origin": (0, C, 0), "axis": "y", "angle": 45}, shade=False, bone="shard"))
    add("shard", g.box("shard_tip_lo", (-0.7, C - 2.6, -0.7), (0.7, C - 2, 0.7), rotation={"origin": (0, C, 0), "axis": "y", "angle": 45}, shade=False, bone="shard"))
    # Sparks: four motes on a tilted orbit round the shard.
    for i in range(4):
        a = i * math.pi / 2
        x, z = 2.4 * math.cos(a), 2.4 * math.sin(a)
        y = C + (0.9 if i % 2 else -0.9)
        add("spark", g.box(f"spark{i}", (x - 0.2, y - 0.2, z - 0.2), (x + 0.2, y + 0.2, z + 0.2), shade=False, bone="sparks"))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, glow, parts)
    return g, tex, glow


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The fabricator's surfaces, in model space (tools/gb/machine_kit.py; the machines' remaster): a black field
    housing with copper windings through its vents, concentric field plates on top, a violet warning placard and a
    readout; steel emitters with glowing violet lenses; gunmetal posts banded yellow; a black canopy with brass
    insulator caps; copper ring windings in steel clamps, their field lines glowing; the shard and its sparks."""
    k = K.Kit(g, parts, seed=329)
    k.plinth(tex)
    k.paint(tex, ["housing", "canopy"], k.BLACK)

    def windings(f, img):
        if not f.vertical:
            r = K.ring(f)
            M.lighten(img, (r >= 2) & (np.floor(r) % 2 == 0), 1.35, 8)          # concentric field-plate rings
            return
        a, b = f.plane_uv()
        inside = (f.border["top"] >= 1) & (f.border["bottom"] >= 1) & (f.border["left"] >= 1) & (f.border["right"] >= 1)
        if f.d == "north":
            inside &= (f.border["left"] >= 8) & (f.border["right"] >= 7)
        slot = inside & ((np.floor(a) % 2) == 0)
        M.paste(img, k.on(f, k.COPPER), slot)
        M.lighten(img, slot & (f.border["top"] < 2), 1.2, 10)
    k.overlay(tex, "housing", windings, only=("north", "south", "east", "west", "up"))

    def placard(t, r, d):
        x, y, w, h = r
        if d == "north":
            t.stripes(x + 2, y + 1, 5, h - 2, P.SINGULARITY[4], P.HAZARD_BLACK.base, width=1)
            t.outline(x + 2, y + 1, 5, h - 2, P.BLACK_PAINT[1])
            t.rect(x + w - 6, y + 1, 4, 2, P.BLACK_PAINT[0])
            t.hline(x + w - 6, y + 1, 3, P.SINGULARITY[5])
    g.paint_all(tex, parts["housing"][0], placard)
    k.paint(tex, "emitter", k.STEEL)

    def lens(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.SINGULARITY[4])
        if min(w, h) >= 3:
            t.rect(x + 1, y + 1, w - 2, h - 2, P.SINGULARITY[6])
    for b in parts["lens"]:
        g.paint_all(tex, b, lens)
        g.paint_all(glow, b, lens)
    k.paint(tex, ["post", "cap"], k.GUN)
    k.overlay(tex, "post", lambda f, img: M.paste(img, k.on(f, k.YELLOW), (f.border["top"] % 6 >= 2) & (f.border["top"] % 6 < 3))
              if f.vertical else None)
    g.paint_all(tex, parts["canopy"][0], lambda t, r, d: [t.px(cx_, cy_, P.BRASS[5]) for cx_, cy_ in
                                                          ((r[0] + 1, r[1] + 1), (r[0] + r[2] - 2, r[1] + 1), (r[0] + 1, r[1] + r[3] - 2),
                                                           (r[0] + r[2] - 2, r[1] + r[3] - 2))] if d == "up" else None)
    # Ring segments (the first even and first odd segment of each ring are painted; the rest share them): copper winding
    # with a steel clamp at each end, and a violet field line on the inside face that glows.
    inner = {"xy": "down", "xz": "north", "yz": "down"}

    def seg(t, r, d, plane):
        x, y, w, h = r
        if d == inner[plane]:
            t.rect(x, y, w, h, P.SINGULARITY[5])
            return
        if w >= h and w >= 3:
            t.vline(x, y, h, P.STEEL[4])
            t.vline(x + w - 1, y, h, P.STEEL[4])
        elif h >= 3:
            t.hline(x, y, w, P.STEEL[4])
            t.hline(x, y + h - 1, w, P.STEEL[4])
    for bone, _, plane in RINGS:
        for first in parts[bone][:2]:
            k.paint(tex, first.name, k.COPPER)
            g.paint_all(tex, first, lambda t, r, d, plane=plane: seg(t, r, d, plane))
            g.paint_all(glow, first, lambda t, r, d, plane=plane: t.rect(*r, P.SINGULARITY[5]) if d == inner[plane] else None)

    def shard(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, SHARD[4])
        if d in ("up", "down"):
            t.rect(x, y, w, h, SHARD[5])
            if min(w, h) >= 2:
                t.px(x + w // 2, y + h // 2, SHARD[0])                     # black core seen end-on
            return
        t.vline(x, y, h, SHARD[6])
        if w >= 2 and h >= 3:
            t.vline(x + w - 1, y + 1, h - 2, SHARD[2])
            t.px(x + w // 2, y + h // 2, SHARD[0])                         # black core
            t.px(x + w // 2, y + h // 2 - 1, SHARD[1])
    for b in parts["shard"]:
        g.paint_all(tex, b, shard)
        g.paint_all(glow, b, shard)
    for b in parts["spark"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, SHARD[6]))
        g.paint_all(glow, b, lambda t, r, d: t.rect(*r, SHARD[6]))


def animations() -> list[Animation]:
    """4 s loop: the rings tumble on three axes at different rates, the shard turns and bobs, the motes orbit."""
    w = Animation("working", 4.0, loop=True)
    w.rotate("ring_a", {0: (0, 0, 0), 4: (0, 360, 0)})
    w.rotate("ring_b", {0: (0, 0, 0), 4: (-720, 0, 0)})
    w.rotate("ring_c", {0: (0, 0, 0), 4: (0, 0, 360)})
    w.rotate("shard", {0: (0, 0, 0), 4: (0, -360, 0)})
    w.move("shard", {0: (0, 0, 0), 1: (0, 0.3, 0), 2: (0, 0, 0), 3: (0, -0.3, 0), 4: (0, 0, 0)}, easing="easeInOutSine")
    w.rotate("sparks", {0: (0, 0, 0), 4: (0, 720, 0)})
    return [w]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
