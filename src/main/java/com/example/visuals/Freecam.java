package com.example.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

/** Фрикам-фоторежим: камера летает сама, не проходит сквозь стены и не уходит дальше дальности прорисовки. */
public final class Freecam {
    private Freecam() {
    }

    public static boolean active = false;
    public static double x, y, z;
    public static float yaw, pitch;
    private static boolean wasDown = false;
    private static Perspective prev = null;
    private static long lastNanos = 0;

    private static boolean down(MinecraftClient mc, int key) {
        long h = mc.getWindow().getHandle();
        if (key < 0) return GLFW.glfwGetMouseButton(h, -1 - key) == GLFW.GLFW_PRESS;
        return InputUtil.isKeyPressed(h, key);
    }

    public static void tick(MinecraftClient mc) {
        Config c = Config.I;
        if (!c.freecamEnabled || mc.player == null || mc.world == null || mc.currentScreen != null) {
            if (active) stop(mc);
            wasDown = false;
            return;
        }
        boolean d = down(mc, c.freecamKey);
        if (d && !wasDown) {
            if (active) stop(mc);
            else start(mc);
        }
        wasDown = d;
    }

    private static void start(MinecraftClient mc) {
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d p = cam.getPos();
        x = p.x;
        y = p.y;
        z = p.z;
        yaw = cam.getYaw();
        pitch = cam.getPitch();
        prev = mc.options.getPerspective();
        mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        lastNanos = 0;
        active = true;
    }

    private static void stop(MinecraftClient mc) {
        active = false;
        if (prev != null) mc.options.setPerspective(prev);
        prev = null;
    }

    public static void rotate(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch = MathHelper.clamp(pitch + (float) dy * 0.15f, -90f, 90f);
    }

    private static Vec3d slide(MinecraftClient mc, Vec3d p, Vec3d d) {
        if (d.lengthSquared() < 1e-12) return p;
        Vec3d q = p.add(d);
        Vec3d ahead = q.add(d.normalize().multiply(0.3));
        BlockHitResult r = mc.world.raycast(new RaycastContext(p, ahead, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player));
        return r.getType() == HitResult.Type.MISS ? q : p;
    }

    /** Вызывается раз в кадр: двигает камеру по нажатым клавишам движения. */
    public static void frame() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;

        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double fx = -Math.sin(yr) * Math.cos(pr), fy = -Math.sin(pr), fz = Math.cos(yr) * Math.cos(pr);
        double rx = -Math.cos(yr), rz = -Math.sin(yr);
        double mx = 0, my = 0, mz = 0;
        if (mc.options.forwardKey.isPressed()) {
            mx += fx;
            my += fy;
            mz += fz;
        }
        if (mc.options.backKey.isPressed()) {
            mx -= fx;
            my -= fy;
            mz -= fz;
        }
        if (mc.options.rightKey.isPressed()) {
            mx += rx;
            mz += rz;
        }
        if (mc.options.leftKey.isPressed()) {
            mx -= rx;
            mz -= rz;
        }
        if (mc.options.jumpKey.isPressed()) my += 1;
        if (mc.options.sneakKey.isPressed()) my -= 1;

        double speed = 6.0 * Config.I.freecamSpeed * (mc.options.sprintKey.isPressed() ? 3.0 : 1.0) * dt;
        Vec3d move = new Vec3d(mx, my, mz);
        if (move.lengthSquared() > 1e-9) move = move.normalize().multiply(speed);

        Vec3d p = new Vec3d(x, y, z);
        p = slide(mc, p, new Vec3d(move.x, 0, 0));
        p = slide(mc, p, new Vec3d(0, move.y, 0));
        p = slide(mc, p, new Vec3d(0, 0, move.z));

        // не дальше дальности прорисовки от игрока
        Vec3d pe = mc.player.getEyePos();
        double R = Math.max(16, mc.options.getClampedViewDistance() * 16 - 8);
        Vec3d off = p.subtract(pe);
        if (off.length() > R) p = pe.add(off.normalize().multiply(R));
        x = p.x;
        y = p.y;
        z = p.z;
    }
}
