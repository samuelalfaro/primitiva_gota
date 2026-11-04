/** Funciones numéricas auxiliares del paquete. */
final class Utils {
    private Utils() {}

    /** v acotado a [lo, hi]. */
    static double acota(double v, double lo, double hi) { return v < lo ? lo : (v > hi ? hi : v); }
}
