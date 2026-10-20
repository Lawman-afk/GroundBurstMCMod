"""Multiblock structural blocks (DESIGN.md §23.2) and controller faces: textures, block models, blockstates, item
definitions. Every part block has a `formed` property; formed variants use a particle-only model because the
controller draws the whole structure.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz import paths  # noqa: E402
from gz.model import write_json  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402

STEEL = P.STEEL
OLIVE = P.OLIVE
GUN = P.GUNMETAL
LEAD_GLASS = fixed("lead_glass", "#141a1e", "#1e262c", "#2a343c", "#36424c", "#4a5864", "#6a7a88", "#a4b4c0")


def frame(t: Tex, ramp=STEEL) -> None:
    """2-px bevelled steel frame round a 16x16 face with corner bolts."""
    t.rect(0, 0, 16, 16, ramp[3])
    t.hline(0, 0, 16, ramp[5])
    t.vline(0, 0, 16, ramp[4])
    t.hline(0, 15, 16, ramp[1])
    t.vline(15, 0, 16, ramp[2])
    t.hline(1, 1, 14, ramp[4])
    t.vline(1, 1, 14, ramp[4])
    t.hline(1, 14, 14, ramp[2])
    t.vline(14, 1, 14, ramp[2])
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        t.px(x, y, ramp[6])
        t.px(x + 1, y + 1, ramp[1])


def casing() -> Tex:
    t = Tex(16, 16)
    frame(t)
    t.panel(2, 2, 12, 12, OLIVE, base=3, style="inset")
    t.outline(4, 4, 8, 8, OLIVE.shadow)                      # access hatch seam
    t.hline(5, 5, 6, OLIVE.light)
    for x, y in ((5, 7), (10, 7)):
        t.px(x, y, OLIVE.deep)                                 # hatch handles
    t.chip_edges(2, 2, 12, 12, P.PRIMER_RED.shadow, seed=9, chips=2, edges="tb", max_len=2)
    return t


def reactor_glass() -> Tex:
    t = Tex(16, 16)
    # Lead came round the edge, smoky leaded glass inside (translucent), two diagonal glints.
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            t.px(x, y, (LEAD_GLASS[2] if edge else (*LEAD_GLASS[4][:3], 110)))
    t.hline(0, 0, 16, LEAD_GLASS[3])
    t.vline(0, 0, 16, LEAD_GLASS[3])
    for k in range(5):
        t.px(3 + k, 9 - k, (*LEAD_GLASS[6][:3], 170))
        t.px(9 + k, 12 - k, (*LEAD_GLASS[6][:3], 140))
    return t


def item_port() -> Tex:
    t = Tex(16, 16)
    frame(t)
    t.stripes(2, 2, 12, 12, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
    t.rect(4, 4, 8, 8, P.BLACK_PAINT.deep)                    # port opening
    t.outline(4, 4, 8, 8, GUN[1])
    # Chevrons: one pointing in, one pointing out.
    for i, (x, y) in enumerate(((6, 6), (7, 7), (6, 8))):
        t.px(x, y, P.NUCLEAR_AMBER[5])
    for i, (x, y) in enumerate(((10, 6), (9, 7), (10, 8))):
        t.px(x, y, P.NUCLEAR_AMBER[4])
    return t


FLUID_BLUE = fixed("fluid_blue", "#0c1a3a", "#12285a", "#1a3a7e", "#2452a6", "#3a6ec8", "#6a98e0", "#a8c8f4")


def fluid_port() -> Tex:
    """Like the Item Port, in blue and black stripes, with a drop in the opening: pipes connect here."""
    t = Tex(16, 16)
    frame(t)
    t.stripes(2, 2, 12, 12, FLUID_BLUE[4], P.HAZARD_BLACK.base, width=2)
    t.rect(4, 4, 8, 8, P.BLACK_PAINT.deep)                    # port opening
    t.outline(4, 4, 8, 8, GUN[1])
    # A drop: narrow point at the top, round belly, highlight left, shade right.
    rows = {5: (7, 8), 6: (7, 8), 7: (6, 9), 8: (5, 10), 9: (5, 10), 10: (6, 9)}
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            t.px(x, y, FLUID_BLUE[5])
    for x, y in ((10, 8), (10, 9), (9, 10), (8, 10)):
        t.px(x, y, FLUID_BLUE[3])
    for x, y in ((6, 8), (6, 9), (7, 7)):
        t.px(x, y, FLUID_BLUE[6])
    return t


def flask_placard(t: Tex) -> None:
    """Chemical Plant: a flask."""
    t.rect(4, 9, 4, 4, P.WHITE_CERAMIC[5])
    t.px(5, 10, P.TOXIN[4])
    t.px(6, 10, P.TOXIN[4])
    t.hline(5, 11, 2, P.TOXIN[3])


def trefoil_placard(t: Tex) -> None:
    """Fission Reactor: a tiny radiation sign."""
    t.rect(4, 9, 4, 4, P.HAZARD_YELLOW.base)
    t.px(5, 10, P.HAZARD_BLACK.base)
    t.px(6, 10, P.HAZARD_BLACK.base)
    t.px(4, 12, P.HAZARD_BLACK.base)
    t.px(7, 12, P.HAZARD_BLACK.base)
    t.px(5, 9, P.HAZARD_BLACK.base)


def orbit_placard(t: Tex) -> None:
    """Particle Accelerator: a ring with a bright bunch on it."""
    t.rect(4, 9, 4, 4, P.BLACK_PAINT[1])
    t.outline(4, 9, 4, 4, P.CHERENKOV[3])
    t.px(7, 9, P.CHERENKOV.shine)


def controller_front(placard=None) -> Tex:
    t = Tex(16, 16)
    frame(t)
    t.panel(2, 2, 12, 12, OLIVE, base=3, style="raised")
    t.rect(3, 3, 7, 5, P.BLACK_PAINT.deep)                    # green CRT
    t.outline(3, 3, 7, 5, GUN[2])
    for x in range(4, 9):
        t.px(x, 5 + (x % 2), P.INDICATOR_GREEN[3])
    t.px(4, 4, P.INDICATOR_GREEN[5])
    for i, c in enumerate((P.INDICATOR_GREEN.base, P.HAZARD_YELLOW.base, P.WARNING_RED.base)):
        t.px(11 + (i % 2) * 2, 4 + (i // 2) * 2, c)           # status buttons
    # Placard: what this controller runs.
    (placard or flask_placard)(t)
    t.stripes(2, 13, 12, 1, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
    t.rect(10, 9, 3, 3, GUN[2])                               # dial
    t.px(11, 10, P.WHITE_CERAMIC[6])
    return t


def lead_casing() -> Tex:
    """Lead-lined casing: dull lead plates bolted into the steel frame, a seam across the middle."""
    t = Tex(16, 16)
    frame(t)
    t.panel(2, 2, 12, 12, P.LEAD, base=3, style="inset")
    t.hline(2, 8, 12, P.LEAD.shadow)
    t.hline(2, 9, 12, P.LEAD.light)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12), (3, 6), (12, 6)):
        t.px(x, y, P.LEAD[5])
    return t


def energy_port() -> Tex:
    t = Tex(16, 16)
    frame(t)
    t.stripes(2, 2, 12, 12, P.WARNING_RED[3], P.HAZARD_BLACK.base, width=2)
    t.rect(4, 4, 8, 8, P.BLACK_PAINT.deep)
    t.outline(4, 4, 8, 8, GUN[1])
    for x, y in ((8, 5), (7, 6), (6, 7), (7, 7), (8, 7), (9, 7), (8, 8), (7, 9), (6, 10)):
        t.px(x, y, P.HAZARD_YELLOW[5])                         # lightning bolt
    return t


def steel_plating() -> Tex:
    """Riveted steel deck plates with a raised diamond tread."""
    t = Tex(16, 16)
    t.rect(0, 0, 16, 16, STEEL[3])
    t.outline(0, 0, 16, 16, STEEL[2])
    t.hline(0, 0, 16, STEEL[4])
    for y in range(2, 14, 4):
        for x in range(2 + (y // 4) % 2 * 2, 14, 4):
            t.px(x, y, STEEL[5])
            t.px(x + 1, y + 1, STEEL[1])
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        t.px(x, y, STEEL[6])
    return t


def magnet_segment() -> Tex:
    """A dipole magnet: red yoke, copper windings either side of the dark beam pipe."""
    t = Tex(16, 16)
    frame(t, P.PRIMER_RED)
    for x in (3, 4, 11, 12):
        for y in range(3, 13):
            t.px(x, y, P.COPPER[4] if y % 2 else P.COPPER[3])
    t.rect(6, 6, 4, 4, P.BLACK_PAINT.deep)                    # beam pipe
    t.outline(6, 6, 4, 4, STEEL[4])
    return t


def injector() -> Tex:
    t = Tex(16, 16)
    frame(t)
    t.panel(2, 2, 12, 12, P.GUNMETAL, base=3, style="inset")
    t.rect(5, 5, 6, 6, P.BLACK_PAINT.deep)
    t.outline(5, 5, 6, 6, P.CHERENKOV[3])
    t.rect(7, 7, 2, 2, P.CHERENKOV.shine)                     # source glow
    t.stripes(2, 13, 12, 1, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
    return t


def target_chamber() -> Tex:
    t = Tex(16, 16)
    frame(t)
    t.panel(2, 2, 12, 12, P.GUNMETAL, base=3, style="inset")
    t.rect(4, 4, 8, 8, P.ANTIMATTER[2])
    t.outline(4, 4, 8, 8, STEEL[5])
    t.rect(6, 6, 4, 4, P.ANTIMATTER[5])                       # trapped antimatter glow
    t.px(7, 7, P.ANTIMATTER.shine)
    return t


def controller_top() -> Tex:
    t = casing()
    t.vent(4, 4, 8, 8, GUN, slat=2)
    return t


def particle_model(tex: str) -> dict:
    return {"textures": {"particle": tex}}


def part_block(name: str, tex: Tex, translucent: bool = False, cutout: bool = False) -> None:
    tex.save(paths.TEXTURES / "block" / f"{name}.png")
    model = {"parent": "minecraft:block/cube_all", "textures": {"all": f"groundzero:block/{name}"}}
    if translucent:
        model["render_type"] = "minecraft:translucent"
    elif cutout:
        model["render_type"] = "minecraft:cutout"
    write_json(paths.MODELS / "block" / f"{name}.json", model)
    write_json(paths.MODELS / "block" / f"{name}_formed.json", particle_model(f"groundzero:block/{name}"))
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": {
        "formed=false": {"model": f"groundzero:block/{name}"},
        "formed=true": {"model": f"groundzero:block/{name}_formed"},
    }})
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundzero:block/{name}"}})


def controller(name: str, front: Tex, top: Tex, side: Tex) -> None:
    front.save(paths.TEXTURES / "block" / f"{name}_front.png")
    top.save(paths.TEXTURES / "block" / f"{name}_top.png")
    side.save(paths.TEXTURES / "block" / f"{name}_side.png")
    write_json(paths.MODELS / "block" / f"{name}.json", {
        "parent": "minecraft:block/orientable",
        "textures": {"front": f"groundzero:block/{name}_front", "side": f"groundzero:block/{name}_side",
                     "top": f"groundzero:block/{name}_top"}})
    write_json(paths.MODELS / "block" / f"{name}_formed.json", particle_model(f"groundzero:block/{name}_side"))
    variants = {}
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    for facing, y in rot.items():
        for active in ("false", "true"):
            variants[f"active={active},facing={facing},formed=false"] = {"model": f"groundzero:block/{name}", **({"y": y} if y else {})}
            variants[f"active={active},facing={facing},formed=true"] = {"model": f"groundzero:block/{name}_formed"}
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": variants})
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundzero:block/{name}"}})


if __name__ == "__main__":
    part_block("machine_casing", casing())
    part_block("reactor_glass", reactor_glass(), translucent=True)
    part_block("item_port", item_port())
    controller("chemical_plant", controller_front(), controller_top(), casing())
    # Slice 7.
    part_block("lead_lined_casing", lead_casing())
    part_block("energy_port", energy_port())
    part_block("fluid_port", fluid_port())
    part_block("steel_plating", steel_plating())
    part_block("magnet_segment", magnet_segment())
    part_block("accelerator_injector", injector())
    part_block("accelerator_target_chamber", target_chamber())
    controller("fission_reactor", controller_front(trefoil_placard), controller_top(), lead_casing())
    controller("particle_accelerator", controller_front(orbit_placard), controller_top(), casing())
    print("structures written")
