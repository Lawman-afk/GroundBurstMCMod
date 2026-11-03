package dev.groundburst.port.render;

/** Only for the name of 26.x's block-breaking overlay a block entity renderer is given (always none here). */
public final class ModelFeatureRenderer {
    private ModelFeatureRenderer() {}

    /** A block being broken (1.21.1 draws its cracks itself). */
    public static final class CrumblingOverlay {
        private CrumblingOverlay() {}
    }
}
