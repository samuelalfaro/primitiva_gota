package forma;

import utils.Utils;
import coordenadas.Coordenadas;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Primitiva 2D "gota": envolvente de la familia de círculos (c(t), r(t)), t en [0,1].
 *  - c(t): Bézier cúbica; sus extremos son los centros de los círculos.
 *  - r(t): cúbica de Bézier monótona de r1 a r2, definida por sus tangentes en los extremos (véase {@link Perfil}).
 * Un punto p pertenece a la envolvente si existe t con |p - c(t)|^2 - r(t)^2 <= 0. Ese polinomio (grado 6) se evalúa en base
 * de Bernstein en {@link Silueta.Envolvente}; la minimización de |p - c(t)| - r(t) está en {@link Camino} y las coordenadas en {@link Coordenadas}.
 */
public final class Gota implements Camino {

    /** Vector relativo (tirador respecto a su centro). */
    public record Vec(double x, double y) {}

    private static final double BANDA = Silueta.Envolvente.BANDA;   // semiancho del antialiasing, en píxeles

    private final double[] px, py;        // puntos de control de c(t), cúbica
    private final double r1, r2;          // radios de los extremos
    private final Perfil.Resuelto radio;  // perfil del radio r(t): cúbica de Bézier
    private final Rectangle2D.Double bounds;
    final int muestras;
    /**
     * La máscara de la gota. Con una trayectoria (la longitud de la curva entre los centros) d mayor que r1 + r2 es la unión de la familia
     * de círculos ({@link Silueta.Envolvente}), aunque los centros coincidan (lazos); con d <= r1 + r2 es la silueta de dos tapas y dos
     * conectores, que no admite deformación: se descarta la curva y se toma la recta de los centros.
     */
    private final Mascara mascara;
    /** La silueta rígida (cuatro arcos sobre la recta de los centros) si la gota es rígida; null si no. */
    private final Silueta silueta;
    /** La silueta rígida de la gota, o null si su trayectoria mide más de r1 + r2 (entonces la forma es la envolvente). */
    public Silueta silueta() { return silueta; }
    private volatile double[] largos;
    /** Coordenadas intrínsecas de esta gota, una por combinación de costura y tira: las gestiona {@link Coordenadas#de}. */
    private final java.util.concurrent.ConcurrentMap<Object, Coordenadas> coords = new java.util.concurrent.ConcurrentHashMap<>();

    // ---------- Fábricas ----------

    public static Gota lineal(Point2D c1, double r1, Point2D c2, double r2, Perfil perfil) {
        Vec v1 = new Vec((c2.getX() - c1.getX()) / 3, (c2.getY() - c1.getY()) / 3);
        Vec v2 = new Vec((c1.getX() - c2.getX()) / 3, (c1.getY() - c2.getY()) / 3);
        return new Gota(c1, r1, v1, c2, r2, v2, perfil);
    }

    public static Gota curva(Point2D c1, double r1, Vec v1, Point2D c2, double r2, Vec v2, Perfil perfil) {
        return new Gota(c1, r1, v1, c2, r2, v2, perfil);
    }

    private Gota(Point2D c1, double ra, Vec v1, Point2D c2, double rb, Vec v2, Perfil perfil) {
        java.util.Objects.requireNonNull(perfil, "perfil");
        for (double v : new double[] { c1.getX(), c1.getY(), c2.getX(), c2.getY(), ra, rb,
                                       v1.x(), v1.y(), v2.x(), v2.y() })
            if (!Double.isFinite(v)) throw new IllegalArgumentException("parámetros no finitos");
        this.r1 = Math.max(0, ra);
        this.r2 = Math.max(0, rb);
        this.radio = perfil.resuelve(this.r1, this.r2);

        double[] qx = { c1.getX(), c1.getX() + v1.x(), c2.getX() + v2.x(), c2.getX() };
        double[] qy = { c1.getY(), c1.getY() + v1.y(), c2.getY() + v2.y(), c2.getY() };
        // Con una trayectoria d <= r1 + r2 la forma no admite deformación: se descarta la curva y el camino es la recta de los centros.
        final boolean rigida = this.r1 + this.r2 > 0 && longitudCamino(qx, qy) <= (this.r1 + this.r2) * (1 + 1e-9)
                && !(RIGIDA_CURVA && sinPliegue(qx, qy, this.radio));      // EXPERIMENTO (-Dgota.rigidaCurva=true): ver sinPliegue
        if (rigida) {
            double ex = qx[3] - qx[0], ey = qy[3] - qy[0];
            if (Math.hypot(ex, ey) <= 1e-9 * (this.r1 + this.r2)) {    // centros coincidentes: la tangente inicial del camino orienta la forma
                ex = qx[1] - qx[0]; ey = qy[1] - qy[0];
                if (ex == 0 && ey == 0) { ex = qx[2] - qx[0]; ey = qy[2] - qy[0]; }
                if (ex == 0 && ey == 0) { ex = qx[3] - qx[0]; ey = qy[3] - qy[0]; }
                double n = Math.hypot(ex, ey);
                if (n == 0) { ex = 1; ey = 0; n = 1; }
                double e = 1e-6 * (this.r1 + this.r2) / n;
                ex *= e; ey *= e;
                qx[3] = qx[0] + ex; qy[3] = qy[0] + ey;
            } else { ex = qx[3] - qx[0]; ey = qy[3] - qy[0]; }
            for (int i = 1; i <= 2; i++) { qx[i] = qx[0] + i * ex / 3; qy[i] = qy[0] + i * ey / 3; }
        }
        px = qx;
        py = qy;

        double minX = px[0], maxX = px[0], minY = py[0], maxY = py[0], largo = 0, rc = 0;
        for (double v : radio.controles()) rc = Math.max(rc, v);
        for (int i = 0; i < 4; i++) {
            minX = Math.min(minX, px[i]); maxX = Math.max(maxX, px[i]);
            minY = Math.min(minY, py[i]); maxY = Math.max(maxY, py[i]);
            if (i > 0) largo += Math.hypot(px[i] - px[i - 1], py[i] - py[i - 1]);
        }
        if (!(this.r1 + this.r2 > 0)) { mascara = VACIA; silueta = null; }
        else if (rigida) {
            silueta = Silueta.de(new Point2D.Double(px[0], py[0]), this.r1, new Point2D.Double(px[3], py[3]), this.r2);
            mascara = silueta.mascara();
        }
        else { mascara = new Silueta.Envolvente(this, new double[][] { px }, new double[][] { py }, new double[][] { radio.controles() }); silueta = null; }
        double rmax = rc + 1;
        bounds = new Rectangle2D.Double(minX - rmax, minY - rmax, maxX - minX + 2 * rmax, maxY - minY + 2 * rmax);
        if (silueta != null) {                                          // la silueta rígida sobresale de los círculos: sus límites mandan
            Rectangle2D f = silueta.forma().getBounds2D();
            bounds.add(new Rectangle2D.Double(f.getMinX() - 1, f.getMinY() - 1, f.getWidth() + 2, f.getHeight() + 2));
        }
        muestras = (int) Math.max(32, Math.min(512, largo / 2 + 16));
    }

    // ---------- Consultas ----------

    public Rectangle2D bounds() { return (Rectangle2D) bounds.clone(); }

    private volatile double[] acumulada;
    private volatile double largoTotal;

    /** Longitud del camino entre los centros (la recta si la gota es rígida), sin contar las tapas. */
    public double longitud() { fraccion(0); return largoTotal; }

    /** Fracción de la longitud de arco del camino hasta t: tabla de longitudes acumuladas (muestreo uniforme en t) interpolada linealmente. */
    @Override public double fraccion(double t) {
        double[] a = acumulada;
        if (a == null) {
            final int n = 512;
            a = new double[n + 1];
            double x0 = cx(0), y0 = cy(0);
            for (int i = 1; i <= n; i++) {
                double x = cx(i / (double) n), y = cy(i / (double) n);
                a[i] = a[i - 1] + Math.hypot(x - x0, y - y0);
                x0 = x; y0 = y;
            }
            largoTotal = a[n];
            if (a[n] > 0) for (int i = 1; i <= n; i++) a[i] /= a[n];
            else for (int i = 0; i <= n; i++) a[i] = i / (double) n;      // camino sin longitud: se reparte por t
            acumulada = a;
        }
        int n = a.length - 1;
        double f = Utils.acota(t, 0, 1) * n;
        int i = Math.min(n - 1, (int) f);
        return a[i] + (a[i + 1] - a[i]) * (f - i);
    }

    public Point2D centro(double t) { return new Point2D.Double(cx(t), cy(t)); }

    public double radio(double t) { return rad(t); }

    // ---------- La máscara y las coordenadas viven fuera (Silueta, Coordenadas); el pintado, en Plotter ----------

    /** La máscara de la gota: pertenencia, cobertura (antialiasing) y pasadas del camino. */
    @Override public Mascara mascara() { return mascara; }

    /** Longitud de arco de la cúbica de Bézier de controles (px, py), por muestreo. */
    /**
     * EXPERIMENTO opt-in (-Dgota.rigidaCurva=true, margen -Dgota.rigidaCurvaMargen, 0,9 por defecto): una gota de trayectoria
     * d <= r1 + r2 se trata como la envolvente de la curva (sin forma rígida) cuando no hay pliegue: en todo el camino el
     * radio de curvatura supera el radio del círculo (kappa * r < margen), la pendiente del radio es menor que la
     * velocidad (|r'| / |c'| < margen) y los centros no coinciden. Si no, sigue siendo rígida, como siempre.
     */
    static final boolean RIGIDA_CURVA = Boolean.getBoolean("gota.rigidaCurva");
    private static final double MARGEN_CURVA = Double.parseDouble(System.getProperty("gota.rigidaCurvaMargen", "0.9"));

    /**
     * Diagnóstico: true si la gota no es rígida y su envolvente se pliega (en algún punto del camino el radio de
     * curvatura es menor o igual que el radio del círculo, kappa * r >= 1, o |r'| >= |c'|). Los centros coincidentes
     * (lazos) no cuentan.
     */
    public boolean plegada() {
        if (silueta != null || Math.hypot(px[3] - px[0], py[3] - py[0]) < 1e-9) return false;
        return !sinPliegue(px, py, radio, 1.0);
    }

    private static boolean sinPliegue(double[] px, double[] py, Perfil.Resuelto radio) { return sinPliegue(px, py, radio, MARGEN_CURVA); }

    private static boolean sinPliegue(double[] px, double[] py, Perfil.Resuelto radio, double margen) {
        if (Math.hypot(px[3] - px[0], py[3] - py[0]) < 1e-9) return false;
        final int n = 64;
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n, u = 1 - t;
            double dx = 3 * (u * u * (px[1] - px[0]) + 2 * u * t * (px[2] - px[1]) + t * t * (px[3] - px[2]));
            double dy = 3 * (u * u * (py[1] - py[0]) + 2 * u * t * (py[2] - py[1]) + t * t * (py[3] - py[2]));
            double ddx = 6 * (u * (px[2] - 2 * px[1] + px[0]) + t * (px[3] - 2 * px[2] + px[1]));
            double ddy = 6 * (u * (py[2] - 2 * py[1] + py[0]) + t * (py[3] - 2 * py[2] + py[1]));
            double v = Math.hypot(dx, dy);
            if (v < 1e-9) return false;
            double kappa = Math.abs(dx * ddy - dy * ddx) / (v * v * v);
            if (kappa * radio.r(t) >= margen || Math.abs(radio.dr(t)) / v >= margen) return false;
        }
        return true;
    }

    private static double longitudCamino(double[] px, double[] py) {
        final int n = 256;
        double l = 0, x0 = px[0], y0 = py[0];
        for (int i = 1; i <= n; i++) {
            double t = i / (double) n, u = 1 - t;
            double a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, d = t * t * t;
            double x = a * px[0] + b * px[1] + c * px[2] + d * px[3], y = a * py[0] + b * py[1] + c * py[2] + d * py[3];
            l += Math.hypot(x - x0, y - y0);
            x0 = x; y0 = y;
        }
        return l;
    }

    // ---------- Forma rígida: radios de la silueta ----------

    private static final int NL = 256;

    /** Distancia de (x0, y0) al primer punto del contorno de la silueta en la dirección unitaria (ux, uy). */
    private double largoRayo(double x0, double y0, double ux, double uy) {
        double tope = 2 * (silueta.r1 + silueta.r2 + silueta.d) + 1, ant = 0, lo = 0, hi = tope;
        final int n = 64;
        for (int i = 1; i <= n; i++) {
            double s = tope * i / n;
            if (!silueta.contiene(x0 + s * ux, y0 + s * uy)) { lo = ant; hi = s; break; }
            ant = s;
        }
        for (int i = 0; i < 40; i++) {
            double m = 0.5 * (lo + hi);
            if (silueta.contiene(x0 + m * ux, y0 + m * uy)) lo = m; else hi = m;
        }
        return 0.5 * (lo + hi);
    }

    @Override public java.util.List<Camino.Arco> arcos() {
        if (silueta == null) return null;
        java.util.List<java.awt.geom.Path2D.Double> t = silueta.tramos();
        return java.util.List.of(new Camino.Arco(false, t.get(0)), new Camino.Arco(true, t.get(1)),
                                 new Camino.Arco(false, t.get(2)), new Camino.Arco(true, t.get(3)));
    }

    /** Longitud de la costilla en NL + 1 valores de t (la silueta es simétrica: sirve para los dos lados). */
    private double[] largos() {
        double[] l = largos;
        if (l == null) synchronized (this) {
            if ((l = largos) == null) {
                Silueta.Angulos a = silueta.angulos();
                double g0 = Math.PI - a.psi1(), g1 = a.psi2();
                double[] v = new double[NL + 1];
                for (int i = 0; i <= NL; i++) {
                    double t = i / (double) NL, ga = g0 + (g1 - g0) * t;
                    double ux = Math.cos(ga) * silueta.tx + Math.sin(ga) * silueta.ty, uy = Math.cos(ga) * silueta.ty - Math.sin(ga) * silueta.tx;
                    v[i] = largoRayo(cx(t), cy(t), ux, uy);
                }
                v[0] = silueta.r1; v[NL] = silueta.r2;
                largos = l = v;
            }
        }
        return l;
    }

    /** En una gota rígida, el radio hacia el punto de la silueta en el que cae el extremo P de cada tapa, y de ahí el abanico lineal en t. */
    @Override public double[] costilla(double t, int lado) {
        if (silueta == null) return null;
        double[] L = largos();
        double f = Math.max(0, Math.min(1, t)) * NL;
        int i = Math.min(NL - 1, (int) f);
        double len = L[i] * (1 - (f - i)) + L[i + 1] * (f - i);
        Silueta.Angulos a = silueta.angulos();
        double ga = Math.PI - a.psi1() + (a.psi2() - (Math.PI - a.psi1())) * Math.max(0, Math.min(1, t)), s = lado == 1 ? 1 : -1;
        double c = Math.cos(ga), sn = s * Math.sin(ga);
        return new double[] { len * (c * silueta.tx + sn * silueta.ty), len * (c * silueta.ty - sn * silueta.tx) };
    }

    @Override public double escala(double t, double dx, double dy) {
        if (silueta == null) return rad(t);
        double n = Math.hypot(dx, dy);
        if (n < 1e-12) return rad(t);
        return largoRayo(cx(t), cy(t), dx / n, dy / n);
    }

    /** La máscara de una gota sin radio. */
    private static final Mascara VACIA = new Mascara() {
        @Override public boolean contiene(double x, double y) { return false; }
        @Override public double cobertura(double x, double y) { return 0; }
        @Override public java.util.List<Camino.Hebra> hebras(double x, double y) { return java.util.List.of(); }
    };

    /** Caché de coordenadas de esta gota (la usa {@link Coordenadas#de}). */
    public java.util.concurrent.ConcurrentMap<Object, Coordenadas> cacheCoordenadas() { return coords; }

    @Override public int muestras() { return muestras; }

    /** Tangente unitaria del camino en t; si la derivada es nula usa la secante, y si no, el eje x. */
    @Override public double[] tangente(double t) {
        double u = 1 - t;
        double dx = 3 * (u * u * (px[1] - px[0]) + 2 * u * t * (px[2] - px[1]) + t * t * (px[3] - px[2]));
        double dy = 3 * (u * u * (py[1] - py[0]) + 2 * u * t * (py[2] - py[1]) + t * t * (py[3] - py[2]));
        double n = Math.hypot(dx, dy);
        if (n < 1e-9) {
            double ta = Math.max(t - 0.01, 0), tb = Math.min(t + 0.01, 1);
            dx = cx(tb) - cx(ta);
            dy = cy(tb) - cy(ta);
            n = Math.hypot(dx, dy);
            if (n < 1e-9) return new double[] { 1, 0 };
        }
        return new double[] { dx / n, dy / n };
    }

    /** Ángulo (radianes) del eje tangente de la familia en t, medido desde +x hacia +y. */
    public double angulo(double t) {
        double[] d = tangente(t);
        return Math.atan2(d[1], d[0]);
    }


    @Override public double dcx(double t) { double u = 1 - t; return 3 * (u*u*(px[1]-px[0]) + 2*u*t*(px[2]-px[1]) + t*t*(px[3]-px[2])); }
    @Override public double dcy(double t) { double u = 1 - t; return 3 * (u*u*(py[1]-py[0]) + 2*u*t*(py[2]-py[1]) + t*t*(py[3]-py[2])); }
    @Override public double drad(double t) { return radio.dr(t); }

    // ---------- Evaluación directa ----------

    @Override public double cx(double t) { double u = 1 - t; return u*u*u*px[0] + 3*u*u*t*px[1] + 3*u*t*t*px[2] + t*t*t*px[3]; }
    @Override public double cy(double t) { double u = 1 - t; return u*u*u*py[0] + 3*u*u*t*py[1] + 3*u*t*t*py[2] + t*t*t*py[3]; }
    @Override public double rad(double t) { return radio.r(t); }
}
