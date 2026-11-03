package dev.groundburst.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import dev.groundburst.config.GBCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/** Placement filter that lets a placed feature be switched off from {@link GBCommonConfig}. */
public final class ConfigToggleFilter extends PlacementFilter {
    public static final MapCodec<ConfigToggleFilter> CODEC = Codec.STRING.fieldOf("toggle").xmap(ConfigToggleFilter::new, ConfigToggleFilter::toggle);

    private final String toggle;

    public ConfigToggleFilter(String toggle) {
        this.toggle = toggle;
    }

    public String toggle() {
        return this.toggle;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
        return GBCommonConfig.isEnabled(this.toggle);
    }

    @Override
    public PlacementModifierType<?> type() {
        return GBWorldgen.CONFIG_TOGGLE.get();
    }
}
