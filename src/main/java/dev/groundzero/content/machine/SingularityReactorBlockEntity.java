package dev.groundzero.content.machine;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundzero.content.multiblock.GZMultiblocks;
import dev.groundzero.content.multiblock.MultiblockController;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.nuclear.GZNuclear;
import dev.groundzero.content.power.PowerUtil;
import dev.groundzero.registry.GZBlockEntities;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

/**
 * Singularity Reactor (DESIGN.md §7, §23.3): a 5x5x5 cage run from this controller. A game abstraction: a seed held in
 * a powered cage turns whatever matter it is fed into power.
 *
 * <ul>
 * <li>Engaging: with a singularity seed in slot 0 and 500 000 FE in the buffer (charged through the Energy Port), the
 *     seed is drawn into the cage and becomes the core. There is no taking it out again.</li>
 * <li>Holding the cage costs 1 000 FE every tick, redstone or not. If the buffer cannot pay, or the structure is broken
 *     while a core is held, the singularity collapses and the core is lost.</li>
 * <li>Matter: every item fed (slot 1, through the Item Port) adds 200 000 FE of accreted matter, up to 4 000 000; the
 *     core turns up to 25 000 FE of it into power a tick. It only pushes out power above a 500 000 FE reserve, so the
 *     cage keeps about 25 seconds of grace when the feed stops.</li>
 * </ul>
 */
public class SingularityReactorBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController {
    public static final int MAX_OUTPUT = 25_000;
    public static final int CAPACITY = 5_000_000;
    public static final int TRANSFER = 32_768;
    public static final int ENGAGE = 500_000;
    public static final int RESERVE = 500_000;
    public static final int UPKEEP = 1_000;
    public static final int MASS_PER_ITEM = 200_000;
    public static final int MASS_CAP = 4_000_000;
    public static final int SEED_SLOT = 0, MATTER_SLOT = 1;
    /** Sync slots: accreted matter (%), output (FE/t), core held (0/1), cage charge toward engaging (%). */
    public static final int DATA_MASS = 0, DATA_POWER = 1, DATA_CORE = 2, DATA_CAGE = 3;
    private static final int RECHECK_INTERVAL = 40;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ReactorEnergyView port = new ReactorEnergyView(this.energy, () -> this.energy, () -> this.core ? RESERVE : 0);
    private boolean core;
    private int mass;
    private int power;
    /** What the model was last told (core held, accretion disk size), so clients only hear about real changes. */
    private int shownStage = -1;

    public SingularityReactorBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.SINGULARITY_REACTOR.get(), pos, state, CAPACITY, TRANSFER, TRANSFER, 2, new int[] {SEED_SLOT, MATTER_SLOT}, new int[0]);
    }

    // --- multiblock ---------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return GZMultiblocks.SINGULARITY_REACTOR;
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
        return false;
    }

    @Override
    public boolean hasSideConfig() {
        return false;
    }

    /** Breaking the controller of a cage that holds a core collapses it. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.core && this.level instanceof ServerLevel server) {
            collapse(server);
        }
        super.preRemoveSideEffects(pos, state);
        StructureMachines.unformOnRemove(this, this, state);
    }

    /** The Energy Port: charge in at any time, power out only above the reserve while a core is held. */
    @Override
    public @Nullable EnergyHandler energyFor(@Nullable Direction side) {
        return side == null ? this.port : null;
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        boolean seed = stack.is(GZNuclear.SINGULARITY_SEED.get());
        return slot == SEED_SLOT ? seed : !seed;
    }

    // --- the cage ------------------------------------------------------------------------------------------------------------
    public boolean holdsCore() {
        return this.core;
    }

    public int mass() {
        return this.mass;
    }

    /** The cage is held every tick, even while redstone halts the reactor's work. */
    @Override
    public void serverTick(ServerLevel level) {
        if (this.core && (!isFormed() || !this.energy.consume(UPKEEP))) {
            collapse(level);
        }
        super.serverTick(level);
        syncStage();
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        if ((level.getGameTime() + this.worldPosition.hashCode()) % RECHECK_INTERVAL == 0) {
            revalidate();
        }
        this.power = 0;
        if (!isFormed()) {
            this.status = MachineStatus.INCOMPLETE;
            return false;
        }
        if (!this.core) {
            boolean seed = !this.inventory.getItem(SEED_SLOT).isEmpty();
            if (seed && this.energy.stored() >= ENGAGE) {
                this.inventory.removeItem(SEED_SLOT, 1);
                this.core = true;
                this.mass = 0;
                setChanged();
            } else {
                this.status = seed ? MachineStatus.CHARGING : MachineStatus.NO_CORE;
                return false;
            }
        }
        // Accrete: one item a tick while there is room for its matter.
        ItemStack feed = this.inventory.getItem(MATTER_SLOT);
        if (!feed.isEmpty() && this.mass <= MASS_CAP - MASS_PER_ITEM) {
            this.inventory.removeItem(MATTER_SLOT, 1);
            this.mass += MASS_PER_ITEM;
        }
        int room = this.energy.capacity() - this.energy.stored();
        int made = Math.min(MAX_OUTPUT, Math.min(this.mass, room));
        if (made > 0) {
            this.mass -= made;
            this.energy.generate(made);
            this.power = made;
            setChanged();
        }
        pushPower(level);
        if (made > 0) {
            this.status = MachineStatus.RUNNING;
        } else {
            this.status = this.mass > 0 ? MachineStatus.BUFFER_FULL : MachineStatus.NO_FUEL;
        }
        return made > 0;
    }

    /** The cage lost containment: the core is gone, with a flash, a boom and a pull of particles toward the centre. */
    private void collapse(ServerLevel level) {
        this.core = false;
        this.mass = 0;
        setChanged();
        Vec3 centre = chamberCentre();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, centre.x, centre.y, centre.z, 160, 1.2, 1.2, 1.2, 0.4);
        level.sendParticles(ParticleTypes.SONIC_BOOM, centre.x, centre.y, centre.z, 1, 0, 0, 0, 0);
        level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFC7A0FF), centre.x, centre.y, centre.z, 1, 0, 0, 0, 0);
        level.playSound(null, centre.x, centre.y, centre.z, GZSounds.SINGULARITY_COLLAPSE.get(), SoundSource.BLOCKS, 3.0f, 1.0f);
        for (Player player : level.players()) {
            if (player.distanceToSqr(centre) < 48 * 48) {
                player.sendOverlayMessage(Component.translatable(MachineText.COLLAPSE_MESSAGE).withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
            }
        }
    }

    /** The middle of the cage (the core cradle's cell). */
    private Vec3 chamberCentre() {
        for (MultiblockDefinition.Cell cell : definition().cells(this.worldPosition, controllerFacing())) {
            if (cell.key() == 'K') {
                return Vec3.atCenterOf(cell.pos());
            }
        }
        return Vec3.atCenterOf(this.worldPosition);
    }

    /** Pushes power above the reserve out of the Energy Port. */
    private void pushPower(ServerLevel level) {
        int spare = Math.min(TRANSFER, this.energy.stored() - RESERVE);
        if (spare <= 0) {
            return;
        }
        for (MultiblockDefinition.Cell cell : definition().cells(this.worldPosition, controllerFacing())) {
            if (cell.key() == 'E') {
                PowerUtil.pushToNeighbours(level, cell.pos(), this.energy, spare);
            }
        }
    }

    /** 0 no core, 1 bare core, 2-4 small to large accretion disk. */
    private int stage() {
        if (!this.core) {
            return 0;
        }
        return this.mass <= 0 ? 1 : 2 + Math.min(2, this.mass * 3 / (MASS_CAP + 1));
    }

    private void syncStage() {
        int stage = stage();
        if (stage != this.shownStage && this.level != null && !this.level.isClientSide()) {
            this.shownStage = stage;
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- GUI -----------------------------------------------------------------------------------------------------------------
    @Override
    public List<Component> readout(MachineMenu menu) {
        return List.of(Component.translatable(MachineText.OUTPUT, menu.extra(DATA_POWER)),
                menu.extra(DATA_CORE) != 0 ? Component.translatable(MachineText.MASS, menu.extra(DATA_MASS))
                        : Component.translatable(MachineText.CAGE_CHARGE, menu.extra(DATA_CAGE)));
    }

    @Override
    protected int extraDataCount() {
        return 4;
    }

    @Override
    protected int getExtraData(int index) {
        return switch (index) {
            case DATA_MASS -> (int) ((long) this.mass * 100 / MASS_CAP);
            case DATA_POWER -> this.power;
            case DATA_CORE -> this.core ? 1 : 0;
            case DATA_CAGE -> this.core ? 100 : (int) Math.min(100, (long) this.energy.stored() * 100 / ENGAGE);
            default -> 0;
        };
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.SINGULARITY_REACTOR.get(), containerId, inventory, this, MachineLayouts.SINGULARITY_REACTOR, this.data);
    }

    // --- rendering -----------------------------------------------------------------------------------------------------------
    /** The core, its accretion disk (three sizes, one shown) and the emitter beams only show while a core is held. */
    @Override
    public Set<String> hiddenBones() {
        if (!isFormed()) {
            return Set.of("frame");
        }
        int stage = stage();
        Set<String> hidden = new java.util.HashSet<>();
        if (stage == 0) {
            hidden.addAll(List.of("core", "beams"));
        }
        for (int i = 0; i < 3; i++) {
            if (stage != 2 + i) {
                hidden.add("disk" + i);
            }
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
        return GZSounds.MACHINE_SINGULARITY_REACTOR.get();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("core", this.core);
        output.putInt("mass", this.mass);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.core = input.getBooleanOr("core", false);
        this.mass = input.getIntOr("mass", 0);
    }
}
