import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import javax.imageio.ImageIO;

/** Galería del perfil de radio: cúbica monótona definida por las tangentes (dr/dt) en los extremos. */
public class GotaPerfil {
    public static void main(String[] a) throws Exception {
        BufferedImage img = new BufferedImage(900, 600, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((java.awt.image.DataBufferInt) img.getRaster().getDataBuffer()).getData(), 0xFFFFFFFF);
        // r1 = 50, r2 = 20: la recta tiene pendiente -30 en los dos extremos; el límite monótono es 3·(r2 - r1) = -90
        double[][] cfg = {
            { -30, -30 }, { 0, 0 }, { -90, 0 },
            { 0, -90 }, { -90, -90 }, { -15, -45 },
        };
        for (int i = 0; i < cfg.length; i++) {
            double ox = (i % 3) * 300, oy = (i / 3) * 300;
            Gota g = Gota.lineal(new Point2D.Double(ox + 60, oy + 150), 50, new Point2D.Double(ox + 240, oy + 150), 20,
                                 Perfil.tangentes(cfg[i][0], cfg[i][1]));
            Plotter.raster(img).plot(g, Colores.cadena(0xFF1E1B4B, 0xFFFFE27A, 0xFF2563EB, 0xFF7C2D12));
            // marca de t = 1/2 sobre el eje
            Point2D c = g.centro(0.5);
            img.setRGB((int) c.getX(), (int) c.getY(), 0xFFFF0000);
            img.setRGB((int) c.getX() + 1, (int) c.getY(), 0xFFFF0000);
        }
        ImageIO.write(img, "png", new File("gota_perfil.png"));
    }
}
