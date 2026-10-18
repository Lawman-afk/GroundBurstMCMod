package dev.groundzero.content.recipe;

/** Which machine performs a {@link ProcessingRecipe}. The id is the recipe type and serializer name. */
public enum ProcessingKind {
    CRUSHING("crushing"),
    ARC_SMELTING("arc_smelting"),
    PRESSING("pressing"),
    CHEMISTRY("chemistry"),
    CENTRIFUGING("centrifuging"),
    ELECTROLYSIS("electrolysis"),
    FUSION_SYNTHESIS("fusion_synthesis"),
    CRYO_CONDENSING("cryo_condensing"),
    FABRICATING("fabricating"),
    EXOTIC_FABRICATING("exotic_fabricating"),
    ACCELERATING("accelerating");

    private final String id;

    ProcessingKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
