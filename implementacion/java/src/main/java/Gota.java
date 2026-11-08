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
 * Un punto p pertenece a la gota si existe t con |p - c(t)|^2 - r(t)^2 <= 0.
 * Ese polinomio (grado 6) se evalúa en base de Bernstein: sin contornos ni tangentes explícitas.
 */
public final class Gota implements Geometria {

    /** Vector relativo (tirador respecto a su centro). */
    public record Vec(double x, double y) {}

    private static final int GRADO = 6;          // círculos
    private static final int MAX_G = 6;
    private static final double BANDA = 0.5;   // semiancho del antialiasing, en píxeles
    private static final int PROF_MAX = 24;    // profundidad máxima de subdivisión
    private static final double[][] BIN = new double[MAX_G + 1][MAX_G + 1];
    static {
        for (int n = 0; n <= MAX_G; n++) {
            BIN[n][0] = 1;
            for (int k = 1; k <= n; k++)
                BIN[n][k] = BIN[n - 1][k - 1] + (k <= n - 1 ? BIN[n - 1][k] : 0);
        }
    }

    private static final int EXT = 0, CERO = 1, INT = 2;   // banda del antialiasing para la pertenencia

    /** Un trozo del camino [t0, t1] con su cúbica de centros y su cuadrática de radio, en base de Bernstein. */
    static final class Tramo {
        final double t0, t1;
        final double[] a;                          // controles de la cuadrática del radio en el tramo
        final double[] cx6, cy6;                   // c(t) del tramo, en Bernstein de grado 6
        final double[] kExt, kCero, kInt;          // K = |c|^2 - (r + d)^2 para d = +BANDA, 0, -BANDA

        Tramo(double t0, double t1, double[] pxs, double[] pys, double[] a) {
            this.t0 = t0; this.t1 = t1; this.a = a;
            cx6 = elevar(pxs, GRADO);
            cy6 = elevar(pys, GRADO);
            double[] c2 = suma(producto(pxs, pxs), producto(pys, pys));
            kExt = k(c2, a, +BANDA);
            kCero = k(c2, a, 0);
            kInt = k(c2, a, -BANDA);
        }

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
    private final boolean interiorValido;
    private final Rectangle2D.Double bounds;
    final int muestras;
    /** Coordenadas intrínsecas de esta gota, una por combinación de costura y tira (se crean al pedirlas). */
    private final java.util.concurrent.ConcurrentMap<Clave, Coordenadas> coords = new java.util.concurrent.ConcurrentHashMap<>();

    private record Clave(Costura costura, Tira tira) {}

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
        double w = 1 - tm;
        double q = (rm - w * w * this.r1 - tm * tm * this.r2) / (2 * tm * w);
        if (suav >= 1 && q >= -1e-12) {
            tramos = new Tramo[] { new Tramo(0, 1, px, py, new double[] { this.r1, Math.max(0, q), this.r2 }) };
        } else {
            double d1 = (rm - this.r1) / tm, d2 = (this.r2 - rm) / w, mS = w * d1 + tm * d2;
            double mL = (1 - suav) * d1 + suav * mS, mR = (1 - suav) * d2 + suav * mS;
            mL = Math.min(mL, 2 * rm / tm);          // controles >= 0: r(t) >= 0 siempre
            mR = Math.max(mR, -2 * rm / w);
            double[] ax = new double[4], bx = new double[4], ay = new double[4], by = new double[4];
            dividirEn(px, tm, ax, bx);
            dividirEn(py, tm, ay, by);
            tramos = new Tramo[] {
                new Tramo(0, tm, ax, ay, new double[] { this.r1, rm - mL * tm / 2, rm }),
                new Tramo(tm, 1, bx, by, new double[] { rm, rm + mR * w / 2, this.r2 }) };
        }

        double minX = px[0], maxX = px[0], minY = py[0], maxY = py[0], largo = 0, rc = 0, rmin = Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            minX = Math.min(minX, px[i]); maxX = Math.max(maxX, px[i]);
            minY = Math.min(minY, py[i]); maxY = Math.max(maxY, py[i]);
            if (i > 0) largo += Math.hypot(px[i] - px[i - 1], py[i] - py[i - 1]);
        }
        for (Tramo t : tramos) { rc = Math.max(rc, t.maxControl()); rmin = Math.min(rmin, t.minControl()); }
        interiorValido = rmin >= BANDA;
        double rmax = rc + 1;
        bounds = new Rectangle2D.Double(minX - rmax, minY - rmax, maxX - minX + 2 * rmax, maxY - minY + 2 * rmax);
        muestras = (int) Math.max(32, Math.min(512, largo / 2 + 16));
    }

    private Coordenadas coordenadas(Opciones op) {
        return coords.computeIfAbsent(new Clave(op.costura(), op.tira()),
                k -> new Coordenadas(this, k.costura(), k.tira().cortes(this)));
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

    /** Pertenencia exacta (a precisión de punto flotante). */
    @Override public boolean contiene(double x, double y) { return dentro(x, y, CERO); }

    /**
     * Distancia con signo. Exacta fuera de la gota. Dentro es la profundidad respecto al
     * mejor círculo de la familia: exacta cerca del borde, aproximada (por defecto) en el interior profundo.
     */
    public double sdf(double x, double y) { return minimizar(x, y, false)[1]; }

    /**
     * Muestra del píxel: el círculo de la familia al que la costura (ver {@link Costura}) asigna el punto.
     * Es la misma t de la que salen las coordenadas de la tira ({@link #uv}), así que colores y texturas coinciden.
     */
    public Coordenadas.Muestra parametros(double x, double y) { return parametros(x, y, Opciones.POR_DEFECTO); }

    public Coordenadas.Muestra parametros(double x, double y, Opciones op) {
        return Coordenadas.muestra(this, x, y, ganador(x, y, op).t());
    }

    /** Muestra del círculo en el que el píxel está más centrado (mínimo de rho): decide qué gota manda en una composición. */
    Coordenadas.Muestra porRho(double x, double y) { return porRho(x, y, Solape.PLANO); }

    Coordenadas.Muestra porRho(double x, double y, Solape solape) {
        if (solape == Solape.TRAZO) {   // la capa más tardía que contiene el punto
            java.util.List<Hebra> hs = hebras(x, y, 0);
            if (!hs.isEmpty()) {
                return Coordenadas.muestra(this, x, y, hs.get(hs.size() - 1).t());
            }
        }
        double t = minimizar(x, y, true)[0];
        return Coordenadas.muestra(this, x, y, t);
    }

    /** Coordenadas de textura de la tira en el punto (valores en [0, 1] dentro de la gota). */
    public Coordenadas.Uv uv(double x, double y) { return uv(x, y, Opciones.POR_DEFECTO); }

    public Coordenadas.Uv uv(double x, double y, Opciones op) { return ganador(x, y, op).uv(); }

    /** Coordenadas del píxel como un todo, según el solape y la costura. */
    private Coordenadas.Costurado ganador(double x, double y, Opciones op) {
        return op.solape().ganador(coordenadas(op), x, y);
    }

    /** El punto (x, y) visto desde esta gota: su muestra y sus coordenadas de tira se calculan al pedirlas, una sola vez. */
    public Punto punto(double x, double y) { return punto(x, y, Opciones.POR_DEFECTO); }

    public Punto punto(double x, double y, Opciones op) {
        return coordenadas(op).punto(x, y, () -> ganador(x, y, op));
    }

    /** Color del relleno en el píxel (con las opciones por defecto). */
    int color(Relleno rel, double x, double y) { return color(rel, x, y, Opciones.POR_DEFECTO); }

    int color(Relleno rel, double x, double y, Opciones op) {
        return rel.argb(punto(x, y, op));
    }

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
        final Coordenadas coord = coordenadas(op);

        IntStream.range(y0, y1).parallel().forEach(j -> {
            for (int i = x0; i < x1; i++) {
                final int idx = base + j * stride + i;
                solape.capas(this, coord, i + 0.5, j + 0.5,
                        capa -> datos[idx] = componer(datos[idx], rel.argb(capa.punto()), capa.cobertura()));
            }
        });
    }

    /** Fracción del píxel que cubre la unión de la gota (0 a más de BANDA del borde, 1 a más de BANDA de profundidad). */
    @Override public double cobertura(double x, double y) {
        if (!dentro(x, y, EXT)) return 0;
        if (interiorValido && dentro(x, y, INT)) return 1;
        return Math.max(0, Math.min(1, 0.5 - sdf(x, y)));        // franja del borde
    }

    @Override public java.util.List<Hebra> hebras(double x, double y) {
        if (!dentro(x, y, EXT)) return java.util.List.of();
        return hebras(x, y, BANDA);
    }

    // ---------- Pertenencia en base de Bernstein ----------

    private boolean dentro(double x, double y, int banda) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) return false;   // con NaN la subdivisión nunca decide
        double pp = x * x + y * y;
        double[] b = new double[GRADO + 1];
        for (Tramo t : tramos) {
            double[] K = banda == EXT ? t.kExt : banda == INT ? t.kInt : t.kCero;
            for (int i = 0; i <= GRADO; i++) b[i] = pp - 2 * (x * t.cx6[i] + y * t.cy6[i]) + K[i];
            if (hayNoPositivo(b, PROF_MAX)) return true;
        }
        return false;
    }

    /** true si el polinomio de coeficientes b toma algún valor <= 0 en [0,1]. */
    private static boolean hayNoPositivo(double[] b, int prof) {
        double min = b[0];
        for (int i = 1; i < b.length; i++) min = Math.min(min, b[i]);
        if (min > 0) return false;                       // envolvente convexa: todo positivo
        if (b[0] <= 0 || b[b.length - 1] <= 0) return true; // F(0) o F(1) ya no positivo
        if (prof == 0) return true;                      // indeciso a esta resolución: dentro
        double[] izq = new double[b.length], der = new double[b.length];
        dividir(b, izq, der);
        return hayNoPositivo(izq, prof - 1) || hayNoPositivo(der, prof - 1);
    }

    /** Divide la Bézier de controles b en el parámetro s: izq cubre [0, s] y der cubre [s, 1]. */
    private static void dividirEn(double[] b, double s, double[] izq, double[] der) {
        double[] w = b.clone();
        int n = b.length - 1;
        izq[0] = w[0];
        der[n] = w[n];
        for (int k = 1; k <= n; k++) {
            for (int i = 0; i <= n - k; i++) w[i] = (1 - s) * w[i] + s * w[i + 1];
            izq[k] = w[0];
            der[n - k] = w[n - k];
        }
    }

    private static void dividir(double[] b, double[] izq, double[] der) {
        double[] w = b.clone();
        int n = b.length - 1;
        izq[0] = w[0];
        der[n] = w[n];
        for (int k = 1; k <= n; k++) {
            for (int i = 0; i <= n - k; i++) w[i] = 0.5 * (w[i] + w[i + 1]);
            izq[k] = w[0];
            der[n - k] = w[n - k];
        }
    }

    // ---------- Minimización numérica (sdf y t*) ----------

    private double f(double x, double y, double t, boolean porRho) {
        double dx = x - cx(t), dy = y - cy(t);
        double d = Math.sqrt(dx * dx + dy * dy), r = rad(t);
        return porRho ? d / Math.max(r, 1e-9) : d - r;
    }

    /** Devuelve {t, valor} del mínimo global de f en [0,1]: muestreo grueso + refinado. */
    private double[] minimizar(double x, double y, boolean porRho) {
        int n = muestras;
        double[] g = new double[n + 1];
        for (int i = 0; i <= n; i++) g[i] = f(x, y, i / (double) n, porRho);
        double bestT = 0, bestV = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= n; i++) {
            boolean izq = i == 0 || g[i] <= g[i - 1];
            boolean der = i == n || g[i] <= g[i + 1];
            if (!(izq && der)) continue;
            double a = Math.max(i - 1, 0) / (double) n, b = Math.min(i + 1, n) / (double) n;
            double[] m = dorada(x, y, a, b, porRho);
            double tt = i / (double) n, tv = g[i];
            if (m[1] < tv) { tt = m[0]; tv = m[1]; }
            if (tv < bestV) { bestV = tv; bestT = tt; }
        }
        return new double[] { ajusta(bestT), bestV };
    }

    /** Si el mínimo cae a un error numérico de tm (codo del perfil), lo fija en tm exactamente. */
    private double ajusta(double t) { return tramos.length == 2 && Math.abs(t - tm) < 1e-7 ? tm : t; }

    private double[] dorada(double x, double y, double a, double b, boolean porRho) {
        final double phi = 0.6180339887498949;
        double c = b - phi * (b - a), d = a + phi * (b - a);
        double fc = f(x, y, c, porRho), fd = f(x, y, d, porRho);
        for (int k = 0; k < 32; k++) {
            if (fc < fd) { b = d; d = c; fd = fc; c = b - phi * (b - a); fc = f(x, y, c, porRho); }
            else         { a = c; c = d; fc = fd; d = a + phi * (b - a); fd = f(x, y, d, porRho); }
        }
        double t = 0.5 * (a + b);
        return new double[] { t, f(x, y, t, porRho) };
    }

    // ---------- Modo TRAZO: hebras (tramos del camino) como capas ----------

    /**
     * Mínimos locales de g(t) = |p - c(t)| - r(t) con g < limite, como {t, g} en orden de t creciente.
     * Cada uno es un tramo del camino que pasa cerca de p.
     */
    private java.util.List<Hebra> hebras(double x, double y, double limite) {
        int n = muestras;
        double[] g = new double[n + 1];
        for (int i = 0; i <= n; i++) g[i] = f(x, y, i / (double) n, false);
        java.util.List<Hebra> out = new java.util.ArrayList<>();
        for (int i = 0; i <= n; i++) {
            boolean izq = i == 0 || g[i] < g[i - 1];     // '<' a un lado: una sola muestra por meseta
            boolean der = i == n || g[i] <= g[i + 1];
            if (!(izq && der)) continue;
            double a = Math.max(i - 1, 0) / (double) n, b = Math.min(i + 1, n) / (double) n;
            double[] m = dorada(x, y, a, b, false);
            double t = i / (double) n, v = g[i];
            if (m[1] < v) { t = m[0]; v = m[1]; }
            if (v < limite) out.add(new Hebra(ajusta(t), v));
        }
        return out;
    }

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

    // ---------- Álgebra de Bernstein ----------

    private static double[] k(double[] c2sum, double[] a, double d) {
        double[] rd = { a[0] + d, a[1] + d, a[2] + d };
        double[] rd2 = elevar(producto(rd, rd), GRADO);
        double[] out = new double[GRADO + 1];
        for (int i = 0; i <= GRADO; i++) out[i] = c2sum[i] - rd2[i];
        return out;
    }

    private static double[] producto(double[] a, double[] b) {
        int m = a.length - 1, n = b.length - 1;
        double[] c = new double[m + n + 1];
        for (int i = 0; i <= m; i++)
            for (int j = 0; j <= n; j++)
                c[i + j] += BIN[m][i] * BIN[n][j] / BIN[m + n][i + j] * a[i] * b[j];
        return c;
    }

    private static double[] resta(double[] a, double[] b) {
        double[] c = new double[a.length];
        for (int i = 0; i < a.length; i++) c[i] = a[i] - b[i];
        return c;
    }

    private static double[] suma(double[] a, double[] b) {
        double[] c = new double[a.length];
        for (int i = 0; i < a.length; i++) c[i] = a[i] + b[i];
        return c;
    }

    private static double[] elevar(double[] a, int grado) {
        double[] c = a;
        while (c.length - 1 < grado) {
            int n = c.length - 1;
            double[] d = new double[n + 2];
            d[0] = c[0];
            d[n + 1] = c[n];
            for (int i = 1; i <= n; i++) {
                double s = i / (double) (n + 1);
                d[i] = s * c[i - 1] + (1 - s) * c[i];
            }
            c = d;
        }
        return c;
    }

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
