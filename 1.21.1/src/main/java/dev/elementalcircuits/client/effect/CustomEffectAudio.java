package dev.elementalcircuits.client.effect;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Plays imported PCM WAV cues locally without registering Minecraft sound events. */
public final class CustomEffectAudio {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ElementalCircuits-CustomAudio");
        thread.setDaemon(true);
        return thread;
    });
    private static final ArrayDeque<Clip> ACTIVE = new ArrayDeque<>();

    private CustomEffectAudio() {}

    public static void play(byte[] wave) {
        if (wave == null || wave.length == 0) return;
        WORKER.execute(() -> start(wave));
    }

    private static void start(byte[] wave) {
        Clip clip = null;
        try (AudioInputStream source = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wave))) {
            AudioFormat input = source.getFormat();
            AudioFormat output = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    input.getSampleRate(), 16, input.getChannels(), input.getChannels() * 2,
                    input.getSampleRate(), false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(output, source)) {
                byte[] samples = pcm.readAllBytes();
                clip = AudioSystem.getClip();
                clip.open(output, samples, 0, samples.length);
                Clip active = clip;
                synchronized (ACTIVE) {
                    while (ACTIVE.size() >= 8) {
                        Clip old = ACTIVE.removeFirst();
                        old.stop();
                        old.close();
                    }
                    ACTIVE.addLast(active);
                }
                active.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        synchronized (ACTIVE) { ACTIVE.remove(active); }
                        active.close();
                    }
                });
                active.start();
            }
        } catch (Exception ignored) {
            if (clip != null) clip.close();
        }
    }
}
