package diddys;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Draws the current screen into the 1280x720 virtual frame. Reads game state; never changes it. */
final class Renderer {
    private static final int W = Config.WIDTH, H = Config.HEIGHT;
    private static final Color CAM_TINT = new Color(18, 40, 34, 46);
    private static final Font F_TITLE = new Font(Font.MONOSPACED, Font.BOLD, 58);
    private static final Font F_BIG = new Font(Font.MONOSPACED, Font.BOLD, 40);
    private static final Font F_MED = new Font(Font.MONOSPACED, Font.BOLD, 26);
    private static final Font F_SMALL = new Font(Font.MONOSPACED, Font.BOLD, 18);
    private static final String[] CHAR_ID = {"jayz", "biggie", "diddy", "kanye"}; // indexed by Sim.Role

    private final Assets a;
    private final BufferedImage scanlines, vignette, camOverlay;
    private final BufferedImage[] statics = new BufferedImage[8];
    private final Map<String, List<Assets.Sign>> signsByRoom = new HashMap<>();
    private final Map<Assets.Sign, BufferedImage> signArt = new HashMap<>();

    /** Needs only the menu essentials; call prepare() once everything else has loaded. */
    Renderer(Assets a) {
        this.a = a;
        for (int i = 0; i < 8; i++) statics[i] = a.img("static_" + (i + 1));
        scanlines = makeScanlines();
        vignette = makeVignette();
        camOverlay = makeCamOverlay();
    }

    /** Draws the signs and pre-builds every cutout, so gameplay never processes images. Any thread. */
    Renderer prepare() {
        Map<String, List<Assets.Sign>> byRoom = new HashMap<>();
        for (Assets.Sign sg : a.signs) {
            byRoom.computeIfAbsent(sg.room, k -> new ArrayList<>()).add(sg);
            signArt.put(sg, renderSign(sg));
        }
        warmCutouts();
        signsByRoom.putAll(byRoom); // published last; Game only shows rooms after the loader reports ready
        return this;
    }

    /** Builds every cutout variant up front so the first sighting or jumpscare never stalls a frame. */
    private void warmCutouts() {
        BufferedImage scratch = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        for (Assets.Placement p : a.placements()) drawPlacement(g, p, 0);
        for (String id : CHAR_ID) {
            Assets.Placement p = jumpscarePose(id);
            p.bright = 0.55; drawPlacement(g, p, 0);
            p.bright = 1.1; drawPlacement(g, p, 0);
        }
        g.dispose();
    }

    /** Which photo a character's jumpscare uses: manifest "jumpscare.<id>", default the main photo. */
    private Assets.Placement jumpscarePose(String id) {
        Assets.Placement pl = new Assets.Placement();
        pl.who = a.text("jumpscare." + id, id).trim();
        if (a.character(pl.who) == null) pl.who = id;
        pl.crop = a.character(pl.who).face;
        pl.fade = 0.18;
        pl.shade = 0.25;
        pl.glow = true;
        return pl;
    }

    void render(Graphics2D g, Game game) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, H);
        switch (game.screen) {
            case MENU: menu(g, game); break;
            case CONFIRM_NEW: confirm(g, game); break;
            case NIGHT_SELECT: panelScreen(g, game, "Select Night", null); break;
            case CONTROLS: controls(g, game); break;
            case SETTINGS: panelScreen(g, game, "Settings", "Volume: " + game.save.volume + "%"); break;
            case INTRO: intro(g, game); break;
            case PLAYING: play(g, game); break;
            case PAUSED: play(g, game); paused(g, game); break;
            case DEFEAT: defeat(g, game); break;
            case VICTORY: victory(g, game); break;
            case COMPLETE: complete(g, game); break;
            default: break;
        }
    }

    // --- gameplay ---

    private void play(Graphics2D g, Game game) {
        Sim s = game.sim;
        if (s.dead) {
            if (s.outage && s.deathTicks < Config.OUTAGE_ANIM_TICKS) outage(g, s, game);
            else fullStatic(g, s.ticks, 1f);
            return;
        }
        if (s.jumpscare != null) {
            jumpscare(g, s, game);
            return;
        }
        if (s.monitorUp) camera(g, game);
        else office(g, game);
        if (game.camSwitchFlash > 0) fullStatic(g, s.ticks, 0.45f * game.camSwitchFlash / Config.MONITOR_LOCK_TICKS);
        hud(g, game);
        if (game.caption != null) captionText(g, game.caption);
        if (game.debugOverlay) debug(g, s);
    }

    private void office(Graphics2D g, Game game) {
        Sim s = game.sim;
        AffineTransform t = g.getTransform();
        g.translate(s.officeView, 0);
        BufferedImage base = s.leftLight ? a.img("office_light_left") : s.rightLight ? a.img("office_light_right") : a.img("office");
        g.drawImage(base, 0, 0, null);
        if (s.leftLight && s.rightLight) {
            // Only one lit image exists per side; take the right half from the right-lit image.
            BufferedImage r = a.img("office_light_right");
            g.drawImage(r, 800, 0, 1600, H, 800, 0, 1600, H, null);
        }
        drawSigns(g, "office");
        if (s.leftLight && s.jay == 8) place(g, "office.jayz", "office", base, s.ticks);
        if (s.rightLight && s.big == 8) place(g, "office.biggie", "office", s.leftLight ? a.img("office_light_right") : base, s.ticks);
        if (!s.leftDoorOpen) g.drawImage(a.img("door_left"), 80, 0, null);
        if (!s.rightDoorOpen) g.drawImage(a.img("door_right"), 1270, 0, null);
        g.drawImage(a.img(panel("left", !s.leftDoorOpen, s.leftLight)), Game.LEFT_PANEL_X, Game.PANEL_Y, null);
        g.drawImage(a.img(panel("right", !s.rightDoorOpen, s.rightLight)), Game.RIGHT_PANEL_X, Game.PANEL_Y, null);
        g.setTransform(t);
    }

    private static String panel(String side, boolean door, boolean light) {
        return "btn_" + side + "_" + (door && light ? "both" : door ? "door" : light ? "light" : "off");
    }

    private void camera(Graphics2D g, Game game) {
        Sim s = game.sim;
        Cam cam = s.camera;
        int off = cam.fixedView() ? -100 : s.cameraPos - 200;
        if (cam == Cam.WEST_HALL_A && s.kanyeStage == 3) sprint(g, s, off);
        else room(g, cam, s, off);

        // Camera feed treatment: noise, then tint + scanlines + vignette pre-combined into one layer
        // (one full-screen blend instead of three matters a lot in the browser build).
        fullStatic(g, s.ticks, 0.09f);
        g.drawImage(camOverlay, 0, 0, null);

        g.setFont(F_SMALL);
        g.setColor(Color.WHITE);
        if ((s.ticks / 50) % 2 == 0) {
            g.setColor(new Color(200, 30, 30));
            g.fillOval(44, 36, 16, 16);
        }
        g.setColor(Color.WHITE);
        g.drawString("REC", 68, 51);
        g.setStroke(new BasicStroke(3));
        g.setColor(new Color(255, 255, 255, 150));
        int m = 26, L = 46;
        g.drawLine(m, m, m + L, m); g.drawLine(m, m, m, m + L);
        g.drawLine(W - m, m, W - m - L, m); g.drawLine(W - m, m, W - m, m + L);
        g.drawLine(m, H - m, m + L, H - m); g.drawLine(m, H - m, m, H - m - L);
        g.drawLine(W - m, H - m, W - m - L, H - m); g.drawLine(W - m, H - m, W - m, H - m - L);

        g.drawImage(a.img("map"), 825, 325, null);
        for (Cam c : Cam.values()) {
            boolean sel = c == cam;
            g.drawImage(a.img("map_button"), c.mapX, c.mapY, null);
            if (sel && (s.ticks / 25) % 2 == 0) {
                g.setColor(new Color(80, 220, 80, 200));
                g.fillRect(c.mapX + 1, c.mapY + 1, Cam.BOX_W - 2, Cam.BOX_H - 2);
            }
            g.drawImage(a.img("cam_label_" + c.ordinal()), c.mapX + 4, c.mapY + 4, null);
        }
        g.setFont(F_MED);
        shadowText(g, "CAM " + cam.code + "  " + cam.title.toUpperCase(), 800, 310, Color.WHITE);
    }

    private void room(Graphics2D g, Cam cam, Sim s, int off) {
        String bg;
        List<String> slots = new ArrayList<>();
        switch (cam) {
            case SHOW_STAGE:
                bg = "stage";
                if (s.jay == 0) slots.add("stage.jayz");
                if (s.big == 0) slots.add("stage.biggie");
                if (s.diddy == 0) slots.add("stage.diddy");
                break;
            case DINING_AREA:
                bg = "lounge";
                if (s.jay == 1) slots.add("lounge.jayz_far");
                if (s.jay == 2) slots.add("lounge.jayz_close");
                if (s.big == 1) slots.add("lounge.biggie_far");
                if (s.big == 2) slots.add("lounge.biggie_close");
                if (s.diddy == 1) slots.add("lounge.diddy");
                break;
            case PIRATE_COVE:
                bg = s.kanyeStage <= 1 ? "vault_closed" : "vault_open";
                if (s.kanyeStage == 1) slots.add("vault.kanye_peek");
                if (s.kanyeStage == 2) slots.add("vault.kanye_out");
                break;
            case WEST_HALL_A:
                bg = "west_hall";
                if (s.jay == 5) slots.add("west_hall.jayz");
                break;
            case WEST_HALL_B:
                bg = "west_corner";
                if (s.jay == 7) slots.add("west_corner.jayz");
                break;
            case SUPPLY_CLOSET:
                bg = "gear_closet";
                if (s.jay == 6) slots.add("closet.jayz");
                break;
            case EAST_HALL_A:
                bg = "east_hall";
                if (s.big == 5) slots.add("east_hall.biggie_far");
                if (s.big == 6) slots.add("east_hall.biggie_close");
                if (s.diddy == 3) slots.add("east_hall.diddy");
                break;
            case EAST_HALL_B:
                bg = "east_corner";
                if (s.big == 7) slots.add("east_corner.biggie");
                if (s.diddy == 4) slots.add("east_corner.diddy");
                break;
            case BACKSTAGE:
                bg = "green_room";
                if (s.jay == 3) slots.add("green_room.jayz_far");
                if (s.jay == 4) slots.add("green_room.jayz_close");
                break;
            default:
                bg = "restrooms";
                if (s.big == 3) slots.add("restrooms.biggie_far");
                if (s.big == 4) slots.add("restrooms.biggie_close");
                if (s.diddy == 2) slots.add("restrooms.diddy");
                break;
        }
        BufferedImage bgImg = a.img("room." + bg);
        AffineTransform t = g.getTransform();
        g.translate(off, 0);
        g.drawImage(bgImg, 0, 0, null);
        drawSigns(g, bg);
        slots.sort(Comparator.comparingInt(k -> a.placement(k) == null ? 0 : a.placement(k).z));
        for (String k : slots) place(g, k, bg, bgImg, s.ticks);
        g.setTransform(t);
    }

    /** ORIGINAL Fox run frames: Kanye charges down the West Hall over 124 ticks. */
    private void sprint(Graphics2D g, Sim s, int off) {
        BufferedImage bg = a.img("room.west_hall");
        Assets.Placement far = a.placement("west_hall.kanye_far"), near = a.placement("west_hall.kanye_near");
        double t = Math.min(1, s.kanyeRun / (double) Config.KANYE_RUN_TICKS);
        double e = t * t;
        Assets.Placement p = new Assets.Placement();
        p.who = far.who;
        p.x = lerp(far.x, near.x, e);
        p.y = lerp(far.y, near.y, e);
        p.h = lerp(far.h, near.h, e);
        p.bright = lerp(far.bright, near.bright, e);
        p.fade = lerp(far.fade, near.fade, e);
        p.shade = lerp(far.shade, near.shade, e);
        p.glow = true;
        p.rot = Math.sin(s.ticks * 0.6) * 3 * t;
        int shake = (int) (Math.sin(s.ticks * 1.7) * 8 * t);
        AffineTransform tr = g.getTransform();
        g.translate(off + shake, 0);
        g.drawImage(bg, 0, 0, null);
        drawPlacement(g, p, s.ticks);
        g.setTransform(tr);
    }

    /** Draws a placement, then repaints its occluder rectangles (room art plus signs) in front of it. */
    private void place(Graphics2D g, String key, String room, BufferedImage bg, int ticks) {
        Assets.Placement p = a.placement(key);
        if (p == null) return;
        drawPlacement(g, p, ticks);
        java.awt.Shape clip = g.getClip();
        for (Rectangle r : p.occluders) {
            g.drawImage(bg, r.x, r.y, r.x + r.width, r.y + r.height, r.x, r.y, r.x + r.width, r.y + r.height, null);
            g.clip(r);
            drawSigns(g, room);
            g.setClip(clip);
        }
    }

    /** Bottom-centre anchored cutout with optional tilt and eye glints. */
    private void drawPlacement(Graphics2D g, Assets.Placement p, int ticks) {
        Assets.Cutout c = a.character(p.who);
        if (c == null) return;
        Rectangle crop = p.crop != null ? p.crop : c.full();
        BufferedImage img = c.variant(crop, round2(p.bright), round2(p.fade), round2(p.shade));
        double sc = p.h / crop.height;
        double w = crop.width * sc;
        AffineTransform at = new AffineTransform();
        at.translate(p.x, p.y);
        at.rotate(Math.toRadians(p.rot));
        at.translate(-w / 2, -p.h);
        if (p.flip) {
            at.translate(w, 0);
            at.scale(-sc, sc);
        } else {
            at.scale(sc, sc);
        }
        Composite old = g.getComposite();
        if (p.alpha < 1) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) p.alpha));
        g.drawImage(img, at, null);
        g.setComposite(old);
        if (p.glow) {
            for (double[] eye : c.eyes) {
                Point2D pt = at.transform(new Point2D.Double(eye[0] - crop.x, eye[1] - crop.y), null);
                eyeGlint(g, pt.getX(), pt.getY(), Math.max(1.5, p.h * 0.0045), 0.9f);
            }
        }
    }

    private static void eyeGlint(Graphics2D g, double x, double y, double r, float strength) {
        float R = (float) (r * 2.6);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), R, new float[] {0f, 0.22f, 1f},
                new Color[] {new Color(255, 250, 235, (int) (255 * strength)), new Color(255, 240, 210, (int) (170 * strength)),
                        new Color(255, 230, 200, 0)}));
        g.fillOval((int) (x - R), (int) (y - R), (int) (2 * R), (int) (2 * R));
    }

    private void jumpscare(Graphics2D g, Sim s, Game game) {
        boolean calm = game.save.reducedFlash;
        AffineTransform t = g.getTransform();
        g.translate(s.officeView, 0);
        g.drawImage(a.img("office"), 0, 0, null);
        g.setTransform(t);
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, W, H);

        int len = Sim.jumpscareLength(s.jumpscare);
        double p = s.jumpTicks / (double) len;
        double grow = Math.min(1, p / 0.22);
        double h = H * (0.8 + 0.55 * (1 - Math.pow(1 - grow, 3)));
        double amp = calm ? 4 : 18;
        double dx = Math.sin(s.ticks * 2.3) * amp * grow, dy = Math.cos(s.ticks * 3.1) * amp * 0.6 * grow;
        boolean flicker = !calm && (s.jumpTicks / 3) % 4 == 3;
        Assets.Placement pl = jumpscarePose(CHAR_ID[s.jumpscare.ordinal()]);
        pl.h = h;
        pl.x = W / 2.0 + dx;
        pl.y = H / 2.0 + h / 2 + dy + h * 0.06;
        pl.bright = flicker ? 0.55 : 1.1;
        pl.rot = Math.sin(s.ticks * 0.9) * (calm ? 1 : 4);
        drawPlacement(g, pl, s.ticks);
        if (!calm && s.jumpTicks < 6) {
            g.setColor(new Color(180, 0, 0, 90));
            g.fillRect(0, 0, W, H);
        }
        g.setColor(new Color(90, 0, 0, 40));
        g.fillRect(0, 0, W, H);
    }

    /** ORIGINAL power-out frames: the office goes dark and P. Diddy appears in the left doorway. */
    private void outage(Graphics2D g, Sim s, Game game) {
        AffineTransform t = g.getTransform();
        g.translate(s.officeView, 0);
        g.drawImage(a.img("office_dark"), 0, 0, null);
        boolean on = game.save.reducedFlash || (s.deathTicks / 8) % 2 == 0;
        Assets.Placement p = a.placement("office.diddy_dark");
        if (p != null) {
            Assets.Placement q = p.copy();
            q.glow = on;
            q.bright = on ? p.bright : p.bright * 0.5;
            drawPlacement(g, q, s.ticks);
        }
        g.setTransform(t);
    }

    private void hud(Graphics2D g, Game game) {
        Sim s = game.sim;
        g.drawImage(a.img("monitor_bar"), 262, 660, null);
        g.setFont(F_SMALL);
        shadowText(g, "Power left: " + s.powerDisplay() + "%", 14, 664, Color.WHITE);
        shadowText(g, "Usage:", 14, 698, Color.WHITE);
        int u = s.usage();
        if (u > 0) g.drawImage(a.img("usage_" + u), 90, 676, null);
        g.setFont(F_BIG);
        int hr = s.hour();
        String clock = (hr == 0 ? 12 : hr) + " AM";
        FontMetrics fm = g.getFontMetrics();
        shadowText(g, clock, W - 40 - fm.stringWidth(clock), 62, Color.WHITE);
        g.setFont(F_SMALL);
        String n = "Night " + s.night;
        shadowText(g, n, W - 40 - g.getFontMetrics().stringWidth(n), 90, Color.WHITE);
    }

    private void captionText(Graphics2D g, String text) {
        g.setFont(F_SMALL);
        int w = g.getFontMetrics().stringWidth(text);
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRoundRect(W / 2 - w / 2 - 14, 104, w + 28, 34, 10, 10);
        g.setColor(Color.WHITE);
        g.drawString(text, W / 2 - w / 2, 127);
    }

    private void debug(Graphics2D g, Sim s) {
        String[] lines = {
                "tick " + s.gameTimer + "/" + Config.NIGHT_TICKS + "  power " + String.format("%.1f", s.power) + "  usage " + s.usage(),
                "jay " + s.jay + " (" + s.jayTick + ")  big " + s.big + " (" + s.bigTick + ")  diddy " + s.diddy + " (" + s.diddyTick + ")",
                "kanye stage " + s.kanyeStage + " (" + s.kanyeTick + ") run " + s.kanyeRun + " doorCheck " + s.kanyeDoorCheck,
                "kills j" + b(s.jayKill) + " b" + b(s.bigKill) + " d" + b(s.diddyKill) + " k" + b(s.kanyeKill) + "  lock " + s.monitorLock,
        };
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(10, 110, 640, 20 * lines.length + 10);
        g.setColor(Color.GREEN);
        for (int i = 0; i < lines.length; i++) g.drawString(lines[i], 18, 130 + 20 * i);
    }

    private static String b(boolean v) { return v ? "1" : "0"; }

    // --- menus and screens ---

    private void menu(Graphics2D g, Game game) {
        boolean twitch = game.menuTwitch > 0 && !game.save.reducedFlash;
        Assets.Placement p = new Assets.Placement();
        String twitchId = a.text("menu.twitch", "diddy").trim();
        p.who = twitch && a.character(twitchId) != null ? twitchId : "diddy";
        p.h = 760;
        p.x = 930 + (twitch ? 14 : 0);
        p.y = 760;
        p.bright = twitch ? 0.75 : 0.42;
        p.fade = 0.3;
        p.rot = twitch ? -4 : 0;
        p.glow = twitch;
        drawPlacement(g, p, game.screenTicks);
        g.setPaint(new GradientPaint(560, 0, new Color(0, 0, 0, 255), 860, 0, new Color(0, 0, 0, 0)));
        g.fillRect(0, 0, 900, H);
        fullStatic(g, game.screenTicks, twitch ? 0.35f : 0.12f);
        g.drawImage(scanlines, 0, 0, null);
        g.setFont(F_TITLE);
        int y = 120;
        for (String line : new String[] {"Five", "Nights", "at", "Diddy's"}) {
            shadowText(g, line, 100, y, Color.WHITE);
            y += 62;
        }
        buttons(g, game);
    }

    private void confirm(Graphics2D g, Game game) {
        dimBackground(g, game);
        center(g, F_MED, "Start a new campaign?", 300, Color.WHITE);
        String prog = game.save.completed ? "You've finished all five nights." : "You've reached Night " + game.save.unlockedNight + ".";
        center(g, F_SMALL, prog + " That progress will be erased.", 350, new Color(220, 200, 200));
        buttons(g, game);
    }

    private void panelScreen(Graphics2D g, Game game, String title, String extra) {
        dimBackground(g, game);
        center(g, F_BIG, title, 150, Color.WHITE);
        if (extra != null) center(g, F_SMALL, extra, 205, new Color(210, 210, 210));
        buttons(g, game);
    }

    private void controls(Graphics2D g, Game game) {
        dimBackground(g, game);
        g.setFont(F_BIG);
        shadowText(g, "Controls", 100, 110, Color.WHITE);
        String[][] rows = {
                {"Mouse to screen edges", "Look around the office"},
                {"Hover bottom bar / Space", "Raise or lower the camera monitor"},
                {"Click DOOR / A, D", "Close or open the left / right door"},
                {"Click LIGHT / Q, E", "Turn the left / right hall light on or off"},
                {"Click the map", "Switch cameras (monitor up)"},
                {"Esc", "Pause"},
                {"M", "Mute"},
        };
        int y = 190;
        for (String[] r : rows) {
            g.setFont(F_SMALL);
            shadowText(g, r[0], 100, y, new Color(255, 230, 150));
            shadowText(g, r[1], 470, y, Color.WHITE);
            y += 40;
        }
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 17));
        String[] tips = {
                "Survive from 12 AM to 6 AM. Doors, lights and the monitor all drain power; at 0% the lights go out.",
                "Check the doorways with the lights. Keep watch on the Vinyl Vault, or someone will make a run for it.",
                "Five nights. Each one is less forgiving than the last.",
        };
        y += 20;
        for (String t : tips) {
            shadowText(g, t, 100, y, new Color(200, 200, 200));
            y += 28;
        }
        buttons(g, game);
    }

    private void intro(Graphics2D g, Game game) {
        boolean waiting = !game.assetsReady && game.screenTicks > Config.INTRO_TICKS - 60;
        int t = waiting ? Config.INTRO_TICKS - 60 : game.screenTicks; // hold the card while loading finishes
        if (waiting) center(g, F_SMALL, "Loading" + ".".repeat(1 + (game.screenTicks / 40) % 3), 560, new Color(150, 150, 150));
        float fadeIn = Math.min(1, t / 60f), fadeOut = Math.min(1, (Config.INTRO_TICKS - t) / 60f);
        float alpha = Math.max(0, Math.min(fadeIn, fadeOut));
        Composite c = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        if (game.night == 1 && t < 300) {
            flyer(g);
        } else {
            center(g, F_BIG, "12:00 AM", 330, Color.WHITE);
            center(g, F_MED, ordinal(game.night) + " Night", 385, Color.WHITE);
            String session = a.text("text.intro." + game.night, "");
            if (!session.isEmpty()) center(g, F_SMALL, session, 440, new Color(190, 170, 120));
        }
        g.setComposite(c);
        fullStatic(g, t, 0.06f);
    }

    private void flyer(Graphics2D g) {
        g.setColor(new Color(214, 206, 188));
        g.fillRect(290, 80, 700, 560);
        g.setColor(new Color(30, 26, 24));
        g.setFont(F_BIG);
        String h = "HELP WANTED";
        g.drawString(h, 640 - g.getFontMetrics().stringWidth(h) / 2, 150);
        g.setFont(F_MED);
        String v = "Diddy's Platinum Studios";
        g.drawString(v, 640 - g.getFontMetrics().stringWidth(v) / 2, 195);
        g.setStroke(new BasicStroke(3));
        g.drawLine(330, 215, 950, 215);
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 22));
        String[] body = {
                "Night security guard needed, 12 AM to 6 AM.",
                "",
                "Duties: watch the studio cameras, mind the power,",
                "and keep the office doors handy. The talent gets",
                "restless after hours and wanders the halls.",
                "",
                "Management is not responsible for lost royalties,",
                "unreleased material, or the night guard.",
                "",
                "$120 a week. Bring your own headphones.",
        };
        int y = 260;
        for (String line : body) {
            g.drawString(line, 340, y);
            y += 32;
        }
        g.setColor(new Color(150, 20, 20));
        g.setStroke(new BasicStroke(4));
        g.drawOval(310, 100, 660, 130);
    }

    private void paused(Graphics2D g, Game game) {
        g.setColor(new Color(0, 0, 0, 185));
        g.fillRect(0, 0, W, H);
        center(g, F_BIG, "PAUSED", 270, Color.WHITE);
        buttons(g, game);
    }

    private void defeat(Graphics2D g, Game game) {
        fullStatic(g, game.screenTicks, 0.35f);
        g.drawImage(vignette, 0, 0, null);
        center(g, F_TITLE, "GAME OVER", 320, new Color(230, 230, 230));
        center(g, F_SMALL, game.loseLine, 380, new Color(200, 160, 160));
        buttons(g, game);
    }

    private void victory(Graphics2D g, Game game) {
        int t = game.screenTicks;
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 96));
        FontMetrics fm = g.getFontMetrics();
        int cx = W / 2, baseY = 330;
        // The digit rolls from 5 to 6 between ticks 100 and 150, as in the FNAF-style clock change.
        double roll = Math.max(0, Math.min(1, (t - 100) / 50.0));
        String am = " AM";
        int digitW = fm.stringWidth("5");
        int total = digitW + fm.stringWidth(am);
        int x0 = cx - total / 2;
        Rectangle clip = new Rectangle(x0 - 10, baseY - fm.getAscent(), digitW + 20, fm.getAscent() + 20);
        java.awt.Shape oldClip = g.getClip();
        g.setClip(clip);
        g.setColor(Color.WHITE);
        int travel = fm.getAscent() + fm.getDescent();
        if (roll < 1) g.drawString("5", x0, (int) (baseY - roll * travel));
        g.drawString("6", x0, (int) (baseY + (1 - roll) * travel));
        g.setClip(oldClip);
        g.drawString(am, x0 + digitW, baseY);
        if (t > 300) {
            String line = a.text("text.win." + game.night, "Good job, intern. See you tomorrow night.");
            center(g, F_SMALL, line, 420, new Color(210, 210, 210));
        }
        buttons(g, game);
    }

    private void complete(Graphics2D g, Game game) {
        String[] faces = {"jayz", "diddy", "biggie", "kanye"};
        for (int i = 0; i < faces.length; i++) {
            Assets.Cutout c = a.character(faces[i]);
            Assets.Placement p = new Assets.Placement();
            p.who = faces[i];
            p.crop = c.face;
            p.h = 230;
            p.x = 325 + i * 210;
            p.y = 260;
            p.bright = 0.45;
            p.fade = 0.35;
            drawPlacement(g, p, game.screenTicks);
        }
        g.setColor(new Color(220, 214, 196));
        g.fillRoundRect(240, 300, 800, 290, 12, 12);
        g.setColor(new Color(40, 34, 30));
        g.setFont(F_MED);
        g.drawString("DIDDY'S PLATINUM STUDIOS", 270, 345);
        g.setFont(F_SMALL);
        g.drawString("Pay to the order of:  Night Security", 270, 400);
        g.drawString("Amount:  $120.50", 270, 440);
        g.drawString("Memo:  " + a.text("text.paycheck.memo", "five nights, zero royalties"), 270, 480);
        g.setFont(new Font(Font.SERIF, Font.ITALIC, 30));
        g.drawString("THE END", 780, 560);
        buttons(g, game);
    }

    private void buttons(Graphics2D g, Game game) {
        g.setFont(F_MED);
        for (Game.Button b : game.buttons()) {
            boolean hover = b.enabled && b.r.contains(game.mouseX, game.mouseY);
            Color col = !b.enabled ? new Color(110, 110, 110) : hover ? Color.WHITE : new Color(205, 205, 205);
            FontMetrics fm = g.getFontMetrics();
            int ty = b.r.y + (b.r.height + fm.getAscent()) / 2 - 4;
            if (hover) shadowText(g, ">>", b.r.x - 46, ty, Color.WHITE);
            shadowText(g, b.label, b.r.x, ty, col);
        }
    }

    private void dimBackground(Graphics2D g, Game game) {
        fullStatic(g, game.screenTicks, 0.1f);
        g.drawImage(scanlines, 0, 0, null);
    }

    // --- helpers ---

    private void fullStatic(Graphics2D g, int ticks, float alpha) {
        if (alpha <= 0) return;
        Composite c = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1, alpha)));
        g.drawImage(statics[(ticks / 6) % 8], 0, 0, null); // ORIGINAL: static frame advances every 6 ticks
        g.setComposite(c);
    }

    private static void shadowText(Graphics2D g, String s, int x, int y, Color c) {
        g.setColor(new Color(0, 0, 0, 200));
        g.drawString(s, x + 2, y + 2);
        g.setColor(c);
        g.drawString(s, x, y);
    }

    private static void center(Graphics2D g, Font f, String s, int y, Color c) {
        g.setFont(f);
        shadowText(g, s, W / 2 - g.getFontMetrics().stringWidth(s) / 2, y, c);
    }

    private static String ordinal(int n) {
        return n + (n == 1 ? "st" : n == 2 ? "nd" : n == 3 ? "rd" : "th");
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static BufferedImage makeScanlines() {
        BufferedImage b = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics();
        g.setColor(new Color(0, 0, 0, 55));
        for (int y = 0; y < H; y += 3) g.drawLine(0, y, W, y);
        g.dispose();
        return b;
    }

    private BufferedImage makeCamOverlay() {
        BufferedImage b = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics();
        g.setColor(CAM_TINT);
        g.fillRect(0, 0, W, H);
        g.drawImage(scanlines, 0, 0, null);
        g.drawImage(vignette, 0, 0, null);
        g.dispose();
        return b;
    }

    private static BufferedImage makeVignette() {
        BufferedImage b = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics();
        g.setPaint(new RadialGradientPaint(new Point2D.Double(W / 2.0, H / 2.0), W * 0.62f, new float[] {0.55f, 1f},
                new Color[] {new Color(0, 0, 0, 0), new Color(0, 0, 0, 190)}));
        g.fillRect(0, 0, W, H);
        g.dispose();
        return b;
    }

    // --- signs, posters and banners (manifest "sign.") ---

    private void drawSigns(Graphics2D g, String room) {
        List<Assets.Sign> list = signsByRoom.get(room);
        if (list == null) return;
        for (Assets.Sign sg : list) {
            AffineTransform t = g.getTransform();
            g.translate(sg.r.getCenterX(), sg.r.getCenterY());
            g.rotate(Math.toRadians(sg.rot));
            g.drawImage(signArt.get(sg), -sg.r.width / 2, -sg.r.height / 2, sg.r.width, sg.r.height, null);
            g.setTransform(t);
        }
    }

    /** Draws a sign once at 2x its placed size; brightness is baked in so it matches the room. */
    private BufferedImage renderSign(Assets.Sign sg) {
        int w = sg.r.width * 2, h = sg.r.height * 2;
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        String[] L = sg.lines;
        switch (sg.style) {
            case "board": {
                g.setColor(new Color(226, 222, 210));
                g.fillRoundRect(0, 0, w, h, 10, 10);
                g.setColor(new Color(40, 36, 34));
                g.setStroke(new BasicStroke(4));
                g.drawRoundRect(2, 2, w - 4, h - 4, 10, 10);
                textBlock(g, L, w, h, Font.SANS_SERIF, true);
                break;
            }
            case "paper": {
                g.setColor(new Color(222, 216, 196));
                g.fillRect(0, 0, w, h);
                g.setColor(new Color(36, 30, 28));
                int y = (int) (h * 0.16);
                fitText(g, L.length > 0 ? L[0] : "", Font.SERIF, Font.BOLD, w * 0.9, h * 0.13, w / 2, y, true);
                for (int i = 1; i < L.length; i++) {
                    y += (int) (h * 0.8 / Math.max(6, L.length));
                    fitText(g, L[i], Font.SANS_SERIF, Font.PLAIN, w * 0.88, h * 0.075, (int) (w * 0.06), y, false);
                }
                break;
            }
            case "plaque_gold":
            case "plaque_platinum": { // framed award record: disc above an engraved plate (title | artist | label)
                boolean gold = sg.style.endsWith("gold");
                g.setColor(new Color(46, 30, 20));
                g.fillRect(0, 0, w, h);
                g.setColor(new Color(12, 10, 10));
                int m = (int) (w * 0.08);
                g.fillRect(m, m, w - 2 * m, h - 2 * m);
                double d = (w - 2 * m) * 0.84;
                double cx = w / 2.0, cy = m + d / 2 + w * 0.06;
                Color rim = gold ? new Color(230, 190, 90) : new Color(222, 224, 230);
                Color deep = gold ? new Color(120, 86, 24) : new Color(110, 112, 120);
                g.setPaint(new RadialGradientPaint(new Point2D.Double(cx - d * 0.15, cy - d * 0.2), (float) d,
                        new float[] {0f, 0.6f, 1f}, new Color[] {rim.brighter(), rim, deep}));
                g.fill(new java.awt.geom.Ellipse2D.Double(cx - d / 2, cy - d / 2, d, d));
                g.setColor(new Color(0, 0, 0, 40));
                for (double r = d * 0.2; r < d / 2; r += d * 0.035) g.draw(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
                g.setColor(new Color(20, 14, 12));
                g.fill(new java.awt.geom.Ellipse2D.Double(cx - d * 0.16, cy - d * 0.16, d * 0.32, d * 0.32));
                g.setColor(rim);
                g.fill(new java.awt.geom.Ellipse2D.Double(cx - d * 0.03, cy - d * 0.03, d * 0.06, d * 0.06));
                int plateY = (int) (cy + d / 2 + w * 0.06), plateH = h - m - plateY - (int) (w * 0.04);
                g.setColor(new Color(176, 150, 96));
                g.fillRect((int) (w * 0.18), plateY, (int) (w * 0.64), plateH);
                g.setColor(new Color(36, 26, 14));
                for (int i = 0; i < L.length && i < 3; i++) {
                    double lh = plateH / 3.4;
                    fitText(g, L[i], Font.SERIF, i == 0 ? Font.BOLD : Font.PLAIN, w * 0.58, lh * (i == 0 ? 0.95 : 0.55),
                            w / 2, (int) (plateY + lh * (i + 0.95)), true);
                }
                break;
            }
            case "banner": {
                g.setPaint(new GradientPaint(0, 0, new Color(96, 14, 18), 0, h, new Color(40, 6, 8)));
                g.fillRect(0, 0, w, (int) (h * 0.82));
                for (int x = 0; x < w; x += 18) g.fillPolygon(new int[] {x, x + 18, x + 9}, new int[] {(int) (h * 0.82), (int) (h * 0.82), h}, 3);
                g.setColor(new Color(226, 190, 96));
                fitText(g, L.length > 0 ? L[0] : "", Font.SERIF, Font.BOLD, w * 0.92, h * 0.42, w / 2, (int) (h * 0.48), true);
                if (L.length > 1) fitText(g, L[1], Font.SANS_SERIF, Font.BOLD, w * 0.8, h * 0.18, w / 2, (int) (h * 0.72), true);
                break;
            }
            default: { // poster: title, subtitle, faces, tagline, small print
                g.setPaint(new GradientPaint(0, 0, new Color(84, 16, 18), 0, h, new Color(20, 5, 7)));
                g.fillRect(0, 0, w, h);
                // First face is the headliner (centre, larger, drawn last); the rest fan out to either side.
                int n = sg.faces.length;
                double step = n == 2 ? 0.2 : 0.36 / Math.max(1, Math.ceil((n - 1) / 2.0));
                for (int k = n - 1; k >= 0; k--) {
                    Assets.Cutout c = a.character(sg.faces[k]);
                    if (c == null) continue;
                    double x = n == 2 ? 0.5 + (k == 0 ? -step : step)
                            : k == 0 ? 0.5 : 0.5 + ((k - 1) % 2 == 0 ? -1 : 1) * ((k - 1) / 2 + 1) * step;
                    Assets.Placement fp = new Assets.Placement();
                    fp.who = sg.faces[k];
                    fp.crop = c.face;
                    fp.h = h * (k == 0 && n > 2 ? 0.58 : 0.5);
                    fp.x = w * x;
                    fp.y = h * 0.82;
                    fp.fade = 0.25;
                    fp.shade = 0.2;
                    drawPlacement(g, fp, 0);
                }
                g.setColor(new Color(226, 190, 96));
                if (L.length > 0) fitText(g, L[0], Font.SERIF, Font.BOLD, w * 0.92, h * 0.17, w / 2, (int) (h * 0.18), true);
                if (L.length > 1) fitText(g, L[1], Font.SANS_SERIF, Font.BOLD, w * 0.8, h * 0.06, w / 2, (int) (h * 0.26), true);
                g.setColor(new Color(244, 234, 214));
                if (L.length > 2) fitText(g, L[2], Font.SANS_SERIF, Font.BOLD, w * 0.9, h * 0.095, w / 2, (int) (h * 0.915), true);
                if (L.length > 3) fitText(g, L[3], Font.SANS_SERIF, Font.PLAIN, w * 0.9, h * 0.045, w / 2, (int) (h * 0.975), true);
                g.setColor(new Color(0, 0, 0, 130));
                g.setStroke(new BasicStroke(6));
                g.drawRect(3, 3, w - 6, h - 6);
                break;
            }
        }
        g.dispose();
        if (sg.bright < 1) {
            int[] px = b.getRGB(0, 0, w, h, null, 0, w);
            for (int i = 0; i < px.length; i++) {
                int p = px[i];
                px[i] = (p & 0xFF000000) | ((int) (((p >> 16) & 255) * sg.bright) << 16)
                        | ((int) (((p >> 8) & 255) * sg.bright) << 8) | (int) ((p & 255) * sg.bright);
            }
            b.setRGB(0, 0, w, h, px, 0, w);
        }
        return b;
    }

    /** Centred lines filling a board, all at the largest size that fits. */
    private static void textBlock(Graphics2D g, String[] lines, int w, int h, String family, boolean bold) {
        if (lines.length == 0) return;
        double lineH = h * 0.78 / lines.length;
        for (int i = 0; i < lines.length; i++) {
            int y = (int) (h * 0.11 + lineH * (i + 0.78));
            fitText(g, lines[i], family, bold ? Font.BOLD : Font.PLAIN, w * 0.88, lineH * 0.82, w / 2, y, true);
        }
    }

    /** Draws text at up to maxH tall, shrinking until it fits maxW. */
    private static void fitText(Graphics2D g, String text, String family, int style, double maxW, double maxH, int x, int y, boolean centred) {
        int size = Math.max(6, (int) (maxH * 1.25));
        Font f = new Font(family, style, size);
        FontMetrics fm = g.getFontMetrics(f);
        while (size > 6 && (fm.stringWidth(text) > maxW || fm.getAscent() > maxH * 1.1)) {
            size--;
            f = new Font(family, style, size);
            fm = g.getFontMetrics(f);
        }
        g.setFont(f);
        g.drawString(text, centred ? x - fm.stringWidth(text) / 2 : x, y);
    }
}
