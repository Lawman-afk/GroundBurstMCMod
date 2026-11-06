package dev.groundburst.heal;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.groundburst.heal.HealSettings.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Healing the game's own explosions and other mods' (DESIGN.md §21.9): a creeper's, TNT's, an end crystal's, a ghast's
 * fireball's, the Wither's, a bed's or a respawn anchor's where it does not work, and any other mod's explosion that goes
 * through the game's explosion code. Each explosion has a recorder of its own, as this mod's weapons do: what set it off
 * is its weapon ({@link #source}) and says which switch under {@code healing.explosions} lets it heal ({@link #kind}).
 * Each block it hits is remembered as it was before the hit; one that will heal drops nothing and spills nothing (it
 * comes back with what it held), and what its going takes with it round it is remembered too. A painting, item frame or
 * armour stand it breaks is kept whole, to be put back with the ground. A block it sets off rather than breaks (TNT, this
 * mod's charges) is never put back: it went off.
 */
public final class Explosions {
    /** What an explosion with no entity and no damage type behind it is called. */
    static final Identifier UNKNOWN = Identifier.withDefaultNamespace("explosion");
    private static final BiConsumer<ItemStack, BlockPos> NO_DROPS = (stack, pos) -> {};

    /** The explosion last asked about and its recorder (an explosion hits its blocks one after another). */
    private static @Nullable Explosion last;
    private static Scars.@Nullable Recorder lastRecorder;

    private Explosions() {}

    /**
     * What set the explosion off, as {@code healing.what.excludedWeapons} names it: the entity that blew up (a creeper,
     * primed TNT, a ghast's fireball, another mod's rocket), or with none its damage type (a bed's and a respawn anchor's
     * is {@code minecraft:bad_respawn_point}, another's often plain {@code minecraft:explosion}).
     */
    public static Identifier source(Explosion explosion) {
        Entity by = explosion.getDirectSourceEntity();
        if (by != null) {
            return BuiltInRegistries.ENTITY_TYPE.getKey(by.getType());
        }
        Identifier damage = Snapshots.damageType(explosion);
        return damage != null ? damage : UNKNOWN;
    }

    /** Which switch lets an explosion set off by {@code source} heal: the game's own by name, anything else another mod's. */
    public static Kind kind(Identifier source) {
        if (!source.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
            return Kind.OTHER_MOD;
        }
        return switch (source.getPath()) {
            case "creeper" -> Kind.CREEPER;
            case "tnt", "tnt_minecart" -> Kind.TNT;
            case "end_crystal" -> Kind.END_CRYSTAL;
            case "fireball" -> Kind.FIREBALL;                  // a ghast's (a blaze's small ones do not explode)
            case "wither", "wither_skull" -> Kind.WITHER;
            case "bad_respawn_point" -> Kind.BED;              // a bed or a respawn anchor where it does not work
            default -> Kind.OTHER_MOD;                         // (a player, or plain explosion damage: another mod set it off)
        };
    }

    /** The recorder for {@code explosion}'s damage in {@code level}: one that remembers nothing where it does not heal. */
    static Scars.Recorder recorder(Explosion explosion, ServerLevel level) {
        Scars.Recorder r = lastRecorder;
        if (explosion == last && r != null) {
            return r;
        }
        Explosion.BlockInteraction interaction = explosion.getBlockInteraction();
        if (interaction == Explosion.BlockInteraction.DESTROY || interaction == Explosion.BlockInteraction.DESTROY_WITH_DECAY) {
            Identifier source = source(explosion);
            r = Scars.recorder(level, explosion.center(), source, kind(source));
        } else {
            r = Scars.Recorder.none(level);                    // a wind charge's: it only works doors and buttons
        }
        last = explosion;
        lastRecorder = r;
        return r;
    }

    /** Each tick: the last explosion is let go of. */
    static void forget() {
        last = null;
        lastRecorder = null;
    }

    /**
     * An explosion hits the block {@code state} at {@code pos} (in place of the block's own {@code onExplosionHit}, which
     * {@code hit} runs, given where its drops go). Remembered first if the explosion heals here; if it will heal, its drops
     * are swallowed and its contents kept.
     */
    public static void hit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> drops,
            Consumer<BiConsumer<ItemStack, BlockPos>> hit) {
        if (state.isAir()) {
            hit.accept(drops);                                 // (most of what an explosion's rays cross)
            return;
        }
        Scars.Recorder r = recorder(explosion, level);
        if (!r.active() || goesOff(state, level, pos, explosion)) {
            hit.accept(drops);
            return;
        }
        r.hit(pos, swallow -> hit.accept(swallow ? NO_DROPS : drops));
    }

    /**
     * Whether the explosion sets the block off rather than breaks it: TNT, which an explosion lights and which drops
     * nothing in one, and blocks like it (another mod's explosives). They are never put back: they went off.
     */
    static boolean goesOff(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion) {
        return state.getBlock() instanceof TntBlock || !state.canDropFromExplosion(level, pos, explosion);
    }

    /**
     * The explosion is about to hurt {@code e} ({@code hurt} does it, as the explosion would). A painting, item frame or
     * armour stand it breaks is kept whole, as it was before, to be put back when its ground heals; what it drops is held
     * back. One the explosion leaves be (a fixed frame, a marker stand) is left as it is.
     */
    public static boolean hurt(Entity e, Explosion explosion, BooleanSupplier hurt) {
        if (!(e instanceof HangingEntity || e instanceof ArmorStand) || !(e.level() instanceof ServerLevel level) || e.isRemoved()) {
            return hurt.getAsBoolean();
        }
        Scars.Recorder r = recorder(explosion, level);
        if (!r.keepsDecorations()) {
            return hurt.getAsBoolean();
        }
        CompoundTag before = Snapshots.save(e, level);
        Healer healer = Healer.of(level);
        // (an armour stand pops what it wears out of the block over its feet)
        healer.hushWhile(List.of(e.position(), Vec3.atCenterOf(e.blockPosition()), Vec3.atCenterOf(e.blockPosition().above())));
        boolean done;
        try {
            done = hurt.getAsBoolean();
        } finally {
            healer.hushWhile(null);
        }
        if (e.isRemoved() && before != null) {
            r.keep(e, before);
        }
        return done;
    }
}
