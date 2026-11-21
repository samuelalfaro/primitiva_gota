import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Dibujo libre con las primitivas: un rape abisal (pez pescador), el vecino de abajo de las medusas.
 * Todo el pez está pintado con gotas: cuerpo en cadena (lomo oscuro y vientre claro con
 * {@code radios(Degradado, Degradado)}), mandíbula con dientes en aguja, aletas y cola que se desvanecen,
 * y la caña con su esca bioluminiscente, cuyo brillo radial se concentra con {@link Degradado#ajustado}.
 * El fondo del abismo y el plancton van directamente en el raster; el pez entero llama a render.
 * Escribe pez_abisal.png en el directorio actual.
 */
public class PezAbisal {
    static final int W = 820, H = 560;
    static final Mezcla RGB = Mezcla.RGB;

    static int mezcla(int a, int b, double t) { return RGB.mezclar(a, b, Math.max(0, Math.min(1, t))); }
    static int conAlfa(int c, double k) {
        return ((int) Math.round((c >>> 24) * Math.max(0, Math.min(1, k)))) << 24 | (c & 0xFFFFFF);
    }
    static int oscuro(int c, double k) { return mezcla(c, 0xFF050A16, k); }
    static int claro(int c, double k) { return mezcla(c, 0xFFD8E6EE, k); }

    static final int OCEANO   = 0xFF060D1C;
    static final int LOMO     = 0xFF2F5D74;
    static final int COLA     = 0xFF22304A;
    static final int MEMBRANA = 0xFF4E7E96;
    static final int DIENTE   = 0xFFE6EFF2;
    static final int OJO      = 0xFFEFE6CF;
    static final int ESCA     = 0xFFD6F8E0;
    static final int NUCLEO   = 0xFFF9FBEA;

    static void fondo(BufferedImage im) {
        int[] pix = ((DataBufferInt) im.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < H; y++) {
            double t = y / (double) (H - 1);
            int col = mezcla(0xFF08122B, 0xFF02050C, Math.pow(t, 1.3));
            for (int x = 0; x < W; x++) {
                double dx = (x - 230.0) / (W * 0.75), dy = (y - 200.0) / (H * 0.75);
                double v = Math.min(1, dx * dx + dy * dy);
                pix[y * W + x] = mezcla(col, 0xFF0B1B33, 0.30 * (1 - v));
            }
        }
    }

    /** Plancton en suspensión: motas deterministas, nada de azar en una lámina. */
    static void plancton(BufferedImage im) {
        for (int i = 0; i < 80; i++) {
            double a = i * 2.39996;
            int x = (int) Math.round((0.5 + 0.46 * Math.sin(a * 1.7 + i * 0.31)) * W);
            int y = (int) Math.round((0.10 + 0.80 * (((i * 37) % 97) / 97.0)) * H);
            double alfa = 0.08 + 0.14 * Math.abs(Math.sin(a * 2.9));
            celda(motas(im), x, y, i % 6 == 0 ? 2 : 1, conAlfa(0xFF9FD8E8, alfa));
        }
    }

    static int[] motaPix;

    static int[] motas(BufferedImage im) { return ((DataBufferInt) im.getRaster().getDataBuffer()).getData(); }

    static void celda(int[] pix, int x, int y, int r, int c) {
        int a = c >>> 24;
        for (int j = -r; j <= r; j++) for (int i = -r; i <= r; i++) {
            int px = x + i, py = y + j;
            if (px < 0 || py < 0 || px >= W || py >= H) continue;
            if (i * i + j * j > r * r) continue;
            int k = py * W + px;
            int f = pix[k];
            double t = a / 255.0;
            pix[k] = conAlfa(mezcla(f & 0xFFFFFF, c & 0xFFFFFF, t), Math.max((f >>> 24) / 255.0, t));
        }
    }

    /**
     * Cadena de gotas: por cada punto un nodo con volumen lomo, entre nodos una arista con espina en LCH;
     * con {@code puntaQueSeApaga} las dos últimas secciones se apagan en alfa, como membrana que se disuelve.
     */
    static void cadena(Composicion c, double[] xs, double[] ys, double[] rs, int col0, int col1, boolean puntaQueSeApaga) {
        int n = xs.length;
        Composicion.Nodo prev = null;
        for (int i = 0; i < n; i++) {
            double t = n == 1 ? 0 : i / (double) (n - 1);
            int col = mezcla(col0, col1, t);
            final int cc = col;
            final boolean punta = puntaQueSeApaga && i == n - 1;
            final boolean antes = puntaQueSeApaga && i == n - 2;
            Degradado rad = punta || antes
                ? v -> conAlfa(oscuro(cc, 0.30 * v * v), Math.max(0, 1 - (antes ? 0.65 : 1.0) * v * v))
                : v -> oscuro(cc, 0.34 * v * v);
            var nd = c.nodo(xs[i], ys[i], rs[i]).centro(col).radio(rad);
            if (prev != null) {
                final Composicion.Nodo p = prev;
                colPrev = mezcla(col0, col1, Math.max(0, t - 1.0 / (n - 1)));
                final int a = colPrev, b = col;
                c.arista(p, nd).espina(Degradado.entre(a, b, Mezcla.LCH));
            }
            prev = nd;
        }
    }

    static int colPrev;

    static double interpola(double t) {
        double[] claves = { 0, 0.08, 0.18, 0.34, 0.52, 0.72, 0.88, 1 };
        double[] vals = { 30, 58, 78, 70, 52, 34, 22, 12 };
        t = Math.max(0, Math.min(1, t));
        for (int j = 1; j < claves.length; j++)
            if (t <= claves[j]) {
                double u = (t - claves[j - 1]) / (claves[j] - claves[j - 1]);
                return vals[j - 1] + (vals[j] - vals[j - 1]) * u;
            }
        return vals[vals.length - 1];
    }

    /** Y por debajo se aclara: contraluz, como si la esca lo bañara desde arriba y adelante. */
    static Degradado contraluz(int col) { return v -> claro(col, (0.20 - 0.06 * v) * v * 2); }

    static BufferedImage dibuja() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        fondo(im);

        Composicion c = new Composicion().mezcla(RGB);

        // ----- cuerpo: cadena por la línea media, de la nariz al pedúnculo; normal arriba (lomo), contra abajo (vientre)
        final int N = 16;
        double[] xs = new double[N], ys = new double[N], rs = new double[N];
        for (int i = 0; i < N; i++) {
            double t = i / (double) (N - 1);
            xs[i] = 252 + 410 * t;
            ys[i] = 322 - 20 * Math.sin(Math.PI * Math.pow(t, 0.8));
            rs[i] = interpola(t);
        }
        int col0Cuerpo = LOMO;
        var cabeza = c.nodo(xs[0], ys[0], rs[0]).centro(col0Cuerpo)
            .radio(v -> oscuro(col0Cuerpo, 0.34 * v * v));
        Composicion.Nodo prev = cabeza;
        for (int i = 1; i < N; i++) {
            double t = i / (double) (N - 1);
            final int col = mezcla(LOMO, COLA, t);
            var nd = c.nodo(xs[i], ys[i], rs[i]).centro(col);
            if (i == N - 1) nd.radio(v -> oscuro(col, 0.34 * v * v));
            else nd.radios(v -> oscuro(col, 0.34 * v * v), contraluz(col));
            final int a = mezcla(LOMO, COLA, (i - 1) / (double) (N - 1));
            c.arista(prev, nd).espina(Degradado.entre(a, col, Mezcla.LCH));
            prev = nd;
        }

        // ----- illicium: la caña de pescar, curvada hacia adelante; algo más clara para verla contra el fondo -----
        cadena(c, new double[] { 306, 276, 244, 220 },
                    new double[] { 236, 208, 188, 176 },
                    new double[] { 6.5, 5, 4, 3 },
                    claro(LOMO, 0.15), claro(MEMBRANA, 0.05), false);

        // ----- esca: el señuelo que enciende la noche -----
        var halo = c.nodo(200, 168, 34).centro(conAlfa(ESCA, 0.85))
            .radio(Degradado.entre(conAlfa(ESCA, 0.85), conAlfa(ESCA, 0.0), RGB).ajustado(0.30));
        var nucleo = c.nodo(197, 165, 9).centro(NUCLEO).radio(v -> claro(NUCLEO, 0.12 * v));
        c.arista(halo, nucleo).espina(Degradado.entre(conAlfa(ESCA, 0.85), NUCLEO, RGB));

        // ----- mandíbula inferior colgando; entre ella y el morro queda la boca entreabierta -----
        cadena(c, new double[] { 254, 240, 252 },
                    new double[] { 352, 378, 390 },
                    new double[] { 12, 9, 0.01 },
                    oscuro(LOMO, 0.25), oscuro(LOMO, 0.40), false);

        // ----- dientes: agujas finas; caen desde el morro y suben desde la mandíbula -----
        double[][] caen = { { 268, 344, 370 }, { 292, 366, 396 }, { 316, 382, 400 }, { 338, 380, 396 } };
        for (double[] d : caen)
            cadena(c, new double[] { d[0], d[0] + 4 }, new double[] { d[1], d[2] + 26 },
                        new double[] { 2.6, 0.6 }, DIENTE, conAlfa(DIENTE, 0.8), true);
        double[][] suben = { { 262, 388, 362 }, { 284, 398, 374 }, { 306, 398, 374 } };
        for (double[] d : suben)
            cadena(c, new double[] { d[0], d[0] - 3 }, new double[] { d[1], d[2] },
                        new double[] { 0.6, 2.4 }, conAlfa(DIENTE, 0.8), DIENTE, false);

        // ----- aleta dorsal (la púa del pescador detrás de la caña) -----
        cadena(c, new double[] { 356, 376, 402 },
                    new double[] { 250, 224, 224 },
                    new double[] { 7, 5, 3 },
                    MEMBRANA, conAlfa(MEMBRANA, 0.7), true);
        // ----- aleta pectoral -----
        cadena(c, new double[] { 402, 424, 450 },
                    new double[] { 358, 386, 398 },
                    new double[] { 8, 6, 3 },
                    MEMBRANA, conAlfa(MEMBRANA, 0.6), true);

        // ----- cola: abanico de tres rayos que se desvanecen -----
        cadena(c, new double[] { 662, 714, 756 }, new double[] { 306, 272, 252 },
                    new double[] { 8, 5, 2 }, COLA, conAlfa(MEMBRANA, 0.5), true);
        cadena(c, new double[] { 662, 728, 770 }, new double[] { 310, 316, 320 },
                    new double[] { 9, 6, 2.5 }, COLA, conAlfa(MEMBRANA, 0.6), true);
        cadena(c, new double[] { 662, 714, 756 }, new double[] { 316, 350, 380 },
                    new double[] { 8, 5, 2 }, COLA, conAlfa(MEMBRANA, 0.5), true);

        c.render(im, Opciones.POR_DEFECTO.conSolape(Solape.TRAZO));

        // ----- ojo, encima de todo (discos directos, como los del pulpo) -----
        disco(im, 322, 294, 12, OJO, oscuro(OJO, 0.30));
        disco(im, 325, 297, 5.5, 0xFF141826, 0xFF141826);
        disco(im, 322, 294, 1.8, 0xFFFFFFFF, 0xFFFFFFFF);

        plancton(im);
        return im;
    }

    /** Disco pintado directo en el raster (solo para el ojo, fuera de la Composicion). */
    static void disco(BufferedImage im, double x, double y, double r, int centro, int borde) {
        java.awt.geom.Point2D.Double a = new java.awt.geom.Point2D.Double(x, y);
        java.awt.geom.Point2D.Double b = new java.awt.geom.Point2D.Double(x + 0.5, y);
        Plotter.raster(im).plot(Gota.lineal(a, r, b, r, Perfil.recto()),
            Colores.cuatroColores(centro, centro, borde, borde, Mezcla.RGB));
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(dibuja(), "png", new File("pez_abisal.png"));
        System.out.println("pez_abisal.png");
    }
}
