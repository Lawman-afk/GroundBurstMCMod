package dev.groundburst.client.fx;

import net.irisshaders.iris.api.v0.IrisApi;

/**
 * Shader packs (Iris; main's DESIGN.md §12.3), the 1.21.1 backport's: Iris 1.8 puts a pack's programs in place of the
 * game's own shaders, found by name, so a render type on one of the game's shaders draws with the pack's version of it.
 * The glow is on vanilla's eyes shader (the pack's glowing program); with a pack on, the fire and the clouds switch to
 * vanilla's translucent entity shader (the pack's lit program, {@link FxRenderTypes}). Effects stay out of the pack's
 * shadow pass. Only loaded when Iris is ({@link FxRenderTypes#IRIS}); main hands its render pipelines to Iris instead.
 */
final class IrisFx {
    private IrisFx() {}

    /** Whether a shader pack is on now. */
    static boolean packInUse() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    /** Whether Iris is drawing the shader pack's shadow map now (effects cast no shadows). */
    static boolean shadowPass() {
        return IrisApi.getInstance().isRenderingShadowPass();
    }
}
