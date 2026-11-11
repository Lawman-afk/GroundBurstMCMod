package dev.groundburst.legacy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.groundburst.legacy.client.CalculatePlayerTurnEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.OptionInstance;
import net.minecraftforge.common.MinecraftForge;

/** The mouse's turn of the player, as NeoForge's CalculatePlayerTurnEvent may change it (the designator's slowed sight). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
    private Object groundburst$sensitivity(OptionInstance<?> option) {
        Object value = option.get();
        Minecraft mc = Minecraft.getInstance();
        if (option != mc.options.sensitivity() || !(value instanceof Double sensitivity)) {
            return value;
        }
        CalculatePlayerTurnEvent event = new CalculatePlayerTurnEvent(sensitivity, mc.options.smoothCamera);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getMouseSensitivity();
    }
}
