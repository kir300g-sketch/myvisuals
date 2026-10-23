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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Небо без единой текстуры: плотная сфера вокруг камеры, раскрашенная цветами вершин.
 * Слои: градиент, туманности, Млечный путь с пылевыми полосами, звёзды разных цветов и размеров,
 * яркие звёзды с лучами и падающие звёзды. Breakcore рисует глитч-блоки.
 */
public final class SkyFx {
    private SkyFx() {
    }

    public static final String[] STYLES = {"Галактика", "Неон", "Изумруд", "Закат", "Rainbowcore", "Breakcore"};

    private static final int LON = 160, LAT = 80;
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
    private static final int[] TINT = {0xFFFFFF, 0xBFD4FF, 0xFFE2B8};
    private static final int BREAK_N = 1100;

    private static final float[] cLon = new float[LON + 1], sLon = new float[LON + 1];
    private static final float[] cLat = new float[LAT + 1], sLat = new float[LAT + 1];
    private static final float[][] rx = new float[2][LON + 1], ry = new float[2][LON + 1], rz = new float[2][LON + 1];
    private static final int[][] rc = new int[2][LON + 1];

    static {
        for (int j = 0; j <= LON; j++) {
            double a = j * Math.PI * 2 / LON;
            cLon[j] = (float) Math.cos(a);
            sLon[j] = (float) Math.sin(a);
        }
        for (int i = 0; i <= LAT; i++) {
            double phi = Math.PI / 2 - i * Math.PI / LAT;
            cLat[i] = (float) Math.cos(phi);
            sLat[i] = (float) Math.sin(phi);
        }
    }

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
        float n1 = smooth(0.40f, 0.78f, fbm(u, v, 5)) * mask;
        float n2 = smooth(0.48f, 0.85f, fbm(u + 0.37f, v + 0.21f, 5)) * mask;
        float n3 = smooth(0.55f, 0.9f, fbm(u + 0.61f, v + 0.77f, 4)) * mask;

        // Млечный путь: яркая полоса вдоль наклонного большого круга с тёмными пылевыми прожилками
        float th = u * 6.2832f, phi = (float) Math.PI / 2 - v * (float) Math.PI;
        float dx = (float) (Math.cos(phi) * Math.cos(th)), dy = (float) Math.sin(phi), dz = (float) (Math.cos(phi) * Math.sin(th));
        float dist = dx * 0.35f + dy * 0.80f + dz * 0.48f;
        float band = (float) Math.exp(-dist * dist / 0.035f);
        float dust = smooth(0.5f, 0.8f, fbm(u + 0.7f, v + 0.3f, 4));
        band *= 1f - 0.65f * dust * band;
        float fine = vnoise(u, v, 32, 16);
        float grain = 0.75f + 0.25f * fine;

        int out = 0;
        for (int k = 0; k < 3; k++) {
            int sh = 16 - 8 * k;
            float c1 = (p[2] >> sh) & 255, c2 = (p[3] >> sh) & 255;
            float glowBand = (c2 * 0.6f + 255f * 0.4f) * band * (0.5f + 0.5f * fine);
            float val = base[k] + c1 * n1 * 0.8f * grain + c2 * n2 * 0.5f + c1 * n3 * 0.35f + glowBand * 0.55f;
            out |= Math.min(255, Math.round(val)) << sh;
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
                    float n = fbm(u, v, 4);
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
            List<float[]> list = new ArrayList<>();
            int tries = 0;
            while (list.size() < 1500 && tries++ < 20000) {
                float sinPhi = Math.max(-0.1f, rnd.nextFloat() * 1.1f - 0.1f);
                float phi = (float) Math.asin(sinPhi), th = rnd.nextFloat() * 6.2832f;
                float cph = (float) Math.cos(phi);
                float dx = cph * (float) Math.cos(th), dz = cph * (float) Math.sin(th);
                float dist = dx * 0.35f + sinPhi * 0.8f + dz * 0.48f;
                float accept = 0.2f + 0.8f * (float) Math.exp(-dist * dist / 0.05f);
                if (rnd.nextFloat() > accept) continue;
                boolean hero = rnd.nextFloat() < 0.05f;
                list.add(new float[]{th, phi, hero ? 2.2f + rnd.nextFloat() : 0.5f + rnd.nextFloat() * 1.1f,
                        rnd.nextFloat(), hero ? 1f : 0f, rnd.nextInt(3)});
            }
            stars = list.toArray(new float[0][]);
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

    private static void sq(BufferBuilder b, Matrix4f m, float[] p0, float[] p1, float[] p2, float[] p3, int c0, int c1) {
        vt(b, m, p0[0], p0[1], p0[2], c0);
        vt(b, m, p1[0], p1[1], p1[2], c0);
        vt(b, m, p2[0], p2[1], p2[2], c1);
        vt(b, m, p0[0], p0[1], p0[2], c0);
        vt(b, m, p2[0], p2[1], p2[2], c1);
        vt(b, m, p3[0], p3[1], p3[2], c1);
    }

    public static void draw(Matrix4f m, Vec3d cam, float R, long now) {
        Config c = Config.I;
        int style = Math.max(0, Math.min(5, c.skyStyle));
        float bright = c.skyBright;
        float ts = (now % 1000000L) / 1000f;
        float spin = ts * 0.004f * c.skySpin * 6.2832f;
        float hueBase = (now % 100000L) / 3000f;
        float cs = (float) Math.cos(spin), ss = (float) Math.sin(spin);

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        int[] g = style < 4 ? gridFor(style) : null;
        float[] rv = style == 4 ? rbValues() : null;

        for (int i = 0; i <= LAT; i++) {
            int bi = i & 1, pv = bi ^ 1;
            for (int j = 0; j <= LON; j++) {
                float cth = cLon[j] * cs - sLon[j] * ss, sth = sLon[j] * cs + cLon[j] * ss;
                rx[bi][j] = (float) (cam.x + R * cLat[i] * cth);
                ry[bi][j] = (float) (cam.y + R * sLat[i]);
                rz[bi][j] = (float) (cam.z + R * cLat[i] * sth);
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
                rc[bi][j] = shade(rgb, bright, 1f);
            }
            if (i > 0) {
                for (int j = 0; j < LON; j++) {
                    vt(b, m, rx[pv][j], ry[pv][j], rz[pv][j], rc[pv][j]);
                    vt(b, m, rx[pv][j + 1], ry[pv][j + 1], rz[pv][j + 1], rc[pv][j + 1]);
                    vt(b, m, rx[bi][j + 1], ry[bi][j + 1], rz[bi][j + 1], rc[bi][j + 1]);
                    vt(b, m, rx[pv][j], ry[pv][j], rz[pv][j], rc[pv][j]);
                    vt(b, m, rx[bi][j + 1], ry[bi][j + 1], rz[bi][j + 1], rc[bi][j + 1]);
                    vt(b, m, rx[bi][j], ry[bi][j], rz[bi][j], rc[bi][j]);
                }
            }
        }

        if (style != 5) {
            // звёзды: разные цвета, размеры, мерцание; яркие со световыми лучами
            for (float[] st : stars()) {
                float th = st[0] + spin, ph = st[1];
                float cph = (float) Math.cos(ph), sph = (float) Math.sin(ph), cth = (float) Math.cos(th), sth = (float) Math.sin(th);
                float rr = R * 0.98f;
                float sx = (float) cam.x + rr * cph * cth, sy = (float) cam.y + rr * sph, sz = (float) cam.z + rr * cph * sth;
                float s = R * 0.0035f * st[2];
                float t1x = -sth, t1z = cth;
                float t2x = -sph * cth, t2y = cph, t2z = -sph * sth;
                float tw = (0.6f + 0.4f * (float) Math.sin(ts * 3f + st[3] * 10f)) * bright;
                int col = shade(TINT[(int) st[5]], 1f, Math.min(1f, tw));
                float[] a0 = {sx - t1x * s - t2x * s, sy - t2y * s, sz - t1z * s - t2z * s};
                float[] a1 = {sx + t1x * s - t2x * s, sy - t2y * s, sz + t1z * s - t2z * s};
                float[] a2 = {sx + t1x * s + t2x * s, sy + t2y * s, sz + t1z * s + t2z * s};
                float[] a3 = {sx - t1x * s + t2x * s, sy + t2y * s, sz - t1z * s + t2z * s};
                sq(b, m, a0, a1, a2, a3, col, col);
                if (st[4] > 0.5f) {
                    float L = s * 5f, W = s * 0.18f;
                    int sc0 = shade(TINT[(int) st[5]], 1f, Math.min(1f, tw) * 0.6f), sc1 = shade(TINT[(int) st[5]], 1f, 0f);
                    float[] h0 = {sx - t1x * L, sy, sz - t1z * L}, h1 = {sx + t1x * L, sy, sz + t1z * L};
                    float[] v0 = {sx - t2x * L, sy - t2y * L, sz - t2z * L}, v1 = {sx + t2x * L, sy + t2y * L, sz + t2z * L};
                    float[] cc = {sx, sy, sz};
                    sq(b, m, new float[]{sx - t2x * W, sy - t2y * W, sz - t2z * W}, new float[]{sx + t2x * W, sy + t2y * W, sz + t2z * W}, h1, h1, sc0, sc1);
                    sq(b, m, new float[]{sx - t2x * W, sy - t2y * W, sz - t2z * W}, new float[]{sx + t2x * W, sy + t2y * W, sz + t2z * W}, h0, h0, sc0, sc1);
                    sq(b, m, new float[]{sx - t1x * W, sy, sz - t1z * W}, new float[]{sx + t1x * W, sy, sz + t1z * W}, v1, v1, sc0, sc1);
                    sq(b, m, new float[]{sx - t1x * W, sy, sz - t1z * W}, new float[]{sx + t1x * W, sy, sz + t1z * W}, v0, v0, sc0, sc1);
                }
            }

            // падающая звезда раз в несколько секунд
            int epoch = (int) Math.floor(ts / 7f);
            float local = ts - epoch * 7f;
            if (local < 0.8f) {
                float th0 = hash(epoch, 11) * 6.2832f, ph0 = 0.45f + hash(epoch, 12) * 0.7f;
                float dth = (hash(epoch, 13) - 0.5f) * 1.2f, dph = -0.25f - hash(epoch, 14) * 0.25f;
                float f = local / 0.8f;
                float[] head = skyPt(cam, R * 0.97f, th0 + dth * f + spin, ph0 + dph * f);
                float[] tail = skyPt(cam, R * 0.97f, th0 + dth * Math.max(0f, f - 0.18f) + spin, ph0 + dph * Math.max(0f, f - 0.18f));
                float dx = head[0] - tail[0], dy = head[1] - tail[1], dz = head[2] - tail[2];
                float rxv = head[0] - (float) cam.x, ryv = head[1] - (float) cam.y, rzv = head[2] - (float) cam.z;
                float sx = dy * rzv - dz * ryv, sy = dz * rxv - dx * rzv, sz = dx * ryv - dy * rxv;
                float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (sl > 1e-6f) {
                    float w = R * 0.0035f / sl;
                    sx *= w;
                    sy *= w;
                    sz *= w;
                    float fa = (float) Math.sin(f * Math.PI) * bright;
                    int hc = shade(0xFFFFFF, 1f, fa), tc = shade(0x9FB8FF, 1f, 0f);
                    sq(b, m, new float[]{tail[0] - sx, tail[1] - sy, tail[2] - sz}, new float[]{tail[0] + sx, tail[1] + sy, tail[2] + sz},
                            new float[]{head[0] + sx, head[1] + sy, head[2] + sz}, new float[]{head[0] - sx, head[1] - sy, head[2] - sz}, tc, hc);
                }
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
                sq(b, m, pts[0], pts[1], pts[2], pts[3], col, col);
            }
        }

        BuiltBuffer built = b.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
    }

    private static float[] skyPt(Vec3d cam, float r, float th, float ph) {
        float cph = (float) Math.cos(ph);
        return new float[]{(float) cam.x + r * cph * (float) Math.cos(th), (float) cam.y + r * (float) Math.sin(ph),
                (float) cam.z + r * cph * (float) Math.sin(th)};
    }
}
