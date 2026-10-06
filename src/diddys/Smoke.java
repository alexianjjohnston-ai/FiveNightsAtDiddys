package diddys;

import java.awt.Frame;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.Timer;

/**
 * Development-only smoke test of the real window (-Ddiddys.dev=true -Ddiddys.smoke=DIR): sends real Swing
 * mouse/key events through the view at several window sizes, checks the results, saves what the window
 * actually painted, then quits. Runs on the live real-time loop, so it also measures game speed.
 */
final class Smoke {
    private final JFrame frame;
    private final Main.View view;
    private final Game game;
    private final Path dir;
    private final List<Runnable> steps = new ArrayList<>();
    private final List<String> log = new ArrayList<>();
    private int failures;
    private long t0;
    private int timer0;

    private Smoke(JFrame frame, Main.View view, Game game, Path dir) {
        this.frame = frame;
        this.view = view;
        this.game = game;
        this.dir = dir;
    }

    static void start(JFrame frame, Main.View view, Game game, Path dir) {
        new Smoke(frame, view, game, dir).begin();
    }

    private void begin() {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Each step runs ~0.6 s after the previous one, on the event thread, while the real loop keeps ticking.
        step(() -> { resize(1600, 1000); });
        step(() -> { shot("a_menu_1600x1000"); clickV(150, 422); }); // "New Game"
        step(() -> {
            if (game.screen == Game.Screen.CONFIRM_NEW) clickV(500, 442);
            expect(game.screen == Game.Screen.INTRO, "New Game opens the night intro");
        });
        step(() -> {});
        step(() -> { clickV(640, 360); expect(game.screen == Game.Screen.PLAYING, "clicking skips the intro"); });
        step(() -> {
            shot("b_office_1600x1000");
            expect(game.panelRect(true).x + Game.PANEL_W <= 0, "left panel starts off-screen, as in the original (-100 view)");
            moveV(60, 400); // look left: the office pans while the mouse is in the left zone
        });
        step(() -> {
            expect(game.sim.officeView == 0, "office panned fully left");
            moveV(640, 400);
            java.awt.Rectangle l = game.panelRect(true);
            clickV(l.x + 30, l.y + 50);
            expect(!game.sim.leftDoorOpen, "left DOOR button works through the real window (letterboxed)");
            clickV(Math.max(2, l.x + 30), l.y + 180);
            expect(game.sim.leftLight, "left LIGHT button works");
            key(KeyEvent.VK_D);
            expect(!game.sim.rightDoorOpen, "D closes the right door");
            key(KeyEvent.VK_E);
            expect(game.sim.rightLight, "E toggles the right light");
        });
        step(() -> { shot("c_office_doors_lights"); key(KeyEvent.VK_A); key(KeyEvent.VK_D); key(KeyEvent.VK_Q); key(KeyEvent.VK_E); });
        step(() -> {
            expect(game.sim.leftDoorOpen && game.sim.rightDoorOpen && !game.sim.leftLight && !game.sim.rightLight, "A/D/Q/E toggle back");
            moveV(640, 500);
            moveV(600, 690); // enter the monitor bar
        });
        step(() -> { expect(game.sim.monitorUp, "hovering the bottom bar raises the monitor"); clickV(Cam.DINING_AREA.mapX + 20, Cam.DINING_AREA.mapY + 15); });
        step(() -> { expect(game.sim.camera == Cam.DINING_AREA, "map click selects CAM 1B"); resize(900, 760); });
        step(() -> {
            shot("d_camera_900x760");
            clickV(Cam.RESTROOMS.mapX + 20, Cam.RESTROOMS.mapY + 15);
            expect(game.sim.camera == Cam.RESTROOMS, "map click still lands after resizing to a tall window");
            t0 = System.nanoTime();
            timer0 = game.sim.gameTimer;
        });
        step(() -> {});
        step(() -> {});
        step(() -> {});
        step(() -> {
            double secs = (System.nanoTime() - t0) / 1e9;
            double rate = (game.sim.gameTimer - timer0) / secs;
            log("measured " + Math.round(rate) + " ticks/s over " + String.format("%.2f", secs) + " s (target 100)");
            expect(Math.abs(rate - 100) < 6, "night clock runs at real time (100 ticks per second)");
            key(KeyEvent.VK_SPACE);
        });
        step(() -> { expect(!game.sim.monitorUp, "Space lowers the monitor"); key(KeyEvent.VK_ESCAPE); });
        step(() -> { expect(game.screen == Game.Screen.PAUSED, "Esc pauses"); shot("e_paused"); timer0 = game.sim.gameTimer; });
        step(() -> { expect(game.sim.gameTimer == timer0, "pause freezes the clock"); key(KeyEvent.VK_ESCAPE); });
        step(() -> {
            expect(game.screen == Game.Screen.PLAYING, "Esc resumes");
            frame.setExtendedState(Frame.ICONIFIED);
        });
        step(() -> {});
        step(() -> { timer0 = game.sim.gameTimer; }); // measured once the minimise (and pause) has landed
        step(() -> {});
        step(() -> { frame.setExtendedState(Frame.NORMAL); frame.toFront(); });
        step(() -> {
            expect(game.screen == Game.Screen.PAUSED, "minimising pauses the night");
            expect(game.sim.gameTimer - timer0 < 10, "no time jump after restoring (" + (game.sim.gameTimer - timer0) + " ticks)");
            resize(1280, 760);
            clickV(500, 352); // Resume
        });
        step(() -> {
            expect(game.screen == Game.Screen.PLAYING, "Resume button works");
            key(KeyEvent.VK_M);
            expect(game.save.muted, "M mutes");
            key(KeyEvent.VK_M);
            game.sim.jay = 8;
            game.sim.leftDoorOpen = true;
            game.sim.monitorLock = 0;
            key(KeyEvent.VK_SPACE);
            game.sim.jayTick = Config.JAYZ_INTERVAL - 1;
        });
        step(() -> {
            expect(game.sim.jayKill, "Jay-Z attacks through the open door while the cameras are up");
            game.sim.monitorLock = 0;
            key(KeyEvent.VK_SPACE);
        });
        step(() -> { shot("f_jumpscare"); expect(game.sim.jumpscare == Sim.Role.JAYZ || game.sim.dead, "lowering the monitor plays the jumpscare"); });
        step(() -> {});
        step(() -> {});
        step(() -> {});
        step(() -> { expect(game.screen == Game.Screen.DEFEAT, "jumpscare then static then Game Over"); });
        step(() -> shot("g_defeat"));
        step(() -> clickV(500, 492)); // Retry
        step(() -> { expect(game.screen == Game.Screen.INTRO && game.sim.jay == 0 && game.sim.power == 100, "Retry restarts the night cleanly"); finish(); });

        Timer t = new Timer(600, null);
        t.addActionListener(e -> {
            if (steps.isEmpty()) {
                t.stop();
                return;
            }
            if (!game.assetsReady) return; // wait for the background loader before driving the game
            try {
                steps.remove(0).run();
            } catch (RuntimeException ex) {
                failures++;
                log("EXCEPTION " + ex);
                finish();
            }
        });
        t.setInitialDelay(1500);
        t.start();
    }

    private void step(Runnable r) {
        steps.add(r);
    }

    private void expect(boolean ok, String what) {
        if (!ok) failures++;
        log((ok ? "ok   " : "FAIL ") + what);
    }

    private void log(String s) {
        log.add(s);
        System.out.println("[smoke] " + s);
    }

    private void resize(int w, int h) {
        frame.setExtendedState(Frame.NORMAL);
        frame.setSize(w, h);
        frame.validate();
    }

    private void moveV(int x, int y) {
        Point p = view.toComponent(x, y);
        view.dispatchEvent(new MouseEvent(view, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, p.x, p.y, 0, false));
    }

    private void clickV(int x, int y) {
        Point p = view.toComponent(x, y);
        view.dispatchEvent(new MouseEvent(view, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, p.x, p.y, 0, false));
        view.dispatchEvent(new MouseEvent(view, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), MouseEvent.BUTTON1_DOWN_MASK,
                p.x, p.y, 1, false, MouseEvent.BUTTON1));
        view.dispatchEvent(new MouseEvent(view, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, p.x, p.y, 1, false, MouseEvent.BUTTON1));
    }

    private void key(int code) {
        view.dispatchEvent(new KeyEvent(view, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED));
        view.dispatchEvent(new KeyEvent(view, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED));
    }

    /** What the window really painted, at its real size (letterbox bars included). */
    private void shot(String name) {
        BufferedImage img = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
        view.paint(img.getGraphics());
        try {
            ImageIO.write(img, "png", new File(dir.toFile(), name + ".png"));
        } catch (IOException e) {
            log("could not save " + name + ": " + e);
        }
    }

    private void finish() {
        log(failures == 0 ? "SMOKE PASSED" : "SMOKE FAILED (" + failures + ")");
        try (PrintWriter w = new PrintWriter(new File(dir.toFile(), "smoke.log"))) {
            for (String s : log) w.println(s);
        } catch (IOException ignored) {
            // console output already has it
        }
        steps.clear();
        game.onQuit.run();
    }
}
