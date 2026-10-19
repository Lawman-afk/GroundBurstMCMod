package dev.groundzero.content.machine;

import java.util.List;
import java.util.Set;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundzero.GroundZero;
import dev.groundzero.content.multiblock.GZMultiblocks;
import dev.groundzero.content.multiblock.MultiblockController;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.nuclear.GZNuclear;
import dev.groundzero.content.power.PowerUtil;
import dev.groundzero.registry.GZBlockEntities;
import dev.groundzero.registry.GZLang;
import dev.groundzero.registry.GZMenus;
import dev.groundzero.registry.GZSounds;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Fission Reactor (DESIGN.md §7, §23.3): a 3x3x4 multiblock run from this controller. A game abstraction, not a
 * reactor design: fuel rods in, a control-rod setting, water coolant and a heat gauge.
 *
 * <ul>
 * <li>Power = 1 200 FE/t x (fuel slots loaded / 4) x (rods withdrawn %). One rod's worth of fuel lasts 12 000 ticks of
 *     full power; a used-up rod comes out as a spent fuel rod.</li>
 * <li>Coolant: up to 25 mB of water a tick at full power. Cooled, the core settles at 20 + 480 x power °C; without
 *     enough water it heats up quickly. At 900 °C it SCRAMs (all rods in) until the rods are withdrawn again. A
 *     damaging meltdown is not part of the game (yet): it would come as a server config option with the explosion
 *     engine.</li>
 * <li>Breeding: a DU or Li-6 target rod in a target slot becomes irradiated after 6 000 ticks of full power.</li>
 * </ul>
 * Slots: 0-3 fuel rods, 4-5 target rods, 6 spent fuel, 7-8 irradiated targets. Tank: water.
 */
public class FissionReactorBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController {
    public static final int MAX_OUTPUT = 1_200;
    public static final int CAPACITY = 200_000;
    public static final int PUSH = 4_800;
    public static final int ROD_LIFE = 12_000;
    public static final int BREED_TIME = 6_000;
    public static final int WATER_AT_FULL_POWER = 25;
    public static final int SCRAM_HEAT = 900, WARN_HEAT = 700, AMBIENT = 20;
    public static final int FUEL_SLOTS = 4, TARGET_START = 4, SPENT_SLOT = 6, IRRADIATED_START = 7;
    /** Sync slots: core heat (°C), rod insertion (%), output (FE/t), fuel burnup of the current rod (%). */
    public static final int DATA_HEAT = 0, DATA_RODS = 1, DATA_POWER = 2, DATA_BURNUP = 3;
    public static final int BUTTON_WITHDRAW = 0, BUTTON_INSERT = 1, BUTTON_SCRAM = 2;
    private static final int RECHECK_INTERVAL = 40;

    public static final String CORE = GZLang.add(GroundZero.MODID + ".gui.core", "CORE %s C");
    public static final String RODS = GZLang.add(GroundZero.MODID + ".gui.rods", "RODS IN %s%%");
    public static final String SCRAM_MESSAGE = GZLang.add(GroundZero.MODID + ".reactor.scram", "Reactor SCRAM: the core overheated. Check the coolant.");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int insertion = 100;
    private float heat = AMBIENT;
    private float burnup;
    private final float[] breed = new float[2];
    private boolean scrammed;
    private int power;

    public FissionReactorBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.FISSION_REACTOR.get(), pos, state, CAPACITY, 0, PUSH, 9, new int[] {0, 1, 2, 3, 4, 5}, new int[] {6, 7, 8},
                List.of(MachineTank.Spec.water(16_000)));
    }

    // --- multiblock ---------------------------------------------------------------------------------------------------------
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

    @Override
    public void revalidate() {
        StructureMachines.revalidate(this, this);
    }

    @Override
    public boolean faceOpen(SideConfig.Face face) {
        return StructureMachines.faceOpen(this, face);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        StructureMachines.unformOnRemove(this, this, state);
    }

    // --- items ---------------------------------------------------------------------------------------------------------------
    private static boolean isTarget(ItemStack stack) {
        return stack.is(GZNuclear.DU_TARGET_ROD.get()) || stack.is(GZNuclear.LI6_TARGET_ROD.get());
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        if (slot < FUEL_SLOTS) {
            return stack.is(GZNuclear.URANIUM_FUEL_ROD.get());
        }
        return slot < SPENT_SLOT && isTarget(stack);
    }

    // --- the core ------------------------------------------------------------------------------------------------------------
    /** Fraction of full power the core runs at: loaded fuel slots / 4 times rods withdrawn. */
    public float powerLevel() {
        if (this.scrammed) {
            return 0;
        }
        int loaded = 0;
        for (int i = 0; i < FUEL_SLOTS; i++) {
            if (!this.inventory.getItem(i).isEmpty()) {
                loaded++;
            }
        }
        return loaded / (float) FUEL_SLOTS * (100 - this.insertion) / 100f;
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

    /** Breeding progress of target slot {@code t} (0 or 1), in full-power ticks. */
    public float breedProgress(int t) {
        return this.breed[t];
    }

    /** For GameTests: jumps a target's breeding progress ahead (a full breed takes 6 000 ticks). */
    public void setBreedProgress(int t, float progress) {
        this.breed[t] = progress;
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        if ((level.getGameTime() + this.worldPosition.hashCode()) % RECHECK_INTERVAL == 0) {
            revalidate();
        }
        if (!isFormed()) {
            this.status = MachineStatus.INCOMPLETE;
            this.power = 0;
            this.heat += (AMBIENT - this.heat) * 0.003f;
            return false;
        }
        float lvl = powerLevel();
        if (lvl > 0 && !this.inventory.getItem(SPENT_SLOT).isEmpty()
                && this.inventory.getItem(SPENT_SLOT).getCount() >= this.inventory.getItem(SPENT_SLOT).getMaxStackSize()) {
            lvl = 0;                                   // nowhere to put the next spent rod
            this.status = MachineStatus.OUTPUT_FULL;
        }
        // Coolant and heat.
        MachineTank water = this.tanks.get(0);
        int need = (int) Math.ceil(WATER_AT_FULL_POWER * lvl);
        boolean cooled = need == 0 || water.amount() >= need;
        if (cooled && need > 0) {
            water.drainInternal(need);
        }
        if (lvl <= 0) {
            this.heat += (AMBIENT - this.heat) * 0.003f;
        } else if (cooled) {
            this.heat += (AMBIENT + 480 * lvl - this.heat) * 0.01f;
        } else {
            this.heat += 1.5f + 3.5f * lvl;
        }
        if (this.heat >= SCRAM_HEAT && !this.scrammed) {
            scram(level);
            lvl = 0;
        }
        // Power, fuel burnup, breeding.
        this.power = Math.round(MAX_OUTPUT * lvl);
        if (lvl > 0) {
            this.energy.generate(this.power);
            this.burnup += lvl;
            if (this.burnup >= ROD_LIFE) {
                this.burnup -= ROD_LIFE;
                useUpRod();
            }
            for (int t = 0; t < 2; t++) {
                ItemStack target = this.inventory.getItem(TARGET_START + t);
                if (!isTarget(target)) {
                    this.breed[t] = 0;
                    continue;
                }
                this.breed[t] += lvl;
                if (this.breed[t] >= BREED_TIME && irradiate(target)) {
                    this.breed[t] = 0;
                }
            }
            setChanged();
        }
        pushPower(level);
        if (this.scrammed) {
            this.status = MachineStatus.SCRAM;
        } else if (this.status == MachineStatus.OUTPUT_FULL && lvl == 0) {
            // keep OUTPUT_FULL
        } else if (lvl <= 0) {
            this.status = powerLevelIgnoringRods() > 0 ? MachineStatus.IDLE : MachineStatus.NO_FUEL;
        } else if (!cooled) {
            this.status = MachineStatus.NEED_FLUID;
        } else {
            this.status = this.heat >= WARN_HEAT ? MachineStatus.OVERHEATING : MachineStatus.RUNNING;
        }
        return lvl > 0;
    }

    private float powerLevelIgnoringRods() {
        for (int i = 0; i < FUEL_SLOTS; i++) {
            if (!this.inventory.getItem(i).isEmpty()) {
                return 1;
            }
        }
        return 0;
    }

    private void useUpRod() {
        for (int i = 0; i < FUEL_SLOTS; i++) {
            if (!this.inventory.getItem(i).isEmpty()) {
                this.inventory.removeItem(i, 1);
                this.inventory.insertDirect(SPENT_SLOT, new ItemStack(GZNuclear.SPENT_FUEL_ROD.get()), false);
                return;
            }
        }
    }

    /** Swaps one target rod for its irradiated form in an output slot; false (and nothing changed) if no room. */
    private boolean irradiate(ItemStack target) {
        Item product = target.is(GZNuclear.DU_TARGET_ROD.get()) ? GZNuclear.IRRADIATED_DU_TARGET_ROD.get() : GZNuclear.IRRADIATED_LI6_TARGET_ROD.get();
        ItemStack out = new ItemStack(product);
        for (int s = IRRADIATED_START; s < IRRADIATED_START + 2; s++) {
            if (this.inventory.insertDirect(s, out, true).isEmpty()) {
                this.inventory.insertDirect(s, out, false);
                target.shrink(1);
                this.inventory.setChanged();
                return true;
            }
        }
        return false;
    }

    private void scram(ServerLevel level) {
        this.scrammed = true;
        this.insertion = 100;
        syncRods();
        for (Player player : level.players()) {
            if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(this.worldPosition)) < 48 * 48) {
                player.sendOverlayMessage(Component.translatable(SCRAM_MESSAGE).withStyle(net.minecraft.ChatFormatting.RED));
            }
        }
    }

    /** Pushes power out of the controller's open faces and every Energy Port. */
    private void pushPower(ServerLevel level) {
        PowerUtil.pushToNeighbours(level, this.worldPosition, this.energy, PUSH, side -> faceOpen(face(side)) && this.sides.energy(face(side)));
        for (MultiblockDefinition.Cell cell : definition().cells(this.worldPosition, controllerFacing())) {
            if (cell.key() == 'E') {
                PowerUtil.pushToNeighbours(level, cell.pos(), this.energy, PUSH);
            }
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
        syncRods();
        return true;
    }

    private void syncRods() {
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- GUI -----------------------------------------------------------------------------------------------------------------
    @Override
    public List<Component> readout(MachineMenu menu) {
        return List.of(Component.translatable(MachineText.OUTPUT, menu.extra(DATA_POWER)),
                Component.translatable(CORE, menu.extra(DATA_HEAT)),
                Component.translatable(RODS, menu.extra(DATA_RODS)));
    }

    @Override
    protected int extraDataCount() {
        return 4;
    }

    @Override
    protected int getExtraData(int index) {
        return switch (index) {
            case DATA_HEAT -> Math.round(this.heat);
            case DATA_RODS -> this.insertion;
            case DATA_POWER -> this.power;
            case DATA_BURNUP -> (int) (this.burnup * 100 / ROD_LIFE);
            default -> 0;
        };
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.FISSION_REACTOR.get(), containerId, inventory, this, MachineLayouts.FISSION_REACTOR, this.data);
    }

    // --- rendering -----------------------------------------------------------------------------------------------------------
    /** The core's water, seen through the reactor glass (see tools/assets/fission_reactor.py). */
    private static final List<TankRender> CORE_WATER = List.of(new TankRender(0, -7.5f, 16.5f, 8.5f, 7.5f, 47.5f, 23.5f));

    @Override
    public List<TankRender> tankRenders() {
        return CORE_WATER;
    }

    @Override
    public boolean showsTanks() {
        return isFormed();
    }

    /** Control-rod positions shown on the model, one per 25% of insertion (bones rods0..rods4, rods4 = fully in). */
    @Override
    public Set<String> hiddenBones() {
        if (!isFormed()) {
            return Set.of("frame");
        }
        Set<String> hidden = new java.util.HashSet<>();
        int shown = Math.round(this.insertion / 25f);
        for (int i = 0; i <= 4; i++) {
            if (i != shown) {
                hidden.add("rods" + i);
            }
        }
        boolean active = getBlockState().hasProperty(MachineBlock.ACTIVE) && getBlockState().getValue(MachineBlock.ACTIVE);
        if (!active) {
            hidden.add("glow");
        }
        return hidden;
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
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
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
        output.putFloat("burnup", this.burnup);
        output.putFloat("breed0", this.breed[0]);
        output.putFloat("breed1", this.breed[1]);
        output.putBoolean("scrammed", this.scrammed);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.insertion = input.getIntOr("insertion", 100);
        this.heat = input.getFloatOr("heat", AMBIENT);
        this.burnup = input.getFloatOr("burnup", 0);
        this.breed[0] = input.getFloatOr("breed0", 0);
        this.breed[1] = input.getFloatOr("breed1", 0);
        this.scrammed = input.getBooleanOr("scrammed", false);
    }
}
