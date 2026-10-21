package com.example.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
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
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.TridentItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
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
        final float maxR, yaw;
        final int rgb, shape; // rgb -1 = градиент цвета кругов; shape: 0 круг, 1 сердце, 2 звезда, 3 пентаграмма
        final boolean splash;

        Ring(double x, double y, double z, long t0, float maxR, long dur, int rgb, boolean splash) {
            this(x, y, z, t0, maxR, dur, rgb, splash, 0, 0f);
        }

        Ring(double x, double y, double z, long t0, float maxR, long dur, int rgb, boolean splash, int shape, float yaw) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.t0 = t0;
            this.maxR = maxR;
            this.dur = dur;
            this.rgb = rgb;
            this.splash = splash;
            this.shape = shape;
            this.yaw = yaw;
        }
    }

    private static class Drop {
        float x, y, z, vy, seed, ground;
        int type; // 0 дождь, 1 звезда, 2 сердце, 3 конфетти
        int age;
    }

    /** type: 0 звезда, 1 шарик, 2 сердце, 3 пламя, 4 капля крови, 5 искра, 6 кольцо-волна. */
    private static class Spark {
        float x, y, z, vx, vy, vz, seed, size;
        int age, life, type, rgb;
    }

    private static class Label {
        double x, y, z;
        float vx, vz, tilt, seed;
        long t0, life;
        String text;
        int tier, kind; // kind 0 комбо, 1 счётчик убийств
    }

    private static class Decal {
        float x, y, z, seed, size;
        long t0, dur;
        int rgb;
    }

    private static class Scythe {
        double x, y, z, hy;
        float rx = 1f, rz = 0f;
        long t0;
        boolean impacted;
    }

    private static class Strike {
        double x, y, z;
        long t0;
        boolean impacted;
    }

    private static class KillFx {
        int type; // 3 молот, 4 пентаграмма, 5 ядерный взрыв
        double x, y, z;
        float rx = 1f, rz = 0f;
        long t0;
        boolean impacted;
    }

    private static class Watch {
        LivingEntity e;
        long t;
        double x, y, z;
        float h = 1.8f;
        boolean done;
    }

    private static final ArrayDeque<Pt> trail = new ArrayDeque<>();
    private static final List<Ring> rings = new ArrayList<>();
    private static final List<Drop> drops = new ArrayList<>();
    private static final List<Spark> sparks = new ArrayList<>();
    private static final List<Label> labels = new ArrayList<>();
    private static final List<Decal> decals = new ArrayList<>();
    private static final List<Scythe> scythes = new ArrayList<>();
    private static final List<Strike> strikes = new ArrayList<>();
    private static final List<KillFx> kfx = new ArrayList<>();
    private static final List<Watch> watches = new ArrayList<>();
    private static final Random RNG = new Random();
    private static boolean wasGround = true;
    private static int airTicks = 0;
    private static int combo = 0;
    private static long lastHit = 0;
    private static int lastHurt = 0;
    private static int labelSide = 1;

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

        boolean crit = p.getAttackCooldownProgress(0.5f) > 0.9f && p.fallDistance > 0 && !p.isOnGround()
                && !p.isClimbing() && !p.isTouchingWater() && !p.hasStatusEffect(StatusEffects.BLINDNESS)
                && !p.hasVehicle() && !p.isSprinting();

        if (e instanceof TestPlayer.Dummy bot) TestPlayer.onHit(bot, crit);
        if ((c.killEnabled || c.streakEnabled) && validKillTarget(le, c)) addWatch(le, now);

        if (c.comboEnabled && (!c.comboPlayersOnly || e instanceof PlayerEntity) && (crit || !c.comboCritOnly)) {
            if (now - lastHit > c.comboReset * 1000) combo = 0;
            combo++;
            lastHit = now;
            spawnLabel(le, p, now, c);
        }
        if (c.critEnabled && (c.critMode == 1 || crit)) critFx(le, p, c);
    }

    /** Эффект убийства и счётчик работают на игроков и мобов, но не на животных. */
    private static boolean validKillTarget(LivingEntity le, Config c) {
        if (le instanceof PlayerEntity) return true;
        if (c.killTargets == 0) return false;
        if (le instanceof Monster) return true;
        if (c.killTargets == 1) return false;
        return !(le instanceof PassiveEntity) && !(le instanceof FishEntity) && !(le instanceof ArmorStandEntity);
    }

    private static void addWatch(LivingEntity le, long now) {
        for (Watch w : watches) {
            if (w.e == le) {
                w.t = now;
                return;
            }
        }
        Watch w = new Watch();
        w.e = le;
        w.t = now;
        w.x = le.getX();
        w.y = le.getY();
        w.z = le.getZ();
        w.h = le.getHeight();
        watches.add(w);
    }

    private static void spawnLabel(LivingEntity le, ClientPlayerEntity p, long now, Config c) {
        int n = combo;
        int tier = n < 10 ? 0 : (n < 20 ? 1 : (n < 30 ? 2 : 3));
        String text;
        if (tier == 0) text = "x " + n;
        else if (tier == 1) text = "cool x " + n;
        else if (tier == 2) text = "holy shit x " + n;
        else text = "unstoppable x " + n;

        // сбоку от лица цели, иногда меняя сторону
        double yaw = Math.toRadians(p.getYaw());
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        if (RNG.nextFloat() < 0.6f) labelSide = -labelSide;
        double off = 0.85 + RNG.nextDouble() * 0.25;

        Label l = new Label();
        l.x = le.getX() + rx * off * labelSide + (RNG.nextDouble() - 0.5) * 0.15;
        l.z = le.getZ() + rz * off * labelSide + (RNG.nextDouble() - 0.5) * 0.15;
        l.y = le.getEyeY() - 0.1 + RNG.nextDouble() * 0.2;
        l.vx = (float) (rx * labelSide * 0.12);
        l.vz = (float) (rz * labelSide * 0.12);
        l.t0 = now;
        l.life = (long) (c.comboLife * 1000);
        l.text = text;
        l.tier = tier;
        l.kind = 0;
        l.tilt = (RNG.nextFloat() - 0.5f) * 0.25f * labelSide;
        l.seed = RNG.nextFloat();
        if (labels.size() < 30) labels.add(l);
    }

    private static void spawnKillLabel(double x, double y, double z, long now) {
        Label l = new Label();
        l.x = x;
        l.y = y;
        l.z = z;
        l.t0 = now;
        l.life = 2600;
        l.text = String.valueOf(Config.I.killCount);
        l.tier = 0;
        l.kind = 1;
        l.tilt = (RNG.nextFloat() - 0.5f) * 0.2f;
        l.seed = RNG.nextFloat();
        if (labels.size() < 30) labels.add(l);
    }

    // ---------- эффекты крита ----------
    private static int bloodRgb(Config c, float seed) {
        float h = c.bloodRainbow ? (System.currentTimeMillis() % 4000) / 4000f + seed * 0.2f : c.bloodHue;
        return Color.HSBtoRGB(h % 1f, 0.92f, 0.5f + 0.4f * seed) & 0xFFFFFF;
    }

    private static void critFx(LivingEntity le, ClientPlayerEntity p, Config c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d hit = null;
        HitResult hr = mc.crosshairTarget;
        if (hr instanceof EntityHitResult ehr && ehr.getEntity() == le) hit = ehr.getPos();
        if (hit == null) hit = new Vec3d(le.getX(), le.getY() + le.getHeight() * 0.6, le.getZ());

        switch (c.critFx) {
            case 1:
                blood(hit, p, c);
                break;
            case 2:
                embers(hit, p, c);
                break;
            case 3:
                shock(hit, c);
                break;
            default:
                burst(le, c);
                break;
        }
    }

    private static void burst(LivingEntity le, Config c) {
        double cx = le.getX(), cy = le.getY() + le.getHeight() * 0.6, cz = le.getZ();
        for (int i = 0; i < c.critCount && sparks.size() < 700; i++) {
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

    private static void blood(Vec3d hit, ClientPlayerEntity p, Config c) {
        Vec3d dir = hit.subtract(p.getEyePos()).normalize();
        int n = Math.max(8, c.critCount);
        for (int i = 0; i < n && sparks.size() < 700; i++) {
            Spark s = new Spark();
            float sp = 0.12f + RNG.nextFloat() * 0.28f;
            s.vx = (float) dir.x * sp + (RNG.nextFloat() - 0.5f) * 0.14f;
            s.vy = (float) dir.y * sp * 0.5f + 0.04f + RNG.nextFloat() * 0.12f;
            s.vz = (float) dir.z * sp + (RNG.nextFloat() - 0.5f) * 0.14f;
            s.x = (float) hit.x + (RNG.nextFloat() - 0.5f) * 0.1f;
            s.y = (float) hit.y + (RNG.nextFloat() - 0.5f) * 0.1f;
            s.z = (float) hit.z + (RNG.nextFloat() - 0.5f) * 0.1f;
            s.seed = RNG.nextFloat();
            s.size = 0.014f + RNG.nextFloat() * 0.012f;
            s.life = 26 + RNG.nextInt(14);
            s.type = 4;
            s.rgb = bloodRgb(c, s.seed);
            sparks.add(s);
        }
    }

    private static void embers(Vec3d hit, ClientPlayerEntity p, Config c) {
        int n = Math.max(10, c.critCount);
        for (int i = 0; i < n && sparks.size() < 700; i++) {
            Spark s = new Spark();
            double th = RNG.nextDouble() * Math.PI * 2;
            double ph = Math.acos(2 * RNG.nextDouble() - 1);
            float sp = 0.2f + RNG.nextFloat() * 0.3f;
            s.vx = (float) (Math.sin(ph) * Math.cos(th)) * sp;
            s.vy = (float) Math.cos(ph) * sp;
            s.vz = (float) (Math.sin(ph) * Math.sin(th)) * sp;
            s.x = (float) hit.x;
            s.y = (float) hit.y;
            s.z = (float) hit.z;
            s.seed = RNG.nextFloat();
            s.size = 0.016f + RNG.nextFloat() * 0.01f;
            s.life = 8 + RNG.nextInt(7);
            s.type = 5;
            s.rgb = hsb(c.critRainbow, c.critHue, s.seed);
            sparks.add(s);
        }
    }

    private static void shock(Vec3d hit, Config c) {
        Spark s = new Spark();
        s.x = (float) hit.x;
        s.y = (float) hit.y;
        s.z = (float) hit.z;
        s.size = 0.9f + c.critSize * 3f;
        s.life = 14;
        s.type = 6;
        s.rgb = hsb(c.critRainbow, c.critHue, 0f);
        sparks.add(s);
        for (int i = 0; i < 10 && sparks.size() < 700; i++) {
            Spark t = new Spark();
            double th = RNG.nextDouble() * Math.PI * 2;
            float sp = 0.12f + RNG.nextFloat() * 0.15f;
            t.vx = (float) Math.cos(th) * sp;
            t.vy = (RNG.nextFloat() - 0.3f) * sp;
            t.vz = (float) Math.sin(th) * sp;
            t.x = (float) hit.x;
            t.y = (float) hit.y;
            t.z = (float) hit.z;
            t.seed = RNG.nextFloat();
            t.size = c.critSize * 0.8f;
            t.life = 16 + RNG.nextInt(8);
            t.type = 0;
            sparks.add(t);
        }
    }

    private static void killBurst(double x, double y, double z, Config c) {
        for (int i = 0; i < 24 && sparks.size() < 700; i++) {
            Spark s = new Spark();
            double th = RNG.nextDouble() * Math.PI * 2;
            float sp = 0.05f + RNG.nextFloat() * 0.18f;
            s.vx = (float) Math.cos(th) * sp;
            s.vy = 0.05f + RNG.nextFloat() * 0.18f;
            s.vz = (float) Math.sin(th) * sp;
            s.x = (float) x;
            s.y = (float) y;
            s.z = (float) z;
            s.seed = RNG.nextFloat();
            s.size = 0.1f * c.killSize;
            s.life = 18 + RNG.nextInt(14);
            s.type = RNG.nextBoolean() ? 0 : 3;
            s.rgb = killRgb(c, s.seed);
            sparks.add(s);
        }
    }

    private static int killRgb(Config c, float off) {
        return hsb(c.killRainbow, c.killHue, off);
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
            decals.clear();
            scythes.clear();
            strikes.clear();
            kfx.clear();
            watches.clear();
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
                rings.add(new Ring(p.getX(), p.getY() + 0.03, p.getZ(), now, c.jumpSize, 900, -1, false, c.jumpShape, (float) Math.toRadians(p.getYaw())));
            }
        }
        if (ground) airTicks = 0;
        wasGround = ground;
        rings.removeIf(r -> now - r.t0 > r.dur);
        if (c.timeEnabled) mc.world.setTimeOfDay((long) c.timeOfDay);

        // комбо: сброс по таймеру и по урону
        if (combo > 0 && now - lastHit > c.comboReset * 1000) combo = 0;
        if (c.comboResetOnHurt && p.hurtTime > 0 && lastHurt == 0) combo = 0;
        lastHurt = p.hurtTime;
        labels.removeIf(l -> now - l.t0 > l.life);
        if (c.comboEnabled && c.comboFire) {
            for (Label l : labels) if (l.kind == 0 && l.tier >= 2) spawnFlames(l, c, now);
        }

        tickWatches(c, now);
        tickScythes(c, now);
        tickStrikes(c, now);
        tickKillFx(c, now);
        decals.removeIf(d -> now - d.t0 > d.dur);
        tickSparks(mc.world);

        if (c.worldFx != 0 || !drops.isEmpty()) tickDrops(mc, p, c, now);
    }

    private static double sq(double v) {
        return v * v;
    }

    private static void tickWatches(Config c, long now) {
        Iterator<Watch> it = watches.iterator();
        while (it.hasNext()) {
            Watch w = it.next();
            if (w.done || now - w.t > 5000) {
                it.remove();
                continue;
            }
            LivingEntity e = w.e;
            boolean dead = e.getHealth() <= 0 || e.isDead();
            if (!dead && !e.isRemoved()) {
                w.x = e.getX();
                w.y = e.getY();
                w.z = e.getZ();
                w.h = e.getHeight();
            } else if (dead) {
                if (!e.isRemoved()) {
                    w.x = e.getX();
                    w.y = e.getY();
                    w.z = e.getZ();
                }
                w.done = true;
                onKill(w, c, now);
            } else {
                it.remove();
            }
        }
    }

    private static void onKill(Watch w, Config c, long now) {
        if (c.killEnabled) onKillEffects(w, c, now);
        if (c.streakEnabled) Streak.onKill();
    }

    private static void onKillEffects(Watch w, Config c, long now) {
        if (!(w.e instanceof TestPlayer.Dummy)) {
            c.killCount++;
            Config.save();
        }
        if (c.killFx == 1) {
            Scythe s = new Scythe();
            s.x = w.x;
            s.y = w.y;
            s.z = w.z;
            s.hy = w.y + w.h * 0.8;
            s.rx = curRhx;
            s.rz = curRhz;
            s.t0 = now;
            scythes.add(s);
        } else if (c.killFx >= 3 && c.killFx <= 5) {
            KillFx k = new KillFx();
            k.type = c.killFx;
            k.x = w.x;
            k.y = w.y;
            k.z = w.z;
            k.rx = curRhx;
            k.rz = curRhz;
            k.t0 = now;
            kfx.add(k);
        } else if (c.killFx == 2) {
            Strike st = new Strike();
            st.x = w.x;
            st.y = w.y;
            st.z = w.z;
            st.t0 = now;
            strikes.add(st);
        } else if (c.killCounter) {
            spawnKillLabel(w.x, w.y + w.h * 0.6, w.z, now);
        }
    }

    private static void tickScythes(Config c, long now) {
        Iterator<Scythe> it = scythes.iterator();
        while (it.hasNext()) {
            Scythe s = it.next();
            long age = now - s.t0;
            float t = age / 1000f;
            if (t < SWING_END) spawnWisps(s, c, t);
            if (!s.impacted && age >= 1000) {
                s.impacted = true;
                killBurst(s.x, s.hy, s.z, c);
                rings.add(new Ring(s.x, s.y + 0.03, s.z, now, 2.2f * c.killSize, 850, killRgb(c, 0f), true));
                if (c.killCounter) spawnKillLabel(s.x, s.hy + 0.25, s.z, now);
            }
            if (age > 2700) it.remove();
        }
    }

    private static void bloodSplash(double x, double y, double z, Config c, int n, float spread, long now) {
        for (int i = 0; i < n && sparks.size() < 700; i++) {
            Spark s = new Spark();
            double th = RNG.nextDouble() * Math.PI * 2;
            float sp = 0.12f + RNG.nextFloat() * 0.3f * spread;
            s.vx = (float) Math.cos(th) * sp;
            s.vz = (float) Math.sin(th) * sp;
            s.vy = 0.03f + RNG.nextFloat() * 0.2f;
            s.x = (float) x;
            s.y = (float) y + 0.15f;
            s.z = (float) z;
            s.seed = RNG.nextFloat();
            s.size = 0.014f + RNG.nextFloat() * 0.012f;
            s.life = 26 + RNG.nextInt(14);
            s.type = 4;
            s.rgb = bloodRgb(c, s.seed);
            sparks.add(s);
        }
        for (int i = 0; i < 24 && decals.size() < 200; i++) {
            Decal d = new Decal();
            double th = RNG.nextDouble() * Math.PI * 2, r = Math.sqrt(RNG.nextDouble()) * 1.9;
            d.x = (float) (x + Math.cos(th) * r);
            d.z = (float) (z + Math.sin(th) * r);
            d.y = (float) y + 0.02f;
            d.seed = RNG.nextFloat();
            d.size = 0.15f + RNG.nextFloat() * 0.45f;
            d.t0 = now;
            d.dur = 4500;
            d.rgb = bloodRgb(c, d.seed);
            decals.add(d);
        }
    }

    private static void flameBurst(double x, double y, double z, double radius, int n, float up, int rgbA, int rgbB) {
        for (int i = 0; i < n && sparks.size() < 700; i++) {
            Spark sp = new Spark();
            double th = RNG.nextDouble() * Math.PI * 2, r = Math.sqrt(RNG.nextDouble()) * radius;
            sp.x = (float) (x + Math.cos(th) * r);
            sp.y = (float) y + 0.1f;
            sp.z = (float) (z + Math.sin(th) * r);
            sp.vx = (RNG.nextFloat() - 0.5f) * 0.06f;
            sp.vz = (RNG.nextFloat() - 0.5f) * 0.06f;
            sp.vy = up * (0.4f + RNG.nextFloat() * 0.8f);
            sp.life = 16 + RNG.nextInt(14);
            sp.size = 0.12f + RNG.nextFloat() * 0.16f;
            sp.type = 3;
            sp.seed = RNG.nextFloat();
            sp.rgb = RNG.nextBoolean() ? rgbA : rgbB;
            sparks.add(sp);
        }
    }

    private static void tickKillFx(Config c, long now) {
        Iterator<KillFx> it = kfx.iterator();
        while (it.hasNext()) {
            KillFx k = it.next();
            long age = now - k.t0;
            float t = age / 1000f;
            if (k.type == 3) {
                if (!k.impacted && age >= 900) {
                    k.impacted = true;
                    bloodSplash(k.x, k.y, k.z, c, 70, 1.4f, now);
                    rings.add(new Ring(k.x, k.y + 0.03, k.z, now, 3.2f * c.killSize, 700, 0xB0A090, true));
                    if (c.killCounter) spawnKillLabel(k.x, k.y + 1.6, k.z, now);
                }
                if (age > 2200) it.remove();
            } else if (k.type == 4) {
                if (!k.impacted && age >= 1350) {
                    k.impacted = true;
                    flameBurst(k.x, k.y, k.z, 1.5 * c.killSize, 70, 0.45f, 0xFFB020, 0xFF4018);
                    rings.add(new Ring(k.x, k.y + 0.03, k.z, now, 3.4f * c.killSize, 800, 0xFF4018, true));
                    rings.add(new Ring(k.x, k.y + 0.03, k.z, now, 2.0f * c.killSize, 600, 0xFFE070, true));
                    if (c.killCounter) spawnKillLabel(k.x, k.y + 2.2, k.z, now);
                }
                if (t >= 1.35f && t < 2.4f) flameBurst(k.x, k.y, k.z, 1.4 * c.killSize, 6, 0.3f, 0xFFB020, 0xFF4018);
                if (age > 3000) it.remove();
            } else {
                if (!k.impacted) {
                    k.impacted = true;
                    flameBurst(k.x, k.y, k.z, 0.6, 60, 0.5f, 0xFFA020, 0xFFE070);
                    rings.add(new Ring(k.x, k.y + 0.03, k.z, now, 6.0f * c.killSize, 1000, 0xFFF0C0, true));
                    rings.add(new Ring(k.x, k.y + 0.03, k.z, now, 3.5f * c.killSize, 800, 0xFF7A18, true));
                    if (c.killCounter) spawnKillLabel(k.x, k.y + 3.0 * c.killSize, k.z, now);
                }
                if (t < 1.6f) flameBurst(k.x, k.y + RNG.nextFloat() * 6.0 * c.killSize, k.z, 0.5, 4, 0.12f, 0x606060, 0xFF7A18);
                if (age > 3600) it.remove();
            }
        }
    }

    private static void tickStrikes(Config c, long now) {
        Iterator<Strike> it = strikes.iterator();
        while (it.hasNext()) {
            Strike s = it.next();
            long age = now - s.t0;
            if (!s.impacted && age >= 1150) {
                s.impacted = true;
                for (int i = 0; i < 40 && sparks.size() < 700; i++) {
                    Spark sp = new Spark();
                    double th = RNG.nextDouble() * Math.PI * 2;
                    float spd = 0.04f + RNG.nextFloat() * 0.16f;
                    sp.vx = (float) Math.cos(th) * spd;
                    sp.vz = (float) Math.sin(th) * spd;
                    sp.vy = 0.08f + RNG.nextFloat() * 0.25f;
                    sp.x = (float) s.x;
                    sp.y = (float) s.y + 0.1f;
                    sp.z = (float) s.z;
                    sp.life = 16 + RNG.nextInt(14);
                    sp.size = 0.12f + RNG.nextFloat() * 0.14f;
                    sp.type = 3;
                    sp.seed = RNG.nextFloat();
                    sp.rgb = RNG.nextBoolean() ? 0xFFB020 : 0xFF5018;
                    sparks.add(sp);
                }
                rings.add(new Ring(s.x, s.y + 0.03, s.z, now, 3.0f, 900, 0xFF8A20, true));
                rings.add(new Ring(s.x, s.y + 0.03, s.z, now, 1.8f, 600, 0xFFE040, true));
                if (c.killCounter) spawnKillLabel(s.x, s.y + 1.2, s.z, now);
            }
            if (age > 2400) it.remove();
        }
    }

    /** Зелёные вихри вдоль лезвия во время замаха. */
    private static void spawnWisps(Scythe sc, Config c, float t) {
        float[] pose = scythePose(t);
        float ang = (float) Math.toRadians(pose[0]);
        float ct = (float) Math.cos(TILT), st = (float) Math.sin(TILT);
        float prx = sc.rx * ct - sc.rz * st, prz = sc.rx * st + sc.rz * ct;
        float S = 0.5f * c.killSize;
        for (int k = 0; k < 3 && sparks.size() < 700; k++) {
            int i = RNG.nextInt(BN + 1);
            float u = RNG.nextFloat();
            float px = BLO[i][0] * (1 - u) + BLI[i][0] * u, py = BLO[i][1] * (1 - u) + BLI[i][1] * u;
            float[] w = w3((float) sc.x, (float) sc.hy, (float) sc.z, prx, prz, S, px, py, 0f, ang, pose[1]);
            Spark sp = new Spark();
            sp.x = w[0];
            sp.y = w[1];
            sp.z = w[2];
            sp.vx = (RNG.nextFloat() - 0.5f) * 0.02f;
            sp.vy = 0.01f + RNG.nextFloat() * 0.02f;
            sp.vz = (RNG.nextFloat() - 0.5f) * 0.02f;
            sp.life = 14 + RNG.nextInt(10);
            sp.size = (0.07f + RNG.nextFloat() * 0.07f) * c.killSize;
            sp.type = 3;
            sp.seed = RNG.nextFloat();
            sp.rgb = killRgb(c, RNG.nextFloat());
            sparks.add(sp);
        }
    }

    private static void spawnFlames(Label l, Config c, long now) {
        GraffitiText.Tex tx = GraffitiText.get(l.text);
        float hh = 0.55f * c.comboSize;
        float ww = hh * tx.w / (float) tx.h;
        float age = (now - l.t0) / 1000f;
        float cy = (float) l.y + 0.25f * age;
        float cx = (float) l.x + l.vx * age, cz = (float) l.z + l.vz * age;
        for (int k = 0; k < 2 && sparks.size() < 700; k++) {
            Spark s = new Spark();
            s.x = cx + (RNG.nextFloat() - 0.5f) * ww * 0.9f;
            s.y = cy + (RNG.nextFloat() - 0.3f) * hh * 0.5f;
            s.z = cz + (RNG.nextFloat() - 0.5f) * 0.1f;
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

    private static void tickSparks(ClientWorld w) {
        long now = System.currentTimeMillis();
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
            switch (s.type) {
                case 3:
                    s.vx *= 0.96f;
                    s.vz *= 0.96f;
                    s.vy = s.vy * 0.98f + 0.002f;
                    break;
                case 4: {
                    s.vx *= 0.985f;
                    s.vz *= 0.985f;
                    s.vy -= 0.03f;
                    BlockPos bp = BlockPos.ofFloored(s.x, s.y, s.z);
                    BlockState st = w.getBlockState(bp);
                    if (!st.getCollisionShape(w, bp).isEmpty()) {
                        if (RNG.nextFloat() < 0.4f && decals.size() < 200) {
                            Decal d = new Decal();
                            d.x = s.x;
                            d.y = bp.getY() + 1.02f;
                            d.z = s.z;
                            d.seed = RNG.nextFloat();
                            d.size = 0.1f + RNG.nextFloat() * 0.22f;
                            d.t0 = now;
                            d.dur = 3500;
                            d.rgb = s.rgb;
                            decals.add(d);
                        }
                        it.remove();
                    }
                    break;
                }
                case 5:
                    s.vx *= 0.95f;
                    s.vz *= 0.95f;
                    s.vy = s.vy * 0.95f - 0.012f;
                    break;
                case 6:
                    break;
                default:
                    s.vx *= 0.96f;
                    s.vz *= 0.96f;
                    s.vy -= 0.018f;
                    break;
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
        boolean anything = c.ribbonEnabled || c.jumpEnabled || c.hatEnabled || c.haloEnabled || c.trajEnabled || c.skyEnabled
                || !drops.isEmpty() || !rings.isEmpty() || !sparks.isEmpty() || !labels.isEmpty()
                || !decals.isEmpty() || !scythes.isEmpty() || !strikes.isEmpty() || !kfx.isEmpty() || c.wingsEnabled;
        if (!anything) return;

        Vec3d cp = ctx.camera().getPos();
        float td = mc.getRenderTickCounter().getTickDelta(true);
        double px = MathHelper.lerp(td, p.prevX, p.getX());
        double py = MathHelper.lerp(td, p.prevY, p.getY());
        double pz = MathHelper.lerp(td, p.prevZ, p.getZ());
        boolean first = mc.options.getPerspective().isFirstPerson();
        long now = System.currentTimeMillis();

        ShaderProgram prevShader = RenderSystem.getShader();
        int prevTex0 = RenderSystem.getShaderTexture(0);
        ms.push();
        ms.translate(-cp.x, -cp.y, -cp.z);
        Matrix4f m = ms.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        if (c.skyEnabled && c.skyMode == 2) {
            SkyFx.draw(m, cp, mc.gameRenderer.getFarPlaneDistance() * 0.9f, now);
        }
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Quaternionf q = ctx.camera().getRotation();
        Vector3f right = new Vector3f(1, 0, 0).rotate(q);
        Vector3f up = new Vector3f(0, 1, 0).rotate(q);
        Vector3f rh = new Vector3f(right.x, 0, right.z);
        if (rh.lengthSquared() > 1e-6f) rh.normalize();
        else rh.set(1, 0, 0);

        if (c.ribbonEnabled) drawRibbon(m, c, px, py, pz, now, cp);
        if (!rings.isEmpty()) drawRings(m, c, now);
        if (c.trajEnabled) drawTrajectory(m, c, p, mc, td, px, py, pz, cp, right, up);
        double top = py + p.getHeight();
        if (c.hatEnabled && !first) drawHat(m, c, px, top, pz);
        if (c.haloEnabled && !first) drawHalo(m, c, px, top, pz, now);
        if (c.wingsEnabled && !first) drawWings(m, c, p, px, py, pz, td, now);
        if (!drops.isEmpty()) drawDrops(m, c, td, right, up, rh);
        if (!sparks.isEmpty() || !decals.isEmpty() || !scythes.isEmpty() || !strikes.isEmpty() || !kfx.isEmpty()) {
            drawFx(m, c, td, now, right, up, rh, cp);
        }
        if (!labels.isEmpty()) drawLabels(m, c, now, right, up);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderTexture(0, prevTex0);
        if (prevShader != null) RenderSystem.setShader(() -> prevShader);
        ms.pop();
    }

    /** Небо в ранний момент кадра (до земли или до сущностей): не пересекается с частицами и подбором предметов. */
    public static void renderSkyStage(WorldRenderContext ctx, int stage) {
        Config c = Config.I;
        if (!c.skyEnabled || c.skyMode != stage) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        MatrixStack ms = ctx.matrixStack();
        if (ms == null) return;
        Vec3d cp = ctx.camera().getPos();
        ShaderProgram prevShader = RenderSystem.getShader();
        int prevTex0 = RenderSystem.getShaderTexture(0);
        ms.push();
        ms.translate(-cp.x, -cp.y, -cp.z);
        Matrix4f m = ms.peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        SkyFx.draw(m, cp, mc.gameRenderer.getFarPlaneDistance() * 0.9f, System.currentTimeMillis());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderTexture(0, prevTex0);
        if (prevShader != null) RenderSystem.setShader(() -> prevShader);
        ms.pop();
    }

    /** Время суток обновляется каждый кадр, чтобы сервер не перебивал его. */
    public static void frameTime() {
        Config c = Config.I;
        if (!c.timeEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null) mc.world.setTimeOfDay((long) c.timeOfDay);
    }

    // ---------- траектория выстрела ----------
    private static void ring3D(BufferBuilder b, Matrix4f m, float x, float y, float z, float nx, float ny, float nz,
                               float[] t1, float[] t2, float rad, float thick, int rgb, float a) {
        int seg = 24;
        float off = 0.02f;
        float ri = Math.max(0f, rad - thick);
        for (int i = 0; i < seg; i++) {
            double a1 = i * Math.PI * 2 / seg, a2 = (i + 1) * Math.PI * 2 / seg;
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1), c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
            float bx = x + nx * off, by = y + ny * off, bz = z + nz * off;
            float[][] p = new float[4][];
            p[0] = new float[]{bx + (t1[0] * c1 + t2[0] * s1) * ri, by + (t1[1] * c1 + t2[1] * s1) * ri, bz + (t1[2] * c1 + t2[2] * s1) * ri};
            p[1] = new float[]{bx + (t1[0] * c1 + t2[0] * s1) * rad, by + (t1[1] * c1 + t2[1] * s1) * rad, bz + (t1[2] * c1 + t2[2] * s1) * rad};
            p[2] = new float[]{bx + (t1[0] * c2 + t2[0] * s2) * rad, by + (t1[1] * c2 + t2[1] * s2) * rad, bz + (t1[2] * c2 + t2[2] * s2) * rad};
            p[3] = new float[]{bx + (t1[0] * c2 + t2[0] * s2) * ri, by + (t1[1] * c2 + t2[1] * s2) * ri, bz + (t1[2] * c2 + t2[2] * s2) * ri};
            int in = argb(rgb, a * 0.15f), out = argb(rgb, a);
            vtx(b, m, p[0][0], p[0][1], p[0][2], in);
            vtx(b, m, p[1][0], p[1][1], p[1][2], out);
            vtx(b, m, p[2][0], p[2][1], p[2][2], out);
            vtx(b, m, p[0][0], p[0][1], p[0][2], in);
            vtx(b, m, p[2][0], p[2][1], p[2][2], out);
            vtx(b, m, p[3][0], p[3][1], p[3][2], in);
        }
    }

    private static void drawTrajectory(Matrix4f m, Config c, ClientPlayerEntity p, MinecraftClient mc, float td,
                                       double px, double py, double pz, Vec3d cam, Vector3f right, Vector3f up) {
        float speed = 0f, grav = 0.03f;
        boolean found = false;
        ItemStack[] hands = {p.getMainHandStack(), p.getOffHandStack()};
        for (ItemStack st : hands) {
            Item it = st.getItem();
            if (c.trajBow && it instanceof BowItem && p.isUsingItem() && p.getActiveItem().getItem() instanceof BowItem) {
                float pull = BowItem.getPullProgress(p.getItemUseTime());
                if (pull < 0.1f) continue;
                speed = pull * 3f;
                grav = 0.05f;
                found = true;
                break;
            }
            if (c.trajBow && it instanceof CrossbowItem && CrossbowItem.isCharged(st)) {
                speed = 3.15f;
                grav = 0.05f;
                found = true;
                break;
            }
            if (c.trajBow && it instanceof TridentItem && p.isUsingItem() && p.getActiveItem().getItem() instanceof TridentItem
                    && p.getItemUseTime() >= 10) {
                speed = 2.5f;
                grav = 0.05f;
                found = true;
                break;
            }
            if (c.trajPearl && (it instanceof EnderPearlItem || it instanceof SnowballItem || it instanceof EggItem)) {
                speed = 1.5f;
                grav = 0.03f;
                found = true;
                break;
            }
        }
        if (!found) return;

        float eyeH = p.getEyeHeight(p.getPose());
        Vec3d pos = new Vec3d(px, py + eyeH - 0.1, pz);
        double yr = Math.toRadians(p.getYaw(td)), pr = Math.toRadians(p.getPitch(td));
        Vec3d dir = new Vec3d(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
        Vec3d pv0 = p.getVelocity();
        Vec3d vel = dir.multiply(speed).add(pv0.x, p.isOnGround() ? 0 : pv0.y, pv0.z);

        List<Entity> ents = mc.world.getOtherEntities(p, new Box(px - 80, py - 80, pz - 80, px + 80, py + 80, pz + 80),
                ent -> ent instanceof LivingEntity && ent.isAlive());
        List<Vec3d> pts = new ArrayList<>();
        pts.add(pos);
        BlockHitResult hitBlock = null;
        boolean hitEnt = false;
        int steps = Math.max(10, c.trajSteps);
        for (int i = 0; i < steps; i++) {
            Vec3d next = pos.add(vel);
            BlockHitResult r = mc.world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, p));
            if (r.getType() != HitResult.Type.MISS) {
                next = r.getPos();
                hitBlock = r;
            }
            for (Entity ent : ents) {
                java.util.Optional<Vec3d> hp = ent.getBoundingBox().expand(0.3).raycast(pos, next);
                if (hp.isPresent()) {
                    next = hp.get();
                    hitEnt = true;
                    hitBlock = null;
                    break;
                }
            }
            pts.add(next);
            if (hitBlock != null || hitEnt) break;
            pos = next;
            vel = vel.multiply(0.99).subtract(0, grav, 0);
        }

        BufferBuilder b = begin(VertexFormat.DrawMode.TRIANGLES);
        int n = pts.size();
        for (int i = 1; i < n; i++) {
            Vec3d a0 = pts.get(i - 1), a1 = pts.get(i);
            int rgb = hsb(c.trajRainbow, c.trajHue, i * 0.02f);
            int col = argb(rgb, 0.95f - 0.6f * (i / (float) n));
            streak(b, m, (float) a1.x, (float) a1.y, (float) a1.z, (float) a0.x, (float) a0.y, (float) a0.z,
                    cam, c.trajThick, col, col);
        }
        if (c.trajMarker) {
            Vec3d last = pts.get(n - 1);
            int mrgb = hitEnt ? 0xFF4040 : (hitBlock != null ? 0x40FF70 : 0xFFFFFF);
            if (hitBlock != null) {
                Direction side = hitBlock.getSide();
                float nx = side.getOffsetX(), ny = side.getOffsetY(), nz = side.getOffsetZ();
                float[] t1 = Math.abs(ny) > 0.5f ? new float[]{1, 0, 0} : new float[]{0, 1, 0};
                float[] t2 = {ny * t1[2] - nz * t1[1], nz * t1[0] - nx * t1[2], nx * t1[1] - ny * t1[0]};
                ring3D(b, m, (float) last.x, (float) last.y, (float) last.z, nx, ny, nz, t1, t2, 0.26f, 0.035f, mrgb, 0.95f);
                ring3D(b, m, (float) last.x, (float) last.y, (float) last.z, nx, ny, nz, t1, t2, 0.1f, 0.03f, mrgb, 0.95f);
            } else {
                ringBillboard(b, m, (float) last.x, (float) last.y, (float) last.z, right, up, 0.24f, 0.035f, mrgb, 0.95f);
            }
        }
        end(b);
    }

    // ---------- частицы, лужи крови, коса ----------
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

    /** Тонкая полоска вдоль линии, повёрнутая лицом к камере. */
    private static void streak(BufferBuilder b, Matrix4f m, float hx, float hy, float hz, float tx, float ty, float tz,
                               Vec3d cam, float w, int cHead, int cTail) {
        float dx = hx - tx, dy = hy - ty, dz = hz - tz;
        float vx = (float) cam.x - hx, vy = (float) cam.y - hy, vz = (float) cam.z - hz;
        float sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
        float l = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-6f) return;
        sx = sx / l * w;
        sy = sy / l * w;
        sz = sz / l * w;
        vtx(b, m, hx - sx, hy - sy, hz - sz, cHead);
        vtx(b, m, hx + sx, hy + sy, hz + sz, cHead);
        vtx(b, m, tx + sx, ty + sy, tz + sz, cTail);
        vtx(b, m, hx - sx, hy - sy, hz - sz, cHead);
        vtx(b, m, tx + sx, ty + sy, tz + sz, cTail);
        vtx(b, m, tx - sx, ty - sy, tz - sz, cTail);
    }

    private static void ringBillboard(BufferBuilder b, Matrix4f m, float x, float y, float z, Vector3f right, Vector3f up,
                                      float rad, float thick, int rgb, float a) {
        int seg = 28;
        for (int i = 0; i < seg; i++) {
            double a1 = i * Math.PI * 2 / seg, a2 = (i + 1) * Math.PI * 2 / seg;
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1), c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
            float ri = Math.max(0f, rad - thick);
            pv(b, m, x, y, z, right, up, c1 * ri, s1 * ri, argb(rgb, a * 0.1f));
            pv(b, m, x, y, z, right, up, c1 * rad, s1 * rad, argb(rgb, a * 0.95f));
            pv(b, m, x, y, z, right, up, c2 * rad, s2 * rad, argb(rgb, a * 0.95f));
            pv(b, m, x, y, z, right, up, c1 * ri, s1 * ri, argb(rgb, a * 0.1f));
            pv(b, m, x, y, z, right, up, c2 * rad, s2 * rad, argb(rgb, a * 0.95f));
            pv(b, m, x, y, z, right, up, c2 * ri, s2 * ri, argb(rgb, a * 0.1f));
        }
    }

    private static void drawFx(Matrix4f m, Config c, float td, long now, Vector3f right, Vector3f up, Vector3f rh, Vec3d cam) {
        curRhx = rh.x;
        curRhz = rh.z;
        BufferBuilder b = begin(VertexFormat.DrawMode.TRIANGLES);

        for (Spark s : sparks) {
            float t = s.age / (float) s.life;
            float a = 1f - t;
            float x = s.x + s.vx * td, y = s.y + s.vy * td, z = s.z + s.vz * td;
            switch (s.type) {
                case 0:
                    star(b, m, x, y, z, right, up, s.size, s.rgb != 0 ? s.rgb : hsb(c.critRainbow, c.critHue, s.seed),
                            (s.age + td) * 0.2f + s.seed * 6f, a);
                    break;
                case 1:
                    disc(b, m, x, y, z, right, up, s.size, hsb(c.critRainbow, c.critHue, s.seed), a);
                    break;
                case 2:
                    heart(b, m, x, y, z, right, up, s.size, hsb(c.critRainbow, c.critHue, s.seed),
                            (float) Math.sin((s.age + td) * 0.2f + s.seed * 6f) * 0.4f, a);
                    break;
                case 4:
                    streak(b, m, x, y, z, x - s.vx * 2.4f, y - s.vy * 2.4f, z - s.vz * 2.4f, cam, s.size,
                            argb(s.rgb, 0.95f * Math.min(1f, a * 2f)), argb(s.rgb, 0f));
                    break;
                case 5:
                    streak(b, m, x, y, z, x - s.vx * 1.8f, y - s.vy * 1.8f, z - s.vz * 1.8f, cam, s.size,
                            argb(mixWhite(s.rgb, 0.6f), a), argb(s.rgb, 0f));
                    break;
                case 6: {
                    float e = 1f - (1f - t) * (1f - t) * (1f - t);
                    float rad = s.size * e + 0.05f;
                    ringBillboard(b, m, x, y, z, right, up, rad, rad * 0.16f + 0.02f, s.rgb, a);
                    break;
                }
                default: {
                    int rgb = t < 0.4f ? mixc(0xFFE040, 0xFF8C20, t / 0.4f) : mixc(0xFF8C20, 0xE02010, (t - 0.4f) / 0.6f);
                    if (s.rgb != 0) rgb = s.rgb;
                    disc(b, m, x, y, z, right, up, s.size * (1f - 0.5f * t), rgb, a * 0.9f);
                    break;
                }
            }
        }

        // лужи крови на земле
        for (Decal d : decals) {
            float t = (now - d.t0) / (float) d.dur;
            if (t < 0f || t > 1f) continue;
            float a = (t > 0.55f ? 1f - (t - 0.55f) / 0.45f : 1f) * 0.85f;
            int seg = 10;
            int dark = mixc(d.rgb, 0x000000, 0.3f);
            for (int i = 0; i < seg; i++) {
                double a1 = i * Math.PI * 2 / seg, a2 = (i + 1) * Math.PI * 2 / seg;
                float r1 = d.size * (0.65f + 0.35f * jitter(i, d.seed));
                float r2 = d.size * (0.65f + 0.35f * jitter(i + 1, d.seed));
                vtx(b, m, d.x, d.y, d.z, argb(dark, a));
                vtx(b, m, d.x + (float) Math.cos(a1) * r1, d.y, d.z + (float) Math.sin(a1) * r1, argb(d.rgb, a * 0.85f));
                vtx(b, m, d.x + (float) Math.cos(a2) * r2, d.y, d.z + (float) Math.sin(a2) * r2, argb(d.rgb, a * 0.85f));
            }
        }

        // коса
        for (Scythe s : scythes) drawScythe(b, m, c, s, now, rh);
        for (Strike s : strikes) drawStrike(b, m, s, now);
        for (KillFx k : kfx) drawKillFx(b, m, c, k, now);

        end(b);
    }

    private static float jitter(int i, float seed) {
        float v = (float) Math.sin((i % 10) * 12.9898 + seed * 78.233) * 43758.5453f;
        return v - (float) Math.floor(v);
    }

    // ---------- коса (3D, по референсу: зелёное лезвие, череп, изогнутое древко, крюк) ----------
    private static final int BN = 16, HN = 8;
    private static final float BASE = (float) Math.toRadians(65);
    private static final float CB = (float) Math.cos(BASE), SB = (float) Math.sin(BASE);
    private static final float HPX = 3.25f * CB - 0.02f * SB, HPY = 3.25f * SB + 0.02f * CB; // хват
    private static final float LX = 0.35f, LY = 0.8f, LZ = 0.5f;

    // координаты точек взяты с картинки (пиксели), остриё лезвия переносится в (0,0)
    private static float lx(float px) {
        return px / 100f - 0.70f;
    }

    private static float ly(float py) {
        return -py / 100f + 3.20f;
    }

    private static float[][] curve(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int n) {
        float[][] r = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n, u = 1 - t;
            float x = u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3;
            float y = u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3;
            r[i] = new float[]{lx(x), ly(y)};
        }
        return r;
    }

    private static final float[][] BLO = curve(100, 75, 20, 150, 35, 260, 75, 320, BN);
    private static final float[][] BLI = curve(150, 105, 100, 170, 70, 250, 75, 320, BN);
    private static final float[][] HNO = curve(145, 60, 180, 35, 215, 40, 232, 62, HN);
    private static final float[][] HNI = curve(150, 105, 175, 70, 205, 60, 232, 62, HN);
    private static final float[][] HKO = curve(468, 328, 500, 295, 540, 320, 520, 358, HN);
    private static final float[][] HKI = curve(472, 345, 498, 320, 515, 332, 512, 352, HN);
    private static final float[][] SP1O = curve(112, 60, 106, 45, 101, 32, 98, 20, 6);
    private static final float[][] SP1I = curve(124, 63, 116, 47, 108, 33, 98, 20, 6);
    private static final float[][] SP2O = curve(130, 58, 134, 42, 138, 30, 142, 18, 6);
    private static final float[][] SP2I = curve(138, 62, 140, 46, 141, 32, 142, 18, 6);

    /** Локальная точка в мировые координаты: базовый наклон, затем вращение вокруг хвата. */
    private static float[] w3(float ox, float oy, float oz, float rhx, float rhz, float S,
                              float px, float py, float pz, float ang, float yoff) {
        float bx = px * CB - py * SB, by = px * SB + py * CB;
        float dx = bx - HPX, dy = by - HPY;
        float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
        float rx = HPX + dx * ca - dy * sa, ry = HPY + dx * sa + dy * ca;
        return new float[]{ox + rhx * rx * S - rhz * EC * ry * S - rhz * ES * pz * S,
                oy + ES * ry * S - EC * pz * S + yoff,
                oz + rhz * rx * S + rhx * EC * ry * S + rhx * ES * pz * S};
    }

    private static void tri3(BufferBuilder b, Matrix4f m, float[] p0, float[] p1, float[] p2, int rgb, float a) {
        float ux = p1[0] - p0[0], uy = p1[1] - p0[1], uz = p1[2] - p0[2];
        float vx = p2[0] - p0[0], vy = p2[1] - p0[1], vz = p2[2] - p0[2];
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        float sh = 0.55f;
        if (l > 1e-8f) sh = 0.45f + 0.55f * Math.abs((nx * LX + ny * LY + nz * LZ) / l);
        int col = argb(mixc(0x000000, rgb, Math.min(1f, sh + 0.1f)), a);
        vtx(b, m, p0[0], p0[1], p0[2], col);
        vtx(b, m, p1[0], p1[1], p1[2], col);
        vtx(b, m, p2[0], p2[1], p2[2], col);
    }

    private static void cyl(BufferBuilder b, Matrix4f m, float ox, float oy, float oz, float rhx, float rhz, float S,
                            float ang, float yoff, float ax, float ay, float fx, float fy, float r, int rgb, float a) {
        float dx = fx - ax, dy = fy - ay;
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        float px = -dy / l, py = dx / l;
        int seg = 8;
        for (int k = 0; k < seg; k++) {
            double t0 = k * Math.PI * 2 / seg, t1 = (k + 1) * Math.PI * 2 / seg;
            float c0 = (float) Math.cos(t0) * r, s0 = (float) Math.sin(t0) * r;
            float c1 = (float) Math.cos(t1) * r, s1 = (float) Math.sin(t1) * r;
            float[] a0 = w3(ox, oy, oz, rhx, rhz, S, ax + px * c0, ay + py * c0, s0, ang, yoff);
            float[] a1 = w3(ox, oy, oz, rhx, rhz, S, ax + px * c1, ay + py * c1, s1, ang, yoff);
            float[] f0 = w3(ox, oy, oz, rhx, rhz, S, fx + px * c0, fy + py * c0, s0, ang, yoff);
            float[] f1 = w3(ox, oy, oz, rhx, rhz, S, fx + px * c1, fy + py * c1, s1, ang, yoff);
            tri3(b, m, a0, a1, f1, rgb, a);
            tri3(b, m, a0, f1, f0, rgb, a);
        }
    }

    private static void ellipsoid(BufferBuilder b, Matrix4f m, float ox, float oy, float oz, float rhx, float rhz, float S,
                                  float ang, float yoff, float cx, float cy, float cz, float rx, float ry, float rz,
                                  int rgb, float a) {
        int lat = 5, lon = 8;
        for (int i = 0; i < lat; i++) {
            double p0 = -Math.PI / 2 + i * Math.PI / lat, p1 = -Math.PI / 2 + (i + 1) * Math.PI / lat;
            for (int j = 0; j < lon; j++) {
                double t0 = j * Math.PI * 2 / lon, t1 = (j + 1) * Math.PI * 2 / lon;
                float[] a0 = w3(ox, oy, oz, rhx, rhz, S, cx + rx * (float) (Math.cos(p0) * Math.cos(t0)),
                        cy + ry * (float) Math.sin(p0), cz + rz * (float) (Math.cos(p0) * Math.sin(t0)), ang, yoff);
                float[] a1 = w3(ox, oy, oz, rhx, rhz, S, cx + rx * (float) (Math.cos(p0) * Math.cos(t1)),
                        cy + ry * (float) Math.sin(p0), cz + rz * (float) (Math.cos(p0) * Math.sin(t1)), ang, yoff);
                float[] b0 = w3(ox, oy, oz, rhx, rhz, S, cx + rx * (float) (Math.cos(p1) * Math.cos(t0)),
                        cy + ry * (float) Math.sin(p1), cz + rz * (float) (Math.cos(p1) * Math.sin(t0)), ang, yoff);
                float[] b1 = w3(ox, oy, oz, rhx, rhz, S, cx + rx * (float) (Math.cos(p1) * Math.cos(t1)),
                        cy + ry * (float) Math.sin(p1), cz + rz * (float) (Math.cos(p1) * Math.sin(t1)), ang, yoff);
                tri3(b, m, a0, a1, b1, rgb, a);
                tri3(b, m, a0, b1, b0, rgb, a);
            }
        }
    }

    /** Лезвие с толщиной: две грани и боковые стенки, цвет плавно меняется от основания к острию. */
    private static void bladeShape(BufferBuilder b, Matrix4f m, float ox, float oy, float oz, float rhx, float rhz, float S,
                                   float ang, float yoff, float a, float[][] O, float[][] I,
                                   float th0, float th1, int cRoot, int cTip) {
        int n = O.length - 1;
        float[][] oF = new float[n + 1][], oB = new float[n + 1][], iF = new float[n + 1][], iB = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            float th = th0 + (th1 - th0) * i / n;
            oF[i] = w3(ox, oy, oz, rhx, rhz, S, O[i][0], O[i][1], th, ang, yoff);
            oB[i] = w3(ox, oy, oz, rhx, rhz, S, O[i][0], O[i][1], -th, ang, yoff);
            iF[i] = w3(ox, oy, oz, rhx, rhz, S, I[i][0], I[i][1], th, ang, yoff);
            iB[i] = w3(ox, oy, oz, rhx, rhz, S, I[i][0], I[i][1], -th, ang, yoff);
        }
        for (int i = 0; i < n; i++) {
            float u = (i + 0.5f) / n;
            int face = mixWhite(mixc(cRoot, cTip, u), 0.1f);
            int edge = mixc(face, 0x000000, 0.3f);
            tri3(b, m, oF[i], iF[i], oF[i + 1], face, a);
            tri3(b, m, iF[i], iF[i + 1], oF[i + 1], face, a);
            tri3(b, m, oB[i], iB[i], oB[i + 1], face, a);
            tri3(b, m, iB[i], iB[i + 1], oB[i + 1], face, a);
            tri3(b, m, oF[i], oB[i], oF[i + 1], edge, a);
            tri3(b, m, oB[i], oB[i + 1], oF[i + 1], edge, a);
            tri3(b, m, iF[i], iB[i], iF[i + 1], edge, a);
            tri3(b, m, iB[i], iB[i + 1], iF[i + 1], edge, a);
        }
        int e0 = mixc(cRoot, 0x000000, 0.3f);
        tri3(b, m, oF[0], oB[0], iF[0], e0, a);
        tri3(b, m, oB[0], iB[0], iF[0], e0, a);
    }

    private static void scytheMesh(BufferBuilder b, Matrix4f m, float x, float y, float z, float rhx, float rhz, float S,
                                   float ang, float yoff, float a, int rgb) {
        int tip = mixc(rgb, 0xFFF08A, 0.55f); // кончик желтеет, как на референсе
        bladeShape(b, m, x, y, z, rhx, rhz, S, ang, yoff, a, BLO, BLI, 0.13f, 0.03f, mixc(rgb, 0x000000, 0.15f), tip);
        bladeShape(b, m, x, y, z, rhx, rhz, S, ang, yoff, a, HNO, HNI, 0.07f, 0.02f, rgb, tip);
        bladeShape(b, m, x, y, z, rhx, rhz, S, ang, yoff, a, HKO, HKI, 0.07f, 0.05f, mixc(rgb, 0x000000, 0.2f), rgb);

        // череп на основании лезвия: черепная коробка, скулы, челюсть, глазницы, нос, зубы и костяные шипы
        int bone = 0xE8D58A, boneDark = mixc(bone, 0x000000, 0.35f), hole = 0x14100A;
        float sx = lx(125), sy = ly(78);
        ellipsoid(b, m, x, y, z, rhx, rhz, S, ang, yoff, sx, sy, 0f, 0.32f, 0.27f, 0.25f, bone, a);
        ellipsoid(b, m, x, y, z, rhx, rhz, S, ang, yoff, sx - 0.03f, sy - 0.2f, 0f, 0.22f, 0.14f, 0.2f, bone, a);
        ellipsoid(b, m, x, y, z, rhx, rhz, S, ang, yoff, sx + 0.02f, sy - 0.33f, 0f, 0.17f, 0.09f, 0.15f, boneDark, a);
        for (int side = -1; side <= 1; side += 2) {
            float zz = side * 0.2f;
            ellipsoid(b, m, x, y, z, rhx, rhz, S, ang, yoff, lx(112), ly(74), zz, 0.085f, 0.1f, 0.05f, hole, a);
            ellipsoid(b, m, x, y, z, rhx, rhz, S, ang, yoff, lx(138), ly(79), zz, 0.085f, 0.1f, 0.05f, hole, a);
            ellipsoid(b, m, x, y, z, rhx, rhz, S, ang, yoff, sx + 0.01f, sy - 0.1f, zz * 1.05f, 0.03f, 0.05f, 0.03f, hole, a);
            for (int k = -2; k <= 2; k++) {
                float tx0 = sx + 0.02f + k * 0.07f;
                float[] q0 = w3(x, y, z, rhx, rhz, S, tx0 - 0.025f, sy - 0.34f, zz, ang, yoff);
                float[] q1 = w3(x, y, z, rhx, rhz, S, tx0 + 0.025f, sy - 0.34f, zz, ang, yoff);
                float[] q2 = w3(x, y, z, rhx, rhz, S, tx0 + 0.025f, sy - 0.26f, zz, ang, yoff);
                float[] q3 = w3(x, y, z, rhx, rhz, S, tx0 - 0.025f, sy - 0.26f, zz, ang, yoff);
                tri3(b, m, q0, q1, q2, 0xF2EBC8, a);
                tri3(b, m, q0, q2, q3, 0xF2EBC8, a);
            }
        }
        bladeShape(b, m, x, y, z, rhx, rhz, S, ang, yoff, a, SP1O, SP1I, 0.05f, 0.015f, bone, boneDark);
        bladeShape(b, m, x, y, z, rhx, rhz, S, ang, yoff, a, SP2O, SP2I, 0.05f, 0.015f, bone, boneDark);

        // древко: изогнутое, коричневое
        int wood = 0x6B4A2E;
        float[][] pole = {{195, 160}, {270, 200}, {315, 242}, {395, 318}, {470, 332}};
        for (int i = 0; i < pole.length - 1; i++) {
            cyl(b, m, x, y, z, rhx, rhz, S, ang, yoff, lx(pole[i][0]), ly(pole[i][1]),
                    lx(pole[i + 1][0]), ly(pole[i + 1][1]), 0.075f, wood, a);
        }
        // зелёные воротники и красная обмотка
        int armor = mixc(rgb, 0x000000, 0.45f);
        cyl(b, m, x, y, z, rhx, rhz, S, ang, yoff, lx(182), ly(148), lx(208), ly(176), 0.17f, armor, a);
        cyl(b, m, x, y, z, rhx, rhz, S, ang, yoff, lx(262), ly(194), lx(284), ly(213), 0.12f, armor, a);
        cyl(b, m, x, y, z, rhx, rhz, S, ang, yoff, lx(284), ly(213), lx(305), ly(236), 0.095f, 0xA03C50, a);
        cyl(b, m, x, y, z, rhx, rhz, S, ang, yoff, lx(305), ly(236), lx(322), ly(250), 0.12f, armor, a);
        cyl(b, m, x, y, z, rhx, rhz, S, ang, yoff, lx(458), ly(328), lx(480), ly(336), 0.1f, armor, a);
        // светящийся шар в крюке
        ellipsoid(b, m, x, y, z, rhx, rhz, S, ang, yoff, lx(505), ly(345), 0f, 0.11f, 0.11f, 0.11f,
                mixWhite(mixc(rgb, 0xFFF08A, 0.5f), 0.3f), a);
    }

    private static final float WIND = 0.45f, SWING_END = 0.95f;
    private static final float TILT = (float) Math.toRadians(20);
    private static final float ELEV = (float) Math.toRadians(35);
    private static final float EC = (float) Math.cos(ELEV), ES = (float) Math.sin(ELEV);
    private static float curRhx = 1f, curRhz = 0f;

    private static float[] gp(float ox, float oy, float oz, float rhx, float rhz, float S, float[] pt,
                              float ang, float yoff, float grow) {
        float cx = lx(80), cy = ly(190);
        return w3(ox, oy, oz, rhx, rhz, S, cx + (pt[0] - cx) * grow, cy + (pt[1] - cy) * grow, 0f, ang, yoff);
    }

    /** Мягкое свечение вокруг лезвия и призрачный след взмаха. */
    private static void bladeGlow(BufferBuilder b, Matrix4f m, float x, float y, float z, float rhx, float rhz, float S,
                                  float ang, float yoff, float a, int rgb, float grow) {
        int col = argb(rgb, a);
        for (int i = 0; i < BN; i++) {
            float[] o0 = gp(x, y, z, rhx, rhz, S, BLO[i], ang, yoff, grow), o1 = gp(x, y, z, rhx, rhz, S, BLO[i + 1], ang, yoff, grow);
            float[] i0 = gp(x, y, z, rhx, rhz, S, BLI[i], ang, yoff, grow), i1 = gp(x, y, z, rhx, rhz, S, BLI[i + 1], ang, yoff, grow);
            vtx(b, m, o0[0], o0[1], o0[2], col);
            vtx(b, m, i0[0], i0[1], i0[2], col);
            vtx(b, m, o1[0], o1[1], o1[2], col);
            vtx(b, m, i0[0], i0[1], i0[2], col);
            vtx(b, m, i1[0], i1[1], i1[2], col);
            vtx(b, m, o1[0], o1[1], o1[2], col);
        }
    }

    /** Светящиеся жилки по лезвию (как энергия на референсе). */
    private static void veins(BufferBuilder b, Matrix4f m, float x, float y, float z, float rhx, float rhz, float S,
                              float ang, float yoff, float a, int rgb) {
        float pulse = 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 120.0);
        int col = argb(mixWhite(rgb, 0.75f), a * pulse);
        for (int i = 1; i < BN - 1; i++) {
            float m0x = (BLO[i][0] + BLI[i][0]) / 2, m0y = (BLO[i][1] + BLI[i][1]) / 2;
            float m1x = (BLO[i + 1][0] + BLI[i + 1][0]) / 2, m1y = (BLO[i + 1][1] + BLI[i + 1][1]) / 2;
            float dx = m1x - m0x, dy = m1y - m0y;
            float l = (float) Math.sqrt(dx * dx + dy * dy);
            if (l < 1e-6f) continue;
            float nx = -dy / l * 0.025f, ny = dx / l * 0.025f;
            float th = 0.13f + (0.03f - 0.13f) * i / BN + 0.012f;
            for (int side = -1; side <= 1; side += 2) {
                float zz = side * th;
                float[] a0 = w3(x, y, z, rhx, rhz, S, m0x - nx, m0y - ny, zz, ang, yoff);
                float[] a1 = w3(x, y, z, rhx, rhz, S, m0x + nx, m0y + ny, zz, ang, yoff);
                float[] b0 = w3(x, y, z, rhx, rhz, S, m1x - nx, m1y - ny, zz, ang, yoff);
                float[] b1 = w3(x, y, z, rhx, rhz, S, m1x + nx, m1y + ny, zz, ang, yoff);
                vtx(b, m, a0[0], a0[1], a0[2], col);
                vtx(b, m, a1[0], a1[1], a1[2], col);
                vtx(b, m, b1[0], b1[1], b1[2], col);
                vtx(b, m, a0[0], a0[1], a0[2], col);
                vtx(b, m, b1[0], b1[1], b1[2], col);
                vtx(b, m, b0[0], b0[1], b0[2], col);
            }
        }
    }

    private static void cylW(BufferBuilder b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                             float r, int rgb, float a) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-6f) return;
        dx /= l;
        dy /= l;
        dz /= l;
        float ux, uy, uz;
        if (Math.abs(dy) < 0.99f) {
            ux = dz;
            uy = 0;
            uz = -dx;
        } else {
            ux = 1;
            uy = 0;
            uz = 0;
        }
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
        int seg = 8;
        for (int k = 0; k < seg; k++) {
            double t0 = k * Math.PI * 2 / seg, t1 = (k + 1) * Math.PI * 2 / seg;
            float c0 = (float) Math.cos(t0) * r, s0 = (float) Math.sin(t0) * r;
            float c1 = (float) Math.cos(t1) * r, s1 = (float) Math.sin(t1) * r;
            float[] a0 = {x0 + ux * c0 + vx * s0, y0 + uy * c0 + vy * s0, z0 + uz * c0 + vz * s0};
            float[] a1 = {x0 + ux * c1 + vx * s1, y0 + uy * c1 + vy * s1, z0 + uz * c1 + vz * s1};
            float[] f0 = {x1 + ux * c0 + vx * s0, y1 + uy * c0 + vy * s0, z1 + uz * c0 + vz * s0};
            float[] f1 = {x1 + ux * c1 + vx * s1, y1 + uy * c1 + vy * s1, z1 + uz * c1 + vz * s1};
            tri3(b, m, a0, a1, f1, rgb, a);
            tri3(b, m, a0, f1, f0, rgb, a);
        }
    }

    private static void ellW(BufferBuilder b, Matrix4f m, float cx, float cy, float cz, float rx, float ry, float rz,
                             int rgb, float a) {
        int lat = 5, lon = 8;
        for (int i = 0; i < lat; i++) {
            double p0 = -Math.PI / 2 + i * Math.PI / lat, p1 = -Math.PI / 2 + (i + 1) * Math.PI / lat;
            for (int j = 0; j < lon; j++) {
                double t0 = j * Math.PI * 2 / lon, t1 = (j + 1) * Math.PI * 2 / lon;
                float[] a0 = {cx + rx * (float) (Math.cos(p0) * Math.cos(t0)), cy + ry * (float) Math.sin(p0), cz + rz * (float) (Math.cos(p0) * Math.sin(t0))};
                float[] a1 = {cx + rx * (float) (Math.cos(p0) * Math.cos(t1)), cy + ry * (float) Math.sin(p0), cz + rz * (float) (Math.cos(p0) * Math.sin(t1))};
                float[] b0 = {cx + rx * (float) (Math.cos(p1) * Math.cos(t0)), cy + ry * (float) Math.sin(p1), cz + rz * (float) (Math.cos(p1) * Math.sin(t0))};
                float[] b1 = {cx + rx * (float) (Math.cos(p1) * Math.cos(t1)), cy + ry * (float) Math.sin(p1), cz + rz * (float) (Math.cos(p1) * Math.sin(t1))};
                tri3(b, m, a0, a1, b1, rgb, a);
                tri3(b, m, a0, b1, b0, rgb, a);
            }
        }
    }

    /** Призрак-жнец: полупрозрачная мантия с рваным подолом, капюшон, светящиеся глаза и руки на древке. */
    private static void drawGhost(BufferBuilder b, Matrix4f m, float x, float y, float z, float prx, float prz, float S,
                                  float ang, float yoff, float a, int rgb, long now) {
        float ga = a * 0.55f;
        float hx = x + prx * HPX * S, hy = y + HPY * S + yoff, hz = z + prz * HPX * S;
        float nx = -prz, nz = prx;
        float gx = hx + prx * 0.25f, gz = hz + prz * 0.25f;
        float bottom = y + yoff * 0.9f, top = hy + 0.25f;
        float ts = (now % 1000000L) / 1000f;
        int robe = mixWhite(rgb, 0.15f);
        int rings = 10, seg = 10;
        for (int r = 0; r < rings; r++) {
            float s0 = r / (float) rings, s1 = (r + 1) / (float) rings;
            float y0 = top + (bottom - top) * s0, y1 = top + (bottom - top) * s1;
            float r0 = 0.26f + 0.24f * (float) Math.pow(s0, 0.7), r1 = 0.26f + 0.24f * (float) Math.pow(s1, 0.7);
            int c0 = argb(robe, ga * (1f - 0.75f * s0)), c1 = argb(robe, ga * (1f - 0.75f * s1));
            for (int k = 0; k < seg; k++) {
                double t0 = k * Math.PI * 2 / seg, t1 = (k + 1) * Math.PI * 2 / seg;
                float w00 = 0.07f * s0 * (float) Math.sin(ts * 5 + k * 1.7 + s0 * 7);
                float w01 = 0.07f * s0 * (float) Math.sin(ts * 5 + (k + 1) * 1.7 + s0 * 7);
                float w10 = 0.07f * s1 * (float) Math.sin(ts * 5 + k * 1.7 + s1 * 7);
                float w11 = 0.07f * s1 * (float) Math.sin(ts * 5 + (k + 1) * 1.7 + s1 * 7);
                vtx(b, m, gx + (float) Math.cos(t0) * (r0 + w00), y0, gz + (float) Math.sin(t0) * (r0 + w00), c0);
                vtx(b, m, gx + (float) Math.cos(t1) * (r0 + w01), y0, gz + (float) Math.sin(t1) * (r0 + w01), c0);
                vtx(b, m, gx + (float) Math.cos(t1) * (r1 + w11), y1, gz + (float) Math.sin(t1) * (r1 + w11), c1);
                vtx(b, m, gx + (float) Math.cos(t0) * (r0 + w00), y0, gz + (float) Math.sin(t0) * (r0 + w00), c0);
                vtx(b, m, gx + (float) Math.cos(t1) * (r1 + w11), y1, gz + (float) Math.sin(t1) * (r1 + w11), c1);
                vtx(b, m, gx + (float) Math.cos(t0) * (r1 + w10), y1, gz + (float) Math.sin(t0) * (r1 + w10), c1);
            }
        }
        // капюшон и глаза
        ellW(b, m, gx, top + 0.28f, gz, 0.24f, 0.3f, 0.24f, mixc(rgb, 0x000000, 0.2f), ga);
        float dxn = x - gx, dzn = z - gz;
        float dl = (float) Math.sqrt(dxn * dxn + dzn * dzn);
        if (dl > 1e-4f) {
            dxn /= dl;
            dzn /= dl;
        }
        int eye = mixWhite(rgb, 0.85f);
        for (int side = -1; side <= 1; side += 2) {
            ellW(b, m, gx + dxn * 0.2f + nx * 0.08f * side, top + 0.3f, gz + dzn * 0.2f + nz * 0.08f * side,
                    0.045f, 0.045f, 0.045f, eye, a);
        }
        // руки: одна держит хват, вторая скользит по древку
        float[] hand2 = w3(x, y, z, prx, prz, S, lx(270), ly(200), 0f, ang, yoff);
        cylW(b, m, gx + nx * 0.22f, top - 0.05f, gz + nz * 0.22f, hx, hy, hz, 0.07f, robe, ga);
        cylW(b, m, gx - nx * 0.22f, top - 0.05f, gz - nz * 0.22f, hand2[0], hand2[1], hand2[2], 0.07f, robe, ga);
    }

    /** Поза косы: [угол, смещение, прозрачность]. Замах назад, затем рубящий диагональный удар через шею с ускорением. */
    private static float[] scythePose(float t) {
        float phi;
        if (t < WIND) {
            float u = t / WIND, e = 1f - (1f - u) * (1f - u);
            phi = -100f - 30f * e;
        } else if (t < SWING_END) {
            float u = (t - WIND) / (SWING_END - WIND);
            phi = -130f + 165f * (float) Math.pow(u, 2.2);
        } else {
            float k = t - SWING_END;
            phi = 35f - 7f * (1f - (float) Math.exp(-8 * k));
        }
        float alpha;
        if (t < 0.3f) alpha = t / 0.3f;
        else if (t < 1.9f) alpha = 1f;
        else alpha = Math.max(0f, 1f - (t - 1.9f) / 0.8f);
        return new float[]{phi, 0f, alpha};
    }

    private static void drawScythe(BufferBuilder b, Matrix4f m, Config c, Scythe sc, long now, Vector3f rh) {
        float t = (now - sc.t0) / 1000f;
        if (t < 0f || t > 2.7f) return;
        float[] pose = scythePose(t);
        float ang = (float) Math.toRadians(pose[0]), yoff = pose[1], alpha = pose[2];
        // ориентация зафиксирована в момент убийства: коса не двигается вместе с камерой
        float ct = (float) Math.cos(TILT), st = (float) Math.sin(TILT);
        float prx = sc.rx * ct - sc.rz * st, prz = sc.rx * st + sc.rz * ct;
        int rgb = killRgb(c, 0f);
        float S = 0.5f * c.killSize;
        float x = (float) sc.x, y = (float) sc.hy, z = (float) sc.z;

        if (t >= WIND * 0.7f && t < SWING_END + 0.12f) {
            for (int g = 8; g >= 1; g--) {
                float[] pp = scythePose(Math.max(0f, t - g * 0.02f));
                bladeGlow(b, m, x, y, z, prx, prz, S, (float) Math.toRadians(pp[0]), pp[1], alpha * (0.5f / g), rgb, 1.12f);
            }
        }
        bladeGlow(b, m, x, y, z, prx, prz, S, ang, yoff, alpha * 0.35f, rgb, 1.15f);
        bladeGlow(b, m, x, y, z, prx, prz, S, ang, yoff, alpha * 0.16f, rgb, 1.45f);
        bladeGlow(b, m, x, y, z, prx, prz, S, ang, yoff, alpha * 0.07f, rgb, 1.9f);
        scytheMesh(b, m, x, y, z, prx, prz, S, ang, yoff, alpha, rgb);
        veins(b, m, x, y, z, prx, prz, S, ang, yoff, alpha, rgb);
    }

    // ---------- надписи: комбо и счётчик убийств ----------
    private static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    private static int[] labelCols(Label l, Config c, long now, float fade) {
        int[] col = new int[4]; // BL, BR, TR, TL
        if (l.kind == 1) {
            // счётчик убийств: золотисто-цветной градиент выбранного оттенка
            for (int i = 0; i < 4; i++) {
                float top = (i == 2 || i == 3) ? 1f : 0f;
                float h = c.killRainbow ? ((now % 100000L) / 1500f + i * 0.25f) % 1f : c.killHue;
                int rgb = Color.HSBtoRGB(h, 0.95f - 0.5f * top, 1f) & 0xFFFFFF;
                col[i] = argb(rgb, fade);
            }
            return col;
        }
        if (c.comboColorMode == 2) {
            for (int i = 0; i < 4; i++) {
                int rgb = Color.HSBtoRGB((c.comboHue + i * 0.03f) % 1f, 0.8f, 1f) & 0xFFFFFF;
                col[i] = argb(rgb, fade);
            }
            return col;
        }
        if (l.tier == 0 && c.comboColorMode == 0) {
            col[0] = argb(0xFFD73C, fade);
            col[1] = argb(0xFF9632, fade);
            col[2] = argb(0xFF46AA, fade);
            col[3] = argb(0xE63CC8, fade);
        } else {
            float speed = l.tier <= 1 ? 1800f : (l.tier == 2 ? 1000f : 600f);
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
        int prevTex = RenderSystem.getShaderTexture(0);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        for (Label l : labels) {
            float age = (now - l.t0) / 1000f;
            float lifeS = l.life / 1000f;
            float t = age / lifeS;
            if (t < 0f || t >= 1f) continue;
            GraffitiText.Tex tx = GraffitiText.get(l.text);
            float pop = age < 0.22f ? easeOutBack(age / 0.22f) : 1f;
            float fade = t > 0.65f ? 1f - (t - 0.65f) / 0.35f : 1f;
            float base = l.kind == 1 ? c.killSize * 2.2f : c.comboSize;
            float scale = base * pop * (l.kind == 0 && l.tier == 3 ? 1.2f : 1f);
            float hh = 0.55f * scale;
            float hw = hh * tx.w / (float) tx.h;
            float cx = (float) l.x + l.vx * age, cy = (float) l.y + 0.25f * age, cz = (float) l.z + l.vz * age;
            if (l.kind == 0 && l.tier == 3) {
                cx += (float) Math.sin(now * 0.05) * 0.015f;
                cy += (float) Math.cos(now * 0.07) * 0.015f;
            }
            float ca = (float) Math.cos(l.tilt), sa = (float) Math.sin(l.tilt);
            Vector3f rr = new Vector3f(right).mul(ca).add(new Vector3f(up).mul(sa));
            Vector3f uu = new Vector3f(up).mul(ca).sub(new Vector3f(right).mul(sa));
            int w = argb(0xFFFFFF, fade);
            texQuad(tx.outId, m, cx, cy, cz, rr, uu, hw * 0.5f, hh * 0.5f, new int[]{w, w, w, w});
            texQuad(tx.fillId, m, cx, cy, cz, rr, uu, hw * 0.5f, hh * 0.5f, labelCols(l, c, now, fade));
        }
        RenderSystem.setShaderTexture(0, prevTex);
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
    private static final float[] STX = new float[10], STY = new float[10];
    private static final float[] CCX = new float[48], CCY = new float[48];

    static {
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float r = (i % 2 == 0) ? 1f : 0.42f;
            STX[i] = (float) Math.cos(a) * r;
            STY[i] = (float) Math.sin(a) * r;
        }
        for (int i = 0; i < 48; i++) {
            CCX[i] = (float) Math.cos(i * Math.PI * 2 / 48);
            CCY[i] = (float) Math.sin(i * Math.PI * 2 / 48);
        }
    }

    /** Контур произвольной формы (сердце, звезда, круг) плоской полосой на земле. */
    private static void shapeBand(BufferBuilder b, Matrix4f m, Ring r, Config c, float[] sx, float[] sy, int n,
                                  float rad, float inner, float yaw, float a) {
        float cx = (float) r.x, cy = (float) r.y, cz = (float) r.z;
        float cs = (float) Math.cos(yaw), sn = (float) Math.sin(yaw);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            int rgb0 = r.rgb >= 0 ? r.rgb : hsb(c.circRainbow, c.circHue, i / (float) n);
            int rgb1 = r.rgb >= 0 ? r.rgb : hsb(c.circRainbow, c.circHue, j / (float) n);
            float ox0 = cx + sx[i] * rad * cs - sy[i] * rad * sn, oz0 = cz + sx[i] * rad * sn + sy[i] * rad * cs;
            float ox1 = cx + sx[j] * rad * cs - sy[j] * rad * sn, oz1 = cz + sx[j] * rad * sn + sy[j] * rad * cs;
            float ix0 = cx + sx[i] * rad * inner * cs - sy[i] * rad * inner * sn, iz0 = cz + sx[i] * rad * inner * sn + sy[i] * rad * inner * cs;
            float ix1 = cx + sx[j] * rad * inner * cs - sy[j] * rad * inner * sn, iz1 = cz + sx[j] * rad * inner * sn + sy[j] * rad * inner * cs;
            int in0 = argb(rgb0, a * 0.1f), out0 = argb(rgb0, a * 0.95f);
            int in1 = argb(rgb1, a * 0.1f), out1 = argb(rgb1, a * 0.95f);
            vtx(b, m, ix0, cy, iz0, in0);
            vtx(b, m, ox0, cy, oz0, out0);
            vtx(b, m, ox1, cy, oz1, out1);
            vtx(b, m, ix0, cy, iz0, in0);
            vtx(b, m, ox1, cy, oz1, out1);
            vtx(b, m, ix1, cy, iz1, in1);
        }
    }

    /** Линии пентаграммы {5/2}. */
    private static void pentaLines(BufferBuilder b, Matrix4f m, Ring r, Config c, float rad, float yaw, float a) {
        float y = (float) r.y;
        for (int k = 0; k < 5; k++) {
            int k2 = (k + 2) % 5;
            double a1 = yaw + (-Math.PI / 2 + k * 2 * Math.PI / 5), a2 = yaw + (-Math.PI / 2 + k2 * 2 * Math.PI / 5);
            float x1 = (float) (r.x + Math.cos(a1) * rad * 0.92f), z1 = (float) (r.z + Math.sin(a1) * rad * 0.92f);
            float x2 = (float) (r.x + Math.cos(a2) * rad * 0.92f), z2 = (float) (r.z + Math.sin(a2) * rad * 0.92f);
            float dx = x2 - x1, dz = z2 - z1;
            float l = (float) Math.sqrt(dx * dx + dz * dz);
            if (l < 1e-6f) continue;
            float nx = -dz / l * rad * 0.035f, nz = dx / l * rad * 0.035f;
            int rgb = r.rgb >= 0 ? r.rgb : hsb(c.circRainbow, c.circHue, k / 5f);
            int col = argb(rgb, a * 0.95f);
            vtx(b, m, x1 - nx, y, z1 - nz, col);
            vtx(b, m, x1 + nx, y, z1 + nz, col);
            vtx(b, m, x2 + nx, y, z2 + nz, col);
            vtx(b, m, x1 - nx, y, z1 - nz, col);
            vtx(b, m, x2 + nx, y, z2 + nz, col);
            vtx(b, m, x2 - nx, y, z2 - nz, col);
        }
    }

    private static void drawRings(Matrix4f m, Config c, long now) {
        BufferBuilder b = begin(VertexFormat.DrawMode.TRIANGLES);
        for (Ring r : rings) {
            float t = (now - r.t0) / (float) r.dur;
            if (t < 0f || t > 1f) continue;
            float e = 1f - (1f - t) * (1f - t) * (1f - t);
            float rad = 0.2f + (r.maxR - 0.2f) * e;
            float a = 1f - t;
            if (!r.splash && r.shape == 1) {
                shapeBand(b, m, r, c, HX, HY, 24, rad, 0.78f, r.yaw, a);
                continue;
            }
            if (!r.splash && r.shape == 2) {
                shapeBand(b, m, r, c, STX, STY, 10, rad, 0.6f, r.yaw + t * 3f, a);
                continue;
            }
            if (!r.splash && r.shape == 3) {
                shapeBand(b, m, r, c, CCX, CCY, 48, rad, 0.93f, 0f, a);
                pentaLines(b, m, r, c, rad, r.yaw + t * 2f, a);
                continue;
            }
            float inner = rad * 0.8f;
            float y = (float) r.y;
            int seg = r.splash ? 20 : 48;
            for (int i = 0; i < seg; i++) {
                double a1 = i * Math.PI * 2 / seg, a2 = (i + 1) * Math.PI * 2 / seg;
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
                int rgb1 = r.rgb >= 0 ? r.rgb : hsb(c.circRainbow, c.circHue, i / (float) seg);
                int rgb2 = r.rgb >= 0 ? r.rgb : hsb(c.circRainbow, c.circHue, (i + 1) / (float) seg);
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
        end(b);
    }

    // ---------- лента ----------
    private static void drawRibbon(Matrix4f m, Config c, double px, double py, double pz, long now, Vec3d cam) {
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
            // рядом с камерой лента плавно исчезает (не лезет в обзор от первого лица)
            double dxc = x - cam.x, dyc = y + h * 0.5 - cam.y, dzc = z - cam.z;
            float dist = (float) Math.sqrt(dxc * dxc + dyc * dyc + dzc * dzc);
            a *= Math.max(0f, Math.min(1f, (dist - 0.9f) / 1.3f));
            a = Math.max(0f, Math.min(1f, a));
            int rgb = hsb(c.trailRainbow, c.trailHue, i * 0.03f);
            b.vertex(m, (float) x, (float) y + 0.05f, (float) z).color(argb(rgb, a * 0.9f));
            b.vertex(m, (float) x, (float) (y + h), (float) z).color(argb(rgb, a * 0.1f));
        }
        end(b);
    }

    // ---------- новые эффекты убийства: молот, пентаграмма, ядерный взрыв ----------
    private static void quadW(BufferBuilder b, Matrix4f m, float[] p0, float[] p1, float[] p2, float[] p3, int rgb, float a) {
        tri3(b, m, p0, p1, p2, rgb, a);
        tri3(b, m, p0, p2, p3, rgb, a);
    }

    private static void boxW(BufferBuilder b, Matrix4f m, float cx, float cy, float cz, float[] ux, float[] uy, float[] uz, int rgb, float a) {
        float[][] p = new float[8][];
        for (int i = 0; i < 8; i++) {
            float sx = (i & 1) == 0 ? -1 : 1, sy = (i & 2) == 0 ? -1 : 1, sz = (i & 4) == 0 ? -1 : 1;
            p[i] = new float[]{cx + sx * ux[0] + sy * uy[0] + sz * uz[0], cy + sx * ux[1] + sy * uy[1] + sz * uz[1],
                    cz + sx * ux[2] + sy * uy[2] + sz * uz[2]};
        }
        int[][] f = {{0, 2, 6, 4}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 5, 7, 6}};
        for (int[] q : f) quadW(b, m, p[q[0]], p[q[1]], p[q[2]], p[q[3]], rgb, a);
    }

    private static void drawKillFx(BufferBuilder b, Matrix4f m, Config c, KillFx k, long now) {
        if (k.type == 3) drawHammer(b, m, c, k, now);
        else if (k.type == 4) drawHell(b, m, c, k, now);
        else drawNuke(b, m, c, k, now);
    }

    private static void drawHammer(BufferBuilder b, Matrix4f m, Config c, KillFx k, long now) {
        float t = (now - k.t0) / 1000f;
        if (t < 0f || t > 2.2f) return;
        float S = 0.6f * c.killSize;
        float H;
        if (t < 0.5f) {
            H = 7f;
        } else if (t < 0.9f) {
            float u = (t - 0.5f) / 0.4f;
            H = 7f * (1f - u * u);
        } else {
            float kk = t - 0.9f;
            H = 0.18f * (float) (Math.exp(-9 * kk) * Math.abs(Math.sin(kk * 18)));
        }
        float alpha = t < 0.25f ? t / 0.25f : (t < 1.6f ? 1f : Math.max(0f, 1f - (t - 1.6f) / 0.6f));
        float ct = (float) Math.cos(TILT), st = (float) Math.sin(TILT);
        float ex = k.rx * ct - k.rz * st, ez = k.rx * st + k.rz * ct;
        float nx = -ez, nz = ex;
        float cx = (float) k.x, cz = (float) k.z;
        float hy = (float) k.y + 0.62f * S + H;
        float[] ux = {ex * 0.55f * S, 0f, ez * 0.55f * S};
        float[] uy = {0f, 0.62f * S, 0f};
        float[] uz = {nx * 0.6f * S, 0f, nz * 0.6f * S};
        float[] gx = {ex * 0.06f * S, 0f, ez * 0.06f * S};
        float[] uyb = {0f, 0.66f * S, 0f};
        float[] uzb = {nx * 0.64f * S, 0f, nz * 0.64f * S};
        int steel = 0x6C7078, gold = 0xC9A24A;

        // кровавое пятно под молотом после удара
        if (t >= 0.9f) {
            float rr = Math.min(1.4f, (t - 0.9f) * 5f) * S * 1.6f;
            float fa = Math.max(0f, 1f - Math.max(0f, t - 1.5f) / 0.7f) * 0.7f;
            for (int i = 0; i < 20; i++) {
                double a1 = i * Math.PI * 2 / 20, a2 = (i + 1) * Math.PI * 2 / 20;
                vtx(b, m, cx, (float) k.y + 0.03f, cz, argb(0x6A0A0A, fa));
                vtx(b, m, cx + (float) Math.cos(a1) * rr, (float) k.y + 0.03f, cz + (float) Math.sin(a1) * rr, argb(0x8A1010, fa * 0.6f));
                vtx(b, m, cx + (float) Math.cos(a2) * rr, (float) k.y + 0.03f, cz + (float) Math.sin(a2) * rr, argb(0x8A1010, fa * 0.6f));
            }
        }

        for (int side = -1; side <= 1; side += 2) {
            boxW(b, m, cx + ex * side * 0.95f * S, hy, cz + ez * side * 0.95f * S, ux, uy, uz, steel, alpha);
            boxW(b, m, cx + ex * side * 1.5f * S, hy, cz + ez * side * 1.5f * S, gx, uyb, uzb, gold, alpha);
            ellW(b, m, cx + nx * side * 0.68f * S, hy, cz + nz * side * 0.68f * S, 0.22f * S, 0.22f * S, 0.22f * S, 0xFF7A20, alpha);
        }
        cylW(b, m, cx - nx * 0.65f * S, hy, cz - nz * 0.65f * S, cx + nx * 0.65f * S, hy, cz + nz * 0.65f * S, 0.5f * S, gold, alpha);
        float topY = hy + 0.62f * S;
        cylW(b, m, cx, topY, cz, cx, topY + 4.5f * S, cz, 0.1f * S, 0x3A2A22, alpha);
        cylW(b, m, cx, topY, cz, cx, topY + 0.3f * S, cz, 0.15f * S, gold, alpha);
        cylW(b, m, cx, topY + 4.3f * S, cz, cx, topY + 4.6f * S, cz, 0.14f * S, gold, alpha);
    }

    private static void drawHell(BufferBuilder b, Matrix4f m, Config c, KillFx k, long now) {
        float t = (now - k.t0) / 1000f;
        if (t < 0f || t > 3.0f) return;
        float R = 2.2f * c.killSize;
        float x = (float) k.x, y = (float) k.y + 0.04f, z = (float) k.z;
        float fadeAll = t < 2.4f ? 1f : Math.max(0f, 1f - (t - 2.4f) / 0.6f);
        float pulse = 0.8f + 0.2f * (float) Math.sin(t * 12f);
        float in = Math.min(1f, t / 0.5f);

        int seg = 36;
        for (int i = 0; i < seg; i++) {
            double a1 = i * Math.PI * 2 / seg, a2 = (i + 1) * Math.PI * 2 / seg;
            vtx(b, m, x, y, z, argb(0xFF2008, 0.4f * in * fadeAll * pulse));
            vtx(b, m, x + (float) Math.cos(a1) * R * 1.15f, y, z + (float) Math.sin(a1) * R * 1.15f, argb(0xFF4018, 0f));
            vtx(b, m, x + (float) Math.cos(a2) * R * 1.15f, y, z + (float) Math.sin(a2) * R * 1.15f, argb(0xFF4018, 0f));
        }
        // круг дорисовывается дугой
        float frac = Math.min(1f, t / 0.9f);
        int rseg = 48, rdraw = (int) (rseg * frac);
        int rc = argb(0xFF3A18, 0.95f * fadeAll * pulse);
        for (int i = 0; i < rdraw; i++) {
            double a1 = i * Math.PI * 2 / rseg, a2 = (i + 1) * Math.PI * 2 / rseg;
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1), c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
            vtx(b, m, x + c1 * R * 0.94f, y + 0.01f, z + s1 * R * 0.94f, rc);
            vtx(b, m, x + c1 * R, y + 0.01f, z + s1 * R, rc);
            vtx(b, m, x + c2 * R, y + 0.01f, z + s2 * R, rc);
            vtx(b, m, x + c1 * R * 0.94f, y + 0.01f, z + s1 * R * 0.94f, rc);
            vtx(b, m, x + c2 * R, y + 0.01f, z + s2 * R, rc);
            vtx(b, m, x + c2 * R * 0.94f, y + 0.01f, z + s2 * R * 0.94f, rc);
        }
        // линии пентаграммы по очереди
        for (int kk = 0; kk < 5; kk++) {
            float p = Math.max(0f, Math.min(1f, (t - 0.15f - kk * 0.16f) / 0.28f));
            if (p <= 0f) continue;
            double a1 = -Math.PI / 2 + kk * 2 * Math.PI / 5, a2 = -Math.PI / 2 + ((kk + 2) % 5) * 2 * Math.PI / 5;
            float sx = x + (float) Math.cos(a1) * R * 0.94f, sz = z + (float) Math.sin(a1) * R * 0.94f;
            float fx = x + (float) Math.cos(a2) * R * 0.94f, fz = z + (float) Math.sin(a2) * R * 0.94f;
            float ex2 = sx + (fx - sx) * p, ez2 = sz + (fz - sz) * p;
            float dx = ex2 - sx, dz = ez2 - sz;
            float l = (float) Math.sqrt(dx * dx + dz * dz);
            if (l < 1e-5f) continue;
            for (int layer = 0; layer < 2; layer++) {
                float w = layer == 0 ? R * 0.045f : R * 0.02f;
                int col = argb(layer == 0 ? 0xFF8A20 : 0xFFE070, 0.95f * fadeAll * pulse);
                float nx = -dz / l * w, nz = dx / l * w;
                vtx(b, m, sx - nx, y + 0.02f, sz - nz, col);
                vtx(b, m, sx + nx, y + 0.02f, sz + nz, col);
                vtx(b, m, ex2 + nx, y + 0.02f, ez2 + nz, col);
                vtx(b, m, sx - nx, y + 0.02f, sz - nz, col);
                vtx(b, m, ex2 + nx, y + 0.02f, ez2 + nz, col);
                vtx(b, m, ex2 - nx, y + 0.02f, ez2 - nz, col);
            }
        }

        // извержение огня
        float te = t - 1.35f;
        if (te < 0f) return;
        float Hh = 16f * (1f - (float) Math.pow(1f - Math.min(1f, te / 0.3f), 3));
        float fade = (te < 1.0f ? 1f : Math.max(0f, 1f - (te - 1.0f) / 0.9f)) * fadeAll;
        float flick = 0.85f + 0.15f * (float) Math.sin(t * 38f);
        float[] radii = {0.9f, 1.5f, 2.1f};
        int[] cols = {0xFFE070, 0xFF8A20, 0xFF3010};
        float[] alphas = {0.7f, 0.45f, 0.22f};
        int segs = 14, sides = 14;
        float dyy = Hh / segs;
        for (int l = 0; l < 3; l++) {
            for (int j = 0; j < segs; j++) {
                float y0 = y + j * dyy, y1 = y0 + dyy;
                float f0 = j / (float) segs, f1 = (j + 1) / (float) segs;
                float r0 = radii[l] * c.killSize * (1f - 0.4f * f0) * (1f + 0.18f * (float) Math.sin(y0 * 0.7f - t * 22f + l * 1.3f));
                float r1 = radii[l] * c.killSize * (1f - 0.4f * f1) * (1f + 0.18f * (float) Math.sin(y1 * 0.7f - t * 22f + l * 1.3f));
                int ca = argb(cols[l], alphas[l] * fade * flick * (1f - 0.8f * f0));
                int cb = argb(cols[l], alphas[l] * fade * flick * (1f - 0.8f * f1));
                for (int q = 0; q < sides; q++) {
                    double a1 = q * Math.PI * 2 / sides, a2 = (q + 1) * Math.PI * 2 / sides;
                    float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1), c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
                    vtx(b, m, x + c1 * r0, y0, z + s1 * r0, ca);
                    vtx(b, m, x + c2 * r0, y0, z + s2 * r0, ca);
                    vtx(b, m, x + c2 * r1, y1, z + s2 * r1, cb);
                    vtx(b, m, x + c1 * r0, y0, z + s1 * r0, ca);
                    vtx(b, m, x + c2 * r1, y1, z + s2 * r1, cb);
                    vtx(b, m, x + c1 * r1, y1, z + s1 * r1, cb);
                }
            }
        }
        for (int q = 0; q < 8; q++) {
            for (int j = 0; j < segs; j++) {
                float y0 = y + j * dyy, y1 = y0 + dyy;
                float f0 = j / (float) segs, f1 = (j + 1) / (float) segs;
                float ang0 = y0 * 1.1f + t * 8f + q * 0.785f, ang1 = y1 * 1.1f + t * 8f + q * 0.785f;
                float rad0 = 1.2f * c.killSize * (1f - 0.35f * f0), rad1 = 1.2f * c.killSize * (1f - 0.35f * f1);
                float w = 0.16f;
                float x0 = x + (float) Math.cos(ang0) * rad0, z0 = z + (float) Math.sin(ang0) * rad0;
                float x1 = x + (float) Math.cos(ang1) * rad1, z1 = z + (float) Math.sin(ang1) * rad1;
                float tx0 = -(float) Math.sin(ang0) * w, tz0 = (float) Math.cos(ang0) * w;
                float tx1 = -(float) Math.sin(ang1) * w, tz1 = (float) Math.cos(ang1) * w;
                int col0 = argb(q % 2 == 0 ? 0xFFD040 : 0xFF5018, 0.8f * fade * (1f - f0 * 0.9f));
                int col1 = argb(q % 2 == 0 ? 0xFFD040 : 0xFF5018, 0.8f * fade * (1f - f1 * 0.9f));
                vtx(b, m, x0 - tx0, y0, z0 - tz0, col0);
                vtx(b, m, x0 + tx0, y0, z0 + tz0, col0);
                vtx(b, m, x1 + tx1, y1, z1 + tz1, col1);
                vtx(b, m, x0 - tx0, y0, z0 - tz0, col0);
                vtx(b, m, x1 + tx1, y1, z1 + tz1, col1);
                vtx(b, m, x1 - tx1, y1, z1 - tz1, col1);
            }
        }
    }

    /** Эллипсоид с градиентом цвета снизу вверх и прозрачностью (без освещения). */
    private static void ellC(BufferBuilder b, Matrix4f m, float cx, float cy, float cz, float rx, float ry, float rz,
                             int cBot, int cTop, float a) {
        int lat = 8, lon = 14;
        for (int i = 0; i < lat; i++) {
            double p0 = -Math.PI / 2 + i * Math.PI / lat, p1 = -Math.PI / 2 + (i + 1) * Math.PI / lat;
            int col0 = argb(mixc(cBot, cTop, (float) (Math.sin(p0) + 1) / 2f), a);
            int col1 = argb(mixc(cBot, cTop, (float) (Math.sin(p1) + 1) / 2f), a);
            for (int j = 0; j < lon; j++) {
                double t0 = j * Math.PI * 2 / lon, t1 = (j + 1) * Math.PI * 2 / lon;
                vtx(b, m, cx + rx * (float) (Math.cos(p0) * Math.cos(t0)), cy + ry * (float) Math.sin(p0), cz + rz * (float) (Math.cos(p0) * Math.sin(t0)), col0);
                vtx(b, m, cx + rx * (float) (Math.cos(p0) * Math.cos(t1)), cy + ry * (float) Math.sin(p0), cz + rz * (float) (Math.cos(p0) * Math.sin(t1)), col0);
                vtx(b, m, cx + rx * (float) (Math.cos(p1) * Math.cos(t1)), cy + ry * (float) Math.sin(p1), cz + rz * (float) (Math.cos(p1) * Math.sin(t1)), col1);
                vtx(b, m, cx + rx * (float) (Math.cos(p0) * Math.cos(t0)), cy + ry * (float) Math.sin(p0), cz + rz * (float) (Math.cos(p0) * Math.sin(t0)), col0);
                vtx(b, m, cx + rx * (float) (Math.cos(p1) * Math.cos(t1)), cy + ry * (float) Math.sin(p1), cz + rz * (float) (Math.cos(p1) * Math.sin(t1)), col1);
                vtx(b, m, cx + rx * (float) (Math.cos(p1) * Math.cos(t0)), cy + ry * (float) Math.sin(p1), cz + rz * (float) (Math.cos(p1) * Math.sin(t0)), col1);
            }
        }
    }

    private static void tubeC(BufferBuilder b, Matrix4f m, float x, float y0, float z, float h, float rBot, float rTop,
                              int cBot, int cTop, float a, float t) {
        int rows = 10, sides = 12;
        for (int i = 0; i < rows; i++) {
            float f0 = i / (float) rows, f1 = (i + 1) / (float) rows;
            float ya = y0 + h * f0, yb = y0 + h * f1;
            float ra = (rBot + (rTop - rBot) * f0) * (1f + 0.1f * (float) Math.sin(f0 * 9f - t * 6f));
            float rb = (rBot + (rTop - rBot) * f1) * (1f + 0.1f * (float) Math.sin(f1 * 9f - t * 6f));
            int ca = argb(mixc(cBot, cTop, f0), a * (1f - 0.2f * f0)), cb = argb(mixc(cBot, cTop, f1), a * (1f - 0.2f * f1));
            for (int q = 0; q < sides; q++) {
                double a1 = q * Math.PI * 2 / sides, a2 = (q + 1) * Math.PI * 2 / sides;
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1), c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
                vtx(b, m, x + c1 * ra, ya, z + s1 * ra, ca);
                vtx(b, m, x + c2 * ra, ya, z + s2 * ra, ca);
                vtx(b, m, x + c2 * rb, yb, z + s2 * rb, cb);
                vtx(b, m, x + c1 * ra, ya, z + s1 * ra, ca);
                vtx(b, m, x + c2 * rb, yb, z + s2 * rb, cb);
                vtx(b, m, x + c1 * rb, yb, z + s1 * rb, cb);
            }
        }
    }

    private static void drawNuke(BufferBuilder b, Matrix4f m, Config c, KillFx k, long now) {
        float t = (now - k.t0) / 1000f;
        if (t < 0f || t > 3.6f) return;
        float S = c.killSize;
        float x = (float) k.x, y = (float) k.y + 0.05f, z = (float) k.z;
        float fade = t < 2.8f ? 1f : Math.max(0f, 1f - (t - 2.8f) / 0.8f);

        // вспышка
        if (t < 0.4f) {
            float fa = 0.9f * (1f - t / 0.4f);
            for (int i = 0; i < 28; i++) {
                double a1 = i * Math.PI * 2 / 28, a2 = (i + 1) * Math.PI * 2 / 28;
                vtx(b, m, x, y + 0.05f, z, argb(0xFFFFE0, fa));
                vtx(b, m, x + (float) Math.cos(a1) * 4f * S, y + 0.05f, z + (float) Math.sin(a1) * 4f * S, argb(0xFFC060, 0f));
                vtx(b, m, x + (float) Math.cos(a2) * 4f * S, y + 0.05f, z + (float) Math.sin(a2) * 4f * S, argb(0xFFC060, 0f));
            }
        }
        // огненный шар
        float eo = 1f - (float) Math.pow(1f - Math.min(1f, t / 0.5f), 3);
        float rf = 2.0f * S * eo;
        float fs = Math.max(0f, Math.min(1f, (t - 0.9f) / 0.9f));
        float fa = 0.8f * (1f - fs * fs * (3f - 2f * fs)) * fade;
        if (fa > 0.01f && rf > 0.02f) ellC(b, m, x, y + rf * 0.75f, z, rf, rf * 0.9f, rf, 0xFF7A18, 0xFFE878, fa);
        // ножка гриба
        float hs = 7f * S * (1f - (float) Math.pow(1f - Math.min(1f, t / 1.4f), 2));
        if (hs > 0.05f) tubeC(b, m, x, y, z, hs, 0.7f * S, 0.45f * S, 0xFF6A14, 0x555048, 0.75f * fade, t);
        // шапка и юбка
        if (t > 0.4f) {
            float ec = 1f - (float) Math.pow(1f - Math.min(1f, (t - 0.4f) / 1.3f), 2);
            float cr = 2.4f * S * ec;
            float cy = y + hs + 0.1f * S;
            if (cr > 0.02f) {
                ellC(b, m, x, cy, z, cr, cr * 0.6f, cr, 0xFF7A28, 0x5A544C, 0.85f * fade);
                ellC(b, m, x, cy - cr * 0.25f, z, cr * 1.25f, cr * 0.2f, cr * 1.25f, 0xFF5A14, 0x6A3A20, 0.55f * fade);
            }
        }
        // пыль по земле
        float er = 1f - (float) Math.pow(1f - Math.min(1f, t / 0.9f), 3);
        float dr = 6.5f * S * er;
        float da = 0.5f * Math.max(0f, 1f - t / 1.6f) * fade;
        if (da > 0.01f && dr > 0.1f) {
            for (int i = 0; i < 40; i++) {
                double a1 = i * Math.PI * 2 / 40, a2 = (i + 1) * Math.PI * 2 / 40;
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1), c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
                vtx(b, m, x + c1 * dr * 0.8f, y + 0.04f, z + s1 * dr * 0.8f, argb(0xB8AA98, 0f));
                vtx(b, m, x + c1 * dr, y + 0.04f, z + s1 * dr, argb(0xB8AA98, da));
                vtx(b, m, x + c2 * dr, y + 0.04f, z + s2 * dr, argb(0xB8AA98, da));
                vtx(b, m, x + c1 * dr * 0.8f, y + 0.04f, z + s1 * dr * 0.8f, argb(0xB8AA98, 0f));
                vtx(b, m, x + c2 * dr, y + 0.04f, z + s2 * dr, argb(0xB8AA98, da));
                vtx(b, m, x + c2 * dr * 0.8f, y + 0.04f, z + s2 * dr * 0.8f, argb(0xB8AA98, 0f));
            }
        }
    }

    // ---------- Sun Strike (как у Инвокера) ----------
    private static void drawStrike(BufferBuilder b, Matrix4f m, Strike s, long now) {
        float t = (now - s.t0) / 1000f;
        if (t < 0f || t > 2.4f) return;
        float x = (float) s.x, y = (float) s.y + 0.04f, z = (float) s.z;

        // 1) земля краснеет и пульсирует
        float glow = t < 1.0f ? t : Math.max(0f, 1f - (t - 1.0f) / 1.2f);
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * 14f);
        float gr = (1.1f + 0.7f * Math.min(1f, t)) * pulse;
        int seg = 28;
        for (int i = 0; i < seg; i++) {
            double a1 = i * Math.PI * 2 / seg, a2 = (i + 1) * Math.PI * 2 / seg;
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1), c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
            vtx(b, m, x, y, z, argb(0xFF3010, 0.55f * glow));
            vtx(b, m, x + c1 * gr, y, z + s1 * gr, argb(0xFF6A18, 0f));
            vtx(b, m, x + c2 * gr, y, z + s2 * gr, argb(0xFF6A18, 0f));
            // контур круга
            float ri = gr * 0.9f;
            int cin = argb(0xFF5A18, 0f), cout = argb(0xFF5A18, 0.7f * glow);
            vtx(b, m, x + c1 * ri, y + 0.01f, z + s1 * ri, cin);
            vtx(b, m, x + c1 * gr, y + 0.01f, z + s1 * gr, cout);
            vtx(b, m, x + c2 * gr, y + 0.01f, z + s2 * gr, cout);
            vtx(b, m, x + c1 * ri, y + 0.01f, z + s1 * ri, cin);
            vtx(b, m, x + c2 * gr, y + 0.01f, z + s2 * gr, cout);
            vtx(b, m, x + c2 * ri, y + 0.01f, z + s2 * ri, cin);
        }

        // 2) луч падает с неба
        float drop = (t - 1.0f) / 0.14f;
        if (drop < 0f) return;
        float top = y + 45f;
        float head = drop >= 1f ? y : top - (top - y) * drop;
        float fade = t < 1.6f ? 1f : Math.max(0f, 1f - (t - 1.6f) / 0.8f);
        float flick = 0.85f + 0.15f * (float) Math.sin(t * 40f);
        float[] radii = {0.28f, 0.55f, 0.95f, 1.5f};
        int[] cols = {0xFFF6B0, 0xFFC020, 0xFF5A10, 0xFF2A08};
        float[] alphas = {0.95f, 0.55f, 0.28f, 0.12f};
        int segs = Math.max(1, Math.min(30, (int) Math.ceil((top - head) / 1.5f)));
        float dy = (top - head) / segs;
        int sides = 12;
        for (int l = 0; l < 4; l++) {
            for (int j = 0; j < segs; j++) {
                float y0 = head + j * dy, y1 = y0 + dy;
                float f0 = (y0 - head) / Math.max(0.01f, top - head), f1 = (y1 - head) / Math.max(0.01f, top - head);
                float r0 = radii[l] * (1f + 0.12f * (float) Math.sin(y0 * 0.6f - t * 25f + l));
                float r1 = radii[l] * (1f + 0.12f * (float) Math.sin(y1 * 0.6f - t * 25f + l));
                int ca = argb(cols[l], alphas[l] * fade * flick * (1f - f0 * 0.85f));
                int cb = argb(cols[l], alphas[l] * fade * flick * (1f - f1 * 0.85f));
                for (int k = 0; k < sides; k++) {
                    double a1 = k * Math.PI * 2 / sides, a2 = (k + 1) * Math.PI * 2 / sides;
                    float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1), c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
                    vtx(b, m, x + c1 * r0, y0, z + s1 * r0, ca);
                    vtx(b, m, x + c2 * r0, y0, z + s2 * r0, ca);
                    vtx(b, m, x + c2 * r1, y1, z + s2 * r1, cb);
                    vtx(b, m, x + c1 * r0, y0, z + s1 * r0, ca);
                    vtx(b, m, x + c2 * r1, y1, z + s2 * r1, cb);
                    vtx(b, m, x + c1 * r1, y1, z + s1 * r1, cb);
                }
            }
        }

        // 3) спиральные языки пламени вокруг луча
        for (int k = 0; k < 6; k++) {
            for (int j = 0; j < segs; j++) {
                float y0 = head + j * dy, y1 = y0 + dy;
                float f0 = (y0 - head) / Math.max(0.01f, top - head), f1 = (y1 - head) / Math.max(0.01f, top - head);
                float ang0 = y0 * 1.3f + t * 9f + k * 1.0472f, ang1 = y1 * 1.3f + t * 9f + k * 1.0472f;
                float rad0 = 0.6f + 0.1f * (float) Math.sin(y0 * 0.8f - t * 12f), rad1 = 0.6f + 0.1f * (float) Math.sin(y1 * 0.8f - t * 12f);
                float w = 0.12f;
                float x0 = x + (float) Math.cos(ang0) * rad0, z0 = z + (float) Math.sin(ang0) * rad0;
                float x1 = x + (float) Math.cos(ang1) * rad1, z1 = z + (float) Math.sin(ang1) * rad1;
                float tx0 = -(float) Math.sin(ang0) * w, tz0 = (float) Math.cos(ang0) * w;
                float tx1 = -(float) Math.sin(ang1) * w, tz1 = (float) Math.cos(ang1) * w;
                int ca = argb(k % 2 == 0 ? 0xFFE040 : 0xFF7018, 0.8f * fade * (1f - f0 * 0.9f));
                int cb = argb(k % 2 == 0 ? 0xFFE040 : 0xFF7018, 0.8f * fade * (1f - f1 * 0.9f));
                vtx(b, m, x0 - tx0, y0, z0 - tz0, ca);
                vtx(b, m, x0 + tx0, y0, z0 + tz0, ca);
                vtx(b, m, x1 + tx1, y1, z1 + tz1, cb);
                vtx(b, m, x0 - tx0, y0, z0 - tz0, ca);
                vtx(b, m, x1 + tx1, y1, z1 + tz1, cb);
                vtx(b, m, x1 - tx1, y1, z1 - tz1, cb);
            }
        }

        // 4) вспышка у земли в момент удара
        float fl = 1f - Math.min(1f, (t - 1.14f) / 0.35f);
        if (t >= 1.1f && fl > 0f) {
            float rr = 2.6f;
            for (int i = 0; i < seg; i++) {
                double a1 = i * Math.PI * 2 / seg, a2 = (i + 1) * Math.PI * 2 / seg;
                vtx(b, m, x, y + 0.06f, z, argb(0xFFF0A0, 0.9f * fl));
                vtx(b, m, x + (float) Math.cos(a1) * rr, y + 0.06f, z + (float) Math.sin(a1) * rr, argb(0xFFA020, 0f));
                vtx(b, m, x + (float) Math.cos(a2) * rr, y + 0.06f, z + (float) Math.sin(a2) * rr, argb(0xFFA020, 0f));
            }
        }
    }

    // ---------- крылья ----------
    private static float[] wp(float ox, float oy, float oz, float ax, float az, float bx, float bz, float flap, float u, float v) {
        float cf = (float) Math.cos(flap), sf = (float) Math.sin(flap);
        float outx = ax * cf, outy = sf, outz = az * cf;
        float upx = -ax * sf, upy = cf, upz = -az * sf;
        float back = 0.18f * u;
        return new float[]{ox + outx * u + upx * v + bx * back, oy + outy * u + upy * v, oz + outz * u + upz * v + bz * back};
    }

    private static void wtri(BufferBuilder b, Matrix4f m, float[] p0, float[] p1, float[] p2, int c0, int c1, int c2) {
        vtx(b, m, p0[0], p0[1], p0[2], c0);
        vtx(b, m, p1[0], p1[1], p1[2], c1);
        vtx(b, m, p2[0], p2[1], p2[2], c2);
    }

    private static void wingStrip(BufferBuilder b, Matrix4f m, float ox, float oy, float oz, float ax, float az, float bx, float bz,
                                  float flap, float u0, float v0, float u1, float v1, float w, int c0, int c1) {
        float dx = u1 - u0, dy = v1 - v0;
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-6f) return;
        float px = -dy / l * w, py = dx / l * w;
        float[] a0 = wp(ox, oy, oz, ax, az, bx, bz, flap, u0 - px, v0 - py), a1 = wp(ox, oy, oz, ax, az, bx, bz, flap, u0 + px, v0 + py);
        float[] b0 = wp(ox, oy, oz, ax, az, bx, bz, flap, u1 - px, v1 - py), b1 = wp(ox, oy, oz, ax, az, bx, bz, flap, u1 + px, v1 + py);
        wtri(b, m, a0, a1, b1, c0, c0, c1);
        wtri(b, m, a0, b1, b0, c0, c1, c1);
    }

    private static void wing(BufferBuilder b, Matrix4f m, Config c, float ox, float oy, float oz, float ax, float az,
                             float bx, float bz, float flap, float size, float t) {
        int style = c.wingStyle;
        float spd = c.wingSpeed;
        float rootU = 0.08f;
        if (style == 0) {
            // ангельские перья
            int n = 9;
            for (int k = 0; k < n; k++) {
                float f = k / (float) (n - 1);
                float ang = (float) Math.toRadians(35 - 95 * f) + 0.06f * (float) Math.sin(t * spd * 6.2832f - f * 2f);
                float len = size * (0.55f + 0.75f * (float) Math.sin(Math.PI * (0.15 + 0.85 * f)));
                float wd = 0.1f * size;
                int rgb = c.wingRainbow ? hsb(true, 0f, f * 0.6f) : mixWhite(hsb(false, c.wingHue, 0f), 0.6f);
                float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang), px = -dy, py = dx;
                float[] rl = wp(ox, oy, oz, ax, az, bx, bz, flap, rootU - px * wd * 0.3f, -py * wd * 0.3f);
                float[] rr = wp(ox, oy, oz, ax, az, bx, bz, flap, rootU + px * wd * 0.3f, py * wd * 0.3f);
                float mu = rootU + dx * len * 0.55f, mv = dy * len * 0.55f;
                float[] ml = wp(ox, oy, oz, ax, az, bx, bz, flap, mu - px * wd, mv - py * wd);
                float[] mr = wp(ox, oy, oz, ax, az, bx, bz, flap, mu + px * wd, mv + py * wd);
                float[] tp = wp(ox, oy, oz, ax, az, bx, bz, flap, rootU + dx * len, dy * len);
                int cr = argb(rgb, 0.9f), cm = argb(rgb, 0.75f), ct = argb(mixWhite(rgb, 0.4f), 0.3f);
                wtri(b, m, rl, rr, mr, cr, cr, cm);
                wtri(b, m, rl, mr, ml, cr, cm, cm);
                wtri(b, m, ml, mr, tp, cm, cm, ct);
            }
        } else if (style == 1 || style == 2) {
            // демонические (перепонка на костях) и неоновые (светящийся каркас)
            float[] angs = {50f, 15f, -20f, -55f};
            float[] lens = {1.0f, 1.3f, 1.2f, 0.85f};
            float[][] tips = new float[4][];
            float[][] tipUV = new float[4][];
            for (int k = 0; k < 4; k++) {
                float ang = (float) Math.toRadians(angs[k]) + 0.05f * (float) Math.sin(t * spd * 6.2832f - k);
                float tu = rootU + (float) Math.cos(ang) * lens[k] * size, tv = (float) Math.sin(ang) * lens[k] * size;
                tipUV[k] = new float[]{tu, tv};
                tips[k] = wp(ox, oy, oz, ax, az, bx, bz, flap, tu, tv);
            }
            float[] root = wp(ox, oy, oz, ax, az, bx, bz, flap, rootU, 0f);
            int base = c.wingRainbow ? hsb(true, 0f, 0f) : hsb(false, c.wingHue, 0f);
            int mem = style == 1 ? mixc(base, 0x000000, 0.55f) : base;
            float memA = style == 1 ? 0.55f : 0.18f;
            for (int k = 0; k < 3; k++) {
                wtri(b, m, root, tips[k], tips[k + 1], argb(mem, memA * 0.3f), argb(mem, memA), argb(mem, memA));
            }
            for (int k = 0; k < 4; k++) {
                if (style == 1) {
                    int bone = argb(0xD8C8B0, 0.95f);
                    wingStrip(b, m, ox, oy, oz, ax, az, bx, bz, flap, rootU, 0f, tipUV[k][0], tipUV[k][1], 0.045f * size, bone, bone);
                } else {
                    int glow = argb(base, 0.2f), core = argb(mixWhite(base, 0.5f), 0.95f);
                    wingStrip(b, m, ox, oy, oz, ax, az, bx, bz, flap, rootU, 0f, tipUV[k][0], tipUV[k][1], 0.09f * size, glow, glow);
                    wingStrip(b, m, ox, oy, oz, ax, az, bx, bz, flap, rootU, 0f, tipUV[k][0], tipUV[k][1], 0.025f * size, core, core);
                }
            }
        } else {
            // бабочка: две доли с радужным переливом
            for (int lobe = 0; lobe < 2; lobe++) {
                int na = 7;
                float a0 = lobe == 0 ? 5f : -75f, a1 = lobe == 0 ? 80f : -5f;
                float lm = lobe == 0 ? 1.2f : 0.85f;
                float[][] tp = new float[na][];
                int[] cc = new int[na];
                for (int k = 0; k < na; k++) {
                    float f = k / (float) (na - 1);
                    float ang = (float) Math.toRadians(a0 + (a1 - a0) * f) + 0.04f * (float) Math.sin(t * spd * 6.2832f - f * 3f);
                    float len = size * lm * (0.75f + 0.25f * (float) Math.sin(Math.PI * f));
                    tp[k] = wp(ox, oy, oz, ax, az, bx, bz, flap, rootU + (float) Math.cos(ang) * len, (float) Math.sin(ang) * len);
                    cc[k] = argb(hsb(true, c.wingHue, f * 0.6f + lobe * 0.3f), 0.8f);
                }
                float[] root = wp(ox, oy, oz, ax, az, bx, bz, flap, rootU, 0f);
                int rc = argb(hsb(c.wingRainbow, c.wingHue, lobe * 0.3f), 0.5f);
                for (int k = 0; k < na - 1; k++) wtri(b, m, root, tp[k], tp[k + 1], rc, cc[k], cc[k + 1]);
            }
        }
    }

    private static void drawWings(Matrix4f m, Config c, ClientPlayerEntity p, double px, double py, double pz, float td, long now) {
        float yaw = (float) Math.toRadians(MathHelper.lerpAngleDegrees(td, p.prevBodyYaw, p.bodyYaw));
        float rxv = (float) -Math.cos(yaw), rzv = (float) -Math.sin(yaw);
        float bx = (float) Math.sin(yaw), bz = (float) -Math.cos(yaw);
        float shoulder = p.isSneaking() ? 1.05f : 1.35f;
        float ox = (float) px + bx * 0.2f, oy = (float) py + shoulder, oz = (float) pz + bz * 0.2f;
        boolean air = !p.isOnGround();
        float t = (now % 1000000L) / 1000f;
        float speed = c.wingSpeed * (air ? 2.6f : 1f);
        float amp = air ? 32f : 16f;
        float flap = (float) Math.toRadians(14f + amp * Math.sin(t * speed * 6.2832f));
        BufferBuilder b = begin(VertexFormat.DrawMode.TRIANGLES);
        for (int side = -1; side <= 1; side += 2) {
            wing(b, m, c, ox, oy, oz, rxv * side, rzv * side, bx, bz, flap, c.wingSize, t);
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
        b.vertex(m, (float) px, apexY, (float) pz).color(argb(hsb(c.hatRainbow, c.hatHue, 0f), 0.85f));
        for (int i = 0; i <= seg; i++) {
            double ang = i * Math.PI * 2 / seg;
            int rgb = hsb(c.hatRainbow, c.hatHue, i / (float) seg);
            b.vertex(m, (float) px + (float) Math.cos(ang) * rad, baseY, (float) pz + (float) Math.sin(ang) * rad)
                    .color(argb(rgb, 0.4f));
        }
        end(b);

        BufferBuilder o = begin(VertexFormat.DrawMode.TRIANGLE_STRIP);
        for (int i = 0; i <= seg; i++) {
            double ang = i * Math.PI * 2 / seg;
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            int rgb = hsb(c.hatRainbow, c.hatHue, i / (float) seg);
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
            int rgb = hsb(c.haloRainbow, c.haloHue, i / (float) seg);
            b.vertex(m, (float) px + cos * ri, y, (float) pz + sin * ri).color(argb(rgb, 0.95f));
            b.vertex(m, (float) px + cos * ro, y, (float) pz + sin * ro).color(argb(rgb, 0.95f));
        }
        end(b);
    }
}
