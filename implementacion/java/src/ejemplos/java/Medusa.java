import geometria.composicion.Grafo;
import pintado.Plotter;
import color.Mezcla;
import color.Degradado;
import forma.Perfil;
import forma.Gota;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Dibujo libre con las primitivas: una medusa. Todo está pintado con gotas: el fondo y su halo, los rayos de
 * luz, la campana (un disco de luz con el borde en cadena de nodos, violeta en el culmen y cian en los remates),
 * los flecos del borde, las tentaculas que ondulan y se desvanecen (alfa en la espina), los brazos orales gruesos
 * encima, y el plancton y las burbujas. Escribe medusa.png.
 */
public class Medusa {
    static final int W = 860, H = 640;
    static final double CX = 430, CY = 322, RC = 166;   // centro y radio de la campana
    static final Mezcla RGB = Mezcla.RGB;

    static final int VIOLETA = 0xFF7C3AED, VIOLETA_CLARO = 0xFFA78BFA, CIAN = 0xFF22D3EE, CIAN_PALIDO = 0xFFA5F3FC;
    static final int ROSA = 0xFFF472B6, AGUAMARINA = 0xFF2DD4BF, FONDO = 0xFF04101F;

    static int conAlfa(int c, double k) { return ((int) Math.round((c >>> 24) * k)) << 24 | (c & 0xFFFFFF); }
    static int mezcla(int a, int b, double t) { return RGB.mezclar(a, b, Math.max(0, Math.min(1, t))); }
    static int oscuro(int c, double k) { return mezcla(c, FONDO, k); }

    /** Disco con degradado radial propio: f va del centro (x = 0) al borde (x = 1). */
    static void disco(BufferedImage im, double x, double y, double r, Degradado f) {
        var a = new Point2D.Double(x, y);
        var b = new Point2D.Double(x + 0.5, y);
        Plotter.raster(im).plot(Gota.lineal(a, r, b, r, Perfil.recto()), p -> f.en(p.muestra().rho()));
    }

    /** Halo suave: el color del centro se desvanece hacia el borde. */
    static void halo(BufferedImage im, double x, double y, double r, int c, double brillo) {
        disco(im, x, y, r, v -> conAlfa(c, brillo * (1 - v * v)));
    }

    static Grafo.Nodo nodo(Grafo c, double x, double y, double r, int col, Degradado f) {
        return c.nodo(x, y, r).centro(col).radio(f);
    }

    /** Lado del nodo: se oscurece un poco y el alfa se funde con el fondo (v = 0 centro, 1 borde). */
    static Degradado lado(int col, double funde) {
        return v -> { int o = oscuro(col, 0.30 * v * v); return mezcla(o, conAlfa(o, 0.0), Math.pow(v, 1.4) * funde); };
    }

    // ---------- fondo ----------

    static void fondo(BufferedImage im) {
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), FONDO);
        halo(im, CX, -120, 720, 0xFF0E2A52, 0.85);
        halo(im, 90, H + 60, 380, 0xFF0A1C3A, 0.6);
        halo(im, W - 60, H * 0.25, 260, 0xFF123B6E, 0.30);
        rayo(im, 230, 150, 0.44);
        rayo(im, 520, 90, 0.34);
        rayo(im, 720, 170, 0.40);
    }

    /** Alfa del rayo: entra en la parte de arriba (t = 0) y se apaga al bajar. */
    static double alfaRayo(double t) { return (1 - t) * Math.min(1, t / 0.02); }

    /** Rayo de luz: cadena vertical que se ensancha y se desvanece al bajar. */
    static void rayo(BufferedImage im, double x0, double caida, double brillo) {
        Grafo c = new Grafo().mezcla(RGB);
        int n = 4;
        Grafo.Nodo prev = null;
        for (int j = 0; j < n; j++) {
            double t = j / (n - 1.0);
            double x = x0 + 90 * t + 26 * Math.sin(t * Math.PI * 0.9);
            double y = -40 + (H + 60) * t + caida * Math.sin(t * Math.PI);
            int col = conAlfa(0xFF2A6FB8, brillo * alfaRayo(t));
            var nd = c.nodo(x, y, 20 + 55 * t).centro(col)
                .radio(v -> conAlfa(col, Math.max(0, 1 - 0.85 * v * v)));
            if (prev != null) {
                final double t0 = (j - 1) / (n - 1.0), t1 = t;
                c.arista(prev, nd).espina(s -> {
                    double tt = t0 + (t1 - t0) * s;
                    return conAlfa(0xFF2A6FB8, brillo * alfaRayo(tt));
                });
            }
            prev = nd;
        }
        Plotter.raster(im).plot(c);
    }

    // ---------- campana ----------

    static int bordeCampana(double t) { return mezcla(CIAN, VIOLETA, Math.pow(Math.sin(Math.PI * t), 0.7)); }

    static Grafo campana(double cx, double cy, double rc) {
        Grafo c = new Grafo().mezcla(RGB);
        int n = 9;
        Grafo.Nodo prev = null;
        for (int i = 0; i < n; i++) {
            double t = (double) i / (n - 1);
            double a0 = Math.toRadians(196 - 212 * t);
            double x = cx + rc * Math.cos(a0), y = cy - rc * Math.sin(a0);
            int col = bordeCampana(t);
            var nd = nodo(c, x, y, (13 + 50 * Math.sin(Math.PI * t)) * (rc / RC), col, lado(col, 0.5));
            if (prev != null) {
                final double t0 = (i - 1) / (double) (n - 1), t1 = t;
                c.arista(prev, nd).espina(s -> bordeCampana(t0 + (t1 - t0) * s));
            }
            prev = nd;
        }
        return c;
    }

    /** La luz interior de la campana y su brillo apical. */
    static void luzCampana(BufferedImage im, double cx, double cy, double rc) {
        double k = rc / RC;
        halo(im, cx, cy + 12 * k, 134 * k, VIOLETA, 0.42);
        halo(im, cx, cy + 26 * k, 88 * k, 0xFF8B5CF6, 0.34);
        halo(im, cx, cy + 40 * k, 46 * k, 0xFFC4B5FD, 0.22);
        halo(im, cx, cy - rc * 0.72, 26 * k, CIAN_PALIDO, 0.22);
    }

    /** Flecos colgando del borde inferior de la campana. */
    static void flecos(BufferedImage im, double cx, double cy, double rc) {
        int n = 9;
        double k = rc / RC;
        for (int j = 0; j < n; j++) {
            double t = (j + 0.5) / n;
            double a0 = Math.toRadians(196 - 212 * t);
            double x = cx + (rc - 6 * k) * Math.cos(a0), y = cy - (rc - 6 * k) * Math.sin(a0) + 20 * k;
            int col = bordeCampana(t);
            Grafo c = new Grafo().mezcla(RGB);
            var a = nodo(c, x, y + 6 * k, 7 * k, col, lado(col, 0.8));
            int punta = conAlfa(col, 0.6);
            var b = nodo(c, x + 5 * k * Math.sin(t * 6), y + 26 * k, 1.5 * k, punta, lado(punta, 0.95));
            c.arista(a, b).espina(s -> mezcla(col, punta, s));
            Plotter.raster(im).plot(c);
        }
    }

    // ---------- tentaculas y brazos ----------

    /** Tentacula: cadena de nodos que ondula y se desvanece con t. */
    static Grafo tentacula(double x0, double y0, double largo, double fase, int base, int punta, double gr) {
        Grafo c = new Grafo().mezcla(RGB);
        int n = 9;
        Grafo.Nodo prev = null;
        for (int i = 0; i < n; i++) {
            double t = i / (n - 1.0);
            double x = x0 + 30 * Math.sin(t * 2.4 * Math.PI + fase) * t * t;
            double y = y0 + largo * (t + 0.08 * Math.sin(t * 5.1 * Math.PI + fase));
            int alfa = (int) Math.round(255 * (1 - 0.85 * t));
            int col = conAlfa(mezcla(base, punta, t), alfa / 255.0);
            var nd = nodo(c, x, y, (4.6 - 3.1 * t) * gr, col, lado(col, 0.7));
            if (prev != null) {
                final double t0 = (i - 1) / (n - 1.0), t1 = t;
                final int b0 = base, p0 = punta;
                c.arista(prev, nd).espina(s -> {
                    double tt = t0 + (t1 - t0) * s;
                    int cc = mezcla(b0, p0, tt);
                    return conAlfa(cc, 1 - 0.85 * tt);
                });
            }
            prev = nd;
        }
        return c;
    }

    /** Brazos orales: más gruesos, cálidos y con rulo final. */
    static Grafo brazo(double x0, double y0, double largo, double fase, int signo) {
        Grafo c = new Grafo().mezcla(RGB);
        int n = 9;
        Grafo.Nodo prev = null;
        for (int i = 0; i < n; i++) {
            double t = i / (n - 1.0);
            double x = x0 + signo * (46 * Math.sin(t * 1.7 * Math.PI + fase) + 18 * t * t);
            double y = y0 + largo * t;
            double adel = Math.pow(Math.sin(Math.PI * Math.min(1, t * 1.15)), 0.8);
            int col = conAlfa(mezcla(ROSA, AGUAMARINA, 0.5 + 0.5 * Math.sin(fase + t * 2.5)), adel);
            var nd = nodo(c, x, y, 13 - 10 * t, col, lado(col, 0.75));
            if (prev != null) {
                final double t0 = (i - 1) / (n - 1.0), t1 = t;
                final double f = fase;
                c.arista(prev, nd).espina(s -> {
                    double tt = t0 + (t1 - t0) * s;
                    return conAlfa(mezcla(ROSA, AGUAMARINA, 0.5 + 0.5 * Math.sin(f + tt * 2.5)),
                        Math.pow(Math.sin(Math.PI * Math.min(1, tt * 1.15)), 0.8));
                });
            }
            prev = nd;
        }
        return c;
    }

    // ---------- vida de fondo ----------

    static void plancton(BufferedImage im) {
        double[][] p = { { 140, 180, 2.2, 1 }, { 210, 420, 1.6, 0 }, { 690, 140, 2.0, 1 }, { 760, 360, 1.4, 0 },
            { 320, 120, 1.5, 0 }, { 620, 500, 2.4, 1 }, { 90, 300, 1.3, 0 }, { 810, 480, 1.7, 1 },
            { 500, 90, 1.4, 0 }, { 180, 560, 1.9, 1 } };
        for (double[] q : p) {
            int c = conAlfa(q[3] > 0 ? CIAN_PALIDO : 0xFF7DD3FC, 0.55);
            halo(im, q[0], q[1], 2.5 + 3.5 * q[2], c, 0.8);
        }
    }

    static void burbujas(BufferedImage im) {
        double[][] b = { { 760, 520, 9 }, { 790, 420, 6 }, { 745, 320, 11 }, { 800, 220, 7 }, { 120, 470, 8 }, { 95, 380, 5 } };
        for (double[] q : b) {
            double x = q[0], y = q[1], r = q[2];
            int borde = conAlfa(0xFF7DD3FC, 0.5), centro = conAlfa(0xFF22D3EE, 0.10);
            disco(im, x, y, r, v -> mezcla(centro, borde, Math.pow(v, 3)));
            halo(im, x - r * 0.25, y - r * 0.3, r * 0.25, conAlfa(0xFFFFFFFF, 0.6), 0.6);
        }
    }

    static BufferedImage dibuja() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        fondo(im);
        plancton(im);
        burbujas(im);
        medusa(im, 660, 190, 62, 2.9, 0.55);
        medusa(im, CX, CY, RC, 1.7, 1.0);
        return im;
    }

    /** Una medusa completa (flecos, tentaculas, campana y brazos) en la posición y escala dadas. */
    static void medusa(BufferedImage im, double cx, double cy, double rc, double fase, double k) {
        double paso = 34 * k;
        for (double x0 = cx - 4 * paso; x0 <= cx + 4.1 * paso; x0 += paso) {
            double t = (x0 - (cx - 4 * paso)) / (8 * paso);
            double y0 = cy - 14 * k - 12 * k * Math.sin(Math.PI * t);
            Plotter.raster(im).plot(tentacula(x0, y0, (210 + 90 * Math.sin(Math.PI * t)) * k, fase + 1.9 * t,
                mezcla(CIAN, VIOLETA_CLARO, t), VIOLETA_CLARO, k * (0.8 + 0.4 * Math.sin(Math.PI * t))));
        }
        luzCampana(im, cx, cy, rc);
        Plotter.raster(im).plot(campana(cx, cy, rc));
        flecos(im, cx, cy, rc);
        if (k > 0.8) {
            Plotter.raster(im).plot(brazo(cx - 16, cy + 64, 210, 0.6, -1));
            Plotter.raster(im).plot(brazo(cx + 16, cy + 64, 230, 2.2, 1));
        }
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(dibuja(), "png", new File("medusa.png"));
        System.out.println("medusa.png");
    }
}
