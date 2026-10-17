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
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
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

    /** Частица от крита и язычки пламени у надписи комбо. type: 0 звезда, 1 шарик, 2 сердце, 3 пламя. */
    private static class Spark {
        float x, y, z, vx, vy, vz, seed, size;
        int age, life, type;
    }

    private static class Label {
        double x, y, z;
        long t0, life;
        String text;
        int tier;
        float tilt, seed;
    }

    private static final ArrayDeque<Pt> trail = new ArrayDeque<>();
    private static final List<Ring> rings = new ArrayList<>();
    private static final List<Drop> drops = new ArrayList<>();
    private static final List<Spark> sparks = new ArrayList<>();
    private static final List<Label> labels = new ArrayList<>();
    private static final Random RNG = new Random();
    private static boolean wasGround = true;
    private static int airTicks = 0;
    private static int combo = 0;
    private static long lastHit = 0;
    private static int lastHurt = 0;

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

    // ================= удар по цели (вызывается из Hud) =================
    public static void onAttack(Entity e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || !(e instanceof LivingEntity le)) return;
        Config c = Config.I;
        long now = System.currentTimeMillis();

        if (c.comboEnabled && (!c.comboPlayersOnly || e instanceof PlayerEntity)) {
            if (now - lastHit > c.comboReset * 1000) combo = 0;
            combo++;
            lastHit = now;
            spawnLabel(le, now, c);
        }
        if (c.critEnabled) {
            boolean crit = p.getAttackCooldownProgress(0.5f) > 0.9f && p.fallDistance > 0 && !p.isOnGround()
                    && !p.isClimbing() && !p.isTouchingWater() && !p.hasStatusEffect(StatusEffects.BLINDNESS)
                    && !p.hasVehicle() && !p.isSprinting();
            if (c.critMode == 1 || crit) burst(le, c);
        }
    }

    private static void spawnLabel(LivingEntity le, long now, Config c) {
        int n = combo;
        int tier = n < 10 ? 0 : (n < 20 ? 1 : (n < 30 ? 2 : 3));
        String text;
        if (tier == 0) text = "x " + n;
        else if (tier == 1) text = "cool x " + n;
        else if (tier == 2) text = "holy shit x " + n;
        else text = "unstoppable x " + n;
        Label l = new Label();
        l.x = le.getX() + (RNG.nextDouble() - 0.5) * 0.7;
        l.y = le.getY() + le.getHeight() + 0.35 + RNG.nextDouble() * 0.2;
        l.z = le.getZ() + (RNG.nextDouble() - 0.5) * 0.7;
        l.t0 = now;
        l.life = (long) (c.comboLife * 1000);
        l.text = text;
        l.tier = tier;
        l.tilt = (RNG.nextFloat() - 0.5f) * 0.25f;
        l.seed = RNG.nextFloat();
        if (labels.size() < 30) labels.add(l);
    }

    private static void burst(LivingEntity le, Config c) {
        double cx = le.getX(), cy = le.getY() + le.getHeight() * 0.6, cz = le.getZ();
        for (int i = 0; i < c.critCount && sparks.size() < 600; i++) {
            Spark s = new Spark();
            double th = RNG.nextDouble() * Math.PI * 2;
            double ph = Math.acos(2 * RNG.nextDouble() - 1);
            float sp = 0.08f + RNG.nextFloat() * 0.2f;
            s.vx = (float) (Math.sin(ph) * Math.cos(th)) * sp;
            s.vy = (float) Math.cos(ph) * sp + 0.06f;
            s.vz = (float) (Math.sin(ph) * Math.sin(th)) * sp;
            s.x = (float) (cx + (RNG.nextDouble() - 0.5) * 0.4);
            s.y = (float) (cy + (RNG.nextDouble() - 0.5) * 0.4);
            s.z = (float) (cz + (RNG.nextDouble() - 0.5) * 0.4);
            s.seed = RNG.nextFloat();
            s.size = c.critSize * (0.7f + 0.6f * RNG.nextFloat());
            s.life = 18 + RNG.nextInt(15);
            s.type = c.critShape == 3 ? RNG.nextInt(3) : c.critShape;
            sparks.add(s);
        }
    }

    // ================= логика (раз в тик) =================
    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            trail.clear();
            rings.clear();
            drops.clear();
            sparks.clear();
            labels.clear();
            combo = 0;
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

        // комбо: сброс по таймеру и по урону
        if (combo > 0 && now - lastHit > c.comboReset * 1000) combo = 0;
        if (c.comboResetOnHurt && p.hurtTime > 0 && lastHurt == 0) combo = 0;
        lastHurt = p.hurtTime;
        labels.removeIf(l -> now - l.t0 > l.life);
        if (c.comboEnabled && c.comboFire) {
            for (Label l : labels) if (l.tier >= 2) spawnFlames(l, c, now);
        }
        tickSparks();

        // падающие эффекты
        if (c.worldFx != 0 || !drops.isEmpty()) tickDrops(mc, p, c, now);
    }

    private static double sq(double v) {
        return v * v;
    }

    private static void spawnFlames(Label l, Config c, long now) {
        GraffitiText.Tex tx = GraffitiText.get(l.text);
        float hh = 0.55f * c.comboSize;
        float ww = hh * tx.w / (float) tx.h;
        float age = (now - l.t0) / 1000f;
        float cy = (float) l.y + 0.35f * age;
        for (int k = 0; k < 2 && sparks.size() < 600; k++) {
            Spark s = new Spark();
            s.x = (float) l.x + (RNG.nextFloat() - 0.5f) * ww * 0.9f;
            s.y = cy + (RNG.nextFloat() - 0.3f) * hh * 0.5f;
            s.z = (float) l.z + (RNG.nextFloat() - 0.5f) * 0.1f;
            s.vx = (RNG.nextFloat() - 0.5f) * 0.01f;
            s.vy = 0.03f + RNG.nextFloat() * 0.03f;
            s.vz = (RNG.nextFloat() - 0.5f) * 0.01f;
            s.life = 14 + RNG.nextInt(9);
            s.size = (0.1f + RNG.nextFloat() * 0.1f) * c.comboSize;
            s.type = 3;
            s.seed = RNG.nextFloat();
            sparks.add(s);
        }
    }

    private static void tickSparks() {
        Iterator<Spark> it = sparks.iterator();
        while (it.hasNext()) {
            Spark s = it.next();
            s.age++;
            if (s.age >= s.life) {
                it.remove();
                continue;
            }
            s.x += s.vx;
            s.y += s.vy;
            s.z += s.vz;
            s.vx *= 0.96f;
            s.vz *= 0.96f;
            if (s.type == 3) {
                s.vy = s.vy * 0.98f + 0.002f;
            } else {
                s.vy -= 0.018f;
            }
        }
    }

    private static final float[] BASE_SPEED = {0.7f, 0.06f, 0.05f, 0.045f};
    private static final float[] SPLASH_CHANCE = {0.12f, 0.5f, 0.5f, 0.4f};
    private static final float[] SPLASH_SIZE = {0.5f, 1.0f, 1.0f, 0.7f};

    private static void tickDrops(MinecraftClient mc, ClientPlayerEntity p, Config c, long now) {
        Config.Fall[] f = {c.rain, c.stars, c.hearts, c.confetti};
        int[] count = new int[4];
        for (Drop d : drops) count[d.type]++;
        for (int t = 0; t < 4; t++) {
            if (c.worldFx != t + 1) continue;
            for (int i = 0; i < f[t].freq && count[t] < f[t].density; i++) {
                if (spawn(mc.world, p, t)) count[t]++;
            }
        }

        Iterator<Drop> it = drops.iterator();
        while (it.hasNext()) {
            Drop d = it.next();
            d.age++;
            boolean off = c.worldFx != d.type + 1;
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

    private static int mixc(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int mixWhite(int rgb, float t) {
        return mixc(rgb, 0xFFFFFF, t);
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
        boolean anything = c.ribbonEnabled || c.jumpEnabled || c.hatEnabled || c.haloEnabled || c.skyEnabled
                || !drops.isEmpty() || !rings.isEmpty() || !sparks.isEmpty() || !labels.isEmpty();
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

        if (c.skyEnabled) {
            SkyFx.draw(m, cp, mc.gameRenderer.getFarPlaneDistance() * 0.9f, now);
        }
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        if (c.ribbonEnabled) drawRibbon(m, c, px, py, pz, now);
        if (!rings.isEmpty()) drawRings(m, c, now);
        double top = py + p.getHeight();
        if (c.hatEnabled && !first) drawHat(m, c, px, top, pz);
        if (c.haloEnabled && !first) drawHalo(m, c, px, top, pz, now);

        Quaternionf q = ctx.camera().getRotation();
        Vector3f right = new Vector3f(1, 0, 0).rotate(q);
        Vector3f up = new Vector3f(0, 1, 0).rotate(q);
        if (!drops.isEmpty()) {
            Vector3f rh = new Vector3f(right.x, 0, right.z);
            if (rh.lengthSquared() > 1e-6f) rh.normalize();
            else rh.set(1, 0, 0);
            drawDrops(m, c, td, right, up, rh);
        }
        if (!sparks.isEmpty()) drawSparks(m, c, td, right, up);
        if (!labels.isEmpty()) drawLabels(m, c, now, right, up);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        ms.pop();
    }

    // ---------- частицы от крита и пламя ----------
    private static void star(BufferBuilder b, Matrix4f m, float x, float y, float z, Vector3f right, Vector3f up,
                             float s, int rgb, float rot, float a) {
        int white = mixWhite(rgb, 0.6f);
        glow(b, m, x, y, z, right, up, s * 2.2f, rgb, 0.3f * a);
        for (int k = 0; k < 8; k++) {
            float r1 = (k % 2 == 0) ? s : s * 0.38f;
            float r2 = ((k + 1) % 2 == 0) ? s : s * 0.38f;
            float a1 = rot + k * (float) Math.PI / 4f;
            float a2 = rot + (k + 1) * (float) Math.PI / 4f;
            pv(b, m, x, y, z, right, up, 0, 0, argb(white, a));
            pv(b, m, x, y, z, right, up, (float) Math.cos(a1) * r1, (float) Math.sin(a1) * r1, argb(rgb, 0.9f * a));
            pv(b, m, x, y, z, right, up, (float) Math.cos(a2) * r2, (float) Math.sin(a2) * r2, argb(rgb, 0.9f * a));
        }
    }

    private static void disc(BufferBuilder b, Matrix4f m, float x, float y, float z, Vector3f right, Vector3f up,
                             float s, int rgb, float a) {
        int seg = 10;
        int light = mixWhite(rgb, 0.5f);
        glow(b, m, x, y, z, right, up, s * 2f, rgb, 0.25f * a);
        for (int i = 0; i < seg; i++) {
            double a1 = i * Math.PI * 2 / seg, a2 = (i + 1) * Math.PI * 2 / seg;
            pv(b, m, x, y, z, right, up, 0, 0, argb(light, a));
            pv(b, m, x, y, z, right, up, (float) Math.cos(a1) * s, (float) Math.sin(a1) * s, argb(rgb, 0.85f * a));
            pv(b, m, x, y, z, right, up, (float) Math.cos(a2) * s, (float) Math.sin(a2) * s, argb(rgb, 0.85f * a));
        }
    }

    private static void heart(BufferBuilder b, Matrix4f m, float x, float y, float z, Vector3f right, Vector3f up,
                              float s, int rgb, float ang, float a) {
        float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
        int light = mixWhite(rgb, 0.35f);
        glow(b, m, x, y, z, right, up, s * 1.9f, rgb, 0.22f * a);
        for (int i = 0; i < 24; i++) {
            int j = (i + 1) % 24;
            float ux1 = HX[i] * s, vy1 = HY[i] * s, ux2 = HX[j] * s, vy2 = HY[j] * s;
            pv(b, m, x, y, z, right, up, 0, 0, argb(light, 0.95f * a));
            pv(b, m, x, y, z, right, up, ux1 * ca - vy1 * sa, ux1 * sa + vy1 * ca, argb(rgb, 0.9f * a));
            pv(b, m, x, y, z, right, up, ux2 * ca - vy2 * sa, ux2 * sa + vy2 * ca, argb(rgb, 0.9f * a));
        }
    }

    private static void drawSparks(Matrix4f m, Config c, float td, Vector3f right, Vector3f up) {
        BufferBuilder b = begin(VertexFormat.DrawMode.TRIANGLES);
        for (Spark s : sparks) {
            float t = s.age / (float) s.life;
            float a = 1f - t;
            float x = s.x + s.vx * td, y = s.y + s.vy * td, z = s.z + s.vz * td;
            switch (s.type) {
                case 0:
                    star(b, m, x, y, z, right, up, s.size, hsb(c.fxRainbow, c.fxHue, s.seed), (s.age + td) * 0.2f + s.seed * 6f, a);
                    break;
                case 1:
                    disc(b, m, x, y, z, right, up, s.size, hsb(c.fxRainbow, c.fxHue, s.seed), a);
                    break;
                case 2:
                    heart(b, m, x, y, z, right, up, s.size, hsb(c.fxRainbow, c.fxHue, s.seed),
                            (float) Math.sin((s.age + td) * 0.2f + s.seed * 6f) * 0.4f, a);
                    break;
                default: {
                    int rgb = t < 0.4f ? mixc(0xFFE040, 0xFF8C20, t / 0.4f) : mixc(0xFF8C20, 0xE02010, (t - 0.4f) / 0.6f);
                    disc(b, m, x, y, z, right, up, s.size * (1f - 0.5f * t), rgb, a * 0.9f);
                    break;
                }
            }
        }
        end(b);
    }

    // ---------- надписи комбо ----------
    private static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    private static int[] labelCols(Label l, long now, float fade) {
        int[] col = new int[4]; // BL, BR, TR, TL
        if (l.tier == 0) {
            col[0] = argb(0xFFD73C, fade);
            col[1] = argb(0xFF9632, fade);
            col[2] = argb(0xFF46AA, fade);
            col[3] = argb(0xE63CC8, fade);
        } else {
            float speed = l.tier == 1 ? 1800f : (l.tier == 2 ? 1000f : 600f);
            float base = (now % 100000L) / speed;
            for (int i = 0; i < 4; i++) {
                int rgb = Color.HSBtoRGB((base + i * 0.25f + l.seed) % 1f, 0.85f, 1f) & 0xFFFFFF;
                if (l.tier == 3) rgb = mixWhite(rgb, 0.15f + 0.15f * (float) Math.sin(now * 0.02 + i));
                col[i] = argb(rgb, fade);
            }
        }
        return col;
    }

    private static void texQuad(int id, Matrix4f m, float cx, float cy, float cz, Vector3f rr, Vector3f uu,
                                float hw, float hh, int[] col) {
        RenderSystem.setShaderTexture(0, id);
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        float[] ox = {-1, 1, 1, -1}, oy = {-1, -1, 1, 1}, us = {0, 1, 1, 0}, vs = {1, 1, 0, 0};
        for (int i = 0; i < 4; i++) {
            b.vertex(m, cx + rr.x * ox[i] * hw + uu.x * oy[i] * hh,
                    cy + rr.y * ox[i] * hw + uu.y * oy[i] * hh,
                    cz + rr.z * ox[i] * hw + uu.z * oy[i] * hh).texture(us[i], vs[i]).color(col[i]);
        }
        end(b);
    }

    private static void drawLabels(Matrix4f m, Config c, long now, Vector3f right, Vector3f up) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        for (Label l : labels) {
            float age = (now - l.t0) / 1000f;
            float lifeS = l.life / 1000f;
            float t = age / lifeS;
            if (t < 0f || t >= 1f) continue;
            GraffitiText.Tex tx = GraffitiText.get(l.text);
            float pop = age < 0.22f ? easeOutBack(age / 0.22f) : 1f;
            float fade = t > 0.65f ? 1f - (t - 0.65f) / 0.35f : 1f;
            float scale = c.comboSize * pop * (l.tier == 3 ? 1.2f : 1f);
            float hh = 0.55f * scale;
            float hw = hh * tx.w / (float) tx.h;
            float cx = (float) l.x, cy = (float) l.y + 0.35f * age, cz = (float) l.z;
            if (l.tier == 3) {
                cx += (float) Math.sin(now * 0.05) * 0.015f;
                cy += (float) Math.cos(now * 0.07) * 0.015f;
            }
            float ca = (float) Math.cos(l.tilt), sa = (float) Math.sin(l.tilt);
            Vector3f rr = new Vector3f(right).mul(ca).add(new Vector3f(up).mul(sa));
            Vector3f uu = new Vector3f(up).mul(ca).sub(new Vector3f(right).mul(sa));
            int w = argb(0xFFFFFF, fade);
            texQuad(tx.outId, m, cx, cy, cz, rr, uu, hw * 0.5f, hh * 0.5f, new int[]{w, w, w, w});
            texQuad(tx.fillId, m, cx, cy, cz, rr, uu, hw * 0.5f, hh * 0.5f, labelCols(l, now, fade));
        }
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
                    quad(b, m, d.x, y, d.z, rh, s * 0.12f, s * 5f, argb(rgb, 0.55f), argb(rgb, 0f));
                    star(b, m, d.x, y, d.z, right, up, s, rgb, ph * 0.08f + d.seed * 6.28f, 1f);
                    break;
                }
                case 2: {
                    float s = c.hearts.size * (0.7f + 0.6f * d.seed);
                    heart(b, m, d.x, y, d.z, right, up, s, rgb, (float) Math.sin(ph * 0.08f + d.seed * 6f) * 0.35f, 1f);
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

    // ---------- круги ----------
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
        if (!any) {
            vtx(b, m, 0, -1000, 0, 0);
            vtx(b, m, 0, -1000, 0, 0);
            vtx(b, m, 0, -1000, 0, 0);
        }
        end(b);
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
