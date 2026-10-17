package com.example.visuals;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Граффити-надписи: два слоя (контур с тенью и заливка), заливка красится при отрисовке. */
public final class GraffitiText {
    private GraffitiText() {
    }

    public static class Tex {
        public int outId, fillId, w, h;
    }

    private static final Map<String, Tex> cache = new LinkedHashMap<>(16, 0.75f, true);
    private static Font font = null;

    private static Font font() {
        if (font != null) return font;
        String[] pref = {"Ink Free", "Showcard Gothic", "Bauhaus 93", "Segoe Print", "Segoe Script",
                "Impact", "Arial Black", "Comic Sans MS"};
        try {
            Set<String> av = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String p : pref) {
                if (av.contains(p)) {
                    font = new Font(p, Font.BOLD, 150);
                    return font;
                }
            }
        } catch (Throwable ignored) {
        }
        font = new Font(Font.SANS_SERIF, Font.BOLD | Font.ITALIC, 150);
        return font;
    }

    public static Tex get(String text) {
        Tex t = cache.get(text);
        if (t != null) return t;
        t = make(text);
        cache.put(text, t);
        if (cache.size() > 40) {
            Iterator<Map.Entry<String, Tex>> it = cache.entrySet().iterator();
            Map.Entry<String, Tex> e = it.next();
            GlStateManager._deleteTexture(e.getValue().outId);
            GlStateManager._deleteTexture(e.getValue().fillId);
            it.remove();
        }
        return t;
    }

    private static Tex make(String text) {
        Font base = font();
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g0 = tmp.createGraphics();
        FontRenderContext frc = g0.getFontRenderContext();
        g0.dispose();

        // каждая буква чуть повёрнута и смещена: рукописный граффити-вид
        Path2D.Float path = new Path2D.Float();
        Random r = new Random(text.hashCode());
        float x = 0;
        for (char ch : text.toCharArray()) {
            if (ch == ' ') {
                x += base.getSize() * 0.32f;
                continue;
            }
            GlyphVector gv = base.createGlyphVector(frc, String.valueOf(ch));
            Shape s = gv.getOutline();
            Rectangle2D gb = gv.getLogicalBounds();
            double rot = (r.nextDouble() - 0.5) * 0.24;
            double sc = 0.95 + r.nextDouble() * 0.22;
            double dy = (r.nextDouble() - 0.5) * 16;
            AffineTransform at = new AffineTransform();
            at.translate(x, dy);
            at.rotate(rot, gb.getWidth() / 2, -base.getSize() * 0.3);
            at.scale(sc, sc);
            path.append(at.createTransformedShape(s), false);
            x += (float) gb.getWidth() * 0.9f;
        }

        Rectangle2D pb = path.getBounds2D();
        int pad = 30;
        int w = (int) Math.ceil(pb.getWidth() + pad * 2 + 14);
        int h = (int) Math.ceil(pb.getHeight() + pad * 2 + 14);
        AffineTransform shift = AffineTransform.getTranslateInstance(pad - pb.getX(), pad - pb.getY());
        Shape sh = shift.createTransformedShape(path);

        // слой 1: тень-экструзия + толстый чёрный контур
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(16f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 12; i >= 1; i--) {
            AffineTransform off = AffineTransform.getTranslateInstance(i * 0.7, i * 0.9);
            Shape os = off.createTransformedShape(sh);
            g.setColor(new Color(45, 10, 80));
            g.fill(os);
            g.draw(os);
        }
        g.setColor(Color.BLACK);
        g.draw(sh);
        g.fill(sh);
        g.dispose();

        // слой 2: светлая заливка с лёгким градиентом (красится при отрисовке)
        BufferedImage fill = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D f = fill.createGraphics();
        f.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        f.setPaint(new GradientPaint(0, pad, new Color(255, 255, 255), 0, h - pad, new Color(215, 215, 230)));
        f.fill(sh);
        f.setComposite(AlphaComposite.SrcAtop);
        f.setColor(new Color(255, 255, 255, 190));
        f.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        AffineTransform hi = AffineTransform.getTranslateInstance(-2, -2);
        f.draw(hi.createTransformedShape(sh));
        f.dispose();

        Tex t = new Tex();
        t.w = w;
        t.h = h;
        t.outId = upload(out);
        t.fillId = upload(fill);
        return t;
    }

    private static int upload(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = img.getRGB(x, y);
                buf.put((byte) ((p >> 16) & 255));
                buf.put((byte) ((p >> 8) & 255));
                buf.put((byte) (p & 255));
                buf.put((byte) ((p >>> 24) & 255));
            }
        }
        buf.flip();
        int id = GlStateManager._genTexture();
        RenderSystem.bindTexture(id);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 33071);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 33071);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        return id;
    }
}
