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

    /**
     * Qué conectores dejan de ser el arco de elipse doblado y pasan a ser una spline por P1, el contacto de la envolvente con el
     * círculo medio y P2, calculada en el plano (sin doblar, así que no se pliega). Exterior/interior se refieren al giro del camino.
     */
    enum Relajo { NINGUNO, EXTERIOR, INTERIOR, AMBOS }

    /** Con giros menores que esto el relajo se atenúa (se conservan los arcos elípticos con el camino recto); es el giro total en radianes. */
    static final double GIRO_RELAJO = Math.toRadians(20);

    final double r1, r2;
    private final Silueta recta;
    private final double largo;
    private final double[] s, cx, cy, tx, ty, k;    // tabla del camino por longitud de arco
    private final double pliegue, giro;
    private final Relajo relajo;

    private SiluetaCurva(Point2D p0, Point2D h1, Point2D h2, Point2D p3, double r1, double r2, Silueta.Conector conector, Relajo relajo) {
        this.relajo = relajo;
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
        double g = 0;
        for (int i = 1; i < n; i++) g += 0.5 * (k[i] + k[i - 1]) * (s[i] - s[i - 1]);
        this.giro = g;                                      // giro total del camino (con signo): > 0 si el lado +y es el exterior
    }

    /** Camino cúbico de p0 a p3 con tiradores h1 y h2 (puntos de control absolutos). */
    static SiluetaCurva de(Point2D p0, Point2D h1, Point2D h2, Point2D p3, double r1, double r2) {
        return de(p0, h1, h2, p3, r1, r2, Silueta.Conector.ELIPSE);
    }

    static SiluetaCurva de(Point2D p0, Point2D h1, Point2D h2, Point2D p3, double r1, double r2, Silueta.Conector conector) {
        return new SiluetaCurva(p0, h1, h2, p3, r1, r2, conector, Relajo.NINGUNO);
    }

    static SiluetaCurva de(Point2D p0, Point2D h1, Point2D h2, Point2D p3, double r1, double r2, Silueta.Conector conector, Relajo relajo) {
        return new SiluetaCurva(p0, h1, h2, p3, r1, r2, conector, relajo);
    }

    /** Giro total del camino en radianes, con signo (> 0: el lado +y de la silueta, hacia N, es el exterior de la curva). */
    double giro() { return giro; }

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
     * La silueta doblada: cada tramo de la silueta recta se parte en trozos y cada trozo se lleva como cúbica de
     * Hermite con los extremos y las tangentes transformadas (G1 con el doblado exacto en los extremos de cada trozo).
     * Los conectores relajados (véase {@link Relajo}) se sustituyen por una spline en el plano.
     */
    Path2D.Double forma() {
        // 1. los segmentos locales, repartidos en cuatro etapas: conector +y (P1→P3), tapa 2, conector -y (P4→P2), tapa 1
        java.util.List<double[]> seg = new java.util.ArrayList<>();            // {tipo, x0, y0, c...}
        double[] c = new double[6];
        double px = 0, py = 0, mx = 0, my = 0;
        for (PathIterator it = recta.formaLocal().getPathIterator(null); !it.isDone(); it.next()) {
            int t = it.currentSegment(c);
            switch (t) {
                case PathIterator.SEG_MOVETO -> { px = mx = c[0]; py = my = c[1]; }
                case PathIterator.SEG_LINETO -> { seg.add(new double[] { 1, px, py, c[0], c[1] }); px = c[0]; py = c[1]; }
                case PathIterator.SEG_CUBICTO -> { seg.add(new double[] { 3, px, py, c[0], c[1], c[2], c[3], c[4], c[5] }); px = c[4]; py = c[5]; }
                case PathIterator.SEG_CLOSE -> { if (Math.hypot(px - mx, py - my) > 1e-12 * (r1 + r2)) seg.add(new double[] { 1, px, py, mx, my }); }
                default -> { }
            }
        }
        Point2D.Double[] e = recta.extremosLocales();           // P1, P2 (tapa 1, +y y -y), P3, P4 (tapa 2)
        double tol = 1e-9 * (r1 + r2);
        double[][] fin = { { e[2].x, e[2].y }, { e[3].x, e[3].y }, { e[1].x, e[1].y } };   // donde acaban las etapas 0, 1 y 2
        java.util.List<java.util.List<double[]>> et = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) et.add(new java.util.ArrayList<>());
        int cur = 0;
        for (double[] g : seg) {
            et.get(cur).add(g);
            double[] fn = g[0] == 1 ? new double[] { g[3], g[4] } : new double[] { g[7], g[8] };
            if (cur < 3 && Math.hypot(fn[0] - fin[cur][0], fn[1] - fin[cur][1]) < tol) cur++;
        }
        // 2. qué lados se relajan
        boolean extMas = giro >= 0;                              // el lado +y es el exterior
        boolean relajaMas = relajo == Relajo.AMBOS || (relajo == Relajo.EXTERIOR && extMas) || (relajo == Relajo.INTERIOR && !extMas);
        boolean relajaMenos = relajo == Relajo.AMBOS || (relajo == Relajo.EXTERIOR && !extMas) || (relajo == Relajo.INTERIOR && extMas);
        double paso = 0.04 * (r1 + r2);
        Path2D.Double out = new Path2D.Double();
        double[] q0 = mapea(e[0].x, e[0].y);
        out.moveTo(q0[0], q0[1]);
        for (int i = 0; i < 4; i++) {
            boolean relaja = (i == 0 && relajaMas) || (i == 2 && relajaMenos);
            if (relaja) anadeSpline(out, et.get(i), i == 0 ? +1 : -1);
            else for (double[] g : et.get(i)) {
                if (g[0] == 1) trozos(out, g[1], g[2], g[1] + (g[3] - g[1]) / 3, g[2] + (g[4] - g[2]) / 3, g[1] + 2 * (g[3] - g[1]) / 3, g[2] + 2 * (g[4] - g[2]) / 3, g[3], g[4], paso);
                else trozos(out, g[1], g[2], g[3], g[4], g[5], g[6], g[7], g[8], paso);
            }
        }
        out.closePath();
        return out;
    }

    /** Dirección de salida y de llegada (vectores locales sin normalizar) de una etapa de segmentos. */
    private static double[] direcciones(java.util.List<double[]> et) {
        double[] a = et.get(0), z = et.get(et.size() - 1);
        double sx, sy, ex, ey;
        if (a[0] == 1) { sx = a[3] - a[1]; sy = a[4] - a[2]; }
        else { sx = a[3] - a[1]; sy = a[4] - a[2]; if (Math.hypot(sx, sy) < 1e-12) { sx = a[5] - a[1]; sy = a[6] - a[2]; } }
        if (z[0] == 1) { ex = z[3] - z[1]; ey = z[4] - z[2]; }
        else { ex = z[7] - z[5]; ey = z[8] - z[6]; if (Math.hypot(ex, ey) < 1e-12) { ex = z[7] - z[3]; ey = z[8] - z[4]; } }
        return new double[] { sx, sy, ex, ey };
    }

    /**
     * Sustituye la etapa (el conector del lado {@code lado} = ±1) por una spline en el plano: dos cúbicas de Hermite por P (en su
     * extremo de la tapa 1), el punto medio M y el otro extremo, con las tangentes de la silueta doblada en los extremos y, en M, la de la
     * envolvente. M es el contacto de la envolvente con el círculo medio, c(L/2) + rm·(-r'·T + lado·√(1-r'²)·N), mezclado con el punto de
     * la silueta doblada según el giro del camino (con el camino recto sigue el arco elíptico).
     */
    private void anadeSpline(Path2D.Double out, java.util.List<double[]> etapa, int lado) {
        double[] dir = direcciones(etapa);
        double[] a = etapa.get(0), z = etapa.get(etapa.size() - 1);
        double ax = a[1], ay = a[2], zx = z[0] == 1 ? z[3] : z[7], zy = z[0] == 1 ? z[4] : z[8];
        boolean atras = lado < 0;                                // la etapa -y se recorre de la tapa 2 a la tapa 1
        // extremos y tangentes de la silueta doblada, en el sentido de la etapa
        double[] qa = mapea(ax, ay), qz = mapea(zx, zy);
        double[] ta = unit(jac(ax, ay, dir[0], dir[1])), tz = unit(jac(zx, zy, dir[2], dir[3]));
        // contacto de la envolvente con el círculo medio
        double sm = largo / 2, rm = (r1 + r2) / 2, drds = (r2 - r1) / largo;
        double kk = Math.sqrt(Math.max(0, 1 - drds * drds));
        double[] e = estado(sm);
        double nx = e[3], ny = -e[2];
        double mex = e[0] + rm * (-drds * e[2] + lado * kk * nx), mey = e[1] + rm * (-drds * e[3] + lado * kk * ny);
        double tex = kk * e[2] + lado * drds * nx, tey = kk * e[3] + lado * drds * ny;
        if (atras) { tex = -tex; tey = -tey; }
        // el punto de la silueta doblada con x = L/2 (aplanando la etapa local)
        double mbx = mex, mby = mey, tbx = tex, tby = tey;
        Path2D.Double loc = new Path2D.Double();
        loc.moveTo(ax, ay);
        for (double[] g : etapa) { if (g[0] == 1) loc.lineTo(g[3], g[4]); else loc.curveTo(g[3], g[4], g[5], g[6], g[7], g[8]); }
        double[] cc = new double[6];
        double lx = ax, ly = ay;
        for (PathIterator it = loc.getPathIterator(null, 1e-3 * (r1 + r2)); !it.isDone(); it.next()) {
            if (it.currentSegment(cc) != PathIterator.SEG_LINETO) continue;
            if ((lx - sm) * (cc[0] - sm) <= 0 && cc[0] != lx) {
                double f = (sm - lx) / (cc[0] - lx), px = lx + f * (cc[0] - lx), py = ly + f * (cc[1] - ly);
                double[] w = mapea(px, py), tt = unit(jac(px, py, cc[0] - lx, cc[1] - ly));
                mbx = w[0]; mby = w[1]; tbx = tt[0]; tby = tt[1];
                break;
            }
            lx = cc[0]; ly = cc[1];
        }
        double s0 = Math.min(1, Math.abs(giro) / GIRO_RELAJO), rho = s0 * s0 * (3 - 2 * s0);
        double mxw = (1 - rho) * mbx + rho * mex, myw = (1 - rho) * mby + rho * mey;
        double[] tm = unit(new double[] { (1 - rho) * tbx + rho * tex, (1 - rho) * tby + rho * tey });
        double[] pa = qa, pz = qz;
        // spline de Hermite pa → M → pz
        double d1 = Math.hypot(mxw - pa[0], myw - pa[1]), d2 = Math.hypot(pz[0] - mxw, pz[1] - myw);
        out.curveTo(pa[0] + ta[0] * d1 / 3, pa[1] + ta[1] * d1 / 3, mxw - tm[0] * d1 / 3, myw - tm[1] * d1 / 3, mxw, myw);
        out.curveTo(mxw + tm[0] * d2 / 3, myw + tm[1] * d2 / 3, pz[0] - tz[0] * d2 / 3, pz[1] - tz[1] * d2 / 3, pz[0], pz[1]);
    }

    private static double[] unit(double[] v) {
        double n = Math.hypot(v[0], v[1]);
        return n > 0 ? new double[] { v[0] / n, v[1] / n } : v;
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
