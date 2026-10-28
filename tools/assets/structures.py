"""Multiblock structural blocks (DESIGN.md §23.2) and controller faces: textures, block models, blockstates, item
definitions. Every part block has a `formed` property; formed variants use a particle-only model because the
controller draws the whole structure.

Faces are painted with the material library (tools/gb/panels.py, DESIGN.md §4.3; the remaster after slice 9b): a
brushed steel frame, bevelled and bolted, round painted or metal panels; the designs as they were.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402

from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb import panels as PN  # noqa: E402
from gb import paths  # noqa: E402
from gb.model import write_json  # noqa: E402
from gb.palette import fixed  # noqa: E402
from gb.tex import Tex  # noqa: E402

STEEL = P.STEEL
OLIVE = P.OLIVE
GUN = P.GUNMETAL
def _ramp(r) -> np.ndarray:
    return np.array([s[:3] for s in r.shades], dtype=np.float32)


OLIVE_PAINT = M.painted(M.OLIVE_RAMP, tone=0.5, wear=0.0)
LEAD_METAL = M.Material(ramp=_ramp(P.LEAD), tone=0.5, mottle=0.03, grain=0.02, dither=0.4, edge_light=0.0, edge_dark=0.0,
                        ao=0.0, grime=0.0, dust=0.0)
RED_PAINT = M.painted(M.RED_PAINT_RAMP, tone=0.4, wear=0.0, mottle=0.015, grain=0.015)
COPPER = M.Material(ramp=M.COPPER_RAMP, tone=0.55, mottle=0.02, grain=0.02, dither=0.4, edge_light=0.0, edge_dark=0.0, ao=0.0,
                    grime=0.0, dust=0.0, brushed=0.05)
POLISHED = M.steel(tone=0.7, brushed=0.08)
DARK_PAINT = M.painted(M.BLACK_RAMP, tone=0.45, wear=0.0)
BRASS = M.Material(ramp=_ramp(P.BRASS), tone=0.55, mottle=0.03, grain=0.03, dither=0.4, edge_light=0.0, edge_dark=0.0, ao=0.0,
                   grime=0.0, dust=0.0)
LEAD_GLASS = fixed("lead_glass", "#141a1e", "#1e262c", "#2a343c", "#36424c", "#4a5864", "#6a7a88", "#a4b4c0")


def frame(t: Tex, ramp=STEEL) -> None:
    """(Kept for older scripts.) 2-px bevelled steel frame round a 16x16 face with corner bolts."""
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
    """Machine Casing: an olive panel set into the steel frame, an access hatch in it (its seam, a lit top lip, two
    handles), a couple of paint chips showing the primer."""
    img = PN.face(OLIVE_PAINT, style="inset", seed=3)
    PN.groove(img, 4, 4, 8, 8)
    t = M.to_tex(img)
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
    """Item Port: yellow and black stripes in the frame round a dark opening, chevrons in and out."""
    img = PN.face(OLIVE_PAINT, style="inset", seed=5)
    PN.stripes(img, PN.area(2, 2, 12, 12), seed=5)
    PN.opening(img, 4, 4, 8, 8)
    t = M.to_tex(img)
    for x, y in ((6, 6), (7, 7), (6, 8)):
        t.px(x, y, P.NUCLEAR_AMBER[5])
    for x, y in ((10, 6), (9, 7), (10, 8)):
        t.px(x, y, P.NUCLEAR_AMBER[4])
    return t


FLUID_BLUE = fixed("fluid_blue", "#0c1a3a", "#12285a", "#1a3a7e", "#2452a6", "#3a6ec8", "#6a98e0", "#a8c8f4")


def fluid_port() -> Tex:
    """Like the Item Port, in blue and black stripes, with a drop in the opening: pipes connect here."""
    img = PN.face(OLIVE_PAINT, style="inset", seed=6)
    PN.stripes(img, PN.area(2, 2, 12, 12), M.painted(_ramp(FLUID_BLUE), tone=0.62, wear=0.0, dither=0.4), seed=6)
    PN.opening(img, 4, 4, 8, 8)
    t = M.to_tex(img)
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
    """A controller's face: a raised olive panel with its green CRT, status buttons, a dial, the placard of what it
    runs and a hazard strip along the bottom."""
    img = PN.face(OLIVE_PAINT, style="raised", seed=8)
    PN.stripes(img, PN.area(2, 13, 12, 1), seed=8)
    t = M.to_tex(img)
    t.rect(3, 3, 7, 5, P.BLACK_PAINT.deep)                    # green CRT
    t.outline(3, 3, 7, 5, GUN[2])
    t.hline(3, 7, 7, GUN[3])                                  # its bezel's lit lower lip
    for x in range(4, 9):
        t.px(x, 5 + (x % 2), P.INDICATOR_GREEN[3])
    t.px(4, 4, P.INDICATOR_GREEN[5])
    for i, c in enumerate((P.INDICATOR_GREEN.base, P.HAZARD_YELLOW.base, P.WARNING_RED.base)):
        t.px(11 + (i % 2) * 2, 4 + (i // 2) * 2, c)           # status buttons
    (placard or flask_placard)(t)
    t.rect(10, 9, 3, 3, GUN[2])                               # dial
    t.px(11, 10, P.WHITE_CERAMIC[6])
    t.px(12, 11, GUN[1])
    return t


def lead_casing() -> Tex:
    """Lead-lined casing: dull lead plates bolted into the steel frame, a seam across the middle."""
    img = PN.face(LEAD_METAL, style="inset", seed=11)
    col, row = PN.grid()
    M.darken(img, PN.area(2, 8, 12, 1), 0.62)
    M.lighten(img, PN.area(2, 9, 12, 1), 1.1, 4)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12), (3, 6), (12, 6)):
        PN.bolt(img, x, y)
    return M.to_tex(img)


def energy_port() -> Tex:
    """Energy Port: red and black stripes round a dark opening with a lightning bolt in it."""
    img = PN.face(OLIVE_PAINT, style="inset", seed=12)
    PN.stripes(img, PN.area(2, 2, 12, 12), M.painted(M.RED_PAINT_RAMP, tone=0.55, wear=0.0, dither=0.4), seed=12)
    PN.opening(img, 4, 4, 8, 8)
    t = M.to_tex(img)
    for x, y in ((8, 5), (7, 6), (6, 7), (7, 7), (8, 7), (9, 7), (8, 8), (7, 9), (6, 10)):
        t.px(x, y, P.HAZARD_YELLOW[5])                         # lightning bolt
    return t


def steel_plating() -> Tex:
    """Riveted steel deck plates with a raised diamond tread, a seam round each plate."""
    img = PN.tile(M.steel(tone=0.55, brushed=0.03, grain=0.008, mottle=0.02), 13)
    PN.bevel_box(img, 0, 0, 16, 16, True, 0.7)
    for y in range(2, 14, 4):
        for x in range(2 + (y // 4) % 2 * 2, 14, 4):
            M.lighten(img, PN.area(x, y, 1, 1), 1.35, 16)      # a tread bar: lit edge, shadow under it
            M.darken(img, PN.area(x + 1, y + 1, 1, 1), 0.55)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        PN.bolt(img, x, y)
    return M.to_tex(img)


def magnet_segment() -> Tex:
    """A dipole magnet: red yoke, copper windings either side of the dark beam pipe."""
    img = PN.face(RED_PAINT, frame=RED_PAINT, style="flat", seed=14)
    col, row = PN.grid()
    coil = ((col == 3) | (col == 4) | (col == 11) | (col == 12)) & (row >= 3) & (row <= 12)
    PN.paste(img, coil, COPPER, 14)
    M.darken(img, coil & (row % 2 == 0), 0.78)                  # the turns of the winding
    M.lighten(img, coil & ((col == 3) | (col == 11)), 1.12, 5)
    PN.opening(img, 6, 6, 4, 4, rim=M.steel(tone=0.62))         # the beam pipe
    return M.to_tex(img)


def injector() -> Tex:
    """The accelerator's injector: a gunmetal panel, the source glowing blue in its port, a hazard strip."""
    img = PN.face(PN.GUNMETAL, style="inset", seed=15)
    PN.opening(img, 5, 5, 6, 6)
    PN.stripes(img, PN.area(2, 13, 12, 1), seed=15)
    t = M.to_tex(img)
    t.outline(5, 5, 6, 6, P.CHERENKOV[3])
    t.rect(7, 7, 2, 2, P.CHERENKOV.shine)                     # source glow
    t.px(6, 6, P.CHERENKOV[2])
    return t


def target_chamber() -> Tex:
    """The target chamber: a steel-rimmed window onto trapped antimatter's glow."""
    img = PN.face(PN.GUNMETAL, style="inset", seed=16)
    PN.paste(img, PN.outline(4, 4, 8, 8), POLISHED, 16)
    PN.bevel_box(img, 4, 4, 8, 8, True, 0.8)
    t = M.to_tex(img)
    t.rect(5, 5, 6, 6, P.ANTIMATTER[2])
    t.rect(6, 6, 4, 4, P.ANTIMATTER[5])                       # trapped antimatter glow
    t.px(7, 7, P.ANTIMATTER.shine)
    t.hline(5, 5, 6, P.ANTIMATTER[1])                         # the rim's shadow on the glass
    return t


def toroidal_field_coil() -> Tex:
    """A toroidal field coil: a D-shaped loop of copper windings set in a gunmetal casing, cooling-line studs."""
    img = PN.face(M.painted(M.ARMOUR_RAMP, tone=0.38, wear=0.0), frame=PN.GUNMETAL, style="inset", seed=17)
    d_shape = ["..######....", ".#......##..", ".#........#.", ".#.........#", ".#.........#", ".#.........#",
               ".#.........#", ".#.........#", ".#........#.", ".#......##..", "..######....", "............"]
    mask = np.zeros((16, 16), dtype=bool)
    for yy, row_ in enumerate(d_shape):
        for xx, c in enumerate(row_):
            if c == "#":
                mask[2 + yy, 2 + xx] = True
    PN.paste(img, mask, COPPER, 17)
    col, row = PN.grid()
    M.darken(img, mask & ((col + row) % 2 == 0), 0.8)            # two strands of winding
    M.lighten(img, mask & (col == 3) & (row % 2 == 0) & (row >= 4) & (row <= 10), 1.25, 10)   # bright turns
    t = M.to_tex(img)
    t.px(8, 7, P.CRYO[4])                                     # liquid nitrogen stud in the bore
    t.px(8, 8, P.CRYO[3])
    return t


def vacuum_vessel() -> Tex:
    """Vacuum vessel casing: polished steel plates round a bolted flange ring."""
    img = PN.face(POLISHED, style="inset", seed=18)
    PN.bevel_box(img, 4, 4, 8, 8, True, 0.9)                    # the flange, raised
    PN.bevel_box(img, 5, 5, 6, 6, False, 0.7)                   # and its bore
    M.darken(img, PN.area(6, 6, 4, 4), 0.82)
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11), (7, 4), (4, 7), (11, 8), (8, 11)):
        PN.bolt(img, x, y)                                     # flange bolts
    return M.to_tex(img)


def central_solenoid() -> Tex:
    """Central solenoid: tight copper windings between steel end bands."""
    img = PN.tile(COPPER, 19)
    col, row = PN.grid()
    M.darken(img, (row % 2 == 0) & (col >= 2) & (col <= 13), 0.82)          # the turns
    glint = np.zeros((16, 16), dtype=bool)
    for y in range(16):
        glint[y, 2 + (y * 5) % 12] = True
    M.lighten(img, glint, 1.25, 10)                                         # glints along the turns
    band = (col <= 1) | (col >= 14)
    PN.paste(img, band, M.steel(tone=0.55, brushed=0.05), 19)
    M.lighten(img, col == 1, 1.12, 4)
    M.darken(img, col == 14, 0.78)
    return M.to_tex(img)


def containment_emitter() -> Tex:
    """Containment emitter: a dark field projector with a violet lens in a brass ring, brass mount bolts."""
    img = PN.face(DARK_PAINT, frame=PN.GUNMETAL, style="inset", seed=20)
    PN.paste(img, PN.outline(4, 4, 8, 8), BRASS, 20)
    PN.bevel_box(img, 4, 4, 8, 8, True, 0.8)
    t = M.to_tex(img)
    t.rect(5, 5, 6, 6, P.SINGULARITY[2])
    t.rect(6, 6, 4, 4, P.SINGULARITY[4])
    t.rect(7, 7, 2, 2, P.ANTIMATTER[6])                             # lens glow
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        t.px(x, y, P.BRASS[5])
        t.px(x + 1, y + 1, P.BLACK_PAINT.deep)
    return t


def core_cradle() -> Tex:
    """Core cradle: a violet gimbal ring round an empty black socket, its brass axle across."""
    img = PN.face(DARK_PAINT, frame=PN.GUNMETAL, style="inset", seed=21)
    M.darken(img, PN.area(2, 2, 12, 12), 0.45)                     # the socket, deep
    t = M.to_tex(img)
    ring_px = [(5, 3), (6, 3), (7, 3), (8, 3), (9, 3), (10, 3), (4, 4), (11, 4), (3, 5), (12, 5), (3, 6), (12, 6), (3, 7), (12, 7),
               (3, 8), (12, 8), (3, 9), (12, 9), (3, 10), (12, 10), (4, 11), (11, 11), (5, 12), (6, 12), (7, 12), (8, 12), (9, 12), (10, 12)]
    for x, y in ring_px:
        t.px(x, y, P.SINGULARITY[4] if y < 8 else P.SINGULARITY[3])
    for x, y in ((6, 3), (7, 3), (3, 6)):
        t.px(x, y, P.SINGULARITY[5])                                # the ring's lit top
    t.hline(6, 7, 4, P.BRASS[4])                                    # gimbal axle
    t.hline(6, 8, 4, P.BRASS[2])
    return t


def torus_placard(t: Tex) -> None:
    """Fusion Reactor: a glowing plasma ring."""
    t.rect(4, 9, 4, 4, P.BLACK_PAINT[1])
    t.outline(4, 9, 4, 4, P.ANTIMATTER[5])
    t.px(5, 10, P.BLACK_PAINT[1])
    t.px(4, 9, P.BLACK_PAINT[1])
    t.px(7, 12, P.BLACK_PAINT[1])


def singularity_placard(t: Tex) -> None:
    """Singularity Reactor: a black core inside a violet ring."""
    t.rect(4, 9, 4, 4, P.SINGULARITY[3])
    t.rect(5, 10, 2, 2, P.BLACK_PAINT.deep)
    t.px(4, 9, P.SINGULARITY[5])


def controller_top() -> Tex:
    """A controller's top: the casing with a louvred vent."""
    img = PN.face(OLIVE_PAINT, style="inset", seed=22)
    PN.paste(img, PN.area(4, 4, 8, 8), PN.GUNMETAL, 22)
    PN.louvres(img, 4, 4, 8, 8)
    PN.bevel_box(img, 4, 4, 8, 8, False, 0.8)
    return M.to_tex(img)


def particle_model(tex: str) -> dict:
    return {"textures": {"particle": tex}}


def part_block(name: str, tex: Tex, translucent: bool = False, cutout: bool = False) -> None:
    tex.save(paths.TEXTURES / "block" / f"{name}.png")
    model = {"parent": "minecraft:block/cube_all", "textures": {"all": f"groundburst:block/{name}"}}
    if translucent:
        model["render_type"] = "minecraft:translucent"
    elif cutout:
        model["render_type"] = "minecraft:cutout"
    write_json(paths.MODELS / "block" / f"{name}.json", model)
    write_json(paths.MODELS / "block" / f"{name}_formed.json", particle_model(f"groundburst:block/{name}"))
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": {
        "formed=false": {"model": f"groundburst:block/{name}"},
        "formed=true": {"model": f"groundburst:block/{name}_formed"},
    }})
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:block/{name}"}})


def controller(name: str, front: Tex, top: Tex, side: Tex, hides: bool = True) -> None:
    """A controller block. {hides}: whether its structure's model draws it once formed (the Fission Reactor, built of
    ordinary blocks, keeps its controller as it is)."""
    front.save(paths.TEXTURES / "block" / f"{name}_front.png")
    top.save(paths.TEXTURES / "block" / f"{name}_top.png")
    side.save(paths.TEXTURES / "block" / f"{name}_side.png")
    write_json(paths.MODELS / "block" / f"{name}.json", {
        "parent": "minecraft:block/orientable",
        "textures": {"front": f"groundburst:block/{name}_front", "side": f"groundburst:block/{name}_side",
                     "top": f"groundburst:block/{name}_top"}})
    formed_model = f"groundburst:block/{name}_formed" if hides else f"groundburst:block/{name}"
    if hides:
        write_json(paths.MODELS / "block" / f"{name}_formed.json", particle_model(f"groundburst:block/{name}_side"))
    else:
        (paths.MODELS / "block" / f"{name}_formed.json").unlink(missing_ok=True)
    variants = {}
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    for facing, y in rot.items():
        for active in ("false", "true"):
            turn = {"y": y} if y else {}
            variants[f"active={active},facing={facing},formed=false"] = {"model": f"groundburst:block/{name}", **turn}
            variants[f"active={active},facing={facing},formed=true"] = {"model": formed_model, **({} if hides else turn)}
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": variants})
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:block/{name}"}})


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
    # Slice 8.
    part_block("toroidal_field_coil", toroidal_field_coil())
    part_block("vacuum_vessel_casing", vacuum_vessel())
    part_block("central_solenoid", central_solenoid())
    part_block("containment_emitter", containment_emitter())
    part_block("core_cradle", core_cradle())
    controller("fusion_reactor", controller_front(torus_placard), controller_top(), casing())
    controller("singularity_reactor", controller_front(singularity_placard), controller_top(), casing())
    print("structures written")
