package com.example.visuals;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Свой тотем: картинка (до 512x512) и звук срабатывания (.ogg).
 * Мод делает ресурспак, включает его и перезагружает ресурсы, перезаходить в игру не нужно.
 */
public final class Totem {
    private Totem() {
    }

    public static String status = "Файлы не загружены";

    private static final String PACK = "MyVisualsTotem";
    private static final int[] RES = {64, 128, 256, 512};
    private static BufferedImage image = null;

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("myvisuals");
    }

    public static Path file() {
        return dir().resolve("totem.png");
    }

    public static Path soundFile() {
        return dir().resolve("totem_sound.ogg");
    }

    private static boolean hasSound() {
        return Files.exists(soundFile());
    }

    private static Path packRoot() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("resourcepacks").resolve(PACK);
    }

    public static void tick(MinecraftClient mc) {
        // ничего делать не нужно: пак остаётся включённым между запусками
    }

    // ---------- загрузка файлов ----------
    public static void loadDropped(List<Path> paths) {
        for (Path p : paths) {
            String n = p.toString().toLowerCase();
            if (n.endsWith(".png")) {
                loadPicture(p, true);
                return;
            }
            if (n.endsWith(".ogg")) {
                loadSound(p);
                return;
            }
        }
        status = "Нужен .png (картинка) или .ogg (звук)";
    }

    public static void loadFromConfigDir() {
        try {
            boolean any = false;
            if (Files.exists(file())) {
                image = ImageIO.read(file().toFile());
                any = image != null;
            }
            if (hasSound()) any = true;
            if (!any) {
                status = "В config/myvisuals нет totem.png или totem_sound.ogg";
                return;
            }
            Config.I.totemEnabled = true;
            Config.save();
            apply();
        } catch (Exception e) {
            status = "Ошибка: " + e.getMessage();
        }
    }

    private static void loadPicture(Path p, boolean copy) {
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
            Config.I.totemEnabled = true;
            Config.save();
            apply();
        } catch (Exception e) {
            status = "Ошибка: " + e.getMessage();
        }
    }

    private static void loadSound(Path p) {
        try (InputStream in = Files.newInputStream(p)) {
            byte[] h = in.readNBytes(4);
            if (h.length < 4 || h[0] != 'O' || h[1] != 'g' || h[2] != 'g' || h[3] != 'S') {
                status = "Нужен OGG (Vorbis). Сконвертируй WAV/MP3 в .ogg";
                return;
            }
            Files.createDirectories(dir());
            if (!p.toAbsolutePath().equals(soundFile().toAbsolutePath())) {
                Files.copy(p, soundFile(), StandardCopyOption.REPLACE_EXISTING);
            }
            Config.I.totemEnabled = true;
            Config.save();
            apply();
        } catch (Exception e) {
            status = "Ошибка: " + e.getMessage();
        }
    }

    // ---------- управление ----------
    private static void ensureImage() throws IOException {
        if (image == null && Files.exists(file())) image = ImageIO.read(file().toFile());
    }

    private static void apply() {
        try {
            ensureImage();
            if (image == null && !hasSound()) {
                status = "Нечего применять: загрузи картинку или звук";
                return;
            }
            generatePack();
            enable(true);
        } catch (Exception e) {
            status = "Ошибка: " + e.getMessage();
        }
    }

    /** Перегенерировать пак (например, после смены разрешения). */
    public static void regenerate() {
        if (Config.I.totemEnabled) apply();
    }

    public static void setEnabled(boolean v) {
        Config.I.totemEnabled = v;
        Config.save();
        if (v) apply();
        else enable(false);
    }

    public static void resetPicture() {
        try {
            image = null;
            Files.deleteIfExists(file());
            afterReset();
        } catch (Exception e) {
            status = "Ошибка: " + e.getMessage();
        }
    }

    public static void resetSound() {
        try {
            Files.deleteIfExists(soundFile());
            afterReset();
        } catch (Exception e) {
            status = "Ошибка: " + e.getMessage();
        }
    }

    private static void afterReset() throws IOException {
        boolean hasPic = image != null || Files.exists(file());
        if (hasPic || hasSound()) {
            apply();
        } else {
            Config.I.totemEnabled = false;
            Config.save();
            enable(false);
            deleteRecursive(packRoot());
            status = "Сброшено";
        }
    }

    // ---------- генерация пака ----------
    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage cur = src;
        while (cur.getWidth() > w * 2 || cur.getHeight() > h * 2) {
            int nw = Math.max(w, cur.getWidth() / 2), nh = Math.max(h, cur.getHeight() / 2);
            cur = step(cur, nw, nh);
        }
        return step(cur, w, h);
    }

    private static BufferedImage step(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    private static void generatePack() throws IOException {
        Path root = packRoot();
        Files.createDirectories(root);
        Files.writeString(root.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":34,\"description\":\"My Visuals totem\"}}");

        Path tex = root.resolve("assets/minecraft/textures/item/totem_of_undying.png");
        String info = "";
        if (image != null) {
            int idx = Math.max(0, Math.min(RES.length - 1, Config.I.totemRes));
            int R = RES[idx];
            double s = Math.min(R / (double) image.getWidth(), R / (double) image.getHeight());
            int w = Math.max(1, (int) Math.round(image.getWidth() * s));
            int h = Math.max(1, (int) Math.round(image.getHeight() * s));
            BufferedImage fit = resize(image, w, h);
            BufferedImage sq = new BufferedImage(R, R, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = sq.createGraphics();
            g.drawImage(fit, (R - w) / 2, (R - h) / 2, null);
            g.dispose();
            Files.createDirectories(tex.getParent());
            ImageIO.write(sq, "png", tex.toFile());
            info += "картинка " + R + "x" + R + " ";
        } else {
            Files.deleteIfExists(tex);
        }

        Path snd = root.resolve("assets/minecraft/sounds/item/totem/use.ogg");
        if (hasSound()) {
            Files.createDirectories(snd.getParent());
            Files.copy(soundFile(), snd, StandardCopyOption.REPLACE_EXISTING);
            info += "звук ";
        } else {
            Files.deleteIfExists(snd);
        }
        status = "Пак создан: " + info + "(перезагрузка ресурсов...)";
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> w = Files.walk(root)) {
            List<Path> list = new ArrayList<>();
            w.sorted(Comparator.reverseOrder()).forEach(list::add);
            for (Path p : list) Files.deleteIfExists(p);
        }
    }

    // ---------- включение/выключение пака ----------
    private static void enable(boolean on) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ResourcePackManager rpm = mc.getResourcePackManager();
        rpm.scanPacks();
        String id = "file/" + PACK;
        List<String> list = new ArrayList<>();
        for (ResourcePackProfile p : rpm.getEnabledProfiles()) list.add(p.getId());
        boolean has = list.contains(id);
        if (on && !has) {
            if (rpm.getProfile(id) == null) {
                status = "Пак не найден в папке resourcepacks";
                return;
            }
            list.add(id);
        } else if (!on && has) {
            list.remove(id);
        }
        rpm.setEnabledProfiles(list);
        mc.options.resourcePacks.clear();
        for (ResourcePackProfile p : rpm.getEnabledProfiles()) {
            if (!p.isPinned()) mc.options.resourcePacks.add(p.getId());
        }
        mc.options.write();
        mc.reloadResources();
        status = on ? "Применено" : "Выключено";
    }
}
