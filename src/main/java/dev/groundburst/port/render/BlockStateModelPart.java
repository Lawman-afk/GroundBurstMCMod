package dev.groundburst.port.render;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/** Minecraft 26.x's block model part (a model's quads for one state, its random pick made): a 1.21.1 baked model's quads. */
public final class BlockStateModelPart {
    private final BakedModel model;
    private final @Nullable BlockState state;
    private final long seed;

    public BlockStateModelPart(BakedModel model, @Nullable BlockState state, long seed) {
        this.model = model;
        this.state = state;
        this.seed = seed;
    }

    public List<BakedQuad> getQuads(@Nullable Direction side) {
        return this.model.getQuads(this.state, side, RandomSource.create(this.seed), ModelData.EMPTY, null);
    }

    public BakedModel model() {
        return this.model;
    }
}
