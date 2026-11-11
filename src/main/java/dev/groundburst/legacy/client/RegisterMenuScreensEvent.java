package dev.groundburst.legacy.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/** NeoForge's menu screen registration: posted on the mod bus at client setup ({@link ClientLifecycle}). */
public final class RegisterMenuScreensEvent extends Event implements IModBusEvent {
    public RegisterMenuScreensEvent() {}

    public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(MenuType<? extends M> menuType,
            MenuScreens.ScreenConstructor<M, U> screenConstructor) {
        MenuScreens.register(menuType, screenConstructor);
    }
}
