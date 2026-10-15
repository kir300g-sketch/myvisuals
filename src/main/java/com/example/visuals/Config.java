package com.example.visuals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    // Аура
    public boolean auraEnabled = true;
    public boolean auraRainbow = false;
    public float auraHue = 0.55f;
    public float auraRadius = 1.0f;
    public float auraSpeed = 0.15f;
    public int auraCount = 3;

    // След
    public boolean trailEnabled = true;
    public boolean trailRainbow = true;
    public float trailHue = 0.9f;
    public float trailSize = 1.0f;
    public int trailDensity = 2;

    // Мир
    public boolean snowEnabled = false;
    public int snowDensity = 6;
    public boolean fireflies = false;
    public boolean timeOverride = false;
    public float timeOfDay = 18000f;

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
