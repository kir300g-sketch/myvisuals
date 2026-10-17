package com.example.visuals;

import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public final class Ui {
    private Ui() {
    }

    public static final String[] THEMES = {"Фиолет", "Океан", "Закат", "Мята", "Роза", "Радуга", "Свой"};
    private static final int[] THEME_COLORS = {0x7C5CFF, 0x2EA8FF, 0xFF7A45, 0x3DDC97, 0xFF5C93};
    public static final String[] BGS = {"Тёмный", "Графит", "Синий", "Бордо"};
    private static final int[] BG_COLORS = {0x14141C, 0x1B1B1B, 0x0F1A2E, 0x2A1018};

    public static int accent() {
        Config c = Config.I;
        int t = c.guiTheme;
        if (t >= 0 && t < THEME_COLORS.length) return THEME_COLORS[t];
        if (t == 5) return Color.HSBtoRGB((System.currentTimeMillis() % 5000) / 5000f, 0.65f, 1f) & 0xFFFFFF;
        return Color.HSBtoRGB(c.guiAccentHue, 0.7f, 1f) & 0xFFFFFF;
    }

    public static int bgColor() {
        int i = Config.I.guiBg;
        if (i < 0 || i >= BG_COLORS.length) i = 0;
        return BG_COLORS[i];
    }

    public static int col(int rgb, float a) {
        int al = Math.max(5, Math.min(255, (int) (a * 255)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static float approach(float cur, float target, float speed) {
        return cur + (target - cur) * Math.min(1f, speed);
    }

    public static void rr(DrawContext c, int x1, int y1, int x2, int y2, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(y2 - y1, x2 - x1) / 2));
        for (int i = 0; i < r; i++) {
            double d = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - d * d));
            c.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            c.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
        }
        c.fill(x1, y1 + r, x2, y2 - r, color);
    }
}
