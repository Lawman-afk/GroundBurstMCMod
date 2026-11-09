package dev.groundburst.content.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;

import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.launch.Launches;
import dev.groundburst.content.launch.MapSource;
import dev.groundburst.content.launch.StationPayloads;
import dev.groundburst.content.launch.TerrainSurvey;
import dev.groundburst.content.machine.AnimatedMachine;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.machine.MachineStatus;
import dev.groundburst.content.machine.SideConfig;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.MultiblockPartBlock;
import dev.groundburst.registry.GBSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The War Room Display (DESIGN.md §17.17): a wall screen seven blocks wide drawing a launch network's whole picture for
 * the room (players within {@value #ROOM} blocks): the terrain, the members, every missile its launchers fired and every
 * one its radars track, impacts, the blasts its seismographs felt, laser spots, the counts, the log, and the alert
 * condition on its header (5 NORMAL to 1 ATTACK). Linked by Launch Codes (it shows the network; it is not a member). 60 FE
 * a tick; no power or an EMP, dark.
 */
public class WarRoomDisplayBlockEntity extends MachineBlockEntity implements AnimatedMachine, MultiblockController, MapSource,
        dev.groundburst.content.launch.NetworkDisplay {
    public static final int ENERGY_CAPACITY = 60_000, POWER_USE = 60;
    /** Players this near get its board; how often (ticks). */
    public static final int ROOM = 64, BOARD_INTERVAL = 10;
    private static final int RECHECK_INTERVAL = 40;
    /** Impacts are drawn this long after (ticks); a missile gone this close to coming down came down. */
    public static final int IMPACT_KEEP = 2400, LANDED = 30;
    /** A blast or an incoming missile this near the network (a member, the display) raises the alert condition. */
    public static final int ALERT_NEAR = 512;
    /** Its views: 0 fits everything in, then centred on it at these scales (blocks a screen pixel). */
    public static final int[] SCALES = {0, 2, 4, 8, 16, 32, 64};
    /** What it draws: the terrain, the radars' reach, labels, the log, blasts and impacts. */
    public static final int TERRAIN = 1, REACH = 2, LABELS = 4, LOG = 8, BLASTS = 16, ALL_LAYERS = 31;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private long network;
    private boolean powered, surveyed;
    private int view, layers = ALL_LAYERS;
    /** The missiles it last saw (not saved): one gone close to its impact came down. */
    private Map<Integer, StationPayloads.ContactView> lastContacts = new HashMap<>();
    private final List<long[]> impacts = new ArrayList<>();
    // What the model shows (synced): lit, and the alert condition on its header.
    private boolean shownPowered;
    private int condition = 5;

    public WarRoomDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(GBCommand.WAR_ROOM_ENTITY.get(), pos, state, ENERGY_CAPACITY, 2_000, 0, 0, new int[0], new int[0]);
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }

    public long networkId() {
        return this.network;
    }

    public int view() {
        return this.view;
    }

    public int layers() {
        return this.layers;
    }

    @Override
    public long shownNetwork() {
        return this.network;
    }

    /** Launch Codes used on it: it shows that network from now on (0: none). */
    @Override
    public void show(ServerLevel level, long id) {
        this.network = id;
        this.lastContacts.clear();
        this.impacts.clear();
        setChanged();
        sync();
    }

    /** Its settings, from its screen. */
    public void applySettings(ServerLevel level, int view, int layers) {
        this.view = Math.clamp(view, 0, SCALES.length - 1);
        this.layers = layers & ALL_LAYERS;
        setChanged();
    }

    // --- multiblock -------------------------------------------------------------------------------------------------------
    @Override
    public MultiblockDefinition definition() {
        return GBCommand.WAR_ROOM;
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
        if (this.level == null || this.level.isClientSide() || isRemoved() || carried()) {
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
        if (this.level instanceof ServerLevel server) {
            if (state.hasProperty(MultiblockPartBlock.FORMED) && state.getValue(MultiblockPartBlock.FORMED)) {
                MultiblockController.setPartsFormed(server, this, false);
            }
            Launches.untrackMapper(server, this.worldPosition);
        }
    }

    // --- its map (the station's survey) ---------------------------------------------------------------------------------
    @Override
    public boolean mapping() {
        return isFormed() && this.powered;
    }

    @Override
    public boolean boundedMap() {
        return false;
    }

    @Override
    public double mapReach() {
        return ROOM;
    }

    // --- ticking ----------------------------------------------------------------------------------------------------------
    @Override
    protected boolean tickMachine(ServerLevel level) {
        long now = level.getGameTime();
        if ((now + this.worldPosition.hashCode()) % RECHECK_INTERVAL == 0) {
            revalidate();
        }
        Launches.trackMapper(level, this.worldPosition, worldBlockPos());
        if (!isFormed()) {
            this.status = MachineStatus.INCOMPLETE;
            this.powered = false;
            this.surveyed = false;
        } else {
            if (!this.surveyed) {
                this.surveyed = true;
                TerrainSurvey.of(level).surveyAround(level, worldBlockPos(), 16);
            }
            this.powered = this.energy.consume(POWER_USE);
            this.status = this.powered ? MachineStatus.RUNNING : MachineStatus.NO_POWER;
        }
        if ((now + 5) % BOARD_INTERVAL == 0) {
            board(level, now);
        }
        return this.powered;
    }

    @Override
    protected void onEmp(ServerLevel level) {
        this.powered = false;
        if ((level.getGameTime() + 5) % BOARD_INTERVAL == 0) {
            board(level, level.getGameTime());
        }
    }

    /** The players in its room. */
    private List<ServerPlayer> room(ServerLevel level) {
        Vec3 c = inWorld(Vec3.atCenterOf(this.worldPosition).add(0, 2, 0));
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(c) <= (double) ROOM * ROOM) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * The alert condition its header shows and the impacts it keeps (worked out whether anyone is there or not, so its
     * lamps are right when someone comes in), and its board for the room (sent only while anyone is there to see it).
     */
    private void board(ServerLevel level, long now) {
        List<ServerPlayer> room = room(level);
        boolean lit = isFormed() && this.powered && !isEmpDisabled();
        LaunchNetwork nets = LaunchNetwork.of(level);
        LaunchNetwork.Network n = lit && this.network != 0 ? nets.network(this.network) : null;
        StationPayloads.Picture pic = n == null ? new StationPayloads.Picture(List.of(), List.of(), List.of(), List.of())
                : StationPayloads.picture(level, n, true);
        // Impacts: a missile it saw gone this close to coming down came down where it was going.
        Map<Integer, StationPayloads.ContactView> seen = new HashMap<>();
        boolean newContact = false;
        for (StationPayloads.ContactView c : pic.contacts()) {
            seen.put(c.id(), c);
            newContact |= !c.own() && !this.lastContacts.containsKey(c.id());
        }
        for (StationPayloads.ContactView c : this.lastContacts.values()) {
            if (!seen.containsKey(c.id()) && c.eta() <= LANDED) {
                this.impacts.add(new long[] {c.targetX(), c.targetZ(), now, c.own() ? 1 : 0});
            }
        }
        this.lastContacts = seen;
        this.impacts.removeIf(i -> now - i[2] > IMPACT_KEEP);
        if (newContact) {
            playSound(level, this.worldPosition.above(2), GBSounds.WAR_ROOM_CONTACT.get(), SoundSource.BLOCKS, 0.8f, 1f);
        }
        int condition = n == null ? 5 : condition(pic, now);
        showModel(level, lit, condition);
        if (room.isEmpty()) {
            return;
        }
        List<WarRoomPayloads.ImpactView> impacts = new ArrayList<>();
        for (long[] i : this.impacts) {
            impacts.add(new WarRoomPayloads.ImpactView((int) i[0], (int) i[1], (int) (now - i[2]), i[3] == 1));
        }
        List<LaunchNetwork.Event> log = n == null ? List.of() : n.log();
        if (log.size() > 4) {
            log = log.subList(log.size() - 4, log.size());
        }
        WarRoomPayloads.Board board = new WarRoomPayloads.Board(this.worldPosition, worldBlockPos(), now, lit, n == null ? "" : n.code(), condition,
                pic.members(), pic.contacts(), pic.blasts(), impacts, pic.spots(), log, this.view, this.layers);
        for (ServerPlayer p : room) {
            PacketDistributor.sendToPlayer(p, board);
        }
    }

    /**
     * The alert condition: 1 ATTACK (a blast its seismographs felt near the network lately, or the Dead Hand in alert), 2
     * INCOMING (a missile its radars track coming down near it), 3 MISSILES IN FLIGHT (its own), 4 ARMED, 5 NORMAL.
     */
    private int condition(StationPayloads.Picture pic, long now) {
        List<double[]> near = new ArrayList<>();
        Vec3 here = inWorld(Vec3.atCenterOf(this.worldPosition));
        near.add(new double[] {here.x, here.z});
        for (StationPayloads.MemberView m : pic.members()) {
            near.add(new double[] {m.at().getX() + 0.5, m.at().getZ() + 0.5});
            if (m.deadHand() && m.readiness() == LaunchSiloBlockEntity.Readiness.COUNTING.ordinal()) {
                return 1;
            }
        }
        for (StationPayloads.BlastView b : pic.blasts()) {
            if (b.age() <= IMPACT_KEEP && b.magnitude() >= 2 && close(near, b.x(), b.z())) {
                return 1;
            }
        }
        boolean own = false;
        for (StationPayloads.ContactView c : pic.contacts()) {
            if (!c.own() && close(near, c.targetX(), c.targetZ())) {
                return 2;
            }
            own |= c.own();
        }
        if (own) {
            return 3;
        }
        for (StationPayloads.MemberView m : pic.members()) {
            if (m.launcher() && !m.lost() && m.willBeArmed()) {
                return 4;
            }
        }
        return 5;
    }

    private static boolean close(List<double[]> near, double x, double z) {
        for (double[] p : near) {
            if (Math.hypot(p[0] - x, p[1] - z) <= ALERT_NEAR) {
                return true;
            }
        }
        return false;
    }

    private void showModel(ServerLevel level, boolean lit, int condition) {
        if (lit != this.shownPowered || condition != this.condition) {
            this.shownPowered = lit;
            this.condition = condition;
            sync();
        }
    }

    private void sync() {
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return null;                                       // its screen is its own (WarRoomDisplayBlock opens it)
    }

    // --- the model (tools/assets/war_room.py) -----------------------------------------------------------------------------
    public boolean shownPowered() {
        return this.shownPowered;
    }

    public int condition() {
        return this.condition;
    }

    /** Unformed, nothing; dark, its lamps out; lit, the alert condition's lamp (flashing at 2 and 1) and the power lamp. */
    @Override
    public Set<String> hiddenBones() {
        if (!isFormed()) {
            return Set.of("frame");
        }
        Set<String> hidden = new HashSet<>();
        long now = this.level == null ? 0 : this.level.getGameTime();
        for (int c = 1; c <= 5; c++) {
            boolean lit = this.shownPowered && this.condition == c && (c > 2 || now % 20 < 13);
            if (!lit) {
                hidden.add("lamp" + c);
            }
        }
        if (!this.shownPowered) {
            hidden.add("power");
        }
        return hidden;
    }

    /** The header's clock: the world's time of day (its hands turned about the clock's face). */
    @Override
    public Map<String, float[]> boneRotations(float partialTick) {
        if (this.level == null) {
            return Map.of();
        }
        double day = (this.level.getOverworldClockTime() + partialTick) % 24000;      // 0: six in the morning
        double hours = (day / 1000.0 + 6) % 12, minutes = day % 1000 / 1000.0 * 60;
        return Map.of("hand_h", new float[] {0, 0, (float) -(hours / 12 * Math.PI * 2)}, "hand_m", new float[] {0, 0, (float) -(minutes / 60 * Math.PI * 2)});
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // --- saving -----------------------------------------------------------------------------------------------------------
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveCustomOnly(registries);
        tag.putBoolean("shown_powered", this.shownPowered);
        tag.putInt("condition", this.condition);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("network", this.network);
        output.putInt("view", this.view);
        output.putInt("layers", this.layers);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.network = input.getLongOr("network", 0L);
        this.view = Math.clamp(input.getIntOr("view", 0), 0, SCALES.length - 1);
        this.layers = input.getIntOr("layers", ALL_LAYERS);
        this.shownPowered = input.getBooleanOr("shown_powered", this.shownPowered);
        this.condition = input.getIntOr("condition", this.condition);
    }
}
