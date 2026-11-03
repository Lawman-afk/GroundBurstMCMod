package dev.groundburst.port;

/**
 * A 26.x render pipeline as screens name one when they draw (1.21.1's GUI picks its own shaders): only a name here.
 * The world's own render types are made in {@code FxRenderTypes} with 1.21.1's render states.
 */
public record RenderPipeline(String name) {}
