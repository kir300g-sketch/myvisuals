package com.example.visuals;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/** Тест-игрок: клиентский манекен с твоим скином. Сервер о нём не знает, другие игроки его не видят. */
public final class TestPlayer {
    private TestPlayer() {
    }

    public static class Dummy extends OtherClientPlayerEntity {
        public Dummy(ClientWorld world, GameProfile profile) {
            super(world, profile);
        }

        @Override
        public SkinTextures getSkinTextures() {
            ClientPlayerEntity me = MinecraftClient.getInstance().player;
            return me != null ? me.getSkinTextures() : super.getSkinTextures();
        }
    }

    private static Dummy bot = null;
    private static double sx, sy, sz;
    private static float syaw;
    private static long deadAt = 0;
    private static int idCounter = 0;

    public static boolean active() {
        return bot != null && !bot.isRemoved();
    }

    public static void spawn(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        remove(mc);
        Vec3d look = p.getRotationVec(1f);
        float d = Config.I.testDist;
        sx = p.getX() + look.x * d;
        sz = p.getZ() + look.z * d;
        sy = p.getY();
        syaw = p.getYaw() + 180f;
        place(mc);
    }

    private static void place(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        GameProfile gp = new GameProfile(UUID.randomUUID(), "Test_Dummy");
        Dummy d = new Dummy(mc.world, gp);
        d.setId(-1_000_000 - (idCounter++ % 100000));
        d.refreshPositionAndAngles(sx, sy, sz, syaw, 0f);
        d.setHeadYaw(syaw);
        d.setBodyYaw(syaw);
        d.setHealth(d.getMaxHealth());
        if (Config.I.testCopyGear) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                    EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
            for (EquipmentSlot s : slots) d.equipStack(s, mc.player.getEquippedStack(s).copy());
        }
        mc.world.addEntity(d);
        bot = d;
    }

    public static void remove(MinecraftClient mc) {
        if (bot != null && mc.world != null) {
            mc.world.removeEntity(bot.getId(), Entity.RemovalReason.DISCARDED);
        }
        bot = null;
        deadAt = 0;
    }

    public static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            bot = null;
            return;
        }
        if (bot == null) return;
        if (bot.getWorld() != mc.world) {
            bot = null;
            return;
        }
        Config c = Config.I;
        long now = System.currentTimeMillis();
        if (bot.isRemoved()) {
            if (bot.getHealth() <= 0 && c.testRespawn) {
                if (deadAt == 0) deadAt = now;
                if (now - deadAt > 1500) {
                    deadAt = 0;
                    place(mc);
                }
            } else {
                bot = null;
                deadAt = 0;
            }
            return;
        }
        if (!c.testMortal && bot.getHealth() < bot.getMaxHealth()) bot.setHealth(bot.getMaxHealth());
    }

    /** Имитация урона на клиенте (настоящего урона нет: сервер манекена не знает). */
    public static void onHit(Dummy b, boolean crit) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || b.isRemoved() || b.getHealth() <= 0) return;
        float dmg = (float) p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        float cd = p.getAttackCooldownProgress(0.5f);
        dmg *= 0.2f + cd * cd * 0.8f;
        if (crit) dmg *= 1.5f;
        b.hurtTime = 10;
        b.maxHurtTime = 10;
        if (Config.I.testMortal) b.setHealth(Math.max(0f, b.getHealth() - dmg));
        else b.setHealth(b.getMaxHealth());
    }
}
