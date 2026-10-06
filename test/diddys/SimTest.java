package diddys;

import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Plain-Java rule checks (no framework). Run with ./test.sh; exits non-zero on the first failure. */
public final class SimTest {
    private static final java.util.Set<String> passed = new java.util.HashSet<>();

    public static void main(String[] args) throws Exception {
        nightFiveMatchesOriginalAi();
        clockAndSixAm();
        powerModel();
        outageHappensOnce();
        legalMovesAcrossManySeeds();
        doorsProtect();
        diddyRetreatsFromClosedDoor();
        kanyeRules();
        kanyeSprintOnlyAtStageThree();
        pendingAttackVersusSixAm();
        jumpscareFreezesNight();
        monitorRules();
        restartResetsEverything();
        officeHitboxesFollowPan();
        saveRoundTripAndDamagedFile();
        campaignFlow();
        System.out.println("All " + passed.size() + " distinct checks passed.");
    }

    // --- helpers ---

    private static void check(boolean ok, String what) {
        if (!ok) {
            System.err.println("FAIL: " + what);
            System.exit(1);
        }
        passed.add(what.replaceAll("[0-9-]+", "#"));
    }

    private static void run(Sim s, int ticks) {
        for (int i = 0; i < ticks; i++) s.step();
    }

    /** Puts the sim exactly one tick before an enemy's movement opportunity. */
    private static void primeJay(Sim s) { s.jayTick = Config.JAYZ_INTERVAL - 1; }
    private static void primeBig(Sim s) { s.bigTick = Config.BIGGIE_INTERVAL - 1; }
    private static void primeDiddy(Sim s) { s.diddyTick = Config.DIDDY_INTERVAL - 1; }
    private static void primeKanye(Sim s) { s.kanyeTick = Config.KANYE_INTERVAL - 1; }

    /** Keeps power from interfering with AI-only checks. */
    private static void infinitePower(Sim s) { s.power = 1e9; }

    // --- the original 2014 enemy code, transcribed literally (statics -> fields, Math.random -> rng) ---

    static final class Original {
        final Random rng;
        int bonnie, chicka, freddy, foxy;
        boolean bonnieDeath, chickaDeath, freddyDeath, foxyDeath;
        boolean door1open = true, door2open = true, monitorUp;
        String cameraLocation = "Show Stage";
        int difficulty = 0; // never set in the shipped game

        Original(Random rng) { this.rng = rng; }

        void bonnieTick() {
            if (bonnie == 8 && door1open == true && monitorUp == true) { bonnie = 9; bonnieDeath = true; return; }
            int rand1 = 1 + (int) (rng.nextDouble() * difficulty);
            if (rand1 == difficulty) return;
            int rand2 = 1 + (int) (rng.nextDouble() * 8);
            if (rand2 >= 4) {
                if (rand2 >= bonnie) {
                    if (chicka == bonnie + 1 || freddy == bonnie + 1) return;
                    if (bonnie + 1 > 8) return;
                    bonnie = 1 + bonnie;
                } else return;
            } else {
                if (rand2 >= bonnie) {
                    if (chicka == bonnie + 1 || freddy == bonnie + 1) return;
                    if (bonnie + 1 > 8) return;
                    bonnie = bonnie + 1;
                } else {
                    if (rand2 == bonnie) {
                        if (chicka == bonnie + 1 || freddy == bonnie + 1) return;
                        bonnie = bonnie - 1;
                    }
                }
            }
        }

        void chickaTick() {
            if (chicka == 8 && door2open == true && monitorUp == true) { chicka = 9; chickaDeath = true; return; }
            int rand1 = 1 + (int) (rng.nextDouble() * difficulty);
            if (rand1 == difficulty) return;
            int rand2 = 1 + (int) (rng.nextDouble() * 8);
            if (rand2 >= 4) {
                if (rand2 >= chicka) {
                    if (bonnie == chicka + 1 || freddy == chicka + 1) return;
                    if (chicka + 1 > 8) return;
                    chicka = 1 + chicka;
                } else return;
            } else {
                if (rand2 >= chicka) {
                    if (bonnie == chicka + 1 || freddy == chicka + 1) return;
                    if (chicka + 1 > 8) return;
                    chicka = chicka + 1;
                } else {
                    if (rand2 == chicka) {
                        if (bonnie == chicka + 1 || freddy == chicka + 1) return;
                        chicka = chicka - 1;
                    }
                }
            }
        }

        void freddyTick() {
            if (freddy == 0 && (bonnie == 0 || chicka == 0)) return;
            if (monitorUp == true && door2open == true && freddy == 4) {
                freddyDeath = true;
                freddy = 5;
            } else {
                if (freddy != 4) {
                    if (chicka == freddy + 1 || bonnie == freddy + 1) return;
                    freddy = freddy + 1;
                } else {
                    if (chicka == freddy + 1 || bonnie == freddy + 1) return;
                    freddy = freddy - 1;
                }
            }
        }

        void foxyTick() {
            if (monitorUp == true && cameraLocation.equals("Pirate Cove")) return;
            if (foxy != 3) foxy = foxy + 1;
            else if (door1open == false) foxy = 1;
            else foxyDeath = true;
        }
    }

    /**
     * Same seed, same scripted player, Night 5 (the original's behaviour): every enemy position must match
     * the literal original on every tick until the first attack. The player never watches West Hall A, so
     * the original's Fox-counter bug (fixed in the remix) isn't exercised.
     */
    private static void nightFiveMatchesOriginalAi() {
        Cam[] safeCams = {Cam.SHOW_STAGE, Cam.DINING_AREA, Cam.PIRATE_COVE, Cam.SUPPLY_CLOSET, Cam.EAST_HALL_A, Cam.RESTROOMS};
        int comparedTicks = 0;
        for (int seed = 1; seed <= 60; seed++) {
            Sim s = new Sim(5, new Random(seed));
            infinitePower(s);
            Original o = new Original(new Random(seed));
            Random player = new Random(seed * 7919L);
            int bt = 0, ct = 0, ft = 0, xt = 0;
            for (int t = 0; t < Config.NIGHT_TICKS - 1; t++) {
                if (t % 97 == 0) {
                    int act = player.nextInt(6);
                    if (act == 0 && !s.monitorUp) s.toggleDoor(true);
                    if (act == 1 && !s.monitorUp) s.toggleDoor(false);
                    if (act == 2) { s.monitorLock = 0; s.toggleMonitor(); }
                    if (act == 3 && s.monitorUp) s.selectCamera(safeCams[player.nextInt(safeCams.length)]);
                }
                o.door1open = s.leftDoorOpen;
                o.door2open = s.rightDoorOpen;
                o.monitorUp = s.monitorUp;
                o.cameraLocation = s.camera == Cam.PIRATE_COVE ? "Pirate Cove" : "Other";
                s.step();
                if (++bt == 1000) { bt = 0; o.bonnieTick(); }
                if (++ct == 1300) { ct = 0; o.chickaTick(); }
                if (++ft == 1700) { ft = 0; o.freddyTick(); }
                if (++xt == 1900) { xt = 0; o.foxyTick(); }
                boolean same = s.jay == o.bonnie && s.big == o.chicka && s.diddy == o.freddy && s.kanyeStage == o.foxy
                        && s.jayKill == o.bonnieDeath && s.bigKill == o.chickaDeath && s.diddyKill == o.freddyDeath
                        && s.kanyeKill == o.foxyDeath;
                if (!same) {
                    check(false, "seed " + seed + " tick " + t + ": remix (" + s.jay + "," + s.big + "," + s.diddy + "," + s.kanyeStage
                            + ") vs original (" + o.bonnie + "," + o.chicka + "," + o.freddy + "," + o.foxy + ")");
                }
                comparedTicks++;
                if (s.attackPending() || s.jumpscare != null) break;
            }
        }
        check(comparedTicks > 100_000, "compared a meaningful number of ticks (" + comparedTicks + ")");
    }

    private static void clockAndSixAm() {
        Sim s = new Sim(1, new Random(1));
        // keep enemies home so nothing interferes
        s.jay = s.big = 0;
        check(s.hour() == 0, "night starts at 12 AM");
        run(s, Config.TICKS_PER_HOUR);
        check(s.hour() == 1, "one hour = 4500 ticks (45 s)");
        Sim w = new Sim(1, new Random(1));
        infinitePower(w);
        w.gameTimer = Config.NIGHT_TICKS - 2;
        w.step();
        check(!w.won, "not won one tick before 6 AM");
        w.step();
        check(w.won && w.hour() == 6, "6 AM at exactly 27000 ticks");
        int jay = w.jay, timer = w.gameTimer;
        run(w, 5000);
        check(w.jay == jay && w.gameTimer == timer, "nothing moves after 6 AM");
    }

    private static void powerModel() {
        Sim idle = new Sim(5, new Random(3));
        idle.jay = idle.big = 0;
        // Pin enemies far from the office so the night completes; only power matters here.
        for (int i = 0; i < Config.NIGHT_TICKS && !idle.won; i++) {
            idle.jay = 0; idle.big = 0; idle.diddy = 0; idle.kanyeStage = 0;
            idle.step();
        }
        check(idle.won, "an idle night reaches 6 AM");
        check(Math.abs(idle.power - 46.5) < 1e-9, "idle drain: 107 intervals x 0.5 = 46.5 % left (got " + idle.power + ")");

        Sim mon = new Sim(5, new Random(3));
        mon.monitorUp = true;
        mon.camera = Cam.SHOW_STAGE;
        int outageAt = -1;
        for (int i = 0; i < Config.NIGHT_TICKS && outageAt < 0; i++) {
            mon.jay = 0; mon.big = 0; mon.diddy = 0; mon.kanyeStage = 0;
            mon.step();
            if (mon.outage) outageAt = mon.gameTimer;
        }
        check(outageAt == 100 * Config.POWER_INTERVAL_TICKS, "monitor up all night runs out at 25000 ticks, before 6 AM (got " + outageAt + ")");

        Sim t = new Sim(1, new Random(1));
        double[] expect = Config.POWER_DRAIN;
        for (int u = 0; u <= 5; u++) {
            Sim p = new Sim(1, new Random(1));
            p.leftLight = u >= 1; p.rightLight = u >= 2; p.leftDoorOpen = u < 3; p.rightDoorOpen = u < 4; p.monitorUp = u >= 5;
            check(p.usage() == u, "usage counts " + u + " devices");
            p.powerTick = Config.POWER_INTERVAL_TICKS - 1;
            p.jay = p.big = 0;
            p.step();
            check(Math.abs(100 - p.power - expect[u]) < 1e-9, "usage " + u + " drains " + expect[u]);
        }
        check(t.powerDisplay() == 100, "display 100 at full power");
        t.power = 80.5; check(t.powerDisplay() == 90, "display rounds up to the original 10 % steps");
        t.power = 0.5; check(t.powerDisplay() == 10, "display 10 just above zero");
    }

    private static void outageHappensOnce() {
        Sim s = new Sim(1, new Random(1));
        s.power = 0.5;
        s.leftDoorOpen = false;
        s.leftLight = true;
        s.powerTick = Config.POWER_INTERVAL_TICKS - 1;
        s.step();
        List<Sim.Event> ev = s.drainEvents();
        check(s.outage && s.dead && s.power == 0, "power clamps to 0 and the outage starts");
        check(s.leftDoorOpen && !s.leftLight && !s.monitorUp, "devices shut off in the outage");
        run(s, 300);
        long outages = ev.stream().filter(e -> e == Sim.Event.OUTAGE).count() + s.drainEvents().stream().filter(e -> e == Sim.Event.OUTAGE).count();
        check(outages == 1, "outage fires exactly once");
        check(s.defeatReady(), "outage leads to the defeat screen after the static");
        check(!s.toggleDoor(true), "doors are unavailable after the outage");
    }

    private static void legalMovesAcrossManySeeds() {
        for (int night = 1; night <= 5; night++) {
            for (int seed = 0; seed < 40; seed++) {
                Sim s = new Sim(night, new Random(seed * 31L + night));
                infinitePower(s);
                Random player = new Random(seed);
                int pj = 0, pb = 0, pd = 0, pk = 0;
                for (int t = 0; t < Config.NIGHT_TICKS && !s.dead && !s.won; t++) {
                    if (t % 150 == 0) {
                        s.monitorLock = 0;
                        if (player.nextBoolean()) s.toggleMonitor();
                        if (s.monitorUp) s.selectCamera(Cam.values()[player.nextInt(Cam.values().length)]);
                        else if (player.nextInt(3) == 0) s.toggleDoor(player.nextBoolean());
                    }
                    s.step();
                    check(s.jay == pj || s.jay == pj + 1, "Jay-Z only advances one room (" + pj + "->" + s.jay + ")");
                    check(s.jay <= 9 && (s.jay < 9 || s.jayKill), "Jay-Z reaches 9 only by attacking");
                    check(s.big == pb || s.big == pb + 1, "Biggie only advances one room");
                    check(Math.abs(s.diddy - pd) <= 1, "Diddy moves one room at a time");
                    check(s.diddy <= 4 || s.diddyKill, "Diddy stays on his route until attacking");
                    check(s.kanyeStage == pk || s.kanyeStage == pk + 1 || s.kanyeStage == 1, "Kanye's stage advances or resets to 1");
                    check(s.kanyeStage <= 3, "Kanye's stage never exceeds 3");
                    if (pd == 0 && s.diddy == 1) check(s.jay != 0 && s.big != 0, "Diddy leaves the stage only after the other two");
                    pj = s.jay; pb = s.big; pd = s.diddy; pk = s.kanyeStage;
                }
            }
        }
    }

    private static void doorsProtect() {
        Sim s = new Sim(5, new Random(1));
        infinitePower(s);
        s.jay = 8; s.leftDoorOpen = false; s.monitorUp = true; primeJay(s);
        s.step();
        check(!s.jayKill && s.jay == 8, "closed left door stops Jay-Z (he waits at the door)");
        s.monitorUp = false; s.leftDoorOpen = true; primeJay(s);
        s.step();
        check(!s.jayKill, "Jay-Z can't attack while the monitor is down (original rule)");
        s.monitorUp = true; primeJay(s);
        s.step();
        check(s.jayKill, "open left door + monitor up on his move = attack decided");
        check(s.jumpscare == null, "the jumpscare waits until the monitor comes down");
        s.monitorLock = 0;
        check(!s.toggleMonitor() || !s.monitorUp, "monitor can still be lowered");
        s.step();
        check(s.jumpscare == Sim.Role.JAYZ, "lowering the monitor triggers Jay-Z's jumpscare");

        Sim b = new Sim(5, new Random(1));
        infinitePower(b);
        b.big = 8; b.rightDoorOpen = false; b.monitorUp = true; primeBig(b);
        b.step();
        check(!b.bigKill, "closed right door stops Biggie");
        b.rightDoorOpen = true; primeBig(b);
        b.step();
        check(b.bigKill, "open right door lets Biggie in");
    }

    private static void diddyRetreatsFromClosedDoor() {
        Sim s = new Sim(5, new Random(1));
        infinitePower(s);
        s.jay = 3; s.big = 3; s.diddy = 4; s.rightDoorOpen = false; s.monitorUp = true; primeDiddy(s);
        s.step();
        check(!s.diddyKill && s.diddy == 3, "closed right door: Diddy steps back to the East Hall");
        primeDiddy(s);
        s.step();
        check(s.diddy == 4, "and comes back to the corner on his next move");
        s.rightDoorOpen = true; primeDiddy(s);
        s.step();
        check(s.diddyKill, "open right door while watching cameras: Diddy attacks");
        Sim stage = new Sim(5, new Random(1));
        infinitePower(stage);
        stage.jay = 3; primeDiddy(stage);
        stage.step();
        check(stage.diddy == 0, "Diddy stays on stage while Biggie is still there");
    }

    private static void kanyeRules() {
        Sim s = new Sim(5, new Random(1));
        infinitePower(s);
        s.monitorUp = true; s.camera = Cam.PIRATE_COVE; primeKanye(s);
        s.step();
        check(s.kanyeStage == 0, "watching the Vinyl Vault freezes Kanye");
        s.camera = Cam.SHOW_STAGE; primeKanye(s);
        s.step();
        check(s.kanyeStage == 1, "not watching: he advances");
        s.kanyeStage = 3; s.monitorUp = false; s.leftDoorOpen = false; primeKanye(s);
        s.step();
        check(s.kanyeStage == 1 && s.drainEvents().contains(Sim.Event.KNOCK), "closed left door: knock, back to stage 1");
        s.kanyeStage = 3; s.leftDoorOpen = true; primeKanye(s);
        s.step();
        check(s.kanyeKill && s.jumpscare == Sim.Role.KANYE, "open left door at stage 3: immediate attack");
    }

    private static void kanyeSprintOnlyAtStageThree() {
        Sim s = new Sim(5, new Random(1));
        infinitePower(s);
        s.monitorUp = true; s.camera = Cam.WEST_HALL_A;
        for (int i = 0; i < 400; i++) { s.kanyeStage = 1; s.kanyeTick = 0; s.step(); }
        check(s.monitorUp && !s.kanyeDoorCheck, "watching West Hall A before stage 3 does nothing (FIX)");
        s.kanyeStage = 3;
        run(s, Config.KANYE_RUN_TICKS - 1);
        check(s.monitorUp, "sprint still playing");
        s.step();
        check(!s.monitorUp && s.kanyeDoorCheck, "after the sprint the monitor drops and the door check begins");
        s.leftDoorOpen = false;
        run(s, Config.KANYE_DOOR_DELAY_TICKS);
        check(!s.kanyeKill && s.kanyeStage == 1, "door closed in time: knock and reset");
        // a second sprint works too (FIX: the original could only play it once)
        s.kanyeStage = 3; s.monitorLock = 0; s.toggleMonitor(); s.camera = Cam.WEST_HALL_A; s.leftDoorOpen = true;
        run(s, Config.KANYE_RUN_TICKS + Config.KANYE_DOOR_DELAY_TICKS + 1);
        check(s.kanyeKill, "second sprint with the door open kills");
    }

    private static void pendingAttackVersusSixAm() {
        Sim s = new Sim(5, new Random(1));
        infinitePower(s);
        s.jayKill = true; s.jay = 9; s.monitorUp = true;
        s.gameTimer = Config.NIGHT_TICKS - 1;
        s.step();
        check(s.won && s.jumpscare == null, "6 AM saves a player whose attack is still pending (original rule)");

        Sim same = new Sim(5, new Random(1));
        infinitePower(same);
        same.jay = 8; same.monitorUp = true; same.gameTimer = Config.NIGHT_TICKS - 1; primeJay(same);
        same.step();
        check(same.won && !same.jayKill, "6 AM and an attack in the same tick: the clock is checked first");
    }

    private static void jumpscareFreezesNight() {
        Sim s = new Sim(5, new Random(1));
        s.jumpscare = Sim.Role.DIDDY;
        int t = s.gameTimer;
        double p = s.power;
        run(s, 10);
        check(s.gameTimer == t && s.power == p, "clock and power stop during a jumpscare");
        check(!s.toggleDoor(true) && !s.toggleMonitor(), "controls are locked during a jumpscare");
        run(s, Sim.jumpscareLength(Sim.Role.DIDDY));
        check(s.dead, "jumpscare ends in defeat");
        run(s, Config.DEATH_STATIC_TICKS);
        check(s.defeatReady(), "static then defeat screen");
    }

    private static void monitorRules() {
        Sim s = new Sim(1, new Random(1));
        check(s.toggleMonitor() && s.monitorUp, "monitor raises");
        check(!s.toggleMonitor(), "200 ms lockout blocks an immediate flip");
        run(s, Config.MONITOR_LOCK_TICKS);
        check(s.toggleMonitor() && !s.monitorUp, "flips again after the lockout");
        check(!s.selectCamera(Cam.BACKSTAGE), "cameras can't be switched with the monitor down");
        check(s.toggleDoor(true) && !s.leftDoorOpen, "door works with the monitor down");
        run(s, Config.MONITOR_LOCK_TICKS);
        s.toggleMonitor();
        check(!s.toggleDoor(false) && s.rightDoorOpen, "doors don't respond behind the monitor (FIX)");
        run(s, Config.MONITOR_LOCK_TICKS);
        s.toggleMonitor();
        s.jayKill = true;
        run(s, Config.MONITOR_LOCK_TICKS);
        s.jumpscare = null;
        check(!s.toggleMonitor() && !s.monitorUp, "monitor can't be raised once an attack is decided");
    }

    private static Game newGame(Path save) throws Exception {
        return new Game(Assets.load(), Main.silentAudio(), Save.load(save), false, new Random(4));
    }

    private static void restartResetsEverything() throws Exception {
        Path tmp = Files.createTempDirectory("diddys-test").resolve("save.properties");
        Game g = newGame(tmp);
        g.startNight(3);
        Sim first = g.sim;
        first.power = 12; first.jay = 7; first.leftDoorOpen = false; first.monitorUp = true; first.camera = Cam.BACKSTAGE;
        first.gameTimer = 20000; first.kanyeStage = 3; first.kanyeDoorCheck = true; first.jumpscare = Sim.Role.BIGGIE;
        g.startNight(3);
        Sim s = g.sim;
        check(s != first, "retry builds a fresh night");
        check(s.power == 100 && s.jay == 0 && s.leftDoorOpen && !s.monitorUp && s.camera == Cam.SHOW_STAGE
                && s.gameTimer == 0 && s.kanyeStage == 0 && !s.kanyeDoorCheck && s.jumpscare == null && !s.dead,
                "clock, power, enemies, doors, monitor, camera and attack state all reset");
        check(g.screen == Game.Screen.INTRO && g.caption == null, "retry goes through the night intro");
        for (int i = 0; i < Config.INTRO_TICKS; i++) g.tick();
        check(g.screen == Game.Screen.PLAYING, "intro hands over to play after 5 s");
        g.focusLost();
        check(g.screen == Game.Screen.PAUSED && g.mouseX == -1, "focus loss pauses and forgets the mouse");
        int timer = g.sim.gameTimer;
        for (int i = 0; i < 500; i++) g.tick();
        check(g.sim.gameTimer == timer, "pause freezes the night");
        g.resume();
        g.tick();
        check(g.sim.gameTimer == timer + 1, "resume continues from the same tick");
    }

    private static void officeHitboxesFollowPan() throws Exception {
        Path tmp = Files.createTempDirectory("diddys-test").resolve("save.properties");
        Game g = newGame(tmp);
        g.startNight(1);
        for (int i = 0; i < Config.INTRO_TICKS; i++) g.tick();
        for (int view : new int[] {0, -100, -320}) {
            g.sim.officeView = view;
            g.sim.leftDoorOpen = g.sim.rightDoorOpen = true;
            g.sim.leftLight = g.sim.rightLight = false;
            Rectangle l = g.panelRect(true), r = g.panelRect(false);
            if (l.x + l.width > 0) {
                g.click(Math.max(0, l.x) + 4, l.y + 40);
                check(!g.sim.leftDoorOpen, "left DOOR button hit at view " + view);
                g.click(Math.max(0, l.x) + 4, l.y + 180);
                check(g.sim.leftLight, "left LIGHT button hit at view " + view);
            }
            if (r.x < Config.WIDTH) {
                g.click(Math.min(Config.WIDTH - 1, r.x + 40), r.y + 40);
                check(!g.sim.rightDoorOpen, "right DOOR button hit at view " + view);
                g.click(Math.min(Config.WIDTH - 1, r.x + 40), r.y + 180);
                check(g.sim.rightLight, "right LIGHT button hit at view " + view);
            }
            g.click(640, 360);
            check(true, "clicking empty office space is harmless");
        }
        g.sim.officeView = -100;
        Rectangle stale = new Rectangle(30, 300, 38, 57); // the original fixed hitbox
        boolean was = g.sim.leftDoorOpen = true;
        g.click(stale.x + 5, stale.y + 5);
        check(g.sim.leftDoorOpen == was, "the original fixed-position hitbox no longer toggles an off-screen door (FIX)");
    }

    private static Game.Button button(Game g, String prefix) {
        for (Game.Button b : g.buttons()) if (b.label.startsWith(prefix)) return b;
        return null;
    }

    private static void clickButton(Game g, String prefix) {
        Game.Button b = button(g, prefix);
        check(b != null && b.enabled, "button '" + prefix + "' is available");
        g.click(b.r.x + 5, b.r.y + 5);
    }

    private static void winNight(Game g) {
        for (int i = 0; i < Config.INTRO_TICKS; i++) g.tick();
        g.sim.gameTimer = Config.NIGHT_TICKS - 1;
        g.tick();
        check(g.screen == Game.Screen.VICTORY, "reaching 6 AM shows the victory screen (night " + g.night + ")");
        for (int i = 0; i < 301; i++) g.tick();
    }

    private static void campaignFlow() throws Exception {
        Path f = Files.createTempDirectory("diddys-flow").resolve("save.properties");
        Game g = newGame(f);
        check(!button(g, "Continue").enabled, "Continue is disabled with no campaign");
        clickButton(g, "New Game");
        check(g.screen == Game.Screen.INTRO && g.night == 1, "first New Game starts Night 1 directly");
        for (int n = 1; n <= 4; n++) {
            winNight(g);
            check(Save.load(f).unlockedNight == n + 1, "beating night " + n + " unlocks and saves night " + (n + 1));
            clickButton(g, "Night " + (n + 1));
            check(g.screen == Game.Screen.INTRO && g.night == n + 1, "next night starts from the victory screen");
        }
        winNight(g);
        check(Save.load(f).completed, "beating Night 5 marks the campaign complete");
        clickButton(g, "Continue");
        check(g.screen == Game.Screen.COMPLETE, "Night 5 leads to the ending");
        for (int i = 0; i < 101; i++) g.tick();
        clickButton(g, "Main Menu");
        check(g.screen == Game.Screen.MENU, "ending returns to the menu");

        Game reopened = newGame(f); // like closing and relaunching the game
        check(button(reopened, "Continue").enabled, "Continue is available after relaunch");
        clickButton(reopened, "Continue");
        check(reopened.screen == Game.Screen.NIGHT_SELECT, "Continue opens night select");
        check(button(reopened, "Night 5").enabled, "all unlocked nights are selectable");
        clickButton(reopened, "Back");
        clickButton(reopened, "New Game");
        check(reopened.screen == Game.Screen.CONFIRM_NEW, "New Game asks before erasing progress");
        clickButton(reopened, "Cancel");
        check(reopened.screen == Game.Screen.MENU && Save.load(f).unlockedNight == 5, "Cancel keeps progress");
        clickButton(reopened, "New Game");
        clickButton(reopened, "Yes");
        check(reopened.night == 1 && Save.load(f).unlockedNight == 1 && !Save.load(f).completed, "confirmed New Game resets progress");

        Game locked = newGame(Files.createTempDirectory("diddys-flow2").resolve("save.properties"));
        locked.save.started = true;
        locked.go(Game.Screen.NIGHT_SELECT);
        check(!button(locked, "Night 2").enabled, "locked nights can't be started");
    }

    private static void saveRoundTripAndDamagedFile() throws Exception {
        Path dir = Files.createTempDirectory("diddys-save");
        Path f = dir.resolve("save.properties");
        Save missing = Save.load(f);
        check(missing.unlockedNight == 1 && !missing.started, "missing save starts fresh");
        missing.unlockedNight = 4; missing.started = true; missing.volume = 30; missing.reducedFlash = true;
        missing.write();
        Save back = Save.load(f);
        check(back.unlockedNight == 4 && back.started && back.volume == 30 && back.reducedFlash, "save round-trips");
        Files.write(f, new byte[] {'u', 'n', 'l', 'o', 'c', 'k', 'e', 'd', 'N', 'i', 'g', 'h', 't', '=', 'x', 'x'});
        Save damaged = Save.load(f);
        check(damaged.unlockedNight == 1, "damaged save falls back to defaults");
        check(Files.exists(dir.resolve("save.properties.bad")), "damaged save is kept aside as .bad");
        Files.write(f, "unlockedNight=99\n".getBytes());
        check(Save.load(f).unlockedNight == Config.NIGHTS, "out-of-range night is clamped");
        List<String> leftovers = new ArrayList<>();
        try (java.util.stream.Stream<Path> st = Files.list(dir)) { st.forEach(p -> leftovers.add(p.getFileName().toString())); }
        check(!leftovers.contains("save.properties.tmp"), "no temp file left behind");
    }
}
