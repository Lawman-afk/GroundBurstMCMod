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
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                this.type == GrenadeType.DYNAMITE ? GZOrdnance.DYNAMITE_LIGHT.get() : GZOrdnance.GRENADE_PIN.get(), SoundSource.PLAYERS, 0.8f,
                0.95f + level.getRandom().nextFloat() * 0.1f);
        if (level instanceof ServerLevel server) {
            Projectile.spawnProjectileFromRotation((l, owner, item) -> new ThrownExplosive(l, owner, item), server, stack, player, 0f,
                    this.type.speed, 1.0f);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        player.getCooldowns().addCooldown(stack, 10);
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
