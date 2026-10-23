package com.example.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class VisualsScreen extends Screen {
    private static final int PW = 150, TITLE_H = 22, MOD_H = 20, SW = 380;

    private static class Module {
        final String name;
        final Supplier<Boolean> on;
        final Consumer<Boolean> setOn;
        final List<Widgets.Option> opts = new ArrayList<>();
        boolean expanded = false;
        float anim = 0f, onAnim = 0f, hover = 0f;
        int y = 0;

        Module(String name, Supplier<Boolean> on, Consumer<Boolean> setOn) {
            this.name = name;
            this.on = on;
            this.setOn = setOn;
        }
    }

    private static class Category {
        final String name;
        final List<Module> mods = new ArrayList<>();
        float scroll = 0f, scrollDisp = 0f, contentH = 0f;
        int vx, vy, vw, vh;
        boolean forced = false;

        Category(String name) {
            this.name = name;
        }
    }

    private final List<Category> cats = new ArrayList<>();
    private int[] order;
    private int[] pxA, pyA, phA;

    private float open = 0f;
    private boolean closing = false;
    private long last = Util.getMeasuringTimeMs();

    private int dragPanel = -1;
    private float dragOffX, dragOffY;
    private Category sbCat = null;
    private Widgets.Option dragOpt = null;
    private int hudDrag = -1;
    private float hudOffX, hudOffY;
    private boolean stdHeaderDrag = false;

    private int stdTab = 0;
    private float tabX = -1f;
    private float pageAnim = 1f;
    private float stdCurX = Float.NaN, stdCurY = 0f;
    private int stdX = 0, stdY = 0, stdH = 300;

    // ---------- построение категорий ----------
    private Category cat(String name) {
        Category c = new Category(name);
        cats.add(c);
        return c;
    }

    private Module mod(Category c, String name, Supplier<Boolean> on, Consumer<Boolean> set) {
        Module m = new Module(name, on, set);
        c.mods.add(m);
        return m;
    }

    public VisualsScreen() {
        super(Text.literal("Visuals"));
        Config c = Config.I;

        // ---- Combat ----
        Category combat = cat("Combat");
        Module sword = mod(combat, "Анимация меча", () -> c.swordEnabled, v -> c.swordEnabled = v);
        sword.opts.add(new Widgets.Choice("Анимация", SwordPose.ANIMS, () -> c.swordAnim, i -> c.swordAnim = i));
        sword.opts.add(new Widgets.Slider("Длительность (сек)", 0.15f, 1.2f, 2, () -> c.swordDuration, v -> c.swordDuration = v));
        sword.opts.add(new Widgets.Slider("Сила", 0.2f, 2f, 2, () -> c.swordIntensity, v -> c.swordIntensity = v));
        sword.opts.add(new Widgets.Choice("Положение", SwordPose.PRESETS, () -> c.swordPreset, i -> {
            c.swordPreset = i;
            SwordPose.applyPreset(i);
        }));
        sword.opts.add(new Widgets.Slider("Сдвиг X", -1f, 1f, 2, () -> c.swordX, v -> c.swordX = v));
        sword.opts.add(new Widgets.Slider("Сдвиг Y", -1f, 1f, 2, () -> c.swordY, v -> c.swordY = v));
        sword.opts.add(new Widgets.Slider("Сдвиг Z", -1f, 1f, 2, () -> c.swordZ, v -> c.swordZ = v));
        sword.opts.add(new Widgets.Slider("Поворот X", -180f, 180f, 0, () -> c.swordRotX, v -> c.swordRotX = v));
        sword.opts.add(new Widgets.Slider("Поворот Y", -180f, 180f, 0, () -> c.swordRotY, v -> c.swordRotY = v));
        sword.opts.add(new Widgets.Slider("Поворот Z", -180f, 180f, 0, () -> c.swordRotZ, v -> c.swordRotZ = v));
        sword.opts.add(new Widgets.Slider("Размер", 0.3f, 2f, 2, () -> c.swordScale, v -> c.swordScale = v));
        sword.opts.add(new Widgets.Action("Сбросить положение", () -> {
            c.swordPreset = 0;
            SwordPose.applyPreset(0);
        }));

        Module th = mod(combat, "Target HUD", () -> c.hudTarget.enabled, v -> c.hudTarget.enabled = v);
        th.opts.add(new Widgets.Header("Перетаскивай мышью"));
        th.opts.add(new Widgets.Slider("Размер", 0.6f, 2f, 2, () -> c.hudTarget.scale, v -> c.hudTarget.scale = v));
        th.opts.add(new Widgets.Slider("Высота", 0.8f, 1.6f, 2, () -> c.hudTarget.rowH, v -> c.hudTarget.rowH = v));
        th.opts.add(new Widgets.Slider("Время показа (сек)", 1f, 10f, 1, () -> c.targetShowSec, v -> c.targetShowSec = v));

        Module crit = mod(combat, "Крит-эффект", () -> c.critEnabled, v -> c.critEnabled = v);
        crit.opts.add(new Widgets.Choice("Эффект", new String[]{"Частицы", "Кровь", "Искры", "Ударная волна"}, () -> c.critFx, i -> c.critFx = i));
        crit.opts.add(new Widgets.Choice("Когда", new String[]{"Только крит", "Каждый удар"}, () -> c.critMode, i -> c.critMode = i));
        crit.opts.add(new Widgets.Choice("Форма частиц", new String[]{"Звёзды", "Шарики", "Сердечки", "Микс"}, () -> c.critShape, i -> c.critShape = i));
        crit.opts.add(new Widgets.Slider("Количество", 5, 100, 0, () -> (float) c.critCount, v -> c.critCount = Math.round(v)));
        crit.opts.add(new Widgets.Slider("Размер", 0.08f, 0.6f, 2, () -> c.critSize, v -> c.critSize = v));
        crit.opts.add(new Widgets.Toggle("Радужный цвет", () -> c.critRainbow, v -> c.critRainbow = v));
        crit.opts.add(new Widgets.HueSlider("Цвет", () -> c.critHue, v -> c.critHue = v));
        crit.opts.add(new Widgets.Toggle("Кровь радужная", () -> c.bloodRainbow, v -> c.bloodRainbow = v));
        crit.opts.add(new Widgets.HueSlider("Цвет крови", () -> c.bloodHue, v -> c.bloodHue = v));

        Module kill = mod(combat, "Эффект убийства", () -> c.killEnabled, v -> c.killEnabled = v);
        kill.opts.add(new Widgets.Header(() -> "Убийств: " + c.killCount));
        kill.opts.add(new Widgets.Choice("Эффект", new String[]{"Нет", "Коса", "Sun Strike", "Молот", "Пентаграмма", "Ядерный взрыв"}, () -> c.killFx, i -> c.killFx = i));
        kill.opts.add(new Widgets.Choice("Цели", new String[]{"Только игроки", "Игроки и мобы", "Всё кроме животных"}, () -> c.killTargets, i -> c.killTargets = i));
        kill.opts.add(new Widgets.Slider("Размер", 0.5f, 2.5f, 2, () -> c.killSize, v -> c.killSize = v));
        kill.opts.add(new Widgets.Toggle("Счётчик на месте смерти", () -> c.killCounter, v -> c.killCounter = v));
        kill.opts.add(new Widgets.Toggle("Радужный цвет", () -> c.killRainbow, v -> c.killRainbow = v));
        kill.opts.add(new Widgets.HueSlider("Цвет", () -> c.killHue, v -> c.killHue = v));
        kill.opts.add(new Widgets.Action("Сбросить счётчик", () -> {
            c.killCount = 0;
            Config.save();
        }));

        Module streak = mod(combat, "Серия убийств", () -> c.streakEnabled, v -> c.streakEnabled = v);
        streak.opts.add(new Widgets.Header("Первая кровь, двойное, тройное, ультра, буйство"));
        streak.opts.add(new Widgets.Slider("Серия держится (сек)", 10f, 300f, 0, () -> c.streakTime, v -> c.streakTime = v));
        streak.opts.add(new Widgets.Slider("Показ надписи (сек)", 1.5f, 6f, 1, () -> c.streakShow, v -> c.streakShow = v));
        streak.opts.add(new Widgets.Action("Сбросить серию", Streak::reset));

        Module trg = mod(combat, "Таргет-эффект", () -> c.trEnabled, v -> c.trEnabled = v);
        trg.opts.add(new Widgets.Header("Висит на цели после удара"));
        trg.opts.add(new Widgets.Choice("Стиль", new String[]{"Рамка", "Полумесяцы", "Ромбики", "Кольцо"}, () -> c.trStyle, i -> c.trStyle = i));
        trg.opts.add(new Widgets.Slider("Время (сек)", 2f, 30f, 0, () -> c.trTime, v -> c.trTime = v));
        trg.opts.add(new Widgets.Slider("Размер", 0.5f, 2.5f, 2, () -> c.trSize, v -> c.trSize = v));
        trg.opts.add(new Widgets.Slider("Скорость", 0.3f, 3f, 2, () -> c.trSpeed, v -> c.trSpeed = v));
        trg.opts.add(new Widgets.Toggle("Только игроки", () -> c.trPlayersOnly, v -> c.trPlayersOnly = v));
        trg.opts.add(new Widgets.Toggle("Радужный цвет", () -> c.trRainbow, v -> c.trRainbow = v));
        trg.opts.add(new Widgets.HueSlider("Цвет", () -> c.trHue, v -> c.trHue = v));

        Module totem = mod(combat, "Свой тотем", () -> c.totemEnabled, v -> Totem.setEnabled(v));
        totem.opts.add(new Widgets.Header(() -> Totem.status));
        totem.opts.add(new Widgets.Header("Перетащи в окно: PNG или OGG"));
        totem.opts.add(new Widgets.Choice("Разрешение", new String[]{"64", "128", "256", "512"}, () -> c.totemRes, i -> {
            c.totemRes = i;
            Totem.regenerate();
        }));
        totem.opts.add(new Widgets.Action("Взять файлы из config/myvisuals", Totem::loadFromConfigDir));
        totem.opts.add(new Widgets.Action("Сбросить картинку", Totem::resetPicture));
        totem.opts.add(new Widgets.Action("Сбросить звук", Totem::resetSound));

        Module tp = mod(combat, "Тест-игрок", () -> TestPlayer.active(), v -> {
            if (v) TestPlayer.spawn(MinecraftClient.getInstance());
            else TestPlayer.removeAll(MinecraftClient.getInstance());
        });
        tp.opts.add(new Widgets.Header("Виден только тебе"));
        tp.opts.add(new Widgets.Choice("Режим", new String[]{"Бессмертный", "Смертный"}, () -> c.testMortal ? 1 : 0, i -> c.testMortal = i == 1));
        tp.opts.add(new Widgets.Toggle("Копировать экипировку", () -> c.testCopyGear, v -> c.testCopyGear = v));
        tp.opts.add(new Widgets.Toggle("Возрождать после смерти", () -> c.testRespawn, v -> c.testRespawn = v));
        tp.opts.add(new Widgets.Slider("Дистанция", 1.5f, 8f, 1, () -> c.testDist, v -> c.testDist = v));
        tp.opts.add(new Widgets.Toggle("Защита от брони", () -> c.testArmor, v -> c.testArmor = v));
        tp.opts.add(new Widgets.Action("Призвать одного", () -> TestPlayer.spawn(MinecraftClient.getInstance())));
        tp.opts.add(new Widgets.Action("Добавить ещё бота", () -> TestPlayer.addBot(MinecraftClient.getInstance())));
        tp.opts.add(new Widgets.Action("Убрать всех", () -> TestPlayer.removeAll(MinecraftClient.getInstance())));

        Module traj = mod(combat, "Траектория", () -> c.trajEnabled, v -> c.trajEnabled = v);
        traj.opts.add(new Widgets.Toggle("Лук, арбалет, трезубец", () -> c.trajBow, v -> c.trajBow = v));
        traj.opts.add(new Widgets.Toggle("Жемчуг, снежки, яйца", () -> c.trajPearl, v -> c.trajPearl = v));
        traj.opts.add(new Widgets.Toggle("Маркер падения", () -> c.trajMarker, v -> c.trajMarker = v));
        traj.opts.add(new Widgets.Slider("Длина", 20, 300, 0, () -> (float) c.trajSteps, v -> c.trajSteps = Math.round(v)));
        traj.opts.add(new Widgets.Slider("Толщина", 0.002f, 0.03f, 3, () -> c.trajThick, v -> c.trajThick = v));
        traj.opts.add(new Widgets.Toggle("Радужный цвет", () -> c.trajRainbow, v -> c.trajRainbow = v));
        traj.opts.add(new Widgets.HueSlider("Цвет", () -> c.trajHue, v -> c.trajHue = v));

        // ---- Visuals ----
        Category vis = cat("Visuals");
        Module rib = mod(vis, "Лента-шлейф", () -> c.ribbonEnabled, v -> c.ribbonEnabled = v);
        rib.opts.add(new Widgets.Toggle("Радужный цвет", () -> c.trailRainbow, v -> c.trailRainbow = v));
        rib.opts.add(new Widgets.HueSlider("Цвет", () -> c.trailHue, v -> c.trailHue = v));
        rib.opts.add(new Widgets.Slider("Длина (сек)", 0.5f, 4f, 1, () -> c.ribbonLife, v -> c.ribbonLife = v));
        rib.opts.add(new Widgets.Slider("Высота", 0.1f, 1.5f, 2, () -> c.ribbonHeight, v -> c.ribbonHeight = v));

        Module circ = mod(vis, "Круги", () -> c.jumpEnabled, v -> c.jumpEnabled = v);
        circ.opts.add(new Widgets.Choice("Когда", new String[]{"Прыжок", "Приземление", "Оба"}, () -> c.jumpMode, i -> c.jumpMode = i));
        circ.opts.add(new Widgets.Slider("Размер", 0.8f, 3.5f, 1, () -> c.jumpSize, v -> c.jumpSize = v));
        circ.opts.add(new Widgets.Choice("Форма", new String[]{"Круг", "Сердце", "Звезда", "Пентаграмма"}, () -> c.jumpShape, i -> c.jumpShape = i));
        circ.opts.add(new Widgets.Toggle("Радужный цвет", () -> c.circRainbow, v -> c.circRainbow = v));
        circ.opts.add(new Widgets.HueSlider("Цвет", () -> c.circHue, v -> c.circHue = v));

        Module hat = mod(vis, "Китайская шляпа", () -> c.hatEnabled, v -> c.hatEnabled = v);
        hat.opts.add(new Widgets.Slider("Размер", 0.6f, 2f, 1, () -> c.hatSize, v -> c.hatSize = v));
        hat.opts.add(new Widgets.Toggle("Радужный цвет", () -> c.hatRainbow, v -> c.hatRainbow = v));
        hat.opts.add(new Widgets.HueSlider("Цвет", () -> c.hatHue, v -> c.hatHue = v));

        Module halo = mod(vis, "Нимб", () -> c.haloEnabled, v -> c.haloEnabled = v);
        halo.opts.add(new Widgets.Toggle("Радужный цвет", () -> c.haloRainbow, v -> c.haloRainbow = v));
        halo.opts.add(new Widgets.HueSlider("Цвет", () -> c.haloHue, v -> c.haloHue = v));

        Module combo = mod(vis, "Комбо", () -> c.comboEnabled, v -> c.comboEnabled = v);
        combo.opts.add(new Widgets.Header("10: cool, 20: holy shit, 30: unstoppable"));
        combo.opts.add(new Widgets.Toggle("Только криты", () -> c.comboCritOnly, v -> c.comboCritOnly = v));
        combo.opts.add(new Widgets.Slider("Размер", 0.5f, 3f, 2, () -> c.comboSize, v -> c.comboSize = v));
        combo.opts.add(new Widgets.Slider("Время жизни (сек)", 0.8f, 4f, 1, () -> c.comboLife, v -> c.comboLife = v));
        combo.opts.add(new Widgets.Slider("Сброс через (сек)", 1f, 6f, 1, () -> c.comboReset, v -> c.comboReset = v));
        combo.opts.add(new Widgets.Toggle("Сброс при уроне", () -> c.comboResetOnHurt, v -> c.comboResetOnHurt = v));
        combo.opts.add(new Widgets.Toggle("Огонь после 20", () -> c.comboFire, v -> c.comboFire = v));
        combo.opts.add(new Widgets.Toggle("Только игроки", () -> c.comboPlayersOnly, v -> c.comboPlayersOnly = v));
        combo.opts.add(new Widgets.Choice("Цвет", new String[]{"Авто", "Радуга", "Свой цвет"}, () -> c.comboColorMode, i -> c.comboColorMode = i));
        combo.opts.add(new Widgets.HueSlider("Свой оттенок", () -> c.comboHue, v -> c.comboHue = v));

        Module free = mod(vis, "Обзор 360", () -> c.freelookEnabled, v -> c.freelookEnabled = v);
        free.opts.add(new Widgets.Choice("Режим", new String[]{"Удержание", "Переключение"}, () -> c.freelookMode, i -> c.freelookMode = i));
        free.opts.add(new Widgets.Bind("Клавиша", () -> c.freelookKey, i -> c.freelookKey = i));
        free.opts.add(new Widgets.Toggle("Третье лицо при включении", () -> c.freelookThird, v -> c.freelookThird = v));

        Module fcam = mod(vis, "Фрикам", () -> c.freecamEnabled, v -> c.freecamEnabled = v);
        fcam.opts.add(new Widgets.Header("Фоторежим: не проходит сквозь стены"));
        fcam.opts.add(new Widgets.Bind("Клавиша", () -> c.freecamKey, i -> c.freecamKey = i));
        fcam.opts.add(new Widgets.Slider("Скорость", 0.2f, 4f, 1, () -> c.freecamSpeed, v -> c.freecamSpeed = v));

        Module arm = mod(vis, "Броня (HUD)", () -> c.hudArmor.enabled, v -> c.hudArmor.enabled = v);
        arm.opts.add(new Widgets.Header("Перетаскивай мышью"));
        arm.opts.add(new Widgets.Slider("Размер", 0.5f, 2.5f, 2, () -> c.hudArmor.scale, v -> c.hudArmor.scale = v));
        arm.opts.add(new Widgets.Slider("Высота", 0.6f, 2f, 2, () -> c.hudArmor.rowH, v -> c.hudArmor.rowH = v));

        Module pot = mod(vis, "Зелья (HUD)", () -> c.hudPotions.enabled, v -> c.hudPotions.enabled = v);
        pot.opts.add(new Widgets.Header("Перетаскивай мышью"));
        pot.opts.add(new Widgets.Slider("Размер", 0.5f, 2.5f, 2, () -> c.hudPotions.scale, v -> c.hudPotions.scale = v));
        pot.opts.add(new Widgets.Slider("Высота", 0.6f, 2f, 2, () -> c.hudPotions.rowH, v -> c.hudPotions.rowH = v));

        // ---- World ----
        Category world = cat("World");
        Module chunks = mod(world, "Подъём чанков", () -> c.chunkAnim, v -> c.chunkAnim = v);
        chunks.opts.add(new Widgets.Slider("Дистанция (чанки)", 2, 32, 0, () -> c.chunkDist, v -> c.chunkDist = v));
        chunks.opts.add(new Widgets.Slider("Глубина (блоки)", 4, 128, 0, () -> c.chunkDepth, v -> c.chunkDepth = v));
        chunks.opts.add(new Widgets.Slider("Время (сек)", 0.3f, 4f, 1, () -> c.chunkTime, v -> c.chunkTime = v));
        chunks.opts.add(new Widgets.Header("Не работает с Sodium"));

        mod(world, "Убрать туман", () -> c.fogRemove, v -> c.fogRemove = v);
        Module haze = mod(world, "Дымка", () -> c.hazeEnabled, v -> c.hazeEnabled = v);
        haze.opts.add(new Widgets.Toggle("Радужный цвет", () -> c.hazeRainbow, v -> c.hazeRainbow = v));
        haze.opts.add(new Widgets.HueSlider("Цвет", () -> c.hazeHue, v -> c.hazeHue = v));
        haze.opts.add(new Widgets.Slider("Дальность", 30, 400, 0, () -> c.hazeDist, v -> c.hazeDist = v));
        haze.opts.add(new Widgets.Slider("Густота", 0.1f, 1f, 2, () -> c.hazeDensity, v -> c.hazeDensity = v));

        Module sky = mod(world, "Небо", () -> c.skyEnabled, v -> c.skyEnabled = v);
        sky.opts.add(new Widgets.Choice("Стиль", SkyFx.STYLES, () -> c.skyStyle, i -> c.skyStyle = i));
        sky.opts.add(new Widgets.Choice("Момент рисования", new String[]{"До земли", "До сущностей", "После воды"}, () -> c.skyMode, i -> c.skyMode = i));
        sky.opts.add(new Widgets.Header("Breakcore мигает, осторожно"));
        sky.opts.add(new Widgets.Slider("Вращение", 0f, 2f, 2, () -> c.skySpin, v -> c.skySpin = v));
        sky.opts.add(new Widgets.Slider("Яркость", 0.3f, 1f, 2, () -> c.skyBright, v -> c.skyBright = v));

        Module time = mod(world, "Время суток", () -> c.timeEnabled, v -> c.timeEnabled = v);
        time.opts.add(new Widgets.Slider("Время (0-24000)", 0, 24000, 0, () -> c.timeOfDay, v -> c.timeOfDay = v));
        time.opts.add(new Widgets.Header("6000 день, 12500 закат, 18000 ночь"));

        // ---- Effects ----
        Category fx = cat("Effects");
        Module wfx = mod(fx, "Эффекты мира", null, null);
        wfx.opts.add(new Widgets.Choice("Эффект", new String[]{"Выкл", "Неоновый дождь", "Звёзды", "Сердечки", "Конфетти"},
                () -> c.worldFx, i -> c.worldFx = i));
        wfx.opts.add(new Widgets.Slider("Скорость", 0.2f, 4f, 2, () -> c.sel().speed, v -> c.sel().speed = v));
        wfx.opts.add(new Widgets.Slider("Частота", 1, 40, 0, () -> (float) c.sel().freq, v -> c.sel().freq = Math.round(v)));
        wfx.opts.add(new Widgets.Slider("Плотность", 20, 2000, 0, () -> (float) c.sel().density, v -> c.sel().density = Math.round(v)));
        wfx.opts.add(new Widgets.Slider("Размер", 0.08f, 0.8f, 2, () -> c.sel().size, v -> c.sel().size = v));
        wfx.opts.add(new Widgets.Slider("Длина капель", 0.3f, 3f, 2, () -> c.rainLength, v -> c.rainLength = v));
        wfx.opts.add(new Widgets.Toggle("Радужный цвет", () -> c.wxRainbow, v -> c.wxRainbow = v));
        wfx.opts.add(new Widgets.HueSlider("Оттенок", () -> c.wxHue, v -> c.wxHue = v));

        Module splash = mod(fx, "Круги от падений", () -> c.splashRings, v -> c.splashRings = v);
        splash.opts.add(new Widgets.Slider("Размер", 0.3f, 2f, 2, () -> c.splashSize, v -> c.splashSize = v));

        Module shake = mod(fx, "Анти-тряска", () -> c.antiShake, v -> c.antiShake = v);
        shake.opts.add(new Widgets.Header("Убирает тряску камеры при уроне"));
        shake.opts.add(new Widgets.Toggle("Без покачивания при ходьбе", () -> c.antiBob, v -> c.antiBob = v));

        mod(fx, "Физика предметов", () -> c.itemPhysics, v -> c.itemPhysics = v);
        mod(fx, "Убрать огонь на экране", () -> c.noFire, v -> c.noFire = v);
        Module wings = mod(fx, "Крылья", () -> c.wingsEnabled, v -> c.wingsEnabled = v);
        wings.opts.add(new Widgets.Choice("Стиль", new String[]{"Ангельские", "Демонические", "Неоновые", "Бабочка"}, () -> c.wingStyle, i -> c.wingStyle = i));
        wings.opts.add(new Widgets.Slider("Размер", 0.6f, 2.5f, 2, () -> c.wingSize, v -> c.wingSize = v));
        wings.opts.add(new Widgets.Slider("Скорость взмаха", 0.2f, 2.5f, 2, () -> c.wingSpeed, v -> c.wingSpeed = v));
        wings.opts.add(new Widgets.Toggle("Радужный цвет", () -> c.wingRainbow, v -> c.wingRainbow = v));
        wings.opts.add(new Widgets.HueSlider("Оттенок", () -> c.wingHue, v -> c.wingHue = v));

        Module wave = mod(fx, "Вейв от прыжка", () -> c.waveEnabled, v -> c.waveEnabled = v);
        wave.opts.add(new Widgets.Choice("Когда", new String[]{"Прыжок", "Приземление", "Оба"}, () -> c.waveMode, i -> c.waveMode = i));
        wave.opts.add(new Widgets.Slider("Скорость", 2f, 20f, 1, () -> c.waveSpeed, v -> c.waveSpeed = v));
        wave.opts.add(new Widgets.Slider("Радиус", 3f, 20f, 0, () -> c.waveRadius, v -> c.waveRadius = v));
        wave.opts.add(new Widgets.Slider("Ширина волны", 0.8f, 6f, 1, () -> c.waveWidth, v -> c.waveWidth = v));
        wave.opts.add(new Widgets.Slider("Размер клетки", 0.5f, 2f, 1, () -> c.waveCell, v -> c.waveCell = v));
        wave.opts.add(new Widgets.Slider("Заливка", 0f, 0.8f, 2, () -> c.waveFill, v -> c.waveFill = v));
        wave.opts.add(new Widgets.Slider("Эхо (волн)", 1f, 4f, 0, () -> (float) c.waveEchoes, v -> c.waveEchoes = Math.round(v)));
        wave.opts.add(new Widgets.Toggle("Радужный цвет", () -> c.waveRainbow, v -> c.waveRainbow = v));
        wave.opts.add(new Widgets.HueSlider("Цвет", () -> c.waveHue, v -> c.waveHue = v));

        Module smooth = mod(fx, "Плавная камера", () -> c.smoothEnabled, v -> c.smoothEnabled = v);
        smooth.opts.add(new Widgets.Slider("Плавность", 1f, 30f, 0, () -> c.smoothAmount, v -> c.smoothAmount = v));
        Module blur = mod(fx, "Размытие при повороте", () -> c.blurEnabled, v -> c.blurEnabled = v);
        blur.opts.add(new Widgets.Slider("Сила", 0.1f, 0.85f, 2, () -> c.blurStrength, v -> c.blurStrength = v));

        // ---- Interface ----
        Category ui = cat("Interface");
        Module menu = mod(ui, "Меню", null, null);
        menu.opts.add(new Widgets.Choice("Стиль", new String[]{"Плавающие окна", "Стандарт"}, () -> c.guiStyle, i -> c.guiStyle = i));
        menu.opts.add(new Widgets.Choice("Тема", Ui.THEMES, () -> c.guiTheme, i -> c.guiTheme = i));
        menu.opts.add(new Widgets.HueSlider("Свой цвет", () -> c.guiAccentHue, v -> {
            c.guiAccentHue = v;
            c.guiTheme = 6;
        }));
        menu.opts.add(new Widgets.Choice("Фон", Ui.BGS, () -> c.guiBg, i -> c.guiBg = i));
        menu.opts.add(new Widgets.Slider("Прозрачность", 0.5f, 1f, 2, () -> c.guiAlpha, v -> c.guiAlpha = v));
        menu.opts.add(new Widgets.Slider("Скругление", 0, 12, 0, () -> (float) c.guiRadius, v -> c.guiRadius = Math.round(v)));
        menu.opts.add(new Widgets.Slider("Скорость анимаций", 0.3f, 2.5f, 2, () -> c.guiAnimSpeed, v -> c.guiAnimSpeed = v));
        menu.opts.add(new Widgets.Toggle("Радужная линия", () -> c.guiRainbowLine, v -> c.guiRainbowLine = v));
        menu.opts.add(new Widgets.Action("Сбросить положение окон", () -> {
            for (int i = 0; i < c.panelX.length; i++) {
                c.panelX[i] = -1;
                c.panelY[i] = -1;
            }
            c.guiX = 0f;
            c.guiY = 0f;
        }));

        Module cur = mod(ui, "Курсор", null, null);
        cur.opts.add(new Widgets.Choice("Эффект", CursorFx.NAMES, () -> c.cursorFx, i -> c.cursorFx = i));
        cur.opts.add(new Widgets.Slider("Размер", 0.5f, 3f, 1, () -> c.cursorSize, v -> c.cursorSize = v));
        cur.opts.add(new Widgets.Slider("Плотность", 0.3f, 3f, 1, () -> c.cursorDensity, v -> c.cursorDensity = v));
        cur.opts.add(new Widgets.Toggle("Радужный цвет", () -> c.cursorRainbow, v -> c.cursorRainbow = v));
        cur.opts.add(new Widgets.HueSlider("Оттенок", () -> c.cursorHue, v -> c.cursorHue = v));

        order = new int[cats.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        pxA = new int[cats.size()];
        pyA = new int[cats.size()];
        phA = new int[cats.size()];
    }

    private static float clampf(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---------- высота и отрисовка категории ----------
    private float contentHeight(Category cat) {
        float h = 4;
        for (Module m : cat.mods) {
            int inner = 0;
            for (Widgets.Option o : m.opts) inner += o.h() + 2;
            h += MOD_H + 2 + Math.round((inner + 4) * m.anim);
        }
        return h;
    }

    private void renderCategory(DrawContext c, Category cat, int x, int top, int w, int vis, float a,
                                boolean forced, int mx, int my, float dt, float rawDt) {
        cat.forced = forced;
        float ch = contentHeight(cat);
        cat.vx = x;
        cat.vy = top;
        cat.vw = w;
        cat.vh = vis;
        cat.contentH = ch;
        float max = Math.max(0f, ch - vis);
        cat.scroll = clampf(cat.scroll, 0f, max);
        cat.scrollDisp = Ui.approach(cat.scrollDisp, cat.scroll, rawDt * 16f);
        int sbw = max > 0 ? 8 : 0;

        Widgets.Option.clipTop = top;
        Widgets.Option.clipBottom = top + vis;
        c.enableScissor(x, top, x + w, top + vis);

        int cy = top + 2 - Math.round(cat.scrollDisp);
        for (Module m : cat.mods) {
            m.anim = Ui.approach(m.anim, (forced || m.expanded) ? 1f : 0f, dt * 10f);
            boolean on = m.on != null && m.on.get();
            m.onAnim = Ui.approach(m.onAnim, on ? 1f : 0f, dt * 12f);
            m.y = cy;
            int rx = x + 4, rw = w - 8 - sbw;
            boolean hov = mx >= rx && mx <= rx + rw && my >= cy && my < cy + MOD_H && my >= top && my <= top + vis;
            m.hover = Ui.approach(m.hover, hov ? 1f : 0f, dt * 14f);

            if (cy + MOD_H > top && cy < top + vis) {
                int bg = Ui.mix(0x2A2A36, Ui.accent(), 0.6f * m.onAnim);
                Ui.rr(c, rx, cy, rx + rw, cy + MOD_H, 4, Ui.col(bg, (0.7f + 0.2f * m.hover) * a));
                float ta = 0.75f + 0.25f * Math.max(m.onAnim, m.hover);
                c.drawText(textRenderer, textRenderer.trimToWidth(m.name, rw - 22), rx + 7, cy + 6, Ui.col(0xFFFFFF, ta * a), false);
                if (!forced && !m.opts.isEmpty()) {
                    String ind = m.expanded ? "v" : ">";
                    c.drawText(textRenderer, ind, rx + rw - 10, cy + 6, Ui.col(0xFFFFFF, 0.55f * a), false);
                }
            }
            cy += MOD_H + 2;

            int inner = 0;
            for (Widgets.Option o : m.opts) inner += o.h() + 2;
            int block = Math.round((inner + 4) * m.anim);
            if (m.anim > 0.01f) {
                int oy = cy + 3;
                int sTop = Math.max(top, cy), sBot = Math.min(top + vis, cy + block);
                boolean visible = sBot > sTop;
                if (visible) c.enableScissor(x, sTop, x + w, sBot);
                for (Widgets.Option o : m.opts) {
                    o.x = x + 8;
                    o.w = w - 16 - sbw;
                    o.y = oy;
                    if (visible && o.y + o.h() > top && o.y < top + vis) {
                        o.render(c, textRenderer, mx, my, dt, a * m.anim);
                    }
                    oy += o.h() + 2;
                }
                if (visible) c.disableScissor();
            }
            cy += block;
        }
        c.disableScissor();

        if (max > 0) {
            int thumbH = Math.max(16, Math.round(vis * (vis / ch)));
            int thumbY = top + Math.round((vis - thumbH) * (cat.scrollDisp / max));
            Ui.rr(c, x + w - 7, top, x + w - 3, top + vis, 2, Ui.col(0xFFFFFF, 0.08f * a));
            Ui.rr(c, x + w - 7, thumbY, x + w - 3, thumbY + thumbH, 2, Ui.col(Ui.accent(), 0.9f * a));
        }
    }

    private void drawLine(DrawContext c, int x, int y, int w, long now, float a) {
        Config cfg = Config.I;
        float time = (now % 6000) / 6000f;
        int accent = Ui.accent();
        for (int i = 0; i < w; i += 3) {
            int rgb = cfg.guiRainbowLine ? Color.HSBtoRGB((i / (float) w + time) % 1f, 0.7f, 1f) & 0xFFFFFF : accent;
            c.fill(x + i, y, x + Math.min(w, i + 3), y + 2, Ui.col(rgb, 0.9f * a));
        }
    }

    // ---------- основной рендер ----------
    @Override
    public void render(DrawContext c, int mx, int my, float tickDelta) {
        Config cfg = Config.I;
        long now = Util.getMeasuringTimeMs();
        float rawDt = Math.min((now - last) / 1000f, 0.1f);
        last = now;
        float dt = rawDt * cfg.guiAnimSpeed;

        open += (closing ? -1 : 1) * dt * 4.5f;
        open = Math.max(0f, Math.min(1f, open));
        if (closing && open <= 0f) {
            this.client.setScreen(null);
            return;
        }
        float ease = 1f - (float) Math.pow(1f - open, 3);

        c.fill(0, 0, width, height, Ui.col(0x000000, 0.30f * ease));
        drawHudOutlines(c, now, ease);

        if (cfg.guiStyle == 0) {
            for (int k = 0; k < order.length; k++) {
                c.getMatrices().push();
                c.getMatrices().translate(0f, 0f, k * 40f);
                renderPanel(c, order[k], mx, my, dt, rawDt, ease, now);
                c.getMatrices().pop();
            }
        } else {
            renderStandard(c, mx, my, dt, rawDt, ease, now);
        }
    }

    private void renderPanel(DrawContext c, int i, int mx, int my, float dt, float rawDt, float ease, long now) {
        Config cfg = Config.I;
        Category cat = cats.get(i);
        if (cfg.panelX[i] < 0) {
            cfg.panelX[i] = Math.min(14 + i * (PW + 8), Math.max(0, width - PW - 4));
            cfg.panelY[i] = 16;
        }
        cfg.panelX[i] = clampf(cfg.panelX[i], 0, Math.max(0, width - PW));
        cfg.panelY[i] = clampf(cfg.panelY[i], 0, Math.max(0, height - TITLE_H - 4));
        int px = Math.round(cfg.panelX[i]);
        int py = Math.round(cfg.panelY[i]) + Math.round((1f - ease) * -20f);

        float ch = contentHeight(cat);
        int avail = Math.max(60, height - py - TITLE_H - 10);
        int vis = (int) Math.min(ch, avail);
        int ph = TITLE_H + vis + 6;
        pxA[i] = px;
        pyA[i] = py;
        phA[i] = ph;

        int radius = cfg.guiRadius;
        int accent = Ui.accent();
        int bg = Ui.bgColor();
        for (int s = 3; s >= 1; s--) {
            Ui.rr(c, px - s * 2, py - s * 2, px + PW + s * 2, py + ph + s * 2, radius + s * 2, Ui.col(0x000000, 0.06f * ease));
        }
        Ui.rr(c, px, py, px + PW, py + ph, radius, Ui.col(bg, cfg.guiAlpha * ease));
        int hdr = Ui.mix(bg, accent, 0.25f);
        Ui.rr(c, px, py, px + PW, py + TITLE_H, radius, Ui.col(hdr, ease));
        c.fill(px, py + radius, px + PW, py + TITLE_H, Ui.col(hdr, ease));
        int tw = textRenderer.getWidth(cat.name);
        c.drawText(textRenderer, cat.name, px + (PW - tw) / 2, py + 7, Ui.col(0xFFFFFF, ease), true);
        drawLine(c, px, py + TITLE_H, PW, now, ease);

        renderCategory(c, cat, px, py + TITLE_H + 3, PW, vis, ease, false, mx, my, dt, rawDt);
    }

    private void renderStandard(DrawContext c, int mx, int my, float dt, float rawDt, float ease, long now) {
        Config cfg = Config.I;
        pageAnim = Math.min(1f, pageAnim + dt * 5f);
        stdH = Math.min(height - 16, 400);
        float tX = (width - SW) / 2f + cfg.guiX;
        float tY = (height - stdH) / 2f + cfg.guiY;
        if (Float.isNaN(stdCurX)) {
            stdCurX = tX;
            stdCurY = tY;
        }
        float k = Math.min(1f, rawDt * 22f);
        stdCurX += (tX - stdCurX) * k;
        stdCurY += (tY - stdCurY) * k;
        int px = Math.round(stdCurX);
        int py = Math.round(stdCurY) + Math.round((1f - ease) * 26f);
        stdX = px;
        stdY = py;

        int radius = cfg.guiRadius;
        int accent = Ui.accent();
        int bg = Ui.bgColor();
        for (int s = 4; s >= 1; s--) {
            Ui.rr(c, px - s * 2, py - s * 2, px + SW + s * 2, py + stdH + s * 2, radius + s * 2, Ui.col(0x000000, 0.07f * ease));
        }
        Ui.rr(c, px, py, px + SW, py + stdH, radius, Ui.col(bg, cfg.guiAlpha * ease));
        int hdr = Ui.mix(bg, accent, 0.22f);
        Ui.rr(c, px, py, px + SW, py + 28, radius, Ui.col(hdr, ease));
        c.fill(px, py + radius, px + SW, py + 28, Ui.col(hdr, ease));
        c.drawText(textRenderer, "✦ Visuals", px + 12, py + 10, Ui.col(0xFFFFFF, ease), true);
        String hint = "тяни за заголовок";
        c.drawText(textRenderer, hint, px + SW - 12 - textRenderer.getWidth(hint), py + 10, Ui.col(0xFFFFFF, 0.35f * ease), false);
        drawLine(c, px, py + 28, SW, now, ease);

        int n = cats.size();
        int tw = (SW - 24) / n;
        int ty = py + 36;
        float target = px + 12 + stdTab * tw;
        if (tabX < 0) tabX = target;
        tabX = Ui.approach(tabX, target, rawDt * 14f);
        for (int i = 0; i < n; i++) {
            int tx = px + 12 + i * tw;
            boolean hov = mx >= tx && mx < tx + tw && my >= ty && my < ty + 16;
            float al = (i == stdTab ? 1f : (hov ? 0.85f : 0.55f)) * ease;
            String nm = cats.get(i).name;
            c.drawText(textRenderer, nm, tx + (tw - textRenderer.getWidth(nm)) / 2, ty + 2, Ui.col(0xFFFFFF, al), false);
        }
        c.fill(Math.round(tabX) + 6, ty + 14, Math.round(tabX) + tw - 6, ty + 16, Ui.col(accent, ease));

        int top = py + 60;
        int vis = stdH - 60 - 22;
        float pe = 1f - (float) Math.pow(1f - pageAnim, 3);
        renderCategory(c, cats.get(stdTab), px + 8, top, SW - 16, vis, ease * pe, true, mx, my, dt, rawDt);
        c.drawText(textRenderer, "Right Shift - закрыть", px + 12, py + stdH - 14, Ui.col(0x888899, ease), false);
    }

    private void drawHudOutlines(DrawContext c, long now, float ease) {
        float pulse = 0.5f + 0.3f * (float) Math.sin(now / 300.0);
        for (int id = 0; id < 3; id++) {
            if (!Hud.cfg(id).enabled) continue;
            int[] b = Hud.bounds(id, width, height);
            int col = Ui.col(Ui.accent(), pulse * ease);
            c.fill(b[0] - 1, b[1] - 1, b[0] + b[2] + 1, b[1], col);
            c.fill(b[0] - 1, b[1] + b[3], b[0] + b[2] + 1, b[1] + b[3] + 1, col);
            c.fill(b[0] - 1, b[1], b[0], b[1] + b[3], col);
            c.fill(b[0] + b[2], b[1], b[0] + b[2] + 1, b[1] + b[3], col);
            c.drawText(textRenderer, Hud.NAMES[id], b[0], b[1] - 10, Ui.col(0xFFFFFF, 0.7f * ease), false);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    // ---------- ввод ----------
    private boolean clickCategory(Category cat, double mx, double my, int button) {
        if (mx < cat.vx || mx > cat.vx + cat.vw || my < cat.vy || my > cat.vy + cat.vh) return false;
        float max = Math.max(0f, cat.contentH - cat.vh);
        if (max > 0 && mx >= cat.vx + cat.vw - 9 && button == 0) {
            sbCat = cat;
            scrollToMouse(cat, my);
            return true;
        }
        Widgets.Option.clipTop = cat.vy;
        Widgets.Option.clipBottom = cat.vy + cat.vh;
        int sbw = max > 0 ? 8 : 0;
        for (Module m : cat.mods) {
            if (my >= m.y && my < m.y + MOD_H && mx >= cat.vx + 4 && mx <= cat.vx + cat.vw - 4 - sbw) {
                if (button == 0 && m.setOn != null) {
                    m.setOn.accept(!m.on.get());
                    Config.save();
                } else if (!cat.forced) {
                    m.expanded = !m.expanded;
                }
                return true;
            }
            if ((m.expanded || cat.forced) && m.anim > 0.4f) {
                for (Widgets.Option o : m.opts) {
                    if (o.contains(mx, my)) {
                        if (o.isSlider() && button != 0) return true;
                        o.click(mx, my, button);
                        if (o.isSlider()) dragOpt = o;
                        else Config.save();
                        return true;
                    }
                }
            }
        }
        return true;
    }

    private void scrollToMouse(Category cat, double my) {
        float max = Math.max(0f, cat.contentH - cat.vh);
        if (max <= 0) return;
        int thumbH = Math.max(16, Math.round(cat.vh * (cat.vh / cat.contentH)));
        float r = (float) ((my - cat.vy - thumbH / 2.0) / Math.max(1, cat.vh - thumbH));
        cat.scroll = clampf(r * max, 0f, max);
        cat.scrollDisp = cat.scroll;
    }

    private void bringToFront(int i) {
        int idx = -1;
        for (int k = 0; k < order.length; k++) if (order[k] == i) idx = k;
        if (idx < 0) return;
        for (int k = idx; k < order.length - 1; k++) order[k] = order[k + 1];
        order[order.length - 1] = i;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (closing) return super.mouseClicked(mx, my, button);
        if (Widgets.Bind.listening != null && button >= 2) {
            Widgets.Bind.listening.accept(-1 - button);
            Config.save();
            return true;
        }
        Config cfg = Config.I;

        if (cfg.guiStyle == 0) {
            for (int k = order.length - 1; k >= 0; k--) {
                int i = order[k];
                if (mx >= pxA[i] && mx <= pxA[i] + PW && my >= pyA[i] && my <= pyA[i] + phA[i]) {
                    bringToFront(i);
                    if (button == 0 && my < pyA[i] + TITLE_H) {
                        dragPanel = i;
                        dragOffX = (float) mx - cfg.panelX[i];
                        dragOffY = (float) my - cfg.panelY[i];
                    } else {
                        clickCategory(cats.get(i), mx, my, button);
                    }
                    return true;
                }
            }
        } else {
            if (mx >= stdX && mx <= stdX + SW && my >= stdY && my <= stdY + stdH) {
                if (button == 0 && my < stdY + 30) {
                    stdHeaderDrag = true;
                    return true;
                }
                int n = cats.size();
                int tw = (SW - 24) / n;
                int ty = stdY + 36;
                if (button == 0) {
                    for (int i = 0; i < n; i++) {
                        int tx = stdX + 12 + i * tw;
                        if (mx >= tx && mx < tx + tw && my >= ty && my < ty + 16) {
                            if (stdTab != i) {
                                stdTab = i;
                                pageAnim = 0f;
                            }
                            return true;
                        }
                    }
                }
                clickCategory(cats.get(stdTab), mx, my, button);
                return true;
            }
        }

        if (button == 0) {
            for (int id = 0; id < 3; id++) {
                if (!Hud.cfg(id).enabled) continue;
                int[] b = Hud.bounds(id, width, height);
                if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {
                    hudDrag = id;
                    hudOffX = (float) mx - b[0];
                    hudOffY = (float) my - b[1];
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        Config cfg = Config.I;
        if (dragPanel >= 0) {
            cfg.panelX[dragPanel] = clampf((float) mx - dragOffX, 0, Math.max(0, width - PW));
            cfg.panelY[dragPanel] = clampf((float) my - dragOffY, 0, Math.max(0, height - TITLE_H - 4));
            return true;
        }
        if (stdHeaderDrag) {
            float maxX = Math.max(0f, (width - SW) / 2f);
            float maxY = Math.max(0f, (height - stdH) / 2f);
            cfg.guiX = clampf(cfg.guiX + (float) dx, -maxX, maxX);
            cfg.guiY = clampf(cfg.guiY + (float) dy, -maxY, maxY);
            return true;
        }
        if (sbCat != null) {
            scrollToMouse(sbCat, my);
            return true;
        }
        if (dragOpt != null) {
            dragOpt.drag(mx);
            return true;
        }
        if (hudDrag >= 0) {
            Config.HudCfg h = Hud.cfg(hudDrag);
            int[] b = Hud.bounds(hudDrag, width, height);
            float nx = ((float) mx - hudOffX) / width;
            float ny = ((float) my - hudOffY) / height;
            h.x = clampf(nx, 0f, Math.max(0f, 1f - b[2] / (float) width));
            h.y = clampf(ny, 0f, Math.max(0f, 1f - b[3] / (float) height));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = dragPanel >= 0 || stdHeaderDrag || sbCat != null || dragOpt != null || hudDrag >= 0;
        dragPanel = -1;
        stdHeaderDrag = false;
        sbCat = null;
        dragOpt = null;
        hudDrag = -1;
        if (was) Config.save();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        Config cfg = Config.I;
        Category target = null;
        if (cfg.guiStyle == 0) {
            for (int k = order.length - 1; k >= 0 && target == null; k--) {
                int i = order[k];
                if (mx >= pxA[i] && mx <= pxA[i] + PW && my >= pyA[i] && my <= pyA[i] + phA[i]) target = cats.get(i);
            }
        } else {
            target = cats.get(stdTab);
        }
        if (target != null) {
            float max = Math.max(0f, target.contentH - target.vh);
            if (max > 0) {
                target.scroll = clampf(target.scroll - (float) vertical * 28f, 0f, max);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    // перетаскивание PNG в окно (названия метода различаются между версиями, поэтому оба)
    public void filesDragged(List<Path> paths) {
        Totem.loadDropped(paths);
    }

    public void onFilesDropped(List<Path> paths) {
        Totem.loadDropped(paths);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (Widgets.Bind.listening != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) Widgets.Bind.listening = null;
            else Widgets.Bind.listening.accept(key);
            Config.save();
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void close() {
        if (!closing) {
            closing = true;
            Config.save();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
