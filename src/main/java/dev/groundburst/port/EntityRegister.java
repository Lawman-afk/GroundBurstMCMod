package dev.groundburst.port;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** NeoForge 26.x's entity type register (DeferredRegister.Entities), over 1.21.1's plain register. */
public final class EntityRegister {
    private final DeferredRegister<EntityType<?>> register;

    public EntityRegister(String modId) {
        this.register = DeferredRegister.create(Registries.ENTITY_TYPE, modId);
    }

    public <E extends Entity> DeferredHolder<EntityType<?>, EntityType<E>> registerEntityType(String name, EntityType.EntityFactory<E> factory,
            MobCategory category) {
        return registerEntityType(name, factory, category, UnaryOperator.identity());
    }

    public <E extends Entity> DeferredHolder<EntityType<?>, EntityType<E>> registerEntityType(String name, EntityType.EntityFactory<E> factory,
            MobCategory category, UnaryOperator<EntityType.Builder<E>> builder) {
        return this.register.register(name, () -> builder.apply(EntityType.Builder.of(factory, category)).build(name));
    }

    public <E extends Entity> DeferredHolder<EntityType<?>, EntityType<E>> register(String name, Supplier<EntityType<E>> type) {
        return this.register.register(name, type);
    }

    public void register(IEventBus bus) {
        this.register.register(bus);
    }

    public DeferredRegister<EntityType<?>> unwrap() {
        return this.register;
    }
}
