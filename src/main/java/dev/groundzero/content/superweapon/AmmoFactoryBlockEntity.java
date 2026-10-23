package dev.groundzero.content.superweapon;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundzero.GroundZero;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A superweapon's ammunition factory (DESIGN.md §19.5), one class for both: the GLA's Arms Dealer, building Toxin SCUDs,
 * and China's War Factory, building Nuclear Missiles. Each takes its own parts, one slot each; with all of them in, power
 * and room in the output it sets to work by itself, its crane laying the missile out section by section on the cradles
 * in its yard (drawn from the missile's own model); the finished round waits there and in the output. Pipes feed and
 * empty it through its Item Port; it draws power through its Energy Port.
 */
public class AmmoFactoryBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController, MissileCarrier {
    /** The sections a missile is laid in (its tail end, its body, its head). */
    public static final int SECTIONS = 3;
    private static final int RECHECK_INTERVAL = 40;

    private static final String T_BUILDS = GZLang.add(GroundZero.MODID + ".factory.builds", "Builds: %s");
    private static final String T_WORKING = GZLang.add(GroundZero.MODID + ".factory.working", "Building: %s%%");
    private static final String T_NEEDS = GZLang.add(GroundZero.MODID + ".factory.needs", "Needs: %s x%s");
    private static final String T_NO_POWER = GZLang.add(GroundZero.MODID + ".factory.no_power", "Needs power: %s FE a tick");
    private static final String T_FULL = GZLang.add(GroundZero.MODID + ".factory.full", "Output full");
    private static final String T_INCOMPLETE = GZLang.add(GroundZero.MODID + ".factory.incomplete", "Structure incomplete");

    /** One input: the part and how many a round takes. */
    public record Input(Supplier<? extends Item> item, int count) {}

    /**
     * Which factory: its parts (one slot each, in order), what it builds, how long it takes and the power it draws, its
     * energy buffer, and how its missile lies in the yard (its warhead's id, the cradles' figures, the model's).
     */
    public enum Kind {
        /** The GLA's Arms Dealer: a Toxin SCUD from a Medium Missile Body, a Solid Rocket Motor II, the anthrax and the waste. */
        ARMS_DEALER(List.of(new Input(() -> dev.groundzero.content.assembly.GZAssembly.MEDIUM_BODY.get(), 1),
                new Input(() -> dev.groundzero.content.assembly.GZAssembly.SRM_II.get(), 1),
                new Input(() -> dev.groundzero.content.assembly.GZAssembly.CHEMICAL_AGENT_CANISTER.get(), 1),
                new Input(() -> dev.groundzero.content.assembly.GZAssembly.TOXIC_WASTE_TANK.get(), 1)),
                () -> GZSuperweapons.TOXIN_SCUD.get(), 600, 160, 100_000, dev.groundzero.content.ordnance.charge.ChargeKind.SCUD_STORM_WARHEAD.id,
                new MissileCarrier.Cradles(22, 28, new float[] {20, 46}), 45),
        /**
         * China's War Factory: a Nuclear Missile from a Strategic Missile Body, two Liquid-fuel Stages, a Boosted Fission
         * Core in a Strategic Warhead Casing, and GPS.
         */
        WAR_FACTORY(List.of(new Input(() -> dev.groundzero.content.assembly.GZAssembly.STRATEGIC_BODY.get(), 1),
                new Input(() -> dev.groundzero.content.assembly.GZAssembly.LIQUID_FUEL_STAGE.get(), 2),
                new Input(() -> dev.groundzero.content.assembly.GZAssembly.BOOSTED_FISSION_CORE.get(), 1),
                new Input(() -> dev.groundzero.content.assembly.GZAssembly.STRATEGIC_CASING.get(), 1),
                new Input(() -> dev.groundzero.content.assembly.GZAssembly.GPS_GUIDANCE.get(), 1)),
                () -> GZSuperweapons.NUCLEAR_MISSILE_ROUND.get(), 1200, 480, 600_000,
                dev.groundzero.content.ordnance.charge.ChargeKind.NUCLEAR_MISSILE_WARHEAD.id,
                new MissileCarrier.Cradles(22, 32, new float[] {30, 100}), 90);

        public final List<Input> inputs;
        private final Supplier<? extends Item> output;
        public final int ticks, energyPerTick, capacity;
        public final String warhead;
        public final MissileCarrier.Cradles cradles;
        public final float turn;

        Kind(List<Input> inputs, Supplier<? extends Item> output, int ticks, int energyPerTick, int capacity, String warhead,
                MissileCarrier.Cradles cradles, float turn) {
            this.inputs = inputs;
            this.output = output;
            this.ticks = ticks;
            this.energyPerTick = energyPerTick;
            this.capacity = capacity;
            this.warhead = warhead;
            this.cradles = cradles;
            this.turn = turn;
        }

        public Item output() {
            return this.output.get();
        }

        public int outputSlot() {
            return this.inputs.size();
        }
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Kind kind;
    /** Ticks of work done on the round in hand, and how many it needs (0: none). */
    private int progress, total;
    /** What the model shows: how many sections are laid (a finished round waiting: all of them). */
    private int laid;

    public AmmoFactoryBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Kind kind) {
        super(type, pos, state, kind.capacity, kind.energyPerTick * 40, 0, kind.inputs.size() + 1, inputSlots(kind),
                new int[] {kind.outputSlot()});
        this.kind = kind;
    }

    private static int[] inputSlots(Kind kind) {
        int[] out = new int[kind.inputs.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = i;
        }
        return out;
    }

    public Kind kind() {
        return this.kind;
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return slot < this.kind.inputs.size() ? stack.is(this.kind.inputs.get(slot).item().get()) : slot == this.kind.outputSlot() && stack.is(this.kind.output());
    }

    private ItemStack slot(int i) {
        return this.inventory.getItem(i);
    }

    /** The first part short for a round (null: all there). */
    public @Nullable Input missing() {
        for (int i = 0; i < this.kind.inputs.size(); i++) {
            Input in = this.kind.inputs.get(i);
            if (slot(i).getCount() < in.count()) {
                return in;
            }
        }
        return null;
    }

    private boolean outputFull() {
        ItemStack out = slot(this.kind.outputSlot());
        return !out.isEmpty() && out.getCount() >= out.getMaxStackSize();
    }

    public boolean working() {
        return this.total > 0;
    }

    /** For GameTests: the round in hand finishes on the next tick of work. */
    public void skipToEndForTest() {
        if (working()) {
            this.progress = this.total - 1;
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
        if (!working()) {
            if (missing() != null || outputFull()) {
                this.status = MachineStatus.NO_RECIPE;
                syncModel();
                return false;
            }
            this.progress = 0;
            this.total = this.kind.ticks;                         // all its parts in: it sets to work
            setChanged();
        }
        if (missing() != null) {                                  // a part was taken out: the round is dropped
            this.progress = this.total = 0;
            this.status = MachineStatus.NO_RECIPE;
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
            for (int i = 0; i < this.kind.inputs.size(); i++) {
                slot(i).shrink(this.kind.inputs.get(i).count());
            }
            ItemStack out = slot(this.kind.outputSlot());
            if (out.isEmpty()) {
                this.inventory.setItem(this.kind.outputSlot(), new ItemStack(this.kind.output()));
            } else {
                out.grow(1);
            }
            this.progress = this.total = 0;
            clank(0.8f);
        } else if (sectionsFor(this.progress) != this.laid) {
            clank(0.95f);                                          // the crane sets a section down
        }
        setChanged();
        syncModel();
        return true;
    }

    private int sectionsFor(int progress) {
        return Math.min(SECTIONS, (int) (progress * (double) SECTIONS / Math.max(1, this.total) + 1 / 3.0));
    }

    private void clank(float pitch) {
        if (this.level != null) {
            this.level.playSound(null, this.worldPosition, GZSounds.ASSEMBLY_CLANK.get(), SoundSource.BLOCKS, 1.4f, pitch);
        }
    }

    /** The model's missile: while working, the sections laid so far; with a round waiting in the output, all of it. */
    private void syncModel() {
        int laid = working() ? sectionsFor(this.progress) : slot(this.kind.outputSlot()).isEmpty() ? 0 : SECTIONS;
        if (laid != this.laid) {
            this.laid = laid;
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
    private static final int DATA_PROGRESS = 0, DATA_TOTAL = 1;

    @Override
    protected int extraDataCount() {
        return 2;
    }

    @Override
    protected int getExtraData(int index) {
        return index == DATA_PROGRESS ? this.progress : this.total;
    }

    /** The progress arrow fills as the round is built. */
    @Override
    public List<Component> readout(MachineMenu menu) {
        Component first = Component.translatable(T_BUILDS, new ItemStack(this.kind.output()).getHoverName());
        int total = menu.extra(DATA_TOTAL);
        Component second;
        if (menu.status() == MachineStatus.INCOMPLETE) {
            second = Component.translatable(T_INCOMPLETE).withStyle(ChatFormatting.RED);
        } else if (total > 0) {
            second = menu.status() == MachineStatus.NO_POWER ? Component.translatable(T_NO_POWER, this.kind.energyPerTick).withStyle(ChatFormatting.RED)
                    : Component.translatable(T_WORKING, menu.extra(DATA_PROGRESS) * 100 / total);
        } else {
            Input in = missing();
            second = in != null ? Component.translatable(T_NEEDS, new ItemStack(in.item().get()).getHoverName(), in.count())
                    : outputFull() ? Component.translatable(T_FULL) : Component.empty();
        }
        return List.of(first, second);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return this.kind == Kind.ARMS_DEALER
                ? new MachineMenu(GZMenus.ARMS_DEALER.get(), containerId, inventory, this, MachineLayouts.ARMS_DEALER, this.data)
                : new MachineMenu(GZMenus.WAR_FACTORY.get(), containerId, inventory, this, MachineLayouts.WAR_FACTORY, this.data);
    }

    // --- multiblock -------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return this.kind == Kind.ARMS_DEALER ? GZSuperweapons.ARMS_DEALER : GZSuperweapons.WAR_FACTORY;
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

    @Override
    public Set<String> hiddenBones() {
        return isFormed() ? Set.of() : Set.of("frame");
    }

    /** The round being built as far as it has been laid out, on the cradles in the yard (a finished one waiting: whole). */
    @Override
    public MissileCarrier.@Nullable Shown shownMissile() {
        return isFormed() && this.laid > 0 ? MissileCarrier.lying(this.kind.warhead, this.laid, "missile", this.kind.turn) : null;
    }

    @Override
    public MissileCarrier.@Nullable Cradles cradles() {
        return this.kind.cradles;
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
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getIntOr("progress", 0);
        this.total = input.getIntOr("total", 0);
        this.laid = input.getIntOr("laid", 0);
    }
}
