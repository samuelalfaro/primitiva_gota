/**
 * Cómo se colorea un píxel de una {@link Gota}: recibe el {@link Punto} y devuelve un color ARGB. El punto calcula su
 * muestra y sus coordenadas de textura solo si el relleno las pide. Las fábricas están en {@link Colores} (por color)
 * y {@link Texturas} (por textura); {@link ComposicionColor} combina dos rellenos.
 */
@FunctionalInterface
public interface Relleno {
    int argb(Punto p);

    /** Con la muestra y las coordenadas ya calculadas (útil en pruebas y fuera del render). */
    default int argb(Coordenadas.Muestra m, Coordenadas.Uv uv) { return argb(Punto.de(m, uv)); }

    /** Con la muestra ya calculada; los rellenos que necesiten las coordenadas Uv lanzan UnsupportedOperationException. */
    default int argb(Coordenadas.Muestra m) { return argb(Punto.de(m)); }

    /** Solo con (t, rho); los rellenos que necesiten axial, lateral o Uv lanzan UnsupportedOperationException. */
    default int argb(double t, double rho) {
        return argb(Punto.de(new Coordenadas.Muestra(t, rho, Double.NaN, Double.NaN)));
    }

    /**
     * Versión del relleno ya enlazada con la gota que lo va a pintar y con las opciones de pintado; render() la llama
     * antes de barrer. Los rellenos que dependen de la tira (cadena axial, textura) toman de las opciones sus cortes.
     */
    default Relleno para(Gota g, Opciones op) { return this; }

    /** Igual, con las opciones por defecto. */
    default Relleno para(Gota g) { return para(g, Opciones.POR_DEFECTO); }

    static Relleno solido(int color) { return p -> color; }
}
