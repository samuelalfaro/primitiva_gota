package geometria;

import coordenadas.Opciones;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.util.function.Consumer;

public interface Geometria {
    Rectangle2D bounds();
    Shape silueta();                 // contorno: alambre, SVG, depuración, rechazo rápido
    String label();                  // metadato de depuración
    double altura();                 // nivel entre geometrías de un Grupo (0 por defecto)
    void capas(double x, double y, Opciones op, Consumer<Tinta> out);
}