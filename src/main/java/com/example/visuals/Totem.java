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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Свой тотем: мод сам делает ресурспак с картинкой выбранного разрешения (до 512x512),
 * включает его и перезагружает ресурсы. Тотем сохраняет чёткость везде: в инвентаре, в руке, при срабатывании.
 */
public final class Totem {
    private Totem() {
    }

    public static String status = "Картинка не загружена";

    private static final String PACK = "MyVisualsTotem";
    private static final int[] RES = {64, 128, 256, 512};
    private static BufferedImage image = null;

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("myvisuals");
    }

    public static Path file() {
        return dir().resolve("totem.png");
    }

    private static Path packRoot() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("resourcepacks").resolve(PACK);
    }

    public static void tick(MinecraftClient mc) {
        // ничего делать не нужно: пак остаётся включённым между запусками
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
            Config.I.totemEnabled = true;
            Config.save();
            generatePack();
            enable(true);
        } catch (Exception e) {
            status = "Ошибка: " + e.getMessage();
        }
    }

    /** Перегенерировать пак (например, после смены разрешения). */
    public static void regenerate() {
        try {
            if (image == null && Files.exists(file())) image = ImageIO.read(file().toFile());
            if (image == null) {
                status = "Сначала загрузи картинку";
                return;
            }
            generatePack();
            if (Config.I.totemEnabled) enable(true);
        } catch (Exception e) {
            status = "Ошибка: " + e.getMessage();
        }
    }

    public static void setEnabled(boolean v) {
        Config.I.totemEnabled = v;
        Config.save();
        try {
            if (v) {
                if (image == null && Files.exists(file())) image = ImageIO.read(file().toFile());
                if (image == null) {
                    status = "Сначала загрузи картинку";
                    Config.I.totemEnabled = false;
                    return;
                }
                generatePack();
                enable(true);
            } else {
                enable(false);
            }
        } catch (Exception e) {
            status = "Ошибка: " + e.getMessage();
        }
    }

    public static void reset() {
        image = null;
        Config.I.totemEnabled = false;
        Config.save();
        try {
            enable(false);
            Files.deleteIfExists(file());
            deleteRecursive(packRoot());
            status = "Сброшено";
        } catch (Exception e) {
            status = "Ошибка: " + e.getMessage();
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

        Path root = packRoot();
        Path tex = root.resolve("assets/minecraft/textures/item/totem_of_undying.png");
        Files.createDirectories(tex.getParent());
        Files.writeString(root.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":34,\"description\":\"My Visuals totem\"}}");
        ImageIO.write(sq, "png", tex.toFile());
        status = "Пак создан (" + R + "x" + R + "), перезагрузка ресурсов...";
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
        status = on ? "Тотем применён" : "Тотем выключен";
    }
}
