package com.example.visuals;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/**
 * Размытие при быстром повороте: прошлый кадр накладывается на текущий полупрозрачно,
 * чем быстрее поворот, тем сильнее. Накладывается после мира, до наших и ванильных HUD-надписей.
 */
public final class MotionBlur {
    private MotionBlur() {
    }

    private static int tex = 0, tw = 0, th = 0;
    private static boolean valid = false;
    private static float lastYaw = 0, lastPitch = 0, amt = 0;
    private static long last = 0;
    private static boolean haveRot = false;

    public static void render(DrawContext c, MinecraftClient mc) {
        Config cfg = Config.I;
        if (!cfg.blurEnabled || mc.player == null || mc.currentScreen != null) {
            valid = false;
            haveRot = false;
            return;
        }
        int fw = mc.getWindow().getFramebufferWidth(), fh = mc.getWindow().getFramebufferHeight();
        if (fw <= 0 || fh <= 0) return;
        c.draw();

        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        float yaw = mc.gameRenderer.getCamera().getYaw(), pitch = mc.gameRenderer.getCamera().getPitch();
        float speed = 0f;
        if (haveRot && dt > 1e-4f) speed = (Math.abs(MathHelper.wrapDegrees(yaw - lastYaw)) + Math.abs(pitch - lastPitch)) / dt;
        lastYaw = yaw;
        lastPitch = pitch;
        haveRot = true;
        float target = Math.min(0.85f, cfg.blurStrength) * Math.max(0f, Math.min(1f, (speed - 40f) / 300f));
        amt += (target - amt) * Math.min(1f, dt * 25f);

        int prevBound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        if (tex == 0 || tw != fw || th != fh) {
            if (tex != 0) GlStateManager._deleteTexture(tex);
            tex = GlStateManager._genTexture();
            RenderSystem.bindTexture(tex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 33071);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 33071);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, fw, fh, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            tw = fw;
            th = fh;
            valid = false;
        }

        if (valid && amt > 0.02f) {
            ShaderProgram prevShader = RenderSystem.getShader();
            int prevSlot = RenderSystem.getShaderTexture(0);
            int sw = c.getScaledWindowWidth(), sh = c.getScaledWindowHeight();
            Matrix4f mat = c.getMatrices().peek().getPositionMatrix();
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            RenderSystem.setShaderTexture(0, tex);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            int col = (Math.max(0, Math.min(255, Math.round(amt * 255))) << 24) | 0xFFFFFF;
            BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            b.vertex(mat, 0, sh, 0).texture(0f, 0f).color(col);
            b.vertex(mat, sw, sh, 0).texture(1f, 0f).color(col);
            b.vertex(mat, sw, 0, 0).texture(1f, 1f).color(col);
            b.vertex(mat, 0, 0, 0).texture(0f, 1f).color(col);
            BuiltBuffer built = b.endNullable();
            if (built != null) BufferRenderer.drawWithGlobalProgram(built);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.setShaderTexture(0, prevSlot);
            if (prevShader != null) RenderSystem.setShader(() -> prevShader);
        }

        RenderSystem.bindTexture(tex);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, fw, fh);
        RenderSystem.bindTexture(prevBound);
        valid = true;
    }
}
