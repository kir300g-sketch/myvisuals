package com.example.visuals;

import net.minecraft.client.MinecraftClient;

/** Плавный поворот: движения мыши копятся и применяются к взгляду постепенно, каждый кадр. */
public final class SmoothLook {
    private SmoothLook() {
    }

    public static boolean applying = false;
    private static double px = 0, py = 0;
    private static long last = 0;

    public static boolean enabled() {
        return Config.I.smoothEnabled;
    }

    public static void add(double dx, double dy) {
        px += dx;
        py += dy;
    }

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
        if (!Config.I.smoothEnabled) {
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
