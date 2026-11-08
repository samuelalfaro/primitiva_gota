/**
 * Cómo varía el radio de la familia de círculos a lo largo del camino de una {@link Gota}.
 * <p>
 * Radio: rm en la posición tm del camino, y suavizado. rm >= 0 (mismo dominio que los radios), tm en [0.05, 0.95]
 * (por defecto 1/2), suavizado en [0, 1]. Con rm = (r1 + r2)/2 y tm = 1/2 las tangentes del contorno son rectas; por
 * encima la gota se hincha y por debajo toma forma de guitarra. Suavizado 1 (por defecto): el radio es una única
 * cuadrática por (0, r1), (tm, rm), (1, r2). Suavizado 0: el radio va en línea recta de r1 a rm y de rm a r2, con un
 * codo en tm (el contorno sigue siendo tangente-continuo: en tm aparece un arco del propio círculo central). Entre
 * ambos, la pendiente en tm de cada lado se interpola. Los valores fuera de dominio se recortan; los no finitos se
 * rechazan.
 * <p>
 * Un perfil es inmutable y no depende de ninguna gota: {@link #resuelve} lo aplica a los radios de los extremos.
 */
public final class Perfil {
    private final double valor, tm, suavizado;
    private final boolean relativo;

    /** Perfil aplicado a los radios de los extremos: valores efectivos, ya validados y recortados a su dominio. */
    record Resuelto(double rm, double tm, double suavizado) {}

    private Perfil(double valor, boolean relativo, double tm, double suavizado) {
        this.valor = valor; this.relativo = relativo; this.tm = tm; this.suavizado = suavizado;
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
     * Aplica el perfil a los radios de los extremos (ya >= 0): rm absoluto, tm y suavizado recortados. Lanza IllegalArgumentException si algún valor no es finito.
     */
    Resuelto resuelve(double r1, double r2) {
        for (double v : new double[] { valor, tm, suavizado })
            if (!Double.isFinite(v)) throw new IllegalArgumentException("parámetros no finitos");
        double rmIn = relativo ? valor * (r1 + r2) / 2 : valor;
        if (!Double.isFinite(rmIn)) throw new IllegalArgumentException("parámetros no finitos");
        return new Resuelto(Math.max(0, rmIn), Utils.acota(tm, 0.05, 0.95), Utils.acota(suavizado, 0, 1));
    }
}
