package dev.groundburst.devworld;

import dev.groundburst.GroundBurst;
import dev.groundburst.client.fx.DevWorlds;
import dev.groundburst.content.assembly.MissileAssembly;
import dev.groundburst.content.assembly.MissileParts;
import dev.groundburst.content.assembly.MissileSpec;
import dev.groundburst.content.assembly.SizeClass;
import dev.groundburst.content.launch.GBLaunch;
import dev.groundburst.content.launch.LaunchCodesItem;
import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.launch.LaunchStationBlockEntity;
import dev.groundburst.content.machine.GBMachines;
import dev.groundburst.content.machine.MachineBlock;
import dev.groundburst.content.machine.MachineBlockEntity;
import dev.groundburst.content.manual.GBManual;
import dev.groundburst.content.multiblock.MultiblockController;
import dev.groundburst.content.multiblock.MultiblockDefinition;
import dev.groundburst.content.multiblock.StructureSpawnerItem;
import dev.groundburst.content.ordnance.GBOrdnance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Development aid, left out of the released jar (build.gradle): the world a player's wishlist (DESIGN.md §21.10) is tried
 * in, "Ground Burst Aftermath Range", made by {@code -Dgroundburst.aftermathRange=true} (the {@code aftermathRange} Gradle
 * run) in a normal world (a fixed seed), everything built, powered, loaded, linked and aimed:
 * <ul>
 * <li>the nearest village to the spawn is the target; 1 400 blocks from it, on cleared ground, an observation post: a
 * concrete deck with a glazed shelter (its windows on all sides), a Launch Control Station and two Launch Complexes, each
 * fed by a Creative Capacitor Bank;</li>
 * <li>one complex holds a thermonuclear missile aimed at the village: from the post its far flash, its cloud over the
 * horizon, its far wave coming over the land (the post stands in its withered ring) and its black rain under its cloud
 * to see; the other a tactical one aimed 300 blocks off the other way, whose broken ring reaches the post (the windows
 * facing it break, the wave throws whoever stands outside);</li>
 * <li>signs saying what to do and what to look for; the player in creative at the post with the network's Launch Codes
 * and the Field Manual.</li>
 * </ul>
 * Then it photographs the post and quits, the world saved to copy into an instance's saves.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = GroundBurst.MODID)
public final class AftermathRange {
    /** The world's seed (a village within a few hundred blocks of its spawn). */
    static final long SEED = 20_261_112L;
    /** How far the post stands from the village (blocks), and the tactical warhead's aim from the post. */
    static final int FROM_VILLAGE = 1_400, TACTICAL = 300;
    /** Half the width of the ground cleared and levelled round the post. */
    static final int CLEAR = 28;

    private static boolean worldMade, built;
    private static int waited, step;
    private static volatile BlockPos post;
    private static volatile Direction toVillage = Direction.NORTH;

    private AftermathRange() {}

    static boolean enabled() {
        return Boolean.getBoolean("groundburst.aftermathRange");
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post e) {
        if (enabled()) {
            tick();
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) {
            mc.gui.setScreen(null);
        }
        if (mc.options.tutorialStep != net.minecraft.client.tutorial.TutorialSteps.NONE) {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        }
        if (mc.level == null && !worldMade && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
            worldMade = true;
            try {
                var settings = new net.minecraft.world.level.LevelSettings("Ground Burst Aftermath Range", GameType.CREATIVE,
                        net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true,
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                String folder = DevWorlds.fresh(mc, "ground_burst_aftermath_range");
                mc.createWorldOpenFlows().createFreshLevel(folder, settings, new net.minecraft.world.level.levelgen.WorldOptions(SEED, true, false),
                        net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions, mc.gui.screen());
            } catch (java.io.IOException ex) {
                GroundBurst.LOGGER.error("Aftermath range: could not make a world", ex);
            }
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null) {
            return;
        }
        if (!built) {
            built = true;
            server.execute(() -> build(server.overworld()));
            return;
        }
        waited++;
        if (waited < 200 || post == null) {
            return;
        }
        if (photos(mc, server)) {
            GroundBurst.LOGGER.info("Aftermath range: built and photographed; the world is saved as it quits");
            mc.stop();
        }
    }

    // --- the pictures --------------------------------------------------------------------------------------------------------
    record View(String name, Vec3 at, float yaw, float pitch, int settle) {}

    private static boolean photos(Minecraft mc, MinecraftServer server) {
        BlockPos p = post;
        float face = toVillage.toYRot();
        Vec3 back = Vec3.atBottomCenterOf(p).subtract(Vec3.atLowerCornerOf(toVillage.getUnitVec3i()).scale(30));
        List<View> views = List.of(
                new View("post", back.add(12, 18, 12), face - 30, 25, 200),
                new View("toward_village", Vec3.atBottomCenterOf(p).add(0, 2, 0), face, 2, 120),
                new View("station", Vec3.atBottomCenterOf(p).add(Vec3.atLowerCornerOf(toVillage.getClockWise().getUnitVec3i()).scale(-4)).add(0, 1, 0),
                        toVillage.getClockWise().toYRot(), 15, 100));
        if (!mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        if (step > views.size()) {
            return true;
        }
        View next = step < views.size() ? views.get(step) : new View("spawn", Vec3.atBottomCenterOf(p).add(0, 1, 0), face, 4, 0);
        int at = waited - 200;
        if (at == 0) {
            server.execute(() -> {
                ServerPlayer pl = server.getPlayerList().getPlayers().getFirst();
                pl.getAbilities().flying = !next.name().equals("spawn");
                pl.onUpdateAbilities();
                pl.teleportTo(server.overworld(), next.at().x, next.at().y, next.at().z, Set.of(), next.yaw(), next.pitch(), true);
            });
        }
        if (step < views.size() && at == next.settle()) {
            Screenshot.grab(mc.gameDirectory, "aftermath_range_" + next.name() + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                    msg -> GroundBurst.LOGGER.info("Aftermath range: {}", msg.getString()));
        }
        if (at >= next.settle() + 10) {
            step++;
            waited = 199;
        }
        return false;
    }

    // --- building --------------------------------------------------------------------------------------------------------------
    /** On the server: the village found, the post placed 1 400 blocks from it, everything built, linked and aimed. */
    private static void build(ServerLevel level) {
        var commands = level.getServer().getCommands();
        var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
        for (String c : List.of("time set 1000", "weather clear 6000", "difficulty peaceful")) {
            commands.performPrefixedCommand(source, c);
        }
        BlockPos spawn = level.getRespawnData().pos();
        BlockPos village = level.findNearestMapStructure(StructureTags.VILLAGE, spawn, 100, false);
        if (village == null) {
            GroundBurst.LOGGER.error("Aftermath range: no village near the spawn");
            village = spawn.offset(800, 0, 0);
        }
        // The post 1 400 blocks from the village, on the side of the spawn (or west of it), along the nearest axis.
        int dx = spawn.getX() - village.getX(), dz = spawn.getZ() - village.getZ();
        Direction away = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST) : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
        if (dx == 0 && dz == 0) {
            away = Direction.WEST;
        }
        toVillage = away.getOpposite();
        int px = village.getX() + away.getStepX() * FROM_VILLAGE, pz = village.getZ() + away.getStepZ() * FROM_VILLAGE;
        for (int cx = (px - 64) >> 4; cx <= (px + 64) >> 4; cx++) {
            for (int cz = (pz - 64) >> 4; cz <= (pz + 64) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        level.getChunk(village.getX() >> 4, village.getZ() >> 4);
        int vy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, village.getX(), village.getZ());
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
        Builder b = new Builder(level, px, y, pz, toVillage);
        b.ground();
        b.deck();
        BlockPos station = b.station();
        BlockPos thermo = b.complex(-14, GBOrdnance.THERMONUCLEAR_WARHEAD.item().get());
        BlockPos tactical = b.complex(14, GBOrdnance.TACTICAL_NUCLEAR_WARHEAD.item().get());
        BlockPos side = new BlockPos(px, y, pz).relative(toVillage.getClockWise(), TACTICAL);
        b.signs();
        List<ItemStack> codes = b.networks(station, thermo, tactical, new BlockPos(village.getX(), vy, village.getZ()), side);
        b.player(codes);
        post = new BlockPos(px, y, pz);
        GroundBurst.LOGGER.info("Aftermath range: village at {}, post at {} {} {} (toward the village: {}), tactical aim {} {}",
                village.toShortString(), px, y, pz, toVillage, side.getX(), side.getZ());
    }

    /** Builds the post, its ground at {@code y} (the deck's floor is y + 1), the village {@code front} of it. */
    static final class Builder {
        final ServerLevel level;
        final int x, y, z;
        final Direction front, right;
        final BlockState concrete = GBLaunch.REINFORCED_CONCRETE.block().get().defaultBlockState();

        Builder(ServerLevel level, int x, int y, int z, Direction front) {
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
            this.front = front;
            this.right = front.getClockWise();
        }

        /** {@code f} blocks toward the village, {@code r} to its right, {@code up} over the ground. */
        BlockPos at(int f, int up, int r) {
            return new BlockPos(this.x + this.front.getStepX() * f + this.right.getStepX() * r, this.y + up,
                    this.z + this.front.getStepZ() * f + this.right.getStepZ() * r);
        }

        void set(BlockPos p, BlockState s) {
            this.level.setBlock(p, s, Block.UPDATE_ALL);
        }

        /** The ground round the post levelled: earth up to its level, nothing standing over it (trees cut, hills taken down). */
        void ground() {
            for (int i = -CLEAR; i <= CLEAR; i++) {
                for (int j = -CLEAR; j <= CLEAR; j++) {
                    int gx = this.x + i, gz = this.z + j;
                    for (int gy = this.y - 4; gy <= this.y + 40; gy++) {
                        BlockState s = gy < this.y ? Blocks.DIRT.defaultBlockState() : gy == this.y ? Blocks.GRASS_BLOCK.defaultBlockState()
                                : Blocks.AIR.defaultBlockState();
                        this.level.setBlock(new BlockPos(gx, gy, gz), s, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }

        /** A concrete deck, and a shelter on it glazed all round (its windows to see the wave's broken glass by). */
        void deck() {
            for (int f = -8; f <= 8; f++) {
                for (int r = -8; r <= 8; r++) {
                    set(at(f, 0, r), this.concrete);
                }
            }
            // The shelter: 7 by 7, walls of concrete with a band of glass panes round at eye height, a roof.
            for (int f = -3; f <= 3; f++) {
                for (int r = -3; r <= 3; r++) {
                    boolean wall = Math.abs(f) == 3 || Math.abs(r) == 3;
                    for (int up = 1; up <= 3; up++) {
                        BlockState s = !wall ? Blocks.AIR.defaultBlockState()
                                : up == 2 && Math.abs(f) != Math.abs(r) ? Blocks.GLASS.defaultBlockState() : this.concrete;
                        set(at(f, up, r), s);
                    }
                    set(at(f, 4, r), this.concrete);
                }
            }
            // A doorway on its back, away from the village.
            set(at(-3, 1, 0), Blocks.AIR.defaultBlockState());
            set(at(-3, 2, 0), Blocks.AIR.defaultBlockState());
        }

        /** The Launch Control Station inside the shelter's back wall... set out on the deck by it, facing the village. */
        BlockPos station() {
            BlockPos s = at(-6, 1, -4);
            place(GBLaunch.STATION_CONTROLLER.block().get(), GBLaunch.STATION, s, this.front);
            power(GBLaunch.STATION, s, this.front);
            if (this.level.getBlockEntity(s) instanceof MachineBlockEntity m) {
                m.energy().generate(m.energy().capacity());
            }
            return s;
        }

        /** A Launch Complex {@code r} to the right of the post (behind the deck), a nuclear missile in it, powered. */
        BlockPos complex(int r, net.minecraft.world.item.Item warhead) {
            BlockPos c = at(-18, 1, r);
            for (int f = -24; f <= -12; f++) {
                for (int rr = r - 5; rr <= r + 5; rr++) {
                    set(at(f, 0, rr), this.concrete);
                }
            }
            place(GBLaunch.COMPLEX_CONTROLLER.block().get(), GBLaunch.COMPLEX, c, this.front);
            power(GBLaunch.COMPLEX, c, this.front);
            ItemStack w = new ItemStack(warhead);
            SizeClass size = MissileAssembly.classOf(w);
            SizeClass body = size == null || size.ordinal() < SizeClass.L.ordinal() ? SizeClass.L : size;
            ItemStack missile = MissileAssembly.missile(new MissileSpec(w, body, MissileParts.Motor.LIQUID, MissileParts.Guidance.GPS, false));
            if (this.level.getBlockEntity(c) instanceof LaunchSiloBlockEntity s) {
                s.inventory().setItem(LaunchSiloBlockEntity.SLOT_MISSILE, missile);
                s.energy().generate(s.energy().capacity());
                GroundBurst.LOGGER.info("Aftermath range: complex at {} loaded with {} ({} body), range {}", c.toShortString(),
                        warhead.getDescriptionId(), body, s.range());
            }
            return c;
        }

        /** The network: the station's codes on both complexes, the thermonuclear one aimed at the village, the tactical aside. */
        List<ItemStack> networks(BlockPos station, BlockPos thermo, BlockPos tactical, BlockPos village, BlockPos side) {
            ItemStack codes = new ItemStack(GBLaunch.LAUNCH_CODES.get());
            if (!(this.level.getBlockEntity(station) instanceof LaunchStationBlockEntity st)) {
                GroundBurst.LOGGER.error("Aftermath range: no station at {}", station);
                return List.of();
            }
            LaunchCodesItem.use(this.level, codes, st, false);
            for (BlockPos p : List.of(thermo, tactical)) {
                var be = LaunchCodesItem.target(this.level, p);
                if (be != null) {
                    LaunchCodesItem.use(this.level, codes, be, false);
                }
            }
            LaunchNetwork.Network n = st.network(this.level);
            if (n == null) {
                GroundBurst.LOGGER.error("Aftermath range: the station has no network");
                return List.of(codes);
            }
            LaunchNetwork nets = LaunchNetwork.of(this.level);
            nets.aim(this.level, n.id, List.of(thermo), village.getX(), village.getZ(), LaunchNetwork.Pattern.POINT, 0);
            nets.aim(this.level, n.id, List.of(tactical), side.getX(), side.getZ(), LaunchNetwork.Pattern.POINT, 0);
            codes.set(DataComponents.CUSTOM_NAME, Component.literal("Launch Codes: Aftermath Range (" + n.code() + ")"));
            GroundBurst.LOGGER.info("Aftermath range: network {} with {} members", n.code(), n.members().size());
            return List.of(codes);
        }

        void signs() {
            int rot = rotationFacing(this.front.getOpposite());
            sign(at(2, 1, -6), rot, "AFTERMATH", "RANGE", "Station: pick a", "complex, ARM, fire");
            sign(at(2, 1, -7), rot, "Left complex:", "THERMONUCLEAR on", "the village, 1400", "blocks ahead.");
            sign(at(2, 1, 6), rot, "Right complex:", "TACTICAL nuke", "300 blocks to", "the right.");
            sign(at(2, 1, 7), rot, "Watch: the far", "flash, the cloud,", "the far WAVE", "coming at you.");
            sign(at(5, 1, -2), rot, "You stand in its", "WITHERED land:", "leaves and grass", "die in patches.");
            sign(at(5, 1, 2), rot, "The tactical's", "BROKEN land", "reaches here:", "windows shatter.");
            sign(at(5, 1, 0), rot, "Then fly to the", "village: charred", "land, BLACK RAIN", "under its cloud.");
            sign(at(-8, 1, 3), rotationFacing(this.front), "Settings: Mods >", "Ground Burst >", "Config: healing", "clock, swaps...");
        }

        /** The player at the post facing the village: creative, the codes, the Field Manual; the world's spawn here. */
        void player(List<ItemStack> codes) {
            var commands = this.level.getServer().getCommands();
            var source = this.level.getServer().createCommandSourceStack().withSuppressedOutput();
            BlockPos s = at(0, 1, 0);
            commands.performPrefixedCommand(source, "setworldspawn " + s.getX() + " " + s.getY() + " " + s.getZ());
            ServerPlayer player = this.level.getServer().getPlayerList().getPlayers().getFirst();
            player.setGameMode(GameType.CREATIVE);
            player.getInventory().clearContent();
            for (ItemStack c : codes) {
                player.getInventory().add(c.copy());
            }
            player.getInventory().add(new ItemStack(GBManual.FIELD_MANUAL.get()));
            player.teleportTo(this.level, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, Set.of(), this.front.toYRot(), 2, true);
        }

        /** A sign's rotation (sixteenths: 0 faces south, 4 west, 8 north, 12 east) facing {@code d}. */
        static int rotationFacing(Direction d) {
            return switch (d) {
                case SOUTH -> 0;
                case WEST -> 4;
                case NORTH -> 8;
                default -> 12;
            };
        }

        void sign(BlockPos at, int rotation, String... lines) {
            set(at, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation));
            if (this.level.getBlockEntity(at) instanceof SignBlockEntity sign) {
                SignText.Mutable text = sign.getText(SignTextSlot.FRONT).asMutable();
                for (int i = 0; i < Math.min(4, lines.length); i++) {
                    text.setLine(i, Component.literal(lines[i]));
                }
                sign.setText(text.asImmutable(), SignTextSlot.FRONT);
                sign.setChanged();
                this.level.sendBlockUpdated(at, sign.getBlockState(), sign.getBlockState(), Block.UPDATE_ALL);
            }
        }

        /** A multiblock placed whole (as its Structure Spawner places it) and formed. */
        void place(Block controller, MultiblockDefinition def, BlockPos at, Direction facing) {
            for (MultiblockDefinition.Cell cell : StructureSpawnerItem.buildOrder(def, at, facing)) {
                BlockState s;
                if (cell.key() == '.') {
                    s = Blocks.AIR.defaultBlockState();
                } else if (cell.key() == MultiblockDefinition.CONTROLLER) {
                    s = controller.defaultBlockState().setValue(MachineBlock.FACING, facing);
                } else {
                    s = def.legend().get(cell.key()).preview().get();
                }
                set(cell.pos(), s);
            }
            if (this.level.getBlockEntity(at) instanceof MultiblockController c) {
                c.revalidate();
                GroundBurst.LOGGER.info("Aftermath range: {} at {} {}", def.id(), at.toShortString(), c.isFormed() ? "formed" : "NOT FORMED");
            }
        }

        /** A Creative Capacitor Bank by each of the structure's Energy Ports, on a side clear of it, facing the port. */
        void power(MultiblockDefinition def, BlockPos at, Direction facing) {
            Set<BlockPos> cells = new HashSet<>();
            for (MultiblockDefinition.Cell c : def.cells(at, facing)) {
                cells.add(c.pos().immutable());
            }
            for (BlockPos port : def.positionsOf('E', at, facing)) {
                for (Direction d : List.of(facing.getOpposite(), facing.getClockWise(), facing.getCounterClockWise(), facing)) {
                    BlockPos p = port.relative(d);
                    if (!cells.contains(p) && this.level.getBlockState(p).isAir()) {
                        set(p, GBMachines.CAPACITOR_BANKS.get(3).block().get().defaultBlockState().setValue(MachineBlock.FACING, d.getOpposite()));
                        break;
                    }
                }
            }
        }
    }
}
