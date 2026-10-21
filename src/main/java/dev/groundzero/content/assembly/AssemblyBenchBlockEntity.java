package dev.groundzero.content.assembly;

import java.util.List;
import java.util.Set;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundzero.GroundZero;
import dev.groundzero.content.machine.AnimatedMachine;
import dev.groundzero.content.machine.MachineBlockEntity;
import dev.groundzero.content.machine.MachineLayouts;
import dev.groundzero.content.machine.MachineMenu;
import dev.groundzero.content.machine.MachineStatus;
import dev.groundzero.registry.GZLang;
import dev.groundzero.registry.GZMenus;
import dev.groundzero.registry.GZSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
 * The Ordnance Assembly Bench (DESIGN.md §10, Phase 7 slice 7a): a payload core, a casing of its class and a fuze go
 * in; ASSEMBLE puts them together over 5-12 s (10 FE a tick) into the core's warhead, carrying the fuze. TAKE APART
 * splits a warhead in the output slot back into its three parts, at once. While it works the hoist lowers the casing
 * onto the core and the clamps close.
 */
public class AssemblyBenchBlockEntity extends MachineBlockEntity implements AnimatedMachine {
    public static final int SLOT_CORE = 0, SLOT_CASING = 1, SLOT_FUZE = 2, SLOT_WARHEAD = 3;
    public static final int BUTTON_ASSEMBLE = 0, BUTTON_TAKE_APART = 1;
    public static final int ENERGY_PER_TICK = 10, CAPACITY = 20_000;

    private static final String T_MAKES = GZLang.add(GroundZero.MODID + ".assembly.makes", "Makes: %s");
    private static final String T_READY = GZLang.add(GroundZero.MODID + ".assembly.ready", "%s, %s fuze: press Assemble");
    private static final String T_WORKING = GZLang.add(GroundZero.MODID + ".assembly.working", "Assembling: %s%%");
    private static final String T_DONE = GZLang.add(GroundZero.MODID + ".assembly.done", "Finished: %s");
    private static final String T_NO_POWER = GZLang.add(GroundZero.MODID + ".assembly.no_power", "Needs power: 10 FE a tick");
    private static final String T_IDLE = GZLang.add(GroundZero.MODID + ".assembly.idle", "Core, casing and fuze make a warhead");
    private static final String T_APART = GZLang.add(GroundZero.MODID + ".assembly.apart", "Clear the three part slots to take it apart");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Ticks of work done on the current job, and how many it needs (0: no job). */
    private int progress, total;

    public AssemblyBenchBlockEntity(BlockPos pos, BlockState state) {
        super(GZAssembly.BENCH_ENTITY.get(), pos, state, CAPACITY, 200, 0, 4,
                new int[] {SLOT_CORE, SLOT_CASING, SLOT_FUZE}, new int[] {SLOT_WARHEAD});
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_CORE -> stack.getItem() instanceof ComponentItems.PayloadCore;
            case SLOT_CASING -> stack.getItem() instanceof ComponentItems.Casing;
            case SLOT_FUZE -> stack.getItem() instanceof ComponentItems.FuzeItem;
            case SLOT_WARHEAD -> WarheadAssembly.isWarhead(stack);
            default -> false;
        };
    }

    public WarheadAssembly.Problem problem() {
        return WarheadAssembly.check(this.inventory.getItem(SLOT_CORE), this.inventory.getItem(SLOT_CASING),
                this.inventory.getItem(SLOT_FUZE), this.inventory.getItem(SLOT_WARHEAD));
    }

    public boolean assembling() {
        return this.total > 0;
    }

    public int progress() {
        return this.progress;
    }

    // --- the two buttons --------------------------------------------------------------------------------------------------
    @Override
    public boolean handleButton(int button) {
        return switch (button) {
            case BUTTON_ASSEMBLE -> startAssembly();
            case BUTTON_TAKE_APART -> takeApart();
            default -> false;
        };
    }

    /** ASSEMBLE: starts a job if the parts fit (and none is running). */
    public boolean startAssembly() {
        if (assembling() || problem() != WarheadAssembly.Problem.NONE) {
            return false;
        }
        var core = (ComponentItems.PayloadCore) this.inventory.getItem(SLOT_CORE).getItem();
        this.progress = 0;
        this.total = WarheadAssembly.assemblyTicks(core.sizeClass);
        setChanged();
        return true;
    }

    /** TAKE APART: the warhead in the output slot goes back into its parts (the part slots must be empty). */
    public boolean takeApart() {
        ItemStack warhead = this.inventory.getItem(SLOT_WARHEAD);
        if (assembling() || warhead.isEmpty() || !this.inventory.getItem(SLOT_CORE).isEmpty() || !this.inventory.getItem(SLOT_CASING).isEmpty()
                || !this.inventory.getItem(SLOT_FUZE).isEmpty()) {
            return false;
        }
        List<ItemStack> parts = WarheadAssembly.takeApart(warhead);
        if (parts.size() != 3) {
            return false;
        }
        this.inventory.setItem(SLOT_CORE, parts.get(0));
        this.inventory.setItem(SLOT_CASING, parts.get(1));
        this.inventory.setItem(SLOT_FUZE, parts.get(2));
        warhead.shrink(1);
        this.inventory.setItem(SLOT_WARHEAD, warhead.isEmpty() ? ItemStack.EMPTY : warhead);
        if (this.level != null) {
            this.level.playSound(null, this.worldPosition, GZSounds.ASSEMBLY_CLANK.get(), SoundSource.BLOCKS, 0.8f, 1.25f);
        }
        return true;
    }

    // --- working ----------------------------------------------------------------------------------------------------------
    @Override
    protected boolean tickMachine(ServerLevel level) {
        if (!assembling()) {
            this.status = MachineStatus.IDLE;
            return false;
        }
        if (problem() != WarheadAssembly.Problem.NONE) {        // a part was taken out: the job is dropped
            this.progress = this.total = 0;
            this.status = MachineStatus.IDLE;
            setChanged();
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
            finish(level);
        }
        setChanged();
        return true;
    }

    private void finish(ServerLevel level) {
        var core = (ComponentItems.PayloadCore) this.inventory.getItem(SLOT_CORE).getItem();
        var fuze = (ComponentItems.FuzeItem) this.inventory.getItem(SLOT_FUZE).getItem();
        ItemStack out = WarheadAssembly.assemble(core, fuze);
        this.inventory.getItem(SLOT_CORE).shrink(1);
        this.inventory.getItem(SLOT_CASING).shrink(1);
        this.inventory.getItem(SLOT_FUZE).shrink(1);
        this.inventory.setItem(SLOT_WARHEAD, out);
        this.progress = this.total = 0;
        level.playSound(null, this.worldPosition, GZSounds.ASSEMBLY_CLANK.get(), SoundSource.BLOCKS, 1f, 1f);
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
        ItemStack core = this.inventory.getItem(SLOT_CORE), fuze = this.inventory.getItem(SLOT_FUZE), out = this.inventory.getItem(SLOT_WARHEAD);
        int total = menu.extra(DATA_TOTAL);
        var problem = WarheadAssembly.Problem.values()[Math.clamp(menu.extra(DATA_PROBLEM), 0, WarheadAssembly.Problem.values().length - 1)];
        Component first = core.getItem() instanceof ComponentItems.PayloadCore c
                ? Component.translatable(T_MAKES, c.warhead().getName()) : !out.isEmpty() ? Component.translatable(T_DONE, out.getHoverName())
                : Component.translatable(T_IDLE);
        Component second;
        if (total > 0) {
            second = menu.status() == MachineStatus.NO_POWER ? Component.translatable(T_NO_POWER).withStyle(ChatFormatting.RED)
                    : Component.translatable(T_WORKING, menu.extra(DATA_PROGRESS) * 100 / total);
        } else if (problem == WarheadAssembly.Problem.NONE && core.getItem() instanceof ComponentItems.PayloadCore c
                && fuze.getItem() instanceof ComponentItems.FuzeItem f) {
            second = Component.translatable(T_READY, c.sizeClass.name(), Component.translatable(f.fuze.name));
        } else if (problem == WarheadAssembly.Problem.WRONG_CASING && core.getItem() instanceof ComponentItems.PayloadCore c) {
            second = Component.translatable(problem.key, c.sizeClass.name(),
                    WarheadAssembly.casing(c.sizeClass).getName(new ItemStack(WarheadAssembly.casing(c.sizeClass)))).withStyle(ChatFormatting.RED);
        } else if (problem == WarheadAssembly.Problem.NO_CORE && !out.isEmpty()) {
            second = Component.translatable(T_APART);
        } else if (problem != WarheadAssembly.Problem.NONE && problem != WarheadAssembly.Problem.NO_CORE) {
            second = Component.translatable(problem.key).withStyle(ChatFormatting.RED);
        } else {
            second = Component.empty();
        }
        return List.of(first, second);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.ASSEMBLY_BENCH.get(), containerId, inventory, this, MachineLayouts.ASSEMBLY_BENCH, this.data);
    }

    // --- model ------------------------------------------------------------------------------------------------------------
    /** The model shows a warhead on the cradle while one is being made or waits in the output slot. */
    @Override
    protected void onSlotChanged(int slot) {
        super.onSlotChanged(slot);
        if (slot == SLOT_WARHEAD || slot < 0) {
            if (this.level != null && !this.level.isClientSide()) {
                this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
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
    public Set<String> hiddenBones() {
        boolean shown = getBlockState().getValue(dev.groundzero.content.machine.MachineBlock.ACTIVE) || !this.inventory.getItem(SLOT_WARHEAD).isEmpty();
        return shown ? Set.of() : Set.of("workpiece");
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
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getIntOr("progress", 0);
        this.total = input.getIntOr("total", 0);
    }
}
