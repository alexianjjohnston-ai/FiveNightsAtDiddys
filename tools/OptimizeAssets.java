import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * One-time asset shrink for the web build: opaque room and static images become JPEGs, and every JPEG
 * is re-saved as plain sRGB without an embedded colour profile (the browser Java runtime can't decode
 * profiled JPEGs). Run: java tools/OptimizeAssets.java assets   (then point the manifest at the .jpg files)
 */
public class OptimizeAssets {
    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "assets");
        for (File f : new File(dir, "rooms").listFiles((d, n) -> n.endsWith(".png"))) toJpeg(f, false, 0.9f, true);
        for (File f : new File(dir, "ui").listFiles((d, n) -> n.startsWith("static_") && n.endsWith(".png"))) toJpeg(f, true, 0.8f, true);
        for (File f : new File(dir, "characters").listFiles((d, n) -> n.endsWith(".jpg"))) toJpeg(f, false, 0.92f, false);
    }

    static void toJpeg(File src, boolean gray, float quality, boolean deleteSource) throws Exception {
        BufferedImage in = ImageIO.read(src);
        BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(), gray ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(in, 0, 0, null);
        g.dispose();
        File dst = new File(src.getParentFile(), src.getName().replaceAll("\\.png$", ".jpg"));
        File tmp = new File(dst.getPath() + ".tmp");
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(quality);
        try (ImageOutputStream os = ImageIO.createImageOutputStream(tmp)) {
            w.setOutput(os);
            w.write(null, new IIOImage(out, null, null), p);
        }
        w.dispose();
        if (deleteSource && !src.equals(dst)) src.delete();
        if (!tmp.renameTo(dst)) throw new IllegalStateException("could not write " + dst);
        System.out.printf("%-28s -> %-28s %6d KB%n", src.getName(), dst.getName(), dst.length() / 1024);
    }
}
