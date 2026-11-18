import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/** Dibujo libre solo con la API de Composicion: un pez (y el océano, una sola gota enorme) con aletas, escamas (textura con alfa), ojo, burbujas y algas. */
public class Pez {
    static final int W = 700, H = 440;

    static final int LOMO = 0xFF1E88A8, VIENTRE = 0xFFFFB74D, PLATA = 0xFFFFE0A3, ALETA = 0xFFFF7043, ALETA2 = 0xFFFFA070, OSC = 0xFFBF4A22;

    /** El océano es una sola gota enorme: dos círculos de radio 1100 casi coincidentes, claros en el centro (arriba) y oscuros en el borde. */
    static void agua(BufferedImage im) {
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFF0F5B7A);
        Point2D.Double a = new Point2D.Double(W / 2.0, -420), b = new Point2D.Double(W / 2.0 + 0.5, -420);
        Plotter.raster(im).plot(Gota.lineal(a, 1000, b, 1000, Perfil.recto()), Colores.cuatroColores(0xFF9AD9E8, 0xFF9AD9E8, 0xFF0F5B7A, 0xFF0F5B7A, Mezcla.RGB));
    }

    static void disco(BufferedImage im, double x, double y, double r, int centro, int borde) {
        Point2D.Double a = new Point2D.Double(x, y), b = new Point2D.Double(x + 0.5, y);
        Plotter.raster(im).plot(Gota.lineal(a, r, b, r, Perfil.recto()), Colores.cuatroColores(centro, centro, borde, borde, Mezcla.RGB));
    }

    /** Alga: cadena de nodos en zigzag con el radio decreciente. */
    static Composicion alga(double x, double y, double alto, double onda, int c1, int c2) {
        Composicion c = new Composicion();
        Composicion.Nodo prev = null;
        int n = 6;
        for (int i = 0; i < n; i++) {
            double t = (double) i / (n - 1);
            var nd = c.nodo(x + onda * Math.sin(t * 5.0) * (0.4 + t), y - alto * t, 9 * (1 - t) + 2).centro(Mezcla.RGB.mezclar(c1, c2, t));
            if (i == 0 || i == n - 1) nd.sectores(Mezcla.RGB.mezclar(c1, c2, t)); else nd.lados(c1, c2);
            if (prev != null) c.arista(prev, nd);
            prev = nd;
        }
        return c;
    }

    /** Cuerpo: de la cola al morro, el lomo (lado N, arriba) azul y el vientre naranja. */
    static Composicion cuerpo() {
        Composicion c = new Composicion();
        double[][] p = { { 178, 262, 8 }, { 262, 250, 33 }, { 360, 232, 58 }, { 456, 228, 61 }, { 535, 240, 40 }, { 592, 256, 12 } };
        Composicion.Nodo prev = null;
        for (int i = 0; i < p.length; i++) {
            var nd = c.nodo(p[i][0], p[i][1], p[i][2]).centro(PLATA);
            if (i == 0) nd.sectores(VIENTRE); else if (i == p.length - 1) nd.sectores(0xFFE08A3A); else nd.lados(VIENTRE, LOMO).ajusteLateral(0.38);
            if (prev != null) c.arista(prev, nd);
            prev = nd;
        }
        return c;
    }

    /** Cola: dos lóbulos que parten del pedúnculo. */
    static Composicion cola() {
        Composicion c = new Composicion();
        var raiz = c.nodo(182, 261, 7).centro(ALETA2).sectores(ALETA, OSC);
        var arriba = c.nodo(92, 168, 24).centro(ALETA2).sectores(ALETA);
        var abajo = c.nodo(96, 352, 24).centro(ALETA2).sectores(ALETA);
        c.arista(raiz, arriba).tiradores(new Gota.Vec(-50, -4), new Gota.Vec(30, 36));
        c.arista(raiz, abajo).tiradores(new Gota.Vec(-50, 4), new Gota.Vec(30, -36));
        return c;
    }

    static Composicion aleta(double x0, double y0, double r0, double x1, double y1, double r1, double vx, double vy, double asp) {
        Composicion c = new Composicion();
        var a = c.nodo(x0, y0, r0).centro(ALETA2).sectores(OSC);
        var b = c.nodo(x1, y1, r1).centro(ALETA2).sectores(ALETA);
        c.arista(a, b).tiradores(new Gota.Vec(vx, vy), null);
        return c;
    }

    /** Escamas: filas de pequeños arcos desplazados media escama, oscuros y con alfa, en (u across, v along) de cada tramo del cuerpo. */
    static final Texturas.Textura ESCAMAS = (u, v) -> {
        double fu = u * 9, fv = v * 7;
        int fila = (int) Math.floor(fu);
        double vv = fv + ((fila & 1) == 0 ? 0 : 0.5);
        double cu = fu - fila - 0.5, cv = (vv - Math.floor(vv)) - 0.5;
        double d = Math.hypot(cu * 1.0, cv * 1.0);
        double borde = Math.abs(d - 0.48);
        double a = borde < 0.06 && cv > -0.1 ? 1 - borde / 0.06 : 0;
        double centro = Math.abs(2 * u - 1);
        int alfa = (int) Math.round(95 * a * (1 - 0.7 * centro * centro));
        return alfa << 24 | 0x10445C;
    };

    static BufferedImage dibuja() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        agua(im);
        alga(70, 440, 190, 22, 0xFF2E7D32, 0xFF9CCC65).render(im);
        alga(115, 445, 130, -16, 0xFF1B5E20, 0xFF7CB342).render(im);
        alga(610, 445, 160, 20, 0xFF2E7D32, 0xFF9CCC65).render(im);
        for (double[] b : new double[][] { { 600, 160, 9 }, { 622, 118, 6 }, { 606, 82, 4 }, { 640, 50, 3 } })
            disco(im, b[0], b[1], b[2], 0xFFEAF9FF, 0xFFA7E1F0);
        cola().render(im);
        aleta(385, 200, 36, 318, 104, 3.5, -10, -50, 1.0).render(im);               // dorsal
        aleta(415, 282, 26, 372, 358, 3, -6, 30, 1.0).render(im);                // ventral
        Composicion cuerpo = cuerpo();
        cuerpo.render(im);
        cuerpo.render(im, ESCAMAS);
        aleta(486, 270, 17, 420, 330, 3, -25, 14, 1.0).render(im);               // pectoral
        // agalla
        Composicion ag = new Composicion();
        var g0 = ag.nodo(522, 205, 2.2).color(OSC);
        var g1 = ag.nodo(516, 272, 2.2).color(OSC);
        ag.arista(g0, g1).tiradores(new Gota.Vec(-26, 18), null);
        ag.render(im);
        // ojo
        disco(im, 552, 226, 11, 0xFFFFFFFF, 0xFFE0E0E0);
        disco(im, 555, 226, 6, 0xFF222222, 0xFF000000);
        disco(im, 557, 223, 2, 0xFFFFFFFF, 0xFFFFFFFF);
        return im;
    }

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        ImageIO.write(dibuja(), "png", new File("pez.png"));
        System.out.printf("render: %.1f s%n", (System.nanoTime() - t0) / 1e9);
    }
}
