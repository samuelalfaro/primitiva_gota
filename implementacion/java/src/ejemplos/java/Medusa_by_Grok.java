import composicion.Grafo;
import pintado.Plotter;
import coordenadas.Solape;
import color.Mezcla;
import color.Degradado;
import color.Colores;
import coordenadas.Opciones;
import forma.Perfil;
import forma.Gota;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Medusa (Grok): campana con anillos bioluminiscentes por la función del radio y tentáculos que se desvanecen
 * hacia la punta (alfa en el radio) con el color corriendo por la espina. Escribe medusa_by_grok.png.
 */
public class Medusa_by_Grok {
    static final int W = 640, H = 820;
    static final Mezcla RGB = Mezcla.RGB;

    static int mezcla(int a, int b, double t) { return RGB.mezclar(a, b, Math.max(0, Math.min(1, t))); }
    static int conAlfa(int c, double k) {
        return ((int) Math.round((c >>> 24) * Math.max(0, Math.min(1, k)))) << 24 | (c & 0xFFFFFF);
    }

    // ---------- fondo ----------

    static void fondo(BufferedImage im) {
        int[] pix = ((DataBufferInt) im.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < H; y++) {
            double t = y / (double) (H - 1);
            int c = mezcla(0xFF071830, 0xFF020A14, t);
            for (int x = 0; x < W; x++) pix[y * W + x] = c;
        }
        // halo bajo la campana
        Point2D.Double a = new Point2D.Double(W * 0.5, 230), b = new Point2D.Double(W * 0.5 + 0.4, 230);
        Plotter.raster(im).plot(Gota.lineal(a, 260, b, 260, Perfil.recto()),
            Colores.cuatroColores(0xFF0C2E52, 0xFF0C2E52, 0x00071830, 0x00071830, Mezcla.RGB));
    }

    // ---------- campana: anillos por función de radio ----------

    static final double[] PARADAS = { 0.0, 0.10, 0.22, 0.36, 0.50, 0.64, 0.78, 0.90, 1.0 };
    static final int[] ANILLOS = {
        0xFFF2FCFF, // núcleo
        0xFF9BE6FF,
        0xFF3AB8F0,
        0xFF1A7BC8,
        0xFF4FE0C8, // banda verde-agua
        0xFF1A8FA0,
        0xFF0E5A7A,
        0xFF0A3858,
        0xFF062030  // borde
    };

    static int anillo(double x) {
        for (int i = 1; i < PARADAS.length; i++) {
            if (x <= PARADAS[i]) {
                double s = (x - PARADAS[i - 1]) / (PARADAS[i] - PARADAS[i - 1]);
                s = s * s * (3 - 2 * s);
                return mezcla(ANILLOS[i - 1], ANILLOS[i], s);
            }
        }
        return ANILLOS[ANILLOS.length - 1];
    }

    static void campana(BufferedImage im) {
        double cx = W * 0.5, cy = 200;
        // más ancha abajo → silueta de campana
        Gota g = Gota.lineal(
            new Point2D.Double(cx, cy - 36), 68,
            new Point2D.Double(cx, cy + 48), 108,
            Perfil.recto());
        Degradado f = Medusa_by_Grok::anillo;
        Degradado centro = Degradado.constante(ANILLOS[0]);
        Plotter.raster(im).plot(g, Colores.cadenaLateral(
            new Colores.Funciones(f, f, centro, f, f), RGB));

        // brillo especular arriba
        Point2D.Double a = new Point2D.Double(cx - 18, cy - 42), b = new Point2D.Double(cx - 17.5, cy - 42);
        Plotter.raster(im).plot(Gota.lineal(a, 28, b, 20, Perfil.recto()),
            Colores.cuatroColores(0xA0FFFFFF, 0xA0FFFFFF, 0x00FFFFFF, 0x00FFFFFF, Mezcla.RGB));
    }

    // ---------- tentáculos ----------

    static int tonoTentaculo(int k, int n) {
        double t = (double) k / Math.max(1, n - 1);
        int a = 0xFF4AD4F0, b = 0xFF7B6BFF, c = 0xFF3AE0B8;
        return t < 0.5 ? mezcla(a, b, t * 2) : mezcla(b, c, (t - 0.5) * 2);
    }

    static Degradado radioTentaculo(int c, double hasta) {
        return x -> {
            double fade = Math.pow(x, 1.25) * hasta;
            int v = mezcla(c, conAlfa(c, 0.0), fade);
            return mezcla(v, conAlfa(0xFF0A2840, (v >>> 24) / 255.0), 0.22 * x * x);
        };
    }

    static Grafo tentaculo(double x0, double y0, double ang0, double largo, int k, int n) {
        Grafo c = new Grafo().mezcla(RGB);
        int col0 = tonoTentaculo(k, n);
        int m = 8;
        Grafo.Nodo prev = null;
        for (int i = 0; i < m; i++) {
            double t = (double) i / (m - 1);
            double ond = Math.sin(t * Math.PI * 2.4 + k * 0.85) * (14 + 36 * t);
            double x = x0 + Math.sin(ang0) * largo * t + ond * Math.cos(ang0);
            double y = y0 + Math.cos(ang0) * largo * t + ond * 0.12;
            double r = 7.2 * (1 - 0.78 * t) + 1.2;
            int col = mezcla(col0, 0xFF163850, t * 0.5);
            var nd = c.nodo(x, y, r).centro(col).radio(radioTentaculo(col, 0.90));
            if (prev != null) {
                final double t0 = (double) (i - 1) / (m - 1);
                final int ck = k, cn = n;
                c.arista(prev, nd).espina(xs -> {
                    double u = t0 + (t - t0) * xs;
                    return mezcla(tonoTentaculo(ck, cn), 0xFF163850, u * 0.5);
                });
            }
            prev = nd;
        }
        return c;
    }

    static Grafo brazoOral(double x0, double y0, double ang, double largo, int tono) {
        Grafo c = new Grafo().mezcla(RGB);
        int m = 5;
        Grafo.Nodo prev = null;
        for (int i = 0; i < m; i++) {
            double t = (double) i / (m - 1);
            double ond = Math.sin(t * Math.PI * 1.5 + ang) * 12 * t;
            double x = x0 + Math.sin(ang) * largo * t + ond;
            double y = y0 + Math.cos(ang) * largo * t;
            double r = 14 * (1 - 0.5 * t) + 2.5;
            int col = mezcla(tono, 0xFF0C3A50, t * 0.35);
            var nd = c.nodo(x, y, r).centro(col).radio(radioTentaculo(col, 0.72));
            if (prev != null) {
                final double t0 = (double) (i - 1) / (m - 1);
                c.arista(prev, nd).espina(xs -> mezcla(tono, 0xFF0C3A50, (t0 + (t - t0) * xs) * 0.35));
            }
            prev = nd;
        }
        return c;
    }

    static BufferedImage dibuja() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        fondo(im);

        // tentáculos largos (detrás)
        int n = 16;
        for (int k = 0; k < n; k++) {
            double frac = (k + 0.5) / n;
            double x0 = W * 0.5 + (frac - 0.5) * 130;
            double y0 = 268 + 6 * Math.sin(frac * Math.PI * 2);
            double ang = Math.toRadians(12 * (frac - 0.5));
            double largo = 400 + 70 * Math.sin(frac * Math.PI);
            tentaculo(x0, y0, ang, largo, k, n)
                .render(im, Opciones.POR_DEFECTO.conSolape(Solape.TRAZO));
        }

        // brazos orales
        int[] tonos = { 0xFF5AD8F0, 0xFF48B8D8, 0xFF6BE8C8, 0xFF3AA8C8, 0xFF7AD0E8 };
        for (int k = 0; k < 5; k++) {
            double ang = Math.toRadians(-28 + 14 * k);
            brazoOral(W * 0.5 + (k - 2) * 14, 258, ang, 125 + 18 * ((k + 1) % 2), tonos[k])
                .render(im, Opciones.POR_DEFECTO.conSolape(Solape.TRAZO));
        }

        campana(im);
        return im;
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(dibuja(), "png", new File("medusa_by_grok.png"));
        System.out.println("medusa_by_grok.png");
    }
}
