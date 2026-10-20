package com.example.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.Random;

/**
 * Небо без единой текстуры: сфера вокруг камеры, раскрашенная цветами вершин.
 * Звёзды и глитч-блоки Breakcore рисуются маленькими цветными четырёхугольниками.
 * Никакого состояния текстур не трогается, поэтому с подбором предметов и опыта конфликтовать нечему.
 */
public final class SkyFx {
    private SkyFx() {
    }

    public static final String[] STYLES = {"Галактика", "Неон", "Изумруд", "Закат", "Rainbowcore", "Breakcore"};

    private static final int LON = 96, LAT = 48;
    //            зенит        горизонт     туманность 1    туманность 2
    private static final int[][] PAL = {
            {0x080A30, 0x4646AA, 0x965AFF, 0x46AAFF},
            {0x140528, 0xC82896, 0xFF50C8, 0x50C8FF},
            {0x04181E, 0x1E9682, 0x50FFB4, 0x3CA0FF},
            {0x190F3C, 0xFF6E3C, 0xFF965A, 0xC850B4}
    };
    private static final int[][] grid = new int[4][];
    private static float[] rbVal = null;
    private static float[][] stars = null;
    private static final int[] BC = {0xFF0000, 0x00FF00, 0x0000FF, 0x00FFFF, 0xFF00FF, 0xFFFF00, 0xFFFFFF, 0x000000, 0xFF2A2A, 0x7A00FF};
    private static final int BREAK_N = 1100;

    public static int horizon(int style) {
        if (style == 4) return 0x7A2CFF;
        if (style == 5) return 0x330044;
        return PAL[Math.max(0, Math.min(3, style))][1];
    }

    private static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float vnoise(float u, float v, int fx, int fy) {
        float x = u * fx, y = v * fy;
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float tx = x - ix, ty = y - iy;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        float a = hash(Math.floorMod(ix, fx), iy), b = hash(Math.floorMod(ix + 1, fx), iy);
        float c = hash(Math.floorMod(ix, fx), iy + 1), d = hash(Math.floorMod(ix + 1, fx), iy + 1);
        return lerp(lerp(a, b, tx), lerp(c, d, tx), ty);
    }

    private static float fbm(float u, float v, int oct) {
        float s = 0, amp = 0.5f;
        int fx = 4, fy = 2;
        for (int o = 0; o < oct; o++) {
            s += amp * vnoise(u, v, fx, fy);
            amp *= 0.5f;
            fx *= 2;
            fy *= 2;
        }
        return s;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    private static int colorAt(int style, float u, float v) {
        int[] p = PAL[style];
        float e = 1f - v * 2f;
        float[] base = new float[3];
        for (int k = 0; k < 3; k++) {
            int sh = 16 - 8 * k;
            float z = (p[0] >> sh) & 255, h = (p[1] >> sh) & 255;
            base[k] = e >= 0 ? lerp(h, z, (float) Math.pow(e, 0.6)) : lerp(h, 0, Math.min(1f, -e * 1.5f));
        }
        float mask = Math.max(0f, Math.min(1f, 1.2f - Math.abs(e - 0.25f) * 1.3f));
        float n1 = smooth(0.42f, 0.8f, fbm(u, v, 3)) * mask;
        float n2 = smooth(0.5f, 0.85f, fbm(u + 0.37f, v + 0.21f, 3)) * mask;
        int out = 0;
        for (int k = 0; k < 3; k++) {
            int sh = 16 - 8 * k;
            float c1 = (p[2] >> sh) & 255, c2 = (p[3] >> sh) & 255;
            int val = Math.min(255, Math.round(base[k] + c1 * n1 * 0.8f + c2 * n2 * 0.5f));
            out |= val << sh;
        }
        return out;
    }

    private static int[] gridFor(int style) {
        if (grid[style] == null) {
            int[] g = new int[(LAT + 1) * (LON + 1)];
            for (int i = 0; i <= LAT; i++) {
                for (int j = 0; j <= LON; j++) g[i * (LON + 1) + j] = colorAt(style, j / (float) LON, i / (float) LAT);
            }
            grid[style] = g;
        }
        return grid[style];
    }

    private static float[] rbValues() {
        if (rbVal == null) {
            float[] r = new float[(LAT + 1) * (LON + 1)];
            for (int i = 0; i <= LAT; i++) {
                for (int j = 0; j <= LON; j++) {
                    float u = j / (float) LON, v = i / (float) LAT;
                    float n = fbm(u, v, 3);
                    float bands = 0.5f + 0.5f * (float) Math.sin(6.2832f * (u * 6f + v * 8f + n * 2f));
                    float rings = 0.5f + 0.5f * (float) Math.sin(6.2832f * (n * 5f + v * 3f));
                    r[i * (LON + 1) + j] = smooth(0.2f, 0.8f, bands * 0.55f + rings * 0.45f);
                }
            }
            rbVal = r;
        }
        return rbVal;
    }

    private static float[][] stars() {
        if (stars == null) {
            Random rnd = new Random(12345);
            stars = new float[700][];
            for (int i = 0; i < stars.length; i++) {
                float sinPhi = Math.max(-0.1f, rnd.nextFloat() * 1.1f - 0.1f);
                stars[i] = new float[]{rnd.nextFloat() * 6.2832f, (float) Math.asin(sinPhi), 0.6f + rnd.nextFloat() * 1.2f, rnd.nextFloat()};
            }
        }
        return stars;
    }

    private static int shade(int rgb, float bright, float alpha) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 255) * bright));
        int g = Math.min(255, Math.round(((rgb >> 8) & 255) * bright));
        int b = Math.min(255, Math.round((rgb & 255) * bright));
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void vt(BufferBuilder b, Matrix4f m, float x, float y, float z, int col) {
        b.vertex(m, x, y, z).color(col);
    }

    public static void draw(Matrix4f m, Vec3d cam, float R, long now) {
        Config c = Config.I;
        int style = Math.max(0, Math.min(5, c.skyStyle));
        float bright = c.skyBright;
        float ts = (now % 1000000L) / 1000f;
        float spin = ts * 0.004f * c.skySpin * 6.2832f;
        float hueBase = (now % 100000L) / 3000f;

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        int[] g = style < 4 ? gridFor(style) : null;
        float[] rv = style == 4 ? rbValues() : null;

        float[] px = new float[LON + 1], py = new float[LON + 1], pz = new float[LON + 1];
        int[] pc = new int[LON + 1];
        for (int i = 0; i <= LAT; i++) {
            double phi = Math.PI / 2 - i * Math.PI / LAT;
            float cph = (float) Math.cos(phi), sph = (float) Math.sin(phi);
            float[] cx = new float[LON + 1], cy = new float[LON + 1], cz = new float[LON + 1];
            int[] cc = new int[LON + 1];
            for (int j = 0; j <= LON; j++) {
                double th = j * Math.PI * 2 / LON + spin;
                cx[j] = (float) (cam.x + R * cph * Math.cos(th));
                cy[j] = (float) (cam.y + R * sph);
                cz[j] = (float) (cam.z + R * cph * Math.sin(th));
                int rgb;
                float u = j / (float) LON, v = i / (float) LAT;
                if (style < 4) {
                    rgb = g[i * (LON + 1) + j];
                } else if (style == 4) {
                    float val = rv[i * (LON + 1) + j];
                    rgb = Color.HSBtoRGB((hueBase + u * 3f + v * 2f) % 1f, 0.85f, 0.25f + 0.75f * val) & 0xFFFFFF;
                } else {
                    rgb = Color.HSBtoRGB((hueBase * 0.3f + v * 0.6f) % 1f, 0.9f, 0.16f) & 0xFFFFFF;
                }
                cc[j] = shade(rgb, bright, 1f);
            }
            if (i > 0) {
                for (int j = 0; j < LON; j++) {
                    vt(b, m, px[j], py[j], pz[j], pc[j]);
                    vt(b, m, px[j + 1], py[j + 1], pz[j + 1], pc[j + 1]);
                    vt(b, m, cx[j + 1], cy[j + 1], cz[j + 1], cc[j + 1]);
                    vt(b, m, px[j], py[j], pz[j], pc[j]);
                    vt(b, m, cx[j + 1], cy[j + 1], cz[j + 1], cc[j + 1]);
                    vt(b, m, cx[j], cy[j], cz[j], cc[j]);
                }
            }
            px = cx;
            py = cy;
            pz = cz;
            pc = cc;
        }

        if (style != 5) {
            // звёзды: маленькие мерцающие четырёхугольники
            for (float[] st : stars()) {
                float th = st[0] + spin, ph = st[1];
                float cph = (float) Math.cos(ph), sph = (float) Math.sin(ph), cth = (float) Math.cos(th), sth = (float) Math.sin(th);
                float rr = R * 0.98f;
                float sx = (float) cam.x + rr * cph * cth, sy = (float) cam.y + rr * sph, sz = (float) cam.z + rr * cph * sth;
                float s = R * 0.0035f * st[2];
                float t1x = -sth, t1z = cth;
                float t2x = -sph * cth, t2y = cph, t2z = -sph * sth;
                float tw = (0.6f + 0.4f * (float) Math.sin(ts * 3f + st[3] * 10f)) * bright;
                int col = shade(0xFFFFFF, 1f, Math.min(1f, tw));
                vt(b, m, sx - t1x * s - t2x * s, sy - t2y * s, sz - t1z * s - t2z * s, col);
                vt(b, m, sx + t1x * s - t2x * s, sy - t2y * s, sz + t1z * s - t2z * s, col);
                vt(b, m, sx + t1x * s + t2x * s, sy + t2y * s, sz + t1z * s + t2z * s, col);
                vt(b, m, sx - t1x * s - t2x * s, sy - t2y * s, sz - t1z * s - t2z * s, col);
                vt(b, m, sx + t1x * s + t2x * s, sy + t2y * s, sz + t1z * s + t2z * s, col);
                vt(b, m, sx - t1x * s + t2x * s, sy + t2y * s, sz - t1z * s + t2z * s, col);
            }
        } else {
            // Breakcore: глитч-блоки и полосы, которые постоянно пересыпаются
            float rr = R * 0.99f;
            for (int i = 0; i < BREAK_N; i++) {
                float period = 0.12f + hash(i, 1) * 0.7f;
                int epoch = (int) Math.floor(ts / period);
                float r1 = hash(i * 7 + epoch * 13, 2), r2 = hash(i * 11 + epoch * 17, 3), r3 = hash(i * 5 + epoch * 19, 4);
                float r4 = hash(i * 3 + epoch * 23, 5), r5 = hash(i * 13 + epoch * 29, 6), r6 = hash(i * 17 + epoch * 31, 7);
                float lon = r1 * 6.2832f + spin * 0.5f;
                float lat = (r2 * 2f - 1f) * 1.3f;
                float hw, hh;
                if (r3 < 0.35f) {
                    hw = (0.15f + r4 * 1.2f) * 0.5f;
                    hh = (0.004f + r5 * 0.02f) * 0.5f;
                } else {
                    hw = (0.03f + r4 * 0.35f) * 0.5f;
                    hh = (0.02f + r5 * 0.25f) * 0.5f;
                }
                int col = shade(BC[(int) (r6 * BC.length) % BC.length], bright, 0.92f);
                float la0 = Math.max(-1.5707f, lat - hh), la1 = Math.min(1.5707f, lat + hh);
                float[][] pts = new float[4][];
                float[] lons = {lon - hw, lon + hw, lon + hw, lon - hw};
                float[] lats = {la0, la0, la1, la1};
                for (int k = 0; k < 4; k++) {
                    float cph = (float) Math.cos(lats[k]);
                    pts[k] = new float[]{(float) cam.x + rr * cph * (float) Math.cos(lons[k]),
                            (float) cam.y + rr * (float) Math.sin(lats[k]),
                            (float) cam.z + rr * cph * (float) Math.sin(lons[k])};
                }
                vt(b, m, pts[0][0], pts[0][1], pts[0][2], col);
                vt(b, m, pts[1][0], pts[1][1], pts[1][2], col);
                vt(b, m, pts[2][0], pts[2][1], pts[2][2], col);
                vt(b, m, pts[0][0], pts[0][1], pts[0][2], col);
                vt(b, m, pts[2][0], pts[2][1], pts[2][2], col);
                vt(b, m, pts[3][0], pts[3][1], pts[3][2], col);
            }
        }

        BuiltBuffer built = b.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
    }
}
