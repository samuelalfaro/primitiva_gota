import pintado.Plotter;
import composicion.Grafo;
import coordenadas.Solape;
import color.Mezcla;
import color.Degradado;
import coordenadas.Opciones;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Colores de Composicion con funciones programáticas: Nodo.radio (de centro a contorno) y Arista.espina (a lo largo).
 * Cuatro paneles: arcoíris con paleta coseno (Íñigo Quilez), rayas con senos, tubo con brillo y pulso con escalones.
 * Escribe funciones_demo.png en el directorio actual.
 */
public class FuncionesDemo {
    static final int W = 460, H = 320;

    // ---------- colores ----------

    static int rgb(double r, double g, double b) {
        return 0xFF000000 | cl(r) << 16 | cl(g) << 8 | cl(b);
    }
    static int cl(double v) { return (int) Math.round(Math.max(0, Math.min(1, v)) * 255); }
    static double canal(int c, int sh) { return ((c >> sh) & 255) / 255.0; }

    /** Paleta coseno de Quilez: a + b cos(2π (c t + d)). */
    static int paleta(double t) {
        double[] a = {0.5, 0.5, 0.5}, b = {0.5, 0.5, 0.5}, c = {1, 1, 1}, d = {0.00, 0.33, 0.67};
        double[] o = new double[3];
        for (int i = 0; i < 3; i++) o[i] = a[i] + b[i] * Math.cos(2 * Math.PI * (c[i] * t + d[i]));
        return rgb(o[0], o[1], o[2]);
    }

    static int oscurece(int c, double k) { return rgb(canal(c, 16) * k, canal(c, 8) * k, canal(c, 0) * k); }
    static int aclara(int c, double k) {
        return rgb(canal(c, 16) + (1 - canal(c, 16)) * k, canal(c, 8) + (1 - canal(c, 8)) * k, canal(c, 0) + (1 - canal(c, 0)) * k);
    }

    // ---------- el camino común: una S con radio variable ----------

    static double[][] camino(int n) {
        double[][] p = new double[n][3];
        for (int i = 0; i < n; i++) {
            double t = (double) i / (n - 1);
            p[i][0] = 50 + t * (W - 100);
            p[i][1] = H / 2.0 + Math.sin(t * Math.PI * 2) * 85;
            p[i][2] = 15 + 12 * Math.sin(t * Math.PI);
        }
        return p;
    }

    /** Un panel: n nodos; colorNodo(t) da el color del centro; radio(t, colorCentro) la función de cada nodo; espina(t0, t1) la de cada arista. */
    interface Panel {
        Degradado radio(double t, int centro);
        Degradado espina(double t0, double t1);
        int centro(double t);
    }

    static BufferedImage panel(Panel pn, int n) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((java.awt.image.DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFFF4F1EA);
        double[][] p = camino(n);
        Grafo c = new Grafo().mezcla(Mezcla.RGB);
        Grafo.Nodo prev = null;
        for (int i = 0; i < n; i++) {
            double t = (double) i / (n - 1);
            var nd = c.nodo(p[i][0], p[i][1], p[i][2]).centro(pn.centro(t)).radio(pn.radio(t, pn.centro(t)));
            if (prev != null) c.arista(prev, nd).espina(pn.espina((double) (i - 1) / (n - 1), t));
            prev = nd;
        }
        Plotter.raster(im).plot(c, Opciones.POR_DEFECTO.conSolape(Solape.TRAZO));
        return im;
    }

    public static void main(String[] args) throws Exception {
        int n = 9;
        // 1. arcoíris: la paleta corre por la espina; cada radio oscurece hacia el contorno
        BufferedImage a = panel(new Panel() {
            public int centro(double t) { return paleta(t); }
            public Degradado espina(double t0, double t1) { return x -> paleta(t0 + (t1 - t0) * x); }
            public Degradado radio(double t, int c) { return x -> oscurece(c, 1 - 0.45 * x * x); }
        }, n);
        // 2. rayas: senos a lo largo de la espina y bandas en cada radio
        int A = 0xFFE9C46A, B = 0xFF264653;
        BufferedImage b = panel(new Panel() {
            double f(double t) { return 0.5 + 0.5 * Math.cos(t * Math.PI * 2 * 7); }
            public int centro(double t) { return Mezcla.RGB.mezclar(A, B, f(t)); }
            public Degradado espina(double t0, double t1) {
                return x -> Mezcla.RGB.mezclar(A, B, f(t0 + (t1 - t0) * x));
            }
            public Degradado radio(double t, int c) {
                return x -> Mezcla.RGB.mezclar(c, aclara(c, 0.6), 0.5 - 0.5 * Math.cos(x * Math.PI * 5));
            }
        }, n);
        // 3. tubo: luz metálica en el radio, con un brillo en x = 0.35
        BufferedImage c3 = panel(new Panel() {
            final int base = 0xFF8D99AE;
            public int centro(double t) { return base; }
            public Degradado espina(double t0, double t1) { return x -> base; }
            public Degradado radio(double t, int c) {
                return x -> {
                    double brillo = Math.exp(-Math.pow((x - 0.35) / 0.12, 2));
                    return aclara(oscurece(c, 1 - 0.7 * x * x), 0.85 * brillo);
                };
            }
        }, n);
        // 4. pulso: escalones duros a lo largo de la espina (la cuarta función de Funciones, sin suavizar)
        int P = 0xFFEF476F, Q = 0xFF06D6A0;
        BufferedImage d = panel(new Panel() {
            double g(double t) { return Math.floor(t * 6) % 2 == 0 ? 0 : 1; }
            public int centro(double t) { return Mezcla.RGB.mezclar(P, Q, g(t)); }
            public Degradado espina(double t0, double t1) {
                return x -> Mezcla.RGB.mezclar(P, Q, g(t0 + (t1 - t0) * x));
            }
            public Degradado radio(double t, int c) { return x -> oscurece(c, 1 - 0.35 * x); }
        }, n);

        BufferedImage hoja = new BufferedImage(W * 2, H * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hoja.createGraphics();
        g.drawImage(a, 0, 0, null); g.drawImage(b, W, 0, null);
        g.drawImage(c3, 0, H, null); g.drawImage(d, W, H, null);
        g.dispose();
        ImageIO.write(hoja, "png", new File("funciones_demo.png"));
        System.out.println("funciones_demo.png");
    }
}
