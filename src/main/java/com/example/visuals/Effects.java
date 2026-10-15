package com.example.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.joml.Vector3f;

import java.awt.Color;

public class Effects {
    private static double angle = 0;
    private static double lastX, lastY, lastZ;
    private static boolean hasLast = false;

    private static int rgb(boolean rainbow, float hue) {
        float h = rainbow ? (System.currentTimeMillis() % 4000) / 4000f : hue;
        return Color.HSBtoRGB(h, 0.85f, 1f) & 0xFFFFFF;
    }

    private static DustParticleEffect dust(int rgb, float size) {
        Vector3f color = new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
        return new DustParticleEffect(color, size);
    }

    public static void tick(MinecraftClient mc) {
        ClientWorld w = mc.world;
        ClientPlayerEntity p = mc.player;
        if (w == null || p == null) {
            hasLast = false;
            return;
        }
        Config c = Config.I;

        // ---- Аура ----
        if (c.auraEnabled) {
            angle += c.auraSpeed;
            int n = Math.max(1, c.auraCount);
            for (int i = 0; i < n; i++) {
                double a = angle + i * (Math.PI * 2 / n);
                double x = p.getX() + Math.cos(a) * c.auraRadius;
                double z = p.getZ() + Math.sin(a) * c.auraRadius;
                double y = p.getY() + 1.0 + Math.sin(angle * 2 + i) * 0.6;
                w.addParticle(dust(rgb(c.auraRainbow, c.auraHue), 0.8f), x, y, z, 0, 0, 0);
            }
        }

        // ---- След ----
        if (c.trailEnabled && hasLast) {
            double dx = p.getX() - lastX, dy = p.getY() - lastY, dz = p.getZ() - lastZ;
            if (dx * dx + dy * dy + dz * dz > 0.0004) {
                for (int i = 0; i < c.trailDensity; i++) {
                    double x = p.getX() + (w.random.nextFloat() - 0.5) * 0.4;
                    double y = p.getY() + 0.1 + w.random.nextFloat() * 0.3;
                    double z = p.getZ() + (w.random.nextFloat() - 0.5) * 0.4;
                    w.addParticle(dust(rgb(c.trailRainbow, c.trailHue), c.trailSize), x, y, z, 0, 0.01, 0);
                }
            }
        }
        lastX = p.getX();
        lastY = p.getY();
        lastZ = p.getZ();
        hasLast = true;

        // ---- Снег ----
        if (c.snowEnabled) {
            for (int i = 0; i < c.snowDensity; i++) {
                double x = p.getX() + (w.random.nextFloat() - 0.5) * 24;
                double y = p.getY() + 5 + w.random.nextFloat() * 6;
                double z = p.getZ() + (w.random.nextFloat() - 0.5) * 24;
                w.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, 0, -0.05, 0);
            }
        }

        // ---- Светлячки ----
        if (c.fireflies && w.random.nextFloat() < 0.6f) {
            double x = p.getX() + (w.random.nextFloat() - 0.5) * 20;
            double y = p.getY() + w.random.nextFloat() * 4;
            double z = p.getZ() + (w.random.nextFloat() - 0.5) * 20;
            w.addParticle(dust(0xC8FF50, 0.7f), x, y, z,
                    (w.random.nextFloat() - 0.5) * 0.02, (w.random.nextFloat() - 0.5) * 0.02, (w.random.nextFloat() - 0.5) * 0.02);
        }

        // ---- Время суток (только визуально) ----
        if (c.timeOverride) {
            w.setTimeOfDay((long) c.timeOfDay);
        }
    }
}
