package dev.groundburst.content.machine;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.Blasts;
import dev.groundburst.config.GBServerConfig;
import dev.groundburst.content.fluid.GBFluids;
import dev.groundburst.content.multiblock.FuelChannelBlock;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import dev.groundburst.content.multiblock.PartCut;
import dev.groundburst.content.multiblock.PortBlockEntity;
import dev.groundburst.content.nuclear.FuelBurnup;
import dev.groundburst.content.nuclear.GBNuclear;
import dev.groundburst.content.nuclear.Meltdown;
import dev.groundburst.content.power.PowerUtil;
import dev.groundburst.registry.GBBlockEntities;
import dev.groundburst.registry.GBDataComponents;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBMenus;
import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Fission Reactor (DESIGN.md §7, §7.2, §23.3): a box of lead-lined casing any size from 3x3x3 to 6x6x6
 * ({@link FissionCore}), run from this controller. Every Fuel Channel in its core holds one fuel rod, up to 64 in a 4x4x4
 * core; the GUI shows the core a layer at a time as a map with a rod slot on each channel. Once formed, its walls are
 * drawn as one vessel (FissionReactorRenderer: sections for every corner, edge, wall, window, port and the console).
 * A game abstraction, not a reactor design: fuel rods in, a control-rod setting, water coolant and a heat gauge.
 *
 * <ul>
 * <li>Power: 75 FE/t for every loaded rod x (rods withdrawn %): 600 FE/t from a 2x2x2 core, 4 800 from a full 4x4x4.
 *     Every loaded rod burns at once. A rod lasts 192 000 ticks of full power on average, each its own lifetime a little
 *     either side of that (drawn when it first burns, up to 15% off), so a core loaded all at once runs out over a while,
 *     not in the same tick. The rod carries its burnup ({@link FuelBurnup}, shown as a bar under it); a used-up rod
 *     becomes a spent fuel rod and moves to the spent slot (it waits in its channel while that is full).</li>
 * <li>Heat goes by how full the core is: loaded rods / channels x rods withdrawn, so any size runs as hot as any other
 *     loaded alike. With coolant flowing the core heads for 20 + 1 150 x that / quality °C (water quality 1: past about
 *     70% the core would pass 900 °C; heavy water 1.6: even full power settles near 740 °C). Each rod uses up to
 *     1.5625 mB of water a tick, heavy water a fifth of that. Water left in the core (cells without a channel) slows
 *     heating and cooling: by 1 + water cells / channels (up to 3).</li>
 * <li>With meltdowns turned off, at 900 °C it SCRAMs (all rods in) until the rods are withdrawn again. With them on
 *     (server config {@code reactor.meltdowns}, the default) there is no automatic SCRAM: past 900 °C with the rods out
 *     the core is <b>critical</b> and runs away (coolant only slows it); a klaxon sounds, nearby players are warned and
 *     steam vents from the top. SCRAM (or all rods in) still saves it. At 1 400 °C it melts down: the whole reactor is
 *     destroyed with its fuel, a dirty blast goes off, molten Corium pools in the crater and a contaminated zone is left
 *     for a day.</li>
 * <li>Breeding: a DU or Li-6 target rod in a target slot becomes irradiated after 6 000 ticks at 1 200 FE/t of output
 *     (faster as the reactor makes more).</li>
 * <li>Buffers grow with the core: 12 500 FE and 2 000 mB of coolant a channel (at least 50 000 FE and 4 000 mB, the
 *     tank up to 32 000 mB).</li>
 * <li>The plan: until it is formed the controller keeps a planned size (4x4x4 at first, set in its screen) and the
 *     ghost preview shows that reactor to build; once formed, the plan is the reactor's own size.</li>
 * </ul>
 * Slots: 0-63 the core map's fuel rods (layer by layer, one a channel), 64-65 target rods, 66 spent fuel, 67-68
 * irradiated targets.
 */
public class FissionReactorBlockEntity extends MachineBlockEntity implements MultiblockController {
    public static final int CHANNELS = FissionCore.CELLS;
    /** FE/t a loaded rod gives with the control rods fully out. */
    public static final int ROD_OUTPUT = 75;
    public static final int MAX_OUTPUT = ROD_OUTPUT * CHANNELS;
    /** Average ticks of full power a fuel rod lasts, and how far either side of that one rod's lifetime may be. */
    public static final int ROD_LIFE = 192_000;
    public static final float ROD_LIFE_SPREAD = 0.15f;
    public static final int BREED_TIME = 6_000;
    /** Output at which a target breeds in {@link #BREED_TIME}. */
    public static final float BREED_POWER = 1_200;
    /** Water a rod uses a tick at full power; heavy water is used this many times slower. */
    public static final float WATER_PER_ROD = 1.5625f;
    public static final int HEAVY_WATER_SAVING = 5;
    /** Core temperature rise with a full core at full power on water coolant; divided by the coolant's quality. */
    public static final float HEAT_RISE = 1_150;
    public static final float WATER_QUALITY = 1.0f, HEAVY_WATER_QUALITY = 1.6f;
    public static final int SCRAM_HEAT = 900, WARN_HEAT = 700, AMBIENT = 20, MELTDOWN_HEAT = 1_400;
    /**
     * The radiation a meltdown leaves (DESIGN.md §21.8): radius, how long until its centre is under 10 RAD/h (one day),
     * and its centre's dose rate when fresh (RAD/h). The corium left in the core is radioactive on its own.
     */
    public static final float CONTAMINATION_RADIUS = 48;
    public static final int CONTAMINATION_TICKS = 24_000;
    public static final float CONTAMINATION_PEAK = 40_000;
    /** How far the meltdown's smoke carries its fallout downwind, blocks (slice 12b). */
    public static final float MELTDOWN_PLUME_REACH = 400;
    /** Buffers per fuel channel and their floors; the tank stops short of what a GUI sync slot can carry. */
    public static final int ENERGY_PER_CHANNEL = 12_500, MIN_ENERGY = 50_000, COOLANT_PER_CHANNEL = 2_000, MIN_COOLANT = 4_000,
            COOLANT_MAX = 32_000;
    public static final int PUSH = MAX_OUTPUT;
    public static final int TARGET_START = CHANNELS, SPENT_SLOT = CHANNELS + 2, IRRADIATED_START = CHANNELS + 3, SLOTS = CHANNELS + 5;
    /**
     * Sync slots: core heat (°C), rod insertion (%), output (FE/t), size (w | h << 3 | d << 6, 0 unformed), loaded rods,
     * the map's layer shown, the plan (packed as the size), and the channel map, 16 bits a layer.
     */
    public static final int DATA_HEAT = 0, DATA_RODS = 1, DATA_POWER = 2, DATA_SIZE = 3, DATA_LOADED = 4, DATA_LAYER = 5,
            DATA_PLAN = 6, DATA_CHANNELS = 7;
    public static final int BUTTON_WITHDRAW = 0, BUTTON_INSERT = 1, BUTTON_SCRAM = 2, BUTTON_LAYER = 3, BUTTON_PLAN = 7;
    private static final int RECHECK_INTERVAL = 40;
    /** Burnup is written to the rods this often (each write re-syncs the rod's slot to anyone looking). */
    private static final int BURN_INTERVAL = 20;
    /** Ticks without power before the channels stop glowing (so a stutter does not re-light the core every tick). */
    private static final int GLOW_LINGER = 10;

    public static final String SIZE_LINE = GBLang.add(GroundBurst.MODID + ".gui.reactor_size", "SIZE %sx%sx%s");
    public static final String RODS_LINE = GBLang.add(GroundBurst.MODID + ".gui.reactor_rods", "RODS %s/%s");
    public static final String PLAN_LINE = GBLang.add(GroundBurst.MODID + ".gui.reactor_plan", "PLAN %sx%sx%s");
    /** Tooltips over the GUI's core map and its controls. */
    public static final String MAP_COOLANT = GBLang.add(GroundBurst.MODID + ".gui.reactor_map_coolant",
            "Coolant: no Fuel Channel in this cell of the core");
    public static final String MAP_CASING = GBLang.add(GroundBurst.MODID + ".gui.reactor_map_casing",
            "Outside the core: a bigger reactor reaches here");
    public static final String LAYER_TIP = GBLang.add(GroundBurst.MODID + ".gui.reactor_layer", "Core layer %s (1 is the bottom)");
    public static final String PLAN_TIP = GBLang.add(GroundBurst.MODID + ".gui.reactor_plan_tip",
            "The reactor to build: the ghost preview shows it (right-click the controller). The controller sits in the middle of the bottom of its front wall.");
    public static final String STATUS = GBLang.add(GroundBurst.MODID + ".reactor.status",
            "Reactor plan %sx%sx%s: %s missing, %s wrong. Sneak-right-click the controller to change the plan.");

    /** The formed reactor (null while not formed); on clients, as last synced. */
    private @Nullable FissionCore core;
    /** Channels the buffers were last sized for (kept while the structure is broken, so nothing spills). */
    private int sizedFor;
    private int insertion = 100;
    private float heat = AMBIENT;
    /** Burn not yet written to each rod (see {@link #BURN_INTERVAL}). */
    private final float[] pending = new float[CHANNELS];
    private final float[] breed = new float[2];
    private boolean scrammed;
    private int power;
    private int loaded;
    /** Coolant used but not yet drawn (heavy water goes at fractions of a mB a tick). */
    private float coolantDebt;
    /** For GameTests: meltdowns on or off for this reactor alone, whatever the server config says (null: the config). */
    private Boolean meltdownsOverride;
    private boolean critical;
    private boolean lit;
    private int darkTicks;
    /** The core layer the GUI's map shows (0 the bottom). */
    private int viewLayer;
    /** The planned size, outside (the ghost preview's reactor). */
    private int planW = 4, planH = 4, planD = 4;
    private @Nullable BlockPos energyPort;
    /** The last check of the build, for the ghost preview (it asks several times a frame). */
    private @Nullable List<MultiblockDefinition.Mismatch> planned;
    private long plannedTime = Long.MIN_VALUE;

    public FissionReactorBlockEntity(BlockPos pos, BlockState state) {
        super(GBBlockEntities.FISSION_REACTOR.get(), pos, state, ENERGY_PER_CHANNEL * CHANNELS, 0, PUSH, SLOTS, inputs(), new int[] {SPENT_SLOT,
                IRRADIATED_START, IRRADIATED_START + 1},
                List.of(new MachineTank.Spec(COOLANT_MAX, MachineTank.Role.INPUT, FissionReactorBlockEntity::isCoolant, true)));
        size(0);
    }

    private static int[] inputs() {
        int[] in = new int[CHANNELS + 2];
        for (int i = 0; i < in.length; i++) {
            in[i] = i;
        }
        return in;
    }

    // --- the structure -------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return GBMultiblocks.FISSION_REACTOR;
    }

    @Override
    public BlockPos controllerPos() {
        return this.worldPosition;
    }

    @Override
    public Direction controllerFacing() {
        return StructureMachines.facing(getBlockState());
    }

    @Override
    public boolean isFormed() {
        return StructureMachines.isFormed(getBlockState());
    }

    /** The formed reactor's box, or null. */
    public @Nullable FissionCore core() {
        return this.core;
    }

    /** The reactor the ghost preview shows. */
    public FissionCore plan() {
        return FissionCore.plan(this.worldPosition, controllerFacing(), this.planW, this.planH, this.planD);
    }

    @Override
    public boolean covers(BlockPos pos) {
        return this.core != null ? this.core.contains(pos) : FissionCore.inReach(this.worldPosition, controllerFacing(), pos);
    }

    @Override
    public List<MultiblockDefinition.Mismatch> mismatches(Level level) {
        if (isFormed()) {
            return List.of();
        }
        if (this.planned == null || this.plannedTime != level.getGameTime()) {
            this.planned = plan().planMismatches(level);
            this.plannedTime = level.getGameTime();
        }
        return this.planned;
    }

    @Override
    public int previewLayers() {
        return this.planH;
    }

    /** The status line: the plan's size and what is left to build, or what else stops it forming. */
    @Override
    public @Nullable Component problem(Level level) {
        List<MultiblockDefinition.Mismatch> left = mismatches(level);
        if (left.isEmpty()) {
            FissionCore.Scan scan = FissionCore.scan(level, this.worldPosition, controllerFacing());
            if (scan.problem() != null) {
                return scan.problem();
            }
        }
        long missing = left.stream().filter(MultiblockDefinition.Mismatch::empty).count();
        return Component.translatable(STATUS, this.planW, this.planH, this.planD, missing, left.size() - missing);
    }

    /** Checks the box and forms, re-forms (it changed size or shape) or un-forms the reactor. */
    @Override
    public void revalidate() {
        if (this.level == null || this.level.isClientSide() || isRemoved()) {
            return;
        }
        this.planned = null;
        FissionCore.Scan scan = FissionCore.scan(this.level, this.worldPosition, controllerFacing());
        FissionCore found = scan.core();
        boolean formed = found != null;
        this.energyPort = formed ? scan.ports()[2] : null;
        if (java.util.Objects.equals(found, this.core) && formed == isFormed()) {
            return;
        }
        if (this.core != null) {
            light(this.core, false);
            connect(this.core, false);
        }
        this.core = found;
        this.lit = false;
        if (found != null) {
            connect(found, true);
            size(found.channelCount());
            this.planW = found.width();
            this.planH = found.height();
            this.planD = found.depth();
            this.viewLayer = Math.min(this.viewLayer, found.coreHeight() - 1);
        }
        if (isFormed() != formed) {
            this.level.setBlock(this.worldPosition, getBlockState().setValue(MultiblockPartBlock.FORMED, formed), Block.UPDATE_ALL);
        }
        setChanged();
        sync();
    }

    /** Hides the walls (the renderer draws the vessel over them) and points the ports at this controller, or undoes it. */
    private void connect(FissionCore box, boolean formed) {
        for (BlockPos pos : box.shell()) {
            if (pos.equals(this.worldPosition)) {
                continue;
            }
            BlockState state = this.level.getBlockState(pos);
            if (state.hasProperty(MultiblockPartBlock.FORMED)
                    && (state.getValue(MultiblockPartBlock.FORMED) != formed || state.getValue(MultiblockPartBlock.CUT) != PartCut.NONE)) {
                this.level.setBlock(pos, state.setValue(MultiblockPartBlock.FORMED, formed).setValue(MultiblockPartBlock.CUT, PartCut.NONE),
                        Block.UPDATE_ALL);
            }
            if (this.level.getBlockEntity(pos) instanceof PortBlockEntity port) {
                port.setController(formed ? this.worldPosition : null);
            }
        }
    }

    /** Sizes the energy buffer and the coolant tank for a core of {@code channels} channels. */
    private void size(int channels) {
        this.sizedFor = channels;
        this.energy.setCapacity(Math.max(MIN_ENERGY, ENERGY_PER_CHANNEL * channels));
        this.tanks.get(0).setCapacity(Math.min(COOLANT_MAX, Math.max(MIN_COOLANT, COOLANT_PER_CHANNEL * channels)));
    }

    /** The channels glow (light and their lit model) while the core makes power. */
    private void light(FissionCore box, boolean on) {
        for (int slot = 0; slot < CHANNELS; slot++) {
            BlockPos pos = box.channel(slot) ? box.cellOf(slot) : null;
            if (pos == null) {
                continue;
            }
            BlockState state = this.level.getBlockState(pos);
            if (state.getBlock() instanceof FuelChannelBlock && state.getValue(FuelChannelBlock.LIT) != on) {
                this.level.setBlock(pos, state.setValue(FuelChannelBlock.LIT, on), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** The controller connects to nothing: items, fluids and power all go through the structure's ports. */
    @Override
    public boolean faceOpen(SideConfig.Face face) {
        return false;
    }

    /** Nothing to configure: the side tab is hidden. */
    @Override
    public boolean hasSideConfig() {
        return false;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null && !this.level.isClientSide() && this.core != null) {
            light(this.core, false);
            connect(this.core, false);
        }
    }

    private static boolean isCoolant(FluidResource resource) {
        return resource.getFluid().isSame(Fluids.WATER) || resource.getFluid().isSame(GBFluids.HEAVY_WATER.sourceFluid());
    }

    private boolean heavyWater() {
        return this.tanks.get(0).amount() > 0 && this.tanks.get(0).fluid().getFluid().isSame(GBFluids.HEAVY_WATER.sourceFluid());
    }

    /** Where the cooled core's temperature heads at heat level {@code lvl} (loaded / channels x rods out). */
    public float equilibrium(float lvl) {
        return AMBIENT + HEAT_RISE * lvl / (heavyWater() ? HEAVY_WATER_QUALITY : WATER_QUALITY);
    }

    @Override
    public String tankHint(int tank) {
        return MachineText.COOLANT_HINT;
    }

    // --- items ---------------------------------------------------------------------------------------------------------------
    private static boolean isTarget(ItemStack stack) {
        return stack.is(GBNuclear.DU_TARGET_ROD.get()) || stack.is(GBNuclear.LI6_TARGET_ROD.get());
    }

    /** Whether fuel slot {@code slot} is a fuel channel (on clients, as last synced). */
    public boolean isChannel(int slot) {
        return this.core != null && slot < CHANNELS && this.core.channel(slot);
    }

    /** The fuel slots that are channels now, layer by layer in map order. */
    public List<Integer> channelSlots() {
        List<Integer> out = new ArrayList<>();
        for (int slot = 0; slot < CHANNELS; slot++) {
            if (isChannel(slot)) {
                out.add(slot);
            }
        }
        return out;
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        if (slot < CHANNELS) {
            return stack.is(GBNuclear.URANIUM_FUEL_ROD.get()) && isChannel(slot);
        }
        return slot < SPENT_SLOT && isTarget(stack);
    }

    /** One rod a channel. */
    @Override
    protected int slotLimit(int slot) {
        return slot < CHANNELS ? 1 : super.slotLimit(slot);
    }

    /**
     * The map shows the layer picked: a rod slot on each of its channels, and on any other cell that still holds
     * something (a rod left behind when the reactor was rebuilt, so it can be taken out).
     */
    @Override
    public boolean slotShown(int slot, MachineMenu menu) {
        if (slot >= CHANNELS) {
            return true;
        }
        if (slot / FissionCore.LAYER != menu.extra(DATA_LAYER)) {
            return false;
        }
        return channelBit(menu, slot) || !this.inventory.getItem(slot).isEmpty();
    }

    /** Shift-clicked rods go to the channels of every layer, not only the one shown. */
    @Override
    public boolean slotTakesHidden(int slot) {
        return slot < CHANNELS;
    }

    /** Whether fuel slot {@code slot} is a channel, from a menu's synced map (either side). */
    public static boolean channelBit(MachineMenu menu, int slot) {
        return (menu.extra(DATA_CHANNELS + slot / FissionCore.LAYER) >> (slot % FissionCore.LAYER) & 1) != 0;
    }

    // --- the core ------------------------------------------------------------------------------------------------------------
    /** How far the control rods are out, 0 to 1 (0 while SCRAMmed). */
    public float flux() {
        return this.scrammed ? 0 : (100 - this.insertion) / 100f;
    }

    /** Fuel rods in channels. */
    public int loadedRods() {
        int n = 0;
        for (int slot = 0; slot < CHANNELS; slot++) {
            if (isChannel(slot) && this.inventory.getItem(slot).is(GBNuclear.URANIUM_FUEL_ROD.get())) {
                n++;
            }
        }
        return n;
    }

    /** How hard the core runs, 0 to 1: loaded rods / channels x rods withdrawn. Heat goes by this. */
    public float powerLevel() {
        if (this.core == null) {
            return 0;
        }
        return loadedRods() / (float) this.core.channelCount() * flux();
    }

    /** How slowly the core heats and cools: 1 for a core packed with channels, more for water left between them (up to 3). */
    public float thermalMass() {
        if (this.core == null) {
            return 1;
        }
        int channels = Math.max(1, this.core.channelCount());
        return Math.min(3f, 1f + (this.core.coreVolume() - channels) / (float) channels);
    }

    public float heat() {
        return this.heat;
    }

    public int insertion() {
        return this.insertion;
    }

    public boolean scrammed() {
        return this.scrammed;
    }

    /** Whether this core melts down rather than SCRAMming (server config, or a GameTest's override). */
    public boolean meltdowns() {
        return this.meltdownsOverride != null ? this.meltdownsOverride : GBServerConfig.REACTOR_MELTDOWNS.get();
    }

    /** For GameTests: meltdowns on or off for this reactor only. */
    public void setMeltdownsForTest(boolean on) {
        this.meltdownsOverride = on;
    }

    /** For GameTests: sets the core temperature. */
    public void setHeatForTest(float heat) {
        this.heat = heat;
    }

    /** Whether the core is critical (past SCRAM heat with meltdowns on and the rods out). */
    public boolean critical() {
        return this.critical;
    }

    /** Breeding progress of target slot {@code t} (0 or 1), in ticks at 1 200 FE/t of output. */
    public float breedProgress(int t) {
        return this.breed[t];
    }

    /** For GameTests: jumps a target's breeding progress ahead (a full breed takes 6 000 ticks). */
    public void setBreedProgress(int t, float progress) {
        this.breed[t] = progress;
    }

    /** For GameTests: writes the burn so far to the rods at once (it is otherwise written once a second). */
    public void burnForTest() {
        burnRods(this.level.getRandom());
        ejectSpent();
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        if ((level.getGameTime() + this.worldPosition.hashCode()) % RECHECK_INTERVAL == 0) {
            revalidate();
        }
        if (this.core == null) {
            this.status = MachineStatus.INCOMPLETE;
            this.power = 0;
            this.loaded = 0;
            this.heat += (AMBIENT - this.heat) * 0.003f;
            return false;
        }
        float flux = flux();
        this.loaded = loadedRods();
        float lvl = this.loaded / (float) this.core.channelCount() * flux;
        float mass = thermalMass();
        // Coolant and heat.
        MachineTank coolant = this.tanks.get(0);
        float settle = equilibrium(lvl);
        this.coolantDebt += WATER_PER_ROD * this.loaded * flux / (heavyWater() ? HEAVY_WATER_SAVING : 1);
        int need = (int) this.coolantDebt;
        boolean cooled = lvl <= 0 || coolant.amount() >= Math.max(1, need);
        if (cooled && need > 0) {
            coolant.drainInternal(need);
            this.coolantDebt -= need;
        }
        boolean meltdowns = meltdowns();
        this.critical = meltdowns && lvl > 0 && this.heat >= SCRAM_HEAT;
        if (lvl <= 0) {
            this.heat += (AMBIENT - this.heat) * 0.003f / mass;
        } else if (this.critical) {
            // Runaway: coolant can no longer hold the core at any temperature, only slow the climb (to half).
            this.heat += ((0.35f + 0.9f * lvl) * (cooled ? 0.5f : 1) + (cooled ? 0 : (1.5f + 3.5f * lvl) * 0.1f)) / mass;
        } else if (cooled) {
            this.heat += (settle - this.heat) * 0.01f / mass;
        } else {
            this.heat += (1.5f + 3.5f * lvl) / mass;
        }
        if (this.critical) {
            warn(level);
            if (this.heat >= MELTDOWN_HEAT) {
                meltdown(level);
                return false;                          // this block entity is gone
            }
        }
        if (this.heat >= SCRAM_HEAT && !this.scrammed && !meltdowns) {
            scram(level);
            flux = 0;
            lvl = 0;
        }
        // Power, fuel burnup, breeding.
        this.power = Math.round(ROD_OUTPUT * this.loaded * flux);
        if (this.power > 0) {
            this.energy.generate(this.power);
            for (int slot = 0; slot < CHANNELS; slot++) {
                if (isChannel(slot) && this.inventory.getItem(slot).is(GBNuclear.URANIUM_FUEL_ROD.get())) {
                    this.pending[slot] += flux;        // every loaded rod burns at once
                }
            }
            float rate = this.power / BREED_POWER;
            for (int t = 0; t < 2; t++) {
                ItemStack target = this.inventory.getItem(TARGET_START + t);
                if (!isTarget(target)) {
                    this.breed[t] = 0;
                    continue;
                }
                this.breed[t] += rate;
                if (this.breed[t] >= BREED_TIME && irradiate(TARGET_START + t)) {
                    this.breed[t] = 0;
                }
            }
            setChanged();
        }
        if ((level.getGameTime() + this.worldPosition.hashCode()) % BURN_INTERVAL == 0) {
            burnRods(level.getRandom());
            ejectSpent();
        }
        pushPower(level);
        glow(this.power > 0);
        boolean stuck = !this.inventory.getItem(SPENT_SLOT).isEmpty() && spentWaiting();
        if (this.scrammed) {
            this.status = MachineStatus.SCRAM;
        } else if (this.critical) {
            this.status = MachineStatus.CRITICAL;
        } else if (this.power <= 0) {
            this.status = stuck && this.loaded == 0 ? MachineStatus.OUTPUT_FULL : this.loaded > 0 ? MachineStatus.IDLE : MachineStatus.NO_FUEL;
        } else if (!cooled) {
            this.status = MachineStatus.NEED_FLUID;
        } else {
            this.status = this.heat >= WARN_HEAT ? MachineStatus.OVERHEATING : MachineStatus.RUNNING;
        }
        return this.power > 0;
    }

    /** Writes the burn since last time to each rod; a rod that has burned its lifetime becomes a spent fuel rod in place. */
    private void burnRods(RandomSource random) {
        for (int slot = 0; slot < CHANNELS; slot++) {
            if (this.pending[slot] <= 0) {
                continue;
            }
            ItemStack rod = this.inventory.getItem(slot);
            if (!rod.is(GBNuclear.URANIUM_FUEL_ROD.get())) {
                this.pending[slot] = 0;
                continue;
            }
            FuelBurnup burnup = rod.get(GBDataComponents.FUEL_BURNUP.get());
            if (burnup == null) {
                burnup = new FuelBurnup(0, rollLife(random));
            }
            burnup = burnup.burn(this.pending[slot]);
            this.pending[slot] = 0;
            if (burnup.spent()) {
                this.inventory.setItem(slot, new ItemStack(GBNuclear.SPENT_FUEL_ROD.get()));
            } else {
                ItemStack burned = rod.copy();
                burned.set(GBDataComponents.FUEL_BURNUP.get(), burnup);
                this.inventory.setItem(slot, burned);
            }
        }
    }

    /** A fresh rod's own lifetime: the average, give or take up to 15%, most often near the middle. */
    static int rollLife(RandomSource random) {
        float spread = random.nextFloat() + random.nextFloat() - 1f;
        return Math.round(ROD_LIFE * (1 + ROD_LIFE_SPREAD * spread));
    }

    /** Spent rods move from their channels to the spent slot while it has room. */
    private void ejectSpent() {
        for (int slot = 0; slot < CHANNELS; slot++) {
            ItemStack stack = this.inventory.getItem(slot);
            if (stack.is(GBNuclear.SPENT_FUEL_ROD.get()) && this.inventory.insertDirect(SPENT_SLOT, stack, true).isEmpty()) {
                this.inventory.insertDirect(SPENT_SLOT, stack, false);
                this.inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private boolean spentWaiting() {
        for (int slot = 0; slot < CHANNELS; slot++) {
            if (this.inventory.getItem(slot).is(GBNuclear.SPENT_FUEL_ROD.get())) {
                return true;
            }
        }
        return false;
    }

    /** Lights the channels as the core starts making power, and puts them out a moment after it stops. */
    private void glow(boolean on) {
        this.darkTicks = on ? 0 : this.darkTicks + 1;
        boolean want = on || (this.lit && this.darkTicks < GLOW_LINGER);
        if (want != this.lit && this.core != null) {
            this.lit = want;
            light(this.core, want);
        }
    }

    /** Swaps one target rod in {@code slot} for its irradiated form in an output slot; false (and nothing changed) if no room. */
    private boolean irradiate(int slot) {
        ItemStack target = this.inventory.getItem(slot);
        Item product = target.is(GBNuclear.DU_TARGET_ROD.get()) ? GBNuclear.IRRADIATED_DU_TARGET_ROD.get() : GBNuclear.IRRADIATED_LI6_TARGET_ROD.get();
        ItemStack out = new ItemStack(product);
        for (int s = IRRADIATED_START; s < IRRADIATED_START + 2; s++) {
            if (this.inventory.insertDirect(s, out, true).isEmpty()) {
                this.inventory.insertDirect(s, out, false);
                this.inventory.removeItem(slot, 1);
                return true;
            }
        }
        return false;
    }

    private void scram(ServerLevel level) {
        this.scrammed = true;
        this.insertion = 100;
        sync();
        for (Player player : level.players()) {
            if (player.distanceToSqr(Vec3.atCenterOf(this.worldPosition)) < 48 * 48) {
                player.sendOverlayMessage(Component.translatable(MachineText.SCRAM_MESSAGE).withStyle(net.minecraft.ChatFormatting.RED));
            }
        }
    }

    /** While critical: steam venting from the top, the klaxon every 2 s, a warning to everyone near. */
    private void warn(ServerLevel level) {
        Vec3 c = structureCentre();
        BlockPos top = BlockPos.containing(c.x, this.core == null ? c.y + 2 : c.y + this.core.height() / 2.0 + 0.5, c.z);
        if (level.getGameTime() % 4 == 0) {
            level.sendParticles(ParticleTypes.CLOUD, top.getX() + 0.5, top.getY() + 0.5, top.getZ() + 0.5, 3, 0.7, 0.2, 0.7, 0.04);
            if (this.heat > 1_150) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, top.getX() + 0.5, top.getY() + 0.5, top.getZ() + 0.5, 2, 0.6, 0.2, 0.6, 0.03);
            }
        }
        if (level.getGameTime() % 40 == 0) {
            level.playSound(null, top, GBSounds.REACTOR_ALARM.get(), net.minecraft.sounds.SoundSource.BLOCKS, 4.0f, 1.0f);
            Component message = Component.translatable(MachineText.CRITICAL_MESSAGE, Math.round(this.heat), MELTDOWN_HEAT)
                    .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD);
            for (Player player : level.players()) {
                if (player.distanceToSqr(Vec3.atCenterOf(this.worldPosition)) < 64 * 64) {
                    player.sendOverlayMessage(message);
                }
            }
        }
    }

    /** The middle of the reactor (the core). */
    private Vec3 structureCentre() {
        return this.core == null ? Vec3.atCenterOf(this.worldPosition) : this.core.centre();
    }

    /**
     * The core melts down: the reactor and its fuel are destroyed, a dirty blast goes off, molten Corium pools in the
     * crater and is thrown round it, and a contaminated zone is left (DESIGN.md §7).
     */
    private void meltdown(ServerLevel level) {
        Vec3 centre = structureCentre();
        List<BlockPos> cells = this.core.cells();
        GroundBurst.LOGGER.info("Fission Reactor at {} melted down", this.worldPosition);
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            this.inventory.setItem(i, ItemStack.EMPTY);   // the fuel is lost in the melt
        }
        for (BlockPos pos : cells) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        Meltdown.afterBlast(level, centre, Blasts.detonate(level, centre, Blasts.profile("reactor_meltdown"), null));
        dev.groundburst.content.radiation.RadiationSources.of(level).add(dev.groundburst.content.radiation.RadiationSource.lasting(centre,
                CONTAMINATION_RADIUS, CONTAMINATION_PEAK, CONTAMINATION_TICKS, level.getGameTime(),
                dev.groundburst.config.GBServerConfig.RADIATION_DECAY_MULTIPLIER.get()));
        // The burning core's smoke carries its fallout off downwind (slice 12b).
        dev.groundburst.content.fallout.Plumes.start(level, centre, CONTAMINATION_RADIUS * 0.5f, CONTAMINATION_PEAK * 0.5f,
                dev.groundburst.content.fallout.Plumes.halfLife(CONTAMINATION_PEAK, CONTAMINATION_TICKS), MELTDOWN_PLUME_REACH, 25);
    }

    /** Pushes power out of the Energy Port. */
    private void pushPower(ServerLevel level) {
        if (this.energyPort == null) {
            for (BlockPos pos : this.core.shell()) {
                if (level.getBlockState(pos).is(GBMultiblocks.ENERGY_PORT.block().get())) {
                    this.energyPort = pos;
                }
            }
        }
        if (this.energyPort != null) {
            PowerUtil.pushToNeighbours(level, this.energyPort, this.energy, PUSH);
        }
    }

    @Override
    public boolean handleButton(int button) {
        if (button >= BUTTON_LAYER && button < BUTTON_LAYER + FissionCore.GRID) {
            this.viewLayer = button - BUTTON_LAYER;
            return true;
        }
        if (button >= BUTTON_PLAN && button < BUTTON_PLAN + 6) {
            if (isFormed()) {
                return false;                          // a formed reactor's plan is its own size
            }
            int axis = (button - BUTTON_PLAN) / 2, step = (button - BUTTON_PLAN) % 2 == 0 ? -1 : 1;
            switch (axis) {
                case 0 -> this.planW = Math.clamp(this.planW + step, FissionCore.MIN, FissionCore.MAX);
                case 1 -> this.planH = Math.clamp(this.planH + step, FissionCore.MIN, FissionCore.MAX);
                default -> this.planD = Math.clamp(this.planD + step, FissionCore.MIN, FissionCore.MAX);
            }
            this.planned = null;
            setChanged();
            sync();
            return true;
        }
        switch (button) {
            case BUTTON_WITHDRAW -> {
                this.insertion = Math.max(0, this.insertion - 10);
                this.scrammed = false;
            }
            case BUTTON_INSERT -> this.insertion = Math.min(100, this.insertion + 10);
            case BUTTON_SCRAM -> {
                this.insertion = 100;
                this.scrammed = false;
            }
            default -> {
                return false;
            }
        }
        setChanged();
        sync();
        return true;
    }

    /** Tells clients what the renderer and the GUI read from the block entity (the box, the rods, the coolant, the plan). */
    private void sync() {
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- GUI -----------------------------------------------------------------------------------------------------------------
    @Override
    public List<Component> readout(MachineMenu menu) {
        List<Component> lines = new ArrayList<>(List.of(Component.translatable(MachineText.OUTPUT, menu.extra(DATA_POWER)),
                Component.translatable(MachineText.CORE, menu.extra(DATA_HEAT))));
        int size = menu.extra(DATA_SIZE);
        if (size != 0) {
            lines.add(Component.translatable(SIZE_LINE, size & 7, size >> 3 & 7, size >> 6 & 7));
            int channels = 0;
            for (int y = 0; y < FissionCore.GRID; y++) {
                channels += Integer.bitCount(menu.extra(DATA_CHANNELS + y) & 0xFFFF);
            }
            lines.add(Component.translatable(RODS_LINE, menu.extra(DATA_LOADED), channels));
        } else {
            int plan = menu.extra(DATA_PLAN);
            lines.add(Component.translatable(PLAN_LINE, plan & 7, plan >> 3 & 7, plan >> 6 & 7));
        }
        return lines;
    }

    @Override
    protected int extraDataCount() {
        return DATA_CHANNELS + FissionCore.GRID;
    }

    @Override
    protected int getExtraData(int index) {
        if (index >= DATA_CHANNELS) {
            return this.core == null ? 0 : (int) (this.core.channels() >>> (FissionCore.LAYER * (index - DATA_CHANNELS)) & 0xFFFF);
        }
        return switch (index) {
            case DATA_HEAT -> Math.round(this.heat);
            case DATA_RODS -> this.insertion;
            case DATA_POWER -> this.power;
            case DATA_SIZE -> this.core == null ? 0 : this.core.width() | this.core.height() << 3 | this.core.depth() << 6;
            case DATA_LOADED -> this.loaded;
            case DATA_LAYER -> this.viewLayer;
            case DATA_PLAN -> this.planW | this.planH << 3 | this.planD << 6;
            default -> 0;
        };
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GBMenus.FISSION_REACTOR.get(), containerId, inventory, this, MachineLayouts.FISSION_REACTOR, this.data);
    }

    // --- rendering -----------------------------------------------------------------------------------------------------------
    /**
     * The renderer fills the whole core with the coolant; this entry only asks for the tank level to be sent to clients
     * as it moves (see {@link MachineBlockEntity#onTankChanged}).
     */
    private static final List<TankRender> CORE_WATER = List.of(new TankRender(0, 0, 0, 0, 0, 0, 0));

    @Override
    public List<TankRender> tankRenders() {
        return CORE_WATER;
    }

    @Override
    public boolean showsTanks() {
        return isFormed();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public SoundEvent loopSound() {
        return GBSounds.MACHINE_FISSION_REACTOR.get();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("insertion", this.insertion);
        output.putFloat("heat", this.heat);
        output.putFloat("breed0", this.breed[0]);
        output.putFloat("breed1", this.breed[1]);
        output.putBoolean("scrammed", this.scrammed);
        output.putInt("sized_for", this.sizedFor);
        output.putBoolean("lit", this.lit);
        output.putInt("layer", this.viewLayer);
        output.putIntArray("plan", new int[] {this.planW, this.planH, this.planD});
        for (int slot = 0; slot < CHANNELS; slot++) {
            if (this.pending[slot] > 0) {
                output.putFloat("pending" + slot, this.pending[slot]);
            }
        }
        if (this.core != null) {
            output.putIntArray("core", new int[] {this.core.col0(), this.core.layer0(), this.core.width(), this.core.height(), this.core.depth()});
            output.putLong("channels", this.core.channels());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        // Sized first, so the saved energy and coolant load into buffers big enough for them.
        int[] c = input.getIntArray("core").orElse(null);
        this.core = c != null && c.length == 5
                ? new FissionCore(this.worldPosition, controllerFacing(), c[0], c[1], c[2], c[3], c[4], input.getLongOr("channels", 0L)) : null;
        size(input.getIntOr("sized_for", this.core == null ? 0 : this.core.channelCount()));
        super.loadAdditional(input);
        this.insertion = input.getIntOr("insertion", 100);
        this.heat = input.getFloatOr("heat", AMBIENT);
        this.breed[0] = input.getFloatOr("breed0", 0);
        this.breed[1] = input.getFloatOr("breed1", 0);
        this.scrammed = input.getBooleanOr("scrammed", false);
        this.lit = input.getBooleanOr("lit", false);
        this.viewLayer = input.getIntOr("layer", 0);
        int[] plan = input.getIntArray("plan").orElse(null);
        if (plan != null && plan.length == 3) {
            this.planW = Math.clamp(plan[0], FissionCore.MIN, FissionCore.MAX);
            this.planH = Math.clamp(plan[1], FissionCore.MIN, FissionCore.MAX);
            this.planD = Math.clamp(plan[2], FissionCore.MIN, FissionCore.MAX);
        }
        for (int slot = 0; slot < CHANNELS; slot++) {
            this.pending[slot] = input.getFloatOr("pending" + slot, 0);
        }
        this.energyPort = null;
        this.planned = null;
    }
}
