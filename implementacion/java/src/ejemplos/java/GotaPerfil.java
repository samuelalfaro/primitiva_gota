import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import javax.imageio.ImageIO;

/** Galería del perfil de radio: rm, tm y suavizado. */
public class GotaPerfil {
    public static void main(String[] a) throws Exception {
        BufferedImage img = new BufferedImage(900, 600, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((java.awt.image.DataBufferInt) img.getRaster().getDataBuffer()).getData(), 0xFFFFFFFF);
        // r1 = 50, r2 = 20: recta = 35. Fila 1: tm = 1/2; fila 2: tm = 0.3
        double[][] cfg = {
            { 35, .5, 1 }, { 60, .5, 1 }, { 60, .5, 0 },
            { 12, .5, 1 }, { 60, .3, 0.3 }, { 6, .7, 0 },
        };
        for (int i = 0; i < cfg.length; i++) {
            double ox = (i % 3) * 300, oy = (i / 3) * 300;
            Gota g = Gota.lineal(new Point2D.Double(ox + 60, oy + 150), 50, new Point2D.Double(ox + 240, oy + 150), 20,
                                 Gota.Perfil.radioMedio(cfg[i][0], cfg[i][1], cfg[i][2]));
            g.render(img, Rellenos.cadena(0xFF1E1B4B, 0xFFFFE27A, 0xFF2563EB, 0xFF7C2D12));
            // marca de tm sobre el eje
            Point2D c = g.centro(g.tm());
            img.setRGB((int) c.getX(), (int) c.getY(), 0xFFFF0000);
            img.setRGB((int) c.getX() + 1, (int) c.getY(), 0xFFFF0000);
        }
        ImageIO.write(img, "png", new File("gota_perfil.png"));
    }
}
