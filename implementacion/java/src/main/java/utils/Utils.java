package utils;

/** Funciones numéricas auxiliares del paquete. */
final public class Utils {
    private Utils() {}

    /** v acotado a [lo, hi]. */
    public static double acota(double v, double lo, double hi) { return v < lo ? lo : (v > hi ? hi : v); }
}
