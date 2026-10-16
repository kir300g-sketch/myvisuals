package com.example.visuals;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class VisualsScreen extends Screen {
    private static final int W = 290;
    private int H = 262;
    private static final int ROW = 28;
    private static final int ACCENT = 0x7C5CFF;

    private final String[] tabNames = {"Аура", "След", "Эффекты", "Мир"};
    private final List<List<Option>> pages = new ArrayList<>();

    private int tab = 0;
    private float tabX = -1;
    private float open = 0f;
    private float pageAnim = 1f;
    private boolean closing = false;
    private long last = Util.getMeasuringTimeMs();
    private Option dragging = null;

    public VisualsScreen() {
        super(Text.literal("Visuals"));
        Config c = Config.I;

        List<Option> aura = new ArrayList<>();
        aura.add(new Toggle("Включить ауру", () -> c.auraEnabled, v -> c.auraEnabled = v));
        aura.add(new Toggle("Радужный цвет", () -> c.auraRainbow, v -> c.auraRainbow = v));
        aura.add(new HueSlider("Цвет", () -> c.auraHue, v -> c.auraHue = v));
        aura.add(new Slider("Радиус", 0.5f, 2.5f, 2, () -> c.auraRadius, v -> c.auraRadius = v));
        aura.add(new Slider("Скорость", 0.05f, 0.5f, 2, () -> c.auraSpeed, v -> c.auraSpeed = v));
        aura.add(new Slider("Количество", 1, 8, 0, () -> (float) c.auraCount, v -> c.auraCount = Math.round(v)));
        pages.add(aura);

        List<Option> trail = new ArrayList<>();
        trail.add(new Toggle("Частицы-след", () -> c.trailEnabled, v -> c.trailEnabled = v));
        trail.add(new Toggle("Лента-шлейф", () -> c.ribbonEnabled, v -> c.ribbonEnabled = v));
        trail.add(new Toggle("Радужный цвет", () -> c.trailRainbow, v -> c.trailRainbow = v));
        trail.add(new HueSlider("Цвет", () -> c.trailHue, v -> c.trailHue = v));
        trail.add(new Slider("Плотность частиц", 1, 6, 0, () -> (float) c.trailDensity, v -> c.trailDensity = Math.round(v)));
        trail.add(new Slider("Размер частиц", 0.4f, 3f, 2, () -> c.trailSize, v -> c.trailSize = v));
        trail.add(new Slider("Длина ленты (сек)", 0.5f, 4f, 1, () -> c.ribbonLife, v -> c.ribbonLife = v));
        trail.add(new Slider("Высота ленты", 0.1f, 1.2f, 2, () -> c.ribbonHeight, v -> c.ribbonHeight = v));
        pages.add(trail);

        List<Option> fx = new ArrayList<>();
        fx.add(new Toggle("Круги при прыжке", () -> c.jumpEnabled, v -> c.jumpEnabled = v));
        fx.add(new Slider("Размер кругов", 0.8f, 3.5f, 1, () -> c.jumpSize, v -> c.jumpSize = v));
        fx.add(new Toggle("Китайская шляпа", () -> c.hatEnabled, v -> c.hatEnabled = v));
        fx.add(new Slider("Размер шляпы", 0.6f, 2f, 1, () -> c.hatSize, v -> c.hatSize = v));
        fx.add(new Toggle("Нимб", () -> c.haloEnabled, v -> c.haloEnabled = v));
        fx.add(new Toggle("Радужный цвет", () -> c.fxRainbow, v -> c.fxRainbow = v));
        fx.add(new HueSlider("Цвет эффектов", () -> c.fxHue, v -> c.fxHue = v));
        pages.add(fx);

        List<Option> world = new ArrayList<>();
        world.add(new Toggle("Снег", () -> c.snowEnabled, v -> c.snowEnabled = v));
        world.add(new Slider("Сила снега", 1, 20, 0, () -> (float) c.snowDensity, v -> c.snowDensity = Math.round(v)));
        world.add(new Toggle("Светлячки", () -> c.fireflies, v -> c.fireflies = v));
        world.add(new Toggle("Своё время суток", () -> c.timeOverride, v -> c.timeOverride = v));
        world.add(new Slider("Время (0-24000)", 0, 24000, 0, () -> c.timeOfDay, v -> c.timeOfDay = v));
        pages.add(world);

        int rows = 0;
        for (List<Option> pg : pages) rows = Math.max(rows, pg.size());
        H = 62 + rows * ROW + 26;
    }

    // ---------- утилиты ----------
    private static void rr(DrawContext c, int x1, int y1, int x2, int y2, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(y2 - y1, x2 - x1) / 2));
        for (int i = 0; i < r; i++) {
            double d = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - d * d));
            c.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            c.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
        }
        c.fill(x1, y1 + r, x2, y2 - r, color);
    }

    private static int col(int rgb, float a) {
        int al = Math.max(5, Math.min(255, (int) (a * 255)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static float approach(float cur, float target, float speed) {
        return cur + (target - cur) * Math.min(1f, speed);
    }

    // ---------- виджеты ----------
    private abstract static class Option {
        final String label;
        int x, y, w;
        float hover = 0f;

        Option(String label) {
            this.label = label;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + ROW - 4;
        }

        void click(double mx) {}

        void drag(double mx) {}

        abstract void render(DrawContext c, TextRenderer tr, int mx, int my, float dt, float a);

        void updateHover(int mx, int my, float dt) {
            hover = approach(hover, contains(mx, my) ? 1f : 0f, dt * 14f);
        }
    }

    private static class Toggle extends Option {
        final Supplier<Boolean> get;
        final Consumer<Boolean> set;
        float t = -1;

        Toggle(String label, Supplier<Boolean> get, Consumer<Boolean> set) {
            super(label);
            this.get = get;
            this.set = set;
        }

        @Override
        void click(double mx) {
            set.accept(!get.get());
        }

        @Override
        void render(DrawContext c, TextRenderer tr, int mx, int my, float dt, float a) {
            updateHover(mx, my, dt);
            if (t < 0) t = get.get() ? 1f : 0f;
            t = approach(t, get.get() ? 1f : 0f, dt * 12f);

            rr(c, x, y, x + w, y + ROW - 4, 5, col(0xFFFFFF, (0.04f + 0.05f * hover) * a));
            c.drawText(tr, label, x + 8, y + 8, col(0xFFFFFF, (0.8f + 0.2f * hover) * a), false);

            int sw = 30, sh = 14;
            int sx = x + w - sw - 8, sy = y + 5;
            rr(c, sx, sy, sx + sw, sy + sh, 7, col(mix(0x3A3A48, ACCENT, t), a));
            int kx = sx + 2 + Math.round(16 * t);
            rr(c, kx, sy + 2, kx + 10, sy + sh - 2, 5, col(0xFFFFFF, a));
        }
    }

    private static class Slider extends Option {
        final float min, max;
        final int decimals;
        final Supplier<Float> get;
        final Consumer<Float> set;
        float disp = -1;

        Slider(String label, float min, float max, int decimals, Supplier<Float> get, Consumer<Float> set) {
            super(label);
            this.min = min;
            this.max = max;
            this.decimals = decimals;
            this.get = get;
            this.set = set;
        }

        float norm() {
            return (get.get() - min) / (max - min);
        }

        void setFromMouse(double mx) {
            float t = (float) Math.max(0, Math.min(1, (mx - (x + 8)) / (double) (w - 16)));
            set.accept(min + t * (max - min));
        }

        @Override
        void click(double mx) {
            setFromMouse(mx);
        }

        @Override
        void drag(double mx) {
            setFromMouse(mx);
        }

        void drawTrack(DrawContext c, int tx, int ty, int tw, float a) {
            rr(c, tx, ty, tx + tw, ty + 4, 2, col(0x3A3A48, a));
            rr(c, tx, ty, tx + Math.max(4, Math.round(tw * disp)), ty + 4, 2, col(ACCENT, a));
        }

        @Override
        void render(DrawContext c, TextRenderer tr, int mx, int my, float dt, float a) {
            updateHover(mx, my, dt);
            if (disp < 0) disp = norm();
            disp = approach(disp, norm(), dt * 18f);

            rr(c, x, y, x + w, y + ROW - 4, 5, col(0xFFFFFF, (0.04f + 0.05f * hover) * a));
            c.drawText(tr, label, x + 8, y + 4, col(0xFFFFFF, (0.8f + 0.2f * hover) * a), false);
            String v = String.format(Locale.ROOT, "%." + decimals + "f", get.get());
            c.drawText(tr, v, x + w - 8 - tr.getWidth(v), y + 4, col(0xB9A8FF, a), false);

            int tx = x + 8, tw = w - 16, ty = y + 15;
            drawTrack(c, tx, ty, tw, a);
            int kx = tx + Math.round(tw * disp);
            rr(c, kx - 3, ty - 2, kx + 3, ty + 6, 3, col(0xFFFFFF, a));
        }
    }

    private static class HueSlider extends Slider {
        HueSlider(String label, Supplier<Float> get, Consumer<Float> set) {
            super(label, 0f, 1f, 2, get, set);
        }

        @Override
        void drawTrack(DrawContext c, int tx, int ty, int tw, float a) {
            for (int i = 0; i < tw; i += 2) {
                int rgb = Color.HSBtoRGB(i / (float) tw, 0.85f, 1f) & 0xFFFFFF;
                c.fill(tx + i, ty, tx + Math.min(tw, i + 2), ty + 4, col(rgb, a));
            }
        }
    }

    // ---------- отрисовка ----------
    @Override
    public void render(DrawContext c, int mx, int my, float tickDelta) {
        long now = Util.getMeasuringTimeMs();
        float dt = Math.min((now - last) / 1000f, 0.1f);
        last = now;

        open += (closing ? -1 : 1) * dt * 4.5f;
        open = Math.max(0, Math.min(1, open));
        if (closing && open <= 0) {
            this.client.setScreen(null);
            return;
        }
        pageAnim = Math.min(1f, pageAnim + dt * 5f);

        float ease = 1f - (float) Math.pow(1f - open, 3);
        float pe = 1f - (float) Math.pow(1f - pageAnim, 3);

        // фон
        c.fill(0, 0, width, height, col(0x000000, 0.45f * ease));

        int px = (width - W) / 2;
        int py = (height - H) / 2 + Math.round((1f - ease) * 26);

        // тень и панель
        for (int i = 4; i >= 1; i--) {
            rr(c, px - i * 2, py - i * 2, px + W + i * 2, py + H + i * 2, 8 + i * 2, col(0x000000, 0.07f * ease));
        }
        rr(c, px, py, px + W, py + H, 8, col(0x14141C, 0.95f * ease));
        rr(c, px, py, px + W, py + 28, 8, col(0x221C44, ease));
        c.fill(px, py + 14, px + W, py + 28, col(0x221C44, ease));

        // заголовок
        c.drawText(textRenderer, "✦ Visuals", px + 12, py + 10, col(0xFFFFFF, ease), true);

        // бегущая радужная линия
        float time = (now % 6000) / 6000f;
        for (int i = 0; i < W; i += 3) {
            int rgb = Color.HSBtoRGB((i / (float) W + time) % 1f, 0.7f, 1f) & 0xFFFFFF;
            c.fill(px + i, py + 28, px + Math.min(W, i + 3), py + 30, col(rgb, 0.9f * ease));
        }

        // вкладки
        int tw = (W - 24) / tabNames.length;
        int ty = py + 36;
        float target = px + 12 + tab * tw;
        if (tabX < 0) tabX = target;
        tabX = approach(tabX, target, dt * 14f);
        for (int i = 0; i < tabNames.length; i++) {
            int tx = px + 12 + i * tw;
            boolean hov = mx >= tx && mx < tx + tw && my >= ty && my < ty + 16;
            float al = (i == tab ? 1f : (hov ? 0.85f : 0.55f)) * ease;
            int wdt = textRenderer.getWidth(tabNames[i]);
            c.drawText(textRenderer, tabNames[i], tx + (tw - wdt) / 2, ty + 2, col(0xFFFFFF, al), false);
        }
        c.fill(Math.round(tabX) + 6, ty + 14, Math.round(tabX) + tw - 6, ty + 16, col(ACCENT, ease));

        // опции текущей страницы
        int ox = Math.round((1f - pe) * 14f);
        int oy = py + 62;
        float a = ease * pe;
        for (Option o : pages.get(tab)) {
            o.x = px + 12 + ox;
            o.y = oy;
            o.w = W - 24;
            o.render(c, textRenderer, mx, my, dt, a);
            oy += ROW;
        }

        // подсказка
        String hint = "Right Shift - закрыть";
        c.drawText(textRenderer, hint, px + W - 12 - textRenderer.getWidth(hint), py + H - 14, col(0x888899, ease), false);
    }

    // фон рисуем сами (без размытия)
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    // ---------- ввод ----------
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || closing) return super.mouseClicked(mx, my, button);

        int px = (width - W) / 2;
        int py = (height - H) / 2;
        int tw = (W - 24) / tabNames.length;
        int ty = py + 36;
        for (int i = 0; i < tabNames.length; i++) {
            int tx = px + 12 + i * tw;
            if (mx >= tx && mx < tx + tw && my >= ty && my < ty + 16) {
                if (tab != i) {
                    tab = i;
                    pageAnim = 0f;
                    dragging = null;
                }
                return true;
            }
        }
        for (Option o : pages.get(tab)) {
            if (o.contains(mx, my)) {
                o.click(mx);
                if (o instanceof Slider) dragging = o;
                else Config.save();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            dragging.drag(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) {
            dragging = null;
            Config.save();
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void close() {
        if (!closing) {
            closing = true;
            Config.save();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
