import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Dibujo libre solo con la API de Composicion: un colibrí en vuelo ante una flor, firmado con caligrafía. La firma es un trazo
 * continuo (Catmull-Rom sobre puntos clave) cuyo radio, la presión de la pluma, sube en los trazos descendentes y baja en los ascendentes.
 */
public class Colibri {
    static final int W = 800, H = 560;

    static void disco(BufferedImage im, double x, double y, double r, int centro, int borde) {
        Point2D.Double a = new Point2D.Double(x, y), b = new Point2D.Double(x + 0.5, y);
        Gota.lineal(a, r, b, r, Perfil.recto()).render(im, Colores.cuatroColores(centro, centro, borde, borde, Mezcla.RGB));
    }

    /** Cadena de nodos por los puntos dados (x, y, r); el primero y el último con un solo color. */
    static Composicion cadena(double[][] p, int centro, int extremoIni, int extremoFin, int contra, int normal, double ajuste) {
        Composicion c = new Composicion();
        Composicion.Nodo prev = null;
        for (int i = 0; i < p.length; i++) {
            var nd = c.nodo(p[i][0], p[i][1], p[i][2]).centro(centro);
            if (i == 0) nd.sectores(extremoIni); else if (i == p.length - 1) nd.sectores(extremoFin); else nd.lados(contra, normal).ajusteLateral(ajuste);
            if (prev != null) c.arista(prev, nd);
            prev = nd;
        }
        return c;
    }

    // ---------- caligrafía ----------

    /** Puntos clave de «Claude» en cursiva (caja de unos 260 x 90, línea base y = 80), de izquierda a derecha, en un solo trazo. */
    static final double[][] CLAUDE = {
        { 52, 30 }, { 40, 21 }, { 25, 26 }, { 14, 45 }, { 15, 65 }, { 27, 78 }, { 43, 76 }, { 53, 64 },               // C
        { 66, 46 }, { 78, 24 }, { 86, 6 }, { 82, -2 }, { 73, 8 }, { 70, 40 }, { 72, 66 }, { 80, 78 }, { 93, 70 },    // l
        { 103, 54 }, { 94, 47 }, { 86, 58 }, { 89, 73 }, { 100, 79 }, { 109, 68 }, { 113, 44 }, { 114, 66 }, { 122, 78 }, { 134, 66 },   // a
        { 140, 49 }, { 142, 66 }, { 149, 78 }, { 158, 69 }, { 162, 50 }, { 164, 68 }, { 172, 78 }, { 184, 66 },       // u
        { 190, 56 }, { 180, 46 }, { 170, 55 }, { 172, 72 }, { 184, 79 }, { 193, 62 }, { 198, 36 }, { 203, 6 }, { 199, -4 }, { 195, 20 }, { 195, 56 }, { 201, 76 }, { 212, 72 }, { 221, 60 },  // d
        { 232, 58 }, { 241, 50 }, { 234, 41 }, { 225, 49 }, { 223, 66 }, { 231, 78 }, { 245, 76 }, { 258, 64 }, { 272, 52 } };  // e y la cola

    static double[] catmull(double[] p0, double[] p1, double[] p2, double[] p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        double x = 0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3);
        double y = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3);
        return new double[] { x, y };
    }

    /** La firma como composición: puntos de la spline cada ~paso píxeles, con la presión según la dirección vertical del trazo. */
    static Composicion firma(double x0, double y0, double esc, double paso) {
        List<double[]> pts = new ArrayList<>();
        int n = CLAUDE.length;
        for (int i = 0; i < n - 1; i++) {
            double[] a = CLAUDE[Math.max(0, i - 1)], b = CLAUDE[i], c = CLAUDE[i + 1], d = CLAUDE[Math.min(n - 1, i + 2)];
            int m = 8;
            for (int k = 0; k < m; k++) pts.add(catmull(a, b, c, d, (double) k / m));
        }
        pts.add(CLAUDE[n - 1]);
        // remuestreo por longitud de arco
        List<double[]> rs = new ArrayList<>();
        rs.add(pts.get(0));
        double acum = 0;
        for (int i = 1; i < pts.size(); i++) {
            acum += Math.hypot(pts.get(i)[0] - pts.get(i - 1)[0], pts.get(i)[1] - pts.get(i - 1)[1]);
            if (acum >= paso / esc) { rs.add(pts.get(i)); acum = 0; }
        }
        double[][] q = new double[rs.size()][3];
        double[] pres = new double[rs.size()];
        for (int i = 0; i < rs.size(); i++) {
            double[] a = rs.get(Math.max(0, i - 1)), b = rs.get(Math.min(rs.size() - 1, i + 1));
            double dy = b[1] - a[1], l = Math.hypot(b[0] - a[0], dy);
            pres[i] = l > 0 ? Math.max(0, dy / l) : 0;                    // 1 = bajando recto, 0 = subiendo o en horizontal
        }
        for (int i = 0; i < rs.size(); i++) {                             // suavizado de la presión
            double s = 0; int c = 0;
            for (int k = -3; k <= 3; k++) { int j = i + k; if (j >= 0 && j < rs.size()) { s += pres[j]; c++; } }
            double p = s / c;
            q[i] = new double[] { x0 + esc * rs.get(i)[0], y0 + esc * rs.get(i)[1], esc * (1.0 + 2.8 * p) * 0.9 };
        }
        q[0][2] = esc * 0.8;
        q[q.length - 1][2] = esc * 0.7;
        return cadena(q, 0xFF2B2438, 0xFF2B2438, 0xFF2B2438, 0xFF2B2438, 0xFF2B2438, 0.5);
    }

    // ---------- colibrí ----------

    static Composicion ala(double x0, double y0, double r0, double x1, double y1, double r1, double vx, double vy, int c) {
        Composicion k = new Composicion();
        var a = k.nodo(x0, y0, r0).centro(c).sectores(c);
        var b = k.nodo(x1, y1, r1).centro(c).sectores(c);
        k.arista(a, b).tiradores(new Gota.Vec(vx, vy), null);
        return k;
    }

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFF2E5D3A);
        disco(im, 430, -380, 1200, 0xFFEAF3C8, 0xFF2E5D3A);                   // el fondo: una gota enorme
        disco(im, 700, 110, 46, 0xFFFFF7CF, 0xFFF4E39A);                       // un halo de luz
        // hojas y flor
        Composicion hoja = Hoja.limbo(new Hoja.Tr(40, 560, Math.toRadians(-62), 0.42), 3.6);
        hoja.render(im);
        Hoja.venas(hoja, 0.8, 0.5).render(im);
        Composicion tallo = new Composicion();
        var t0n = tallo.nodo(40, 570, 6).centro(0xFF558B2F).sectores(0xFF33691E);
        var t1n = tallo.nodo(70, 420, 5.5).centro(0xFF7CB342).lados(0xFF33691E, 0xFF8BC34A);
        var t2n = tallo.nodo(98, 300, 5).centro(0xFF7CB342).sectores(0xFF558B2F);
        tallo.arista(t0n, t1n);
        tallo.arista(t1n, t2n);
        tallo.render(im);
        Composicion flor = new Composicion();
        var fb = flor.nodo(98, 300, 7).centro(0xFFFFB74D).sectores(0xFFE65100);
        var fo = flor.nodo(138, 218, 40).centro(0xFFFFEE58).sectores(0xFFD32F2F);
        flor.arista(fb, fo).tiradores(new Gota.Vec(10, -40), new Gota.Vec(-22, 18));
        flor.render(im);
        disco(im, 150, 205, 14, 0xFF7A1F10, 0xFFB5341D);                      // la boca de la flor
        // pájaro: ala trasera, cola, cuerpo, ala delantera
        int ALA = 0xB0C5DCD8, ALA2 = 0xC08FB3B0;
        ala(372, 352, 34, 640, 270, 3, 120, 30, ALA2).render(im);
        for (double[] f : new double[][] { { 470, 478, 520, 548, 8 }, { 462, 480, 468, 556, 8 }, { 466, 478, 560, 520, 8 } })
            ala(f[0], f[1], 10, f[2], f[3], 3.5, 8, 30, 0xFF1F6F57).render(im);
        double[][] cuerpo = { { 208, 238, 1.5 }, { 262, 262, 2.8 }, { 296, 286, 20 }, { 334, 326, 28 }, { 386, 384, 38 }, { 432, 434, 30 }, { 466, 474, 12 } };
        Composicion c = cadena(cuerpo, 0xFF3BB37A, 0xFF1A1A1A, 0xFF1F6F57, 0xFFE8E4D2, 0xFF2E9E6B, 0.42);
        c.render(im);
        // garganta rubí: otro trazo sobre el lado del vientre de la cabeza
        double[][] garg = { { 292, 298, 6 }, { 312, 326, 14 }, { 332, 352, 10 } };
        cadena(garg, 0xFFD81B60, 0xFFD81B60, 0xFFAD1457, 0xFFAD1457, 0xFFE91E63, 0.5).render(im);
        ala(372, 352, 40, 560, 140, 3, 60, -70, ALA).render(im);
        disco(im, 293, 281, 4.2, 0xFF111111, 0xFF000000);
        disco(im, 294.5, 279.5, 1.3, 0xFFFFFFFF, 0xFFFFFFFF);
        firma(540, 470, 0.9, 5.5).render(im);
        ImageIO.write(im, "png", new File("colibri.png"));
        System.out.printf("render: %.1f s%n", (System.nanoTime() - t0) / 1e9);
    }
}
