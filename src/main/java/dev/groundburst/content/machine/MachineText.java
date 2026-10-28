package dev.groundburst.content.machine;

import dev.groundburst.GroundBurst;
import dev.groundburst.registry.GBLang;

/**
 * Translation keys shared by the machine GUIs. Kept out of the client screen class so the keys are registered with
 * {@link GBLang} on both sides (datagen loads this through {@link GBMachines#init()}).
 */
public final class MachineText {
    public static final String ENERGY = GBLang.add(GroundBurst.MODID + ".gui.energy", "Energy: %s / %s FE");
    public static final String PROGRESS = GBLang.add(GroundBurst.MODID + ".gui.progress", "Progress: %s%%");
    public static final String BURN = GBLang.add(GroundBurst.MODID + ".gui.burn", "Burning: %s s left");
    public static final String OUTPUT = GBLang.add(GroundBurst.MODID + ".gui.output", "OUT %s FE/t");
    public static final String STORED = GBLang.add(GroundBurst.MODID + ".gui.stored", "BUF %s%%");
    public static final String REDSTONE = GBLang.add(GroundBurst.MODID + ".gui.redstone", "Redstone: %s");

    public static final String TANK = GBLang.add(GroundBurst.MODID + ".gui.tank", "%s: %s / %s mB");
    public static final String TANK_INPUT = GBLang.add(GroundBurst.MODID + ".gui.tank_input", "Input tank: fill with pipes, buckets or canisters");
    public static final String TANK_OUTPUT = GBLang.add(GroundBurst.MODID + ".gui.tank_output", "Product tank: drain with pipes, buckets or canisters");
    public static final String DUMP = GBLang.add(GroundBurst.MODID + ".gui.dump", "Dump tank");
    public static final String DUMP_HINT = GBLang.add(GroundBurst.MODID + ".gui.dump_hint", "Shift-click to empty it (the fluid is lost)");
    public static final String TANK_EMPTY = GBLang.add(GroundBurst.MODID + ".gui.tank_empty", "Empty: 0 / %s mB");

    // Side configuration tab
    public static final String SIDES_TITLE = GBLang.add(GroundBurst.MODID + ".gui.sides", "Configuration");
    public static final String SIDES_TAB = GBLang.add(GroundBurst.MODID + ".gui.sides.tab", "Side configuration");
    public static final String CHANNEL_ITEMS = GBLang.add(GroundBurst.MODID + ".gui.sides.items", "Items");
    public static final String CHANNEL_FUEL = GBLang.add(GroundBurst.MODID + ".gui.sides.fuel", "Fuel");
    public static final String CHANNEL_ENERGY = GBLang.add(GroundBurst.MODID + ".gui.sides.energy", "Energy");
    public static final String CHANNEL_FLUID = GBLang.add(GroundBurst.MODID + ".gui.sides.fluid", "Fluids");
    public static final String FLUID_ON = GBLang.add(GroundBurst.MODID + ".gui.sides.fluid_on", "Pipes can fill the tank");
    public static final String FLUID_OFF = GBLang.add(GroundBurst.MODID + ".gui.sides.fluid_off", "Fluid disconnected");
    public static final String ENERGY_ON = GBLang.add(GroundBurst.MODID + ".gui.sides.energy_on", "Energy connected");
    public static final String ENERGY_OFF = GBLang.add(GroundBurst.MODID + ".gui.sides.energy_off", "Energy disconnected");
    public static final String AUTO_EJECT = GBLang.add(GroundBurst.MODID + ".gui.sides.auto_eject", "Auto-eject");
    public static final String AUTO_EJECT_TIP = GBLang.add(GroundBurst.MODID + ".gui.sides.auto_eject.tip", "Push finished products out of every Output face");
    public static final String HINT_CYCLE = GBLang.add(GroundBurst.MODID + ".gui.sides.hint_cycle", "Left-click: next, right-click: previous");
    public static final String HINT_TOGGLE = GBLang.add(GroundBurst.MODID + ".gui.sides.hint_toggle", "Click to toggle");
    public static final String FACE_COVERED = GBLang.add(GroundBurst.MODID + ".gui.sides.covered", "%s: inside the structure (nothing can connect)");
    public static final String FACE_MODE = GBLang.add(GroundBurst.MODID + ".gui.sides.face", "%s: %s");

    // Recipe viewer (JEI) texts
    public static final String RECIPE_TIME_ENERGY = GBLang.add(GroundBurst.MODID + ".recipe.time_energy", "%s s · %s FE/t");
    public static final String RECIPE_TOTAL_ENERGY = GBLang.add(GroundBurst.MODID + ".recipe.total_energy", "%s FE in total");
    public static final String RECIPE_CHANCE = GBLang.add(GroundBurst.MODID + ".recipe.chance", "%s%% chance");
    public static final String RECIPE_NOT_CONSUMED = GBLang.add(GroundBurst.MODID + ".recipe.not_consumed", "Die: not consumed");
    public static final String INFO_DIE = GBLang.add(GroundBurst.MODID + ".info.die",
            "Goes in the Industrial Press die slot and picks what the press makes. Dies are never used up.");
    public static final String INFO_MEMBRANE = GBLang.add(GroundBurst.MODID + ".info.membrane",
            "Goes in the Cryogenic Condenser's membrane slot and picks which gas it liquefies. Membranes are never used up.");
    public static final String INFO_CHEMICAL_PLANT = GBLang.add(GroundBurst.MODID + ".info.chemical_plant",
            "Chemical Plant (2x2x2): put the controller down, then add an Energy Port beside it, a Fluid Port behind it, an Item Port in the far corner of that bottom layer, and four Reactor Glass on top. Right-click the controller, or hold a Holo-Projector, to see where each block goes (sneak-right-click opens it before it is finished). The controller itself connects to nothing: power goes into the Energy Port, items in and out through the Item Port, and fluids in and out through the Fluid Port (a Water Pump supplies water). Right-click with a bucket or canister to pour it in, sneak-right-click with an empty one to draw off the product.");
    public static final String INFO_FLUID_PIPE = GBLang.add(GroundBurst.MODID + ".info.fluid_pipe",
            "Fluid pipes carry what machines push out of faces with auto-eject and fluids switched on, and what pipe sides set to Pull (with the Wrench) draw out of tanks and machines, sharing it between every tank on the run's normal sides. Pipes hold nothing themselves, so breaking one never spills. A run carries as much as its weakest pipe: copper 150, steel 600, pressurized 2,400 mB per tick.");
    public static final String INFO_ITEM_PIPE = GBLang.add(GroundBurst.MODID + ".info.item_pipe",
            "Item pipes carry items between inventories. A pipe side set to Pull (with the Wrench) draws items out of the chest or machine it touches (a machine only gives from its outputs); machines with auto-eject, and hoppers, can push in too. The run hands everything straight to the inventories on its other sides, shared in turn, never back where it came from. Pipes hold nothing, so breaking one never drops items. A run moves as many items as its weakest pipe: iron 8, steel 32, pneumatic 128 items a second (a hopper moves 2.5).");
    public static final String INFO_WATER_PUMP = GBLang.add(GroundBurst.MODID + ".info.water_pump",
            "Place on a water source block and power it: it draws 50 mB of water a tick (the source is never used up) and pushes it out of its two side outlets into pipes, tanks and machines.");
    public static final String INFO_FLUID_TANK = GBLang.add(GroundBurst.MODID + ".info.fluid_tank",
            "Each Fluid Tank holds 16,000 mB. Touching tanks join into one when together they fill a box with a square base up to 3x3 and up to 5 high (720,000 mB). Fluid only moves in and out by pipe: pipes fill it from any side, and a pipe set to pull (with the Wrench) empties it.");
    public static final String INFO_CREATIVE_FLUID_TANK = GBLang.add(GroundBurst.MODID + ".info.creative_fluid_tank",
            "Creative only. Pour a bucket or canister of any fluid in and the tank holds an endless supply of it: buckets, canisters and pipes can draw from it forever. Pouring in a different fluid switches it over; sneak-right-click with an empty hand to clear it.");
    public static final String INFO_WRENCH = GBLang.add(GroundBurst.MODID + ".info.wrench",
            "Right-click a side of a fluid pipe to cycle it: Normal (fluid travels both ways), Pull (draws fluid out of the tank or machine it touches; shown by a striped collar), Disconnected. Right-click a cable side to connect or disconnect it. Right-click a machine to turn it. Sneak-right-click a pipe, cable, machine, tank or structure block to pick it up.");
    public static final String INFO_FLUIDS = GBLang.add(GroundBurst.MODID + ".info.fluids",
            "Chemical fluids live in machine tanks, pipes, canisters and (liquids only) buckets. Spilled acids burn, napalm ignites and liquid oxygen or nitrogen freeze anything standing in them.");
    public static final String CASCADE = GBLang.add(GroundBurst.MODID + ".gui.cascade", "CASCADE %s/%s");
    public static final String CASCADE_SPEED = GBLang.add(GroundBurst.MODID + ".gui.cascade_speed", "SPEED +%s%%");
    public static final String INFO_GAS_CENTRIFUGE = GBLang.add(GroundBurst.MODID + ".info.gas_centrifuge",
            "Pipe uranium hexafluoride gas from the Chemical Plant into the Gas Centrifuge to get enriched uranium nuggets and depleted uranium dust. Cascade: centrifuges joined flange to flange (the pipe flanges on their sides meeting, all facing the same way or the opposite way) run 15% faster for every other centrifuge in the line, counting up to 8 (+105%). The screen shows the cascade size.");
    public static final String INFO_CIRCUIT_FABRICATOR = GBLang.add(GroundBurst.MODID + ".info.circuit_fabricator",
            "Makes basic, advanced, military and quantum circuits, sensor arrays and superconducting coils. Coils need liquid nitrogen: pipe it into the coolant tank from a Cryogenic Condenser, or pour in a canister.");
    public static final String INFO_FUSION_FUEL_SYNTHESIZER = GBLang.add(GroundBurst.MODID + ".info.fusion_fuel_synthesizer",
            "Presses lithium-6 dust with deuterium gas (piped in from an Electrolytic Separator) into lithium deuteride pellets, the fuel of thermonuclear weapons.");
    public static final String INFO_EXOTIC_MATTER_FABRICATOR = GBLang.add(GroundBurst.MODID + ".info.exotic_matter_fabricator",
            "Holds exotic matter in a spinning magnetic cage to make stabilised exotic matter, containment cells and singularity seeds. It draws 400 FE a tick, more than copper cable carries: feed it with insulated or high-voltage cable.");
    public static final String INFO_CANNING_MACHINE = GBLang.add(GroundBurst.MODID + ".info.canning_machine",
            "Fills canisters and empties them again. Put an empty canister (liquids) or gas canister (gases) in and it takes 1,000 mB from the supply tank (left) to fill it; put a filled canister in and its 1,000 mB is poured into the catch tank (right). Pipes and buckets fill the supply tank; pipes, buckets and auto-eject drain the catch tank.");
    public static final String COOLANT_HINT = GBLang.add(GroundBurst.MODID + ".gui.coolant_hint",
            "Coolant: water, or heavy water (the core runs cooler and it lasts five times longer)");
    public static final String DECAY_LEFT = GBLang.add(GroundBurst.MODID + ".gui.decay_left", "Decaying: %s min left");
    public static final String DECAY_NONE = GBLang.add(GroundBurst.MODID + ".gui.decay_none", "No slug loaded");
    public static final String ENGINE_ON = GBLang.add(GroundBurst.MODID + ".gui.engine_on", "Engine running: %s mB of fuel left");
    public static final String ENGINE_OFF = GBLang.add(GroundBurst.MODID + ".gui.engine_off", "Engine stopped: %s mB of fuel left");
    public static final String FUEL_LEFT = GBLang.add(GroundBurst.MODID + ".gui.fuel_left", "FUEL %s min");
    public static final String INFO_DIESEL_GENERATOR = GBLang.add(GroundBurst.MODID + ".info.diesel_generator",
            "Burns liquid fuel piped into its tank (or poured in from a canister) for 180 FE a tick: synthetic fuel gives 90 FE per mB, liquid rocket fuel 140; other mods' diesel works too. It pushes power out of its energy faces and pauses when its buffer is full.");
    public static final String INFO_RTG = GBLang.add(GroundBurst.MODID + ".info.rtg",
            "A steady 40 FE a tick from a radioisotope slug. An enriched uranium ingot lasts two in-game days (plutonium, from the Fission Reactor, will last ten). The slug decays whether the power is used or not, so wire it to something that always draws.");
    public static final String INFO_CAPACITOR_BANK = GBLang.add(GroundBurst.MODID + ".info.capacitor_bank",
            "Stores power: 100,000 / 1,000,000 / 10,000,000 FE, moving 1,024 / 8,192 / 32,768 FE a tick. Every face takes power in except the front, which pushes it out (turn it with the Wrench). It keeps its charge when picked up, and a comparator reads how full it is. The Creative Capacitor Bank (creative menu only) is always full and pushes up to 1,000,000 FE a tick.");
    public static final String INFO_BATTERY_CHARGER = GBLang.add(GroundBurst.MODID + ".info.battery_charger",
            "Charges a battery (or any other mod's chargeable item) at up to 512 FE a tick from the power its cables bring, then moves it to the output slot.");
    public static final String INFO_DECONTAMINATION_SHOWER = GBLang.add(GroundBurst.MODID + ".info.decontamination_shower",
            "A walk-in booth: pipe water in and power it, then step inside. It sprays whoever stands in it (20 FE and 10 mB of water a tick), putting out fire and washing off poison, wither and nausea. Radiation contamination will wash off too once radiation exists.");
    public static final String INFO_BATTERY = GBLang.add(GroundBurst.MODID + ".info.battery",
            "Holds 50,000 FE. Charge it in the Battery Charger; later gear (Geiger counter, guns) runs on it.");
    // CRT readout lines (strings only reach the language file from classes datagen loads, so they live here).
    public static final String CORE = GBLang.add(GroundBurst.MODID + ".gui.core", "CORE %s C");
    public static final String NET = GBLang.add(GroundBurst.MODID + ".gui.net", "NET %s FE/t");
    public static final String SCRAM_MESSAGE = GBLang.add(GroundBurst.MODID + ".reactor.scram", "Reactor SCRAM: the core overheated. Check the coolant.");
    public static final String CRITICAL_MESSAGE = GBLang.add(GroundBurst.MODID + ".reactor.critical",
            "REACTOR CORE CRITICAL: %s °C. SCRAM NOW or it melts down at %s °C.");
    public static final String HEAT_TIP = GBLang.add(GroundBurst.MODID + ".gui.heat", "Core temperature: %s °C (it SCRAMs at 900)");
    public static final String HEAT_TIP_MELTDOWN = GBLang.add(GroundBurst.MODID + ".gui.heat_meltdown",
            "Core temperature: %s °C (critical from 900: SCRAM, or it melts down at 1,400)");
    public static final String RODS_TIP = GBLang.add(GroundBurst.MODID + ".gui.rods_gauge", "Control rods: %s%% in");
    public static final String WITHDRAW_TIP = GBLang.add(GroundBurst.MODID + ".gui.withdraw", "Withdraw the control rods 10% (more power, more heat)");
    public static final String INSERT_TIP = GBLang.add(GroundBurst.MODID + ".gui.insert", "Insert the control rods 10%");
    public static final String SCRAM_TIP = GBLang.add(GroundBurst.MODID + ".gui.scram", "SCRAM: drop every control rod in at once");
    public static final String INFO_FISSION_REACTOR = GBLang.add(GroundBurst.MODID + ".info.fission_reactor",
            "Fission Reactor (any size from 3x3x3 to 6x6x6): a hollow box with Lead-Lined Casing on its corners and edges, and casing or Reactor Glass on its faces, with exactly one Item Port, one Fluid Port and one Energy Port among them; the controller goes in a wall or the bottom edge of its wall. Inside it, every Fuel Channel holds one uranium fuel rod, wherever it stands (up to 64 in a 4x4x4 core); the rest of the core is left empty for the coolant. Right-click the controller or hold a Holo-Projector to see its plan as ghost blocks: the size set with the PLAN buttons on its screen (sneak-right-click to open it). Formed, its walls become one vessel with a control-rod drive over every column of channels. The controller connects to nothing: pipe coolant into the Fluid Port, feed rods through the Item Port and take power from the Energy Port. Every loaded rod gives 75 FE a tick with the control rods out (4,800 from sixty-four) and they all burn at once, each lasting about 2 hours 40 minutes at full power, a little more or less. Heat goes by how full the core is: with water coolant, past about 70% rods out it climbs past 900 C and goes critical (SCRAM it, or it melts down at 1,400 C); heavy water (Chemical Plant: deuterium and liquid oxygen) keeps it below that even at full power and lasts five times longer. Without coolant the core heats up fast; water left between the channels makes it heat and cool more slowly. Target rods in the breeding slots come out irradiated: DU rods for plutonium, lithium-6 rods for tritium (reprocess them in the Chemical Plant).");
    // Slice 8: Fusion and Singularity Reactors.
    public static final String PLASMA_LIT = GBLang.add(GroundBurst.MODID + ".gui.plasma_lit", "PLASMA LIT");
    public static final String IGNITION = GBLang.add(GroundBurst.MODID + ".gui.ignition_readout", "IGNITION %s%%");
    public static final String IGNITION_TIP = GBLang.add(GroundBurst.MODID + ".gui.ignition",
            "Ignition charge: %s%% (fed in through the Energy Port; the plasma lights at 100%%)");
    public static final String PLASMA_LOST = GBLang.add(GroundBurst.MODID + ".reactor.plasma_lost", "Fusion Reactor: the plasma went out.");
    public static final String FEED = GBLang.add(GroundBurst.MODID + ".gui.feed_readout", "EATS %s ITEMS/S");
    public static final String CAGE_CHARGE = GBLang.add(GroundBurst.MODID + ".gui.cage_charge", "CAGE %s%%");
    public static final String MASS_TIP = GBLang.add(GroundBurst.MODID + ".gui.mass", "Matter waiting to be turned into power: %s%% of 50 items' worth");
    public static final String COLLAPSE_MESSAGE = GBLang.add(GroundBurst.MODID + ".reactor.collapse",
            "The singularity collapsed: its containment cage lost power. The seed is gone.");
    public static final String INFO_FUSION_REACTOR = GBLang.add(GroundBurst.MODID + ".info.fusion_reactor",
            "Fusion Reactor (9x9x3 octagonal ring, the Particle Accelerator's footprint): a base ring of machine casing with the controller at the front centre, a Fluid Port in the middle of the right side and an Energy Port at the back; a ring of toroidal field coils above it with reactor glass viewports in the middle of each side; a ring of vacuum vessel casing on top; and a central solenoid column three blocks tall in the middle. Pipe deuterium, tritium and liquid nitrogen into the Fluid Port. To light the plasma it needs an ignition charge of 2,000,000 FE through its Energy Port (a big capacitor bank); once lit it makes 6,000 FE a tick and pushes it out of the same port. Liquid nitrogen keeps its magnets cold (2 mB a tick): if the nitrogen or the fuel runs out the plasma goes out and must be lit again.");
    public static final String INFO_SINGULARITY_REACTOR = GBLang.add(GroundBurst.MODID + ".info.singularity_reactor",
            "Singularity Reactor (5x5x5): a machine casing floor and roof, steel plating posts at the corners, reactor glass walls, a containment emitter in the middle of each wall and of the roof, and a core cradle floating in the centre of the empty chamber. The controller is in the middle of the front of the floor, an Item Port on the left and an Energy Port on the right; any other floor block can be swapped for an extra Item Port to feed it faster. Put a singularity seed in the controller (it stays there, like a battery in the charger) and charge the cage with 500,000 FE through the Energy Port: the cage engages and holds the seed. Then it eats the items in its feed slots, any item at all. Every item is worth 10,000 FE and it eats at most 20 items a second, so its output is set by how fast you feed it: 10 items a second gives 5,000 FE a tick, 20 a second gives the full 10,000. Holding the cage costs 1,000 FE a tick, the worth of 2 items a second, and it keeps a 500,000 FE reserve for that. Take the seed out to shut it down safely. If the cage runs out of power, or the structure is broken while it holds the seed, the singularity collapses and the seed is destroyed.");
    public static final String INFO_PARTICLE_ACCELERATOR = GBLang.add(GroundBurst.MODID + ".info.particle_accelerator",
            "Particle Accelerator (9x9 octagonal ring, two layers): an octagon one block wide, three blocks straight on each side with a five-block staircase at each corner. Bottom layer Steel Plating; top layer Accelerator Magnet Segments with the controller at the front centre, the Injector beside it, the Target Chamber at the back centre, an Energy Port in the middle of one side and an Item Port in the middle of the other. Right-click the controller or hold a Holo-Projector to see where each block goes. It fills a containment cell with antimatter at 2,000 FE a tick. The controller itself connects to nothing: feed power into the Energy Port (insulated cable carries 1,024 FE a tick per run) and containment cells into the Item Port, which also gives out the antimatter cells.");
    public static final String INFO_SIDES = GBLang.add(GroundBurst.MODID + ".info.sides",
            "Every machine has a side-configuration tab (the wrench on the right of its screen): choose which faces accept items, give out products and connect to power, and switch on auto-eject. Multiblock structures have no tab: they connect only through their Item, Fluid and Energy Ports.");

    private MachineText() {}

    /** Loads this class and the enums whose constants register their own keys. */
    static void init() {
        MachineStatus.values();
        RedstoneMode.values();
        SideConfig.Face.values();
        SideConfig.ItemMode.values();
    }
}
