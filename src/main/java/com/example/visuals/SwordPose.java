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
            "Ванильная", "Слайд", "Плавный", "Взмах", "Тычок",
            "Рубка", "Вращение", "Сальто", "Веер", "Пружина"
    };

    // кадры: t, x, y, z, rotX, rotY, rotZ  (правая рука; вперёд = -z)
    private static final float[][][] ANIM_KF = {
            null,
            // 1 Слайд
            {{0, 0, 0, 0, 0, 0, 0}, {0.2f, 0, 0, 0.12f, 12, 0, 0}, {0.45f, -0.05f, -0.12f, -0.55f, -35, 0, -8},
                    {0.7f, 0, -0.05f, -0.25f, -12, 0, 0}, {1, 0, 0, 0, 0, 0, 0}},
            // 2 Плавный
            {{0, 0, 0, 0, 0, 0, 0}, {0.25f, 0.12f, 0.08f, 0.05f, 18, 0, -22}, {0.55f, -0.22f, -0.18f, -0.22f, -52, 0, 38},
                    {0.8f, -0.08f, -0.05f, -0.08f, -15, 0, 10}, {1, 0, 0, 0, 0, 0, 0}},
            // 3 Взмах
            {{0, 0, 0, 0, 0, 0, 0}, {0.22f, 0.2f, 0.05f, 0.05f, 10, 0, -45}, {0.55f, -0.4f, -0.08f, -0.1f, -20, 0, 60},
                    {0.8f, -0.1f, 0, 0, -5, 0, 15}, {1, 0, 0, 0, 0, 0, 0}},
            // 4 Тычок
            {{0, 0, 0, 0, 0, 0, 0}, {0.15f, 0, 0.02f, 0.15f, 8, 0, 0}, {0.35f, 0, 0.05f, -0.8f, -14, 0, 0},
                    {0.6f, 0, 0.03f, -0.5f, -8, 0, 0}, {1, 0, 0, 0, 0, 0, 0}},
            // 5 Рубка
            {{0, 0, 0, 0, 0, 0, 0}, {0.28f, 0, 0.32f, 0.12f, 55, 0, 0}, {0.55f, -0.02f, -0.28f, -0.32f, -80, 0, -6},
                    {0.8f, 0, -0.08f, -0.1f, -25, 0, 0}, {1, 0, 0, 0, 0, 0, 0}},
            // 6 Вращение
            {{0, 0, 0, 0, 0, 0, 0}, {0.25f, 0.05f, 0.05f, -0.1f, -20, 0, -100}, {0.5f, 0, 0.1f, -0.2f, -30, 0, -200},
                    {0.75f, 0, 0.05f, -0.1f, -10, 0, -300}, {1, 0, 0, 0, 0, 0, -360}},
            // 7 Сальто
            {{0, 0, 0, 0, 0, 0, 0}, {0.25f, 0, 0.1f, -0.15f, -90, 0, 0}, {0.5f, 0, 0.18f, -0.3f, -180, 0, 0},
                    {0.75f, 0, 0.08f, -0.15f, -270, 0, 0}, {1, 0, 0, 0, -360, 0, 0}},
            // 8 Веер
            {{0, 0, 0, 0, 0, 0, 0}, {0.2f, 0.1f, 0, -0.05f, 0, 0, -40}, {0.4f, -0.15f, 0, -0.1f, -10, 0, 45},
                    {0.6f, 0.1f, 0, -0.1f, -10, 0, -30}, {0.8f, -0.05f, 0, -0.05f, -5, 0, 20}, {1, 0, 0, 0, 0, 0, 0}},
            // 9 Пружина
            {{0, 0, 0, 0, 0, 0, 0}, {0.2f, 0, 0.05f, 0.1f, 20, 0, 0}, {0.4f, 0, -0.1f, -0.35f, -85, 0, 0},
                    {0.55f, 0, -0.05f, -0.25f, -60, 0, 0}, {0.7f, 0, -0.07f, -0.3f, -75, 0, 0}, {1, 0, 0, 0, 0, 0, 0}}
    };

    private static float lastP = 0f;
    private static long animStart = -1L;

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

    /** Эрмитов сплайн по ключевым кадрам: плавно проходит через кадры без остановок. */
    private static float[] sample(float[][] kf, float t) {
        int n = kf.length;
        int i = 0;
        while (i < n - 2 && t > kf[i + 1][0]) i++;
        float t0 = kf[i][0], t1 = kf[i + 1][0];
        float dt = t1 - t0;
        float u = dt <= 0f ? 1f : Math.max(0f, Math.min(1f, (t - t0) / dt));
        float u2 = u * u, u3 = u2 * u;
        float h00 = 2 * u3 - 3 * u2 + 1, h10 = u3 - 2 * u2 + u, h01 = -2 * u3 + 3 * u2, h11 = u3 - u2;
        float[] out = new float[6];
        for (int ch = 1; ch <= 6; ch++) {
            float p0 = kf[i][ch], p1 = kf[i + 1][ch];
            float m0 = (i == 0) ? 0f : (kf[i + 1][ch] - kf[i - 1][ch]) / (kf[i + 1][0] - kf[i - 1][0]);
            float m1 = (i + 1 == n - 1) ? 0f : (kf[i + 2][ch] - kf[i][ch]) / (kf[i + 2][0] - kf[i][0]);
            out[ch - 1] = h00 * p0 + h10 * dt * m0 + h01 * p1 + h11 * dt * m1;
        }
        return out;
    }

    public static void apply(AbstractClientPlayerEntity player, float tickDelta, Hand hand, ItemStack item, MatrixStack m) {
        Config c = Config.I;
        if (!c.swordEnabled || !(item.getItem() instanceof SwordItem)) return;

        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        float side = arm == Arm.RIGHT ? 1f : -1f;

        float tx = c.swordX, ty = c.swordY, tz = c.swordZ;
        float rx = c.swordRotX, ry = c.swordRotY, rz = c.swordRotZ;

        if (hand == Hand.MAIN_HAND && c.swordAnim > 0 && c.swordAnim < ANIM_KF.length) {
            float p = player.getHandSwingProgress(tickDelta);
            long now = System.nanoTime();
            if (p > 0f && (lastP <= 0f || p < lastP - 0.2f)) animStart = now;
            lastP = p;
            if (animStart >= 0) {
                float t = (now - animStart) / 1.0e9f / Math.max(0.05f, c.swordDuration);
                if (t >= 1f) {
                    animStart = -1L;
                } else {
                    float[] v = sample(ANIM_KF[c.swordAnim], t);
                    float k = (c.swordAnim == 6 || c.swordAnim == 7) ? 1f : c.swordIntensity;
                    tx += v[0] * k;
                    ty += v[1] * k;
                    tz += v[2] * k;
                    rx += v[3] * k;
                    ry += v[4] * k;
                    rz += v[5] * k;
                }
            }
        }

        m.translate(side * tx, ty, tz);
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
