package diddys;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Entry point. One Swing timer drives everything on the event thread: it measures real elapsed time
 * with System.nanoTime() and runs as many fixed 10 ms simulation ticks as have elapsed, so game speed
 * never depends on frame rate. Elapsed time is capped per frame so a minimised or stalled window
 * doesn't fast-forward the night.
 */
public final class Main {
    private static final long TICK_NS = Config.TICK_MS * 1_000_000L;
    private static final long MAX_CATCHUP_NS = 250_000_000L;

    public static void main(String[] args) {
        Assets assets;
        try {
            assets = Assets.load();
        } catch (Assets.MissingAssets e) {
            fail(e.getMessage());
            return;
        }
        for (String w : assets.warnings) System.err.println("warning: " + w);
        if (args.length >= 2 && args[0].equals("--shots")) {
            Shots.run(assets, Paths.get(args[1]));
            return;
        }
        boolean dev = Boolean.getBoolean("diddys.dev");
        SwingUtilities.invokeLater(() -> start(assets, dev));
    }

    private static void fail(String msg) {
        System.err.println(msg);
        if (!GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(null, msg, "Five Nights at Diddy's - missing files", JOptionPane.ERROR_MESSAGE);
        }
        System.exit(1);
    }

    private static void start(Assets assets, boolean dev) {
        List<String> warnings = new ArrayList<>();
        Audio audio = new Audio(assets.sounds(), warnings);
        for (String w : warnings) System.err.println("warning: " + w);
        String smoke = dev ? System.getProperty("diddys.smoke") : null;
        Save save = Save.load(smoke != null ? Paths.get(smoke, "save.properties") : Save.defaultPath());
        Game game = new Game(assets, audio, save, dev, new Random());
        Renderer renderer = new Renderer(assets);

        JFrame frame = new JFrame("Five Nights at Diddy's");
        View view = new View(game, renderer);
        frame.setContentPane(view);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        double fit = Math.min(1, Math.min(screen.width * 0.92 / Config.WIDTH, screen.height * 0.88 / Config.HEIGHT));
        view.setPreferredSize(new Dimension((int) (Config.WIDTH * fit), (int) (Config.HEIGHT * fit)));
        frame.pack();
        frame.setMinimumSize(new Dimension(640, 400));
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        Runnable quit = () -> {
            save.write();
            audio.stopAll();
            audio.close();
            frame.dispose();
            System.exit(0);
        };
        game.onQuit = quit;
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { quit.run(); }
            @Override public void windowDeactivated(WindowEvent e) { game.focusLost(); }
            @Override public void windowIconified(WindowEvent e) { game.focusLost(); }
        });
        frame.setVisible(true);
        view.requestFocusInWindow();

        long[] last = {System.nanoTime()};
        long[] acc = {0};
        long[] lastPaint = {0};
        Timer timer = new Timer(4, e -> {
            long now = System.nanoTime();
            acc[0] += Math.min(now - last[0], MAX_CATCHUP_NS);
            last[0] = now;
            while (acc[0] >= TICK_NS) {
                game.tick();
                acc[0] -= TICK_NS;
            }
            if (now - lastPaint[0] >= 15_000_000L) {
                lastPaint[0] = now;
                view.repaint();
            }
        });
        timer.setCoalesce(true);
        timer.start();

        if (smoke != null) Smoke.start(frame, view, game, Paths.get(smoke));
    }

    /** Letterboxed view: the 1280x720 frame is scaled to fit, bars fill the rest, input is mapped back. */
    static final class View extends JPanel {
        private final Game game;
        private final Renderer renderer;
        private final BufferedImage frame = new BufferedImage(Config.WIDTH, Config.HEIGHT, BufferedImage.TYPE_INT_RGB);
        private double scale = 1, ox, oy;

        View(Game game, Renderer renderer) {
            this.game = game;
            this.renderer = renderer;
            setBackground(Color.BLACK);
            setFocusable(true);
            setFocusTraversalKeysEnabled(false);
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) { game.mouseMoved(vx(e), vy(e)); }
                @Override public void mouseDragged(MouseEvent e) { game.mouseMoved(vx(e), vy(e)); }
                @Override public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    if (e.getButton() == MouseEvent.BUTTON1) game.click(vx(e), vy(e));
                }
                @Override public void mouseExited(MouseEvent e) { game.mouseExited(); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    if (!e.isMetaDown() && !e.isControlDown()) game.key(e.getKeyCode());
                }
            });
        }

        /** Virtual 1280x720 point -> this component's pixel coordinates (used by the smoke test). */
        java.awt.Point toComponent(int x, int y) {
            return new java.awt.Point((int) Math.round(ox + (x + 0.5) * scale), (int) Math.round(oy + (y + 0.5) * scale));
        }

        private int vx(MouseEvent e) { return (int) Math.floor((e.getX() - ox) / scale); }
        private int vy(MouseEvent e) { return (int) Math.floor((e.getY() - oy) / scale); }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D fg = frame.createGraphics();
            renderer.render(fg, game);
            fg.dispose();
            scale = Math.min(getWidth() / (double) Config.WIDTH, getHeight() / (double) Config.HEIGHT);
            int w = (int) Math.round(Config.WIDTH * scale), h = (int) Math.round(Config.HEIGHT * scale);
            ox = (getWidth() - w) / 2.0;
            oy = (getHeight() - h) / 2.0;
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(frame, (int) ox, (int) oy, w, h, null);
        }
    }

    /** Audio stand-in for tools and tests: loads nothing, plays nothing. */
    static Audio silentAudio() {
        return new Audio(new HashMap<>(), new ArrayList<>());
    }
}
