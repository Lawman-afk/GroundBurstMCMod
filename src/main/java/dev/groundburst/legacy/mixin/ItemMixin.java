package dev.groundburst.legacy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.groundburst.legacy.component.Comps;
import net.minecraft.world.item.Item;

/** An item made with properties given default data components (Comps.withDefault): they become its own. */
@Mixin(Item.class)
public abstract class ItemMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void groundburst$defaults(Item.Properties properties, CallbackInfo ci) {
        Comps.itemMade((Item) (Object) this, properties);
    }
}
