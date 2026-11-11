package dev.groundburst.client.fx;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Shader packs (Iris; main's DESIGN.md §12.3), the 1.21.1 backport's: by default the effects are drawn after the level
 * under a pack ({@link DrawnLast}), where Iris leaves the game's shaders alone. For a player who asks the pack to draw
 * them (Iris 1.8 puts a pack's programs in place of the game's own shaders, found by name, so a render type on one of
 * the game's shaders draws with the pack's version of it), the glow is on vanilla's eyes shader (the pack's glowing
 * program) and the fire and the clouds switch to vanilla's translucent entity shader (the pack's lit program,
 * {@link FxRenderTypes}). Either way the effects stay out of the pack's shadow pass. Only loaded when Iris is
 * ({@link FxRenderTypes#IRIS}); main hands its render pipelines to Iris instead.
 * <p>
 * On 1.20.1 Iris is Oculus (Iris for Forge), with Iris's API: asked here by name, so the build needs no copy of it.
 */
final class IrisFx {
    private IrisFx() {}

    private static final MethodHandle INSTANCE, PACK_IN_USE, SHADOW_PASS;

    static {
        MethodHandle instance = null, inUse = null, shadow = null;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            instance = lookup.findStatic(api, "getInstance", MethodType.methodType(api));
            inUse = lookup.findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class));
            shadow = lookup.findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            instance = null;
        }
        INSTANCE = instance;
        PACK_IN_USE = inUse;
        SHADOW_PASS = shadow;
    }

    private static boolean ask(MethodHandle question) {
        if (INSTANCE == null || question == null) {
            return false;
        }
        try {
            return (boolean) question.invoke(INSTANCE.invoke());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Whether a shader pack is on now. */
    static boolean packInUse() {
        return ask(PACK_IN_USE);
    }

    /** Whether Iris is drawing the shader pack's shadow map now (effects cast no shadows). */
    static boolean shadowPass() {
        return ask(SHADOW_PASS);
    }
}
