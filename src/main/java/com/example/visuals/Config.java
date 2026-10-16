package com.example.visuals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    // Лента-шлейф
    public boolean ribbonEnabled = true;
    public boolean trailRainbow = true;
    public float trailHue = 0.9f;
    public float ribbonLife = 1.5f;
    public float ribbonHeight = 0.4f;

    // Эффекты персонажа
    public boolean jumpEnabled = true;
    public float jumpSize = 1.8f;
    public boolean hatEnabled = false;
    public float hatSize = 1.0f;
    public boolean haloEnabled = false;
    public boolean fxRainbow = true;
    public float fxHue = 0.75f;

    // Мир: неоновый дождь и звёзды
    public boolean rainEnabled = false;
    public float rainSpeed = 1.5f;
    public int rainFreq = 8;
    public int rainDensity = 700;
    public float rainLength = 1.2f;

    public boolean starsEnabled = false;
    public float starSpeed = 1.0f;
    public int starFreq = 2;
    public int starDensity = 150;
    public float starSize = 0.25f;

    public boolean wxRainbow = true;
    public float wxHue = 0.6f;

    // Чанки
    public boolean chunkAnim = false;
    public float chunkDepth = 24f;
    public float chunkTime = 1.2f;

    // Меч
    public boolean swordEnabled = false;
    public int swordPreset = 0;
    public int swordAnim = 0;
    public float swordIntensity = 1f;
    public float swordCurve = 1f;
    public float swordScale = 1f;
    public float swordX = 0f, swordY = 0f, swordZ = 0f;
    public float swordRotX = 0f, swordRotY = 0f, swordRotZ = 0f;

    // Дизайн меню
    public int guiTheme = 0;
    public float guiAccentHue = 0.72f;
    public int guiBg = 0;
    public float guiAlpha = 0.95f;
    public int guiRadius = 8;
    public float guiAnimSpeed = 1f;
    public boolean guiRainbowLine = true;
    public float guiX = 0f, guiY = 0f;

    public static Config I = new Config();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("myvisuals.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) I = c;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(I));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
