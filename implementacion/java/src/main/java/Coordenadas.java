/**
 * Coordenadas intrínsecas de los píxeles de una {@link Gota}, comunes a los colores ({@link Colores}) y a las texturas
 * ({@link Texturas}): ambos se calculan sobre las mismas coordenadas, que la gota deduce de la posición del píxel.
 * <ul>
 *   <li>{@link Muestra}: (t, rho, axial, lateral) respecto al círculo de la familia que lo cubre;</li>
 *   <li>{@link Uv}: la tira (u de lado a lado, v a lo largo, con las tapas como semidiscos);</li>
 *   <li>{@link #discoTapa}: del punto de la tira al disco unidad de una tapa (l, a), de donde salen las coordenadas
 *       polares de los colores y la posición plana de las texturas.</li>
 * </ul>
 */
public final class Coordenadas {

    /**
     * Parámetros de un píxel respecto al círculo de la familia que lo cubre (c = c(t), r = r(t)):
     * t = posición en el camino; rho = |p - c| / r;
     * axial = (p - c)·T / r, con T la tangente unitaria del camino;
     * lateral = (p - c)·N / r, con N = (Ty, -Tx) (con T hacia abajo en pantalla, N apunta a la derecha).
     * Se cumple rho² = axial² + lateral².
     */
    public record Muestra(double t, double rho, double axial, double lateral) {}

    /** Coordenadas de textura de la tira: v a lo largo de la gota, u de lado a lado. */
    public record Uv(double u, double v) {}

    private final Gota g;

    Coordenadas(Gota g) { this.g = g; }

    Muestra muestra(double x, double y, double t) {
        double r = Math.max(g.rad(t), 1e-9);
        double dx = x - g.cx(t), dy = y - g.cy(t);
        double[] tg = g.tangente(t);
        if (g.elipses) {
            double ax = (dx * tg[0] + dy * tg[1]) / (r * g.lam(t)), la = (dx * tg[1] - dy * tg[0]) / r;
            return new Muestra(t, Math.hypot(ax, la), ax, la);
        }
        return new Muestra(t, Math.hypot(dx, dy) / r,
                           (dx * tg[0] + dy * tg[1]) / r,
                           (dx * tg[1] - dy * tg[0]) / r);   // N = (Ty, -Tx)
    }

    // ---------- Tira de textura ----------
    //
    // v recorre la gota en cuatro tramos (por defecto cuartos):
    //   [0, v1]   tapa trasera (semicírculo exterior del primer círculo)
    //   [v1, v2]  primera mitad del cuerpo, t en [0, tm]
    //   [v2, v3]  segunda mitad del cuerpo, t en [tm, 1]
    //   [v3, 1]   tapa delantera
    // u cruza de lado a lado: 1/2 sobre el camino, 0 y 1 en los bordes. Es una coordenada con signo,
    // lineal en la distancia lateral, así que no tiene arista en el eje (a diferencia de rho).
    // Orientación: u crece hacia la izquierda del sentido de avance en pantalla.

    Uv uv(double x, double y, double t) {
        if (g.elipses) return uvElipse(x, y, t);
        double eps = 2.0 / g.muestras;
        if (t <= eps) {
            Uv c = tapa(x, y, 0);
            if (c != null) return c;
        } else if (t >= 1 - eps) {
            Uv c = tapa(x, y, 1);
            if (c != null) return c;
        }
        double[] m = marco(x, y, t);
        double u = 0.5 + 0.5 * Gota.acota(m[1] / Math.sqrt(1 - m[3] * m[3]), -1, 1);
        double v = t < g.tm ? g.v1 + (g.v2 - g.v1) * t / g.tm : g.v2 + (g.v3 - g.v2) * (t - g.tm) / (1 - g.tm);
        return new Uv(u, Gota.acota(v, 0, 1));
    }

    // ---------- Tira de textura con elipses ----------
    //
    // Mismas bandas que con círculos, pero exactas para cualquier aspecto. En cada elipse de la familia,
    // con A = d·T y S = d·N sin normalizar (d = p - c(t)), el valor q = (A/a)² + (S/b)² crece o decrece con t
    // según el signo de Q = q'/2. El contorno de la unión toca a la elipse donde Q = 0 sobre su borde
    // (dos puntos de contacto, de lateral S+ > 0 y S- < 0), y la tapa de un extremo es la zona donde el
    // mínimo de q (o de la distancia, en TRAZO) está en el propio extremo. De ahí:
    //   u = 1/2 + 1/2 · S / S+  (S >= 0)   o   1/2 + 1/2 · S / |S-|  (S < 0)
    //   tapas: w = (profundidad - borde interior) / (borde exterior - borde interior), a lateral S fijo,
    //   con el borde exterior en la propia elipse y el interior donde Q cambia de signo.

    private static final double K_MIN = Math.sqrt(1 - 0.999 * 0.999);   // extensión mínima de la tira, como k = ±0.999 con círculos

    /** Magnitudes en t: {vT, vN, omega, bp, ap, b, a}. bp es b'(t); NaN usa la del tramo. */
    private double[] dinamica(double t, double bp) {
        double tt = t, vx = g.dcx(tt), vy = g.dcy(tt);
        if (Math.hypot(vx, vy) < 1e-9) {
            tt = t < 0.5 ? t + 1e-3 : t - 1e-3;
            vx = g.dcx(tt); vy = g.dcy(tt);
        }
        double dx = g.ddx(tt), dy = g.ddy(tt), n = Math.hypot(dx, dy);
        double tx = dx / n, ty = dy / n;
        double vT = vx * tx + vy * ty, vN = vx * ty - vy * tx;
        double u = 1 - tt;
        double dxp = 2 * (u * (g.dxq[1] - g.dxq[0]) + tt * (g.dxq[2] - g.dxq[1]));
        double dyp = 2 * (u * (g.dyq[1] - g.dyq[0]) + tt * (g.dyq[2] - g.dyq[1]));
        double omega = (dx * dyp - dy * dxp) / (n * n);
        double b = Math.max(g.rad(t), 1e-9);
        double bpv = Double.isNaN(bp) ? g.drad(tt) : bp;
        double lamv = g.lam(t), ap = (g.lam2 - g.lam1) * b + lamv * bpv;
        return new double[] { vT, vN, omega, bpv, ap, b, lamv * b };
    }

    /** Q(A, S) = q'/2: derivada respecto a t del cuadrado de la norma elíptica del punto (A, S) fijo. */
    private static double qE(double[] d, double A, double S) {
        double a = d[6], b = d[5];
        return A * (-d[0] - d[2] * S) / (a * a) + S * (-d[1] + d[2] * A) / (b * b)
               - A * A * d[4] / (a * a * a) - S * S * d[3] / (b * b * b);
    }

    /** Puntos de contacto {{A+, S+}, {A-, S-}} con S+ > 0 máximo y S- < 0 mínimo (null si no hay). */
    private static double[][] contactos(double[] d) {
        double a = d[6], b = d[5];
        int n = 180;
        double[] pos = null, neg = null;
        double th0 = -Math.PI, h0 = qE(d, a * Math.cos(th0), b * Math.sin(th0));
        for (int i = 1; i <= n; i++) {
            double th1 = -Math.PI + 2 * Math.PI * i / n, h1 = qE(d, a * Math.cos(th1), b * Math.sin(th1));
            if ((h0 < 0) != (h1 < 0) || h0 == 0) {
                double lo = th0, hi = th1, flo = h0;
                for (int k = 0; k < 60; k++) {
                    double mid = 0.5 * (lo + hi), fm = qE(d, a * Math.cos(mid), b * Math.sin(mid));
                    if ((fm < 0) == (flo < 0)) { lo = mid; flo = fm; } else hi = mid;
                }
                double th = 0.5 * (lo + hi), A = a * Math.cos(th), S = b * Math.sin(th);
                if (S > 1e-12 * b && (pos == null || S > pos[1])) pos = new double[] { A, S };
                if (S < -1e-12 * b && (neg == null || S < neg[1])) neg = new double[] { A, S };
            }
            th0 = th1; h0 = h1;
        }
        return new double[][] { pos, neg };
    }

    /** Denominador de u a un lado: S+ (lado +) o |S-| (lado -), con la extensión mínima si no hay contacto. */
    private static double extension(double[][] c, int lado, double b) {
        double[] k = c[lado];
        return k == null ? K_MIN * b : Math.abs(k[1]);
    }

    /** En el codo del perfil la elipse aporta un arco al contorno: se acota A entre los contactos de ambos lados. */
    private static double extensionCodo(double[] ci, double[] cd, double A, double a, double b) {
        if (ci == null || cd == null) return K_MIN * b;
        double lo = Math.min(ci[0], cd[0]), hi = Math.max(ci[0], cd[0]);
        double ae = Gota.acota(A, lo, hi);
        return Math.max(K_MIN * b, b * Math.sqrt(Math.max(0, 1 - (ae / a) * (ae / a))));
    }

    /** Punto de la elipse (x/a)² + (y/b)² = 1 más cercano a (u, v). */
    static double[] pieElipse(double a, double b, double u, double v) {
        boolean giro = a < b;
        double e0 = giro ? b : a, e1 = giro ? a : b;
        double q0 = giro ? v : u, q1 = giro ? u : v;
        double y0 = Math.abs(q0), y1 = Math.abs(q1), x0, x1;
        if (e1 <= 1e-12) {
            x0 = Math.min(y0, e0); x1 = 0;
        } else if (y1 > 0) {
            if (y0 > 0) {
                double z0 = y0 / e0, z1 = y1 / e1, g = z0 * z0 + z1 * z1 - 1;
                double r0 = (e0 / e1) * (e0 / e1), n0 = r0 * z0;
                double s0 = z1 - 1, s1 = g < 0 ? 0 : Math.hypot(n0, z1) - 1, s = 0;
                for (int i = 0; i < 400; i++) {
                    s = 0.5 * (s0 + s1);
                    if (s == s0 || s == s1) break;
                    double ra = n0 / (s + r0), rb = z1 / (s + 1), gg = ra * ra + rb * rb - 1;
                    if (gg > 0) s0 = s; else if (gg < 0) s1 = s; else break;
                }
                x0 = r0 * y0 / (s + r0); x1 = y1 / (s + 1);
            } else { x0 = 0; x1 = e1; }
        } else {
            double num = e0 * y0, den = e0 * e0 - e1 * e1;
            if (num < den) { double xde = num / den; x0 = e0 * xde; x1 = e1 * Math.sqrt(1 - xde * xde); }
            else { x0 = e0; x1 = 0; }
        }
        double fx = Math.copySign(x0, q0), fy = Math.copySign(x1, q1);
        return giro ? new double[] { fy, fx } : new double[] { fx, fy };
    }

    /** ¿Está el punto (A, S) en la tapa del extremo fin? (el mínimo está en el propio extremo) */
    private boolean tapaE(double[] d, int fin, double A, double S) {
        double qa = A, qs = S;
        if (g.solape == Gota.Solape.TRAZO) {   // regla de las hebras: el signo se evalúa en el pie sobre la elipse
            double[] f = pieElipse(d[6], d[5], A, S);
            qa = f[0]; qs = f[1];
        }
        double Q = qE(d, qa, qs);
        return fin == 0 ? Q >= 0 : Q <= 0;
    }

    private Uv tapa(double x, double y, int fin, boolean elipse) {
        double t = fin, b = g.rad(t);
        if (b <= 1e-9) return null;
        double[] d = dinamica(t, Double.NaN);
        double a = d[6];
        double[] T = g.dirD(t);
        double dx = x - g.cx(t), dy = y - g.cy(t);
        double A = dx * T[0] + dy * T[1], S = dx * T[1] - dy * T[0];
        if (Math.abs(S) > b) return null;
        double depOut = a * Math.sqrt(Math.max(0, 1 - (S / b) * (S / b)));
        double dep = fin == 0 ? -A : A;
        if (!tapaE(d, fin, fin == 0 ? -dep : dep, S)) return null;
        // borde interior: el cambio del criterio "en la tapa" más cercano a la profundidad 0 (la raíz de menor
        // módulo, como con círculos): hacia fuera si el centro no está en la tapa, hacia dentro si lo está
        double dIn;
        boolean enCentro = tapaE(d, fin, 0, S);
        int n = 48;
        double fin1 = enCentro ? -depOut : depOut;
        dIn = fin1;
        double prev = 0;
        for (int i = 1; i <= n; i++) {
            double cur = fin1 * i / n;
            boolean tp = tapaE(d, fin, fin == 0 ? -cur : cur, S);
            if (tp != enCentro) {
                double l = prev, h = cur;
                for (int k = 0; k < 50; k++) {
                    double mid = 0.5 * (l + h);
                    if (tapaE(d, fin, fin == 0 ? -mid : mid, S) != enCentro) h = mid; else l = mid;
                }
                dIn = 0.5 * (l + h);
                break;
            }
            prev = cur;
        }
        double den = depOut - dIn;
        double w = den > 1e-9 ? Gota.acota((dep - dIn) / den, 0, 1) : 1;
        double[][] c = contactos(d);
        double u = 0.5 + 0.5 * Gota.acota(S / extension(c, S >= 0 ? 0 : 1, b), -1, 1);
        double v = fin == 0 ? g.v1 * (1 - w) : g.v3 + (1 - g.v3) * w;
        return new Uv(u, Gota.acota(v, 0, 1));
    }

    private Uv uvElipse(double x, double y, double t) {
        double eps = 2.0 / g.muestras;
        if (t <= eps) {
            Uv c = tapa(x, y, 0, true);
            if (c != null) return c;
        } else if (t >= 1 - eps) {
            Uv c = tapa(x, y, 1, true);
            if (c != null) return c;
        }
        double b = Math.max(g.rad(t), 1e-9), a = g.lam(t) * b;
        double[] T = g.dirD(t);
        double dx = x - g.cx(t), dy = y - g.cy(t);
        double A = dx * T[0] + dy * T[1], S = dx * T[1] - dy * T[0];
        double ext;
        if (g.tramos.length == 2 && t == g.tm) {
            double[][] ci = contactos(dinamica(t, g.tramos[0].drad(g.tm))), cd = contactos(dinamica(t, g.tramos[1].drad(g.tm)));
            ext = extensionCodo(ci[S >= 0 ? 0 : 1], cd[S >= 0 ? 0 : 1], A, a, b);
        } else {
            ext = extension(contactos(dinamica(t, Double.NaN)), S >= 0 ? 0 : 1, b);
        }
        double u = 0.5 + 0.5 * Gota.acota(S / ext, -1, 1);
        double v = t < g.tm ? g.v1 + (g.v2 - g.v1) * t / g.tm : g.v2 + (g.v3 - g.v2) * (t - g.tm) / (1 - g.tm);
        return new Uv(u, Gota.acota(v, 0, 1));
    }

    /**
     * Tapa en el extremo fin (0 o 1), o null si el punto no cae en ella. Un punto está en la tapa si
     * su mejor tramo es el propio extremo (derivada de la regla de t* con el signo adecuado).
     * La profundidad w va de 0 en el borde con el cuerpo a 1 en el arco exterior.
     */
    private Uv tapa(double x, double y, int fin) {
        double[] m = marco(x, y, fin);
        double a = m[0], s = m[1], rho = m[2], k = m[3];
        double dir = fin == 0 ? 1 : -1;
        double kk = k * dir;                    // pendiente del radio vista desde el propio extremo
        double b = -a * dir;                    // distancia más allá del centro, hacia fuera
        boolean trazo = g.solape == Gota.Solape.TRAZO;
        double e = b - kk * (trazo ? rho : rho * rho);
        if (e < 0) return null;
        double bb = trazo
                ? kk * Math.abs(s) / Math.sqrt(1 - kk * kk)
                : (kk == 0 ? 0 : (1 - Math.sqrt(Math.max(0, 1 - 4 * kk * kk * s * s))) / (2 * kk));
        double bo = Math.sqrt(Math.max(0, 1 - s * s));
        double den = bo - bb;
        double w = den > 1e-9 ? Gota.acota((b - bb) / den, 0, 1) : 1;
        double u = 0.5 + 0.5 * Gota.acota(s / Math.sqrt(1 - k * k), -1, 1);
        double v = fin == 0 ? g.v1 * (1 - w) : g.v3 + (1 - g.v3) * w;
        return new Uv(u, Gota.acota(v, 0, 1));
    }

    /** Marco del círculo en t: {avance, lateral, rho, k}; avance y lateral normalizados por r, k = r'/|c'|. */
    private double[] marco(double x, double y, double t) {
        double r = Math.max(g.rad(t), 1e-9);
        double dx = x - g.cx(t), dy = y - g.cy(t);
        double tt = t;
        double vx = g.elipses ? g.ddx(tt) : g.dcx(tt), vy = g.elipses ? g.ddy(tt) : g.dcy(tt), sp = Math.hypot(vx, vy);
        if (!g.elipses && sp < 1e-9) {             // tirador nulo: dirección tomada un poco más adentro
            tt = t < 0.5 ? t + 1e-3 : t - 1e-3;
            vx = g.dcx(tt); vy = g.dcy(tt); sp = Math.hypot(vx, vy);
        }
        double tx = 1, ty = 0, k = 0;
        if (g.elipses) {
            // la orientación sale de D (regularizada); la pendiente k = r'/|c'| usa la velocidad real del camino
            tx = vx / sp; ty = vy / sp;
            double spk = Math.hypot(g.dcx(tt), g.dcy(tt));
            if (spk < 1e-9) { tt = t < 0.5 ? t + 1e-3 : t - 1e-3; spk = Math.hypot(g.dcx(tt), g.dcy(tt)); }
            if (spk > 1e-9) k = g.drad(tt) / spk;
            sp = spk > 1e-9 ? spk : 0;
        } else if (sp > 1e-9) { tx = vx / sp; ty = vy / sp; k = g.drad(tt) / sp; }
        double axial = (dx * tx + dy * ty) / (g.elipses ? r * g.lam(t) : r);   // con elipses, normalizado por el radio tangente a
        if (sp > 1e-9 && g.tramos.length == 2 && tt == g.tm) {
            // Codo del perfil: el círculo central aporta un arco al contorno, cuyo contacto va de
            // axial = -kIzq a axial = -kDer. Dentro del arco, k es el que cumple axial = -k.
            double kI = g.tramos[0].drad(g.tm) / sp, kD = g.tramos[1].drad(g.tm) / sp;
            k = Gota.acota(-axial, Math.min(kI, kD), Math.max(kI, kD));
        }
        k = Gota.acota(k, -0.999, 0.999);
        double lat = (dx * ty - dy * tx) / r;
        return new double[] { axial, lat, g.elipses ? Math.hypot(axial, lat) : Math.hypot(dx, dy) / r, k };
    }

    /**
     * Las tapas de la tira son semidiscos. Con (u, w) de la tira (w va de 0 en la costura con el cuerpo a 1 en el arco),
     * l = 2u - 1 y a = w·sqrt(1 - l²) dan el punto (l, a) del semidisco unidad (en la gota recta de radios iguales es la
     * posición real del píxel). Como w es una fracción de la profundidad de la tapa, el cuerpo y la tapa casan para
     * cualquier pendiente del radio y para las elipses. La textura se coloca plana sobre ese semidisco (v' = c1·(1 - a))
     * y el color lateral va en polares (s, th): ver {@link Lateral#enDisco}.
     */
    /** {l, a, fin} del punto de tapa de la tira (fin 0 = primera, 1 = segunda), o null si cae en el cuerpo. */
    static double[] discoTapa(Coordenadas.Uv uv, double c1, double c3) {
        double v = uv.v(), w;
        int fin;
        if (v < c1) { w = 1 - v / c1; fin = 0; }
        else if (v > c3) { w = (v - c3) / (1 - c3); fin = 1; }
        else return null;
        double l = 2 * clamp01(uv.u()) - 1;
        return new double[] { l, w * Math.sqrt(Math.max(0, 1 - l * l)), fin };
    }

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
}
