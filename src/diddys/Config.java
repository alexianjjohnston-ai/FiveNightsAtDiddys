package diddys;

/**
 * All gameplay numbers in one place. Values marked ORIGINAL are copied from the supplied
 * 2014 source (Main.java / Office.java). Values marked REMIX are new and documented in README.
 * One simulation tick = 10 ms, matching the original loop's Thread.sleep(10).
 */
final class Config {
    private Config() {}

    static final int TICK_MS = 10;
    static final int TICKS_PER_SECOND = 1000 / TICK_MS;

    // ORIGINAL: gameTimer counts to 27000 ticks (4.5 minutes) for 12 AM -> 6 AM.
    static final int NIGHT_TICKS = 27000;
    static final int TICKS_PER_HOUR = NIGHT_TICKS / 6;

    // ORIGINAL: movement opportunity intervals (BonnieTick 1000, ChickaTick 1300, FreddyTick 1700, FoxyTick 1900).
    static final int JAYZ_INTERVAL = 1000;   // Bonnie role
    static final int BIGGIE_INTERVAL = 1300; // Chica role
    static final int DIDDY_INTERVAL = 1700;  // Freddy role
    static final int KANYE_INTERVAL = 1900;  // Foxy role

    // ORIGINAL: monitorTimer lockout after raising/lowering the monitor.
    static final int MONITOR_LOCK_TICKS = 20;

    // ORIGINAL: Foxy's West Hall A sprint = 31 frames x 4 ticks, then 50 ticks before the door check.
    static final int KANYE_RUN_TICKS = 31 * 4;
    static final int KANYE_DOOR_DELAY_TICKS = 50;

    // ORIGINAL: jumpscare lengths = frame count x 4 ticks (Bonnie 11, Chica 16, Freddy 28, Foxy 19).
    static final int JUMPSCARE_JAYZ = 11 * 4;
    static final int JUMPSCARE_BIGGIE = 16 * 4;
    static final int JUMPSCARE_DIDDY = 28 * 4;
    static final int JUMPSCARE_KANYE = 19 * 4;

    // ORIGINAL: power-out animation 20 frames x 4 ticks; death static runs 200 ticks before the Game Over screen.
    static final int OUTAGE_ANIM_TICKS = 20 * 4;
    static final int DEATH_STATIC_TICKS = 200;

    // ORIGINAL: newspaper intro 500 ticks.
    static final int INTRO_TICKS = 500;

    // ORIGINAL: Office.descreasepower() table, indexed by devices in use (lights, closed doors, monitor).
    static final double[] POWER_DRAIN = {0.5, 1, 2, 4, 5, 6};
    // REMIX FIX: the original drained every 100 ticks but never set devicedUsed, so power always hit 0 at
    // 200 s, before 6 AM at 270 s, making the night unwinnable. Device use is now wired in, and the
    // interval is 250 ticks so an idle night ends at 46 % but keeping one device on all night runs out just before 6 AM.
    static final int POWER_INTERVAL_TICKS = 250;

    // REMIX EXTENSION: the original has an unused per-enemy Difficulty field: each move opportunity is skipped
    // when 1 + floor(random * D) == D, i.e. with chance 1/D (D = 0 never skips). The shipped game never
    // set it, so it always ran at D = 0. Nights 1-4 use it; Night 5 is exactly the original behaviour.
    static final int[] NIGHT_SKIP_D = {2, 3, 4, 6, 0};
    static final int NIGHTS = NIGHT_SKIP_D.length;

    // ORIGINAL: office pans 5 px every 2 ticks while the mouse is left of x=400 or right of x=880.
    static final int OFFICE_PAN_STEP = 5;
    static final int OFFICE_PAN_LEFT_ZONE = 400, OFFICE_PAN_RIGHT_ZONE = 880;
    static final int OFFICE_VIEW_MIN = -320, OFFICE_VIEW_START = -100;

    // ORIGINAL: camera image sweeps 0..200 px at 1 px/tick and pauses 200 ticks at each end.
    static final int CAMERA_PAN_MAX = 200, CAMERA_PAN_PAUSE = 200;

    static final int WIDTH = 1280, HEIGHT = 720;
}
