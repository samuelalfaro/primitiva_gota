import geometria.composicion.Grafo;
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
 * Lámina libre de Claude (tema elegido por Claude; la regla del repo es que cada autor firma su clase con
 * _by_Autor): un caballito de mar. Todo son gotas de una Composicion.
 * <ul>
 * <li>El cuerpo es una sola cadena de nodos de la cabeza a la punta de la cola, que se enrosca en espiral:
 *     es el test de estrés de TRAZO (vueltas sucesivas que se solapan consigo mismas).</li>
 * <li>Los anillos del cuerpo van en la función de la espina (bandas oscuras periódicas en t) y el volumen en la
 *     función del radio (centro claro, contorno oscuro), sin textura.</li>
 * <li>La aleta dorsal es un abanico de gotas con alfa en la función del radio, como las aletas del Betta.</li>
 * </ul>
 * Escribe caballito_by_claude.png.
 */
public class Caballito_by_Claude {
    static final int W = 640, H = 820;
    static final Mezcla RGB = Mezcla.RGB;

    static int mezcla(int a, int b, double t) { return RGB.mezclar(a, b, Math.max(0, Math.min(1, t))); }
    static int conAlfa(int c, double k) { return ((int) Math.round((c >>> 24) * k)) << 24 | (c & 0xFFFFFF); }

    static final int AMBAR = 0xFFF2A83B, CORAL = 0xFFE2573A, GRANATE = 0xFF7E1F2B, CREMA = 0xFFF8E3B0;

    /** Color de la espina en t (0 = hocico, 1 = punta de la cola): ámbar en la cabeza, coral en el vientre, granate en la cola. */
    static int base(double t) {
        return t < 0.45 ? mezcla(AMBAR, CORAL, t / 0.45) : mezcla(CORAL, GRANATE, (t - 0.45) / 0.55);
    }

    /** Anillos: bandas oscuras periódicas a lo largo del cuerpo (más juntas en la cola). */
    static int anillo(double t) {
        double f = t * (14 + 14 * t);                 // frecuencia creciente hacia la cola
        double b = Math.pow(0.5 + 0.5 * Math.cos(2 * Math.PI * f), 3);
        return mezcla(base(t), 0xFF3A0B18, 0.55 * b * Math.min(1, t * 6));
    }

    /** Color de la espina con el vientre aclarado hacia crema (bulto suave entre t = 0.2 y 0.6). */
    static int espina(double t) {
        double v = Math.max(0, Math.sin(Math.PI * (t - 0.2) / 0.4));
        return mezcla(anillo(t), CREMA, 0.5 * v * v);
    }

    /** Volumen: el centro es el color de la espina y hacia el contorno se oscurece. */
    static Degradado radio(int c) {
        return x -> mezcla(c, 0xFF2A0A14, 0.55 * x * x);
    }

    // hocico, cabeza, cuello, pecho, vientre, cola enroscada: x, y, radio
    static final double[][] P = {
        { 150, 168, 4 }, { 205, 178, 9 }, { 262, 190, 24 }, { 318, 215, 38 }, { 330, 290, 30 },
        { 352, 372, 50 }, { 350, 470, 52 }, { 322, 558, 40 }, { 318, 630, 27 }, { 362, 690, 19 },
        { 435, 712, 15 }, { 495, 676, 12 }, { 488, 612, 9.5 }, { 432, 586, 7.5 }, { 390, 618, 6 },
        { 396, 664, 4.5 }, { 430, 668, 3 }
    };

    static Grafo cuerpo() {
        Grafo c = new Grafo().mezcla(RGB);
        Grafo.Nodo prev = null;
        int n = P.length;
        for (int i = 0; i < n; i++) {
            double t = (double) i / (n - 1);
            int col = espina(t);
            var nd = c.nodo(P[i][0], P[i][1], P[i][2]).centro(col).radio(radio(col));
            if (prev != null) {
                final double t0 = (double) (i - 1) / (n - 1), t1 = t;
                c.arista(prev, nd).espina(x -> espina(t0 + (t1 - t0) * x));
            }
            prev = nd;
        }
        return c;
    }

    /** Gota de base ancha y punta fina que se desvanece hacia el borde (aleta, espina, cresta). */
    static Grafo rayo(double x0, double y0, double r0, double x1, double y1, double r1,
                            double vx, double vy, int base, int punta, double desvanece) {
        Grafo c = new Grafo().mezcla(RGB);
        Degradado fb = x -> mezcla(base, conAlfa(base, 0), Math.pow(x, 1.5) * desvanece);
        Degradado fp = x -> mezcla(punta, conAlfa(punta, 0), Math.pow(x, 1.5) * desvanece);
        var a = c.nodo(x0, y0, r0).centro(base).radio(fb);
        var b = c.nodo(x1, y1, r1).centro(punta).radio(fp);
        c.arista(a, b).tiradores(new Gota.Vec(vx, vy), null);
        return c;
    }

    static void disco(BufferedImage im, double x, double y, double r, int centro, int borde) {
        Point2D.Double a = new Point2D.Double(x, y), b = new Point2D.Double(x + 0.5, y);
        Plotter.raster(im).plot(Gota.lineal(a, r, b, r, Perfil.recto()), Colores.cuatroColores(centro, centro, borde, borde, Mezcla.RGB));
    }

    static void fondo(BufferedImage im) {
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFF0B2A3D);
        Point2D.Double a = new Point2D.Double(W * 0.5, 300), b = new Point2D.Double(W * 0.5 + 0.5, 300);
        Plotter.raster(im).plot(Gota.lineal(a, 800, b, 800, Perfil.recto()),
            Colores.cuatroColores(0xFF1E6A80, 0xFF1E6A80, 0xFF08202F, 0xFF08202F, Mezcla.RGB));
    }

    static BufferedImage dibuja() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        fondo(im);
        int oro = 0xFFFFD27A, rojo = 0xFFE2573A;
        // aleta dorsal: abanico de rayos que sale de la espalda (pegada al cuerpo, por detrás)
        int n = 15;
        for (int i = 0; i < n; i++) {
            double k = (double) i / (n - 1);
            double y0 = 330 + 150 * k;
            Plotter.raster(im).plot(rayo(385, y0, 16, 495 + 30 * Math.sin(Math.PI * k), y0 - 18 + 30 * k, 9, 60, -10 + 30 * (k - 0.5),
                 mezcla(oro, rojo, k), mezcla(rojo, 0xFFC03A5B, k), 0.95));
        }
        // cresta de la cabeza: tres puntas
        for (int i = 0; i < 3; i++)
            Plotter.raster(im).plot(rayo(305 + 12 * i, 195, 10, 296 + 30 * i, 128 - 8 * (i == 1 ? 1 : 0), 3, 0, -30, oro, rojo, 0.9));
        Plotter.raster(im).plot(cuerpo());
        // pectoral: pequeña aleta translúcida sobre el costado, detrás de la cabeza
        Plotter.raster(im).plot(rayo(338, 318, 12, 292, 352, 4, -25, 25, oro, 0xFFFFE9B0, 0.85));
        // ojo
        disco(im, 282, 196, 12, 0xFFF3E9C8, 0xFFB07A22);
        disco(im, 283, 196, 7.5, 0xFF0A0A14, 0xFF0A0A14);
        disco(im, 280, 192, 2.2, 0xFFFFFFFF, 0xFFFFFFFF);
        return im;
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(dibuja(), "png", new File("caballito_by_claude.png"));
        System.out.println("caballito_by_claude.png");
    }
}
