package dev.groundburst.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import dev.groundburst.config.GBCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;

/** Placement filter that lets a placed feature be switched off from {@link GBCommonConfig}. */
public record ConfigToggleFilter(String toggle) implements PlacementFilter {
    public static final MapCodec<ConfigToggleFilter> CODEC = Codec.STRING.fieldOf("toggle").xmap(ConfigToggleFilter::new, ConfigToggleFilter::toggle);

    @Override
    public boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
        return GBCommonConfig.isEnabled(this.toggle);
    }

    @Override
    public MapCodec<ConfigToggleFilter> codec() {
        return CODEC;
    }
}
