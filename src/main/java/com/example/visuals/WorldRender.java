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

    private static class Drop {
        float x, y, z, vy, seed, ground;
        boolean star;
        int age;
    }

    private static final ArrayDeque<Pt> trail = new ArrayDeque<>();
    private static final List<Pt> circles = new ArrayList<>();
    private static final List<Drop> drops = new ArrayList<>();
    private static final Random RNG = new Random();
    private static boolean wasGround = true;
    private static final long CIRCLE_MS = 900;

    // ================= логика (раз в тик) =================
    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            trail.clear();
            circles.clear();
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

        // круги при прыжке
        boolean ground = p.isOnGround();
        if (c.jumpEnabled && wasGround && !ground && p.getVelocity().y > 0.1) {
            circles.add(new Pt(p.getX(), p.getY(), p.getZ(), now));
        }
        wasGround = ground;
        circles.removeIf(ci -> now - ci.t > CIRCLE_MS);

        // дождь и звёзды
        if (c.rainEnabled || c.starsEnabled || !drops.isEmpty()) {
            tickDrops(mc, p, c);
        }
    }

    private static double sq(double v) {
        return v * v;
    }

    private static void tickDrops(MinecraftClient mc, ClientPlayerEntity p, Config c) {
        int rain = 0, stars = 0;
        for (Drop d : drops) {
            if (d.star) stars++;
            else rain++;
        }
        if (c.rainEnabled) {
            for (int i = 0; i < c.rainFreq && rain < c.rainDensity; i++) {
                if (spawn(mc.world, p, false)) rain++;
            }
        }
        if (c.starsEnabled) {
            for (int i = 0; i < c.starFreq && stars < c.starDensity; i++) {
                if (spawn(mc.world, p, true)) stars++;
            }
        }

        Iterator<Drop> it = drops.iterator();
        while (it.hasNext()) {
            Drop d = it.next();
            d.age++;
            boolean off = d.star ? !c.starsEnabled : !c.rainEnabled;
            d.vy = -(d.star ? 0.06f * c.starSpeed : 0.7f * c.rainSpeed) * (0.85f + 0.3f * d.seed);
            d.y += d.vy;
            if (d.star) {
                d.x += (float) Math.cos(d.age * 0.07f + d.seed * 6.28f) * 0.012f;
                d.z += (float) Math.sin(d.age * 0.05f + d.seed * 6.28f) * 0.012f;
            }
            float dx = d.x - (float) p.getX(), dz = d.z - (float) p.getZ();
            if (off || d.y <= d.ground || dx * dx + dz * dz > 34 * 34) it.remove();
        }
    }

    private static boolean spawn(ClientWorld w, ClientPlayerEntity p, boolean star) {
        double ang = RNG.nextDouble() * Math.PI * 2;
        double r = Math.sqrt(RNG.nextDouble()) * 18;
        double x = p.getX() + Math.cos(ang) * r;
        double z = p.getZ() + Math.sin(ang) * r;
        double y = p.getY() + 9 + RNG.nextDouble() * 8;
        int ground = w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        if (ground >= y) return false;
        Drop d = new Drop();
        d.x = (float) x;
        d.y = (float) y;
        d.z = (float) z;
        d.seed = RNG.nextFloat();
        d.ground = ground;
        d.star = star;
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

    // ================= отрисовка =================
    public static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        MatrixStack ms = ctx.matrixStack();
        if (ms == null) return;
        Config c = Config.I;
        boolean anything = c.ribbonEnabled || c.jumpEnabled || c.hatEnabled || c.haloEnabled
                || c.rainEnabled || c.starsEnabled || !drops.isEmpty();
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
        if (c.jumpEnabled) drawCircles(m, c, now);
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
            drawRain(m, c, td, rh);
            drawStars(m, c, td, right, up, rh);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        ms.pop();
    }

    // ---------- неоновый дождь ----------
    private static void drawRain(Matrix4f m, Config c, float td, Vector3f rh) {
        BufferBuilder b = null;
        for (Drop d : drops) {
            if (d.star) continue;
            if (b == null) b = begin(VertexFormat.DrawMode.TRIANGLES);
            float y = d.y + d.vy * td;
            int rgb = hsb(c.wxRainbow, c.wxHue, d.seed);
            float len = c.rainLength * (0.8f + 0.4f * d.seed);
            quad(b, m, d.x, y, d.z, rh, 0.012f, len, argb(rgb, 0.95f), argb(rgb, 0f));
            quad(b, m, d.x, y, d.z, rh, 0.05f, len, argb(rgb, 0.16f), argb(rgb, 0f));
        }
        if (b != null) end(b);
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

    // ---------- падающие звёзды ----------
    private static void drawStars(Matrix4f m, Config c, float td, Vector3f right, Vector3f up, Vector3f rh) {
        BufferBuilder b = null;
        for (Drop d : drops) {
            if (!d.star) continue;
            if (b == null) b = begin(VertexFormat.DrawMode.TRIANGLES);
            float cy = d.y + d.vy * td;
            float s = c.starSize * (0.7f + 0.6f * d.seed);
            int rgb = hsb(c.wxRainbow, c.wxHue, d.seed);
            int white = mixWhite(rgb, 0.6f);

            // свечение
            int seg = 12;
            float gr = s * 2.4f;
            for (int i = 0; i < seg; i++) {
                double a1 = i * Math.PI * 2 / seg, a2 = (i + 1) * Math.PI * 2 / seg;
                vtx(b, m, d.x, cy, d.z, argb(rgb, 0.35f));
                vtx(b, m, d.x + (right.x * (float) Math.cos(a1) + up.x * (float) Math.sin(a1)) * gr,
                        cy + (right.y * (float) Math.cos(a1) + up.y * (float) Math.sin(a1)) * gr,
                        d.z + (right.z * (float) Math.cos(a1) + up.z * (float) Math.sin(a1)) * gr, argb(rgb, 0f));
                vtx(b, m, d.x + (right.x * (float) Math.cos(a2) + up.x * (float) Math.sin(a2)) * gr,
                        cy + (right.y * (float) Math.cos(a2) + up.y * (float) Math.sin(a2)) * gr,
                        d.z + (right.z * (float) Math.cos(a2) + up.z * (float) Math.sin(a2)) * gr, argb(rgb, 0f));
            }

            // хвост
            quad(b, m, d.x, cy, d.z, rh, s * 0.12f, s * 5f, argb(rgb, 0.55f), argb(rgb, 0f));

            // сама звезда (4 луча)
            float rot = (d.age + td) * 0.08f + d.seed * 6.28f;
            for (int k = 0; k < 8; k++) {
                float r1 = (k % 2 == 0) ? s : s * 0.38f;
                float r2 = ((k + 1) % 2 == 0) ? s : s * 0.38f;
                float a1 = rot + k * (float) Math.PI / 4f;
                float a2 = rot + (k + 1) * (float) Math.PI / 4f;
                float c1 = (float) Math.cos(a1) * r1, s1 = (float) Math.sin(a1) * r1;
                float c2 = (float) Math.cos(a2) * r2, s2 = (float) Math.sin(a2) * r2;
                vtx(b, m, d.x, cy, d.z, argb(white, 1f));
                vtx(b, m, d.x + right.x * c1 + up.x * s1, cy + right.y * c1 + up.y * s1, d.z + right.z * c1 + up.z * s1, argb(rgb, 0.9f));
                vtx(b, m, d.x + right.x * c2 + up.x * s2, cy + right.y * c2 + up.y * s2, d.z + right.z * c2 + up.z * s2, argb(rgb, 0.9f));
            }
        }
        if (b != null) end(b);
    }

    private static int mixWhite(int rgb, float t) {
        int r = (int) (((rgb >> 16) & 255) * (1 - t) + 255 * t);
        int g = (int) (((rgb >> 8) & 255) * (1 - t) + 255 * t);
        int bl = (int) ((rgb & 255) * (1 - t) + 255 * t);
        return (r << 16) | (g << 8) | bl;
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

    // ---------- круги при прыжке ----------
    private static void drawCircles(Matrix4f m, Config c, long now) {
        int seg = 48;
        for (Pt ci : new ArrayList<>(circles)) {
            float t = (now - ci.t) / (float) CIRCLE_MS;
            if (t < 0f || t > 1f) continue;
            float e = 1f - (1f - t) * (1f - t) * (1f - t);
            float r = 0.3f + (c.jumpSize - 0.3f) * e;
            float a = 1f - t;
            float y = (float) ci.y + 0.03f;
            BufferBuilder b = begin(VertexFormat.DrawMode.TRIANGLE_STRIP);
            for (int i = 0; i <= seg; i++) {
                double ang = i * Math.PI * 2 / seg;
                float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
                int rgb = hsb(c.fxRainbow, c.fxHue, i / (float) seg);
                b.vertex(m, (float) ci.x + cos * r * 0.8f, y, (float) ci.z + sin * r * 0.8f).color(argb(rgb, a * 0.1f));
                b.vertex(m, (float) ci.x + cos * r, y, (float) ci.z + sin * r).color(argb(rgb, a * 0.95f));
            }
            end(b);
        }
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
