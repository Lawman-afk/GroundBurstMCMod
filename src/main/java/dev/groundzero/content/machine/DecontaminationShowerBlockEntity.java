package dev.groundzero.content.machine;

import java.util.List;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundzero.registry.GZBlockEntities;
import dev.groundzero.registry.GZMenus;
import dev.groundzero.registry.GZSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Decontamination Shower (DESIGN.md §8, §21.6): a walk-in booth, two blocks tall, with water piped into its tank.
 * While someone stands inside it sprays them (20 FE/t and 10 mB of water a tick): it puts out fire and washes off
 * chemical exposure (poison, wither, nausea) and Radiation Sickness ({@link #clean}). Washing items and the dose of the
 * radiation field come with Phase 12.
 */
public class DecontaminationShowerBlockEntity extends MachineBlockEntity implements AnimatedMachine {
    public static final int ENERGY_PER_TICK = 20;
    public static final int WATER_PER_TICK = 10;
    /** Effects the shower washes off. */
    public static final List<Holder<MobEffect>> WASHED = List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.NAUSEA,
            dev.groundzero.content.nuclear.GZEffects.RADIATION_SICKNESS);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public DecontaminationShowerBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.DECONTAMINATION_SHOWER.get(), pos, state, 10_000, 200, 0, 0, new int[0], new int[0],
                List.of(MachineTank.Spec.water(8_000)));
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }

    /** The booth's inside: both blocks, inset from the walls. */
    private AABB booth() {
        return new AABB(this.worldPosition).expandTowards(0, 1, 0).deflate(0.1, 0.05, 0.1);
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        List<LivingEntity> inside = level.getEntitiesOfClass(LivingEntity.class, booth(), LivingEntity::isAlive);
        if (inside.isEmpty()) {
            this.status = MachineStatus.IDLE;
            return false;
        }
        MachineTank water = this.tanks.get(0);
        if (water.amount() < WATER_PER_TICK) {
            this.status = MachineStatus.NEED_FLUID;
            return false;
        }
        if (!this.energy.consume(ENERGY_PER_TICK)) {
            this.status = MachineStatus.NO_POWER;
            return false;
        }
        water.drainInternal(WATER_PER_TICK);
        for (LivingEntity entity : inside) {
            clean(entity);
        }
        spray(level);
        this.status = MachineStatus.RUNNING;
        return true;
    }

    /** What a pass under the shower washes off. */
    public static void clean(LivingEntity entity) {
        entity.clearFire();
        for (Holder<MobEffect> effect : WASHED) {
            entity.removeEffect(effect);
        }
    }

    private void spray(ServerLevel level) {
        double x = this.worldPosition.getX() + 0.5, y = this.worldPosition.getY() + 1.85, z = this.worldPosition.getZ() + 0.5;
        level.sendParticles(ParticleTypes.FALLING_WATER, x, y, z, 6, 0.22, 0.02, 0.22, 0);
        if (level.getGameTime() % 4 == 0) {
            level.sendParticles(ParticleTypes.SPLASH, x, this.worldPosition.getY() + 0.1, z, 4, 0.25, 0.02, 0.25, 0.05);
            level.sendParticles(ParticleTypes.CLOUD, x, this.worldPosition.getY() + 0.9, z, 1, 0.2, 0.4, 0.2, 0.002);   // foam
        }
    }

    /** Water only falls while someone is being sprayed. */
    @Override
    public java.util.Set<String> hiddenBones() {
        boolean active = getBlockState().hasProperty(MachineBlock.ACTIVE) && getBlockState().getValue(MachineBlock.ACTIVE);
        return active ? java.util.Set.of() : java.util.Set.of("spray");
    }

    @Override
    public List<net.minecraft.network.chat.Component> readout(MachineMenu menu) {
        return List.of(net.minecraft.network.chat.Component.translatable(menu.status().translationKey()));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.DECONTAMINATION_SHOWER.get(), containerId, inventory, this, MachineLayouts.DECONTAMINATION_SHOWER, this.data);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GZSounds.MACHINE_DECONTAMINATION_SHOWER.get();
    }
}
