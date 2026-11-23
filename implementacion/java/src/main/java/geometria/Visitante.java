package geometria;

/**
 * Recorrido de una estructura de {@link Geometria}: cada hoja (gota con relleno, grafo...) se entrega a
 * {@link #hoja(Geometria)}, y cada {@link Grupo} se anuncia con {@link #entra(Grupo)} antes de sus hijos y con
 * {@link #sale(Grupo)} después. Sirve para lo que necesita bajar por la estructura (alambre, SVG con
 * {@code <g>}, recuentos) sin que la estructura exponga sus hijos.
 */
public interface Visitante {
    /** Una geometría que no es un grupo. */
    void hoja(Geometria g);

    /** Se entra en un grupo; si devuelve {@code false} no se visitan sus hijos (pero {@link #sale} sí se llama). */
    default boolean entra(Grupo g) { return true; }

    /** Se sale de un grupo, siempre que se haya llamado a {@link #entra} para él. */
    default void sale(Grupo g) {}
}
