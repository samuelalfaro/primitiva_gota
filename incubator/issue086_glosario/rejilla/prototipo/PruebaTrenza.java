import color.Colores;
import color.Mezcla;
import coordenadas.Coordenadas;
import coordenadas.Opciones;
import coordenadas.Solape;
import forma.Gota;
import geometria.Geometria;
import geometria.Tinta;
import geometria.composicion.Pintada;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/**
 * Estudio #86: la trenza aleatoria (TrenzaAleatoria, un lazo cerrado de 16 nodos con muchos cruces consigo mismo y la altura
 * por arista) pintada de dos maneras:
 * A) el Grafo con el Plotter actual: una sola capa por píxel, la de la gota que manda, con la cobertura de la unión;
 * B) cada arista como su propia cinta que aporta a la Rejilla su color, su cobertura y la altura del cruce (la misma función
 *    que da la altura a las aristas del Grafo), y la Rejilla ordena por altura en cada muestra.
 * La hipótesis: en B el borde de la cinta que pasa por encima de otra queda con antialiasing sobre la de abajo, mientras
 * que en A el píxel lleva la cobertura de la unión (1 sobre la otra cinta) y el borde queda duro.
 */
public class PruebaTrenza {
    static final int N_NODOS = 16;

    /** Una arista del lazo como geometría: color y cobertura de su gota; altura del cruce según la posición a lo largo de ella. */
    static final class Cinta implements Geometria {
        final Gota g; final Pintada p; final DoubleUnaryOperator alt; final boolean prioridadCuerpo;
        Cinta(Gota g, Pintada p, DoubleUnaryOperator alt, boolean prioridadCuerpo) { this.g = g; this.p = p; this.alt = alt; this.prioridadCuerpo = prioridadCuerpo; }
        @Override public Rectangle2D bounds() { return p.bounds(); }
        @Override public Shape silueta() { return p.silueta(); }
        @Override public String label() { return null; }
        @Override public double altura() { return 0; }
        @Override public void capas(double x, double y, Opciones op, Consumer<Tinta> out) {
            p.capas(x, y, op, t -> {
                double tt = Coordenadas.porRho(g, x, y, Solape.TRAZO).t();
                double h = alt.applyAsDouble(g.fraccion(tt));
                if (prioridadCuerpo) {              // la regla del Grafo: entre gotas que contienen el píxel manda primero la que lo tiene en su cuerpo (no en la tapa del nodo)
                    coordenadas.Cortes ck = op.tira().cortes(g);
                    double v = Coordenadas.uv(g, x, y, op).v();
                    if (v >= ck.c1() && v <= ck.c3()) h += 1000;
                }
                out.accept(new Tinta(t.argb(), t.cobertura(), h));
            });
        }
    }

    /**
     * Un Grafo que decide por dentro y emite varias capas: reúne las de sus cintas, las ordena con SU regla (cuerpo antes que
     * tapa, luego altura) y las emite de abajo a arriba, todas con la altura de la geometría (0). La Rejilla, al ordenar de
     * forma estable, solo reparte entre geometrías distintas y respeta ese orden interno.
     */
    static final class GrafoOrdenado implements Geometria {
        final List<Geometria> cintas; final Rectangle2D caja;
        GrafoOrdenado(List<Geometria> cintas) {
            this.cintas = cintas;
            Rectangle2D b = null;
            for (Geometria c : cintas) { Rectangle2D r = c.bounds(); if (b == null) b = new Rectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight()); else b.add(r); }
            this.caja = b;
        }
        @Override public Rectangle2D bounds() { return caja; }
        @Override public Shape silueta() { return caja; }
        @Override public String label() { return null; }
        @Override public double altura() { return 0; }
        @Override public void capas(double x, double y, Opciones op, Consumer<Tinta> out) {
            List<Tinta> ts = new ArrayList<>();
            for (Geometria c : cintas) { Rectangle2D r = c.bounds(); if (x >= r.getMinX() - 2 && x <= r.getMaxX() + 2 && y >= r.getMinY() - 2 && y <= r.getMaxY() + 2) c.capas(x, y, op, ts::add); }
            ts.sort(java.util.Comparator.comparingDouble(Tinta::altura));              // su regla: cuerpo (+1000) antes que tapa, luego altura
            for (Tinta t : ts) out.accept(new Tinta(t.argb(), t.cobertura(), 0));      // hacia fuera, todas a la altura de la geometría
        }
    }

    static List<Geometria> cintas(List<Gota> gotas, TrenzaAleatoria.Datos d, double[] tau, double[] val, double[] ext, int n, boolean cuerpo) {
        List<Geometria> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int ii = i;
            Colores.Lateral a = Colores.Lateral.uniforme(d.color()[i]), b = Colores.Lateral.uniforme(d.color()[(i + 1) % n]);
            Pintada p = Pintada.de(gotas.get(i), Colores.cadenaLateral(a, b, Mezcla.RGB));
            l.add(new Cinta(gotas.get(i), p, x -> TrenzaAleatoria.altura(ii + x, tau, val, ext, n), cuerpo));
        }
        return l;
    }

    public static void main(String[] args) throws Exception {
        long semilla = args.length > 0 ? Long.parseLong(args[0]) : 7;
        File dir = new File(args.length > 1 ? args[1] : ".");
        dir.mkdirs();
        int n = N_NODOS;
        TrenzaAleatoria.Datos d = TrenzaAleatoria.datos(semilla, n);
        TrenzaAleatoria.Trenza conAlt = TrenzaAleatoria.construye(d, true, true);
        TrenzaAleatoria.Trenza sinAlt = TrenzaAleatoria.construye(d, true, false);       // mismas gotas (alisadas), sin altura
        System.out.printf("semilla %d: %d cruces, alternan=%b%n", semilla, conAlt.cruces(), conAlt.alternan());

        // la función de altura global (la misma que TrenzaAleatoria pone en las aristas)
        double[][] visitas = TrenzaAleatoria.cruces(sinAlt.c(), n);
        List<double[]> todas = new ArrayList<>();
        for (int k = 0; k < visitas.length; k++) {
            todas.add(new double[] { visitas[k][0], k, visitas[k][2] });
            todas.add(new double[] { visitas[k][1], k, visitas[k][3] });
        }
        todas.sort((p, q) -> Double.compare(p[0], q[0]));
        int K = todas.size();
        double[] tau = new double[K], val = new double[K], ext = new double[K];
        for (int k = 0; k < K; k++) { tau[k] = todas.get(k)[0]; val[k] = (k % 2 == 0) ? 1 : -1; ext[k] = todas.get(k)[2]; }

        List<Gota> gotas = sinAlt.c().gotas();
        List<Geometria> cintas = cintas(gotas, d, tau, val, ext, n, false), cintasD = cintas(gotas, d, tau, val, ext, n, true);

        Opciones op = Opciones.POR_DEFECTO.conSolape(Solape.TRAZO);
        long t0 = System.nanoTime();
        BufferedImage A = TrenzaAleatoria.pinta(conAlt.c());
        System.out.printf("A  Grafo + Plotter actual: %.0f ms%n", (System.nanoTime() - t0) / 1e6);
        ImageIO.write(A, "png", new File(dir, "trenza_A_grafo.png"));
        for (Rejilla.Cobertura cb : new Rejilla.Cobertura[] { Rejilla.Cobertura.TAL_CUAL }) {
            BufferedImage B = fondo();
            Rejilla r = new Rejilla(1, 32, cb, Rejilla.Filtro.SRGB);
            for (Geometria c : cintas) r.aporta(c, op);
            Rejilla.Estadisticas e = r.prensa(B);
            int[] dd = diferencias(A, B);
            System.out.printf("B  Rejilla N=1 %-10s %.0f ms  máx. profundidad=%d  dif. con A: %d px (canal hasta %d)%n", cb, e.nanos() / 1e6, e.maxProfundidad(), dd[0], dd[1]);
            ImageIO.write(B, "png", new File(dir, "trenza_B_rejilla_n1.png"));
        }
        BufferedImage C = fondo();
        Rejilla r3 = new Rejilla(3, 32, Rejilla.Cobertura.ESTRECHADA, Rejilla.Filtro.SRGB);
        for (Geometria c : cintas) r3.aporta(c, op);
        Rejilla.Estadisticas e3 = r3.prensa(C);
        int[] d3 = diferencias(A, C);
        System.out.printf("C  Rejilla N=3 ESTRECHADA %.0f ms  máx. profundidad=%d  dif. con A: %d px (canal hasta %d)%n", e3.nanos() / 1e6, e3.maxProfundidad(), d3[0], d3[1]);
        ImageIO.write(C, "png", new File(dir, "trenza_C_rejilla_n3.png"));

        // D: igual que B/C pero con la regla del Grafo (cuerpo antes que tapa) codificada en la altura del aporte
        BufferedImage D = fondo();
        Rejilla.Estadisticas e1 = rejilla(1, Rejilla.Cobertura.TAL_CUAL, cintasD, op).prensa(D);
        System.out.printf("D  Rejilla N=1 + prioridad de cuerpo  %.0f ms  dif. con A: %d px (canal hasta %d)%n", e1.nanos() / 1e6, diferencias(A, D)[0], diferencias(A, D)[1]);
        ImageIO.write(D, "png", new File(dir, "trenza_D_rejilla_n1_cuerpo.png"));
        BufferedImage E = fondo();
        Rejilla.Estadisticas e2 = rejilla(3, Rejilla.Cobertura.ESTRECHADA, cintasD, op).prensa(E);
        System.out.printf("E  Rejilla N=3 ESTRECHADA + prioridad de cuerpo  %.0f ms  dif. con A: %d px (canal hasta %d)%n", e2.nanos() / 1e6, diferencias(A, E)[0], diferencias(A, E)[1]);
        ImageIO.write(E, "png", new File(dir, "trenza_E_rejilla_n3_cuerpo.png"));

        // F: el Grafo decide el orden por dentro y emite varias capas a altura 0; la Rejilla solo ordena entre geometrías (aquí, además, un fondo)
        BufferedImage F = fondo();
        Rejilla rf = new Rejilla(1, 32, Rejilla.Cobertura.TAL_CUAL, Rejilla.Filtro.SRGB);
        rf.aporta(new GrafoOrdenado(cintasD), op);
        Rejilla.Estadisticas ef = rf.prensa(F);
        int[] dF = diferencias(D, F);
        System.out.printf("F  Rejilla N=1 con un GrafoOrdenado (orden interno)  %.0f ms  dif. con D: %d px (canal hasta %d); con A: %d px%n", ef.nanos() / 1e6, dF[0], dF[1], diferencias(A, F)[0]);
    }

    static Rejilla rejilla(int n, Rejilla.Cobertura cb, List<Geometria> gs, Opciones op) {
        Rejilla r = new Rejilla(n, 32, cb, Rejilla.Filtro.SRGB);
        for (Geometria g : gs) r.aporta(g, op);
        return r;
    }

    static BufferedImage fondo() {
        BufferedImage im = new BufferedImage(TrenzaAleatoria.W, TrenzaAleatoria.H, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), TrenzaAleatoria.FONDO);
        return im;
    }

    static int[] diferencias(BufferedImage a, BufferedImage b) {
        int[] r = new int[2];
        for (int j = 0; j < a.getHeight(); j++) for (int i = 0; i < a.getWidth(); i++) {
            int p = a.getRGB(i, j), q = b.getRGB(i, j);
            if (p == q) continue;
            r[0]++;
            for (int s : new int[] { 24, 16, 8, 0 }) r[1] = Math.max(r[1], Math.abs(((p >> s) & 255) - ((q >> s) & 255)));
        }
        return r;
    }
}
