package dev.groundburst.compat;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.blast.Blasts;
import dev.groundburst.blast.Freezes;
import dev.groundburst.blast.ParticleBeams;
import dev.groundburst.blast.Singularities;
import dev.groundburst.compat.sable.SableScenes;
import dev.groundburst.content.artillery.Ballistics;
import dev.groundburst.content.artillery.GunBlockEntity;
import dev.groundburst.content.fallout.GBFallout;
import dev.groundburst.content.launch.Designators;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Create check's last scenes ({@link CreateCheck}, with Sable and Create Aeronautics): ships under the slower weapons,
 * and the tools that aim at them. A Laser Designator's beam and a mortar shell's fall land on a ship where it is seen; a
 * particle beam eats through a wooden ship's middle and chars its edge; a deep freeze rimes and snows on a stone ship; a
 * small singularity draws a floating ship in; a nuclear blast eighty blocks off chars a wooden ship, sets it alight and
 * throws it, and the server never stalls on the ships the scenes before emptied, nor on the crater's lake.
 */
final class ShipCheck {
    static final int START = 1210, END = 1700;
    /** A ship the designator and a mortar aim at, ten blocks from where they stand. */
    private static final BlockPos AIM_MIN = new BlockPos(80, -60, 18), AIM_MAX = new BlockPos(84, -59, 22), MORTAR = new BlockPos(52, -60, 20);
    /** A wooden platform a beam comes down on. */
    private static final BlockPos BEAM_MIN = new BlockPos(100, -60, 14), BEAM_MAX = new BlockPos(108, -60, 22), BEAM_AT = new BlockPos(104, -61, 18);
    /** A stone platform frozen from its middle. */
    private static final BlockPos ICE_MIN = new BlockPos(120, -60, 15), ICE_MAX = new BlockPos(126, -60, 21);
    private static final Vec3 ICE_AT = new Vec3(123.5, -59.5, 18.5);
    /** A small airship, held up by levitite, a singularity opens seven blocks east of (inside its pull once it is a few ticks old). */
    private static final BlockPos FLOAT_MIN = new BlockPos(140, -50, 17), FLOAT_MAX = new BlockPos(142, -49, 19);
    private static final Vec3 HOLE_AT = new Vec3(148.5, -49.5, 18.5);
    /**
     * A wooden ship, and a nuclear blast seventy blocks west of it and forty over the ground (eighty from its deck, past
     * the crater, in the fire band: the whole deck sees the fireball).
     */
    private static final BlockPos NUKED_MIN = new BlockPos(60, -60, 100), NUKED_MAX = new BlockPos(66, -59, 106);
    private static final Vec3 NUKE_AT = new Vec3(-6.5, -19, 103.5);

    private static @Nullable Object aimed, beamed, iced, floated, nuked;
    private static int blocksBefore;
    private static Vec3 before = Vec3.ZERO;
    private static volatile long tickStart, slowest;
    private static boolean timing;

    private ShipCheck() {}

    static void tick(Minecraft mc, int ticks) {
        switch (ticks) {
            // --- the designator and a mortar, aimed at a ship ---------------------------------------------------------------
            case 1210 -> CreateCheck.onServer(mc, level -> build(level, AIM_MIN, AIM_MAX, Blocks.OAK_PLANKS.defaultBlockState()));
            case 1215 -> CreateCheck.onServer(mc, level -> assemble(level, AIM_MIN, AIM_MAX));
            case 1230 -> CreateCheck.onServer(mc, ShipCheck::aim);
            // --- a particle beam on a wooden platform ---------------------------------------------------------------------
            case 1240 -> CreateCheck.onServer(mc, level -> build(level, BEAM_MIN, BEAM_MAX, Blocks.OAK_PLANKS.defaultBlockState()));
            case 1245 -> CreateCheck.onServer(mc, level -> assemble(level, BEAM_MIN, BEAM_MAX));
            case 1250 -> CreateCheck.look(mc, new Vec3(95.5, -52, 9.5), new Vec3(104.5, -60, 18.5));
            case 1260 -> CreateCheck.onServer(mc, level -> {
                beamed = ship(level, BEAM_MIN, BEAM_MAX);
                blocksBefore = beamed == null ? -1 : SableScenes.blocks(level, beamed);
                ParticleBeams.burnOverride = 60;
                try {
                    ParticleBeams.strike(level, BEAM_AT, null, false);
                } finally {
                    ParticleBeams.burnOverride = 0;
                }
            });
            case 1300 -> CreateCheck.shot(mc, "ship_beamed");
            case 1330 -> CreateCheck.onServer(mc, level -> {
                int now = beamed == null ? -1 : SableScenes.blocks(level, beamed);
                int charred = beamed == null ? 0 : SableScenes.count(level, beamed, s -> s.is(GBFallout.CHARRED_PLANKS.block().get()));
                CreateCheck.check(beamed != null && blocksBefore == 81 && now > 0 && now < 81 - 12,
                        "a particle beam ate down through the ship's middle (" + blocksBefore + " -> " + now + " blocks)");
                CreateCheck.check(charred > 0, "and charred its edge round it (" + charred + " charred planks)");
            });
            // --- a deep freeze on a stone platform ------------------------------------------------------------------------
            case 1340 -> CreateCheck.onServer(mc, level -> build(level, ICE_MIN, ICE_MAX, Blocks.STONE.defaultBlockState()));
            case 1345 -> CreateCheck.onServer(mc, level -> assemble(level, ICE_MIN, ICE_MAX));
            case 1350 -> CreateCheck.look(mc, new Vec3(116.5, -54, 11.5), new Vec3(123.5, -60, 18.5));
            case 1360 -> CreateCheck.onServer(mc, level -> {
                iced = ship(level, ICE_MIN, ICE_MAX);
                Freezes.start(level, ICE_AT, 12, 60, null);
            });
            case 1420 -> CreateCheck.shot(mc, "ship_frozen");
            case 1430 -> CreateCheck.onServer(mc, level -> {
                int frost = iced == null ? 0 : SableScenes.count(level, iced, s -> s.is(GBFallout.FROSTED_STONE.block().get())
                        || s.is(GBFallout.CRYO_SNOW.block().get()) || s.is(GBFallout.CRYO_CRYSTAL.block().get()));
                CreateCheck.check(frost >= 10, "a deep freeze rimed and snowed on the stone ship (" + frost + " frosted or snowed on)");
            });
            // --- a singularity beside a floating ship ---------------------------------------------------------------------
            case 1440 -> CreateCheck.onServer(mc, level -> {
                build(level, FLOAT_MIN, FLOAT_MAX.below(), block("aeronautics:levitite"));
                build(level, FLOAT_MIN.above(), FLOAT_MAX, Blocks.OAK_PLANKS.defaultBlockState());
            });
            case 1445 -> CreateCheck.onServer(mc, level -> assemble(level, FLOAT_MIN, FLOAT_MAX));
            case 1460 -> CreateCheck.onServer(mc, level -> {
                floated = ship(level, FLOAT_MIN, FLOAT_MAX);
                before = floated == null ? Vec3.ZERO : SableScenes.position(floated);
                blocksBefore = floated == null ? -1 : SableScenes.blocks(level, floated);
                Singularities.start(level, HOLE_AT, 8, 0.56f, 10, 100, null);
            });
            case 1540 -> CreateCheck.onServer(mc, level -> {
                boolean gone = floated == null || SableScenes.gone(floated);
                Vec3 at = gone ? HOLE_AT : SableScenes.position(floated);
                int now = gone ? 0 : SableScenes.blocks(level, floated);
                CreateCheck.check(floated != null && (at.x - before.x > 0.3 || now < blocksBefore),
                        "a singularity drew the floating ship in (moved " + at.subtract(before) + ", " + blocksBefore + " -> " + now + " blocks)");
            });
            // --- a nuclear blast eighty blocks from a wooden ship ----------------------------------------------------------------
            case 1550 -> CreateCheck.onServer(mc, level -> build(level, NUKED_MIN, NUKED_MAX, Blocks.OAK_PLANKS.defaultBlockState()));
            case 1555 -> CreateCheck.onServer(mc, level -> assemble(level, NUKED_MIN, NUKED_MAX));
            case 1560 -> CreateCheck.look(mc, new Vec3(72.5, -50, 112.5), new Vec3(63.5, -59, 103.5));
            case 1570 -> CreateCheck.onServer(mc, level -> {
                nuked = ship(level, NUKED_MIN, NUKED_MAX);
                before = nuked == null ? Vec3.ZERO : SableScenes.position(nuked);
                time();
                slowest = 0;
                Blasts.detonate(level, NUKE_AT, Blasts.profile("nuclear_missile_warhead"), null);
            });
            case 1598 -> CreateCheck.onServer(mc, level -> {
                // (the fireball reaches it 22 to 25 ticks in: counted before its fires burn out or spread)
                int fires = nuked == null || SableScenes.gone(nuked) ? 0 : SableScenes.count(level, nuked, s -> s.getBlock() instanceof BaseFireBlock);
                int charred = nuked == null || SableScenes.gone(nuked) ? 0
                        : SableScenes.count(level, nuked, s -> s.is(GBFallout.CHARRED_PLANKS.block().get()));
                CreateCheck.check(charred > 0, "the nuclear fireball charred the wooden ship 80 blocks off (" + charred + " charred planks)");
                CreateCheck.check(fires > 0, "and set it alight on top (" + fires + " fires on it)");
            });
            case 1599 -> CreateCheck.shot(mc, "ship_nuked");
            case 1660 -> CreateCheck.onServer(mc, level -> {
                Vec3 at = nuked == null || SableScenes.gone(nuked) ? before : SableScenes.position(nuked);
                CreateCheck.check(at.x - before.x > 1, "its shock threw the ship east, away from the burst (moved " + at.subtract(before) + ")");
                CreateCheck.check(slowest < 1000, "the server never stalled while the blast passed the ships, emptied ones too (its slowest tick "
                        + slowest + " ms)");
            });
            default -> {}
        }
    }

    /** The designator's beam and a mortar's fall onto the ship it aims at: both land on it where it is seen. */
    private static void aim(ServerLevel level) {
        aimed = ship(level, AIM_MIN, AIM_MAX);
        if (aimed == null) {
            CreateCheck.check(false, "a ship to aim at");
            return;
        }
        AABB box = SableScenes.box(aimed);
        Vec3 mid = box.getCenter();
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        Vec3 eye = new Vec3(box.minX - 10, mid.y, mid.z);
        long t0 = System.nanoTime();
        Designators.Hit hit = Designators.trace(level, player, eye, new Vec3(1, 0, 0), 64);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        boolean landed = hit != null && box.inflate(0.6).contains(hit.pos()) && Math.abs(hit.pos().x - box.minX) < 0.6;
        CreateCheck.check(landed && ms < 50,
                "a Laser Designator's beam on a ship lands on its near side where it is seen (" + (hit == null ? "nothing" : hit.pos()) + ", " + ms + " ms)");
        if (!landed) {
            // What the beam struck instead: a creature, or a block of the world's or of another ship.
            var struck = level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(64, 0, 0), net.minecraft.world.level.ClipContext.Block.VISUAL,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, player));
            GroundBurst.LOGGER.info("Create check: the beam struck {} at {} ({}); a creature: {}", level.getBlockState(struck.getBlockPos()),
                    struck.getBlockPos(), dev.groundburst.content.machine.Frames.at(level, struck.getBlockPos()).isWorld() ? "the world's" : "a ship's",
                    hit == null || hit.entity() == null ? "none" : hit.entity());
        }
        level.setBlockAndUpdate(MORTAR, block("groundburst:mortar"));             // (it turns all round)
        if (!(level.getBlockEntity(MORTAR) instanceof GunBlockEntity gun)) {
            CreateCheck.check(false, "a mortar to lay on the ship");
            return;
        }
        Vec3 deck = new Vec3(mid.x, box.maxY, mid.z);
        GunBlockEntity.Solution s = gun.solve(deck);
        Vec3 muzzle = gun.muzzle(s.yaw(), s.elevation());
        Ballistics.Fall fall = Ballistics.fall(level, muzzle, Ballistics.velocity(gun.spec.charges()[s.charge()], s.yaw(), s.elevation()), null,
                GunBlockEntity.MAX_FLIGHT);
        double off = fall == null ? Double.MAX_VALUE : Math.hypot(fall.at().x - deck.x, fall.at().z - deck.z);
        double radius = fall == null ? 0 : Math.max(2.5, gun.radius(Math.hypot(fall.at().x - muzzle.x, fall.at().z - muzzle.z)));
        CreateCheck.check(s.reachable() && fall != null && fall.aboard() && off <= radius,
                "a mortar laid on the ship's deck lands there, on the ship: not blocked (" + (fall == null ? "no fall" : fall.at() + ", " + Math.round(off * 10) / 10.0
                        + " off, aboard " + fall.aboard()) + ")");
    }

    /** Times every server tick from now on (the slowest kept). */
    private static void time() {
        if (timing) {
            return;
        }
        timing = true;
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre e) -> tickStart = System.nanoTime());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            long ms = (System.nanoTime() - tickStart) / 1_000_000;
            if (ms > 250) {
                GroundBurst.LOGGER.info("Create check: server tick {} took {} ms", e.getServer().getTickCount(), ms);
            }
            slowest = Math.max(slowest, ms);
        });
    }

    private static void build(ServerLevel level, BlockPos min, BlockPos max, BlockState state) {
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            level.setBlock(p, state, Block.UPDATE_ALL);
        }
    }

    private static void assemble(ServerLevel level, BlockPos min, BlockPos max) {
        CreateCheck.serverCommand(level.getServer(), "sable assemble area " + min.getX() + " " + min.getY() + " " + min.getZ() + " " + max.getX() + " "
                + max.getY() + " " + max.getZ());
    }

    private static @Nullable Object ship(ServerLevel level, BlockPos min, BlockPos max) {
        Object ship = SableScenes.shipIn(level, new AABB(min).minmax(new AABB(max)).inflate(1));
        GroundBurst.LOGGER.info("Create check: ship {}", ship == null ? "none" : SableScenes.describe(ship));
        return ship;
    }

    private static BlockState block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState();
    }
}
