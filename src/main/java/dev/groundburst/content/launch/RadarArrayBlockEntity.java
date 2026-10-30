package dev.groundburst.content.launch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.groundburst.content.machine.AnimatedMachine;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.machine.MachineStatus;
import dev.groundburst.content.machine.SideConfig;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

/**
 * The Radar Array (DESIGN.md §18.1, early): a deck, a mast and a big turning dish that tracks every missile in the air
 * within {@link #RANGE} blocks, whoever fired it, and calls the ones coming down within {@link #INCOMING_RADIUS} of it
 * incoming. Its screen is a sweeping radar scope; a comparator reads how many are incoming (up to 15). It is the
 * anti-missile defence's eyes: an Interceptor Battery engages what it tracks at full reach (DESIGN.md §18.2). Runs on
 * 100 FE/t.
 */
public class RadarArrayBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController, ScreenWatched {
    public static final int ENERGY_CAPACITY = 100_000, POWER_USE = 100, RANGE = 2048, INCOMING_RADIUS = 512;
    private static final int RECHECK_INTERVAL = 40, SCAN_INTERVAL = 10;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Set<UUID> viewers = new HashSet<>();
    private boolean powered;
    private int incoming;

    public RadarArrayBlockEntity(BlockPos pos, BlockState state) {
        super(GBLaunch.RADAR_ENTITY.get(), pos, state, ENERGY_CAPACITY, 2_000, 0, 0, new int[0], new int[0]);
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }

    /** How many missiles are coming down near it (what a comparator reads, up to 15). */
    public int incoming() {
        return this.incoming;
    }

    @Override
    public void addViewer(ServerPlayer player) {
        this.viewers.add(player.getUUID());
        send((ServerLevel) player.level(), player, tracks((ServerLevel) player.level()));
    }

    @Override
    public void removeViewer(ServerPlayer player) {
        this.viewers.remove(player.getUUID());
    }

    // --- multiblock -------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return GBLaunch.RADAR;
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
            this.powered = this.energy.consume(POWER_USE);
            this.status = this.powered ? MachineStatus.RUNNING : MachineStatus.NO_POWER;
        }
        // The anti-missile defence's eyes (DESIGN.md §18.2): batteries engage what it tracks at their full reach.
        if (this.powered) {
            dev.groundburst.content.defence.Interception.radarWorking(level, this.worldPosition);
        } else {
            dev.groundburst.content.defence.Interception.radarStopped(level, this.worldPosition);
        }
        if (level.getGameTime() % SCAN_INTERVAL == 0) {
            List<LaunchPayloads.Track> tracks = tracks(level);
            int in = (int) Math.min(15, tracks.stream().filter(LaunchPayloads.Track::incoming).count());
            if (in != this.incoming) {
                this.incoming = in;
                level.updateNeighbourForOutputSignal(this.worldPosition, getBlockState().getBlock());
            }
            for (UUID id : List.copyOf(this.viewers)) {
                if (level.getPlayerByUUID(id) instanceof ServerPlayer p && p.position().distanceTo(Vec3.atCenterOf(this.worldPosition)) < 16) {
                    send(level, p, tracks);
                } else {
                    this.viewers.remove(id);
                }
            }
        }
        return this.powered;
    }

    /** In an EMP field it goes dark: no power, nothing tracked, nothing on its screen. */
    @Override
    protected void onEmp(ServerLevel level) {
        this.powered = false;
        dev.groundburst.content.defence.Interception.radarStopped(level, this.worldPosition);
        if (level.getGameTime() % SCAN_INTERVAL == 0) {
            for (UUID id : List.copyOf(this.viewers)) {
                if (level.getPlayerByUUID(id) instanceof ServerPlayer p) {
                    send(level, p, List.of());
                }
            }
        }
    }

    /** Every missile in the air within range (none while it is unformed or unpowered). */
    private List<LaunchPayloads.Track> tracks(ServerLevel level) {
        List<LaunchPayloads.Track> out = new ArrayList<>();
        if (!isFormed() || !this.powered) {
            return out;
        }
        Vec3 c = Vec3.atCenterOf(this.worldPosition);
        for (Flights.Flight f : Flights.of(level).all()) {
            Vec3 p = f.position(), t = f.target();
            if (Math.abs(p.x - c.x) > RANGE || Math.abs(p.z - c.z) > RANGE) {
                continue;
            }
            boolean incoming = Math.hypot(t.x - c.x, t.z - c.z) <= INCOMING_RADIUS;
            out.add(new LaunchPayloads.Track(f.trackId(), (int) p.x, (int) p.y, (int) p.z, f.heading(), (int) t.x, (int) t.z,
                    f.ticksToImpact(), incoming));
        }
        return out;
    }

    private void send(ServerLevel level, ServerPlayer player, List<LaunchPayloads.Track> tracks) {
        PacketDistributor.sendToPlayer(player, new LaunchPayloads.Radar(this.worldPosition, isFormed(), this.powered,
                (int) ((long) this.energy.stored() * 100 / Math.max(1, this.energy.capacity())), tracks));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return null;                                   // its screen is the radar scope, opened by RadarArrayBlock
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
