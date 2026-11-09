import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/** Dibujo libre solo con la API de Composicion: una serpiente ondulando por un desierto (un camino sinuoso de nodos con el radio variable). */
public class Serpiente {
    static final int W = 820, H = 480;
    static final int LOMO = 0xFF6B8E23, LOMO2 = 0xFF8FB339, VIENTRE = 0xFFF1E3B5, OSC = 0xFF2F4A12;

    static double suave(double a, double b, double x) { double t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); }

    static double[] punto(double t) {
        double x = 50 + 700 * t, y = 290 - 95 * Math.sin(2 * Math.PI * 1.5 * t + 0.35) * (0.45 + 0.55 * suave(0, 0.25, t));
        return new double[] { x, y };
    }

    static double radio(double t) {
        double r = 3 + 26 * suave(0.0, 0.40, t);
        r -= 5 * suave(0.82, 0.97, t);
        return r;
    }

    static void disco(BufferedImage im, double x, double y, double r, int centro, int borde) {
        Point2D.Double a = new Point2D.Double(x, y), b = new Point2D.Double(x + 0.5, y);
        Plotter.raster(im).plot(Gota.lineal(a, r, b, r, Perfil.recto()), Colores.cuatroColores(centro, centro, borde, borde, Mezcla.RGB));
    }

    /** El desierto: una gota enorme de arena (centro claro, borde ocre) y otra más para la duna del fondo. */
    static void fondo(BufferedImage im) {
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFFFBE7B8);
        disco(im, 410, -700, 1300, 0xFFFFF1CC, 0xFF9CCFE0);        // el cielo: otra gota enorme, de borde azul
        disco(im, 300, 760, 520, 0xFFF3D08A, 0xFFC98F45);
        disco(im, 640, 620, 330, 0xFFEBC378, 0xFFB57F3A);
        disco(im, 690, 90, 42, 0xFFFFF6C9, 0xFFFFC857);
    }

    static Composicion cuerpo(int n) {
        Composicion c = new Composicion();
        Composicion.Nodo prev = null;
        for (int i = 0; i < n; i++) {
            double t = (double) i / (n - 1);
            double[] p = punto(t);
            var nd = c.nodo(p[0], p[1], radio(t) * (i == n - 1 ? 1.35 : 1)).centro(LOMO2);
            if (i == 0) nd.sectores(LOMO);
            else if (i == n - 1) { nd.sectores(LOMO); }
            else nd.lados(VIENTRE, LOMO).ajusteLateral(0.5);
            if (prev != null) c.arista(prev, nd);
            prev = nd;
        }
        return c;
    }

    /** Rombos oscuros del dibujo del lomo (u a lo ancho, v a lo largo de cada tramo), con alfa; solo en el lado del lomo. */
    static final Texturas.Textura ROMBOS = (u, v) -> {
        double du = Math.abs(u - 0.62), dv = Math.abs(((v * 1.0) % 1 + 1) % 1 - 0.5);
        double d = du * 1.9 + dv * 1.0;
        double a = d < 0.30 ? 1 : (d < 0.36 ? (0.36 - d) / 0.06 : 0);
        double borde = d > 0.24 && d < 0.30 ? 0.55 : 1;
        int alfa = (int) Math.round(170 * a * borde * (u > 0.2 ? 1 : 0));
        return alfa << 24 | 0x2F2A12;
    };

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        fondo(im);
        // piedras
        disco(im, 120, 420, 16, 0xFFCFC0A0, 0xFF8D7B5A);
        disco(im, 150, 432, 9, 0xFFCFC0A0, 0xFF8D7B5A);
        disco(im, 730, 400, 20, 0xFFCFC0A0, 0xFF8D7B5A);
        int n = 20;
        double[] h = punto(1), h0 = punto(1 - 0.02);
        double ang = Math.atan2(h[1] - h0[1], h[0] - h0[0]), ca = Math.cos(ang), sa = Math.sin(ang);
        // sombra bajo la serpiente: el mismo cuerpo, aplastado y oscuro, desplazado
        Composicion lengua = new Composicion();
        var l0 = lengua.nodo(h[0] + ca * 28, h[1] + sa * 28, 2.4).color(0xFFD32F2F);
        var l1 = lengua.nodo(h[0] + ca * 64, h[1] + sa * 64, 2).color(0xFFD32F2F);
        var l2 = lengua.nodo(h[0] + ca * 86 - sa * 14, h[1] + sa * 86 + ca * 14, 1.2).color(0xFFD32F2F);
        var l3 = lengua.nodo(h[0] + ca * 86 + sa * 14, h[1] + sa * 86 - ca * 14, 1.2).color(0xFFD32F2F);
        lengua.arista(l0, l1);
        lengua.arista(l1, l2);
        lengua.arista(l1, l3);
        lengua.render(im);
        Composicion cu = cuerpo(n);
        cu.render(im);
        cu.render(im, ROMBOS);
        // ojos
        for (int s = -1; s <= 1; s += 2) {
            double ex = h[0] + ca * 4 - sa * 11 * s, ey = h[1] + sa * 4 + ca * 11 * s;
            disco(im, ex, ey, 7, 0xFFFFE066, 0xFFE0A800);
            disco(im, ex + ca * 1.5, ey + sa * 1.5, 3, 0xFF111111, 0xFF000000);
        }
        ImageIO.write(im, "png", new File("serpiente.png"));
        System.out.printf("render: %.1f s%n", (System.nanoTime() - t0) / 1e9);
    }
}
