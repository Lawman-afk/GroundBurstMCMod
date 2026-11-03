package dev.groundburst.port;

/** The 26.x GUI pipelines the screens name when they draw (see {@link RenderPipeline}). */
public final class RenderPipelines {
    public static final RenderPipeline GUI = new RenderPipeline("gui");
    public static final RenderPipeline GUI_TEXTURED = new RenderPipeline("gui_textured");
    public static final RenderPipeline GUI_TEXTURED_PREMULTIPLIED_ALPHA = new RenderPipeline("gui_textured_premultiplied_alpha");

    private RenderPipelines() {}
}
