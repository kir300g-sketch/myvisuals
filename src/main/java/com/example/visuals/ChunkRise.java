package com.example.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

public class ChunkRise {
    private static final Map<Long, Long> seen = new HashMap<>();
    private static ClientWorld lastWorld = null;
    private static long lastClean = 0;

    /**
     * Смещение по Y для секции чанка. Дальше выбранной дистанции чанк "утоплен",
     * при входе в дистанцию он плавно поднимается.
     */
    public static float offset(float fx, float fy, float fz) {
        Config c = Config.I;
        if (!c.chunkAnim) return 0f;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return 0f;
        if (mc.world != lastWorld) {
            seen.clear();
            lastWorld = mc.world;
        }
        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        int ox = (int) (Math.round((fx + cam.x) / 16.0) * 16);
        int oy = (int) (Math.round((fy + cam.y) / 16.0) * 16);
        int oz = (int) (Math.round((fz + cam.z) / 16.0) * 16);
        long key = BlockPos.asLong(ox, oy, oz);
        long now = System.currentTimeMillis();

        if (now - lastClean > 5000) {
            lastClean = now;
            int lim = (mc.options.getViewDistance().getValue() + 4) * 16;
            seen.keySet().removeIf(k -> {
                BlockPos bp = BlockPos.fromLong(k);
                return Math.abs(bp.getX() - cam.x) > lim || Math.abs(bp.getZ() - cam.z) > lim;
            });
        }

        double dx = Math.abs(ox + 8 - cam.x), dz = Math.abs(oz + 8 - cam.z);
        float distChunks = (float) (Math.max(dx, dz) / 16.0);
        if (distChunks > c.chunkDist) {
            seen.remove(key);
            return -c.chunkDepth;
        }

        Long t0 = seen.get(key);
        if (t0 == null) {
            seen.put(key, now);
            t0 = now;
        }
        float t = (now - t0) / (c.chunkTime * 1000f);
        if (t >= 1f) return 0f;
        float e = 1f - (1f - t) * (1f - t) * (1f - t);
        return -(1f - e) * c.chunkDepth;
    }
}
