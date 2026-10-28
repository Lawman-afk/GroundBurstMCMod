"""The world's Cold War sites (DESIGN.md §22, Phase 15): their templates, their structure and structure-set definitions,
and the biome tags they stand in.

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
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from build import AIR, Build, gb  # noqa: E402
import site_radar  # noqa: E402
import site_test  # noqa: E402
import site_silo  # noqa: E402
import site_bunker  # noqa: E402
import site_crashed  # noqa: E402
from gb import paths  # noqa: E402

DATA = paths.DATA
CONC, CONC_DARK, CONC_WHITE = "light_gray_concrete", "gray_concrete", "white_concrete"
RC, HAZ, STEEL, CASING = gb("reinforced_concrete"), gb("hazard_concrete"), gb("steel_plating"), gb("machine_casing")
LEAD, GLASS_R = gb("lead_block"), gb("reactor_glass")
RUST = ("brown_terracotta", "terracotta", "orange_terracotta")


# =====================================================================================================================
# Helpers the sites not yet rebuilt still use (the research station's dome and dish).
# =====================================================================================================================
def dome(b: Build, cx: float, cy: float, cz: float, r: float, shell: str, panel: str, broken: float = 0.0,
         gap: tuple[float, float] | None = None) -> None:
    """A hemispherical shell (a radome): its panels in two materials laid out irregularly (later radomes spaced their
    panels at random so they would not interfere with the radar); a stretch of it fallen in {gap} (an angle range)."""
    for x in range(math.floor(cx - r - 1), math.ceil(cx + r + 1) + 1):
        for z in range(math.floor(cz - r - 1), math.ceil(cz + r + 1) + 1):
            for y in range(int(cy), int(cy + r + 1)):
                d = math.sqrt((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 + (z + 0.5 - cz) ** 2)
                if r - 1 < d <= r:
                    ang = math.atan2(z + 0.5 - cz, x + 0.5 - cx)
                    if gap and gap[0] < ang < gap[1] and y > cy + 1:
                        continue
                    if b.rng.random() < broken:
                        continue
                    b.set(x, y, z, panel if b.rng.random() < 0.3 else shell)


def dish(b: Build, cx: float, cy: float, cz: float, r: float, tilt: float, yaw: float, block: str, rim: str) -> None:
    """A parabolic dish of radius {r}, tilted {tilt} radians from facing up, turned {yaw}: its shell one block thick."""
    focal = r * 0.55
    ct, st, cy_, sy = math.cos(tilt), math.sin(tilt), math.cos(yaw), math.sin(yaw)
    steps = int(r * 10)
    for i in range(steps + 1):
        rho = r * i / steps
        for j in range(int(2 * math.pi * rho * 3) + 1):
            a = 2 * math.pi * j / max(1, int(2 * math.pi * rho * 3))
            u, v = rho * math.cos(a), rho * math.sin(a)
            w = rho * rho / (4 * focal)
            # tilt about x, then turn about y
            y1, w1 = v * ct - w * st, v * st + w * ct
            x2, z2 = u * cy_ - w1 * sy, u * sy + w1 * cy_
            b.set(round(cx + x2 - 0.5), round(cy + y1 - 0.5), round(cz + z2 - 0.5), rim if rho > r - 0.8 else block)


# =====================================================================================================================
# 6. The waste storage (WIPP): rooms mined from salt, drums three high under their sacks, decontamination, a hot cell.
# =====================================================================================================================
SALT = ("calcite", "calcite", "calcite", "diorite", "white_terracotta", "smooth_quartz")


def waste_storage() -> Build:
    b = Build(39, 9, 33, seed=71)
    F, C = 1, 6                                       # floor, ceiling
    # The salt: banded (halite with clay seams), with the rooms cut out of it.
    for y in range(0, 9):
        band = ("calcite", "diorite", "white_terracotta")[(y // 2) % 3]
        for x in range(39):
            for z in range(33):
                b.set(x, y, z, band if b.rng.random() < 0.55 else b.rng.choice(SALT))
    # The main drift east-west, rails down it, roof bolts (bars) and lamps.
    b.clear(0, F + 1, 14, 38, C - 1, 18)
    for x in range(0, 39):
        b.set(x, F + 1, 16, "rail", {"shape": "east_west", "waterlogged": "false"})
        if x % 4 == 0:
            b.set(x, C - 1, 14, "iron_bars")
            b.set(x, C - 1, 18, "iron_bars")
        if x % 8 == 4:
            b.lantern(x, C - 1, 16)
    # Rooms (panels) off it: north side z 2..12, south side z 20..30, seven across, three-wide pillars between.
    rooms_n = ((2, 8), (12, 18), (22, 28), (31, 37))
    rooms_s = ((2, 8), (12, 18), (22, 28), (31, 37))
    for x0, x1 in rooms_n:
        b.clear(x0, F + 1, 2, x1, C - 1, 13)
    for x0, x1 in rooms_s:
        b.clear(x0, F + 1, 19, x1, C - 1, 30)
    # Drum rooms: drums three high in rows, filled from the back, a white sack layer on top (magnesium oxide).
    def drums(x0: int, x1: int, z0: int, z1: int, gaps: float = 0.04) -> None:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x - x0) % 3 == 2:
                    continue                                 # an aisle between rows of seven-packs
                for y in range(F + 1, F + 4):
                    if b.rng.random() > gaps:
                        b.set(x, y, z, gb("waste_drum"))
                if b.name(x, F + 3, z) == gb("waste_drum") and b.rng.random() < 0.8:
                    b.set(x, F + 4, z, "white_carpet")
    drums(2, 8, 2, 11)
    drums(12, 18, 2, 9)
    drums(22, 28, 20, 30, gaps=0.12)
    # One north room closed off by a bulkhead (its drums sealed in).
    b.fill(22, F + 1, 13, 28, C - 1, 13, RC)
    drums(22, 28, 2, 11, gaps=0.0)
    b.set(25, F + 2, 13, "gray_concrete")
    # The far north room collapsed (the salt creeping in): rubble over crushed drums.
    for x in range(31, 38):
        for z in range(2, 14):
            for y in range(F + 1, C):
                if b.rng.random() < 0.55 + 0.1 * (y - F):
                    b.set(x, y, z, b.rng.choice(SALT + ("gravel",)))
                elif y == F + 1 and b.rng.random() < 0.3:
                    b.set(x, y, z, gb("waste_drum"))
    # Decontamination at the west end of the south side: a shower, lockers, suits on stands, the waste store's chest.
    b.fill(2, F, 20, 8, F, 30, CONC)
    b.walls(2, F + 1, 20, 8, C - 1, 30, CONC)
    b.clear(4, F + 1, 20, 6, F + 3, 20)
    b.door(5, F + 1, 20, "north", "iron_door")
    b.set(4, F + 1, 20, CONC)
    b.set(6, F + 1, 20, CONC)
    b.set(4, F + 2, 20, CONC)
    b.set(6, F + 2, 20, CONC)
    for z in (23, 26):
        b.trapdoor(4, F, z, "iron_trapdoor", "north", half="top")
        b.set(4, C - 2, z, "lightning_rod", {"facing": "down", "powered": "false", "waterlogged": "false"})
    b.marker(7, F + 1, 22, "stand:hazmat")
    b.marker(7, F + 1, 24, "stand:hazmat")
    b.marker(7, F + 1, 26, "stand:lead")
    for z in (28, 29):
        b.barrel(3, F + 1, z, "east")
    b.chest(7, F + 1, 29, "west", "waste_storage")
    b.lamp(5, C - 1, 25)
    # The hot cell: lead-lined, a thick window into it, the manipulator's arms through the wall, a chest inside.
    b.fill(12, F, 19, 18, C - 1, 30, CONC)
    b.fill(13, F + 1, 20, 17, C - 2, 29, LEAD)
    b.clear(14, F + 1, 21, 16, C - 3, 28)
    b.fill(14, F + 2, 20, 16, F + 2, 20, GLASS_R)
    b.clear(14, F + 1, 19, 16, F + 3, 19)
    for x in (14, 16):
        b.set(x, F + 3, 20, "iron_bars")
    b.set(15, F + 1, 20, CASING)
    b.set(15, F + 2, 19, "lever", {"face": "wall", "facing": "north", "powered": "false"})
    b.clear(18, F + 1, 25, 18, F + 2, 25)
    b.clear(17, F + 1, 25, 17, F + 2, 25)
    b.door(17, F + 1, 25, "east", "iron_door")
    b.chest(14, F + 1, 27, "east", "waste_storage")
    b.set(16, F + 1, 22, gb("waste_drum"))
    # The shaft at the west end: the cage at its foot, sealed above by a concrete plug.
    b.clear(0, F + 1, 14, 3, C + 1, 18)
    b.fill(0, C + 1, 14, 3, 8, 18, RC)
    for y in range(F + 1, C + 1):
        for x, z in ((0, 15), (0, 17), (2, 15), (2, 17)):
            b.set(x, y, z, "iron_bars")
    b.set(1, F + 1, 16, "iron_block")
    b.cobwebs((0, 0, 0, 38, 8, 32), 0.15)
    b.rubble((0, F + 1, 0, 38, F + 1, 32), 0.02, ("calcite", "diorite", "gravel"))
    return b


# =====================================================================================================================
# 7. The exotic research station in the End: a deck on a floating rock, a glass dome over a broken containment ring,
# exotic crystals growing through the floor.
# =====================================================================================================================
def research_station() -> Build:
    b = Build(31, 24, 31, seed=81)
    c = 15.0
    D = 9                                            # the deck
    # The rock under it: an inverted cone of end stone, ragged.
    for y in range(0, D):
        r = 11.5 * ((y + 1) / D) ** 0.8
        for x in range(31):
            for z in range(31):
                d = math.hypot(x + 0.5 - c, z + 0.5 - c)
                if d <= r - b.rng.random() * 1.6:
                    b.set(x, y, z, "end_stone" if b.rng.random() < 0.9 else gb("voidstone"))
    # The deck: an octagon of steel plating over reinforced concrete, hazard bands at its edge.
    for x in range(31):
        for z in range(31):
            dx, dz = abs(x + 0.5 - c), abs(z + 0.5 - c)
            if max(dx, dz) <= 13 and dx + dz <= 18:
                edge = max(dx, dz) > 12 or dx + dz > 17
                b.set(x, D, z, HAZ if edge else (STEEL if (x + z) % 7 else RC))
    # The dome over the containment chamber.
    dome(b, c, D + 1, c, 7.4, GLASS_R, STEEL, broken=0.08, gap=(-2.6, -1.6))
    # The containment ring, broken: a ring of steel and casings round a scorched pit where the exotic matter got out.
    for i in range(24):
        a = 2 * math.pi * i / 24
        x, z = round(c + 4.5 * math.cos(a) - 0.5), round(c + 4.5 * math.sin(a) - 0.5)
        if not (0.4 < a < 1.3):
            b.set(x, D + 1, z, CASING if i % 3 == 0 else "iron_block")
            if i % 6 == 0:
                b.set(x, D + 2, z, "end_rod", {"facing": "up"})
    for x in range(11, 20):
        for z in range(11, 20):
            if math.hypot(x + 0.5 - c, z + 0.5 - c) < 3:
                b.set(x, D, z, b.rng.choice(("obsidian", "crying_obsidian", gb("voidstone"), "black_concrete")))
    # Exotic crystals grown through the floor round the pit.
    for _ in range(14):
        a, d = b.rng.random() * 2 * math.pi, 2 + b.rng.random() * 6
        x, z = round(c + d * math.cos(a) - 0.5), round(c + d * math.sin(a) - 0.5)
        if b.name(x, D + 1, z) in (None, AIR):
            b.set(x, D, z, gb("voidstone"))
            b.set(x, D + 1, z, gb(b.rng.choice(("exotic_crystal_cluster", "large_exotic_bud", "small_exotic_bud"))), {"facing": "up", "waterlogged": "false"})
    # Two laboratories on the deck, benches and chests inside.
    for x0, z0 in ((2, 12), (24, 12)):
        b.room(x0, D, z0, x0 + 4, D + 4, z0 + 6, "white_concrete", floor=STEEL, roof=STEEL)
        b.clear(x0 + 1, D + 1, z0 + 6, x0 + 3, D + 2, z0 + 6) if x0 < 10 else b.clear(x0 + 1, D + 1, z0, x0 + 3, D + 2, z0)
        b.fill(x0 + 1, D + 1, z0 + 1, x0 + 3, D + 1, z0 + 1, CASING)
        b.chest(x0 + 2, D + 1, z0 + 3, "east" if x0 < 10 else "west", "research_station")
        b.set(x0 + 4 if x0 < 10 else x0, D + 2, z0 + 3, "glass_pane")
        b.set(x0 + 2, D + 3, z0 + 4, "end_rod", {"facing": "down"})
    # The antenna, its dish bent over.
    for y in range(D + 1, D + 12):
        b.set(27, y, 27, "iron_bars")
    dish(b, 25.5, D + 12, 25.5, 2.6, 2.2, 0.8, "iron_block", "white_concrete")
    # Debris drifting off the deck.
    for cx_, cy_, cz_ in ((3, 14, 3), (28, 6, 4), (5, 4, 27), (27, 17, 22)):
        for _ in range(5):
            b.set(cx_ + b.rng.randint(-1, 1), cy_ + b.rng.randint(-1, 1), cz_ + b.rng.randint(-1, 1),
                  b.rng.choice(("end_stone", STEEL, CONC_WHITE, gb("voidstone"))))
    return b


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
    "waste_storage": waste_storage,
    "research_station": research_station,
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
        return [{"weight": 1, "parts": [part("waste_storage", (-19, 0, -16), "encapsulate")]}]
    if name == "exotic_research_station":
        return [{"weight": 1, "parts": [part("research_station", (-15, -9, -15), "none", weathering="end")]}]
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
