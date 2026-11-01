package dev.groundburst.content.strategic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.assembly.AssemblyStats;
import dev.groundburst.content.assembly.GBAssembly;
import dev.groundburst.content.assembly.MissileItem;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.machine.AnimatedMachine;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.machine.MachineLayouts;
import dev.groundburst.content.machine.MachineMenu;
import dev.groundburst.content.machine.MachineStatus;
import dev.groundburst.content.machine.SideConfig;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import dev.groundburst.content.ordnance.charge.ChargeKind;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBMenus;
import dev.groundburst.registry.GBSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The ICBM Assembly Hall (DESIGN.md §15.2, Phase 8 slice 8c): a long crane hall over two cradles that builds a
 * whole weapon in a minute or so at {@link #ENERGY_PER_TICK} FE a tick ({@link StrategicAssembly}). ASSEMBLE starts the job;
 * the crane lays the missile on the cradles section by section, tail first, as the work goes on; the finished
 * intercontinental missile waits in the output (and on the cradles) for the Launch Complex. TAKE APART puts a finished
 * one back into its parts. Pipes feed and empty it through its Item Port.
 */
public class StrategicAssemblyBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController,
        dev.groundburst.content.launch.MissileCarrier {
    public static final int SLOT_PRIMARY = 0, SLOT_SECONDARY = 1, SLOT_JACKET = 2, SLOT_BOOSTER = 3, SLOT_GUIDANCE = 4, SLOT_MODULE = 5,
            SLOT_OUTPUT = 6;
    public static final int BUTTON_ASSEMBLE = 0, BUTTON_TAKE_APART = 1, BUTTON_AUTO = 2;

    /**
     * AUTO ASSEMBLE (the user's request): while it is on, the next job starts by itself as soon as the parts are in and
     * the output is clear (a finished one taken out by hand or by pipe), so a fed line keeps building.
     */
    private boolean auto;
    public static final int ENERGY_PER_TICK = 250, CAPACITY = 400_000;
    /** The missile's sections as the crane lays them: the first stage, the second, the third, the warhead's shroud. */
    public static final int SECTIONS = 4;
    /**
     * Which warhead the cradles show: none yet, a thermonuclear warhead, a cobalt one, the Tsar; the exotic ones only for a
     * missile built here before they had their own assembler (one left in its output).
     */
    public static final int LOOK_NONE = 0, LOOK_THERMONUCLEAR = 1, LOOK_COBALT = 2, LOOK_TSAR = 3, LOOK_ANTIMATTER = 4, LOOK_SINGULARITY = 5, LOOK_STORM = 6;
    private static final int RECHECK_INTERVAL = 40;

    private static final String T_MAKES = GBLang.add(GroundBurst.MODID + ".strategic.makes", "Makes: %s");
    private static final String T_THERMONUCLEAR = GBLang.add(GroundBurst.MODID + ".strategic.thermonuclear", "Thermonuclear ICBM");
    private static final String T_COBALT = GBLang.add(GroundBurst.MODID + ".strategic.cobalt", "Cobalt ICBM");
    private static final String T_TSAR = GBLang.add(GroundBurst.MODID + ".strategic.tsar", "Tsar ICBM");
    private static final String T_READY = GBLang.add(GroundBurst.MODID + ".strategic.ready", "Range %s, %s: press Assemble");
    private static final String T_WORKING = GBLang.add(GroundBurst.MODID + ".strategic.working", "Assembling: %s%%");
    private static final String T_DONE = GBLang.add(GroundBurst.MODID + ".strategic.done", "Finished: %s");
    private static final String T_NO_POWER = GBLang.add(GroundBurst.MODID + ".strategic.no_power", "Needs power: %s FE a tick");
    private static final String T_IDLE = GBLang.add(GroundBurst.MODID + ".strategic.idle", "Needs a primary and a booster");
    private static final String T_APART = GBLang.add(GroundBurst.MODID + ".strategic.apart", "Clear the part slots to take it apart");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Ticks of work done on the job, and how many it needs (0: no job). */
    private int progress, total;
    /** What the model shows on the cradles: how many sections are laid, and whose nose (LOOK_*). */
    private int laid, look;

    public StrategicAssemblyBlockEntity(BlockPos pos, BlockState state) {
        super(GBStrategic.HALL_ENTITY.get(), pos, state, CAPACITY, 8_000, 0, 7,
                new int[] {SLOT_PRIMARY, SLOT_SECONDARY, SLOT_JACKET, SLOT_BOOSTER, SLOT_GUIDANCE, SLOT_MODULE}, new int[] {SLOT_OUTPUT});
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_PRIMARY -> StrategicAssembly.isPrimary(stack);
            case SLOT_SECONDARY -> stack.is(GBStrategic.FUSION_SECONDARY.get());
            case SLOT_JACKET -> stack.is(GBStrategic.COBALT_JACKET.get()) || stack.is(GBStrategic.TERTIARY_STAGE.get());
            case SLOT_BOOSTER -> stack.is(GBStrategic.INTERCONTINENTAL_BOOSTER.get());
            case SLOT_GUIDANCE -> stack.getItem() instanceof MissileParts.GuidanceItem;
            case SLOT_MODULE -> stack.getItem() instanceof MissileParts.HardenedModule;
            case SLOT_OUTPUT -> stack.getItem() instanceof MissileItem && MissileItem.spec(stack) != null && MissileItem.spec(stack).intercontinental()
                    && !MissileItem.spec(stack).exotic();
            default -> false;
        };
    }

    private ItemStack slot(int i) {
        return this.inventory.getItem(i);
    }

    public StrategicAssembly.Problem problem() {
        return !isFormed() ? StrategicAssembly.Problem.NOT_FORMED
                : StrategicAssembly.check(slot(SLOT_PRIMARY), slot(SLOT_SECONDARY), slot(SLOT_JACKET), slot(SLOT_BOOSTER), slot(SLOT_OUTPUT));
    }

    public boolean assembling() {
        return this.total > 0;
    }

    public int progress() {
        return this.progress;
    }

    /** Sections laid on the cradles (0 to SECTIONS), as the model shows them. */
    public int laid() {
        return this.laid;
    }

    /** For GameTests: the job in hand finishes on the next tick of work. */
    public void skipToEndForTest() {
        if (assembling()) {
            this.progress = this.total - 1;
            setChanged();
        }
    }

    // --- the buttons ------------------------------------------------------------------------------------------------------
    /** AUTO ASSEMBLE on or off. */
    public boolean toggleAuto() {
        this.auto = !this.auto;
        setChanged();
        return true;
    }

    public boolean autoAssemble() {
        return this.auto;
    }

    @Override
    public boolean handleButton(int button) {
        return switch (button) {
            case BUTTON_ASSEMBLE -> startAssembly();
            case BUTTON_TAKE_APART -> takeApart();
            case BUTTON_AUTO -> toggleAuto();
            default -> false;
        };
    }

    /** ASSEMBLE: starts the job if the parts are there (and no job is running). */
    public boolean startAssembly() {
        if (assembling() || problem() != StrategicAssembly.Problem.NONE) {
            return false;
        }
        this.progress = 0;
        this.total = StrategicAssembly.ticks(slot(SLOT_JACKET));
        setChanged();
        syncModel();
        return true;
    }

    /** TAKE APART: the finished missile in the output goes back into its parts (the part slots must be empty). */
    public boolean takeApart() {
        if (assembling() || slot(SLOT_OUTPUT).isEmpty()) {
            return false;
        }
        for (int i = SLOT_PRIMARY; i <= SLOT_MODULE; i++) {
            if (!slot(i).isEmpty()) {
                return false;
            }
        }
        List<ItemStack> parts = StrategicAssembly.takeApart(slot(SLOT_OUTPUT));
        if (parts.size() != SLOT_OUTPUT) {
            return false;
        }
        for (int i = 0; i < parts.size(); i++) {
            this.inventory.setItem(i, parts.get(i));
        }
        this.inventory.setItem(SLOT_OUTPUT, ItemStack.EMPTY);
        clank(1.25f);
        return true;
    }

    private void clank(float pitch) {
        if (this.level != null) {
            this.level.playSound(null, this.worldPosition, GBSounds.ASSEMBLY_CLANK.get(), SoundSource.BLOCKS, 1.5f, pitch);
        }
    }

    /** It takes an upgrade chip (DESIGN.md §7.3). */
    @Override
    public boolean acceptsUpgrades() {
        return true;
    }

    // --- working ----------------------------------------------------------------------------------------------------------
    @Override
    protected boolean tickMachine(ServerLevel level) {
        if ((level.getGameTime() + this.worldPosition.hashCode()) % RECHECK_INTERVAL == 0) {
            revalidate();
        }
        if (!isFormed()) {
            this.status = MachineStatus.INCOMPLETE;
            return false;
        }
        if (!assembling() && this.auto) {
            startAssembly();                                       // AUTO: the next one as soon as it can
        }
        if (!assembling()) {
            this.status = MachineStatus.IDLE;
            return false;
        }
        if (StrategicAssembly.check(slot(SLOT_PRIMARY), slot(SLOT_SECONDARY), slot(SLOT_JACKET), slot(SLOT_BOOSTER), slot(SLOT_OUTPUT))
                != StrategicAssembly.Problem.NONE) {                // a part was taken out: the job is dropped
            this.progress = this.total = 0;
            this.status = MachineStatus.IDLE;
            setChanged();
            syncModel();
            return false;
        }
        int cost = workEnergy(ENERGY_PER_TICK);          // an upgrade chip's draw (DESIGN.md §7.3)
        if (this.energy.stored() < cost) {
            this.status = MachineStatus.NO_POWER;
            return false;
        }
        this.energy.consume(cost);
        this.status = MachineStatus.RUNNING;
        this.progress += workSteps();
        if (this.progress >= this.total) {
            finish();
            this.progress = this.total = 0;
            clank(0.8f);
        } else if (sectionsFor(this.progress) != this.laid) {
            clank(0.9f);                                     // the crane sets a section down
        }
        setChanged();
        syncModel();
        return true;
    }

    /** Sections on the cradles {@code progress} ticks into a job: each set down halfway through its share of the work. */
    private int sectionsFor(int progress) {
        return Math.min(SECTIONS, (int) ((progress * (double) SECTIONS / Math.max(1, this.total)) + 0.5));
    }

    private void finish() {
        ItemStack out = StrategicAssembly.assemble(slot(SLOT_JACKET), slot(SLOT_GUIDANCE), slot(SLOT_MODULE));
        for (int i = SLOT_PRIMARY; i <= SLOT_MODULE; i++) {
            slot(i).shrink(1);
        }
        this.inventory.setItem(SLOT_OUTPUT, out);
    }

    /**
     * What the cradles show: while a job runs, the sections laid so far and the warhead the parts make; with a finished
     * missile waiting, all of it; otherwise nothing. Clients are told when it changes.
     */
    private void syncModel() {
        int laid, look;
        var out = MissileItem.spec(slot(SLOT_OUTPUT));
        if (assembling()) {
            laid = sectionsFor(this.progress);
            look = lookOf(StrategicAssembly.warhead(slot(SLOT_JACKET)).kind);
        } else if (out != null && out.intercontinental()) {
            laid = SECTIONS;
            look = lookOf(AssemblyStats.kindOf(out.warhead()));
        } else {
            laid = 0;
            look = LOOK_NONE;
        }
        if (laid != this.laid || look != this.look) {
            this.laid = laid;
            this.look = look;
            setChanged();
            if (this.level != null && !this.level.isClientSide()) {
                this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static int lookOf(@org.jspecify.annotations.Nullable ChargeKind kind) {
        return kind == null ? LOOK_THERMONUCLEAR : switch (kind) {
            case COBALT_WARHEAD -> LOOK_COBALT;
            case TSAR_WARHEAD -> LOOK_TSAR;
            case ANTIMATTER_WARHEAD -> LOOK_ANTIMATTER;
            case SINGULARITY_WARHEAD -> LOOK_SINGULARITY;
            case STORM_WARHEAD -> LOOK_STORM;
            default -> LOOK_THERMONUCLEAR;
        };
    }

    @Override
    protected void onSlotChanged(int slot) {
        super.onSlotChanged(slot);
        if (this.level != null && !this.level.isClientSide()) {
            syncModel();
        }
    }

    @Override
    public SoundEvent loopSound() {
        return GBSounds.MACHINE_ASSEMBLY_BENCH.get();
    }

    // --- screen -----------------------------------------------------------------------------------------------------------
    public static final int DATA_PROGRESS = 0, DATA_TOTAL = 1, DATA_PROBLEM = 2, DATA_AUTO = 3;

    @Override
    protected int extraDataCount() {
        return 4;
    }

    @Override
    protected int getExtraData(int index) {
        return switch (index) {
            case DATA_PROGRESS -> this.progress;
            case DATA_TOTAL -> this.total;
            case DATA_AUTO -> this.auto ? 1 : 0;
            default -> problem().ordinal();
        };
    }

    @Override
    public List<Component> readout(MachineMenu menu) {
        int total = menu.extra(DATA_TOTAL);
        var problem = StrategicAssembly.Problem.values()[Math.clamp(menu.extra(DATA_PROBLEM), 0, StrategicAssembly.Problem.values().length - 1)];
        ItemStack primary = slot(SLOT_PRIMARY), out = slot(SLOT_OUTPUT);
        boolean parts = !primary.isEmpty() || !slot(SLOT_SECONDARY).isEmpty() || !slot(SLOT_BOOSTER).isEmpty();
        Component first = parts || total > 0 ? Component.translatable(T_MAKES,
                Component.translatable(switch (lookOf(StrategicAssembly.warhead(slot(SLOT_JACKET)).kind)) {
                    case LOOK_COBALT -> T_COBALT;
                    case LOOK_TSAR -> T_TSAR;
                    default -> T_THERMONUCLEAR;
                }))
                : !out.isEmpty() ? Component.translatable(T_DONE, out.getHoverName()) : Component.translatable(T_IDLE);
        Component second;
        if (total > 0) {
            second = menu.status() == MachineStatus.NO_POWER ? Component.translatable(T_NO_POWER, workEnergy(ENERGY_PER_TICK)).withStyle(ChatFormatting.RED)
                    : Component.translatable(T_WORKING, menu.extra(DATA_PROGRESS) * 100 / total);
        } else if (problem == StrategicAssembly.Problem.NONE) {
            MissileParts.Guidance g = slot(SLOT_GUIDANCE).getItem() instanceof MissileParts.GuidanceItem gi ? gi.guidance : MissileParts.Guidance.UNGUIDED;
            second = Component.translatable(T_READY, MissileParts.Motor.ICBM.range, Component.translatable(MissileParts.guidanceName(g)));
        } else if (problem == StrategicAssembly.Problem.NO_PRIMARY && !parts && !out.isEmpty()) {
            second = Component.translatable(T_APART);
        } else if (problem == StrategicAssembly.Problem.NO_PRIMARY && !parts) {
            second = Component.empty();
        } else {
            second = Component.translatable(problem.key).withStyle(ChatFormatting.RED);
        }
        return List.of(first, second);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GBMenus.STRATEGIC_ASSEMBLY.get(), containerId, inventory, this, MachineLayouts.STRATEGIC_ASSEMBLY, this.data);
    }

    // --- multiblock -------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return GBStrategic.HALL;
    }

    @Override
    public BlockPos controllerPos() {
        return this.worldPosition;
    }

    @Override
    public Direction controllerFacing() {
        return getBlockState().hasProperty(MachineBlock.FACING) ? getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
    }

    @Override
    public boolean faceOpen(SideConfig.Face face) {
        return false;
    }

    @Override
    public boolean hasSideConfig() {
        return false;
    }

    @Override
    public boolean isFormed() {
        return getBlockState().hasProperty(MultiblockPartBlock.FORMED) && getBlockState().getValue(MultiblockPartBlock.FORMED);
    }

    @Override
    public void revalidate() {
        if (this.level == null || this.level.isClientSide() || isRemoved()) {
            return;
        }
        boolean complete = definition().check(this.level, this.worldPosition, controllerFacing()).isEmpty();
        if (complete != isFormed()) {
            MultiblockController.setPartsFormed(this.level, this, complete);
            this.level.setBlock(this.worldPosition, getBlockState().setValue(MultiblockPartBlock.FORMED, complete), Block.UPDATE_ALL);
            setChanged();
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null && !this.level.isClientSide() && state.hasProperty(MultiblockPartBlock.FORMED) && state.getValue(MultiblockPartBlock.FORMED)) {
            MultiblockController.setPartsFormed(this.level, this, false);
        }
    }

    // --- model ------------------------------------------------------------------------------------------------------------
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * The model: unformed, nothing (the controller is its own block); formed, the hall. The missile on its cradles is
     * drawn from its own model's sections, the warhead last ({@link #shownMissile}).
     */
    @Override
    public Set<String> hiddenBones() {
        return isFormed() ? Set.of() : Set.of("frame");
    }

    /** The warhead the hall is building, by its look (none: the empty string). */
    public String lookWarhead() {
        var warhead = switch (this.look) {
            case LOOK_COBALT -> dev.groundburst.content.ordnance.GBOrdnance.COBALT_WARHEAD;
            case LOOK_TSAR -> dev.groundburst.content.ordnance.GBOrdnance.TSAR_WARHEAD;
            case LOOK_THERMONUCLEAR -> dev.groundburst.content.ordnance.GBOrdnance.THERMONUCLEAR_WARHEAD;
            case LOOK_ANTIMATTER -> dev.groundburst.content.ordnance.GBOrdnance.ANTIMATTER_WARHEAD;
            case LOOK_SINGULARITY -> dev.groundburst.content.ordnance.GBOrdnance.SINGULARITY_WARHEAD;
            case LOOK_STORM -> dev.groundburst.content.ordnance.GBOrdnance.STORM_WARHEAD;
            default -> null;
        };
        return warhead == null ? "" : warhead.block().getId().getPath();
    }

    /** The missile on the cradles: the sections the crane has laid so far, the warhead last, lying nose to the front. */
    @Override
    public dev.groundburst.content.launch.MissileCarrier.@org.jspecify.annotations.Nullable Shown shownMissile() {
        return isFormed() ? dev.groundburst.content.launch.MissileCarrier.lying(lookWarhead(), this.laid, "missile", 0) : null;
    }

    /** The two cradles' rubber saddles (tools/assets/strategic.py: tops at 26, under the first stage and the second). */
    private static final dev.groundburst.content.launch.MissileCarrier.Cradles CRADLES =
            new dev.groundburst.content.launch.MissileCarrier.Cradles(26, 38, new float[] {60, 93});

    @Override
    public dev.groundburst.content.launch.MissileCarrier.@org.jspecify.annotations.Nullable Cradles cradles() {
        return CRADLES;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // --- saving -----------------------------------------------------------------------------------------------------------
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("progress", this.progress);
        output.putInt("total", this.total);
        output.putInt("laid", this.laid);
        output.putInt("look", this.look);
        output.putBoolean("auto", this.auto);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getIntOr("progress", 0);
        this.total = input.getIntOr("total", 0);
        this.laid = input.getIntOr("laid", 0);
        this.look = input.getIntOr("look", LOOK_NONE);
        this.auto = input.getBooleanOr("auto", false);
    }
}
