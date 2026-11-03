import java.util.ArrayList;
import java.util.List;

/**
 * Coordenadas de la tira (t, u, v) de un píxel según el modo de {@link Gota.Costura}. Solo para gotas de círculos.
 * <p>
 * Cada modo reparte el interior en una banda, cuyas líneas de t constante van del contorno al eje y al otro contorno
 * (u = 1/2 ± w/2, con w = 0 en el eje y 1 en el contorno), y dos tapas en los extremos:
 * <ul>
 *   <li>ORTOGONAL: las líneas de t constante son normales al camino; las tapas son semidiscos.</li>
 *   <li>ESPIGA: cada mitad de la línea es el radio del círculo hacia su punto de tangencia con el contorno
 *       (recta, de ahí la espiga); la tapa es el sector del círculo que queda por detrás de los dos radios del extremo.</li>
 *   <li>SUAVIZADO: cada mitad es un Hermite cúbico, con la normal como tangente en el eje y el radio como tangente
 *       en el contorno (continuidad C1 en el eje); la tapa es la región por detrás de las dos curvas del extremo.</li>
 * </ul>
 * En las tapas de ESPIGA y SUAVIZADO el punto se muestrea en polares desde el centro, con el ángulo repartido
 * linealmente por el sector, y se lleva al semidisco unidad (l, a) que las texturas y los colores esperan.
 */
final class Costurador {

    private static final int NS = 256;           // muestras de t para localizar las raíces
    private static final int NW = 128;           // muestras del contorno al eje de la curva de un extremo
    private static final double HM = 0.72;       // tangente del Hermite, en radios (ajustada sobre la figura de referencia)
    private static final int M = 512;            // tabla de semianchuras (ORTOGONAL)

    /** Marco de un círculo de la familia: centro, tangente, normal N = (Ty, -Tx), radio y k = r'/|c'|. */
    private record Fr(double cx, double cy, double tx, double ty, double nx, double ny, double r, double k, double sq) {}

    /** Una solución posible para un píxel y su clave de orden: t en la banda, -rho en la tapa inicial, 1 + rho en la final. */
    record Candidato(double clave, Coordenadas.Costurado c) {}

    private final Gota g;
    private final Gota.Costura modo;
    private final Fr[] tabla = new Fr[NS + 1];
    private final double[][] dist = new double[2][NW + 1], fase = new double[2][NW + 1];   // curva de cada extremo
    private volatile Ortogonal ort;

    Costurador(Gota g, Gota.Costura modo) {
        this.g = g;
        this.modo = modo;
        for (int i = 0; i <= NS; i++) tabla[i] = marco(i / (double) NS);
        if (modo != Gota.Costura.ORTOGONAL) {
            for (int e = 0; e < 2; e++) {
                Fr f = tabla[e == 0 ? 0 : NS];
                double sg = e == 0 ? -1 : 1;
                for (int j = 0; j <= NW; j++) {
                    double[] q = punto(f, 1, j / (double) NW);
                    double dx = q[0] - f.cx, dy = q[1] - f.cy;
                    dist[e][j] = Math.hypot(dx, dy) / f.r;
                    fase[e][j] = Math.atan2(dx * f.nx + dy * f.ny, sg * (dx * f.tx + dy * f.ty));
                }
            }
        }
    }

    // ---------- Geometría de las líneas de t constante ----------

    private Fr marco(double t) {
        t = Gota.acota(t, 0, 1);
        double x = g.dcx(t), y = g.dcy(t), n = Math.hypot(x, y);
        if (n < 1e-9) {                              // tangente nula (cúspide): se toma la de un punto vecino
            double tt = t < 0.5 ? t + 1e-3 : t - 1e-3;
            x = g.dcx(tt); y = g.dcy(tt); n = Math.max(Math.hypot(x, y), 1e-12);
        }
        double tx = x / n, ty = y / n, k = Gota.acota(g.drad(t) / n, -0.999, 0.999);
        return new Fr(g.cx(t), g.cy(t), tx, ty, ty, -tx, g.rad(t), k, Math.sqrt(1 - k * k));
    }

    /** Dirección unitaria del radio hacia el punto de tangencia del lado s (-1 / +1). */
    private static double[] costilla(Fr f, int s) {
        return new double[] { -f.k * f.tx + s * f.sq * f.nx, -f.k * f.ty + s * f.sq * f.ny };
    }

    /** Punto de la línea de t constante del lado s: w = 0 en el eje, w = 1 en el contorno. */
    private double[] punto(Fr f, int s, double w) {
        double[] d = costilla(f, s);
        if (modo == Gota.Costura.ESPIGA) return new double[] { f.cx + w * f.r * d[0], f.cy + w * f.r * d[1] };
        double px = f.cx + f.r * d[0], py = f.cy + f.r * d[1], m = HM * f.r;
        double h00 = 2 * w * w * w - 3 * w * w + 1, h10 = w * w * w - 2 * w * w + w;
        double h01 = -2 * w * w * w + 3 * w * w, h11 = w * w * w - w * w;
        return new double[] { h00 * f.cx + h10 * m * s * f.nx + h01 * px + h11 * m * d[0],
                              h00 * f.cy + h10 * m * s * f.ny + h01 * py + h11 * m * d[1] };
    }

    // ---------- Cálculo ----------

    /** Coordenadas con un solo resultado por píxel (solape PLANO): banda de menor w, si no la tapa, si no ORTOGONAL. */
    Coordenadas.Costurado calcula(double x, double y) {
        if (modo == Gota.Costura.ORTOGONAL) return ortogonal().calcula(x, y);
        double[] b = null;
        for (double[] r : raices(x, y)) if (b == null || r[1] < b[1]) b = r;
        if (b != null && modo == Gota.Costura.SUAVIZADO) b = newton(x, y, b);
        if (b != null) return enBanda(b[0], b[1], (int) b[2]);
        for (int e = 0; e < 2; e++) {
            Candidato c = tapa(x, y, e);
            if (c != null) return c.c();
        }
        return ortogonal().calcula(x, y);            // sin cubrir: unos pocos píxeles del contorno
    }

    /**
     * Todas las soluciones del píxel (solape TRAZO, donde el camino puede pasar varias veces por él), sin orden.
     * Gana la de mayor clave: la banda de mayor t, y las tapas como si prolongaran el trazo (la inicial, más cerca del
     * centro es posterior; la final, más lejos del centro es posterior).
     */
    List<Candidato> candidatos(double x, double y) {
        List<Candidato> out = new ArrayList<>();
        if (modo == Gota.Costura.ORTOGONAL) { ortogonal().candidatos(x, y, out); return out; }
        for (double[] r : raices(x, y)) {
            double[] b = modo == Gota.Costura.SUAVIZADO ? newton(x, y, r) : r;
            if (b != null) out.add(new Candidato(b[0], enBanda(b[0], b[1], (int) b[2])));
        }
        for (int e = 0; e < 2; e++) {
            Candidato c = tapa(x, y, e);
            if (c != null) out.add(c);
        }
        return out;
    }

    private Coordenadas.Costurado enBanda(double t, double w, int s) {
        double v = t < g.tm ? g.v1 + (g.v2 - g.v1) * t / g.tm : g.v2 + (g.v3 - g.v2) * (t - g.tm) / (1 - g.tm);
        return new Coordenadas.Costurado(t, new Coordenadas.Uv(0.5 + 0.5 * s * Gota.acota(w, 0, 1), Gota.acota(v, 0, 1)));
    }

    /** Raíces de t en las que el punto está sobre el radio (recto) de algún lado: {t, w, s}. */
    private List<double[]> raices(double x, double y) {
        List<double[]> out = new ArrayList<>();
        for (int s = -1; s <= 1; s += 2) {
            double h0 = 0;
            for (int i = 0; i <= NS; i++) {
                Fr f = tabla[i];
                double[] d = costilla(f, s);
                double h = (x - f.cx) * d[1] - (y - f.cy) * d[0];
                if (i > 0 && (h0 < 0) != (h < 0)) {
                    double lo = (i - 1) / (double) NS, hi = i / (double) NS, flo = h0;
                    for (int it = 0; it < 40; it++) {
                        double mid = 0.5 * (lo + hi);
                        Fr fm = marco(mid);
                        double[] dm = costilla(fm, s);
                        double hm = (x - fm.cx) * dm[1] - (y - fm.cy) * dm[0];
                        if ((hm < 0) == (flo < 0)) { lo = mid; flo = hm; } else hi = mid;
                    }
                    double t = 0.5 * (lo + hi);
                    Fr fm = marco(t);
                    double[] dm = costilla(fm, s);
                    double ex = x - fm.cx, ey = y - fm.cy, w = Math.hypot(ex, ey) / fm.r;
                    if (ex * dm[0] + ey * dm[1] >= -1e-9 && w <= 1.03) out.add(new double[] { t, Math.min(w, 1), s });
                }
                h0 = h;
            }
        }
        return out;
    }

    /** Refina (t, w) para que la curva de SUAVIZADO pase por el punto; null si no converge dentro de la banda. */
    private double[] newton(double x, double y, double[] ini) {
        int s = (int) ini[2];
        double t = ini[0], w = ini[1];
        double[] r = resto(x, y, t, w, s);
        for (int it = 0; it < 14; it++) {
            if (Math.abs(r[0]) < 1e-4 && Math.abs(r[1]) < 1e-4) return new double[] { t, Gota.acota(w, 0, 1), s };
            double ht = 1e-4, hw = 1e-4;
            double[] a = resto(x, y, Gota.acota(t + ht, 0, 1), w, s), b = resto(x, y, Gota.acota(t - ht, 0, 1), w, s);
            double dt = Gota.acota(t + ht, 0, 1) - Gota.acota(t - ht, 0, 1);
            double[] c = resto(x, y, t, w + hw, s), d = resto(x, y, t, w - hw, s);
            double j00 = (a[0] - b[0]) / dt, j10 = (a[1] - b[1]) / dt, j01 = (c[0] - d[0]) / (2 * hw), j11 = (c[1] - d[1]) / (2 * hw);
            double det = j00 * j11 - j01 * j10;
            if (Math.abs(det) < 1e-12) return null;
            double st = (r[0] * j11 - r[1] * j01) / det, sw = (j00 * r[1] - j10 * r[0]) / det;
            double paso = 1;
            for (int k = 0; k < 5; k++) {
                double nt = Gota.acota(t - paso * st, 0, 1), nw = Gota.acota(w - paso * sw, 0, 1.03);
                double[] n = resto(x, y, nt, nw, s);
                if (Math.hypot(n[0], n[1]) < Math.hypot(r[0], r[1]) || k == 4) { t = nt; w = nw; r = n; break; }
                paso /= 2;
            }
        }
        return Math.abs(r[0]) < 1e-3 && Math.abs(r[1]) < 1e-3 ? new double[] { t, Gota.acota(w, 0, 1), s } : null;
    }

    private double[] resto(double x, double y, double t, double w, int s) {
        double[] q = punto(marco(t), s, w);
        return new double[] { q[0] - x, q[1] - y };
    }

    /** Tapa del extremo e (0 inicial, 1 final): sector polar entre las dos curvas, desde el centro; null si el punto no cae. */
    private Candidato tapa(double x, double y, int e) {
        Fr f = tabla[e == 0 ? 0 : NS];
        double dx = x - f.cx, dy = y - f.cy, rho = Math.hypot(dx, dy) / f.r;
        if (rho > 1.002) return null;
        double sg = e == 0 ? -1 : 1;
        double phi = Math.atan2(dx * f.nx + dy * f.ny, sg * (dx * f.tx + dy * f.ty));   // 0 = centro de la tapa, + hacia la normal
        double[] ds = dist[e];
        double r1 = Math.min(rho, ds[NW]);
        int lo = 0, hi = NW;
        while (hi - lo > 1) { int m = (lo + hi) >> 1; if (ds[m] <= r1) lo = m; else hi = m; }
        double fr = (r1 - ds[lo]) / Math.max(1e-12, ds[lo + 1] - ds[lo]);
        double w = (lo + fr) / NW, phiE = fase[e][lo] + fr * (fase[e][lo + 1] - fase[e][lo]);
        if (Math.abs(phi) > phiE + 1e-9) return null;
        double th = Math.PI * (phiE - phi) / (2 * phiE);          // 0 sobre la curva del lado +, pi sobre la del lado -
        double l = w * Math.cos(th), a = w * Math.sin(th), s1 = Math.sqrt(Math.max(0, 1 - l * l));
        double p = s1 > 1e-9 ? Gota.acota(a / s1, 0, 1) : 0;
        double v = e == 0 ? g.v1 * (1 - p) : g.v3 + (1 - g.v3) * p;
        return new Candidato(e == 0 ? -rho : 1 + rho, new Coordenadas.Costurado(e, new Coordenadas.Uv((l + 1) / 2, v)));
    }

    private Ortogonal ortogonal() {
        Ortogonal o = ort;
        if (o == null) synchronized (this) { if ((o = ort) == null) ort = o = new Ortogonal(); }
        return o;
    }

    // ---------- ORTOGONAL ----------

    /** t = punto más cercano de la Bézier; banda por la normal, semianchura medida sobre la gota; tapas = semidiscos. */
    private final class Ortogonal {
        private final double[][] hw = new double[2][M + 1];   // semianchura (px) sobre la normal: [0] lado -N, [1] lado +N

        Ortogonal() {
            for (int i = 0; i <= M; i++) {
                Fr f = marco(i / (double) M);
                for (int s = 0; s < 2; s++) {
                    double sg = s == 1 ? 1 : -1, lo = 0, hi = 4 * f.r + 50;
                    for (int it = 0; it < 45; it++) {
                        double mid = (lo + hi) / 2;
                        if (g.contiene(f.cx + sg * mid * f.nx, f.cy + sg * mid * f.ny)) lo = mid; else hi = mid;
                    }
                    hw[s][i] = Math.max(lo, 1e-6);
                }
            }
        }

        private double d2(double x, double y, double t) {
            double dx = x - g.cx(t), dy = y - g.cy(t);
            return dx * dx + dy * dy;
        }

        private double masCercano(double x, double y) {
            int n = 128, mi = 0;
            double mv = Double.MAX_VALUE;
            for (int i = 0; i <= n; i++) { double v = d2(x, y, i / (double) n); if (v < mv) { mv = v; mi = i; } }
            double lo = Math.max(0, (mi - 1) / (double) n), hi = Math.min(1, (mi + 1) / (double) n), gr = 0.6180339887498949;
            double c = hi - gr * (hi - lo), d = lo + gr * (hi - lo), fc = d2(x, y, c), fd = d2(x, y, d);
            for (int it = 0; it < 40; it++) {
                if (fc < fd) { hi = d; d = c; fd = fc; c = hi - gr * (hi - lo); fc = d2(x, y, c); }
                else { lo = c; c = d; fc = fd; d = lo + gr * (hi - lo); fd = d2(x, y, d); }
            }
            double t = (lo + hi) / 2;
            if (t < 1e-9) t = 0; else if (t > 1 - 1e-9) t = 1;
            if (d2(x, y, 0) <= d2(x, y, t)) t = 0; else if (d2(x, y, 1) <= d2(x, y, t)) t = 1;
            return t;
        }

        Coordenadas.Costurado calcula(double x, double y) { return en(x, y, masCercano(x, y), false).c(); }

        /** Mínimos locales de la distancia al camino (con los extremos): uno por cada paso del trazo por el punto. */
        void candidatos(double x, double y, List<Candidato> out) {
            int n = 128;
            double[] d = new double[n + 1];
            for (int i = 0; i <= n; i++) d[i] = d2(x, y, i / (double) n);
            for (int i = 0; i <= n; i++) {
                if (!((i == 0 || d[i] < d[i - 1]) && (i == n || d[i] <= d[i + 1]))) continue;
                double t = i == 0 ? 0 : i == n ? 1 : refina(x, y, (i - 1) / (double) n, (i + 1) / (double) n);
                Candidato c = en(x, y, t, true);
                if (c != null) out.add(c);
            }
        }

        private double refina(double x, double y, double lo, double hi) {
            double gr = 0.6180339887498949, c = hi - gr * (hi - lo), d = lo + gr * (hi - lo), fc = d2(x, y, c), fd = d2(x, y, d);
            for (int it = 0; it < 40; it++) {
                if (fc < fd) { hi = d; d = c; fd = fc; c = hi - gr * (hi - lo); fc = d2(x, y, c); }
                else { lo = c; c = d; fc = fd; d = lo + gr * (hi - lo); fd = d2(x, y, d); }
            }
            return (lo + hi) / 2;
        }

        /** Coordenadas respecto al pie t; con estricto, null si el punto queda fuera de la banda o de la tapa de ese pie. */
        private Candidato en(double x, double y, double t, boolean estricto) {
            Fr f = marco(t);
            double r = Math.max(f.r, 1e-9), dx = x - f.cx, dy = y - f.cy, rho = Math.hypot(dx, dy) / r;
            double A = (dx * f.tx + dy * f.ty) / r, S = (dx * f.nx + dy * f.ny) / r;
            if (t == 0 || t == 1) {
                double b = t == 0 ? -A : A;                                  // profundidad hacia el exterior del extremo
                if (b >= 0) {
                    if (estricto && rho > 1.02) return null;
                    int i = t == 0 ? 0 : M;
                    double den = Math.max(1, S >= 0 ? hw[1][i] / r : hw[0][i] / r);
                    double l = Gota.acota(S / den, -1, 1), s1 = Math.sqrt(Math.max(0, 1 - l * l));
                    double a = Gota.acota(b, 0, s1), w = s1 > 1e-9 ? a / s1 : 0;
                    double v = t == 0 ? g.v1 * (1 - w) : g.v3 + (1 - g.v3) * w;
                    return new Candidato(t == 0 ? -rho : 1 + rho, new Coordenadas.Costurado(t, new Coordenadas.Uv((l + 1) / 2, v)));
                }
            }
            double fi = t * M;
            int i0 = Math.min(M - 1, (int) fi), side = S >= 0 ? 1 : 0;
            double fr = fi - i0, h = hw[side][i0] * (1 - fr) + hw[side][i0 + 1] * fr;
            if (estricto && Math.abs(S) * r > h * 1.02) return null;
            double q = Gota.acota(S * r / h, -1, 1);
            double v = t < g.tm ? g.v1 + (g.v2 - g.v1) * t / g.tm : g.v2 + (g.v3 - g.v2) * (t - g.tm) / (1 - g.tm);
            return new Candidato(t, new Coordenadas.Costurado(t, new Coordenadas.Uv(0.5 + 0.5 * q, Gota.acota(v, 0, 1))));
        }
    }
}
