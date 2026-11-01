package dev.groundburst.content.assembly;

import java.util.List;
import java.util.Set;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.machine.AnimatedMachine;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.machine.MachineLayouts;
import dev.groundburst.content.machine.MachineMenu;
import dev.groundburst.content.machine.MachineStatus;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBMenus;
import dev.groundburst.registry.GBSounds;
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
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Ordnance Assembly Bench (DESIGN.md §10, Phase 7): warheads (slice 7a), one job at a time, 10 FE a tick. A payload
 * core, a casing of its class and a fuze; ASSEMBLE puts them together over 5-12 s into the core's warhead, carrying the
 * fuze and its setting. TAKE APART splits a warhead back into its parts. While it works the hoist lowers its load onto
 * the cradle and the clamps close. (Its missile page, slice 7b, went to the missile assemblers, §10.4: anything left in
 * that page's slots in an older world is dropped beside the bench.)
 */
public class AssemblyBenchBlockEntity extends MachineBlockEntity implements AnimatedMachine {
    public static final int SLOT_CORE = 0, SLOT_CASING = 1, SLOT_FUZE = 2, SLOT_WARHEAD = 3;
    /** The missile page's old slots (4-9): kept so older worlds load, emptied beside the bench. */
    private static final int RETIRED_FROM = 4, SLOTS = 10;
    public static final int BUTTON_ASSEMBLE = 0, BUTTON_TAKE_APART = 1, BUTTON_AUTO = 2;

    /**
     * AUTO ASSEMBLE (the user's request): while it is on, the next job starts by itself as soon as the parts are in and
     * the output is clear (a finished one taken out by hand or by pipe), so a fed line keeps building.
     */
    private boolean auto;
    public static final int ENERGY_PER_TICK = 10, CAPACITY = 20_000;

    private static final String T_MAKES = GBLang.add(GroundBurst.MODID + ".assembly.makes", "Makes: %s");
    private static final String T_READY = GBLang.add(GroundBurst.MODID + ".assembly.ready", "%s, %s fuze: press Assemble");
    private static final String T_WORKING = GBLang.add(GroundBurst.MODID + ".assembly.working", "Assembling: %s%%");
    private static final String T_DONE = GBLang.add(GroundBurst.MODID + ".assembly.done", "Finished: %s");
    private static final String T_NO_POWER = GBLang.add(GroundBurst.MODID + ".assembly.no_power", "Needs power: %s FE a tick");
    private static final String T_IDLE = GBLang.add(GroundBurst.MODID + ".assembly.idle", "Core, casing and fuze make a warhead");
    private static final String T_APART = GBLang.add(GroundBurst.MODID + ".assembly.apart", "Clear the three part slots to take it apart");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Ticks of work done on the current job, and how many it needs (0: no job). */
    private int progress, total;
    /** Each fuze's setting, as last set on the screen (or taken from a warhead taken apart), in Fuze order. */
    private final int[] fuzeValues = new int[Fuze.values().length];
    {
        for (Fuze f : Fuze.values()) {
            this.fuzeValues[f.ordinal()] = f.setting.fallback;
        }
    }
    /** Whether the old missile page's slots have been emptied since it loaded. */
    private boolean retiredChecked;

    public AssemblyBenchBlockEntity(BlockPos pos, BlockState state) {
        super(GBAssembly.BENCH_ENTITY.get(), pos, state, CAPACITY, 200, 0, SLOTS, new int[] {SLOT_CORE, SLOT_CASING, SLOT_FUZE},
                new int[] {SLOT_WARHEAD});
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

    @Override
    protected boolean slotAutomated(int slot) {
        return slot < RETIRED_FROM;
    }

    private ItemStack slot(int i) {
        return this.inventory.getItem(i);
    }

    public WarheadAssembly.Problem problem() {
        return WarheadAssembly.check(slot(SLOT_CORE), slot(SLOT_CASING), slot(SLOT_FUZE), slot(SLOT_WARHEAD));
    }

    public boolean assembling() {
        return this.total > 0;
    }

    /** The fuze in the fuze slot (null: none). */
    public @org.jspecify.annotations.Nullable Fuze fittedFuze() {
        return slot(SLOT_FUZE).getItem() instanceof ComponentItems.FuzeItem f ? f.fuze : null;
    }

    /** The setting the fitted fuze will be given. */
    public int fuzeValue() {
        Fuze f = fittedFuze();
        return f == null ? 0 : this.fuzeValues[f.ordinal()];
    }

    /** The screen sets the fitted fuze's setting (kept in its range). */
    public void setFuzeValue(int value) {
        Fuze f = fittedFuze();
        if (f != null) {
            this.fuzeValues[f.ordinal()] = f.setting.clamp(value);
            setChanged();
        }
    }

    public int progress() {
        return this.progress;
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

    /** ASSEMBLE: starts a job if the parts fit (and no job is running). */
    public boolean startAssembly() {
        if (assembling() || problem() != WarheadAssembly.Problem.NONE) {
            return false;
        }
        var core = (ComponentItems.PayloadCore) slot(SLOT_CORE).getItem();
        this.progress = 0;
        this.total = WarheadAssembly.assemblyTicks(core.sizeClass);
        setChanged();
        return true;
    }

    /** TAKE APART: the warhead in the output slot goes back into its parts (the part slots must be empty). */
    public boolean takeApart() {
        ItemStack warhead = slot(SLOT_WARHEAD);
        if (assembling() || warhead.isEmpty() || !slot(SLOT_CORE).isEmpty() || !slot(SLOT_CASING).isEmpty() || !slot(SLOT_FUZE).isEmpty()) {
            return false;
        }
        List<ItemStack> parts = WarheadAssembly.takeApart(warhead);
        if (parts.size() != 3) {
            return false;
        }
        this.inventory.setItem(SLOT_CORE, parts.get(0));
        this.inventory.setItem(SLOT_CASING, parts.get(1));
        this.inventory.setItem(SLOT_FUZE, parts.get(2));
        FuzeSettings old = warhead.getOrDefault(dev.groundburst.registry.GBDataComponents.FUZE.get(), FuzeSettings.IMPACT);
        this.fuzeValues[old.fuze().ordinal()] = old.value();             // reassembled, it keeps its setting
        warhead.shrink(1);
        this.inventory.setItem(SLOT_WARHEAD, warhead.isEmpty() ? ItemStack.EMPTY : warhead);
        clank(1.25f);
        return true;
    }

    private void clank(float pitch) {
        if (this.level != null) {
            this.level.playSound(null, this.worldPosition, GBSounds.ASSEMBLY_CLANK.get(), SoundSource.BLOCKS, 1f, pitch);
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
        if (!this.retiredChecked) {
            dropRetired(level);
        }
        if (!assembling() && this.auto) {
            startAssembly();                                       // AUTO: the next one as soon as it can
        }
        if (!assembling()) {
            this.status = MachineStatus.IDLE;
            return false;
        }
        if (problem() != WarheadAssembly.Problem.NONE) {            // a part was taken out: the job is dropped
            this.progress = this.total = 0;
            this.status = MachineStatus.IDLE;
            setChanged();
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
            clank(1f);
        }
        setChanged();
        return true;
    }

    /** Drops whatever an older world left in the old missile page's slots beside the bench (once after it loads). */
    private void dropRetired(ServerLevel level) {
        this.retiredChecked = true;
        for (int i = RETIRED_FROM; i < SLOTS; i++) {
            ItemStack stack = slot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.1, this.worldPosition.getZ() + 0.5, stack.copy());
                this.inventory.setItem(i, ItemStack.EMPTY);
                setChanged();
            }
        }
    }

    private void finish() {
        var core = (ComponentItems.PayloadCore) slot(SLOT_CORE).getItem();
        var fuze = (ComponentItems.FuzeItem) slot(SLOT_FUZE).getItem();
        ItemStack out = WarheadAssembly.assemble(core, fuze, this.fuzeValues[fuze.fuze.ordinal()]);
        slot(SLOT_CORE).shrink(1);
        slot(SLOT_CASING).shrink(1);
        slot(SLOT_FUZE).shrink(1);
        this.inventory.setItem(SLOT_WARHEAD, out);
    }

    @Override
    public SoundEvent loopSound() {
        return GBSounds.MACHINE_ASSEMBLY_BENCH.get();
    }

    // --- screen -----------------------------------------------------------------------------------------------------------
    public static final int DATA_PROGRESS = 0, DATA_TOTAL = 1, DATA_PROBLEM = 2, DATA_FUZE_VALUE = 3, DATA_AUTO = 4;

    @Override
    protected int extraDataCount() {
        return 5;
    }

    @Override
    protected int getExtraData(int index) {
        return switch (index) {
            case DATA_PROGRESS -> this.progress;
            case DATA_TOTAL -> this.total;
            case DATA_FUZE_VALUE -> fuzeValue();
            case DATA_AUTO -> this.auto ? 1 : 0;
            default -> problem().ordinal();
        };
    }

    @Override
    public List<Component> readout(MachineMenu menu) {
        int total = menu.extra(DATA_TOTAL);
        ItemStack core = slot(SLOT_CORE), fuze = slot(SLOT_FUZE), out = slot(SLOT_WARHEAD);
        var problem = WarheadAssembly.Problem.values()[Math.clamp(menu.extra(DATA_PROBLEM), 0, WarheadAssembly.Problem.values().length - 1)];
        Component first = core.getItem() instanceof ComponentItems.PayloadCore c
                ? Component.translatable(T_MAKES, c.warhead().getName()) : !out.isEmpty() ? Component.translatable(T_DONE, out.getHoverName())
                : Component.translatable(T_IDLE);
        Component second;
        if (total > 0) {
            second = menu.status() == MachineStatus.NO_POWER ? Component.translatable(T_NO_POWER, workEnergy(ENERGY_PER_TICK)).withStyle(ChatFormatting.RED)
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
        return new MachineMenu(GBMenus.ASSEMBLY_BENCH.get(), containerId, inventory, this, MachineLayouts.ASSEMBLY_BENCH, this.data);
    }

    // --- model ------------------------------------------------------------------------------------------------------------
    /** The model shows a warhead on the cradle while a job runs or a finished warhead waits. */
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
        boolean shown = getBlockState().getValue(dev.groundburst.content.machine.MachineBlock.ACTIVE) || !slot(SLOT_WARHEAD).isEmpty();
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
        output.putIntArray("fuze_values", this.fuzeValues);
        output.putBoolean("auto", this.auto);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getIntOr("progress", 0);
        this.total = input.getIntOr("total", 0);
        input.getIntArray("fuze_values").ifPresent(v -> System.arraycopy(v, 0, this.fuzeValues, 0, Math.min(v.length, this.fuzeValues.length)));
        this.auto = input.getBooleanOr("auto", false);
    }
}
