package com.example.visuals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    public static class Fall {
        public boolean enabled = false;
        public float speed = 1f;
        public int freq = 2;
        public int density = 150;
        public float size = 0.25f;

        public Fall() {
        }

        public Fall(boolean enabled, float speed, int freq, int density, float size) {
            this.enabled = enabled;
            this.speed = speed;
            this.freq = freq;
            this.density = density;
            this.size = size;
        }
    }

    public static class HudCfg {
        public boolean enabled = false;
        public float x = 0.5f, y = 0.5f;
        public float scale = 1f;
        public float rowH = 1f;

        public HudCfg() {
        }

        public HudCfg(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    // Лента
    public boolean ribbonEnabled = true;
    public boolean trailRainbow = true;
    public float trailHue = 0.9f;
    public float ribbonLife = 1.5f;
    public float ribbonHeight = 0.4f;

    // Круги
    public boolean jumpEnabled = true;
    public int jumpMode = 1; // 0 прыжок, 1 приземление, 2 оба
    public float jumpSize = 1.8f;
    public boolean splashRings = true;
    public float splashSize = 0.8f;

    // Шляпа, нимб, общий цвет
    public boolean hatEnabled = false;
    public float hatSize = 1.0f;
    public boolean haloEnabled = false;
    public boolean fxRainbow = true;
    public float fxHue = 0.75f;

    // Падающие эффекты
    public Fall rain = new Fall(false, 1.5f, 8, 700, 0.25f);
    public Fall stars = new Fall(false, 1f, 3, 180, 0.25f);
    public Fall hearts = new Fall(false, 1f, 2, 120, 0.28f);
    public Fall confetti = new Fall(false, 1f, 3, 250, 0.14f);
    public float rainLength = 1.2f;
    public boolean wxRainbow = true;
    public float wxHue = 0.6f;

    // Выбор эффекта мира (0 выкл, 1 дождь, 2 звёзды, 3 сердечки, 4 конфетти)
    public int worldFx = 0;

    public Fall sel() {
        switch (worldFx) {
            case 2:
                return stars;
            case 3:
                return hearts;
            case 4:
                return confetti;
            default:
                return rain;
        }
    }

    // Анти-тряска
    public boolean antiShake = false;
    public boolean antiBob = false;

    // Крит-эффект
    public boolean critEnabled = false;
    public int critMode = 0;
    public int critShape = 3;
    public int critCount = 30;
    public float critSize = 0.2f;

    // Цвета отдельных эффектов
    public boolean circRainbow = true;
    public float circHue = 0.75f;
    public boolean hatRainbow = true;
    public float hatHue = 0.75f;
    public boolean haloRainbow = true;
    public float haloHue = 0.12f;
    public boolean critRainbow = true;
    public float critHue = 0.08f;
    public boolean bloodRainbow = false;
    public float bloodHue = 0.0f;

    // Крит-эффект: какой именно (0 частицы, 1 кровь, 2 искры, 3 ударная волна)
    public int critFx = 0;

    // Комбо: дополнительные настройки
    public boolean comboCritOnly = true;
    public int comboColorMode = 0; // 0 авто, 1 радуга, 2 свой цвет
    public float comboHue = 0.9f;

    // Эффект убийства и счётчик
    public boolean killEnabled = false;
    public int killFx = 1; // 0 нет, 1 коса
    public float killSize = 1f;
    public boolean killRainbow = false;
    public float killHue = 0.38f;
    public boolean killCounter = true;
    public int killCount = 0;

    // Траектория выстрела
    public boolean trajEnabled = false;
    public boolean trajBow = true;
    public boolean trajPearl = true;
    public boolean trajMarker = true;
    public boolean trajRainbow = false;
    public float trajHue = 0.55f;
    public float trajThick = 0.008f;
    public int trajSteps = 160;

    // Обзор 360
    public boolean freelookEnabled = false;
    public int freelookMode = 0; // 0 удержание, 1 переключение
    public int freelookKey = 342; // Left Alt (GLFW_KEY_LEFT_ALT); отрицательные значения: кнопки мыши
    public boolean freelookThird = true;

    // Цели для эффекта убийства и счётчика: 0 только игроки, 1 игроки и мобы, 2 всё кроме животных
    public int killTargets = 1;

    public boolean killGhost = false;

    // Физика предметов
    public boolean itemPhysics = false;

    // Тест-игрок
    public boolean testMortal = false;
    public boolean testCopyGear = true;
    public boolean testRespawn = true;
    public float testDist = 3f;

    // Форма кругов (0 круг, 1 сердце, 2 звезда, 3 пентаграмма)
    public int jumpShape = 0;

    // Небо: момент отрисовки (0 до земли, 1 до сущностей, 2 после воды)
    public int skyMode = 0;

    // Окружение
    public boolean noFire = false;
    public boolean timeEnabled = false;
    public float timeOfDay = 6000f;

    // Фрикам
    public boolean freecamEnabled = false;
    public int freecamKey = 295; // F6
    public float freecamSpeed = 1f;

    // Курсор
    public int cursorFx = 0;
    public float cursorSize = 1f;
    public float cursorDensity = 1f;
    public boolean cursorRainbow = true;
    public float cursorHue = 0.8f;

    // Крылья
    public boolean wingsEnabled = false;
    public int wingStyle = 0;
    public float wingSize = 1.2f;
    public boolean wingRainbow = false;
    public float wingHue = 0.55f;
    public float wingSpeed = 0.8f;

    // Серия убийств
    public boolean streakEnabled = false;
    public float streakTime = 60f;
    public float streakShow = 3.2f;

    public boolean testArmor = true;

    // Таргет-эффект
    public boolean trEnabled = false;
    public int trStyle = 0;
    public float trTime = 10f;
    public float trSize = 1f;
    public float trSpeed = 1f;
    public boolean trPlayersOnly = false;
    public boolean trRainbow = false;
    public float trHue = 0.58f;

    // Вейв от прыжка
    public boolean waveEnabled = false;
    public int waveMode = 0;
    public float waveSpeed = 8f;
    public float waveRadius = 10f;
    public float waveWidth = 2.2f;
    public float waveCell = 1f;
    public float waveFill = 0.3f;
    public int waveEchoes = 2;
    public boolean waveRainbow = false;
    public float waveHue = 0.52f;

    // Плавность и размытие камеры
    public boolean smoothEnabled = false;
    public float smoothAmount = 8f;
    public boolean blurEnabled = false;
    public float blurStrength = 0.45f;

    // Комбо
    public boolean comboEnabled = false;
    public float comboSize = 1.2f;
    public float comboLife = 1.6f;
    public float comboReset = 2.5f;
    public boolean comboResetOnHurt = true;
    public boolean comboFire = true;
    public boolean comboPlayersOnly = false;

    // Небо
    public boolean skyEnabled = false;
    public int skyStyle = 0;
    public float skySpin = 0.3f;
    public float skyBright = 1f;

    // Тотем
    public int totemRes = 2;

    // Чанки
    public boolean chunkAnim = false;
    public float chunkDist = 8f;
    public float chunkDepth = 40f;
    public float chunkTime = 1.2f;

    // Туман и дымка
    public boolean fogRemove = false;
    public boolean hazeEnabled = false;
    public boolean hazeRainbow = false;
    public float hazeHue = 0.6f;
    public float hazeDist = 120f;
    public float hazeDensity = 0.6f;

    // Меч
    public boolean swordEnabled = false;
    public int swordPreset = 0;
    public int swordAnim = 0;
    public float swordIntensity = 1f;
    public float swordDuration = 0.4f;
    public float swordScale = 1f;
    public float swordX = 0f, swordY = 0f, swordZ = 0f;
    public float swordRotX = 0f, swordRotY = 0f, swordRotZ = 0f;

    // Тотем
    public boolean totemEnabled = false;

    // HUD
    public HudCfg hudTarget = new HudCfg(0.5f, 0.74f);
    public HudCfg hudArmor = new HudCfg(0.02f, 0.35f);
    public HudCfg hudPotions = new HudCfg(0.02f, 0.10f);
    public float targetShowSec = 4f;

    // Меню
    public int guiStyle = 0; // 0 окна, 1 стандарт
    public int guiTheme = 0;
    public float guiAccentHue = 0.72f;
    public int guiBg = 0;
    public float guiAlpha = 0.95f;
    public int guiRadius = 6;
    public float guiAnimSpeed = 1f;
    public boolean guiRainbowLine = true;
    public float guiX = 0f, guiY = 0f;
    public float[] panelX = {-1, -1, -1, -1, -1, -1};
    public float[] panelY = {-1, -1, -1, -1, -1, -1};

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
                if (c != null) {
                    if (c.panelX == null || c.panelX.length < 6) c.panelX = new float[]{-1, -1, -1, -1, -1, -1};
                    if (c.panelY == null || c.panelY.length < 6) c.panelY = new float[]{-1, -1, -1, -1, -1, -1};
                    if (c.rain == null) c.rain = new Fall(false, 1.5f, 8, 700, 0.25f);
                    if (c.stars == null) c.stars = new Fall(false, 1f, 3, 180, 0.25f);
                    if (c.hearts == null) c.hearts = new Fall(false, 1f, 2, 120, 0.28f);
                    if (c.confetti == null) c.confetti = new Fall(false, 1f, 3, 250, 0.14f);
                    if (c.hudTarget == null) c.hudTarget = new HudCfg(0.5f, 0.74f);
                    if (c.hudArmor == null) c.hudArmor = new HudCfg(0.02f, 0.35f);
                    if (c.hudPotions == null) c.hudPotions = new HudCfg(0.02f, 0.10f);
                    I = c;
                }
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
