package dev.groundburst.client.fx;

import net.irisshaders.iris.api.v0.IrisApi;

/**
 * Shader packs (Iris; main's DESIGN.md §12.3), the 1.21.1 backport's: by default the effects are drawn after the level
 * under a pack ({@link DrawnLast}), where Iris leaves the game's shaders alone. For a player who asks the pack to draw
 * them (Iris 1.8 puts a pack's programs in place of the game's own shaders, found by name, so a render type on one of
 * the game's shaders draws with the pack's version of it), the glow is on vanilla's eyes shader (the pack's glowing
 * program) and the fire and the clouds switch to vanilla's translucent entity shader (the pack's lit program,
 * {@link FxRenderTypes}). Either way the effects stay out of the pack's shadow pass. Only loaded when Iris is
 * ({@link FxRenderTypes#IRIS}); main hands its render pipelines to Iris instead.
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
