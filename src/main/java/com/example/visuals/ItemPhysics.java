package com.example.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/** Выброшенные предметы делают кувырок в полёте и лежат на земле на боку, без вращения и подпрыгивания. */
public final class ItemPhysics {
    private ItemPhysics() {
    }

    public static ItemEntity current = null;
    private static final Map<Integer, float[]> STATE = new HashMap<>();

    /** Угол кувырка 0..360 (360 = лежит). Один оборот в полёте, затем плавная укладка. */
    private static float tumble(ItemEntity e) {
        float now = (float) (System.nanoTime() / 1.0e9);
        float[] st = STATE.get(e.getId());
        if (st == null) {
            if (STATE.size() > 500) STATE.clear();
            st = new float[]{e.isOnGround() ? 360f : 0f, now};
            STATE.put(e.getId(), st);
        }
        float dt = Math.min(0.1f, now - st[1]);
        st[1] = now;
        if (!e.isOnGround()) {
            st[0] = Math.min(360f, st[0] + 540f * dt);
        } else {
            st[0] += (360f - st[0]) * Math.min(1f, dt * 12f);
            if (st[0] > 359.5f) st[0] = 360f;
        }
        return st[0];
    }

    public static void rotate(MatrixStack ms, Quaternionf q) {
        ItemEntity e = current;
        if (!Config.I.itemPhysics || e == null) {
            ms.multiply(q);
            return;
        }
        boolean depth;
        try {
            depth = MinecraftClient.getInstance().getItemRenderer().getModels().getModel(e.getStack()).hasDepth();
        } catch (Exception ex) {
            depth = e.getStack().getItem() instanceof BlockItem;
        }
        float yaw = (float) Math.toRadians((e.getId() * 137) % 360);
        float t = tumble(e);
        ms.multiply(RotationAxis.POSITIVE_Y.rotation(yaw));
        if (!depth) {
            ms.translate(0f, -0.095f, 0f);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f + t));
        } else {
            ms.translate(0f, 0.1f, 0f);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(t));
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(((e.getId() * 53) % 4) * 90f));
        }
    }
}
