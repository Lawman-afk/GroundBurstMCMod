"""Fluid pipes (DESIGN.md §6.4) and item pipes (§6.5), three tiers each like the cables: texture, multipart block
models, item model each.

Look: a 6 px pipe with a bolted flange at every joint, meeting the next pipe flange-to-flange at the block edge, and
a squat bolted junction box at the centre. Copper: copper tube, brass fittings. Steel: brushed steel, gunmetal
fittings. Pressurized: dark armoured tube with red high-pressure fittings. Same multipart scheme as the cables: a core model
plus one arm model rotated to each connected side; a side set to pull (Wrench) also gets a hazard-striped collar.

Item pipes share the shape but read differently at a glance: the tube is a glass sight-tube in a metal frame (you see
the light through it), with chevrons on the walls, and the junction box carries an item-slot window. Iron: pale iron
frame, steel fittings. Steel: gunmetal frame, yellow fittings. Pneumatic: brass frame, copper fittings, blue-tinted
glass.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import atlas as A  # noqa: E402
from gz import machine_kit as K  # noqa: E402
from gz import materials as M  # noqa: E402
from gz import palette as P  # noqa: E402
from gz import paths  # noqa: E402
from gz.model import write_json  # noqa: E402
from gz.tex import Tex  # noqa: E402

# id -> (tube ramp, fittings ramp, bolt colour)
TIERS = {
    "copper_fluid_pipe": (P.COPPER, P.BRASS, P.BRASS[6]),
    "fluid_pipe": (P.STEEL, P.GUNMETAL, P.STEEL[5]),
    "pressurized_fluid_pipe": (P.BLACK_PAINT, P.WARNING_RED, P.STEEL[5]),
}

# id -> (frame ramp, fittings ramp, glass colour)
ITEM_TIERS = {
    "iron_item_pipe": (P.STEEL, P.STEEL, (196, 214, 222, 255)),
    "item_pipe": (P.GUNMETAL, P.HAZARD_YELLOW, (186, 206, 214, 255)),
    "pneumatic_item_pipe": (P.BRASS, P.COPPER, (150, 196, 232, 255)),
}

# Texture regions (u0, v0, u1, v1).
ARM_SIDE = (0, 0, 6, 4)       # pipe wall, 6 across x 3.5 long (stretched to 4)
CORE = (7, 0, 14, 7)          # junction box face
FLANGE_SIDE = (0, 8, 7, 9)    # flange rim
FLANGE_END = (8, 8, 15, 15)   # flange face with the bore
COLLAR_FACE = (0, 9, 7, 16)   # pull collar ring (top/bottom)
COLLAR_SIDE = (8, 15, 15, 16) # pull collar rim: hazard stripes mark an extracting side


def _metal_or_paint(ramp):
    """Copper, brass and steel stay bare metal; everything else is paint."""
    return K.metal(ramp, 0.6) if ramp in (P.COPPER, P.BRASS, P.STEEL) else K.paint_of(ramp, 0.55, 0.15)


def texture(NAME: str, S, G, BOLT) -> Tex:
    """The tier's 16x16 sheet, painted with the material library (tools/gz/atlas.py; the remaster after the Fission
    Reactor rework): a round tube, a bevelled junction box with corner bolts and a raised boss, the flange with its
    bolts round the bore, the hazard-striped pull collar."""
    t = Tex(16, 16)
    tube, fit = _metal_or_paint(S), _metal_or_paint(G)
    A.fill(t, ARM_SIDE, tube, 1, A.ROUND6)
    A.fill(t, (7, 0, 14, 7), fit, 2)
    A.rim(t, (7, 0, 14, 7))
    for x, y in ((8, 1), (12, 1), (8, 5), (12, 5)):
        t.px(x, y, BOLT)
    A.scale(t, (9, 2, 12, 5), 1.18)                            # raised boss
    t.px(11, 4, G[2])
    A.fill(t, (0, 8, 7, 9), fit, 3)                            # flange rim
    A.scale(t, (0, 8, 1, 9), 1.2)
    A.fill(t, (8, 8, 15, 15), fit, 4)                          # flange face
    A.rim(t, (8, 8, 15, 15), 0.75)
    t.rect(10, 10, 3, 3, P.BLACK_PAINT.deep)                   # bore
    t.px(10, 10, G[1])
    for x, y in ((9, 9), (13, 9), (9, 13), (13, 13)):
        t.px(x, y, BOLT)                                       # bolts
    # Pull collar: hazard-striped band, so an extracting side reads at a glance.
    A.fill(t, (8, 15, 15, 16), M.stripes(width=2, wear=0.0), 5)
    A.fill(t, (0, 9, 7, 16), K.YELLOW, 6)
    A.rim(t, (0, 9, 7, 16), 0.72)
    A.fill(t, (2, 11, 5, 14), K.BLACK, 7)
    t.save(paths.TEXTURES / "block" / f"{NAME}.png")
    return t


def item_texture(NAME: str, S, G, GLASS) -> Tex:
    """Same regions as a fluid pipe's texture, drawn as a glass sight-tube in a metal frame."""
    t = texture(NAME, S, G, S[6])
    frame = _metal_or_paint(S)
    A.fill(t, (0, 0, 1, 4), frame, 8)
    A.fill(t, (5, 0, 6, 4), frame, 9)
    A.scale(t, (5, 0, 6, 4), 0.72)
    for y in range(4):
        for x in range(1, 5):
            t.px(x, y, GLASS)
    shade = (GLASS[0] - 40, GLASS[1] - 40, GLASS[2] - 30, 255)
    for x, y in ((1, 1), (2, 2), (3, 2), (4, 1)):
        t.px(x, y, shade)                                       # chevron: items run along the tube
    t.hline(1, 0, 4, (min(255, GLASS[0] + 30), min(255, GLASS[1] + 30), min(255, GLASS[2] + 25), 255))   # glint
    # Junction box: an item-slot window in place of the raised boss.
    t.rect(9, 2, 3, 3, (40, 44, 50, 255))
    t.hline(9, 4, 3, S[5])
    t.vline(11, 2, 3, S[5])
    t.save(paths.TEXTURES / "block" / f"{NAME}.png")
    return t


def face(uv, rotation: int = 0, cull: str | None = None) -> dict:
    f = {"uv": list(uv), "texture": "#pipe"}
    if rotation:
        f["rotation"] = rotation
    if cull:
        f["cullface"] = cull
    return f


def models(NAME: str) -> None:
    tex = {"pipe": f"groundzero:block/{NAME}", "particle": f"groundzero:block/{NAME}"}
    core = {"from": [4.5, 4.5, 4.5], "to": [11.5, 11.5, 11.5],
            "faces": {d: face(CORE) for d in ("north", "south", "east", "west", "up", "down")}}
    write_json(paths.MODELS / "block" / f"{NAME}_core.json", {"textures": tex, "elements": [core]})
    # Arm pointing up: pipe from the core to the flange, flange flush with the block edge.
    pipe = {"from": [5, 11.5, 5], "to": [11, 15, 11],
            "faces": {d: face(ARM_SIDE) for d in ("north", "south", "east", "west")}}
    flange = {"from": [4.5, 15, 4.5], "to": [11.5, 16, 11.5],
              "faces": {**{d: face(FLANGE_SIDE) for d in ("north", "south", "east", "west")},
                        "up": face(FLANGE_END, cull="up"), "down": face(FLANGE_END)}}
    write_json(paths.MODELS / "block" / f"{NAME}_arm.json", {"textures": tex, "elements": [pipe, flange]})
    # Pull side: a striped collar round the arm, just inside the flange.
    collar = {"from": [4.7, 13.3, 4.7], "to": [11.3, 14.3, 11.3],
              "faces": {**{d: face(COLLAR_SIDE) for d in ("north", "south", "east", "west")},
                        "up": face(COLLAR_FACE), "down": face(COLLAR_FACE)}}
    write_json(paths.MODELS / "block" / f"{NAME}_pull.json", {"textures": tex, "elements": [collar]})

    rot = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "east": {"x": 90, "y": 90},
           "south": {"x": 90, "y": 180}, "west": {"x": 90, "y": 270}}
    multipart = [{"apply": {"model": f"groundzero:block/{NAME}_core"}}]
    for d, r in rot.items():
        multipart.append({"when": {d: "normal|pull"}, "apply": {"model": f"groundzero:block/{NAME}_arm", **r}})
        multipart.append({"when": {d: "pull"}, "apply": {"model": f"groundzero:block/{NAME}_pull", **r}})
    write_json(paths.ASSETS / "blockstates" / f"{NAME}.json", {"multipart": multipart})

    # Inventory: a straight east-west run through the junction box, flanged at both ends.
    def run(x0: float, x1: float) -> dict:
        return {"from": [x0, 5, 5], "to": [x1, 11, 11],
                "faces": {d: face(ARM_SIDE, rotation=90) for d in ("north", "south", "up", "down")}}

    def end_flange(x0: float) -> dict:
        return {"from": [x0, 4.5, 4.5], "to": [x0 + 1, 11.5, 11.5],
                "faces": {**{d: face(FLANGE_SIDE, rotation=90) for d in ("north", "south", "up", "down")},
                          "east": face(FLANGE_END), "west": face(FLANGE_END)}}
    elements = [run(1, 4.5), run(11.5, 15), end_flange(0), end_flange(15), core]
    display = {"gui": {"rotation": [30, 225, 0], "scale": [0.95, 0.95, 0.95]},
               "fixed": {"rotation": [0, 90, 0], "scale": [0.8, 0.8, 0.8]}}
    write_json(paths.MODELS / "item" / f"{NAME}.json", {"parent": "minecraft:block/block", "textures": tex, "elements": elements, "display": display})
    write_json(paths.ASSETS / "items" / f"{NAME}.json", {"model": {"type": "minecraft:model", "model": f"groundzero:item/{NAME}"}})


if __name__ == "__main__":
    for name, (tube, fit, bolt) in TIERS.items():
        texture(name, tube, fit, bolt)
        models(name)
    for name, (frame, fit, glass) in ITEM_TIERS.items():
        item_texture(name, frame, fit, glass)
        models(name)
    print("fluid and item pipes written")
