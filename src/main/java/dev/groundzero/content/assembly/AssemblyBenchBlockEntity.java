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
 * The Ordnance Assembly Bench (DESIGN.md §10, Phase 7). Two lines, one job at a time, 10 FE a tick:
 * <ul>
 *   <li>Warheads (slice 7a): a payload core, a casing of its class and a fuze; ASSEMBLE puts them together over 5-12 s
 *   into the core's warhead, carrying the fuze and its setting. TAKE APART splits a warhead back into its parts.</li>
 *   <li>Missiles (slice 7b): a warhead, a body that takes its class, a motor that fits the body, and optionally a
 *   guidance unit and hardened electronics; ASSEMBLE makes a missile over 8 s, TAKE APART gives the parts back.</li>
 * </ul>
 * While it works the hoist lowers its load onto the cradle and the clamps close.
 */
public class AssemblyBenchBlockEntity extends MachineBlockEntity implements AnimatedMachine {
    public static final int SLOT_CORE = 0, SLOT_CASING = 1, SLOT_FUZE = 2, SLOT_WARHEAD = 3;
    public static final int SLOT_MISSILE_WARHEAD = 4, SLOT_BODY = 5, SLOT_MOTOR = 6, SLOT_GUIDANCE = 7, SLOT_MODULE = 8, SLOT_MISSILE = 9;
    public static final int BUTTON_ASSEMBLE = 0, BUTTON_TAKE_APART = 1, BUTTON_ASSEMBLE_MISSILE = 2, BUTTON_TAKE_APART_MISSILE = 3;
    /** The page tabs: button BUTTON_PAGE + page. */
    public static final int BUTTON_PAGE = 4;
    public static final int ENERGY_PER_TICK = 10, CAPACITY = 20_000;
    /** The two jobs (and the two pages of the screen). */
    public static final int JOB_WARHEAD = 0, JOB_MISSILE = 1;

    private static final String T_MAKES = GZLang.add(GroundZero.MODID + ".assembly.makes", "Makes: %s");
    private static final String T_READY = GZLang.add(GroundZero.MODID + ".assembly.ready", "%s, %s fuze: press Assemble");
    private static final String T_WORKING = GZLang.add(GroundZero.MODID + ".assembly.working", "Assembling: %s%%");
    private static final String T_DONE = GZLang.add(GroundZero.MODID + ".assembly.done", "Finished: %s");
    private static final String T_NO_POWER = GZLang.add(GroundZero.MODID + ".assembly.no_power", "Needs power: 10 FE a tick");
    private static final String T_IDLE = GZLang.add(GroundZero.MODID + ".assembly.idle", "Core, casing and fuze make a warhead");
    private static final String T_APART = GZLang.add(GroundZero.MODID + ".assembly.apart", "Clear the three part slots to take it apart");
    private static final String T_MISSILE_IDLE = GZLang.add(GroundZero.MODID + ".assembly.missile_idle", "Warhead, body and motor make a missile");
    private static final String T_MISSILE_READY = GZLang.add(GroundZero.MODID + ".assembly.missile_ready", "Range %s, %s: press Assemble");
    private static final String T_MISSILE_APART = GZLang.add(GroundZero.MODID + ".assembly.missile_apart", "Clear the part slots to take it apart");
    private static final String T_BUSY = GZLang.add(GroundZero.MODID + ".assembly.busy", "Busy with the other page's job");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Ticks of work done on the current job, how many it needs (0: no job), and which job it is. */
    private int progress, total, job;
    /** Each fuze's setting, as last set on the screen (or taken from a warhead taken apart), in Fuze order. */
    private final int[] fuzeValues = new int[Fuze.values().length];
    {
        for (Fuze f : Fuze.values()) {
            this.fuzeValues[f.ordinal()] = f.setting.fallback;
        }
    }
    /**
     * The page it is on (JOB_WARHEAD or JOB_MISSILE): the screen shows it, shift-clicks land on it, and pipes and hoppers
     * feed and empty only its slots. Saved, and switched by the screen's tabs.
     */
    private int page = JOB_WARHEAD;

    public AssemblyBenchBlockEntity(BlockPos pos, BlockState state) {
        super(GZAssembly.BENCH_ENTITY.get(), pos, state, CAPACITY, 200, 0, 10,
                new int[] {SLOT_CORE, SLOT_CASING, SLOT_FUZE, SLOT_MISSILE_WARHEAD, SLOT_BODY, SLOT_MOTOR, SLOT_GUIDANCE, SLOT_MODULE},
                new int[] {SLOT_WARHEAD, SLOT_MISSILE});
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_CORE -> stack.getItem() instanceof ComponentItems.PayloadCore;
            case SLOT_CASING -> stack.getItem() instanceof ComponentItems.Casing;
            case SLOT_FUZE -> stack.getItem() instanceof ComponentItems.FuzeItem;
            case SLOT_WARHEAD, SLOT_MISSILE_WARHEAD -> WarheadAssembly.isWarhead(stack);
            case SLOT_BODY -> stack.getItem() instanceof MissileParts.Body;
            case SLOT_MOTOR -> stack.getItem() instanceof MissileParts.MotorItem;
            case SLOT_GUIDANCE -> stack.getItem() instanceof MissileParts.GuidanceItem;
            case SLOT_MODULE -> stack.getItem() instanceof MissileParts.HardenedModule;
            case SLOT_MISSILE -> stack.getItem() instanceof MissileItem && MissileItem.spec(stack) != null && !MissileItem.spec(stack).intercontinental();
            default -> false;
        };
    }

    private ItemStack slot(int i) {
        return this.inventory.getItem(i);
    }

    public int page() {
        return this.page;
    }

    /** Whether a slot belongs to a page: slots 0-3 to the warheads, 4-9 to the missiles. */
    public static boolean onPage(int slot, int page) {
        return page == JOB_MISSILE ? slot >= SLOT_MISSILE_WARHEAD : slot < SLOT_MISSILE_WARHEAD;
    }

    @Override
    protected boolean slotAutomated(int slot) {
        return onPage(slot, this.page);
    }

    @Override
    public boolean slotShown(int slot, MachineMenu menu) {
        return onPage(slot, menu.extra(DATA_PAGE));
    }

    public WarheadAssembly.Problem problem() {
        return WarheadAssembly.check(slot(SLOT_CORE), slot(SLOT_CASING), slot(SLOT_FUZE), slot(SLOT_WARHEAD));
    }

    public MissileAssembly.Problem missileProblem() {
        return MissileAssembly.check(slot(SLOT_MISSILE_WARHEAD), slot(SLOT_BODY), slot(SLOT_MOTOR), slot(SLOT_MISSILE));
    }

    public boolean assembling() {
        return this.total > 0;
    }

    public int job() {
        return this.job;
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
    @Override
    public boolean handleButton(int button) {
        return switch (button) {
            case BUTTON_ASSEMBLE -> startAssembly();
            case BUTTON_TAKE_APART -> takeApart();
            case BUTTON_ASSEMBLE_MISSILE -> startMissile();
            case BUTTON_TAKE_APART_MISSILE -> takeApartMissile();
            case BUTTON_PAGE + JOB_WARHEAD, BUTTON_PAGE + JOB_MISSILE -> setPage(button - BUTTON_PAGE);
            default -> false;
        };
    }

    /** Turns to a page (the screen's tabs); pipes and hoppers follow. */
    public boolean setPage(int page) {
        if (page != JOB_WARHEAD && page != JOB_MISSILE) {
            return false;
        }
        if (page != this.page) {
            this.page = page;
            setChanged();
            if (this.level != null) {
                this.level.invalidateCapabilities(this.worldPosition);
                this.level.invalidateCapabilities(sideHalfPos());
            }
        }
        return true;
    }

    /** ASSEMBLE on the warhead page: starts a job if the parts fit (and no job is running). */
    public boolean startAssembly() {
        if (assembling() || problem() != WarheadAssembly.Problem.NONE) {
            return false;
        }
        var core = (ComponentItems.PayloadCore) slot(SLOT_CORE).getItem();
        start(JOB_WARHEAD, WarheadAssembly.assemblyTicks(core.sizeClass));
        return true;
    }

    /** ASSEMBLE on the missile page. */
    public boolean startMissile() {
        if (assembling() || missileProblem() != MissileAssembly.Problem.NONE) {
            return false;
        }
        start(JOB_MISSILE, MissileAssembly.TICKS);
        return true;
    }

    private void start(int job, int ticks) {
        this.job = job;
        this.progress = 0;
        this.total = ticks;
        setChanged();
    }

    /** TAKE APART on the warhead page: the warhead in the output slot goes back into its parts (the part slots must be empty). */
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
        FuzeSettings old = warhead.getOrDefault(dev.groundzero.registry.GZDataComponents.FUZE.get(), FuzeSettings.IMPACT);
        this.fuzeValues[old.fuze().ordinal()] = old.value();             // reassembled, it keeps its setting
        warhead.shrink(1);
        this.inventory.setItem(SLOT_WARHEAD, warhead.isEmpty() ? ItemStack.EMPTY : warhead);
        clank(1.25f);
        return true;
    }

    /** TAKE APART on the missile page: the missile goes back into its warhead and parts (those slots must be empty). */
    public boolean takeApartMissile() {
        ItemStack missile = slot(SLOT_MISSILE);
        if (assembling() || missile.isEmpty()) {
            return false;
        }
        for (int i = SLOT_MISSILE_WARHEAD; i <= SLOT_MODULE; i++) {
            if (!slot(i).isEmpty()) {
                return false;
            }
        }
        List<ItemStack> parts = MissileAssembly.takeApart(missile);
        if (parts.size() != 5) {
            return false;
        }
        for (int i = 0; i < 5; i++) {
            this.inventory.setItem(SLOT_MISSILE_WARHEAD + i, parts.get(i));
        }
        this.inventory.setItem(SLOT_MISSILE, ItemStack.EMPTY);
        clank(1.25f);
        return true;
    }

    private void clank(float pitch) {
        if (this.level != null) {
            this.level.playSound(null, this.worldPosition, GZSounds.ASSEMBLY_CLANK.get(), SoundSource.BLOCKS, 1f, pitch);
        }
    }

    // --- working ----------------------------------------------------------------------------------------------------------
    @Override
    protected boolean tickMachine(ServerLevel level) {
        if (!assembling()) {
            this.status = MachineStatus.IDLE;
            return false;
        }
        boolean fits = this.job == JOB_MISSILE ? missileProblem() == MissileAssembly.Problem.NONE : problem() == WarheadAssembly.Problem.NONE;
        if (!fits) {                                           // a part was taken out: the job is dropped
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
            if (this.job == JOB_MISSILE) {
                finishMissile();
            } else {
                finish();
            }
            this.progress = this.total = 0;
            clank(1f);
        }
        setChanged();
        return true;
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

    private void finishMissile() {
        ItemStack out = MissileAssembly.assemble(slot(SLOT_MISSILE_WARHEAD), (MissileParts.Body) slot(SLOT_BODY).getItem(),
                (MissileParts.MotorItem) slot(SLOT_MOTOR).getItem(), slot(SLOT_GUIDANCE), slot(SLOT_MODULE));
        for (int i = SLOT_MISSILE_WARHEAD; i <= SLOT_MODULE; i++) {
            slot(i).shrink(1);
        }
        this.inventory.setItem(SLOT_MISSILE, out);
    }

    @Override
    public SoundEvent loopSound() {
        return GZSounds.MACHINE_ASSEMBLY_BENCH.get();
    }

    // --- screen -----------------------------------------------------------------------------------------------------------
    public static final int DATA_PROGRESS = 0, DATA_TOTAL = 1, DATA_PROBLEM = 2, DATA_FUZE_VALUE = 3, DATA_MISSILE_PROBLEM = 4, DATA_JOB = 5,
            DATA_PAGE = 6;

    @Override
    protected int extraDataCount() {
        return 7;
    }

    @Override
    protected int getExtraData(int index) {
        return switch (index) {
            case DATA_PROGRESS -> this.progress;
            case DATA_TOTAL -> this.total;
            case DATA_FUZE_VALUE -> fuzeValue();
            case DATA_MISSILE_PROBLEM -> missileProblem().ordinal();
            case DATA_JOB -> this.job;
            case DATA_PAGE -> this.page;
            default -> problem().ordinal();
        };
    }

    @Override
    public List<Component> readout(MachineMenu menu) {
        int total = menu.extra(DATA_TOTAL);
        int shown = menu.extra(DATA_PAGE);
        boolean otherJob = total > 0 && menu.extra(DATA_JOB) != shown;
        Component working = otherJob ? Component.translatable(T_BUSY) : menu.status() == MachineStatus.NO_POWER
                ? Component.translatable(T_NO_POWER).withStyle(ChatFormatting.RED) : Component.translatable(T_WORKING, total > 0 ? menu.extra(DATA_PROGRESS) * 100 / total : 0);
        return shown == JOB_MISSILE ? missileReadout(menu, total > 0, working) : warheadReadout(menu, total > 0, working);
    }

    private List<Component> warheadReadout(MachineMenu menu, boolean busy, Component working) {
        ItemStack core = slot(SLOT_CORE), fuze = slot(SLOT_FUZE), out = slot(SLOT_WARHEAD);
        var problem = WarheadAssembly.Problem.values()[Math.clamp(menu.extra(DATA_PROBLEM), 0, WarheadAssembly.Problem.values().length - 1)];
        Component first = core.getItem() instanceof ComponentItems.PayloadCore c
                ? Component.translatable(T_MAKES, c.warhead().getName()) : !out.isEmpty() ? Component.translatable(T_DONE, out.getHoverName())
                : Component.translatable(T_IDLE);
        Component second;
        if (busy) {
            second = working;
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

    private List<Component> missileReadout(MachineMenu menu, boolean busy, Component working) {
        ItemStack warhead = slot(SLOT_MISSILE_WARHEAD), body = slot(SLOT_BODY), motor = slot(SLOT_MOTOR), out = slot(SLOT_MISSILE);
        var problem = MissileAssembly.Problem.values()[Math.clamp(menu.extra(DATA_MISSILE_PROBLEM), 0, MissileAssembly.Problem.values().length - 1)];
        Component first = body.getItem() instanceof MissileParts.Body b
                ? Component.translatable(T_MAKES, Component.translatable(MissileItem.T_NAMED, Component.translatable(MissileParts.bodyName(b.sizeClass))))
                : !out.isEmpty() ? Component.translatable(T_DONE, out.getHoverName()) : Component.translatable(T_MISSILE_IDLE);
        Component second;
        if (busy) {
            second = working;
        } else if (problem == MissileAssembly.Problem.NONE && motor.getItem() instanceof MissileParts.MotorItem m) {
            MissileParts.Guidance g = slot(SLOT_GUIDANCE).getItem() instanceof MissileParts.GuidanceItem gi ? gi.guidance : MissileParts.Guidance.UNGUIDED;
            second = Component.translatable(T_MISSILE_READY, m.motor.range, Component.translatable(MissileParts.guidanceName(g)));
        } else if (problem == MissileAssembly.Problem.WARHEAD_TOO_BIG && WarheadAssembly.isWarhead(warhead)) {
            SizeClass size = WarheadAssembly.sizeOf((dev.groundzero.content.ordnance.charge.ChargeBlock) ((net.minecraft.world.item.BlockItem) warhead.getItem()).getBlock());
            second = Component.translatable(problem.key, size == null ? "?" : size.name()).withStyle(ChatFormatting.RED);
        } else if (problem == MissileAssembly.Problem.MOTOR_DOES_NOT_FIT && motor.getItem() instanceof MissileParts.MotorItem m) {
            second = Component.translatable(problem.key, m.motor.bodies()).withStyle(ChatFormatting.RED);
        } else if (problem == MissileAssembly.Problem.NO_WARHEAD && !out.isEmpty() && body.isEmpty()) {
            second = Component.translatable(T_MISSILE_APART);
        } else if (problem != MissileAssembly.Problem.NONE) {
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
    /** The model shows a warhead on the cradle while a job runs or a finished warhead or missile waits. */
    @Override
    protected void onSlotChanged(int slot) {
        super.onSlotChanged(slot);
        if (slot == SLOT_WARHEAD || slot == SLOT_MISSILE || slot < 0) {
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
        boolean shown = getBlockState().getValue(dev.groundzero.content.machine.MachineBlock.ACTIVE) || !slot(SLOT_WARHEAD).isEmpty()
                || !slot(SLOT_MISSILE).isEmpty();
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
        output.putInt("job", this.job);
        output.putInt("page", this.page);
        output.putIntArray("fuze_values", this.fuzeValues);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getIntOr("progress", 0);
        this.total = input.getIntOr("total", 0);
        this.job = input.getIntOr("job", JOB_WARHEAD);
        this.page = input.getIntOr("page", JOB_WARHEAD) == JOB_MISSILE ? JOB_MISSILE : JOB_WARHEAD;
        input.getIntArray("fuze_values").ifPresent(v -> System.arraycopy(v, 0, this.fuzeValues, 0, Math.min(v.length, this.fuzeValues.length)));
    }
}
