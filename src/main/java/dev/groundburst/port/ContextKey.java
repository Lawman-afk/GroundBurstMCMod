package dev.groundburst.port;

import net.minecraft.resources.ResourceLocation;

/** Minecraft 26.x's typed key (here, for a frame's render data): equal by identity, named for reading. */
public final class ContextKey<T> {
    private final ResourceLocation name;

    public ContextKey(ResourceLocation name) {
        this.name = name;
    }

    public static <T> ContextKey<T> vanilla(String name) {
        return new ContextKey<>(new ResourceLocation(name));
    }

    public ResourceLocation name() {
        return this.name;
    }

    @Override
    public String toString() {
        return "<" + this.name + ">";
    }
}
