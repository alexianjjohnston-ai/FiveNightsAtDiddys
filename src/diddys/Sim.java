package diddys;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * One night of gameplay, advanced in fixed 10 ms ticks. This is a port of the original Main.run() loop
 * and the Bonnie/Chicka/Freddy/Foxy/Office classes. Role mapping:
 *   Bonnie -> Jay-Z (jay), Chica -> Biggie (big), Freddy -> P. Diddy (diddy), Foxy -> Kanye (kanye).
 *
 * Room indices are the original ones:
 *   jay:   0 stage, 1 lounge far, 2 lounge close, 3 green room far, 4 green room close,
 *          5 west hall, 6 gear closet, 7 west corner, 8 left door, 9 attacking
 *   big:   0 stage, 1 lounge far, 2 lounge close, 3 restrooms far, 4 restrooms close,
 *          5 east hall far, 6 east hall close, 7 east corner, 8 right door, 9 attacking
 *   diddy: 0 stage, 1 lounge, 2 restrooms, 3 east hall, 4 east corner, 5 attacking
 *   kanye: stage 0..3 behind/out of the Vinyl Vault curtain
 */
final class Sim {
    enum Role { JAYZ, BIGGIE, DIDDY, KANYE }

    enum Event { KNOCK, RUN, JUMPSCARE, OUTAGE, WIN, MONITOR_FORCED_DOWN }

    final int night;
    private final int skipD;
    private final Random rng;
    private final List<Event> events = new ArrayList<>();

    // Clock and office
    int ticks, gameTimer;
    boolean won;
    boolean leftLight, rightLight, leftDoorOpen = true, rightDoorOpen = true, monitorUp;
    double power = 100;
    int powerTick, monitorLock;
    Cam camera = Cam.SHOW_STAGE;
    int officeView = Config.OFFICE_VIEW_START;
    int panInput; // -1 pan toward left side, +1 toward right side, 0 none (set by mouse position)
    int cameraPos, cameraDir = 1, cameraPause;

    // Enemies
    int jay, big, diddy, kanyeStage;
    int jayTick, bigTick, diddyTick, kanyeTick;
    boolean jayKill, bigKill, diddyKill, kanyeKill;
    int kanyeRun;
    boolean kanyeDoorCheck;
    int kanyeDoorTimer;

    // Defeat
    Role jumpscare;
    int jumpTicks;
    boolean outage, dead;
    int deathTicks;

    Sim(int night, Random rng) {
        this.night = Math.max(1, Math.min(Config.NIGHTS, night));
        this.skipD = Config.NIGHT_SKIP_D[this.night - 1];
        this.rng = rng;
    }

    /** Advance one 10 ms tick. Order follows the original run() loop. */
    void step() {
        ticks++;
        panCamera();
        if (dead) {
            deathTicks++;
            return;
        }
        if (won) return;
        if (jumpscare != null) {
            if (++jumpTicks >= jumpscareLength(jumpscare)) dead = true;
            return;
        }
        // The clock is checked before AI, as in the original, so 6 AM beats an attack decided in the same
        // tick. An attack that is only pending (monitor still up) is also beaten by 6 AM, as in the original.
        if (++gameTimer >= Config.NIGHT_TICKS) {
            won = true;
            events.add(Event.WIN);
            return;
        }
        if (monitorLock > 0) monitorLock--;
        kanyeDoorResolve();
        if (++jayTick >= Config.JAYZ_INTERVAL) { jayTick = 0; jayMove(); }
        if (++bigTick >= Config.BIGGIE_INTERVAL) { bigTick = 0; bigMove(); }
        if (++diddyTick >= Config.DIDDY_INTERVAL) { diddyTick = 0; diddyMove(); }
        if (++kanyeTick >= Config.KANYE_INTERVAL) { kanyeTick = 0; kanyeMove(); }
        panOffice();
        kanyeSprint();
        startJumpscareIfDue();
        if (jumpscare != null) return;
        drainPower();
    }

    // --- Enemy rules (ported) ---

    /**
     * ORIGINAL difficulty roll: skip when 1 + (int)(random * D) == D. D = 0 never skips. Random numbers are
     * drawn in the same order as the original (Bonnie/Chica always roll), so Night 5 replays it exactly.
     */
    private boolean skipMove() {
        return 1 + (int) (rng.nextDouble() * skipD) == skipD;
    }

    /** REMIX: the same roll for Freddy/Foxy roles, which had no roll in the original; absent on Night 5. */
    private boolean extraSkip() {
        return skipD != 0 && skipMove();
    }

    private int roll8() {
        return 1 + (int) (rng.nextDouble() * 8);
    }

    /** Bonnie.tick(). The original's retreat branch is unreachable, so the bot only ever advances. */
    private void jayMove() {
        if (jay == 8 && leftDoorOpen && monitorUp) {
            jay = 9;
            jayKill = true;
            return;
        }
        if (skipMove()) return;
        int roll = roll8();
        if (roll < jay) return;
        // ORIGINAL quirk kept: blocking compares raw room numbers across different routes.
        if (big == jay + 1 || diddy == jay + 1) return;
        if (jay + 1 > 8) return;
        jay++;
    }

    /** Chicka.tick(): same rule as Jay-Z, right door. */
    private void bigMove() {
        if (big == 8 && rightDoorOpen && monitorUp) {
            big = 9;
            bigKill = true;
            return;
        }
        if (skipMove()) return;
        int roll = roll8();
        if (roll < big) return;
        if (jay == big + 1 || diddy == big + 1) return;
        if (big + 1 > 8) return;
        big++;
    }

    /** Freddy.tick(): leaves the stage only after both others; shuffles 3 <-> 4 outside the right door. */
    private void diddyMove() {
        if (diddy == 0 && (jay == 0 || big == 0)) return;
        if (monitorUp && rightDoorOpen && diddy == 4) {
            diddyKill = true;
            diddy = 5;
            return;
        }
        if (extraSkip()) return; // REMIX: Freddy had a Difficulty field but no roll
        if (big == diddy + 1 || jay == diddy + 1) return;
        diddy += diddy != 4 ? 1 : -1;
    }

    /** Foxy.tick(): frozen while watched on the Vinyl Vault camera; at stage 3 he attacks the left door. */
    private void kanyeMove() {
        if (monitorUp && camera == Cam.PIRATE_COVE) return;
        if (kanyeStage != 3) {
            if (!extraSkip()) kanyeStage++; // REMIX: roll applies to advancing only
        } else if (!leftDoorOpen) {
            kanyeStage = 1;
            events.add(Event.KNOCK);
        } else {
            kanyeKill = true;
        }
    }

    /** ORIGINAL Fox counter. FIX: the original advanced it whenever West Hall A was watched, at any stage. */
    private void kanyeSprint() {
        if (kanyeStage != 3) {
            kanyeRun = 0;
            return;
        }
        if (!monitorUp || camera != Cam.WEST_HALL_A || kanyeDoorCheck) return;
        if (kanyeRun == 0) events.add(Event.RUN);
        if (++kanyeRun >= Config.KANYE_RUN_TICKS) {
            monitorUp = false;
            events.add(Event.MONITOR_FORCED_DOWN);
            kanyeDoorCheck = true;
            kanyeDoorTimer = 0; // FIX: the original never reset this, so later sprints skipped the delay
            kanyeRun = 0;       // FIX: the original left Fox at 0, so a second sprint could never play
        }
    }

    /** ORIGINAL foxyTime: 50 ticks after the sprint, the left door decides. */
    private void kanyeDoorResolve() {
        if (!kanyeDoorCheck || ++kanyeDoorTimer < Config.KANYE_DOOR_DELAY_TICKS) return;
        kanyeDoorCheck = false;
        if (leftDoorOpen) {
            kanyeKill = true;
        } else {
            events.add(Event.KNOCK);
            kanyeStage = 1;
        }
    }

    /** Bonnie/Chica/Freddy jumpscares wait for the monitor to come down; Kanye's is immediate. */
    private void startJumpscareIfDue() {
        Role who = null;
        if (kanyeKill) who = Role.KANYE;
        else if (!monitorUp && jayKill) who = Role.JAYZ;
        else if (!monitorUp && bigKill) who = Role.BIGGIE;
        else if (!monitorUp && diddyKill) who = Role.DIDDY;
        if (who == null) return;
        jumpscare = who;
        jumpTicks = 0;
        monitorUp = false;
        leftLight = rightLight = false;
        events.add(Event.JUMPSCARE);
    }

    static int jumpscareLength(Role r) {
        switch (r) {
            case JAYZ: return Config.JUMPSCARE_JAYZ;
            case BIGGIE: return Config.JUMPSCARE_BIGGIE;
            case DIDDY: return Config.JUMPSCARE_DIDDY;
            default: return Config.JUMPSCARE_KANYE;
        }
    }

    // --- Power (the only place power changes) ---

    int usage() {
        int u = 0;
        if (leftLight) u++;
        if (rightLight) u++;
        if (!leftDoorOpen) u++;
        if (!rightDoorOpen) u++;
        if (monitorUp) u++;
        return u;
    }

    private void drainPower() {
        if (++powerTick < Config.POWER_INTERVAL_TICKS) return;
        powerTick = 0;
        power = Math.max(0, power - Config.POWER_DRAIN[usage()]);
        if (power <= 0) {
            // ORIGINAL: reaching 0 % plays the outage sequence and the night is lost.
            outage = true;
            dead = true;
            deathTicks = 0;
            leftLight = rightLight = monitorUp = false;
            leftDoorOpen = rightDoorOpen = true;
            events.add(Event.OUTAGE);
        }
    }

    /** ORIGINAL HUD shows power in 10 % steps: 100 above 90, 90 for (80, 90], ..., 0 at exactly 0. */
    int powerDisplay() {
        return power <= 0 ? 0 : (int) Math.ceil(power / 10.0) * 10;
    }

    // --- Views ---

    private void panCamera() {
        if (cameraPause > 0) {
            cameraPause--;
            return;
        }
        cameraPos += cameraDir;
        if (cameraPos <= 0 || cameraPos >= Config.CAMERA_PAN_MAX) {
            cameraPos = Math.max(0, Math.min(Config.CAMERA_PAN_MAX, cameraPos));
            cameraDir = -cameraDir;
            cameraPause = Config.CAMERA_PAN_PAUSE;
        }
    }

    private void panOffice() {
        if ((ticks & 1) != 0 || monitorUp || panInput == 0) return;
        officeView = Math.max(Config.OFFICE_VIEW_MIN,
                Math.min(0, officeView - panInput * Config.OFFICE_PAN_STEP));
    }

    // --- Player actions (return false when the original rules don't allow them right now) ---

    boolean inputLocked() {
        return dead || won || jumpscare != null;
    }

    boolean attackPending() {
        return jayKill || bigKill || diddyKill || kanyeKill;
    }

    boolean toggleMonitor() {
        if (inputLocked() || monitorLock > 0) return false;
        if (!monitorUp && attackPending()) return false; // ORIGINAL: can't raise it once an attack is decided
        monitorUp = !monitorUp;
        monitorLock = Config.MONITOR_LOCK_TICKS;
        return true;
    }

    /** Doors and lights only respond with the monitor down (FIX: original hitboxes stayed live behind it). */
    boolean toggleDoor(boolean left) {
        if (inputLocked() || monitorUp) return false;
        if (left) leftDoorOpen = !leftDoorOpen;
        else rightDoorOpen = !rightDoorOpen;
        return true;
    }

    boolean toggleLight(boolean left) {
        if (inputLocked() || monitorUp) return false;
        if (left) leftLight = !leftLight;
        else rightLight = !rightLight;
        return true;
    }

    boolean selectCamera(Cam c) {
        if (inputLocked() || !monitorUp || c == camera) return false;
        camera = c;
        return true;
    }

    /** 0 = 12 AM ... 6 = 6 AM, derived from the one authoritative clock. */
    int hour() {
        return Math.min(6, gameTimer / Config.TICKS_PER_HOUR);
    }

    boolean defeatReady() {
        return dead && deathTicks >= Config.DEATH_STATIC_TICKS;
    }

    List<Event> drainEvents() {
        List<Event> out = new ArrayList<>(events);
        events.clear();
        return out;
    }
}
