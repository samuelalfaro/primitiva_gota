import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.util.stream.IntStream;

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
        envolvente = new Silueta.Envolvente(pxs, pys, controles);
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

    // ---------- Delegados (la lógica vive en Silueta, Geometria y Coordenadas; se quitarán cuando se corrijan las llamadas) ----------

    /** Pertenencia exacta (a precisión de punto flotante). Delega en {@link Silueta.Envolvente#contiene}. */
    @Deprecated @Override public boolean contiene(double x, double y) { return envolvente.contiene(x, y); }

    /** Distancia con signo. Delega en {@link Geometria#distancia}. */
    @Deprecated public double sdf(double x, double y) { return distancia(x, y); }

    /** Delega en {@link Coordenadas#parametros}. */
    @Deprecated public Coordenadas.Muestra parametros(double x, double y) { return Coordenadas.parametros(this, x, y, Opciones.POR_DEFECTO); }

    @Deprecated public Coordenadas.Muestra parametros(double x, double y, Opciones op) { return Coordenadas.parametros(this, x, y, op); }

    /** Delega en {@link Coordenadas#porRho}. */
    @Deprecated Coordenadas.Muestra porRho(double x, double y) { return Coordenadas.porRho(this, x, y, Solape.PLANO); }

    @Deprecated Coordenadas.Muestra porRho(double x, double y, Solape solape) { return Coordenadas.porRho(this, x, y, solape); }

    /** Delega en {@link Coordenadas#uv}. */
    @Deprecated public Coordenadas.Uv uv(double x, double y) { return Coordenadas.uv(this, x, y, Opciones.POR_DEFECTO); }

    @Deprecated public Coordenadas.Uv uv(double x, double y, Opciones op) { return Coordenadas.uv(this, x, y, op); }

    /** Delega en {@link Coordenadas#punto}. */
    @Deprecated public Punto punto(double x, double y) { return Coordenadas.punto(this, x, y, Opciones.POR_DEFECTO); }

    @Deprecated public Punto punto(double x, double y, Opciones op) { return Coordenadas.punto(this, x, y, op); }

    /** Color del relleno en el píxel (con las opciones por defecto). */
    int color(Relleno rel, double x, double y) { return color(rel, x, y, Opciones.POR_DEFECTO); }

    int color(Relleno rel, double x, double y, Opciones op) {
        return rel.argb(Coordenadas.punto(this, x, y, op));
    }

    /** La pertenencia a la envolvente de la familia de círculos (para quien ya no pase por los delegados). */
    Silueta.Envolvente envolvente() { return envolvente; }

    /** Caché de coordenadas de esta gota (la usa {@link Coordenadas#de}). */
    java.util.concurrent.ConcurrentMap<Object, Coordenadas> cacheCoordenadas() { return coords; }

    @Override public int muestras() { return muestras; }

    @Override public boolean tieneCodo() { return tramos.length == 2; }

    // ---------- Render ----------

    /** Pinta sobre dst (TYPE_INT_ARGB) con antialiasing y composición alfa, con las opciones por defecto. */
    public void render(BufferedImage dst, Relleno relleno) { render(dst, relleno, Opciones.POR_DEFECTO); }

    /** Pinta sobre dst (TYPE_INT_ARGB) con antialiasing y composición alfa. */
    public void render(BufferedImage dst, Relleno relleno, Opciones op) {
        if (dst.getType() != BufferedImage.TYPE_INT_ARGB)
            throw new IllegalArgumentException("Se requiere BufferedImage.TYPE_INT_ARGB");
        // El píxel (i, j) no está en j * ancho + i si dst es una subimagen: el raster comparte el búfer del
        // padre y conserva su desplazamiento y su stride. Se calcula el índice real a partir del raster.
        final WritableRaster raster = dst.getRaster();
        if (!(raster.getSampleModel() instanceof SinglePixelPackedSampleModel sm))
            throw new IllegalArgumentException("Raster no soportado: se requiere INT_ARGB empaquetado");
        final int[] datos = ((DataBufferInt) raster.getDataBuffer()).getData();
        final int stride = sm.getScanlineStride();
        final int base = raster.getDataBuffer().getOffset()
                - raster.getSampleModelTranslateY() * stride - raster.getSampleModelTranslateX();
        final int w = dst.getWidth();
        final int x0 = Math.max(0, (int) Math.floor(bounds.getMinX()));
        final int x1 = Math.min(w, (int) Math.ceil(bounds.getMaxX()));
        final int y0 = Math.max(0, (int) Math.floor(bounds.getMinY()));
        final int y1 = Math.min(dst.getHeight(), (int) Math.ceil(bounds.getMaxY()));
        if (x0 >= x1 || y0 >= y1) return;
        final Relleno rel = relleno.para(this, op);
        final Solape solape = op.solape();
        final Coordenadas coord = Coordenadas.de(this, op);

        IntStream.range(y0, y1).parallel().forEach(j -> {
            for (int i = x0; i < x1; i++) {
                final int idx = base + j * stride + i;
                solape.capas(this, coord, i + 0.5, j + 0.5,
                        capa -> datos[idx] = componer(datos[idx], rel.argb(capa.punto()), capa.cobertura()));
            }
        });
    }

    /** Fracción del píxel que cubre la unión de la gota. Delega en {@link Silueta.Envolvente#cobertura}. */
    @Deprecated @Override public double cobertura(double x, double y) { return envolvente.cobertura(this, x, y); }

    @Override public java.util.List<Hebra> hebras(double x, double y) { return envolvente.hebras(this, x, y); }

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

    // ---------- Composición alfa (src-over, no premultiplicado) ----------

    private static int componer(int dst, int src, double cob) {
        double sa = ((src >>> 24) & 0xFF) / 255.0 * cob;
        if (sa <= 0) return dst;
        double da = ((dst >>> 24) & 0xFF) / 255.0;
        double kd = da * (1 - sa);
        double oa = sa + kd;
        int r = (int) Math.round((((src >> 16) & 0xFF) * sa + ((dst >> 16) & 0xFF) * kd) / oa);
        int g = (int) Math.round((((src >> 8) & 0xFF) * sa + ((dst >> 8) & 0xFF) * kd) / oa);
        int b = (int) Math.round(((src & 0xFF) * sa + (dst & 0xFF) * kd) / oa);
        int a = (int) Math.round(oa * 255);
        return a << 24 | r << 16 | g << 8 | b;
    }
}
