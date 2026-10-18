package com.example.visuals;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;

import java.awt.Color;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class Widgets {
    private Widgets() {
    }

    public abstract static class Option {
        public static int clipTop = 0, clipBottom = 100000;
        public final String label;
        public int x, y, w;
        public float hover = 0f;

        protected Option(String label) {
            this.label = label;
        }

        public int h() {
            return 16;
        }

        public boolean clickable() {
            return true;
        }

        public boolean isSlider() {
            return false;
        }

        public boolean contains(double mx, double my) {
            return clickable() && mx >= x && mx <= x + w && my >= y && my < y + h()
                    && my >= clipTop && my <= clipBottom;
        }

        public void click(double mx, double my, int button) {
        }

        public void drag(double mx) {
        }

        public abstract void render(DrawContext c, TextRenderer tr, int mx, int my, float dt, float a);

        protected void updateHover(int mx, int my, float dt) {
            hover = Ui.approach(hover, contains(mx, my) ? 1f : 0f, dt * 14f);
        }

        protected void bg(DrawContext c, int hh, float a) {
            Ui.rr(c, x, y, x + w, y + hh, 4, Ui.col(0xFFFFFF, (0.05f + 0.05f * hover) * a));
        }
    }

    public static class Header extends Option {
        private final Supplier<String> text;

        public Header(String label) {
            super(label);
            this.text = null;
        }

        public Header(Supplier<String> text) {
            super("");
            this.text = text;
        }

        @Override
        public int h() {
            return 14;
        }

        @Override
        public boolean clickable() {
            return false;
        }

        @Override
        public void render(DrawContext c, TextRenderer tr, int mx, int my, float dt, float a) {
            String s = text != null ? text.get() : label;
            s = tr.trimToWidth(s, w - 6);
            c.drawText(tr, s, x + 3, y + 3, Ui.col(Ui.accent(), 0.95f * a), false);
        }
    }

    public static class Toggle extends Option {
        private final Supplier<Boolean> get;
        private final Consumer<Boolean> set;
        private float t = -1;

        public Toggle(String label, Supplier<Boolean> get, Consumer<Boolean> set) {
            super(label);
            this.get = get;
            this.set = set;
        }

        @Override
        public void click(double mx, double my, int button) {
            set.accept(!get.get());
        }

        @Override
        public void render(DrawContext c, TextRenderer tr, int mx, int my, float dt, float a) {
            updateHover(mx, my, dt);
            if (t < 0) t = get.get() ? 1f : 0f;
            t = Ui.approach(t, get.get() ? 1f : 0f, dt * 12f);
            bg(c, 16, a);
            c.drawText(tr, tr.trimToWidth(label, w - 36), x + 5, y + 4, Ui.col(0xFFFFFF, (0.8f + 0.2f * hover) * a), false);
            int sw = 22, sh = 10;
            int sx = x + w - sw - 5, sy = y + 3;
            Ui.rr(c, sx, sy, sx + sw, sy + sh, 5, Ui.col(Ui.mix(0x3A3A48, Ui.accent(), t), a));
            int kx = sx + 1 + Math.round(11 * t);
            Ui.rr(c, kx, sy + 1, kx + 8, sy + sh - 1, 4, Ui.col(0xFFFFFF, a));
        }
    }

    public static class Slider extends Option {
        private final float min, max;
        private final int decimals;
        private final Supplier<Float> get;
        private final Consumer<Float> set;
        private float disp = -1;

        public Slider(String label, float min, float max, int decimals, Supplier<Float> get, Consumer<Float> set) {
            super(label);
            this.min = min;
            this.max = max;
            this.decimals = decimals;
            this.get = get;
            this.set = set;
        }

        @Override
        public int h() {
            return 24;
        }

        @Override
        public boolean isSlider() {
            return true;
        }

        private float norm() {
            return (get.get() - min) / (max - min);
        }

        private void setFromMouse(double mx) {
            float t = (float) Math.max(0, Math.min(1, (mx - (x + 5)) / (double) (w - 10)));
            set.accept(min + t * (max - min));
        }

        @Override
        public void click(double mx, double my, int button) {
            setFromMouse(mx);
        }

        @Override
        public void drag(double mx) {
            setFromMouse(mx);
        }

        protected void drawTrack(DrawContext c, int tx, int ty, int tw, float a) {
            Ui.rr(c, tx, ty, tx + tw, ty + 4, 2, Ui.col(0x3A3A48, a));
            Ui.rr(c, tx, ty, tx + Math.max(4, Math.round(tw * disp)), ty + 4, 2, Ui.col(Ui.accent(), a));
        }

        @Override
        public void render(DrawContext c, TextRenderer tr, int mx, int my, float dt, float a) {
            updateHover(mx, my, dt);
            if (disp < 0) disp = norm();
            disp = Ui.approach(disp, norm(), dt * 18f);
            bg(c, 24, a);
            String v = String.format(Locale.ROOT, "%." + decimals + "f", get.get());
            int vw = tr.getWidth(v);
            c.drawText(tr, tr.trimToWidth(label, w - vw - 14), x + 5, y + 3, Ui.col(0xFFFFFF, (0.8f + 0.2f * hover) * a), false);
            c.drawText(tr, v, x + w - 5 - vw, y + 3, Ui.col(Ui.mix(Ui.accent(), 0xFFFFFF, 0.4f), a), false);
            int tx = x + 5, tw = w - 10, ty = y + 15;
            drawTrack(c, tx, ty, tw, a);
            int kx = tx + Math.round(tw * disp);
            Ui.rr(c, kx - 2, ty - 2, kx + 2, ty + 6, 2, Ui.col(0xFFFFFF, a));
        }
    }

    public static class HueSlider extends Slider {
        public HueSlider(String label, Supplier<Float> get, Consumer<Float> set) {
            super(label, 0f, 1f, 2, get, set);
        }

        @Override
        protected void drawTrack(DrawContext c, int tx, int ty, int tw, float a) {
            for (int i = 0; i < tw; i += 2) {
                int rgb = Color.HSBtoRGB(i / (float) tw, 0.85f, 1f) & 0xFFFFFF;
                c.fill(tx + i, ty, tx + Math.min(tw, i + 2), ty + 4, Ui.col(rgb, a));
            }
        }
    }

    /** Кнопка с выпадающей сеткой вариантов (открывается вниз). */
    public static class Choice extends Option {
        private final String[] names;
        private final Supplier<Integer> get;
        private final Consumer<Integer> set;
        private boolean open = false;
        private float openAnim = 0f;
        private float flash = 0f;

        public Choice(String label, String[] names, Supplier<Integer> get, Consumer<Integer> set) {
            super(label);
            this.names = names;
            this.get = get;
            this.set = set;
        }

        private int cols() {
            return Math.max(1, Math.min(3, w / 58));
        }

        private int rows() {
            int cols = cols();
            return (names.length + cols - 1) / cols;
        }

        private int gridH() {
            return rows() * 16 + 4;
        }

        @Override
        public int h() {
            return 16 + Math.round(gridH() * openAnim);
        }

        @Override
        public void click(double mx, double my, int button) {
            if (my < y + 16) {
                open = !open;
                return;
            }
            if (!open) return;
            int cols = cols();
            int cw = (w - 4 - (cols - 1) * 2) / cols;
            int cx = (int) ((mx - (x + 2)) / (cw + 2));
            int cy = (int) ((my - (y + 18)) / 16);
            if (cx < 0 || cx >= cols || cy < 0) return;
            int idx = cy * cols + cx;
            if (idx >= 0 && idx < names.length) {
                set.accept(idx);
                flash = 1f;
                open = false;
            }
        }

        @Override
        public void render(DrawContext c, TextRenderer tr, int mx, int my, float dt, float a) {
            updateHover(mx, my, dt);
            openAnim = Ui.approach(openAnim, open ? 1f : 0f, dt * 12f);
            flash = Ui.approach(flash, 0f, dt * 6f);
            int cur = Math.max(0, Math.min(names.length - 1, get.get()));

            bg(c, h(), a);
            String arrow = open ? "v" : ">";
            String v = tr.trimToWidth(names[cur], w / 2 - 6);
            int color = Ui.mix(Ui.accent(), 0xFFFFFF, 0.25f + 0.6f * flash);
            c.drawText(tr, arrow, x + w - 5 - tr.getWidth(arrow), y + 4, Ui.col(0xFFFFFF, 0.6f * a), false);
            c.drawText(tr, v, x + w - 14 - tr.getWidth(v), y + 4, Ui.col(color, a), false);
            c.drawText(tr, tr.trimToWidth(label, w - tr.getWidth(v) - 28), x + 5, y + 4,
                    Ui.col(0xFFFFFF, (0.8f + 0.2f * hover) * a), false);

            if (openAnim > 0.05f) {
                int cols = cols();
                int cw = (w - 4 - (cols - 1) * 2) / cols;
                float ga = a * openAnim;
                for (int i = 0; i < names.length; i++) {
                    int cx = x + 2 + (i % cols) * (cw + 2);
                    int cy = y + 18 + (i / cols) * 16;
                    boolean sel = i == cur;
                    boolean hov = mx >= cx && mx < cx + cw && my >= cy && my < cy + 14 && my >= clipTop && my <= clipBottom;
                    int bgc = sel ? Ui.accent() : 0xFFFFFF;
                    float ba = sel ? 0.45f : (hov ? 0.16f : 0.06f);
                    Ui.rr(c, cx, cy, cx + cw, cy + 14, 3, Ui.col(bgc, ba * ga));
                    String s = tr.trimToWidth(names[i], cw - 4);
                    c.drawText(tr, s, cx + (cw - tr.getWidth(s)) / 2, cy + 3, Ui.col(0xFFFFFF, (sel ? 1f : 0.8f) * ga), false);
                }
            }
        }
    }

    public static class Action extends Option {
        private final Runnable run;

        public Action(String label, Runnable run) {
            super(label);
            this.run = run;
        }

        @Override
        public void click(double mx, double my, int button) {
            run.run();
        }

        @Override
        public void render(DrawContext c, TextRenderer tr, int mx, int my, float dt, float a) {
            updateHover(mx, my, dt);
            Ui.rr(c, x, y, x + w, y + 16, 4, Ui.col(Ui.accent(), (0.18f + 0.25f * hover) * a));
            String s = tr.trimToWidth(label, w - 6);
            c.drawText(tr, s, x + (w - tr.getWidth(s)) / 2, y + 4, Ui.col(0xFFFFFF, a), false);
        }
    }

    /** Кнопка назначения клавиши: клик, затем нажми клавишу (Esc отмена, боковые кнопки мыши тоже подходят). */
    public static class Bind extends Option {
        public static Bind listening = null;
        private final Supplier<Integer> get;
        private final Consumer<Integer> set;

        public Bind(String label, Supplier<Integer> get, Consumer<Integer> set) {
            super(label);
            this.get = get;
            this.set = set;
        }

        public void accept(int code) {
            set.accept(code);
            listening = null;
        }

        public static String keyName(int code) {
            if (code < 0) return "Mouse " + (-code);
            try {
                return InputUtil.fromKeyCode(code, 0).getLocalizedText().getString();
            } catch (Exception e) {
                return "Key " + code;
            }
        }

        @Override
        public void click(double mx, double my, int button) {
            listening = (listening == this) ? null : this;
        }

        @Override
        public void render(DrawContext c, TextRenderer tr, int mx, int my, float dt, float a) {
            updateHover(mx, my, dt);
            bg(c, 16, a);
            boolean on = listening == this;
            String v = on ? "нажми клавишу..." : keyName(get.get());
            int color = on ? 0xFFFFFF : Ui.mix(Ui.accent(), 0xFFFFFF, 0.3f);
            c.drawText(tr, tr.trimToWidth(v, w / 2), x + w - 5 - Math.min(tr.getWidth(v), w / 2), y + 4, Ui.col(color, a), false);
            c.drawText(tr, tr.trimToWidth(label, w / 2 - 6), x + 5, y + 4, Ui.col(0xFFFFFF, (0.8f + 0.2f * hover) * a), false);
        }
    }
}
