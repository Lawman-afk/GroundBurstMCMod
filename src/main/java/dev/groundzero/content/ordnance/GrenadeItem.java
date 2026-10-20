package dev.groundzero.content.ordnance;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A thrown explosive (DESIGN.md §14): right-click throws it, the fuse (if any) lit or the pin pulled as it goes. */
public class GrenadeItem extends Item {
    private final GrenadeType type;

    public GrenadeItem(Properties properties, GrenadeType type) {
        super(properties);
        this.type = type;
    }

    public GrenadeType type() {
        return this.type;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        float pitch = 0.95f + level.getRandom().nextFloat() * 0.1f;
        if (this.type != GrenadeType.DYNAMITE) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), GZOrdnance.GRENADE_PIN.get(), SoundSource.PLAYERS, 0.8f, pitch);
        }
        if (level instanceof ServerLevel server) {
            ThrownExplosive thrown = Projectile.spawnProjectileFromRotation((l, owner, item) -> new ThrownExplosive(l, owner, item), server, stack,
                    player, 0f, this.type.speed, 1.0f);
            if (this.type == GrenadeType.DYNAMITE) {
                // The lit fuse fizzes where the dynamite is, following it through the air.
                server.playSound(null, thrown, GZOrdnance.DYNAMITE_LIGHT.get(), SoundSource.PLAYERS, 0.8f, pitch);
            }
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        player.getCooldowns().addCooldown(stack, 10);
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
