package com.example.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/** Обзор 360: камера вращается отдельно от игрока, направление бега не меняется. */
public final class Freelook {
    private Freelook() {
    }

    public static boolean active = false;
    public static float yaw = 0f, pitch = 0f;
    private static boolean wasDown = false;
    private static Perspective prev = null;

    private static boolean down(MinecraftClient mc, int key) {
        long h = mc.getWindow().getHandle();
        if (key < 0) return GLFW.glfwGetMouseButton(h, -1 - key) == GLFW.GLFW_PRESS;
        return InputUtil.isKeyPressed(h, key);
    }

    public static void tick(MinecraftClient mc) {
        Config c = Config.I;
        if (!c.freelookEnabled || mc.player == null || mc.currentScreen != null) {
            if (active) stop(mc);
            wasDown = false;
            return;
        }
        boolean d = down(mc, c.freelookKey);
        if (c.freelookMode == 0) {
            if (d && !active) start(mc);
            else if (!d && active) stop(mc);
        } else {
            if (d && !wasDown) {
                if (active) stop(mc);
                else start(mc);
            }
        }
        wasDown = d;
    }

    private static void start(MinecraftClient mc) {
        active = true;
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        prev = mc.options.getPerspective();
        if (Config.I.freelookThird) mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
    }

    private static void stop(MinecraftClient mc) {
        active = false;
        if (prev != null && Config.I.freelookThird) mc.options.setPerspective(prev);
        prev = null;
    }

    public static void rotate(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch = MathHelper.clamp(pitch + (float) dy * 0.15f, -90f, 90f);
    }
}
