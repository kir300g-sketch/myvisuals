package com.example.visuals;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.fabricmc.loader.api.FabricLoader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * Звуки эффектов убийства и серии убийств. Файлы берутся из config/myvisuals/sounds (mp3, wav) и в мод не вшиты.
 * Номера: 0 Sun Strike, 1 коса, 2 первая кровь, 3 двойное, 4 тройное, 5 ультра кил, 6 буйство.
 */
public final class SoundFx {
    private SoundFx() {
    }

    public static final int SUNSTRIKE = 0, SCYTHE = 1;
    private static final Path[] files = new Path[7];
    private static final byte[][] pcm = new byte[7][];
    private static final AudioFormat[] fmt = new AudioFormat[7];
    private static boolean scanned = false;
    private static String lastError = "";

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("myvisuals").resolve("sounds");
    }

    private static int match(String name) {
        String n = name.toLowerCase();
        if (n.contains("sunstrike") || n.contains("sun_strike") || n.contains("sun strike")) return 0;
        if (n.contains("scythe") || n.contains("harvest") || n.contains("reaper")) return 1;
        if (n.contains("first") && n.contains("blood")) return 2;
        if (n.contains("double")) return 3;
        if (n.contains("triple")) return 4;
        if (n.contains("ultra")) return 5;
        if (n.contains("rampage")) return 6;
        return -1;
    }

    private static boolean audioExt(String name) {
        String l = name.toLowerCase();
        return l.endsWith(".mp3") || l.endsWith(".mpeg") || l.endsWith(".mpg") || l.endsWith(".mp2") || l.endsWith(".wav");
    }

    /** Файлы, перетащенные в окно меню: подходящие по названию копируются в папку звуков. */
    public static boolean handleDropped(List<Path> paths) {
        boolean any = false;
        for (Path p : paths) {
            String n = p.getFileName().toString();
            if (!audioExt(n) || match(n) < 0) continue;
            try {
                Files.createDirectories(dir());
                Files.copy(p, dir().resolve(n), StandardCopyOption.REPLACE_EXISTING);
                any = true;
            } catch (Exception e) {
                lastError = "не удалось скопировать";
            }
        }
        if (any) scan();
        return any;
    }

    public static void scan() {
        scanned = true;
        for (int i = 0; i < 7; i++) {
            files[i] = null;
            pcm[i] = null;
            fmt[i] = null;
        }
        try {
            Files.createDirectories(dir());
            try (Stream<Path> s = Files.list(dir())) {
                s.forEach(p -> {
                    String n = p.getFileName().toString();
                    int i = audioExt(n) ? match(n) : -1;
                    if (i >= 0 && files[i] == null) files[i] = p;
                });
            }
            lastError = "";
        } catch (Exception e) {
            lastError = "папка недоступна";
        }
    }

    public static String status() {
        if (!scanned) scan();
        int n = 0;
        for (Path p : files) if (p != null) n++;
        return "Найдено звуков: " + n + " из 7" + (lastError.isEmpty() ? "" : " (" + lastError + ")");
    }

    private static synchronized void decode(int idx, Path p) throws Exception {
        if (pcm[idx] != null) return;
        String l = p.getFileName().toString().toLowerCase();
        if (l.endsWith(".wav")) {
            try (AudioInputStream in = AudioSystem.getAudioInputStream(p.toFile())) {
                AudioFormat src = in.getFormat();
                AudioFormat dst = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16,
                        src.getChannels(), src.getChannels() * 2, src.getSampleRate(), false);
                try (AudioInputStream conv = AudioSystem.getAudioInputStream(dst, in)) {
                    pcm[idx] = conv.readAllBytes();
                    fmt[idx] = dst;
                }
            }
            return;
        }
        try (InputStream in = new BufferedInputStream(Files.newInputStream(p))) {
            Bitstream bs = new Bitstream(in);
            Decoder dec = new Decoder();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int rate = 44100, ch = 2;
            Header h;
            while ((h = bs.readFrame()) != null) {
                SampleBuffer sb = (SampleBuffer) dec.decodeFrame(h, bs);
                rate = sb.getSampleFrequency();
                ch = sb.getChannelCount();
                short[] buf = sb.getBuffer();
                int len = sb.getBufferLength();
                for (int i = 0; i < len; i++) {
                    out.write(buf[i] & 0xFF);
                    out.write((buf[i] >> 8) & 0xFF);
                }
                bs.closeFrame();
            }
            bs.close();
            pcm[idx] = out.toByteArray();
            fmt[idx] = new AudioFormat(rate, 16, ch, true, false);
        }
    }

    public static void play(int idx) {
        Config c = Config.I;
        if (idx < 0 || idx >= 7) return;
        if (!scanned) scan();
        Path p = files[idx];
        if (p == null) return;
        Thread t = new Thread(() -> {
            try {
                if (pcm[idx] == null) decode(idx, p);
                if (pcm[idx] == null || pcm[idx].length == 0) return;
                Clip clip = AudioSystem.getClip();
                clip.open(fmt[idx], pcm[idx], 0, pcm[idx].length);
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl g = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    float db = (float) (20.0 * Math.log10(Math.max(0.0001, 1.0)));
                    g.setValue(Math.max(g.getMinimum(), Math.min(g.getMaximum(), db)));
                }
                clip.addLineListener(ev -> {
                    if (ev.getType() == LineEvent.Type.STOP) clip.close();
                });
                clip.start();
            } catch (Throwable e) {
                lastError = "ошибка декодирования";
            }
        }, "myvisuals-sound");
        t.setDaemon(true);
        t.start();
    }
}
