import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

/**
 * Silueta de una gota de camino recto: dos tapas circulares con su radio, unidas por dos conectores (uno por lado), con la
 * continuidad G1 en los empalmes. El conector es, por defecto, un arco de elipse de mínima excentricidad (círculo exacto si
 * existe un círculo tangente en los dos extremos, como con radios iguales); si con unos ángulos no existe elipse (las
 * tangentes no se cortan hacia delante) esos ángulos no son admisibles y los puntos de unión se mueven; el biarco es solo el
 * respaldo si no hubiera ninguna pareja admisible, o se elige con {@link Conector#BIARCO}. La formulación completa (incógnitas, energía y evidencia numérica) está en
 * {@code gota_formulacion_biarcos.md}; aquí solo lo imprescindible.
 *
 * <p>Marco local: C1 en el origen, T = (c2 - c1)/d (o la tangente explícita, o (1, 0) si los centros coinciden) y N = T girada
 * +90° en el plano matemático (en coordenadas de imagen, N = (Ty, -Tx): hacia arriba en pantalla con T = (1, 0)). La tapa 1
 * mira hacia -T y la 2 hacia +T. Los extremos de los arcos quedan a los ángulos psi1 y psi2 de la dirección de su tapa; los
 * radios que llegan a ellos son perpendiculares a la silueta.
 *
 * <p>Los ángulos son el mínimo de F = rm·E(psi1, psi2) + lambda·(psi1² + psi2²): E es la energía de flexión (integral de
 * curvatura² por longitud de arco) del conector que une los extremos, rm = (r1 + r2)/2, y lambda = (2/pi)·max(0, 1 - d/(r1 + r2)) es la
 * afinidad de los radios libres con la tangente de su centro. Con d >= r1 + r2 no hay afinidad y los ángulos son los del
 * contacto de la tangente común (la silueta de siempre: dos arcos y dos rectas).
 *
 * <p>La unicidad del mínimo no está demostrada; ver el documento. Los radios deben ser positivos.
 */
final class Silueta {
    /** Peso de la afinidad con d = 0: con dos círculos idénticos da psi = pi/4 (la X). */
    static final double LAMBDA0 = 2 / Math.PI;
    private static final double PSI_MIN = 0.02, INF = 1e9;

    /** Conector entre las tapas. RECTA solo aparece como resultado (con d >= r1 + r2, la envolvente convexa); no se puede pedir. */
    enum Conector { ELIPSE, BIARCO, RECTA }

    /** Semiángulos de las tapas, en radianes, desde la dirección hacia la que mira cada una, y el conector con el que se obtuvieron. */
    record Angulos(double psi1, double psi2, Conector conector) {}

    final Point2D.Double c1, c2;
    final double r1, r2, d, tx, ty;
    private final Angulos angulos;
    /** Elipse conectora superior {ax, ay, bx, by, w, xx, xy} (marco local); null si el conector no es ELIPSE. */
    private final double[] elipseSup;
    private final double xP1, xP3, yP1, yP3;
    private Path2D.Double formaCache;                  // solo para el respaldo BIARCO de contiene()

    private Silueta(Point2D c1, double r1, Point2D c2, double r2, double tx, double ty, double d, Angulos a) {
        this.c1 = new Point2D.Double(c1.getX(), c1.getY());
        this.c2 = new Point2D.Double(c2.getX(), c2.getY());
        this.r1 = r1;
        this.r2 = r2;
        this.d = d;
        this.tx = tx;
        this.ty = ty;
        this.angulos = a;
        double p1 = a.psi1(), p2 = a.psi2();
        this.xP1 = -r1 * Math.cos(p1);
        this.yP1 = r1 * Math.sin(p1);
        this.xP3 = d + r2 * Math.cos(p2);
        this.yP3 = r2 * Math.sin(p2);
        double[] el = a.conector() == Conector.ELIPSE
                ? elipse(xP1, yP1, Math.sin(p1), Math.cos(p1), xP3, yP3, Math.sin(p2), -Math.cos(p2)) : null;
        this.elipseSup = el == null ? null : new double[] { xP1, yP1, xP3, yP3, el[0], el[2], el[3] };
    }

    /** Con la tangente de la cuerda c1→c2 o, si los centros coinciden, el eje x. */
    static Silueta de(Point2D c1, double r1, Point2D c2, double r2) {
        return de(c1, r1, c2, r2, 1, 0);
    }

    /** Como {@link #de(Point2D, double, Point2D, double)}, con una tangente explícita (se normaliza) para cuando los centros coinciden. */
    static Silueta de(Point2D c1, double r1, Point2D c2, double r2, double tx, double ty) {
        return de(c1, r1, c2, r2, tx, ty, Conector.ELIPSE);
    }

    /** Como {@link #de(Point2D, double, Point2D, double, double, double)}, eligiendo el conector (ELIPSE con respaldo de biarco, o BIARCO). */
    static Silueta de(Point2D c1, double r1, Point2D c2, double r2, double tx, double ty, Conector conector) {
        if (conector == Conector.RECTA) throw new IllegalArgumentException("RECTA no se puede pedir");
        if (!(r1 > 0 && r2 > 0)) throw new IllegalArgumentException("los radios deben ser positivos");
        double dx = c2.getX() - c1.getX(), dy = c2.getY() - c1.getY(), d = Math.hypot(dx, dy);
        double ex = tx, ey = ty;
        if (d > 1e-12 * (r1 + r2)) {
            ex = dx / d;
            ey = dy / d;
        } else {
            double n = Math.hypot(tx, ty);
            if (!(n > 0)) { ex = 1; ey = 0; } else { ex /= n; ey /= n; }
            d = 0;
        }
        return new Silueta(c1, r1, c2, r2, ex, ey, d, angulos(r1, r2, d, conector));
    }

    Angulos angulos() { return angulos; }

    /** El conector realmente usado: ELIPSE, BIARCO (pedido o de respaldo) o RECTA (con d >= r1 + r2). */
    Conector conector() { return angulos.conector(); }

    // ---------- los ángulos ----------

    /** Los ángulos con el conector por defecto (elipse). */
    static Angulos angulos(double r1, double r2, double d) { return angulos(r1, r2, d, Conector.ELIPSE); }

    /** Los ángulos de las tapas para unos radios y una distancia entre centros (no depende de la orientación ni de la escala). */
    static Angulos angulos(double r1, double r2, double d, Conector pedido) {
        double delta = d / (r1 + r2);
        if (delta >= 1) {
            double th = Math.acos((r1 - r2) / d);
            return new Angulos(Math.PI - th, th, Conector.RECTA);
        }
        if (pedido == Conector.ELIPSE) {
            double[] m = resuelve(r1, r2, d, true);
            if (m != null) return new Angulos(m[0], m[1], Conector.ELIPSE);
        }
        double[] m = resuelve(r1, r2, d, false);
        if (m == null) throw new IllegalStateException("sin solución admisible: r1 = " + r1 + ", r2 = " + r2 + ", d = " + d);
        return new Angulos(m[0], m[1], Conector.BIARCO);
    }

    /** Mínimo de F con el conector indicado (rejilla 7×7 + arranques especiales y Nelder-Mead desde los 8 mejores); null si ninguna pareja es admisible. */
    private static double[] resuelve(double r1, double r2, double d, boolean el) {
        double delta = d / (r1 + r2);
        double lam = LAMBDA0 * (1 - delta), rm = (r1 + r2) / 2;
        double lo = 0.15, hi = Math.PI - 0.15;
        int n = 7;
        double[][] cand = new double[n * n + 2][3];
        int k = 0;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                double a = lo + (hi - lo) * i / (n - 1), b = lo + (hi - lo) * j / (n - 1);
                cand[k++] = new double[] { a, b, f(a, b, r1, r2, d, lam, rm, el) };
            }
        cand[k++] = new double[] { Math.PI / 4, Math.PI / 4, f(Math.PI / 4, Math.PI / 4, r1, r2, d, lam, rm, el) };
        double kk = (r1 - r2) / d;
        double th = Math.abs(kk) < 1 ? Math.acos(kk) : Math.PI / 4;
        cand[k++] = new double[] { Math.PI - th, th, f(Math.PI - th, th, r1, r2, d, lam, rm, el) };
        java.util.Arrays.sort(cand, (u, v) -> Double.compare(u[2], v[2]));
        double[] mejor = null;
        for (int i = 0; i < 8 && i < cand.length; i++) {
            if (cand[i][2] >= INF) break;
            double[] s = minimiza(cand[i][0], cand[i][1], r1, r2, d, lam, rm, el);
            if (mejor == null || s[2] < mejor[2]) mejor = s;
        }
        return mejor;
    }

    /** Nelder-Mead sobre (psi1, psi2). Devuelve {psi1, psi2, F}. */
    private static double[] minimiza(double a0, double b0, double r1, double r2, double d, double lam, double rm, boolean el) {
        double[][] x = { { a0, b0 }, { a0 + 0.1, b0 }, { a0, b0 + 0.1 } };
        double[] fx = new double[3];
        for (int i = 0; i < 3; i++) fx[i] = f(x[i][0], x[i][1], r1, r2, d, lam, rm, el);
        for (int it = 0; it < 600; it++) {
            int ia = 0, ib = 1, ic = 2;      // mejor, intermedio, peor
            if (fx[ia] > fx[ib]) { int t = ia; ia = ib; ib = t; }
            if (fx[ib] > fx[ic]) { int t = ib; ib = ic; ic = t; }
            if (fx[ia] > fx[ib]) { int t = ia; ia = ib; ib = t; }
            double tam = Math.max(Math.abs(x[ia][0] - x[ic][0]) + Math.abs(x[ia][1] - x[ic][1]),
                                  Math.abs(x[ia][0] - x[ib][0]) + Math.abs(x[ia][1] - x[ib][1]));
            if (tam < 1e-11) break;
            double mx = (x[ia][0] + x[ib][0]) / 2, my = (x[ia][1] + x[ib][1]) / 2;
            double rx = mx + (mx - x[ic][0]), ry = my + (my - x[ic][1]), fr = f(rx, ry, r1, r2, d, lam, rm, el);
            if (fr < fx[ia]) {
                double ex = mx + 2 * (mx - x[ic][0]), ey = my + 2 * (my - x[ic][1]), fe = f(ex, ey, r1, r2, d, lam, rm, el);
                if (fe < fr) { x[ic] = new double[] { ex, ey }; fx[ic] = fe; } else { x[ic] = new double[] { rx, ry }; fx[ic] = fr; }
            } else if (fr < fx[ib]) {
                x[ic] = new double[] { rx, ry };
                fx[ic] = fr;
            } else {
                double cx, cy;
                if (fr < fx[ic]) { cx = mx + 0.5 * (rx - mx); cy = my + 0.5 * (ry - my); }
                else { cx = mx + 0.5 * (x[ic][0] - mx); cy = my + 0.5 * (x[ic][1] - my); }
                double fc = f(cx, cy, r1, r2, d, lam, rm, el);
                if (fc < Math.min(fr, fx[ic])) { x[ic] = new double[] { cx, cy }; fx[ic] = fc; }
                else {
                    for (int i : new int[] { ib, ic }) {
                        x[i] = new double[] { x[ia][0] + 0.5 * (x[i][0] - x[ia][0]), x[ia][1] + 0.5 * (x[i][1] - x[ia][1]) };
                        fx[i] = f(x[i][0], x[i][1], r1, r2, d, lam, rm, el);
                    }
                }
            }
        }
        int m = 0;
        for (int i = 1; i < 3; i++) if (fx[i] < fx[m]) m = i;
        return new double[] { x[m][0], x[m][1], fx[m] };
    }

    /** F = rm·E + lambda·(psi1² + psi2²), con un muro fuera de los topes y de la zona admisible. */
    private static double f(double p1, double p2, double r1, double r2, double d, double lam, double rm, boolean el) {
        double lo = PSI_MIN, hi = Math.PI - PSI_MIN;
        double fuera = Math.max(0, lo - p1) + Math.max(0, p1 - hi) + Math.max(0, lo - p2) + Math.max(0, p2 - hi);
        if (fuera > 0) return INF + fuera;
        double e = el ? energiaElipse(p1, p2, r1, r2, d) : energia(p1, p2, r1, r2, d);
        if (e >= INF) return INF;
        return rm * e + lam * (p1 * p1 + p2 * p2);
    }

    // ---------- el biarco ----------

    /** Biarco simétrico entre P0 (tangente t0) y P1 (tangente t1): {Jx, Jy, k1, L1, k2, L2}; k2 es la curvatura del segundo arco recorrido desde P1 hacia J. */
    private static double[] biarco(double ax, double ay, double t0x, double t0y, double bx, double by, double t1x, double t1y) {
        double vx = bx - ax, vy = by - ay, vv = vx * vx + vy * vy;
        if (vv < 1e-24) return null;
        double tx = t0x + t1x, ty = t0y + t1y, vt = vx * tx + vy * ty, a = 4 - (tx * tx + ty * ty), s;
        if (Math.abs(a) < 1e-12) {
            if (Math.abs(vt) < 1e-9 * Math.sqrt(vv)) return null;
            s = vv / (2 * vt);
        } else {
            double disc = vt * vt + a * vv;
            if (disc < 0) return null;
            s = (-vt + Math.sqrt(disc)) / a;
        }
        if (!(s > 0) || Double.isInfinite(s)) return null;
        double jx = ((ax + s * t0x) + (bx - s * t1x)) / 2, jy = ((ay + s * t0y) + (by - s * t1y)) / 2;
        double[] p = arco(ax, ay, t0x, t0y, jx, jy), q = arco(bx, by, -t1x, -t1y, jx, jy);
        if (p == null || q == null) return null;
        return new double[] { jx, jy, p[0], p[1], q[0], q[1] };
    }

    /** Arco desde P con tangente t hasta Q: {curvatura con signo, longitud}; null si gira casi media vuelta o más. */
    private static double[] arco(double px, double py, double tx, double ty, double qx, double qy) {
        double cx = qx - px, cy = qy - py, l0 = Math.hypot(cx, cy);
        if (l0 < 1e-15) return new double[] { 0, 0 };
        double beta = Math.atan2(tx * cy - ty * cx, tx * cx + ty * cy);
        if (Math.abs(beta) < 1e-9) return new double[] { 0, l0 };
        if (Math.abs(beta) > Math.PI - 1e-6) return null;
        return new double[] { 2 * Math.sin(beta) / l0, l0 * Math.abs(beta) / Math.sin(Math.abs(beta)) };
    }

    /** Energía de flexión del biarco superior: curvatura² por longitud de sus dos arcos. */
    private static double energia(double p1, double p2, double r1, double r2, double d) {
        double c1 = Math.cos(p1), s1 = Math.sin(p1), c2 = Math.cos(p2), s2 = Math.sin(p2);
        double ax = -r1 * c1, ay = r1 * s1, bx = d + r2 * c2, by = r2 * s2;
        if (bx - ax <= 1e-9 * (r1 + r2)) return INF;
        double[] b = biarco(ax, ay, s1, c1, bx, by, s2, -c2);
        return b == null ? INF : b[2] * b[2] * b[3] + b[4] * b[4] * b[5];
    }

    // ---------- la elipse de mínima excentricidad ----------

    private static final int NG = 32;
    private static final double[] GU = new double[NG], GW = new double[NG];   // Gauss-Legendre en [0, 1]
    static {
        for (int i = 0; i < NG; i++) {
            double x = Math.cos(Math.PI * (i + 0.75) / (NG + 0.5)), pp = 1;
            for (int it = 0; it < 100; it++) {
                double p0 = 1, p1 = x;
                for (int j = 2; j <= NG; j++) { double p2 = ((2 * j - 1) * x * p1 - (j - 1) * p0) / j; p0 = p1; p1 = p2; }
                pp = NG * (x * p1 - p0) / (x * x - 1);
                double dx = p1 / pp;
                x -= dx;
                if (Math.abs(dx) < 1e-15) break;
            }
            GU[i] = (1 - x) / 2;
            GW[i] = 1 / ((1 - x * x) * pp * pp);
        }
    }

    /**
     * Elipse de mínima excentricidad tangente en P (tangente t0) y Q (tangente t1) a las rectas dadas: {w, e, Xx, Xy}, con X el
     * cruce de las tangentes y w el peso de la cónica racional cuadrática (1, w, 1) sobre (P, X, Q); null si no existe (las
     * tangentes no se cortan hacia delante). La parte cuadrática de la cónica es A0 + w²·A1 y la excentricidad depende solo de
     * g(s) = (a s² + b s + c)/(m0 + m1 s)², con s = w²; g'(s) = 0 es lineal en s, así que no hay búsqueda. Se acota a w < 1
     * (con w = 1 es una parábola).
     */
    private static double[] elipse(double px, double py, double t0x, double t0y, double qx, double qy, double t1x, double t1y) {
        double det = t0x * t1y - t0y * t1x;
        if (Math.abs(det) < 1e-12) return null;
        double vx = qx - px, vy = qy - py;
        double s1 = (vx * t1y - vy * t1x) / det, s2 = (t0x * vy - t0y * vx) / det;
        if (!(s1 > 0 && s2 > 0)) return null;
        double xx = px + s1 * t0x, xy = py + s1 * t0y;
        double dd = (xx - px) * (qy - py) - (xy - py) * (qx - px);
        if (Math.abs(dd) < 1e-300) return null;
        // gradientes de las coordenadas baricentricas respecto a P, X y Q
        double gpx = (xy - qy) / dd, gpy = (qx - xx) / dd, gxx = (qy - py) / dd, gxy = (px - qx) / dd, gqx = (py - xy) / dd, gqy = (xx - px) / dd;
        double a000 = gxx * gxx, a011 = gxy * gxy, a001 = gxx * gxy;
        double a100 = -4 * gpx * gqx, a111 = -4 * gpy * gqy, a101 = -2 * (gpx * gqy + gqx * gpy);
        double p0 = (a000 - a011) / 2, p1 = (a100 - a111) / 2, q0 = a001, q1 = a101;
        double m0 = (a000 + a011) / 2, m1 = (a100 + a111) / 2;
        double al = p1 * p1 + q1 * q1, be = 2 * (p0 * p1 + q0 * q1), ga = p0 * p0 + q0 * q0;
        double den = 2 * al * m0 - be * m1;
        if (Math.abs(den) < 1e-300) return null;
        double s = (2 * m1 * ga - be * m0) / den;
        s = Math.min(Math.max(s, 1e-4), 0.9998);
        double g = (al * s * s + be * s + ga) / ((m0 + m1 * s) * (m0 + m1 * s)), rg = Math.sqrt(Math.max(g, 0));
        return new double[] { Math.sqrt(s), Math.sqrt(2 * rg / (1 + rg)), xx, xy };
    }

    /** Energía de flexión (integral de curvatura² por longitud de arco) de la cónica racional (1, w, 1) sobre (P, X, Q). */
    private static double energiaConica(double px, double py, double xx, double xy, double qx, double qy, double w) {
        double e = 0;
        for (int i = 0; i < NG; i++) {
            double u = GU[i], a = (1 - u) * (1 - u), b = 2 * w * u * (1 - u), c = u * u, dn = a + b + c;
            double da = -2 * (1 - u), db = 2 * w * (1 - 2 * u), dc = 2 * u, dD = da + db + dc, ddD = 2 - 4 * w + 2;
            double nx = a * px + b * xx + c * qx, ny = a * py + b * xy + c * qy;
            double dnx = da * px + db * xx + dc * qx, dny = da * py + db * xy + dc * qy;
            double ddnx = 2 * px - 4 * w * xx + 2 * qx, ddny = 2 * py - 4 * w * xy + 2 * qy;
            double cx = nx / dn, cy = ny / dn;
            double c1x = (dnx - dD * cx) / dn, c1y = (dny - dD * cy) / dn;
            double c2x = (ddnx - 2 * dD * c1x - ddD * cx) / dn, c2y = (ddny - 2 * dD * c1y - ddD * cy) / dn;
            double cruz = c1x * c2y - c1y * c2x, v = Math.hypot(c1x, c1y);
            e += GW[i] * cruz * cruz / (v * v * v * v * v);
        }
        return e;
    }

    /** Energía de flexión de la elipse que une los extremos superiores; INF si no hay elipse. */
    private static double energiaElipse(double p1, double p2, double r1, double r2, double d) {
        double c1 = Math.cos(p1), s1 = Math.sin(p1), c2 = Math.cos(p2), s2 = Math.sin(p2);
        double ax = -r1 * c1, ay = r1 * s1, bx = d + r2 * c2, by = r2 * s2;
        if (bx - ax <= 1e-9 * (r1 + r2)) return INF;
        double[] el = elipse(ax, ay, s1, c1, bx, by, s2, -c2);
        return el == null ? INF : energiaConica(ax, ay, el[2], el[3], bx, by, el[0]);
    }

    // ---------- pertenencia ----------

    /**
     * Si el punto (x, y) del plano está dentro de la silueta (la máscara de la gota). Con la simetría respecto al eje, en el marco
     * local basta la semianchura h(x) del contorno superior: las tapas son círculos, y el conector es la recta P1-P3 o el arco de
     * elipse, que es monótono en x, de modo que x(u) = x es una ecuación de segundo grado en el parámetro u del arco. Coste constante
     * y sin subdivisión. Con el respaldo BIARCO se usa el contorno ({@link #forma()}), más lento.
     */
    boolean contiene(double x, double y) {
        double ex = x - c1.x, ey = y - c1.y;
        return contieneLocal(ex * tx + ey * ty, ex * ty - ey * tx);
    }

    /** Como {@link #contiene(double, double)}, en el marco local (c1 en el origen, T = (1, 0), N hacia +y). */
    boolean contieneLocal(double x, double y) {
        if (angulos.conector() == Conector.BIARCO) {
            if (formaCache == null) formaCache = formaLocal();
            return formaCache.contains(x, y);
        }
        return distanciaLocal(x, y) >= 0;
    }

    /**
     * Distancia con signo (positiva dentro) del punto (x, y) al contorno, aproximada: exacta en las tapas y de primer orden en el
     * conector (la semianchura en x dividida por sqrt(1 + h'^2)), que basta cerca del borde. Su signo es el de la pertenencia.
     * No definida (se usa el respaldo del contorno) con el conector BIARCO.
     */
    double distancia(double x, double y) {
        double ex = x - c1.x, ey = y - c1.y;
        return distanciaLocal(ex * tx + ey * ty, ex * ty - ey * tx);
    }

    /** Como {@link #distancia(double, double)}, en el marco local. */
    double distanciaLocal(double x, double y) {
        if (angulos.conector() == Conector.BIARCO) {
            if (formaCache == null) formaCache = formaLocal();
            return formaCache.contains(x, y) ? 0 : -1e-12;      // sin distancia: solo el signo
        }
        y = Math.abs(y);
        if (x <= xP1 || x < -r1) return r1 - Math.hypot(x, y);
        if (x >= xP3 || x > d + r2) return r2 - Math.hypot(x - d, y);
        double h, dh;
        if (elipseSup == null) {                                  // RECTA
            dh = (yP3 - yP1) / (xP3 - xP1);
            h = yP1 + dh * (x - xP1);
        } else {
            double[] q = elipseEn(x);
            if (q == null) return -INF;
            h = q[0];
            dh = q[1];
        }
        return (h - y) / Math.sqrt(1 + dh * dh);
    }

    /** {h, h'} del arco de elipse superior en x (xP1 < x < xP3), o null si no hay raíz. */
    private double[] elipseEn(double x) {
        double[] k = elipseSup;
        double w = k[4], a = k[0] - x, b = k[5] - x, c = k[2] - x;
        double q2 = a - 2 * w * b + c, q1 = -2 * a + 2 * w * b, q0 = a, u;
        if (Math.abs(q2) < 1e-14 * (Math.abs(a) + Math.abs(b) + Math.abs(c) + 1e-300)) u = -q0 / q1;
        else {
            double sq = Math.sqrt(Math.max(q1 * q1 - 4 * q2 * q0, 0));
            double u1 = (-q1 + sq) / (2 * q2), u2 = (-q1 - sq) / (2 * q2);
            u = u1 >= -1e-9 && u1 <= 1 + 1e-9 ? u1 : (u2 >= -1e-9 && u2 <= 1 + 1e-9 ? u2 : Double.NaN);
        }
        if (Double.isNaN(u)) return null;
        u = Math.min(Math.max(u, 0), 1);
        double A = (1 - u) * (1 - u), B = 2 * w * u * (1 - u), C = u * u, D = A + B + C;
        double dD = 2 * (w - 1) * (1 - 2 * u);
        double nx = A * k[0] + B * k[5] + C * k[2], ny = A * k[1] + B * k[6] + C * k[3];
        double dnx = -2 * (1 - u) * k[0] + 2 * w * (1 - 2 * u) * k[5] + 2 * u * k[2];
        double dny = -2 * (1 - u) * k[1] + 2 * w * (1 - 2 * u) * k[6] + 2 * u * k[3];
        double dx = (dnx - nx / D * dD) / D, dy = (dny - ny / D * dD) / D;
        return new double[] { ny / D, dy / dx };
    }

    // ---------- la forma ----------

    /** Los extremos de los arcos en el plano: P1 y P2 son los de la tapa 1 (lados +N y -N), P3 y P4 los de la tapa 2; las ramas son P1→P3 y P2→P4. */
    Point2D.Double[] extremos() {
        Point2D.Double[] out = extremosLocales();
        AffineTransform at = aMundo();
        for (int i = 0; i < 4; i++) at.transform(out[i], out[i]);
        return out;
    }

    /** Como {@link #extremos()}, pero en el marco local (c1 en el origen, T = (1, 0), N hacia +y). */
    Point2D.Double[] extremosLocales() {
        double p1 = angulos.psi1(), p2 = angulos.psi2();
        double[][] l = {
            { -r1 * Math.cos(p1), r1 * Math.sin(p1) }, { -r1 * Math.cos(p1), -r1 * Math.sin(p1) },
            { d + r2 * Math.cos(p2), r2 * Math.sin(p2) }, { d + r2 * Math.cos(p2), -r2 * Math.sin(p2) } };
        Point2D.Double[] out = new Point2D.Double[4];
        for (int i = 0; i < 4; i++) out[i] = new Point2D.Double(l[i][0], l[i][1]);
        return out;
    }

    private AffineTransform aMundo() {
        // x' = Tx·x + Nx·y + cx; y' = Ty·x + Ny·y + cy, con N = (Ty, -Tx)
        return new AffineTransform(tx, ty, ty, -tx, c1.x, c1.y);
    }

    /** La silueta cerrada (arcos de círculo en cúbicas de Bézier, error ~1e-4 del radio), en el sentido T→N→-T. */
    Path2D.Double forma() {
        Path2D.Double p = formaLocal();
        p.transform(aMundo());
        return p;
    }

    /** La silueta en el marco local (c1 en el origen, T = (1, 0), N hacia +y matemático). */
    Path2D.Double formaLocal() {
        double p1 = angulos.psi1(), p2 = angulos.psi2();
        double c1c = Math.cos(p1), c1s = Math.sin(p1), c2c = Math.cos(p2), c2s = Math.sin(p2);
        double ax = -r1 * c1c, ay = r1 * c1s, bx = d + r2 * c2c, by = r2 * c2s;
        double[] el = angulos.conector() == Conector.ELIPSE ? elipse(ax, ay, c1s, c1c, bx, by, c2s, -c2c) : null;
        Path2D.Double p = new Path2D.Double();
        p.moveTo(ax, ay);
        if (el != null) {
            anade(p, ramaElipse(ax, ay, bx, by, el, false));          // superior, P1 → P3
            bezier(p, d, 0, r2, p2, -2 * p2);                                // tapa 2, por +T
            anade(p, ramaElipse(ax, ay, bx, by, el, true));            // inferior: la espejada, de P4 a P2
        } else if (angulos.conector() == Conector.RECTA) {
            p.lineTo(bx, by);
            bezier(p, d, 0, r2, p2, -2 * p2);
            p.lineTo(-r1 * c1c, -r1 * c1s);
        } else {
            double[] b = biarco(ax, ay, c1s, c1c, bx, by, c2s, -c2c);
            if (b == null) throw new IllegalStateException("biarco no admisible");
            double ka = b[2], la = b[3], kb = -b[4], lb = b[5];            // recorridos de P1 a P3
            double[] s = traza(p, ax, ay, c1s, c1c, ka, la);
            traza(p, s[0], s[1], s[2], s[3], kb, lb);                      // termina en P3
            bezier(p, d, 0, r2, p2, -2 * p2);                              // tapa 2, por +T
            double lx = d + r2 * c2c, ly = -r2 * c2s;                      // P4
            s = traza(p, lx, ly, -c2s, -c2c, kb, lb);                      // el biarco inferior es el espejo del superior, al revés
            traza(p, s[0], s[1], s[2], s[3], ka, la);
        }
        bezier(p, 0, 0, r1, Math.PI + p1, -2 * p1);                // tapa 1, por -T
        p.closePath();
        return p;
    }

    /** Añade a dst los segmentos de src sin su moveTo inicial (src empieza donde dst acaba; evita aristas de longitud cero). */
    private static void anade(Path2D.Double dst, Path2D.Double src) {
        double[] c = new double[6];
        for (java.awt.geom.PathIterator it = src.getPathIterator(null); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case java.awt.geom.PathIterator.SEG_LINETO -> dst.lineTo(c[0], c[1]);
                case java.awt.geom.PathIterator.SEG_CUBICTO -> dst.curveTo(c[0], c[1], c[2], c[3], c[4], c[5]);
                default -> { }
            }
        }
    }

    /**
     * La elipse (cónica racional (1, w, 1) sobre P, X, Q) es la imagen afín de un arco de circunferencia unidad de semiángulo
     * alfa = acos(w): se dibuja ese arco con cúbicas de Bézier (tramos de hasta 45°) y se transforma. Con {@code espejo} da la
     * rama inferior (y → -y) recorrida de Q a P.
     */
    private static Path2D.Double ramaElipse(double px, double py, double qx, double qy, double[] el, boolean espejo) {
        double w = el[0], xx = el[2], xy = el[3];
        double al = Math.acos(w), sa = Math.sin(al), ca = Math.cos(al);
        double mx = (px + qx) / 2, my = (py + qy) / 2;
        double ux = (qx - px) / (2 * sa), uy = (qy - py) / (2 * sa);               // imagen de (1, 0)
        double vx = (xx - mx) * ca / (sa * sa), vy = (xy - my) * ca / (sa * sa);   // imagen de (0, 1)
        AffineTransform t = new AffineTransform(ux, uy, vx, vy, mx - vx * ca, my - vy * ca);
        double a0 = espejo ? Math.PI / 2 - al : Math.PI / 2 + al;
        Path2D.Double r = new Path2D.Double();
        r.moveTo(Math.cos(a0), Math.sin(a0));
        bezier(r, 0, 0, 1, a0, espejo ? 2 * al : -2 * al);
        r.transform(t);
        if (espejo) r.transform(AffineTransform.getScaleInstance(1, -1));
        return r;
    }

    /** Añade a p un arco de curvatura k y longitud l desde (x, y) con tangente (tx, ty); devuelve {x, y, tx, ty} finales. */
    private static double[] traza(Path2D.Double p, double x, double y, double tx, double ty, double k, double l) {
        if (Math.abs(k) * l < 1e-12) {
            x += l * tx;
            y += l * ty;
            p.lineTo(x, y);
            return new double[] { x, y, tx, ty };
        }
        double cx = x - ty / k, cy = y + tx / k, r = 1 / Math.abs(k), a0 = Math.atan2(y - cy, x - cx), sw = k * l;
        bezier(p, cx, cy, r, a0, sw);
        double c = Math.cos(sw), s = Math.sin(sw);
        return new double[] { cx + r * Math.cos(a0 + sw), cy + r * Math.sin(a0 + sw), tx * c - ty * s, tx * s + ty * c };
    }

    /** Arco de círculo (centro, radio, ángulo inicial y barrido con signo) en tramos de hasta 45° como cúbicas de Bézier; continúa el camino. */
    private static void bezier(Path2D.Double p, double cx, double cy, double r, double a0, double sweep) {
        int n = Math.max(1, (int) Math.ceil(Math.abs(sweep) / (Math.PI / 4) - 1e-9));
        double da = sweep / n, k = 4.0 / 3.0 * Math.tan(da / 4) * r;
        for (int i = 0; i < n; i++) {
            double a = a0 + i * da, b = a + da;
            p.curveTo(cx + r * Math.cos(a) - k * Math.sin(a), cy + r * Math.sin(a) + k * Math.cos(a),
                      cx + r * Math.cos(b) + k * Math.sin(b), cy + r * Math.sin(b) - k * Math.cos(b),
                      cx + r * Math.cos(b), cy + r * Math.sin(b));
        }
    }
}
