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
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Тест-игроки: клиентские манекены с твоим скином. Сервер о них не знает, другие игроки их не видят. */
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

    private static class Bot {
        Dummy e;
        double x, y, z;
        float yaw;
        long deadAt = 0;
    }

    private static final List<Bot> bots = new ArrayList<>();
    private static int idCounter = 0;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static boolean active() {
        for (Bot b : bots) if (b.e != null && !b.e.isRemoved()) return true;
        return false;
    }

    public static void spawn(MinecraftClient mc) {
        removeAll(mc);
        addBot(mc);
    }

    public static void addBot(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        Vec3d look = p.getRotationVec(1f);
        float d = Config.I.testDist;
        int n = bots.size();
        double side = ((n + 1) / 2) * 1.4 * (n % 2 == 0 ? 1 : -1);
        double rx = -Math.cos(Math.toRadians(p.getYaw())), rz = -Math.sin(Math.toRadians(p.getYaw()));
        Bot b = new Bot();
        b.x = p.getX() + look.x * d + rx * side;
        b.z = p.getZ() + look.z * d + rz * side;
        b.y = p.getY();
        b.yaw = p.getYaw() + 180f;
        place(mc, b);
        bots.add(b);
    }

    private static void place(MinecraftClient mc, Bot b) {
        if (mc.world == null || mc.player == null) return;
        GameProfile gp = new GameProfile(UUID.randomUUID(), "Test_Dummy");
        Dummy d = new Dummy(mc.world, gp);
        d.setId(-1_000_000 - (idCounter++ % 100000));
        d.refreshPositionAndAngles(b.x, b.y, b.z, b.yaw, 0f);
        d.setHeadYaw(b.yaw);
        d.setBodyYaw(b.yaw);
        d.setHealth(d.getMaxHealth());
        if (Config.I.testCopyGear) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                    EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
            for (EquipmentSlot s : slots) d.equipStack(s, mc.player.getEquippedStack(s).copy());
        }
        mc.world.addEntity(d);
        b.e = d;
        b.deadAt = 0;
    }

    public static void removeAll(MinecraftClient mc) {
        for (Bot b : bots) {
            if (b.e != null && mc.world != null) mc.world.removeEntity(b.e.getId(), Entity.RemovalReason.DISCARDED);
        }
        bots.clear();
    }

    public static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            bots.clear();
            return;
        }
        Config c = Config.I;
        long now = System.currentTimeMillis();
        Iterator<Bot> it = bots.iterator();
        while (it.hasNext()) {
            Bot b = it.next();
            if (b.e == null) {
                it.remove();
                continue;
            }
            if (b.e.getWorld() != mc.world) {
                it.remove();
                continue;
            }
            if (b.e.isRemoved()) {
                if (b.e.getHealth() <= 0 && c.testRespawn) {
                    if (b.deadAt == 0) b.deadAt = now;
                    if (now - b.deadAt > 1500) place(mc, b);
                } else {
                    it.remove();
                }
                continue;
            }
            if (!c.testMortal && b.e.getHealth() < b.e.getMaxHealth()) b.e.setHealth(b.e.getMaxHealth());
        }
    }

    private static float armorOf(Dummy d) {
        float a = 0f;
        for (EquipmentSlot s : ARMOR) {
            ItemStack st = d.getEquippedStack(s);
            if (st.getItem() instanceof ArmorItem ai) a += ai.getProtection();
        }
        return a;
    }

    private static float toughnessOf(Dummy d) {
        float a = 0f;
        for (EquipmentSlot s : ARMOR) {
            ItemStack st = d.getEquippedStack(s);
            if (st.getItem() instanceof ArmorItem ai) a += ai.getToughness();
        }
        return a;
    }

    /** Имитация урона на клиенте с учётом брони (настоящего урона нет: сервер манекена не знает). */
    public static void onHit(Dummy b, boolean crit) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || b.isRemoved() || b.getHealth() <= 0) return;
        float dmg = (float) p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        float cd = p.getAttackCooldownProgress(0.5f);
        dmg *= 0.2f + cd * cd * 0.8f;
        if (crit) dmg *= 1.5f;
        if (Config.I.testArmor) {
            float armor = armorOf(b), tough = toughnessOf(b);
            float reduce = Math.min(20f, Math.max(armor / 5f, armor - dmg / (2f + tough / 4f)));
            dmg *= 1f - reduce / 25f;
        }
        b.hurtTime = 10;
        b.maxHurtTime = 10;
        if (Config.I.testMortal) b.setHealth(Math.max(0f, b.getHealth() - dmg));
        else b.setHealth(b.getMaxHealth());
    }
}
