package dev.groundburst.legacy.mixin;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.windows.User32;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;

import dev.groundburst.legacy.client.DevRuns;
import net.minecraft.Util;

/**
 * The dev test runs' window ({@link DevRuns}): made without taking the focus, and once made put behind the other windows.
 * It still draws: the runs' pictures are read from the game's own buffers, not the screen. (Forge's early loading window,
 * made before any mod code runs, is off in those runs, so this is where the window is made; Mixin 0.8.5 injects in a
 * constructor only at its end, so the making is wrapped with MixinExtras.)
 */
@Mixin(Window.class)
abstract class DevWindowMixin {
    @Shadow
    @Final
    private long window;

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraftforge/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J"))
    private long groundburst$unfocused(IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitor, Operation<Long> original) {
        if (DevRuns.KEEP_MOUSE) {
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        }
        return original.call(width, height, title, monitor);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void groundburst$behind(CallbackInfo ci) {
        if (DevRuns.KEEP_MOUSE && Util.getPlatform() == Util.OS.WINDOWS) {
            long handle = GLFWNativeWin32.glfwGetWin32Window(this.window);
            User32.SetWindowPos(handle, User32.HWND_BOTTOM, 0, 0, 0, 0, User32.SWP_NOMOVE | User32.SWP_NOSIZE | User32.SWP_NOACTIVATE);
        }
    }
}
