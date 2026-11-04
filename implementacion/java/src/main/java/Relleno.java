/**
 * Cómo se colorea un píxel de una {@link Gota}: recibe la muestra del píxel (y, si la pide, sus coordenadas de la tira)
 * y devuelve un color ARGB. Las fábricas están en {@link Colores} (por color) y {@link Texturas} (por textura);
 * {@link ComposicionColor} combina dos rellenos.
 */
@FunctionalInterface
public interface Relleno {
    int argb(double t, double rho);

    /** Versión con la muestra completa (axial y lateral incluidos); por defecto usa (t, rho). */
    default int argb(Coordenadas.Muestra m) { return argb(m.t(), m.rho()); }

    /** false si el color no depende de la muestra: el render se ahorra calcularla. */
    default boolean usaParametros() { return true; }

    /** true si el color depende de las coordenadas de textura (u, v) de la muestra. */
    default boolean usaUv() { return false; }

    /** Versión con las coordenadas de textura de la tira; solo se llama si usaUv() es true. */
    default int argb(Coordenadas.Muestra m, Coordenadas.Uv uv) { return argb(m); }

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
