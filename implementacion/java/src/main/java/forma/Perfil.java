package forma;

import utils.Utils;

/**
 * Cómo varía el radio de la familia de círculos a lo largo del camino de una {@link Gota}.
 * <p>
 * El radio es una cúbica de Bézier en t, r(t), con r(0) = r1 y r(1) = r2, que no se sale del intervalo [r1, r2]
 * (monótona). Se define por sus tangentes en los extremos, las pendientes dr/dt en t = 0 y t = 1:
 * <ul>
 *   <li>{@link #lineal()} (por defecto): sin más datos, el radio va en línea recta de r1 a r2 (pendiente r2 - r1 en los dos extremos).</li>
 *   <li>{@link #tangentes(double, double)}: pendientes en los extremos, que se recortan para conservar la monotonía: entre 0 y
 *       3·(r2 - r1) (del mismo signo que r2 - r1; con r1 = r2 el radio es constante). Con pendiente 0 el radio llega o sale
 *       del extremo con suavidad (el contorno es tangente al círculo de ese extremo); con 3·(r2 - r1) lo hace de golpe.</li>
 * </ul>
 * Como r(t) queda entre r1 y r2 no hay radios negativos, y su cuadrado sigue siendo un polinomio de grado 6 igual que el de
 * la distancia al camino, de modo que la envolvente ({@link Silueta.Envolvente}) se evalúa igual que siempre.
 * <p>
 * Un perfil es inmutable y no depende de ninguna gota: {@link #resuelve} lo aplica a los radios de los extremos.
 */
public final class Perfil {
    private enum Tipo { LINEAL, TANGENTES, MEDIO }

    private final Tipo tipo;
    private final double a, b, c;           // según el tipo: (m1, m2) | (rm, tm, relativo) | (q)
    private final boolean relativo;

    /** Perfil aplicado a los radios de los extremos: los cuatro controles de Bézier de r(t), ya validados. */
    record Resuelto(double[] controles) {
        double r(double t) {
            double u = 1 - t;
            return u * u * u * controles[0] + 3 * u * u * t * controles[1] + 3 * u * t * t * controles[2] + t * t * t * controles[3];
        }
        double dr(double t) {
            double u = 1 - t;
            return 3 * (u * u * (controles[1] - controles[0]) + 2 * u * t * (controles[2] - controles[1]) + t * t * (controles[3] - controles[2]));
        }
    }

    private Perfil(Tipo tipo, double a, double b, double c, boolean relativo) {
        this.tipo = tipo; this.a = a; this.b = b; this.c = c; this.relativo = relativo;
    }

    /** El radio va en línea recta de r1 a r2 (las tangentes del contorno son rectas cuando el camino es recto). */
    public static Perfil lineal() { return new Perfil(Tipo.LINEAL, 0, 0, 0, false); }

    /**
     * Radio cúbico monótono con las pendientes dr/dt dadas en t = 0 y t = 1. Se recortan al intervalo que mantiene el
     * radio entre r1 y r2: [0, 3·(r2 - r1)] (o [3·(r2 - r1), 0] si r2 < r1).
     *
     * @throws IllegalArgumentException si alguna pendiente no es finita (al resolver el perfil)
     */
    public static Perfil tangentes(double pendiente1, double pendiente2) {
        return new Perfil(Tipo.TANGENTES, pendiente1, pendiente2, 0, false);
    }

    /** Igual que {@link #lineal()}: tangentes rectas. */
    public static Perfil recto() { return lineal(); }

    /**
     * Radio rm en la posición tm del camino: la cuadrática por (0, r1), (tm, rm), (1, r2) elevada a cúbica. rm >= 0, tm en [0.05, 0.95].
     *
     * @deprecated tm desaparece: el perfil es una cúbica monótona definida por sus tangentes ({@link #tangentes}).
     */
    @Deprecated
    public static Perfil radioMedio(double rm) { return radioMedio(rm, 0.5, 1); }

    /**
     * Igual que {@link #radioMedio(double)} con tm y suavizado. El suavizado ya no tiene efecto (siempre una sola curva) y se ignora.
     *
     * @deprecated tm y el suavizado desaparecen: el perfil es una cúbica monótona definida por sus tangentes ({@link #tangentes}).
     */
    @Deprecated
    public static Perfil radioMedio(double rm, double tm, double suavizado) { return new Perfil(Tipo.MEDIO, rm, tm, 0, false); }

    /**
     * Radio en tm = 1/2, relativo: rm = central * (r1 + r2) / 2.
     *
     * @deprecated el perfil es una cúbica monótona definida por sus tangentes ({@link #tangentes}).
     */
    @Deprecated
    public static Perfil central(double central) { return new Perfil(Tipo.MEDIO, central, 0.5, 0, true); }

    /**
     * Aplica el perfil a los radios de los extremos (ya >= 0). Lanza IllegalArgumentException si algún valor no es finito.
     */
    Resuelto resuelve(double r1, double r2) {
        for (double v : new double[] { a, b, c })
            if (!Double.isFinite(v)) throw new IllegalArgumentException("parámetros no finitos");
        double d = r2 - r1;
        switch (tipo) {
            case TANGENTES: {
                double lim = 3 * Math.abs(d), s = Math.signum(d);
                double m1 = s * Utils.acota(s * a, 0, lim), m2 = s * Utils.acota(s * b, 0, lim);
                return new Resuelto(new double[] { r1, r1 + m1 / 3, r2 - m2 / 3, r2 });
            }
            case MEDIO: {
                double rm = relativo ? a * (r1 + r2) / 2 : a;
                if (!Double.isFinite(rm)) throw new IllegalArgumentException("parámetros no finitos");
                double tm = Utils.acota(b, 0.05, 0.95), w = 1 - tm;
                double q = Math.max(0, (Math.max(0, rm) - w * w * r1 - tm * tm * r2) / (2 * tm * w));
                return new Resuelto(new double[] { r1, (r1 + 2 * q) / 3, (2 * q + r2) / 3, r2 });
            }
            default:
                return new Resuelto(new double[] { r1, r1 + d / 3, r1 + 2 * d / 3, r2 });
        }
    }
}
