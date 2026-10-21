"""Field Manual content (DESIGN.md §24.2): writes assets/groundzero/manual/chapters/*.json and entries/<chapter>/*.json.

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

from gz import paths  # noqa: E402

ROOT = paths.ASSETS / "manual"
G = "groundzero:"


def gz(name: str) -> str:
    return name if ":" in name else G + name


# --- page helpers -------------------------------------------------------------------------------------------------------
def text(t: str) -> dict:
    return {"type": "text", "text": t.strip()}


def spot(item: str, t: str = "", title: str | None = None) -> dict:
    p = {"type": "spotlight", "item": gz(item), "text": t.strip()}
    if title:
        p["title"] = title
    return p


def craft(item: str, t: str = "") -> dict:
    return {"type": "crafting", "item": gz(item), "text": t.strip()}


def smelt(item: str, t: str = "") -> dict:
    return {"type": "smelting", "item": gz(item), "text": t.strip()}


def machine(kind: str, t: str = "", item: str | None = None, fluid: str | None = None) -> dict:
    p = {"type": "machine", "kind": kind, "text": t.strip()}
    if item:
        p["item"] = gz(item)
    if fluid:
        p["fluid"] = gz(fluid)
    return p


def structure(sid: str, t: str = "") -> dict:
    return {"type": "structure", "structure": gz(sid), "text": t.strip()}


def image(name: str, w: int, h: int, t: str = "") -> dict:
    return {"type": "image", "image": f"groundzero:textures/gui/manual/plates/{name}.png", "width": w, "height": h, "text": t.strip()}


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
    CHAPTERS.append({"id": cid, "title": title, "icon": gz(icon), "order": len(CHAPTERS), "text": intro.strip()})


def entry(eid: str, title: str, icon: str, items: list[str], pages: list[dict]) -> None:
    chap = eid.split("/")[0]
    order = sum(1 for e in ENTRIES if e["id"].startswith(chap + "/"))
    ENTRIES.append({"id": eid, "title": title, "icon": gz(icon), "order": order, "items": [gz(i) for i in items], "pages": pages})


# Plates rendered by tools/manual/plates.py (width x height in pixels).
PLATES = {"chemical_plant": (240, 220), "fission_reactor": (240, 260), "particle_accelerator": (240, 150),
          "fusion_reactor": (240, 150), "singularity_reactor": (240, 240)}


def plate(name: str, t: str = "") -> dict:
    w, h = PLATES[name]
    return image(name, w, h, t)


# =========================================================================================================================
chapter("getting_started", "Getting Started", "field_manual", """
This manual covers everything Ground Zero adds, from the first ore to the reactors that power the late game.

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
Ground Zero is a long climb from hand tools to superweapons. Everything runs on **power** (FE), made by generators and carried by cables.

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
    "deepslate_saltpeter_ore", "sandstone_saltpeter_ore"], [
    text("""
Seven new ores generate in new chunks. Each has a deepslate form; sulfur also grows in the Nether and saltpeter in desert and badlands sandstone. Tungsten is the rarest; lithium and rare earths are richer in the mountains, uranium in the badlands.
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
    ], "Where they are"),
    text("""
Uranium, lead, lithium, rare earths and tungsten drop **raw ores**: crush them in an [Ore Crusher](processing/ore_crusher) for two dusts each. Sulfur and saltpeter drop their dusts directly.

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
For the late game, a [Fission Reactor](nuclear/fission_reactor) makes up to 1,200 FE a tick from uranium fuel rods, and breeds plutonium and tritium on the side.

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

entry("processing/electrolytic_separator", "Electrolytic Separator", "electrolytic_separator", ["electrolytic_separator"], [
    spot("electrolytic_separator", """
Splits deuterium gas out of water. Pipe water into its water tank (a [Water Pump](processing/water_pump) is easiest) and draw the gas off with a pipe or a gas canister. Draws 60 FE a tick.
"""),
    craft("electrolytic_separator"),
    machine("electrolysis"),
])

entry("processing/cryogenic_condenser", "Cryogenic Condenser", "cryogenic_condenser", ["cryogenic_condenser", "oxygen_membrane", "nitrogen_membrane"], [
    spot("cryogenic_condenser", """
Liquefies gases out of the air. The **membrane** in its slot picks the gas: liquid oxygen or liquid nitrogen. Membranes are never used up. Draws 100 FE a tick.

Cryogenic liquids freeze anything standing in them.
"""),
    craft("cryogenic_condenser"),
    craft("oxygen_membrane"),
    craft("nitrogen_membrane"),
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

# =========================================================================================================================
chapter("components", "Components", "basic_circuit", """
The parts machines, weapons and structures are built from.
""")

entry("components/circuits", "Circuits", "basic_circuit", ["handmade_circuit_board", "basic_circuit", "advanced_circuit", "military_circuit",
                                                           "quantum_circuit", "sensor_array"], [
    text("""
Four tiers, each built on the one below in the [Circuit Fabricator](processing/circuit_fabricator). Early on a **handmade circuit board**, crafted by hand, counts as a basic circuit.
"""),
    craft("handmade_circuit_board"),
    machine("fabricating", "", item="basic_circuit"),
    machine("fabricating", "", item="advanced_circuit"),
    machine("fabricating", "", item="military_circuit"),
    machine("fabricating", "", item="quantum_circuit"),
    machine("fabricating", "", item="sensor_array"),
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
A **uranium fuel rod** powers the [Fission Reactor](nuclear/fission_reactor); it comes out as a **spent fuel rod**.

**Target rods** ride along in the reactor's breeding slots and come out irradiated: depleted uranium rods for plutonium, lithium-6 rods for tritium. See [Breeding and reprocessing](nuclear/reprocessing).
"""),
    craft("uranium_fuel_rod"),
    craft("du_target_rod"),
    craft("li6_target_rod"),
])

entry("nuclear/fission_reactor", "Fission Reactor", "fission_reactor", ["fission_reactor", "fission_reactor_spawner", "lead_lined_casing"], [
    plate("fission_reactor", "The formed Fission Reactor."),
    text("""
A 3x3x4 structure of lead-lined casing round a two-block core, with reactor glass windows on all four sides. Load up to four uranium fuel rods and **withdraw the control rods**: up to **1,200 FE a tick** out of its Energy Port.

Everything goes through its ports: coolant into the **Fluid Port** at the back of the bottom layer, fuel and target rods into the front **Item Port** on top, products out of the back one.
"""),
    structure("fission_reactor", "Controller at the front of the bottom layer, the Fluid Port behind it; on top an Item Port, the Energy Port in the middle and a second Item Port."),
    text("""
**Heat.** The further the rods are out, the more power and the more heat. With coolant flowing the core settles at a temperature that rises with power; without coolant it climbs fast. Past **900 C** the core goes **critical**: press the red **SCRAM** button (every rod drops in) or it melts down. See [Reactor safety](safety/reactor_safety).

- **Water:** safe up to about 70% rods out; further than that and the core climbs past 900 C. Up to 25 mB a tick.
- **Heavy water:** keeps the core well below 900 C even at full power, and lasts five times longer. Make it in the [Chemical Plant](processing/chemical_plant) from deuterium and liquid oxygen.

The coolant gauge has a dump button to empty it. The heat and rod gauges sit beside the rod buttons; the red button is the manual SCRAM.
"""),
    table([
        ["Full power", "1,200 FE/t"],
        ["Fuel rod lasts", "12,000 ticks"],
        ["Target rod", "6,000 ticks"],
        ["Critical at", "900 C"],
        ["Water", "25 mB/t"],
        ["Heavy water", "5 mB/t"],
    ], "At full power"),
    craft("fission_reactor"),
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
A 5x5x5 cage that holds a **singularity seed** and makes power by **eating items**: any item at all. Feed it fast enough and it makes up to **25,000 FE a tick**.

A machine casing floor with the controller in the middle of the front, an **Item Port** on the left and the **Energy Port** on the right; steel plating posts at the corners; reactor glass walls with a **containment emitter** in the middle of each wall and of the roof; and the **core cradle** floating in the centre of the empty chamber.
"""),
    structure("singularity_reactor", "The chamber inside must be empty but for the cradle. Any floor block may be swapped for an extra Item Port."),
    text("""
**The output rule.** Every item it eats is worth **10,000 FE**, and it eats at most **50 items a second**. So the power is set by how fast you feed it:

- 10 items a second: 5,000 FE a tick.
- 25 items a second: 12,500 FE a tick.
- 50 items a second: the full 25,000 FE a tick.

Holding the cage costs **1,000 FE a tick**, the worth of 2 items a second, so a trickle of items does not even pay for itself. Run it from automatic farms: cobblestone generators, tree farms, quarries.
"""),
    text("""
**The seed.** Put a singularity seed in the controller, where it stays, like a battery in the charger, and charge the cage with **500,000 FE** through the Energy Port. The cage then engages and holds the seed in the cradle. Take the seed out to shut the reactor down safely.

**Feeding.** Items go into its four feed slots through Item Ports. A hopper moves only 2.5 items a second; an [item pipe](processing/item_pipes) up to 128. **Swap more floor blocks for Item Ports** (do it before engaging) to feed it from several pipes. The reactor keeps a 500,000 FE reserve for the cage and only pushes out power above it.

**Collapse.** If the cage runs out of power, or any part of the structure is broken while it holds the seed, **the singularity collapses and the seed is destroyed**.
"""),
    table([
        ["Per item eaten", "10,000 FE"],
        ["Most it eats", "50 items/s"],
        ["Full output", "25,000 FE/t"],
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
                                                                 "incendiary_grenade", "smoke_grenade", "molotov_cocktail"], [
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
    craft("molotov_cocktail"),
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

entry("ordnance/warheads", "Warheads", "he_warhead", ["he_warhead", "heavy_warhead", "thermobaric_warhead", "cluster_warhead",
                                                      "bunker_buster_warhead", "napalm_warhead", "massive_warhead"], [
    text("""
Warheads are what a [missile](ordnance/launch_site) carries: fit one in a Launch Silo with the missile, and the missile's nose is painted to match it. Each goes off as described below where the missile comes down.

Placed on their own, warheads stand nose up on their mounting rings. They are set up and armed like [placed charges](ordnance/charges): a frequency, at once or on a timer, then **Arm**. Armed, a detonator, redstone or its timer sets one off. They also have an **impact fuze**: nothing holds a warhead up, so with its support gone it falls, and an armed one that lands, or is hit by an arrow, goes off.
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
Its casing splits and scatters **24 bomblets** up to about 18 blocks around, each with a small blast of its own. One in twenty is a dud that lies where it fell as a live landmine.
"""),
    spot("bunker_buster_warhead", """
A slim penetrator. Placed and set off, a 9-block crater where it stands; delivered by a missile, it bores up to 40 blocks down first (obsidian-hard blocks cost more) and goes off at the bottom.
"""),
    spot("napalm_warhead", """
A small burst and a pool of **napalm** about 10 blocks round. Napalm burns for about 30 seconds whatever the weather, sets anything in it alight, and lights what burns beside it.
"""),
    spot("massive_warhead", """
The biggest conventional warhead: a 28-block crater and deadly out to 40. Stand well clear.
"""),
    craft("he_warhead"),
    craft("heavy_warhead"),
    craft("thermobaric_warhead"),
    craft("cluster_warhead"),
    craft("bunker_buster_warhead"),
    craft("napalm_warhead"),
    craft("massive_warhead"),
])

entry("ordnance/launch_site", "Launch site", "launch_silo", ["launch_silo", "launch_computer", "basic_missile", "launch_pad",
                                                              "gantry_frame", "gantry_arm", "radar_mast", "radar_dish", "antenna"], [
    text("""
A **Launch Silo** puts a [warhead](ordnance/warheads) on top of a missile and sends it to any point up to 1024 blocks away. A **Launch Computer** aims it: its screen is a map of the ground round it, with the silos it controls and every missile in flight.

The silo needs a **Basic Missile** and any warhead. Each launch takes the missile, the warhead and 100,000 FE.
"""),
    structure("launch_silo", "A 3 by 3 launch pad, the controller in the middle of its front edge and the Energy Port in the middle of its back edge. The gantry tower stands beside the pad's middle row, on your left as you face the controller: 9 high, with the Gantry Arm reaching in at the seventh block up. The column above the middle of the pad must be clear: that is where the missile stands."),
    structure("launch_computer", "The controller and its Energy Port side by side, with the radar mast behind the controller: three Radar Masts with the Radar Dish on top, and the Antenna on the controller."),
    text("""
**The map.** The computer maps the ground within 1024 blocks of it as it gets loaded (by you, by missiles flying over) and keeps what it has seen; ground no one has been near is blank. Drag the map to look round, scroll to zoom in and out, and press **Centre** to come back. Under the map are the coordinates of the point under the cursor.

**Launching.** Load the silo with a missile and a warhead (right-click its controller) and power it. Then open the Launch Computer: it needs a little power of its own, and finds every silo within 256 blocks. Each silo has its own target: pick one in the list, then click on the map to aim it there, or type the X and Z in and press **Set**. **ARM** arms the picked silo (it must be ready, with a target in range); **DISARM** makes it safe. **LAUNCH** fires every armed silo at once, each at its own target. A silo that stops being ready makes itself safe.

The silo sounds its siren and counts down for 10 seconds while the gantry arm swings clear. The missile burns on the pad for 2 seconds, then climbs, arcs over and dives on the target from high above, screaming down for its last few seconds. The further the target, the higher the arc: a missile sent 1000 blocks arrives about 20 seconds after lift-off. It flies on whether or not anyone is near the ground below, loading the chunks ahead of it, so the target need not be loaded.

Whatever it hits on the way sets off the warhead there, so keep the sky above the silo clear.
"""),
    spot("basic_missile", """
A solid-fuel missile with no guidance beyond its launch: it flies the arc it was given. Its fuel goes in the [Chemical Plant](processing/chemical_plant) as solid propellant.
"""),
    craft("launch_silo"),
    craft("launch_computer"),
    craft("basic_missile"),
    craft("launch_pad"),
    craft("gantry_frame"),
    craft("gantry_arm"),
    craft("radar_mast"),
    craft("radar_dish"),
    craft("antenna"),
])

entry("ordnance/radar_array", "Radar Array", "radar_array", ["radar_array"], [
    text("""
A **Radar Array** watches the sky for 2048 blocks round it and tracks every missile in the air, yours or anyone's. Its screen is a sweeping radar scope: a missile shows as a blip where the sweep last passed it. One that will come down within 512 blocks of the array (inside the dashed red ring) is **incoming**: red on the scope, flagged on the list, with how long until it lands and where.

A comparator by the controller reads how many missiles are incoming (up to 15): wire it to alarms or doors. It runs on 100 FE/t. Anti-missile defences to shoot them down are still to come, and will take their tracks from it.
"""),
    structure("radar_array", "A 3 by 3 deck of Machine Casing, the controller in the middle of its front edge and the Energy Port in the middle of its back edge. Three Radar Masts stand up from the middle of the deck, with a 3 by 3 crown of Radar Dishes on top: formed, they become one big turning dish."),
    craft("radar_array"),
])

entry("ordnance/chemical", "Chemical grenades", "gas_grenade", ["gas_grenade", "toxin_grenade", "chlorine_canister",
                                                                "toxin_canister", "salt", "chlorine_bucket", "toxin_bucket"], [
    text("""
Grenades that poison rather than blow up. Both are made in the [Chemical Plant](processing/chemical_plant) and both are stopped by a [hazmat suit](safety/hazmat_suits): each piece keeps out its share, and a full suit of either kind keeps out all of it.
"""),
    spot("gas_grenade", """
A smoke grenade filled with **chlorine**. After 2 seconds it pours out a low yellow-green cloud for 25 seconds. Everything breathing it gets **Poison III** and takes half a heart every half second; the undead are not poisoned, but the gas still burns them. Like smoke, it hides whoever is in it.
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
chapter("safety", "Safety", "decontamination_shower", """
Hazards the mod adds, and how to deal with them.
""")

entry("safety/decontamination_shower", "Decontamination Shower", "decontamination_shower", ["decontamination_shower"], [
    spot("decontamination_shower", """
A walk-in booth: pipe water in, power it, step inside. It sprays whoever stands in it (20 FE and 10 mB of water a tick), putting out fire and washing off poison, wither, nausea and radiation sickness.
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

**Press SCRAM** (or push every rod in) and it cools down. Leave it, and at **1,400 C** it melts down: the reactor and its fuel are destroyed in a dirty blast, molten corium pools in the crater and is thrown round it, and the land round it is contaminated for a day.
"""),
    table([
        ["Critical from", "900 C"],
        ["Meltdown at", "1,400 C"],
        ["Warning time", "15 to 45 s"],
        ["  with coolant", "the longer"],
        ["Contaminated", "48 blocks, 1 day"],
    ], "Meltdowns"),
    spot("corium", """
Molten core. It burns anything that stands on it and sets fire to what is next to it, and cools into cooled corium over about ten minutes. Both are radioactive; mine them with an iron pickaxe or better.
"""),
    text("""
**Contamination.** Anything alive in the contaminated land falls ill with **Radiation Sickness**, worse the nearer the crater: hunger, slowness and damage that ordinary armour does not stop, from half a heart every two seconds at level I to four hearts a second at level IV. A [hazmat suit](safety/hazmat_suits) stops most of it. It wears off a few seconds after you leave, and the [Decontamination Shower](safety/decontamination_shower) washes it off at once. The undead are immune.
"""),
    spot("nuclear_waste", """
What reprocessing leaves behind. It does not burn, not even in lava; store it somewhere safe. A dropped stack still disappears in time like any other item.
"""),
    links(["safety/hazmat_suits", "safety/decontamination_shower"]),
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
In the contaminated land round a [meltdown](safety/reactor_safety), a full Hazmat Suit keeps Radiation Sickness to its second level even at the crater, and the lead-lined suit to the first. Wash it off afterwards in a [Decontamination Shower](safety/decontamination_shower).

The hood needs plastic sheet, a glass pane for the visor and charcoal for the filter; the lead-lined suit is a Hazmat Suit piece with four lead plates round it.
"""),
    craft("hazmat_hood"),
    craft("hazmat_coat"),
    craft("hazmat_trousers"),
    craft("hazmat_boots"),
    craft("lead_lined_hazmat_coat"),
    links(["safety/reactor_safety", "safety/decontamination_shower"]),
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
