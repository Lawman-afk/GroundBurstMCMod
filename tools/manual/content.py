"""Field Manual content (DESIGN.md §24.2): writes assets/groundburst/manual/chapters/*.json and entries/<chapter>/*.json.

Text markup: paragraphs split by a blank line; **bold**, *italic*; lines starting "- " are bullets; [label](chapter/entry)
links to an entry. Page types: text, spotlight, crafting, smelting, machine, structure, image, table, links (see
client/manual/Manual.java). Recipes are not written here: crafting and machine pages read the game's recipes, so they
always match. Chemistry and nuclear content stays a game abstraction: what each machine and item does in the game,
never real quantities, conditions or designs.

Run: tools\\.venv\\Scripts\\python.exe tools\\manual\\content.py
"""
from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gb import paths  # noqa: E402

ROOT = paths.ASSETS / "manual"
G = "groundburst:"


def gb(name: str) -> str:
    return name if ":" in name else G + name


# --- page helpers -------------------------------------------------------------------------------------------------------
def text(t: str) -> dict:
    return {"type": "text", "text": t.strip()}


def spot(item: str, t: str = "", title: str | None = None) -> dict:
    p = {"type": "spotlight", "item": gb(item), "text": t.strip()}
    if title:
        p["title"] = title
    return p


def craft(item: str, t: str = "") -> dict:
    return {"type": "crafting", "item": gb(item), "text": t.strip()}


def smelt(item: str, t: str = "") -> dict:
    return {"type": "smelting", "item": gb(item), "text": t.strip()}


def machine(kind: str, t: str = "", item: str | None = None, fluid: str | None = None) -> dict:
    p = {"type": "machine", "kind": kind, "text": t.strip()}
    if item:
        p["item"] = gb(item)
    if fluid:
        p["fluid"] = gb(fluid)
    return p


def structure(sid: str, t: str = "") -> dict:
    return {"type": "structure", "structure": gb(sid), "text": t.strip()}


def image(name: str, w: int, h: int, t: str = "") -> dict:
    return {"type": "image", "image": f"groundburst:textures/gui/manual/plates/{name}.png", "width": w, "height": h, "text": t.strip()}


def table(rows: list[list[str]], title: str | None = None, t: str = "") -> dict:
    p = {"type": "table", "rows": rows, "text": t.strip()}
    if title:
        p["title"] = title
    return p


def links(entries: list[str], title: str | None = None) -> dict:
    p = {"type": "links", "entries": entries, "text": ""}
    if title:
        p["title"] = title
    return p


CHAPTERS: list[dict] = []
ENTRIES: list[dict] = []


def chapter(cid: str, title: str, icon: str, intro: str) -> None:
    CHAPTERS.append({"id": cid, "title": title, "icon": gb(icon), "order": len(CHAPTERS), "text": intro.strip()})


def entry(eid: str, title: str, icon: str, items: list[str], pages: list[dict]) -> None:
    chap = eid.split("/")[0]
    order = sum(1 for e in ENTRIES if e["id"].startswith(chap + "/"))
    ENTRIES.append({"id": eid, "title": title, "icon": gb(icon), "order": order, "items": [gb(i) for i in items], "pages": pages})


# Plates rendered by tools/manual/plates.py (width x height in pixels).
PLATES = {"chemical_plant": (240, 220), "fission_reactor": (240, 260), "particle_accelerator": (240, 150),
          "fusion_reactor": (240, 150), "singularity_reactor": (240, 240)}


def plate(name: str, t: str = "") -> dict:
    w, h = PLATES[name]
    return image(name, w, h, t)


# =========================================================================================================================
chapter("getting_started", "Getting Started", "field_manual", """
This manual covers everything Ground Burst adds, from the first ore to the reactors that power the late game.

Start with [First steps](getting_started/first_steps). Each chapter lists its entries; a red dot marks one you have not read yet.
""")

entry("getting_started/field_manual", "Using this manual", "field_manual", ["field_manual"], [
    spot("field_manual", """
Every new player is handed one on their first join. Right-click to open it; it reopens where you left it.
"""),
    text("""
**Turning pages:** the arrows at the bottom, the mouse wheel, or the left and right arrow keys.

**Getting around:** the house at the top left goes back to the contents; the curled arrow (or a right-click, or Backspace) goes back one step.

**Links:** underlined words open another entry. Items in recipes open their own entry when clicked.

**Search:** type on the first page. It looks through titles, the items each entry covers and the text.

**Bookmarks:** the grey ribbon on the book's edge bookmarks the open entry; right-click a red ribbon to remove it. Six at most.

**From any screen:** hover over an item and press **H** (change it in Controls) to open its entry.
"""),
    craft("field_manual", "A spare copy: a book and some gunpowder."),
])

entry("getting_started/first_steps", "First steps", "iron_dust", [], [
    text("""
Ground Burst is a long climb from hand tools to superweapons. Everything runs on **power** (FE), made by generators and carried by cables.

- Mine the new ores: uranium, lead, lithium, rare earths, saltpeter, sulfur and tungsten. See [Ores](resources/ores).
- Build a [Combustion Generator](power/combustion_generator) and some [Copper Cable](power/cables).
- Build an [Ore Crusher](processing/ore_crusher): two dusts per ore, smelted into two ingots.
- Build an [Arc Furnace](processing/arc_furnace) to make **steel**, which nearly everything else needs.
- With an [Industrial Press](processing/industrial_press) you can make plates, rods, wire and canisters.

From there, fluids, chemistry and circuits open up; then nuclear power.
"""),
    links(["getting_started/machines", "getting_started/wrench", "getting_started/multiblocks", "resources/ores", "power/combustion_generator"]),
])

entry("getting_started/machines", "Working machines", "machine_frame", ["machine_frame"], [
    text("""
Every machine has the same screen layout:

- **Energy gauge** on the left: the stored FE. Hover for the numbers.
- **Status lamp** at the top right: green while working; hover to see what it is waiting for (power, input, room for its output...).
- **Redstone button**: ignore redstone, run only with a signal, or only without one.
- **Side tab** (the wrench on the right edge): pick which faces take items in, give products out, connect to power and fluids, and turn on **auto-eject** to push products into whatever is next to it.

Machines keep working while you are away as long as their chunk is loaded. Most need a [Machine Frame](components/parts) to build.
"""),
    craft("machine_frame"),
])

entry("getting_started/wrench", "The Wrench", "wrench", ["wrench"], [
    spot("wrench", """
- Right-click a machine to turn it.
- Right-click a side of a fluid pipe to cycle it: Normal, Pull (draws fluid out of what it touches) and Disconnected.
- Right-click a cable side to connect or disconnect it.
- Sneak-right-click a machine, pipe, cable, tank or structure block to pick it up, contents and all for capacitor banks.
"""),
    craft("wrench"),
])

entry("getting_started/multiblocks", "Multiblocks", "holo_projector", ["holo_projector", "machine_casing", "reactor_glass", "item_port", "fluid_port", "energy_port"], [
    text("""
The largest machines are **structures**: a controller block surrounded by parts in a set pattern. When the last block is in place the structure *forms* and becomes one machine with its own model.

Right-click an unfinished controller to see a ghost of every missing block, layer by layer; or hold a [Holo-Projector](getting_started/multiblocks). Breaking any part un-forms it again.

A structure connects to the outside world only through its **ports**:

- **Item Port**: items in and products out (pipes, hoppers, conveyors).
- **Fluid Port**: fills input tanks and drains product tanks.
- **Energy Port**: power in, or out for a reactor.

The controller itself has no faces to connect to and no side tab.
"""),
    spot("holo_projector", "Hold it near an unfinished structure to project the blocks still missing; use it to step through the layers one at a time."),
    craft("holo_projector"),
    craft("item_port"),
    craft("fluid_port"),
    craft("energy_port"),
    craft("machine_casing"),
    craft("reactor_glass"),
    links(["processing/chemical_plant", "nuclear/fission_reactor", "nuclear/particle_accelerator", "nuclear/fusion_reactor",
           "nuclear/singularity_reactor"], "Structures"),
])

# =========================================================================================================================
chapter("resources", "Resources", "raw_uranium", """
The ores, minerals and materials that everything else is made from, and where to find them.
""")

entry("resources/ores", "Ores", "uranium_ore", [
    "uranium_ore", "deepslate_uranium_ore", "raw_uranium", "lead_ore", "deepslate_lead_ore", "raw_lead", "lithium_ore",
    "deepslate_lithium_ore", "raw_lithium", "rare_earth_ore", "deepslate_rare_earth_ore", "raw_rare_earth", "tungsten_ore",
    "deepslate_tungsten_ore", "raw_tungsten", "sulfur_ore", "deepslate_sulfur_ore", "nether_sulfur_ore", "saltpeter_ore",
    "deepslate_saltpeter_ore", "sandstone_saltpeter_ore", "fluorite_ore", "deepslate_fluorite_ore", "fluorite"], [
    text("""
Eight new ores generate in new chunks. Each has a deepslate form; sulfur also grows in the Nether and saltpeter in desert and badlands sandstone. Tungsten is the rarest; lithium and rare earths are richer in the mountains, uranium in the badlands.
"""),
    table([
        ["Uranium", "y -48 to 24"],
        ["  also", "badlands"],
        ["Lead", "y -32 to 48"],
        ["Lithium", "y -16 to 64"],
        ["  also", "mountains"],
        ["Rare earth", "y -32 to 48"],
        ["  also", "mountains"],
        ["Tungsten", "y -80 to 0"],
        ["Sulfur", "y -64 to 16"],
        ["  also", "the Nether"],
        ["Saltpeter", "y 0 to 80"],
        ["  also", "deserts"],
        ["Fluorite", "y -24 to 72"],
    ], "Where they are"),
    text("""
Uranium, lead, lithium, rare earths and tungsten drop **raw ores**: crush them in an [Ore Crusher](processing/ore_crusher) for two dusts each. Sulfur and saltpeter drop their dusts directly, fluorite its purple and green crystals (see [Silicon and chips](components/semiconductors)).

Vanilla's cinnabar gives cinnabar dust, the source of mercury. Each ore can be turned off in the server config.
"""),
    spot("raw_uranium", "Uranium is mildly radioactive. Handle it sensibly; the manual will say more once radiation arrives."),
])

entry("resources/dusts", "Dusts and ingots", "iron_dust", [
    "iron_dust", "copper_dust", "gold_dust", "lead_dust", "lead_ingot", "uranium_dust", "uranium_ingot", "lithium_dust",
    "rare_earth_dust", "tungsten_dust", "cobalt_dust", "cobalt_ingot", "cinnabar_dust"], [
    text("""
Crushing a raw ore gives **two dusts**, so crushing before smelting doubles your metal. Dusts smelt in a furnace like raw ore; tungsten and cobalt need the heat of an [Arc Furnace](processing/arc_furnace).

Lead, uranium and tungsten also come as storage blocks.
"""),
    machine("crushing", "", item="lead_dust"),
    smelt("lead_ingot"),
    smelt("uranium_ingot"),
])

entry("resources/steel", "Steel and refractory metals", "steel_ingot", ["steel_ingot", "tungsten_ingot", "tungsten_carbide_ingot", "neodymium_magnet"], [
    text("""
**Steel** is the backbone of every machine. An [Arc Furnace](processing/arc_furnace) makes it from iron (ingot or dust) and coal.

**Tungsten** only melts in the arc furnace too. With more coal it becomes **tungsten carbide**, the hardest material you will make.

Rare-earth dust and iron fuse into **neodymium magnets** for motors and generators.
"""),
    machine("arc_smelting", "", item="steel_ingot"),
    machine("arc_smelting", "", item="tungsten_carbide_ingot"),
    machine("arc_smelting", "", item="neodymium_magnet"),
])

entry("resources/powders", "Sulfur and saltpeter", "sulfur_dust", ["sulfur_dust", "saltpeter_dust"], [
    text("""
Sulfur and saltpeter come straight from their ores as dust. With charcoal they make **gunpowder**, four at a time.

Sulfur also feeds the [Chemical Plant](processing/chemical_plant), which turns it into sulfuric acid.
"""),
    craft("minecraft:gunpowder"),
])

entry("resources/exotic", "Voidstone and exotic matter", "exotic_shard", ["voidstone", "budding_voidstone", "small_exotic_bud",
                                                                         "large_exotic_bud", "exotic_crystal_cluster", "exotic_shard"], [
    text("""
In the End, hollow **void geodes** hang in the undersides and flanks of the outer islands. They are lined with voidstone, and some of it is **budding voidstone**, which slowly grows exotic crystals.

Break a fully grown cluster for **exotic matter shards**. They feed the [Exotic Matter Fabricator](processing/exotic_matter_fabricator), and through it the most powerful machines and weapons in the game.

Budding voidstone drops nothing when broken, not even with Silk Touch: leave it where it is and harvest the crystals.
"""),
    spot("exotic_shard"),
])

# =========================================================================================================================
chapter("power", "Power", "combustion_generator", """
Machines run on Forge Energy (FE). Generators make it, cables carry it and capacitor banks store it. Any other mod's FE cables, generators and batteries work too.
""")

entry("power/combustion_generator", "Combustion Generator", "combustion_generator", ["combustion_generator"], [
    spot("combustion_generator", """
Burns anything a furnace burns for **60 FE a tick** and pushes the power into cables and machines beside it. It stops burning while its store is full, so no fuel is wasted.
"""),
    craft("combustion_generator"),
    links(["power/cables", "power/diesel_generator"]),
])

entry("power/cables", "Cables", "copper_cable", ["copper_cable", "insulated_cable", "hv_cable"], [
    text("""
Cables join up with each other and with machines. A run carries as much as its weakest cable each tick:

- **Copper cable:** 256 FE
- **Insulated cable:** 1,024 FE
- **High-voltage cable:** 8,192 FE

Each tier is made from the one below. Use the [Wrench](getting_started/wrench) on a cable side to cut or join a connection.
"""),
    craft("copper_cable"),
    craft("insulated_cable"),
    craft("hv_cable"),
])

entry("power/diesel_generator", "Diesel Generator", "diesel_generator", ["diesel_generator"], [
    spot("diesel_generator", """
A twin-cylinder engine for **180 FE a tick**. Pipe liquid fuel into it or pour a canister in: synthetic fuel gives 90 FE per mB and liquid rocket fuel 140; other mods' diesel works too.
"""),
    craft("diesel_generator"),
    links(["processing/chemical_plant", "processing/fluids"]),
])

entry("power/rtg", "Radioisotope Generator", "rtg", ["rtg"], [
    spot("rtg", """
A steady **40 FE a tick** from a radioisotope slug, with no moving parts. An enriched uranium ingot lasts two in-game days, a plutonium ingot ten.

The slug decays whether the power is used or not, so give it something that always draws, or a capacitor bank.
"""),
    craft("rtg"),
])

entry("power/capacitor_banks", "Capacitor Banks", "capacitor_bank", ["capacitor_bank", "advanced_capacitor_bank",
                                                                        "superconducting_capacitor_bank", "creative_capacitor_bank"], [
    text("""
Banks store power. Every face takes it in except the **front**, which pushes it out; turn a bank with the [Wrench](getting_started/wrench).
"""),
    table([
        ["Capacitor Bank", ""],
        ["  stores", "100,000 FE"],
        ["  moves", "1,024 FE/t"],
        ["Advanced", ""],
        ["  stores", "1,000,000 FE"],
        ["  moves", "8,192 FE/t"],
        ["Superconducting", ""],
        ["  stores", "10,000,000 FE"],
        ["  moves", "32,768 FE/t"],
    ], "Tiers"),
    text("""
A bank keeps its charge when you pick it up, and a comparator reads how full it is.

The **Creative Capacitor Bank** (creative menu only) is always full and pushes up to a million FE a tick.
"""),
    craft("capacitor_bank"),
    craft("advanced_capacitor_bank"),
    craft("superconducting_capacitor_bank"),
])

entry("power/batteries", "Batteries", "battery", ["battery", "battery_charger"], [
    spot("battery", "Holds 50,000 FE. Portable gear (Geiger counters, guns) will run on it."),
    spot("battery_charger", """
Charges a battery, or any other mod's chargeable item, at up to 512 FE a tick and moves it to the output slot when full.
"""),
    craft("battery"),
    craft("battery_charger"),
])

entry("power/fission", "Nuclear power", "uranium_fuel_rod", [], [
    text("""
For the late game, a [Fission Reactor](nuclear/fission_reactor) makes power from uranium fuel rods, 75 FE a tick a rod, one rod in each Fuel Channel: build it bigger for more channels, up to 4,800 FE a tick from sixty-four. It breeds plutonium and tritium on the side.

Fusion and singularity reactors will follow.
"""),
    links(["nuclear/fission_reactor", "nuclear/fuel", "nuclear/fusion_reactor", "nuclear/singularity_reactor"]),
])

# =========================================================================================================================
chapter("processing", "Processing", "ore_crusher", """
The machines that turn raw materials into parts, fluids and chemicals. Each entry shows the machine's recipes as the game has them.
""")

entry("processing/ore_crusher", "Ore Crusher", "ore_crusher", ["ore_crusher"], [
    spot("ore_crusher", "Grinds raw ores into two dusts each, and a few other things into powder. Draws 20 FE a tick."),
    craft("ore_crusher"),
    machine("crushing"),
])

entry("processing/arc_furnace", "Arc Furnace", "arc_furnace", ["arc_furnace"], [
    spot("arc_furnace", "A two-block-tall electric furnace for steel and the metals a furnace cannot melt. Draws 80 FE a tick."),
    craft("arc_furnace"),
    machine("arc_smelting"),
])

entry("processing/industrial_press", "Industrial Press", "industrial_press", ["industrial_press", "plate_die", "rod_die", "wire_die", "canister_die"], [
    spot("industrial_press", """
Presses ingots into parts. The **die** in its die slot picks what it makes; dies are never used up. Draws 40 FE a tick.
"""),
    craft("industrial_press"),
    craft("plate_die"),
    craft("rod_die"),
    craft("wire_die"),
    craft("canister_die"),
    machine("pressing"),
])

entry("processing/electrolytic_separator", "Electrolytic Separator", "electrolytic_separator", ["electrolytic_separator",
                                                                                         "proton_exchange_membrane"], [
    spot("electrolytic_separator", """
Splits water with electricity. With a **Proton Exchange Membrane** in its slot it makes **hydrogen**: 1,000 mB from a bucket of water in ten seconds (the oxygen is let go). With the slot empty it concentrates **deuterium** instead: 1,000 mB from eight buckets. The membrane is never used up. Pipe water into its water tank (a [Water Pump](processing/water_pump) is easiest) and draw the gas off with a pipe or a gas canister. Draws 60 FE a tick.
"""),
    craft("electrolytic_separator"),
    craft("proton_exchange_membrane"),
    machine("electrolysis"),
])

entry("processing/cryogenic_condenser", "Cryogenic Condenser", "cryogenic_condenser", ["cryogenic_condenser", "oxygen_membrane", "nitrogen_membrane",
                                                                                   "argon_sieve", "argon_canister"], [
    spot("cryogenic_condenser", """
Draws gases out of the air. The **membrane** in its slot picks the gas: liquid oxygen or liquid nitrogen, or with the **argon molecular sieve**, argon (a quarter as fast: argon is 1% of the air). Membranes and sieves are never used up. Draws 100 FE a tick.

Cryogenic liquids freeze anything standing in them.
"""),
    craft("cryogenic_condenser"),
    craft("oxygen_membrane"),
    craft("nitrogen_membrane"),
    craft("argon_sieve"),
    machine("cryo_condensing"),
])

entry("processing/water_pump", "Water Pump", "water_pump", ["water_pump"], [
    spot("water_pump", """
Place it on a water source block and power it (20 FE a tick): it draws 50 mB of water a tick without ever using the source up, and pushes it out of its two side outlets.
"""),
    craft("water_pump"),
])

entry("processing/fluids", "Pipes, tanks and canisters", "fluid_pipe", ["copper_fluid_pipe", "fluid_pipe", "pressurized_fluid_pipe", "fluid_tank",
                                                                         "creative_fluid_tank", "empty_canister", "empty_gas_canister"], [
    text("""
Machines push fluids out of faces whose fluid switch is on with auto-eject, and pipes carry them. A pipe side set to **Pull** with the [Wrench](getting_started/wrench) draws fluid out of the tank or machine it touches.

A run carries as much as its weakest pipe: copper 150, steel 600, pressurized 2,400 mB a tick. Pipes hold nothing themselves, so breaking one never spills.
"""),
    text("""
A **Fluid Tank** holds 16,000 mB. Tanks that touch join into one when they fill a box with a square base up to 3x3 and up to 5 high.

**Canisters** carry 1,000 mB of a liquid, **gas canisters** 1,000 mB of a gas; right-click a machine with one to pour it in, sneak-right-click with an empty one to fill it. The [Canning Machine](processing/canning_machine) fills and empties them automatically. Liquids also fit in buckets; spilled acids burn and napalm ignites.
"""),
    text("""
The **Creative Fluid Tank** (creative only) never runs dry: pour any fluid in and buckets, canisters and pipes can draw it forever. Pouring in a different fluid switches it over; sneak-right-click it with an empty hand to clear it.
"""),
    craft("fluid_pipe"),
    craft("copper_fluid_pipe"),
    craft("pressurized_fluid_pipe"),
    craft("fluid_tank"),
    machine("pressing", "", item="empty_canister"),
])

entry("processing/item_pipes", "Item pipes", "item_pipe", ["iron_item_pipe", "item_pipe", "pneumatic_item_pipe"], [
    text("""
**Item pipes** move items between chests and machines. Set a pipe side to **Pull** with the [Wrench](getting_started/wrench) and it draws items out of what it touches (from a machine, only its outputs); machines with auto-eject and hoppers can push in too. The run hands everything straight to the chests and machines on its other sides, in turn, never back where it came from.

Pipes hold nothing, so breaking one never drops items. A run moves as many items as its **weakest** pipe.
"""),
    table([
        ["Iron", "8 items/s"],
        ["Steel", "32 items/s"],
        ["Pneumatic", "128 items/s"],
        ["(a hopper)", "2.5 items/s"],
    ], "Speed"),
    craft("iron_item_pipe"),
    craft("item_pipe"),
    craft("pneumatic_item_pipe"),
])

entry("processing/canning_machine", "Canning Machine", "canning_machine", ["canning_machine"], [
    spot("canning_machine", """
Fills and empties canisters, 1,000 mB at a time. Put an **empty canister** (liquids) or **gas canister** (gases) in and it fills from the **supply tank** on the left; put a filled canister in and it is poured into the **catch tank** on the right. Draws 20 FE a tick.
"""),
    text("""
Pipes and buckets fill the supply tank; pipes, buckets and auto-eject drain the catch tank. It works for every fluid that has a canister, so a line of them can bottle a [Chemical Plant](processing/chemical_plant)'s output for later use.
"""),
    craft("canning_machine"),
    machine("canning", "", item="sulfuric_acid_canister"),
    machine("canning", "", item="deuterium_canister"),
    machine("canning", "", fluid="sulfuric_acid"),
])

entry("processing/chemical_plant", "Chemical Plant", "chemical_plant", ["chemical_plant", "chemical_plant_spawner", "sulfuric_acid_canister",
                                                                         "nitric_acid_canister", "synthetic_fuel_canister", "liquid_rocket_fuel_canister",
                                                                         "thermobaric_slurry_canister", "napalm_canister", "plastic_sheet",
                                                                         "rdx_crystals", "plastic_explosive", "solid_propellant", "yellowcake",
                                                                         "uf6_canister", "heavy_water_canister", "lithium6_dust", "mercury_flask"], [
    plate("chemical_plant", "The formed Chemical Plant."),
    text("""
A 2x2x2 structure for every chemical in the mod: acids, fuels, explosive fillers, uranium compounds and heavy water. Each recipe is one step; the plant draws 60 FE a tick.

It takes items through its **Item Port**, fluids through its **Fluid Port** (water into its water tank, chemicals into its two input tanks; the product drains out the same way) and power through its **Energy Port**. You can also pour canisters and buckets into any part.
"""),
    structure("chemical_plant", "Controller at the front left, the Energy Port beside it, the Fluid Port behind it, the Item Port in the far corner, four Reactor Glass on top."),
    craft("chemical_plant"),
    machine("chemistry"),
])

entry("processing/circuit_fabricator", "Circuit Fabricator", "circuit_fabricator", ["circuit_fabricator"], [
    spot("circuit_fabricator", """
Makes every circuit, from basic to quantum, plus sensor arrays and superconducting coils. Coils need liquid nitrogen in its coolant tank. Draws 40 FE a tick.
"""),
    craft("circuit_fabricator"),
    machine("fabricating"),
])

entry("processing/crystal_puller", "Crystal Puller", "crystal_puller", ["crystal_puller", "silicon_boule", "silicon_wafer", "diamond_wire_saw",
                                                                    "sapphire_boule", "sapphire_wafer", "tungsten_crucible"], [
    spot("crystal_puller", """
Grows the single crystals of silicon that chips are made on. Four **polysilicon** melt in a **quartz crucible** under **argon** (1,000 mB a crystal, piped or poured into its tank), and a seed is drawn slowly up out of the melt, turning: in a minute, a **silicon boule**. Each crystal takes a crucible, as in a real puller. Draws 160 FE a tick.

The crystal shows in the window of its pull chamber as it grows.
"""),
    craft("crystal_puller"),
    machine("crystal_pulling"),
    text("""
An [Industrial Press](processing/industrial_press) with a **Diamond Wire Saw** in its die slot saws a boule into **eight wafers**. The saw is never used up.
"""),
    craft("diamond_wire_saw"),
    machine("pressing", "", item="silicon_wafer"),
    text("""
**Sapphire** grows the same way, slower: four **alumina** melted in a **tungsten crucible** (sapphire melts at 2,050 °C, too hot for fused quartz) under argon give a **sapphire boule** in 80 seconds, and the saw cuts it into **six sapphire wafers**, more slowly (sapphire is hard). Rad-hard and quantum chips are built on sapphire.
"""),
    craft("tungsten_crucible"),
    machine("pressing", "", item="sapphire_wafer"),
    links(["components/semiconductors", "processing/cryogenic_condenser"]),
])

entry("processing/clean_room", "Clean Room", "clean_room", ["clean_room", "clean_room_panel", "fan_filter_unit", "clean_room_spawner",
                                                              "microchip", "rad_hard_microchip", "quantum_chip"], [
    spot("clean_room", """
A sealed module (3 wide, 2 deep, 2 high) where chips are printed on silicon wafers, under yellow light: photoresist is spoiled by any other. Draws 320 FE a tick.

**Microchips:** a wafer is coated with **photoresist**, the circuit's pattern exposed into it, etched with **hydrofluoric acid** and doped with **red phosphorus**; four chips come off it, moulded in a plastic sheet. Thirty seconds.

**Rad-Hard Microchips:** two **sapphire wafers**, a film of silicon grown on each from **trichlorosilane** (silicon-on-sapphire: the insulating crystal under the circuit is why radiation and an EMP do not upset it), printed the same way and sealed in a **ceramic package**: two chips. A minute.

**Quantum Chips:** a sapphire wafer and a **stabilised exotic matter**: the qubits are held in the exotic matter and wired with a superconducting film sputtered on under **argon**, then sealed in a ceramic package: two chips in 80 seconds. A quantum circuit is built round two.
"""),
    structure("clean_room", "Controller at the front left, a Clean Room Panel beside it and the Energy Port at the front right; behind them the Fluid Port, the Item Port and a panel. On top: three Reactor Glass across the front, two Fan Filter Units and a panel behind them."),
    text("""
Power goes into the **Energy Port**; wafers, phosphorus, packages and exotic matter go in, and chips come out, through the **Item Port**; its fluids go into its three tanks through the **Fluid Port** (or pour canisters into any part): photoresist and hydrofluoric acid for every chip, then trichlorosilane for rad-hard chips or argon for quantum ones. The controller connects to nothing itself.
"""),
    craft("clean_room"),
    craft("clean_room_panel"),
    craft("fan_filter_unit"),
    machine("lithography", "", item="microchip"),
    machine("lithography", "", item="rad_hard_microchip"),
    machine("lithography", "", item="quantum_chip"),
    links(["components/semiconductors", "processing/crystal_puller", "components/circuits"]),
])

entry("processing/gas_centrifuge", "Gas Centrifuge", "gas_centrifuge", ["gas_centrifuge"], [
    spot("gas_centrifuge", """
Pipe uranium hexafluoride gas from the [Chemical Plant](processing/chemical_plant) in to get **enriched uranium nuggets** and depleted uranium dust. Draws 50 FE a tick.
"""),
    text("""
**Cascades:** centrifuges joined flange to flange, the pipe flanges on their sides meeting, run 15% faster for every other centrifuge in the line, counting up to 8 (more than twice as fast). The screen shows the cascade size.
"""),
    craft("gas_centrifuge"),
    machine("centrifuging"),
])

entry("processing/fusion_fuel_synthesizer", "Fusion Fuel Synthesizer", "fusion_fuel_synthesizer", ["fusion_fuel_synthesizer", "lithium_deuteride_pellet"], [
    spot("fusion_fuel_synthesizer", """
Presses lithium-6 dust and deuterium gas into **lithium deuteride pellets**, the fuel of thermonuclear weapons and, later, fusion reactors. Draws 120 FE a tick.
"""),
    craft("fusion_fuel_synthesizer"),
    machine("fusion_synthesis"),
])

entry("processing/exotic_matter_fabricator", "Exotic Matter Fabricator", "exotic_matter_fabricator", ["exotic_matter_fabricator", "stabilised_exotic_matter",
                                                                                                       "containment_cell", "singularity_seed"], [
    spot("exotic_matter_fabricator", """
Holds exotic matter in a spinning magnetic cage to make stabilised exotic matter, containment cells and singularity seeds.

It draws **400 FE a tick**, more than copper cable carries: feed it with insulated or high-voltage [cable](power/cables).
"""),
    craft("exotic_matter_fabricator"),
    machine("exotic_fabricating"),
])

entry("processing/upgrade_chips", "Upgrade chips", "quantum_speed_chip", ['basic_speed_chip', 'advanced_speed_chip', 'military_speed_chip', 'quantum_speed_chip', 'basic_efficiency_chip', 'advanced_efficiency_chip', 'military_efficiency_chip', 'quantum_efficiency_chip', 'basic_balanced_chip', 'advanced_balanced_chip', 'military_balanced_chip', 'quantum_balanced_chip'], [
    text("""
An **upgrade chip** makes a machine work faster, or on less power. Every processing machine takes one (from the [Ore Crusher](processing/ore_crusher) to the [Exotic Matter Fabricator](processing/exotic_matter_fabricator)), and so does every assembler: the [Assembly Bench](ordnance/assembly), the four [missile assemblers](ordnance/missile_assemblers), the [ICBM Assembly Hall](ordnance/strategic_assembly), the [Special Weapons Assembler](ordnance/special_weapons) and the superweapons' ammunition factories, and so do the [Interceptor Battery](ordnance/interceptor_battery) and the [Interceptor Complex](ordnance/interceptor_complex). Generators, reactors and launchers take none.

Open the machine's screen and click the **Upgrades** tab on its left edge (the config tab's twin): it holds **three chips**, and says what they do together. Shift-clicking a chip from your inventory fits it in the first free slot. Breaking the machine drops the chips with everything else.
"""),
    text("""
**Speed chips** make it work faster: a job costs the same power, but it has to be fed faster (a speed chip on a machine at the edge of its cable's capacity will starve). **Efficiency chips** make it use less power, at the same speed. **Balanced chips** do half of each. The chips in the three slots add up.

Each is built on a circuit, and each tier gives more (speed, then efficiency):

- **Basic**: 10%, 5% (a balanced chip 5% and 2.5%)
- **Advanced**: 20%, 10% (10% and 5%)
- **Military**: 30%, 15% (15% and 7.5%)
- **Quantum**: 40%, 20% (20% and 10%)

A quantum speed chip makes a crusher's 10 seconds of work take 7, and three of them under 5; a quantum efficiency chip makes it take 80% of the power, and three 40%. The board's colour and the pips along its top say the tier, as on the circuits; the plate says the kind: amber chevrons for speed, a green bolt for efficiency, half of each for balanced.
"""),
    craft("basic_speed_chip"),
    craft("basic_efficiency_chip"),
    craft("basic_balanced_chip", "Two balanced chips from a speed chip and an efficiency chip of the same tier."),
])

# =========================================================================================================================
chapter("components", "Components", "basic_circuit", """
The parts machines, weapons and structures are built from.
""")

entry("components/circuits", "Circuits", "basic_circuit", ["handmade_circuit_board", "basic_circuit", "advanced_circuit", "military_circuit",
                                                           "quantum_circuit", "sensor_array"], [
    text("""
Four tiers, each built on the one below in the [Circuit Fabricator](processing/circuit_fabricator). Early on a **handmade circuit board**, crafted by hand, counts as a basic circuit.

An **advanced circuit** is built round two **microchips**, a **military circuit** round two **rad-hard microchips** (built on sapphire) with a diamond to spread their heat, and a **quantum circuit** round two **quantum chips** (stabilised exotic matter on sapphire) with a superconducting coil: all three are printed in the [Clean Room](processing/clean_room), on crystals grown and sawn as [Silicon and chips](components/semiconductors) tells. That is the long road every advanced, military and quantum machine and weapon waits on.
"""),
    craft("handmade_circuit_board"),
    machine("fabricating", "", item="basic_circuit"),
    machine("fabricating", "", item="advanced_circuit"),
    machine("fabricating", "", item="military_circuit"),
    machine("fabricating", "", item="quantum_circuit"),
    machine("fabricating", "", item="sensor_array"),
])

entry("components/semiconductors", "Silicon and chips", "silicon_wafer", ["silicon", "polysilicon", "quartz_crucible", "red_phosphorus", "alumina",
                                                                       "ceramic_package", "fluorite_dust", "hydrogen_canister",
                                                                       "hydrogen_chloride_canister", "trichlorosilane_canister",
                                                                       "photoresist_canister", "hydrofluoric_acid_canister"], [
    text("""
Chips are made on silicon purer than anything else you will make, by the steps real fabs use:

1. **Silicon:** quartz reduced with coal in the [Arc Furnace](processing/arc_furnace).
2. **Trichlorosilane:** silicon dissolved in **hydrogen chloride** (hydrogen burnt in chlorine) in the [Chemical Plant](processing/chemical_plant).
3. **Polysilicon:** trichlorosilane broken down under hydrogen, pure silicon laid down.
4. **Wafers:** a crystal grown in the [Crystal Puller](processing/crystal_puller), sawn on the press.
"""),
    machine("arc_smelting", "", item="silicon"),
    machine("chemistry", "", fluid="hydrogen_chloride"),
    machine("chemistry", "", fluid="trichlorosilane"),
    machine("chemistry", "", item="polysilicon"),
    text("""
**Hydrogen** comes from the [Electrolytic Separator](processing/electrolytic_separator), a **Proton Exchange Membrane** in its slot: water split by electricity. A [Cryogenic Condenser](processing/cryogenic_condenser) draws the **argon** the crystals grow under.

The crucible each crystal grows in is fused from quartz and sand.
"""),
    machine("electrolysis", "", fluid="hydrogen"),
    machine("arc_smelting", "", item="quartz_crucible"),
    text("""
The chips are printed in the [Clean Room](processing/clean_room) with **photoresist** (plastic and coal with nitric acid, in synthetic fuel), etched with **hydrofluoric acid** (fluorite dust in sulfuric acid) and doped with **red phosphorus** (bone meal and coal in the Arc Furnace). Rad-hard chips are sealed in a **ceramic package**: clay fired round gold leads.

Hydrogen chloride is a toxic gas; trichlorosilane and hydrofluoric acid burn like acids.
"""),
    machine("chemistry", "", fluid="photoresist"),
    machine("chemistry", "", fluid="hydrofluoric_acid"),
    machine("arc_smelting", "", item="red_phosphorus"),
    machine("arc_smelting", "", item="ceramic_package"),
    text("""
**Military-grade chips** are built on **sapphire**, as real radiation-hardened ones are (silicon-on-sapphire). **Alumina** is leached out of clay with sulfuric acid in the Chemical Plant; the [Crystal Puller](processing/crystal_puller) grows it into sapphire in a **tungsten crucible**, and the saw cuts the crystal into wafers. Quantum chips are built on sapphire too.
"""),
    machine("chemistry", "", item="alumina"),
])

entry("components/parts", "Machine parts", "machine_frame", ["machine_frame", "electric_motor", "servo", "heating_element", "pressure_vessel",
                                                            "capacitor", "crt_display", "superconducting_coil"], [
    text("""
Most machines start from a **Machine Frame**. Motors, servos, heating elements and pressure vessels go into the machines that need them; the CRT display into structure controllers.
"""),
    craft("machine_frame"),
    craft("electric_motor"),
    craft("servo"),
    craft("heating_element"),
    craft("pressure_vessel"),
    craft("capacitor"),
    craft("crt_display"),
    machine("fabricating", "", item="superconducting_coil"),
])

entry("components/metal_parts", "Plates, rods and wire", "steel_plate", ["steel_plate", "dense_steel_plate", "copper_plate", "lead_plate", "tungsten_plate",
                                                                         "steel_rod", "tungsten_rod", "copper_wire", "gold_wire", "tungsten_filament"], [
    text("""
The [Industrial Press](processing/industrial_press) turns ingots into plates, rods and wire with the matching die. Dense steel plate is pressed from four steel plates; tungsten filament from tungsten ingots with the wire die.
"""),
    machine("pressing"),
])

# =========================================================================================================================
chapter("nuclear", "Nuclear", "uranium_fuel_rod", """
Uranium enrichment, the Fission Reactor, breeding and reprocessing, and antimatter from the Particle Accelerator. These are game mechanics, not engineering: one machine, one step.
""")

entry("nuclear/enrichment", "Uranium enrichment", "enriched_uranium_ingot", ["yellowcake", "uf6_canister", "enriched_uranium_nugget", "enriched_uranium_ingot",
                                                                             "depleted_uranium_dust", "depleted_uranium_ingot"], [
    text("""
- Uranium dust and sulfuric acid make **yellowcake** in the [Chemical Plant](processing/chemical_plant).
- Yellowcake becomes **uranium hexafluoride** gas, also in the plant.
- A [Gas Centrifuge](processing/gas_centrifuge) (best in a cascade) turns the gas into **enriched uranium nuggets** and depleted uranium dust.

Enriched uranium goes into fuel rods and RTG slugs; depleted uranium into target rods and armour-piercing ammunition later on.
"""),
    machine("chemistry", "", item="yellowcake"),
    machine("chemistry", "", fluid="uranium_hexafluoride"),
    machine("centrifuging"),
])

entry("nuclear/fuel", "Fuel and target rods", "uranium_fuel_rod", ["uranium_fuel_rod", "spent_fuel_rod", "du_target_rod", "li6_target_rod",
                                                                    "irradiated_du_target_rod", "irradiated_li6_target_rod"], [
    text("""
A **uranium fuel rod** powers the [Fission Reactor](nuclear/fission_reactor), one rod in each Fuel Channel. Once it has burned, a bar under it shows the fuel it has left; a used-up rod comes out as a **spent fuel rod**. Take a half-burned rod out and it keeps its burn.

**Target rods** ride along in the reactor's breeding slots and come out irradiated: depleted uranium rods for plutonium, lithium-6 rods for tritium. See [Breeding and reprocessing](nuclear/reprocessing).
"""),
    craft("uranium_fuel_rod"),
    craft("du_target_rod"),
    craft("li6_target_rod"),
])

entry("nuclear/fission_reactor", "Fission Reactor", "fission_reactor", ["fission_reactor", "fission_reactor_spawner", "lead_lined_casing",
                                                                        "fuel_channel"], [
    plate("fission_reactor", "A formed 4x4x4 Fission Reactor with eight channels."),
    text("""
Build it **any size** from 3x3x3 to 6x6x6: a hollow box of **lead-lined casing** with **Fuel Channels** in its core. Every channel holds **one uranium fuel rod**, wherever it stands, so a bigger core holds more: up to 64 in a 4x4x4 core. Leave the rest of the core empty, for the coolant. Withdraw the control rods and every rod gives **75 FE a tick**: 600 from the example's eight, **4,800 from sixty-four**.

- **Corners and edges:** lead-lined casing.
- **Walls:** casing or **reactor glass**, with **one Item Port, one Fluid Port and one Energy Port** among them, anywhere in the walls, roof or floor.
- **The controller:** in a wall, or in the bottom edge of its wall.

Coolant goes into the **Fluid Port**, rods into the **Item Port** (spent rods and irradiated targets come out of it too) and power out of the **Energy Port**. Once formed, the walls become one vessel: a plinth, windows onto the core, a head with a control-rod drive over every column of channels (their rods rise as you withdraw the control rods), the ports' housings and the console where the controller stands.
"""),
    text("""
**The plan.** Right-click the controller (or hold a Holo-Projector) to see its plan as ghost blocks: one reactor of the size set on its screen, 4x4x4 at first, with casing on the edges, glass in the side walls, the three ports and channels filling the core. Sneak-right-click to open the screen and change the size with the **PLAN** buttons. The ghosts are only a suggestion: casing instead of glass, a port in another wall or water left in the core are all fine. Only a block that cannot stand where it is gets a red outline.
"""),
    structure("fission_reactor", "The example: 4x4x4 with a 2x2x2 core of eight channels. The controller at the front of the bottom layer, the Fluid Port low in a side wall, the Energy Port low in the back wall and the Item Port in the roof."),
    text("""
**The core map.** The controller's screen shows the core a layer at a time from above, the controller's wall at the bottom: pick the layer with the numbered tabs beside it. There is a rod slot on each channel, coolant between them. Shift-click fills every channel at once. Every loaded rod burns at the same time. Each lasts about **2 hours 40 minutes** at full power, a little more or less (no two alike), and the bar under it shows the fuel left; a used-up rod comes out as a spent fuel rod.

**Heat.** The further the rods are out, the more power and the more heat. Heat goes by how full the core is, so a big reactor runs as hot as a small one loaded alike. With coolant flowing the core settles at a temperature that rises with power; without coolant it climbs fast. Past **900 C** the core goes **critical**: press the red **SCRAM** button (every rod drops in) or it melts down. See [Reactor safety](safety/reactor_safety).

- **Water:** safe up to about 70% rods out; further than that and the core climbs past 900 C. About 1.6 mB a tick a rod.
- **Heavy water:** keeps the core well below 900 C even at full power, and lasts five times longer. Make it in the [Chemical Plant](processing/chemical_plant) from deuterium and liquid oxygen.

Water left in the core between the channels makes it heat up and cool down more slowly: as much water as fuel halves the rate, twice as much makes it a third. The energy buffer and the coolant tank grow with the number of channels.
"""),
    table([
        ["A rod", "75 FE/t"],
        ["Sixty-four rods", "4,800 FE/t"],
        ["A rod lasts", "about 192,000 ticks"],
        ["Target rod", "6,000 ticks at 1,200 FE/t"],
        ["Critical at", "900 C"],
        ["Water", "1.5625 mB/t a rod"],
        ["Heavy water", "0.3125 mB/t a rod"],
    ], "At full power"),
    craft("fission_reactor"),
    craft("fuel_channel"),
    craft("lead_lined_casing"),
    links(["nuclear/fuel", "nuclear/reprocessing", "safety/reactor_safety"]),
])

entry("nuclear/reprocessing", "Breeding and reprocessing", "plutonium_ingot", ["plutonium_nugget", "plutonium_ingot", "tritium_canister"], [
    text("""
Irradiated target rods from the [Fission Reactor](nuclear/fission_reactor) go into the [Chemical Plant](processing/chemical_plant):

- An irradiated **depleted uranium** rod gives **plutonium** nuggets and some nuclear waste.
- An irradiated **lithium-6** rod gives **tritium** gas, and the steel rods back.
- A spent fuel rod gives back some uranium dust, and waste.

Plutonium powers the [RTG](power/rtg) for ten days a slug; both plutonium and tritium matter for the weapons to come.
"""),
    machine("chemistry", "", item="plutonium_nugget"),
    machine("chemistry", "", fluid="tritium"),
    craft("plutonium_ingot"),
])

entry("nuclear/particle_accelerator", "Particle Accelerator", "particle_accelerator", ["particle_accelerator", "particle_accelerator_spawner", "magnet_segment",
                                                                                       "accelerator_injector", "accelerator_target_chamber", "steel_plating",
                                                                                       "antimatter_cell"], [
    plate("particle_accelerator", "The formed accelerator ring."),
    text("""
A two-layer octagonal ring nine blocks across. It fills a **containment cell** with antimatter at **2,000 FE a tick**, slowly.

Steel plating underneath, magnet segments on top, with the controller at the front centre, the injector beside it, the target chamber at the back, and the Energy and Item Ports in the middle of the two sides. Feed it power with insulated or high-voltage [cable](power/cables), containment cells into the Item Port; antimatter cells come out of it.
"""),
    structure("particle_accelerator", "When it forms, the corner blocks take the octagon's slope."),
    craft("particle_accelerator"),
    craft("magnet_segment"),
    craft("accelerator_injector"),
    craft("accelerator_target_chamber"),
    craft("steel_plating"),
    machine("accelerating"),
])

entry("nuclear/fusion_reactor", "Fusion Reactor", "fusion_reactor", ["fusion_reactor", "fusion_reactor_spawner", "toroidal_field_coil",
                                                                     "vacuum_vessel_casing", "central_solenoid"], [
    plate("fusion_reactor", "The formed Fusion Reactor."),
    text("""
A three-layer octagonal ring nine blocks across, the accelerator's footprint, round a **central solenoid** column. It makes **6,000 FE a tick** once its plasma is lit.

Machine casing at the bottom, with the controller at the front, a **Fluid Port** in the middle of the right side and the **Energy Port** at the back; a ring of **toroidal field coils** above, with reactor glass viewports in the middle of each side; **vacuum vessel casing** on top; and three central solenoid blocks stacked in the middle.
"""),
    structure("fusion_reactor", "When it forms, the corner blocks take the octagon's slope."),
    text("""
**Fuel.** Pipe **deuterium** and **tritium** into the Fluid Port; it burns 1 mB of each every 4 ticks. Deuterium comes from the [Electrolytic Separator](processing/electrolytic_separator), tritium from lithium-6 target rods ([reprocessing](nuclear/reprocessing)). Lithium deuteride pellets are not a reactor fuel: they are kept for thermonuclear weapons.

**Cold magnets.** Its coils need **liquid nitrogen**, 2 mB a tick, through the same Fluid Port. A [Cryogenic Condenser](processing/cryogenic_condenser) keeps up.

**Ignition.** To light the plasma it needs a charge of **2,000,000 FE** through its Energy Port, more than any one cable carries in a tick: charge it from a big [capacitor bank](power/capacitor_banks). Once lit it pushes its power out of the same port. If the fuel or the nitrogen runs out, or redstone halts it, the plasma goes out and it needs a fresh charge.
"""),
    table([
        ["Output", "6,000 FE/t"],
        ["Ignition", "2,000,000 FE"],
        ["Deuterium", "1 mB / 4 ticks"],
        ["Tritium", "1 mB / 4 ticks"],
        ["Liquid nitrogen", "2 mB/t"],
    ], "Running"),
    craft("fusion_reactor"),
    craft("toroidal_field_coil"),
    craft("vacuum_vessel_casing"),
    craft("central_solenoid"),
    links(["processing/electrolytic_separator", "nuclear/reprocessing", "power/capacitor_banks"]),
])

entry("nuclear/singularity_reactor", "Singularity Reactor", "singularity_reactor", ["singularity_reactor", "singularity_reactor_spawner",
                                                                               "containment_emitter", "core_cradle"], [
    plate("singularity_reactor", "The formed Singularity Reactor, holding a seed."),
    text("""
A 5x5x5 cage that holds a **singularity seed** and makes power by **eating items**: any item at all. Feed it fast enough and it makes up to **10,000 FE a tick**.

A machine casing floor with the controller in the middle of the front, an **Item Port** on the left and the **Energy Port** on the right; steel plating posts at the corners; reactor glass walls with a **containment emitter** in the middle of each wall and of the roof; and the **core cradle** floating in the centre of the empty chamber.
"""),
    structure("singularity_reactor", "The chamber inside must be empty but for the cradle. Any floor block may be swapped for an extra Item Port."),
    text("""
**The output rule.** Every item it eats is worth **10,000 FE**, and it eats at most **20 items a second**. So the power is set by how fast you feed it:

- 5 items a second: 2,500 FE a tick.
- 10 items a second: 5,000 FE a tick.
- 20 items a second: the full 10,000 FE a tick.

Holding the cage costs **1,000 FE a tick**, the worth of 2 items a second, so a trickle of items does not even pay for itself. Run it from automatic farms: cobblestone generators, tree farms, quarries.
"""),
    text("""
**The seed.** Put a singularity seed in the controller, where it stays, like a battery in the charger, and charge the cage with **500,000 FE** through the Energy Port. The cage then engages and holds the seed in the cradle. Take the seed out to shut the reactor down safely.

**Feeding.** Items go into its four feed slots through Item Ports. A hopper moves only 2.5 items a second; an [item pipe](processing/item_pipes) up to 128. **Swap more floor blocks for Item Ports** (do it before engaging) to feed it from several pipes. The reactor keeps a 500,000 FE reserve for the cage and only pushes out power above it.

**Collapse.** If the cage runs out of power, or any part of the structure is broken while it holds the seed, **the singularity collapses and the seed is destroyed**.
"""),
    table([
        ["Per item eaten", "10,000 FE"],
        ["Most it eats", "20 items/s"],
        ["Full output", "10,000 FE/t"],
        ["Cage upkeep", "1,000 FE/t"],
        ["To engage", "500,000 FE"],
        ["Reserve kept", "500,000 FE"],
    ], "The rules"),
    craft("singularity_reactor"),
    machine("exotic_fabricating", "", item="containment_emitter"),
    machine("exotic_fabricating", "", item="core_cradle"),
    links(["processing/exotic_matter_fabricator"]),
])

# =========================================================================================================================
chapter("ordnance", "Ordnance", "frag_grenade", """
Explosives you throw, place and drop. Everything here goes off with the mod's own explosion engine: craters, fire, shock and all.
""")

entry("ordnance/thrown", "Thrown explosives", "frag_grenade", ["dynamite", "frag_grenade", "sticky_grenade",
                                                                 "incendiary_grenade", "smoke_grenade", "molotov_cocktail",
                                                                 "cryo_grenade", "emp_grenade"], [
    text("""
Right-click to throw. Most bounce and roll before they go off, so bank them off walls and down slopes; their fuse starts the moment they leave your hand.
"""),
    table([
        ["Dynamite", "3 s"],
        ["Frag", "4 s"],
        ["Sticky", "4 s"],
        ["Incendiary", "on impact"],
        ["Smoke", "2 s, 30 s"],
        ["Poison gas", "2 s, 25 s"],
        ["Toxin", "on impact"],
        ["Molotov", "on impact"],
        ["Cryo", "3 s"],
        ["EMP", "3 s"],
    ], "Fuses"),
    spot("dynamite", """
The first explosive you can make: a bundle of sticks with a sputtering fuse. A small, sharp blast.
"""),
    spot("frag_grenade", """
A small blast and **24 shrapnel fragments** that fly out in every direction, 6 damage each to whatever they hit. Keep behind cover, yourself included.
"""),
    spot("sticky_grenade", """
Sticks to the first thing it hits, wall or creature, and goes off where it stuck. A frag grenade with a slime ball.
"""),
    spot("incendiary_grenade", """
Bursts on impact into a carpet of fire 4 blocks across; everything caught in it burns for 8 seconds.
"""),
    spot("molotov_cocktail", """
A bottle of fuel with a burning rag. It shatters where it lands into a pool of fire 3 blocks across, with no blast at all, so it will not dig into what it hits.
"""),
    spot("smoke_grenade", """
Pours out a dense cloud for 30 seconds. Mobs inside it, or chasing something inside it, lose their target, and anyone standing in it is hard to spot: mobs notice you there only from a tenth of their usual range.
"""),
    text("""
For poison gas and toxin grenades see [Chemical grenades](ordnance/chemical).
"""),
    craft("dynamite"),
    craft("frag_grenade"),
    craft("sticky_grenade"),
    craft("incendiary_grenade"),
    craft("smoke_grenade"),
    spot("cryo_grenade", """
A canister of liquid nitrogen. It bursts in a blast of cold **5 blocks** round: water freezes to ice, lava to obsidian, fires go out and frost settles on the ground. Everything alive in it is **frozen solid** for 10 seconds: it cannot move or jump. A frozen mob of 40 health or less hit hard (6 damage or more) **shatters**. Bosses are not frozen.
"""),
    spot("emp_grenade", """
An electromagnetic pulse **12 blocks** round that lasts **20 seconds**. Every machine in it loses half its stored power as the pulse hits, then sits dead until the field passes, when it reboots: Launch Silos cannot launch, Launch Computers and Radar Arrays go dark. Armed charges with an electronic fuze (remote, timer, laser) are made safe; landmines and bouncing mines are pressure plates and stay live. A missile flying through the field has an even chance of a dead fuze.
"""),
    craft("molotov_cocktail"),
    craft("cryo_grenade"),
    craft("emp_grenade"),
])

entry("ordnance/charges", "Placed charges", "c4", ["c4", "breaching_charge", "thermite_charge", "timed_bomb",
                                                  "remote_detonator"], [
    text("""
Charges are placed like blocks and are **safe** when placed. Right-click one with an empty hand to open it:

- Set its **frequency** (0-9999), and for those with a timer, whether it goes off **at once** or on its **timer** (1-600 seconds).
- Press **Arm**. An armed charge blinks a red lamp; press **Make safe** to undo it.
- Armed, a **Remote Detonator** on its frequency (or a redstone signal) sets it off, or starts its timer.

A safe charge ignores the detonator. A charge caught in another blast goes off a moment later, armed or not.
"""),
    spot("c4", """
Sticks to any face and goes off at once. Use more C4 on a placed charge to stack up to **four** on one spot; four make a blast 1.6 times as wide.
"""),
    spot("breaching_charge", """
Cuts a clean 3x3 hole 3 blocks deep into whatever it is stuck to, short of obsidian, and nothing else. Anyone in or just past the hole is knocked through it.
"""),
    spot("thermite_charge", """
Burns a 1x1 shaft 8 blocks deep into what it is stuck to, through anything softer than obsidian; water stops it. It leaves molten slag at the bottom. Keep clear while it burns.
"""),
    spot("timed_bomb", """
A big blast on a clock. Armed, the detonator (or redstone) starts its countdown, shown on its face and beeping faster near the end. Once counting it cannot be made safe: right-click it and **cut a wire**. One wire stops it, one sets it off, the other two halve the time left. Breaking it while it counts sets it off.
"""),
    spot("remote_detonator", """
Press to trigger every armed charge on its frequency within 256 blocks. Sneak-use it on a charge to take that charge's frequency, or in the air to type one in.
"""),
    craft("c4"),
    craft("breaching_charge"),
    craft("thermite_charge"),
    craft("timed_bomb"),
    craft("remote_detonator"),
])

entry("ordnance/mines", "Mines", "landmine", ["landmine", "bouncing_mine", "claymore"], [
    text("""
Mines are placed on the ground and are safe until armed: right-click one with an empty hand and press **Arm**. It is live at once, so arm it from a step away. **Make safe** from the same screen to pick one up again. Redstone, or another blast, sets one off too.
"""),
    spot("landmine", """
Sits half buried with only its pressure plate showing. Anything that steps on it once it is live sets it off.
"""),
    spot("bouncing_mine", """
Stepped on, it clicks and throws its canister up to about waist height, where it bursts into **30 fragments** in every direction. Lying flat does not help.
"""),
    spot("claymore", """
Place it facing the way you are looking. Armed, it shows a red **laser tripwire** up to 6 blocks ahead; anything crossing the laser fires **60 fragments** in a 60 degree cone, deadly out to about 12 blocks. It also has a frequency, so a Remote Detonator can fire it.
"""),
    craft("landmine"),
    craft("bouncing_mine"),
    craft("claymore"),
])

entry("ordnance/assembly", "Assembly Bench", "assembly_bench", ["assembly_bench", "he_charge", "heavy_he_charge", "thermobaric_charge", "cluster_dispenser", "penetrator_charge", "incendiary_charge", "massive_ordnance_charge", "gas_dispenser", "smoke_dispenser", "cryo_dispenser", "emp_dispenser", "toxic_waste_tank", "chemical_agent_canister", "tactical_fission_core", "fission_core", "boosted_fission_core", "neutron_core", "dirty_payload", "antimatter_trap", "singularity_core", "light_warhead_casing", "medium_warhead_casing", "heavy_warhead_casing", "strategic_warhead_casing", "impact_fuze", "barometric_fuze"], [
    text("""
The **Ordnance Assembly Bench** puts warheads together; a [missile assembler](ordnance/missile_assemblers) of the warhead's class then builds it into its [missile](ordnance/missiles). The bench is two blocks wide (it takes the space to the right of where you place it) and runs on power, 10 FE a tick. Pipes and hoppers feed its part slots and take from its output.

A warhead is three parts: a **payload core**, which decides what the warhead does; a **casing** of the core's size class (Light, Medium or Heavy; a nuclear core is class XL, Strategic, and takes the lead-lined Strategic Warhead Casing); and a **fuze**, which decides where the missile carrying it sets it off. Put them in the three slots along the top and press **Assemble**: the hoist lowers the casing onto the core, the clamps close, and 5 to 16 seconds later (longer for heavier warheads) the warhead is in the output slot.

**Take Apart** does the reverse: with the three part slots empty, a warhead in the output slot comes apart into its core, a casing and its fuze, so a fuze can be swapped or set again.

**Auto Assemble**, the looping-arrow button left of **Assemble** (its lamp lit green while it is on), starts the next one by itself as soon as its parts are in and the finished one has been taken out of the output: pipe parts in and finished ones out and it keeps building.

The bench is two blocks wide, and its side configuration (the wrench tab) covers both halves: each half's top, front, bottom and back, and its two ends, can be set separately.
"""),
    spot("assembly_bench", """
The readout says what the parts on the bench make, and in red what is missing or wrong (a core and a casing of different classes will not go together).
"""),
    text("""
**Preview and figures.** The column on the right of the screen shows the warhead the parts make (or the one waiting in the output slot) as a model you can drag round, and under it its figures: its blast radii, its yield and its fuze.
"""),
    craft("assembly_bench"),
    text("""
**Payload cores** decide what a warhead does; each makes one warhead (its class in brackets).

**Explosives:** the HE Charge (S), Heavy HE Charge (M), Thermobaric Charge (L), Penetrator Charge (L, a Bunker Buster), Incendiary Charge (S, Napalm) and Massive Ordnance Charge (L).
"""),
    text("""
**Dispensers** open in the air when a missile brings them down: the Cluster Dispenser and the Gas, Smoke and Cryo Canister Dispensers (all M), and the EMP Canister Dispenser (L: its flux generators are heavy).

**Chemical cores** (S): the Toxic Waste Tank and the Chemical Agent Canister.

A core's tooltip names the warhead it makes.
"""),
    text("""
**Fuzes:** a missile sets its warhead off where it comes down (an **Impact** fuze). A **Barometric** fuze bursts it in the air instead, a set height over the target (10 to 200 blocks): type the height on the bench before you press Assemble.

A burst in the air spreads a warhead's effect over the ground below rather than digging a crater. Cluster-type warheads open in the air whichever fuze they have (30 blocks up on an impact fuze).
"""),
    spot("medium_warhead_casing", """
Casings come in three classes, marked by yellow bands: one for **Light**, two for **Medium**, three for **Heavy**. A core only fits a casing of its own class.
"""),
    craft("light_warhead_casing"),
    craft("medium_warhead_casing"),
    craft("heavy_warhead_casing"),
    craft("strategic_warhead_casing"),
    craft("impact_fuze"),
    craft("barometric_fuze"),
    craft("he_charge"),
    craft("heavy_he_charge"),
    craft("thermobaric_charge"),
    craft("cluster_dispenser"),
    craft("penetrator_charge"),
    craft("incendiary_charge"),
    craft("massive_ordnance_charge"),
    craft("gas_dispenser"),
    craft("smoke_dispenser"),
    craft("cryo_dispenser"),
    craft("emp_dispenser"),
    craft("toxic_waste_tank"),
    craft("chemical_agent_canister"),
])

entry("ordnance/missiles", "Missiles", "missile", ["missile", "tactical_missile_body", "medium_missile_body", "heavy_missile_body", "strategic_missile_body", "solid_rocket_motor_i", "solid_rocket_motor_ii", "liquid_fuel_stage", "cruise_engine", "inertial_guidance_unit", "gps_guidance_unit", "laser_guidance_unit", "hardened_electronics"], [
    text("""
Every [warhead](ordnance/warheads) has a **missile of its own**, built for it: the warhead is its nose, and the body under it is cut to the warhead's own girth and painted to match all the way down, with its own fins, bands and markings. A missile is put together at the [missile assembler](ordnance/missile_assemblers) of its warhead's class: the warhead, the class's **body**, a **rocket motor**, and if you like a **guidance unit** and **Hardened Electronics**. **Take Apart** gives every part back.
"""),
    text("""
**Bodies** come in the warheads' classes: a **Tactical** body for class S warheads (Light missiles, about 4 blocks long), a **Medium** body for class M (5 to 5.5 blocks), a **Heavy** body for class L (about 7 blocks) and a **Strategic** body for the class XL [nuclear warheads](ordnance/nuclear): a long two-stage rocket, about 9 blocks, that only a [Launch Complex](ordnance/launch_complex) or a [Missile Silo](ordnance/missile_silo) fires. Bigger still are the three-stage intercontinental missiles the [ICBM Assembly Hall](ordnance/strategic_assembly) builds whole, and the exotic ones of the [Special Weapons Assembler](ordnance/special_weapons).

**Motors** decide how far it flies: **Solid Rocket Motor I**, 400 blocks (Tactical and Medium bodies); **Solid Rocket Motor II**, 1024 blocks (Tactical, Medium and Heavy bodies); **Liquid-fuel Stage**, 2500 blocks (Heavy and Strategic bodies).
"""),
    text("""
**Cruise missiles.** A **Cruise Engine** (Tactical and Medium bodies, 1500 blocks) is a small jet with its own launch booster. A missile built with one is a **cruise missile**: the same warhead on its own winged body, painted as that warhead's missile (an HE Cruise Missile, a Cryo Cruise Missile...). It does not arc through the sky: its booster lights it out of its box and falls away, its wings unfold, and its jet carries it **40 blocks over the ground** all the way, climbing early for a hill and easing down after it, at 32 blocks a second. 50 blocks short of the target it pops up and dives on it. Anything standing taller than its cruise height in its way stops it there. Only the [Cruise Missile Launcher](ordnance/cruise_launcher) fires it, and no other launcher will take one.
"""),
    text("""
**Guidance** decides how close to the target it comes down. With none it lands anywhere within 25% of the distance flown: up to 250 blocks long or short on a 1000-block shot, half that to either side, and seldom close. An **Inertial Guidance Unit** brings that to 4%, a **GPS Guidance Unit** to 2 blocks. A **Laser Guidance Unit** goes where a [Laser Designator](ordnance/laser_designator) points: launched from a designator, it follows that player's laser all the way in; otherwise it flies as inertial guidance does and, coming down, steers onto a designator's spot near where it was aimed. Cruise missiles keep to their path and do not home.

**Hardened Electronics** keep a missile live through an EMP: without them, a missile that flies through an EMP field has an even chance of coming down a dud.
"""),
    spot("missile", """
Its tooltip lists its warhead and fuze, its range and its guidance; its name is its own (an HE Missile, a Thermobaric Missile, a Fission Missile...).
"""),
    craft("tactical_missile_body"),
    craft("medium_missile_body"),
    craft("heavy_missile_body"),
    craft("strategic_missile_body"),
    craft("solid_rocket_motor_i"),
    craft("solid_rocket_motor_ii"),
    craft("liquid_fuel_stage"),
    craft("cruise_engine"),
    craft("inertial_guidance_unit"),
    craft("gps_guidance_unit"),
    craft("laser_guidance_unit"),
    craft("hardened_electronics"),
])

entry("ordnance/missile_assemblers", "Missile Assemblers", "light_missile_assembler", ["light_missile_assembler", "medium_missile_assembler",
                                                                                   "heavy_missile_assembler", "strategic_missile_assembler",
                                                                                   "light_missile_assembler_spawner", "medium_missile_assembler_spawner",
                                                                                   "heavy_missile_assembler_spawner", "strategic_missile_assembler_spawner"], [
    text("""
A **missile assembler** builds [missiles](ordnance/missiles): one for each class of warhead, each a bigger structure than the last and a harder one to craft. Each takes only its own class: a warhead of that class, the class's missile body, a motor that fits it, and optionally a guidance unit and Hardened Electronics. Its screen is the [bench's](ordnance/assembly), with the missile the parts make turning in the window on the right. Press **Assemble**; while it works the crane lays the missile out section by section (motor and fins, then the body, then the warhead) and the finished missile waits on the cradles until you take it out. **Take Apart** gives every part back. Each draws power through an Energy Port, and pipes feed and empty it through an Item Port: its hatch in the deck, or its mouth in the deck's edge under it (parts go in and finished missiles come out through either).

**Auto Assemble**, the looping-arrow button left of **Assemble** (its lamp lit green while it is on), starts the next one by itself as soon as its parts are in and the finished one has been taken out of the output: pipe parts in and finished ones out and it keeps building.

**Light** (class S: HE, Napalm, Chemical, Toxic Waste, Dirty): 10 seconds at 20 FE a tick. **Medium** (class M: Heavy HE, Cluster, Poison Gas, Smoke, Cryo): 15 seconds at 40 FE a tick. These two also build [cruise missiles](ordnance/missiles): give one a Cruise Engine for its motor. **Heavy** (class L: Thermobaric, Bunker Buster, EMP, Massive Ordnance): 20 seconds at 80 FE a tick. **Strategic** (class XL, the nuclear warheads): 30 seconds at 160 FE a tick.
"""),
    structure("light_missile_assembler", "The Light Missile Assembler: a 3 by 6 deck of Launch Pads, the controller in the middle of the front edge, the Item Port and the Energy Port on either side of the fifth row. An A-frame stands over each end: a Gantry Frame block on each side, two high. A Crane Rail runs the deck's length three blocks up, over the middle: the hoist beam. Keep the middle clear under it, the missile lies there."),
    structure("medium_missile_assembler", "The Medium Missile Assembler: a 5 by 7 deck of Launch Pads, the controller in the middle of the front edge, the Item Port and the Energy Port in the back row. The slewing jib crane's post, Gantry Frame four high, stands in the middle of the side on your left as you face the controller. Keep the middle three columns clear two blocks up: the missile's bay. Pipes and cables may stand on the two ports."),
    structure("heavy_missile_assembler", "The Heavy Missile Assembler: a 5 by 9 deck, its two outer columns Crane Rail (the gantry crane's track) and the rest Launch Pads, the controller in the middle of the front edge, the Item Port and the Energy Port in the back row. The crane stands parked over the back row: a Gantry Frame leg four high on each rail and a row of Crane Rail across the top. Everything over the deck in front of it must be clear, for the crane to run and the missile to lie; pipes and cables may stand on the two ports under it."),
    structure("strategic_missile_assembler", "The Strategic Missile Assembler, an integration tower: a 5 by 5 deck of Launch Pads, the controller in the middle of the front edge, the Item Port and the Energy Port in the back row. A Gantry Frame column eleven high stands on each corner; on top, a Crane Rail girder runs down each side and another across the middle, the hoist's beam. The middle three by three must be clear: the missile stands there on its stand, stacked stage on stage, and work platforms fold down round it."),
    text("""
**Crafting, a ladder.** Each assembler's controller takes better parts than the last: the Light's servos and basic circuits, the Medium's electric motors and an advanced circuit, the Heavy's pressure vessels (its hydraulics), tungsten and a military circuit, the Strategic's lead-lined casing, two military circuits and a sensor array. At the top, the [ICBM Assembly Hall](ordnance/strategic_assembly) takes superconducting coils and three military circuits, and the [Special Weapons Assembler](ordnance/special_weapons) two quantum circuits and a containment emitter.
"""),
    craft("light_missile_assembler"),
    craft("medium_missile_assembler"),
    craft("heavy_missile_assembler"),
    craft("strategic_missile_assembler"),
])

entry("ordnance/warheads", "Warheads", "he_warhead", ["he_warhead", "heavy_warhead", "thermobaric_warhead", "cluster_warhead",
                                                      "bunker_buster_warhead", "napalm_warhead", "massive_warhead", "gas_warhead",
                                                      "smoke_warhead", "cryo_warhead", "emp_warhead", "toxic_waste_warhead",
                                                      "chemical_warhead"], [
    text("""
Warheads are what a [missile](ordnance/missiles) carries: each has a missile of its own, built at the [missile assembler](ordnance/missile_assemblers) of its class, its nose the warhead itself. Each goes off as described below where the missile comes down.

Warheads are not crafted: each is put together at an [Ordnance Assembly Bench](ordnance/assembly) from a payload core, a casing and a **fuze**. The fuze decides where the missile's warhead goes off: where it hits, or in the air over its target.

Placed on their own, warheads stand nose up on their mounting rings, waiting to be fitted to a missile; with nothing under them they fall. A placed warhead cannot be armed and nothing sets it off, except another explosion.
"""),
    spot("he_warhead", """
The general-purpose warhead, olive with a yellow band: a crater about 6 blocks across.
"""),
    spot("heavy_warhead", """
The same, a size up: a crater about 12 blocks across.
"""),
    spot("thermobaric_warhead", """
Throws out a cloud of fuel, then lights it a moment later. A shallow crater, but the burning overpressure reaches 24 blocks, and anything with no open sky above it (indoors, in a cave) is hurt far worse.
"""),
    spot("cluster_warhead", """
Delivered by a missile, it bursts **30 blocks over its target** and rains **32 bomblets** that land within about 20 blocks of that point; set off on the ground, it throws 24 of them up and out to about 18 blocks. Each bomblet goes off where it lands with a blast of its own. One in twenty is a dud that lies where it fell as a live landmine.
"""),
    spot("bunker_buster_warhead", """
A slim penetrator. Placed and set off, a 9-block crater where it stands; delivered by a missile, it bores a **3 by 3 shaft** up to 40 blocks down first (obsidian-hard blocks under its middle cost more; unbreakable ones stop it) and goes off at the bottom.
"""),
    spot("napalm_warhead", """
A small burst and a pool of **napalm** about 10 blocks round. Napalm burns for about 30 seconds whatever the weather, sets anything in it alight, and lights what burns beside it.
"""),
    spot("massive_warhead", """
The biggest conventional warhead: a 28-block crater and deadly out to 40. Stand well clear.
"""),
    spot("gas_warhead", """
A cluster of **poison gas** canisters. Delivered by a missile, it bursts high over its target and drops 14 canisters across a wide area; each lands and pours out a cloud of chlorine like a [gas grenade](ordnance/chemical). Set off on the ground, it throws them out round itself.
"""),
    spot("smoke_warhead", """
The same with **smoke**: 14 smoke canisters over a wide area, a screen that hides whoever is in it.
"""),
    spot("toxic_waste_warhead", """
A small burst that splashes **toxic sludge** 10 blocks round: whatever is caught in it gets a heavy dose of toxin, and the sludge poisons anything that walks through it until it dries out.
"""),
    spot("chemical_warhead", """
A **chemical agent** (anthrax): it bursts into a thick pale green cloud 9 blocks round that behaves like [chlorine](ordnance/chemical): it spreads, sinks into low ground and drifts with the wind, but it is far harsher (**Poison IV**, a heart every half second where it is thick) and it hangs about for a minute and a half. A [hazmat suit](safety/hazmat_suits) keeps it out.
"""),
    spot("cryo_warhead", """
The same with **cryo** canisters: 14 of them over a wide area, each a burst of cold like a [cryo grenade](ordnance/thrown): water freezes, fires go out, and everything caught in one is frozen solid for 10 seconds (hit hard, a frozen mob shatters).
"""),
    spot("emp_warhead", """
The same with **EMP** canisters: 14 EMP fields over a wide area, each like an [EMP grenade](ordnance/thrown)'s. Machines in them lose half their power and shut down for 20 seconds, armed charges with electronic fuzes go safe, and missiles flying through may be left duds.
"""),
])

entry("ordnance/launch_site", "Launch site", "launch_silo", ["launch_silo", "launch_computer", "launch_pad",
                                                              "gantry_frame", "gantry_arm", "radar_mast", "radar_dish", "antenna"], [
    text("""
A **Launch Silo** sends an assembled [missile](ordnance/missiles) to its target, as far as the missile's motor reaches (400 to 2500 blocks). A **Launch Computer** aims it: its screen is a map of the ground round it, with the silos it controls and every missile in flight.

The silo takes one Light, Medium or Heavy missile, built at its [missile assembler](ordnance/missile_assemblers) (not a cruise missile: those go from a [Cruise Missile Launcher](ordnance/cruise_launcher)). Each launch takes the missile and 100,000 FE.

**Reloading.** Every launcher has an **Item Port**: pipes and hoppers feed missiles through it straight into its launch slot, so one fired is replaced at once. It takes only missiles that launcher can fire, and nothing comes out of it. (A launcher built before it had one still works with a Launch Pad, or Hazard Concrete, where the port goes.) The gantry's arm slides up or down its tower and reaches in to hold whichever missile stands on the pad, just under its warhead.
"""),
    structure("launch_silo", "A 3 by 3 launch pad, the controller in the middle of its front edge, the Energy Port in the middle of its back edge and the Item Port beside it, on your right as you face the controller. The gantry tower stands beside the pad's middle row, on your left as you face the controller: 9 high, with the Gantry Arm reaching in at the seventh block up. The column above the middle of the pad must be clear: that is where the missile stands."),
    structure("launch_computer", "The controller and its Energy Port side by side, with the radar mast behind the controller: three Radar Masts with the Radar Dish on top, and the Antenna on the controller."),
    text("""
**The map.** The computer maps the ground within 3000 blocks of it as it gets loaded (by you, by missiles flying over) and keeps what it has seen; ground no one has been near is blank. Hold the right mouse button and drag to look round, scroll to zoom in and out, and press **Centre** to come back. Under the map are the coordinates of the point under the cursor.

**Launching.** Load the silo with a missile (right-click its controller, or pipe it into its Item Port) and power it. Then open the Launch Computer: it needs a little power of its own, and finds every silo within 256 blocks (for launchers further off, anywhere in the dimension, build a [Launch Control Station](ordnance/launch_control_station)). Each silo has its own target: pick one in the list, then left-click on the map to aim it there, or type the X and Z in and press **Set**. **ARM** arms the picked silo (it must be ready, with a target within its missile's range); **DISARM** makes it safe. **LAUNCH** fires every armed silo at once, each at its own target. A silo that stops being ready makes itself safe.

**Strike circles.** Each armed silo's strike shows round its target, the picked silo's bright and the rest dark: shaded where its warhead kills, a ring at the farthest it harms anything, and a dashed ring further out for how far off it may come down (long or short along its flight more than to either side; a superweapon's missiles anywhere round the target). From the countdown on, the missile is locked in its launcher.

The silo sounds its siren and counts down for 10 seconds while the gantry arm swings clear. The missile burns on the pad for 2 seconds, then climbs, arcs over and dives on the target from high above, screaming down for its last few seconds. The further the target, the higher the arc: a missile sent 1000 blocks arrives about 20 seconds after lift-off. It flies on whether or not anyone is near the ground below, loading the chunks ahead of it, so the target need not be loaded.

Whatever it hits on the way sets off the warhead there, so keep the sky above the silo clear.
"""),
    craft("launch_silo"),
    craft("launch_computer"),
    craft("launch_pad"),
    craft("gantry_frame"),
    craft("gantry_arm"),
    craft("radar_mast"),
    craft("radar_dish"),
    craft("antenna"),
])

entry("ordnance/launch_control_station", "Launch Control Station", "launch_control_station", ["launch_control_station", "launch_codes",
                                                                                               "launch_control_station_spawner"], [
    text("""
A **Launch Control Station** runs your launchers anywhere in its dimension, near or far, loaded or not: Launch Silos, [Launch Complexes](ordnance/launch_complex), [Missile Silos](ordnance/missile_silo), [Cruise Missile Launchers](ordnance/cruise_launcher) and the superweapons (the [SCUD Storm](superweapons/scud_storm) and the [Nuclear Missile](superweapons/nuclear_missile_silo)). It shows what your linked [Radar Arrays](ordnance/radar_array) track too.
"""),
    structure("launch_control_station", "Three wide, two deep and three high. The desk across the front: Machine Casing, the controller in the middle, Machine Casing. Behind it the instrument bank: Machine Casing two high, the Energy Port in the middle of its bottom row. An Antenna on top of the bank's middle."),
    text("""
**Launch Codes.** Use a card of Launch Codes on the station: it takes the station's network code. Then use it on a launcher's controller, or on any part of it, to link that launcher, and on a Radar Array to link the radar; sneak while you use it to unlink. Any number of cards can carry one code: give one to a friend and they can link their launchers to your station. Used while sneaking on another station, a card makes that station join its network (two control rooms, one arsenal). **New codes** on the station's screen (press it twice) voids every card made before; the links stay.

**The map** shows the whole dimension: the ground round every linked launcher and radar is mapped as it loads. Each launcher is a numbered square and its target a cross; the network's missiles in the air are amber, and what its radars track is red. Drag with the right mouse button, scroll to zoom out as far as 1:128, and press **Station** or **Picked** to bring the view back.

**Orders.** Pick a launcher in the list (shift-click to pick several), then left-click on the map to aim, or type X and Z and press **Set**. Several picked take a **pattern** round the point: a line across the line of attack, a ring, or a grid, the **spacing** apart. **ARM** arms the picks (each must be ready, with its aim in range). **LAUNCH** fires every armed launcher: **at once**, or **on target together**, each launch held back so that all the first warheads land in the same moment.

**Far launchers.** A launcher far away keeps showing what it last reported: nothing changes there while no one is near. Aiming and arming it waits until it is next loaded, and **LAUNCH wakes it**: its chunks are loaded, it takes its orders, counts down and fires, and it stays loaded until it has settled. It needs its energy already stored (its generators may not be running), and a superweapon only charges while it is loaded. A launcher that has been broken shows **No contact**.

**The log** lists the network's news: launches, impacts, missiles lost (shot down, or stopped by something on the way), orders that failed and fire called from the field. A teletype at the station prints each report as it comes in.

**Laser Designators** on the network show on the map as red diamonds while they lase, with their holders' names; **Laser** aims the picked launchers at the spot (in their pattern). See [Laser Designator](ordnance/laser_designator).

The station needs 40 FE a tick. In an EMP it goes dark and no orders go out.
"""),
    craft("launch_control_station"),
    craft("launch_codes"),
])

entry("ordnance/laser_designator", "Laser Designator", "laser_designator", ["laser_designator", "laser_guidance_unit"], [
    text("""
A **Laser Designator** marks a target for your missiles. Hold use and it goes to your eye: the view zooms in, the reticle shows where the beam lands, and the rangefinder gives the **range** and the **spot's coordinates** (and the creature, if it is on one). A thin red beam runs from it to a pulsing red spot, and everyone sees both: by day they are faint, at night they shine. It reaches **as far as you can see** (your view distance, up to 1,024 blocks), creatures and blocks alike; on a creature the spot follows it. Other players see the whole beam, out to the spot the missiles use.
"""),
    text("""
**On a network.** Use the designator on a [Launch Control Station](ordnance/launch_control_station): it takes the station's network. Its launchers loaded with missiles that have a **Laser Guidance Unit** are the ones it commands, one at a time: the sight shows the one it works with (its number on the network, what it is, and which of them it is, say *2/3*). It starts with the first; **scroll** while zoomed to pick the next (the hotbar stays put), and the designator keeps the pick. The station's map shows your spot while you lase, and its **Laser** button aims the picked launchers at it.

**Arm, then launch.** Lase the target and **left-click**: if the picked launcher is not armed, it is aimed at the spot and **armed**; if it is armed, it **launches**. By the reticle the sight says which: **READY** (a click arms it), **ARMED** (a click launches it), **OUT OF REACH** or **NOT READY**. Once one has launched, the designator moves on to the next. **Sneak and left-click** to **disarm** the picked launcher instead: it stays aimed where it was, safe.

**Armed targets.** While you are zoomed in, every armed launcher of the network shows where it is aimed, whoever armed it: a glowing sphere centred on the point you clicked (one aimed from the station on the top of its target), red for the one you have picked.
"""),
    text("""
**Leading it in.** Keep lasing after a launch: the missile follows **your laser** all the way in, wherever you point it, so you can walk it onto a moving target or onto another one entirely. If you let go, it keeps going where it was headed. The sight counts the missiles **homing** on your spot, and the station's log notes each launcher armed and launched from a designator.

It holds **40,000 FE**: a hundred seconds of lasing; charge it in a [Battery Charger](power/batteries). Smoke or gas between it and the target breaks the beam, and an EMP knocks it out for a while.
"""),
    craft("laser_designator"),
    craft("laser_guidance_unit"),
])

entry("ordnance/launch_complex", "Launch Complex", "launch_complex", ["launch_complex", "flame_deflector"], [
    text("""
The **Launch Complex** is the heavy launcher on the surface: a deck with a flame trench under the missile and a service tower beside it. It fires [nuclear missiles](ordnance/nuclear), as the [Missile Silo](ordnance/missile_silo) does. A [Launch Computer](ordnance/launch_site) aims and arms it like any Launch Silo, and its **LAUNCH** fires it with the rest.

It takes any [missile](ordnance/missiles), an [intercontinental](ordnance/strategic_assembly) one too, as the Missile Silo does. Each launch takes the missile and 250,000 FE (it holds 500,000), and the countdown runs 15 seconds: the siren sounds, the two umbilical arms swing away from the missile one after the other, and for the last five seconds the deluge soaks the deck and the trench. At ignition steam and smoke pour out of the trench's open end.
"""),
    structure("launch_complex", "A 5 by 5 deck of Launch Pads, the controller in the middle of its front edge, the Energy Port in the middle of its back edge with the Item Port beside it (on your right as you face the controller), and the Flame Deflector in its middle, under the missile. The service tower, Gantry Frame, stands two blocks deep beside the deck's middle and back rows, on your left as you face the controller: 13 high, with Gantry Arms (the umbilicals) reaching in at the fifth and ninth blocks up. The 11 blocks above the deflector must be clear: that is where the missile stands. The trench opens on the side away from the tower."),
    craft("launch_complex"),
    craft("flame_deflector"),
])

entry("ordnance/missile_silo", "Missile Silo", "missile_silo", ["missile_silo", "silo_hatch", "reinforced_concrete", "hazard_concrete"], [
    text("""
The **Missile Silo** is the hardened launcher: a shaft dug into the ground and lined with concrete, the missile standing at the bottom under a pair of heavy doors. It fires any [missile](ordnance/missiles) at all: Strategic and [intercontinental](ordnance/strategic_assembly) ones and their [nuclear warheads](ordnance/nuclear) too. A [Launch Computer](ordnance/launch_site) aims and arms it like any Launch Silo, and its **LAUNCH** fires it with the rest.

Each launch takes the missile and 250,000 FE (it holds 500,000). The countdown runs 20 seconds: the siren sounds, the beacons on its console flash, and with 15 seconds to go the doors unlatch and slide apart on their rails. If they are not open by the end of the count, it waits for them. At ignition the exhaust blasts up out of the four vents at the silo's corners and smoke pours out of the shaft; the missile climbs straight up and out, and turns onto its course once it is clear. The doors shut again ten seconds later.

Shut, the doors keep everything out of the shaft, and you can walk on them. The **DOORS** button on the controller's screen opens or shuts them when it is not launching, to look at the missile. Anything solid over the hatch, in the missile's way out, stops it launching (**Hatch blocked**): the missile would hit it.
"""),
    structure("missile_silo", "Dug into the ground: 5 by 5 and 14 deep. The bottom layer is Reinforced Concrete with the Flame Deflector in its middle. Above it are twelve rings of Reinforced Concrete round a 3 by 3 shaft, which must be clear: the missile stands in it. The top layer, level with the ground, is a ring of Hazard Concrete round a Silo Hatch over the shaft (one Silo Hatch covers all nine blocks), with the controller in the middle of the ring's front, the Energy Port in the middle of its back and the Item Port beside it, on your right as you face the controller."),
    text("""
**Reinforced Concrete** is concrete cast round a steel rod. It stands up to blasts as well as obsidian does, so it makes bunkers as well as silos, and it takes an iron pickaxe and a while to break. **Hazard Concrete** is the same painted in yellow and black stripes.
"""),
    craft("missile_silo"),
    craft("silo_hatch"),
    craft("reinforced_concrete"),
    craft("hazard_concrete"),
])

entry("ordnance/cruise_launcher", "Cruise Missile Launcher", "cruise_launcher", ["cruise_launcher", "launch_box", "cruise_launcher_spawner"], [
    text("""
The **Cruise Missile Launcher** fires [cruise missiles](ordnance/missiles), and only those: two armored boxes side by side on a concrete deck, hinged at the back, a missile in each. A [Launch Computer](ordnance/launch_site) aims, arms and fires it like any launcher, and its **LAUNCH** fires it with the rest.

Load a cruise missile into each box (right-click the controller, or pipe them into its Item Port) and power it: each missile takes 60,000 FE (it holds 150,000). Its range is the shorter of its two missiles'.
"""),
    structure("cruise_launcher", "A 2 by 6 deck of Launch Pads, the controller and an Energy Port side by side in its front row (the port on your left as you face the controller), the Item Port right behind the Energy Port, and two Launch Boxes side by side on top of its back row: the boxes' hinged ends. Keep the space over the deck clear where the boxes lie and swing up: two blocks over all of it, and higher toward its front, up to five blocks over its second and third rows."),
    text("""
**Firing.** The siren sounds and the count runs 10 seconds while the beacon on the boxes turns and the rams raise them to 45 degrees. Then the first missile goes: its booster lights in its box, bursting the box's front cover, blasts smoke out of the box's back over the deck, and drives it out along the launcher's front. The second follows 3 seconds later, at the same target (each with its own guidance's scatter). A few seconds after the last, the boxes come down again, fresh covers in their mouths.

Out of its box, each climbs on its booster, drops it, unfolds its wings and turns toward the target low over the ground. An EMP, a redstone signal or a broken structure stops a launch; whatever has not gone stays loaded.
"""),
    craft("cruise_launcher"),
    craft("launch_box"),
])

entry("ordnance/vertical_launch_battery", "Vertical Launch Battery", "vertical_launch_battery", ["vertical_launch_battery", "strike_cell",
                                                                                         "missile_canister", "vertical_launch_battery_spawner"], [
    text("""
The **Vertical Launch Battery** saturates an area: sixteen cells in two eight-cell modules sunk into the ground, each holding one Light or Medium [missile](ordnance/missiles) upright under an armoured hatch, all fired in one volley. Each cell is six blocks deep, as long as a Medium missile: a **Strike Cell** at the top (its hatch and launch electronics) over five **Missile Canisters**. It takes conventional warheads only (nothing nuclear or exotic) and no cruise missiles. A [Launch Computer](ordnance/launch_site), the [Launch Control Station](ordnance/launch_control_station) or a [Laser Designator](ordnance/laser_designator) aims, arms and fires it like any launcher.

Load a missile into each cell (right-click the controller, or pipe them into its Item Port) and power it: 20,000 FE a missile (it holds 640,000, enough for two full volleys). Its range is its shortest missile's.
"""),
    structure("vertical_launch_battery", "Dig a pit four by four and five deep and fill it with Missile Canisters. On top of them, level with the ground, sixteen Strike Cells four by four, the air over them clear for their hatches. In front of the cells a walkway of four Launch Pads with the controller second from the left; behind them Machine Casing, the Energy Port, the Item Port and Machine Casing."),
    text("""
**The spread.** On its screen, the dotted button picks the pattern its missiles land in round the target: all on the **point**, a **line** across the line of attack, a **ring**, or a **grid**; the arrows set how far apart, up to 48 blocks. Each missile goes to its own point (and its own guidance's scatter on top).

**Firing.** The siren sounds and the count runs 10 seconds while the beacon at its back turns. Then it **ripples**: one cell every quarter of a second, back and forth across the two modules. Each hatch swings open, the missile's motor lights at the foot of its cell, its blast roaring up the module's uptake, and half a second later the missile climbs straight up out of the cell and away. A full load is in the air in four seconds. An EMP, a redstone signal or a broken structure stops the ripple; whatever has not gone stays loaded.
"""),
    craft("vertical_launch_battery"),
    craft("strike_cell"),
    craft("missile_canister"),
])

entry("ordnance/nuclear", "Nuclear warheads", "tactical_nuclear_warhead", ["tactical_nuclear_warhead", "fission_warhead", "boosted_fission_warhead", "neutron_warhead", "dirty_warhead", "thermonuclear_warhead", "cobalt_warhead", "tsar_warhead", "tactical_fission_core", "fission_core", "boosted_fission_core", "neutron_core", "dirty_payload"], [
    text("""
A nuclear warhead is put together at the [Assembly Bench](ordnance/assembly) like any other, from a nuclear payload core, a **Strategic Warhead Casing** (nuclear warheads are class XL, Strategic) and a fuze, and is built into its two-stage **Strategic** [missile](ordnance/missiles) at the [Strategic Missile Assembler](ordnance/missile_assemblers). Only a [Launch Complex](ordnance/launch_complex) or a [Missile Silo](ordnance/missile_silo) fires it. Weapons too big for any missile are built whole, missile and all, in the [ICBM Assembly Hall](ordnance/strategic_assembly).

**Tactical Nuclear Warhead** (about 300 tonnes of TNT), the smallest nuclear weapon and still half as big again as the Massive Ordnance Warhead: a crater 42 blocks round, wrecking everything to 60 blocks and deadly to 60. Its core is a **Tactical Fission Core**: three plutonium nuggets in a high-explosive shell.

**Fission Warhead** (about 15 kilotons): a crater 75 blocks round, wrecking everything to 110 blocks and deadly to 120. Its core is a **Fission Core**: a plutonium ingot in a sphere of explosive lenses, with a depleted uranium tamper and a lead case.

**Boosted Fission Warhead** (about 45 kilotons): a crater 95 blocks round, wrecking everything to 140 blocks and deadly to 150. Its core is a **Boosted Fission Core**: a Fission Core with a tritium canister and a deuterium canister piped into its pit. The gas burns as the pit goes off and splits far more of the plutonium.

All three go off with a blinding flash, a fireball and a mushroom cloud, and burn the ground round the crater to scorched earth.
"""),
    text("""
**Neutron Warhead** (about 1 kiloton): slim and silver, built to kill rather than to wreck. Its blast digs a crater only 10 blocks round, but it lets out a flood of **neutrons** that kills every living thing out to **180 blocks**, unless enough lies in between: every block between the burst and a body takes its share. Two blocks of concrete, lead or obsidian (or five of stone or earth, or four of water) keep the neutrons from killing even beside the burst, and four of concrete or lead keep out the sickness too; farther out, less does. A [hazmat suit](safety/hazmat_suits) helps only a little, as neutrons go through cloth. Whoever they do not kill takes a heavy dose of [radiation](safety/radiation) and falls ill with **Radiation Sickness**. Undead are not harmed.

Plants in the open die out to **120 blocks** (under a roof they are spared): leaves fall; crops, grass and flowers wither; saplings die; grass turns to bare earth. Buildings are hardly touched. Its core is a **Neutron Core**: a Tactical Fission Core with two tritium canisters, in a tungsten carbide cage that lets the neutrons out instead of holding them in.

**Dirty Warhead**: not a nuclear weapon at all, but high explosive wrapped round a drum of **nuclear waste**. Its blast is small (a crater 6 blocks round), but the waste goes up as dust and settles **90 blocks round**: weaker than a nuclear blast's fallout, and it lingers for **five days**. It is class S: it takes a **Light Warhead Casing**, its patched-up missile is built at the Light Missile Assembler, and it flies from any [Launch Silo](ordnance/launch_site). Being only explosive, it goes off however it is set off. Its core is a **Dirty Payload**: four Nuclear Waste, an HE Charge and a Lead Plate.
"""),
    text("""
**Thermonuclear Warhead** (about 1.2 megatons): two stages, a boosted fission primary setting off a fusion secondary. A crater 150 blocks round, wrecking everything to 220 blocks and deadly to 260; fallout 360 blocks round for three days. It is only ever built whole, into an intercontinental missile, at the [ICBM Assembly Hall](ordnance/strategic_assembly).

**Cobalt Warhead** (about 400 kilotons): the same weapon with a Cobalt-59 Jacket round its secondary. The blast is smaller (a crater 70 blocks round, deadly to 110), but the jacket turns to cobalt-60: fallout 300 blocks round the burst and strung out about **1,000 blocks downwind**, lasting **a month**. Built whole like the thermonuclear warhead.
"""),
    text("""
**Tsar Mega-bomb** (about 50 megatons), the biggest weapon there is: a fat dark-green re-entry body with cooling vents that glow, its skirt flared over the top of its huge intercontinental missile (a core stage ringed by six strap-on tanks, each with its own engine). Three stages: a **Twin Boosted Core** for its primary (two boosted fission cores fired together: one is too small a spark for the rest), a fusion secondary, and a **Tertiary Stage** set off by the secondary. A crater **250 blocks** round, wrecking everything to 380 blocks, burning to 450, deadly to 450 and hurting to 700; fallout 600 blocks round for three days. Its cloud climbs 500 blocks.

So much ground goes that the crater keeps forming for a minute or two after the shock wave has passed, out of sight under the dust. Built whole at the [ICBM Assembly Hall](ordnance/strategic_assembly), a Tertiary Stage in place of the jacket and a Twin Boosted Core in place of the Boosted Fission Core. Its mushroom cloud stands until the crater is finished, then lasts two and a half minutes more.
"""),
    text("""
**Fallout.** A nuclear blast leaves the ground round it [radioactive](safety/radiation), strongest at ground zero (60,000 RAD/h: deadly in a minute) and falling off to nothing at its edge, and fading as it ages: 90 blocks round, safe again after a day, after a tactical warhead; 180 blocks round for two days after a fission warhead; 220 for two days after a boosted fission warhead; and 140 for only a quarter of a day after a neutron warhead (its neutrons leave little behind). Anything alive in it takes a dose and falls ill with **Radiation Sickness**; a [hazmat suit](safety/hazmat_suits) stops most of it. A burst on the ground also sends its debris cloud off downwind: a [fallout plume](safety/fallout) that lays fallout and ash along its path for hundreds of blocks. A barometric fuze bursts the warhead in the air over the target: the fireball does not touch the ground, so it leaves half the fallout and no plume.

**The ground.** The fireball burns the land it sees to [scorched ground](safety/scorched_ground): scorched earth and stone, glassed sand, charred trees, ash where the fires burned; the crater's wall melts to slag and trinitite.

**Fizzles.** A nuclear warhead only goes off properly when its missile brings it down. Set off any other way (dropped, shot, caught in another blast) its explosive lenses go off unevenly and the core breaks up: a small blast and a patch of contamination 16 blocks round, and no nuclear explosion. A dirty warhead is not nuclear, and does not fizzle.
"""),
    craft("tactical_fission_core"),
    craft("fission_core"),
    craft("boosted_fission_core"),
    craft("neutron_core"),
    craft("dirty_payload"),
])

entry("ordnance/exotic", "Exotic warheads", "antimatter_warhead", ["antimatter_warhead", "antimatter_trap", "singularity_warhead", "singularity_core",
                                                                     "storm_warhead", "storm_core", "cryogenic_warhead", "cryo_core"], [
    text("""
An exotic warhead holds something stranger than a nuclear core, and it is only ever built whole into its **ICBM**. Its core goes to the [Special Weapons Assembler](ordnance/special_weapons), which builds it whole with an **Intercontinental Booster**: an **Antimatter ICBM**, a **Singularity ICBM**, a **Storm ICBM** or a **Cryo ICBM**, fired like any other intercontinental missile from a [Launch Complex](ordnance/launch_complex) or a [Missile Silo](ordnance/missile_silo). There is no exotic bomb: the Assembly Bench takes no exotic core, and no missile assembler builds an exotic missile.

**Antimatter Warhead**: a white ceramic capsule ringed by three violet confinement coils. Its core is an **Antimatter Trap**: four [antimatter cells](nuclear/particle_accelerator) held in a Penning trap by two superconducting coils and a quantum circuit. When its missile brings it down the trap opens: a blinding white flash, then a white sphere grows to **67 blocks** in a second and a half and erases everything in it. Blocks of every kind go, however blast-proof (only bedrock and the like stay), with nothing dropped, no rubble and no fire; a chest goes with what is in it; anything alive inside is annihilated. When the sphere fades it leaves a perfectly round hole, a faint violet glow hanging in it for a few seconds. Sand at its edge falls in and water runs in, as at the rim of a crater.
"""),
    text("""
**The gamma flash.** The first instant sends out a flash of gamma rays that kills anything in the open out to **90 blocks** and hurts it out to **134**. Blocks in between stop it: one block of stone keeps it from killing even close by, though it still burns; two of stone, or one of concrete, lead or obsidian, cut it to a fifth; behind two of concrete it is hardly felt. A full [hazmat suit's](safety/hazmat_suits) lead keeps its wearer alive in the open, just. Whoever it does not kill takes a dose of [radiation](safety/radiation). It leaves no fallout behind.
"""),
    craft("antimatter_trap"),
    text("""
**Singularity Warhead**: a matte-black sphere hung in a gyroscope of three hoops, its violet core glowing through a slit. Its core is a **Singularity Core**: a [singularity seed](processing/exotic_matter_fabricator) held by four containment cells round a quantum circuit. When its missile brings it down the field is let go, and a black hole opens: a pinpoint at first, ringed by a glowing disc and two jets. It lives **a minute**, and it **feeds**: everything it tears loose or swallows makes it bigger, and the bigger it grows the farther it reaches and the harder it pulls.

On open ground it eats its way out to about **74 blocks** by the end of its minute: earth, sand and water a little past that, stone to about **67** (as far as the antimatter warhead's sphere), deepslate a little less. Its event horizon grows to about **22 blocks** round, and it draws things in from about **113**. Fed more it grows more: in a mountainside, or come down in a lake or the sea (the water keeps running in), it reaches about **90 blocks**. With nothing to eat (in the open air) it barely opens, to about 26.
"""),
    text("""
**The pull.** Everything within its pull is drawn in, harder the nearer it is and the bigger the hole has grown, and anything that reaches the horizon is gone. Run, and early: nothing on foot outruns it from within about half its reach, some 33 blocks when it is full grown.

**Tearing.** It tears the ground loose from the surface inward, the weakest blocks first: leaves and plants, then earth, sand and water, then stone. Its pull falls off steeply with distance, so the hole has a sharp edge; obsidian goes only within half its reach, everything within its horizon, and bedrock never. The torn blocks spiral into its disc, and nothing is dropped.

**Collapse.** At a minute it collapses in on itself: for two seconds it pulls harder than ever, its disc and everything in it spiralling in, a bright shell rushing inward, its horizon shrinking to a point; then it is gone, with a last flash and a deep thud. Nothing is blown outward: what is left is the hole it ate.
"""),
    craft("singularity_core"),
    text("""
**Storm Warhead**: a Tesla storm generator, squat and caged: a copper-wound drum, four capacitor tubes glowing blue round it and a Tesla coil's ring on top. Its core is a **Storm Core**: capacitor banks, two superconducting coils and a quantum circuit to fire them. Where its missile comes down it seeds a **supercell over a whole region**: **160 blocks round** for **a minute and a half**. A wide dark cloud deck gathers over it in fifteen seconds and turns slowly round the eye, its wall cloud hanging low in the middle; the sky darkens, rain drives down and the wind howls.

**Lightning** comes down all over the region, **six strikes a second** at its height: a third of them at creatures standing in the open, a third at whatever stands tallest (trees, towers, hilltops), the rest anywhere. A strike does what lightning does (a creeper is charged, a pig turned) and more: it **chains** on to the nearest creature within 6 blocks, and on again, up to four jumps, each weaker. Every strike **explodes** where it comes down, blowing out a small crater about three blocks round with fires round its rim; it burns grass to dirt and fuses sand to glass round it, burns leaves away, and sends out an **EMP pulse**, a ring of blue sparks running out along the ground: every machine within **24 blocks** goes dark for twelve seconds, interceptors flying through it are fried, and a missile flown through it may turn a dud. At the storm's height almost anywhere in it sits under a pulse most of the time, so **missile defences inside it are knocked out**: send your next missiles in while it rages, fitted with Hardened Electronics so its pulses cannot touch them. Thunder cracks close by and rolls on from far off.
"""),
    text("""
**Shelter.** Under a roof you are safe from the strikes aimed at creatures in the open, though a chain can still jump to you from someone outside. A **lightning rod** inside the storm draws every strike that would land within 32 blocks of it to itself, safely (no blast, no crater, no fire), giving a redstone pulse each time. But the current pouring down the rod still sends out the strike's **EMP pulse**: rods keep a base from being blasted, not its machines and defences from going dark.

**The wind** drives everything round the eye, anticlockwise and a little in, hardest two fifths of the way out and calm in the eye itself. On foot you can still walk against it.
"""),
    craft("storm_core"),
    text("""
**Cryogenic Warhead**: a steel dewar with a sight glass glowing ice blue round its waist, four liquid nitrogen tanks strapped round it and a relief valve on top. Its core is a **Cryo Core**: [stabilised exotic matter](processing/exotic_matter_fabricator) held near absolute zero by two superconducting coils in a jacket of four liquid nitrogen canisters, and a quantum circuit to let it go. Where its missile comes down the heat sink is let go and drinks the heat out of everything round it: a blue-white flash, a dome of white vapour boiling up, and a **white wall of frozen air** rolls out over the land, **128 blocks** in eight seconds.

**The front.** Where it passes, water freezes to milky **Cryo Ice** (ponds near the middle freeze solid), lava crusts over to obsidian, fires go out, stone and grass rime over, plants freeze solid, and **Cryo Snow** is laid over the land and the treetops, deepest near the middle. Its cold air pools in low ground: it climbs no more than about 40 blocks above where it struck, so hilltops are spared. Every creature it catches is **frozen solid** (for 20 seconds near the heart, 4 at the edge, half as long under a roof) and hurt by the cold; armour does not help.
"""),
    text("""
**The heart.** Within **40 blocks** of where it struck nothing lives: everything there is frozen through and shatters. The cold goes underground there too: water and lava freeze in caves, glass cracks and falls apart, cave floors rime over and grow crystals. **Spires of ice** burst out of the ground as the front passes, a great one at the middle and a ring of smaller ones leaning outward, packed ice round a core of blue ice. They stay when the rest thaws.

**The cold snap.** For **three minutes** the region stays deadly cold: snow falls, a mist drifts over the ground, ice glitters in the air and the fog closes in (a whiteout near the heart), and your breath smokes. Anything in it freezes as in powder snow, faster near the middle and in the open, and once frozen through takes cold damage every second. **Leather** keeps the cold out, as it does powder snow's: wear one piece of leather armour and the cold snap cannot touch you (the front itself still freezes you where you stand).

**The thaw.** Where snow would not lie, the snow thaws a layer at a time and the ice melts over the next half hour or so: still water's ice back to water, a stream's away. In cold places it all stays, as do the spires anywhere.
"""),
    craft("cryo_core"),
])

entry("ordnance/strategic_assembly", "ICBM Assembly Hall", "strategic_assembly_hall", ["strategic_assembly_hall", "strategic_assembly_hall_spawner", "crane_rail", "fusion_secondary", "cobalt_jacket", "intercontinental_booster", "tertiary_stage", "twin_boosted_core"], [
    text("""
The **ICBM Assembly Hall** builds the biggest weapons whole. No missile body takes a two-stage thermonuclear warhead, so the hall puts the warhead and its missile together at once: an **intercontinental missile** (an ICBM), ready for the [Launch Complex](ordnance/launch_complex) or the [Missile Silo](ordnance/missile_silo). It is a long crane hall; while it works, the crane lays the missile on its cradles section by section. Each of its three warheads has an ICBM of its own: a drab three-stage one for the thermonuclear warhead, a dark blue one for the cobalt and the Tsar's clustered giant. The exotic weapons have an assembler of their own, the [Special Weapons Assembler](ordnance/special_weapons). (Not to be confused with the [Strategic Missile Assembler](ordnance/missile_assemblers), which builds the two-stage Strategic missiles for the fission warheads.)

Fit a **Boosted Fission Core** (the primary, from the [Assembly Bench's](ordnance/assembly) cores), a **Fusion Secondary** and an **Intercontinental Booster**: that makes a thermonuclear warhead. The slot beside the secondary takes one more part: a **Cobalt-59 Jacket** makes a [cobalt warhead](ordnance/nuclear) instead, a **Tertiary Stage** the **Tsar Mega-bomb**, whose primary is a **Twin Boosted Core** (two Boosted Fission Cores, two tritium canisters, a military circuit and a dense steel plate) in place of the Boosted Fission Core. A guidance unit and Hardened Electronics are optional, as on any [missile](ordnance/missiles). Press **Assemble**: a minute's work at 250 FE a tick, a minute and a half for the Tsar. **Take Apart** gives every part back. Pipes can feed and empty it through its Item Port, from above or from the floor's back edge. **Auto Assemble**, the looping-arrow button left of **Assemble** (its lamp lit green while it is on), starts the next one by itself as soon as its parts are in and the finished one has been taken out of the output: pipe parts in and finished ones out and it keeps building.
"""),
    text("""
An intercontinental missile flies **3,000 blocks**, as far as a [Launch Computer's](ordnance/launch_site) map reaches, from a Launch Complex or a Missile Silo. Its warhead goes off where it lands. What its warheads do is under [Nuclear warheads](ordnance/nuclear) and [Exotic warheads](ordnance/exotic).
"""),
    structure("strategic_assembly_hall", "A 7 by 13 floor of Launch Pads: the controller in the middle of the front edge, the Energy Port in the middle of the back edge, and an Item Port two blocks to its right as you face the controller. Gantry Frame columns four high stand at the four corners and halfway along each long side, and a Crane Rail runs the length of each long side on top of them. Keep the hall between them clear, five wide and five high: the missile and the crane need the room. A pipe may stand on the Item Port."),
    craft("strategic_assembly_hall"),
    craft("crane_rail"),
    craft("fusion_secondary"),
    craft("cobalt_jacket"),
    craft("intercontinental_booster"),
    craft("tertiary_stage"),
    craft("twin_boosted_core"),
])

entry("ordnance/special_weapons", "Special Weapons Assembler", "special_weapons_assembler", ["special_weapons_assembler", "special_weapons_assembler_spawner"], [
    text("""
The **Special Weapons Assembler** builds the [exotic weapons](ordnance/exotic) whole: an **Antimatter ICBM**, a **Singularity ICBM**, a **Storm ICBM** or a **Cryo ICBM**, each from its core and an **Intercontinental Booster**. A [Launch Complex](ordnance/launch_complex) or a [Missile Silo](ordnance/missile_silo) fires them like any other intercontinental missile, **3,000 blocks**.

It is a containment bay: three magnetic **containment rings** stand across it, and the missile is laid on its cradles through them, section by section, the warhead last. While it works the rings' rotors turn and their fields glow violet round the missile.
"""),
    text("""
Fit an **Antimatter Trap**, a **Singularity Core**, a **Storm Core** or a **Cryo Core** and an **Intercontinental Booster**; a guidance unit and Hardened Electronics are optional, as on any [missile](ordnance/missiles). Press **Assemble**: a minute and a half's work at 320 FE a tick. **Take Apart** gives every part back. Pipes can feed and empty it through its Item Port (from above or from the floor's back edge), and it takes [upgrade chips](processing/upgrade_chips) like the other assemblers. **Auto Assemble**, the looping-arrow button left of **Assemble** (its lamp lit green while it is on), starts the next one by itself as soon as its parts are in and the finished one has been taken out of the output: pipe parts in and finished ones out and it keeps building.

The cores themselves are crafted: see [Exotic warheads](ordnance/exotic).
"""),
    structure("special_weapons_assembler", "A 7 by 13 floor of Launch Pads, as the ICBM Assembly Hall's: the controller in the middle of the front edge, the Energy Port in the middle of the back edge, and an Item Port two blocks to its right as you face the controller. Across it, on the fourth, seventh and tenth rows from the front, stand three containment rings: each a square of eight Toroidal Field Coils, three wide and three high, round the middle of its row, its middle left empty. Keep the bay clear five wide and three high: the missile and its cradles need the room. A pipe may stand on the Item Port."),
    craft("special_weapons_assembler"),
])

entry("ordnance/radar_array", "Radar Array", "radar_array", ["radar_array", "large_radar_dish"], [
    text("""
A **Radar Array** watches the sky for 2048 blocks round it and tracks every missile in the air, yours or anyone's. Its screen is a sweeping radar scope: a missile shows as a blip where the sweep last passed it. One that will come down within 512 blocks of the array (inside the dashed red ring) is **incoming**: red on the scope, flagged on the list, with how long until it lands and where.

A comparator by the controller reads how many missiles are incoming (up to 15): wire it to alarms or doors. It runs on 100 FE/t. It is also the eyes of the [Interceptor Battery](ordnance/interceptor_battery) and the [Interceptor Complex](ordnance/interceptor_complex): they engage any missile a working array tracks at their full reach. Link it to a [Launch Control Station](ordnance/launch_control_station) with Launch Codes and the station's map shows what it tracks, while it is loaded and working.
"""),
    structure("radar_array", "A 3 by 3 deck of Machine Casing, the controller in the middle of its front edge and the Energy Port in the middle of its back edge. Three Radar Masts stand up from the middle of the deck, with a Large Radar Dish on top, its middle over the mast: formed, it turns."),
    spot("large_radar_dish", """
One item that places a whole 3 by 3 dish, face up, centred where you place it (the 3 by 3 must be clear). Break any part of it and the whole dish comes away as one item.
"""),
    craft("radar_array"),
    craft("large_radar_dish"),
])

entry("ordnance/interceptor_battery", "Interceptor Battery", "interceptor_battery", ["interceptor_battery", "interceptor_missile"], [
    text("""
The **Interceptor Battery** is the anti-missile defence: a launcher turret on a two-block pedestal that shoots down missiles coming down near it. Load **Interceptor Missiles** into it, by hand or by pipe; its four cells fill from that store, one every three seconds, and you can see their white noses in the launcher's mouth.

Powered (40 FE a tick, and 4,000 for each launch), it watches the sky. A missile bound for within **256 blocks** of it, if a working [Radar Array](ordnance/radar_array) tracks it, or **96** on the battery's own seeker, draws an interceptor the moment one can meet it in time: the turret swings round, the interceptor leaps out of its cell, climbs and curves over onto the point where it meets the missile. A class L or bigger missile draws two. Missiles flying out from near it, bound elsewhere, it lets go, so it will not shoot down your own.
"""),
    text("""
**Kill chance.** An interceptor kills a small missile **80%** of the time, a medium one 72%, a heavy one 60%, a Strategic one 48% and an intercontinental one 40%. Without radar, only six tenths as often; against a missile coming back down hot from high up, 0.85 as often; against a superweapon's missile (a SCUD, China's Nuclear Missile), half as often. A missile shot down breaks up in the air: its warhead **never goes off**. A miss bursts harmlessly, and the battery fires again if there is still time.

Its screen shows its cells, whether a radar is tracking for it, and its tally. It takes [upgrade chips](processing/upgrade_chips): speed loads its cells faster, efficiency spends less power. A redstone signal can stop it, as any machine. An **EMP** knocks it out, and fries its interceptors flying through one: a [Storm](ordnance/exotic)'s strikes send out EMP pulses, so a battery inside a storm is down most of the time. For more reach, surer kills and bigger volleys, build the [Interceptor Complex](ordnance/interceptor_complex).
"""),
    craft("interceptor_battery"),
    craft("interceptor_missile"),
])

entry("ordnance/interceptor_complex", "Interceptor Complex", "interceptor_complex", ["interceptor_complex", "launch_cell", "phased_array_panel"], [
    text("""
The **Interceptor Complex** is the anti-missile defence built big: a vertical launcher of 24 cells in front of a fire-control radar. Beside an [Interceptor Battery](ordnance/interceptor_battery) it reaches **two and a half times as far**, its interceptors are surer, and it sends **twice as many** at each missile.

Load **Interceptor Missiles** into its store (three slots, by hand or by pipe into its Item Port); its cells fill one a second. Powered (160 FE a tick into its Energy Port, and 4,000 for each launch), it watches the sky. A missile bound for within **640 blocks** of it, if a working [Radar Array](ordnance/radar_array) tracks it, or **240** that its own radar alone sees, draws a **volley**: two interceptors, four at a class L or bigger missile, fired two at a time. Their hatches swing open and they leap straight up out of their cells, then curve over onto the missile.
"""),
    text("""
**Kill chance.** Its radar guides every interceptor all the way in, so each kills a small missile **95%** of the time, radar array or not: a medium one 86%, a heavy one 71%, a Strategic one 57% and an intercontinental one 48% (less against one coming back down hot, half as often against a superweapon's). With two or four at each, little gets through, and if a whole volley misses and there is time, it fires another.

It shoots down any missile coming down within its reach, **yours too**: a redstone signal stops it while you fire close by. An **EMP** knocks it out and fries its interceptors flying through one (a [Storm](ordnance/exotic)'s strikes send out EMP pulses). Its screen shows its cells and store, its reach and its tally; it takes [upgrade chips](processing/upgrade_chips).
"""),
    structure("interceptor_complex", "A 5 by 5 deck, three high. The front row: Launch Pads, the controller in the middle. Behind it the launcher: six Launch Cells, three across and two deep, a Launch Pad either side of them; the blocks just over the cells must be clear, for their hatches to swing open. At the back the deckhouse, two deep and three high, of Machine Casing: in the middle of its front, over the deck, six Phased Array Panels (three across, two high), its radar; low in its back wall the Energy Port, on your right as you face the controller, and the Item Port on your left."),
    craft("interceptor_complex"),
    craft("launch_cell"),
    craft("phased_array_panel"),
])

entry("ordnance/chemical", "Chemical grenades", "gas_grenade", ["gas_grenade", "toxin_grenade", "chlorine_canister",
                                                                "toxin_canister", "salt", "chlorine_bucket", "toxin_bucket"], [
    text("""
Grenades that poison rather than blow up. Both are made in the [Chemical Plant](processing/chemical_plant) and both are stopped by a [hazmat suit](safety/hazmat_suits): each piece keeps out its share, and a full suit of either kind keeps out all of it.
"""),
    spot("gas_grenade", """
A smoke grenade filled with **chlorine**. After 2 seconds it pours gas out for 25 seconds. The gas behaves like a real gas: it spreads out through the open air, **sinks** into hollows, cellars and trenches, drifts **downwind**, and is stopped by walls, glass, closed doors and panes (but runs over slabs and carpets). It thins out and clears within half a minute of the canister running dry. Everything breathing it gets **Poison III** and takes up to half a heart every half second, less where the gas is thin; the undead are not poisoned, but the gas still burns them. Like smoke, it hides whoever is in it.
"""),
    spot("toxin_grenade", """
A glass vial that shatters on impact and splashes **toxin** over the ground 3.5 blocks round. Whatever is caught in the splash gets **Poison IV** and nausea, and the glowing green sludge it leaves does the same to anything that walks through it. The sludge dries out over about two minutes.
"""),
    machine("chemistry", "", item="salt"),
    machine("chemistry", "", fluid="chlorine"),
    machine("chemistry", "", fluid="toxin"),
    text("""
Salt is leached out of dried kelp with water; chlorine comes from salt water. Toxin is chlorine and a little sulfuric acid worked up with spider eyes and a poisonous potato. Can them in the [Canning Machine](processing/canning_machine), then craft the canisters into grenades.
"""),
    craft("gas_grenade"),
    craft("toxin_grenade"),
])

# =========================================================================================================================
chapter("superweapons", "Superweapons", "scud_storm", """
The two great superweapons of the old wars: the GLA's **SCUD Storm** and China's **Nuclear Missile**. Each is a structure of its own, takes only its own ammunition (built at a factory of its own faction, the **Arms Dealer** or the **War Factory**), charges for minutes on power and is fired from a [Launch Computer](ordnance/launch_site). When one is built, readied or launched, everyone in the dimension hears the warning.
""")

entry("superweapons/scud_storm", "SCUD Storm", "scud_storm", ["scud_storm", "adobe_wall", "toxin_tank", "scud_erector", "scud_storm_spawner",
                                                               "toxin_scud"], [
    text("""
The GLA's superweapon: a square sand pad, nine **Toxin SCUDs** lying like the spokes of a wheel on their erectors round a tank of toxin, behind a low adobe wall with a watchtower at each end, a guard hut flying its flag.

Load it with up to nine Toxin SCUDs and give it power through its Energy Port (pipes can feed it through its Item Port). It **charges for 5 minutes** at 400 FE a tick (charging pauses without power); then it is **READY**. A [Launch Computer](ordnance/launch_site) within reach lists it with its charge; aim it, arm it and press LAUNCH. Its erectors raise the SCUDs upright, a 10 second countdown runs and they go **one after another**, each coming down anywhere within **16 blocks** of the target. Then it charges again from empty.
"""),
    structure("scud_storm", "The SCUD Storm: a 15 by 15 pad of Launch Pads, the controller in the middle of its front edge with an Item Port three to its left and an Energy Port three to its right. Behind the pad and down its left side, an L of Adobe Wall two high, with a watchtower four high at each end. On the pad, a 3 by 3 Toxin Tank two high in the middle and nine SCUD Erectors round it (the Holo-Projector shows where). Keep everything else over the pad clear six blocks up: the SCUDs lie on it and rise over it."),
    spot("toxin_scud", """
A SCUD as the GLA paints it: white, its fins and nose toxin green. Built at an [Arms Dealer](superweapons/arms_dealer); only a SCUD Storm fires it. Where it comes down: a heavy blast, then [toxin](superweapons/toxin) wells up out of the crater.
"""),
    craft("scud_storm"),
    craft("adobe_wall"),
    craft("toxin_tank"),
    craft("scud_erector"),
])

entry("superweapons/toxin", "Toxin strikes", "toxin_scud", ["toxin_scud"], [
    text("""
A Toxin SCUD's warhead is the point of it. A moment after its blast has dug its crater, its **toxin** wells up out of it as a heavy green cloud that hugs the ground, sinks into hollows and drifts with the wind for two minutes and more, and a **front of glowing anthrax residue** runs out over the ground round it, 21 blocks, splashing as it goes and dosing whatever it washes over.

The residue bubbles and fumes and poisons whatever walks through it (Poison IV and nausea), drying out over about eight minutes. A hazmat suit keeps out both the cloud and the residue.
"""),
])

entry("superweapons/nuclear_missile_silo", "Nuclear Missile Silo", "nuclear_missile_silo", ["nuclear_missile_silo", "fieldstone_wall",
                                                                                         "missile_erector", "nuclear_missile_silo_spawner",
                                                                                         "nuclear_missile"], [
    text("""
China's superweapon is not a silo dug into the ground but an above-ground "coffin": a long hall, fieldstone below and grey walls between green steel pilasters under a brown roof in four sections, a red star on its gable; the missile lies inside on its erector. Beside the hall's front stands an octagonal control house, at its back a green lattice tower.

Load it with a **Nuclear Missile** and give it power. It **charges for 6 minutes** at 800 FE a tick; then a [Launch Computer](ordnance/launch_site) aims, arms and fires it. The roof folds open, the erector raises the missile upright beside the tower, its arm swings in, and after a 10 second countdown it climbs straight out of the hall. Where it comes down: a nuclear blast (a crater about 68 blocks across) and a **radiation field 200 blocks round** that lasts two days.
"""),
    structure("nuclear_missile_silo", "The Nuclear Missile Silo: the hall, 5 wide and 15 long, its walls Fieldstone Wall two high with the controller in the middle of the front wall, an Energy Port in the right wall near the front and an Item Port in the left; inside, a floor of Launch Pads with the Missile Erector on it at the back. Beside the hall's front on the left, the control house: a 3 by 3 of Fieldstone Wall two high. Beside the hall's back on the right, a Gantry Frame tower nine high. Keep the hall's inside and the space over its roof clear, and the missile's column over its back end eleven blocks up."),
    spot("nuclear_missile", """
China's missile, a two-stage liquid-fuelled ICBM in silver with red bands and a red star, a boosted fission warhead under its cone. Built at a [War Factory](superweapons/war_factory); only a Nuclear Missile Silo fires it.
"""),
    craft("nuclear_missile_silo"),
    craft("fieldstone_wall"),
    craft("missile_erector"),
])

entry("superweapons/arms_dealer", "Arms Dealer", "arms_dealer", ["arms_dealer", "arms_dealer_spawner"], [
    text("""
The GLA's factory for **Toxin SCUDs**: a walled adobe compound with a workshop at its back, an awning over racks of rockets and an A-frame hoist over its yard. Load its parts (a Medium Missile Body, a Solid Rocket Motor II, a Chemical Agent Canister and a Toxic Waste Tank) and give it power: it sets to work by itself, its hoist laying the SCUD out on its trestles section by section, **30 seconds** at 160 FE a tick for each. The finished SCUD waits on the trestles and in the output.
"""),
    structure("arms_dealer", "The Arms Dealer: a 9 by 7 compound walled with Adobe Wall two high, the controller in the middle of its front wall, an Item Port in its left wall and an Energy Port in its right. Inside, a yard of Launch Pads; across the back of it, the workshop, a 7 by 2 block of Adobe Wall two high. Keep the yard clear three blocks up."),
    craft("arms_dealer"),
])

entry("superweapons/war_factory", "War Factory", "war_factory", ["war_factory", "war_factory_spawner"], [
    text("""
China's factory for **Nuclear Missiles**: an octagonal hall under two pagoda roofs, a furnace annex with its striped chimney, a conveyor of crates and a missile yard with a gantry crane on rails. Load its parts (a Strategic Missile Body, two Liquid-fuel Stages, a Boosted Fission Core, a Strategic Warhead Casing and a GPS Guidance Unit) and give it power: it builds by itself, its crane laying the missile out on the cradles section by section, **60 seconds** at 480 FE a tick for each.
"""),
    structure("war_factory", "The War Factory: 9 wide and 13 long. At the back left, the hall: a 5 by 5 ring of Fieldstone Wall two high round a floor of Launch Pads. At the front left, the furnace annex: a 3 by 3 of Fieldstone Wall two high, its chimney (Fieldstone Wall) six high in the front left corner. Down the right, the missile's yard, three Launch Pads wide and eleven long, with a row of Crane Rail along its far side. Launch Pads everywhere else, the controller in the middle of the front row, an Energy Port and an Item Port in the back row. Keep the yard, the crane's way and the space over the hall clear."),
    craft("war_factory"),
])

chapter("safety", "Safety", "decontamination_shower", """
Hazards the mod adds, and how to deal with them.
""")

entry("safety/radiation", "Radiation", "geiger_counter", [], [
    text("""
Radiation can't be seen or felt, only measured: carry a [Geiger Counter](safety/geiger_counter). Its strength where you stand is the **dose rate**, in **RAD/h**; what it does to you adds up as your **dose**, in **rad** (a [Dosimeter Badge](safety/geiger_counter) keeps count). An hour at 100 RAD/h is 100 rad.
"""),
    text("""
**Where it comes from.**

- **Craters and meltdowns.** A nuclear blast leaves the land round it radioactive, strongest at ground zero and falling off to nothing at its edge, and fading as it ages: it halves every few minutes at first (cobalt lingers for weeks). A fission warhead's crater starts at 60,000 RAD/h: deadly in a minute. Its [fallout plume](safety/fallout) carries more of it downwind.
- **Radioactive blocks.** Uranium ore (faintly: a Geiger counter finds it), uranium and raw uranium blocks, corium (deadly). You feel them within 6 blocks, less the farther you are and through whatever lies between.
- **What you carry.** Uranium, plutonium, fuel rods (spent ones above all), irradiated targets, nuclear waste and corium dose you from your own pockets: a stack of uranium ingots brings sickness within half an hour, a spent fuel rod within three minutes. Keep them in a [Lead Container](safety/lead_container).
"""),
    table([
        ["1 RAD/h", "100 hours"],
        ["10 RAD/h", "10 hours"],
        ["100 RAD/h", "an hour"],
        ["1,000 RAD/h", "6 minutes"],
        ["10,000 RAD/h", "36 seconds"],
    ], "Until sickness", """
Real time until the first 100 rad, at each dose rate.
"""),
    table([
        ["100 rad", "Sickness I"],
        ["200 rad", "Sickness II"],
        ["400 rad", "Sickness III"],
        ["600 rad", "Sickness IV"],
        ["1,000 rad", "lethal"],
    ], "Radiation Sickness", """
I: nausea now and then, hunger. II: weakness, slow mining, more hunger. III: a heart of damage every 5 s, slowness, no natural healing. IV: wither, the edge of sight going dark. Lethal: a quick death.
"""),
    text("""
**Shelter.** Blocks stop radiation: a block of stone stops half of it, a block of concrete or obsidian three quarters, a block of lead nearly all; earth, wood and water less, glass very little. A one-block stone hut halves what reaches you; a bunker under the ground stops it. A [hazmat suit](safety/hazmat_suits) stops its share of what comes from outside, not of what you carry.

**Getting better.** Below 10 RAD/h your dose falls by itself, 1 rad a minute. [Medicine](safety/radiation_medicine) is faster. Death clears it. The undead are immune.
"""),
    links(["safety/geiger_counter", "safety/lead_container", "safety/radiation_medicine", "safety/hazmat_suits", "safety/reactor_safety"]),
])

entry("safety/fallout", "Fallout", "fallout_ash", ["fallout_ash", "ash"], [
    text("""
A nuclear burst on the ground, a dirty warhead and a reactor meltdown each send a **plume** of radioactive debris off on the **wind**. It drifts at the wind's speed, spreading as it goes, and lays [radiation](safety/radiation) along its path: strongest near the blast, weaker and shorter-lived downwind, out to 250 blocks after a tactical warhead, 600 after a fission warhead, 900 after a thermonuclear one and 1,400 after the Tsar; a cobalt warhead's carries its month-long fallout 1,020 blocks. An airburst sends none.
"""),
    text("""
**The wind** is the same everywhere in a dimension. It wanders: every few minutes it swings round, up to about a right angle, and changes speed: 1 to 3.5 blocks a second in fair weather, 2.5 to 5 in rain, 4 to 8 in a thunderstorm. The fallout clouds overhead drift with it.

**Under a plume** ash falls round you, drifting with the wind, and **Fallout Ash** settles on the ground in layers, as snow does. Get under cover: a roof keeps the ash off and the walls stop some of the radiation. A [Geiger Counter](safety/geiger_counter) shows where the plume went.
"""),
    spot("fallout_ash", """
Weakly radioactive: a carpet of it reads about 10 RAD/h. Shovel it away (it is dust: nothing drops). Left alone it weathers into plain **Ash** over a couple of hours.
"""),
    links(["safety/radiation", "safety/scorched_ground", "ordnance/nuclear", "safety/reactor_safety"]),
])

entry("safety/scorched_ground", "Scorched ground", "trinitite",
      ["trinitite", "glassed_sand", "slag", "scorched_earth", "scorched_stone", "irradiated_soil", "charred_log", "charred_wood",
       "charred_planks", "dead_grass_block", "dead_leaves", "withered_crops"], [
    text("""
What a nuclear blast leaves of the land round it. The fireball burns what it sees out to its scorch band: earth to **Scorched Earth**, stone to **Scorched Stone**, sand and sandstone to **Glassed Sand**, trees to charred trunks; where the firestorm raged, a layer or two of **Ash**. The crater's own wall melts: rock to **Slag**, sand and sandstone to **Trinitite**, earth to **Irradiated Soil**.
"""),
    spot("trinitite", """
Sand fused to green glass by the fireball, laced with its fission products: weakly radioactive.
"""),
    spot("irradiated_soil", """
Earth full of fallout. It glows faintly, and it is radioactive: dig it out before you build on it.
"""),
    spot("charred_log", """
A trunk burnt black. Four Charred Planks from a log, or smelt it for charcoal. Charred planks are planks: sticks, crafting tables, anything.
"""),
    text("""
**Dead plants.** Strong radiation, 1,000 RAD/h or more, kills plants from the top down: leaves die on the branch and drop away in an hour or two, crops wither, grass and flowers go and the grass block dies. A roof spares what is under it. A neutron warhead's burst kills them out to 120 blocks. **Dead Grass** grows back once its ground is safe again, under 10 RAD/h in daylight.
"""),
    craft("charred_planks"),
    links(["safety/fallout", "safety/radiation", "ordnance/nuclear"]),
])

entry("safety/wasteland", "The wasteland", "frosted_stone",
      ["toxic_soil", "frosted_stone", "frosted_grass_block", "frozen_plant", "cryo_crystal", "cryo_snow", "cryo_ice"], [
    text("""
**The Nuclear Wasteland.** Once a nuclear blast has dug its crater, the land it scorched turns to wasteland: grey-brown grass and leaves, water a dead olive, a dusty sky and brown fog, ash drifting down and no animals; only the dead walk there. It stays wasteland when the radiation has gone (the server can turn this off).

**Crater lakes.** A crater fills with still water where the land would flood it: next to the sea, a lake or a river (to that water's level), or deep below sea level where the ground is wet (groundwater; never in deserts, badlands or savanna). The water stays a block under the rim's lowest point; a giant crater fills only to a pond in its deepest part. It is as radioactive as the crater.
"""),
    spot("frosted_stone", """
**Frozen ground.** A cryo burst rimes the stone and grass it reaches, freezes small plants solid and grows **Cryo Crystals** round its middle. Where snow would not lie it all thaws again within a quarter of an hour or so; in cold places it stays. Frozen plants and crystals melt away; silk touch keeps a crystal.

A [Cryo ICBM's](ordnance/exotic) front also lays **Cryo Snow** (snow that thaws, a layer at a time, where snow would not lie) and freezes water to **Cryo Ice**, milky with the air it trapped: it thaws in the same places within about half an hour, still water's ice back to water and a stream's away, so a thaw floods nothing. Its spires of packed and blue ice stay.
"""),
    spot("toxic_soil", """
**Toxic Soil.** Where a heavy anthrax cloud lies on the ground, or a toxin SCUD's residue settles, the soil turns. It kills what grows on it, breathes a thin anthrax haze now and then (a gas mask or a hazmat suit keeps it out), and is spent, coarse dirt, after about a day.
"""),
    links(["safety/scorched_ground", "safety/fallout", "ordnance/nuclear"]),
])

entry("safety/old_sites", "Old sites", "waste_drum", ["damaged_warhead", "waste_drum"], [
    text("""
The Cold War left its sites across the world, long abandoned. Each has its own loot; a few have their own dangers.

**Abandoned Missile Silo** (plains, taiga, desert, savanna): a fenced hardstand with a closure door on rails and a concrete portal. Its ladder goes down to a blast lock; one cableway runs to the silo, where the missile still stands among its work platforms (or the shaft has flooded, or the door has fallen in), the other to the two-floor launch control centre. The foot of the shaft is where the warhead fell.

**Command Bunker** (hills, forests, taiga): a blockhouse whose doorway turns twice to break a blast, a ladder down to blast doors, decontamination showers with suits still on their stands, the situation room's wall map, the radio room, the armoury, bunks, and the plant room's filters and generator. Its escape tube is full of gravel.

**Radar Outpost** (hills, plateaus): a radome on a train of linked buildings, the long-range dish toppled off its pedestal, a watchtower.
"""),
    text("""
**Test Site** (deserts, badlands): ground zero's shallow crater, its sand fused to trinitite and glass, still weakly radioactive; the shot tower's four footings and an obelisk; a great steel vessel on its side; instrument towers; a blasted house; and, to the south, the observation bunker banked with sand.

**Crashed Missile** (most lands): a furrow ploughed into the ground, the missile in pieces along it, its warhead split open in the crater at the end: the core lies bare. Do not stand by it long.

**Waste Storage** (deep underground): rooms mined from salt, drums of nuclear waste three high under their sacks, one room sealed off, one caved in; decontamination by the shaft, and a lead-lined hot cell. The drums are radioactive.

**Exotic Research Station** (the End's outer islands): a deck on a floating rock, its dome over a broken containment ring, exotic crystals grown through the floor.
"""),
    spot("damaged_warhead", """
A broken nuclear warhead, from a silo's shaft or a crashed missile. It is radioactive: keep it in a [Lead Container](safety/lead_container). A [Chemical Plant](processing/chemical_plant) leaches its core out with sulfuric acid: enriched uranium, often plutonium, and steel.
"""),
    links(["safety/radiation", "safety/wasteland", "processing/chemical_plant"]),
])

entry("safety/geiger_counter", "Geiger Counter and Dosimeter", "geiger_counter", ["geiger_counter", "dosimeter_badge"], [
    spot("geiger_counter", """
A civil-defence survey meter. Hold it in either hand: it **clicks** at random, faster the stronger the radiation where you stand (a few clicks a second near uranium ore, a roar in a crater), and its **meter** shows in a corner of the screen: a needle on a scale from 0.1 to 10,000 RAD/h and the reading. Anywhere in your hotbar it still clicks, and shows the reading alone.
"""),
    text("""
**Alarm.** Sneak and use it to set its alarm: it beeps and its reading turns red above 1, 10, 100 or 1,000 RAD/h (or never). New counters are set to 100: sickness within the hour.

**Power.** It runs on its own charge, 20,000 FE, using 5 FE a second while it is on (an hour on a full charge). Charge it in a [Battery Charger](power/batteries). An EMP knocks it out until 10 seconds after you leave the field.
"""),
    spot("dosimeter_badge", """
A film badge: carry it anywhere in your inventory. Its film shows the dose you hold now: white under 100 rad, then yellow, orange, red and black as each stage of sickness begins, and clearer again as the dose falls (by itself or with [medicine](safety/radiation_medicine)). Its tooltip gives the dose to the rad and the sickness it brings, and the dose shows under the Geiger reading.
"""),
    craft("geiger_counter"),
    craft("dosimeter_badge"),
    links(["safety/radiation", "safety/radiation_medicine"]),
])

entry("safety/lead_container", "Lead Container", "lead_container", ["lead_container"], [
    spot("lead_container", """
A thick lead flask with nine places, for radioactive items only. Nothing inside it reaches you, wherever it is in your inventory. Use it to open it.

Carried loose, a stack of uranium ingots doses you at 256 RAD/h (sick within half an hour), a spent fuel rod at 2,000 (sick in three minutes) and a stack of nuclear waste at 32,000 (dead in two). A bundle or a shulker box stops nothing.
"""),
    craft("lead_container"),
    links(["safety/radiation"]),
])

entry("safety/radiation_medicine", "Radiation medicine", "rad_away", ["rad_away", "potassium_iodide", "anti_rad_injector"], [
    spot("potassium_iodide", """
Take before going in: for 10 minutes you take up only **half** the dose. Iodine from dried kelp, potassium from saltpeter.
"""),
    spot("rad_away", """
An IV bag: draws **150 rad** out of you over 30 seconds.
"""),
    spot("anti_rad_injector", """
For emergencies: **400 rad** off at once, but you are weak for a minute after.
"""),
    craft("potassium_iodide"),
    craft("rad_away"),
    craft("anti_rad_injector"),
    links(["safety/radiation", "safety/geiger_counter"]),
])

entry("safety/decontamination_shower", "Decontamination Shower", "decontamination_shower", ["decontamination_shower"], [
    spot("decontamination_shower", """
A walk-in booth: pipe water in, power it, step inside. It sprays whoever stands in it (20 FE and 10 mB of water a tick), putting out fire and washing off poison, wither and nausea. A dose of [radiation](safety/radiation) once taken can't be washed off.
"""),
    craft("decontamination_shower"),
])

entry("safety/chemical_hazards", "Chemical hazards", "sulfuric_acid_canister", [], [
    text("""
Spilled chemicals are dangerous:

- **Acids** burn whatever stands in them.
- **Napalm gel** sets things alight.
- **Liquid oxygen and nitrogen** freeze anything in them.
- **Gases** released into the world drift upward as a cloud until they disperse.

Keep fluids in tanks, pipes and canisters. The [Decontamination Shower](safety/decontamination_shower) puts out fire and washes off poison.
"""),
])

entry("safety/reactor_safety", "Reactor safety", "lead_lined_casing", ["nuclear_waste", "corium", "cooled_corium"], [
    text("""
Keep the [Fission Reactor](nuclear/fission_reactor) below 900 C: keep coolant flowing and watch the heat gauge; with plain water, stay at or below about 70% rods out. The red button is the **SCRAM**: every control rod drops in and the reactor stays down until you withdraw them.
"""),
    text("""
**Meltdowns.** Nothing SCRAMs the reactor for you (a server can turn meltdowns off; then it SCRAMs itself at 900 C). Past **900 C** with the rods out the core goes **critical**: it keeps heating even with coolant flowing, a klaxon wails, a red warning flashes on the screen of everyone nearby and steam vents from the top.

**Press SCRAM** (or push every rod in) and it cools down. Leave it, and at **1,400 C** it melts down: the reactor and its fuel are destroyed in a dirty blast, molten corium pools in the crater and is thrown round it, and the land round it is radioactive for a day.
"""),
    table([
        ["Critical from", "900 C"],
        ["Meltdown at", "1,400 C"],
        ["Warning time", "15 to 45 s"],
        ["  with coolant", "the longer"],
        ["Radiation", "48 blocks"],
        ["  at the core", "40,000 RAD/h"],
        ["  safe again", "after a day"],
    ], "Meltdowns"),
    spot("corium", """
Molten core. It burns anything that stands on it and sets fire to what is next to it, and cools into cooled corium over about ten minutes. Both are radioactive; mine them with an iron pickaxe or better.
"""),
    text("""
**Radiation.** The land round a meltdown is [radioactive](safety/radiation): 40,000 RAD/h at the core when fresh, falling off to nothing 48 blocks out, and halving every couple of minutes. Anything alive in it takes a dose, and the dose brings **Radiation Sickness**. Corium is deadly up close. A [hazmat suit](safety/hazmat_suits) stops most of what comes from outside; check with a [Geiger Counter](safety/geiger_counter) before going in.
"""),
    spot("nuclear_waste", """
What reprocessing leaves behind. It does not burn, not even in lava; store it somewhere safe. A dropped stack still disappears in time like any other item.
"""),
    links(["safety/radiation", "safety/hazmat_suits", "safety/geiger_counter"]),
])

entry("safety/hazmat_suits", "Hazmat suits", "hazmat_hood", [
    "hazmat_hood", "hazmat_coat", "hazmat_trousers", "hazmat_boots", "lead_lined_hazmat_hood", "lead_lined_hazmat_coat",
    "lead_lined_hazmat_trousers", "lead_lined_hazmat_boots"], [
    text("""
"Hazmat" means hazardous materials, radioactive ones included. A **Hazmat Suit** is sealed plastic with a filter mask: it keeps radioactive dust off your skin and out of your lungs, which stops most of the harm of contaminated land, but it is thin protection against radiation itself.

A **Lead-Lined Hazmat Suit** adds lead plates over the chest, back, shins, crown and boots. It stops far more, and its weight slows you a little.
"""),
    table([
        ["Hazmat Suit", "75%"],
        ["Lead-lined", "92%"],
        ["Hood", "1/5 of it"],
        ["Coat", "7/20"],
        ["Trousers", "3/10"],
        ["Boots", "3/20"],
        ["Lead-lined", "5% slower"],
    ], "Radiation stopped", """
A full set gives the whole figure; each piece its share. Neither is much use as armour against blows.
"""),
    text("""
Both suits are also sealed against chemicals: poison gas, toxin sludge and toxic liquids. Each piece keeps out the same share as above (the hood a fifth, and so on), and a full set of either keeps out all of it. See [Chemical grenades](ordnance/chemical). The figures for each piece are on its tooltip.
"""),
    text("""
Against [radiation](safety/radiation), a suit stops its share of what comes from outside (craters, meltdowns, radioactive blocks), not of what you carry: keep that in a [Lead Container](safety/lead_container).

The hood needs plastic sheet, a glass pane for the visor and charcoal for the filter; the lead-lined suit is a Hazmat Suit piece with four lead plates round it.
"""),
    craft("hazmat_hood"),
    craft("hazmat_coat"),
    craft("hazmat_trousers"),
    craft("hazmat_boots"),
    craft("lead_lined_hazmat_coat"),
    links(["safety/radiation", "safety/reactor_safety", "safety/decontamination_shower"]),
])

entry("safety/alarms", "Alarm system", "air_raid_siren", ["missile_warning_receiver", "alarm_panel", "hazard_monitor", "air_raid_siren",
                                                          "klaxon", "alarm_bell", "warning_beacon", "emergency_light", "alert_display",
                                                          "alarm_relay"], [
    text("""
A base can warn itself. **Alarm devices** talk over **alarm channels**: numbers from 0 to 9999, set on each device's screen (right-click it). They all start on channel **0**, so a warning receiver and a siren placed anywhere in the same dimension already work together; give a group its own channel to keep it apart.

A channel **goes off** while any of its **senders** has something wrong:
- a [Missile Warning Receiver](safety/missile_warning_receiver) sees a missile coming down near it;
- an [Alarm Panel](safety/alarm_panel) is switched on by hand, or powered by redstone;
- a [Hazard Monitor](safety/hazard_monitor) reads too much radiation, or poison gas.
"""),
    text("""
Then everything on the channel answers: [sirens, klaxons and bells](safety/sirens) sound, [warning lights](safety/warning_lights) light, [Alert Displays](safety/alert_display) show what is wrong and **Alarm Relays** give redstone (shut your blast doors with them). Each of them also goes off when it is powered by redstone itself, channel or not.

The receiver, the monitor and the relay give redstone out; a comparator by a [Radar Array](ordnance/radar_array) reads how many missiles are coming too. None of the devices needs power.
"""),
    links(["safety/missile_warning_receiver", "safety/alarm_panel", "safety/hazard_monitor", "safety/sirens", "safety/warning_lights",
           "safety/alert_display"]),
])

entry("safety/missile_warning_receiver", "Missile Warning Receiver", "missile_warning_receiver", ["missile_warning_receiver"], [
    text("""
The **Missile Warning Receiver** listens to the radar. Every missile a working [Radar Array](ordnance/radar_array) is tracking (one within 2048 blocks of it) is checked against where it will come down: if that is within the receiver's **warning radius** (64 to 1024 blocks, on its screen), the receiver raises its channel, its lamp lights, and it sounds the attention tone each time a new one appears. It only warns where a missile is going, so a missile passing over to somewhere else stays quiet.

Its screen shows how many are coming and the time to the first impact. Its redstone, by choice: a steady 15; the number incoming; or rising as the first impact nears (15 in its last two seconds).
"""),
    craft("missile_warning_receiver"),
])

entry("safety/alarm_panel", "Alarm Panel", "alarm_panel", ["alarm_panel"], [
    text("""
The **Alarm Panel** is the alarm by hand. On its screen: **Sound alarm** raises its channel until you **Stop** it; **Silence** quiets the channel's sounders (its lights and displays stay on) until the alert is over; **Test** sounds the channel for five seconds. A redstone signal into the panel raises its channel too: wire it to a tripwire, a pressure plate, a daylight sensor, a comparator, anything.

It raises its channel as an **Alarm**, or as an **Attack** (a siren then sounds the All Clear when it stops).
"""),
    craft("alarm_panel"),
])

entry("safety/hazard_monitor", "Hazard Monitor", "hazard_monitor", ["hazard_monitor"], [
    text("""
The **Hazard Monitor** watches the air in front of it. It raises its channel when the dose rate there passes its threshold (10, 50, 200 or 1000 RAD/h: 10 is the most that is safe to stand in for long), or when there is poison gas in the air (chlorine, the toxin; this it can be set to ignore). Its needle and its lamp show it; its screen reads the radiation and the gas; it gives a redstone signal while it alarms, and beeps.

Put one in your shelter's airlock, and one by the reactor.
"""),
    craft("hazard_monitor"),
])

entry("safety/sirens", "Sirens, klaxons and bells", "air_raid_siren", ["air_raid_siren", "klaxon", "alarm_bell"], [
    spot("air_raid_siren", """
A motor and a rotor under a big horn that **turns round** as it sounds: its wail swells each time the horn sweeps past you. Heard 160 blocks off. Two signals, as the Cold War's: **Attack** (a wail rising and falling) and **Alert** (steady). When an attack ends it sounds the **All Clear**, steady, for fifteen seconds. It winds up as it starts and coasts down as it stops. Stand it on top of a post.
"""),
    spot("klaxon", """
A horn for the wall, heard 64 blocks off, with five tones: the **klaxon** (a-oo-ga); the **slow whoop**; **evacuate** (three blasts and a pause, the evacuation signal of fire alarms); **hi-lo** (two tones); the **general alarm** (seven short blasts and a long one, a ship's call to stations).
"""),
    spot("alarm_bell", """
An electric bell for the wall, heard 48 blocks off.
"""),
    craft("air_raid_siren"),
    craft("klaxon"),
    craft("alarm_bell"),
])

entry("safety/warning_lights", "Warning lights", "warning_beacon", ["warning_beacon", "emergency_light"], [
    spot("warning_beacon", """
A beacon for any face, floor, ceiling or wall: lit, its lens **rotates** (two beams sweeping round), **flashes** twice a second (every beacon in step) or burns **steady**. Red, amber, blue, green or white: use a dye on it (red; orange or yellow for amber; blue; green; white), or choose on its screen. It lights the space round it while it is on.
"""),
    spot("emergency_light", """
A caged bulkhead lamp for any face. Dark until an alarm, or white all the time (your bunker's lights); in alarm it turns **red and flashes**, every one in step, and keeps lighting the room.
"""),
    craft("warning_beacon"),
    craft("emergency_light"),
])

entry("safety/alert_display", "Alert Display and Alarm Relay", "alert_display", ["alert_display", "alarm_relay"], [
    spot("alert_display", """
A screen for the wall. **ALL CLEAR** in green, with its channel; in an alert, flashing: **ATTACK**, how many are inbound and the time to impact; **RADIATION** and the dose rate; **POISON GAS** and how much; or **ALARM, TAKE COVER**.
"""),
    spot("alarm_relay", """
Wireless redstone: it gives a signal of 15 while its channel goes off (or, set the other way, while all is clear). Shut blast doors, cut the lights, start the shelter's air.
"""),
    craft("alert_display"),
    craft("alarm_relay"),
])


# =========================================================================================================================
def write() -> None:
    if ROOT.exists():
        shutil.rmtree(ROOT)
    for c in CHAPTERS:
        out = ROOT / "chapters" / f"{c['id']}.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        data = {k: v for k, v in c.items() if k != "id"}
        out.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    for e in ENTRIES:
        chap, name = e["id"].split("/")
        out = ROOT / "entries" / chap / f"{name}.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        data = {k: v for k, v in e.items() if k != "id"}
        out.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    # An index of every file, for the GameTest that checks the content against the game (ManualContentTests).
    (ROOT / "index.json").write_text(json.dumps({"chapters": [c["id"] for c in CHAPTERS], "entries": [e["id"] for e in ENTRIES]},
                                                indent=2) + "\n", encoding="utf-8")
    # Every link must point at a real entry or chapter.
    ids = {e["id"] for e in ENTRIES} | {c["id"] for c in CHAPTERS}
    import re
    bad = []
    for e in ENTRIES:
        for p in e["pages"]:
            for target in re.findall(r"\]\(([^)]+)\)", p.get("text", "")):
                if target not in ids:
                    bad.append((e["id"], target))
            for target in p.get("entries", []):
                if target not in ids:
                    bad.append((e["id"], target))
    for c in CHAPTERS:
        for target in re.findall(r"\]\(([^)]+)\)", c["text"]):
            if target not in ids:
                bad.append((c["id"], target))
    if bad:
        raise SystemExit(f"broken links: {bad}")
    print(f"{len(CHAPTERS)} chapters, {len(ENTRIES)} entries written to {ROOT}")


if __name__ == "__main__":
    write()
