package geometria;

/**
 * Una capa de color en un punto: color, cobertura y altura.
 * <p>
 * Ojo, hay dos «alturas» con el mismo nombre que viven en planos distintos. La {@code altura} de una arista de un
 * {@code Grafo} ordena las capas <i>por píxel</i> dentro de ese grafo (quién queda encima en cada cruce). La de esta
 * {@code Tinta} es la que ordena entre geometrías: cada geometría la emite ya resuelta y {@link Grupo} la desplaza con
 * su propia {@link Geometria#altura()} antes de ordenar. Hojas en un mismo {@code Grafo}: orden por píxel; hojas en un
 * {@code Grupo}: orden por geometría entera.
 */
public record Tinta(int argb, double cobertura, double altura) {}