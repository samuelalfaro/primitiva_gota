import java.util.ArrayList;
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
 * <p>Convención del mundo: Y crece hacia abajo; las ramas crecen hacia Y
 * negativa. Las direcciones se construyen en un sistema local ligado a la
 * rama madre; la apertura es relativa a esa dirección, no a los ejes
 * globales.</p>
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
        /** Separación angular entre dos ramas hijas (en radianes). */
        public double apertura = Math.toRadians(55);
        public double variacionAngular = Math.toRadians(9);
        public double variacionLongitud = 0.12;
        public long semilla = 20261121;
        /**
         * Giro del patrón de ramificación alrededor de la dirección madre.
         * El ángulo áureo distribuye los planos de bifurcación entre niveles.
         */
        public double giro = Math.toRadians(137.50776);
    }

    /** Vector inmutable en el espacio del árbol. */
    private record Vector3(double x, double y, double z) {}

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
        public Grafo proyectar(Proyeccion proyeccion) {
            if (proyeccion == null) throw new NullPointerException("proyeccion");

            Map<Punto3D, Proyectado> vista = new IdentityHashMap<>();
            for (Punto3D p : puntos) {
                Proyectado q = proyeccion.aplicar(p);
                if (q == null) throw new IllegalArgumentException(
                        "la proyección devolvió null para el punto " + p.id());
                vista.put(p, q);
            }

            Grafo c = new Grafo().mezcla(Mezcla.RGB);
            Map<Punto3D, Grafo.Nodo> nodos = new IdentityHashMap<>();

            for (Punto3D p : puntos) {
                Proyectado q = vista.get(p);
                Grafo.Nodo n = c.nodo(q.x(), q.y(), p.radio() * q.escala())
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
                || !(p.apertura >= 0 && p.apertura < Math.PI)
                || !(p.variacionAngular >= 0)
                || !(p.reduccionLongitud > 0)
                || !(p.reduccionRadio > 0)) {
            throw new IllegalArgumentException("parámetros del árbol no válidos");
        }

        ArbolParametrico3D arbol = new ArbolParametrico3D(p);
        Punto3D raiz = arbol.punto(0, 0, 0, p.radio, 0);
        Vector3 direccionInicial = new Vector3(0, -1, 0);
        Vector3 ejeLocalInicial = new Vector3(1, 0, 0);
        arbol.crecer(raiz, direccionInicial, ejeLocalInicial,
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

    private static double producto(Vector3 a, Vector3 b) {
        return a.x() * b.x() + a.y() * b.y() + a.z() * b.z();
    }

    private static Vector3 sumar(Vector3 a, Vector3 b) {
        return new Vector3(a.x() + b.x(), a.y() + b.y(), a.z() + b.z());
    }

    private static Vector3 escalar(Vector3 v, double k) {
        return new Vector3(v.x() * k, v.y() * k, v.z() * k);
    }

    private static Vector3 restar(Vector3 a, Vector3 b) {
        return new Vector3(a.x() - b.x(), a.y() - b.y(), a.z() - b.z());
    }

    private static Vector3 cruz(Vector3 a, Vector3 b) {
        return new Vector3(
                a.y() * b.z() - a.z() * b.y(),
                a.z() * b.x() - a.x() * b.z(),
                a.x() * b.y() - a.y() * b.x());
    }

    private static Vector3 normalizar(Vector3 v) {
        double longitud = Math.sqrt(producto(v, v));
        if (!(longitud > 1e-12)) {
            throw new IllegalArgumentException("vector de longitud nula");
        }
        return escalar(v, 1 / longitud);
    }

    /**
     * Genera una rama y sus descendientes.
     *
     * <p>direccion es el eje de crecimiento de la rama madre. ejeLocal es
     * perpendicular a él y, junto con su producto vectorial, define el plano
     * local en el que se distribuyen las hijas.</p>
     */
    private void crecer(
            Punto3D origen,
            Vector3 direccion,
            Vector3 ejeLocal,
            double longitud,
            double radio,
            int nivel) {

        Vector3 d = normalizar(direccion);
        Vector3 u = normalizar(restar(ejeLocal, escalar(d, producto(ejeLocal, d))));
        Vector3 v = normalizar(cruz(d, u));

        double x = origen.x() + d.x() * longitud;
        double y = origen.y() + d.y() * longitud;
        double z = origen.z() + d.z() * longitud;

        double radioFin = Math.max(0.8, radio * p.reduccionRadio);
        Punto3D fin = punto(x, y, z, radioFin, nivel + 1);
        ramas.add(new Rama3D(origen, fin, nivel));

        if (nivel >= p.niveles) return;

        // La apertura es el ángulo total entre dos hijas cuando ramas == 2.
        // Para más ramas, se distribuyen radialmente sobre el mismo cono.
        double anguloBase = p.apertura * 0.5;

        for (int i = 0; i < p.ramas; i++) {
            double fase = p.giro * (nivel + 1)
                    + 2 * Math.PI * i / p.ramas
                    + aleatorio(p.variacionAngular * 0.45);
            double angulo = Math.max(0, Math.min(Math.PI * 0.499,
                    anguloBase + aleatorio(p.variacionAngular * 0.45)));

            Vector3 lateral = sumar(escalar(u, Math.cos(fase)),
                    escalar(v, Math.sin(fase)));
            Vector3 direccionHija = normalizar(sumar(
                    escalar(d, Math.cos(angulo)),
                    escalar(lateral, Math.sin(angulo))));

            // Transporta el eje local a la hija proyectándolo sobre el plano
            // perpendicular a su nueva dirección: evita reiniciar el marco
            // contra los ejes globales en cada nivel.
            Vector3 ejeHija = normalizar(restar(ejeLocal,
                    escalar(direccionHija, producto(ejeLocal, direccionHija))));

            double longitudHija = longitud * p.reduccionLongitud
                    * (1 + aleatorio(p.variacionLongitud));

            crecer(fin, direccionHija, ejeHija,
                    longitudHija, radioFin, nivel + 1);
        }
    }
}
