package dev.groundzero.client.render;

import java.util.function.Consumer;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * The warhead on a missile as an item ({@code "groundzero:warhead"} in items/missile.json, beside the missile's airframe
 * model): the warhead's own block model standing on the airframe's body, wherever the item is drawn (the inventory, a
 * hand, the missile in flight, the assembly screens' preview).
 */
public final class WarheadSpecialRenderer implements NoDataSpecialModelRenderer {
    public static final Identifier TYPE = dev.groundzero.GroundZero.id("warhead");

    private final Block warhead;
    private final String airframe;

    private WarheadSpecialRenderer(Block warhead, String airframe) {
        this.warhead = warhead;
        this.airframe = airframe;
    }

    private WarheadMounts.Mount mount() {
        return WarheadMounts.get(this.airframe, BuiltInRegistries.BLOCK.getKey(this.warhead).getPath());
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean hasFoil, int outline) {
        WarheadMounts.Mount m = mount();
        if (m == null) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5f, m.top() / 16f, 0.5f);                 // the item model's pixels: the body's top, on its axis
        WarheadNose.submit(pose, collector, this.warhead, this.airframe, false, light, overlay, outline);
        pose.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        WarheadMounts.Mount m = mount();
        if (m == null) {
            return;
        }
        float half = m.half() * m.scale() / 16f, foot = m.top() / 16f, tip = foot + m.height() * m.scale() / 16f;
        for (float x : new float[] {0.5f - half, 0.5f + half}) {
            for (float y : new float[] {foot, tip}) {
                for (float z : new float[] {0.5f - half, 0.5f + half}) {
                    output.accept(new Vector3f(x, y, z));
                }
            }
        }
    }

    /** {"type": "groundzero:warhead", "warhead": its block, "airframe": "basic", "strategic" or "icbm"}. */
    public record Unbaked(Identifier warhead, String airframe) implements NoDataSpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("warhead").forGetter(Unbaked::warhead),
                Codec.STRING.fieldOf("airframe").forGetter(Unbaked::airframe)).apply(i, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
            return new WarheadSpecialRenderer(BuiltInRegistries.BLOCK.getValue(this.warhead), this.airframe);
        }
    }
}
