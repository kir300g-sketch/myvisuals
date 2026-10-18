package com.example.visuals;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/** Процедурное небо: градиент, туманности и звёзды, натянутые на сферу вокруг камеры. */
public final class SkyFx {
    private SkyFx() {
    }

    private static final int[] ids = new int[4];

    //            зенит        горизонт     туманность 1    туманность 2
    private static final int[][] PAL = {
            {0x080A30, 0x4646AA, 0x965AFF, 0x46AAFF},
            {0x140528, 0xC82896, 0xFF50C8, 0x50C8FF},
            {0x04181E, 0x1E9682, 0x50FFB4, 0x3CA0FF},
            {0x190F3C, 0xFF6E3C, 0xFF965A, 0xC850B4}
    };

    public static final String[] STYLES = {"Галактика", "Неон", "Изумруд", "Закат"};

    public static int horizon(int style) {
        return PAL[Math.max(0, Math.min(3, style))][1];
    }

    private static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float vnoise(float u, float v, int fx, int fy) {
        float x = u * fx, y = v * fy;
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float tx = x - ix, ty = y - iy;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        float a = hash(Math.floorMod(ix, fx), iy), b = hash(Math.floorMod(ix + 1, fx), iy);
        float c = hash(Math.floorMod(ix, fx), iy + 1), d = hash(Math.floorMod(ix + 1, fx), iy + 1);
        return lerp(lerp(a, b, tx), lerp(c, d, tx), ty);
    }

    private static float fbm(float u, float v) {
        float s = 0, amp = 0.5f;
        int fx = 4, fy = 2;
        for (int o = 0; o < 5; o++) {
            s += amp * vnoise(u, v, fx, fy);
            amp *= 0.5f;
            fx *= 2;
            fy *= 2;
        }
        return s;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    private static int[] rgb(int c) {
        return new int[]{(c >> 16) & 255, (c >> 8) & 255, c & 255};
    }

    private static int generate(int style) {
        int W = 1024, H = 512;
        int[][] pal = new int[4][];
        for (int i = 0; i < 4; i++) pal[i] = rgb(PAL[style][i]);
        ByteBuffer buf = BufferUtils.createByteBuffer(W * H * 4);
        for (int y = 0; y < H; y++) {
            float v = y / (float) H;
            float e = 1f - v * 2f;
            float[] base = new float[3];
            for (int k = 0; k < 3; k++) {
                if (e >= 0) base[k] = lerp(pal[1][k], pal[0][k], (float) Math.pow(e, 0.6));
                else base[k] = lerp(pal[1][k], 0, Math.min(1f, -e * 1.5f));
            }
            float mask = Math.max(0f, Math.min(1f, 1.2f - Math.abs(e - 0.25f) * 1.3f));
            for (int x = 0; x < W; x++) {
                float u = x / (float) W;
                float n1 = smooth(0.42f, 0.8f, fbm(u, v)) * mask;
                float n2 = smooth(0.5f, 0.85f, fbm(u + 0.37f, v + 0.21f)) * mask;
                float[] col = new float[3];
                for (int k = 0; k < 3; k++) {
                    col[k] = base[k] + pal[2][k] * n1 * 0.8f + pal[3][k] * n2 * 0.5f;
                }
                float sh = hash(x * 7919 + 13, y * 104729 + 7);
                if (e > -0.05f && sh > 0.9975f) {
                    float br = 0.55f + 0.45f * hash(x, y);
                    for (int k = 0; k < 3; k++) col[k] += 255 * br;
                }
                buf.put((byte) Math.min(255, Math.round(col[0])));
                buf.put((byte) Math.min(255, Math.round(col[1])));
                buf.put((byte) Math.min(255, Math.round(col[2])));
                buf.put((byte) 255);
            }
        }
        buf.flip();
        int prevBound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int id = GlStateManager._genTexture();
        RenderSystem.bindTexture(id);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 10497);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 33071);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, W, H, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        RenderSystem.bindTexture(prevBound);
        return id;
    }

    public static void draw(Matrix4f m, Vec3d cam, float radius, long now) {
        Config c = Config.I;
        int style = Math.max(0, Math.min(3, c.skyStyle));
        if (ids[style] == 0) ids[style] = generate(style);

        int prevTex = RenderSystem.getShaderTexture(0);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, ids[style]);
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR);

        int g = Math.max(0, Math.min(255, Math.round(255 * c.skyBright)));
        int color = 0xFF000000 | (g << 16) | (g << 8) | g;
        float off = ((now % 2000000L) / 1000f) * 0.004f * c.skySpin;
        int lon = 48, lat = 24;
        for (int i = 0; i < lat; i++) {
            double p0 = Math.PI / 2 - i * Math.PI / lat, p1 = Math.PI / 2 - (i + 1) * Math.PI / lat;
            float v0 = i / (float) lat, v1 = (i + 1) / (float) lat;
            for (int j = 0; j < lon; j++) {
                double t0 = j * Math.PI * 2 / lon, t1 = (j + 1) * Math.PI * 2 / lon;
                float u0 = j / (float) lon + off, u1 = (j + 1) / (float) lon + off;
                float[] a = pt(cam, radius, p0, t0), bb = pt(cam, radius, p0, t1);
                float[] cc = pt(cam, radius, p1, t1), d = pt(cam, radius, p1, t0);
                v(b, m, a, u0, v0, color);
                v(b, m, bb, u1, v0, color);
                v(b, m, cc, u1, v1, color);
                v(b, m, a, u0, v0, color);
                v(b, m, cc, u1, v1, color);
                v(b, m, d, u0, v1, color);
            }
        }
        BuiltBuffer built = b.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        RenderSystem.setShaderTexture(0, prevTex);
    }

    private static float[] pt(Vec3d cam, float r, double phi, double th) {
        return new float[]{(float) (cam.x + r * Math.cos(phi) * Math.cos(th)),
                (float) (cam.y + r * Math.sin(phi)),
                (float) (cam.z + r * Math.cos(phi) * Math.sin(th))};
    }

    private static void v(BufferBuilder b, Matrix4f m, float[] p, float u, float vv, int color) {
        b.vertex(m, p[0], p[1], p[2]).texture(u, vv).color(color);
    }
}
