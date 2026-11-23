package coordenadas;

/**
 * Los cortes del eje v de la tira de una {@link forma.Gota}: la tira (v de 0 a 1) se reparte en la tapa trasera [0, c1],
 * el cuerpo [c1, c3] (v lineal) y la tapa delantera [c3, 1]. Con {@code porLongitud}, v es lineal en la longitud de arco
 * del camino ({@link forma.Camino#fraccion}) y no en t. Los calcula una {@link Tira} y los usan las {@link Coordenadas} y los rellenos.
 */
public record Cortes(double c1, double c3, boolean porLongitud) {
    /** Cortes con v lineal en t dentro del cuerpo. */
    public Cortes(double c1, double c3) { this(c1, c3, false); }
}
