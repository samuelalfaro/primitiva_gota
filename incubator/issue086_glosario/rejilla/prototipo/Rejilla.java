import coordenadas.Opciones;
import geometria.Geometria;
import geometria.Tinta;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * PROTOTIPO del estudio #86 (no es de la biblioteca): una Rejilla real.
 * <p>
 * Las geometrías se <b>aportan</b> (no se pintan); {@link #prensa(BufferedImage)} reparte la imagen en franjas de {@code franja}
 * filas de píxeles y, por cada píxel, muestrea una cuadrícula N×N: en cada muestra pide a todas las geometrías sus
 * {@link Tinta}s (el contrato actual {@code capas}), las ordena por altura (estable: a igual altura, el orden de aporte y de
 * emisión), las compone src-over y, al final, promedia las N×N muestras (en premultiplicado) y compone el píxel sobre lo que
 * ya hubiera en la imagen.
 * <p>
 * Las aportaciones de cada fila de muestras se materializan en memoria (arrays planos) hasta resolverlas, para medir cuánto
 * ocupa una franja; no se conservan tras prensar.
 */
public final class Rejilla {
    /** Qué se hace con la cobertura que entrega cada geometría. */
    public enum Cobertura {
        /** Tal cual: parcial, calculada para un píxel de lado 1, aunque la muestra sea de 1/N. */
        TAL_CUAL,
        /** Reescalada para la huella de la muestra: {@code clamp(0,5 + N·(c − 0,5))}; solo vale si la cobertura sale de una distancia con signo. */
        ESTRECHADA,
        /** 0 o 1 con umbral 0,5 (muestra puntual). En el empate exacto (0,5) cubre. */
        BINARIA
    }

    /** En qué espacio se promedian las N×N muestras. */
    public enum Filtro { SRGB, LINEAL }

    public record Estadisticas(long muestras, long aportes, int maxProfundidad, long maxAportesFranja, long nanos) {}

    private final int n, franja;
    private final Cobertura cobertura;
    private final Filtro filtro;
    private final List<Geometria> geos = new ArrayList<>();
    private final List<Opciones> ops = new ArrayList<>();

    public Rejilla(int n, int franja, Cobertura cobertura, Filtro filtro) {
        if (n < 1 || franja < 1) throw new IllegalArgumentException("n y franja >= 1");
        this.n = n; this.franja = franja; this.cobertura = cobertura; this.filtro = filtro;
    }

    public Rejilla aporta(Geometria g, Opciones op) { geos.add(g); ops.add(op); return this; }

    public Rejilla aporta(Geometria g) { return aporta(g, Opciones.POR_DEFECTO); }

    /** Una fila de muestras materializada: aportes por muestra en listas enlazadas sobre arrays planos. */
    private static final class Fila {
        final int[] cabeza; int[] sig = new int[256], argb = new int[256]; double[] cob = new double[256], alt = new double[256];
        int cuenta = 0;
        Fila(int muestras) { cabeza = new int[muestras]; Arrays.fill(cabeza, -1); }
        void anade(int s, Tinta t, double c) {
            if (cuenta == sig.length) {
                int m = cuenta * 2;
                sig = Arrays.copyOf(sig, m); argb = Arrays.copyOf(argb, m); cob = Arrays.copyOf(cob, m); alt = Arrays.copyOf(alt, m);
            }
            sig[cuenta] = cabeza[s]; argb[cuenta] = t.argb(); cob[cuenta] = c; alt[cuenta] = t.altura(); cabeza[s] = cuenta++;
        }
    }

    private double cob(double c) {
        return switch (cobertura) {
            case TAL_CUAL -> c;
            case ESTRECHADA -> Math.max(0, Math.min(1, 0.5 + n * (c - 0.5)));
            case BINARIA -> c >= 0.5 ? 1 : 0;
        };
    }

    private double aEspacio(double c8) {
        double c = c8 / 255.0;
        return filtro == Filtro.SRGB ? c : (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
    }

    private double deEspacio(double c) {
        double v = filtro == Filtro.SRGB ? c : (c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055);
        return Math.max(0, Math.min(255, v * 255.0));
    }

    public Estadisticas prensa(BufferedImage dst) {
        if (dst.getType() != BufferedImage.TYPE_INT_ARGB) throw new IllegalArgumentException("TYPE_INT_ARGB");
        final int w = dst.getWidth(), h = dst.getHeight();
        // Grafo construye de forma perezosa y sin sincronizar: bounds() la dispara, así que se pide una vez, aquí y en serie,
        // antes del muestreo en paralelo (el Plotter actual hace lo mismo antes de recorrer la zona).
        final Rectangle2D[] cajas = new Rectangle2D[geos.size()];
        for (int k = 0; k < cajas.length; k++) cajas[k] = geos.get(k).bounds();
        final int[] datos = ((DataBufferInt) dst.getRaster().getDataBuffer()).getData();
        final long[] tot = new long[4];                                   // muestras, aportes, maxProf, maxFranja
        final long t0 = System.nanoTime();
        for (int j0 = 0; j0 < h; j0 += franja) {
            final int j1 = Math.min(h, j0 + franja);
            final long[] enFranja = new long[1];
            IntStream.range(j0, j1).parallel().forEach(j -> {
                double[] acc = new double[w * 4];                          // premultiplicado: a, r, g, b por píxel
                long aportes = 0; int maxProf = 0;
                for (int q = 0; q < n; q++) {
                    final double ys = j + (q + 0.5) / n;
                    final Fila f = new Fila(w * n);
                    int orden = 0;
                    for (int k = 0; k < geos.size(); k++) {
                        Rectangle2D b = cajas[k];
                        if (ys < b.getMinY() - 2 || ys > b.getMaxY() + 2) continue;
                        int s0 = Math.max(0, (int) Math.floor((b.getMinX() - 2) * n)), s1 = Math.min(w * n, (int) Math.ceil((b.getMaxX() + 2) * n));
                        final Geometria g = geos.get(k); final Opciones op = ops.get(k);
                        for (int s = s0; s < s1; s++) {
                            final int ss = s;
                            g.capas((s + 0.5) / n, ys, op, t -> { double c = cob(t.cobertura()); if (c > 0) f.anade(ss, t, c); });
                        }
                    }
                    aportes += f.cuenta;
                    Integer[] idx = new Integer[16];
                    for (int s = 0; s < w * n; s++) {
                        if (f.cabeza[s] < 0) continue;
                        int m = 0;
                        for (int e = f.cabeza[s]; e >= 0; e = f.sig[e]) { if (m == idx.length) idx = Arrays.copyOf(idx, m * 2); idx[m++] = e; }
                        maxProf = Math.max(maxProf, m);
                        // la lista enlazada va en orden inverso al de emisión: se invierte y se ordena por altura (estable)
                        Integer[] orde = new Integer[m];
                        for (int a = 0; a < m; a++) orde[a] = idx[m - 1 - a];
                        Arrays.sort(orde, Comparator.comparingDouble(e -> f.alt[e]));
                        double pa = 0, pr = 0, pg = 0, pb = 0;
                        for (int e : orde) {
                            int c = f.argb[e];
                            double sa = ((c >>> 24) / 255.0) * f.cob[e];
                            pr = aEspacio((c >> 16) & 255) * sa + pr * (1 - sa);
                            pg = aEspacio((c >> 8) & 255) * sa + pg * (1 - sa);
                            pb = aEspacio(c & 255) * sa + pb * (1 - sa);
                            pa = sa + pa * (1 - sa);
                        }
                        int px = s / n;
                        acc[px * 4] += pa; acc[px * 4 + 1] += pr; acc[px * 4 + 2] += pg; acc[px * 4 + 3] += pb;
                    }
                }
                final double k2 = n * n;
                for (int i = 0; i < w; i++) {
                    double a = acc[i * 4] / k2;
                    if (a <= 0) continue;
                    double r = deEspacio(acc[i * 4 + 1] / k2 / a), g2 = deEspacio(acc[i * 4 + 2] / k2 / a), b2 = deEspacio(acc[i * 4 + 3] / k2 / a);
                    datos[j * w + i] = componer(datos[j * w + i], r, g2, b2, a);
                }
                synchronized (enFranja) { enFranja[0] += aportes; }
                synchronized (tot) { tot[1] += aportes; tot[2] = Math.max(tot[2], maxProf); }
            });
            tot[3] = Math.max(tot[3], enFranja[0]);
            tot[0] += (long) (j1 - j0) * w * n * n;
        }
        return new Estadisticas(tot[0], tot[1], (int) tot[2], tot[3], System.nanoTime() - t0);
    }

    /** src-over del píxel (color no premultiplicado y alfa a) sobre un argb de la imagen. */
    private static int componer(int dst, double r, double g, double b, double sa) {
        double da = (dst >>> 24) / 255.0, kd = da * (1 - sa), oa = sa + kd;
        int rr = (int) Math.round((r * sa + ((dst >> 16) & 255) * kd) / oa);
        int gg = (int) Math.round((g * sa + ((dst >> 8) & 255) * kd) / oa);
        int bb = (int) Math.round((b * sa + (dst & 255) * kd) / oa);
        return (int) Math.round(oa * 255) << 24 | rr << 16 | gg << 8 | bb;
    }
}
