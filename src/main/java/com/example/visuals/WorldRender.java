package com.example.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class WorldRender {
    private static class Pt {
        final double x, y, z;
        final long t;

        Pt(double x, double y, double z, long t) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.t = t;
        }
    }

    private static class Ring {
        final double x, y, z;
        final long t0, dur;
        final float maxR;
        final int rgb; // -1 = градиент цвета эффектов
        final boolean splash;

        Ring(double x, double y, double z, long t0, float maxR, long dur, int rgb, boolean splash) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.t0 = t0;
            this.maxR = maxR;
            this.dur = dur;
            this.rgb = rgb;
            this.splash = splash;
        }
    }

    private static class Drop {
        float x, y, z, vy, seed, ground;
        int type; // 0 дождь, 1 звезда, 2 сердце, 3 конфетти
        int age;
    }

    private static final ArrayDeque<Pt> trail = new ArrayDeque<>();
    private static final List<Ring> rings = new ArrayList<>();
    private static final List<Drop> drops = new ArrayList<>();
    private static final Random RNG = new Random();
    private static boolean wasGround = true;
    private static int airTicks = 0;

    private static final float[] HX = new float[24];
    private static final float[] HY = new float[24];

    static {
        for (int i = 0; i < 24; i++) {
            double t = i * Math.PI * 2 / 24;
            double x = 16 * Math.pow(Math.sin(t), 3);
            double y = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
            HX[i] = (float) (x / 17.0);
            HY[i] = (float) (y / 17.0);
        }
    }

    // ================= логика (раз в тик) =================
    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            trail.clear();
            rings.clear();
            drops.clear();
            return;
        }
        Config c = Config.I;
        long now = System.currentTimeMillis();

        // лента
        if (c.ribbonEnabled) {
            Pt last = trail.peekLast();
            if (last != null) {
                double d2 = sq(p.getX() - last.x) + sq(p.getY() - last.y) + sq(p.getZ() - last.z);
                if (d2 > 64) trail.clear();
                else if (d2 > 0.01) trail.addLast(new Pt(p.getX(), p.getY(), p.getZ(), now));
            } else {
                trail.addLast(new Pt(p.getX(), p.getY(), p.getZ(), now));
            }
        } else {
            trail.clear();
        }
        long lifeMs = (long) (c.ribbonLife * 1000);
        while (!trail.isEmpty() && now - trail.peekFirst().t > lifeMs) trail.pollFirst();

        // круги: прыжок / приземление
        boolean ground = p.isOnGround();
        if (!ground) airTicks++;
        if (c.jumpEnabled) {
            boolean takeoff = wasGround && !ground && p.getVelocity().y > 0.1;
            boolean landing = !wasGround && ground && airTicks >= 4;
            if ((takeoff && (c.jumpMode == 0 || c.jumpMode == 2)) || (landing && (c.jumpMode == 1 || c.jumpMode == 2))) {
                rings.add(new Ring(p.getX(), p.getY() + 0.03, p.getZ(), now, c.jumpSize, 900, -1, false));
            }
        }
        if (ground) airTicks = 0;
        wasGround = ground;
        rings.removeIf(r -> now - r.t0 > r.dur);

        // падающие эффекты
        boolean anyFall = c.rain.enabled || c.stars.enabled || c.hearts.enabled || c.confetti.enabled;
        if (anyFall || !drops.isEmpty()) tickDrops(mc, p, c, now);
    }

    private static double sq(double v) {
        return v * v;
    }

    private static final float[] BASE_SPEED = {0.7f, 0.06f, 0.05f, 0.045f};
    private static final float[] SPLASH_CHANCE = {0.12f, 0.5f, 0.5f, 0.4f};
    private static final float[] SPLASH_SIZE = {0.5f, 1.0f, 1.0f, 0.7f};

    private static void tickDrops(MinecraftClient mc, ClientPlayerEntity p, Config c, long now) {
        Config.Fall[] f = {c.rain, c.stars, c.hearts, c.confetti};
        int[] count = new int[4];
        for (Drop d : drops) count[d.type]++;
        for (int t = 0; t < 4; t++) {
            if (!f[t].enabled) continue;
            for (int i = 0; i < f[t].freq && count[t] < f[t].density; i++) {
                if (spawn(mc.world, p, t)) count[t]++;
            }
        }

        Iterator<Drop> it = drops.iterator();
        while (it.hasNext()) {
            Drop d = it.next();
            d.age++;
            boolean off = !f[d.type].enabled;
            d.vy = -BASE_SPEED[d.type] * f[d.type].speed * (0.85f + 0.3f * d.seed);
            d.y += d.vy;
            if (d.type != 0) {
                float sway = d.type == 3 ? 0.02f : 0.012f;
                d.x += (float) Math.cos(d.age * 0.07f + d.seed * 6.28f) * sway;
                d.z += (float) Math.sin(d.age * 0.05f + d.seed * 6.28f) * sway;
            }
            float dx = d.x - (float) p.getX(), dz = d.z - (float) p.getZ();
            if (off || dx * dx + dz * dz > 36 * 36) {
                it.remove();
                continue;
            }
            if (d.y <= d.ground) {
                if (c.splashRings && rings.size() < 300 && RNG.nextFloat() < SPLASH_CHANCE[d.type]) {
                    int rgb = hsb(c.wxRainbow, c.wxHue, d.seed);
                    rings.add(new Ring(d.x, d.ground + 0.03, d.z, now, c.splashSize * SPLASH_SIZE[d.type], 650, rgb, true));
                }
                it.remove();
            }
        }
    }

    private static boolean spawn(ClientWorld w, ClientPlayerEntity p, int type) {
        double ang = RNG.nextDouble() * Math.PI * 2;
        double r = Math.sqrt(RNG.nextDouble()) * 26;
        Vec3d v = p.getVelocity();
        double x = p.getX() + Math.cos(ang) * r + v.x * RNG.nextDouble() * 25;
        double z = p.getZ() + Math.sin(ang) * r + v.z * RNG.nextDouble() * 25;
        int ground = w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        double top = p.getY() + 16;
        double lo = Math.max(ground, p.getY() - 8);
        if (lo >= top) return false;
        Drop d = new Drop();
        d.x = (float) x;
        d.y = (float) (lo + RNG.nextDouble() * (top - lo));
        d.z = (float) z;
        d.seed = RNG.nextFloat();
        d.ground = ground;
        d.type = type;
        drops.add(d);
        return true;
    }

    // ================= цвет и буфер =================
    private static int hsb(boolean rainbow, float hue, float off) {
        float h = rainbow
                ? ((System.currentTimeMillis() % 4000) / 4000f + off) % 1f
                : (((hue + off * 0.1f) % 1f) + 1f) % 1f;
        return Color.HSBtoRGB(h, 0.8f, 1f) & 0xFFFFFF;
    }

    private static int argb(int rgb, float a) {
        int al = (int) (Math.max(0f, Math.min(1f, a)) * 255);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    private static int mixWhite(int rgb, float t) {
        int r = (int) (((rgb >> 16) & 255) * (1 - t) + 255 * t);
        int g = (int) (((rgb >> 8) & 255) * (1 - t) + 255 * t);
        int bl = (int) ((rgb & 255) * (1 - t) + 255 * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static BufferBuilder begin(VertexFormat.DrawMode mode) {
        return Tessellator.getInstance().begin(mode, VertexFormats.POSITION_COLOR);
    }

    private static void end(BufferBuilder b) {
        BuiltBuffer built = b.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
    }

    private static void vtx(BufferBuilder b, Matrix4f m, float x, float y, float z, int color) {
        b.vertex(m, x, y, z).color(color);
    }

    /** Вершина в плоскости камеры: центр + right*u + up*v. */
    private static void pv(BufferBuilder b, Matrix4f m, float cx, float cy, float cz,
                           Vector3f right, Vector3f up, float u, float v, int color) {
        vtx(b, m, cx + right.x * u + up.x * v, cy + right.y * u + up.y * v, cz + right.z * u + up.z * v, color);
    }

    // ================= отрисовка =================
    public static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        MatrixStack ms = ctx.matrixStack();
        if (ms == null) return;
        Config c = Config.I;
        boolean anything = c.ribbonEnabled || c.jumpEnabled || c.hatEnabled || c.haloEnabled
                || !drops.isEmpty() || !rings.isEmpty();
        if (!anything) return;

        Vec3d cp = ctx.camera().getPos();
        float td = mc.getRenderTickCounter().getTickDelta(true);
        double px = MathHelper.lerp(td, p.prevX, p.getX());
        double py = MathHelper.lerp(td, p.prevY, p.getY());
        double pz = MathHelper.lerp(td, p.prevZ, p.getZ());
        boolean first = mc.options.getPerspective().isFirstPerson();
        long now = System.currentTimeMillis();

        ms.push();
        ms.translate(-cp.x, -cp.y, -cp.z);
        Matrix4f m = ms.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        if (c.ribbonEnabled) drawRibbon(m, c, px, py, pz, now);
        if (!rings.isEmpty()) drawRings(m, c, now);
        double top = py + p.getHeight();
        if (c.hatEnabled && !first) drawHat(m, c, px, top, pz);
        if (c.haloEnabled && !first) drawHalo(m, c, px, top, pz, now);

        if (!drops.isEmpty()) {
            Quaternionf q = ctx.camera().getRotation();
            Vector3f right = new Vector3f(1, 0, 0).rotate(q);
            Vector3f up = new Vector3f(0, 1, 0).rotate(q);
            Vector3f rh = new Vector3f(right.x, 0, right.z);
            if (rh.lengthSquared() > 1e-6f) rh.normalize();
            else rh.set(1, 0, 0);
            drawDrops(m, c, td, right, up, rh);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        ms.pop();
    }

    // ---------- все падающие штуки одним буфером ----------
    private static void drawDrops(Matrix4f m, Config c, float td, Vector3f right, Vector3f up, Vector3f rh) {
        BufferBuilder b = begin(VertexFormat.DrawMode.TRIANGLES);
        for (Drop d : drops) {
            float y = d.y + d.vy * td;
            int rgb = hsb(c.wxRainbow, c.wxHue, d.seed);
            float ph = d.age + td;
            switch (d.type) {
                case 0: {
                    float len = c.rainLength * (0.8f + 0.4f * d.seed);
                    quad(b, m, d.x, y, d.z, rh, 0.012f, len, argb(rgb, 0.95f), argb(rgb, 0f));
                    quad(b, m, d.x, y, d.z, rh, 0.05f, len, argb(rgb, 0.16f), argb(rgb, 0f));
                    break;
                }
                case 1: {
                    float s = c.stars.size * (0.7f + 0.6f * d.seed);
                    int white = mixWhite(rgb, 0.6f);
                    glow(b, m, d.x, y, d.z, right, up, s * 2.4f, rgb, 0.35f);
                    quad(b, m, d.x, y, d.z, rh, s * 0.12f, s * 5f, argb(rgb, 0.55f), argb(rgb, 0f));
                    float rot = ph * 0.08f + d.seed * 6.28f;
                    for (int k = 0; k < 8; k++) {
                        float r1 = (k % 2 == 0) ? s : s * 0.38f;
                        float r2 = ((k + 1) % 2 == 0) ? s : s * 0.38f;
                        float a1 = rot + k * (float) Math.PI / 4f;
                        float a2 = rot + (k + 1) * (float) Math.PI / 4f;
                        pv(b, m, d.x, y, d.z, right, up, 0, 0, argb(white, 1f));
                        pv(b, m, d.x, y, d.z, right, up, (float) Math.cos(a1) * r1, (float) Math.sin(a1) * r1, argb(rgb, 0.9f));
                        pv(b, m, d.x, y, d.z, right, up, (float) Math.cos(a2) * r2, (float) Math.sin(a2) * r2, argb(rgb, 0.9f));
                    }
                    break;
                }
                case 2: {
                    float s = c.hearts.size * (0.7f + 0.6f * d.seed);
                    float ang = (float) Math.sin(ph * 0.08f + d.seed * 6f) * 0.35f;
                    float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
                    int light = mixWhite(rgb, 0.35f);
                    glow(b, m, d.x, y, d.z, right, up, s * 1.9f, rgb, 0.22f);
                    for (int i = 0; i < 24; i++) {
                        int j = (i + 1) % 24;
                        float ux1 = HX[i] * s, vy1 = HY[i] * s, ux2 = HX[j] * s, vy2 = HY[j] * s;
                        pv(b, m, d.x, y, d.z, right, up, 0, 0, argb(light, 0.95f));
                        pv(b, m, d.x, y, d.z, right, up, ux1 * ca - vy1 * sa, ux1 * sa + vy1 * ca, argb(rgb, 0.9f));
                        pv(b, m, d.x, y, d.z, right, up, ux2 * ca - vy2 * sa, ux2 * sa + vy2 * ca, argb(rgb, 0.9f));
                    }
                    break;
                }
                default: {
                    float s = c.confetti.size * (0.8f + 0.5f * d.seed);
                    float rot = ph * 0.12f + d.seed * 6.28f;
                    float wob = Math.abs((float) Math.cos(ph * 0.15f + d.seed * 7f));
                    float hw = Math.max(0.08f, wob) * s * 0.7f, hh = s * 1.1f;
                    float cr = (float) Math.cos(rot), sr = (float) Math.sin(rot);
                    float[] cx = {-hw, hw, hw, -hw};
                    float[] cy = {-hh, -hh, hh, hh};
                    int col = argb(rgb, 0.95f);
                    int[] order = {0, 1, 2, 0, 2, 3};
                    for (int idx : order) {
                        pv(b, m, d.x, y, d.z, right, up, cx[idx] * cr - cy[idx] * sr, cx[idx] * sr + cy[idx] * cr, col);
                    }
                    break;
                }
            }
        }
        end(b);
    }

    private static void glow(BufferBuilder b, Matrix4f m, float x, float y, float z, Vector3f right, Vector3f up,
                             float radius, int rgb, float alpha) {
        int seg = 10;
        for (int i = 0; i < seg; i++) {
            double a1 = i * Math.PI * 2 / seg, a2 = (i + 1) * Math.PI * 2 / seg;
            pv(b, m, x, y, z, right, up, 0, 0, argb(rgb, alpha));
            pv(b, m, x, y, z, right, up, (float) Math.cos(a1) * radius, (float) Math.sin(a1) * radius, argb(rgb, 0f));
            pv(b, m, x, y, z, right, up, (float) Math.cos(a2) * radius, (float) Math.sin(a2) * radius, argb(rgb, 0f));
        }
    }

    private static void quad(BufferBuilder b, Matrix4f m, float x, float y, float z, Vector3f rh,
                             float w, float len, int bottom, int topc) {
        float ax = x - rh.x * w, az = z - rh.z * w;
        float bx = x + rh.x * w, bz = z + rh.z * w;
        vtx(b, m, ax, y, az, bottom);
        vtx(b, m, bx, y, bz, bottom);
        vtx(b, m, bx, y + len, bz, topc);
        vtx(b, m, ax, y, az, bottom);
        vtx(b, m, bx, y + len, bz, topc);
        vtx(b, m, ax, y + len, az, topc);
    }

    // ---------- круги (прыжок, приземление, падения) ----------
    private static void drawRings(Matrix4f m, Config c, long now) {
        BufferBuilder b = begin(VertexFormat.DrawMode.TRIANGLES);
        boolean any = false;
        for (Ring r : rings) {
            float t = (now - r.t0) / (float) r.dur;
            if (t < 0f || t > 1f) continue;
            any = true;
            float e = 1f - (1f - t) * (1f - t) * (1f - t);
            float rad = 0.2f + (r.maxR - 0.2f) * e;
            float a = 1f - t;
            float inner = rad * 0.8f;
            float y = (float) r.y;
            int seg = r.splash ? 20 : 48;
            for (int i = 0; i < seg; i++) {
                double a1 = i * Math.PI * 2 / seg, a2 = (i + 1) * Math.PI * 2 / seg;
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
                int rgb1 = r.rgb >= 0 ? r.rgb : hsb(c.fxRainbow, c.fxHue, i / (float) seg);
                int rgb2 = r.rgb >= 0 ? r.rgb : hsb(c.fxRainbow, c.fxHue, (i + 1) / (float) seg);
                int in1 = argb(rgb1, a * 0.1f), out1 = argb(rgb1, a * 0.95f);
                int in2 = argb(rgb2, a * 0.1f), out2 = argb(rgb2, a * 0.95f);
                float x = (float) r.x, z = (float) r.z;
                vtx(b, m, x + c1 * inner, y, z + s1 * inner, in1);
                vtx(b, m, x + c1 * rad, y, z + s1 * rad, out1);
                vtx(b, m, x + c2 * rad, y, z + s2 * rad, out2);
                vtx(b, m, x + c1 * inner, y, z + s1 * inner, in1);
                vtx(b, m, x + c2 * rad, y, z + s2 * rad, out2);
                vtx(b, m, x + c2 * inner, y, z + s2 * inner, in2);
            }
        }
        if (any) {
            end(b);
        } else {
            // пустой буфер всё равно нужно закрыть
            vtx(b, m, 0, -1000, 0, 0);
            vtx(b, m, 0, -1000, 0, 0);
            vtx(b, m, 0, -1000, 0, 0);
            end(b);
        }
    }

    // ---------- лента ----------
    private static void drawRibbon(Matrix4f m, Config c, double px, double py, double pz, long now) {
        List<Pt> pts = new ArrayList<>(trail);
        if (pts.isEmpty()) return;
        float life = c.ribbonLife * 1000f;
        float h = c.ribbonHeight;
        BufferBuilder b = begin(VertexFormat.DrawMode.TRIANGLE_STRIP);
        int n = pts.size() + 1;
        for (int i = 0; i < n; i++) {
            double x, y, z;
            float a;
            if (i < pts.size()) {
                Pt q = pts.get(i);
                x = q.x;
                y = q.y;
                z = q.z;
                a = 1f - (now - q.t) / life;
            } else {
                x = px;
                y = py;
                z = pz;
                a = 1f;
            }
            a = Math.max(0f, Math.min(1f, a));
            int rgb = hsb(c.trailRainbow, c.trailHue, i * 0.03f);
            b.vertex(m, (float) x, (float) y + 0.05f, (float) z).color(argb(rgb, a * 0.9f));
            b.vertex(m, (float) x, (float) (y + h), (float) z).color(argb(rgb, a * 0.1f));
        }
        end(b);
    }

    // ---------- китайская шляпа ----------
    private static void drawHat(Matrix4f m, Config c, double px, double top, double pz) {
        int seg = 40;
        float rad = 0.55f * c.hatSize;
        float apexY = (float) top + 0.38f * c.hatSize;
        float baseY = (float) top + 0.02f;

        BufferBuilder b = begin(VertexFormat.DrawMode.TRIANGLE_FAN);
        b.vertex(m, (float) px, apexY, (float) pz).color(argb(hsb(c.fxRainbow, c.fxHue, 0f), 0.85f));
        for (int i = 0; i <= seg; i++) {
            double ang = i * Math.PI * 2 / seg;
            int rgb = hsb(c.fxRainbow, c.fxHue, i / (float) seg);
            b.vertex(m, (float) px + (float) Math.cos(ang) * rad, baseY, (float) pz + (float) Math.sin(ang) * rad)
                    .color(argb(rgb, 0.4f));
        }
        end(b);

        BufferBuilder o = begin(VertexFormat.DrawMode.TRIANGLE_STRIP);
        for (int i = 0; i <= seg; i++) {
            double ang = i * Math.PI * 2 / seg;
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            int rgb = hsb(c.fxRainbow, c.fxHue, i / (float) seg);
            o.vertex(m, (float) px + cos * (rad - 0.025f), baseY, (float) pz + sin * (rad - 0.025f)).color(argb(rgb, 0.95f));
            o.vertex(m, (float) px + cos * (rad + 0.025f), baseY, (float) pz + sin * (rad + 0.025f)).color(argb(rgb, 0.95f));
        }
        end(o);
    }

    // ---------- нимб ----------
    private static void drawHalo(Matrix4f m, Config c, double px, double top, double pz, long now) {
        int seg = 40;
        float y = (float) top + 0.25f + (float) Math.sin(now / 500.0) * 0.03f;
        float ri = 0.26f, ro = 0.34f;
        BufferBuilder b = begin(VertexFormat.DrawMode.TRIANGLE_STRIP);
        for (int i = 0; i <= seg; i++) {
            double ang = i * Math.PI * 2 / seg;
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            int rgb = hsb(c.fxRainbow, c.fxHue, i / (float) seg);
            b.vertex(m, (float) px + cos * ri, y, (float) pz + sin * ri).color(argb(rgb, 0.95f));
            b.vertex(m, (float) px + cos * ro, y, (float) pz + sin * ro).color(argb(rgb, 0.95f));
        }
        end(b);
    }
}
