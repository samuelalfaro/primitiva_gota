/**
 * Un degradado es una función x ∈ [0, 1] → color ARGB. Es la pieza común de todas las transiciones de color: no tiene
 * resolución ni ajustes propios, solo dice qué color corresponde a cada x. El ajuste fino de una transición (adelantar
 * o retrasar el punto medio) es una deformación de x que se aplica antes: {@link #ajustado(double)} o
 * {@link #deformado(java.util.function.DoubleUnaryOperator)}.
 *
 * <p>Se puede dar cualquier lambda propia, p. ej. una paleta: {@code x -> paleta[(int) (x * 255)]}. Las x fuera de
 * [0, 1] se recortan en las fábricas de esta interfaz, no en las lambdas propias.
 */
@FunctionalInterface
public interface Degradado {
    int en(double x);

    /** Degradado de c0 (x = 0) a c1 (x = 1) con la mezcla dada. */
    static Degradado entre(int c0, int c1, Mezcla m) {
        java.util.Objects.requireNonNull(m, "mezcla");
        return x -> m.mezclar(c0, c1, x < 0 ? 0 : (x > 1 ? 1 : x));
    }

    /** Color constante. */
    static Degradado constante(int c) { return x -> c; }

    /** El mismo degradado con x deformada por f (f debe llevar [0, 1] a [0, 1]). */
    default Degradado deformado(java.util.function.DoubleUnaryOperator f) {
        java.util.Objects.requireNonNull(f, "f");
        return x -> en(f.applyAsDouble(x));
    }

    /** Con el ajuste normalizado s ∈ [0, 1] de {@link Colores}: 0.5 neutro, menor adelanta, mayor retrasa. */
    default Degradado ajustado(double s) {
        if (!(s >= 0 && s <= 1)) throw new IllegalArgumentException("ajuste debe estar en [0, 1]");
        return s == 0.5 ? this : deformado(x -> Colores.transicion(x, s));
    }
}
