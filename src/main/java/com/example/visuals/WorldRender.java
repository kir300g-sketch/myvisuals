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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

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

    private static final ArrayDeque<Pt> trail = new ArrayDeque<>();
    private static final List<Pt> circles = new ArrayList<>();
    private static boolean wasGround = true;
    private static final long CIRCLE_MS = 900;

    // ---------- логика (раз в тик) ----------
    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            trail.clear();
            circles.clear();
            return;
        }
        Config c = Config.I;
        long now = System.currentTimeMillis();

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

        boolean ground = p.isOnGround();
        if (c.jumpEnabled && wasGround && !ground && p.getVelocity().y > 0.1) {
            circles.add(new Pt(p.getX(), p.getY(), p.getZ(), now));
        }
        wasGround = ground;
        circles.removeIf(ci -> now - ci.t > CIRCLE_MS);
    }

    private static double sq(double v) {
        return v * v;
    }

    // ---------- цвет ----------
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

    // ---------- отрисовка ----------
    public static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        MatrixStack ms = ctx.matrixStack();
        if (ms == null) return;
        Config c = Config.I;
        if (!c.ribbonEnabled && !c.jumpEnabled && !c.hatEnabled && !c.haloEnabled) return;

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

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        ms.pop();
    }

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
