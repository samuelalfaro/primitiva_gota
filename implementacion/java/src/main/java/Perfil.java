/**
 * Cómo varían el radio y el aspecto de la familia de círculos (o elipses) a lo largo del camino de una {@link Gota}.
 * <p>
 * Radio: rm en la posición tm del camino, y suavizado. rm >= 0 (mismo dominio que los radios), tm en [0.05, 0.95]
 * (por defecto 1/2), suavizado en [0, 1]. Con rm = (r1 + r2)/2 y tm = 1/2 las tangentes del contorno son rectas; por
 * encima la gota se hincha y por debajo toma forma de guitarra. Suavizado 1 (por defecto): el radio es una única
 * cuadrática por (0, r1), (tm, rm), (1, r2). Suavizado 0: el radio va en línea recta de r1 a rm y de rm a r2, con un
 * codo en tm (el contorno sigue siendo tangente-continuo: en tm aparece un arco del propio círculo central). Entre
 * ambos, la pendiente en tm de cada lado se interpola. Los valores fuera de dominio se recortan; los no finitos se
 * rechazan.
 * <p>
 * Aspecto (elipses): ver {@link #aspecto(double, double)} y {@link #radiosTangentes(double, double)}.
 * <p>
 * Un perfil es inmutable y no depende de ninguna gota: {@link #resuelve} lo aplica a los radios de los extremos.
 */
public final class Perfil {
    private static final double ASPECTO_MIN = 0.05, ASPECTO_MAX = 20;

    private final double valor, tm, suavizado;
    private final boolean relativo;
    private final double lam1, lam2;          // aspecto a/b en los extremos (1 = círculo)
    private final double tang1, tang2;        // radios tangentes absolutos (solo si usaTang)
    private final boolean usaTang, forzar;    // pruebas: usar el algoritmo de elipses aunque sean círculos

    /** Perfil aplicado a los radios de los extremos: valores efectivos, ya validados y recortados a su dominio. */
    record Resuelto(double rm, double tm, double suavizado, double lam1, double lam2, boolean elipses) {}

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
     * a / r1 y a / r2 al aplicar el perfil a los radios (si el radio normal de un extremo es 0, el aspecto de ese extremo es 1).
     */
    public Perfil radiosTangentes(double a1, double a2) {
        return new Perfil(valor, relativo, tm, suavizado, 1, 1, a1, a2, true, forzar);
    }

    /** Para pruebas: obliga a usar el algoritmo de elipses (con aspecto 1 debe coincidir con el de círculos). */
    Perfil forzarElipses() {
        return new Perfil(valor, relativo, tm, suavizado, lam1, lam2, tang1, tang2, usaTang, true);
    }

    /**
     * Aplica el perfil a los radios de los extremos (ya >= 0): rm absoluto, tm y suavizado recortados, aspectos
     * en los extremos y si hacen falta elipses. Lanza IllegalArgumentException si algún valor no es finito.
     */
    Resuelto resuelve(double r1, double r2) {
        for (double v : new double[] { valor, tm, suavizado, lam1, lam2, usaTang ? tang1 : 0, usaTang ? tang2 : 0 })
            if (!Double.isFinite(v)) throw new IllegalArgumentException("parámetros no finitos");
        double rmIn = relativo ? valor * (r1 + r2) / 2 : valor;
        if (!Double.isFinite(rmIn)) throw new IllegalArgumentException("parámetros no finitos");
        double l1 = usaTang ? (r1 > 0 ? tang1 / r1 : 1) : lam1;
        double l2 = usaTang ? (r2 > 0 ? tang2 / r2 : 1) : lam2;
        double a1 = Utils.acota(l1, ASPECTO_MIN, ASPECTO_MAX), a2 = Utils.acota(l2, ASPECTO_MIN, ASPECTO_MAX);
        return new Resuelto(Math.max(0, rmIn), Utils.acota(tm, 0.05, 0.95), Utils.acota(suavizado, 0, 1),
                            a1, a2, forzar || a1 != 1 || a2 != 1);
    }
}
