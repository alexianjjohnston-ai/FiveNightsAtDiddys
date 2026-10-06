package diddys;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/** Development tool: renders representative scenes to PNGs for visual review (java ... diddys.Main --shots dir). */
final class Shots {
    private final Renderer renderer;
    private final Game game;
    private final Path dir;

    private Shots(Assets assets, Path dir) throws IOException {
        this.dir = dir;
        Files.createDirectories(dir);
        Path tmpSave = Files.createTempFile("diddys-shots", ".properties");
        Files.delete(tmpSave);
        game = new Game(assets, Main.silentAudio(), Save.load(tmpSave), true, new Random(1));
        renderer = new Renderer(assets);
    }

    static void run(Assets assets, Path dir) {
        try {
            new Shots(assets, dir).all();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void shot(String name) throws IOException {
        BufferedImage img = new BufferedImage(Config.WIDTH, Config.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        renderer.render(g, game);
        g.dispose();
        ImageIO.write(img, "png", new File(dir.toFile(), name + ".png"));
    }

    private void playing(Consumer<Sim> setup) {
        game.sim = new Sim(game.night, new Random(2));
        game.screen = Game.Screen.PLAYING;
        game.sim.ticks = 30;
        setup.accept(game.sim);
    }

    private void cam(String name, Cam c, Consumer<Sim> setup) throws IOException {
        playing(s -> {
            s.monitorUp = true;
            s.camera = c;
            s.cameraPos = 100;
            setup.accept(s);
        });
        shot(name);
    }

    private void all() throws IOException {
        game.screen = Game.Screen.MENU;
        game.mouseX = 200;
        game.mouseY = 420;
        shot("01_menu");
        game.menuTwitch = 5;
        shot("02_menu_twitch");
        game.menuTwitch = 0;
        game.go(Game.Screen.CONTROLS);
        shot("03_controls");
        game.go(Game.Screen.SETTINGS);
        shot("04_settings");
        game.night = 1;
        game.go(Game.Screen.INTRO);
        game.screenTicks = 150;
        shot("05_intro_flyer");
        game.screenTicks = 400;
        shot("06_intro_card");

        playing(s -> {});
        shot("10_office_default");
        playing(s -> s.officeView = 0);
        shot("11_office_left_edge");
        playing(s -> { s.officeView = 0; s.leftLight = true; s.jay = 8; });
        shot("12_office_left_light_jayz");
        playing(s -> { s.officeView = -320; s.rightLight = true; s.big = 8; });
        shot("13_office_right_light_biggie");
        playing(s -> { s.officeView = -160; s.leftDoorOpen = false; s.rightDoorOpen = false; s.leftLight = true; s.rightLight = true; s.power = 37; });
        shot("14_office_doors_closed_both_lights");

        cam("20_cam_stage_all", Cam.SHOW_STAGE, s -> {});
        cam("21_cam_stage_diddy_only", Cam.SHOW_STAGE, s -> { s.jay = 3; s.big = 3; });
        cam("22_cam_lounge_far", Cam.DINING_AREA, s -> { s.jay = 1; s.big = 1; s.diddy = 2; });
        cam("23_cam_lounge_close", Cam.DINING_AREA, s -> { s.jay = 2; s.big = 2; });
        cam("24_cam_lounge_diddy", Cam.DINING_AREA, s -> { s.jay = 3; s.big = 3; s.diddy = 1; });
        cam("25_cam_vault_0", Cam.PIRATE_COVE, s -> s.kanyeStage = 0);
        cam("26_cam_vault_1", Cam.PIRATE_COVE, s -> s.kanyeStage = 1);
        cam("27_cam_vault_2", Cam.PIRATE_COVE, s -> s.kanyeStage = 2);
        cam("28_cam_vault_3", Cam.PIRATE_COVE, s -> s.kanyeStage = 3);
        cam("30_cam_green_far", Cam.BACKSTAGE, s -> s.jay = 3);
        cam("31_cam_green_close", Cam.BACKSTAGE, s -> s.jay = 4);
        cam("32_cam_west_hall", Cam.WEST_HALL_A, s -> s.jay = 5);
        cam("33_cam_closet", Cam.SUPPLY_CLOSET, s -> s.jay = 6);
        cam("34_cam_west_corner", Cam.WEST_HALL_B, s -> s.jay = 7);
        cam("35_cam_restrooms_far", Cam.RESTROOMS, s -> s.big = 3);
        cam("36_cam_restrooms_close", Cam.RESTROOMS, s -> s.big = 4);
        cam("37_cam_restrooms_diddy", Cam.RESTROOMS, s -> { s.jay = 3; s.big = 5; s.diddy = 2; });
        cam("38_cam_east_hall_far", Cam.EAST_HALL_A, s -> s.big = 5);
        cam("39_cam_east_hall_close", Cam.EAST_HALL_A, s -> s.big = 6);
        cam("40_cam_east_hall_diddy", Cam.EAST_HALL_A, s -> { s.jay = 3; s.big = 7; s.diddy = 3; });
        cam("41_cam_east_corner_biggie", Cam.EAST_HALL_B, s -> s.big = 7);
        cam("42_cam_east_corner_diddy", Cam.EAST_HALL_B, s -> { s.jay = 3; s.big = 3; s.diddy = 4; });
        cam("43_cam_west_hall_empty", Cam.WEST_HALL_A, s -> {});
        cam("44_sprint_start", Cam.WEST_HALL_A, s -> { s.kanyeStage = 3; s.kanyeRun = 10; });
        cam("45_sprint_mid", Cam.WEST_HALL_A, s -> { s.kanyeStage = 3; s.kanyeRun = 70; });
        cam("46_sprint_end", Cam.WEST_HALL_A, s -> { s.kanyeStage = 3; s.kanyeRun = 120; });

        Sim.Role[] roles = Sim.Role.values();
        for (int i = 0; i < roles.length; i++) {
            Sim.Role r = roles[i];
            playing(s -> { s.jumpscare = r; s.jumpTicks = Sim.jumpscareLength(r) / 2; });
            shot("5" + i + "_jumpscare_" + r.name().toLowerCase());
        }
        playing(s -> { s.dead = true; s.outage = true; s.deathTicks = 10; s.power = 0; });
        shot("55_outage");
        playing(s -> { s.dead = true; s.deathTicks = 120; });
        shot("56_death_static");

        game.loseLine = "You've been signed. Permanently.";
        game.go(Game.Screen.DEFEAT);
        game.screenTicks = 100;
        shot("60_defeat");
        game.go(Game.Screen.VICTORY);
        game.screenTicks = 125;
        shot("61_victory_roll");
        game.screenTicks = 400;
        shot("62_victory");
        game.go(Game.Screen.COMPLETE);
        game.screenTicks = 200;
        shot("63_complete");
        playing(s -> {});
        game.screen = Game.Screen.PAUSED;
        shot("64_paused");
        game.go(Game.Screen.NIGHT_SELECT);
        shot("65_night_select");
        cam("70_sign_west_corner", Cam.WEST_HALL_B, x -> {});
        cam("71_sign_east_corner", Cam.EAST_HALL_B, x -> {});
        cam("72_sign_lounge", Cam.DINING_AREA, x -> { x.jay = 3; x.big = 3; });
        cam("73_sign_restrooms", Cam.RESTROOMS, x -> {});
        cam("74_sign_green_room", Cam.BACKSTAGE, x -> {});
        game.night = 3;
        game.go(Game.Screen.INTRO);
        game.screenTicks = 250;
        shot("75_intro_night3");
        game.night = 2;
        game.go(Game.Screen.VICTORY);
        game.screenTicks = 400;
        shot("76_victory_night2");
        System.out.println("Wrote shots to " + dir.toAbsolutePath());
    }
}
