package dev.groundzero.content.strategic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundzero.GroundZero;
import dev.groundzero.content.assembly.AssemblyStats;
import dev.groundzero.content.assembly.GZAssembly;
import dev.groundzero.content.assembly.MissileItem;
import dev.groundzero.content.assembly.MissileParts;
import dev.groundzero.content.machine.AnimatedMachine;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.machine.MachineBlockEntity;
import dev.groundzero.content.machine.MachineLayouts;
import dev.groundzero.content.machine.MachineMenu;
import dev.groundzero.content.machine.MachineStatus;
import dev.groundzero.content.machine.SideConfig;
import dev.groundzero.content.multiblock.MultiblockController;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.multiblock.MultiblockPartBlock;
import dev.groundzero.content.ordnance.charge.ChargeKind;
import dev.groundzero.registry.GZLang;
import dev.groundzero.registry.GZMenus;
import dev.groundzero.registry.GZSounds;
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
 * The Strategic Assembly Hall (DESIGN.md §15.2, Phase 8 slice 8c): a long crane hall over two cradles that builds a
 * whole weapon in a minute at {@link #ENERGY_PER_TICK} FE a tick ({@link StrategicAssembly}). ASSEMBLE starts the job;
 * the crane lays the missile on the cradles section by section, tail first, as the work goes on; the finished
 * intercontinental missile waits in the output (and on the cradles) for the Launch Complex. TAKE APART puts a finished
 * one back into its parts. Pipes feed and empty it through its Item Port.
 */
public class StrategicAssemblyBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController {
    public static final int SLOT_PRIMARY = 0, SLOT_SECONDARY = 1, SLOT_JACKET = 2, SLOT_BOOSTER = 3, SLOT_GUIDANCE = 4, SLOT_MODULE = 5,
            SLOT_OUTPUT = 6;
    public static final int BUTTON_ASSEMBLE = 0, BUTTON_TAKE_APART = 1;
    public static final int ENERGY_PER_TICK = 250, CAPACITY = 400_000;
    /** The missile's sections as the crane lays them: the first stage, the second, the third, the warhead's shroud. */
    public static final int SECTIONS = 4;
    /** Which nose the cradles show: none yet, a thermonuclear warhead's, a cobalt one's, the Tsar. */
    public static final int LOOK_NONE = 0, LOOK_THERMONUCLEAR = 1, LOOK_COBALT = 2, LOOK_TSAR = 3;
    /** The model's nose bone for each look (tools/assets/strategic.py). */
    private static final String[] NOSE_BONES = {"", "nose_thermonuclear_warhead", "nose_cobalt_warhead", "nose_tsar_warhead"};
    private static final int RECHECK_INTERVAL = 40;

    private static final String T_MAKES = GZLang.add(GroundZero.MODID + ".strategic.makes", "Makes: %s");
    private static final String T_THERMONUCLEAR = GZLang.add(GroundZero.MODID + ".strategic.thermonuclear", "Thermonuclear Missile");
    private static final String T_COBALT = GZLang.add(GroundZero.MODID + ".strategic.cobalt", "Cobalt Missile");
    private static final String T_TSAR = GZLang.add(GroundZero.MODID + ".strategic.tsar", "Tsar Missile");
    private static final String T_READY = GZLang.add(GroundZero.MODID + ".strategic.ready", "Range %s, %s: press Assemble");
    private static final String T_WORKING = GZLang.add(GroundZero.MODID + ".strategic.working", "Assembling: %s%%");
    private static final String T_DONE = GZLang.add(GroundZero.MODID + ".strategic.done", "Finished: %s");
    private static final String T_NO_POWER = GZLang.add(GroundZero.MODID + ".strategic.no_power", "Needs power: 250 FE a tick");
    private static final String T_IDLE = GZLang.add(GroundZero.MODID + ".strategic.idle", "Needs primary, secondary, booster");
    private static final String T_APART = GZLang.add(GroundZero.MODID + ".strategic.apart", "Clear the part slots to take it apart");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Ticks of work done on the job, and how many it needs (0: no job). */
    private int progress, total;
    /** What the model shows on the cradles: how many sections are laid, and whose nose (LOOK_*). */
    private int laid, look;

    public StrategicAssemblyBlockEntity(BlockPos pos, BlockState state) {
        super(GZStrategic.HALL_ENTITY.get(), pos, state, CAPACITY, 8_000, 0, 7,
                new int[] {SLOT_PRIMARY, SLOT_SECONDARY, SLOT_JACKET, SLOT_BOOSTER, SLOT_GUIDANCE, SLOT_MODULE}, new int[] {SLOT_OUTPUT});
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_PRIMARY -> stack.is(GZAssembly.BOOSTED_FISSION_CORE.get());
            case SLOT_SECONDARY -> stack.is(GZStrategic.FUSION_SECONDARY.get());
            case SLOT_JACKET -> stack.is(GZStrategic.COBALT_JACKET.get()) || stack.is(GZStrategic.TERTIARY_STAGE.get());
            case SLOT_BOOSTER -> stack.is(GZStrategic.INTERCONTINENTAL_BOOSTER.get());
            case SLOT_GUIDANCE -> stack.getItem() instanceof MissileParts.GuidanceItem;
            case SLOT_MODULE -> stack.getItem() instanceof MissileParts.HardenedModule;
            case SLOT_OUTPUT -> stack.getItem() instanceof MissileItem && MissileItem.spec(stack) != null && MissileItem.spec(stack).intercontinental();
            default -> false;
        };
    }

    private ItemStack slot(int i) {
        return this.inventory.getItem(i);
    }

    public StrategicAssembly.Problem problem() {
        return !isFormed() ? StrategicAssembly.Problem.NOT_FORMED
                : StrategicAssembly.check(slot(SLOT_PRIMARY), slot(SLOT_SECONDARY), slot(SLOT_BOOSTER), slot(SLOT_OUTPUT));
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
    @Override
    public boolean handleButton(int button) {
        return switch (button) {
            case BUTTON_ASSEMBLE -> startAssembly();
            case BUTTON_TAKE_APART -> takeApart();
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
            this.level.playSound(null, this.worldPosition, GZSounds.ASSEMBLY_CLANK.get(), SoundSource.BLOCKS, 1.5f, pitch);
        }
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
        if (!assembling()) {
            this.status = MachineStatus.IDLE;
            return false;
        }
        if (StrategicAssembly.check(slot(SLOT_PRIMARY), slot(SLOT_SECONDARY), slot(SLOT_BOOSTER), slot(SLOT_OUTPUT))
                != StrategicAssembly.Problem.NONE) {                // a part was taken out: the job is dropped
            this.progress = this.total = 0;
            this.status = MachineStatus.IDLE;
            setChanged();
            syncModel();
            return false;
        }
        if (this.energy.stored() < ENERGY_PER_TICK) {
            this.status = MachineStatus.NO_POWER;
            return false;
        }
        this.energy.consume(ENERGY_PER_TICK);
        this.status = MachineStatus.RUNNING;
        this.progress++;
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
        return kind == ChargeKind.COBALT_WARHEAD ? LOOK_COBALT : kind == ChargeKind.TSAR_WARHEAD ? LOOK_TSAR : LOOK_THERMONUCLEAR;
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
        return GZSounds.MACHINE_ASSEMBLY_BENCH.get();
    }

    // --- screen -----------------------------------------------------------------------------------------------------------
    public static final int DATA_PROGRESS = 0, DATA_TOTAL = 1, DATA_PROBLEM = 2;

    @Override
    protected int extraDataCount() {
        return 3;
    }

    @Override
    protected int getExtraData(int index) {
        return switch (index) {
            case DATA_PROGRESS -> this.progress;
            case DATA_TOTAL -> this.total;
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
            second = menu.status() == MachineStatus.NO_POWER ? Component.translatable(T_NO_POWER).withStyle(ChatFormatting.RED)
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
        return new MachineMenu(GZMenus.STRATEGIC_ASSEMBLY.get(), containerId, inventory, this, MachineLayouts.STRATEGIC_ASSEMBLY, this.data);
    }

    // --- multiblock -------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return GZStrategic.HALL;
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
     * The model: unformed, nothing (the controller is its own block); formed, the hall, with the sections laid so far on
     * the cradles and, once the last is down, the nose of the warhead it carries.
     */
    @Override
    public Set<String> hiddenBones() {
        if (!isFormed()) {
            return Set.of("frame");
        }
        Set<String> hidden = new HashSet<>();
        for (int k = this.laid + 1; k <= SECTIONS - 1; k++) {
            hidden.add("sec" + k);
        }
        for (int look = LOOK_THERMONUCLEAR; look <= LOOK_TSAR; look++) {
            if (this.laid < SECTIONS || this.look != look) {
                hidden.add(NOSE_BONES[look]);
            }
        }
        return hidden;
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
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getIntOr("progress", 0);
        this.total = input.getIntOr("total", 0);
        this.laid = input.getIntOr("laid", 0);
        this.look = input.getIntOr("look", LOOK_NONE);
    }
}
