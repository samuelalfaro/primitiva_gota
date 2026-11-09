import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Primitiva 2D "gota": envolvente de la familia de círculos (c(t), r(t)), t en [0,1].
 *  - c(t): Bézier cúbica; sus extremos son los centros de los círculos.
 *  - r(t): cuadrática de Bézier por r1, r(tm) = rm, r2 (rm = radio medio, tm desplazable) o, con
 *    suavizado < 1, dos cuadráticas unidas en tm con pendientes interpoladas (véase Perfil).
 * Un punto p pertenece a la envolvente si existe t con |p - c(t)|^2 - r(t)^2 <= 0. Ese polinomio (grado 6) se evalúa en base
 * de Bernstein en {@link Silueta.Envolvente}; la minimización de |p - c(t)| - r(t) está en {@link Geometria} y las coordenadas en {@link Coordenadas}.
 */
public final class Gota implements Geometria {

    /** Vector relativo (tirador respecto a su centro). */
    public record Vec(double x, double y) {}

    private static final double BANDA = Silueta.Envolvente.BANDA;   // semiancho del antialiasing, en píxeles

    /** Un trozo del perfil del radio en [t0, t1]: la cuadrática de Bézier de controles a. */
    static final class Tramo {
        final double t0, t1;
        final double[] a;                          // controles de la cuadrática del radio en el tramo

        Tramo(double t0, double t1, double[] a) { this.t0 = t0; this.t1 = t1; this.a = a; }

        double rad(double t) {
            double u = (t - t0) / (t1 - t0), w = 1 - u;
            return w * w * a[0] + 2 * w * u * a[1] + u * u * a[2];
        }

        double drad(double t) {
            double u = (t - t0) / (t1 - t0), w = 1 - u;
            return 2 * (w * (a[1] - a[0]) + u * (a[2] - a[1])) / (t1 - t0);
        }

        double minControl() { return Math.min(a[0], Math.min(a[1], a[2])); }
        double maxControl() { return Math.max(a[0], Math.max(a[1], a[2])); }
    }

    private final double[] px, py;        // puntos de control de c(t), cúbica
    private final double r1, r2;          // radios de los extremos
    final double rm, tm, suav;    // perfil efectivo (tras recortar)
    final Tramo[] tramos;         // 1 tramo (cuadrática) o 2 (partido en tm)
    private final Rectangle2D.Double bounds;
    final int muestras;
    /** La pertenencia a la envolvente de la familia de círculos (base de Bernstein): vive en {@link Silueta}. */
    private final Silueta.Envolvente envolvente;
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
        Perfil.Resuelto pr = perfil.resuelve(this.r1, this.r2);
        this.rm = pr.rm();
        this.tm = pr.tm();
        this.suav = pr.suavizado();

        px = new double[] { c1.getX(), c1.getX() + v1.x(), c2.getX() + v2.x(), c2.getX() };
        py = new double[] { c1.getY(), c1.getY() + v1.y(), c2.getY() + v2.y(), c2.getY() };

        // Perfil del radio. Cuadrática única si es suave y sus controles son >= 0; si no, dos
        // cuadráticas unidas en tm con pendientes interpoladas entre la secante y la suave.
        double[][] pxs, pys;                         // controles de c(t) en cada tramo del perfil
        double w = 1 - tm;
        double q = (rm - w * w * this.r1 - tm * tm * this.r2) / (2 * tm * w);
        if (suav >= 1 && q >= -1e-12) {
            tramos = new Tramo[] { new Tramo(0, 1, new double[] { this.r1, Math.max(0, q), this.r2 }) };
            pxs = new double[][] { px };
            pys = new double[][] { py };
        } else {
            double d1 = (rm - this.r1) / tm, d2 = (this.r2 - rm) / w, mS = w * d1 + tm * d2;
            double mL = (1 - suav) * d1 + suav * mS, mR = (1 - suav) * d2 + suav * mS;
            mL = Math.min(mL, 2 * rm / tm);          // controles >= 0: r(t) >= 0 siempre
            mR = Math.max(mR, -2 * rm / w);
            double[] ax = new double[4], bx = new double[4], ay = new double[4], by = new double[4];
            Silueta.Envolvente.dividirEn(px, tm, ax, bx);
            Silueta.Envolvente.dividirEn(py, tm, ay, by);
            tramos = new Tramo[] {
                new Tramo(0, tm, new double[] { this.r1, rm - mL * tm / 2, rm }),
                new Tramo(tm, 1, new double[] { rm, rm + mR * w / 2, this.r2 }) };
            pxs = new double[][] { ax, bx };
            pys = new double[][] { ay, by };
        }

        double minX = px[0], maxX = px[0], minY = py[0], maxY = py[0], largo = 0, rc = 0;
        for (int i = 0; i < 4; i++) {
            minX = Math.min(minX, px[i]); maxX = Math.max(maxX, px[i]);
            minY = Math.min(minY, py[i]); maxY = Math.max(maxY, py[i]);
            if (i > 0) largo += Math.hypot(px[i] - px[i - 1], py[i] - py[i - 1]);
        }
        double[][] controles = new double[tramos.length][];
        for (int i = 0; i < tramos.length; i++) { rc = Math.max(rc, tramos[i].maxControl()); controles[i] = tramos[i].a; }
        envolvente = new Silueta.Envolvente(this, pxs, pys, controles);
        double rmax = rc + 1;
        bounds = new Rectangle2D.Double(minX - rmax, minY - rmax, maxX - minX + 2 * rmax, maxY - minY + 2 * rmax);
        muestras = (int) Math.max(32, Math.min(512, largo / 2 + 16));
    }

    // ---------- Consultas ----------

    public Rectangle2D bounds() { return (Rectangle2D) bounds.clone(); }

    /** Radio efectivo en tm (tras recortar a >= 0). */
    public double rm() { return rm; }

    /** Posición efectiva de rm sobre el camino (tras recortar a [0.05, 0.95]); es también el corte central de la tira. */
    @Override public double tm() { return tm; }

    /** Suavizado efectivo del perfil, en [0, 1]. */
    public double suavizado() { return suav; }

    public Point2D centro(double t) { return new Point2D.Double(cx(t), cy(t)); }

    public double radio(double t) { return rad(t); }

    // ---------- La máscara y las coordenadas viven fuera (Silueta, Coordenadas); el pintado, en Plotter ----------

    /** La máscara de la gota: pertenencia, cobertura (antialiasing) y pasadas del camino. */
    @Override public Mascara mascara() { return envolvente; }

    /** Caché de coordenadas de esta gota (la usa {@link Coordenadas#de}). */
    java.util.concurrent.ConcurrentMap<Object, Coordenadas> cacheCoordenadas() { return coords; }

    @Override public int muestras() { return muestras; }

    @Override public boolean tieneCodo() { return tramos.length == 2; }

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
    @Override public double drad(double t) { return tramoDe(t).drad(t); }

    // ---------- Evaluación directa ----------

    @Override public double cx(double t) { double u = 1 - t; return u*u*u*px[0] + 3*u*u*t*px[1] + 3*u*t*t*px[2] + t*t*t*px[3]; }
    @Override public double cy(double t) { double u = 1 - t; return u*u*u*py[0] + 3*u*u*t*py[1] + 3*u*t*t*py[2] + t*t*t*py[3]; }
    @Override public double rad(double t) { return tramoDe(t).rad(t); }

    /** Tramo del perfil que contiene t (en el codo, el izquierdo). */
    Tramo tramoDe(double t) { return tramos.length == 1 || t <= tm ? tramos[0] : tramos[1]; }
}
