import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * Casos adversos de geometría. Cada caso vive en una celda de 300x300 de una rejilla 4x2 y se
 * contrasta contra fuerza bruta (círculos pegados) dentro de su celda.
 */
public class GotaAdversos {

    static Point2D.Double p(int i, double x, double y) {
        return new Point2D.Double((i % 4) * 300 + x, (i / 4) * 300 + y);
    }

    public static void main(String[] args) throws Exception {
        String[] nombres = {
            "h=0.5 (minimo)", "h=6 (enorme)", "lazo fino", "lazo grueso",
            "concentricos", "radio cero", "tiradores 900", "punta->grueso (arco)"
        };
        Gota[] g = new Gota[8];
        g[0] = Gota.lineal(p(0, 60, 150), 50, p(0, 240, 150), 30, Gota.Perfil.central(0.5));
        g[1] = Gota.lineal(p(1, 60, 150), 30, p(1, 240, 150), 15, Gota.Perfil.central(6.0));
        g[2] = Gota.curva(p(2, 80, 230), 14, new Gota.Vec(180, -230),
                          p(2, 220, 230), 10, new Gota.Vec(-180, -230), Gota.Perfil.central(1.0));
        g[3] = Gota.curva(p(3, 80, 230), 40, new Gota.Vec(180, -230),
                          p(3, 220, 230), 30, new Gota.Vec(-180, -230), Gota.Perfil.central(1.0));
        g[4] = Gota.lineal(p(4, 150, 150), 30, p(4, 150, 150), 60, Gota.Perfil.central(1.0));
        g[5] = Gota.lineal(p(5, 60, 150), 0, p(5, 240, 150), 0, Gota.Perfil.central(1.0));
        g[6] = Gota.curva(p(6, 60, 60), 10, new Gota.Vec(900, 0),
                          p(6, 240, 240), 6, new Gota.Vec(0, -900), Gota.Perfil.central(1.0));
        g[7] = Gota.curva(p(7, 60, 240), 0, new Gota.Vec(0, -200),
                          p(7, 240, 240), 40, new Gota.Vec(0, -200), Gota.Perfil.central(1.0));

        Gota.Relleno rel = Rellenos.cuatroColores(0xFFFFE27A, 0xFFFF8A5B, 0xFFC2410C, 0xFF7C1D6F);
        BufferedImage img = new BufferedImage(1200, 600, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) img.getRaster().getDataBuffer()).getData(), 0xFFFFFFFF);

        boolean ok = true;
        for (int i = 0; i < g.length; i++) {
            long t0 = System.nanoTime();
            g[i].render(img, rel);
            double ms = (System.nanoTime() - t0) / 1e6;
            int x0 = (i % 4) * 300, y0 = (i / 4) * 300;
            int[] r = comparar(g[i], x0, y0, x0 + 300, y0 + 300);
            int tol = Math.max(5, (int) (r[0] * 0.0005));
            boolean bien = r[1] <= tol && r[2] <= tol;
            System.out.printf("%-22s render %6.1f ms | dentro=%6d  difs fuerza bruta=%d  difs signo sdf=%d  %s%n",
                    nombres[i], ms, r[0], r[1], r[2], bien ? "ok" : "REVISAR");
            ok &= bien;
        }
        ImageIO.write(img, "png", new File("gota_adversos.png"));
        System.out.println(ok ? "OK" : "HAY DISCREPANCIAS");
    }

    /** {píxeles dentro, discrepancias contiene vs fuerza bruta, discrepancias de signo sdf vs contiene}. */
    static int[] comparar(Gota g, int x0, int y0, int x1, int y1) {
        // longitud aproximada del camino, para elegir el paso de la fuerza bruta (~0.1 px)
        double largo = 0;
        Point2D prev = g.centro(0);
        for (int i = 1; i <= 400; i++) {
            Point2D c = g.centro(i / 400.0);
            largo += prev.distance(c);
            prev = c;
        }
        int n = (int) Math.max(2000, Math.min(40000, largo / 0.1));
        // solo se conservan los círculos que tocan la celda
        double[] cx = new double[n + 1], cy = new double[n + 1], rr = new double[n + 1];
        int m = 0;
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            Point2D c = g.centro(t);
            double r = g.radio(t);
            if (c.getX() + r < x0 || c.getX() - r > x1 || c.getY() + r < y0 || c.getY() - r > y1) continue;
            cx[m] = c.getX(); cy[m] = c.getY(); rr[m] = r * r; m++;
        }
        int dentro = 0, disc = 0, discSdf = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                double px = x + 0.5, py = y + 0.5;
                boolean a = g.contiene(px, py), bf = false;
                for (int k = 0; k < m && !bf; k++) {
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
