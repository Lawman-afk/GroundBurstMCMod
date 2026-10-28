package dev.groundburst.content.pipe;

/**
 * Item pipe tiers (DESIGN.md §6.5), like the fluid pipe and cable tiers: a run carries as much as its weakest pipe,
 * moving items in batches every half second. For scale: a vanilla hopper moves 2.5 items a second; Pipez moves 1 /
 * 4 / 32 / 128 / 1 280 items a second with no / basic / improved / advanced / ultimate upgrades; Mekanism's transporters
 * pull 1 / 16 / 32 / 64 items at a time. Ours step up four times per tier: 8, 32 and 128 items a second, so a steel
 * run keeps up with a busy machine line and a pneumatic one can feed a Singularity Reactor at full power on its own.
 */
public enum ItemPipeTier {
    IRON("iron_item_pipe", "Iron Item Pipe", 4),
    STEEL("item_pipe", "Steel Item Pipe", 16),
    PNEUMATIC("pneumatic_item_pipe", "Pneumatic Item Pipe", 64);

    /** Ticks per batch window: each window a run may move up to {@link #batch()} items. */
    public static final int WINDOW = 10;

    private final String id;
    private final String english;
    private final int batch;

    ItemPipeTier(String id, String english, int batch) {
        this.id = id;
        this.english = english;
        this.batch = batch;
    }

    public String id() {
        return id;
    }

    public String english() {
        return english;
    }

    /** Items a run of these pipes may move every {@link #WINDOW} ticks. */
    public int batch() {
        return batch;
    }

    public int perSecond() {
        return batch * 20 / WINDOW;
    }
}
