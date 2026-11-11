package dev.groundburst.port.render;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/** Minecraft 26.x's block state model (its parts and particle texture), over a 1.21.1 state's baked model. */
public final class BlockStateModel {
    /** The particle texture, as 26.x's material gives it. */
    public record Particle(TextureAtlasSprite sprite) {}

    private final BakedModel model;
    private final BlockState state;

    private BlockStateModel(BakedModel model, BlockState state) {
        this.model = model;
        this.state = state;
    }

    public static BlockStateModel of(BlockState state) {
        return new BlockStateModel(Minecraft.getInstance().getBlockRenderer().getBlockModel(state), state);
    }

    public void collectParts(RandomSource random, List<BlockStateModelPart> out) {
        out.add(new BlockStateModelPart(this.model, this.state, random.nextLong()));
    }

    public Particle particleMaterial() {
        return new Particle(this.model.getParticleIcon(ModelData.EMPTY));
    }
}
