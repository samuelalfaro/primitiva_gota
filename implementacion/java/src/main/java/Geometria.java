/**
 * Consulta de solo lectura de la geometría de una gota: el camino (una Bézier cúbica de centros), los radios
 * a lo largo del parámetro t en [0, 1] y la pertenencia de un punto.
 * Es lo único que necesitan {@link Coordenadas} y las zonas de la tira para repartir el interior de la gota, y también lo que
 * necesitaría cualquier otro dibujo de la primitiva (por ejemplo, un modo de alambre).
 * Todas las coordenadas están en el espacio de la imagen.
 */
public interface Geometria {
    /** Centro del círculo de la familia en t. */
    double cx(double t);
    double cy(double t);

    /** Derivada del centro respecto a t (velocidad sobre el camino). */
    double dcx(double t);
    double dcy(double t);

    /** Radio en t y su derivada. */
    double rad(double t);
    double drad(double t);

    /** Tangente unitaria del camino en t. */
    double[] tangente(double t);

    /** Posición efectiva de rm sobre el camino: es también donde cae el corte central de la tira. */
    double tm();

    /** Pertenencia exacta de un punto a la gota. */
    boolean contiene(double x, double y);

    /** Una pasada del camino sobre un punto: su parámetro t y la distancia con signo al borde de esa pasada. */
    record Hebra(double t, double distancia) {
        /** Fracción del píxel que cubre la pasada (antialiasing de medio píxel a cada lado del borde). */
        public double cobertura() { return Math.max(0, Math.min(1, 0.5 - distancia)); }
    }

    /** Fracción del píxel centrado en (x, y) que cubre la unión de la gota, en [0, 1]. */
    double cobertura(double x, double y);

    /** Las pasadas del camino que cubren (aunque sea en parte) el píxel (x, y), en orden de t creciente. */
    java.util.List<Hebra> hebras(double x, double y);

    // ---------- Minimización numérica de |p - c(t)| - r(t) (distancia con signo y t del círculo ganador) ----------

    /** Muestras del barrido grueso de la minimización (crece con el largo del camino). */
    default int muestras() { return 64; }

    /** true si el perfil del radio tiene un codo en tm (dos cuadráticas): un mínimo que cae a un error numérico de tm se fija en tm. */
    default boolean tieneCodo() { return false; }

    /**
     * Distancia con signo al borde de la unión de la familia de círculos. Exacta fuera; dentro es la profundidad respecto al
     * mejor círculo de la familia: exacta cerca del borde, aproximada (por defecto) en el interior profundo.
     */
    default double distancia(double x, double y) { return minimizar(x, y, false)[1]; }

    /** Devuelve {t, valor} del mínimo global en [0, 1] de |p - c(t)| - r(t), o de |p - c(t)| / r(t) con {@code porRho}: muestreo grueso y refinado. */
    default double[] minimizar(double x, double y, boolean porRho) {
        int n = muestras();
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

    /**
     * Mínimos locales de g(t) = |p - c(t)| - r(t) con g < limite, como {t, g} en orden de t creciente (el modo TRAZO).
     * Cada uno es un tramo del camino que pasa cerca de p.
     */
    default java.util.List<Hebra> hebras(double x, double y, double limite) {
        int n = muestras();
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

    private double f(double x, double y, double t, boolean porRho) {
        double dx = x - cx(t), dy = y - cy(t);
        double d = Math.sqrt(dx * dx + dy * dy), r = rad(t);
        return porRho ? d / Math.max(r, 1e-9) : d - r;
    }

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

    /** Si el mínimo cae a un error numérico de tm (codo del perfil), lo fija en tm exactamente. */
    private double ajusta(double t) { return tieneCodo() && Math.abs(t - tm()) < 1e-7 ? tm() : t; }
}
