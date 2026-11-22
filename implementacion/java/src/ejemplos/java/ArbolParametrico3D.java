import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generador de un árbol paramétrico en espacio 3D.
 *
 * <p>Esta clase solo define el árbol espacial: posiciones (x,y,z), radios,
 * colores y conexiones. No decide cómo se mira el árbol ni cómo se pinta.
 * La proyección a pantalla se proporciona después mediante {@link Proyeccion};
 * así la misma geometría puede verse desde distintas cámaras.</p>
 *
 * <p>Convención del mundo: Y crece hacia arriba. La inclinación se mide desde
 * el eje vertical (0 = arriba, 90° = horizontal). El ángulo azimutal gira
 * alrededor del eje Y.</p>
 */
public final class ArbolParametrico3D {

    static final int MADERA_BASE = 0xFF70452F;
    static final int MADERA_PUNTA = 0xFFB17A50;

    /** Parámetros del generador. Conservan los valores del árbol 2D original. */
    public static final class Parametros {
        public double altura = 270;
        public double radio = 13;
        public int niveles = 5;
        public int ramas = 2;
        public double reduccionLongitud = 0.70;
        public double reduccionRadio = 0.66;
        public double apertura = Math.toRadians(55);
        public double variacionAngular = Math.toRadians(9);
        public double variacionLongitud = 0.12;
        public long semilla = 20261121;
        /** Inclinación máxima de las ramas respecto de la vertical. */
        public double inclinacionMaxima = Math.toRadians(78);
    }

    /** Un punto del árbol en coordenadas del mundo. */
    public record Punto3D(
            int id, double x, double y, double z,
            double radio, int nivel, int color) {}

    /** Una rama dirigida desde su nacimiento hasta su extremo. */
    public record Rama3D(Punto3D origen, Punto3D extremo, int nivel) {}

    /**
     * Resultado de transformar un punto 3D a la vista 2D.
     *
     * @param x coordenada horizontal de pantalla
     * @param y coordenada vertical de pantalla
     * @param profundidad profundidad en coordenadas de cámara; valores mayores
     *                    quedan por encima en Solape.TRAZO
     * @param escala factor uniforme aplicado a las longitudes y radios del mundo
     */
    public record Proyectado(double x, double y, double profundidad, double escala) {
        public Proyectado {
            if (!Double.isFinite(x) || !Double.isFinite(y)
                    || !Double.isFinite(profundidad)
                    || !(escala > 0) || !Double.isFinite(escala)) {
                throw new IllegalArgumentException("proyección no válida");
            }
        }
    }

    /** Transformación de cámara: devuelve pantalla y profundidad de cámara. */
    @FunctionalInterface
    public interface Proyeccion {
        Proyectado aplicar(Punto3D punto);
    }

    /** Modelo espacial terminado, todavía independiente de cualquier cámara. */
    public static final class Modelo {
        private final List<Punto3D> puntos;
        private final List<Rama3D> ramas;

        private Modelo(List<Punto3D> puntos, List<Rama3D> ramas) {
            this.puntos = List.copyOf(puntos);
            this.ramas = List.copyOf(ramas);
        }

        public List<Punto3D> puntos() { return puntos; }
        public List<Rama3D> ramas() { return ramas; }

        /**
         * Construye la composición 2D a partir de una proyección.
         *
         * <p>La profundidad se conserva como altura de cada arista, interpolada
         * entre sus extremos. Por tanto, todas las ramas que comparten un nodo
         * reciben exactamente la misma profundidad en él. La composición
         * resultante ya está lista para que quien la use decida si aplica
         * relajar(), alisar() u otras operaciones.</p>
         */
        public Composicion proyectar(Proyeccion proyeccion) {
            if (proyeccion == null) throw new NullPointerException("proyeccion");

            Map<Punto3D, Proyectado> vista = new IdentityHashMap<>();
            for (Punto3D p : puntos) {
                Proyectado q = proyeccion.aplicar(p);
                if (q == null) throw new IllegalArgumentException(
                        "la proyección devolvió null para el punto " + p.id());
                vista.put(p, q);
            }

            Composicion c = new Composicion().mezcla(Mezcla.RGB);
            Map<Punto3D, Composicion.Nodo> nodos = new IdentityHashMap<>();

            for (Punto3D p : puntos) {
                Proyectado q = vista.get(p);
                Composicion.Nodo n = c.nodo(q.x(), q.y(), p.radio() * q.escala())
                        .centro(p.color());
                nodos.put(p, n);
            }

            for (Rama3D r : ramas) {
                Proyectado a = vista.get(r.origen());
                Proyectado b = vista.get(r.extremo());
                double h0 = a.profundidad();
                double h1 = b.profundidad();

                c.arista(nodos.get(r.origen()), nodos.get(r.extremo()))
                        .espina(t -> mezcla(r.origen().color(), r.extremo().color(), t))
                        .altura(t -> h0 + (h1 - h0) * t);
            }
            return c;
        }
    }

    private final Parametros p;
    private final Random azar;
    private final List<Punto3D> puntos = new ArrayList<>();
    private final List<Rama3D> ramas = new ArrayList<>();

    private ArbolParametrico3D(Parametros p) {
        this.p = p;
        this.azar = new Random(p.semilla);
    }

    /** Genera el árbol espacial completo, sin aplicar ninguna proyección. */
    public static Modelo crear(Parametros p) {
        if (p == null) throw new NullPointerException("parametros");
        if (p.niveles < 0 || p.ramas < 1 || !(p.altura > 0) || !(p.radio > 0)
                || !(p.inclinacionMaxima >= 0 && p.inclinacionMaxima < Math.PI / 2)) {
            throw new IllegalArgumentException("parámetros del árbol no válidos");
        }

        ArbolParametrico3D arbol = new ArbolParametrico3D(p);
        Punto3D raiz = arbol.punto(0, 0, 0, p.radio, 0);
        arbol.crecer(raiz, 0, 0, 0,
                p.altura, p.radio, 0);
        return new Modelo(arbol.puntos, arbol.ramas);
    }

    private static int mezcla(int a, int b, double t) {
        return Mezcla.RGB.mezclar(a, b, Math.max(0, Math.min(1, t)));
    }

    private int color(int nivel) {
        double t = p.niveles == 0 ? 0 : (double) nivel / p.niveles;
        return mezcla(MADERA_BASE, MADERA_PUNTA, t);
    }

    private Punto3D punto(double x, double y, double z, double radio, int nivel) {
        Punto3D n = new Punto3D(puntos.size(), x, y, z,
                Math.max(0.8, radio), nivel, color(nivel));
        puntos.add(n);
        return n;
    }

    private double aleatorio(double amplitud) {
        return (azar.nextDouble() * 2 - 1) * amplitud;
    }

    /**
     * Genera una rama usando azimut e inclinación. Las hijas se abren en
     * azimut y aumentan progresivamente su inclinación: el abanico deja de
     * estar contenido en un único plano, pero conserva el patrón paramétrico.
     */
    private void crecer(
            Punto3D origen,
            double azimut,
            double inclinacion,
            double longitud,
            double radio,
            int nivel) {

        double az = azimut + aleatorio(p.variacionAngular * 0.25);
        double inc = Math.min(p.inclinacionMaxima,
                inclinacion + aleatorio(p.variacionAngular * 0.25));

        double horizontal = Math.sin(inc);
        double dx = Math.sin(az) * horizontal;
        double dy = -Math.cos(inc);
        double dz = Math.cos(az) * horizontal;

        double x = origen.x() + dx * longitud;
        double y = origen.y() + dy * longitud;
        double z = origen.z() + dz * longitud;

        double radioFin = Math.max(0.8, radio * p.reduccionRadio);
        Punto3D fin = punto(x, y, z, radioFin, nivel + 1);
        ramas.add(new Rama3D(origen, fin, nivel));

        if (nivel >= p.niveles) return;

        for (int i = 0; i < p.ramas; i++) {
            double posicion = p.ramas == 1
                    ? 0
                    : (double) i / (p.ramas - 1) - 0.5;

            double azimutHija = az
                    + posicion * p.apertura
                    + aleatorio(p.variacionAngular * 0.45);

            double inclinacionHija = Math.min(p.inclinacionMaxima,
                    inc + p.apertura * 0.5
                    + aleatorio(p.variacionAngular * 0.45));

            double longitudHija = longitud * p.reduccionLongitud
                    * (1 + aleatorio(p.variacionLongitud));

            crecer(fin, azimutHija, inclinacionHija,
                    longitudHija, radioFin, nivel + 1);
        }
    }
}
