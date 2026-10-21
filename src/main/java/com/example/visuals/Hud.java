package com.example.visuals;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Hud {
    private Hud() {
    }

    public static final String[] NAMES = {"Target HUD", "Броня", "Зелья"};

    private static Entity target = null;
    private static long targetTime = 0;
    private static Entity shown = null;
    private static Entity hpFor = null;
    private static float hpDisp = 0f;
    private static float alphaDisp = 0f;

    public static Config.HudCfg cfg(int id) {
        Config c = Config.I;
        return id == 0 ? c.hudTarget : (id == 1 ? c.hudArmor : c.hudPotions);
    }

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> render(ctx));
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient && entity instanceof LivingEntity) {
                target = entity;
                targetTime = System.currentTimeMillis();
                WorldRender.onAttack(entity);
            }
            return ActionResult.PASS;
        });
    }

    private static int baseW(int id) {
        return id == 0 ? 150 : (id == 1 ? 96 : 118);
    }

    private static int baseH(int id) {
        Config.HudCfg h = cfg(id);
        if (id == 0) return Math.round(38 * h.rowH);
        if (id == 1) return Math.round(4 * 20 * h.rowH) + 4;
        MinecraftClient mc = MinecraftClient.getInstance();
        int n = mc.player == null ? 1 : Math.max(1, mc.player.getStatusEffects().size());
        return Math.round(n * 22 * h.rowH) + 4;
    }

    /** x, y, ширина, высота на экране (с учётом размера). */
    public static int[] bounds(int id, int sw, int sh) {
        Config.HudCfg h = cfg(id);
        return new int[]{Math.round(h.x * sw), Math.round(h.y * sh),
                Math.round(baseW(id) * h.scale), Math.round(baseH(id) * h.scale)};
    }

    private static void render(DrawContext c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        Config cfg = Config.I;
        boolean editing = mc.currentScreen instanceof VisualsScreen;
        int sw = c.getScaledWindowWidth(), sh = c.getScaledWindowHeight();
        Streak.render(c, mc, sw, sh);
        if (cfg.hudTarget.enabled) drawTarget(c, mc, cfg, sw, sh, editing);
        if (cfg.hudArmor.enabled) drawArmor(c, mc, sw, sh);
        if (cfg.hudPotions.enabled) drawPotions(c, mc, sw, sh, editing);
    }

    private static void push(DrawContext c, int id, int sw, int sh) {
        Config.HudCfg h = cfg(id);
        c.getMatrices().push();
        c.getMatrices().translate(h.x * sw, h.y * sh, 0);
        c.getMatrices().scale(h.scale, h.scale, 1f);
    }

    // ---------- Target HUD ----------
    private static void drawTarget(DrawContext c, MinecraftClient mc, Config cfg, int sw, int sh, boolean editing) {
        long now = System.currentTimeMillis();
        boolean recent = target != null && now - targetTime < cfg.targetShowSec * 1000 && target.isAlive();
        Entity e = recent ? target : null;
        if (e == null && editing) e = mc.player;
        alphaDisp = Ui.approach(alphaDisp, e != null ? 1f : 0f, 0.18f);
        if (e != null) shown = e;
        if (alphaDisp < 0.02f || shown == null) return;
        if (!(shown instanceof LivingEntity le)) return;

        if (hpFor != shown) {
            hpFor = shown;
            hpDisp = le.getHealth();
        }
        float hp = Math.max(0f, le.getHealth());
        float max = Math.max(1f, le.getMaxHealth());
        hpDisp = Ui.approach(hpDisp, hp, 0.15f);
        float ratio = Math.max(0f, Math.min(1f, hpDisp / max));

        TextRenderer tr = mc.textRenderer;
        float a = alphaDisp;
        int W = 150, H = baseH(0);
        push(c, 0, sw, sh);
        Ui.rr(c, 0, 0, W, H, Math.min(12, H / 2), Ui.col(Ui.bgColor(), 0.85f * a));

        int size = H - 12, hx = 6, hy = 6;
        Identifier tex = null;
        if (le instanceof AbstractClientPlayerEntity pl) tex = pl.getSkinTextures().texture();
        if (tex != null) {
            c.drawTexture(tex, hx, hy, size, size, 8f, 8f, 8, 8, 64, 64);
            c.drawTexture(tex, hx, hy, size, size, 40f, 8f, 8, 8, 64, 64);
        } else {
            Ui.rr(c, hx, hy, hx + size, hy + size, 4, Ui.col(0x3A3A48, a));
            c.drawText(tr, "?", hx + size / 2 - 2, hy + size / 2 - 4, Ui.col(0xFFFFFF, a), false);
        }

        int tx = hx + size + 7;
        String name = tr.trimToWidth(le.getName().getString(), W - tx - 44);
        c.drawText(tr, name, tx, 7, Ui.col(0xFFFFFF, a), false);

        // 6 слотов экипировки: шлем, нагрудник, поножи, ботинки, рука, вторая рука
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
        int ey = H - 17;
        for (int i = 0; i < slots.length; i++) {
            ItemStack st = le.getEquippedStack(slots[i]);
            int ex = tx + i * 11;
            if (st.isEmpty()) {
                c.drawText(tr, "x", ex + 2, ey + 1, Ui.col(0xFFFFFF, 0.35f * a), false);
            } else {
                c.getMatrices().push();
                c.getMatrices().translate(ex, ey, 0);
                c.getMatrices().scale(0.6f, 0.6f, 1f);
                c.drawItem(st, 0, 0);
                c.getMatrices().pop();
            }
        }

        // круг со здоровьем справа
        int cx = W - 20, cy = H / 2, seg = 36;
        float rad = 14f;
        int hpCol = Ui.mix(0xFF4040, 0x40FF70, ratio);
        int filled = Math.round(seg * ratio);
        for (int i = 0; i < seg; i++) {
            double ang = -Math.PI / 2 + i * Math.PI * 2 / seg;
            int px = cx + (int) Math.round(Math.cos(ang) * rad);
            int py = cy + (int) Math.round(Math.sin(ang) * rad);
            c.fill(px - 1, py - 1, px + 1, py + 1, Ui.col(i < filled ? hpCol : 0x3A3A48, a));
        }
        String hn = String.valueOf(Math.round(hpDisp));
        c.drawText(tr, hn, cx - tr.getWidth(hn) / 2, cy - 4, Ui.col(0xFFFFFF, a), false);
        c.getMatrices().pop();
    }

    // ---------- Броня ----------
    private static void drawArmor(DrawContext c, MinecraftClient mc, int sw, int sh) {
        Config.HudCfg h = Config.I.hudArmor;
        TextRenderer tr = mc.textRenderer;
        int W = 96, H = baseH(1);
        float rh = 20 * h.rowH;
        push(c, 1, sw, sh);
        Ui.rr(c, 0, 0, W, H, 5, Ui.col(Ui.bgColor(), 0.75f));
        for (int row = 0; row < 4; row++) {
            ItemStack st = mc.player.getInventory().armor.get(3 - row);
            int y0 = 2 + Math.round(row * rh);
            int iy = y0 + Math.round((rh - 16) / 2f);
            if (st.isEmpty()) {
                c.drawText(tr, "-", 9, iy + 4, Ui.col(0xFFFFFF, 0.25f), false);
                continue;
            }
            c.drawItem(st, 4, iy);
            if (st.isDamageable()) {
                int maxD = st.getMaxDamage();
                int left = Math.max(0, maxD - st.getDamage());
                float ratio = maxD <= 0 ? 0f : Math.max(0f, Math.min(1f, left / (float) maxD));
                String txt = String.valueOf(left);
                int bx = 24, bw = W - bx - tr.getWidth(txt) - 10;
                Ui.rr(c, bx, iy + 6, bx + bw, iy + 10, 2, Ui.col(0x000000, 0.5f));
                Ui.rr(c, bx, iy + 6, bx + Math.max(3, Math.round(bw * ratio)), iy + 10, 2,
                        Ui.col(Ui.mix(0xFF4040, 0x40FF70, ratio), 1f));
                c.drawText(tr, txt, W - 5 - tr.getWidth(txt), iy + 4, Ui.col(0xFFFFFF, 0.9f), false);
            }
        }
        c.getMatrices().pop();
    }

    // ---------- Зелья ----------
    private static String timeText(StatusEffectInstance e) {
        if (e.isInfinite()) return "inf";
        int s = e.getDuration() / 20;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private static void drawPotions(DrawContext c, MinecraftClient mc, int sw, int sh, boolean editing) {
        Config.HudCfg h = Config.I.hudPotions;
        TextRenderer tr = mc.textRenderer;
        List<StatusEffectInstance> list = new ArrayList<>(mc.player.getStatusEffects());
        if (list.isEmpty() && !editing) return;
        int W = 118, H = baseH(2);
        float rh = 22 * h.rowH;
        push(c, 2, sw, sh);
        Ui.rr(c, 0, 0, W, H, 5, Ui.col(Ui.bgColor(), 0.75f));
        if (list.isEmpty()) {
            c.drawText(tr, "Нет эффектов", 8, 8, Ui.col(0xFFFFFF, 0.5f), false);
        }
        for (int i = 0; i < list.size(); i++) {
            StatusEffectInstance e = list.get(i);
            int y0 = 2 + Math.round(i * rh);
            int iy = y0 + Math.round((rh - 18) / 2f);
            Sprite sp = mc.getStatusEffectSpriteManager().getSprite(e.getEffectType());
            c.drawSprite(4, iy, 0, 18, 18, sp);
            String name = e.getEffectType().value().getName().getString();
            if (e.getAmplifier() > 0) name = name + " " + (e.getAmplifier() + 1);
            boolean good = e.getEffectType().value().isBeneficial();
            c.drawText(tr, tr.trimToWidth(name, W - 28), 26, iy + 1, Ui.col(good ? 0xFFFFFF : 0xFF8080, 1f), false);
            c.drawText(tr, timeText(e), 26, iy + 10, Ui.col(0xAAAAAA, 1f), false);
        }
        c.getMatrices().pop();
    }
}
