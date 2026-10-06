package diddys;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.imageio.ImageIO;

/**
 * Loads everything named in assets/manifest.properties once at startup. Rooms and UI images are
 * critical (a missing one aborts with a readable message); character photos fall back to a labelled
 * placeholder silhouette; sounds are optional (see Audio).
 */
final class Assets {
    static final class MissingAssets extends Exception {
        MissingAssets(String msg) { super(msg); }
    }

    /** A processed character photo: background masked out, feathered, colour-graded to the rooms. */
    static final class Cutout {
        final BufferedImage img;
        final double[][] eyes;
        final Rectangle face;
        final boolean placeholder;
        private final Map<String, BufferedImage> cache = new HashMap<>();

        Cutout(BufferedImage img, double[][] eyes, Rectangle face, boolean placeholder) {
            this.img = img;
            this.eyes = eyes;
            this.face = face;
            this.placeholder = placeholder;
        }

        /**
         * Crop + brightness + bottom fade + top-down shading, cached so nothing is processed during play.
         * Every crop also gets soft side edges, so a photo cut at its border never shows a hard line.
         */
        BufferedImage variant(Rectangle crop, double bright, double fade, double shade) {
            String k = crop + "|" + bright + "|" + fade + "|" + shade;
            return cache.computeIfAbsent(k, kk -> {
                BufferedImage out = new BufferedImage(crop.width, crop.height, BufferedImage.TYPE_INT_ARGB);
                double edge = Math.max(4, crop.width * 0.07);
                for (int y = 0; y < crop.height; y++) {
                    double fy = y / (double) crop.height;
                    double fa = smooth(fade <= 0 ? 1 : (crop.height - 1 - y) / (fade * crop.height))
                            * smooth(y / (edge * 0.5));
                    double lit = bright * (1 - shade * Math.pow(fy, 1.4));
                    for (int x = 0; x < crop.width; x++) {
                        int sx = crop.x + x, sy = crop.y + y;
                        if (sx < 0 || sy < 0 || sx >= img.getWidth() || sy >= img.getHeight()) continue;
                        int p = img.getRGB(sx, sy);
                        double side = smooth(Math.min(x, crop.width - 1 - x) / edge);
                        int a = (int) (((p >>> 24) & 255) * fa * side);
                        double l = lit * (0.82 + 0.18 * side);
                        int r = clamp((int) (((p >> 16) & 255) * l));
                        int g = clamp((int) (((p >> 8) & 255) * l));
                        int b = clamp((int) ((p & 255) * l));
                        out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                    }
                }
                return out;
            });
        }

        private static double smooth(double t) {
            t = Math.max(0, Math.min(1, t));
            return t * t * (3 - 2 * t);
        }

        Rectangle full() {
            return new Rectangle(0, 0, img.getWidth(), img.getHeight());
        }
    }

    /** Where and how a character appears in a room. Coordinates are in the room image's pixels. */
    static final class Placement {
        String who;
        double x, y, h, bright = 1, alpha = 1, rot, fade = 0.3, shade = 0.45;
        Rectangle crop;
        boolean glow, flip;
        int z;
        final List<Rectangle> occluders = new ArrayList<>();

        Placement copy() {
            Placement q = new Placement();
            q.who = who; q.x = x; q.y = y; q.h = h; q.bright = bright; q.alpha = alpha; q.rot = rot;
            q.fade = fade; q.shade = shade; q.crop = crop; q.glow = glow; q.flip = flip; q.z = z;
            q.occluders.addAll(occluders);
            return q;
        }
    }

    /** A sign, poster or banner drawn onto a room (see "sign." in the manifest). */
    static final class Sign {
        String room, style;
        Rectangle r;
        double rot, bright = 1;
        String[] faces = new String[0];
        String[] lines;
    }

    private final Properties manifest = new Properties();
    private final Map<String, BufferedImage> images = new HashMap<>();
    private final Map<String, Cutout> characters = new HashMap<>();
    private final Map<String, Placement> placements = new HashMap<>();
    final List<Sign> signs = new ArrayList<>();
    final List<String> warnings = new ArrayList<>();

    static Assets load() throws MissingAssets {
        Assets a = new Assets();
        try (InputStream in = Assets.class.getResourceAsStream("/manifest.properties")) {
            if (in == null) throw new MissingAssets("assets/manifest.properties was not found on the classpath.");
            a.manifest.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MissingAssets("assets/manifest.properties could not be read: " + e.getMessage());
        }
        List<String> missing = new ArrayList<>();
        for (String k : a.manifest.stringPropertyNames()) {
            if (k.startsWith("img.")) a.loadImage(k.substring(4), a.manifest.getProperty(k), missing);
        }
        if (!missing.isEmpty()) {
            throw new MissingAssets("These required files are missing or unreadable in the assets folder:\n  "
                    + String.join("\n  ", missing));
        }
        for (String k : a.manifest.stringPropertyNames()) {
            if (k.startsWith("char.") && k.endsWith(".file")) {
                String id = k.substring(5, k.length() - 5);
                a.characters.put(id, a.loadCharacter(id));
            }
            if (k.startsWith("place.")) a.placements.put(k.substring(6), a.parsePlacement(k, a.manifest.getProperty(k)));
            if (k.startsWith("sign.")) a.signs.add(a.parseSign(k, a.manifest.getProperty(k)));
        }
        a.signs.removeIf(x -> x == null);
        return a;
    }

    BufferedImage img(String key) {
        BufferedImage b = images.get(key);
        if (b == null) throw new IllegalStateException("Image key not in manifest: img." + key);
        return b;
    }

    Cutout character(String id) {
        return characters.get(id);
    }

    Placement placement(String key) {
        return placements.get(key);
    }

    java.util.Collection<Placement> placements() {
        return placements.values();
    }

    java.util.Set<String> characterIds() {
        return characters.keySet();
    }

    String text(String key, String fallback) {
        return manifest.getProperty(key, fallback);
    }

    Map<String, String> sounds() {
        Map<String, String> m = new HashMap<>();
        for (String k : manifest.stringPropertyNames()) if (k.startsWith("snd.")) m.put(k.substring(4), manifest.getProperty(k));
        return m;
    }

    // --- loading ---

    private void loadImage(String key, String spec, List<String> missing) {
        String[] parts = spec.trim().split("\\s+");
        BufferedImage raw = read(parts[0]);
        if (raw == null) {
            missing.add(parts[0] + "   (img." + key + ")");
            return;
        }
        boolean keyed = parts.length > 1 && parts[1].startsWith("key=");
        BufferedImage img = toType(raw, keyed || raw.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        if (keyed) {
            String[] kv = parts[1].substring(4).split(":");
            int color = Integer.parseInt(kv[0], 16);
            int tol = kv.length > 1 ? Integer.parseInt(kv[1]) : 0;
            colorKey(img, color, tol);
        }
        images.put(key, img);
    }

    private static BufferedImage read(String path) {
        URL u = Assets.class.getResource("/" + path);
        if (u == null) return null;
        try {
            return ImageIO.read(u);
        } catch (IOException e) {
            return null;
        }
    }

    private static BufferedImage toType(BufferedImage src, int type) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), type);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /** Like the original makeColorTransparent(), with an optional tolerance for JPEG-ish edges. */
    private static void colorKey(BufferedImage img, int color, int tol) {
        int kr = (color >> 16) & 255, kg = (color >> 8) & 255, kb = color & 255;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                int d = Math.max(Math.abs(((p >> 16) & 255) - kr),
                        Math.max(Math.abs(((p >> 8) & 255) - kg), Math.abs((p & 255) - kb)));
                if (d <= tol) img.setRGB(x, y, p & 0x00FFFFFF);
            }
        }
    }

    private Cutout loadCharacter(String id) {
        String p = "char." + id + ".";
        String file = manifest.getProperty(p + "file");
        BufferedImage raw = read(file);
        double[][] eyes = parsePoints(manifest.getProperty(p + "eyes", ""));
        Rectangle face = parseRect(manifest.getProperty(p + "face", ""));
        if (raw == null) {
            warnings.add("Character photo missing, using placeholder: " + file);
            BufferedImage ph = placeholderSilhouette(id);
            return new Cutout(ph, new double[][] {{170, 190}, {250, 190}}, new Rectangle(60, 40, 300, 360), true);
        }
        BufferedImage img = toType(raw, BufferedImage.TYPE_INT_ARGB);
        String outline = manifest.getProperty(p + "outline", "").trim();
        if (!outline.isEmpty()) {
            double[][] pts = parsePoints(outline);
            Polygon poly = new Polygon();
            for (double[] pt : pts) poly.addPoint((int) pt[0], (int) pt[1]);
            int feather = Integer.parseInt(manifest.getProperty(p + "feather", "8").trim());
            applyMask(img, poly, feather);
        }
        String bgKey = manifest.getProperty(p + "key", "").trim();
        if (!bgKey.isEmpty()) {
            String[] kv = bgKey.split(":");
            softKey(img, Integer.parseInt(kv[0], 16), Integer.parseInt(kv[1]), Integer.parseInt(kv[2]));
        }
        double[] grade = parseNums(manifest.getProperty(p + "grade", "0.6 0.92 0.96 1.04 0.92"));
        grade(img, grade[0], grade[1], grade[2], grade[3], grade[4]);
        if (face == null) face = new Rectangle(0, 0, img.getWidth(), img.getHeight());
        return new Cutout(img, eyes, face, false);
    }

    /** Polygon mask with a box-blurred (feathered) edge. Pixels outside the outline become transparent. */
    private static void applyMask(BufferedImage img, Polygon poly, int feather) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage m = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = m.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillPolygon(poly);
        g.dispose();
        float[] a = new float[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) a[y * w + x] = (m.getRGB(x, y) & 255) / 255f;
        for (int pass = 0; pass < 3 && feather > 0; pass++) a = boxBlur(a, w, h, feather);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = img.getRGB(x, y);
                int alpha = (int) (((p >>> 24) & 255) * a[y * w + x]);
                img.setRGB(x, y, (alpha << 24) | (p & 0xFFFFFF));
            }
        }
    }

    private static float[] boxBlur(float[] src, int w, int h, int r) {
        float[] tmp = new float[src.length], out = new float[src.length];
        for (int y = 0; y < h; y++) {
            float sum = 0;
            for (int x = -r; x <= r; x++) sum += src[y * w + Math.max(0, Math.min(w - 1, x))];
            for (int x = 0; x < w; x++) {
                tmp[y * w + x] = sum / (2 * r + 1);
                sum += src[y * w + Math.min(w - 1, x + r + 1)] - src[y * w + Math.max(0, x - r)];
            }
        }
        for (int x = 0; x < w; x++) {
            float sum = 0;
            for (int y = -r; y <= r; y++) sum += tmp[Math.max(0, Math.min(h - 1, y)) * w + x];
            for (int y = 0; y < h; y++) {
                out[y * w + x] = sum / (2 * r + 1);
                sum += tmp[Math.min(h - 1, y + r + 1) * w + x] - tmp[Math.max(0, y - r) * w + x];
            }
        }
        return out;
    }

    /** Removes a flat studio backdrop: alpha ramps from 0 (within lo) to 1 (beyond hi) colour distance. */
    private static void softKey(BufferedImage img, int color, int lo, int hi) {
        int kr = (color >> 16) & 255, kg = (color >> 8) & 255, kb = color & 255;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                double d = Math.sqrt(sq(((p >> 16) & 255) - kr) + sq(((p >> 8) & 255) - kg) + sq((p & 255) - kb));
                double t = Math.max(0, Math.min(1, (d - lo) / (double) (hi - lo)));
                int alpha = (int) (((p >>> 24) & 255) * t);
                img.setRGB(x, y, (alpha << 24) | (p & 0xFFFFFF));
            }
        }
    }

    /** Desaturate, tint and flatten contrast so studio photos sit in the dark rendered rooms. */
    private static void grade(BufferedImage img, double sat, double tr, double tg, double tb, double contrast) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                double r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                double l = 0.3 * r + 0.59 * g + 0.11 * b;
                r = l + (r - l) * sat;
                g = l + (g - l) * sat;
                b = l + (b - l) * sat;
                r = ((r - 128) * contrast + 128) * tr;
                g = ((g - 128) * contrast + 128) * tg;
                b = ((b - 128) * contrast + 128) * tb;
                img.setRGB(x, y, (p & 0xFF000000) | (clamp((int) r) << 16) | (clamp((int) g) << 8) | clamp((int) b));
            }
        }
    }

    private static BufferedImage placeholderSilhouette(String id) {
        BufferedImage b = new BufferedImage(420, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(40, 40, 46));
        g.fill(new Ellipse2D.Double(110, 40, 200, 250));
        g.fillRoundRect(20, 300, 380, 320, 160, 160);
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(200, 60, 60));
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 26));
        g.drawString("PHOTO MISSING", 105, 420);
        g.drawString(id, 105, 455);
        g.dispose();
        return b;
    }

    // --- parsing ---

    private Placement parsePlacement(String key, String spec) {
        Placement pl = new Placement();
        String[] parts = spec.trim().split("\\s+");
        pl.who = parts[0];
        for (int i = 1; i < parts.length; i++) {
            String[] kv = parts[i].split("=", 2);
            if (kv.length != 2) {
                warnings.add(key + ": ignored '" + parts[i] + "'");
                continue;
            }
            String v = kv[1];
            switch (kv[0]) {
                case "x": pl.x = Double.parseDouble(v); break;
                case "y": pl.y = Double.parseDouble(v); break;
                case "h": pl.h = Double.parseDouble(v); break;
                case "b": pl.bright = Double.parseDouble(v); break;
                case "a": pl.alpha = Double.parseDouble(v); break;
                case "rot": pl.rot = Double.parseDouble(v); break;
                case "fade": pl.fade = Double.parseDouble(v); break;
                case "shade": pl.shade = Double.parseDouble(v); break;
                case "crop": pl.crop = parseRect(v); break;
                case "glow": pl.glow = v.equals("1"); break;
                case "flip": pl.flip = v.equals("1"); break;
                case "z": pl.z = Integer.parseInt(v); break;
                case "occ":
                    for (String r : v.split(";")) pl.occluders.add(parseRect(r));
                    break;
                default: warnings.add(key + ": unknown option '" + kv[0] + "'");
            }
        }
        return pl;
    }

    /** sign.N = room x y w h rot style [b=brightness] [faces=id,id] | line | line ... */
    private Sign parseSign(String key, String spec) {
        String[] parts = spec.split("\\|");
        String[] head = parts[0].trim().split("\\s+");
        if (head.length < 7) {
            warnings.add(key + ": needs 'room x y w h rot style | text'");
            return null;
        }
        Sign sg = new Sign();
        sg.room = head[0];
        sg.r = new Rectangle(Integer.parseInt(head[1]), Integer.parseInt(head[2]), Integer.parseInt(head[3]), Integer.parseInt(head[4]));
        sg.rot = Double.parseDouble(head[5]);
        sg.style = head[6];
        for (int i = 7; i < head.length; i++) {
            if (head[i].startsWith("b=")) sg.bright = Double.parseDouble(head[i].substring(2));
            else if (head[i].startsWith("faces=")) sg.faces = head[i].substring(6).split(",");
            else warnings.add(key + ": unknown option '" + head[i] + "'");
        }
        sg.lines = new String[parts.length - 1];
        for (int i = 1; i < parts.length; i++) sg.lines[i - 1] = parts[i].trim();
        return sg;
    }

    private static Rectangle parseRect(String s) {
        double[] n = parseNums(s.replace(',', ' '));
        return n.length == 4 ? new Rectangle((int) n[0], (int) n[1], (int) n[2], (int) n[3]) : null;
    }

    private static double[][] parsePoints(String s) {
        String[] pts = s.trim().isEmpty() ? new String[0] : s.trim().split("\\s+");
        double[][] out = new double[pts.length][];
        for (int i = 0; i < pts.length; i++) {
            String[] xy = pts[i].split(",");
            out[i] = new double[] {Double.parseDouble(xy[0]), Double.parseDouble(xy[1])};
        }
        return out;
    }

    private static double[] parseNums(String s) {
        String[] p = s.trim().isEmpty() ? new String[0] : s.trim().split("\\s+");
        double[] out = new double[p.length];
        for (int i = 0; i < p.length; i++) out[i] = Double.parseDouble(p[i]);
        return out;
    }

    private static double sq(double v) { return v * v; }

    static int clamp(int v) { return v < 0 ? 0 : v > 255 ? 255 : v; }
}
