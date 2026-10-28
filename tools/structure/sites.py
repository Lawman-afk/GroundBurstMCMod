"""The world's Cold War sites (DESIGN.md §22, Phase 15): their templates, their structure and structure-set definitions,
and the biome tags they stand in. Each site is built in its own module (site_radar, site_test, site_silo, site_bunker,
site_crashed, site_waste, site_station) with the building kit (kit.py), as rebuilt from scratch in DESIGN.md §22.2.

Researched from the real places (DESIGN.md §22's notes): the Titan II complex (a silo reached by a cableway from a
blast lock with two blast doors, the launch control centre a short tunnel on, the crew's stair down from an access
portal; the 740-ton closure door on rails; work platforms round the missile; Atlas F silos left flooded), the
Diefenbunker (a curved blast-diffuser entrance, blast doors, decontamination showers, a situation room with wall maps,
a radio room, filters and generators, an escape hatch filled with gravel), DEW Line stations (a radome over a
"building train" of linked modules, a long-range antenna), Trinity and the Nevada Test Site (the shallow glassed
crater, the shot tower's four concrete footings, the obelisk, the "Jumbo" steel vessel, instrument towers and
earth-covered bunkers 10,000 yards out, Doom Town's blasted houses and bleachers), the Damascus Titan II explosion and
the broken-arrow crashes (a missile in pieces, its warhead thrown clear), and WIPP (rooms of drums stacked three high
under magnesium-oxide sacks, mined from salt, closed off with bulkheads; a hot cell behind lead).

    tools/.venv/Scripts/python.exe tools/structure/sites.py

Writes data/groundburst/structure/site/*.nbt, worldgen/structure/*.json, worldgen/structure_set/*.json and
tags/worldgen/biome/has_structure/*.json under src/main/resources.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from build import Build  # noqa: E402
import site_radar  # noqa: E402
import site_test  # noqa: E402
import site_silo  # noqa: E402
import site_bunker  # noqa: E402
import site_crashed  # noqa: E402
import site_waste  # noqa: E402
import site_station  # noqa: E402
from gb import paths  # noqa: E402

DATA = paths.DATA
CONC = "light_gray_concrete"


def test_room() -> Build:
    """For the GameTests: a small room with a chest (its loot table) and a data marker (a suited stand)."""
    b = Build(5, 4, 5, seed=1)
    b.room(0, 0, 0, 4, 3, 4, CONC)
    b.chest(1, 1, 1, "south", "waste_storage")
    b.marker(3, 1, 3, "stand:hazmat")
    return b


# =====================================================================================================================
TEMPLATES = {
    "test_room": test_room,
    "silo_surface_intact": lambda: site_silo.silo_surface("intact"), "silo_surface_flooded": lambda: site_silo.silo_surface("flooded"),
    "silo_surface_collapsed": lambda: site_silo.silo_surface("collapsed"),
    "silo_below_intact": lambda: site_silo.silo_below("intact"), "silo_below_flooded": lambda: site_silo.silo_below("flooded"),
    "silo_below_collapsed": lambda: site_silo.silo_below("collapsed"),
    "bunker_surface": site_bunker.bunker_surface, "bunker_below": site_bunker.bunker_below,
    "radar_outpost": site_radar.radar_outpost,
    "test_site_tower": site_test.test_site_tower, "test_site_zero": site_test.test_site_zero,
    "crashed_missile": site_crashed.crashed_missile,
    "waste_storage": site_waste.waste_storage,
    "research_station": site_station.research_station,
}


def part(template: str, offset: tuple[int, int, int], terrain: str = "none", integrity: float = 1.0, weathering: str = "abandoned") -> dict:
    return {"template": f"groundburst:site/{template}", "offset": list(offset), "terrain": terrain, "integrity": integrity,
            "weathering": weathering}


#: The structures: (biome tag values, generation step, placement, spacing, separation, salt, extra, variants). The
#: spacings and max_slope (the most the land may rise and fall over the parts on the surface, read on a 5x5 grid) were
#: set from the world survey's census (`gradlew runWorldSurvey -Psurvey=census`, DESIGN.md §22.1): each site about as
#: common as the vanilla structure it is most like. apart_from_villages: no closer than this many chunks to where a
#: village could stand (vanilla's exclusion zone, as its pillager outposts keep), since the census found a few sites in
#: or against villages.
STRUCTURES = {
    "abandoned_silo": dict(
        biomes=["#minecraft:is_taiga", "minecraft:plains", "minecraft:sunflower_plains", "minecraft:snowy_plains", "minecraft:desert",
                "minecraft:savanna", "minecraft:meadow"],
        step="surface_structures", placement="surface", spacing=48, separation=18, salt=1_946_311, max_slope=12, apart_from_villages=6,
        variants=[(3, "intact"), (2, "flooded"), (2, "collapsed")]),
    "command_bunker": dict(
        biomes=["#minecraft:is_hill", "#minecraft:is_forest", "#minecraft:is_taiga", "minecraft:meadow", "minecraft:plains"],
        step="surface_structures", placement="surface", spacing=52, separation=20, salt=1_957_101, max_slope=8, apart_from_villages=6),
    "radar_outpost": dict(
        biomes=["#minecraft:is_hill", "minecraft:meadow", "minecraft:savanna_plateau", "minecraft:wooded_badlands",
                "minecraft:snowy_plains", "minecraft:plains", "minecraft:windswept_savanna"],
        step="surface_structures", placement="surface", spacing=44, separation=16, salt=1_954_820, max_slope=12, apart_from_villages=6),
    "test_site": dict(
        biomes=["minecraft:desert", "#minecraft:is_badlands"],
        step="surface_structures", placement="surface", spacing=32, separation=12, salt=1_945_716, max_slope=12, apart_from_villages=6),
    "crashed_missile": dict(
        biomes=["minecraft:plains", "minecraft:sunflower_plains", "#minecraft:is_forest", "#minecraft:is_taiga", "#minecraft:is_savanna",
                "minecraft:desert", "#minecraft:is_badlands", "minecraft:snowy_plains", "minecraft:meadow"],
        step="surface_structures", placement="surface", spacing=38, separation=14, salt=1_980_919, max_slope=5, apart_from_villages=6),
    "waste_storage": dict(
        biomes=["#minecraft:is_overworld"],
        step="underground_structures", placement="buried", spacing=44, separation=16, salt=1_999_326, depth=42, frequency=0.35),
    "exotic_research_station": dict(
        biomes=["minecraft:end_highlands", "minecraft:end_midlands", "minecraft:end_barrens", "minecraft:small_end_islands"],
        step="surface_structures", placement="floating", spacing=64, separation=24, salt=1_983_055),
}


def site_variants(name: str) -> list[dict]:
    if name == "abandoned_silo":
        # The origin is the shaft's axis at grade, (20, ., 18) in both templates; the complex's top layer just under it.
        ax, az = site_silo.AX, site_silo.AZ
        return [{"weight": w, "parts": [part(f"silo_surface_{v}", (-ax, 0, -az), "beard_thin", weathering="none"),
                                        part(f"silo_below_{v}", (-ax, -site_silo.BELOW[1], -az), "encapsulate", weathering="none")]}
                for w, v in STRUCTURES[name]["variants"]]
    if name == "command_bunker":
        # The origin is at (22, ., 22) in both templates; the bunker's top layer just under the ground.
        ox, oz = site_bunker.ORIGIN
        return [{"weight": 1, "parts": [part("bunker_surface", (-ox, 0, -oz), "beard_thin", weathering="none"),
                                        part("bunker_below", (-ox, -site_bunker.BELOW_Y, -oz), "encapsulate", weathering="none")]}]
    if name == "radar_outpost":
        return [{"weight": 1, "parts": [part("radar_outpost", (-23, 0, -22), "beard_thin", weathering="none")]}]
    if name == "test_site":
        # The shot tower's middle is the origin; the earlier shot's crater forty-eight blocks east, sunk three.
        return [{"weight": 1, "parts": [part("test_site_tower", (-24, 0, -24), "beard_thin", weathering="none"),
                                        part("test_site_zero", (28, -site_test.GRADE, -20), "beard_thin", weathering="none")]}]
    if name == "crashed_missile":
        # The origin is the wreck's block on the land; the furrow and crater are dug below the template's grade.
        return [{"weight": 1, "parts": [part("crashed_missile", (-site_crashed.WX, -site_crashed.G, -site_crashed.WZ), "beard_thin",
                                             weathering="none")]}]
    if name == "waste_storage":
        # The origin is at the drift's middle; the whole block of salt is buried (placement "buried").
        return [{"weight": 1, "parts": [part("waste_storage", (-22, 0, -19), "encapsulate", weathering="none")]}]
    if name == "exotic_research_station":
        # The origin is the hall's middle on the deck; the rock hangs under it.
        return [{"weight": 1, "parts": [part("research_station", (-site_station.C, -site_station.D, -site_station.C), "none",
                                             weathering="none")]}]
    raise KeyError(name)


def check(name: str, b: Build) -> None:
    """Every block a template names must exist (26.3 renamed some: chain is iron_chain): vanilla's from the decompiled
    client's blockstates, ours from the mod's."""
    missing = set()
    for _, block, _ in b.t.blocks():
        ns, _, n = block.partition(":")
        folder = paths.VANILLA_ASSETS / "blockstates" if ns == "minecraft" else paths.ASSETS / "blockstates"
        if n != "air" and not (folder / f"{n}.json").exists():
            missing.add(block)
    if missing:
        raise SystemExit(f"{name}: unknown blocks {sorted(missing)}")


def main() -> None:
    out = DATA / "structure" / "site"
    for name, make in TEMPLATES.items():
        b = make()
        if hasattr(b, "finish"):
            b.finish()                                   # what waits for the walls: buttons by the iron doors
        check(name, b)
        b.save(out / f"{name}.nbt")
        print("template", name)
    for name, s in STRUCTURES.items():
        structure = {"type": "groundburst:site", "biomes": f"#groundburst:has_structure/{name}", "spawn_overrides": {}, "step": s["step"],
                     "variants": site_variants(name), "placement": s["placement"]}
        if s["placement"] != "floating":
            # Not "none": the beardifier skips a structure whose own adaptation is none before it asks the pieces for
            # theirs (each SitePiece gives its own: beard_thin, encapsulate).
            structure["terrain_adaptation"] = "beard_thin"
        if "max_slope" in s:
            structure["max_slope"] = s["max_slope"]
        if "depth" in s:
            structure["depth"] = s["depth"]
        path = DATA / "worldgen" / "structure" / f"{name}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(structure, indent=2) + "\n", encoding="utf-8")
        sset = {"placement": {"type": "minecraft:random_spread", "salt": s["salt"], "separation": s["separation"], "spacing": s["spacing"]},
                "structures": [{"structure": f"groundburst:{name}", "weight": 1}]}
        if "frequency" in s:
            sset["placement"]["frequency"] = s["frequency"]       # a share of the places it could stand
        if "apart_from_villages" in s:
            sset["placement"]["exclusion_zone"] = {"chunk_count": s["apart_from_villages"], "other_set": "minecraft:villages"}
        path = DATA / "worldgen" / "structure_set" / f"{name}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(sset, indent=2) + "\n", encoding="utf-8")
        tag = DATA / "tags" / "worldgen" / "biome" / "has_structure" / f"{name}.json"
        tag.parent.mkdir(parents=True, exist_ok=True)
        tag.write_text(json.dumps({"values": s["biomes"]}, indent=2) + "\n", encoding="utf-8")
        print("structure", name)


if __name__ == "__main__":
    main()
