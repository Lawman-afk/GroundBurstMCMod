package dev.groundburst.legacy.loot;

import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;

import dev.groundburst.GroundBurst;
import dev.groundburst.legacy.component.DataComponentMap;
import dev.groundburst.legacy.component.ImplicitComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Minecraft 1.21's copy_components from a block entity, as a loot function of the mod's own on 1.20.1
 * (groundburst:copy_components): the block entity's data it keeps in its item (ImplicitComponents), those the table names.
 */
public final class CopyComponentsFunction extends LootItemConditionalFunction {
    private static final DeferredRegister<LootItemFunctionType> TYPES = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, GroundBurst.MODID);
    public static final RegistryObject<LootItemFunctionType> TYPE = TYPES.register("copy_components",
            () -> new LootItemFunctionType(new Serializer()));

    private final @Nullable Set<ResourceLocation> include;

    private CopyComponentsFunction(LootItemCondition[] conditions, @Nullable Set<ResourceLocation> include) {
        super(conditions);
        this.include = include;
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        BlockEntity be = context.getParamOrNull(LootContextParams.BLOCK_ENTITY);
        if (be instanceof ImplicitComponents implicit) {
            DataComponentMap.Builder components = new DataComponentMap.Builder();
            implicit.collectImplicitComponents(components);
            components.applyTo(stack, this.include);
        }
        return stack;
    }

    @Override
    public LootItemFunctionType getType() {
        return TYPE.get();
    }

    public static final class Serializer extends LootItemConditionalFunction.Serializer<CopyComponentsFunction> {
        @Override
        public void serialize(JsonObject json, CopyComponentsFunction function, JsonSerializationContext context) {
            super.serialize(json, function, context);
            json.addProperty("source", "block_entity");
            if (function.include != null) {
                JsonArray include = new JsonArray();
                function.include.forEach(id -> include.add(id.toString()));
                json.add("include", include);
            }
        }

        @Override
        public CopyComponentsFunction deserialize(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions) {
            Set<ResourceLocation> include = null;
            if (json.has("include")) {
                include = new HashSet<>();
                for (JsonElement e : json.getAsJsonArray("include")) {
                    include.add(new ResourceLocation(e.getAsString()));
                }
            }
            return new CopyComponentsFunction(conditions, include);
        }
    }
}
