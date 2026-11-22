import composicion.Grafo;
import coordenadas.Solape;
import color.Mezcla;
import coordenadas.Opciones;
import forma.Gota;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Prueba de fuego de {@link Grafo#alisar()} y {@link Composicion.Arista#altura}: una única composición cerrada
 * de puntos, radios y colores aleatorios (semilla fija, replicable), con muchos cruces de la cinta consigo misma.
 * <ul>
 * <li>A: por defecto (tiradores automáticos, TRAZO).</li>
 * <li>B: alisar() (curvatura continua).</li>
 * <li>C: alisar() + altura: se calculan los cruces de la espina y se alterna por encima / por debajo en el orden del
 *     recorrido (en una curva plana cerrada siempre es posible: entre las dos pasadas por un cruce hay un número par
 *     de cruces), con la altura suavizada entre cruces para que sea continua en los nodos.</li>
 * </ul>
 * Sin argumentos escribe trenza_aleatoria.png (semilla 2026). Con "semillas N" comprueba las semillas 1..N sin pintar
 * (excepto las 12 primeras): construcción, paridad de los cruces, continuidad de la curvatura, aristas rígidas.
 */
public class TrenzaAleatoria {
    static final int W = 640, H = 640, FONDO = 0xFFF4F1EA;

    record Datos(double[] x, double[] y, double[] r, int[] color) {}

    /** Puntos aleatorios: saltos de 90 a 260 entre consecutivos (también el de cierre), separados al menos 50 entre sí. */
    static Datos datos(long semilla, int n) {
        Random rnd = new Random(semilla);
        while (true) {
            double[] x = new double[n], y = new double[n], r = new double[n];
            int[] c = new int[n];
            boolean ok = true;
            for (int i = 0; i < n && ok; i++) {
                ok = false;
                for (int intento = 0; intento < 400 && !ok; intento++) {
                    double px = 70 + rnd.nextDouble() * (W - 140), py = 70 + rnd.nextDouble() * (H - 140);
                    boolean bien = true;
                    for (int j = 0; j < i && bien; j++) {
                        double d = Math.hypot(px - x[j], py - y[j]);
                        if (d < 50 || (j == i - 1 && (d < 90 || d > 260))) bien = false;
                    }
                    if (bien && i == n - 1) { double d = Math.hypot(px - x[0], py - y[0]); bien = d >= 90 && d <= 260; }
                    if (bien) { x[i] = px; y[i] = py; ok = true; }
                }
                r[i] = 9 + rnd.nextDouble() * 9;
                c[i] = Color.HSBtoRGB((float) rnd.nextDouble(), (float) (0.55 + 0.4 * rnd.nextDouble()), (float) (0.75 + 0.25 * rnd.nextDouble())) | 0xFF000000;
            }
            if (ok) return new Datos(x, y, r, c);
        }
    }

    record Trenza(Grafo c, Grafo.Arista[] aristas, int cruces, boolean alternan) {}

    static Trenza construye(Datos d, boolean alisa, boolean alturas) {
        int n = d.x().length;
        Grafo c = new Grafo().mezcla(Mezcla.RGB);
        var nodos = new Grafo.Nodo[n];
        for (int i = 0; i < n; i++) nodos[i] = c.nodo(d.x()[i], d.y()[i], d.r()[i]).centro(d.color()[i]);
        var aristas = new Grafo.Arista[n];
        for (int i = 0; i < n; i++) aristas[i] = c.arista(nodos[i], nodos[(i + 1) % n]);
        c.relajar();
        if (alisa || alturas) c.alisar();
        int cruces = 0; boolean alternan = true;
        if (alturas) {
            double[][] visitas = cruces(c, n);                           // {tau_a, tau_b} por cruce
            cruces = visitas.length;
            List<double[]> todas = new ArrayList<>();                    // {tau, id del cruce, mitad del solape en unidades de tau}
            for (int k = 0; k < visitas.length; k++) {
                todas.add(new double[] { visitas[k][0], k, visitas[k][2] });
                todas.add(new double[] { visitas[k][1], k, visitas[k][3] });
            }
            todas.sort((p, q) -> Double.compare(p[0], q[0]));
            int K = todas.size();
            double[] tau = new double[K], val = new double[K], ext = new double[K];
            int[] pos = new int[cruces * 2 + 1]; Arrays.fill(pos, -1);
            int[] primera = new int[cruces]; Arrays.fill(primera, -1);
            for (int k = 0; k < K; k++) {
                tau[k] = todas.get(k)[0]; val[k] = (k % 2 == 0) ? 1 : -1; ext[k] = todas.get(k)[2];
                int id = (int) todas.get(k)[1];
                if (primera[id] < 0) primera[id] = k; else if (((k - primera[id]) & 1) == 0) alternan = false;
            }
            if (K > 0 && alternan) {
                for (int i = 0; i < n; i++) {
                    final int ii = i;
                    aristas[i].altura(x -> altura(ii + x, tau, val, ext, n));
                }
            }
        }
        return new Trenza(c, aristas, cruces, alternan);
    }

    /** Altura global en tau (posición a lo largo del lazo, 0..n): interpola con smoothstep entre los valores +-1 de los cruces. */
    static double altura(double t, double[] tau, double[] val, double[] ext, int n) {
        int K = tau.length;
        int k = Arrays.binarySearch(tau, t);
        if (k >= 0) return val[k];
        int sig = -k - 1;                                              // primer tau mayor que t
        int a = (sig - 1 + K) % K, b = sig % K;
        double ta = tau[a] - (sig == 0 ? n : 0), tb = tau[b] + (sig == K ? n : 0);
        // la altura se queda en +-1 mientras la cinta solapa con la otra en el cruce (ext) y cambia solo en el hueco libre; si no lo hay, en el punto medio
        double lo = ta + ext[a], hi = tb - ext[b];
        if (hi - lo < 1e-3) { double m = (ta + tb) / 2; lo = m - 5e-4; hi = m + 5e-4; }
        double u = Math.max(0, Math.min(1, (t - lo) / (hi - lo)));
        u = u * u * (3 - 2 * u);
        return val[a] + (val[b] - val[a]) * u;
    }

    /** Cruces de la espina consigo misma (aristas no contiguas), como pares de posiciones tau = índice de arista + fracción de arco. */
    static double[][] cruces(Grafo c, int n) {
        var g = c.gotas();
        final int S = 48;
        double[][][] pt = new double[n][S + 1][3];                     // x, y, fracción de arco
        for (int i = 0; i < n; i++)
            for (int s = 0; s <= S; s++) {
                double t = s / (double) S;
                Point2D p = g.get(i).centro(t);
                pt[i][s][0] = p.getX(); pt[i][s][1] = p.getY(); pt[i][s][2] = g.get(i).fraccion(t);
            }
        List<double[]> res = new ArrayList<>();
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
            boolean contiguas = j == i + 1 || (i == 0 && j == n - 1);   // comparten un nodo: se ignora el tramo pegado a él
            for (int a = 0; a < S; a++) for (int b = 0; b < S; b++) {
                if (contiguas && ((j == i + 1 && a == S - 1 && b == 0) || (i == 0 && j == n - 1 && a == 0 && b == S - 1))) continue;
                double[] p = pt[i][a], p2 = pt[i][a + 1], q = pt[j][b], q2 = pt[j][b + 1];
                double rx = p2[0] - p[0], ry = p2[1] - p[1], sx = q2[0] - q[0], sy = q2[1] - q[1];
                double den = rx * sy - ry * sx;
                if (Math.abs(den) < 1e-12) continue;
                double u = ((q[0] - p[0]) * sy - (q[1] - p[1]) * sx) / den, v = ((q[0] - p[0]) * ry - (q[1] - p[1]) * rx) / den;
                if (u < 0 || u >= 1 || v < 0 || v >= 1) continue;
                double xi = p[2] + u * (p2[2] - p[2]), xj = q[2] + v * (q2[2] - q[2]);
                double seno = Math.abs(den) / (Math.hypot(rx, ry) * Math.hypot(sx, sy));
                double ri = g.get(i).radio((a + u) / S), rj = g.get(j).radio((b + v) / S);
                double mitad = Math.min((ri + rj) / Math.max(seno, 1e-3), 8 * (ri + rj));   // semilongitud del solape sobre cada cinta, en píxeles
                res.add(new double[] { i + xi, j + xj, mitad / g.get(i).longitud(), mitad / g.get(j).longitud() });
            }
        }
        return res.toArray(new double[0][]);
    }

    static double curv(Point2D a, Point2D b, Point2D c) {
        double cross = (b.getX() - a.getX()) * (c.getY() - a.getY()) - (b.getY() - a.getY()) * (c.getX() - a.getX());
        return 2 * cross / (a.distance(b) * b.distance(c) * c.distance(a));
    }

    /** Salto máximo de curvatura en los nodos, relativo a la curvatura máxima. */
    static double salto(Grafo c) {
        var g = c.gotas();
        int n = g.size();
        double e = 1e-5, s = 0, kmax = 1e-9;            // e pequeño: con 1e-3 las puntas dan saltos falsos de hasta el 15 %
        for (int i = 0; i < n; i++) {
            Gota p = g.get(i), q = g.get((i + 1) % n);
            double kp = curv(p.centro(1 - 2 * e), p.centro(1 - e), p.centro(1)), kq = curv(q.centro(0), q.centro(e), q.centro(2 * e));
            s = Math.max(s, Math.abs(kp - kq)); kmax = Math.max(kmax, Math.max(Math.abs(kp), Math.abs(kq)));
        }
        return s / kmax;
    }

    static BufferedImage pinta(Grafo c) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), FONDO);
        c.render(im, Opciones.POR_DEFECTO.conSolape(Solape.TRAZO));
        return im;
    }

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && args[0].equals("semillas")) { semillas(Integer.parseInt(args[1])); return; }
        long semilla = args.length >= 1 ? Long.parseLong(args[0]) : 2026;
        int n = 16;
        Datos d = datos(semilla, n);
        Trenza a = construye(d, false, false), b = construye(d, true, false), c = construye(d, true, true);
        System.out.printf("semilla %d, %d nodos, %d cruces, alternan=%b, rígidas A/B/C = %d/%d/%d, plegadas A/B = %d/%d, salto de curvatura A = %.1f %%, B = %.2f %%%n",
                semilla, n, c.cruces(), c.alternan(), a.c().rigidas().size(), b.c().rigidas().size(), c.c().rigidas().size(),
                a.c().plegadas().size(), b.c().plegadas().size(), 100 * salto(a.c()), 100 * salto(b.c()));
        BufferedImage hoja = new BufferedImage(3 * W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hoja.createGraphics();
        g.drawImage(pinta(a.c()), 0, 0, null); g.drawImage(pinta(b.c()), W, 0, null); g.drawImage(pinta(c.c()), 2 * W, 0, null);
        g.dispose();
        ImageIO.write(hoja, "png", new File("trenza_aleatoria.png"));
    }

    static void semillas(int total) {
        int fallos = 0, sinCruces = 0, noAlt = 0, rig = 0, crucesTot = 0, plA = 0, plB = 0, semPlA = 0, semPlB = 0;
        double peorA = 0, peorB = 0; int salB = 0;
        for (int s = 1; s <= total; s++) {
            try {
                Datos d = datos(s, 16);
                Trenza a = construye(d, false, false), b = construye(d, true, false), c = construye(d, true, true);
                peorA = Math.max(peorA, salto(a.c())); double sb = salto(b.c()); peorB = Math.max(peorB, sb); if (sb > 0.01) salB++;
                crucesTot += c.cruces();
                if (c.cruces() == 0) sinCruces++;
                if (!c.alternan()) noAlt++;
                rig += b.c().rigidas().size();
                int pa = a.c().plegadas().size(), pb = b.c().plegadas().size();
                plA += pa; plB += pb; if (pa > 0) semPlA++; if (pb > 0) semPlB++;
                if (s <= 3) { pinta(a.c()); pinta(b.c()); pinta(c.c()); }
            } catch (Throwable t) { fallos++; System.out.println("semilla " + s + ": " + t); }
        }
        System.out.printf("%d semillas: %d fallos, %d sin cruces, %d sin paridad, %.1f cruces de media, aristas rígidas en total %d, salto de curvatura máx sin alisar %.1f %%, con alisar %.2f %%%n",
                total, fallos, sinCruces, noAlt, crucesTot / (double) total, rig, 100 * peorA, 100 * peorB);
        System.out.printf("semillas con salto de curvatura > 1 %% tras alisar: %d%n", salB);
        System.out.printf("aristas plegadas (kappa*r >= 1): sin alisar %d en %d semillas, con alisar %d en %d semillas%n", plA, semPlA, plB, semPlB);
    }
}
