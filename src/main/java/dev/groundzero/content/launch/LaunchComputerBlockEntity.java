package dev.groundzero.content.launch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.groundzero.content.machine.AnimatedMachine;
import dev.groundzero.content.machine.MachineBlock;
import dev.groundzero.content.machine.MachineBlockEntity;
import dev.groundzero.content.machine.MachineStatus;
import dev.groundzero.content.machine.SideConfig;
import dev.groundzero.content.multiblock.MultiblockController;
import dev.groundzero.content.multiblock.MultiblockDefinition;
import dev.groundzero.content.multiblock.MultiblockPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

/**
 * The Launch Computer (DESIGN.md §17.3, §18.1, basic version): a terminal desk with a radar mast and dish behind it and
 * an antenna on top. Its radar (20 FE/t; the dish turns while it is powered) watches 512 blocks round: the silos it
 * links (formed silos within 256 blocks), and every missile in the air. From its screen you aim each silo at its own
 * target and arm it; LAUNCH then fires every armed silo it links.
 */
public class LaunchComputerBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController {
    public static final int ENERGY_CAPACITY = 20_000, RADAR_USE = 20, RADAR_RANGE = 512;
    private static final int RECHECK_INTERVAL = 40, SCOPE_INTERVAL = 10;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Players with this computer's screen open (they are sent its scope). */
    private final Set<UUID> viewers = new HashSet<>();
    private boolean powered;

    public LaunchComputerBlockEntity(BlockPos pos, BlockState state) {
        super(GZLaunch.COMPUTER_ENTITY.get(), pos, state, ENERGY_CAPACITY, 1_000, 0, 0, new int[0], new int[0]);
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }

    public boolean powered() {
        return this.powered;
    }

    /** A silo this computer runs (it formed and powered, the silo within its reach), or null. */
    private @Nullable LaunchSiloBlockEntity linked(ServerLevel level, BlockPos silo) {
        if (!isFormed() || !this.powered || silo.distSqr(this.worldPosition) > (double) Launches.LINK_RANGE * Launches.LINK_RANGE) {
            return null;
        }
        return level.getBlockEntity(silo) instanceof LaunchSiloBlockEntity s ? s : null;
    }

    /** Aims one silo. */
    public boolean setTarget(ServerLevel level, BlockPos silo, int x, int z) {
        LaunchSiloBlockEntity s = linked(level, silo);
        return s != null && s.setTarget(x, z);
    }

    /** Arms one silo (it must be ready, with a target in range) or makes it safe. */
    public boolean arm(ServerLevel level, BlockPos silo, boolean on) {
        LaunchSiloBlockEntity s = linked(level, silo);
        return s != null && s.arm(on);
    }

    /** LAUNCH: every armed silo this computer links starts its countdown. Returns how many did. */
    public int launchArmed(ServerLevel level, @Nullable ServerPlayer by) {
        if (!isFormed() || !this.powered) {
            return 0;
        }
        int n = 0;
        for (LaunchSiloBlockEntity s : Launches.silosNear(level, this.worldPosition)) {
            if (s.launchArmed(level, by)) {
                n++;
            }
        }
        return n;
    }

    void addViewer(ServerPlayer player) {
        this.viewers.add(player.getUUID());
        sendScope((ServerLevel) player.level(), player);
    }

    void removeViewer(ServerPlayer player) {
        this.viewers.remove(player.getUUID());
    }

    // --- multiblock -------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return GZLaunch.COMPUTER;
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

    // --- ticking ----------------------------------------------------------------------------------------------------------
    @Override
    protected boolean tickMachine(ServerLevel level) {
        if ((level.getGameTime() + this.worldPosition.hashCode()) % RECHECK_INTERVAL == 0) {
            revalidate();
        }
        if (!isFormed()) {
            this.status = MachineStatus.INCOMPLETE;
            this.powered = false;
        } else {
            this.powered = this.energy.consume(RADAR_USE);
            this.status = this.powered ? MachineStatus.RUNNING : MachineStatus.NO_POWER;
        }
        if (!this.viewers.isEmpty() && level.getGameTime() % SCOPE_INTERVAL == 0) {
            for (UUID id : List.copyOf(this.viewers)) {
                if (level.getPlayerByUUID(id) instanceof ServerPlayer p && p.position().distanceTo(Vec3.atCenterOf(this.worldPosition)) < 16) {
                    sendScope(level, p);
                } else {
                    this.viewers.remove(id);
                }
            }
        }
        return this.powered;
    }

    /** What the scope shows: the linked silos (with their targets), and the missiles in the air within range. */
    private void sendScope(ServerLevel level, ServerPlayer player) {
        List<LaunchPayloads.Silo> silos = new ArrayList<>();
        if (isFormed()) {
            for (LaunchSiloBlockEntity s : Launches.silosNear(level, this.worldPosition)) {
                BlockPos t = s.target();
                silos.add(new LaunchPayloads.Silo(s.getBlockPos(), s.readiness().ordinal(), s.countdown(), s.armed(), t != null,
                        t == null ? 0 : t.getX(), t == null ? 0 : t.getZ()));
            }
        }
        List<LaunchPayloads.Contact> contacts = new ArrayList<>();
        if (this.powered) {
            Vec3 c = Vec3.atCenterOf(this.worldPosition);
            for (MissileEntity m : level.getEntitiesOfClass(MissileEntity.class, new AABB(c, c).inflate(RADAR_RANGE, 1024, RADAR_RANGE))) {
                Vec3 v = m.getDeltaMovement();
                contacts.add(new LaunchPayloads.Contact((int) m.getX(), (int) m.getY(), (int) m.getZ(), (float) Math.atan2(v.x, v.z),
                        (int) m.target().x, (int) m.target().z));
            }
        }
        PacketDistributor.sendToPlayer(player, new LaunchPayloads.Scope(this.worldPosition, isFormed(), this.powered,
                (int) ((long) this.energy.stored() * 100 / Math.max(1, this.energy.capacity())), silos, contacts));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return null;                                   // its screen is the radar scope, opened by LaunchComputerBlock
    }

    // --- model ------------------------------------------------------------------------------------------------------------
    @Override
    public Set<String> hiddenBones() {
        return isFormed() ? Set.of() : Set.of("frame");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
