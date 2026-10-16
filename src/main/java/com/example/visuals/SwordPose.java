package com.example.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;

public class SwordPose {
    public static final String[] PRESETS = {
            "Ванильное", "Низкое", "Высокое", "Ближе к центру", "Далёкое",
            "Горизонтально", "Клинок вперёд", "Крупный", "Мини", "Наклон влево"
    };
    // x, y, z, rotX, rotY, rotZ, scale
    private static final float[][] VALUES = {
            {0f, 0f, 0f, 0f, 0f, 0f, 1f},
            {0f, -0.18f, 0f, 0f, 0f, 0f, 1f},
            {0f, 0.15f, -0.05f, 0f, 0f, 0f, 1f},
            {-0.3f, 0f, 0f, 0f, 15f, 0f, 1f},
            {0.08f, 0.05f, -0.35f, 0f, 0f, 0f, 1f},
            {0f, -0.05f, 0f, 0f, 0f, -70f, 1f},
            {0f, 0f, -0.15f, 60f, 0f, 0f, 1f},
            {0f, 0f, 0f, 0f, 0f, 0f, 1.5f},
            {0.05f, 0.05f, 0f, 0f, 0f, 0f, 0.65f},
            {-0.2f, 0f, 0f, 0f, 25f, 35f, 1f}
    };

    public static final String[] ANIMS = {
            "Ванильная", "Рубящий", "Взмах", "Колющий", "Вращение",
            "Сверху", "Мягкий", "Вихрь", "Сальто"
    };

    public static void applyPreset(int i) {
        if (i < 0 || i >= VALUES.length) return;
        Config c = Config.I;
        float[] v = VALUES[i];
        c.swordX = v[0];
        c.swordY = v[1];
        c.swordZ = v[2];
        c.swordRotX = v[3];
        c.swordRotY = v[4];
        c.swordRotZ = v[5];
        c.swordScale = v[6];
    }

    /** true, если ванильную анимацию удара нужно отключить (её заменяет наша). */
    public static boolean replacesSwing() {
        Config c = Config.I;
        if (!c.swordEnabled || c.swordAnim == 0) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && mc.player.getMainHandStack().getItem() instanceof SwordItem;
    }

    public static void apply(AbstractClientPlayerEntity player, float tickDelta, Hand hand, ItemStack item, MatrixStack m) {
        Config c = Config.I;
        if (!c.swordEnabled || !(item.getItem() instanceof SwordItem)) return;

        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        float side = arm == Arm.RIGHT ? 1f : -1f;

        float tx = c.swordX, ty = c.swordY, tz = c.swordZ;
        float rx = c.swordRotX, ry = c.swordRotY, rz = c.swordRotZ;

        if (hand == Hand.MAIN_HAND && c.swordAnim != 0) {
            float p = player.getHandSwingProgress(tickDelta);
            if (p > 0f) {
                float pc = (float) Math.pow(p, c.swordCurve);
                float s = (float) Math.sin(pc * Math.PI);
                float w = (float) Math.sin(pc * Math.PI * 2);
                float i = c.swordIntensity;
                switch (c.swordAnim) {
                    case 1: // рубящий
                        rx += -45f * s * i;
                        rz += -50f * s * i;
                        tx += -0.2f * s * i;
                        ty += 0.1f * s * i;
                        break;
                    case 2: // горизонтальный взмах
                        ry += 70f * w * i;
                        tx += -0.15f * w * i;
                        break;
                    case 3: // колющий
                        tz += -0.7f * s * i;
                        rx += 12f * s * i;
                        break;
                    case 4: // вращение
                        rz += 360f * pc;
                        break;
                    case 5: // удар сверху
                        rx += -95f * s * i;
                        ty += 0.25f * s * i;
                        tz += -0.25f * s * i;
                        break;
                    case 6: // мягкий
                        rx += -25f * s * i;
                        tz += -0.25f * s * i;
                        ty += -0.05f * s * i;
                        break;
                    case 7: // вихрь
                        ry += 360f * pc;
                        rx += -30f * s * i;
                        break;
                    case 8: // сальто
                        rx += -360f * pc;
                        break;
                    default:
                        break;
                }
            }
        }

        // позиция
        m.translate(side * tx, ty, tz);
        // вращаем и масштабируем вокруг примерного положения руки
        float px = side * 0.56f, py = -0.52f, pz = -0.72f;
        m.translate(px, py, pz);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rx));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * ry));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * rz));
        float sc = c.swordScale;
        m.scale(sc, sc, sc);
        m.translate(-px, -py, -pz);
    }
}
