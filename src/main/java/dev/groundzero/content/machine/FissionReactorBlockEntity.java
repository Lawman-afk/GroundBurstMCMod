package dev.groundzero.content.machine;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.groundzero.GroundZero;
import dev.groundzero.blast.Blasts;
import dev.groundzero.config.GZServerConfig;
import dev.groundzero.content.fluid.GZFluids;
import dev.groundzero.content.multiblock.FuelChannelBlock;
import dev.groundzero.content.multiblock.GZMultiblocks;
import dev.groundzero.content.multiblock.MultiblockController;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.multiblock.MultiblockPartBlock;
import dev.groundzero.content.multiblock.PartCut;
import dev.groundzero.content.multiblock.PortBlockEntity;
import dev.groundzero.content.nuclear.Contamination;
import dev.groundzero.content.nuclear.FuelBurnup;
import dev.groundzero.content.nuclear.GZNuclear;
import dev.groundzero.content.nuclear.Meltdown;
import dev.groundzero.content.power.PowerUtil;
import dev.groundzero.registry.GZBlockEntities;
import dev.groundzero.registry.GZDataComponents;
import dev.groundzero.registry.GZLang;
import dev.groundzero.registry.GZMenus;
import dev.groundzero.registry.GZSounds;
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
 * Fission Reactor (DESIGN.md §7, §23.3): a box of lead-lined casing any size from 3x3x3 to 6x6x6 ({@link FissionCore}),
 * run from this controller. Each column of Fuel Channels in its core holds one fuel rod, up to 16 in a 4x4 core; the GUI
 * shows the core as a map with a rod slot on each channel. A game abstraction, not a reactor design: fuel rods in, a
 * control-rod setting, water coolant and a heat gauge.
 *
 * <ul>
 * <li>Power: 300 FE/t for every loaded rod x (rods withdrawn %): 1 200 FE/t from four rods, 4 800 from sixteen. Every
 *     loaded rod burns at once. A rod lasts 48 000 ticks of full power on average, each its own lifetime a little either
 *     side of that (drawn when it first burns, up to 15% off), so a core loaded all at once runs out over a while, not in
 *     the same tick. The rod carries its burnup ({@link FuelBurnup}, shown as a bar under it); a used-up rod becomes a
 *     spent fuel rod and moves to the spent slot (it waits in its channel while that is full).</li>
 * <li>Heat goes by how full the core is: loaded rods / channels x rods withdrawn, so any size runs as hot as any other
 *     loaded alike. With coolant flowing the core heads for 20 + 1 150 x that / quality °C (water quality 1: past about
 *     70% the core would pass 900 °C; heavy water 1.6: even full power settles near 740 °C). Each rod uses up to
 *     6.25 mB of water a tick, heavy water a fifth of that. A taller core is slower to heat and to cool (more water
 *     and steel): half as fast for every block of core above two, twice as fast for a one-block core.</li>
 * <li>With meltdowns turned off, at 900 °C it SCRAMs (all rods in) until the rods are withdrawn again. With them on
 *     (server config {@code reactor.meltdowns}, the default) there is no automatic SCRAM: past 900 °C with the rods out
 *     the core is <b>critical</b> and runs away (coolant only slows it); a klaxon sounds, nearby players are warned and
 *     steam vents from the top. SCRAM (or all rods in) still saves it. At 1 400 °C it melts down: the whole reactor is
 *     destroyed with its fuel, a dirty blast goes off, molten Corium pools in the crater and a contaminated zone is left
 *     for a day.</li>
 * <li>Breeding: a DU or Li-6 target rod in a target slot becomes irradiated after 6 000 ticks at the flux of four rods
 *     at full power (sixteen rods breed four times as fast).</li>
 * <li>Buffers grow with the core: 50 000 FE and 4 000 mB of coolant a channel (the tank up to 32 000 mB).</li>
 * </ul>
 * Slots: 0-15 the core map's fuel rods (one a channel), 16-17 target rods, 18 spent fuel, 19-20 irradiated targets.
 */
public class FissionReactorBlockEntity extends MachineBlockEntity implements MultiblockController {
    public static final int CHANNELS = FissionCore.GRID * FissionCore.GRID;
    /** FE/t a loaded rod gives with the control rods fully out. */
    public static final int ROD_OUTPUT = 300;
    public static final int MAX_OUTPUT = ROD_OUTPUT * CHANNELS;
    /** Average ticks of full power a fuel rod lasts, and how far either side of that one rod's lifetime may be. */
    public static final int ROD_LIFE = 48_000;
    public static final float ROD_LIFE_SPREAD = 0.15f;
    public static final int BREED_TIME = 6_000;
    /** Water a rod uses a tick at full power; heavy water is used this many times slower. */
    public static final float WATER_PER_ROD = 6.25f;
    public static final int HEAVY_WATER_SAVING = 5;
    /** Core temperature rise with a full core at full power on water coolant; divided by the coolant's quality. */
    public static final float HEAT_RISE = 1_150;
    public static final float WATER_QUALITY = 1.0f, HEAVY_WATER_QUALITY = 1.6f;
    public static final int SCRAM_HEAT = 900, WARN_HEAT = 700, AMBIENT = 20, MELTDOWN_HEAT = 1_400;
    /** The contaminated zone a meltdown leaves: radius, and how long it lasts (one day). */
    public static final float CONTAMINATION_RADIUS = 48;
    public static final int CONTAMINATION_TICKS = 24_000;
    /** Buffers per fuel channel; the tank stops short of what a GUI sync slot can carry. */
    public static final int ENERGY_PER_CHANNEL = 50_000, COOLANT_PER_CHANNEL = 4_000, COOLANT_MAX = 32_000;
    public static final int PUSH = 4_800;
    public static final int TARGET_START = CHANNELS, SPENT_SLOT = CHANNELS + 2, IRRADIATED_START = CHANNELS + 3, SLOTS = CHANNELS + 5;
    /** Sync slots: core heat (°C), rod insertion (%), output (FE/t), channel map (a bit a cell), size (w | h << 3 | d << 6), loaded rods. */
    public static final int DATA_HEAT = 0, DATA_RODS = 1, DATA_POWER = 2, DATA_CHANNELS = 3, DATA_SIZE = 4, DATA_LOADED = 5;
    public static final int BUTTON_WITHDRAW = 0, BUTTON_INSERT = 1, BUTTON_SCRAM = 2;
    private static final int RECHECK_INTERVAL = 40;
    /** Burnup is written to the rods this often (each write re-syncs the rod's slot to anyone looking). */
    private static final int BURN_INTERVAL = 20;
    /** Ticks without power before the channels stop glowing (so a stutter does not re-light the core every tick). */
    private static final int GLOW_LINGER = 10;

    public static final String SIZE_LINE = GZLang.add(GroundZero.MODID + ".gui.reactor_size", "SIZE %sx%sx%s");
    public static final String RODS_LINE = GZLang.add(GroundZero.MODID + ".gui.reactor_rods", "RODS %s/%s");
    /** Tooltips over the GUI's core map where there is no rod slot. */
    public static final String MAP_COOLANT = GZLang.add(GroundZero.MODID + ".gui.reactor_map_coolant",
            "Coolant: no Fuel Channel in this column of the core");
    public static final String MAP_CASING = GZLang.add(GroundZero.MODID + ".gui.reactor_map_casing",
            "Outside the core: a bigger reactor reaches here");
    public static final String MAP_UNFORMED = GZLang.add(GroundZero.MODID + ".gui.reactor_map_unformed",
            "The core map shows once the reactor is complete");

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
    private @Nullable List<BlockPos> energyPorts;
    /** The last check of the build, for the ghost preview (it asks several times a frame). */
    private FissionCore.@Nullable Scan scan;
    private long scanTime = Long.MIN_VALUE;

    public FissionReactorBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.FISSION_REACTOR.get(), pos, state, ENERGY_PER_CHANNEL * CHANNELS, 0, PUSH, SLOTS, inputs(), new int[] {SPENT_SLOT,
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
        return GZMultiblocks.FISSION_REACTOR;
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

    @Override
    public boolean covers(BlockPos pos) {
        return this.core != null ? this.core.contains(pos) : FissionCore.inReach(this.worldPosition, controllerFacing(), pos);
    }

    private FissionCore.Scan scan(Level level) {
        if (this.scan == null || this.scanTime != level.getGameTime()) {
            this.scan = FissionCore.scan(level, this.worldPosition, controllerFacing());
            this.scanTime = level.getGameTime();
        }
        return this.scan;
    }

    @Override
    public List<MultiblockDefinition.Mismatch> mismatches(Level level) {
        FissionCore.Scan s = scan(level);
        return s.core() != null ? List.of() : s.mismatches();
    }

    @Override
    public int previewLayers() {
        return this.level == null ? definition().layerCount() : scan(this.level).box().height();
    }

    @Override
    public @Nullable Component problem(Level level) {
        return scan(level).problem();
    }

    /** Checks the box and forms, re-forms (it changed size or shape) or un-forms the reactor. */
    @Override
    public void revalidate() {
        if (this.level == null || this.level.isClientSide() || isRemoved()) {
            return;
        }
        this.scan = null;
        FissionCore found = FissionCore.scan(this.level, this.worldPosition, controllerFacing()).core();
        boolean formed = found != null;
        if (java.util.Objects.equals(found, this.core) && formed == isFormed()) {
            return;
        }
        if (this.core != null) {
            light(this.core, false);
            connect(this.core, false);
        }
        this.core = found;
        this.energyPorts = null;
        this.lit = false;
        if (found != null) {
            connect(found, true);
            size(found.channelCount());
        }
        if (isFormed() != formed) {
            this.level.setBlock(this.worldPosition, getBlockState().setValue(MultiblockPartBlock.FORMED, formed), Block.UPDATE_ALL);
        }
        setChanged();
        sync();
    }

    /**
     * Points the walls' ports at this controller (or at nothing). Its walls stay as they are, but a wall block a 3x3x4
     * reactor from before had hidden is shown again.
     */
    private void connect(FissionCore box, boolean formed) {
        for (BlockPos pos : box.shell()) {
            BlockState state = this.level.getBlockState(pos);
            if (state.hasProperty(MultiblockPartBlock.FORMED) && !pos.equals(this.worldPosition)
                    && (state.getValue(MultiblockPartBlock.FORMED) || state.getValue(MultiblockPartBlock.CUT) != PartCut.NONE)) {
                this.level.setBlock(pos, state.setValue(MultiblockPartBlock.FORMED, false).setValue(MultiblockPartBlock.CUT, PartCut.NONE),
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
        this.energy.setCapacity(ENERGY_PER_CHANNEL * Math.max(1, channels));
        this.tanks.get(0).setCapacity(Math.min(COOLANT_MAX, COOLANT_PER_CHANNEL * Math.max(1, channels)));
    }

    /** The channels glow (light and their lit model) while the core makes power. */
    private void light(FissionCore box, boolean on) {
        for (int slot = 0; slot < CHANNELS; slot++) {
            if (!box.channel(slot)) {
                continue;
            }
            for (BlockPos pos : box.column(slot)) {
                BlockState state = this.level.getBlockState(pos);
                if (state.getBlock() instanceof FuelChannelBlock && state.getValue(FuelChannelBlock.LIT) != on) {
                    this.level.setBlock(pos, state.setValue(FuelChannelBlock.LIT, on), Block.UPDATE_CLIENTS);
                }
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
        return resource.getFluid().isSame(Fluids.WATER) || resource.getFluid().isSame(GZFluids.HEAVY_WATER.sourceFluid());
    }

    private boolean heavyWater() {
        return this.tanks.get(0).amount() > 0 && this.tanks.get(0).fluid().getFluid().isSame(GZFluids.HEAVY_WATER.sourceFluid());
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
        return stack.is(GZNuclear.DU_TARGET_ROD.get()) || stack.is(GZNuclear.LI6_TARGET_ROD.get());
    }

    /** Whether core map cell {@code slot} is a fuel channel (on clients, as last synced). */
    public boolean isChannel(int slot) {
        return this.core != null && slot < CHANNELS && this.core.channel(slot);
    }

    /** The fuel slots that are channels now, in map order. */
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
            return stack.is(GZNuclear.URANIUM_FUEL_ROD.get()) && isChannel(slot);
        }
        return slot < SPENT_SLOT && isTarget(stack);
    }

    /** One rod a channel. */
    @Override
    protected int slotLimit(int slot) {
        return slot < CHANNELS ? 1 : super.slotLimit(slot);
    }

    /**
     * The map shows a rod slot on every channel, and on any other cell that still holds something (a rod left behind
     * when the reactor was rebuilt, so it can be taken out).
     */
    @Override
    public boolean slotShown(int slot, MachineMenu menu) {
        if (slot >= CHANNELS) {
            return true;
        }
        return (menu.extra(DATA_CHANNELS) >> slot & 1) != 0 || !this.inventory.getItem(slot).isEmpty();
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
            if (isChannel(slot) && this.inventory.getItem(slot).is(GZNuclear.URANIUM_FUEL_ROD.get())) {
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

    /** How slowly the core heats and cools: 1 for a two-block core, half a block of core more or less. */
    public float thermalMass() {
        return this.core == null ? 1 : Math.max(0.5f, this.core.coreHeight() / 2f);
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
        return this.meltdownsOverride != null ? this.meltdownsOverride : GZServerConfig.REACTOR_MELTDOWNS.get();
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

    /** Breeding progress of target slot {@code t} (0 or 1), in ticks at the flux of four rods at full power. */
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
                if (isChannel(slot) && this.inventory.getItem(slot).is(GZNuclear.URANIUM_FUEL_ROD.get())) {
                    this.pending[slot] += flux;        // every loaded rod burns at once
                }
            }
            float rate = this.loaded * flux / 4f;
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
            if (!rod.is(GZNuclear.URANIUM_FUEL_ROD.get())) {
                this.pending[slot] = 0;
                continue;
            }
            FuelBurnup burnup = rod.get(GZDataComponents.FUEL_BURNUP.get());
            if (burnup == null) {
                burnup = new FuelBurnup(0, rollLife(random));
            }
            burnup = burnup.burn(this.pending[slot]);
            this.pending[slot] = 0;
            if (burnup.spent()) {
                this.inventory.setItem(slot, new ItemStack(GZNuclear.SPENT_FUEL_ROD.get()));
            } else {
                ItemStack burned = rod.copy();
                burned.set(GZDataComponents.FUEL_BURNUP.get(), burnup);
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
            if (stack.is(GZNuclear.SPENT_FUEL_ROD.get()) && this.inventory.insertDirect(SPENT_SLOT, stack, true).isEmpty()) {
                this.inventory.insertDirect(SPENT_SLOT, stack, false);
                this.inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private boolean spentWaiting() {
        for (int slot = 0; slot < CHANNELS; slot++) {
            if (this.inventory.getItem(slot).is(GZNuclear.SPENT_FUEL_ROD.get())) {
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
        Item product = target.is(GZNuclear.DU_TARGET_ROD.get()) ? GZNuclear.IRRADIATED_DU_TARGET_ROD.get() : GZNuclear.IRRADIATED_LI6_TARGET_ROD.get();
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
            level.playSound(null, top, GZSounds.REACTOR_ALARM.get(), net.minecraft.sounds.SoundSource.BLOCKS, 4.0f, 1.0f);
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
        GroundZero.LOGGER.info("Fission Reactor at {} melted down", this.worldPosition);
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            this.inventory.setItem(i, ItemStack.EMPTY);   // the fuel is lost in the melt
        }
        for (BlockPos pos : cells) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        Meltdown.afterBlast(level, centre, Blasts.detonate(level, centre, Blasts.profile("reactor_meltdown"), null));
        Contamination.of(level).add(new Contamination.Zone(centre, CONTAMINATION_RADIUS, 1f, level.getGameTime(), CONTAMINATION_TICKS));
    }

    /** Pushes power out of every Energy Port in the walls. */
    private void pushPower(ServerLevel level) {
        if (this.energyPorts == null) {
            this.energyPorts = new ArrayList<>();
            for (BlockPos pos : this.core.shell()) {
                if (level.getBlockState(pos).is(GZMultiblocks.ENERGY_PORT.block().get())) {
                    this.energyPorts.add(pos);
                }
            }
        }
        for (BlockPos port : this.energyPorts) {
            PowerUtil.pushToNeighbours(level, port, this.energy, PUSH);
        }
    }

    @Override
    public boolean handleButton(int button) {
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

    /** Tells clients what the renderer and the GUI read from the block entity (the box, the rods, the coolant). */
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
            lines.add(Component.translatable(RODS_LINE, menu.extra(DATA_LOADED), Integer.bitCount(menu.extra(DATA_CHANNELS) & 0xFFFF)));
        }
        return lines;
    }

    @Override
    protected int extraDataCount() {
        return 6;
    }

    @Override
    protected int getExtraData(int index) {
        return switch (index) {
            case DATA_HEAT -> Math.round(this.heat);
            case DATA_RODS -> this.insertion;
            case DATA_POWER -> this.power;
            case DATA_CHANNELS -> this.core == null ? 0 : this.core.channels();
            case DATA_SIZE -> this.core == null ? 0 : this.core.width() | this.core.height() << 3 | this.core.depth() << 6;
            case DATA_LOADED -> this.loaded;
            default -> 0;
        };
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.FISSION_REACTOR.get(), containerId, inventory, this, MachineLayouts.FISSION_REACTOR, this.data);
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
        return GZSounds.MACHINE_FISSION_REACTOR.get();
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
        for (int slot = 0; slot < CHANNELS; slot++) {
            if (this.pending[slot] > 0) {
                output.putFloat("pending" + slot, this.pending[slot]);
            }
        }
        if (this.core != null) {
            output.putIntArray("core", new int[] {this.core.col0(), this.core.layer0(), this.core.width(), this.core.height(), this.core.depth(),
                    this.core.channels()});
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        // Sized first, so the saved energy and coolant load into buffers big enough for them.
        int[] c = input.getIntArray("core").orElse(null);
        this.core = c != null && c.length == 6
                ? new FissionCore(this.worldPosition, controllerFacing(), c[0], c[1], c[2], c[3], c[4], c[5]) : null;
        size(input.getIntOr("sized_for", this.core == null ? 0 : this.core.channelCount()));
        super.loadAdditional(input);
        this.insertion = input.getIntOr("insertion", 100);
        this.heat = input.getFloatOr("heat", AMBIENT);
        this.breed[0] = input.getFloatOr("breed0", 0);
        this.breed[1] = input.getFloatOr("breed1", 0);
        this.scrammed = input.getBooleanOr("scrammed", false);
        this.lit = input.getBooleanOr("lit", false);
        for (int slot = 0; slot < CHANNELS; slot++) {
            this.pending[slot] = input.getFloatOr("pending" + slot, 0);
        }
        this.energyPorts = null;
    }
}
