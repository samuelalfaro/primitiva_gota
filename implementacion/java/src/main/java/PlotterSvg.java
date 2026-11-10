import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Dibuja gotas en modo alambre como un documento SVG. Cada trazo es un {@code <path>} con la clase de su
 * {@link Trazos.Rol}; el estilo va en un bloque {@code <style>} (generado desde el propio enum), de modo que se puede
 * retocar en el SVG sin regenerarlo.
 *
 * <p>Un SVG necesita su vista antes que su contenido, así que el plotter acumula los trazos y escribe el documento en
 * {@link #close()}; la vista es la unión de los límites de las gotas dibujadas, más un margen:
 * <pre>{@code
 * try (PlotterSvg svg = PlotterSvg.a(Path.of("gota.svg"))) {
 *     svg.plot(gota);
 * }
 * }</pre>
 *
 * Comparte con {@link Plotter} solo el prefijo del nombre y el verbo {@code plot}: no la implementa, a propósito, hasta que
 * se vean las necesidades de todos los plotters. Los trazos los calcula {@link Trazos}, igual que en {@link PlotterAlambre}.
 */
public final class PlotterSvg implements AutoCloseable {

    private static final double MARGEN = 30;

    private final Writer out;
    private final Trazos.Ajustes ajustes;
    private final List<Trazos.Trazo> trazos = new ArrayList<>();
    private Rectangle2D vista;
    private boolean cerrado;

    /** Escribe en {@code out}, que se cierra con {@link #close()}. */
    public PlotterSvg(Writer out) {
        this(out, Trazos.Ajustes.POR_DEFECTO);
    }

    public PlotterSvg(Writer out, Trazos.Ajustes ajustes) {
        this.out = Objects.requireNonNull(out, "out");
        this.ajustes = Objects.requireNonNull(ajustes, "ajustes");
    }

    /** Escribe en el fichero {@code destino} (UTF-8), que se crea al cerrar el plotter. */
    public static PlotterSvg a(Path destino) throws IOException {
        return a(destino, Trazos.Ajustes.POR_DEFECTO);
    }

    public static PlotterSvg a(Path destino, Trazos.Ajustes ajustes) throws IOException {
        return new PlotterSvg(Files.newBufferedWriter(destino, StandardCharsets.UTF_8), ajustes);
    }

    /** Añade la gota con los ajustes de este plotter. */
    public void plot(Gota gota) {
        plot(gota, ajustes);
    }

    /** Añade la gota con los ajustes indicados (no cambian los del plotter). */
    public void plot(Gota gota, Trazos.Ajustes aj) {
        Objects.requireNonNull(gota, "gota");
        Objects.requireNonNull(aj, "ajustes");
        if (cerrado) throw new IllegalStateException("el plotter está cerrado");
        trazos.addAll(Trazos.trazos(gota, aj));
        Rectangle2D b = gota.bounds();
        if (vista == null) vista = b; else vista.add(b);
    }

    /** Escribe el documento y cierra el destino. Cerrar de nuevo no hace nada; sin gotas dibujadas no hay vista y falla. */
    @Override
    public void close() {
        if (cerrado) return;
        cerrado = true;
        try (Writer w = out) {
            if (vista == null) throw new IllegalStateException("no se ha dibujado ninguna gota");
            escribe(trazos, vista, w);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Escribe el SVG con la vista dada (en coordenadas de imagen) más un margen. */
    static void escribe(List<Trazos.Trazo> trazos, Rectangle2D vista, Writer out) throws IOException {
        double x = vista.getX() - MARGEN, y = vista.getY() - MARGEN;
        double w = vista.getWidth() + 2 * MARGEN, h = vista.getHeight() + 2 * MARGEN;
        out.write(String.format(Locale.ROOT,
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"%s %s %s %s\" width=\"%s\" height=\"%s\">%n",
            num(x), num(y), num(w), num(h), num(w), num(h)));
        out.write("<style>\n");
        out.write("  path { fill: none; stroke-linecap: round; stroke-linejoin: round; }\n");
        for (Trazos.Rol r : Trazos.Rol.values()) {
            out.write(String.format(Locale.ROOT, "  .%s { stroke: %s; stroke-width: %s;%s%s }%n", r.clase, rgb(r),
                num(r.ancho),
                r.guiones == null ? "" : " stroke-dasharray: " + num(r.guiones[0]) + " " + num(r.guiones[1]) + ";",
                r.relleno ? " fill: " + rgb(r) + ";" : ""));
        }
        out.write("</style>\n");
        for (Trazos.Trazo t : trazos) {
            out.write("<path class=\"" + t.rol().clase + "\" d=\"" + camino(t.forma()) + "\"/>\n");
        }
        out.write("</svg>\n");
    }

    /** El atributo d de un Shape. */
    static String camino(Shape s) {
        StringBuilder sb = new StringBuilder();
        double[] c = new double[6];
        for (PathIterator it = s.getPathIterator(null); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO -> sb.append('M').append(num(c[0])).append(' ').append(num(c[1]));
                case PathIterator.SEG_LINETO -> sb.append('L').append(num(c[0])).append(' ').append(num(c[1]));
                case PathIterator.SEG_QUADTO -> sb.append('Q').append(num(c[0])).append(' ').append(num(c[1])).append(' ')
                    .append(num(c[2])).append(' ').append(num(c[3]));
                case PathIterator.SEG_CUBICTO -> sb.append('C').append(num(c[0])).append(' ').append(num(c[1])).append(' ')
                    .append(num(c[2])).append(' ').append(num(c[3])).append(' ').append(num(c[4])).append(' ').append(num(c[5]));
                default -> sb.append('Z');
            }
        }
        return sb.toString();
    }

    private static String num(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.indexOf('.') >= 0) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.equals("-0") ? "0" : s;
    }

    private static String rgb(Trazos.Rol r) {
        return String.format("#%02x%02x%02x", r.color.getRed(), r.color.getGreen(), r.color.getBlue());
    }
}
