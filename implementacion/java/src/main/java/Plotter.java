import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.util.stream.IntStream;

/**
 * Dibuja una gota en algún sitio: {@code plotter.plot(gota, relleno, opciones)}. Cada plotter es una forma distinta de hacerlo:
 * {@link #raster(BufferedImage)} la rasteriza en una imagen con antialiasing y composición alfa; el modo alambre y la exportación
 * a SVG serían otros plotters. La gota solo guarda sus parámetros: la máscara está en {@link Silueta}, las coordenadas en
 * {@link Coordenadas} y el color en {@link Relleno}; el plotter los ensambla.
 */
public interface Plotter {

    /** Dibuja la gota con el relleno y las opciones dadas. */
    void plot(Gota g, Relleno relleno, Opciones op);

    /** Dibuja la gota con las opciones por defecto. */
    default void plot(Gota g, Relleno relleno) { plot(g, relleno, Opciones.POR_DEFECTO); }

    /** Un plotter que rasteriza en {@code dst} (TYPE_INT_ARGB). */
    static Plotter raster(BufferedImage dst) { return new Raster(dst); }

    /** Rasteriza en una imagen TYPE_INT_ARGB con antialiasing y composición alfa (src-over, no premultiplicado). */
    final class Raster implements Plotter {
        private final int[] datos;
        private final int stride, base, ancho, alto;

        /** Zona de píxeles a recorrer: [x0, x1) x [y0, y1). */
        record Zona(int x0, int x1, int y0, int y1) {}

        /** Lo que se hace en cada píxel (i, j) de una zona. */
        interface Celda { void pinta(int i, int j); }

        Raster(BufferedImage dst) {
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
                    capa -> set(i, j, componer(get(i, j), rel.argb(capa.punto()), capa.cobertura()))));
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
