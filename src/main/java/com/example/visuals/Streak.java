package com.example.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Серия убийств: надписи с эффектами на несколько секунд, серия держится заданное время. */
public final class Streak {
    private Streak() {
    }

    public static final String[] NAMES = {"Первая кровь", "Двойное убийство", "Тройное убийство", "Ультра кил", "Буйство"};
    private static int count = 0, level = 0;
    private static long lastKill = 0, showT0 = 0;

    public static void reset() {
        count = 0;
        level = 0;
    }

    public static void onKill() {
        long now = System.currentTimeMillis();
        Config c = Config.I;
        if (count > 0 && now - lastKill > (long) (c.streakTime * 1000)) count = 0;
        count++;
        lastKill = now;
        level = Math.min(5, count);
        showT0 = now;
        SoundFx.play(1 + level);
    }

    private static float rnd(int i, int salt) {
        float v = (float) Math.sin(i * 12.9898 + salt * 78.233) * 43758.5453f;
        return v - (float) Math.floor(v);
    }

    private static void blood(DrawContext c, int x0, int y0, int x1, int y1, float t, float a) {
        int bw = x1 - x0, bh = y1 - y0;
        for (int i = 0; i < 110; i++) {
            float appear = rnd(i, 3) * 0.4f;
            if (t < appear) continue;
            int px = x0 + (int) (rnd(i, 1) * bw), py = y0 + (int) (rnd(i, 2) * bh);
            int s = 1 + (int) (rnd(i, 4) * 3);
            int col = Ui.col(Ui.mix(0x5A0000, 0xC01010, 0.6f + 0.4f * rnd(i, 5)), a * 0.9f);
            c.fill(px, py, px + s, py + s, col);
            if (rnd(i, 6) > 0.8f) c.fill(px, py, px + 1, py + (int) ((3 + rnd(i, 7) * 10) * Math.min(1f, t * 2f)), col);
        }
    }

    private static void gold(DrawContext c, int x0, int y0, int x1, int y1, int cx, int cy, float t, float a) {
        for (int k = 0; k < 10; k++) {
            int hw = (int) ((x1 - x0) * 0.5f * (1f - k / 10f));
            c.fill(cx - hw, y0, cx + hw, y1, Ui.col(0xFFD040, a * 0.07f));
        }
        for (int i = 0; i < 18; i++) {
            float ang = rnd(i, 1) * 6.2832f;
            float len = (40 + rnd(i, 2) * 120) * Math.min(1f, t * 3f);
            for (float st = 0; st < len; st += 3) {
                int px = cx + (int) (Math.cos(ang) * st), py = cy + (int) (Math.sin(ang) * st * 0.35f);
                if (px < x0 || px > x1 || py < y0 - 12 || py > y1 + 12) continue;
                c.fill(px, py, px + 2, py + 1, Ui.col(0xFFE070, a * 0.6f * (1f - st / len)));
            }
        }
    }

    private static void embers(DrawContext c, int x0, int y0, int x1, int y1, float t, float a, int color) {
        int bw = x1 - x0, bh = y1 - y0;
        for (int i = 0; i < 40; i++) {
            int ex = x0 + (int) (rnd(i, 1) * bw);
            int ey = y1 - (int) ((t * 30f * (0.5f + rnd(i, 2)) + rnd(i, 3) * bh) % bh);
            c.fill(ex, ey, ex + 2, ey + 2, Ui.col(color, a * 0.8f));
        }
    }

    private static void red(DrawContext c, int x0, int y0, int x1, int y1, int cx, float t, float a) {
        for (int k = 0; k < 10; k++) {
            int hw = (int) ((x1 - x0) * 0.5f * (1f - k / 10f));
            c.fill(cx - hw, y0, cx + hw, y1, Ui.col(0xFF2010, a * 0.09f));
        }
        embers(c, x0, y0, x1, y1, t, a, 0xFFA040);
    }

    private static void flame(DrawContext c, int x0, int y0, int x1, int y1, int cx, float t, float a) {
        int bw = x1 - x0, bh = y1 - y0;
        for (int r = 0; r < bh; r++) {
            float v = (r - bh / 2f) / (bh / 2f);
            float jitter = rnd(r + (int) (t * 25), 9) * 10f;
            float w = (float) Math.sqrt(Math.max(0f, 1f - v * v)) * bw * 0.45f * (0.85f + 0.15f * (float) Math.sin(t * 20 + r * 0.8f)) + jitter;
            int col = Ui.mix(0xFFE060, 0xFF6A10, Math.abs(v));
            c.fill(cx - (int) (w * 1.15f), y0 + r, cx + (int) (w * 1.15f), y0 + r + 1, Ui.col(0xFF8A20, a * 0.25f));
            c.fill(cx - (int) w, y0 + r, cx + (int) w, y0 + r + 1, Ui.col(col, a * 0.85f));
        }
    }

    private static void bolt(DrawContext c, int bx, int ytop, int ybot, int seed, float a) {
        int px = bx, py = ytop;
        int segs = 9;
        for (int s = 1; s <= segs; s++) {
            int ny = ytop + s * (ybot - ytop) / segs;
            int nx = px + (int) ((rnd(s + seed * 10, 2) - 0.5f) * 16);
            int steps = Math.max(1, Math.abs(ny - py));
            for (int k = 0; k < steps; k++) {
                int x = px + (nx - px) * k / steps, y = py + (ny - py) * k / steps;
                c.fill(x - 1, y, x + 1, y + 1, Ui.col(0xFFF2B0, a * 0.9f));
            }
            px = nx;
            py = ny;
        }
    }

    public static void render(DrawContext c, MinecraftClient mc, int sw, int sh) {
        Config cfg = Config.I;
        if (!cfg.streakEnabled || level == 0) return;
        long now = System.currentTimeMillis();
        float t = (now - showT0) / 1000f;
        float dur = cfg.streakShow;
        if (t > dur) return;
        float a = Math.min(1f, t / 0.2f) * (t > dur - 0.6f ? Math.max(0f, (dur - t) / 0.6f) : 1f);
        TextRenderer tr = mc.textRenderer;
        int bw = Math.min((int) (sw * 0.7f), 360), bh = 44;
        int x0 = (sw - bw) / 2, y0 = (int) (sh * 0.12f), x1 = x0 + bw, y1 = y0 + bh;
        int cx = sw / 2, cy = (y0 + y1) / 2;

        c.fill(x0, y0, x1, y1, Ui.col(0x000000, 0.35f * a));
        switch (level) {
            case 1:
                blood(c, x0, y0, x1, y1, t, a);
                break;
            case 2:
                gold(c, x0, y0, x1, y1, cx, cy, t, a);
                break;
            case 3:
                red(c, x0, y0, x1, y1, cx, t, a);
                break;
            case 4:
                flame(c, x0, y0, x1, y1, cx, t, a);
                break;
            default: {
                c.fillGradient(x0, y0, x1, y1, Ui.col(0xFF8A20, a * 0.5f), Ui.col(0xB01808, a * 0.5f));
                embers(c, x0, y0, x1, y1, t, a, 0xFFD060);
                for (int bI = 0; bI < 2; bI++) {
                    if ((int) (t * 12 + bI * 3) % 3 != 0) {
                        int seed = bI + (int) (t * 12);
                        bolt(c, x0 + (int) (rnd(seed, 1) * bw), y0 - 10, y1 + 10, seed, a);
                    }
                }
                break;
            }
        }

        String name = NAMES[level - 1];
        float pop = 1f + 0.3f * Math.max(0f, 1f - t / 0.3f);
        float scale = (level == 5 ? 2.2f : 1.5f) * pop;
        int[] textCol = {0xF2E6D0, 0xFFE9A0, 0xFFD2B8, 0xFFF0B0, 0xC81E0A};
        Text txt = level == 5 ? Text.literal(name).formatted(Formatting.BOLD) : Text.literal(name);
        int tw = tr.getWidth(txt);
        c.getMatrices().push();
        c.getMatrices().translate(cx, cy, 0);
        c.getMatrices().scale(scale, scale, 1f);
        if (level == 5) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) c.drawText(tr, txt, -tw / 2 + dx, -4 + dy, Ui.col(0x3A0800, a), false);
                }
            }
        }
        c.drawText(tr, txt, -tw / 2, -4, Ui.col(textCol[level - 1], a), true);
        c.getMatrices().pop();
    }
}
