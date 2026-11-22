import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Dibujo libre con las funciones de color de Composicion: un pez luchador (betta). El cuerpo lleva una paleta a lo
 * largo de la espina (Nodo.radio y Arista.espina) con el contorno iridiscente; las aletas son gotas de base ancha y
 * punta fina que se desvanecen hacia el borde (alfa en la función del radio). Escribe betta.png.
 */
public class Betta {
    static final int W = 820, H = 520;
    static final Mezcla RGB = Mezcla.RGB;

    // ---------- color ----------

    static int rgba(int a, int r, int g, int b) { return a << 24 | r << 16 | g << 8 | b; }
    static int conAlfa(int c, double k) { return ((int) Math.round((c >>> 24) * k)) << 24 | (c & 0xFFFFFF); }
    static int mezcla(int a, int b, double t) { return RGB.mezclar(a, b, Math.max(0, Math.min(1, t))); }

    /** Cuerpo: de la cola (magenta) a la cabeza (azul), con una pizca de verde azulado de iridiscencia. */
    static int cuerpo(double t) {
        int magenta = 0xFFC2185B, violeta = 0xFF6A1B9A, azul = 0xFF1A3FA8;
        return t < 0.5 ? mezcla(magenta, violeta, t * 2) : mezcla(violeta, azul, (t - 0.5) * 2);
    }
    static final int IRIS = 0xFF21C7B8;

    /** Función de radio del cuerpo: el centro es el color de la espina; hacia el contorno se oscurece y se tiñe de iris. */
    static Degradado radioCuerpo(int c) {
        return x -> {
            double t = x * x;
            int v = mezcla(c, IRIS, 0.55 * Math.pow(x, 3));
            return mezcla(v, 0xFF0B1030, 0.45 * t);
        };
    }

    /** Aleta: el color base se desvanece (alfa) hacia el contorno. */
    static Degradado radioAleta(int c, double hasta) {
        return x -> mezcla(c, conAlfa(c, 0.0), Math.pow(x, 1.6) * hasta);
    }

    // ---------- piezas ----------

    static Grafo.Nodo nodo(Grafo c, double x, double y, double r, int centro, Degradado f) {
        return c.nodo(x, y, r).centro(centro).radio(f);
    }

    static void fondo(BufferedImage im) {
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFF07142B);
        Point2D.Double a = new Point2D.Double(W * 0.55, 150), b = new Point2D.Double(W * 0.55 + 0.5, 150);
        Plotter.raster(im).plot(Gota.lineal(a, 900, b, 900, Perfil.recto()),
            Colores.cuatroColores(0xFF1F4E79, 0xFF1F4E79, 0xFF07142B, 0xFF07142B, Mezcla.RGB));
    }

    /** Cuerpo: de la cola a la cabeza, con la paleta corriendo por la espina. */
    static Grafo cuerpo() {
        Grafo c = new Grafo().mezcla(Mezcla.RGB);
        double[][] p = { {235, 272, 7}, {300, 266, 26}, {385, 254, 41}, {475, 248, 43}, {552, 254, 31}, {612, 264, 15} };
        Grafo.Nodo prev = null;
        for (int i = 0; i < p.length; i++) {
            double t = (double) i / (p.length - 1);
            int col = cuerpo(t);
            var nd = nodo(c, p[i][0], p[i][1], p[i][2], col, radioCuerpo(col));
            if (prev != null) {
                double t0 = (double) (i - 1) / (p.length - 1);
                c.arista(prev, nd).espina(x -> cuerpo(t0 + (t - t0) * x));
            }
            prev = nd;
        }
        return c;
    }

    /** Aleta de base ancha y punta fina que se desvanece hacia el borde. */
    static Grafo aleta(double x0, double y0, double r0, double x1, double y1, double r1,
                             double vx, double vy, int base, int punta, double desvanece) {
        Grafo c = new Grafo().mezcla(Mezcla.RGB);
        var a = nodo(c, x0, y0, r0, base, radioAleta(base, desvanece));
        var b = nodo(c, x1, y1, r1, punta, radioAleta(punta, desvanece));
        c.arista(a, b).tiradores(new Gota.Vec(vx, vy), null);
        return c;
    }

    static void ojo(BufferedImage im, double x, double y) {
        disco(im, x, y, 13, 0xFFF3E9C8, 0xFFB08A2E);
        disco(im, x + 1, y, 8, 0xFF0A0A14, 0xFF0A0A14);
        disco(im, x - 3, y - 4, 2.5, 0xFFFFFFFF, 0xFFFFFFFF);
    }
    static void disco(BufferedImage im, double x, double y, double r, int centro, int borde) {
        Point2D.Double a = new Point2D.Double(x, y), b = new Point2D.Double(x + 0.5, y);
        Plotter.raster(im).plot(Gota.lineal(a, r, b, r, Perfil.recto()), Colores.cuatroColores(centro, centro, borde, borde, Mezcla.RGB));
    }

    static BufferedImage dibuja() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        fondo(im);
        int rosa = 0xFFE0408A, vio = 0xFF7B2FBE, azul = 0xFF2E6FE0;
        // cola: abanico de rayos desde el pedúnculo (el abanico se abre y se desvanece)
        int n = 11;
        for (int i = 0; i < n; i++) {
            double k = (double) i / (n - 1), fi = Math.toRadians(-66 + 132 * k);
            double d = 175 + 30 * Math.cos(fi * 1.3);
            aleta(238, 272, 7, 238 - d * Math.cos(fi), 272 + d * Math.sin(fi), 40, -50, 120 * (k - 0.5) * 0.3,
                  mezcla(rosa, vio, k), mezcla(vio, azul, k), 0.9).render(im);
        }
        // dorsal, anal y ventrales: base ancha, punta fina
        aleta(420, 212, 26, 292, 96, 3, -20, -60, vio, rosa, 0.8).render(im);
        aleta(445, 296, 26, 330, 420, 3, -10, 60, vio, rosa, 0.8).render(im);
        aleta(505, 300, 10, 470, 460, 2, -6, 80, azul, rosa, 0.7).render(im);
        aleta(545, 296, 8, 540, 450, 2, 10, 90, azul, vio, 0.7).render(im);
        // pectoral
        aleta(520, 270, 14, 480, 330, 2, -30, 20, azul, 0xFF9FD0FF, 0.9).render(im);
        cuerpo().render(im);
        ojo(im, 580, 246);
        return im;
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(dibuja(), "png", new File("betta.png"));
        System.out.println("betta.png");
    }
}
