package dev.groundzero.content.assembler;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

import dev.groundzero.GroundZero;
import dev.groundzero.content.assembly.GZAssembly;
import dev.groundzero.content.assembly.MissileAssembly;
import dev.groundzero.content.assembly.MissileItem;
import dev.groundzero.content.assembly.MissileParts;
import dev.groundzero.content.assembly.MissileSpec;
import dev.groundzero.content.assembly.WarheadAssembly;
import dev.groundzero.content.launch.MissileCarrier;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A missile assembler (DESIGN.md §10.4), one class for all four: the Light, Medium, Heavy and Strategic Missile
 * Assemblers, each building the missiles of its class ({@link AssemblerClass}). A warhead of its class, its class's
 * missile body, a motor that fits it, and optionally a guidance unit and Hardened Electronics: ASSEMBLE starts the job,
 * and while it runs the crane lays the warhead's own missile out section by section (the motor and fins or first stage,
 * the body or second stage, the warhead), drawn from the missile's own model. The finished missile waits in the output
 * and on the cradles (or the stand). TAKE APART gives a finished one's parts back. Pipes feed and empty it through its
 * Item Port; it draws power through its Energy Port.
 */
public class MissileAssemblerBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController, MissileCarrier {
    public static final int SLOT_WARHEAD = 0, SLOT_BODY = 1, SLOT_MOTOR = 2, SLOT_GUIDANCE = 3, SLOT_MODULE = 4, SLOT_OUTPUT = 5;
    public static final int BUTTON_ASSEMBLE = 0, BUTTON_TAKE_APART = 1;
    /** The sections a missile is laid in: its motor and fins (or first stage), its body (or second stage), its warhead. */
    public static final int SECTIONS = 3;
    private static final int RECHECK_INTERVAL = 40;

    private static final String T_MAKES = GZLang.add(GroundZero.MODID + ".assembler.makes", "Makes: %s");
    private static final String T_READY = GZLang.add(GroundZero.MODID + ".assembler.ready", "Range %s, %s: press Assemble");
    private static final String T_WORKING = GZLang.add(GroundZero.MODID + ".assembler.working", "Assembling: %s%%");
    private static final String T_DONE = GZLang.add(GroundZero.MODID + ".assembler.done", "Finished: %s");
    private static final String T_NO_POWER = GZLang.add(GroundZero.MODID + ".assembler.no_power", "Needs power: %s FE a tick");
    private static final String T_IDLE = GZLang.add(GroundZero.MODID + ".assembler.idle", "Warhead, body and motor make a missile");
    private static final String T_APART = GZLang.add(GroundZero.MODID + ".assembler.apart", "Clear the part slots to take it apart");

    private static final RawAnimation PLATFORMS_IN = RawAnimation.begin().thenPlayAndHold("platforms_in");
    private static final RawAnimation PLATFORMS_OUT = RawAnimation.begin().thenPlayAndHold("platforms_out");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final AssemblerClass kind;
    /** Ticks of work done on the job, and how many it needs (0: no job). */
    private int progress, total;
    /** What the model shows: how many sections are laid, and whose missile they are (the warhead's id; empty: none). */
    private int laid;
    private String shown = "";

    public MissileAssemblerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, AssemblerClass kind) {
        super(type, pos, state, kind.capacity, kind.energyPerTick * 50, 0, 6,
                new int[] {SLOT_WARHEAD, SLOT_BODY, SLOT_MOTOR, SLOT_GUIDANCE, SLOT_MODULE}, new int[] {SLOT_OUTPUT});
        this.kind = kind;
    }

    public AssemblerClass kind() {
        return this.kind;
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_WARHEAD -> WarheadAssembly.isWarhead(stack) && MissileAssembly.classOf(stack) == this.kind.size;
            case SLOT_BODY -> stack.getItem() instanceof MissileParts.Body b && b.sizeClass == this.kind.size;
            case SLOT_MOTOR -> stack.getItem() instanceof MissileParts.MotorItem m && m.motor.fits(this.kind.size);
            case SLOT_GUIDANCE -> stack.getItem() instanceof MissileParts.GuidanceItem;
            case SLOT_MODULE -> stack.getItem() instanceof MissileParts.HardenedModule;
            case SLOT_OUTPUT -> isOwnMissile(stack);
            default -> false;
        };
    }

    /** A missile of this assembler's class (the ones it builds and takes apart). */
    public boolean isOwnMissile(ItemStack stack) {
        MissileSpec spec = stack.getItem() instanceof MissileItem ? MissileItem.spec(stack) : null;
        return spec != null && spec.body() == this.kind.size;
    }

    private ItemStack slot(int i) {
        return this.inventory.getItem(i);
    }

    public MissileAssembly.Problem problem() {
        return !isFormed() ? MissileAssembly.Problem.NOT_FORMED
                : MissileAssembly.check(slot(SLOT_WARHEAD), slot(SLOT_BODY), slot(SLOT_MOTOR), slot(SLOT_OUTPUT), this.kind.size);
    }

    public boolean assembling() {
        return this.total > 0;
    }

    public int progress() {
        return this.progress;
    }

    /** Sections laid out (0 to SECTIONS), as the model shows them. */
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
        if (assembling() || problem() != MissileAssembly.Problem.NONE) {
            return false;
        }
        this.progress = 0;
        this.total = this.kind.ticks;
        setChanged();
        syncModel();
        return true;
    }

    /** TAKE APART: the finished missile in the output goes back into its parts (the part slots must be empty). */
    public boolean takeApart() {
        if (assembling() || !isOwnMissile(slot(SLOT_OUTPUT))) {
            return false;
        }
        for (int i = SLOT_WARHEAD; i <= SLOT_MODULE; i++) {
            if (!slot(i).isEmpty()) {
                return false;
            }
        }
        List<ItemStack> parts = MissileAssembly.takeApart(slot(SLOT_OUTPUT));
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
            this.level.playSound(null, this.worldPosition, GZSounds.ASSEMBLY_CLANK.get(), SoundSource.BLOCKS, 1.2f, pitch);
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
        if (MissileAssembly.check(slot(SLOT_WARHEAD), slot(SLOT_BODY), slot(SLOT_MOTOR), slot(SLOT_OUTPUT), this.kind.size)
                != MissileAssembly.Problem.NONE) {                  // a part was taken out: the job is dropped
            this.progress = this.total = 0;
            this.status = MachineStatus.IDLE;
            setChanged();
            syncModel();
            return false;
        }
        if (this.energy.stored() < this.kind.energyPerTick) {
            this.status = MachineStatus.NO_POWER;
            return false;
        }
        this.energy.consume(this.kind.energyPerTick);
        this.status = MachineStatus.RUNNING;
        this.progress++;
        if (this.progress >= this.total) {
            finish();
            this.progress = this.total = 0;
            clank(0.8f);
        } else if (sectionsFor(this.progress) != this.laid) {
            clank(0.95f);                                          // the crane sets a section down
        }
        setChanged();
        syncModel();
        return true;
    }

    /** Sections laid {@code progress} ticks into a job: each set down two thirds of the way through its share of the work. */
    private int sectionsFor(int progress) {
        return Math.min(SECTIONS, (int) (progress * (double) SECTIONS / Math.max(1, this.total) + 1 / 3.0));
    }

    private void finish() {
        ItemStack out = MissileAssembly.assemble(slot(SLOT_WARHEAD), (MissileParts.Body) slot(SLOT_BODY).getItem(),
                (MissileParts.MotorItem) slot(SLOT_MOTOR).getItem(), slot(SLOT_GUIDANCE), slot(SLOT_MODULE));
        for (int i = SLOT_WARHEAD; i <= SLOT_MODULE; i++) {
            slot(i).shrink(1);
        }
        this.inventory.setItem(SLOT_OUTPUT, out);
    }

    /**
     * What the model shows: while a job runs, the sections laid so far of the missile the parts make; with a finished
     * missile waiting, all of it; otherwise nothing. Clients are told when it changes.
     */
    private void syncModel() {
        int laid;
        String shown;
        MissileSpec out = isOwnMissile(slot(SLOT_OUTPUT)) ? MissileItem.spec(slot(SLOT_OUTPUT)) : null;
        if (assembling()) {
            laid = sectionsFor(this.progress);
            // (a Cruise Engine in the motor slot: the warhead's cruise missile, laid out section by section the same way)
            shown = MissileSpec.look(slot(SLOT_WARHEAD), slot(SLOT_MOTOR).getItem() instanceof MissileParts.MotorItem m && m.motor.cruise());
        } else if (out != null) {
            laid = SECTIONS;
            shown = out.look();
        } else {
            laid = 0;
            shown = "";
        }
        if (laid != this.laid || !shown.equals(this.shown)) {
            this.laid = laid;
            this.shown = shown;
            setChanged();
            if (this.level != null && !this.level.isClientSide()) {
                this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
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

    /** The missile the parts in the slots make (null: they make none yet). */
    public @Nullable ItemStack makes() {
        if (WarheadAssembly.isWarhead(slot(SLOT_WARHEAD)) && slot(SLOT_BODY).getItem() instanceof MissileParts.Body body
                && slot(SLOT_MOTOR).getItem() instanceof MissileParts.MotorItem motor) {
            return MissileAssembly.assemble(slot(SLOT_WARHEAD), body, motor, slot(SLOT_GUIDANCE), slot(SLOT_MODULE));
        }
        return null;
    }

    @Override
    public List<Component> readout(MachineMenu menu) {
        int total = menu.extra(DATA_TOTAL);
        var problem = MissileAssembly.Problem.values()[Math.clamp(menu.extra(DATA_PROBLEM), 0, MissileAssembly.Problem.values().length - 1)];
        ItemStack warhead = slot(SLOT_WARHEAD), out = slot(SLOT_OUTPUT);
        ItemStack makes = makes();
        boolean parts = !warhead.isEmpty() || !slot(SLOT_BODY).isEmpty() || !slot(SLOT_MOTOR).isEmpty();
        Component first = makes != null ? Component.translatable(T_MAKES, makes.getHoverName())
                : !out.isEmpty() && !parts ? Component.translatable(T_DONE, out.getHoverName()) : Component.translatable(T_IDLE);
        Component second;
        if (total > 0) {
            second = menu.status() == MachineStatus.NO_POWER
                    ? Component.translatable(T_NO_POWER, this.kind.energyPerTick).withStyle(ChatFormatting.RED)
                    : Component.translatable(T_WORKING, menu.extra(DATA_PROGRESS) * 100 / total);
        } else if (problem == MissileAssembly.Problem.NONE && slot(SLOT_MOTOR).getItem() instanceof MissileParts.MotorItem m) {
            MissileParts.Guidance g = slot(SLOT_GUIDANCE).getItem() instanceof MissileParts.GuidanceItem gi ? gi.guidance : MissileParts.Guidance.UNGUIDED;
            second = Component.translatable(T_READY, m.motor.range, Component.translatable(MissileParts.guidanceName(g)));
        } else if (problem == MissileAssembly.Problem.NEEDS_WARHEAD && !parts) {
            second = !out.isEmpty() ? Component.translatable(T_APART) : Component.empty();
        } else {
            second = problemText(problem).withStyle(ChatFormatting.RED);
        }
        return List.of(first, second);
    }

    /** A problem's text, its blanks filled in. */
    private net.minecraft.network.chat.MutableComponent problemText(MissileAssembly.Problem problem) {
        return switch (problem) {
            case NEEDS_WARHEAD -> Component.translatable(problem.key, this.kind.size.name());
            case WRONG_CLASS -> {
                var size = MissileAssembly.classOf(slot(SLOT_WARHEAD));
                AssemblerClass right = size == null ? null : AssemblerClass.of(size);
                yield Component.translatable(problem.key, size == null ? "?" : size.name(),
                        right == null ? Component.literal("?") : GZAssemblers.controller(right).block().get().getName());
            }
            case NEEDS_BODY -> Component.translatable(problem.key,
                    new ItemStack(GZAssembly.BODIES.get(this.kind.size.ordinal()).get()).getHoverName());
            case MOTOR_DOES_NOT_FIT -> Component.translatable(problem.key,
                    slot(SLOT_MOTOR).getItem() instanceof MissileParts.MotorItem m ? m.motor.bodies() : Component.literal("?"));
            default -> Component.translatable(problem.key);
        };
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.MISSILE_ASSEMBLER.get(), containerId, inventory, this, MachineLayouts.MISSILE_ASSEMBLER, this.data);
    }

    // --- multiblock -------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return GZAssemblers.definition(this.kind);
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

    /** Unformed, the model shows nothing (the controller is its own block); formed, the whole assembler. */
    @Override
    public Set<String> hiddenBones() {
        return isFormed() ? Set.of() : Set.of("frame");
    }

    /**
     * The missile being built, as far as it has been laid out: on the cradles nose to the front, or (the Strategic
     * Missile Assembler) stacked upright on its stand, stage on stage.
     */
    @Override
    public MissileCarrier.@Nullable Shown shownMissile() {
        if (!isFormed() || this.laid <= 0 || this.shown.isEmpty()) {
            return null;
        }
        return this.kind.lying ? MissileCarrier.lying(this.shown, this.laid, "missile", 45)
                : MissileCarrier.stacked(this.shown, this.laid, "missile", 180);
    }

    /** The cradles a lying missile rests on (tools/assets/assemblers.py: each model's saddles and where they hold it). */
    @Override
    public MissileCarrier.@Nullable Cradles cradles() {
        return switch (this.kind) {
            case LIGHT -> LIGHT_CRADLES;
            case MEDIUM -> MEDIUM_CRADLES;
            case HEAVY -> HEAVY_CRADLES;
            case STRATEGIC -> null;
        };
    }

    private static final MissileCarrier.Cradles LIGHT_CRADLES = new MissileCarrier.Cradles(20, 25, new float[] {20, 36});
    private static final MissileCarrier.Cradles MEDIUM_CRADLES = new MissileCarrier.Cradles(21, 27, new float[] {25, 55});
    private static final MissileCarrier.Cradles HEAVY_CRADLES = new MissileCarrier.Cradles(22, 30, new float[] {25, 55, 85});

    /**
     * The main controller (working / idle); the Strategic Missile Assembler's work platforms have their own, folding in
     * round the missile while one stands on the stand and out again when it is taken away.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimatedMachine.super.registerControllers(controllers);
        if (this.kind == AssemblerClass.STRATEGIC) {
            controllers.add(new AnimationController<MissileAssemblerBlockEntity>("platforms", 0,
                    test -> test.setAndContinue(test.animatable().laid > 0 ? PLATFORMS_IN : PLATFORMS_OUT)));
        }
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
        output.putString("shown", this.shown);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getIntOr("progress", 0);
        this.total = input.getIntOr("total", 0);
        this.laid = input.getIntOr("laid", 0);
        this.shown = input.getStringOr("shown", "");
    }
}
