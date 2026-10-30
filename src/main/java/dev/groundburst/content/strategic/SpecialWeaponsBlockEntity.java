package dev.groundburst.content.strategic;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.assembly.AssemblyStats;
import dev.groundburst.content.assembly.MissileItem;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.launch.MissileCarrier;
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
import dev.groundburst.content.ordnance.GBOrdnance;
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
 * The Special Weapons Assembler (DESIGN.md §16.15): a containment bay that builds the exotic weapons whole, each from its
 * core and an Intercontinental Booster, in a minute and a half at {@link #ENERGY_PER_TICK} FE a tick
 * ({@link SpecialWeapons}). Three magnetic containment rings stand round the missile's cradles; while it works they turn
 * and glow, and the missile is laid section by section through them, the warhead last. The finished intercontinental
 * missile waits in the output (and on the cradles) for the Launch Complex or the Missile Silo. TAKE APART puts a finished
 * one back into its parts. Pipes feed and empty it through its Item Port.
 */
public class SpecialWeaponsBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController, MissileCarrier {
    public static final int SLOT_CORE = 0, SLOT_BOOSTER = 1, SLOT_GUIDANCE = 2, SLOT_MODULE = 3, SLOT_OUTPUT = 4;
    public static final int BUTTON_ASSEMBLE = 0, BUTTON_TAKE_APART = 1;
    public static final int ENERGY_PER_TICK = 320, CAPACITY = 600_000;
    /** The missile's sections as they are laid: the first stage, the second, the third, the warhead's shroud. */
    public static final int SECTIONS = 4;
    /** Which warhead the cradles show: none yet, or one of the three exotic ones. */
    public static final int LOOK_NONE = 0, LOOK_ANTIMATTER = 1, LOOK_SINGULARITY = 2, LOOK_STORM = 3;
    private static final int RECHECK_INTERVAL = 40;

    private static final String T_MAKES = GBLang.add(GroundBurst.MODID + ".special_weapons.makes", "Makes: %s");
    private static final String T_ANTIMATTER = GBLang.add(GroundBurst.MODID + ".special_weapons.antimatter", "Antimatter ICBM");
    private static final String T_SINGULARITY = GBLang.add(GroundBurst.MODID + ".special_weapons.singularity", "Singularity ICBM");
    private static final String T_STORM = GBLang.add(GroundBurst.MODID + ".special_weapons.storm", "Storm ICBM");
    private static final String T_READY = GBLang.add(GroundBurst.MODID + ".special_weapons.ready", "Range %s, %s: press Assemble");
    private static final String T_WORKING = GBLang.add(GroundBurst.MODID + ".special_weapons.working", "Assembling: %s%%");
    private static final String T_DONE = GBLang.add(GroundBurst.MODID + ".special_weapons.done", "Finished: %s");
    private static final String T_NO_POWER = GBLang.add(GroundBurst.MODID + ".special_weapons.no_power", "Needs power: %s FE a tick");
    private static final String T_IDLE = GBLang.add(GroundBurst.MODID + ".special_weapons.idle", "Needs an exotic core and a booster");
    private static final String T_APART = GBLang.add(GroundBurst.MODID + ".special_weapons.apart", "Clear the part slots to take it apart");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Ticks of work done on the job, and how many it needs (0: no job). */
    private int progress, total;
    /** What the model shows on the cradles: how many sections are laid, and whose nose (LOOK_*). */
    private int laid, look;

    public SpecialWeaponsBlockEntity(BlockPos pos, BlockState state) {
        super(GBStrategic.SPECIAL_ENTITY.get(), pos, state, CAPACITY, 10_000, 0, 5,
                new int[] {SLOT_CORE, SLOT_BOOSTER, SLOT_GUIDANCE, SLOT_MODULE}, new int[] {SLOT_OUTPUT});
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_CORE -> SpecialWeapons.isCore(stack);
            case SLOT_BOOSTER -> stack.is(GBStrategic.INTERCONTINENTAL_BOOSTER.get());
            case SLOT_GUIDANCE -> stack.getItem() instanceof MissileParts.GuidanceItem;
            case SLOT_MODULE -> stack.getItem() instanceof MissileParts.HardenedModule;
            case SLOT_OUTPUT -> stack.getItem() instanceof MissileItem && MissileItem.spec(stack) != null && MissileItem.spec(stack).intercontinental()
                    && MissileItem.spec(stack).exotic();
            default -> false;
        };
    }

    private ItemStack slot(int i) {
        return this.inventory.getItem(i);
    }

    public SpecialWeapons.Problem problem() {
        return !isFormed() ? SpecialWeapons.Problem.NOT_FORMED : SpecialWeapons.check(slot(SLOT_CORE), slot(SLOT_BOOSTER), slot(SLOT_OUTPUT));
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
        if (assembling() || problem() != SpecialWeapons.Problem.NONE) {
            return false;
        }
        this.progress = 0;
        this.total = SpecialWeapons.TICKS;
        setChanged();
        syncModel();
        return true;
    }

    /** TAKE APART: the finished missile in the output goes back into its parts (the part slots must be empty). */
    public boolean takeApart() {
        if (assembling() || slot(SLOT_OUTPUT).isEmpty()) {
            return false;
        }
        for (int i = SLOT_CORE; i <= SLOT_MODULE; i++) {
            if (!slot(i).isEmpty()) {
                return false;
            }
        }
        List<ItemStack> parts = SpecialWeapons.takeApart(slot(SLOT_OUTPUT));
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

    /** It takes upgrade chips (DESIGN.md §7.3). */
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
        if (!assembling()) {
            this.status = MachineStatus.IDLE;
            return false;
        }
        if (SpecialWeapons.check(slot(SLOT_CORE), slot(SLOT_BOOSTER), slot(SLOT_OUTPUT)) != SpecialWeapons.Problem.NONE) {
            this.progress = this.total = 0;                     // a part was taken out: the job is dropped
            this.status = MachineStatus.IDLE;
            setChanged();
            syncModel();
            return false;
        }
        int cost = workEnergy(ENERGY_PER_TICK);                 // upgrade chips' draw (DESIGN.md §7.3)
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
            clank(0.9f);                                        // a section set down through the rings
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
        ItemStack out = SpecialWeapons.assemble(slot(SLOT_CORE), slot(SLOT_GUIDANCE), slot(SLOT_MODULE));
        for (int i = SLOT_CORE; i <= SLOT_MODULE; i++) {
            slot(i).shrink(1);
        }
        this.inventory.setItem(SLOT_OUTPUT, out);
    }

    /**
     * What the cradles show: while a job runs, the sections laid so far and the warhead its core makes; with a finished
     * missile waiting, all of it; otherwise nothing. Clients are told when it changes.
     */
    private void syncModel() {
        int laid, look;
        var out = MissileItem.spec(slot(SLOT_OUTPUT));
        var core = SpecialWeapons.core(slot(SLOT_CORE));
        if (assembling() && core != null) {
            laid = sectionsFor(this.progress);
            look = lookOf(core.kind);
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

    private static int lookOf(@Nullable ChargeKind kind) {
        return kind == null ? LOOK_NONE : switch (kind) {
            case ANTIMATTER_WARHEAD -> LOOK_ANTIMATTER;
            case SINGULARITY_WARHEAD -> LOOK_SINGULARITY;
            case STORM_WARHEAD -> LOOK_STORM;
            default -> LOOK_NONE;
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
        return GBSounds.MACHINE_PARTICLE_ACCELERATOR.get();     // the rings' magnets thrumming
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

    /** The name of the missile a core makes. */
    private static String missileName(int look) {
        return switch (look) {
            case LOOK_SINGULARITY -> T_SINGULARITY;
            case LOOK_STORM -> T_STORM;
            default -> T_ANTIMATTER;
        };
    }

    @Override
    public List<Component> readout(MachineMenu menu) {
        int total = menu.extra(DATA_TOTAL);
        var problem = SpecialWeapons.Problem.values()[Math.clamp(menu.extra(DATA_PROBLEM), 0, SpecialWeapons.Problem.values().length - 1)];
        ItemStack core = slot(SLOT_CORE), out = slot(SLOT_OUTPUT);
        boolean parts = !core.isEmpty() || !slot(SLOT_BOOSTER).isEmpty();
        var warhead = SpecialWeapons.core(core);
        Component first = warhead != null ? Component.translatable(T_MAKES, Component.translatable(missileName(lookOf(warhead.kind))))
                : !out.isEmpty() ? Component.translatable(T_DONE, out.getHoverName()) : Component.translatable(T_IDLE);
        Component second;
        if (total > 0) {
            second = menu.status() == MachineStatus.NO_POWER ? Component.translatable(T_NO_POWER, workEnergy(ENERGY_PER_TICK)).withStyle(ChatFormatting.RED)
                    : Component.translatable(T_WORKING, menu.extra(DATA_PROGRESS) * 100 / total);
        } else if (problem == SpecialWeapons.Problem.NONE) {
            MissileParts.Guidance g = slot(SLOT_GUIDANCE).getItem() instanceof MissileParts.GuidanceItem gi ? gi.guidance : MissileParts.Guidance.UNGUIDED;
            second = Component.translatable(T_READY, MissileParts.Motor.ICBM.range, Component.translatable(MissileParts.guidanceName(g)));
        } else if (problem == SpecialWeapons.Problem.NO_CORE && !parts && !out.isEmpty()) {
            second = Component.translatable(T_APART);
        } else if (problem == SpecialWeapons.Problem.NO_CORE && !parts) {
            second = Component.empty();
        } else {
            second = Component.translatable(problem.key).withStyle(ChatFormatting.RED);
        }
        return List.of(first, second);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GBMenus.SPECIAL_WEAPONS.get(), containerId, inventory, this, MachineLayouts.SPECIAL_WEAPONS, this.data);
    }

    // --- multiblock -------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return GBStrategic.SPECIAL;
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
     * The model: unformed, nothing (the controller is its own block); formed, the bay, its containment fields shown only
     * while it works. The missile on its cradles is drawn from its own model's sections, the warhead last
     * ({@link #shownMissile}).
     */
    @Override
    public Set<String> hiddenBones() {
        if (!isFormed()) {
            return Set.of("frame");
        }
        boolean active = getBlockState().hasProperty(MachineBlock.ACTIVE) && getBlockState().getValue(MachineBlock.ACTIVE);
        return active ? Set.of() : Set.of("field");
    }

    /** The warhead the assembler is building, by its look (none: the empty string). */
    public String lookWarhead() {
        var warhead = switch (this.look) {
            case LOOK_ANTIMATTER -> GBOrdnance.ANTIMATTER_WARHEAD;
            case LOOK_SINGULARITY -> GBOrdnance.SINGULARITY_WARHEAD;
            case LOOK_STORM -> GBOrdnance.STORM_WARHEAD;
            default -> null;
        };
        return warhead == null ? "" : warhead.block().getId().getPath();
    }

    /** The missile on the cradles: the sections laid so far, the warhead last, lying nose to the front through the rings. */
    @Override
    public MissileCarrier.@Nullable Shown shownMissile() {
        return isFormed() ? MissileCarrier.lying(lookWarhead(), this.laid, "missile", 0) : null;
    }

    /**
     * The two cradles' rubber saddles (tools/assets/special_weapons.py: tops at 28.5, 20 and 64 px up the missile from its
     * tail), so the missile's axis lies at the rings' centre, 40 px up.
     */
    private static final MissileCarrier.Cradles CRADLES = new MissileCarrier.Cradles(28.5f, 40, new float[] {20, 64});

    @Override
    public MissileCarrier.@Nullable Cradles cradles() {
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
