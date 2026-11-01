"""Machine & cable glue assets (DESIGN.md §7–8).

* GeckoLib-rendered machines: a particle-only block model, a blockstate covering facing x active (the geo renderer
  draws the block, so every variant uses the particle model), and an item definition using GeckoLib's special item
  renderer over a base model that supplies the standard block display transforms.
* Cables: per tier a core model and an arm model (arm points up; the multipart blockstate rotates it like vanilla's
  lightning rod), an inventory model (straight east-west run) and one 16x16 texture holding every face.

Run after adding a machine or cable tier; the geo models themselves come from the per-machine scripts.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402,F401

from gb import atlas as A  # noqa: E402
from gb import machine_kit as K  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb import paths  # noqa: E402
from gb.model import write_json  # noqa: E402
from gb.palette import fixed  # noqa: E402
from gb.tex import Tex  # noqa: E402

GECKO_MACHINES = ["combustion_generator", "ore_crusher", "electrolytic_separator", "water_pump", "circuit_fabricator",
                  "fusion_fuel_synthesizer", "canning_machine", "diesel_generator", "rtg", "battery_charger", "capacitor_bank",
                  "advanced_capacitor_bank", "superconducting_capacitor_bank", "creative_capacitor_bank"]
TALL_GECKO_MACHINES = ["arc_furnace", "industrial_press", "cryogenic_condenser", "crystal_puller", "gas_centrifuge", "exotic_matter_fabricator",
                       "decontamination_shower"]

# --- GeckoLib machines -------------------------------------------------------------------------------------------------


def machine_particle() -> None:
    """Shared break-particle texture: a patch of the machines' olive paint in its bolted steel frame (the material
    library, as the machines' own finish)."""
    from gb import panels as PN
    img = PN.face(K.OLIVE, style="raised", seed=5)
    M.to_tex(img).save(paths.TEXTURES / "block" / "machine_particle.png")


# Two-block-tall machines shrink in the GUI and hands so the whole model fits (GeckoLib draws it from the base).
TALL_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -2.6, 0], "scale": [0.38, 0.38, 0.38]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 1, 0], "scale": [0.18, 0.18, 0.18]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, -3.2, 0], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 0.5, 0], "scale": [0.28, 0.28, 0.28]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 0.5, 0], "scale": [0.28, 0.28, 0.28]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -2, 0], "scale": [0.3, 0.3, 0.3]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, -2, 0], "scale": [0.3, 0.3, 0.3]},
}


def gecko_machine(name: str, tall: bool = False) -> None:
    particle = {"textures": {"particle": "groundburst:block/machine_particle"}}
    write_json(paths.MODELS / "block" / f"{name}.json", particle)
    variants = {}
    for facing in ("north", "east", "south", "west"):
        for active in ("false", "true"):
            if tall:
                for half in ("lower", "upper"):
                    variants[f"active={active},facing={facing},half={half}"] = {"model": f"groundburst:block/{name}"}
            else:
                variants[f"active={active},facing={facing}"] = {"model": f"groundburst:block/{name}"}
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": variants})
    item_model = {"parent": "minecraft:block/block", "textures": {"particle": "groundburst:block/machine_particle"}}
    if tall:
        item_model["display"] = TALL_DISPLAY
    write_json(paths.MODELS / "item" / f"{name}.json", item_model)
    write_json(paths.ASSETS / "items" / f"{name}.json",
               {"model": {"type": "minecraft:special", "base": f"groundburst:item/{name}", "model": {"type": "geckolib:geckolib"}}})


# --- cables -------------------------------------------------------------------------------------------------------------
# Texture layout (16x16): arm side x0..4 y0..6 · core face x6..11 y0..5 · arm end x12..16 y0..4 ·
# insulator side x0..6 y8..9 · insulator face x8..14 y8..14.

KELP = fixed("kelp_wrap", "#141a0e", "#222c16", "#33401f", "#47552a", "#5d6c36", "#788946", "#9aac64")
HV_ORANGE = fixed("hv_orange", "#3a1404", "#6a2608", "#a03c0c", "#d05812", "#ec7a26", "#f8a456", "#ffd6a4")


def cable_texture(tier: str) -> Tex:
    """The tier's 16x16 sheet, painted with the material library (tools/gb/atlas.py; the remaster after the Fission
    Reactor rework): each region filled from its material, round cables shaded across their width, details on top."""
    t = Tex(16, 16)
    side, core, end = (0, 0, 4, 6), (6, 0, 11, 5), (12, 0, 16, 4)
    col, row = A.grid(side)
    if tier == "copper_cable":
        # Copper bundle under a spiral wrap of dried-kelp fibre: copper shows between the turns.
        copper = K.metal(P.COPPER, 0.62)
        kelp = M.Material(ramp=K.ramp_of(KELP), tone=0.55, mottle=0.06, grain=0.07, dither=0.8, edge_light=0.0, edge_dark=0.0, ao=0.0,
                          grime=0.0, dust=0.0, despeckle=False)
        A.fill(t, side, copper, 1, A.ROUND4)
        A.fill(t, side, kelp, 2, A.ROUND4, mask=(col + row) % 3 == 0)
        A.fill(t, core, kelp, 3)                                 # core: a knotted wrap with a copper splice band
        A.fill(t, (6, 2, 11, 3), copper, 4)
        t.px(6, 2, P.COPPER.highlight)
        A.rim(t, core)
        A.fill(t, end, K.metal(P.COPPER, 0.45), 5)               # end: bare copper strands
        t.px(13, 1, P.COPPER.highlight)
        t.px(14, 2, P.COPPER.light)
    elif tier == "insulated_cable":
        # Black rubber sheath, a yellow ID band.
        rubber = M.rubber(tone=0.5)
        A.fill(t, side, rubber, 1, A.ROUND4)
        A.fill(t, (0, 4, 4, 5), K.YELLOW, 2, A.ROUND4)
        A.fill(t, core, rubber, 3)
        A.rim(t, core)
        A.fill(t, (7, 1, 10, 4), M.rubber(tone=0.7), 4)
        t.px(8, 2, P.HAZARD_YELLOW[4])
        A.fill(t, end, M.rubber(tone=0.3), 5)
        A.fill(t, (13, 1, 15, 3), K.metal(P.COPPER, 0.55), 6)   # conductor visible at the cut end
    else:  # hv_cable
        # Grey armoured sheath with HV-orange marker stripes; ceramic insulator discs on the arms.
        armour = K.paint_of(P.GUNMETAL, 0.6, 0.15)
        hv = K.paint_of(HV_ORANGE, 0.58, 0.1)
        A.fill(t, side, armour, 1, A.ROUND4)
        A.fill(t, (0, 1, 4, 2), hv, 2, A.ROUND4)
        A.fill(t, core, armour, 3)
        A.rim(t, core)
        A.fill(t, (7, 1, 10, 4), hv, 4)
        A.fill(t, end, K.paint_of(P.GUNMETAL, 0.4, 0.0), 5)
        A.fill(t, (13, 1, 15, 3), K.metal(P.COPPER, 0.55), 6)
        A.fill(t, (0, 8, 6, 9), K.CERAMIC, 7)                    # insulator rim
        A.fill(t, (8, 8, 14, 14), K.CERAMIC, 8)                  # insulator face: ribbed disc
        A.rim(t, (8, 8, 14, 14), 0.8)
        A.fill(t, (10, 10, 12, 12), armour, 9)
    t.save(paths.TEXTURES / "block" / f"{tier}.png")
    return t


def _faces(sides: dict[str, list[float]], end: list[float] | None, end_dirs: tuple[str, ...], cull: str | None, tex: str = "#cable") -> dict:
    faces = {d: {"uv": uv, "texture": tex} for d, uv in sides.items()}
    if end is not None:
        for d in end_dirs:
            faces[d] = {"uv": end, "texture": tex}
            if cull and d == cull:
                faces[d]["cullface"] = cull
    return faces


def cable_models(tier: str) -> None:
    tex = {"cable": f"groundburst:block/{tier}", "particle": f"groundburst:block/{tier}"}
    arm_side = [0, 0, 4, 6]
    core_uv = [6, 0, 11, 5]
    end_uv = [12, 0, 16, 4]
    core = {"from": [5.5, 5.5, 5.5], "to": [10.5, 10.5, 10.5],
            "faces": {d: {"uv": core_uv, "texture": "#cable"} for d in ("north", "south", "east", "west", "up", "down")}}
    write_json(paths.MODELS / "block" / f"{tier}_core.json", {"textures": tex, "elements": [core]})

    arm = {"from": [6, 10.5, 6], "to": [10, 16, 10],
           "faces": _faces({d: arm_side for d in ("north", "south", "east", "west")}, end_uv, ("up",), "up")}
    elements = [arm]
    if tier == "hv_cable":
        ins_side = [0, 8, 6, 9]
        ins_face = [8, 8, 14, 14]
        elements.append({"from": [5, 13, 5], "to": [11, 14, 11],
                         "faces": {**{d: {"uv": ins_side, "texture": "#cable"} for d in ("north", "south", "east", "west")},
                                   "up": {"uv": ins_face, "texture": "#cable"}, "down": {"uv": ins_face, "texture": "#cable"}}})
    write_json(paths.MODELS / "block" / f"{tier}_arm.json", {"textures": tex, "elements": elements})

    # Inventory: a straight east-west run through the core, built from two arm-length segments.
    def seg(x0: float, x1: float) -> dict:
        side = {"uv": [0, 0, 4, x1 - x0], "texture": "#cable", "rotation": 90}
        return {"from": [x0, 6, 6], "to": [x1, 10, 10],
                "faces": {"north": side, "south": side, "up": side, "down": side,
                          "east": {"uv": end_uv, "texture": "#cable"}, "west": {"uv": end_uv, "texture": "#cable"}}}
    inv = [seg(0, 5.5), seg(10.5, 16), core]
    if tier == "hv_cable":
        for x0 in (2, 13):
            inv.append({"from": [x0, 5, 5], "to": [x0 + 1, 11, 11],
                        "faces": {**{d: {"uv": [0, 8, 6, 9], "texture": "#cable", "rotation": 90} for d in ("north", "south", "up", "down")},
                                  "east": {"uv": [8, 8, 14, 14], "texture": "#cable"}, "west": {"uv": [8, 8, 14, 14], "texture": "#cable"}}})
    display = {"gui": {"rotation": [30, 225, 0], "scale": [0.95, 0.95, 0.95]},
               "fixed": {"rotation": [0, 90, 0], "scale": [0.8, 0.8, 0.8]}}
    write_json(paths.MODELS / "item" / f"{tier}.json",
               {"parent": "minecraft:block/block", "textures": tex, "elements": inv, "display": display})
    write_json(paths.ASSETS / "items" / f"{tier}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:item/{tier}"}})

    rot = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "east": {"x": 90, "y": 90},
           "south": {"x": 90, "y": 180}, "west": {"x": 90, "y": 270}}
    multipart = [{"apply": {"model": f"groundburst:block/{tier}_core"}}]
    for d, r in rot.items():
        multipart.append({"when": {d: "connected"}, "apply": {"model": f"groundburst:block/{tier}_arm", **r}})
    write_json(paths.ASSETS / "blockstates" / f"{tier}.json", {"multipart": multipart})


CABLES = ["copper_cable", "insulated_cable", "hv_cable"]

if __name__ == "__main__":
    machine_particle()
    for m in GECKO_MACHINES:
        gecko_machine(m)
    for m in TALL_GECKO_MACHINES:
        gecko_machine(m, tall=True)
    for c in CABLES:
        cable_texture(c)
        cable_models(c)
    print("machines + cables written")
