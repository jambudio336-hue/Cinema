import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class MakeIcons {
    private static BufferedImage source;

    private static BufferedImage square(int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double scale = Math.max((double) size / source.getWidth(), (double) size / source.getHeight());
        int w = (int) Math.ceil(source.getWidth() * scale);
        int h = (int) Math.ceil(source.getHeight() * scale);
        g.drawImage(source, (size - w) / 2, (size - h) / 2, w, h, null);
        g.dispose();
        return out;
    }

    private static void write(BufferedImage image, String path) throws Exception {
        File file = new File(path);
        file.getParentFile().mkdirs();
        ImageIO.write(image, "png", file);
    }

    private static BufferedImage circular(BufferedImage input) {
        int size = input.getWidth();
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setClip(new Ellipse2D.Double(0, 0, size, size));
        g.drawImage(input, 0, 0, null);
        g.dispose();
        return out;
    }

    private static void density(String name, int px) throws Exception {
        String dir = "app/src/main/res/" + name;
        BufferedImage icon = square(px);
        write(icon, dir + "/ic_launcher.png");
        write(circular(icon), dir + "/ic_launcher_round.png");
    }

    private static void adaptive(String name, int canvas, int art) throws Exception {
        String dir = "app/src/main/res/" + name;
        BufferedImage bg = square(canvas);
        Graphics2D bgG = bg.createGraphics();
        bgG.setColor(new Color(8, 7, 13, 95));
        bgG.fillRect(0, 0, canvas, canvas);
        bgG.dispose();
        write(bg, dir + "/ic_launcher_background.png");

        BufferedImage fg = new BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB);
        Graphics2D fgG = fg.createGraphics();
        fgG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        fgG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double scale = Math.min((double) art / source.getWidth(), (double) art / source.getHeight());
        int w = (int) Math.round(source.getWidth() * scale);
        int h = (int) Math.round(source.getHeight() * scale);
        fgG.drawImage(source, (canvas - w) / 2, (canvas - h) / 2, w, h, null);
        fgG.dispose();
        write(fg, dir + "/ic_launcher_foreground.png");
    }

    public static void main(String[] args) throws Exception {
        File input = new File(args.length == 0 ? "kiplay.png" : args[0]);
        source = ImageIO.read(input);
        if (source == null) throw new IllegalArgumentException("File sumber bukan PNG/JPEG yang didukung: " + input);
        density("mipmap-mdpi", 48);
        density("mipmap-hdpi", 72);
        density("mipmap-xhdpi", 96);
        density("mipmap-xxhdpi", 144);
        density("mipmap-xxxhdpi", 192);
        adaptive("mipmap-mdpi", 108, 67);
        adaptive("mipmap-hdpi", 162, 100);
        adaptive("mipmap-xhdpi", 216, 134);
        adaptive("mipmap-xxhdpi", 324, 201);
        adaptive("mipmap-xxxhdpi", 432, 268);
        new File("app/src/main/res/mipmap-anydpi").mkdirs();
        new File("app/src/main/res/mipmap-anydpi/ic_launcher.xml").delete();
        System.out.println("OK: launcher icon PNG generated from " + input);
    }
}
