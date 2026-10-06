package diddys;

import java.util.Random;

/**
 * Rough balance probe (not a test): a cautious bot that only acts on what a player could see
 * (hall lights, the camera it is looking at) plays many nights. Run: ./test.sh then
 * java -cp build/test:assets diddys.BalanceBot
 */
public final class BalanceBot {
    static boolean TRACE;

    public static void main(String[] args) {
        if (args.length > 0) {
            TRACE = true;
            play(new Sim(1, new Random(7)), new Random(7));
            return;
        }
        int games = 400;
        for (int night = 1; night <= Config.NIGHTS; night++) {
            int wins = 0, outages = 0, outageTime = 0;
            int[] killers = new int[4];
            double powerLeft = 0;
            for (int g = 0; g < games; g++) {
                Sim s = new Sim(night, new Random(g * 1009L + night));
                play(s, new Random(g));
                if (s.won) { wins++; powerLeft += s.power; }
                if (s.outage) { outages++; outageTime += s.gameTimer; }
                else if (s.dead) killers[s.jumpscare == null ? 0 : s.jumpscare.ordinal()]++;
            }
            System.out.printf("Night %d: %3d%% wins, %3d%% outages (avg at %d AM), avg power at 6 AM %.0f%%, kills J/B/D/K %d/%d/%d/%d%n",
                    night, wins * 100 / games, outages * 100 / games, outages == 0 ? 0 : outageTime / outages / Config.TICKS_PER_HOUR,
                    wins == 0 ? 0 : powerLeft / wins, killers[0], killers[1], killers[2], killers[3]);
        }
    }

    private static void play(Sim s, Random r) {
        boolean jayAtDoor = false, bigAtDoor = false, diddyLeftStage = false;
        int kanyeAlert = 0;
        while (!s.won && !s.dead) {
            int t = s.gameTimer;
            boolean deciding = s.jumpscare == null && !s.monitorUp;
            // Light checks every ~4 s: a quick flick of each hall light.
            if (deciding && t % 700 == 0) { s.toggleLight(true); }
            if (s.leftLight && t % 700 >= 30) { if (s.jay == 8) jayAtDoor = true; s.toggleLight(true); }
            if (deciding && t % 700 == 350) { s.toggleLight(false); }
            if (s.rightLight && t % 700 >= 380) { if (s.big == 8) bigAtDoor = true; s.toggleLight(false); }

            boolean wantLeftClosed = jayAtDoor || kanyeAlert > 0;
            if (!s.monitorUp && s.leftDoorOpen == wantLeftClosed) s.toggleDoor(true);

            // Camera use: watch the Vinyl Vault in bursts while the left door isn't already closed for Jay-Z.
            boolean wantMonitor = !jayAtDoor && (t / 150) % 4 == 0;
            boolean rightNeeded = bigAtDoor || diddyLeftStage;
            if (wantMonitor && !s.monitorUp && s.monitorLock == 0 && !s.attackPending()) {
                if (rightNeeded && s.rightDoorOpen) s.toggleDoor(false);
                if (s.toggleMonitor()) s.selectCamera(r.nextInt(5) == 0 ? Cam.SHOW_STAGE : Cam.PIRATE_COVE);
            } else if (!wantMonitor && s.monitorUp && s.monitorLock == 0) {
                s.toggleMonitor();
            }
            if (s.monitorUp) {
                if (s.camera == Cam.SHOW_STAGE && s.diddy != 0) diddyLeftStage = true;
                if (s.camera == Cam.PIRATE_COVE && s.kanyeStage == 3) kanyeAlert = 4000; // Vault empty: he is coming
            }
            if (!s.monitorUp && !s.rightDoorOpen) s.toggleDoor(false); // right door only matters behind the monitor
            if (kanyeAlert > 0) kanyeAlert--;
            if (TRACE && t % 1500 == 0) System.out.printf("  t=%5d pwr=%5.1f use=%d L%s R%s mon=%s ll=%s rl=%s jay=%d big=%d kan=%d alert=%d%n", t, s.power, s.usage(),
                    s.leftDoorOpen ? "o" : "C", s.rightDoorOpen ? "o" : "C", s.monitorUp, s.leftLight, s.rightLight, s.jay, s.big, s.kanyeStage, kanyeAlert);
            s.step();
            if (s.drainEvents().contains(Sim.Event.KNOCK)) kanyeAlert = 0; // the knock means he went back
        }
    }
}
