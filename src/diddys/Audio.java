package diddys;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

/**
 * All sounds are preloaded into Clips. Each name has one clip, so replaying a cue restarts it rather
 * than stacking copies. Missing or unplayable sounds are skipped with a warning; the game runs silent.
 */
final class Audio {
    private final Map<String, Clip> clips = new HashMap<>();
    private final Map<String, Float> baseGain = new HashMap<>();
    private final Set<String> looping = new HashSet<>();
    private float volume = 0.8f;
    private boolean muted;

    Audio(Map<String, String> sounds, java.util.List<String> warnings) {
        for (Map.Entry<String, String> e : sounds.entrySet()) {
            String[] parts = e.getValue().trim().split("\\s+");
            try (InputStream raw = Audio.class.getResourceAsStream("/" + parts[0])) {
                if (raw == null) {
                    warnings.add("Sound missing (skipped): " + parts[0]);
                    continue;
                }
                AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(raw));
                Clip c = AudioSystem.getClip();
                c.open(in);
                clips.put(e.getKey(), c);
                baseGain.put(e.getKey(), parts.length > 1 ? Float.parseFloat(parts[1]) : 0f);
            } catch (Exception ex) {
                warnings.add("Sound unavailable (" + parts[0] + "): " + ex.getMessage());
            }
        }
        applyVolume();
    }

    /** Restart a one-shot sound from the beginning. */
    void play(String name) {
        Clip c = clips.get(name);
        if (c == null) return;
        c.stop();
        c.setFramePosition(0);
        c.start();
    }

    void loop(String name) {
        Clip c = clips.get(name);
        if (c == null || looping.contains(name)) return;
        looping.add(name);
        c.setFramePosition(0);
        c.loop(Clip.LOOP_CONTINUOUSLY);
    }

    void stop(String name) {
        Clip c = clips.get(name);
        looping.remove(name);
        if (c != null) c.stop();
    }

    void stopAll() {
        looping.clear();
        for (Clip c : clips.values()) c.stop();
    }

    /** Pause keeps loop positions; one-shots are simply cut. */
    void pause() {
        for (Clip c : clips.values()) c.stop();
    }

    void resume() {
        for (String n : looping) clips.get(n).loop(Clip.LOOP_CONTINUOUSLY);
    }

    void setVolume(float v) {
        volume = Math.max(0, Math.min(1, v));
        applyVolume();
    }

    void setMuted(boolean m) {
        muted = m;
        applyVolume();
    }

    private void applyVolume() {
        for (Map.Entry<String, Clip> e : clips.entrySet()) {
            Clip c = e.getValue();
            if (!c.isControlSupported(FloatControl.Type.MASTER_GAIN)) continue;
            FloatControl g = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
            float db = muted || volume <= 0.001f ? g.getMinimum()
                    : (float) (20 * Math.log10(volume)) + baseGain.get(e.getKey());
            g.setValue(Math.max(g.getMinimum(), Math.min(g.getMaximum(), db)));
        }
    }

    void close() {
        for (Clip c : clips.values()) c.close();
    }
}
