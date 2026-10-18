package com.example.visuals;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;

/** Выброшенные предметы лежат на земле (плоские - плашмя, под случайным углом), без вращения и подпрыгивания. */
public final class ItemPhysics {
    private ItemPhysics() {
    }

    public static ItemEntity current = null;

    public static void rotate(MatrixStack ms, Quaternionf q) {
        ItemEntity e = current;
        if (!Config.I.itemPhysics || e == null) {
            ms.multiply(q);
            return;
        }
        float yaw = (float) Math.toRadians((e.getId() * 137) % 360);
        ms.multiply(RotationAxis.POSITIVE_Y.rotation(yaw));
        if (!(e.getStack().getItem() instanceof BlockItem)) {
            ms.translate(0f, -0.095f, 0f);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));
        }
    }
}
