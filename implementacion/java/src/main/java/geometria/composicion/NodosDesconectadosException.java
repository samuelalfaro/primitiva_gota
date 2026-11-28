package geometria.composicion;

import java.util.List;

/**
 * Un {@link Grafo} es conexo: si sus nodos forman varias componentes (dos nodos sin ninguna arista entre ellos, dos trazos
 * separados...), no es un grafo sino un {@code Grupo} de grafos o de gotas. {@code Grafo.construye()} lo comprueba antes de
 * hacer nada más y lanza esta excepción, que lleva las componentes (los identificadores de nodo de cada una, en el orden de
 * creación) para que quien llame pueda, si quiere ser permisivo, construir el {@code Grupo} con un grafo o una gota por cada
 * componente.
 */
public final class NodosDesconectadosException extends IllegalStateException {
    private static final long serialVersionUID = 1L;
    private final transient List<List<Integer>> componentes;

    NodosDesconectadosException(List<List<Integer>> componentes) {
        super("nodos desconectados: " + componentes.size() + " componentes " + componentes
              + "; un Grafo es conexo, varias partes sin unir son un Grupo de grafos o de gotas");
        this.componentes = componentes;
    }

    /** Identificadores de los nodos de cada componente (en orden de creación, ordenadas por su primer nodo). */
    public List<List<Integer>> componentes() { return componentes; }
}
