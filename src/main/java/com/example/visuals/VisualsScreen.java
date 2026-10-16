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
    private static final int W = 360;
    private static final int VIS = 8;
    private static final int ROW = 28;
    private static final int H = 62 + VIS * ROW + 26;

    private static final String[] THEMES = {"Фиолет", "Океан", "Закат", "Мята", "Роза", "Радуга", "Свой"};
    private static final int[] THEME_COLORS = {0x7C5CFF, 0x2EA8FF, 0xFF7A45, 0x3DDC97, 0xFF5C93};
    private static final String[] BGS = {"Тёмный", "Графит", "Синий", "Бордо"};
    private static final int[] BG_COLORS = {0x14141C, 0x1B1B1B, 0x0F1A2E, 0x2A1018};

    private final String[] tabNames = {"След", "Эффекты", "Мир", "Чанки", "Меч", "Меню"};
    private final List<List<Option>> pages = new ArrayList<>();
    private final float[] scrollTarget = new float[6];
    private final float[] scrollDisp = new float[6];

    private int tab = 0;
    private float tabX = -1;
    private float open = 0f;
    private float pageAnim = 1f;
    private boolean closing = false;
    private long last = Util.getMeasuringTimeMs();
    private Option dragging = null;
    private boolean draggingHeader = false;
    private float curX = Float.NaN, curY = 0f;
    private int panelX = 0, panelY = 0;

    // ---------- конструктор: страницы ----------
    public VisualsScreen() {
        super(Text.literal("Visuals"));
        Config c = Config.I;

        List<Option> trail = new ArrayList<>();
        trail.add(new Header("Лента-шлейф"));
        trail.add(new Toggle("Включить ленту", () -> c.ribbonEnabled, v -> c.ribbonEnabled = v));
        trail.add(new Toggle("Радужный цвет", () -> c.trailRainbow, v -> c.trailRainbow = v));
        trail.add(new HueSlider("Цвет", () -> c.trailHue, v -> c.trailHue = v));
        trail.add(new Slider("Длина (сек)", 0.5f, 4f, 1, () -> c.ribbonLife, v -> c.ribbonLife = v));
        trail.add(new Slider("Высота", 0.1f, 1.5f, 2, () -> c.ribbonHeight, v -> c.ribbonHeight = v));
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
        world.add(new Header("Неоновый дождь"));
        world.add(new Toggle("Включить дождь", () -> c.rainEnabled, v -> c.rainEnabled = v));
        world.add(new Slider("Скорость", 0.3f, 4f, 2, () -> c.rainSpeed, v -> c.rainSpeed = v));
        world.add(new Slider("Частота", 1, 40, 0, () -> (float) c.rainFreq, v -> c.rainFreq = Math.round(v)));
        world.add(new Slider("Плотность", 50, 2000, 0, () -> (float) c.rainDensity, v -> c.rainDensity = Math.round(v)));
        world.add(new Slider("Длина капель", 0.3f, 3f, 2, () -> c.rainLength, v -> c.rainLength = v));
        world.add(new Header("Падающие звёзды"));
        world.add(new Toggle("Включить звёзды", () -> c.starsEnabled, v -> c.starsEnabled = v));
        world.add(new Slider("Скорость", 0.2f, 4f, 2, () -> c.starSpeed, v -> c.starSpeed = v));
        world.add(new Slider("Частота", 1, 20, 0, () -> (float) c.starFreq, v -> c.starFreq = Math.round(v)));
        world.add(new Slider("Плотность", 20, 800, 0, () -> (float) c.starDensity, v -> c.starDensity = Math.round(v)));
        world.add(new Slider("Размер", 0.1f, 0.8f, 2, () -> c.starSize, v -> c.starSize = v));
        world.add(new Header("Цвет"));
        world.add(new Toggle("Радужный цвет", () -> c.wxRainbow, v -> c.wxRainbow = v));
        world.add(new HueSlider("Оттенок", () -> c.wxHue, v -> c.wxHue = v));
        pages.add(world);

        List<Option> chunks = new ArrayList<>();
        chunks.add(new Toggle("Подъём чанков", () -> c.chunkAnim, v -> c.chunkAnim = v));
        chunks.add(new Slider("Глубина (блоки)", 4, 64, 0, () -> c.chunkDepth, v -> c.chunkDepth = v));
        chunks.add(new Slider("Время (сек)", 0.3f, 4f, 1, () -> c.chunkTime, v -> c.chunkTime = v));
        chunks.add(new Header("Не работает с Sodium"));
        pages.add(chunks);

        List<Option> sword = new ArrayList<>();
        sword.add(new Toggle("Свои позы меча", () -> c.swordEnabled, v -> c.swordEnabled = v));
        sword.add(new Choice("Пресет позиции", SwordPose.PRESETS, () -> c.swordPreset, i -> {
            c.swordPreset = i;
            SwordPose.applyPreset(i);
        }));
        sword.add(new Choice("Анимация удара", SwordPose.ANIMS, () -> c.swordAnim, i -> c.swordAnim = i));
        sword.add(new Slider("Сила анимации", 0.2f, 2f, 2, () -> c.swordIntensity, v -> c.swordIntensity = v));
        sword.add(new Slider("Плавность (кривая)", 0.5f, 3f, 2, () -> c.swordCurve, v -> c.swordCurve = v));
        sword.add(new Header("Положение"));
        sword.add(new Slider("Сдвиг X", -1f, 1f, 2, () -> c.swordX, v -> c.swordX = v));
        sword.add(new Slider("Сдвиг Y", -1f, 1f, 2, () -> c.swordY, v -> c.swordY = v));
        sword.add(new Slider("Сдвиг Z", -1f, 1f, 2, () -> c.swordZ, v -> c.swordZ = v));
        sword.add(new Slider("Поворот X", -180f, 180f, 0, () -> c.swordRotX, v -> c.swordRotX = v));
        sword.add(new Slider("Поворот Y", -180f, 180f, 0, () -> c.swordRotY, v -> c.swordRotY = v));
        sword.add(new Slider("Поворот Z", -180f, 180f, 0, () -> c.swordRotZ, v -> c.swordRotZ = v));
        sword.add(new Slider("Размер", 0.3f, 2f, 2, () -> c.swordScale, v -> c.swordScale = v));
        sword.add(new Action("Сбросить положение", () -> {
            c.swordPreset = 0;
            SwordPose.applyPreset(0);
        }));
        pages.add(sword);

        List<Option> gui = new ArrayList<>();
        gui.add(new Choice("Тема", THEMES, () -> c.guiTheme, i -> c.guiTheme = i));
        gui.add(new HueSlider("Свой цвет", () -> c.guiAccentHue, v -> {
            c.guiAccentHue = v;
            c.guiTheme = 6;
        }));
        gui.add(new Choice("Фон", BGS, () -> c.guiBg, i -> c.guiBg = i));
        gui.add(new Slider("Прозрачность", 0.5f, 1f, 2, () -> c.guiAlpha, v -> c.guiAlpha = v));
        gui.add(new Slider("Скругление", 0, 14, 0, () -> (float) c.guiRadius, v -> c.guiRadius = Math.round(v)));
        gui.add(new Slider("Скорость анимаций", 0.3f, 2.5f, 2, () -> c.guiAnimSpeed, v -> c.guiAnimSpeed = v));
        gui.add(new Toggle("Радужная линия", () -> c.guiRainbowLine, v -> c.guiRainbowLine = v));
        gui.add(new Action("Сбросить положение окна", () -> {
            c.guiX = 0f;
            c.guiY = 0f;
        }));
        pages.add(gui);
    }

    // ---------- утилиты ----------
    static int accent() {
        Config c = Config.I;
        int t = c.guiTheme;
        if (t >= 0 && t < THEME_COLORS.length) return THEME_COLORS[t];
        if (t == 5) return Color.HSBtoRGB((System.currentTimeMillis() % 5000) / 5000f, 0.65f, 1f) & 0xFFFFFF;
        return Color.HSBtoRGB(c.guiAccentHue, 0.7f, 1f) & 0xFFFFFF;
    }

    private static int bgColor() {
        int i = Config.I.guiBg;
        if (i < 0 || i >= BG_COLORS.length) i = 0;
        return BG_COLORS[i];
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

    // ---------- виджеты ----------
    private abstract static class Option {
        static int clipTop, clipBottom;
        final String label;
        int x, y, w;
        float hover = 0f;

        Option(String label) {
            this.label = label;
        }

        boolean clickable() {
            return true;
        }

        boolean contains(double mx, double my) {
            return clickable() && mx >= x && mx <= x + w && my >= y && my <= y + ROW - 4
                    && my >= clipTop && my <= clipBottom;
        }

        void click(double mx, int button) {
        }

        void drag(double mx) {
        }

        abstract void render(DrawContext c, TextRenderer tr, int mx, int my, float dt, float a);

        void updateHover(int mx, int my, float dt) {
            hover = approach(hover, contains(mx, my) ? 1f : 0f, dt * 14f);
        }

        void drawRow(DrawContext c, float a) {
            rr(c, x, y, x + w, y + ROW - 4, 5, col(0xFFFFFF, (0.04f + 0.05f * hover) * a));
        }
    }

    private static class Header extends Option {
        Header(String label) {
            super(label);
        }

        @Override
        boolean clickable() {
            return false;
        }

        @Override
        void render(DrawContext c, TextRenderer tr, int mx, int my, float dt, float a) {
            c.drawText(tr, label, x + 4, y + 8, col(accent(), 0.95f * a), false);
            int lx = x + 4 + tr.getWidth(label) + 6;
            c.fill(lx, y + 12, x + w - 4, y + 13, col(0xFFFFFF, 0.10f * a));
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
        void click(double mx, int button) {
            set.accept(!get.get());
        }

        @Override
        void render(DrawContext c, TextRenderer tr, int mx, int my, float dt, float a) {
            updateHover(mx, my, dt);
            if (t < 0) t = get.get() ? 1f : 0f;
            t = approach(t, get.get() ? 1f : 0f, dt * 12f);

            drawRow(c, a);
            c.drawText(tr, label, x + 8, y + 8, col(0xFFFFFF, (0.8f + 0.2f * hover) * a), false);

            int sw = 30, sh = 14;
            int sx = x + w - sw - 8, sy = y + 5;
            rr(c, sx, sy, sx + sw, sy + sh, 7, col(mix(0x3A3A48, accent(), t), a));
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
        void click(double mx, int button) {
            setFromMouse(mx);
        }

        @Override
        void drag(double mx) {
            setFromMouse(mx);
        }

        void drawTrack(DrawContext c, int tx, int ty, int tw, float a) {
            rr(c, tx, ty, tx + tw, ty + 4, 2, col(0x3A3A48, a));
            rr(c, tx, ty, tx + Math.max(4, Math.round(tw * disp)), ty + 4, 2, col(accent(), a));
        }

        @Override
        void render(DrawContext c, TextRenderer tr, int mx, int my, float dt, float a) {
            updateHover(mx, my, dt);
            if (disp < 0) disp = norm();
            disp = approach(disp, norm(), dt * 18f);

            drawRow(c, a);
            c.drawText(tr, label, x + 8, y + 4, col(0xFFFFFF, (0.8f + 0.2f * hover) * a), false);
            String v = String.format(Locale.ROOT, "%." + decimals + "f", get.get());
            c.drawText(tr, v, x + w - 8 - tr.getWidth(v), y + 4, col(mix(accent(), 0xFFFFFF, 0.4f), a), false);

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

    private static class Choice extends Option {
        final String[] names;
        final Supplier<Integer> get;
        final Consumer<Integer> set;
        float flash = 0f;

        Choice(String label, String[] names, Supplier<Integer> get, Consumer<Integer> set) {
            super(label);
            this.names = names;
            this.get = get;
            this.set = set;
        }

        @Override
        void click(double mx, int button) {
            int n = names.length;
            int i = get.get();
            i = (button == 1) ? (i + n - 1) % n : (i + 1) % n;
            set.accept(i);
            flash = 1f;
        }

        @Override
        void render(DrawContext c, TextRenderer tr, int mx, int my, float dt, float a) {
            updateHover(mx, my, dt);
            flash = approach(flash, 0f, dt * 6f);
            drawRow(c, a);
            c.drawText(tr, label, x + 8, y + 8, col(0xFFFFFF, (0.8f + 0.2f * hover) * a), false);
            int i = Math.max(0, Math.min(names.length - 1, get.get()));
            String v = "< " + names[i] + " >";
            int color = mix(accent(), 0xFFFFFF, 0.25f + 0.6f * flash);
            c.drawText(tr, v, x + w - 8 - tr.getWidth(v), y + 8, col(color, a), false);
        }
    }

    private static class Action extends Option {
        final Runnable run;

        Action(String label, Runnable run) {
            super(label);
            this.run = run;
        }

        @Override
        void click(double mx, int button) {
            run.run();
        }

        @Override
        void render(DrawContext c, TextRenderer tr, int mx, int my, float dt, float a) {
            updateHover(mx, my, dt);
            rr(c, x, y, x + w, y + ROW - 4, 5, col(accent(), (0.18f + 0.25f * hover) * a));
            c.drawText(tr, label, x + (w - tr.getWidth(label)) / 2, y + 8, col(0xFFFFFF, a), false);
        }
    }

    // ---------- отрисовка ----------
    @Override
    public void render(DrawContext c, int mx, int my, float tickDelta) {
        Config cfg = Config.I;
        long now = Util.getMeasuringTimeMs();
        float rawDt = Math.min((now - last) / 1000f, 0.1f);
        last = now;
        float dt = rawDt * cfg.guiAnimSpeed;

        open += (closing ? -1 : 1) * dt * 4.5f;
        open = Math.max(0, Math.min(1, open));
        if (closing && open <= 0) {
            this.client.setScreen(null);
            return;
        }
        pageAnim = Math.min(1f, pageAnim + dt * 5f);

        float ease = 1f - (float) Math.pow(1f - open, 3);
        float pe = 1f - (float) Math.pow(1f - pageAnim, 3);

        // положение окна (плавно догоняет цель)
        float targetX = (width - W) / 2f + cfg.guiX;
        float targetY = (height - H) / 2f + cfg.guiY;
        if (Float.isNaN(curX)) {
            curX = targetX;
            curY = targetY;
        }
        float k = Math.min(1f, rawDt * 22f);
        curX += (targetX - curX) * k;
        curY += (targetY - curY) * k;
        int px = Math.round(curX);
        int py = Math.round(curY) + Math.round((1f - ease) * 26);
        panelX = px;
        panelY = py;

        int accent = accent();
        int bg = bgColor();
        int radius = Math.max(0, cfg.guiRadius);

        // затемнение экрана
        c.fill(0, 0, width, height, col(0x000000, 0.45f * ease));

        // тень и панель
        for (int i = 4; i >= 1; i--) {
            rr(c, px - i * 2, py - i * 2, px + W + i * 2, py + H + i * 2, radius + i * 2, col(0x000000, 0.07f * ease));
        }
        rr(c, px, py, px + W, py + H, radius, col(bg, cfg.guiAlpha * ease));
        int hdr = mix(bg, accent, 0.22f);
        rr(c, px, py, px + W, py + 28, radius, col(hdr, ease));
        c.fill(px, py + radius, px + W, py + 28, col(hdr, ease));

        // заголовок
        c.drawText(textRenderer, "✦ Visuals", px + 12, py + 10, col(0xFFFFFF, ease), true);
        String hint = "тяни за заголовок";
        c.drawText(textRenderer, hint, px + W - 12 - textRenderer.getWidth(hint), py + 10, col(0xFFFFFF, 0.35f * ease), false);

        // линия под заголовком
        float time = (now % 6000) / 6000f;
        for (int i = 0; i < W; i += 3) {
            int rgb = cfg.guiRainbowLine
                    ? Color.HSBtoRGB((i / (float) W + time) % 1f, 0.7f, 1f) & 0xFFFFFF
                    : accent;
            c.fill(px + i, py + 28, px + Math.min(W, i + 3), py + 30, col(rgb, 0.9f * ease));
        }

        // вкладки
        int tw = (W - 24) / tabNames.length;
        int ty = py + 36;
        float target = px + 12 + tab * tw;
        if (tabX < 0) tabX = target;
        tabX = approach(tabX, target, rawDt * 14f);
        for (int i = 0; i < tabNames.length; i++) {
            int tx = px + 12 + i * tw;
            boolean hov = mx >= tx && mx < tx + tw && my >= ty && my < ty + 16;
            float al = (i == tab ? 1f : (hov ? 0.85f : 0.55f)) * ease;
            int wdt = textRenderer.getWidth(tabNames[i]);
            c.drawText(textRenderer, tabNames[i], tx + (tw - wdt) / 2, ty + 2, col(0xFFFFFF, al), false);
        }
        c.fill(Math.round(tabX) + 6, ty + 14, Math.round(tabX) + tw - 6, ty + 16, col(accent, ease));

        // список опций со скроллом
        List<Option> list = pages.get(tab);
        int listTop = py + 62;
        int listBottom = listTop + VIS * ROW;
        float maxScroll = Math.max(0, list.size() * ROW - VIS * ROW);
        scrollTarget[tab] = Math.max(0, Math.min(maxScroll, scrollTarget[tab]));
        scrollDisp[tab] = approach(scrollDisp[tab], scrollTarget[tab], rawDt * 16f);

        Option.clipTop = listTop;
        Option.clipBottom = listBottom - 4;
        int ox = Math.round((1f - pe) * 14f);
        float a = ease * pe;
        int rowW = W - 24 - (maxScroll > 0 ? 8 : 0);

        c.enableScissor(px, listTop, px + W, listBottom - 2);
        for (int i = 0; i < list.size(); i++) {
            Option o = list.get(i);
            o.x = px + 12 + ox;
            o.y = listTop - Math.round(scrollDisp[tab]) + i * ROW;
            o.w = rowW;
            if (o.y + ROW > listTop && o.y < listBottom) {
                o.render(c, textRenderer, mx, my, dt, a);
            }
        }
        c.disableScissor();

        // полоса прокрутки
        if (maxScroll > 0) {
            int trackH = VIS * ROW - 6;
            int thumbH = Math.max(16, Math.round(trackH * (VIS * ROW) / (float) (list.size() * ROW)));
            int thumbY = listTop + Math.round((trackH - thumbH) * (scrollDisp[tab] / maxScroll));
            rr(c, px + W - 12, listTop, px + W - 9, listTop + trackH, 1, col(0xFFFFFF, 0.08f * ease));
            rr(c, px + W - 12, thumbY, px + W - 9, thumbY + thumbH, 1, col(accent, 0.9f * ease));
        }

        String foot = "Right Shift - закрыть";
        c.drawText(textRenderer, foot, px + 12, py + H - 14, col(0x888899, ease), false);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    // ---------- ввод ----------
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (closing) return super.mouseClicked(mx, my, button);

        if (button == 0 && mx >= panelX && mx <= panelX + W && my >= panelY && my <= panelY + 30) {
            draggingHeader = true;
            return true;
        }

        if (button == 0) {
            int tw = (W - 24) / tabNames.length;
            int ty = panelY + 36;
            for (int i = 0; i < tabNames.length; i++) {
                int tx = panelX + 12 + i * tw;
                if (mx >= tx && mx < tx + tw && my >= ty && my < ty + 16) {
                    if (tab != i) {
                        tab = i;
                        pageAnim = 0f;
                        dragging = null;
                    }
                    return true;
                }
            }
        }

        for (Option o : pages.get(tab)) {
            if (o.contains(mx, my)) {
                if (o instanceof Slider && button != 0) return true;
                o.click(mx, button);
                if (o instanceof Slider) dragging = o;
                else Config.save();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingHeader) {
            Config cfg = Config.I;
            float maxX = Math.max(0f, (width - W) / 2f);
            float maxY = Math.max(0f, (height - H) / 2f);
            cfg.guiX = Math.max(-maxX, Math.min(maxX, cfg.guiX + (float) dx));
            cfg.guiY = Math.max(-maxY, Math.min(maxY, cfg.guiY + (float) dy));
            return true;
        }
        if (dragging != null) {
            dragging.drag(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (draggingHeader || dragging != null) {
            draggingHeader = false;
            dragging = null;
            Config.save();
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        List<Option> list = pages.get(tab);
        float maxScroll = Math.max(0, list.size() * ROW - VIS * ROW);
        if (maxScroll > 0) {
            scrollTarget[tab] = Math.max(0, Math.min(maxScroll, scrollTarget[tab] - (float) vertical * ROW * 1.5f));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
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
