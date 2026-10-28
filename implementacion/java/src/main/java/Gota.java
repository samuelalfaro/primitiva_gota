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
public final class Gota {

    /** Vector relativo (tirador respecto a su centro). */
    public record Vec(double x, double y) {}

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

    @FunctionalInterface
    public interface Relleno {
        int argb(double t, double rho);

        /** Versión con la muestra completa (axial y lateral incluidos); por defecto usa (t, rho). */
        default int argb(Muestra m) { return argb(m.t(), m.rho()); }

        /** false si el color no depende de la muestra: el render se ahorra calcularla. */
        default boolean usaParametros() { return true; }

        /** true si el color depende de las coordenadas de textura (u, v) de la muestra. */
        default boolean usaUv() { return false; }

        /** Versión con las coordenadas de textura de la tira; solo se llama si usaUv() es true. */
        default int argb(Muestra m, Uv uv) { return argb(m); }

        /**
         * Versión del relleno ya enlazada con la gota que lo va a pintar; render() la llama antes de
         * barrer. Los rellenos que dependen de la tira (cadena axial) toman de ella sus cortes.
         */
        default Relleno para(Gota g) { return this; }

        static Relleno solido(int color) {
            return new Relleno() {
                public int argb(double t, double rho) { return color; }
                public boolean usaParametros() { return false; }
            };
        }
    }

    /** Cómo se resuelve el color donde el camino se cruza consigo mismo o los círculos se solapan. */
    public enum Solape {
        /**
         * Primitiva plana (por defecto): un único color por píxel, el del círculo en el que el píxel
         * está más centrado (mínimo de rho). Deja costuras duras donde dos tramos compiten.
         */
        PLANO,
        /**
         * Trazo: cada tramo del camino es una capa. Las de t mayor tapan completamente a las
         * anteriores, con antialiasing propio por capa.
         */
        TRAZO
    }

    private static final int GRADO = 6;          // círculos
    private static final int GRADO_E = 12;       // elipses (aspecto lineal en t)
    private static final int MAX_G = 12;
    private static final double ASPECTO_MIN = 0.05, ASPECTO_MAX = 20;
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

    /**
     * Perfil del radio entre los extremos: radio rm en la posición tm del camino, y suavizado.
     * rm >= 0 (mismo dominio que los radios), tm en [0.05, 0.95] (por defecto 1/2), suavizado en [0, 1].
     * Con rm = (r1 + r2)/2 y tm = 1/2 las tangentes del contorno son rectas; por encima la gota se
     * hincha y por debajo toma forma de guitarra. Suavizado 1 (por defecto): el radio es una única
     * cuadrática por (0, r1), (tm, rm), (1, r2). Suavizado 0: el radio va en línea recta de r1 a rm
     * y de rm a r2, con un codo en tm (el contorno sigue siendo tangente-continuo: en tm aparece un
     * arco del propio círculo central). Entre ambos, la pendiente en tm de cada lado se interpola.
     * Los valores fuera de dominio se recortan; los no finitos se rechazan.
     */
    public static final class Perfil {
        private final double valor, tm, suavizado;
        private final boolean relativo;
        private final double lam1, lam2;          // aspecto a/b en los extremos (1 = círculo)
        private final double tang1, tang2;        // radios tangentes absolutos (solo si usaTang)
        private final boolean usaTang, forzar;             // pruebas: usar el algoritmo de elipses aunque sean círculos

        private Perfil(double valor, boolean relativo, double tm, double suavizado) {
            this(valor, relativo, tm, suavizado, 1, 1, 0, 0, false, false);
        }

        private Perfil(double valor, boolean relativo, double tm, double suavizado,
                       double lam1, double lam2, double tang1, double tang2, boolean usaTang, boolean forzar) {
            this.valor = valor; this.relativo = relativo; this.tm = tm; this.suavizado = suavizado;
            this.lam1 = lam1; this.lam2 = lam2; this.tang1 = tang1; this.tang2 = tang2; this.usaTang = usaTang; this.forzar = forzar;
        }

        public static Perfil radioMedio(double rm) { return new Perfil(rm, false, 0.5, 1); }

        public static Perfil radioMedio(double rm, double tm, double suavizado) {
            return new Perfil(rm, false, tm, suavizado);
        }

        /** Radio en tm, relativo: rm = central * (r1 + r2) / 2, con tm = 1/2 y suavizado 1 (modelo anterior). */
        public static Perfil central(double central) { return new Perfil(central, true, 0.5, 1); }

        /** Tangentes rectas: rm = (r1 + r2) / 2. */
        public static Perfil recto() { return central(1); }

        /**
         * Elipses: relación de aspecto lambda = a / b entre el radio tangente al camino (a) y el normal (b, el
         * radio de siempre) en cada extremo, interpolada linealmente en t. lambda = 1 en ambos extremos son
         * círculos (algoritmo de grado 6); si no, se usa el algoritmo de elipses (grado 12). Dominio (0.05, 20),
         * los valores fuera se recortan. Con radio normal 0 en un extremo el aspecto de ese extremo no cuenta.
         */
        public Perfil aspecto(double lambda1, double lambda2) {
            return new Perfil(valor, relativo, tm, suavizado, lambda1, lambda2, 0, 0, false, forzar);
        }

        public Perfil aspecto(double lambda) { return aspecto(lambda, lambda); }

        /**
         * Alternativa cómoda: radios tangentes absolutos a1 y a2 en los extremos. Se convierten en aspecto
         * a / r1 y a / r2 al construir la gota (si el radio normal de un extremo es 0, el aspecto de ese extremo es 1).
         */
        public Perfil radiosTangentes(double a1, double a2) {
            return new Perfil(valor, relativo, tm, suavizado, 1, 1, a1, a2, true, forzar);
        }

        /** Para pruebas: obliga a usar el algoritmo de elipses (con aspecto 1 debe coincidir con el de círculos). */
        Perfil forzarElipses() {
            return new Perfil(valor, relativo, tm, suavizado, lam1, lam2, tang1, tang2, usaTang, true);
        }
    }

    /** Un trozo del camino [t0, t1] con su cúbica de centros y su cuadrática de radio, en base de Bernstein. */
    private static final class Tramo {
        final double t0, t1;
        final double[] a;                          // controles de la cuadrática del radio en el tramo
        final double[] cx6, cy6;                   // c(t) del tramo, en Bernstein de grado 6
        final double[] kExt, kCero, kInt;          // K = |c|^2 - (r + d)^2 para d = +BANDA, 0, -BANDA
        // Elipses (null si son círculos): F = pᵀA p - 2 pᵀB + C0 en Bernstein de grado 12
        double[] eA11, eA12, eA22, eBx, eBy, eC0Ext, eC0Cero;

        Tramo(double t0, double t1, double[] pxs, double[] pys, double[] a) {
            this.t0 = t0; this.t1 = t1; this.a = a;
            cx6 = elevar(pxs, GRADO);
            cy6 = elevar(pys, GRADO);
            double[] c2 = suma(producto(pxs, pxs), producto(pys, pys));
            kExt = k(c2, a, +BANDA);
            kCero = k(c2, a, 0);
            kInt = k(c2, a, -BANDA);
        }

        /**
         * Polinomios de la pertenencia en elipse: (d·D)² + lam²(d·D⊥)² - lam² b² |D|² <= 0 con d = p - c(t),
         * D la dirección del camino (cuadrática, no hace falta normalizarla), lam = a/b lineal y b el radio normal.
         * kappa infla b en la banda exterior (conservador: la envolvente del desplazamiento de una elipse).
         */
        void elipses(double[] pxs, double[] pys, double l0, double l1, double[] dx, double[] dy, double kappa) {
            double[] lam = { l0, l1 };
            double[] lam2 = producto(lam, lam);                          // grado 2
            double[] uno2 = { 1, 1, 1 };
            double[] dx2 = producto(dx, dx), dy2 = producto(dy, dy), dxy = producto(dx, dy);   // grado 4
            double[] a11 = suma(elevar(dx2, 6), producto(lam2, dy2));
            double[] a22 = suma(elevar(dy2, 6), producto(lam2, dx2));
            double[] a12 = producto(resta(uno2, lam2), dxy);
            double[] bx9 = suma(producto(a11, pxs), producto(a12, pys));
            double[] by9 = suma(producto(a12, pxs), producto(a22, pys));
            double[] cac = suma(producto(pxs, bx9), producto(pys, by9));                       // grado 12
            double[] d2 = suma(dx2, dy2);
            eA11 = elevar(a11, GRADO_E); eA12 = elevar(a12, GRADO_E); eA22 = elevar(a22, GRADO_E);
            eBx = elevar(bx9, GRADO_E); eBy = elevar(by9, GRADO_E);
            eC0Cero = c0(cac, lam2, d2, 0);
            eC0Ext = c0(cac, lam2, d2, kappa * BANDA);
        }

        private double[] c0(double[] cac, double[] lam2, double[] d2, double o) {
            double[] bo = { a[0] + o, a[1] + o, a[2] + o };
            double[] t = elevar(producto(lam2, producto(producto(bo, bo), d2)), GRADO_E);
            double[] out = new double[GRADO_E + 1];
            for (int i = 0; i <= GRADO_E; i++) out[i] = cac[i] - t[i];
            return out;
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
    private final double rm, tm, suav;    // perfil efectivo (tras recortar)
    private final Tramo[] tramos;         // 1 tramo (cuadrática) o 2 (partido en tm)
    private final boolean interiorValido;
    private final boolean elipses;          // algoritmo de elipses (grado 12) en vez de círculos (grado 6)
    private final double lam1, lam2;        // aspecto a/b en los extremos
    private final double[] dxq, dyq;        // dirección D(t) del camino: cuadrática regularizada (solo elipses)
    private final Rectangle2D.Double bounds;
    private final int muestras;
    private final Solape solape;
    private final double v1, v2, v3;   // cortes de la tira: fin de la tapa 1, v en t = tm, inicio de la tapa 2

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
                                       perfil.valor, perfil.tm, perfil.suavizado, perfil.lam1, perfil.lam2,
                                       perfil.usaTang ? perfil.tang1 : 0, perfil.usaTang ? perfil.tang2 : 0,
                                       v1.x(), v1.y(), v2.x(), v2.y() })
            if (!Double.isFinite(v)) throw new IllegalArgumentException("parámetros no finitos");
        this.r1 = Math.max(0, ra);
        this.r2 = Math.max(0, rb);
        double rmIn = perfil.relativo ? perfil.valor * (this.r1 + this.r2) / 2 : perfil.valor;
        if (!Double.isFinite(rmIn)) throw new IllegalArgumentException("parámetros no finitos");
        this.rm = Math.max(0, rmIn);
        this.tm = acota(perfil.tm, 0.05, 0.95);
        this.suav = acota(perfil.suavizado, 0, 1);
        double l1 = perfil.usaTang ? (this.r1 > 0 ? perfil.tang1 / this.r1 : 1) : perfil.lam1;
        double l2 = perfil.usaTang ? (this.r2 > 0 ? perfil.tang2 / this.r2 : 1) : perfil.lam2;
        this.lam1 = acota(l1, ASPECTO_MIN, ASPECTO_MAX);
        this.lam2 = acota(l2, ASPECTO_MIN, ASPECTO_MAX);
        this.elipses = perfil.forzar || lam1 != 1 || lam2 != 1;

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

        // Dirección del camino para las elipses: derivada de la cúbica (cuadrática) con los controles nulos
        // sustituidos por el vecino y una pequeña regularización constante en la dirección de la cuerda,
        // de modo que D no se anule nunca (si D = 0 en algún t, el polinomio vale 0 para todo p).
        double[] ddx = new double[3], ddy = new double[3];
        for (int i = 0; i < 3; i++) { ddx[i] = 3 * (px[i + 1] - px[i]); ddy[i] = 3 * (py[i + 1] - py[i]); }
        double mx = 0;
        for (int i = 0; i < 3; i++) mx = Math.max(mx, Math.hypot(ddx[i], ddy[i]));
        // Eje de referencia e: la cuerda; si es nula, el mayor de los controles; si no, el eje x.
        double cx0 = px[3] - px[0], cy0 = py[3] - py[0], cn = Math.hypot(cx0, cy0);
        double ex = 1, ey = 0;
        if (cn > 1e-9 * Math.max(mx, 1e-300) && cn > 0) { ex = cx0 / cn; ey = cy0 / cn; }
        else if (mx > 0) {
            for (int i = 0; i < 3; i++)
                if (Math.hypot(ddx[i], ddy[i]) == mx) { ex = ddx[i] / mx; ey = ddy[i] / mx; break; }
        }
        // Camino sobre una recta (todos los controles paralelos a e): la orientación es e en todo t. Así la
        // elipse no gira ni siquiera donde el camino da la vuelta (D cambia de signo y el polinomio tendría una
        // raíz doble para todo p, que la subdivisión no sabe descartar).
        boolean colineal = true;
        for (int i = 0; i < 3; i++) if (Math.abs(ddx[i] * ey - ddy[i] * ex) > 1e-9 * mx) colineal = false;
        if (colineal) {
            ddx = new double[] { ex, ex, ex };
            ddy = new double[] { ey, ey, ey };
        } else {
            for (int i = 0; i < 3; i++) {
                if (Math.hypot(ddx[i], ddy[i]) > 1e-6 * mx) continue;
                int[] orden = i == 0 ? new int[] { 1, 2 } : i == 2 ? new int[] { 1, 0 } : new int[] { 0, 2 };
                boolean hecho = false;
                for (int j : orden)
                    if (Math.hypot(ddx[j], ddy[j]) > 1e-6 * mx) { ddx[i] = ddx[j]; ddy[i] = ddy[j]; hecho = true; break; }
                if (!hecho) { ddx[i] = ex; ddy[i] = ey; }
            }
            double eps = 1e-5 * mx;
            for (int i = 0; i < 3; i++) { ddx[i] += eps * ex; ddy[i] += eps * ey; }
        }
        dxq = ddx; dyq = ddy;
        if (elipses) {
            double lmin = Math.min(lam1, lam2);
            double smax = Math.max((lam1 + 1) / (2 * Math.sqrt(lam1)), (lam2 + 1) / (2 * Math.sqrt(lam2)));
            double kappa = Math.max(1, 1 / lmin) * smax;
            for (Tramo t : tramos) {
                double[] cxl = px, cyl = py, dxl = dxq, dyl = dyq;
                if (tramos.length == 2) {
                    double[] l = new double[4], r = new double[4], l2c = new double[3], r2c = new double[3];
                    double[] ly = new double[4], ry = new double[4], l2y = new double[3], r2y = new double[3];
                    dividirEn(px, tm, l, r); dividirEn(py, tm, ly, ry);
                    dividirEn(dxq, tm, l2c, r2c); dividirEn(dyq, tm, l2y, r2y);
                    boolean primero = t == tramos[0];
                    cxl = primero ? l : r; cyl = primero ? ly : ry;
                    dxl = primero ? l2c : r2c; dyl = primero ? l2y : r2y;
                }
                t.elipses(cxl, cyl, lam1 + (lam2 - lam1) * t.t0, lam1 + (lam2 - lam1) * t.t1, dxl, dyl, kappa);
            }
        }

        double minX = px[0], maxX = px[0], minY = py[0], maxY = py[0], largo = 0, rc = 0, rmin = Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            minX = Math.min(minX, px[i]); maxX = Math.max(maxX, px[i]);
            minY = Math.min(minY, py[i]); maxY = Math.max(maxY, py[i]);
            if (i > 0) largo += Math.hypot(px[i] - px[i - 1], py[i] - py[i - 1]);
        }
        for (Tramo t : tramos) { rc = Math.max(rc, t.maxControl()); rmin = Math.min(rmin, t.minControl()); }
        interiorValido = !elipses && rmin >= BANDA;   // sin atajo de interior para elipses (el desplazamiento no es una elipse)
        double rmax = rc * (elipses ? Math.max(1, Math.max(lam1, lam2)) : 1) + 1;
        bounds = new Rectangle2D.Double(minX - rmax, minY - rmax, maxX - minX + 2 * rmax, maxY - minY + 2 * rmax);
        muestras = (int) Math.max(32, Math.min(512, largo / 2 + 16));
        solape = Solape.PLANO;
        // v lineal en t a lo largo del cuerpo: el corte central cae donde t = tm (con tm = 1/2, v2 = 1/2)
        this.v1 = 0.25; this.v3 = 0.75; this.v2 = 0.25 + 0.5 * tm;
    }

    private Gota(Gota o, Solape s, double v1, double v2, double v3) {
        px = o.px; py = o.py;
        r1 = o.r1; r2 = o.r2; rm = o.rm; tm = o.tm; suav = o.suav;
        tramos = o.tramos;
        interiorValido = o.interiorValido;
        elipses = o.elipses; lam1 = o.lam1; lam2 = o.lam2; dxq = o.dxq; dyq = o.dyq;
        bounds = o.bounds;
        muestras = o.muestras;
        solape = java.util.Objects.requireNonNull(s, "solape");
        this.v1 = v1; this.v2 = v2; this.v3 = v3;
    }

    /** Copia de esta gota con otro modo de solape (la geometría es idéntica). */
    public Gota conSolape(Solape s) { return new Gota(this, s, v1, v2, v3); }

    /**
     * Copia con otro reparto de la textura en la tira. Los tres pesos son la parte de v que ocupan
     * la tapa trasera, el cuerpo y la tapa delantera (por defecto 1 : 2 : 1). Dentro del cuerpo, v es
     * lineal en t y el corte central cae donde t = tm: tm manda sobre el corte central.
     */
    public Gota conTira(double wTapa1, double wCuerpo, double wTapa2) {
        if (!(wTapa1 > 0) || !(wCuerpo > 0) || !(wTapa2 > 0)
                || !Double.isFinite(wTapa1 + wCuerpo + wTapa2))
            throw new IllegalArgumentException("los pesos de la tira deben ser finitos y > 0");
        double s = wTapa1 + wCuerpo + wTapa2, c1 = wTapa1 / s, c3 = (wTapa1 + wCuerpo) / s;
        return new Gota(this, solape, c1, c1 + (c3 - c1) * tm, c3);
    }

    /** Copia con la tira repartida según la longitud real: tapas de longitud r1 y r2, cuerpo del largo del camino. */
    public Gota conTiraProporcional() {
        double largo = 0;
        Point2D prev = centro(0);
        for (int i = 1; i <= 400; i++) {
            Point2D c = centro(i / 400.0);
            largo += prev.distance(c);
            prev = c;
        }
        double mini = 1e-3 * (r1 + r2 + largo + 1e-9);
        return conTira(Math.max(r1, mini), Math.max(largo, mini), Math.max(r2, mini));
    }

    public Solape solape() { return solape; }

    // ---------- Consultas ----------

    public Rectangle2D bounds() { return (Rectangle2D) bounds.clone(); }

    /** Radio efectivo en tm (tras recortar a >= 0). */
    public double rm() { return rm; }

    /** Posición efectiva de rm sobre el camino (tras recortar a [0.05, 0.95]); es también el corte central de la tira. */
    public double tm() { return tm; }

    /** Suavizado efectivo del perfil, en [0, 1]. */
    public double suavizado() { return suav; }

    /** Corte de la tira entre la tapa trasera y el cuerpo: v en el centro del primer círculo. */
    public double corte1() { return v1; }

    /** Corte central de la tira: v en t = tm (siempre sigue a tm). */
    public double corte2() { return v2; }

    /** Corte de la tira entre el cuerpo y la tapa delantera: v en el centro del segundo círculo. */
    public double corte3() { return v3; }

    /** true si la gota usa elipses (algún aspecto distinto de 1); con false son círculos. */
    public boolean esElipse() { return elipses; }

    /** Aspecto a/b en t (1 en las gotas de círculos). */
    public double aspecto(double t) { return elipses ? lam(t) : 1; }

    /** Radio tangente al camino en t: aspecto(t) · radio(t). */
    public double radioTangente(double t) { return aspecto(t) * radio(t); }

    public Point2D centro(double t) { return new Point2D.Double(cx(t), cy(t)); }

    public double radio(double t) { return rad(t); }

    /** Pertenencia exacta (a precisión de punto flotante). */
    public boolean contiene(double x, double y) { return dentro(x, y, CERO); }

    /**
     * Distancia con signo. Exacta fuera de la gota. Dentro es la profundidad respecto al
     * mejor círculo de la familia: exacta cerca del borde, aproximada (por defecto) en el interior profundo.
     */
    public double sdf(double x, double y) { return minimizar(x, y, false)[1]; }

    public Muestra parametros(double x, double y) {
        if (solape == Solape.TRAZO) {   // la capa más tardía que contiene el punto
            java.util.List<double[]> hs = hebras(x, y, 0);
            if (!hs.isEmpty()) {
                return muestraEn(x, y, hs.get(hs.size() - 1)[0]);
            }
        }
        double t = minimizar(x, y, true)[0];
        return muestraEn(x, y, t);
    }

    /** Coordenadas de textura de la tira en el punto (valores en [0, 1] dentro de la gota). */
    public Uv uv(double x, double y) {
        return uvEn(x, y, parametros(x, y).t());
    }

    // ---------- Render ----------

    /** Pinta sobre dst (TYPE_INT_ARGB) con antialiasing y composición alfa. */
    public void render(BufferedImage dst, Relleno relleno) {
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
        final Relleno rel = relleno.para(this);
        final boolean usaUv = rel.usaUv();
        final boolean usa = rel.usaParametros();

        IntStream.range(y0, y1).parallel().forEach(j -> {
            for (int i = x0; i < x1; i++) {
                double x = i + 0.5, y = j + 0.5;
                final int idx = base + j * stride + i;
                if (!dentro(x, y, EXT)) continue;                    // a más de BANDA del borde: fuera
                if (solape == Solape.TRAZO) {
                    datos[idx] = pintaTrazo(datos[idx], x, y, rel, usa, usaUv);
                    continue;
                }
                double cob;
                if (interiorValido && dentro(x, y, INT)) cob = 1;     // a más de BANDA de profundidad
                else cob = Math.max(0, Math.min(1, 0.5 - sdf(x, y)));  // franja del borde
                if (cob <= 0) continue;
                int color;
                if (usaUv) {
                    Muestra m = parametros(x, y);
                    color = rel.argb(m, uvEn(x, y, m.t()));
                } else if (usa) {
                    color = rel.argb(parametros(x, y));
                } else {
                    color = rel.argb(0, 0);
                }
                datos[idx] = componer(datos[idx], color, cob);
            }
        });
    }

    // ---------- Pertenencia en base de Bernstein ----------

    private boolean dentro(double x, double y, int banda) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) return false;   // con NaN la subdivisión nunca decide
        if (elipses) return dentroElipses(x, y, banda);
        double pp = x * x + y * y;
        double[] b = new double[GRADO + 1];
        for (Tramo t : tramos) {
            double[] K = banda == EXT ? t.kExt : banda == INT ? t.kInt : t.kCero;
            for (int i = 0; i <= GRADO; i++) b[i] = pp - 2 * (x * t.cx6[i] + y * t.cy6[i]) + K[i];
            if (hayNoPositivo(b, PROF_MAX)) return true;
        }
        return false;
    }

    /** Pertenencia con elipses: banda EXT usa la elipse inflada (conservadora), CERO la exacta. INT no se usa. */
    private boolean dentroElipses(double x, double y, int banda) {
        double[] b = new double[GRADO_E + 1];
        double xx = x * x, xy2 = 2 * x * y, yy = y * y;
        for (Tramo t : tramos) {
            double[] c0 = banda == EXT ? t.eC0Ext : t.eC0Cero;
            for (int i = 0; i <= GRADO_E; i++)
                b[i] = xx * t.eA11[i] + xy2 * t.eA12[i] + yy * t.eA22[i] - 2 * (x * t.eBx[i] + y * t.eBy[i]) + c0[i];
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

    /**
     * f en el muestreo grueso. Usa la misma f exacta que el refinado: el muestreo con la distancia aproximada de
     * Sampson daba mínimos erróneos con elipses. Sampson queda solo como alternativa de distanciaElipse.
     */
    private double fMuestreo(double x, double y, double t, boolean porRho) {
        return f(x, y, t, porRho);
    }

    private double f(double x, double y, double t, boolean porRho) {
        if (elipses) return fElipse(x, y, t, porRho, true);
        double dx = x - cx(t), dy = y - cy(t);
        double d = Math.sqrt(dx * dx + dy * dy), r = rad(t);
        return porRho ? d / Math.max(r, 1e-9) : d - r;
    }

    /** Devuelve {t, valor} del mínimo global de f en [0,1]: muestreo grueso + refinado. */
    private double[] minimizar(double x, double y, boolean porRho) {
        int n = muestras;
        double[] g = new double[n + 1];
        for (int i = 0; i <= n; i++) g[i] = fMuestreo(x, y, i / (double) n, porRho);
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
    private java.util.List<double[]> hebras(double x, double y, double limite) {
        int n = muestras;
        double[] g = new double[n + 1];
        for (int i = 0; i <= n; i++) g[i] = fMuestreo(x, y, i / (double) n, false);
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (int i = 0; i <= n; i++) {
            boolean izq = i == 0 || g[i] < g[i - 1];     // '<' a un lado: una sola muestra por meseta
            boolean der = i == n || g[i] <= g[i + 1];
            if (!(izq && der)) continue;
            double a = Math.max(i - 1, 0) / (double) n, b = Math.min(i + 1, n) / (double) n;
            double[] m = dorada(x, y, a, b, false);
            double t = i / (double) n, v = g[i];
            if (m[1] < v) { t = m[0]; v = m[1]; }
            if (v < limite) out.add(new double[] { ajusta(t), v });
        }
        return out;
    }

    private int pintaTrazo(int dst, double x, double y, Relleno relleno, boolean usa, boolean usaUv) {
        for (double[] hb : hebras(x, y, BANDA)) {        // t creciente: la posterior tapa a la anterior
            double cob = Math.max(0, Math.min(1, 0.5 - hb[1]));
            if (cob <= 0) continue;
            int color;
            if (usaUv) {
                color = relleno.argb(muestraEn(x, y, hb[0]), uvEn(x, y, hb[0]));
            } else if (usa) {
                color = relleno.argb(muestraEn(x, y, hb[0]));
            } else {
                color = relleno.argb(0, 0);
            }
            dst = componer(dst, color, cob);
        }
        return dst;
    }

    // ---------- Muestra completa en un círculo de la familia ----------

    // ---------- Elipses: orientación, aspecto y distancia ----------

    /** Aspecto a/b en t (lineal). */
    private double lam(double t) { return lam1 + (lam2 - lam1) * t; }

    private double ddx(double t) { double u = 1 - t; return u * u * dxq[0] + 2 * u * t * dxq[1] + t * t * dxq[2]; }
    private double ddy(double t) { double u = 1 - t; return u * u * dyq[0] + 2 * u * t * dyq[1] + t * t * dyq[2]; }

    /** Eje tangente unitario de la elipse en t (la dirección regularizada del camino). */
    private double[] dirD(double t) {
        double x = ddx(t), y = ddy(t), n = Math.hypot(x, y);
        return n > 0 ? new double[] { x / n, y / n } : new double[] { 1, 0 };
    }

    /**
     * Valor de la familia en t para elipses: ρ_e (norma elíptica, igual que ρ si son círculos) o la distancia
     * con signo a la elipse (exacta, o aproximada de primer orden si exacto es false).
     */
    private double fElipse(double x, double y, double t, boolean porRho, boolean exacto) {
        double dx = x - cx(t), dy = y - cy(t);
        double[] d = dirD(t);
        double u = dx * d[0] + dy * d[1], v = dx * d[1] - dy * d[0];
        double b = rad(t), a = lam(t) * b;
        if (porRho) return Math.hypot(u / Math.max(a, 1e-9), v / Math.max(b, 1e-9));
        if (b <= 1e-12) return Math.hypot(u, v);
        return exacto ? distElipse(a, b, u, v) : sampson(a, b, u, v);
    }

    /** Distancia con signo aproximada (de primer orden) a la elipse de semiejes a, b, para un punto (u, v). */
    private static double sampson(double a, double b, double u, double v) {
        double q = (u / a) * (u / a) + (v / b) * (v / b);
        double gx = u / (a * a), gy = v / (b * b);
        double g = 2 * Math.sqrt(gx * gx + gy * gy);
        if (g < 1e-12) return -Math.min(a, b);
        return (q - 1) / g;
    }

    /**
     * Distancia con signo (negativa dentro) de (u, v) a la elipse (x/a)² + (y/b)² = 1; raíz por bisección
     * (Eberly, "Distance from a point to an ellipse"). Exacta a precisión de punto flotante.
     */
    static double distElipse(double a, double b, double u, double v) {
        double e0 = a, e1 = b, y0 = Math.abs(u), y1 = Math.abs(v);
        if (e0 < e1) { double s = e0; e0 = e1; e1 = s; s = y0; y0 = y1; y1 = s; }
        if (e1 <= 1e-12) return Math.hypot(Math.max(y0 - e0, 0), y1);
        double z0 = y0 / e0, z1 = y1 / e1, g = z0 * z0 + z1 * z1 - 1;
        double dist;
        if (y1 > 0) {
            if (y0 > 0) {
                if (g == 0) return 0;
                double r0 = (e0 / e1) * (e0 / e1), n0 = r0 * z0;
                double s0 = z1 - 1, s1 = g < 0 ? 0 : Math.hypot(n0, z1) - 1, s = 0;
                for (int i = 0; i < 400; i++) {
                    s = 0.5 * (s0 + s1);
                    if (s == s0 || s == s1) break;
                    double ra = n0 / (s + r0), rb = z1 / (s + 1), gg = ra * ra + rb * rb - 1;
                    if (gg > 0) s0 = s; else if (gg < 0) s1 = s; else break;
                }
                double x0 = r0 * y0 / (s + r0), x1 = y1 / (s + 1);
                dist = Math.hypot(x0 - y0, x1 - y1);
            } else {
                dist = Math.abs(y1 - e1);
            }
        } else {
            double num = e0 * y0, den = e0 * e0 - e1 * e1;
            if (num < den) {
                double xde = num / den, x0 = e0 * xde, x1 = e1 * Math.sqrt(1 - xde * xde);
                dist = Math.hypot(x0 - y0, x1);
            } else {
                dist = Math.abs(y0 - e0);
            }
        }
        return g < 0 ? -dist : dist;
    }

    /** Tangente unitaria del camino en t; si la derivada es nula usa la secante, y si no, el eje x. */
    private double[] tangente(double t) {
        if (elipses) return dirD(t);
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

    private Muestra muestraEn(double x, double y, double t) {
        double r = Math.max(rad(t), 1e-9);
        double dx = x - cx(t), dy = y - cy(t);
        double[] tg = tangente(t);
        if (elipses) {
            double ax = (dx * tg[0] + dy * tg[1]) / (r * lam(t)), la = (dx * tg[1] - dy * tg[0]) / r;
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

    private Uv uvEn(double x, double y, double t) {
        if (elipses) return uvElipse(x, y, t);
        double eps = 2.0 / muestras;
        if (t <= eps) {
            Uv c = tapa(x, y, 0);
            if (c != null) return c;
        } else if (t >= 1 - eps) {
            Uv c = tapa(x, y, 1);
            if (c != null) return c;
        }
        double[] m = marco(x, y, t);
        double u = 0.5 + 0.5 * acota(m[1] / Math.sqrt(1 - m[3] * m[3]), -1, 1);
        double v = t < tm ? v1 + (v2 - v1) * t / tm : v2 + (v3 - v2) * (t - tm) / (1 - tm);
        return new Uv(u, acota(v, 0, 1));
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
        double tt = t, vx = dcx(tt), vy = dcy(tt);
        if (Math.hypot(vx, vy) < 1e-9) {
            tt = t < 0.5 ? t + 1e-3 : t - 1e-3;
            vx = dcx(tt); vy = dcy(tt);
        }
        double dx = ddx(tt), dy = ddy(tt), n = Math.hypot(dx, dy);
        double tx = dx / n, ty = dy / n;
        double vT = vx * tx + vy * ty, vN = vx * ty - vy * tx;
        double u = 1 - tt;
        double dxp = 2 * (u * (dxq[1] - dxq[0]) + tt * (dxq[2] - dxq[1]));
        double dyp = 2 * (u * (dyq[1] - dyq[0]) + tt * (dyq[2] - dyq[1]));
        double omega = (dx * dyp - dy * dxp) / (n * n);
        double b = Math.max(rad(t), 1e-9);
        double bpv = Double.isNaN(bp) ? drad(tt) : bp;
        double lamv = lam(t), ap = (lam2 - lam1) * b + lamv * bpv;
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
        double ae = acota(A, lo, hi);
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
        if (solape == Solape.TRAZO) {   // regla de las hebras: el signo se evalúa en el pie sobre la elipse
            double[] f = pieElipse(d[6], d[5], A, S);
            qa = f[0]; qs = f[1];
        }
        double Q = qE(d, qa, qs);
        return fin == 0 ? Q >= 0 : Q <= 0;
    }

    private Uv tapa(double x, double y, int fin, boolean elipse) {
        double t = fin, b = rad(t);
        if (b <= 1e-9) return null;
        double[] d = dinamica(t, Double.NaN);
        double a = d[6];
        double[] T = dirD(t);
        double dx = x - cx(t), dy = y - cy(t);
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
        double w = den > 1e-9 ? acota((dep - dIn) / den, 0, 1) : 1;
        double[][] c = contactos(d);
        double u = 0.5 + 0.5 * acota(S / extension(c, S >= 0 ? 0 : 1, b), -1, 1);
        double v = fin == 0 ? v1 * (1 - w) : v3 + (1 - v3) * w;
        return new Uv(u, acota(v, 0, 1));
    }

    private Uv uvElipse(double x, double y, double t) {
        double eps = 2.0 / muestras;
        if (t <= eps) {
            Uv c = tapa(x, y, 0, true);
            if (c != null) return c;
        } else if (t >= 1 - eps) {
            Uv c = tapa(x, y, 1, true);
            if (c != null) return c;
        }
        double b = Math.max(rad(t), 1e-9), a = lam(t) * b;
        double[] T = dirD(t);
        double dx = x - cx(t), dy = y - cy(t);
        double A = dx * T[0] + dy * T[1], S = dx * T[1] - dy * T[0];
        double ext;
        if (tramos.length == 2 && t == tm) {
            double[][] ci = contactos(dinamica(t, tramos[0].drad(tm))), cd = contactos(dinamica(t, tramos[1].drad(tm)));
            ext = extensionCodo(ci[S >= 0 ? 0 : 1], cd[S >= 0 ? 0 : 1], A, a, b);
        } else {
            ext = extension(contactos(dinamica(t, Double.NaN)), S >= 0 ? 0 : 1, b);
        }
        double u = 0.5 + 0.5 * acota(S / ext, -1, 1);
        double v = t < tm ? v1 + (v2 - v1) * t / tm : v2 + (v3 - v2) * (t - tm) / (1 - tm);
        return new Uv(u, acota(v, 0, 1));
    }

    /** Ángulo (radianes) del eje tangente de la familia en t, medido desde +x hacia +y. */
    public double angulo(double t) {
        double[] d = tangente(t);
        return Math.atan2(d[1], d[0]);
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
        boolean trazo = solape == Solape.TRAZO;
        double e = b - kk * (trazo ? rho : rho * rho);
        if (e < 0) return null;
        double bb = trazo
                ? kk * Math.abs(s) / Math.sqrt(1 - kk * kk)
                : (kk == 0 ? 0 : (1 - Math.sqrt(Math.max(0, 1 - 4 * kk * kk * s * s))) / (2 * kk));
        double bo = Math.sqrt(Math.max(0, 1 - s * s));
        double den = bo - bb;
        double w = den > 1e-9 ? acota((b - bb) / den, 0, 1) : 1;
        double u = 0.5 + 0.5 * acota(s / Math.sqrt(1 - k * k), -1, 1);
        double v = fin == 0 ? v1 * (1 - w) : v3 + (1 - v3) * w;
        return new Uv(u, acota(v, 0, 1));
    }

    /** Marco del círculo en t: {avance, lateral, rho, k}; avance y lateral normalizados por r, k = r'/|c'|. */
    private double[] marco(double x, double y, double t) {
        double r = Math.max(rad(t), 1e-9);
        double dx = x - cx(t), dy = y - cy(t);
        double tt = t;
        double vx = elipses ? ddx(tt) : dcx(tt), vy = elipses ? ddy(tt) : dcy(tt), sp = Math.hypot(vx, vy);
        if (!elipses && sp < 1e-9) {             // tirador nulo: dirección tomada un poco más adentro
            tt = t < 0.5 ? t + 1e-3 : t - 1e-3;
            vx = dcx(tt); vy = dcy(tt); sp = Math.hypot(vx, vy);
        }
        double tx = 1, ty = 0, k = 0;
        if (elipses) {
            // la orientación sale de D (regularizada); la pendiente k = r'/|c'| usa la velocidad real del camino
            tx = vx / sp; ty = vy / sp;
            double spk = Math.hypot(dcx(tt), dcy(tt));
            if (spk < 1e-9) { tt = t < 0.5 ? t + 1e-3 : t - 1e-3; spk = Math.hypot(dcx(tt), dcy(tt)); }
            if (spk > 1e-9) k = drad(tt) / spk;
            sp = spk > 1e-9 ? spk : 0;
        } else if (sp > 1e-9) { tx = vx / sp; ty = vy / sp; k = drad(tt) / sp; }
        double axial = (dx * tx + dy * ty) / (elipses ? r * lam(t) : r);   // con elipses, normalizado por el radio tangente a
        if (sp > 1e-9 && tramos.length == 2 && tt == tm) {
            // Codo del perfil: el círculo central aporta un arco al contorno, cuyo contacto va de
            // axial = -kIzq a axial = -kDer. Dentro del arco, k es el que cumple axial = -k.
            double kI = tramos[0].drad(tm) / sp, kD = tramos[1].drad(tm) / sp;
            k = acota(-axial, Math.min(kI, kD), Math.max(kI, kD));
        }
        k = acota(k, -0.999, 0.999);
        double lat = (dx * ty - dy * tx) / r;
        return new double[] { axial, lat, elipses ? Math.hypot(axial, lat) : Math.hypot(dx, dy) / r, k };
    }

    private static double acota(double v, double lo, double hi) { return v < lo ? lo : (v > hi ? hi : v); }

    private double dcx(double t) { double u = 1 - t; return 3 * (u*u*(px[1]-px[0]) + 2*u*t*(px[2]-px[1]) + t*t*(px[3]-px[2])); }
    private double dcy(double t) { double u = 1 - t; return 3 * (u*u*(py[1]-py[0]) + 2*u*t*(py[2]-py[1]) + t*t*(py[3]-py[2])); }
    private double drad(double t) { return tramoDe(t).drad(t); }

    // ---------- Evaluación directa ----------

    private double cx(double t) { double u = 1 - t; return u*u*u*px[0] + 3*u*u*t*px[1] + 3*u*t*t*px[2] + t*t*t*px[3]; }
    private double cy(double t) { double u = 1 - t; return u*u*u*py[0] + 3*u*u*t*py[1] + 3*u*t*t*py[2] + t*t*t*py[3]; }
    private double rad(double t) { return tramoDe(t).rad(t); }

    /** Tramo del perfil que contiene t (en el codo, el izquierdo). */
    private Tramo tramoDe(double t) { return tramos.length == 1 || t <= tm ? tramos[0] : tramos[1]; }

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
