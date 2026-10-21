"""Ordnance Assembly Bench (GeckoLib block model + animation), DESIGN.md §10, Phase 7. Two blocks wide.

Look: a heavy steel workbench running across both blocks (the main half east, the side half west, front = north).
An overhead hoist beam on two end uprights carries a trolley; its chain and hook lower the casing onto the cradle in
the middle of the bench, where two clamp jaws hold the work. On the bench: a blueprint sheet at the west end, a
drawer cabinet under it, a pegboard of tools at the back, and a hooded work lamp at the east front corner (its lens
glows). While a warhead is being made or waits in the output slot, a warhead body lies in the cradle ("workpiece").

Animation "working", 2 s (timed with the bench sound): the hook rattles down (0-0.6 s), the casing settles and the
jaws bite at 1.0 s, a ratchet and a wrench work the bolts (1.15-1.75 s), the hook rises again.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz import paths  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.model import write_json  # noqa: E402
from gz.tex import Tex  # noqa: E402

NAME = "assembly_bench"
X0, X1 = -24, 8          # the side half (west) then the main half: 32 px across
MID = -8                 # the seam between the halves: the cradle sits over it
TOP = 13                 # bench top surface


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(256, 128))
    g.bone("frame")
    g.bone("trolley", pivot=(MID, 28, 0), parent="frame")
    g.bone("hook", pivot=(MID, 27, 0), parent="trolley")
    g.bone("jaw_w", pivot=(MID - 9, TOP, 0), parent="frame")
    g.bone("jaw_e", pivot=(MID + 9, TOP, 0), parent="frame")
    g.bone("workpiece", pivot=(MID, TOP + 2, 0), parent="frame")
    g.bone("ratchet", pivot=(MID + 4, TOP + 6, -3), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    # Bench: a thick top across both halves on four legs, a lower shelf, a drawer cabinet under the west end.
    add("top", g.box("top", (X0 + 0.5, TOP - 2, -7), (X1 - 0.5, TOP, 6), bone=F))
    for i, (x, z) in enumerate(((X0 + 1, -6.5), (X1 - 3, -6.5), (X0 + 1, 3.5), (X1 - 3, 3.5))):
        add("leg", g.box(f"leg{i}", (x, 0, z), (x + 2, TOP - 2, z + 2), faces=("north", "south", "east", "west", "down"), bone=F))
    add("shelf", g.box("shelf", (X0 + 3, 2, -5.5), (X1 - 3, 3, 4.5), faces=("north", "south", "east", "west", "up", "down"), bone=F))
    add("cabinet", g.box("cabinet", (X0 + 3, 3, -6), (X0 + 13, TOP - 2, 4), faces=("north", "south", "east", "west"), bone=F))
    # Cradle: two V-block saddles across the seam, with clamp jaws outside them.
    for i, x in enumerate((MID - 6, MID + 1)):
        add("saddle", g.box(f"saddle{i}", (x, TOP, -3), (x + 2, TOP + 2, 3), faces=("north", "south", "east", "west", "up"), bone=F))
    add("jaw", g.box("jaw_w", (MID - 10, TOP, -2.5), (MID - 8, TOP + 5, 2.5), faces=("north", "south", "east", "west", "up"), bone="jaw_w"))
    add("jaw", g.box("jaw_e", (MID + 8, TOP, -2.5), (MID + 10, TOP + 5, 2.5), faces=("north", "south", "east", "west", "up"), bone="jaw_e"))
    add("screw", g.box("screw_w", (MID - 13, TOP + 1.5, -0.5), (MID - 10, TOP + 2.5, 0.5), faces=("north", "south", "up", "down", "west"), bone="jaw_w"))
    add("screw", g.box("screw_e", (MID + 10, TOP + 1.5, -0.5), (MID + 13, TOP + 2.5, 0.5), faces=("north", "south", "up", "down", "east"), bone="jaw_e"))
    # The warhead being made: an olive body lying east-west in the saddles, a stepped nose to the east.
    W = "workpiece"
    add("work_body", g.box("work_body", (MID - 7.5, TOP + 1, -2), (MID + 4, TOP + 5, 2), bone=W))
    add("work_nose", g.box("work_nose0", (MID + 4, TOP + 1.5, -1.5), (MID + 6, TOP + 4.5, 1.5), faces=("north", "south", "east", "up", "down"), bone=W))
    add("work_nose", g.box("work_nose1", (MID + 6, TOP + 2.25, -0.75), (MID + 7.5, TOP + 3.75, 0.75), faces=("north", "south", "east", "up", "down"), bone=W))
    # Hoist: an upright at each end of the bench, a beam across, a trolley with the chain and hook over the cradle.
    for i, x in enumerate((X0 + 1, X1 - 3)):
        add("upright", g.box(f"upright{i}", (x, TOP, -1), (x + 2, 30, 1), faces=("north", "south", "east", "west"), bone=F))
        add("foot", g.box(f"foot{i}", (x - 0.5, TOP, -2.5), (x + 2.5, TOP + 1, 2.5), faces=("north", "south", "east", "west", "up"), bone=F))
    add("beam", g.box("beam", (X0 + 0.5, 28, -1.5), (X1 - 0.5, 31, 1.5), bone=F))
    T = "trolley"
    add("trolley", g.box("trolley", (MID - 2, 26.5, -2), (MID + 2, 28, 2), faces=("north", "south", "east", "west", "down"), bone=T))
    add("hoist_motor", g.box("hoist_motor", (MID - 1.5, 28, -2.5), (MID + 1.5, 31, -1.5), faces=("north", "east", "west", "up"), bone=T))
    H = "hook"
    add("chain", g.box("chain", (MID - 0.5, 21.5, -0.5), (MID + 0.5, 26.5, 0.5), faces=("north", "south", "east", "west"), bone=H))
    add("block", g.box("hook_block", (MID - 1.5, 19.5, -1), (MID + 1.5, 21.5, 1), faces=("north", "south", "east", "west", "up", "down"), bone=H))
    add("hook", g.box("hook_tip", (MID - 0.5, 18.5, -0.5), (MID + 0.5, 19.5, 0.5), faces=("north", "south", "east", "west", "down"), bone=H))
    # Tools on a pegboard at the back of the main half, a ratchet wrench lying by the work.
    add("pegboard", g.box("pegboard", (MID + 2, TOP, 5), (X1 - 4, TOP + 9, 6), faces=("north", "east", "west", "up"), bone=F))
    for i, (x, h) in enumerate(((MID + 3.5, 6), (MID + 6, 5), (MID + 8.5, 6.5))):
        add("tool", g.box(f"tool{i}", (x, TOP + 8.5 - h, 4.6), (x + 1, TOP + 8.5, 5), faces=("north", "east", "west", "down"), bone=F))
    add("ratchet", g.box("ratchet", (MID + 2.5, TOP, -5.5), (MID + 6.5, TOP + 0.75, -4.5), faces=("north", "south", "east", "west", "up"), bone="ratchet"))
    # Work lamp at the east front corner: a post, an arm reaching in, a hood with a glowing lens under it.
    add("lamp_post", g.box("lamp_post", (X1 - 3, TOP, -6.5), (X1 - 2, TOP + 9, -5.5), faces=("north", "south", "east", "west"), bone=F))
    add("lamp_arm", g.box("lamp_arm", (X1 - 6, TOP + 8, -6.5), (X1 - 3, TOP + 9, -5.5), faces=("north", "south", "up", "down", "west"), bone=F))
    add("lamp_hood", g.box("lamp_hood", (X1 - 8, TOP + 7, -7.5), (X1 - 5, TOP + 9.5, -4.5), faces=("north", "south", "east", "west", "up"), bone=F))
    add("lamp_lens", g.box("lamp_lens", (X1 - 7.5, TOP + 6.9, -7), (X1 - 5.5, TOP + 7, -5), faces=("down",), shade=False, bone=F))
    # The blueprint sheet at the west end (thin, just above the top).
    add("blueprint", g.box("blueprint", (X0 + 4, TOP, -6.5), (X0 + 13, TOP + 0.2, -3), faces=("up", "north", "east", "west", "south"), bone=F))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    gun, steel, olive, yellow, black = P.GUNMETAL, P.STEEL, P.OLIVE, P.HAZARD_YELLOW, P.HAZARD_BLACK

    def bench_top(t, r, d):
        x, y, w, h = r
        if d == "up":
            t.rect(x, y, w, h, steel[3])
            for xx in range(x + 3, x + w - 2, 8):              # sparse tread-plate dimples
                for yy in range(y + 2 + (xx // 8) % 2 * 2, y + h - 2, 4):
                    t.px(xx, yy, steel[4])
            t.outline(x, y, w, h, steel[2])
        elif d == "north":
            t.stripes(x, y, w, h, yellow.base, black.base, width=2)      # hazard edge on the working side
        else:
            t.rect(x, y, w, h, gun[tone(d)])
            t.hline(x, y, w, gun[5])
    g.paint_all(tex, parts["top"][0], bench_top)

    def painted(ramp):
        def paint(t, r, d):
            x, y, w, h = r
            t.panel(x, y, w, h, ramp, base=tone(d), style="raised") if min(w, h) >= 3 else t.rect(x, y, w, h, ramp[tone(d)])
        return paint
    for key in ("leg", "shelf", "upright", "foot"):
        for b in parts[key]:
            g.paint_all(tex, b, painted(gun))

    def beam(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, yellow[3 if d != "up" else 4])
        if d in ("north", "south") and w > 8:
            t.hline(x, y, w, yellow[5])
            t.hline(x, y + h - 1, w, yellow[2])
            t.text(x + w // 2 - 6, y + 1, "2T", black.base) if d == "north" else None   # rated load
    g.paint_all(tex, parts["beam"][0], beam)

    def cabinet(t, r, d):
        x, y, w, h = r
        t.panel(x, y, w, h, olive, base=tone(d), style="raised")
        if d == "north":
            for k in range(3):                                   # three drawers with pulls
                dy = y + 1 + k * (h - 2) // 3
                t.outline(x + 1, dy, w - 2, (h - 2) // 3 - 1, olive[2])
                t.hline(x + w // 2 - 1, dy + 1, 3, steel[5])
    g.paint_all(tex, parts["cabinet"][0], cabinet)

    for b in parts["saddle"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, P.RUBBER[3] if d == "up" else gun[tone(d) + 1]),
                                             t.hline(r[0], r[1], r[2], P.RUBBER[2]) if d == "up" else None))
    for b in parts["jaw"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, P.WARNING_RED[tone(d)]), t.hline(r[0], r[1], r[2], P.WARNING_RED[5])))
    for b in parts["screw"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[5]), t.px(r[0], r[1], steel[6])))

    def work_body(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, olive[tone(d)])
        if d in ("north", "south", "up") and w > 6:
            t.vline(x + w - 3, y, h, yellow[4])                  # nose band
            t.vline(x + 2, y, h, olive[1])                       # tail seam
    g.paint_all(tex, parts["work_body"][0], work_body)
    for b in parts["work_nose"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, olive[tone(d) + 1]))

    g.paint_all(tex, parts["trolley"][0], lambda t, r, d: (t.rect(*r, gun[tone(d) + 1]), t.hline(r[0], r[1], r[2], gun[5])))
    g.paint_all(tex, parts["hoist_motor"][0], lambda t, r, d: (t.rect(*r, P.NATO_GREEN[tone(d)]),
                                                               t.vent(r[0], r[1] + 1, r[2], r[3] - 1, P.NATO_GREEN, slat=1) if d == "north" and r[3] > 2 else None))

    def chain(t, r, d):
        x, y, w, h = r
        for yy in range(y, y + h):
            t.px(x, yy, steel[5] if yy % 2 else steel[2])
    g.paint_all(tex, parts["chain"][0], chain)
    g.paint_all(tex, parts["block"][0], lambda t, r, d: (t.rect(*r, yellow[tone(d)]), t.px(r[0] + r[2] // 2, r[1], black.base)))
    g.paint_all(tex, parts["hook"][0], lambda t, r, d: t.rect(*r, steel[3]))

    def pegboard(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.DESERT_TAN[tone(d)])
        if d == "north":
            for yy in range(y + 1, y + h - 1, 2):
                for xx in range(x + 1 + (yy // 2) % 2, x + w - 1, 2):
                    t.px(xx, yy, P.DESERT_TAN[1])
    g.paint_all(tex, parts["pegboard"][0], pegboard)
    tool_colours = (P.WARNING_RED, steel, P.HAZARD_YELLOW)
    for i, b in enumerate(parts["tool"]):
        ramp = tool_colours[i]
        g.paint_all(tex, b, lambda t, r, d, ramp=ramp: (t.rect(*r, ramp[tone(d) + 1]), t.px(r[0], r[1], steel[6])))
    g.paint_all(tex, parts["ratchet"][0], lambda t, r, d: (t.rect(*r, steel[tone(d) + 1]), t.px(r[0] + r[2] - 1, r[1], steel[6])))

    for key in ("lamp_post", "lamp_arm"):
        g.paint_all(tex, parts[key][0], lambda t, r, d: t.rect(*r, gun[tone(d)]))
    g.paint_all(tex, parts["lamp_hood"][0], lambda t, r, d: (t.rect(*r, yellow[tone(d)]), t.hline(r[0], r[1], r[2], yellow[5])))

    def lens(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.WHITE_CERAMIC[6])
        t.px(x, y, (255, 250, 220, 255))
        glow.rect(x, y, w, h, P.WHITE_CERAMIC[6])
    g.paint_all(tex, parts["lamp_lens"][0], lens)

    def blueprint(t, r, d):
        x, y, w, h = r
        paper, ink = (40, 82, 156, 255), (196, 220, 248, 255)
        t.rect(x, y, w, h, paper)
        if d == "up" and w >= 6:
            t.outline(x + 1, y + 1, w - 2, h - 2, ink)
            t.hline(x + 2, y + h // 2, w - 4, ink)                # a warhead in side view
            t.rect(x + 2, y + h // 2 - 1, w - 5, 3, (88, 132, 200, 255))
            t.px(x + w - 3, y + h // 2, ink)
            t.px(x + 2, y + 2, ink)
    g.paint_all(tex, parts["blueprint"][0], blueprint)
    return g, tex, glow


def animations() -> list[Animation]:
    work = Animation("working", 2.0, loop=True)
    work.move("hook", {0: (0, 0, 0), 0.6: (0, -4.5, 0), 1.0: (0, -4.5, 0), 1.05: (0, -4.2, 0), 1.5: (0, -3, 0), 2.0: (0, 0, 0)})
    work.move("trolley", {0: (0, 0, 0), 0.3: (0.3, 0, 0), 0.6: (0, 0, 0), 2.0: (0, 0, 0)})
    work.move("jaw_w", {0: (0, 0, 0), 0.9: (0, 0, 0), 1.0: (1, 0, 0), 1.8: (1, 0, 0), 2.0: (0, 0, 0)})
    work.move("jaw_e", {0: (0, 0, 0), 0.9: (0, 0, 0), 1.0: (-1, 0, 0), 1.8: (-1, 0, 0), 2.0: (0, 0, 0)})
    work.move("workpiece", {0: (0, 0, 0), 0.98: (0, 0, 0), 1.0: (0, -0.15, 0), 1.06: (0, 0.05, 0), 1.12: (0, 0, 0), 2.0: (0, 0, 0)})
    work.rotate("ratchet", {0: (0, 0, 0), 1.15: (0, 0, 0), 1.22: (0, 20, 0), 1.29: (0, 0, 0), 1.36: (0, 20, 0), 1.43: (0, 0, 0),
                            1.5: (0, 20, 0), 1.57: (0, 0, 0), 2.0: (0, 0, 0)})
    return [work]


# In the hand and the GUI the two-block model shrinks to fit (GeckoLib draws it from the main half; GeoBlockItem
# shifts it half a block west so the whole bench is centred).
WIDE_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -1.5, 0], "scale": [0.36, 0.36, 0.36]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 1, 0], "scale": [0.16, 0.16, 0.16]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.38, 0.38, 0.38]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 0.5, 0], "scale": [0.24, 0.24, 0.24]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 0.5, 0], "scale": [0.24, 0.24, 0.24]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -2, 0], "scale": [0.26, 0.26, 0.26]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, -2, 0], "scale": [0.26, 0.26, 0.26]},
}


def glue() -> None:
    """Block model (particle only), blockstate over facing x active x part, the GeckoLib item definition."""
    write_json(paths.MODELS / "block" / f"{NAME}.json", {"textures": {"particle": "groundzero:block/machine_particle"}})
    variants = {}
    for facing in ("north", "east", "south", "west"):
        for active in ("false", "true"):
            for part in ("main", "side"):
                variants[f"active={active},facing={facing},part={part}"] = {"model": f"groundzero:block/{NAME}"}
    write_json(paths.ASSETS / "blockstates" / f"{NAME}.json", {"variants": variants})
    write_json(paths.MODELS / "item" / f"{NAME}.json", {"parent": "minecraft:block/block",
                                                        "textures": {"particle": "groundzero:block/machine_particle"},
                                                        "display": WIDE_DISPLAY})
    write_json(paths.ASSETS / "items" / f"{NAME}.json",
               {"model": {"type": "minecraft:special", "base": f"groundzero:item/{NAME}", "model": {"type": "geckolib:geckolib"}}})


if __name__ == "__main__":
    model, texture, glowmask = build()
    print("z-fights:", model.zfight())
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
    glue()
