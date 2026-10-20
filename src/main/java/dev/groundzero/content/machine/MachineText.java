package dev.groundzero.content.machine;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;

/**
 * Translation keys shared by the machine GUIs. Kept out of the client screen class so the keys are registered with
 * {@link GZLang} on both sides (datagen loads this through {@link GZMachines#init()}).
 */
public final class MachineText {
    public static final String ENERGY = GZLang.add(GroundZero.MODID + ".gui.energy", "Energy: %s / %s FE");
    public static final String PROGRESS = GZLang.add(GroundZero.MODID + ".gui.progress", "Progress: %s%%");
    public static final String BURN = GZLang.add(GroundZero.MODID + ".gui.burn", "Burning: %s s left");
    public static final String OUTPUT = GZLang.add(GroundZero.MODID + ".gui.output", "OUT %s FE/t");
    public static final String STORED = GZLang.add(GroundZero.MODID + ".gui.stored", "BUF %s%%");
    public static final String REDSTONE = GZLang.add(GroundZero.MODID + ".gui.redstone", "Redstone: %s");

    public static final String TANK = GZLang.add(GroundZero.MODID + ".gui.tank", "%s: %s / %s mB");
    public static final String TANK_INPUT = GZLang.add(GroundZero.MODID + ".gui.tank_input", "Input tank: fill with pipes, buckets or canisters");
    public static final String TANK_OUTPUT = GZLang.add(GroundZero.MODID + ".gui.tank_output", "Product tank: drain with pipes, buckets or canisters");
    public static final String DUMP = GZLang.add(GroundZero.MODID + ".gui.dump", "Dump tank");
    public static final String DUMP_HINT = GZLang.add(GroundZero.MODID + ".gui.dump_hint", "Shift-click to empty it (the fluid is lost)");
    public static final String TANK_EMPTY = GZLang.add(GroundZero.MODID + ".gui.tank_empty", "Empty: 0 / %s mB");

    // Side configuration tab
    public static final String SIDES_TITLE = GZLang.add(GroundZero.MODID + ".gui.sides", "Configuration");
    public static final String SIDES_TAB = GZLang.add(GroundZero.MODID + ".gui.sides.tab", "Side configuration");
    public static final String CHANNEL_ITEMS = GZLang.add(GroundZero.MODID + ".gui.sides.items", "Items");
    public static final String CHANNEL_FUEL = GZLang.add(GroundZero.MODID + ".gui.sides.fuel", "Fuel");
    public static final String CHANNEL_ENERGY = GZLang.add(GroundZero.MODID + ".gui.sides.energy", "Energy");
    public static final String CHANNEL_FLUID = GZLang.add(GroundZero.MODID + ".gui.sides.fluid", "Fluids");
    public static final String FLUID_ON = GZLang.add(GroundZero.MODID + ".gui.sides.fluid_on", "Pipes can fill the tank");
    public static final String FLUID_OFF = GZLang.add(GroundZero.MODID + ".gui.sides.fluid_off", "Fluid disconnected");
    public static final String ENERGY_ON = GZLang.add(GroundZero.MODID + ".gui.sides.energy_on", "Energy connected");
    public static final String ENERGY_OFF = GZLang.add(GroundZero.MODID + ".gui.sides.energy_off", "Energy disconnected");
    public static final String AUTO_EJECT = GZLang.add(GroundZero.MODID + ".gui.sides.auto_eject", "Auto-eject");
    public static final String AUTO_EJECT_TIP = GZLang.add(GroundZero.MODID + ".gui.sides.auto_eject.tip", "Push finished products out of every Output face");
    public static final String HINT_CYCLE = GZLang.add(GroundZero.MODID + ".gui.sides.hint_cycle", "Left-click: next, right-click: previous");
    public static final String HINT_TOGGLE = GZLang.add(GroundZero.MODID + ".gui.sides.hint_toggle", "Click to toggle");
    public static final String FACE_COVERED = GZLang.add(GroundZero.MODID + ".gui.sides.covered", "%s: inside the structure (nothing can connect)");
    public static final String FACE_MODE = GZLang.add(GroundZero.MODID + ".gui.sides.face", "%s: %s");

    // Recipe viewer (JEI) texts
    public static final String RECIPE_TIME_ENERGY = GZLang.add(GroundZero.MODID + ".recipe.time_energy", "%s s · %s FE/t");
    public static final String RECIPE_TOTAL_ENERGY = GZLang.add(GroundZero.MODID + ".recipe.total_energy", "%s FE in total");
    public static final String RECIPE_CHANCE = GZLang.add(GroundZero.MODID + ".recipe.chance", "%s%% chance");
    public static final String RECIPE_NOT_CONSUMED = GZLang.add(GroundZero.MODID + ".recipe.not_consumed", "Die: not consumed");
    public static final String INFO_DIE = GZLang.add(GroundZero.MODID + ".info.die",
            "Goes in the Industrial Press die slot and picks what the press makes. Dies are never used up.");
    public static final String INFO_MEMBRANE = GZLang.add(GroundZero.MODID + ".info.membrane",
            "Goes in the Cryogenic Condenser's membrane slot and picks which gas it liquefies. Membranes are never used up.");
    public static final String INFO_CHEMICAL_PLANT = GZLang.add(GroundZero.MODID + ".info.chemical_plant",
            "Chemical Plant (2x2x2): put the controller down, then add Machine Casing beside it, a Fluid Port behind it, an Item Port in the far corner of that bottom layer, and four Reactor Glass on top. Right-click the controller, or hold a Holo-Projector, to see where each block goes (sneak-right-click opens it before it is finished). Feed items through the Item Port and pipe fluids in and out through the Fluid Port (a Water Pump supplies water); the controller's open faces take both too. Right-click with a bucket or canister to pour it in, sneak-right-click with an empty one to draw off the product.");
    public static final String INFO_FLUID_PIPE = GZLang.add(GroundZero.MODID + ".info.fluid_pipe",
            "Fluid pipes carry what machines push out of faces with auto-eject and fluids switched on, and what pipe sides set to Pull (with the Wrench) draw out of tanks and machines, sharing it between every tank on the run's normal sides. Pipes hold nothing themselves, so breaking one never spills. A run carries as much as its weakest pipe: copper 150, steel 600, pressurized 2,400 mB per tick.");
    public static final String INFO_WATER_PUMP = GZLang.add(GroundZero.MODID + ".info.water_pump",
            "Place on a water source block and power it: it draws 50 mB of water a tick (the source is never used up) and pushes it out of its two side outlets into pipes, tanks and machines.");
    public static final String INFO_FLUID_TANK = GZLang.add(GroundZero.MODID + ".info.fluid_tank",
            "Each Fluid Tank holds 16,000 mB. Touching tanks join into one when together they fill a box with a square base up to 3x3 and up to 5 high (720,000 mB). Fluid only moves in and out by pipe: pipes fill it from any side, and a pipe set to pull (with the Wrench) empties it.");
    public static final String INFO_WRENCH = GZLang.add(GroundZero.MODID + ".info.wrench",
            "Right-click a side of a fluid pipe to cycle it: Normal (fluid travels both ways), Pull (draws fluid out of the tank or machine it touches; shown by a striped collar), Disconnected. Right-click a cable side to connect or disconnect it. Right-click a machine to turn it. Sneak-right-click a pipe, cable, machine, tank or structure block to pick it up.");
    public static final String INFO_FLUIDS = GZLang.add(GroundZero.MODID + ".info.fluids",
            "Chemical fluids live in machine tanks, pipes, canisters and (liquids only) buckets. Spilled acids burn, napalm ignites and liquid oxygen or nitrogen freeze anything standing in them.");
    public static final String CASCADE = GZLang.add(GroundZero.MODID + ".gui.cascade", "CASCADE %s/%s");
    public static final String CASCADE_SPEED = GZLang.add(GroundZero.MODID + ".gui.cascade_speed", "SPEED +%s%%");
    public static final String INFO_GAS_CENTRIFUGE = GZLang.add(GroundZero.MODID + ".info.gas_centrifuge",
            "Pipe uranium hexafluoride gas from the Chemical Plant into the Gas Centrifuge to get enriched uranium nuggets and depleted uranium dust. Cascade: centrifuges joined flange to flange (the pipe flanges on their sides meeting, all facing the same way or the opposite way) run 15% faster for every other centrifuge in the line, counting up to 8 (+105%). The screen shows the cascade size.");
    public static final String INFO_CIRCUIT_FABRICATOR = GZLang.add(GroundZero.MODID + ".info.circuit_fabricator",
            "Makes basic, advanced, military and quantum circuits, sensor arrays and superconducting coils. Coils need liquid nitrogen: pipe it into the coolant tank from a Cryogenic Condenser, or pour in a canister.");
    public static final String INFO_FUSION_FUEL_SYNTHESIZER = GZLang.add(GroundZero.MODID + ".info.fusion_fuel_synthesizer",
            "Presses lithium-6 dust with deuterium gas (piped in from an Electrolytic Separator) into lithium deuteride pellets, the fuel of thermonuclear weapons.");
    public static final String INFO_EXOTIC_MATTER_FABRICATOR = GZLang.add(GroundZero.MODID + ".info.exotic_matter_fabricator",
            "Holds exotic matter in a spinning magnetic cage to make stabilised exotic matter, containment cells and singularity seeds. It draws 400 FE a tick, more than copper cable carries: feed it with insulated or high-voltage cable.");
    public static final String COOLANT_HINT = GZLang.add(GroundZero.MODID + ".gui.coolant_hint",
            "Coolant: water, or heavy water (the core runs cooler and it lasts five times longer)");
    public static final String DECAY_LEFT = GZLang.add(GroundZero.MODID + ".gui.decay_left", "Decaying: %s min left");
    public static final String DECAY_NONE = GZLang.add(GroundZero.MODID + ".gui.decay_none", "No slug loaded");
    public static final String ENGINE_ON = GZLang.add(GroundZero.MODID + ".gui.engine_on", "Engine running: %s mB of fuel left");
    public static final String ENGINE_OFF = GZLang.add(GroundZero.MODID + ".gui.engine_off", "Engine stopped: %s mB of fuel left");
    public static final String FUEL_LEFT = GZLang.add(GroundZero.MODID + ".gui.fuel_left", "FUEL %s min");
    public static final String INFO_DIESEL_GENERATOR = GZLang.add(GroundZero.MODID + ".info.diesel_generator",
            "Burns liquid fuel piped into its tank (or poured in from a canister) for 180 FE a tick: synthetic fuel gives 90 FE per mB, liquid rocket fuel 140; other mods' diesel works too. It pushes power out of its energy faces and pauses when its buffer is full.");
    public static final String INFO_RTG = GZLang.add(GroundZero.MODID + ".info.rtg",
            "A steady 40 FE a tick from a radioisotope slug. An enriched uranium ingot lasts two in-game days (plutonium, from the Fission Reactor, will last ten). The slug decays whether the power is used or not, so wire it to something that always draws.");
    public static final String INFO_CAPACITOR_BANK = GZLang.add(GroundZero.MODID + ".info.capacitor_bank",
            "Stores power: 100,000 / 1,000,000 / 10,000,000 FE, moving 1,024 / 8,192 / 32,768 FE a tick. Every face takes power in except the front, which pushes it out (turn it with the Wrench). It keeps its charge when picked up, and a comparator reads how full it is. The Creative Capacitor Bank (creative menu only) is always full and pushes up to 1,000,000 FE a tick.");
    public static final String INFO_BATTERY_CHARGER = GZLang.add(GroundZero.MODID + ".info.battery_charger",
            "Charges a battery (or any other mod's chargeable item) at up to 512 FE a tick from the power its cables bring, then moves it to the output slot.");
    public static final String INFO_DECONTAMINATION_SHOWER = GZLang.add(GroundZero.MODID + ".info.decontamination_shower",
            "A walk-in booth: pipe water in and power it, then step inside. It sprays whoever stands in it (20 FE and 10 mB of water a tick), putting out fire and washing off poison, wither and nausea. Radiation contamination will wash off too once radiation exists.");
    public static final String INFO_BATTERY = GZLang.add(GroundZero.MODID + ".info.battery",
            "Holds 50,000 FE. Charge it in the Battery Charger; later gear (Geiger counter, guns) runs on it.");
    // CRT readout lines (strings only reach the language file from classes datagen loads, so they live here).
    public static final String CORE = GZLang.add(GroundZero.MODID + ".gui.core", "CORE %s C");
    public static final String NET = GZLang.add(GroundZero.MODID + ".gui.net", "NET %s FE/t");
    public static final String SCRAM_MESSAGE = GZLang.add(GroundZero.MODID + ".reactor.scram", "Reactor SCRAM: the core overheated. Check the coolant.");
    public static final String HEAT_TIP = GZLang.add(GroundZero.MODID + ".gui.heat", "Core temperature: %s °C (it SCRAMs at 900)");
    public static final String RODS_TIP = GZLang.add(GroundZero.MODID + ".gui.rods_gauge", "Control rods: %s%% in");
    public static final String WITHDRAW_TIP = GZLang.add(GroundZero.MODID + ".gui.withdraw", "Withdraw the control rods 10% (more power, more heat)");
    public static final String INSERT_TIP = GZLang.add(GroundZero.MODID + ".gui.insert", "Insert the control rods 10%");
    public static final String SCRAM_TIP = GZLang.add(GroundZero.MODID + ".gui.scram", "SCRAM: drop every control rod in at once");
    public static final String INFO_FISSION_REACTOR = GZLang.add(GroundZero.MODID + ".info.fission_reactor",
            "Fission Reactor (3x3x4): lead-lined casing round an empty two-block core with reactor glass on all four sides; the controller at the front of the bottom layer and a Fluid Port under the core; on top an Item Port, an Energy Port in the middle and a second Item Port. Right-click the controller or hold a Holo-Projector to see where each block goes. The controller itself connects to nothing: pipe coolant into the Fluid Port, feed rods through the Item Ports and take power from the Energy Port. Load uranium fuel rods and withdraw the control rods: up to 1,200 FE a tick. More power means more heat: with water coolant the core runs hotter the further the rods are out and past about 70% it climbs to 900 C and SCRAMs; heavy water (Chemical Plant: deuterium and liquid oxygen) keeps it below that even at full power and lasts five times longer. Without coolant the core heats up fast. Target rods in the breeding slots come out irradiated: DU rods for plutonium, lithium-6 rods for tritium (reprocess them in the Chemical Plant).");
    public static final String INFO_PARTICLE_ACCELERATOR = GZLang.add(GroundZero.MODID + ".info.particle_accelerator",
            "Particle Accelerator (9x9x3 ring): a hollow square ring one block wide. Bottom layer Steel Plating, top layer Steel Frame, middle layer Accelerator Magnet Segments with the Injector at the front middle, the controller beside it, the Target Chamber at the back middle and an Energy Port at the left middle. Use a Holo-Projector to place it. It fills a containment cell with antimatter at 2,000 FE a tick: feed power into the Energy Port and the controller (insulated cable carries 1,024 FE a tick per run).");
    public static final String INFO_SIDES = GZLang.add(GroundZero.MODID + ".info.sides",
            "Every machine has a side-configuration tab (the wrench on the right of its screen): choose which faces accept items, give out products and connect to power, and switch on auto-eject.");

    private MachineText() {}

    /** Loads this class and the enums whose constants register their own keys. */
    static void init() {
        MachineStatus.values();
        RedstoneMode.values();
        SideConfig.Face.values();
        SideConfig.ItemMode.values();
    }
}
