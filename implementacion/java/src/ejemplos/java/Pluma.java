import composicion.Grafo;
import pintado.Plotter;
import color.Mezcla;
import color.Degradado;
import color.Colores;
import forma.Perfil;
import forma.Gota;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Dibujo libre con las funciones de color: una pluma de pavo real. El ojo es una sola gota alargada cuyos radios
 * (los cuatro, la misma función) dibujan los anillos concéntricos; las barbas son gotas finas con base ancha, punta
 * fina y alfa hacia el borde, con el color corriendo por la espina. Escribe pluma.png.
 */
public class Pluma {
    static final int W = 700, H = 720;
    static final Mezcla RGB = Mezcla.RGB;

    static int mezcla(int a, int b, double t) { return RGB.mezclar(a, b, Math.max(0, Math.min(1, t))); }
    static int conAlfa(int c, double k) { return ((int) Math.round((c >>> 24) * k)) << 24 | (c & 0xFFFFFF); }

    // ---------- camino de la pluma: Bézier cuadrática ----------
    static final double[] P0 = {330, 690}, P1 = {300, 440}, P2 = {365, 255};
    static double[] punto(double t) {
        double u = 1 - t;
        return new double[] { u * u * P0[0] + 2 * u * t * P1[0] + t * t * P2[0], u * u * P0[1] + 2 * u * t * P1[1] + t * t * P2[1] };
    }
    static double[] tangente(double t) {
        double u = 1 - t, x = 2 * u * (P1[0] - P0[0]) + 2 * t * (P2[0] - P1[0]), y = 2 * u * (P1[1] - P0[1]) + 2 * t * (P2[1] - P1[1]);
        double n = Math.hypot(x, y);
        return new double[] { x / n, y / n };
    }

    // ---------- barbas ----------

    static Degradado fade(int c, double hasta) { return x -> mezcla(c, conAlfa(c, 0.0), Math.pow(x, 1.5) * hasta); }

    static void barba(BufferedImage im, double t, int lado) {
        double[] p = punto(t), T = tangente(t);
        double[] N = { -T[1] * lado, T[0] * lado };
        double L = 45 + 185 * Math.sin(Math.PI * Math.pow(t, 0.8)) * (1 - 0.3 * t);
        double ang = Math.toRadians(58 - 30 * t * t);
        double qx = p[0] + N[0] * L * Math.cos(ang) + T[0] * L * Math.sin(ang);
        double qy = p[1] + N[1] * L * Math.cos(ang) + T[1] * L * Math.sin(ang);
        int base = mezcla(0xFF1F6B3A, 0xFF0E7C7B, t);
        int punta = mezcla(0xFFE0B63A, 0xFF59C2A5, t * 0.8);
        Grafo c = new Grafo().mezcla(RGB);
        var a = c.nodo(p[0], p[1], 3.6).centro(base).radio(fade(base, 0.9));
        var b = c.nodo(qx, qy, 1.5).centro(punta).radio(fade(punta, 0.9));
        c.arista(a, b).tiradores(new Gota.Vec(N[0] * L * 0.45 + T[0] * L * 0.2, N[1] * L * 0.45 + T[1] * L * 0.2), null);
        c.render(im);
    }

    // ---------- el ojo: anillos por la función del radio ----------

    static final double[] PARADAS = { 0.0, 0.16, 0.32, 0.46, 0.58, 0.72, 0.86, 1.0 };
    static final int[] COLORES = { 0xFF140A55, 0xFF1D4FC4, 0xFF13A3C9, 0xFF0F8F6B, 0xFFD9B23A, 0xFF7A3F16, 0xFF2E7D32, 0xFF1B5E20 };

    static int anillos(double x) {
        for (int i = 1; i < PARADAS.length; i++)
            if (x <= PARADAS[i]) {
                double s = (x - PARADAS[i - 1]) / (PARADAS[i] - PARADAS[i - 1]);
                s = s * s * (3 - 2 * s);
                return mezcla(COLORES[i - 1], COLORES[i], s);
            }
        return COLORES[COLORES.length - 1];
    }

    static void ojo(BufferedImage im, double x, double y) {
        Gota g = Gota.lineal(new Point2D.Double(x, y - 16), 62, new Point2D.Double(x, y + 22), 54, Perfil.recto());
        Degradado f = Pluma::anillos;
        // un mismo perfil en los cuatro radios; la espina es el color del centro (constante)
        Degradado centro = Degradado.constante(COLORES[0]);
        Plotter.raster(im).plot(g, Colores.cadenaLateral(new Colores.Funciones(f, f, centro, f, f), RGB));
    }

    static BufferedImage dibuja() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFF0E1A17);
        int n = 56;
        for (int i = 0; i < n; i++) {
            double t = 0.04 + 0.95 * i / (n - 1);
            barba(im, t, -1);
            barba(im, t, +1);
        }
        ojo(im, 366, 238);
        // raquis: una gota fina clara por encima de las barbas
        Grafo c = new Grafo().mezcla(RGB);
        Grafo.Nodo prev = null;
        int m = 9;
        for (int i = 0; i < m; i++) {
            double t = 0.0 + 0.93 * i / (m - 1);
            double[] p = punto(t);
            int col = mezcla(0xFFB89B4A, 0xFFE9D38A, t);
            var nd = c.nodo(p[0], p[1], 4.2 - 2.4 * t).centro(col).radio(x -> mezcla(col, 0xFF6F5A22, 0.5 * x));
            if (prev != null) c.arista(prev, nd);
            prev = nd;
        }
        c.render(im);
        return im;
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(dibuja(), "png", new File("pluma.png"));
        System.out.println("pluma.png");
    }
}
