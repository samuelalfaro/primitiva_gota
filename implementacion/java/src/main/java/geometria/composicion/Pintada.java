package geometria.composicion;

import color.Relleno;
import coordenadas.Contorno;
import coordenadas.Coordenadas;
import coordenadas.Opciones;
import forma.Gota;
import geometria.Geometria;
import geometria.Tinta;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.List;

/**
 * Una {@link Gota} con su {@link Relleno}: la gota sola solo guarda parámetros y no se puede pintar, esta sí.
 * Las capas de cada píxel son las que da el solape de las opciones ({@code PLANO}: una; {@code TRAZO}: una por
 * pasada del camino), con el color que el relleno da a su punto.
 */
public final class Pintada implements Geometria {
    private final Gota gota;
    private final Relleno relleno;
    private final double altura;
    private final String label;
    private final ConcurrentMap<Opciones, Relleno> rellenos = new ConcurrentHashMap<>();   // el relleno depende de las opciones

    private Pintada(Gota gota, Relleno relleno, double altura, String label) {
        this.gota = java.util.Objects.requireNonNull(gota, "gota");
        this.relleno = java.util.Objects.requireNonNull(relleno, "relleno");
        this.altura = altura;
        this.label = label;
    }

    public static Pintada de(Gota gota, Relleno relleno) { return new Pintada(gota, relleno, 0, null); }

    public Pintada conAltura(double altura) { return new Pintada(gota, relleno, altura, label); }

    public Pintada conLabel(String label) { return new Pintada(gota, relleno, altura, label); }

    public Gota gota() { return gota; }

    @Override public List<Gota> caminos() { return List.of(gota); }

    @Override public Rectangle2D bounds() { return gota.bounds(); }

    /** El contorno de la gota: los tramos de su silueta que dibuja el modo alambre. */
    @Override public Shape silueta() {
        Shape borde = Contorno.de(gota).borde();
        return borde != null ? borde : new Path2D.Double();
    }

    @Override public String label() { return label; }

    @Override public double altura() { return altura; }

    @Override public void capas(double x, double y, Opciones op, Consumer<Tinta> out) {
        final Relleno rel = rellenos.computeIfAbsent(op, o -> relleno.para(gota, o));
        op.solape().capas(gota, Coordenadas.de(gota, op), x, y,
                capa -> out.accept(new Tinta(rel.argb(capa.punto()), capa.cobertura(), altura)));
    }
}
