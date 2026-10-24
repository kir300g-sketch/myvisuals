package com.example.visuals;

import net.minecraft.client.MinecraftClient;

/**
 * Плавный поворот. Режим 0: наш (движения мыши копятся и применяются постепенно, сила настраивается).
 * Режим 1: ванильная кинематографичная камера Minecraft.
 */
public final class SmoothLook {
    private SmoothLook() {
    }

    public static boolean applying = false;
    private static double px = 0, py = 0;
    private static long last = 0;
    private static boolean vanillaSetByUs = false;

    public static boolean enabled() {
        return Config.I.smoothEnabled && Config.I.smoothMode == 0;
    }

    public static void add(double dx, double dy) {
        px += dx;
        py += dy;
    }

    /** Раз в тик: включает/выключает ванильную плавную камеру. */
    public static void tick(MinecraftClient mc) {
        boolean wantVanilla = Config.I.smoothEnabled && Config.I.smoothMode == 1;
        if (wantVanilla) {
            mc.options.smoothCameraEnabled = true;
            vanillaSetByUs = true;
        } else if (vanillaSetByUs) {
            mc.options.smoothCameraEnabled = false;
            vanillaSetByUs = false;
        }
    }

    /** Раз в кадр. */
    public static void frame() {
        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        if (mc.player == null) {
            px = 0;
            py = 0;
            return;
        }
        if (!enabled()) {
            if (px != 0 || py != 0) {
                applying = true;
                mc.player.changeLookDirection(px, py);
                applying = false;
                px = 0;
                py = 0;
            }
            return;
        }
        if (Math.abs(px) < 1e-4 && Math.abs(py) < 1e-4) return;
        float k = 60f / Math.max(1f, Config.I.smoothAmount);
        double f = 1.0 - Math.exp(-dt * k);
        double ax = px * f, ay = py * f;
        px -= ax;
        py -= ay;
        applying = true;
        mc.player.changeLookDirection(ax, ay);
        applying = false;
    }
}
