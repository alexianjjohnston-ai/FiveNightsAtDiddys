package diddys;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Screen flow, input and audio cues. Only the current screen handles input; gameplay rules live in Sim.
 * All coordinates here are in the 1280x720 virtual screen (Main converts mouse positions).
 */
final class Game {
    enum Screen { MENU, CONFIRM_NEW, NIGHT_SELECT, CONTROLS, SETTINGS, INTRO, PLAYING, PAUSED, DEFEAT, VICTORY, COMPLETE }

    static final class Button {
        final String label;
        final Rectangle r;
        final boolean enabled;
        final Runnable action;

        Button(String label, int x, int y, int w, int h, boolean enabled, Runnable action) {
            this.label = label;
            this.r = new Rectangle(x, y, w, h);
            this.enabled = enabled;
            this.action = action;
        }
    }

    // ORIGINAL: the monitor flips when the mouse enters the bottom bar (x 360..860, y >= 660).
    static final Rectangle MONITOR_ZONE = new Rectangle(360, 660, 500, 60);
    // Door/light panels sit at office x 0 and 1490 (y 250), 92x247 each, and pan with the office.
    static final int LEFT_PANEL_X = 0, RIGHT_PANEL_X = 1490, PANEL_Y = 250, PANEL_W = 92, PANEL_H = 247;
    static final int PANEL_SPLIT = 118; // panel-local y: door button above, light button below

    final Assets assets;
    final Audio audio;
    final Save save;
    final boolean dev;
    final Random rng;
    Runnable onQuit = () -> System.exit(0);

    Screen screen = Screen.MENU;
    int screenTicks;
    Sim sim;
    int night = 1;
    int mouseX = -1, mouseY = -1;
    boolean barArmed, barArmedPrev;
    int camSwitchFlash;
    String caption;
    int captionTicks;
    boolean debugOverlay;
    boolean deathAudioDone;
    String loseLine = "";
    int menuTwitch;

    Game(Assets assets, Audio audio, Save save, boolean dev, Random rng) {
        this.assets = assets;
        this.audio = audio;
        this.save = save;
        this.dev = dev;
        this.rng = rng;
        applyAudioSettings();
        go(Screen.MENU);
    }

    // --- flow ---

    void go(Screen s) {
        Screen prev = screen;
        screen = s;
        screenTicks = 0;
        boolean menuish = s == Screen.MENU || s == Screen.CONFIRM_NEW || s == Screen.NIGHT_SELECT
                || s == Screen.CONTROLS || s == Screen.SETTINGS || s == Screen.INTRO;
        if (menuish) {
            if (prev == Screen.PLAYING || prev == Screen.PAUSED || prev == Screen.DEFEAT
                    || prev == Screen.VICTORY || prev == Screen.COMPLETE) audio.stopAll();
            audio.loop("menu_music");
        } else if (s != Screen.PAUSED && s != Screen.PLAYING) {
            audio.stopAll();
        }
    }

    void startNight(int n) {
        night = Math.max(1, Math.min(Config.NIGHTS, n));
        sim = new Sim(night, rng); // a fresh Sim is a complete reset: clock, power, AI, doors, camera, timers
        deathAudioDone = false;
        caption = null;
        barArmed = false;
        if (!save.started) {
            save.started = true;
            save.write();
        }
        go(Screen.INTRO);
    }

    private void beginPlay() {
        audio.stopAll();
        screen = Screen.PLAYING;
        screenTicks = 0;
        audio.loop("ambience_fan");
        audio.loop("ambience_drone");
        audio.play("blip"); // ORIGINAL: blip when the night starts
    }

    void newGame() {
        save.unlockedNight = 1;
        save.completed = false;
        save.started = true;
        save.write();
        startNight(1);
    }

    void pause() {
        if (screen != Screen.PLAYING || sim.inputLocked()) return;
        screen = Screen.PAUSED;
        audio.pause();
    }

    void resume() {
        if (screen != Screen.PAUSED) return;
        screen = Screen.PLAYING;
        barArmed = false;
        audio.resume();
    }

    // --- per tick (10 ms) ---

    void tick() {
        screenTicks++;
        if (captionTicks > 0 && --captionTicks == 0) caption = null;
        if (camSwitchFlash > 0) camSwitchFlash--;
        if (menuTwitch > 0) menuTwitch--;
        else if (screen == Screen.MENU && rng.nextInt(400) == 0) menuTwitch = 6 + rng.nextInt(10);

        switch (screen) {
            case INTRO:
                if (screenTicks >= Config.INTRO_TICKS) beginPlay();
                break;
            case PLAYING:
                tickPlaying();
                break;
            case VICTORY:
                if (screenTicks == 150) audio.play("chime");
                break;
            default:
                break;
        }
    }

    private void tickPlaying() {
        boolean officeMouse = mouseX >= 0 && !sim.monitorUp;
        sim.panInput = !officeMouse ? 0
                : mouseX < Config.OFFICE_PAN_LEFT_ZONE ? -1
                : mouseX > Config.OFFICE_PAN_RIGHT_ZONE ? 1 : 0;
        if (barArmed && sim.toggleMonitor()) {
            barArmed = false;
            onMonitorFlip();
        }
        sim.step();
        for (Sim.Event e : sim.drainEvents()) onEvent(e);

        if (sim.leftLight || sim.rightLight) audio.loop("light_buzz");
        else audio.stop("light_buzz");

        if (sim.dead && !deathAudioDone) {
            boolean outageAnimDone = !sim.outage || sim.deathTicks >= Config.OUTAGE_ANIM_TICKS;
            if (outageAnimDone) {
                deathAudioDone = true;
                audio.stop("scream"); // ORIGINAL: the scream is cut when the jumpscare's last frame shows
                audio.play("static");
            }
        }
        if (sim.defeatReady()) {
            loseLine = randomLoseLine();
            go(Screen.DEFEAT);
        } else if (sim.won) {
            if (night >= Config.NIGHTS) save.completed = true;
            save.unlockedNight = Math.max(save.unlockedNight, Math.min(Config.NIGHTS, night + 1));
            save.write();
            go(Screen.VICTORY);
        }
    }

    /** Game Over lines come from the manifest (text.lose.1, text.lose.2, ...). */
    private String randomLoseLine() {
        List<String> lines = new ArrayList<>();
        for (int i = 1; !assets.text("text.lose." + i, "").isEmpty(); i++) lines.add(assets.text("text.lose." + i, ""));
        return lines.isEmpty() ? "" : lines.get(rng.nextInt(lines.size()));
    }

    private void onEvent(Sim.Event e) {
        switch (e) {
            case KNOCK:
                audio.play("knock");
                caption("[Banging on the left door]");
                break;
            case RUN:
                audio.play("run");
                caption("[Footsteps sprinting down the West Hall]");
                break;
            case JUMPSCARE:
                audio.stop("light_buzz");
                audio.stop("ambience_drone");
                audio.play("scream");
                break;
            case OUTAGE:
                audio.stopAll();
                audio.play("power_down");
                caption("[Power failure]");
                break;
            case MONITOR_FORCED_DOWN:
                audio.play("blip");
                break;
            default:
                break;
        }
    }

    private void caption(String text) {
        if (!save.captions) return;
        caption = text;
        captionTicks = 250;
    }

    private void onMonitorFlip() {
        audio.play("blip");
        camSwitchFlash = Config.MONITOR_LOCK_TICKS;
    }

    // --- input ---

    void mouseMoved(int x, int y) {
        mouseX = x;
        mouseY = y;
        if (screen != Screen.PLAYING) return;
        boolean inBar = MONITOR_ZONE.contains(x, y);
        if (!inBar) barArmed = false;
        else if (!barArmedPrev) barArmed = true; // edge-triggered: one flip per entry (FIX: original re-toggled on every move)
        barArmedPrev = inBar;
    }


    void mouseExited() {
        mouseX = mouseY = -1;
        barArmed = barArmedPrev = false;
    }

    void click(int x, int y) {
        mouseMoved(x, y);
        if (screen == Screen.PLAYING) {
            clickPlaying(x, y);
            return;
        }
        if (screen == Screen.INTRO) {
            if (screenTicks > 100) beginPlay();
            return;
        }
        for (Button b : buttons()) {
            if (b.enabled && b.r.contains(x, y)) {
                audio.play("blip");
                b.action.run();
                return;
            }
        }
    }

    private void clickPlaying(int x, int y) {
        if (sim.inputLocked()) return;
        if (sim.monitorUp) {
            for (Cam c : Cam.values()) {
                if (c.hit(x, y)) {
                    if (sim.selectCamera(c)) {
                        audio.play("blip");
                        camSwitchFlash = 15;
                    }
                    return;
                }
            }
            return;
        }
        Rectangle l = panelRect(true), r = panelRect(false);
        if (l.contains(x, y)) {
            if (y - l.y < PANEL_SPLIT) door(true);
            else light(true);
        } else if (r.contains(x, y)) {
            if (y - r.y < PANEL_SPLIT) door(false);
            else light(false);
        }
    }

    /** Screen-space rectangle of a door/light panel at the current office pan. */
    Rectangle panelRect(boolean left) {
        int x = sim.officeView + (left ? LEFT_PANEL_X : RIGHT_PANEL_X);
        return new Rectangle(x, PANEL_Y, PANEL_W, PANEL_H);
    }

    private void door(boolean left) {
        if (sim.toggleDoor(left)) {
            audio.play("door");
            caption(left ? "[Left door " + (sim.leftDoorOpen ? "opens]" : "slams shut]")
                    : "[Right door " + (sim.rightDoorOpen ? "opens]" : "slams shut]"));
        }
    }

    private void light(boolean left) {
        sim.toggleLight(left);
    }

    void key(int code) {
        if (code == KeyEvent.VK_M) {
            save.muted = !save.muted;
            applyAudioSettings();
            save.write();
            return;
        }
        if (dev && devKey(code)) return;
        switch (screen) {
            case PLAYING:
                switch (code) {
                    case KeyEvent.VK_A: door(true); break;
                    case KeyEvent.VK_D: door(false); break;
                    case KeyEvent.VK_Q: light(true); break;
                    case KeyEvent.VK_E: light(false); break;
                    case KeyEvent.VK_SPACE:
                        if (sim.toggleMonitor()) onMonitorFlip();
                        break;
                    case KeyEvent.VK_ESCAPE: pause(); break;
                    default: break;
                }
                break;
            case PAUSED:
                if (code == KeyEvent.VK_ESCAPE) resume();
                break;
            case INTRO:
                if ((code == KeyEvent.VK_SPACE || code == KeyEvent.VK_ENTER) && screenTicks > 100) beginPlay();
                break;
            case CONTROLS: case SETTINGS: case NIGHT_SELECT: case CONFIRM_NEW:
                if (code == KeyEvent.VK_ESCAPE) go(Screen.MENU);
                break;
            default:
                break;
        }
    }

    /** Window lost focus or was minimised: forget the mouse so nothing keeps panning, and pause. */
    void focusLost() {
        mouseExited();
        if (screen == Screen.PLAYING) pause();
    }

    void applyAudioSettings() {
        audio.setVolume(save.volume / 100f);
        audio.setMuted(save.muted);
    }

    // --- menus: the same list drives drawing and clicking, so hitboxes always match ---

    List<Button> buttons() {
        List<Button> b = new ArrayList<>();
        int x = 100, w = 420, h = 44;
        switch (screen) {
            case MENU: {
                int y = 400;
                b.add(new Button("New Game", x, y, w, h, true,
                        () -> { if (save.started) go(Screen.CONFIRM_NEW); else newGame(); }));
                b.add(new Button("Continue", x, y += 52, w, h, save.started, () -> go(Screen.NIGHT_SELECT)));
                b.add(new Button("Controls", x, y += 52, w, h, true, () -> go(Screen.CONTROLS)));
                b.add(new Button("Settings", x, y += 52, w, h, true, () -> go(Screen.SETTINGS)));
                b.add(new Button("Quit", x, y + 52, w, h, true, () -> onQuit.run()));
                break;
            }
            case CONFIRM_NEW:
                b.add(new Button("Yes, start over", 440, 420, 400, h, true, this::newGame));
                b.add(new Button("Cancel", 440, 472, 400, h, true, () -> go(Screen.MENU)));
                break;
            case NIGHT_SELECT:
                for (int n = 1; n <= Config.NIGHTS; n++) {
                    final int nn = n;
                    String label = "Night " + n + (n == save.unlockedNight && !save.completed ? "   (continue)" : "");
                    b.add(new Button(label, 440, 230 + (n - 1) * 56, 400, h, n <= save.unlockedNight, () -> startNight(nn)));
                }
                b.add(new Button("Back", 440, 230 + Config.NIGHTS * 56 + 20, 400, h, true, () -> go(Screen.MENU)));
                break;
            case CONTROLS:
                b.add(new Button("Back", 100, 640, 300, h, true, () -> go(Screen.MENU)));
                break;
            case SETTINGS: {
                int y = 230;
                b.add(new Button("Volume -", 440, y, 190, h, save.volume > 0, () -> setVolume(save.volume - 10)));
                b.add(new Button("Volume +", 650, y, 190, h, save.volume < 100, () -> setVolume(save.volume + 10)));
                b.add(new Button("Mute: " + onOff(save.muted), 440, y += 70, 400, h, true, () -> {
                    save.muted = !save.muted;
                    applyAudioSettings();
                    save.write();
                }));
                b.add(new Button("Reduced flashing: " + onOff(save.reducedFlash), 440, y += 56, 400, h, true, () -> {
                    save.reducedFlash = !save.reducedFlash;
                    save.write();
                }));
                b.add(new Button("Sound captions: " + onOff(save.captions), 440, y += 56, 400, h, true, () -> {
                    save.captions = !save.captions;
                    save.write();
                }));
                b.add(new Button("Back", 440, y + 76, 400, h, true, () -> go(Screen.MENU)));
                break;
            }
            case PAUSED:
                b.add(new Button("Resume", 440, 330, 400, h, true, this::resume));
                b.add(new Button("Restart Night " + night, 440, 382, 400, h, true, () -> startNight(night)));
                b.add(new Button("Main Menu", 440, 434, 400, h, true, () -> go(Screen.MENU)));
                break;
            case DEFEAT:
                if (screenTicks > 60) {
                    b.add(new Button("Retry Night " + night, 440, 470, 400, h, true, () -> startNight(night)));
                    b.add(new Button("Main Menu", 440, 522, 400, h, true, () -> go(Screen.MENU)));
                }
                break;
            case VICTORY:
                if (screenTicks > 300) {
                    if (night < Config.NIGHTS) {
                        b.add(new Button("Night " + (night + 1), 440, 500, 400, h, true, () -> startNight(night + 1)));
                        b.add(new Button("Main Menu", 440, 552, 400, h, true, () -> go(Screen.MENU)));
                    } else {
                        b.add(new Button("Continue", 440, 500, 400, h, true, () -> go(Screen.COMPLETE)));
                    }
                }
                break;
            case COMPLETE:
                if (screenTicks > 100) b.add(new Button("Main Menu", 440, 620, 400, h, true, () -> go(Screen.MENU)));
                break;
            default:
                break;
        }
        return b;
    }

    private void setVolume(int v) {
        save.volume = Math.max(0, Math.min(100, v));
        applyAudioSettings();
        save.write();
    }

    private static String onOff(boolean v) {
        return v ? "On" : "Off";
    }

    // --- private development mode (-Ddiddys.dev=true); never active in a normal launch ---

    private boolean devKey(int code) {
        if (screen == Screen.MENU && code >= KeyEvent.VK_1 && code <= KeyEvent.VK_5) {
            startNight(code - KeyEvent.VK_0);
            return true;
        }
        if (sim == null || screen != Screen.PLAYING) return false;
        switch (code) {
            case KeyEvent.VK_F1: debugOverlay = !debugOverlay; return true;
            case KeyEvent.VK_F2: for (int i = 0; i < 30 * Config.TICKS_PER_SECOND && !sim.won && !sim.dead; i++) sim.step(); return true;
            case KeyEvent.VK_F3: sim.power = Math.max(0.5, sim.power - 10); return true;
            case KeyEvent.VK_F4: sim.kanyeStage = 3; return true;
            case KeyEvent.VK_F5: sim.jay = 8; return true;
            case KeyEvent.VK_F6: sim.big = 8; return true;
            case KeyEvent.VK_F7: sim.diddy = 4; return true;
            case KeyEvent.VK_F8: sim.gameTimer = Config.NIGHT_TICKS - 300; return true;
            default: return false;
        }
    }
}
