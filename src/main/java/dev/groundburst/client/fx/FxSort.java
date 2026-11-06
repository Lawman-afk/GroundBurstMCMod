package dev.groundburst.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.groundburst.port.render.SubmitNodeCollector;
import net.minecraft.client.renderer.RenderType;

/**
 * Main sorts the effects' blended quads itself once a frame, under twins of their render types that leave them in order
 * (main's DESIGN.md §11.10); this branch draws its effects its own way ({@code FxRenderTypes}, kept by hand) and keeps the
 * game's sorting, so nothing is held back here.
 */
final class FxSort {
    private FxSort() {}

    static void begin() {}

    static boolean add(PoseStack poseStack, RenderType presorted, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        return false;
    }

    static void end(SubmitNodeCollector collector, PoseStack poseStack) {}
}
