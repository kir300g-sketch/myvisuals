package com.example.visuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class VisualsClient implements ClientModInitializer {
    public static KeyBinding menuKey;

    @Override
    public void onInitializeClient() {
        Config.load();
        Hud.register();

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.myvisuals.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "key.categories.myvisuals"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new VisualsScreen());
                }
            }
            WorldRender.tick(client);
            Totem.tick(client);
            Freelook.tick(client);
            TestPlayer.tick(client);
        });

        // небо рисуем до сущностей и частиц: так оно не перекрывает их и не ломает их текстуры
        WorldRenderEvents.BEFORE_ENTITIES.register(WorldRender::renderSky);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WorldRender::render);
    }
}
