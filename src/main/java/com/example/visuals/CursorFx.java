package com.example.visuals;

import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Эффекты за курсором мыши в любых экранах (инвентарь, меню и т.д.). */
public final class CursorFx {
    private CursorFx() {
    }

    private static class P {
        float x, y, vx, vy, life, age, seed;
    }

    private static final List<P> list = new ArrayList<>();
    private static final Random RNG = new Random();
    private static float lastX = -1, lastY = -1;
    private static long lastT = 0;
    private static final String[] HEART = {" ## ## ", "#######", "#######", " ##### ", "  ###  ", "   #   "};

    public static final String[] NAMES = {"Выкл", "Искры", "Звёзды", "Сердечки", "Радуга", "Огонь"};

    private static int color(Config c, P p) {
        if (c.cursorFx == 5) return -1;
        float h = c.cursorRainbow ? ((System.currentTimeMillis() % 3000) / 3000f + p.seed * 0.3f) % 1f : c.cursorHue;
        return Color.HSBtoRGB(h, 0.75f, 1f) & 0xFFFFFF;
    }

    public static void render(DrawContext c, int mx, int my) {
        Config cfg = Config.I;
        long now = System.nanoTime();
        float dt = lastT == 0 ? 0.016f : Math.min(0.1f, (now - lastT) / 1e9f);
        lastT = now;
        if (cfg.cursorFx == 0) {
            list.clear();
            lastX = -1;
            return;
        }

        if (lastX >= 0) {
            float dx = mx - lastX, dy = my - lastY;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            int n = (int) Math.min(8, d * 0.4f * cfg.cursorDensity + (RNG.nextFloat() < 0.35f * cfg.cursorDensity ? 1 : 0));
            for (int k = 0; k < n && list.size() < 400; k++) {
                float t = RNG.nextFloat();
                P p = new P();
                p.x = lastX + dx * t;
                p.y = lastY + dy * t;
                p.seed = RNG.nextFloat();
                p.vx = (RNG.nextFloat() - 0.5f) * 30f;
                p.vy = (RNG.nextFloat() - 0.5f) * 30f;
                p.life = 0.5f + RNG.nextFloat() * 0.5f;
                if (cfg.cursorFx == 3) {
                    p.vy = -25f - RNG.nextFloat() * 15f;
                    p.life = 0.9f;
                } else if (cfg.cursorFx == 5) {
                    p.vy = -40f - RNG.nextFloat() * 30f;
                    p.vx *= 0.4f;
                    p.life = 0.5f;
                }
                list.add(p);
            }
        }
        lastX = mx;
        lastY = my;

        c.getMatrices().push();
        c.getMatrices().translate(0f, 0f, 400f);
        Iterator<P> it = list.iterator();
        while (it.hasNext()) {
            P p = it.next();
            p.age += dt;
            if (p.age >= p.life) {
                it.remove();
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            float a = 1f - p.age / p.life;
            int px = Math.round(p.x), py = Math.round(p.y);
            int s = Math.max(1, Math.round(cfg.cursorSize * (0.6f + 0.8f * p.seed)));
            int rgb = color(cfg, p);
            switch (cfg.cursorFx) {
                case 1: {
                    int col = Ui.col(rgb, a);
                    c.fill(px - s, py, px + s + 1, py + 1, col);
                    c.fill(px, py - s, px + 1, py + s + 1, col);
                    c.fill(px, py, px + 1, py + 1, Ui.col(0xFFFFFF, a));
                    break;
                }
                case 2: {
                    int col = Ui.col(rgb, a);
                    int s2 = s + 1;
                    c.fill(px - s2, py, px + s2 + 1, py + 1, col);
                    c.fill(px, py - s2, px + 1, py + s2 + 1, col);
                    c.fill(px - s, py - s, px + s + 1, py - s + 1, Ui.col(rgb, a * 0.6f));
                    c.fill(px - s, py + s, px + s + 1, py + s + 1, Ui.col(rgb, a * 0.6f));
                    c.fill(px - 1, py - 1, px + 2, py + 2, Ui.col(0xFFFFFF, a));
                    break;
                }
                case 3: {
                    int sc = Math.max(1, s);
                    for (int r = 0; r < HEART.length; r++) {
                        for (int q = 0; q < 7; q++) {
                            if (HEART[r].charAt(q) == '#') {
                                int hx = px + (q - 3) * sc, hy = py + (r - 3) * sc;
                                c.fill(hx, hy, hx + sc, hy + sc, Ui.col(rgb, a));
                            }
                        }
                    }
                    break;
                }
                case 4: {
                    int sz = Math.max(1, Math.round(s * (0.4f + 0.6f * a)));
                    c.fill(px - sz, py - sz, px + sz, py + sz, Ui.col(rgb, a));
                    c.fill(px - sz - 1, py, px + sz + 1, py + 1, Ui.col(rgb, a * 0.5f));
                    break;
                }
                default: {
                    float t = p.age / p.life;
                    int fr = t < 0.4f ? Ui.mix(0xFFE040, 0xFF8C20, t / 0.4f) : Ui.mix(0xFF8C20, 0xE02010, (t - 0.4f) / 0.6f);
                    int sz = Math.max(1, Math.round(s * (1.2f - t)));
                    c.fill(px - sz, py - sz, px + sz, py + sz, Ui.col(fr, a));
                    break;
                }
            }
        }
        c.getMatrices().pop();
    }
}
