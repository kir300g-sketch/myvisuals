package com.example.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Подмена текстуры тотема бессмертия прямо в атласе, без перезахода. */
public final class Totem {
    private Totem() {
    }

    public static String status = "Картинка не загружена";

    private static BufferedImage image = null;
    private static Object lastSprite = null;
    private static boolean tried = false;

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("myvisuals");
    }

    public static Path file() {
        return dir().resolve("totem.png");
    }

    public static void loadDropped(List<Path> paths) {
        for (Path p : paths) {
            if (p.toString().toLowerCase().endsWith(".png")) {
                loadPath(p, true);
                return;
            }
        }
        status = "Нужен файл .png";
    }

    public static void loadFromConfigDir() {
        if (Files.exists(file())) loadPath(file(), false);
        else status = "Нет файла config/myvisuals/totem.png";
    }

    private static void loadPath(Path p, boolean copy) {
        try {
            BufferedImage img = ImageIO.read(p.toFile());
            if (img == null) {
                status = "Не удалось прочитать картинку";
                return;
            }
            if (copy) {
                Files.createDirectories(dir());
                if (!p.toAbsolutePath().equals(file().toAbsolutePath())) {
                    Files.copy(p, file(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            image = img;
            lastSprite = null;
            Config.I.totemEnabled = true;
            Config.save();
            status = "Тотем загружен";
        } catch (Exception e) {
            status = "Ошибка: " + e.getMessage();
        }
    }

    public static void reset() {
        image = null;
        lastSprite = null;
        Config.I.totemEnabled = false;
        Config.save();
        status = "Сброшено, ресурсы перезагружаются";
        MinecraftClient.getInstance().reloadResources();
    }

    public static void tick(MinecraftClient mc) {
        if (!Config.I.totemEnabled) return;
        if (image == null && !tried) {
            tried = true;
            if (Files.exists(file())) loadPath(file(), false);
        }
        if (image == null) return;
        try {
            SpriteAtlasTexture atlas = mc.getBakedModelManager().getAtlas(Identifier.of("minecraft", "textures/atlas/blocks.png"));
            if (atlas == null) return;
            Sprite sprite = atlas.getSprite(Identifier.of("minecraft", "item/totem_of_undying"));
            if (sprite != lastSprite) {
                lastSprite = sprite;
                apply(mc, atlas, sprite);
            }
        } catch (Exception e) {
            status = "Ошибка применения: " + e;
        }
    }

    private static void apply(MinecraftClient mc, SpriteAtlasTexture atlas, Sprite sprite) {
        int sw = sprite.getContents().getWidth();
        int sh = sprite.getContents().getHeight();
        int mips = mc.options.getMipmapLevels().getValue();

        RenderSystem.bindTexture(atlas.getGlId());
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        for (int lvl = 0; lvl <= mips; lvl++) {
            int w = sw >> lvl, h = sh >> lvl;
            if (w < 1 || h < 1) break;
            ByteBuffer buf = toBuffer(image, w, h);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, lvl, sprite.getX() >> lvl, sprite.getY() >> lvl,
                    w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        }
        status = "Тотем применён";
    }

    private static ByteBuffer toBuffer(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = dst.getRGB(x, y);
                buf.put((byte) ((p >> 16) & 255));
                buf.put((byte) ((p >> 8) & 255));
                buf.put((byte) (p & 255));
                buf.put((byte) ((p >>> 24) & 255));
            }
        }
        buf.flip();
        return buf;
    }
}
