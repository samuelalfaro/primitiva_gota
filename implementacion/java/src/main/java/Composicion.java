import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Composición de gotas: varias gotas que comparten nodos (una línea abierta, un ciclo, una estrella...).
 *
 * <p>El nodo es una sección compartida (centro, radio, colores); la arista es una gota que conecta dos nodos.
 * La composición NO modifica la primitiva: usa solo {@code contiene}, {@code sdf}, {@code parametros} y {@code uv}
 * de {@link Gota}, y se pinta como la unión de sus gotas.
 *
 * <h3>Reglas</h3>
 * <ul>
 *   <li><b>Aspecto elíptico solo en extremos libres</b> (nodos de grado 1). Un nodo con más de una rama es un
 *       círculo: una elipse solo sería la misma vista desde todas sus gotas si todas llegaran con la misma
 *       tangente, y el círculo es la única forma que no depende de la orientación. Las gotas con ambos extremos
 *       circulares usan el algoritmo de grado 6.</li>
 *   <li><b>Tiradores automáticos</b> (si no se dan): en un nodo de grado 2 la tangente es la de Catmull-Rom entre sus
 *       dos vecinos (contorno G1); en los demás nodos, la dirección hacia el vecino. Longitud: un tercio de la
 *       distancia entre nodos.</li>
 *   <li><b>Pendiente del radio</b> (por defecto): en los nodos de grado 2 se igualan a ambos lados los k = r'/|c'|
 *       para que el contorno no se quiebre, dentro de lo que permite una cuadrática de radio.</li>
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
        private double aspecto = 1;
        private double ajusteLateral = 0.5, ajusteArco = 0.5;

        private Nodo(Composicion dueña, int id, double x, double y, double radio) {
            this.dueña = dueña; this.id = id; this.x = x; this.y = y; this.radio = radio;
        }

        public double x() { return x; }
        public double y() { return y; }
        public double radio() { return radio; }

        /** Color único: centro y todos los sectores. */
        public Nodo color(int argb) { centro = argb; sectores = null; contra = normal = null; return cambia(); }

        /** Color del eje de la tira (u = 1/2); por defecto el de {@link #color(int)}. */
        public Nodo centro(int argb) { centro = argb; return cambia(); }

        /**
         * Colores de los dos lados de un nodo de grado 2, relativos al avance de la PRIMERA arista que se añadió en
         * él: normal es el lado de N = (Ty, -Tx) y contra el opuesto.
         */
        public Nodo lados(int contra, int normal) { this.contra = contra; this.normal = normal; sectores = null; return cambia(); }

        /** Un color por sector, en orden de ángulo creciente (atan2, y hacia abajo) de las ramas del nodo. */
        public Nodo sectores(int... colores) { sectores = colores.clone(); contra = normal = null; return cambia(); }


        /**
         * Ajuste de la transición del color del centro al de los lados (0.5 lineal). Por debajo de 0.5 el punto medio de
         * la mezcla queda más cerca del centro y la nervadura se marca; ver {@link Colores.Lateral#conAjuste}.
         */
        public Nodo ajusteLateral(double s) {
            if (!(s >= 0 && s <= 1)) throw new IllegalArgumentException("ajusteLateral debe estar en [0, 1]");
            ajusteLateral = s;
            return cambia();
        }

        /**
         * Ajuste de la mezcla polar normal -> contra en las tapas (0.5 lineal: la punta es la media). Ver
         * {@link Colores.Lateral#conAjusteArco}.
         */
        public Nodo ajusteArco(double s) {
            if (!(s >= 0 && s <= 1)) throw new IllegalArgumentException("ajusteArco debe estar en [0, 1]");
            ajusteArco = s;
            return cambia();
        }

        /** Aspecto a/b (tangente/normal) del extremo; solo válido en nodos de grado 1. */
        public Nodo aspecto(double lambda) { aspecto = lambda; return cambia(); }

        private Nodo cambia() { dueña.invalida(); return this; }
    }

    /** Gota entre dos nodos. Se obtiene con {@link Composicion#arista(Nodo, Nodo)}. */
    public static final class Arista {
        private final Composicion dueña;
        private final int id;
        private final Nodo a, b;
        private Gota.Vec va, vb;

        private Arista(Composicion dueña, int id, Nodo a, Nodo b) { this.dueña = dueña; this.id = id; this.a = a; this.b = b; }

        public Nodo a() { return a; }
        public Nodo b() { return b; }

        /**
         * Tiradores explícitos, relativos a cada extremo (null = automático en ese extremo). Una arista con algún
         * tirador explícito queda fuera del ajuste de pendientes del radio. Un lazo (arista de un nodo consigo mismo)
         * exige los dos: no hay vecino del que deducir la tangente.
         */
        public Arista tiradores(Gota.Vec enA, Gota.Vec enB) { va = enA; vb = enB; dueña.invalida(); return this; }
    }

    private final List<Nodo> nodos = new ArrayList<>();
    private final List<Arista> aristas = new ArrayList<>();
    private double suaviza = 0;
    private Mezcla mezcla = Mezcla.LCH;
    private boolean ajustaPendientes = true;

    // Resultado de construir (se rehace tras cualquier cambio).
    private List<Gota> gotas;
    private List<Gota.Relleno> rellenos;
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

    public int grado(Nodo n) {
        int g = 0;
        for (Arista e : aristas) { if (e.a == n) g++; if (e.b == n) g++; }
        return g;
    }

    public List<Nodo> nodos() { return Collections.unmodifiableList(nodos); }
    public List<Arista> aristas() { return Collections.unmodifiableList(aristas); }

    private void invalida() { gotas = null; rellenos = null; bounds = null; }

    // ---------- Consulta ----------

    public List<Gota> gotas() { construye(); return Collections.unmodifiableList(gotas); }

    public Gota gota(Arista e) { construye(); return gotas.get(e.id); }

    public Rectangle2D bounds() { construye(); return (Rectangle2D) bounds.clone(); }

    /** Pertenencia exacta a la unión dura (el suavizado solo afecta al pintado y a la sdf). */
    public boolean contiene(double x, double y) {
        construye();
        for (Gota g : gotas) if (g.contiene(x, y)) return true;
        return false;
    }

    /** Distancia con signo de la unión (suave si hay suavizado): exacta fuera, aproximada en el interior profundo. */
    public double sdf(double x, double y) {
        construye();
        double s = Double.MAX_VALUE;
        for (Gota g : gotas) s = s == Double.MAX_VALUE ? g.sdf(x, y) : smin(s, g.sdf(x, y), suaviza);
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
            double t0 = inicio ? 0 : 1, t1 = inicio ? 1e-4 : 1 - 1e-4;
            Point2D p0 = g.centro(t0), p1 = g.centro(t1);
            double ds = Math.hypot(p1.getX() - p0.getX(), p1.getY() - p0.getY());
            double k = (g.radio(t1) - g.radio(t0)) / ds;          // alejándose del nodo
            suma += Math.asin(Math.max(-0.999, Math.min(0.999, k)));
        }
        return Math.toDegrees(Math.abs(suma));
    }

    // ---------- Pintado ----------

    /** Pinta la composición sobre dst (TYPE_INT_ARGB) con antialiasing y composición alfa, con el color lateral de los nodos. */
    public void render(BufferedImage dst) {
        construye();
        pinta(dst, rellenos);
    }

    /**
     * Pinta la composición con una textura: cada arista es una tira (u a lo ancho, v a lo largo, tapas de semidisco).
     * El color lo pone, como en el color lateral, la gota de menor ρ en cada píxel, así que las tapas de los nodos
     * compartidos desaparecen y solo quedan las de los extremos libres.
     */
    public void render(BufferedImage dst, Texturas.Textura tex) {
        Objects.requireNonNull(tex, "textura");
        construye();
        List<Gota.Relleno> rs = new ArrayList<>();
        for (int i = 0; i < gotas.size(); i++) rs.add(Texturas.tira(tex));
        pinta(dst, rs);
    }

    private void pinta(BufferedImage dst, List<Gota.Relleno> rellenos) {
        if (dst.getType() != BufferedImage.TYPE_INT_ARGB)
            throw new IllegalArgumentException("Se requiere BufferedImage.TYPE_INT_ARGB");
        final WritableRaster raster = dst.getRaster();
        if (!(raster.getSampleModel() instanceof SinglePixelPackedSampleModel sm))
            throw new IllegalArgumentException("Raster no soportado: se requiere INT_ARGB empaquetado");
        final int[] datos = ((DataBufferInt) raster.getDataBuffer()).getData();
        final int stride = sm.getScanlineStride();
        final int base = raster.getDataBuffer().getOffset()
                - raster.getSampleModelTranslateY() * stride - raster.getSampleModelTranslateX();
        final int w = dst.getWidth();
        final double marg = 2 + suaviza;
        final int x0 = Math.max(0, (int) Math.floor(bounds.getMinX() - marg));
        final int x1 = Math.min(w, (int) Math.ceil(bounds.getMaxX() + marg));
        final int y0 = Math.max(0, (int) Math.floor(bounds.getMinY() - marg));
        final int y1 = Math.min(dst.getHeight(), (int) Math.ceil(bounds.getMaxY() + marg));
        if (x0 >= x1 || y0 >= y1) return;
        final List<Gota.Relleno> rel = new ArrayList<>();
        for (int i = 0; i < gotas.size(); i++) rel.add(rellenos.get(i).para(gotas.get(i)));
        IntStream.range(y0, y1).parallel().forEach(j -> {
            for (int i = x0; i < x1; i++) {
                double x = i + 0.5, y = j + 0.5;
                double[] ev = evalua(x, y);
                if (ev[1] < 0) continue;
                Gota g = gotas.get((int) ev[1]);
                Gota.Relleno rl = rel.get((int) ev[1]);
                Coordenadas.Muestra m = g.parametros(x, y);
                int c = rl.usaUv() ? rl.argb(m, g.uv(x, y)) : rl.argb(m);
                int idx = base + j * stride + i;
                datos[idx] = sobre(datos[idx], c, ev[0]);
            }
        });
    }

    // ---------- Internos ----------

    private static double smin(double a, double b, double k) {
        if (k <= 0) return Math.min(a, b);
        double h = Math.max(k - Math.abs(a - b), 0) / k;
        return Math.min(a, b) - h * h * k / 4;
    }

    /** {cobertura, gota que manda (o -1)} en (x, y): sdf = unión suave de las sdf, color de la gota de menor ρ. */
    private double[] evalua(double x, double y) {
        double marg = 2 + suaviza;
        double smin = Double.MAX_VALUE, rmin = Double.MAX_VALUE;
        int mejor = -1;
        double[] s = new double[gotas.size()];
        for (int k = 0; k < gotas.size(); k++) {
            s[k] = Double.MAX_VALUE;
            Rectangle2D bb = gotas.get(k).bounds();
            if (x < bb.getMinX() - marg || x > bb.getMaxX() + marg || y < bb.getMinY() - marg || y > bb.getMaxY() + marg)
                continue;
            s[k] = gotas.get(k).sdf(x, y);
            smin = smin == Double.MAX_VALUE ? s[k] : smin(smin, s[k], suaviza);
        }
        if (smin == Double.MAX_VALUE) return new double[] { 0, -1 };
        double cob = Math.max(0, Math.min(1, 0.5 - smin));
        if (cob <= 0) return new double[] { 0, -1 };
        for (int k = 0; k < gotas.size(); k++) {
            if (s[k] >= 0.75 + suaviza) continue;
            double rho = gotas.get(k).parametros(x, y).rho();
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

    private static double largo(Gota.Vec v) { return Math.hypot(v.x(), v.y()); }

    /** Datos de una arista tras fijar tiradores y pendiente. */
    private record Ar(int a, int b, Gota.Vec va, Gota.Vec vb, double lamA, double lamB, double q, boolean fija) {}

    private void construye() {
        if (gotas != null) return;
        if (aristas.isEmpty()) throw new IllegalStateException("la composición no tiene aristas");
        for (Nodo n : nodos) {
            int g = grado(n);
            if (g == 0) throw new IllegalStateException("nodo " + n.id + " sin aristas");
            if (n.aspecto != 1 && g != 1)
                throw new IllegalStateException("aspecto distinto de 1 solo en nodos de grado 1 (nodo " + n.id + ", grado " + g + ")");
        }
        // Tiradores.
        List<Ar> ars = new ArrayList<>();
        for (Arista e : aristas) {
            if (e.a == e.b && (e.va == null || e.vb == null))
                throw new IllegalStateException("lazo en el nodo " + e.a.id + ": hay que dar los dos tiradores");
            double d = Math.hypot(e.b.x - e.a.x, e.b.y - e.a.y);
            double[] ua = salida(e, true), ub = salida(e, false);
            Gota.Vec va = e.va != null ? e.va : new Gota.Vec(ua[0] * d / 3, ua[1] * d / 3);
            Gota.Vec vb = e.vb != null ? e.vb : new Gota.Vec(ub[0] * d / 3, ub[1] * d / 3);
            ars.add(new Ar(e.a.id, e.b.id, va, vb, e.a.aspecto, e.b.aspecto, Double.NaN, e.va != null || e.vb != null));
        }
        if (ajustaPendientes) ars = igualaK(ars);

        gotas = new ArrayList<>();
        rellenos = new ArrayList<>();
        Rectangle2D.Double bb = null;
        for (int i = 0; i < ars.size(); i++) {
            Ar r = ars.get(i);
            Nodo A = nodos.get(r.a), B = nodos.get(r.b);
            Gota.Perfil base = Double.isNaN(r.q)
                    ? Gota.Perfil.recto()
                    : Gota.Perfil.radioMedio((A.radio + B.radio) / 4 + Math.max(0, r.q) / 2, 0.5, 1);
            Gota g = Gota.curva(new Point2D.Double(A.x, A.y), A.radio, r.va, new Point2D.Double(B.x, B.y), B.radio, r.vb,
                    base.aspecto(r.lamA, r.lamB));
            gotas.add(g);
            rellenos.add(Colores.cadenaLateral(lateralDe(A, aristas.get(i), true), lateralDe(B, aristas.get(i), false), mezcla));
            Rectangle2D gb = g.bounds();
            if (bb == null) bb = new Rectangle2D.Double(gb.getX(), gb.getY(), gb.getWidth(), gb.getHeight());
            else bb.add(gb);
        }
        bounds = bb;
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

    private Colores.Lateral lateralDe(Nodo n, Arista e, boolean inicio) {
        int centro = n.centro != null ? n.centro : (n.sectores != null && n.sectores.length > 0 ? n.sectores[0] : 0xFFCCCCCC);
        List<Brazo> br = brazos(n);
        int d = br.size();
        int[] col = new int[d];
        java.util.Arrays.fill(col, centro);
        if (n.sectores != null) {
            if (n.sectores.length != d)
                throw new IllegalStateException("nodo " + n.id + ": " + n.sectores.length + " sectores para " + d + " ramas");
            col = n.sectores;
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
        for (int k = 0; k < d; k++)
            if (br.get(k).e == e && br.get(k).inicio == inicio) {
                int[] sc = sectoresDe(br, k);
                return new Colores.Lateral(col[sc[0]], centro, col[sc[1]], n.ajusteLateral, n.ajusteArco);
            }
        return Colores.Lateral.uniforme(centro);
    }

    // ---------- Pendiente del radio ----------

    /**
     * Para que el contorno sea G1 en un nodo de grado 2 hay que igualar a ambos lados k = r'/|c'|. El perfil de radio
     * de cada gota es una cuadrática con q libre: k0 = 2(q - r1)/(3 l1) y k1 = 2(r2 - q)/(3 l2), con l1 y l2 las
     * longitudes de los tiradores, lo que deja una ecuación por arista: k0*·l1 + k1*·l2 = 2(r2 - r1)/3. Se busca el
     * k de cada nodo (de grado 2) más cercano al de Catmull-Rom que cumpla esas ecuaciones con tiradores de d/3
     * (mínimos cuadrados con una regularización pequeña) y después se corrigen lo mínimo las longitudes.
     * Los nodos con alguna arista de tiradores explícitos, los de grado 1 y los de grado >= 3 no imponen k.
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
        resuelveK(ars, kn, activo, pq);

        List<Ar> nuevas = new ArrayList<>();
        for (int i = 0; i < ars.size(); i++) {
            Ar r = ars.get(i);
            if (r.fija) { nuevas.add(r); continue; }
            Nodo A = nodos.get(r.a), B = nodos.get(r.b);
            double d = Math.hypot(B.x - A.x, B.y - A.y), L = d / 3, dr = B.radio - A.radio, c = 2 * dr / 3;
            Double ka = objetivo(i, r, r.a, kn, activo, pq), kb = objetivo(i, r, r.b, kn, activo, pq);
            if (ka == null && kb == null) { ka = c / (2 * L); kb = ka; }
            else if (ka == null) ka = c / L - kb;
            else if (kb == null) kb = c / L - ka;
            double den = ka * ka + kb * kb, lam = den < 1e-12 ? 0 : (c - (ka + kb) * L) / den;
            double l1 = Math.max(0.25 * L, Math.min(3 * L, L + lam * ka));
            double l2 = Math.max(0.25 * L, Math.min(3 * L, L + lam * kb));
            double q = A.radio + 1.5 * ka * l1;
            double[] ua = unit(r.va.x(), r.va.y()), ub = unit(r.vb.x(), r.vb.y());
            nuevas.add(new Ar(r.a, r.b, new Gota.Vec(ua[0] * l1, ua[1] * l1), new Gota.Vec(ub[0] * l2, ub[1] * l2),
                    r.lamA, r.lamB, q, false));
        }
        return nuevas;
    }

    /** +1 si la arista i va a favor del sentido de viaje p -> n -> q en el nodo n; -1 si va en contra. */
    private static double signo(int i, Ar r, int n, int[][] pq) {
        boolean esQ = pq[n][1] == i;
        boolean sale = r.a == n;
        return (esQ == sale) ? 1 : -1;
    }

    private static Double objetivo(int i, Ar r, int n, double[] kn, boolean[] activo, int[][] pq) {
        if (!activo[n]) return null;
        double k = Math.max(-0.8, Math.min(0.8, kn[n]));
        return signo(i, r, n, pq) * k;
    }

    /**
     * Con tiradores de d/3 la cuadrática exige, en cada arista entre dos nodos activos, k_inicio + k_fin =
     * 2(r2 - r1)/d. Mínimos cuadrados sobre los k de los nodos, regularizados hacia los de Catmull-Rom.
     */
    private void resuelveK(List<Ar> ars, double[] kn, boolean[] activo, int[][] pq) {
        int n = nodos.size();
        int[] idx = new int[n];
        int m = 0;
        for (int i = 0; i < n; i++) idx[i] = activo[i] ? m++ : -1;
        if (m == 0) return;
        double mu = 1e-3;
        double[][] M = new double[m][m + 1];
        for (int i = 0; i < n; i++) if (idx[i] >= 0) { M[idx[i]][idx[i]] += mu; M[idx[i]][m] += mu * kn[i]; }
        for (int e = 0; e < ars.size(); e++) {
            Ar r = ars.get(e);
            if (!activo[r.a] || !activo[r.b]) continue;
            Nodo A = nodos.get(r.a), B = nodos.get(r.b);
            double s = 2 * (B.radio - A.radio) / Math.hypot(B.x - A.x, B.y - A.y);
            double si = signo(e, r, r.a, pq), sj = signo(e, r, r.b, pq);
            int a = idx[r.a], b = idx[r.b];
            M[a][a] += 1; M[b][b] += 1; M[a][b] += si * sj; M[b][a] += si * sj;
            M[a][m] += si * s; M[b][m] += sj * s;
        }
        for (int c = 0; c < m; c++) {
            int piv = c;
            for (int r = c + 1; r < m; r++) if (Math.abs(M[r][c]) > Math.abs(M[piv][c])) piv = r;
            double[] tmp = M[c]; M[c] = M[piv]; M[piv] = tmp;
            for (int r = 0; r < m; r++) {
                if (r == c) continue;
                double f = M[r][c] / M[c][c];
                for (int k = c; k <= m; k++) M[r][k] -= f * M[c][k];
            }
        }
        for (int i = 0; i < n; i++) if (idx[i] >= 0) kn[i] = M[idx[i]][m] / M[idx[i]][idx[i]];
    }
}
