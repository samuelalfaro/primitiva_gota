package pintado;

import coordenadas.Solape;
import color.Relleno;
import coordenadas.Opciones;
import coordenadas.Coordenadas;
import forma.Gota;
import geometria.Geometria;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.util.stream.IntStream;

/**
 * Dibuja una gota en algún sitio: {@code plotter.plot(gota, relleno, opciones)}. Cada plotter es una forma distinta de hacerlo:
 * {@link #raster(BufferedImage)} la rasteriza en una imagen con antialiasing y composición alfa; el modo alambre y la exportación
 * a SVG serían otros plotters. La gota solo guarda sus parámetros: la máscara está en {@code Silueta}, las coordenadas en
 * {@link Coordenadas} y el color en {@link Relleno}; el plotter los ensambla.
 *
 * <p>{@link PlotterAlambre} y {@link PlotterSvg} siguen la convención (prefijo {@code Plotter} y verbo {@code plot}) sin
 * implementar esta interfaz: no usan relleno ni opciones de costura. Se generalizará cuando se vean las necesidades de todos.
 */
public interface Plotter {

    /** ESTUDIO #86 (clase sombra, no es de la biblioteca): cuenta cuántas capas con cobertura > 0 recibe cada píxel de cada imagen. */
    final class Profundidad {
        /** -Dgota.binaria=true: cobertura 0/1 por muestra (umbral 0,5), para simular un sobremuestreo de muestras puntuales. */
        static final boolean BINARIA = Boolean.getBoolean("gota.binaria");
        static double cob(double c) { return BINARIA ? (c >= 0.5 ? 1 : 0) : c; }
        static final java.util.Map<int[], int[]> POR_IMAGEN = new java.util.concurrent.ConcurrentHashMap<>();
        static final java.util.Map<int[], Integer> ANCHO = new java.util.concurrent.ConcurrentHashMap<>();
        static {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                long[] h = new long[7]; long parciales = 0;
                for (int[] p : POR_IMAGEN.values()) for (int v : p) { if (v == 0) continue; h[Math.min(v, 6)]++; }
                long tot = 0; for (long v : h) tot += v;
                if (tot == 0) return;
                StringBuilder sb = new StringBuilder("PROFUNDIDAD pixeles tocados=" + tot);
                for (int k = 1; k <= 6; k++) sb.append(String.format("  %s%d=%.2f%%", k == 6 ? ">=" : "", k, 100.0 * h[k] / tot));
                System.out.println(sb);
            }));
        }
        static void suma(int[] datos, int pos, int n) {
            if (n == 0) return;
            int[] p = POR_IMAGEN.computeIfAbsent(datos, d -> new int[d.length]);
            p[pos] += n;
        }
    }


    /** Dibuja la gota con el relleno y las opciones dadas. */
    void plot(Gota g, Relleno relleno, Opciones op);

    /**
     * Dibuja una geometría: por cada píxel de su {@link Geometria#bounds()} (con un margen de 2 píxeles para el
     * antialiasing) compone, de abajo a arriba y en orden de altura, las capas que ella entrega.
     */
    void plot(Geometria g, Opciones op);

    /** Igual, con las opciones por defecto. */
    default void plot(Geometria g) { plot(g, Opciones.POR_DEFECTO); }

    /** Dibuja la gota con las opciones por defecto. */
    default void plot(Gota g, Relleno relleno) { plot(g, relleno, Opciones.POR_DEFECTO); }

    /** Un plotter que rasteriza en {@code dst} (TYPE_INT_ARGB). */
    static Plotter raster(BufferedImage dst) { return new Raster(dst); }

    /** Rasteriza en una imagen TYPE_INT_ARGB con antialiasing y composición alfa (src-over, no premultiplicado). */
    final public class Raster implements Plotter {
        private final int[] datos;
        private final int stride, base, ancho, alto;

        /** Zona de píxeles a recorrer: [x0, x1) x [y0, y1). */
        record Zona(int x0, int x1, int y0, int y1) {}

        /** Lo que se hace en cada píxel (i, j) de una zona. */
        interface Celda { void pinta(int i, int j); }

        public Raster(BufferedImage dst) {
            if (dst.getType() != BufferedImage.TYPE_INT_ARGB)
                throw new IllegalArgumentException("Se requiere BufferedImage.TYPE_INT_ARGB");
            // El píxel (i, j) no está en j * ancho + i si dst es una subimagen: el raster comparte el búfer del
            // padre y conserva su desplazamiento y su stride. Se calcula el índice real a partir del raster.
            final WritableRaster raster = dst.getRaster();
            if (!(raster.getSampleModel() instanceof SinglePixelPackedSampleModel sm))
                throw new IllegalArgumentException("Raster no soportado: se requiere INT_ARGB empaquetado");
            datos = ((DataBufferInt) raster.getDataBuffer()).getData();
            stride = sm.getScanlineStride();
            base = raster.getDataBuffer().getOffset() - raster.getSampleModelTranslateY() * stride - raster.getSampleModelTranslateX();
            ancho = dst.getWidth();
            alto = dst.getHeight();
        }

        /** La parte de la imagen que cubre el rectángulo (con un margen), o null si queda fuera. */
        Zona zona(double minX, double maxX, double minY, double maxY, double margen) {
            int x0 = Math.max(0, (int) Math.floor(minX - margen)), x1 = Math.min(ancho, (int) Math.ceil(maxX + margen));
            int y0 = Math.max(0, (int) Math.floor(minY - margen)), y1 = Math.min(alto, (int) Math.ceil(maxY + margen));
            return x0 >= x1 || y0 >= y1 ? null : new Zona(x0, x1, y0, y1);
        }

        /** Recorre la zona repartiendo las filas en paralelo; cada celda solo debe escribir su píxel. */
        void recorre(Zona z, Celda celda) {
            IntStream.range(z.y0(), z.y1()).parallel().forEach(j -> {
                for (int i = z.x0(); i < z.x1(); i++) celda.pinta(i, j);
            });
        }

        int get(int i, int j) { return datos[base + j * stride + i]; }

        void set(int i, int j, int argb) { datos[base + j * stride + i] = argb; }

        @Override public void plot(Gota g, Relleno relleno, Opciones op) {
            final Rectangle2DView b = new Rectangle2DView(g.bounds());
            final Zona z = zona(b.minX, b.maxX, b.minY, b.maxY, 0);
            if (z == null) return;
            final Relleno rel = relleno.para(g, op);
            final Solape solape = op.solape();
            final Coordenadas coord = Coordenadas.de(g, op);
            recorre(z, (i, j) -> solape.capas(g, coord, i + 0.5, j + 0.5,
                    capa -> { if (capa.cobertura() > 0) Profundidad.suma(datos, base + j * stride + i, 1);
                              set(i, j, componer(get(i, j), rel.argb(capa.punto()), Profundidad.cob(capa.cobertura()))); }));
        }

        @Override public void plot(Geometria g, Opciones op) {
            java.util.Objects.requireNonNull(g, "geometria");
            java.util.Objects.requireNonNull(op, "opciones");
            final java.awt.geom.Rectangle2D b = g.bounds();
            final Zona z = zona(b.getMinX(), b.getMaxX(), b.getMinY(), b.getMaxY(), 2);
            if (z == null) return;
            recorre(z, (i, j) -> g.capas(i + 0.5, j + 0.5, op,
                    t -> { if (t.cobertura() > 0) Profundidad.suma(datos, base + j * stride + i, 1);
                           set(i, j, componer(get(i, j), t.argb(), Profundidad.cob(t.cobertura()))); }));
        }

        /** Composición alfa src-over, no premultiplicada: src con su cobertura sobre dst. */
        static int componer(int dst, int src, double cob) {
            double sa = ((src >>> 24) & 0xFF) / 255.0 * cob;
            if (sa <= 0) return dst;
            double da = ((dst >>> 24) & 0xFF) / 255.0;
            double kd = da * (1 - sa);
            double oa = sa + kd;
            int r = (int) Math.round((((src >> 16) & 0xFF) * sa + ((dst >> 16) & 0xFF) * kd) / oa);
            int gg = (int) Math.round((((src >> 8) & 0xFF) * sa + ((dst >> 8) & 0xFF) * kd) / oa);
            int bb = (int) Math.round(((src & 0xFF) * sa + (dst & 0xFF) * kd) / oa);
            int a = (int) Math.round(oa * 255);
            return a << 24 | r << 16 | gg << 8 | bb;
        }

        private record Rectangle2DView(double minX, double maxX, double minY, double maxY) {
            Rectangle2DView(java.awt.geom.Rectangle2D r) { this(r.getMinX(), r.getMaxX(), r.getMinY(), r.getMaxY()); }
        }
    }
}
