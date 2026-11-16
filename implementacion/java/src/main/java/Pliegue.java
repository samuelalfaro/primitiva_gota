/**
 * EXPERIMENTAL (rama de estudio, issue #29; ver ESTUDIO_SOLAPES.md). Zona de pliegue de la costura ORTOGONAL: cuando el
 * contorno interior del codo, recorrido por las normales del camino, se corta a sí mismo en un punto X, ese punto
 * corresponde a dos valores del camino (ta y tb), y el tramo [ta, tb] deja de usar la tira (t, u) para usar polares con centro en X.
 * <ul>
 *   <li>Cuña: la región acotada por las normales de ta y de tb (de X al contorno exterior), la espina entre ellas y el
 *       contorno exterior entre ellas. Las dos normales miden hw_interior + hw_exterior en ta y en tb, igual que en la tira, así
 *       que las coordenadas casan en los dos bordes.</li>
 *   <li>Dentro: el ángulo del rayo desde X fija t (el de la espina en el punto donde el rayo la corta) y a lo largo del rayo
 *       q (el u con signo de la tira, en [-1, 1]) es lineal por tramos: 1 en X, 0 en la espina, -1 en el contorno exterior.</li>
 * </ul>
 * Solo se trata un cruce (el de mayor tramo de t). Con varios (S, espirales) el resto queda con la tira. Pendiente: radios
 * muy distintos con inclinación fuerte del contorno (aquí la normal es la de la tira, no la de la envolvente exacta).
 */
final class Pliegue {
    private static final int NA = 512;          // rayos de la tabla
    private static final int NS = 600;          // muestras de la espina y del contorno exterior en [ta, tb]
    private static final int HUECO = 4;         // separación mínima, en muestras de la tabla, entre las dos pasadas que se cruzan

    final double ta, tb, xx, xy;
    private final double a0, da;
    private final int lado;                     // lado interior (0 = -N, 1 = +N)
    private final double[] d = new double[NA + 1], r = new double[NA + 1], t = new double[NA + 1];

    private Pliegue(double ta, double tb, double xx, double xy, int lado, Geometria g, double[][] hw, int m) {
        this.ta = ta; this.tb = tb; this.xx = xx; this.xy = xy; this.lado = lado;
        double[] ca = { g.cx(ta), g.cy(ta) }, cb = { g.cx(tb), g.cy(tb) };
        a0 = Math.atan2(ca[1] - xy, ca[0] - xx);
        da = envuelve(Math.atan2(cb[1] - xy, cb[0] - xx) - a0);
        double[][] esp = new double[NS + 1][], ext = new double[NS + 1][];
        int otro = 1 - lado;
        double sg = otro == 1 ? 1 : -1;
        for (int i = 0; i <= NS; i++) {
            double tt = ta + (tb - ta) * i / NS;
            double[] tg = g.tangente(tt);
            double h = interpola(hw[otro], tt, m);
            esp[i] = new double[] { g.cx(tt), g.cy(tt), tt };
            ext[i] = new double[] { g.cx(tt) + sg * h * tg[1], g.cy(tt) - sg * h * tg[0], tt };   // N = (Ty, -Tx)
        }
        for (int k = 0; k <= NA; k++) {
            double th = a0 + da * k / NA, ux = Math.cos(th), uy = Math.sin(th);
            double[] he = golpe(esp, ux, uy, 0), hx;
            if (he == null) { d[k] = Double.NaN; r[k] = Double.NaN; t[k] = Double.NaN; continue; }
            hx = golpe(ext, ux, uy, he[0]);
            d[k] = he[0]; t[k] = he[1]; r[k] = hx == null ? Double.NaN : hx[0];
        }
        relleno(d); relleno(r); relleno(t);
    }

    private double envuelve(double a) { return a - 2 * Math.PI * Math.floor((a + Math.PI) / (2 * Math.PI)); }

    private static double interpola(double[] h, double tt, int m) {
        double f = Math.max(0, Math.min(1, tt)) * m;
        int i = Math.min(m - 1, (int) f);
        return h[i] * (1 - (f - i)) + h[i + 1] * (f - i);
    }

    /** Primer corte del rayo (X + s·u) con la polilínea que queda a más de smin: {s, t}, o null. */
    private double[] golpe(double[][] pl, double ux, double uy, double smin) {
        double[] mejor = null;
        for (int i = 0; i < pl.length - 1; i++) {
            double px = pl[i][0], py = pl[i][1], dx = pl[i + 1][0] - px, dy = pl[i + 1][1] - py;
            double den = ux * dy - uy * dx;
            if (Math.abs(den) < 1e-14) continue;
            double wx = px - xx, wy = py - xy, s = (wx * dy - wy * dx) / den, f = (wx * uy - wy * ux) / den;
            if (s > smin + 1e-9 && f >= 0 && f <= 1 && (mejor == null || s < mejor[0]))
                mejor = new double[] { s, pl[i][2] + f * (pl[i + 1][2] - pl[i][2]) };
        }
        return mejor;
    }

    private static void relleno(double[] v) {      // rayos extremos que fallan por tolerancia: copian al vecino
        for (int k = 0; k < v.length; k++) if (Double.isNaN(v[k])) {
            int a = k, b = k;
            while (a >= 0 && Double.isNaN(v[a])) a--;
            while (b < v.length && Double.isNaN(v[b])) b++;
            v[k] = a >= 0 ? v[a] : b < v.length ? v[b] : 0;
        }
    }

    /**
     * Busca el cruce del contorno interior recorrido por las normales de la tabla de semianchuras (hw[lado][i], i / m = t).
     * Devuelve la zona, o null si ningún lado se cruza a sí mismo.
     */
    static Pliegue de(Geometria g, double[][] hw, int m) {
        Pliegue mejor = null;
        double mejorDt = 0;
        for (int l = 0; l < 2; l++) {
            double sg = l == 1 ? 1 : -1;
            double[][] q = new double[m + 1][2];
            for (int i = 0; i <= m; i++) {
                double[] tg = g.tangente(i / (double) m);
                q[i][0] = g.cx(i / (double) m) + sg * hw[l][i] * tg[1];
                q[i][1] = g.cy(i / (double) m) - sg * hw[l][i] * tg[0];
            }
            int bi = -1, bj = -1; double bs = 0, bu = 0;
            for (int i = 0; i < m; i++) for (int j = i + HUECO; j < m; j++) {
                double r1x = q[i + 1][0] - q[i][0], r1y = q[i + 1][1] - q[i][1], r2x = q[j + 1][0] - q[j][0], r2y = q[j + 1][1] - q[j][1];
                double den = r1x * r2y - r1y * r2x;
                if (Math.abs(den) < 1e-12) continue;
                double wx = q[j][0] - q[i][0], wy = q[j][1] - q[i][1];
                double s = (wx * r2y - wy * r2x) / den, u = (wx * r1y - wy * r1x) / den;
                if (s >= 0 && s <= 1 && u >= 0 && u <= 1 && (bi < 0 || j - i > bj - bi)) { bi = i; bj = j; bs = s; bu = u; }
            }
            if (bi < 0) continue;
            double ta = (bi + bs) / m, tb = (bj + bu) / m;
            if (tb - ta > mejorDt) {
                mejorDt = tb - ta;
                mejor = new Pliegue(ta, tb, q[bi][0] + bs * (q[bi + 1][0] - q[bi][0]), q[bi][1] + bs * (q[bi + 1][1] - q[bi][1]), l, g, hw, m);
            }
        }
        return mejor;
    }

    /** {t, q} del píxel si cae dentro de la cuña (q en [-1, 1], con el signo de u de la tira), o null. */
    double[] localiza(double x, double y) {
        double dx = x - xx, dy = y - xy, rho = Math.hypot(dx, dy);
        double rel = envuelve(Math.atan2(dy, dx) - a0) / da;
        if (!(rel >= 0 && rel <= 1)) return null;
        double f = rel * NA;
        int k = Math.min(NA - 1, (int) f);
        double fr = f - k;
        double dd = d[k] * (1 - fr) + d[k + 1] * fr, rr = r[k] * (1 - fr) + r[k + 1] * fr, tt = t[k] * (1 - fr) + t[k + 1] * fr;
        if (rho > rr || dd < 1e-12) return null;
        double q = rho <= dd ? 1 - rho / dd : -(rho - dd) / Math.max(rr - dd, 1e-12);
        return new double[] { tt, lado == 1 ? q : -q };
    }

    @Override public String toString() { return String.format("Pliegue[ta=%.3f tb=%.3f X=(%.1f, %.1f) lado=%d]", ta, tb, xx, xy, lado); }
}
