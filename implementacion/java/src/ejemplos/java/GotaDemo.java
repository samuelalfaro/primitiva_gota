import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.Arrays;
import javax.imageio.ImageIO;

/** Demo de la fase 1: seis casos a PNG y contraste con fuerza bruta (círculos pegados). */
public class GotaDemo {

    static Point2D.Double p(int col, int fila, double x, double y) {
        return new Point2D.Double(col * 300 + x, fila * 300 + y);
    }

    public static void main(String[] args) throws Exception {
        String[] nombres = { "lineal h=1", "hinchada h=1.8", "guitarra h=0.55",
                             "curva (arco)", "r2=0 (punta)", "circulo contenido" };
        Gota[] gotas = {
            Gota.lineal(p(0, 0, 60, 150), 50, p(0, 0, 240, 150), 20, Gota.Perfil.central(1.0)),
            Gota.lineal(p(1, 0, 60, 150), 50, p(1, 0, 240, 150), 20, Gota.Perfil.central(1.8)),
            Gota.lineal(p(2, 0, 60, 150), 50, p(2, 0, 240, 150), 20, Gota.Perfil.central(0.55)),
            Gota.curva(p(0, 1, 60, 240), 35, new Gota.Vec(0, -200),
                       p(0, 1, 240, 240), 15, new Gota.Vec(0, -200), Gota.Perfil.central(1.0)),
            Gota.lineal(p(1, 1, 90, 150), 60, p(1, 1, 250, 150), 0, Gota.Perfil.central(1.0)),
            Gota.lineal(p(2, 1, 120, 150), 70, p(2, 1, 160, 150), 15, Gota.Perfil.central(1.0)),
        };
        int[] colores = { 0xFF534AB7, 0xFF0F6E56, 0xFF993C1D, 0xFF185FA5, 0xFF993556, 0xFF854F0B };

        BufferedImage img = new BufferedImage(900, 600, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) img.getRaster().getDataBuffer()).getData(), 0xFFFFFFFF);

        boolean ok = true;
        for (int i = 0; i < gotas.length; i++) {
            long t0 = System.nanoTime();
            gotas[i].render(img, Gota.Relleno.solido(colores[i]));
            double ms = (System.nanoTime() - t0) / 1e6;
            int[] r = comparar(gotas[i]);
            System.out.printf("%-20s render %6.1f ms | dentro=%6d  difs vs fuerza bruta=%d  difs signo sdf=%d%n",
                    nombres[i], ms, r[0], r[1], r[2]);
            if (r[1] > 5 || r[2] > 0) ok = false;
        }
        ImageIO.write(img, "png", new File("gota_fase1.png"));
        System.out.println(ok ? "OK" : "HAY DISCREPANCIAS");
        if (!ok) System.exit(1);
    }

    /** {píxeles dentro, discrepancias contiene vs fuerza bruta, discrepancias de signo sdf vs contiene}. */
    static int[] comparar(Gota g) {
        Rectangle2D b = g.bounds();
        int x0 = (int) Math.floor(b.getMinX()), x1 = (int) Math.ceil(b.getMaxX());
        int y0 = (int) Math.floor(b.getMinY()), y1 = (int) Math.ceil(b.getMaxY());
        int n = 2000;
        double[] cx = new double[n + 1], cy = new double[n + 1], rr = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            Point2D c = g.centro(i / (double) n);
            cx[i] = c.getX(); cy[i] = c.getY();
            double r = g.radio(i / (double) n);
            rr[i] = r * r;
        }
        int dentro = 0, disc = 0, discSdf = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                double px = x + 0.5, py = y + 0.5;
                boolean a = g.contiene(px, py), bf = false;
                for (int k = 0; k <= n && !bf; k++) {
                    double dx = px - cx[k], dy = py - cy[k];
                    bf = dx * dx + dy * dy <= rr[k];
                }
                if (a) dentro++;
                if (a != bf) disc++;
                double s = g.sdf(px, py);
                if ((s < 0) != a && Math.abs(s) > 1e-3) discSdf++;
            }
        }
        return new int[] { dentro, disc, discSdf };
    }
}
