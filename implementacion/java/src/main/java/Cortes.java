/**
 * Los cortes del eje v de la tira de una {@link Gota}: la tira (v de 0 a 1) se reparte en la tapa trasera [0, c1],
 * la primera mitad del cuerpo [c1, c2], la segunda mitad [c2, c3] y la tapa delantera [c3, 1]. c2 es la v del centro de
 * t = tm. Con {@code porLongitud}, v es lineal en la longitud de arco del camino ({@link Geometria#fraccion}) y no en t. Los calcula una {@link Tira} y los usan las {@link Coordenadas} y los rellenos.
 */
public record Cortes(double c1, double c2, double c3, boolean porLongitud) {
    /** Cortes con v lineal en t dentro del cuerpo. */
    public Cortes(double c1, double c2, double c3) { this(c1, c2, c3, false); }
}
