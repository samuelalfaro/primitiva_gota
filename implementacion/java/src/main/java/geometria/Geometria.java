package geometria;

import coordenadas.Opciones;
import forma.Camino;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface Geometria {
    Rectangle2D bounds();
    Shape silueta();                 // contorno: alambre, SVG, depuración, rechazo rápido
    String label();                  // metadato de depuración
    double altura();                 // nivel entre geometrías de un Grupo (0 por defecto)
    void capas(double x, double y, Opciones op, Consumer<Tinta> out);

    /** Recorrido por visitante: una hoja se entrega a {@link Visitante#hoja}; {@link Grupo} lo redefine para descender. */
    default void acepta(Visitante v) { v.hoja(this); }

    /** Los caminos de las gotas que forman esta geometría (vacío si no tiene), en orden. */
    default List<? extends Camino> caminos() { return List.of(); }

    /** Cada camino con la transformación acumulada hasta él (hoy siempre la identidad: aún no hay transformadas). */
    default void recorre(BiConsumer<Camino, AffineTransform> f) {
        acepta(new Visitante() {
            @Override public void hoja(Geometria g) { for (Camino c : g.caminos()) f.accept(c, new AffineTransform()); }
        });
    }

    /** {@code true} si no contiene otras geometrías. */
    default boolean esHoja() { return true; }

    /** Número de hijos directos (0 en una hoja). */
    default int nHijos() { return 0; }
}
