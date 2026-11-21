import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;

/**
 * Composición de gotas: varias gotas que comparten nodos (una línea abierta, un ciclo, una estrella...).
 *
 * <p>El nodo es una sección compartida (centro, radio, colores); la arista es una gota que conecta dos nodos.
 * La composición NO modifica la primitiva: usa solo {@code contiene}, {@code sdf}, {@code parametros} y {@code uv}
 * de {@link Gota}, y se pinta como la unión de sus gotas.
 *
 * <h3>Reglas</h3>
 * <ul>
 *   <li><b>Los nodos son círculos</b>: el círculo es la única forma que no depende de la orientación, así que es la
 *       misma vista desde todas las gotas que llegan al nodo.</li>
 *   <li><b>Tiradores automáticos</b> (si no se dan): en un nodo de grado 2 la tangente es la de Catmull-Rom entre sus
 *       dos vecinos (contorno G1); en los demás nodos, la dirección hacia el vecino. Longitud: un tercio de la
 *       distancia entre nodos.</li>
 *   <li><b>Orden</b>: el recorrido en profundidad desde el nodo de menor número, tomando las aristas en el orden en que se
 *       escribieron, da a cada nodo su s (longitud de camino desde el 0) y a cada gota el sentido del recorrido (si una arista se
 *       escribió al revés, su gota va de b a a). Las topografías son: tira (0 .. N), círculo (0 .. Z0), Y (0 .. M y varias M .. N),
 *       Y lobulada (M .. Zn) y el 8 (dos pétalos con el nodo 0 en común, o un círculo que se cruza). Un cierre Zn es la arista que
 *       termina en un nodo ya recorrido ({@link #cierra}). La textura corre por toda la composición ({@link #cortes}): v va de la s de
 *       un extremo de cada gota a la del otro, y cada rama de una Y sigue desde la s del cruce (con {@link Reparto#POR_GOTA} cada gota
 *       usa su propia tira, como antes). Con {@link Solape#TRAZO} manda, entre
 *       gotas, la de cuerpo en el píxel y mayor s; con empate, la arista escrita después.</li>
 *   <li><b>Pendiente del radio</b> (por defecto): en los nodos de grado 2 se igualan a ambos lados los k = r'/|c'|
 *       para que el contorno no se quiebre, dentro de lo que permite un radio cúbico monótono por arista.</li>
 *   <li><b>Unión</b>: cobertura por el mínimo (suave si {@link #suavizado(double)} &gt; 0) de las distancias de las
 *       gotas; el color lo da la gota con menor ρ (PLANO).</li>
 *   <li><b>Color</b>: cada nodo tiene un color central y colores por <i>sector</i> (cada hueco entre dos ramas); la
 *       gota toma el de su lado normal y el de su lado contra-normal, de modo que el color de contorno es continuo
 *       entre ramas. Las puntas son la media de los colores de los lados: no hay color de extremo.</li>
 * </ul>
 */
public final class Composicion {

    // ---------- Modelo ----------

    /** Sección compartida. Se obtiene con {@link Composicion#nodo(double, double, double)}. */
    public static final class Nodo {
        private final Composicion dueña;
        private final int id;
        private final double x, y, radio;
        private Integer centro;
        private int[] sectores;
        private Integer contra, normal;
        private double ajusteLateral = 0.5, ajusteArco = 0.5;
        private Degradado fNormal, fContra;
        private final List<Arista[]> pasos = new ArrayList<>();

        private Nodo(Composicion dueña, int id, double x, double y, double radio) {
            this.dueña = dueña; this.id = id; this.x = x; this.y = y; this.radio = radio;
        }

        public double x() { return x; }
        public double y() { return y; }
        public double radio() { return radio; }

        /**
         * Declara que las aristas e1 y e2, que llegan a este nodo, se continúan por él: su tangente en el nodo es la de
         * Catmull-Rom entre sus vecinos (G1 entre las dos), como en un nodo de grado 2. Pensado para nodos de grado 3 o
         * más, donde sin esto cada arista sale hacia su vecino. Un nodo puede tener varias parejas, sin repetir aristas;
         * un tirador explícito de la arista manda sobre la pareja. Ver también {@link Composicion#relajar()}.
         */
        public Nodo paso(Arista e1, Arista e2) {
            if (e1 == null || e2 == null) throw new NullPointerException("paso: arista nula");
            if (e1 == e2) throw new IllegalArgumentException("paso: las dos aristas son la misma");
            for (Arista e : new Arista[] { e1, e2 }) {
                if (e.dueña != dueña || (e.a != this && e.b != this)) throw new IllegalArgumentException("paso: la arista no llega al nodo " + id);
                if (e.a == e.b) throw new IllegalArgumentException("paso: un lazo no puede ser pareja de paso");
                if (parejaDe(e) != null) throw new IllegalArgumentException("paso: la arista ya tiene pareja en el nodo " + id);
            }
            pasos.add(new Arista[] { e1, e2 });
            return cambia();
        }

        private Arista parejaDe(Arista e) {
            for (Arista[] p : pasos) { if (p[0] == e) return p[1]; if (p[1] == e) return p[0]; }
            return null;
        }

        /** Color único: centro y todos los sectores. */
        public Nodo color(int argb) { centro = argb; sectores = null; contra = normal = null; return cambia(); }

        /** Color del eje de la tira (u = 1/2); por defecto el de {@link #color(int)}. */
        public Nodo centro(int argb) { centro = argb; return cambia(); }

        /**
         * Colores de los dos lados de un nodo de grado 2, relativos al avance de la PRIMERA arista que se añadió en
         * él: normal es el lado de N = (Ty, -Tx) y contra el opuesto.
         */
        public Nodo lados(int contra, int normal) { this.contra = contra; this.normal = normal; sectores = null; return cambia(); }

        /**
         * Un color por sector, en orden de ángulo creciente (atan2, y hacia abajo) de las ramas del nodo: el ángulo es el
         * de la tangente con que sale cada rama, no la dirección al otro nodo. Hace falta un color por rama y la cuenta
         * se comprueba al construir (gotas() o render), cuando ya se sabe qué aristas llegan al nodo: si no cuadra, el
         * error lista las ramas con su ángulo y su nodo vecino. En un nodo de grado 2, {@link #lados(int, int)} evita
         * contar ramas; con un solo color para todo el nodo, {@link #color(int)}.
         */
        public Nodo sectores(int... colores) { sectores = colores.clone(); contra = normal = null; return cambia(); }


        /**
         * Misma función de color a los dos lados: va del centro (x = 0) al contorno (x = 1), en cualquier nodo. Sustituye
         * a la mezcla lineal entre el color del centro y el de los lados; en el centro debe valer lo mismo que el color del
         * nodo ({@link #centro(int)}; si no se dio, se toma de la función).
         */
        public Nodo radio(Degradado f) { fNormal = fContra = Objects.requireNonNull(f, "radio"); return cambia(); }

        /**
         * Una función de color para cada lado, de x = 0 (centro) a x = 1 (contorno), con el mismo criterio de normal y
         * contra que {@link #lados(int, int)}. Solo en nodos de grado 2.
         */
        public Nodo radios(Degradado normal, Degradado contra) {
            fNormal = Objects.requireNonNull(normal, "normal"); fContra = Objects.requireNonNull(contra, "contra");
            return cambia();
        }

        /**
         * Ajuste de la transición del color del centro al de los lados (0.5 lineal). Por debajo de 0.5 el punto medio de
         * la mezcla queda más cerca del centro y la nervadura se marca; ver {@link Colores.Lateral#conAjuste}.
         *
         * @deprecated Se sustituirá por un {@link Degradado}.
         */
        @Deprecated
        public Nodo ajusteLateral(double s) {
            if (!(s >= 0 && s <= 1)) throw new IllegalArgumentException("ajusteLateral debe estar en [0, 1]");
            ajusteLateral = s;
            return cambia();
        }

        /**
         * Ajuste de la mezcla polar normal -> contra en las tapas (0.5 lineal: la punta es la media). Ver
         * {@link Colores.Lateral#conAjusteArco}.
         *
         * @deprecated Se sustituirá por un {@link Degradado}.
         */
        @Deprecated
        public Nodo ajusteArco(double s) {
            if (!(s >= 0 && s <= 1)) throw new IllegalArgumentException("ajusteArco debe estar en [0, 1]");
            ajusteArco = s;
            return cambia();
        }

        private Nodo cambia() { dueña.invalida(); return this; }
    }

    /** Gota entre dos nodos. Se obtiene con {@link Composicion#arista(Nodo, Nodo)}. */
    public static final class Arista {
        private final Composicion dueña;
        private final int id;
        private final Nodo a, b;
        private Gota.Vec va, vb;
        private Degradado espina;
        private DoubleUnaryOperator altura;
        private Gota.Vec ava, avb;                  // tiradores de alisar(): solo si no hay tiradores explícitos

        private Arista(Composicion dueña, int id, Nodo a, Nodo b) { this.dueña = dueña; this.id = id; this.a = a; this.b = b; }

        public Nodo a() { return a; }
        public Nodo b() { return b; }

        /**
         * Tiradores explícitos, relativos a cada extremo (null = automático en ese extremo). Una arista con algún
         * tirador explícito queda fuera del ajuste de pendientes del radio. Un lazo (arista de un nodo consigo mismo)
         * exige los dos: no hay vecino del que deducir la tangente.
         */
        public Arista tiradores(Gota.Vec enA, Gota.Vec enB) { va = enA; vb = enB; dueña.invalida(); return this; }

        /**
         * Función de color a lo largo del eje de la gota, del color del centro de {@link #a()} (x = 0) al de {@link #b()}
         * (x = 1); por defecto, la mezcla lineal entre los dos. Debe coincidir con los colores de los centros.
         */
        public Arista espina(Degradado f) { espina = Objects.requireNonNull(f, "espina"); dueña.invalida(); return this; }

        /**
         * Altura de la arista a lo largo de su eje, de {@link #a()} (x = 0) a {@link #b()} (x = 1): en un solape con
         * {@link Solape#TRAZO} manda la mayor. Permite cruces alternados (por encima / por debajo) que el orden del
         * recorrido no puede dar. Sin declarar, la arista usa el orden del recorrido como hasta ahora, y las aristas con
         * altura quedan por encima de las que no la tienen. En los nodos, las alturas de las aristas declaradas que
         * llegan deben coincidir (IllegalArgumentException al construir).
         */
        public Arista altura(DoubleUnaryOperator f) { altura = Objects.requireNonNull(f, "altura"); dueña.invalida(); return this; }
    }

    private final List<Nodo> nodos = new ArrayList<>();
    private final List<Arista> aristas = new ArrayList<>();
    private double suaviza = 0;
    private Mezcla mezcla = Mezcla.LCH;
    private boolean ajustaPendientes = true;
    private Reparto reparto = Reparto.GLOBAL;

    // Resultado de construir (se rehace tras cualquier cambio).
    private List<Gota> gotas;
    private boolean[] invertida;          // la gota va de b a a: el recorrido desde el nodo 0 llega a la arista por su extremo b
    private boolean[] cierre;             // la arista termina en un nodo ya recorrido (un cierre Zn)
    private int[] componente;             // componente conexa de cada arista
    private double[] sInicio, sFin;       // s (longitud de camino desde el nodo 0) al principio y al final de cada gota
    private double[] sNodo;               // s de cada nodo
    private double[] sTotal;              // s máxima de cada componente
    private List<Relleno> rellenos;
    private Rectangle2D.Double bounds;

    // ---------- Construcción ----------

    public Nodo nodo(double x, double y, double radio) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !(radio >= 0) || !Double.isFinite(radio))
            throw new IllegalArgumentException("nodo no válido");
        Nodo n = new Nodo(this, nodos.size(), x, y, radio);
        nodos.add(n);
        invalida();
        return n;
    }

    public Arista arista(Nodo a, Nodo b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (a.dueña != this || b.dueña != this) throw new IllegalArgumentException("nodo de otra composición");
        Arista e = new Arista(this, aristas.size(), a, b);
        aristas.add(e);
        invalida();
        return e;
    }

    /** Radio (px) de la unión suave de distancias en los cruces; 0 = unión dura (por defecto). */
    public Composicion suavizado(double px) {
        if (!(px >= 0) || !Double.isFinite(px)) throw new IllegalArgumentException("suavizado no válido");
        suaviza = px;
        invalida();
        return this;
    }

    /** Mezcla de color (por defecto LCH). Ojo: el arco corto del tono puede dejar un salto con tonos opuestos. */
    public Composicion mezcla(Mezcla m) { mezcla = Objects.requireNonNull(m, "mezcla"); invalida(); return this; }

    /** Igualar la pendiente del radio en las uniones (por defecto sí). */

    public Composicion ajustarPendientes(boolean si) { ajustaPendientes = si; invalida(); return this; }

    /** Cómo se reparte la textura (el eje v de la tira) entre las gotas de la composición. */
    public enum Reparto {
        /** Cada gota usa su propia tira de las {@link Opciones} (por defecto 1 : 2 : 1): v empieza de nuevo en cada arista. */
        POR_GOTA,
        /** v corre por toda la composición según la s de cada gota ({@link Composicion#cortes}): la textura continúa de una gota a la siguiente. */
        GLOBAL
    }

    /**
     * Reparto de la textura entre las gotas (por defecto {@link Reparto#GLOBAL}). Solo afecta a la v de textura y color lateral: la forma, el
     * orden del recorrido y el solape por s de TRAZO no dependen de él.
     */
    public Composicion reparto(Reparto r) { reparto = Objects.requireNonNull(r, "reparto"); invalida(); return this; }

    public int grado(Nodo n) {
        int g = 0;
        for (Arista e : aristas) { if (e.a == n) g++; if (e.b == n) g++; }
        return g;
    }

    public List<Nodo> nodos() { return Collections.unmodifiableList(nodos); }
    public List<Arista> aristas() { return Collections.unmodifiableList(aristas); }

    private void invalida() { gotas = null; rellenos = null; bounds = null; invertida = null; }

    // ---------- Consulta ----------

    public List<Gota> gotas() { construye(); return Collections.unmodifiableList(gotas); }

    public Gota gota(Arista e) { construye(); return gotas.get(e.id); }

    public Rectangle2D bounds() { construye(); return (Rectangle2D) bounds.clone(); }

    /** Pertenencia exacta a la unión dura (el suavizado solo afecta al pintado y a la sdf). */
    public boolean contiene(double x, double y) {
        construye();
        for (Gota g : gotas) if (g.mascara().contiene(x, y)) return true;
        return false;
    }

    /** Distancia con signo de la unión (suave si hay suavizado): exacta fuera, aproximada en el interior profundo. */
    public double sdf(double x, double y) {
        construye();
        double s = Double.MAX_VALUE;
        for (Gota g : gotas) s = s == Double.MAX_VALUE ? g.distancia(x, y) : smin(s, g.distancia(x, y), suaviza);
        return s;
    }

    /**
     * Quiebro (grados) del contorno en un nodo de grado 2 por la pendiente del radio: el contorno de una familia de
     * círculos forma con el camino un ángulo asin(k), con k = r'/|c'| medido alejándose del nodo, así que a ambos
     * lados del nodo debe cumplirse asin(k1) + asin(k2) = 0. Es exacto para nodos circulares; con un extremo
     * elíptico en la gota vecina añade un término de segundo orden en k que no se cuenta. El desalineamiento de las
     * tangentes (G1 de los tiradores) no entra aquí: con tiradores automáticos es nulo.
     */
    public double quiebro(Nodo n) {
        construye();
        if (grado(n) != 2) throw new IllegalArgumentException("solo en nodos de grado 2");
        double suma = 0;
        for (Arista e : aristas) for (boolean inicio : new boolean[] { true, false }) {
            if ((inicio ? e.a : e.b) != n) continue;
            Gota g = gotas.get(e.id);
            boolean alPrincipio = inicio != invertida[e.id];          // la gota de una arista invertida va de b a a
            double t0 = alPrincipio ? 0 : 1, t1 = alPrincipio ? 1e-4 : 1 - 1e-4;
            Point2D p0 = g.centro(t0), p1 = g.centro(t1);
            double ds = Math.hypot(p1.getX() - p0.getX(), p1.getY() - p0.getY());
            double k = (g.radio(t1) - g.radio(t0)) / ds;          // alejándose del nodo
            suma += Math.asin(Math.max(-0.999, Math.min(0.999, k)));
        }
        return Math.toDegrees(Math.abs(suma));
    }

    /**
     * Aristas cuya gota es rígida: su trayectoria mide r1 + r2 o menos, así que la forma son cuatro arcos sobre la recta
     * de los centros y se ignoran los tiradores (la cinta se ve facetada, con quiebros en los nodos). Diagnóstico: con
     * nodos más separados que r1 + r2, o con radios menores, la lista queda vacía.
     */
    public List<Arista> rigidas() {
        construye();
        List<Arista> r = new ArrayList<>();
        for (Arista e : aristas) if (gotas.get(e.id).silueta() != null) r.add(e);
        return r;
    }

    /**
     * Curvatura continua (G2) a lo largo de las cintas: por cada cadena de aristas que se continúan (nodos de grado 2 o
     * parejas de {@link Nodo#paso}) calcula la spline cúbica C2 que pasa por sus nodos, con parámetro de longitud de
     * cuerda (periódica si la cadena se cierra; natural, de curvatura nula en los extremos, si es abierta), y da a cada
     * arista los tiradores de esa spline. Con los tiradores automáticos de siempre la tangente es continua (G1) pero la
     * curvatura salta en cada nodo. No mueve nodos ni toca radios; las aristas con tiradores explícitos y los lazos
     * quedan fuera, y una cadena cerrada de menos de tres aristas se deja como está. Opt-in e idempotente; llámala
     * después de {@link #relajar()} si quieres que las uniones también entren.
     */
    public Composicion alisar() {
        for (Arista e : aristas) { e.ava = null; e.avb = null; }
        Set<Arista> hechas = new HashSet<>();
        // cadenas abiertas: se empieza por una arista con un extremo sin continuación
        for (Arista e : aristas) {
            if (hechas.contains(e) || !alisable(e)) continue;
            for (Nodo extremo : new Nodo[] { e.a, e.b }) {
                if (continuacion(e, extremo) == null) { alisaCadena(e, extremo, hechas, false); break; }
            }
        }
        for (Arista e : aristas) if (!hechas.contains(e) && alisable(e)) alisaCadena(e, e.a, hechas, true);
        invalida();
        return this;
    }

    private static boolean alisable(Arista e) { return e.a != e.b && e.va == null && e.vb == null; }

    /** La arista que continúa a e al otro lado de n (grado 2, o pareja de paso), o null. */
    private Arista continuacion(Arista e, Nodo n) {
        Arista o = null;
        if (grado(n) == 2) {
            for (Arista f : aristas) if (f != e && (f.a == n || f.b == n)) o = f;
        } else o = n.parejaDe(e);
        return o != null && alisable(o) && o.a != o.b ? o : null;
    }

    private void alisaCadena(Arista primera, Nodo inicio, Set<Arista> hechas, boolean cerrada) {
        List<Arista> es = new ArrayList<>();
        List<Nodo> ps = new ArrayList<>();
        Arista e = primera; Nodo desde = inicio;
        while (e != null && hechas.add(e)) {
            es.add(e); ps.add(desde);
            Nodo hasta = e.a == desde ? e.b : e.a;
            Arista sig = continuacion(e, hasta);
            if (sig == null && !cerrada) ps.add(hasta);
            desde = hasta; e = sig;
        }
        int n = es.size();
        if (cerrada) { if (n < 3 || desde != inicio) { for (Arista f : es) hechas.remove(f); return; } }
        double[] h = new double[n];
        for (int i = 0; i < n; i++) {
            Nodo a = ps.get(i), b = cerrada ? ps.get((i + 1) % n) : ps.get(i + 1);
            double cuerda = Math.hypot(b.x - a.x, b.y - a.y);
            if (cuerda < 1e-9) return;                                    // nodos coincidentes: sin spline
            h[i] = cuerda;
        }
        int m = cerrada ? n : n + 1;
        double[][] A = new double[m][m];
        double[][] rhs = new double[m][2];
        for (int i = 0; i < m; i++) {
            Nodo p = ps.get(i), q = ps.get((i + 1) % ps.size()), o = ps.get((i + ps.size() - 1) % ps.size());
            boolean interior = cerrada || (i > 0 && i < n);
            if (interior) {
                double hm = h[(i + n - 1) % n], hi = h[i % n];
                A[i][(i + m - 1) % m] += hi; A[i][i] += 2 * (hm + hi); A[i][(i + 1) % m] += hm;
                rhs[i][0] = 3 * (hi * (p.x - o.x) / hm + hm * (q.x - p.x) / hi);
                rhs[i][1] = 3 * (hi * (p.y - o.y) / hm + hm * (q.y - p.y) / hi);
            } else if (i == 0) {                                          // extremo natural: 2 m0 + m1 = 3 (P1 - P0) / h0
                Nodo q1 = ps.get(1);
                A[0][0] = 2; A[0][1] = 1;
                rhs[0][0] = 3 * (q1.x - p.x) / h[0]; rhs[0][1] = 3 * (q1.y - p.y) / h[0];
            } else {                                                      // m_{n-1} + 2 m_n = 3 (Pn - Pn-1) / h_{n-1}
                Nodo a = ps.get(n - 1);
                A[i][i - 1] = 1; A[i][i] = 2;
                rhs[i][0] = 3 * (p.x - a.x) / h[n - 1]; rhs[i][1] = 3 * (p.y - a.y) / h[n - 1];
            }
        }
        double[][] mm = resuelve(A, rhs);
        if (mm == null) { for (Arista f : es) hechas.remove(f); return; }
        for (int i = 0; i < n; i++) {
            int j = cerrada ? (i + 1) % n : i + 1;
            Gota.Vec c1 = new Gota.Vec(mm[i][0] * h[i] / 3, mm[i][1] * h[i] / 3);
            Gota.Vec c2 = new Gota.Vec(-mm[j][0] * h[i] / 3, -mm[j][1] * h[i] / 3);
            Arista f = es.get(i);
            if (f.a == ps.get(i)) { f.ava = c1; f.avb = c2; } else { f.avb = c1; f.ava = c2; }
        }
    }

    /** Eliminación gaussiana con pivote parcial; null si el sistema es singular. */
    private static double[][] resuelve(double[][] A, double[][] b) {
        int m = A.length;
        for (int c = 0; c < m; c++) {
            int piv = c;
            for (int r = c + 1; r < m; r++) if (Math.abs(A[r][c]) > Math.abs(A[piv][c])) piv = r;
            if (Math.abs(A[piv][c]) < 1e-12) return null;
            double[] t = A[c]; A[c] = A[piv]; A[piv] = t;
            double[] u = b[c]; b[c] = b[piv]; b[piv] = u;
            for (int r = c + 1; r < m; r++) {
                double f = A[r][c] / A[c][c];
                if (f == 0) continue;
                for (int k = c; k < m; k++) A[r][k] -= f * A[c][k];
                b[r][0] -= f * b[c][0]; b[r][1] -= f * b[c][1];
            }
        }
        double[][] x = new double[m][2];
        for (int r = m - 1; r >= 0; r--) for (int d = 0; d < 2; d++) {
            double v = b[r][d];
            for (int k = r + 1; k < m; k++) v -= A[r][k] * x[k][d];
            x[r][d] = v / A[r][r];
        }
        return x;
    }

    /**
     * Aristas cuya gota (no rígida) se pliega: en algún punto del camino el radio de curvatura es menor o igual que el
     * radio de la cinta (kappa * r >= 1) o el radio cambia más deprisa que el camino (|r'| >= |c'|). Diagnóstico de
     * puntas y giros muy cerrados, por ejemplo un nodo donde la ruta se invierte casi 180°.
     */
    public List<Arista> plegadas() {
        construye();
        List<Arista> r = new ArrayList<>();
        for (Arista e : aristas) if (gotas.get(e.id).plegada()) r.add(e);
        return r;
    }

    /** {@link #relajar(double)} con 120°. */
    public Composicion relajar() { return relajar(120); }

    /**
     * Da tangente continua (G1) por los nodos de grado 3 o más sin tener que calcular los tiradores a mano: en cada
     * nodo sin parejas declaradas, empareja las ramas más opuestas (ángulo entre las direcciones a sus vecinos mayor
     * o igual que minGrados) con {@link Nodo#paso}, de más a menos opuestas. No mueve nodos ni toca radios; las
     * ramas sin pareja siguen saliendo hacia su vecino. Heurística opt-in: se corrige con {@link Nodo#paso} o con
     * tiradores explícitos. Hay que llamarla con la topología terminada (las aristas añadidas después no entran).
     */
    public Composicion relajar(double minGrados) {
        for (Nodo n : nodos) {
            if (grado(n) < 3 || !n.pasos.isEmpty()) continue;
            List<Arista> ramas = new ArrayList<>();
            for (Arista e : aristas) if (e.a != e.b && (e.a == n || e.b == n)) ramas.add(e);
            while (ramas.size() >= 2) {
                double mejor = -1; int bi = -1, bj = -1;
                for (int i = 0; i < ramas.size(); i++) for (int j = i + 1; j < ramas.size(); j++) {
                    double[] u = dirA(n, ramas.get(i)), w = dirA(n, ramas.get(j));
                    double ang = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, u[0] * w[0] + u[1] * w[1]))));
                    if (ang > mejor) { mejor = ang; bi = i; bj = j; }
                }
                if (mejor < minGrados) break;
                n.paso(ramas.get(bi), ramas.get(bj));
                ramas.remove(bj); ramas.remove(bi);
            }
        }
        return this;
    }

    private static double[] dirA(Nodo n, Arista e) {
        Nodo m = e.a == n ? e.b : e.a;
        return unit(m.x - n.x, m.y - n.y);
    }

    // ---------- Pintado ----------

    /** Pinta la composición sobre dst (TYPE_INT_ARGB) con antialiasing y composición alfa, con el color lateral de los nodos. */
    public void render(BufferedImage dst) { render(dst, Opciones.POR_DEFECTO); }

    /** Igual, con las opciones de pintado dadas (costura de la tira y solape). */
    public void render(BufferedImage dst, Opciones op) {
        Objects.requireNonNull(op, "opciones");
        construye();
        pinta(dst, rellenos, op);
    }

    /**
     * Pinta la composición con una textura: cada arista es una tira (u a lo ancho, v a lo largo, tapas de semidisco).
     * El color lo pone, como en el color lateral, la gota de menor ρ en cada píxel, así que las tapas de los nodos
     * compartidos desaparecen y solo quedan las de los extremos libres.
     */
    public void render(BufferedImage dst, Texturas.Textura tex) { render(dst, tex, Opciones.POR_DEFECTO); }

    /** Igual, con las opciones de pintado dadas. */
    public void render(BufferedImage dst, Texturas.Textura tex, Opciones op) {
        Objects.requireNonNull(tex, "textura");
        Objects.requireNonNull(op, "opciones");
        construye();
        List<Relleno> rs = new ArrayList<>();
        for (int i = 0; i < gotas.size(); i++) rs.add(Texturas.tira(tex));
        pinta(dst, rs, op);
    }

    /**
     * Pinta la composición combinando en una sola pasada el color lateral de cada gota (Cf) con la textura (Ct), al estilo de
     * glTexEnv ({@link ComposicionColor.Modo}). La gota que manda en cada píxel es la misma que con el color solo, y la cobertura del
     * borde se aplica una vez (dos pasadas, color y luego textura con alfa, equivalen como mucho a DECAL y la cobertura se aplica dos veces).
     * Con REPLACE da lo mismo que {@link #render(BufferedImage, Texturas.Textura, Opciones)}.
     */
    public void render(BufferedImage dst, Texturas.Textura tex, ComposicionColor.Modo modo) {
        render(dst, tex, modo, Opciones.POR_DEFECTO);
    }

    /** Igual, con las opciones de pintado dadas. */
    public void render(BufferedImage dst, Texturas.Textura tex, ComposicionColor.Modo modo, Opciones op) {
        render(dst, tex, modo, 0xFF000000, op);
    }

    /** Igual, con el color de entorno Ce de BLEND (su alfa se ignora). */
    public void render(BufferedImage dst, Texturas.Textura tex, ComposicionColor.Modo modo, int entorno, Opciones op) {
        Objects.requireNonNull(tex, "textura");
        Objects.requireNonNull(modo, "modo");
        Objects.requireNonNull(op, "opciones");
        construye();
        List<Relleno> rs = new ArrayList<>();
        for (int i = 0; i < gotas.size(); i++) rs.add(ComposicionColor.combinar(rellenos.get(i), Texturas.tira(tex), modo, entorno));
        pinta(dst, rs, op);
    }

    private void pinta(BufferedImage dst, List<Relleno> rellenos, Opciones op) {
        final Plotter.Raster raster = new Plotter.Raster(dst);
        final double marg = 2 + suaviza;
        final Plotter.Raster.Zona z = raster.zona(bounds.getMinX(), bounds.getMaxX(), bounds.getMinY(), bounds.getMaxY(), marg);
        if (z == null) return;
        final List<Relleno> rel = new ArrayList<>();
        final Opciones[] ops = new Opciones[gotas.size()];
        for (int i = 0; i < gotas.size(); i++) {
            Cortes k = cortesDe(i, op);
            ops[i] = reparto == Reparto.GLOBAL ? op.conTira(Tira.fija(k.c1(), k.c3())) : op;    // GLOBAL: v corre por toda la composición
            rel.add(rellenos.get(i).para(gotas.get(i), ops[i]));
        }
        raster.recorre(z, (i, j) -> {
            double x = i + 0.5, y = j + 0.5;
            double[] ev = evalua(x, y, op, ops);
            if (ev[1] < 0) return;
            Gota g = gotas.get((int) ev[1]);
            int c = rel.get((int) ev[1]).argb(Coordenadas.punto(g, x, y, ops[(int) ev[1]]));
            raster.set(i, j, sobre(raster.get(i, j), c, ev[0]));
        });
    }

    // ---------- Orden del recorrido ----------

    /** Un paso del recorrido: la arista se recorre desde un nodo hasta otro; es de árbol si llega a un nodo nuevo y de cierre si llega a uno ya recorrido. */
    private record Paso(int arista, int desde, int hasta, boolean arbol, int componente) {}

    /**
     * Recorrido en profundidad desde el nodo de menor número de cada componente, tomando las aristas en el orden en que se
     * escribieron. Es el orden del dibujo: tira (0 .. N), círculo (0 .. Z0), Y (0 .. M y varias M .. N), Y lobulada (M .. Zn)...
     */
    private List<Paso> recorrido() {
        int n = nodos.size(), m = aristas.size(), comp = 0;
        boolean[] vis = new boolean[n], usada = new boolean[m];
        List<Paso> out = new ArrayList<>();
        for (Nodo raiz : nodos) {
            if (vis[raiz.id]) continue;
            vis[raiz.id] = true;
            Deque<int[]> pila = new ArrayDeque<>();                // {nodo, siguiente arista a mirar}
            pila.push(new int[] { raiz.id, 0 });
            while (!pila.isEmpty()) {
                int[] f = pila.peek();
                int u = f[0], k = f[1];
                while (k < m && (usada[k] || (aristas.get(k).a.id != u && aristas.get(k).b.id != u))) k++;
                if (k >= m) { pila.pop(); continue; }
                f[1] = k + 1;
                usada[k] = true;
                Arista e = aristas.get(k);
                int w = e.a.id == u ? e.b.id : e.a.id;             // en un lazo w == u
                boolean arbol = !vis[w];
                out.add(new Paso(k, u, w, arbol, comp));
                if (arbol) { vis[w] = true; pila.push(new int[] { w, 0 }); }
            }
            comp++;
        }
        return out;
    }

    /** s de cada nodo y de cada extremo de gota: la longitud de camino desde el nodo 0, que sigue desde el nodo en cada rama. */
    private void ordena(List<Paso> pasos, boolean[] inv) {
        int n = nodos.size(), m = aristas.size(), nc = 0;
        for (Paso p : pasos) nc = Math.max(nc, p.componente + 1);
        invertida = inv;
        cierre = new boolean[m]; componente = new int[m];
        sInicio = new double[m]; sFin = new double[m]; sNodo = new double[n];
        sTotal = new double[nc];
        for (Paso p : pasos) {
            double largo = gotas.get(p.arista).longitud();
            sInicio[p.arista] = sNodo[p.desde];
            sFin[p.arista] = sNodo[p.desde] + largo;
            if (p.arbol) sNodo[p.hasta] = sFin[p.arista];
            cierre[p.arista] = !p.arbol;
            componente[p.arista] = p.componente;
            sTotal[p.componente] = Math.max(sTotal[p.componente], sFin[p.arista]);
        }
    }

    /**
     * s del nodo: la longitud de camino desde el nodo de menor número de su componente (el 0 en una composición conexa), siguiendo
     * las aristas en el orden en que se escribieron (recorrido en profundidad). En una Y, cada rama sigue contando desde la s del cruce.
     */
    public double orden(Nodo n) {
        construye();
        if (n.dueña != this) throw new IllegalArgumentException("nodo de otra composición");
        return sNodo[n.id];
    }

    /** true si la arista cierra un ciclo: en el recorrido desde el nodo 0 termina en un nodo ya recorrido (en la notación, Zn). */
    public boolean cierra(Arista e) { construye(); return cierre[e.id]; }

    /**
     * Cortes de v con que se pinta la gota de la arista: toda la composición comparte el reparto de la textura, y v va de la s de
     * un extremo de la gota a la del otro (lineal en la longitud de camino). Las tapas (v por debajo de c1 y por encima de c3)
     * son las de la tira de la primera gota de las opciones, 1 : 2 : 1 por defecto, y solo aparecen en los extremos libres; el cuerpo
     * usa siempre la banda [c1, c3] de la textura, también en un círculo. La v de un cierre acaba en la s del final del ciclo, que no
     * coincide con la del nodo en que cierra: ahí queda una costura.
     */
    public Cortes cortes(Arista e, Opciones op) {
        construye();
        return cortesDe(e.id, op);
    }

    /** Cortes de v con que se pinta la gota i: los globales o, con {@link Reparto#POR_GOTA}, los de la tira de las opciones aplicada a esa gota. */
    private Cortes cortesDe(int i, Opciones op) {
        return reparto == Reparto.GLOBAL ? cortesGlobales(i, op) : op.tira().cortes(gotas.get(i));
    }

    private Cortes cortesGlobales(int i, Opciones op) {
        int c = componente[i];
        Cortes b = op.tira().cortes(gotas.get(0));
        double c1 = b.c1(), c3 = b.c3();
        double total = sTotal[c];
        double v0 = total > 0 ? c1 + (c3 - c1) * sInicio[i] / total : c1, v1 = total > 0 ? c1 + (c3 - c1) * sFin[i] / total : c1;
        final double eps = 1e-6;                                   // los rellenos exigen 0 < c1 < c3 < 1: sin tapas o con una gota de longitud 0 se acotan
        double w0 = Math.max(eps, Math.min(v0, 1 - 2 * eps));
        double w1 = Math.max(w0 + eps, Math.min(v1, 1 - eps));
        return new Cortes(w0, w1, true);
    }

    // ---------- Internos ----------

    private static double smin(double a, double b, double k) {
        if (k <= 0) return Math.min(a, b);
        double h = Math.max(k - Math.abs(a - b), 0) / k;
        return Math.min(a, b) - h * h * k / 4;
    }

    /** {cobertura, gota que manda (o -1)} en (x, y): sdf = unión suave de las sdf, color de la gota de menor ρ. */
    private double[] evalua(double x, double y, Opciones op, Opciones[] ops) {
        double marg = 2 + suaviza;
        double smin = Double.MAX_VALUE, rmin = Double.MAX_VALUE;
        int mejor = -1;
        double[] s = new double[gotas.size()];
        for (int k = 0; k < gotas.size(); k++) {
            s[k] = Double.MAX_VALUE;
            Rectangle2D bb = gotas.get(k).bounds();
            if (x < bb.getMinX() - marg || x > bb.getMaxX() + marg || y < bb.getMinY() - marg || y > bb.getMaxY() + marg)
                continue;
            s[k] = gotas.get(k).distancia(x, y);
            smin = smin == Double.MAX_VALUE ? s[k] : smin(smin, s[k], suaviza);
        }
        if (smin == Double.MAX_VALUE) return new double[] { 0, -1 };
        double cob = Math.max(0, Math.min(1, 0.5 - smin));
        if (cob <= 0) return new double[] { 0, -1 };
        if (op.solape() == Solape.TRAZO) {
            // Entre las gotas que contienen el píxel manda, primero, la que lo tiene en su cuerpo (las tapas de los nodos compartidos
            // no tapan a los cuerpos vecinos); después, la que va más tarde en el recorrido (mayor s); con empate, la arista escrita después.
            boolean cuerpoTop = false, altTop = false;
            double sTop = Double.NEGATIVE_INFINITY, sGan = Double.NEGATIVE_INFINITY;
            for (int k = 0; k < gotas.size(); k++) {
                if (!(s[k] <= 0)) continue;
                Gota g = gotas.get(k);
                Cortes ck = ops[k].tira().cortes(g);
                double v = Coordenadas.uv(g, x, y, ops[k]).v();
                boolean cuerpo = v >= ck.c1() && v <= ck.c3();
                double t = Coordenadas.porRho(g, x, y, Solape.TRAZO).t();
                double sg = sInicio[k] + (sFin[k] - sInicio[k]) * g.fraccion(t);
                DoubleUnaryOperator fa = aristas.get(k).altura;
                double h = sg;                                           // sin altura declarada manda s; con ella, la altura (por encima de s)
                if (fa != null) { double x0 = g.fraccion(t); h = fa.applyAsDouble(invertida[k] ? 1 - x0 : x0); }
                boolean dec = fa != null;
                boolean gana = mejor < 0 || (cuerpo != cuerpoTop ? cuerpo
                        : dec != altTop ? dec
                        : h > sTop || (h == sTop && sg >= sGan));
                if (gana) { cuerpoTop = cuerpo; altTop = dec; sTop = h; sGan = sg; mejor = k; }
            }
            if (mejor >= 0) return new double[] { cob, mejor };
        }
        for (int k = 0; k < gotas.size(); k++) {
            if (s[k] >= 0.75 + suaviza) continue;
            double rho = Coordenadas.porRho(gotas.get(k), x, y, op.solape()).rho();
            if (rho < rmin) { rmin = rho; mejor = k; }
        }
        return new double[] { cob, mejor };
    }

    /** fg con cobertura a sobre dst (ARGB no premultiplicado). */
    private static int sobre(int dst, int fg, double a) {
        double fa = ((fg >>> 24) / 255.0) * a, da = (dst >>> 24) / 255.0;
        double oa = fa + da * (1 - fa);
        if (oa <= 0) return 0;
        int r = (int) Math.round(oa * 255) << 24;
        for (int s = 16; s >= 0; s -= 8) {
            double c = (((fg >> s) & 255) * fa + ((dst >> s) & 255) * da * (1 - fa)) / oa;
            r |= (int) Math.round(c) << s;
        }
        return r;
    }

    private static double[] unit(double x, double y) {
        double l = Math.hypot(x, y);
        return l == 0 ? new double[] { 1, 0 } : new double[] { x / l, y / l };
    }

    /** Dirección saliente (unitaria, alejándose del nodo) de la arista e en su extremo inicio/fin. */
    private double[] salida(Arista e, boolean inicio) {
        Gota.Vec v = inicio ? e.va : e.vb;
        Nodo n = inicio ? e.a : e.b, m = inicio ? e.b : e.a;
        if (v != null && (v.x() != 0 || v.y() != 0)) return unit(v.x(), v.y());
        Arista pareja = n.parejaDe(e);
        if (pareja != null) {
            Nodo o = pareja.a == n ? pareja.b : pareja.a;
            if (!(o.x == m.x && o.y == m.y)) return unit(m.x - o.x, m.y - o.y);
        }
        if (grado(n) == 2) {
            Nodo o = null;
            for (Arista f : aristas) {
                if (f == e) continue;
                if (f.a == n) o = f.b;
                if (f.b == n) o = f.a;
            }
            if (o != null && !(o.x == m.x && o.y == m.y)) return unit(m.x - o.x, m.y - o.y);
            // Anillo de dos nodos (las dos aristas unen los mismos nodos): sin vecino distinto no hay Catmull-Rom.
            // La primera arista se curva hacia +p (perpendicular a su cuerda) en ambos extremos y la segunda hacia -p.
            Arista primera = null;
            for (Arista f : aristas) if (f.a == n || f.b == n) { primera = f; break; }
            double[] p = unit(-(primera.b.y - primera.a.y), primera.b.x - primera.a.x);
            double sg = e == primera ? 1 : -1;
            return new double[] { p[0] * sg, p[1] * sg };
        }
        return unit(m.x - n.x, m.y - n.y);
    }

    /** Datos de una arista tras fijar tiradores y pendiente. */
    /** m1 y m2: pendientes dr/dt del radio en los extremos (NaN: sin dato, el radio va en recta). */
    private record Ar(int a, int b, Gota.Vec va, Gota.Vec vb, double m1, double m2, boolean fija) {}

    private void construye() {
        if (gotas != null) return;
        if (aristas.isEmpty()) throw new IllegalStateException("la composición no tiene aristas");
        for (Nodo n : nodos) {
            int g = grado(n);
            if (g == 0) throw new IllegalStateException("nodo " + n.id + " sin aristas");
        }
        // Tiradores.
        List<Ar> ars = new ArrayList<>();
        for (Arista e : aristas) {
            if (e.a == e.b && (e.va == null || e.vb == null))
                throw new IllegalStateException("lazo en el nodo " + e.a.id + ": hay que dar los dos tiradores");
            double d = Math.hypot(e.b.x - e.a.x, e.b.y - e.a.y);
            double[] ua = salida(e, true), ub = salida(e, false);
            Gota.Vec va = e.va != null ? e.va : e.ava != null ? e.ava : new Gota.Vec(ua[0] * d / 3, ua[1] * d / 3);
            Gota.Vec vb = e.vb != null ? e.vb : e.avb != null ? e.avb : new Gota.Vec(ub[0] * d / 3, ub[1] * d / 3);
            ars.add(new Ar(e.a.id, e.b.id, va, vb, Double.NaN, Double.NaN, e.va != null || e.vb != null));
        }
        if (ajustaPendientes) ars = igualaK(ars);
        List<Paso> pasos = recorrido();
        boolean[] inv = new boolean[aristas.size()];
        for (Paso p : pasos) inv[p.arista] = p.desde != aristas.get(p.arista).a.id;

        gotas = new ArrayList<>();
        rellenos = new ArrayList<>();
        Rectangle2D.Double bb = null;
        for (int i = 0; i < ars.size(); i++) {
            Ar r = ars.get(i);
            boolean v = inv[i];                                     // la gota va en el sentido del recorrido, no del orden en que se escribió la arista
            Nodo A = nodos.get(v ? r.b : r.a), B = nodos.get(v ? r.a : r.b);
            Gota.Vec va = v ? r.vb : r.va, vb = v ? r.va : r.vb;
            double m1 = v ? r.m2 : r.m1, m2 = v ? r.m1 : r.m2;
            double dr = B.radio - A.radio;
            Perfil base = Double.isNaN(m1) && Double.isNaN(m2)
                    ? Perfil.lineal()
                    : Perfil.tangentes(Double.isNaN(m1) ? dr : m1, Double.isNaN(m2) ? dr : m2);
            Gota g = Gota.curva(new Point2D.Double(A.x, A.y), A.radio, va, new Point2D.Double(B.x, B.y), B.radio, vb, base);
            gotas.add(g);
            Arista ea = aristas.get(i);
            Lado la = lateralDe(A, ea, !v, v), lb = lateralDe(B, ea, v, v);
            if (la.normal == null && la.contra == null && lb.normal == null && lb.contra == null && ea.espina == null)
                rellenos.add(Colores.cadenaLateral(la.lat, lb.lat, mezcla));
            else rellenos.add(Colores.cadenaLateral(funcionesDe(la, lb, ea, v), mezcla));
            Rectangle2D gb = g.bounds();
            if (bb == null) bb = new Rectangle2D.Double(gb.getX(), gb.getY(), gb.getWidth(), gb.getHeight());
            else bb.add(gb);
        }
        bounds = bb;
        ordena(pasos, inv);
        validaAlturas();
    }

    /** Las aristas con altura que llegan a un mismo nodo deben darle la misma altura (como los colores en los centros). */
    private void validaAlturas() {
        for (Nodo n : nodos) {
            Double ref = null;
            for (Arista e : aristas) {
                if (e.altura == null) continue;
                for (int extremo = 0; extremo < 2; extremo++) {
                    if ((extremo == 0 ? e.a : e.b) != n) continue;
                    double h = e.altura.applyAsDouble(extremo);
                    if (ref == null) ref = h;
                    else if (Math.abs(h - ref) > 1e-6 * Math.max(1, Math.abs(ref)))
                        throw new IllegalArgumentException("nodo " + n.id + ": las alturas de las aristas que llegan no coinciden (" + ref + " y " + h
                                + " en x = " + extremo + "); la altura debe ser continua en los nodos");
                }
            }
        }
    }

    // ---------- Color lateral por sectores ----------

    private record Brazo(Arista e, boolean inicio, double ox, double oy, double ang) {}

    private List<Brazo> brazos(Nodo n) {
        List<Brazo> l = new ArrayList<>();
        for (Arista e : aristas) {
            if (e.a == n) { double[] o = salida(e, true); l.add(new Brazo(e, true, o[0], o[1], Math.atan2(o[1], o[0]))); }
            if (e.b == n) { double[] o = salida(e, false); l.add(new Brazo(e, false, o[0], o[1], Math.atan2(o[1], o[0]))); }
        }
        l.sort(Comparator.comparingDouble(Brazo::ang));
        return l;
    }

    /** {sector del lado contra-normal, sector del lado normal} de la rama k (ordenada por ángulo). */
    private static int[] sectoresDe(List<Brazo> br, int k) {
        int d = br.size();
        if (d == 1) return new int[] { 0, 0 };
        Brazo b = br.get(k);
        double tx = b.inicio ? b.ox : -b.ox, ty = b.inicio ? b.oy : -b.oy;
        double nx = ty, ny = -tx;                            // N = (Ty, -Tx)
        boolean creciente = b.ox * ny - b.oy * nx > 0;
        int sCre = k, sDec = (k - 1 + d) % d;
        return creciente ? new int[] { sDec, sCre } : new int[] { sCre, sDec };
    }

    /**
     * Mensaje de sectores() con el número de colores que no cuadra: dice cuántos colores hacen falta y por qué (qué
     * aristas llegan al nodo, con su ángulo y el nodo del otro extremo), en el orden en que se esperan los colores.
     */
    private String mensajeSectores(Nodo n, List<Brazo> br) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("nodo %d (x=%.1f, y=%.1f): sectores() recibió %d colores y el nodo tiene %d rama%s; hace falta un color por rama",
                n.id, n.x, n.y, n.sectores.length, br.size(), br.size() == 1 ? "" : "s"));
        sb.append(", en orden de ángulo de salida creciente, en (-180°, 180°] (atan2 con y hacia abajo: 0° = +x, 90° = hacia abajo en pantalla; es la tangente con que sale la rama del nodo, no la dirección al otro nodo). Ramas: ");
        for (int k = 0; k < br.size(); k++) {
            Brazo b = br.get(k);
            Nodo otro = b.inicio() ? b.e().b : b.e().a;
            if (k > 0) sb.append("; ");
            sb.append(String.format("%d.ª sale a %.0f° (arista hacia el nodo %d)", k + 1, Math.toDegrees(b.ang()), otro.id));
        }
        sb.append(". Si la cuenta no es la esperada, quizá falta o sobra una arista (o se añadió otra después de dar los colores).");
        return sb.toString();
    }

    /** Colores de un extremo de gota y, si el nodo las tiene, sus funciones de color (null = mezcla lineal). */
    private record Lado(Colores.Lateral lat, Degradado normal, Degradado contra) {}

    private Colores.Funciones funcionesDe(Lado a, Lado b, Arista e, boolean invertida) {
        Mezcla m = mezcla;
        Degradado espina = e.espina == null ? Degradado.entre(a.lat.centro(), b.lat.centro(), m)
                         : invertida ? x -> e.espina.en(1 - x) : e.espina;
        return new Colores.Funciones(
            a.normal != null ? a.normal : Degradado.entre(a.lat.centro(), a.lat.normal(), m),
            a.contra != null ? a.contra : Degradado.entre(a.lat.centro(), a.lat.contra(), m),
            espina,
            b.normal != null ? b.normal : Degradado.entre(b.lat.centro(), b.lat.normal(), m),
            b.contra != null ? b.contra : Degradado.entre(b.lat.centro(), b.lat.contra(), m));
    }

    private Lado lateralDe(Nodo n, Arista e, boolean inicio, boolean invertida) {
        int centro = n.centro != null ? n.centro
                   : n.fNormal != null ? n.fNormal.en(0)
                   : (n.sectores != null && n.sectores.length > 0 ? n.sectores[0] : 0xFFCCCCCC);
        List<Brazo> br = brazos(n);
        int d = br.size();
        int[] col = new int[d];
        java.util.Arrays.fill(col, centro);
        Degradado[] fun = new Degradado[d];
        if (n.fNormal != null && n.fNormal != n.fContra && d != 2)
            throw new IllegalStateException("radios() solo en nodos de grado 2 (nodo " + n.id + "); con radio(f) vale cualquier grado");
        if (n.sectores != null) {
            if (n.sectores.length != d) throw new IllegalStateException(mensajeSectores(n, br));
            col = n.sectores;
            if (n.fNormal != null) java.util.Arrays.fill(fun, n.fNormal);
        } else if (n.contra != null) {
            if (d != 2) throw new IllegalStateException("lados() solo en nodos de grado 2 (nodo " + n.id + ")");
            Arista primera = null;
            for (Arista f : aristas) if (f.a == n || f.b == n) { primera = f; break; }
            for (int k = 0; k < d; k++)
                if (br.get(k).e == primera) {
                    int[] sc = sectoresDe(br, k);
                    col = new int[2];
                    col[sc[0]] = n.contra;
                    col[sc[1]] = n.normal;
                }
        }
        if (n.fNormal != null && n.sectores == null) {
            if (n.fNormal == n.fContra) java.util.Arrays.fill(fun, n.fNormal);
            else {
                Arista primera = null;
                for (Arista f : aristas) if (f.a == n || f.b == n) { primera = f; break; }
                for (int k = 0; k < d; k++)
                    if (br.get(k).e == primera) { int[] sc = sectoresDe(br, k); fun[sc[0]] = n.fContra; fun[sc[1]] = n.fNormal; }
            }
        }
        for (int k = 0; k < d; k++)
            if (br.get(k).e == e && br.get(k).inicio == inicio) {
                int[] sc = sectoresDe(br, k);
                return invertida ? new Lado(new Colores.Lateral(col[sc[1]], centro, col[sc[0]], n.ajusteLateral, n.ajusteArco),   // la gota va al revés: normal y contra se cambian
                                            fun[sc[0]], fun[sc[1]])
                                 : new Lado(new Colores.Lateral(col[sc[0]], centro, col[sc[1]], n.ajusteLateral, n.ajusteArco),
                                            fun[sc[1]], fun[sc[0]]);
            }
        return new Lado(Colores.Lateral.uniforme(centro), n.fNormal, n.fContra);
    }

    // ---------- Pendiente del radio ----------

    /**
     * Para que el contorno sea G1 en un nodo de grado 2 hay que igualar a ambos lados k = r'/|c'| (la pendiente del radio por
     * longitud de camino). Como el radio de cada gota es una cúbica monótona definida por sus pendientes en los extremos
     * ({@link Perfil#tangentes}), cada nodo fija su k y las dos aristas lo heredan: dr/dt = ±k·|c'| en ese extremo. No hace falta
     * tocar los tiradores. El k de partida es el de Catmull-Rom, (r siguiente - r anterior) / (distancias a los dos vecinos), y se
     * recorta a lo que permite la monotonía de las dos aristas: dr/dt entre 0 y 3·(r2 - r1). En un nodo donde el radio tiene un
     * máximo o un mínimo, k = 0. Los nodos con alguna arista de tiradores explícitos, los de grado 1 y los de grado >= 3 no
     * imponen k: su extremo va en recta (pendiente r2 - r1).
     */
    private List<Ar> igualaK(List<Ar> ars) {
        int n = nodos.size();
        double[] kn = new double[n];
        boolean[] activo = new boolean[n];
        int[][] pq = new int[n][];
        for (Nodo N : nodos) {
            if (grado(N) != 2) continue;
            int[] es = new int[2];
            int c = 0;
            boolean fija = false;
            for (int i = 0; i < ars.size(); i++) {
                Ar r = ars.get(i);
                if (r.a == N.id || r.b == N.id) { es[c++] = i; if (r.fija) fija = true; }
            }
            if (fija) continue;
            Nodo P = nodos.get(ars.get(es[0]).a == N.id ? ars.get(es[0]).b : ars.get(es[0]).a);
            Nodo Q = nodos.get(ars.get(es[1]).a == N.id ? ars.get(es[1]).b : ars.get(es[1]).a);
            double den = Math.hypot(N.x - P.x, N.y - P.y) + Math.hypot(N.x - Q.x, N.y - Q.y);
            kn[N.id] = den == 0 ? 0 : (Q.radio - P.radio) / den;
            activo[N.id] = true;
            pq[N.id] = es;
        }
        for (Nodo N : nodos) {
            if (!activo[N.id]) continue;
            double lo = -0.8, hi = 0.8;                              // el contorno forma con el camino un ángulo asin(k)
            for (int i : pq[N.id]) {
                Ar r = ars.get(i);
                boolean enA = r.a == N.id;
                double v = enA ? Math.hypot(r.va.x(), r.va.y()) : Math.hypot(r.vb.x(), r.vb.y());
                double dr = nodos.get(r.b).radio - nodos.get(r.a).radio;
                double lim = v < 1e-12 ? 0 : dr / v;                // dr/ds máximo (en valor absoluto) de la arista en ese extremo: 3·|dr| / (3·|v|)
                double b0 = Math.min(0, lim), b1 = Math.max(0, lim);
                double s = signo(i, r, N.id, pq);
                lo = Math.max(lo, s > 0 ? b0 : -b1);
                hi = Math.min(hi, s > 0 ? b1 : -b0);
            }
            kn[N.id] = Math.max(lo, Math.min(hi, kn[N.id]));
        }

        List<Ar> nuevas = new ArrayList<>();
        for (int i = 0; i < ars.size(); i++) {
            Ar r = ars.get(i);
            if (r.fija) { nuevas.add(r); continue; }
            double m1 = activo[r.a] ? signo(i, r, r.a, pq) * kn[r.a] * 3 * Math.hypot(r.va.x(), r.va.y()) : Double.NaN;
            double m2 = activo[r.b] ? signo(i, r, r.b, pq) * kn[r.b] * 3 * Math.hypot(r.vb.x(), r.vb.y()) : Double.NaN;
            nuevas.add(new Ar(r.a, r.b, r.va, r.vb, m1, m2, false));
        }
        return nuevas;
    }

    /** +1 si la arista i va a favor del sentido de viaje p -> n -> q en el nodo n; -1 si va en contra. */
    private static double signo(int i, Ar r, int n, int[][] pq) {
        boolean esQ = pq[n][1] == i;
        boolean sale = r.a == n;
        return (esQ == sale) ? 1 : -1;
    }
}
