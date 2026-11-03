package dev.groundburst.port.render;

/** Minecraft 26.x's state of an entity for a frame, as the mod's renderers read it (taken by {@link StateEntityRenderer}). */
public class EntityRenderState {
    public double x, y, z;
    public float ageInTicks;
    public float boundingBoxWidth, boundingBoxHeight;
    public boolean isInvisible;
    public int lightCoords = 0xF000F0;
    public int outlineColor;
    public float partialTick;
}
