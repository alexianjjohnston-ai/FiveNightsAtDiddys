import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

/**
 * Generates the original (non-photo) assets the supplied files don't cover:
 *   audio/power_down.wav      - PLACEHOLDER power-failure cue
 *   audio/chime_6am.wav       - PLACEHOLDER 6 AM chime
 * Usage: java tools/MakeAssets.java assets
 */
public class MakeAssets {
    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "assets");
        writeWav(new File(dir, "audio/power_down.wav"), powerDown());
        writeWav(new File(dir, "audio/chime_6am.wav"), chime());
        System.out.println("Generated placeholder sounds in " + dir);
    }


    static final float RATE = 44100f;

    static short[] powerDown() {
        int n = (int) (RATE * 1.8);
        short[] s = new short[n];
        Random r = new Random(3);
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / RATE;
            double f = 220 * Math.exp(-t * 2.2) + 30;
            phase += 2 * Math.PI * f / RATE;
            double env = Math.min(1, t * 40) * Math.exp(-t * 1.4);
            double v = 0.55 * Math.sin(phase) + 0.25 * Math.sin(phase * 2.01) + (t < 0.05 ? (r.nextDouble() - 0.5) * 0.8 : 0);
            s[i] = (short) (v * env * 22000);
        }
        return s;
    }

    static short[] chime() {
        int n = (int) (RATE * 3.2);
        short[] s = new short[n];
        double[] starts = {0, 1.0};
        for (int i = 0; i < n; i++) {
            double t = i / RATE, v = 0;
            for (double st : starts) {
                double u = t - st;
                if (u < 0) continue;
                double env = Math.exp(-u * 1.6);
                v += env * (0.5 * Math.sin(2 * Math.PI * 660 * u) + 0.3 * Math.sin(2 * Math.PI * 990 * u)
                        + 0.15 * Math.sin(2 * Math.PI * 1320 * u + 0.4));
            }
            s[i] = (short) (Math.max(-1, Math.min(1, v * 0.6)) * 26000);
        }
        return s;
    }

    static void writeWav(File f, short[] samples) throws Exception {
        byte[] b = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            b[2 * i] = (byte) samples[i];
            b[2 * i + 1] = (byte) (samples[i] >> 8);
        }
        AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
        AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(b), fmt, samples.length),
                AudioFileFormat.Type.WAVE, f);
    }
}
