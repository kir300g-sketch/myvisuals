package com.example.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
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
        final float maxR;
        final int rgb; // -1 = градиент цвета кругов
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
        double x, y, z;
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
        if (c.killEnabled && validKillTarget(le, c)) addWatch(le, now);

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
            for (Label l : labels) if (l.kind == 0 && l.tier >= 2) spawnFlames(l, c, now);
        }

        tickWatches(c, now);
        tickScythes(c, now);
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
        if (!(w.e instanceof TestPlayer.Dummy)) {
            c.killCount++;
            Config.save();
        }
        if (c.killFx == 1) {
            Scythe s = new Scythe();
            s.x = w.x;
            s.y = w.y;
            s.z = w.z;
            s.t0 = now;
            scythes.add(s);
        } else if (c.killCounter) {
            spawnKillLabel(w.x, w.y + w.h * 0.6, w.z, now);
        }
    }

    private static void tickScythes(Config c, long now) {
        Iterator<Scythe> it = scythes.iterator();
        while (it.hasNext()) {
            Scythe s = it.next();
            long age = now - s.t0;
            if (!s.impacted && age >= 900) {
                s.impacted = true;
                killBurst(s.x, s.y + 0.4, s.z, c);
                if (c.killCounter) spawnKillLabel(s.x, s.y + 1.0, s.z, now);
            }
            if (age > 2500) it.remove();
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
        boolean anything = c.ribbonEnabled || c.jumpEnabled || c.hatEnabled || c.haloEnabled || c.trajEnabled
                || !drops.isEmpty() || !rings.isEmpty() || !sparks.isEmpty() || !labels.isEmpty()
                || !decals.isEmpty() || !scythes.isEmpty();
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

        Quaternionf q = ctx.camera().getRotation();
        Vector3f right = new Vector3f(1, 0, 0).rotate(q);
        Vector3f up = new Vector3f(0, 1, 0).rotate(q);
        Vector3f rh = new Vector3f(right.x, 0, right.z);
        if (rh.lengthSquared() > 1e-6f) rh.normalize();
        else rh.set(1, 0, 0);

        if (c.ribbonEnabled) drawRibbon(m, c, px, py, pz, now);
        if (!rings.isEmpty()) drawRings(m, c, now);
        if (c.trajEnabled) drawTrajectory(m, c, p, mc, td, px, py, pz, cp, right, up);
        double top = py + p.getHeight();
        if (c.hatEnabled && !first) drawHat(m, c, px, top, pz);
        if (c.haloEnabled && !first) drawHalo(m, c, px, top, pz, now);
        if (!drops.isEmpty()) drawDrops(m, c, td, right, up, rh);
        if (!sparks.isEmpty() || !decals.isEmpty() || !scythes.isEmpty()) {
            drawFx(m, c, td, now, right, up, rh, cp);
        }
        if (!labels.isEmpty()) drawLabels(m, c, now, right, up);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        ms.pop();
    }

    /** Небо: рисуется до сущностей и частиц, не перекрывает их и не трогает их состояние. */
    public static void renderSky(WorldRenderContext ctx) {
        Config c = Config.I;
        if (!c.skyEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        MatrixStack ms = ctx.matrixStack();
        if (ms == null) return;
        Vec3d cp = ctx.camera().getPos();
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
        ms.pop();
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
    private static final float SWING = 0.9f;
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

    /** Локальная точка в мировые координаты: базовый наклон, затем вращение вокруг хвата. */
    private static float[] w3(float ox, float oy, float oz, float rhx, float rhz, float S,
                              float px, float py, float pz, float ang, float yoff) {
        float bx = px * CB - py * SB, by = px * SB + py * CB;
        float dx = bx - HPX, dy = by - HPY;
        float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
        float rx = HPX + dx * ca - dy * sa, ry = HPY + dx * sa + dy * ca;
        return new float[]{ox + rhx * rx * S - rhz * pz * S, oy + ry * S + yoff, oz + rhz * rx * S + rhx * pz * S};
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

        // череп на основании лезвия
        int bone = 0xE8D58A;
        float sx = lx(125), sy = ly(78);
        ellipsoid(b, m, x, y, z, rhx, rhz, S, ang, yoff, sx, sy, 0f, 0.33f, 0.27f, 0.26f, bone, a);
        ellipsoid(b, m, x, y, z, rhx, rhz, S, ang, yoff, sx + 0.03f, sy - 0.27f, 0f, 0.2f, 0.12f, 0.18f, bone, a);
        float[][] eyes = {{lx(112), ly(75)}, {lx(137), ly(80)}};
        for (float[] e : eyes) {
            for (int side = -1; side <= 1; side += 2) {
                float zz = side * 0.265f;
                float[] q0 = w3(x, y, z, rhx, rhz, S, e[0] - 0.07f, e[1] - 0.08f, zz, ang, yoff);
                float[] q1 = w3(x, y, z, rhx, rhz, S, e[0] + 0.07f, e[1] - 0.08f, zz, ang, yoff);
                float[] q2 = w3(x, y, z, rhx, rhz, S, e[0] + 0.07f, e[1] + 0.08f, zz, ang, yoff);
                float[] q3 = w3(x, y, z, rhx, rhz, S, e[0] - 0.07f, e[1] + 0.08f, zz, ang, yoff);
                tri3(b, m, q0, q1, q2, 0x2A2410, a);
                tri3(b, m, q0, q2, q3, 0x2A2410, a);
            }
        }

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

    private static float[] gp(float ox, float oy, float oz, float rhx, float rhz, float S, float[] pt, float ang, float yoff) {
        float cx = lx(80), cy = ly(190);
        return w3(ox, oy, oz, rhx, rhz, S, cx + (pt[0] - cx) * 1.2f, cy + (pt[1] - cy) * 1.2f, 0f, ang, yoff);
    }

    /** Мягкое свечение вокруг лезвия и призрачный след взмаха. */
    private static void bladeGlow(BufferBuilder b, Matrix4f m, float x, float y, float z, float rhx, float rhz, float S,
                                  float ang, float yoff, float a, int rgb) {
        int col = argb(rgb, a);
        for (int i = 0; i < BN; i++) {
            float[] o0 = gp(x, y, z, rhx, rhz, S, BLO[i], ang, yoff), o1 = gp(x, y, z, rhx, rhz, S, BLO[i + 1], ang, yoff);
            float[] i0 = gp(x, y, z, rhx, rhz, S, BLI[i], ang, yoff), i1 = gp(x, y, z, rhx, rhz, S, BLI[i + 1], ang, yoff);
            vtx(b, m, o0[0], o0[1], o0[2], col);
            vtx(b, m, i0[0], i0[1], i0[2], col);
            vtx(b, m, o1[0], o1[1], o1[2], col);
            vtx(b, m, i0[0], i0[1], i0[2], col);
            vtx(b, m, i1[0], i1[1], i1[2], col);
            vtx(b, m, o1[0], o1[1], o1[2], col);
        }
    }

    private static void drawScythe(BufferBuilder b, Matrix4f m, Config c, Scythe sc, long now, Vector3f rh) {
        float t = (now - sc.t0) / 1000f;
        if (t < 0f || t > 2.5f) return;
        float tt = Math.min(1f, t / SWING);
        float phi, yoff;
        if (t < SWING) {
            float e = tt * tt * tt; // медленно сверху, всё быстрее к низу
            phi = -115f * (1f - e);
            yoff = 2.4f * (1f - e);
        } else {
            float k = t - SWING;
            phi = 7f * (float) (Math.exp(-6 * k) * Math.cos(k * 25));
            yoff = 0f;
        }
        float alpha;
        if (t < 0.3f) alpha = t / 0.3f;
        else if (t < 1.7f) alpha = 1f;
        else alpha = Math.max(0f, 1f - (t - 1.7f) / 0.8f);

        int rgb = killRgb(c, 0f);
        float S = 0.5f * c.killSize;
        float x = (float) sc.x, y = (float) sc.y, z = (float) sc.z;
        if (t < SWING && tt > 0.55f) {
            for (int g = 4; g >= 1; g--) {
                bladeGlow(b, m, x, y, z, rh.x, rh.z, S, (float) Math.toRadians(phi - g * 7f), yoff + g * 0.1f, alpha * (0.4f / g), rgb);
            }
        }
        bladeGlow(b, m, x, y, z, rh.x, rh.z, S, (float) Math.toRadians(phi), yoff, alpha * 0.3f, rgb);
        scytheMesh(b, m, x, y, z, rh.x, rh.z, S, (float) Math.toRadians(phi), yoff, alpha, rgb);
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
            float base = l.kind == 1 ? c.killSize * 1.1f : c.comboSize;
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
    private static void drawRings(Matrix4f m, Config c, long now) {
        BufferBuilder b = begin(VertexFormat.DrawMode.TRIANGLES);
        for (Ring r : rings) {
            float t = (now - r.t0) / (float) r.dur;
            if (t < 0f || t > 1f) continue;
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
