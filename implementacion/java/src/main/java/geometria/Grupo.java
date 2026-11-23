package geometria;

import coordenadas.Opciones;
import forma.Camino;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Varias geometrías sin relación topológica (gotas con relleno, grafos u otros grupos) que se pintan juntas. No hay
 * orden de pintado entre ellas: en cada píxel se juntan las capas de todas y se componen de abajo a arriba por su
 * {@link Tinta#altura() altura}; con la misma altura va debajo la que se añadió antes (y, dentro de una geometría,
 * su orden de siempre). Así una composición puede quedar por encima de otra sin depender de cuál se pinta primero.
 * <p>
 * La {@link #altura() altura} del grupo se suma a la de todas las capas de sus hijos, como un desplazamiento: sube o
 * baja el grupo entero respecto a lo que lo rodea. Un grupo no puede contenerse a sí mismo, ni directa ni indirectamente.
 */
public final class Grupo implements Geometria {
    private final List<Geometria> hijos = new ArrayList<>();
    private String label;
    private double altura = 0;

    public Grupo() {}

    public Grupo(Geometria... hijos) {
        for (Geometria g : hijos) anade(g);
    }

    /** Añade una geometría al grupo. */
    public Grupo anade(Geometria g) {
        Objects.requireNonNull(g, "geometria");
        if (contiene(g, this)) throw new IllegalArgumentException("un grupo no puede contenerse a sí mismo");
        hijos.add(g);
        return this;
    }

    private static boolean contiene(Geometria g, Grupo buscado) {
        if (g == buscado) return true;
        if (g instanceof Grupo gr) for (Geometria h : gr.hijos) if (contiene(h, buscado)) return true;
        return false;
    }

    /** Entra en el grupo, visita los hijos en el orden en que se añadieron (salvo que {@code entra} devuelva false) y sale. */
    @Override public void acepta(Visitante v) {
        if (v.entra(this)) for (Geometria g : hijos) g.acepta(v);
        v.sale(this);
    }

    /** Los caminos de todos los hijos, en orden. */
    @Override public List<Camino> caminos() {
        List<Camino> r = new ArrayList<>();
        for (Geometria g : hijos) r.addAll(g.caminos());
        return r;
    }

    /** Un grupo nunca es hoja, aunque esté vacío. */
    @Override public boolean esHoja() { return false; }

    @Override public int nHijos() { return hijos.size(); }

    /** Unión de los límites de las geometrías; un grupo vacío tiene límites vacíos. */
    @Override public Rectangle2D bounds() {
        Rectangle2D.Double r = null;
        for (Geometria g : hijos) {
            Rectangle2D b = g.bounds();
            if (r == null) r = new Rectangle2D.Double(b.getX(), b.getY(), b.getWidth(), b.getHeight());
            else r.add(b);
        }
        return r != null ? r : new Rectangle2D.Double();
    }

    /** Unión de las siluetas de las geometrías. */
    @Override public Shape silueta() {
        Area a = new Area();
        for (Geometria g : hijos) a.add(new Area(g.silueta()));
        return a;
    }

    @Override public String label() { return label; }

    /** Metadato de depuración. */
    public Grupo label(String label) { this.label = label; return this; }

    @Override public double altura() { return altura; }

    /** Desplazamiento de altura de todo el grupo (por defecto 0). */
    public Grupo altura(double altura) {
        if (!Double.isFinite(altura)) throw new IllegalArgumentException("altura no válida");
        this.altura = altura;
        return this;
    }

    private static final Comparator<Tinta> POR_ALTURA = Comparator.comparingDouble(Tinta::altura);

    /** Las capas de todas las geometrías, de abajo a arriba por altura (orden estable: a igual altura, el de añadido y emisión). */
    @Override public void capas(double x, double y, Opciones op, Consumer<Tinta> out) {
        final List<Tinta> ts = new ArrayList<>();
        for (Geometria g : hijos) g.capas(x, y, op, t -> ts.add(altura == 0 ? t : new Tinta(t.argb(), t.cobertura(), t.altura() + altura)));
        if (ts.size() > 1) ts.sort(POR_ALTURA);         // List.sort es estable
        for (Tinta t : ts) out.accept(t);
    }
}
