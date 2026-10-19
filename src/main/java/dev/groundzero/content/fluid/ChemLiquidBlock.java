package dev.groundzero.content.fluid;

import dev.groundzero.GroundZero;
import dev.groundzero.registry.GZLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * A spilled chemical. Acids eat at anything standing in them, napalm sets it alight, and liquid oxygen or nitrogen
 * freeze it like powder snow. Fuels and slurry are merely unpleasant to swim in.
 */
public class ChemLiquidBlock extends LiquidBlock {
    public static final ResourceKey<DamageType> ACID = ResourceKey.create(Registries.DAMAGE_TYPE, GroundZero.id("acid"));

    static {
        GZLang.add("death.attack." + GroundZero.MODID + ".acid", "%1$s dissolved in acid");
        GZLang.add("death.attack." + GroundZero.MODID + ".acid.player", "%1$s dissolved in acid while fighting %2$s");
    }

    private final ChemFluid.Hazard hazard;

    public ChemLiquidBlock(FlowingFluid fluid, ChemFluid.Hazard hazard, Properties properties) {
        super(fluid, properties);
        this.hazard = hazard;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        super.entityInside(state, level, pos, entity, effectApplier, isPrecise);
        if (!(level instanceof ServerLevel server) || !(entity instanceof LivingEntity living)) {
            return;
        }
        switch (this.hazard) {
            case ACID -> {
                if (living.tickCount % 10 == 0) {
                    living.hurtServer(server, new DamageSource(server.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ACID)), 2f);
                }
            }
            case FIRE -> living.igniteForSeconds(8f);
            case CRYO -> {
                living.setTicksFrozen(Math.min(living.getTicksRequiredToFreeze() + 40, living.getTicksFrozen() + 4));
                if (living.tickCount % 20 == 0) {
                    living.hurtServer(server, server.damageSources().freeze(), 1f);
                }
            }
            case NONE -> {
            }
        }
    }
}
