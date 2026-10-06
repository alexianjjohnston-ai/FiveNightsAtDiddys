package diddys;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Progress and settings in a small properties file in the user's app-data folder (never the game folder):
 *   macOS   ~/Library/Application Support/FiveNightsAtDiddys/save.properties
 *   Windows %APPDATA%\FiveNightsAtDiddys\save.properties
 *   Linux   ~/.local/share/FiveNightsAtDiddys/save.properties
 * A damaged file is renamed to save.properties.bad and defaults are used.
 */
final class Save {
    int unlockedNight = 1;   // highest night the player may start
    boolean started;         // a campaign exists (used for Continue and the New Game confirmation)
    boolean completed;       // Night 5 beaten
    int volume = 80;         // 0..100
    boolean muted, reducedFlash, captions;

    private final Path file;

    Save(Path file) {
        this.file = file;
    }

    static Path defaultPath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        Path dir;
        if (os.contains("mac")) dir = Paths.get(home, "Library", "Application Support", "FiveNightsAtDiddys");
        else if (os.contains("win") && System.getenv("APPDATA") != null) dir = Paths.get(System.getenv("APPDATA"), "FiveNightsAtDiddys");
        else dir = Paths.get(home, ".local", "share", "FiveNightsAtDiddys");
        return dir.resolve("save.properties");
    }

    static Save load(Path file) {
        Save s = new Save(file);
        if (!Files.exists(file)) return s;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
            s.unlockedNight = clamp(Integer.parseInt(p.getProperty("unlockedNight", "1").trim()), 1, Config.NIGHTS);
            s.started = Boolean.parseBoolean(p.getProperty("started", "false").trim());
            s.completed = Boolean.parseBoolean(p.getProperty("completed", "false").trim());
            s.volume = clamp(Integer.parseInt(p.getProperty("volume", "80").trim()), 0, 100);
            s.muted = Boolean.parseBoolean(p.getProperty("muted", "false").trim());
            s.reducedFlash = Boolean.parseBoolean(p.getProperty("reducedFlash", "false").trim());
            s.captions = Boolean.parseBoolean(p.getProperty("captions", "false").trim());
        } catch (IOException | RuntimeException e) {
            System.err.println("Save file unreadable, starting fresh: " + e);
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // keep going with defaults
            }
            return new Save(file);
        }
        return s;
    }

    /** Write to a temp file and move it into place so a crash can't leave a half-written save. */
    void write() {
        Properties p = new Properties();
        p.setProperty("unlockedNight", Integer.toString(unlockedNight));
        p.setProperty("started", Boolean.toString(started));
        p.setProperty("completed", Boolean.toString(completed));
        p.setProperty("volume", Integer.toString(volume));
        p.setProperty("muted", Boolean.toString(muted));
        p.setProperty("reducedFlash", Boolean.toString(reducedFlash));
        p.setProperty("captions", Boolean.toString(captions));
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                p.store(out, "Five Nights at Diddy's");
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); // e.g. the browser's storage
            }
        } catch (IOException e) {
            System.err.println("Could not save progress: " + e);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
