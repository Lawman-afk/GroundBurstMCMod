"""Machine GUI textures + the matching Java layout constants (src/.../content/machine/MachineLayouts.java).

One source of truth: LAYOUTS below drives both the drawn texture and the slot/widget positions the menu and
screen use. Military/industrial style: riveted gunmetal panels, recessed slots, amber phosphor gauges on dark
glass, hazard trim. Texture: 256x256, window at (0,0), fill sprites to the right of it (x >= 176).
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gb import palette as P  # noqa: E402
from gb import paths  # noqa: E402
from gb.palette import fixed, mix  # noqa: E402
from gb.tex import Tex  # noqa: E402

W, H = 176, 166
PANEL = P.GUNMETAL
GLASS = fixed("crt_glass", "#05080a", "#0a1013", "#10181c", "#162126", "#1e2c33", "#2a3c44", "#3c525c")
AMBER = fixed("phosphor_amber", "#3a1c02", "#6a3404", "#a05408", "#d8800e", "#ffa826", "#ffc864", "#ffe6b0")

# Sprite positions (shared by every machine texture).
ENERGY_FILL = (180, 0)        # 12 x 52
PROGRESS_FILL = (196, 0)      # 24 x 17
FLAME_FILL = (196, 20)        # 14 x 14
REDSTONE_ICONS = (180, 56)    # 3 icons, 16 x 16 each

LAYOUTS = {
    "ore_crusher": {
        "title": (8, 6),
        "slots": [("in", 56, 35), ("out_big", 116, 35), ("out", 146, 35)],
        "energy": (8, 20, 14, 46),
        "progress": (79, 34, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": None,
        "inventory": (8, 84),
    },
    "arc_furnace": {
        "title": (8, 6),
        "slots": [("in", 56, 25), ("in", 56, 47), ("out_big", 116, 35), ("out", 146, 35)],
        "energy": (8, 20, 14, 46),
        "progress": (79, 34, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": None,
        "inventory": (8, 84),
    },
    "industrial_press": {
        "title": (8, 6),
        "slots": [("in", 56, 25), ("die", 56, 47), ("out_big", 116, 35)],
        "energy": (8, 20, 14, 46),
        "progress": (79, 34, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": None,
        "inventory": (8, 84),
    },
    "electrolytic_separator": {
        "title": (8, 6),
        "slots": [],
        "energy": (8, 20, 14, 46),
        "tanks": [(40, 20, 12, 46, "H2O", False), (116, 20, 12, 42, None, True)],
        "progress": (79, 34, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": None,
        "inventory": (8, 84),
    },
    "cryogenic_condenser": {
        "title": (8, 6),
        "slots": [("membrane", 52, 35)],
        "energy": (8, 20, 14, 46),
        "tanks": [(116, 20, 12, 42, None, True)],
        "progress": (79, 34, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": None,
        "inventory": (8, 84),
    },
    "chemical_plant": {
        "title": (8, 6),
        "slots": [("in", 76, 20), ("in", 76, 38), ("in", 76, 56), ("out", 126, 25), ("out", 126, 47)],
        "energy": (8, 20, 14, 46),
        "tanks": [(26, 20, 12, 46, "H2O", False), (42, 20, 12, 42, None, True), (58, 20, 12, 42, None, True),
                  (150, 20, 12, 42, None, True)],
        "progress": (98, 34, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (101, 56, 18, 18),
        "readout": None,
        "inventory": (8, 84),
    },
    "water_pump": {
        "title": (8, 6),
        "slots": [],
        "energy": (8, 20, 14, 46),
        "tanks": [(82, 20, 12, 42, None, True)],
        "progress": None,
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": None,
        "inventory": (8, 84),
    },
    # Slice 5. Circuit Fabricator: four inputs in a 2x2 grid, liquid nitrogen coolant tank for the coils.
    "circuit_fabricator": {
        "title": (8, 6),
        "slots": [("in", 48, 26), ("in", 66, 26), ("in", 48, 44), ("in", 66, 44), ("out_big", 124, 35)],
        "energy": (8, 20, 14, 46),
        "tanks": [(28, 20, 12, 42, None, True)],
        "progress": (90, 34, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": None,
        "inventory": (8, 84),
    },
    # Gas Centrifuge: UF6 tank, two products, and a CRT showing the cascade size and speed bonus.
    "gas_centrifuge": {
        "title": (8, 6),
        "slots": [("out", 78, 25), ("out", 78, 47)],
        "energy": (8, 20, 14, 46),
        "tanks": [(28, 20, 12, 42, None, True)],
        "progress": (48, 34, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": (104, 21, 62, 25),
        "inventory": (8, 84),
    },
    "fusion_fuel_synthesizer": {
        "title": (8, 6),
        "slots": [("in", 56, 35), ("out_big", 116, 35)],
        "energy": (8, 20, 14, 46),
        "tanks": [(28, 20, 12, 42, None, True)],
        "progress": (79, 34, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": None,
        "inventory": (8, 84),
    },
    "exotic_matter_fabricator": {
        "title": (8, 6),
        "slots": [("in", 50, 20), ("in", 50, 38), ("in", 50, 56), ("out_big", 116, 35)],
        "energy": (8, 20, 14, 46),
        "progress": (79, 34, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": None,
        "inventory": (8, 84),
    },
    # Slice 6. Diesel Generator: fuel tank, burn flame, output readout.
    "diesel_generator": {
        "title": (8, 6),
        "slots": [],
        "energy": (8, 20, 14, 46),
        "tanks": [(30, 20, 12, 42, None, True)],
        "progress": None,
        "flame": (57, 34, 14, 14),
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": (88, 20, 78, 34),
        "inventory": (8, 84),
    },
    # Launch Silo (DESIGN.md §17.2): the missile and its warhead, the energy for a launch, and its state on the CRT.
    "launch_silo": {
        "title": (8, 6),
        "slots": [("missile", 34, 37)],
        "energy": (8, 20, 14, 46),
        "progress": None,
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": (58, 22, 108, 30),
        "inventory": (8, 84),
    },
    # Missile Silo (DESIGN.md §17.10): the missile and the energy for a launch as on the Launch Silo; a taller CRT for its
    # state, warhead and doors, and under it the DOORS button that opens or shuts the hatch (not while it counts down).
    "missile_silo": {
        "title": (8, 6),
        "slots": [("missile", 34, 37)],
        "energy": (8, 20, 14, 46),
        "progress": None,
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": (58, 17, 110, 38),
        "controls": [("doors", 58, 58, 30, 18, -1, 0, 0, "groundburst.gui.silo_doors")],
        "inventory": (8, 84),
    },
    # The Cruise Missile Launcher (DESIGN.md §17.11): a cruise missile for each of its two boxes, the energy for their
    # launch, and the CRT with its state, missiles and target.
    "cruise_launcher": {
        "title": (8, 6),
        "slots": [("missile", 28, 37), ("missile", 46, 37)],
        "energy": (8, 20, 14, 46),
        "progress": None,
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": (66, 17, 104, 38),
        "inventory": (8, 84),
    },
    # The superweapons (DESIGN.md §19.5): their ammunition (a SCUD Storm's nine Toxin SCUDs, a Nuclear Missile Silo's one
    # missile), the energy they charge from, and the CRT with the state (the charge's clock), ammunition and target.
    "superweapon": {
        "title": (8, 6),
        "slots": [("missile", 34, 37)],
        "energy": (8, 20, 14, 46),
        "progress": None,
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": (58, 17, 110, 38),
        "inventory": (8, 84),
    },
    # The superweapons' ammunition factories (DESIGN.md §19.5): their parts in two rows of three (the Arms Dealer four,
    # the War Factory five), the arrow, the finished round; the readout under them, tall enough for its two lines to
    # stack (a one-line glass set them side by side, half the width each, and cut the parts' names short).
    "arms_dealer": {
        "title": (8, 6),
        "slots": [("body", 30, 18), ("motor", 48, 18), ("warhead", 66, 18), ("warhead", 30, 36), ("out_big", 124, 27)],
        "energy": (8, 20, 14, 46),
        "progress": (92, 28, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": (30, 57, 118, 25),
        "inventory": (8, 84),
    },
    "war_factory": {
        "title": (8, 6),
        "slots": [("body", 30, 18), ("motor", 48, 18), ("core", 66, 18), ("casing", 30, 36), ("guidance", 48, 36), ("out_big", 124, 27)],
        "energy": (8, 20, 14, 46),
        "progress": (92, 28, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": (30, 57, 118, 25),
        "inventory": (8, 84),
    },
    # Ordnance Assembly Bench (Phase 7): core, casing and fuze in a row, the arrow, the finished warhead; the readout
    # under them; the fuze's setting (label and field, drawn by the screen) under that, then the Assemble / Take Apart
    # buttons (drawn by the screen) with the redstone button beside them. A right-hand column (slice 7c): the 3D preview
    # of what the parts make over a stats screen (both drawn into by the screen). (Its missile page went to the missile
    # assemblers, DESIGN.md §10.4.)
    "assembly_bench": {
        "title": (8, 6),
        "size": (288, 210),
        "inventory_width": 178,
        "preview": (183, 22, 94, 80),
        "stats": (183, 110, 94, 92),
        "slots": [("core", 32, 24), ("casing", 52, 24), ("fuze", 72, 24), ("out_big", 132, 24)],
        "energy": (8, 20, 14, 46),
        "progress": (97, 24, 24, 17),                # centred between the fuze slot and the output frame
        "flame": None,
        "status": (272, 5, 8, 8),
        "redstone": (151, 98, 18, 18),
        "readout": (34, 50, 134, 24),
        "inventory": (8, 128),
    },
    # The missile assemblers (DESIGN.md §10.4; once the bench's missile page): warhead, body, motor, guidance and module
    # in a row, the arrow, the missile; the readout, the guidance's accuracy, then Assemble / Take Apart (drawn by the
    # screen); the bench's right-hand column.
    "missile_assembler": {
        "title": (8, 6),
        "size": (288, 210),
        "inventory_width": 178,
        "preview": (183, 22, 94, 80),
        "stats": (183, 110, 94, 92),
        "slots": [("warhead", 26, 24), ("body", 44, 24), ("motor", 62, 24), ("guidance", 80, 24), ("module", 98, 24), ("out_big", 150, 24)],
        "energy": (8, 20, 14, 46),
        "progress": (119, 24, 24, 17),               # centred between the module slot and the output frame
        "flame": None,
        "status": (272, 5, 8, 8),
        "redstone": (151, 98, 18, 18),
        "readout": (34, 50, 134, 24),
        "inventory": (8, 128),
    },
    # ICBM Assembly Hall (Phase 8 slice 8c): the warhead's parts (primary, secondary, an optional cobalt jacket) over
    # the missile's (booster, optional guidance and module), the arrow, the finished missile; the readout under them,
    # then the Assemble / Take Apart buttons (drawn by the screen) with the redstone button beside them. The bench's
    # right-hand column, its preview a little shorter and its stats screen taller (a whole weapon's list is long).
    "strategic_assembly": {
        "title": (8, 6),
        "size": (288, 210),
        "inventory_width": 178,
        "preview": (183, 22, 94, 66),
        "stats": (183, 94, 94, 108),
        "slots": [("core", 30, 22), ("secondary", 48, 22), ("jacket", 66, 22), ("booster", 30, 42), ("guidance", 48, 42),
                  ("module", 66, 42), ("out_big", 124, 31)],
        "energy": (8, 20, 14, 46),
        "progress": (90, 32, 24, 17),                # centred between the slot columns and the output frame
        "flame": None,
        "status": (272, 5, 8, 8),
        "redstone": (151, 94, 18, 18),
        "readout": (34, 64, 134, 24),
        "inventory": (8, 128),
    },
    # Special Weapons Assembler (Phase 11, DESIGN.md §16.15): the hall's layout with the exotic core alone over the
    # missile's parts (booster, optional guidance and module), the arrow, the finished missile; the readout, the
    # Assemble / Take Apart buttons (drawn by the screen) and the redstone button; the bench's right-hand column.
    "special_weapons": {
        "title": (8, 6),
        "size": (288, 210),
        "inventory_width": 178,
        "preview": (183, 22, 94, 66),
        "stats": (183, 94, 94, 108),
        "slots": [("core", 48, 22), ("booster", 30, 42), ("guidance", 48, 42), ("module", 66, 42), ("out_big", 124, 31)],
        "energy": (8, 20, 14, 46),
        "progress": (90, 32, 24, 17),
        "flame": None,
        "status": (272, 5, 8, 8),
        "redstone": (151, 94, 18, 18),
        "readout": (34, 64, 134, 24),
        "inventory": (8, 128),
    },
    # Interceptor Battery (Phase 10, DESIGN.md §18.2): its store of interceptor missiles, and the readout: cells ready,
    # radar or seeker, its tally.
    "interceptor_battery": {
        "title": (8, 6),
        "slots": [("missile", 28, 35)],
        "energy": (8, 20, 14, 46),
        "progress": None,
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": (52, 20, 116, 34),
        "inventory": (8, 84),
    },
    # Interceptor Complex (DESIGN.md §18.5): its store of interceptor missiles, three slots down the left, and the
    # readout: cells ready, its reach (with a Radar Array or its own radar), its tally.
    "interceptor_complex": {
        "title": (8, 6),
        "slots": [("missile", 28, 17), ("missile", 28, 35), ("missile", 28, 53)],
        "energy": (8, 20, 14, 46),
        "progress": None,
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": (52, 20, 116, 34),
        "inventory": (8, 84),
    },
    # RTG: fuel slug slot, decay flame, readout with the fuel time left.
    "rtg": {
        "title": (8, 6),
        "slots": [("fuel", 56, 53)],
        "energy": (8, 20, 14, 46),
        "progress": None,
        "flame": (57, 34, 14, 14),
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": (88, 20, 78, 34),
        "inventory": (8, 84),
    },
    "capacitor_bank": {
        "title": (8, 6),
        "slots": [],
        "energy": (8, 20, 14, 46),
        "progress": None,
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": (40, 22, 100, 24),
        "inventory": (8, 84),
    },
    # Crystal Puller (DESIGN.md §8.1): polysilicon and a crucible in, argon from its tank, the boule out.
    "crystal_puller": {
        "title": (8, 6),
        "slots": [("in", 56, 25), ("crucible", 56, 47), ("out_big", 116, 35)],
        "energy": (8, 20, 14, 46),
        "tanks": [(32, 20, 12, 42, None, True)],
        "progress": (79, 34, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": None,
        "inventory": (8, 84),
    },
    # Canning Machine: the supply tank (left) feeds the filler, poured-out canisters go to the catch tank (right).
    "canning_machine": {
        "title": (8, 6),
        "slots": [("canister", 56, 35), ("out_big", 116, 35)],
        "energy": (8, 20, 14, 46),
        "tanks": [(28, 20, 12, 42, None, True), (146, 20, 12, 42, None, True)],
        "progress": (79, 34, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (82, 58, 18, 18),
        "readout": None,
        "inventory": (8, 84),
    },
    # Battery Charger: the arrow fills with the item's charge.
    "battery_charger": {
        "title": (8, 6),
        "slots": [("battery", 56, 35), ("out_big", 116, 35)],
        "energy": (8, 20, 14, 46),
        "progress": (79, 34, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": None,
        "inventory": (8, 84),
    },
    "decontamination_shower": {
        "title": (8, 6),
        "slots": [],
        "energy": (8, 20, 14, 46),
        "tanks": [(40, 20, 12, 46, "H2O", False)],
        "progress": None,
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": (64, 22, 96, 14),
        "inventory": (8, 84),
    },
    # Fission Reactor (any size, DESIGN.md §7.2): the core map, 4x4 cells of lead casing on which the screen draws the
    # layer of the core picked with the tabs beside it: a rod slot on each fuel channel, coolant round them
    # (FissionReactorScreen mirrors MAP and the cell sprites; slots 0-63 are the four layers' cells, at the same places);
    # while the reactor is not formed the screen draws its plan panel over the map. The coolant tank, heat (0 to
    # 1 400 °C) and control-rod gauges and the rod buttons (withdraw / insert / SCRAM) the height of the map; a
    # right-hand column with the breeding (two target rods over their irradiated outputs) and the spent fuel; under them
    # a CRT with the output, core temperature, size and rods.
    "fission_reactor": {
        "title": (8, 6),
        "size": (226, 218),
        "inventory_width": 178,
        "core_map": (57, 19),
        "slots": [("core_cell", 58 + 18 * (k % 4), 20 + 18 * (k // 4)) for _layer in range(4) for k in range(16)]
                 + [("target", 184, 22), ("target", 202, 22), ("spent", 193, 78), ("out", 184, 50), ("out", 202, 50)],
        "chevrons": [(190, 42, False), (208, 42, False)],
        "energy": (8, 20, 14, 46),
        "tanks": [(26, 20, 12, 42, None, True)],
        "progress": None,
        "flame": None,
        "status": (210, 5, 8, 8),
        "redstone": (192, 103, 18, 18),
        "readout": (11, 100, 154, 24),
        "controls": [("heat", 135, 20, 8, 72, 0, 1400, -1, "groundburst.gui.heat"),
                     ("rods", 147, 20, 8, 72, 1, 100, -1, "groundburst.gui.rods_gauge"),
                     ("up", 158, 20, 16, 22, -1, 0, 0, "groundburst.gui.withdraw"),
                     ("down", 158, 44, 16, 22, -1, 0, 1, "groundburst.gui.insert"),
                     ("scram", 158, 68, 16, 24, -1, 0, 2, "groundburst.gui.scram")]
                    + [("layer", 42, 74 - 18 * y, 11, 17, y, 0, 3 + y, "groundburst.gui.reactor_layer") for y in range(4)],
        "inventory": (8, 136),
    },
    # Fusion Reactor: deuterium, tritium and liquid nitrogen tanks, the ignition-charge gauge.
    "fusion_reactor": {
        "title": (8, 6),
        "size": (176, 194),
        "slots": [],
        "energy": (8, 20, 14, 46),
        "tanks": [(52, 20, 12, 46, "D", False), (74, 20, 12, 46, "T", False), (96, 20, 12, 46, "LN2", False)],
        "progress": None,
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 82, 18, 18),
        "readout": (11, 82, 132, 18),
        "controls": [("charge", 140, 20, 8, 56, 0, 100, -1, "groundburst.gui.ignition")],
        "inventory": (8, 112),
    },
    # Singularity Reactor: the seed slot (it stays there), four feed slots and the stored-matter gauge.
    "singularity_reactor": {
        "title": (8, 6),
        "size": (176, 194),
        "slots": [("seed", 108, 35), ("matter", 40, 26), ("matter", 58, 26), ("matter", 40, 44), ("matter", 58, 44)],
        "chevrons": [(84, 41, True), (90, 41, True), (96, 41, True)],
        "energy": (8, 20, 14, 46),
        "progress": None,
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 82, 18, 18),
        "readout": (11, 82, 132, 18),
        "controls": [("mass", 140, 20, 8, 56, 0, 100, -1, "groundburst.gui.mass")],
        "inventory": (8, 112),
    },
    "particle_accelerator": {
        "title": (8, 6),
        "slots": [("canister", 56, 35), ("out_big", 116, 35)],
        "energy": (8, 20, 14, 46),
        "progress": (79, 34, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": None,
        "inventory": (8, 84),
    },
    "combustion_generator": {
        "title": (8, 6),
        "slots": [("fuel", 56, 53)],
        "energy": (8, 20, 14, 46),
        "progress": None,
        "flame": (57, 34, 14, 14),
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": (88, 20, 78, 34),
        "inventory": (8, 84),
    },
}


# --- widgets ---------------------------------------------------------------------------------------------------------

def window(t: Tex, W: int = W, H: int = H, inv_y: int = 84, inv_w: int | None = None) -> None:
    """Riveted gunmetal panel with a header strip and an inventory section separated by a seam. A window wider than its
    inventory section (inv_w) gets a column on the right, divided off by a vertical seam."""
    seam = inv_y - 8
    iw = W if inv_w is None else inv_w
    sw = iw - 6 if iw == W else iw - 5
    t.rect(0, 0, W, H, PANEL.base)
    t.outline(0, 0, W, H, PANEL.outline)
    t.hline(1, 1, W - 2, PANEL.highlight)
    t.vline(1, 1, H - 2, PANEL.light)
    t.hline(1, H - 2, W - 2, PANEL.deep)
    t.vline(W - 2, 1, H - 2, PANEL.deep)
    # Header strip
    t.rect(3, 3, W - 6, 13, PANEL.shadow)
    t.hline(3, 16, W - 6, PANEL.light)
    # Inventory section
    t.seam_h(3, seam, sw, PANEL)
    t.rect(3, seam + 2, sw, H - seam - 5, PANEL[3])
    t.hline(3, seam + 2, sw, PANEL.shadow)
    if iw < W:
        t.seam_v(iw - 2, 18, H - 21, PANEL)
    # Corner rivets and a few along the edges
    for x, y in ((4, 18), (W - 6, 18), (4, H - 6), (W - 6, H - 6), (4, seam - 4), (W - 6, seam - 4)):
        t.rivet(x, y, PANEL, size=2)
    # Subtle panel wear
    t.scratches(30, 20, 110, 50, (*PANEL.light[:3], 90), seed=5, count=4, length=4)


def slot(t: Tex, x: int, y: int) -> None:
    """Recessed 18x18 slot (the item sits at x+1, y+1)."""
    t.rect(x, y, 18, 18, PANEL.deep)
    t.hline(x, y, 18, PANEL.outline)
    t.vline(x, y, 18, PANEL.outline)
    t.hline(x + 1, y + 17, 17, PANEL.light)
    t.vline(x + 17, y + 1, 17, PANEL.light)
    t.rect(x + 1, y + 1, 16, 16, PANEL[1])


def big_slot(t: Tex, x: int, y: int) -> None:
    """26x26 framed output slot around an 18x18 slot."""
    t.panel(x - 4, y - 4, 26, 26, PANEL, base=2, style="inset")
    t.outline(x - 4, y - 4, 26, 26, PANEL.outline)
    slot(t, x, y)


DIE_GHOST = ["................", "................", "................", "................", "......####......",
             "....##....##....", "...#..####..#...", "...#.#....#.#...", "...##.####.##...", "...#.##..##.#...",
             "...#...##...#...", "....##....##....", "......####......", "................", "................",
             "................"]


def die_slot(t: Tex, x: int, y: int) -> None:
    """Catalyst slot with a ghosted die outline so players know what goes there."""
    slot(t, x, y)
    for yy, row in enumerate(DIE_GHOST):
        for xx, c in enumerate(row):
            if c == "#":
                t.px(x + 1 + xx, y + 1 + yy, PANEL[2])


BUCKET_GHOST = ["................", "................", "................", "...##########...", "...#........#...",
                "...##########...", "....#......#....", "....#......#....", "....#......#....", ".....#....#.....",
                ".....#....#.....", ".....######.....", "................", "................", "................",
                "................"]
CANISTER_GHOST = ["................", "......####......", ".....#....#.....", ".....######.....", "....#......#....",
                  "....#......#....", "....#......#....", "....########....", "....#......#....", "....########....",
                  "....#......#....", "....#......#....", "....#......#....", ".....######.....", "................",
                  "................"]
MEMBRANE_GHOST = ["................", "................", "...##########...", "...#.#.#.#.#.#..", "...##########...",
                  "...#.#.#.#.#.#..", "...##########...", "...#.#.#.#.#.#..", "...##########...", "...#.#.#.#.#.#..",
                  "...##########...", "................", "................", "................", "................",
                  "................"]


# A quartz crucible: a tapering cup, its rim and the hollow inside.
CRUCIBLE_GHOST = ["................", "................", "................", "....########....", "...#++++++++#...",
                  "...##########...", "...#........#...", "...#........#...", "....#......#....", "....#......#....",
                  ".....#....#.....", ".....######.....", "................", "................", "................",
                  "................"]


# The Battery item's outline ("#") and its lightning bolt ("+", a lighter ghost), so the slot shows what goes in it.
BATTERY_GHOST = ["................", "......####......", "......#..#......", "....########....", "...#........#...",
                 "...#........#...", "...#....++..#...", "...#...++...#...", "...#..++++..#...", "...#....++..#...",
                 "...#...++...#...", "...#...+....#...", "...#........#...", "...#........#...", "....########....",
                 "................"]


ROD_GHOST = ["................", "................", ".......##.......", "......#..#......", "......#..#......",
             "......#..#......", "......#..#......", "......#..#......", "......#..#......", "......#..#......",
             "......#..#......", "......#..#......", "......#..#......", ".......##.......", "................",
             "................"]
# A spent rod: the fuel rod's outline, its fuel gone grey and cracked.
SPENT_GHOST = ["................", "................", ".......##.......", "......#..#......", "......#+.#......",
               "......#..#......", "......#.+#......", "......#..#......", "......#+.#......", "......#..#......",
               "......#.+#......", "......#..#......", "......#..#......", ".......##.......", "................",
               "................"]
TARGET_GHOST = ["................", "................", ".......##.......", "......#..#......", "......####......",
                "......#..#......", "......#..#......", "......####......", "......#..#......", "......#..#......",
                "......####......", "......#..#......", "......#..#......", ".......##.......", "................",
                "................"]


# A lithium deuteride pellet (a squat cylinder) for the fusion fuel slot.
PELLET_GHOST = ["................", "................", "................", "................", ".....######.....",
                "....#......#....", "....########....", "....#+.+.+.#....", "....#.+.+.+#....", "....#+.+.+.#....",
                "....#......#....", ".....######.....", "................", "................", "................",
                "................"]
# A singularity seed: a dark sphere in an orbit ring.
SEED_GHOST = ["................", "................", "................", "......####......", ".....#++++#.....",
              "..##########....", ".#..#++++#..#...", "..##########....", ".....#++++#.....", "......####......",
              "................", "................", "................", "................", "................",
              "................"]
# Matter to feed the core: a block falling in.
MATTER_GHOST = ["................", "................", "......###.......", "....##...##.....", "...#.......#....",
                "...##.....##....", "...#.##.##.#....", "...#...#...#....", "...#...#...#....", "...#...#...#....",
                "....##.#.##.....", "......###.......", "................", ".......+........", "......+++.......",
                ".......+........"]


# Assembly Bench: a payload core (a squat drum with a band), a warhead casing (a finned shell, open at the top), a fuze
# (a nose fuze with its arming vane).
CORE_GHOST = ["................", "................", "................", ".....######.....", "....#......#....",
              "....########....", "....#......#....", "....#++++++#....", "....#++++++#....", "....#......#....",
              "....#......#....", "....########....", ".....######.....", "................", "................",
              "................"]
CASING_GHOST = ["................", "....#......#....", "....#......#....", "....#......#....", "....#......#....",
                "....#......#....", "....#......#....", "....#......#....", "....#......#....", "....#......#....",
                "...##......##...", "..#.#......#.#..", ".#..#......#..#.", ".#..########..#.", ".####......####.",
                "................"]
FUZE_GHOST = ["................", ".......##.......", "......#..#......", ".....#....#.....", ".....#....#.....",
              "....#......#....", "....########....", "....#+.+.+.#....", "....#.+.+.+#....", "....########....",
              ".....#....#.....", ".....######.....", "..+...#..#...+..", "...++.####.++...", ".....++..++.....",
              "................"]


# The bench's missile page: a warhead (a nose cone on its ring), a missile body (a tube with fins), a rocket motor (a
# casing on its nozzle), a guidance unit (a box with a crosshair), the hardened module (a shield).
WARHEAD_GHOST = ["................", ".......##.......", "......#..#......", "......#..#......", ".....#....#.....",
                 ".....#....#.....", "....#......#....", "....#......#....", "....#......#....", "....#......#....",
                 "....#......#....", "....########....", "...##########...", "...#........#...", "...##########...",
                 "................"]
BODY_GHOST = ["................", "......####......", "......#..#......", "......#..#......", "......#..#......",
              "......#..#......", "......#..#......", "......#..#......", "......#..#......", "......#..#......",
              "....#.#..#.#....", "...##.#..#.##...", "..#.#.#..#.#.#..", "..###.####.###..", "................",
              "................"]
MOTOR_GHOST = ["................", "....########....", "....#......#....", "....#.++++.#....", "....#......#....",
               "....#.++++.#....", "....#......#....", "....#.++++.#....", "....#......#....", "....########....",
               ".....#....#.....", "....#......#....", "...#........#...", "...##########...", "................",
               "................"]
GUIDANCE_GHOST = ["................", "................", "...##########...", "...#...+....#...", "...#...+....#...",
                  "...#..###...#...", "...#++#.#++.#...", "...#..###...#...", "...#...+....#...", "...#...+....#...",
                  "...##########...", ".....#....#.....", ".....#....#.....", "................", "................",
                  "................"]
MODULE_GHOST = ["................", "................", "....########....", "...#........#...", "...#..++++..#...",
                "...#.+....+.#...", "...#.+....+.#...", "...#..++++..#...", "....#......#....", "....#......#....",
                ".....#....#.....", "......#..#......", ".......##.......", "................", "................",
                "................"]


# The ICBM Assembly Hall: a fusion secondary (a capsule round its spark-plug rod), a cobalt jacket (a sleeve of
# plates), an intercontinental booster (three stages narrowing upward on their nozzles).
SECONDARY_GHOST = ["................", "......####......", ".....#....#.....", "....#......#....", "....#..##..#....",
                   "....#.#++#.#....", "....#.#++#.#....", "....#.#++#.#....", "....#.#++#.#....", "....#.#++#.#....",
                   "....#..##..#....", "....#......#....", ".....#....#.....", "......####......", "................",
                   "................"]
JACKET_GHOST = ["................", "................", "...##########...", "...#+.#+.#+.#...", "...#..#..#..#...",
                "...#..#..#..#...", "...##########...", "...#+.#+.#+.#...", "...#..#..#..#...", "...#..#..#..#...",
                "...##########...", "...#+.#+.#+.#...", "...#..#..#..#...", "...##########...", "................",
                "................"]
BOOSTER_GHOST = ["................", "......####......", "......#..#......", "......#..#......", ".....######.....",
                 ".....#....#.....", ".....#....#.....", ".....#....#.....", "....########....", "....#......#....",
                 "....#......#....", "....#......#....", "....#......#....", "....########....", ".....#.##.#.....",
                 "................"]


# The Launch Silo's slot: a whole missile (nose, body, fins).
MISSILE_GHOST = [".......##.......", "......#..#......", "......#..#......", "......####......", "......#..#......",
                 "......#..#......", "......#..#......", "......#..#......", "......#..#......", "......#..#......",
                 "......#..#......", ".....##..##.....", "....#.#..#.#....", "...#..#..#..#...", "...####..####...",
                 ".......##......."]


def ghost_slot(rows: list[str]):
    def draw(t: Tex, x: int, y: int) -> None:
        slot(t, x, y)
        for yy, row in enumerate(rows):
            for xx, c in enumerate(row):
                if c == "#":
                    t.px(x + 1 + xx, y + 1 + yy, PANEL[2])
                elif c == "+":
                    t.px(x + 1 + xx, y + 1 + yy, PANEL[3])
    return draw


DUMP = ["..##..", "..##..", "..##..", "######", ".####.", "..##.."]


def dump_button(t: Tex, x: int, y: int, w: int, h: int) -> None:
    """Small dump button under a tank gauge: raised red plate with a down-arrow into a drain bar."""
    t.panel(x, y, w, h, P.PRIMER_RED, base=3, style="raised")
    t.outline(x, y, w, h, PANEL.outline)
    gx, gy = x + (w - len(DUMP[0])) // 2, y + (h - len(DUMP)) // 2
    for yy, row in enumerate(DUMP):
        for xx, c in enumerate(row):
            if c == "#":
                t.px(gx + xx, gy + yy, P.WHITE_CERAMIC[3])


def fill_chevron(t: Tex, x: int, y: int, horizontal: bool) -> None:
    """Small arrow between the fill-in and fill-out slots."""
    pts = [(0, 0), (1, 1), (2, 2), (1, 3), (0, 4)] if horizontal else [(0, 0), (1, 1), (2, 2), (3, 1), (4, 0)]
    for dx, dy in pts:
        t.px(x + dx, y + dy, PANEL.highlight)


def tank_frame(t: Tex, x: int, y: int, w: int, h: int) -> None:
    """Fluid gauge: dark glass with bright graduation ticks on the right so the level reads against the fluid."""
    t.rect(x - 1, y - 1, w + 2, h + 2, PANEL.outline)
    t.rect(x, y, w, h, GLASS.base)
    for yy in range(y + h - 1, y, -5):
        t.hline(x + w - 3, yy, 3, PANEL.highlight)
    t.hline(x - 1, y + h + 1, w + 2, PANEL.light)


def gauge_frame(t: Tex, x: int, y: int, w: int, h: int) -> None:
    t.rect(x - 1, y - 1, w + 2, h + 2, PANEL.outline)
    t.rect(x, y, w, h, GLASS.base)
    for yy in range(y + h - 1, y, -6):          # graduation ticks
        t.px(x, yy, GLASS.highlight)
        t.px(x + w - 1, yy, GLASS.highlight)
    t.hline(x, y, w, GLASS.shine)               # glass glint
    t.hline(x - 1, y + h + 1, w + 2, PANEL.light)


def energy_fill(t: Tex, dx: int = 0) -> None:
    """Segmented amber phosphor bars (2 px bars, 1 px gaps), hotter toward the top."""
    x0, y0 = ENERGY_FILL[0] + dx, ENERGY_FILL[1]
    for yy in range(52):
        if yy % 3 == 2:
            continue
        k = 1 - yy / 51
        c = AMBER[3 + round(k * 2)]
        t.hline(x0, y0 + yy, 12, c)
        t.px(x0, y0 + yy, AMBER.shadow)
        t.px(x0 + 11, y0 + yy, AMBER.shadow)


def arrow_mask(w: int, h: int) -> list[tuple[int, int]]:
    """Chunky right-pointing arrow: shaft then head."""
    pts = []
    mid = h // 2
    for x in range(w):
        if x < w - 9:
            for y in range(mid - 3, mid + 4):
                pts.append((x, y))
        else:
            half = (w - x) - 1
            for y in range(mid - half, mid + half + 1):
                pts.append((x, y))
    return pts


def progress_track(t: Tex, x: int, y: int, w: int, h: int) -> None:
    for (ax, ay) in arrow_mask(w, h):
        t.px(x + ax, y + ay, PANEL.deep)
    for (ax, ay) in arrow_mask(w, h):
        if (ax, ay - 1) not in arrow_mask(w, h):
            t.px(x + ax, y + ay, PANEL.outline)


def progress_fill(t: Tex, dx: int = 0) -> None:
    x0, y0 = PROGRESS_FILL[0] + dx, PROGRESS_FILL[1]
    for (ax, ay) in arrow_mask(24, 17):
        stripe = ((ax + ay) // 3) % 2 == 0
        t.px(x0 + ax, y0 + ay, P.HAZARD_YELLOW.light if stripe else P.HAZARD_YELLOW.base)
    for (ax, ay) in arrow_mask(24, 17):
        if (ax, ay - 1) not in arrow_mask(24, 17):
            t.px(x0 + ax, y0 + ay, P.HAZARD_YELLOW.shine)


FLAME = ["......#.......", ".....##.......", ".....###......", "....####......", "....#####.....", "...######..#..",
         "...#######.##.", "..##########..", "..###########.", ".############.", ".#############", ".#############",
         "..###########.", "...#########.."]


def flame_track(t: Tex, x: int, y: int) -> None:
    for yy, row in enumerate(FLAME):
        for xx, c in enumerate(row):
            if c == "#":
                t.px(x + xx, y + yy, PANEL.deep)


def flame_fill(t: Tex, dx: int = 0) -> None:
    x0, y0 = FLAME_FILL[0] + dx, FLAME_FILL[1]
    for yy, row in enumerate(FLAME):
        for xx, c in enumerate(row):
            if c == "#":
                core = 4 <= xx <= 9 and yy >= 7
                col = P.NUCLEAR_AMBER.shine if core and yy >= 9 else P.NUCLEAR_AMBER.light if core else P.WARNING_RED.light if yy < 6 else P.NUCLEAR_AMBER.base
                t.px(x0 + xx, y0 + yy, col)


def lamp_bezel(t: Tex, x: int, y: int, w: int, h: int) -> None:
    """Status lamp in a thin hazard-striped warning frame."""
    t.stripes(x - 3, y - 3, w + 6, h + 6, P.HAZARD_YELLOW.shadow, P.HAZARD_BLACK.base, width=2)
    t.outline(x - 4, y - 4, w + 8, h + 8, PANEL.outline)
    t.rect(x - 1, y - 1, w + 2, h + 2, PANEL.outline)
    t.rect(x, y, w, h, GLASS.base)


def button(t: Tex, x: int, y: int, w: int, h: int) -> None:
    t.panel(x, y, w, h, PANEL, base=4, style="raised")
    t.outline(x, y, w, h, PANEL.outline)


ARROW_UP = ["...#...", "..###..", ".#####.", "#######", "..###..", "..###..", "..###.."]


DIGITS = {1: [".#.", "##.", ".#.", ".#.", "###"], 2: ["##.", "..#", ".#.", "#..", "###"], 3: ["##.", "..#", ".#.", "..#", "##."],
          4: ["#.#", "#.#", "###", "..#", "..#"]}


def control(t: Tex, kind: str, x: int, y: int, w: int, h: int, top: int = 1000, data: int = 0) -> None:
    """Machine controls: vertical gauges (filled live by the screen) and push buttons with their icon. {top} is a
    gauge's full scale; a layer tab shows layer {data} + 1."""
    if kind == "layer":
        button(t, x, y, w, h)
        gx, gy = x + (w - 3) // 2, y + (h - 5) // 2
        for yy, row in enumerate(DIGITS[data + 1]):
            for xx, c in enumerate(row):
                if c == "#":
                    t.px(gx + xx, gy + yy, P.WHITE_CERAMIC[5])
        return
    if kind in ("heat", "rods", "charge", "mass"):
        gauge_frame(t, x, y, w, h)
        if kind == "heat":
            # Red warning band from 900 °C up: where the core SCRAMs (or goes critical).
            t.rect(x - 2, y, 1, max(1, round(h * (1 - 900 / top))), P.WARNING_RED.base)
        return
    if kind == "scram":
        t.stripes(x - 1, y - 1, w + 2, h + 2, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)   # guard collar
        t.panel(x + 2, y + 2, w - 4, h - 4, P.WARNING_RED, base=4, style="raised")
        t.outline(x + 2, y + 2, w - 4, h - 4, P.WARNING_RED.outline)
        t.rect(x + w // 2 - 3, y + h // 2 - 3, 6, 6, P.WARNING_RED[5])                                 # mushroom head
        t.outline(x + w // 2 - 3, y + h // 2 - 3, 6, 6, P.WARNING_RED[2])
        return
    button(t, x, y, w, h)
    if kind == "doors":
        # Two door leaves parting, an arrow out from each.
        cx, cy = x + w // 2, y + (h - 10) // 2
        for lx in (cx - 6, cx + 1):
            t.rect(lx, cy, 5, 10, P.GUNMETAL[2])
            t.outline(lx, cy, 5, 10, P.GUNMETAL[1])
            t.vline(lx + 1, cy + 1, 8, P.GUNMETAL[4])
        for k in range(3):
            t.vline(cx - 1, cy + 1 + k * 3, 2, P.HAZARD_YELLOW[4])               # the seam's stripes
            t.vline(cx, cy + 2 + k * 3, 2, P.HAZARD_BLACK[3])
        ay = cy + 4
        for tip, step in ((cx - 11, 1), (cx + 10, -1)):             # each arrow points out from its leaf
            t.px(tip, ay + 1, P.WHITE_CERAMIC[5])
            t.vline(tip + step, ay, 3, P.WHITE_CERAMIC[5])
            t.hline(min(tip + step * 2, tip + step * 3), ay + 1, 2, P.WHITE_CERAMIC[5])
        return
    rows = ARROW_UP if kind == "up" else ARROW_UP[::-1]
    gx, gy = x + (w - 7) // 2, y + (h - 7) // 2
    for yy, row in enumerate(rows):
        for xx, c in enumerate(row):
            if c == "#":
                t.px(gx + xx, gy + yy, P.WHITE_CERAMIC[5])


def redstone_icons(t: Tex, dx: int = 0) -> None:
    """Ignore (grey dust), active-high (lit torch), active-low (dark torch)."""
    x0, y0 = REDSTONE_ICONS[0] + dx, REDSTONE_ICONS[1]
    dust = [(5, 9), (6, 8), (7, 9), (8, 8), (9, 9), (10, 8), (6, 10), (8, 10), (10, 10), (7, 7), (9, 7)]
    for x, y in dust:
        t.px(x0 + x, y0 + y, P.CONCRETE.light)
    t.line(x0 + 3, y0 + 12, x0 + 12, y0 + 3, P.WARNING_RED.base)    # "ignored" slash
    for i, lit in ((1, True), (2, False)):
        bx = x0 + i * 16
        t.rect(bx + 7, y0 + 6, 2, 8, P.DESERT_TAN.shadow)          # stick
        head = P.WARNING_RED.light if lit else P.PRIMER_RED.deep
        t.rect(bx + 6, y0 + 3, 4, 3, head)
        if lit:
            t.px(bx + 7, y0 + 2, P.WARNING_RED.shine)
            t.px(bx + 5, y0 + 3, (*P.WARNING_RED.highlight[:3], 160), blend=True)
            t.px(bx + 10, y0 + 3, (*P.WARNING_RED.highlight[:3], 160), blend=True)


BLUEPRINT = fixed("blueprint", "#0c1526", "#111d33", "#172742", "#1d3150", "#274066", "#36557f", "#5476a3")


def blueprint_view(t: Tex, x: int, y: int, w: int, h: int) -> None:
    """A 3D preview's window: a bezel round blueprint paper, its grid centred so the model turns on a grid line."""
    t.panel(x - 3, y - 3, w + 6, h + 6, PANEL, base=2, style="inset")
    t.rect(x - 1, y - 1, w + 2, h + 2, PANEL.outline)
    t.rect(x, y, w, h, BLUEPRINT[2])
    cx, cy = x + w // 2, y + h // 2
    for k in range(-(w // 2) // 6 - 1, (w // 2) // 6 + 2):
        gx = cx + k * 6
        if x <= gx < x + w:
            t.vline(gx, y, h, BLUEPRINT[4] if k % 4 == 0 else BLUEPRINT[3])
    for k in range(-(h // 2) // 6 - 1, (h // 2) // 6 + 2):
        gy = cy + k * 6
        if y <= gy < y + h:
            t.hline(x, gy, w, BLUEPRINT[4] if k % 4 == 0 else BLUEPRINT[3])
    t.hline(x, y, w, BLUEPRINT[1])
    t.vline(x, y, h, BLUEPRINT[1])


def readout_panel(t: Tex, x: int, y: int, w: int, h: int) -> None:
    """CRT screen: bezel, dark glass, faint scanlines; text is drawn live by the screen."""
    t.panel(x - 3, y - 3, w + 6, h + 6, PANEL, base=2, style="inset")
    t.rect(x - 1, y - 1, w + 2, h + 2, PANEL.outline)
    t.rect(x, y, w, h, GLASS.base)
    for yy in range(y, y + h, 2):
        t.hline(x, yy, w, GLASS[2])
    t.hline(x, y, w, GLASS.highlight)


LEAD_GUI = P.LEAD
WATER = fixed("core_water", "#061428", "#0a2040", "#10305c", "#18447c", "#2460a0", "#3a86c4", "#8cc8f0")
CORE_SPRITES = (180, 80)      # three 18x18 cells: a fuel channel, coolant, a rod left off the channels


def core_cell(t: Tex, x: int, y: int) -> None:
    """One cell of the reactor's core map as the window has it: a lead casing plate, its seam on the right and bottom."""
    t.rect(x, y, 18, 18, LEAD_GUI[2])
    t.hline(x, y, 17, LEAD_GUI[3])
    t.vline(x, y, 17, LEAD_GUI[3])
    t.hline(x, y + 17, 18, LEAD_GUI[0])
    t.vline(x + 17, y, 18, LEAD_GUI[0])
    for bx, by in ((2, 2), (14, 2), (2, 14), (14, 14)):
        t.px(x + bx, y + by, LEAD_GUI[4])
        t.px(x + bx + 1, y + by + 1, LEAD_GUI[1])


def core_map_frame(t: Tex, x: int, y: int) -> None:
    """The map's bezel: the 4x4 cells set into the panel, a lit lower lip."""
    t.panel(x - 3, y - 3, 78, 78, PANEL, base=2, style="inset")
    t.rect(x - 1, y - 1, 74, 74, PANEL.outline)
    t.hline(x - 1, y + 73, 74, PANEL.light)


def core_sprites(t: Tex, dx: int) -> None:
    """The screen's map cells: a fuel channel (a slot sunk in deep water, a thin blue ring of light round its bore),
    open coolant (water with ripples), and a slot ringed in red (a rod on a cell that is no longer a channel)."""
    x0, y0 = CORE_SPRITES[0] + dx, CORE_SPRITES[1]
    # Fuel channel.
    t.rect(x0, y0, 18, 18, WATER[2])
    t.hline(x0, y0 + 17, 18, WATER[0])
    t.vline(x0 + 17, y0, 18, WATER[0])
    t.rect(x0 + 1, y0 + 1, 16, 16, WATER[0])
    t.outline(x0 + 1, y0 + 1, 16, 16, WATER[4])
    t.hline(x0 + 2, y0 + 2, 14, WATER[1])
    t.vline(x0 + 2, y0 + 2, 14, WATER[1])
    for bx, by in ((1, 1), (16, 1), (1, 16), (16, 16)):
        t.px(x0 + bx, y0 + by, P.STEEL[4])                     # the assembly's corner posts
    # Coolant.
    c0 = x0 + 20
    t.rect(c0, y0, 18, 18, WATER[3])
    t.hline(c0, y0 + 17, 18, WATER[1])
    t.vline(c0 + 17, y0, 18, WATER[1])
    for rx, ry, n in ((3, 4, 4), (9, 8, 5), (4, 12, 3), (11, 14, 3), (12, 3, 3)):
        t.hline(c0 + rx, y0 + ry, n, WATER[5])
        t.hline(c0 + rx + 1, y0 + ry + 1, n - 1, WATER[2])
    # A rod left off the channels.
    s0 = x0 + 40
    slot(t, s0, y0)
    t.outline(s0 + 1, y0 + 1, 16, 16, P.WARNING_RED.base)
    t.outline(s0 + 2, y0 + 2, 14, 14, P.WARNING_RED.shadow)


def texture_width(spec: dict) -> int:
    """256, or 512 for a window too wide to leave the fill sprites (x 180 on) clear."""
    return 256 if spec.get("size", (W, H))[0] <= 178 else 512


def fill_dx(spec: dict) -> int:
    return texture_width(spec) - 256


def build(name: str, spec: dict) -> Path:
    t = Tex(texture_width(spec), 256)
    w, h = spec.get("size", (W, H))
    window(t, w, h, spec["inventory"][1], spec.get("inventory_width"))
    if spec.get("preview"):
        blueprint_view(t, *spec["preview"])
    if spec.get("stats"):
        readout_panel(t, *spec["stats"])
    if spec.get("core_map"):
        core_map_frame(t, *spec["core_map"])
    for kind, x, y in spec["slots"]:
        {"out_big": big_slot, "die": die_slot, "bucket": ghost_slot(BUCKET_GHOST), "canister": ghost_slot(CANISTER_GHOST),
         "membrane": ghost_slot(MEMBRANE_GHOST), "fill_in": ghost_slot(BUCKET_GHOST), "battery": ghost_slot(BATTERY_GHOST),
         "rod": ghost_slot(ROD_GHOST), "target": ghost_slot(TARGET_GHOST), "pellet": ghost_slot(PELLET_GHOST),
         "seed": ghost_slot(SEED_GHOST), "matter": ghost_slot(MATTER_GHOST),
         "core": ghost_slot(CORE_GHOST), "casing": ghost_slot(CASING_GHOST), "fuze": ghost_slot(FUZE_GHOST),
         "warhead": ghost_slot(WARHEAD_GHOST), "body": ghost_slot(BODY_GHOST), "motor": ghost_slot(MOTOR_GHOST),
         "guidance": ghost_slot(GUIDANCE_GHOST), "module": ghost_slot(MODULE_GHOST), "missile": ghost_slot(MISSILE_GHOST),
         "secondary": ghost_slot(SECONDARY_GHOST), "jacket": ghost_slot(JACKET_GHOST), "booster": ghost_slot(BOOSTER_GHOST),
         "core_cell": core_cell, "spent": ghost_slot(SPENT_GHOST), "crucible": ghost_slot(CRUCIBLE_GHOST)}.get(kind, slot)(t, x - 1, y - 1)
    fills = [(x, y) for kind, x, y in spec["slots"] if kind in ("fill_in", "fill_out")]
    if len(fills) == 2:
        (ax, ay), (bx, by) = fills
        if ay == by:
            fill_chevron(t, (ax + 17 + bx - 1) // 2 - 1, ay + 6, True)
        else:
            fill_chevron(t, ax + 6, (ay + 17 + by - 1) // 2 - 2, False)
    for cx, cy, horizontal in spec.get("chevrons", []):
        fill_chevron(t, cx, cy, horizontal)
    ex, ey, ew, eh = spec["energy"]
    gauge_frame(t, ex, ey, ew, eh)
    t.text(ex + 3, ey + eh + 3, "FE", PANEL.highlight)
    for tx, ty, tw, th, label, dumpable in spec.get("tanks", []):
        tank_frame(t, tx, ty, tw, th)
        if label:
            t.text(tx, ty + th + 3, label, PANEL.highlight)
        if dumpable:
            dump_button(t, *dump_rect(tx, ty, tw, th))
    if spec["progress"]:
        progress_track(t, *spec["progress"])
    if spec["flame"]:
        fx, fy, _, _ = spec["flame"]
        flame_track(t, fx, fy)
    lamp_bezel(t, *spec["status"])
    button(t, *spec["redstone"])
    if spec["readout"]:
        readout_panel(t, *spec["readout"])
    for kind, cx, cy, cw, ch, data, top, *_ in spec.get("controls", []):
        control(t, kind, cx, cy, cw, ch, top or 1000, data)
    ix, iy = spec["inventory"]
    for row in range(3):
        for col in range(9):
            slot(t, ix - 1 + col * 18, iy - 1 + row * 18)
    for col in range(9):
        slot(t, ix - 1 + col * 18, iy - 1 + 58)
    energy_fill(t, fill_dx(spec))
    progress_fill(t, fill_dx(spec))
    flame_fill(t, fill_dx(spec))
    redstone_icons(t, fill_dx(spec))
    if spec.get("core_map"):
        core_sprites(t, fill_dx(spec))
    return t.save(paths.TEXTURES / "gui" / "machine" / f"{name}.png")


def dump_rect(tx: int, ty: int, tw: int, th: int) -> tuple[int, int, int, int]:
    return tx, ty + th + 2, tw, 10


def java_rect(r) -> str:
    return "null" if r is None else f"new MachineLayout.Rect({r[0]}, {r[1]}, {r[2]}, {r[3]})"


def write_java() -> Path:
    lines = [
        "package dev.groundburst.content.machine;",
        "",
        "import java.util.List;",
        "",
        "import dev.groundburst.GroundBurst;",
        "",
        "/** GENERATED by tools/assets/gui.py together with the GUI textures. Do not edit by hand. */",
        "public final class MachineLayouts {",
    ]
    for name, s in LAYOUTS.items():
        slots = ", ".join(f"new MachineLayout.Pos({x}, {y})" for _, x, y in s["slots"] + s.get("hidden_slots", []))
        p = s["progress"]
        f = s["flame"]
        lines.append(f"    public static final MachineLayout {name.upper()} = new MachineLayout(")
        w, h = s.get("size", (W, H))
        lines.append(f"            GroundBurst.id(\"textures/gui/machine/{name}.png\"), {w}, {h}, List.of({slots}), {s['inventory'][0]}, {s['inventory'][1]},")
        dx = fill_dx(s)
        lines.append(f"            {java_rect(s['energy'])}, {ENERGY_FILL[0] + dx}, {ENERGY_FILL[1]},")
        lines.append(f"            {java_rect(p)}, {PROGRESS_FILL[0] + dx}, {PROGRESS_FILL[1]},")
        lines.append(f"            {java_rect(f)}, {FLAME_FILL[0] + dx}, {FLAME_FILL[1]},")
        lines.append(f"            {java_rect(s['status'])}, {java_rect(s['redstone'])}, {REDSTONE_ICONS[0] + dx}, {REDSTONE_ICONS[1]},")
        gauges = ", ".join(f"new MachineLayout.Gauge({java_rect(tk[:4])}, {java_rect(dump_rect(*tk[:4]) if tk[5] else None)})"
                           for tk in s.get("tanks", []))
        controls = ", ".join(f"new MachineLayout.Control(\"{c[0]}\", {java_rect(c[1:5])}, {c[5]}, {c[6]}, {c[7]}, \"{c[8]}\")"
                             for c in s.get("controls", []))
        lines.append(f"            {java_rect(s['readout'])}, {s['title'][0]}, {s['title'][1]}, List.of({gauges}),")
        lines.append(f"            List.of({controls}), {texture_width(s)});")
        lines.append("")
    lines.append("    private MachineLayouts() {}")
    lines.append("}")
    out = paths.ROOT / "src" / "main" / "java" / "dev" / "groundburst" / "content" / "machine" / "MachineLayouts.java"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return out


# --- side-configuration tab (shared by every machine) -----------------------------------------------------------------
# Layout constants mirrored in MachineScreen.SideTab.
TAB = (0, 0, 24, 26)            # collapsed tab
PANEL_RECT = (0, 32, 112, 112)  # expanded panel: channel buttons stacked on the left, face grid on the right
FACE_FRAME = (120, 0)           # 18x18
FACE_ICON = (140, 0)            # 16x16 plain face
FACE_FRONT = (160, 0)           # 16x16 front face
CHANNEL_ON = (120, 24)          # 40x12
CHANNEL_OFF = (120, 40)         # 40x12
CHECK_OFF = (120, 56)           # 10x10
CHECK_ON = (132, 56)            # 10x10
ICON_ITEMS = (164, 24)          # 8x8
ICON_ENERGY = (174, 24)         # 8x8
ICON_WRENCH = (184, 24)         # 12x12
ICON_FLUID = (164, 36)          # 8x8

# Open-end spanner: forked jaw top-right, handle running down-left to a rounded end. 'h' = lit edge, 's' = shade.
# Open-end spanner: a forked jaw (two prongs round a notch) top-right, a 3-px handle running down-left.
# 'h' = lit metal, 's' = shade.
WRENCH = [".......hh...", "......hhh.h.", "......hh..hh", "......hhh.hs", ".....hhhhhs.", "....hhhss...",
          "...hhhs.....", "..hhhs......", ".hhhs.......", "hhhs........", "hhs.........", ".s.........."]
BOLT = ["...##...", "..##....", ".##.....", "######..", "...##...", "..##....", ".##.....", "........"]
DROP = ["...#....", "...#....", "..###...", ".#####..", ".##.##..", ".#.###..", "..###...", "........"]
CRATE = ["........", ".######.", ".#....#.", ".######.", ".#.##.#.", ".#.##.#.", ".######.", "........"]


def tab_panel(t: Tex, x: int, y: int, w: int, h: int) -> None:
    """A tab panel that grows out of the GUI's right edge: flat left side, bevelled right side and corners."""
    t.rect(x, y, w, h, PANEL.base)
    t.hline(x, y, w - 2, PANEL.outline)
    t.hline(x, y + h - 1, w - 2, PANEL.outline)
    t.vline(x + w - 1, y + 2, h - 4, PANEL.outline)
    t.px(x + w - 2, y + 1, PANEL.outline)
    t.px(x + w - 2, y + h - 2, PANEL.outline)
    t.px(x + w - 1, y, (0, 0, 0, 0))
    t.px(x + w - 1, y + 1, (0, 0, 0, 0))
    t.px(x + w - 1, y + h - 1, (0, 0, 0, 0))
    t.px(x + w - 1, y + h - 2, (0, 0, 0, 0))
    t.px(x + w - 2, y, (0, 0, 0, 0))
    t.px(x + w - 2, y + h - 1, (0, 0, 0, 0))
    t.hline(x, y + 1, w - 3, PANEL.highlight)
    t.hline(x, y + h - 2, w - 3, PANEL.deep)
    t.vline(x + w - 2, y + 2, h - 4, PANEL.deep)
    t.rivet(x + w - 5, y + 3, PANEL)
    t.rivet(x + w - 5, y + h - 5, PANEL)


def glyph(t: Tex, rows: list[str], x: int, y: int, c, shade=None) -> None:
    for yy, row in enumerate(rows):
        for xx, ch in enumerate(row):
            if ch in "#h":
                t.px(x + xx, y + yy, c)
            elif ch == "s":
                t.px(x + xx, y + yy, shade if shade is not None else c)


def side_config_texture() -> Path:
    t = Tex(256, 256)
    tab_panel(t, *TAB)
    glyph(t, WRENCH, TAB[0] + 5, TAB[1] + 7, P.STEEL.highlight, P.STEEL.shadow)
    tab_panel(t, *PANEL_RECT)
    px_, py_ = PANEL_RECT[0], PANEL_RECT[1]
    glyph(t, WRENCH, px_ + 5, py_ + 4, P.STEEL.highlight, P.STEEL.shadow)
    t.hline(px_ + 3, py_ + 17, PANEL_RECT[2] - 8, PANEL.shadow)
    t.hline(px_ + 3, py_ + 18, PANEL_RECT[2] - 8, PANEL.light)
    # Face frame + face icons.
    slot(t, *FACE_FRAME)
    fx, fy = FACE_ICON
    t.panel(fx, fy, 16, 16, P.OLIVE, base=3, style="raised")
    t.outline(fx + 2, fy + 2, 12, 12, P.OLIVE.shadow)
    for rx, ry in ((fx + 1, fy + 1), (fx + 14, fy + 1), (fx + 1, fy + 14), (fx + 14, fy + 14)):
        t.rivet(rx, ry, P.OLIVE)
    gx, gy = FACE_FRONT
    t.panel(gx, gy, 16, 16, P.OLIVE, base=3, style="raised")
    t.rect(gx + 3, gy + 3, 7, 3, P.BLACK_PAINT.base)           # nameplate
    t.hline(gx + 4, gy + 4, 5, P.HAZARD_YELLOW.base)
    t.rect(gx + 11, gy + 3, 2, 2, P.INDICATOR_GREEN.base)      # status lamp
    t.stripes(gx + 1, gy + 12, 14, 3, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
    # Channel buttons (selected / not), checkbox, icons.
    cx, cy = CHANNEL_ON
    t.panel(cx, cy, 40, 12, PANEL, base=4, style="inset")
    t.outline(cx, cy, 40, 12, P.HAZARD_YELLOW.shadow)
    cx, cy = CHANNEL_OFF
    t.panel(cx, cy, 40, 12, PANEL, base=3, style="raised")
    t.outline(cx, cy, 40, 12, PANEL.outline)
    for (bx, by), on in ((CHECK_OFF, False), (CHECK_ON, True)):
        t.rect(bx, by, 10, 10, PANEL.deep)
        t.outline(bx, by, 10, 10, PANEL.outline)
        if on:
            for i, (xx, yy) in enumerate(((2, 5), (3, 6), (4, 7), (5, 6), (6, 5), (7, 4), (7, 3))):
                t.px(bx + xx, by + yy, P.INDICATOR_GREEN.light)
                t.px(bx + xx, by + yy - 1, P.INDICATOR_GREEN.base)
    glyph(t, CRATE, *ICON_ITEMS, P.DESERT_TAN.light)
    glyph(t, BOLT, *ICON_ENERGY, P.HAZARD_YELLOW.light)
    glyph(t, DROP, *ICON_FLUID, P.CHERENKOV.light)
    glyph(t, WRENCH, *ICON_WRENCH, P.STEEL.highlight, P.STEEL.shadow)
    return t.save(paths.TEXTURES / "gui" / "machine" / "side_config.png")


PAGE_TAB_ON = (0, 0, 28, 24)    # the shown page: its last two columns cover the window's left border, so it joins it
PAGE_TAB_OFF = (0, 32, 24, 24)  # another page: darker, ending at the window's edge, so it sits behind


def page_tab(t: Tex, x: int, y: int, w: int, h: int, on: bool) -> None:
    """A page tab on the GUI's left edge (the side tab mirrored): bevelled left side and corners, flat right side."""
    face = PANEL.base if on else PANEL.shadow
    t.rect(x, y, w, h, face)
    t.hline(x + 2, y, w - 2, PANEL.outline)
    t.hline(x + 2, y + h - 1, w - 2, PANEL.outline)
    t.vline(x, y + 2, h - 4, PANEL.outline)
    t.px(x + 1, y + 1, PANEL.outline)
    t.px(x + 1, y + h - 2, PANEL.outline)
    for px_, py_ in ((0, 0), (0, 1), (1, 0), (0, h - 1), (0, h - 2), (1, h - 1)):
        t.px(x + px_, y + py_, (0, 0, 0, 0))
    lit = PANEL.highlight if on else PANEL.base
    t.hline(x + 2, y + 1, w - 2, lit)
    t.vline(x + 1, y + 2, h - 4, PANEL.light if on else PANEL.base)
    t.hline(x + 2, y + h - 2, w - 2, PANEL.deep)
    if on:
        # Where it meets the window: its edges stop at the window's outline, and the window's face runs into the tab.
        t.vline(x + w - 2, y + 1, h - 2, face)
        t.vline(x + w - 1, y + 1, h - 2, face)
        t.px(x + w - 1, y, PANEL.light)
        t.px(x + w - 1, y + h - 1, PANEL.light)


def page_tabs_texture() -> Path:
    t = Tex(64, 64)
    page_tab(t, *PAGE_TAB_ON, on=True)
    page_tab(t, *PAGE_TAB_OFF, on=False)
    return t.save(paths.TEXTURES / "gui" / "machine" / "page_tabs.png")


# --- Upgrades tab (DESIGN.md §7.3; every machine and assembler that takes a chip) -------------------------------------------
# Layout constants mirrored in UpgradeTab (the menu's slot: MachineMenu.UPGRADE_SLOT_X/Y).
UPG_TAB = (0, 0, 24, 26)          # collapsed tab, on the window's left edge
UPG_PANEL = (0, 32, 82, 64)       # expanded panel: header, three slots in a row, two lines (just wider than they are)
UPG_SLOT = (8, 22)                # the first slot frame within the panel; the other two follow it 18 apart
UPG_SLOTS = 3


def tab_panel_left(t: Tex, x: int, y: int, w: int, h: int) -> None:
    """A tab panel growing out of the GUI's left edge (the side tab mirrored): flat right side, bevelled left side."""
    t.rect(x, y, w, h, PANEL.base)
    t.hline(x + 2, y, w - 2, PANEL.outline)
    t.hline(x + 2, y + h - 1, w - 2, PANEL.outline)
    t.vline(x, y + 2, h - 4, PANEL.outline)
    t.px(x + 1, y + 1, PANEL.outline)
    t.px(x + 1, y + h - 2, PANEL.outline)
    for px_, py_ in ((0, 0), (0, 1), (1, 0), (0, h - 1), (0, h - 2), (1, h - 1)):
        t.px(x + px_, y + py_, (0, 0, 0, 0))
    t.hline(x + 2, y + 1, w - 2, PANEL.highlight)
    t.vline(x + 1, y + 2, h - 4, PANEL.light)
    t.hline(x + 2, y + h - 2, w - 2, PANEL.deep)
    t.rivet(x + 3, y + 3, PANEL)
    t.rivet(x + 3, y + h - 5, PANEL)


def chip_glyph(t: Tex, x: int, y: int) -> None:
    """An 11x11 integrated circuit: a dark package with a lit top edge, a gold die, four pins a side."""
    t.rect(x + 1, y + 1, 9, 9, P.BLACK_PAINT.base)
    t.hline(x + 1, y + 1, 9, P.BLACK_PAINT.highlight)
    t.hline(x + 1, y + 9, 9, P.BLACK_PAINT.shadow)
    t.rect(x + 3, y + 3, 5, 5, P.BRASS.base)
    t.hline(x + 3, y + 3, 5, P.BRASS.light)
    t.px(x + 2, y + 2, P.BLACK_PAINT.light)                    # pin-1 dot
    for k in (2, 4, 6, 8):
        t.px(x + k, y, P.STEEL.highlight)
        t.px(x + k, y + 10, P.STEEL.shadow)
        t.px(x, y + k, P.STEEL.highlight)
        t.px(x + 10, y + k, P.STEEL.base)


def upgrades_texture() -> Path:
    t = Tex(256, 256)
    tab_panel_left(t, *UPG_TAB)
    chip_glyph(t, UPG_TAB[0] + 7, UPG_TAB[1] + 7)
    tab_panel_left(t, *UPG_PANEL)
    px_, py_ = UPG_PANEL[0], UPG_PANEL[1]
    chip_glyph(t, px_ + 7, py_ + 4)
    t.hline(px_ + 5, py_ + 17, UPG_PANEL[2] - 8, PANEL.shadow)
    t.hline(px_ + 5, py_ + 18, UPG_PANEL[2] - 8, PANEL.light)
    for i in range(UPG_SLOTS):
        slot(t, px_ + UPG_SLOT[0] + 18 * i, py_ + UPG_SLOT[1])
    return t.save(paths.TEXTURES / "gui" / "machine" / "upgrades.png")


if __name__ == "__main__":
    print(side_config_texture())
    print(upgrades_texture())
    for n, spec in LAYOUTS.items():
        print(build(n, spec))
    print(write_java())
