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
 * a powered cage eats matter and turns it into power. One rule sets the output: <b>every item eaten is worth 10 000
 * FE, and the core eats at most 50 items a second</b> (2 and 3 on alternate ticks), so 50 items a second gives the
 * full 25 000 FE a tick and 10 items a second gives 5 000.
 *
 * <ul>
 * <li>The seed sits in its slot like a battery in the charger and stays there. With a seed in and the buffer charged to
 *     500 000 FE (through the Energy Port) the cage engages and holds it. Taking the seed out lets go of it safely.</li>
 * <li>Holding the cage costs 1 000 FE every tick, redstone or not: the worth of 2 items a second just to keep it up.
 *     If the buffer cannot pay, or the structure is broken while it holds the seed, the singularity collapses and the
 *     seed is destroyed.</li>
 * <li>Matter comes from the four feed slots (any item but a seed), filled through Item Ports: the one in the floor's
 *     west side and any floor block swapped for an Item Port. Eaten matter waits in a store of up to 50 items' worth
 *     and becomes power at up to 25 000 FE a tick. Only power above a 500 000 FE reserve is pushed out, so the cage
 *     keeps about 25 seconds of grace when the feed stops.</li>
 * </ul>
 */
public class SingularityReactorBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController {
    public static final int MAX_OUTPUT = 25_000;
    public static final int CAPACITY = 5_000_000;
    public static final int TRANSFER = 32_768;
    public static final int ENGAGE = 500_000;
    public static final int RESERVE = 500_000;
    public static final int UPKEEP = 1_000;
    public static final int FE_PER_ITEM = 10_000;
    /** Most items eaten a second: with {@link #FE_PER_ITEM} this is what sets the 25 000 FE/t ceiling. */
    public static final int MAX_ITEMS_PER_SECOND = 50;
    public static final int MASS_CAP = 50 * FE_PER_ITEM;
    public static final int SEED_SLOT = 0, FEED_START = 1, FEED_SLOTS = 4;
    /** Sync slots: stored matter (%), output (FE/t), cage engaged (0/1), cage charge toward engaging (%), items eaten in the last second. */
    public static final int DATA_MASS = 0, DATA_POWER = 1, DATA_ENGAGED = 2, DATA_CAGE = 3, DATA_FEED = 4;
    private static final int RECHECK_INTERVAL = 40;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ReactorEnergyView port = new ReactorEnergyView(this.energy, () -> this.energy, () -> this.engaged ? RESERVE : 0);
    private boolean engaged;
    private int mass;
    private int power;
    /** Items eaten in each of the last 20 ticks, for the feed-rate readout. */
    private final int[] eaten = new int[20];
    /** What the model was last told (seed, cage, accretion disk size), so clients only hear about real changes. */
    private int shownStage = -1;

    public SingularityReactorBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.SINGULARITY_REACTOR.get(), pos, state, CAPACITY, TRANSFER, TRANSFER, 1 + FEED_SLOTS,
                new int[] {SEED_SLOT, 1, 2, 3, 4}, new int[0]);
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

    /** Breaking the controller while the cage holds the seed collapses it (the seed is destroyed, not dropped). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.engaged && this.level instanceof ServerLevel server) {
            collapse(server);
        }
        super.preRemoveSideEffects(pos, state);
        StructureMachines.unformOnRemove(this, this, state);
    }

    /** The Energy Port: charge in at any time, power out only above the reserve while the cage is engaged. */
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
    public boolean engaged() {
        return this.engaged;
    }

    public int mass() {
        return this.mass;
    }

    private boolean hasSeed() {
        return !this.inventory.getItem(SEED_SLOT).isEmpty();
    }

    /** Items eaten over the last second. */
    public int feedRate() {
        int sum = 0;
        for (int n : this.eaten) {
            sum += n;
        }
        return sum;
    }

    /** The cage is held every tick, even while redstone halts the reactor's work. */
    @Override
    public void serverTick(ServerLevel level) {
        if (this.engaged && !hasSeed()) {
            this.engaged = false;                       // the seed was taken out: the cage simply lets go
            setChanged();
        }
        if (this.engaged && (!isFormed() || !this.energy.consume(UPKEEP))) {
            collapse(level);
        }
        this.eaten[(int) (level.getGameTime() % this.eaten.length)] = 0;
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
        if (!this.engaged) {
            if (!hasSeed()) {
                this.status = MachineStatus.NO_CORE;
                return false;
            }
            if (this.energy.stored() < ENGAGE) {
                this.status = MachineStatus.CHARGING;
                return false;
            }
            this.engaged = true;
            setChanged();
        }
        // Eat: 2 or 3 items a tick (50 a second), while the store has room.
        int bite = 2 + (int) (level.getGameTime() & 1);
        int ate = 0;
        for (int slot = FEED_START; slot < FEED_START + FEED_SLOTS && ate < bite; slot++) {
            while (ate < bite && this.mass + FE_PER_ITEM <= MASS_CAP && !this.inventory.getItem(slot).isEmpty()) {
                this.inventory.removeItem(slot, 1);
                this.mass += FE_PER_ITEM;
                ate++;
            }
        }
        this.eaten[(int) (level.getGameTime() % this.eaten.length)] = ate;
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

    /** The cage lost containment: the seed is destroyed, with a flash, a boom and a pull of particles toward the centre. */
    private void collapse(ServerLevel level) {
        this.engaged = false;
        this.mass = 0;
        this.inventory.removeItem(SEED_SLOT, 1);
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
        List<BlockPos> cradle = definition().positionsOf('K', this.worldPosition, controllerFacing());
        return Vec3.atCenterOf(cradle.isEmpty() ? this.worldPosition : cradle.get(0));
    }

    /** Pushes power above the reserve out of the Energy Port. */
    private void pushPower(ServerLevel level) {
        int spare = Math.min(TRANSFER, this.energy.stored() - RESERVE);
        if (spare <= 0) {
            return;
        }
        for (BlockPos port : definition().positionsOf('E', this.worldPosition, controllerFacing())) {
            PowerUtil.pushToNeighbours(level, port, this.energy, spare);
        }
    }

    /** 0 no seed, 1 seed resting in the cradle, 2 cage holding it, 3-5 small to large accretion disk. */
    private int stage() {
        if (!hasSeed()) {
            return 0;
        }
        if (!this.engaged) {
            return 1;
        }
        return this.mass <= 0 ? 2 : 3 + Math.min(2, (int) ((long) this.mass * 3 / (MASS_CAP + 1)));
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
                menu.extra(DATA_ENGAGED) != 0 ? Component.translatable(MachineText.FEED, menu.extra(DATA_FEED))
                        : Component.translatable(MachineText.CAGE_CHARGE, menu.extra(DATA_CAGE)));
    }

    @Override
    protected int extraDataCount() {
        return 5;
    }

    @Override
    protected int getExtraData(int index) {
        return switch (index) {
            case DATA_MASS -> (int) ((long) this.mass * 100 / MASS_CAP);
            case DATA_POWER -> this.power;
            case DATA_ENGAGED -> this.engaged ? 1 : 0;
            case DATA_CAGE -> this.engaged ? 100 : (int) Math.min(100, (long) this.energy.stored() * 100 / ENGAGE);
            case DATA_FEED -> feedRate();
            default -> 0;
        };
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.SINGULARITY_REACTOR.get(), containerId, inventory, this, MachineLayouts.SINGULARITY_REACTOR, this.data);
    }

    // --- rendering -----------------------------------------------------------------------------------------------------------
    /**
     * The seed shows in the cradle while it is in its slot; the beams while the cage holds it; the accretion disk (three
     * sizes, one shown) while it has matter to eat. Item hatches show on the floor cells that are Item Ports.
     */
    @Override
    public Set<String> hiddenBones() {
        if (!isFormed()) {
            return Set.of("frame");
        }
        int stage = stage();
        Set<String> hidden = new java.util.HashSet<>();
        if (stage == 0) {
            hidden.add("core");
        }
        if (stage < 2) {
            hidden.add("beams");
        }
        for (int i = 0; i < 3; i++) {
            if (stage != 3 + i) {
                hidden.add("disk" + i);
            }
        }
        if (this.level != null) {
            for (MultiblockDefinition.Cell cell : definition().cells(this.worldPosition, controllerFacing())) {
                if (cell.key() == 'B' && !this.level.getBlockState(cell.pos()).is(GZMultiblocks.ITEM_PORT.block().get())) {
                    hidden.add("hatch_" + cell.col() + "_" + cell.row());
                }
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
        output.putBoolean("engaged", this.engaged);
        output.putInt("mass", this.mass);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.engaged = input.getBooleanOr("engaged", false);
        this.mass = input.getIntOr("mass", 0);
    }
}
