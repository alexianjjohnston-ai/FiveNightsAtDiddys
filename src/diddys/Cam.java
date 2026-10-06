package diddys;

/**
 * Camera network. Enum names are the original room identifiers; map boxes are the original click
 * rectangles (50x34 at x,y). Display names are the remix's venue names.
 */
enum Cam {
    SHOW_STAGE("1A", "Main Stage", 923, 343),
    DINING_AREA("1B", "VIP Lounge", 904, 399),
    PIRATE_COVE("1C", "Vinyl Vault", 877, 481),
    WEST_HALL_A("2A", "West Hall", 926, 596),
    WEST_HALL_B("2B", "W. Hall Corner", 926, 631),
    SUPPLY_CLOSET("3", "Gear Closet", 850, 578),
    EAST_HALL_A("4A", "East Hall", 1034, 596),
    EAST_HALL_B("4B", "E. Hall Corner", 1034, 631),
    BACKSTAGE("5", "Green Room", 796, 431),
    RESTROOMS("6", "Restrooms", 1146, 436);

    static final int BOX_W = 50, BOX_H = 34;

    final String code, title;
    final int mapX, mapY;

    Cam(String code, String title, int mapX, int mapY) {
        this.code = code;
        this.title = title;
        this.mapX = mapX;
        this.mapY = mapY;
    }

    boolean hit(int x, int y) {
        return x >= mapX && x <= mapX + BOX_W && y >= mapY && y <= mapY + BOX_H;
    }

    /** ORIGINAL: these two camera feeds are drawn at a fixed -100 offset instead of sweeping. */
    boolean fixedView() {
        return this == PIRATE_COVE || this == SUPPLY_CLOSET;
    }
}
