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

from gz import palette as P  # noqa: E402
from gz import paths  # noqa: E402
from gz.model import write_json  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402

GECKO_MACHINES = ["combustion_generator", "ore_crusher", "electrolytic_separator", "water_pump", "circuit_fabricator",
                  "fusion_fuel_synthesizer", "diesel_generator", "rtg", "battery_charger", "capacitor_bank",
                  "advanced_capacitor_bank", "superconducting_capacitor_bank"]
TALL_GECKO_MACHINES = ["arc_furnace", "industrial_press", "cryogenic_condenser", "gas_centrifuge", "exotic_matter_fabricator",
                       "decontamination_shower"]

# --- GeckoLib machines -------------------------------------------------------------------------------------------------


def machine_particle() -> None:
    """Shared break-particle texture: a patch of the machines' olive paint with a rivet and some primer chips."""
    t = Tex(16, 16)
    t.panel(0, 0, 16, 16, P.OLIVE, base=3, style="raised")
    t.outline(2, 2, 12, 12, P.OLIVE.shadow)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        t.rivet(x, y, P.OLIVE)
    t.chip_edges(0, 0, 16, 16, P.PRIMER_RED.shadow, seed=5, chips=3, edges="tb", max_len=2)
    t.save(paths.TEXTURES / "block" / "machine_particle.png")


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
    particle = {"textures": {"particle": "groundzero:block/machine_particle"}}
    write_json(paths.MODELS / "block" / f"{name}.json", particle)
    variants = {}
    for facing in ("north", "east", "south", "west"):
        for active in ("false", "true"):
            if tall:
                for half in ("lower", "upper"):
                    variants[f"active={active},facing={facing},half={half}"] = {"model": f"groundzero:block/{name}"}
            else:
                variants[f"active={active},facing={facing}"] = {"model": f"groundzero:block/{name}"}
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": variants})
    item_model = {"parent": "minecraft:block/block", "textures": {"particle": "groundzero:block/machine_particle"}}
    if tall:
        item_model["display"] = TALL_DISPLAY
    write_json(paths.MODELS / "item" / f"{name}.json", item_model)
    write_json(paths.ASSETS / "items" / f"{name}.json",
               {"model": {"type": "minecraft:special", "base": f"groundzero:item/{name}", "model": {"type": "geckolib:geckolib"}}})


# --- cables -------------------------------------------------------------------------------------------------------------
# Texture layout (16x16): arm side x0..4 y0..6 · core face x6..11 y0..5 · arm end x12..16 y0..4 ·
# insulator side x0..6 y8..9 · insulator face x8..14 y8..14.

KELP = fixed("kelp_wrap", "#141a0e", "#222c16", "#33401f", "#47552a", "#5d6c36", "#788946", "#9aac64")
HV_ORANGE = fixed("hv_orange", "#3a1404", "#6a2608", "#a03c0c", "#d05812", "#ec7a26", "#f8a456", "#ffd6a4")


def cable_texture(tier: str) -> Tex:
    t = Tex(16, 16)
    if tier == "copper_cable":
        # Copper bundle under a spiral wrap of dried-kelp fibre: copper shows between the turns.
        for y in range(6):
            for x in range(4):
                wrap = (x + y) % 3 == 0
                t.px(x, y, KELP[4 if x == 0 else 3 if x < 3 else 2] if wrap else P.COPPER[5 if x == 0 else 4 if x < 3 else 3])
        t.rect(6, 0, 5, 5, KELP[3])                              # core: a knotted wrap with a copper splice band
        t.hline(6, 2, 5, P.COPPER.light)
        t.px(6, 2, P.COPPER.highlight)
        t.outline(6, 0, 5, 5, KELP[2])
        t.rect(12, 0, 4, 4, P.COPPER[3])                         # end: bare copper strands
        t.px(13, 1, P.COPPER.highlight)
        t.px(14, 2, P.COPPER.light)
    elif tier == "insulated_cable":
        # Black rubber sheath, a yellow ID band, a soft highlight down the lit edge.
        r = P.RUBBER
        for y in range(6):
            t.px(0, y, r[5])
            t.px(1, y, r[4])
            t.px(2, y, r[3])
            t.px(3, y, r[2])
        t.hline(0, 4, 4, P.HAZARD_YELLOW[4])
        t.px(3, 4, P.HAZARD_YELLOW[3])
        t.rect(6, 0, 5, 5, r[3])
        t.outline(6, 0, 5, 5, r[2])
        t.rect(7, 1, 3, 3, r[4])
        t.px(8, 2, P.HAZARD_YELLOW[4])
        t.rect(12, 0, 4, 4, r[2])
        t.rect(13, 1, 2, 2, P.COPPER[4])                        # conductor visible at the cut end
    else:  # hv_cable
        # Grey armoured sheath with HV-orange marker stripes; ceramic insulator discs on the arms.
        s = P.GUNMETAL
        for y in range(6):
            for x in range(4):
                t.px(x, y, s[5 if x == 0 else 4 if x < 3 else 3])
        t.hline(0, 1, 4, HV_ORANGE[4])
        t.px(0, 1, HV_ORANGE[5])
        t.px(3, 1, HV_ORANGE[3])
        t.rect(6, 0, 5, 5, s[3])
        t.outline(6, 0, 5, 5, s[2])
        t.rect(7, 1, 3, 3, HV_ORANGE[4])
        t.px(7, 1, HV_ORANGE[5])
        t.rect(12, 0, 4, 4, s[2])
        t.rect(13, 1, 2, 2, P.COPPER[4])
        c = P.WHITE_CERAMIC
        t.hline(0, 8, 6, c[4])                                   # insulator rim
        t.px(0, 8, c[5])
        t.rect(8, 8, 6, 6, c[4])                                 # insulator face: ribbed disc
        t.outline(8, 8, 6, 6, c[3])
        t.rect(10, 10, 2, 2, s[3])
        t.px(9, 9, c[5])
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
    tex = {"cable": f"groundzero:block/{tier}", "particle": f"groundzero:block/{tier}"}
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
    write_json(paths.ASSETS / "items" / f"{tier}.json", {"model": {"type": "minecraft:model", "model": f"groundzero:item/{tier}"}})

    rot = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "east": {"x": 90, "y": 90},
           "south": {"x": 90, "y": 180}, "west": {"x": 90, "y": 270}}
    multipart = [{"apply": {"model": f"groundzero:block/{tier}_core"}}]
    for d, r in rot.items():
        multipart.append({"when": {d: "connected"}, "apply": {"model": f"groundzero:block/{tier}_arm", **r}})
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
