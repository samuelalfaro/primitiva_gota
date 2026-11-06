import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Escribe un alambre como SVG. Cada trazo es un {@code <path>} con la clase de su {@link Alambre.Rol}; el estilo va en
 * un bloque {@code <style>} (generado desde el propio enum), de modo que se puede retocar en el SVG sin regenerarlo.
 */
public final class Svg {

    private Svg() {}

    private static final double MARGEN = 30;

    /** Escribe el SVG con la vista dada (en coordenadas de imagen) más un margen. */
    public static void escribe(List<Alambre.Trazo> trazos, Rectangle2D vista, Writer out) throws IOException {
        double x = vista.getX() - MARGEN, y = vista.getY() - MARGEN;
        double w = vista.getWidth() + 2 * MARGEN, h = vista.getHeight() + 2 * MARGEN;
        out.write(String.format(Locale.ROOT,
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"%s %s %s %s\" width=\"%s\" height=\"%s\">%n",
            num(x), num(y), num(w), num(h), num(w), num(h)));
        out.write("<style>\n");
        out.write("  path { fill: none; stroke-linecap: round; stroke-linejoin: round; }\n");
        for (Alambre.Rol r : Alambre.Rol.values()) {
            out.write(String.format(Locale.ROOT, "  .%s { stroke: %s; stroke-width: %s;%s%s }%n", r.clase, rgb(r),
                num(r.ancho),
                r.guiones == null ? "" : " stroke-dasharray: " + num(r.guiones[0]) + " " + num(r.guiones[1]) + ";",
                r.relleno ? " fill: " + rgb(r) + ";" : ""));
        }
        out.write("</style>\n");
        for (Alambre.Trazo t : trazos) {
            out.write("<path class=\"" + t.rol().clase + "\" d=\"" + camino(t.forma()) + "\"/>\n");
        }
        out.write("</svg>\n");
    }

    public static void escribe(List<Alambre.Trazo> trazos, Rectangle2D vista, Path destino) throws IOException {
        try (Writer w = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            escribe(trazos, vista, w);
        }
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

    private static String rgb(Alambre.Rol r) {
        return String.format("#%02x%02x%02x", r.color.getRed(), r.color.getGreen(), r.color.getBlue());
    }
}
