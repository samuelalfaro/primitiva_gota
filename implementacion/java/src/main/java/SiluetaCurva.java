import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;

/**
 * Silueta de una gota cuyo camino entre centros es una Bézier cúbica: se resuelve la silueta RECTA ({@link Silueta}) con la
 * distancia entre centros igual a la longitud de arco L del camino, y se dobla a lo largo del camino:
 * (x, y) → c(x) + y·N(x), con x la longitud de arco y N = (Ty, -Tx) la normal de las coordenadas de imagen (la misma de
 * {@code Silueta}). Fuera de [0, L] (las tapas) se usa la tangente del extremo, sin doblar.
 *
 * <p>Por qué así: con giro, la silueta de cada lado por separado (tangentes locales) pierde la tapa interior y no coincide con la
 * unión de círculos del camino en d = r1 + r2 (hasta un 14 % del área con 90° de giro); doblada, la diferencia en δ = L/(r1+r2) = 0,99
 * es del 0,4 al 3 % con giros de hasta 120° (estudio en Python, sección 11.1 del documento de formulación). Además los
 * cinco ejes (los cuatro radios libres y el camino) quedan en coordenadas (s, y), donde viven las funciones 1D.
 *
 * <p>Límites: el contorno se pliega en el lado interior cuando 1 + k(x)·y <= 0, con k la curvatura del camino (semianchura mayor que
 * el radio de curvatura); {@link #pliegue()} lo mide y {@link #plegada()} lo avisa. Donde se pliega, la silueta no es válida y manda la
 * unión de círculos. Los puntos P1..P4 ya no están exactamente sobre los círculos de las tapas cuando caen dentro de [0, L]
 * (ψ mayor de 90°): el doblado los desplaza algo. Con d >= r1 + r2 la silueta recta es la envolvente convexa, que doblada es
 * solo una aproximación de la envolvente de la gota. La unicidad y las demás propiedades de los ángulos son las de {@code Silueta}.
 */
final class SiluetaCurva {
    private static final int N_MUESTRAS = 2048;

    final double r1, r2;
    private final Silueta recta;
    private final double largo;
    private final double[] s, cx, cy, tx, ty, k;    // tabla del camino por longitud de arco
    private final double pliegue;

    private SiluetaCurva(Point2D p0, Point2D h1, Point2D h2, Point2D p3, double r1, double r2, Silueta.Conector conector) {
        if (!(r1 > 0 && r2 > 0)) throw new IllegalArgumentException("los radios deben ser positivos");
        this.r1 = r1;
        this.r2 = r2;
        int n = N_MUESTRAS + 1;
        s = new double[n]; cx = new double[n]; cy = new double[n]; tx = new double[n]; ty = new double[n]; k = new double[n];
        double[] ax = { p0.getX(), h1.getX(), h2.getX(), p3.getX() }, ay = { p0.getY(), h1.getY(), h2.getY(), p3.getY() };
        double[] dx = new double[n], dy = new double[n], ddx = new double[n], ddy = new double[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / (n - 1), u = 1 - t;
            cx[i] = u * u * u * ax[0] + 3 * u * u * t * ax[1] + 3 * u * t * t * ax[2] + t * t * t * ax[3];
            cy[i] = u * u * u * ay[0] + 3 * u * u * t * ay[1] + 3 * u * t * t * ay[2] + t * t * t * ay[3];
            dx[i] = 3 * (u * u * (ax[1] - ax[0]) + 2 * u * t * (ax[2] - ax[1]) + t * t * (ax[3] - ax[2]));
            dy[i] = 3 * (u * u * (ay[1] - ay[0]) + 2 * u * t * (ay[2] - ay[1]) + t * t * (ay[3] - ay[2]));
            ddx[i] = 6 * (u * (ax[2] - 2 * ax[1] + ax[0]) + t * (ax[3] - 2 * ax[2] + ax[1]));
            ddy[i] = 6 * (u * (ay[2] - 2 * ay[1] + ay[0]) + t * (ay[3] - 2 * ay[2] + ay[1]));
            if (i > 0) s[i] = s[i - 1] + Math.hypot(cx[i] - cx[i - 1], cy[i] - cy[i - 1]);
        }
        largo = s[n - 1];
        double tx0 = 1, ty0 = 0;
        for (int i = 0; i < n; i++) {                       // la tangente de los extremos degenerados se toma del primer punto con velocidad
            double v = Math.hypot(dx[i], dy[i]);
            if (v > 1e-12 * Math.max(largo, 1e-300)) { tx0 = dx[i] / v; ty0 = dy[i] / v; break; }
        }
        double txp = tx0, typ = ty0;
        for (int i = 0; i < n; i++) {
            double v = Math.hypot(dx[i], dy[i]);
            if (v > 1e-12 * Math.max(largo, 1e-300)) { txp = dx[i] / v; typ = dy[i] / v; }
            tx[i] = txp;
            ty[i] = typ;
            // dT/ds = (c'' - (c''·T) T)/|c'|²; con N = (Ty, -Tx): k = -N·dT/ds
            if (v > 1e-12 * Math.max(largo, 1e-300)) {
                double dd = ddx[i] * tx[i] + ddy[i] * ty[i];
                double dtx = (ddx[i] - dd * tx[i]) / (v * v), dty = (ddy[i] - dd * ty[i]) / (v * v);
                k[i] = -(ty[i] * dtx - tx[i] * dty);
            }
        }
        this.recta = Silueta.de(new Point2D.Double(0, 0), r1, new Point2D.Double(largo, 0), r2, 1, 0, conector);
        double mf = 1;
        double[] c = new double[6];
        for (PathIterator it = recta.formaLocal().getPathIterator(null, 1e-3 * (r1 + r2)); !it.isDone(); it.next()) {
            int t = it.currentSegment(c);
            if (t == PathIterator.SEG_CLOSE) continue;
            double x = c[0], y = c[1];
            if (x > 0 && x < largo) mf = Math.min(mf, 1 + curvatura(x) * y);
        }
        this.pliegue = mf;
    }

    /** Camino cúbico de p0 a p3 con tiradores h1 y h2 (puntos de control absolutos). */
    static SiluetaCurva de(Point2D p0, Point2D h1, Point2D h2, Point2D p3, double r1, double r2) {
        return de(p0, h1, h2, p3, r1, r2, Silueta.Conector.ELIPSE);
    }

    static SiluetaCurva de(Point2D p0, Point2D h1, Point2D h2, Point2D p3, double r1, double r2, Silueta.Conector conector) {
        return new SiluetaCurva(p0, h1, h2, p3, r1, r2, conector);
    }

    /** Longitud de arco del camino. */
    double longitud() { return largo; }

    /** La silueta recta (distancia entre centros = longitud del camino), en su marco local. */
    Silueta recta() { return recta; }

    /** Mínimo de 1 + k(x)·y sobre el contorno recto con x en (0, L); con <= 0 el contorno doblado se pliega. Vale 1 si no hay giro. */
    double pliegue() { return pliegue; }

    boolean plegada() { return pliegue <= 0; }

    // ---------- el camino ----------

    /** Índice y fracción de la tabla para una longitud de arco (acotada a [0, L]). */
    private int busca(double x) {
        int lo = 0, hi = s.length - 1;
        while (hi - lo > 1) {
            int m = (lo + hi) >>> 1;
            if (s[m] <= x) lo = m; else hi = m;
        }
        return lo;
    }

    private double interp(double[] v, double x) {
        int i = busca(x);
        double d = s[i + 1] - s[i];
        double f = d > 0 ? (x - s[i]) / d : 0;
        return v[i] + f * (v[i + 1] - v[i]);
    }

    private double curvatura(double x) { return interp(k, Math.min(Math.max(x, 0), largo)); }

    /** {c(x), T(x), k(x)} en (x acotada a [0, L]): {cx, cy, tx, ty, k}. */
    private double[] estado(double x) {
        double xx = Math.min(Math.max(x, 0), largo);
        double tX = interp(tx, xx), tY = interp(ty, xx), n = Math.hypot(tX, tY);
        return new double[] { interp(cx, xx), interp(cy, xx), tX / n, tY / n, x > 0 && x < largo ? interp(k, xx) : 0, xx };
    }

    /** El punto local (x, y) llevado al plano. */
    private double[] mapea(double x, double y) {
        double[] e = estado(x);
        double nx = e[3], ny = -e[2];
        return new double[] { e[0] + (x - e[5]) * e[2] + y * nx, e[1] + (x - e[5]) * e[3] + y * ny };
    }

    /** El vector local (dx, dy) en (x, y) llevado al plano (jacobiano del doblado). */
    private double[] jac(double x, double y, double dX, double dY) {
        double[] e = estado(x);
        double f = 1 + e[4] * y, nx = e[3], ny = -e[2];
        return new double[] { e[2] * f * dX + nx * dY, e[3] * f * dX + ny * dY };
    }

    // ---------- la forma ----------

    /** Los extremos P1..P4 de {@link Silueta#extremos()}, doblados. */
    Point2D.Double[] extremos() {
        Point2D.Double[] l = recta.extremosLocales();
        Point2D.Double[] out = new Point2D.Double[4];
        for (int i = 0; i < 4; i++) {
            double[] q = mapea(l[i].x, l[i].y);
            out[i] = new Point2D.Double(q[0], q[1]);
        }
        return out;
    }

    /**
     * La silueta doblada: cada tramo de la silueta recta se parte en trozos pequeños y cada trozo se lleva como cúbica de
     * Hermite con los extremos y las tangentes transformadas (G1 con el doblado exacto en los extremos de cada trozo).
     */
    Path2D.Double forma() {
        Path2D.Double local = recta.formaLocal(), out = new Path2D.Double();
        double[] c = new double[6];
        double px = 0, py = 0, mx = 0, my = 0;
        double paso = 0.04 * (r1 + r2);
        for (PathIterator it = local.getPathIterator(null); !it.isDone(); it.next()) {
            int t = it.currentSegment(c);
            switch (t) {
                case PathIterator.SEG_MOVETO -> {
                    px = mx = c[0];
                    py = my = c[1];
                    double[] q = mapea(px, py);
                    out.moveTo(q[0], q[1]);
                }
                case PathIterator.SEG_LINETO -> {
                    trozos(out, px, py, px + (c[0] - px) / 3, py + (c[1] - py) / 3, px + 2 * (c[0] - px) / 3, py + 2 * (c[1] - py) / 3, c[0], c[1], paso);
                    px = c[0];
                    py = c[1];
                }
                case PathIterator.SEG_CUBICTO -> {
                    trozos(out, px, py, c[0], c[1], c[2], c[3], c[4], c[5], paso);
                    px = c[4];
                    py = c[5];
                }
                case PathIterator.SEG_CLOSE -> {
                    if (Math.hypot(px - mx, py - my) > 1e-12 * (r1 + r2))
                        trozos(out, px, py, px + (mx - px) / 3, py + (my - py) / 3, px + 2 * (mx - px) / 3, py + 2 * (my - py) / 3, mx, my, paso);
                    out.closePath();
                }
                default -> { }
            }
        }
        return out;
    }

    /** Parte la cúbica local (a, b, c, d) en trozos de longitud de control <= paso y añade cada uno doblado, como cúbica de Hermite. */
    private void trozos(Path2D.Double out, double ax, double ay, double bx, double by, double cx2, double cy2, double dx, double dy, double paso) {
        double largoCtl = Math.hypot(bx - ax, by - ay) + Math.hypot(cx2 - bx, cy2 - by) + Math.hypot(dx - cx2, dy - cy2);
        int n = Math.max(2, (int) Math.ceil(largoCtl / paso));
        double[] prev = null;
        for (int i = 0; i < n; i++) {
            double u0 = (double) i / n, u1 = (double) (i + 1) / n, du = u1 - u0;
            double[] p0 = cubica(ax, ay, bx, by, cx2, cy2, dx, dy, u0), p1 = cubica(ax, ay, bx, by, cx2, cy2, dx, dy, u1);
            double[] v0 = derivada(ax, ay, bx, by, cx2, cy2, dx, dy, u0), v1 = derivada(ax, ay, bx, by, cx2, cy2, dx, dy, u1);
            double[] q0 = prev != null ? prev : mapea(p0[0], p0[1]);
            double[] q1 = mapea(p1[0], p1[1]);
            double[] w0 = jac(p0[0], p0[1], v0[0] * du, v0[1] * du), w1 = jac(p1[0], p1[1], v1[0] * du, v1[1] * du);
            out.curveTo(q0[0] + w0[0] / 3, q0[1] + w0[1] / 3, q1[0] - w1[0] / 3, q1[1] - w1[1] / 3, q1[0], q1[1]);
            prev = q1;
        }
    }

    private static double[] cubica(double ax, double ay, double bx, double by, double cx, double cy, double dx, double dy, double t) {
        double u = 1 - t, a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, d = t * t * t;
        return new double[] { a * ax + b * bx + c * cx + d * dx, a * ay + b * by + c * cy + d * dy };
    }

    private static double[] derivada(double ax, double ay, double bx, double by, double cx, double cy, double dx, double dy, double t) {
        double u = 1 - t;
        return new double[] { 3 * (u * u * (bx - ax) + 2 * u * t * (cx - bx) + t * t * (dx - cx)),
                              3 * (u * u * (by - ay) + 2 * u * t * (cy - by) + t * t * (dy - cy)) };
    }
}
