package common;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Compara imágenes píxel a píxel, para medir el efecto de un cambio en la librería sobre los ejemplos o las láminas: se
 * generan las imágenes con las dos versiones (cada una en su carpeta) y se comparan. Dos archivos, o dos carpetas (se emparejan
 * por ruta relativa, en cualquier nivel).
 *
 * <p>Uso: {@code Compara <antes> <despues> [carpeta_de_salida] [factor]}. Imprime por imagen los píxeles distintos (y su
 * proporción respecto del dibujo, es decir, de lo que no es el color de fondo de la esquina), el máximo por canal y un
 * resumen. Si se da carpeta de salida, deja por cada imagen distinta un tríptico <em>antes | después | diferencia</em>
 * ({@code <nombre>_comparacion.png}); la diferencia es el valor absoluto por canal multiplicado por el factor (8 por
 * defecto) para que se vean los matices. Termina con código 1 si alguna imagen difiere o falta en una de las carpetas y con 0 si todo es
 * idéntico, de modo que sirve también como comprobación de que un cambio no altera las láminas.
 *
 * <p>La diferencia de las <em>texturas</em> y los degradados cuenta una historia: es un mapa de dónde discrepan los dos
 * criterios (por ejemplo, la t de la Bézier frente a la longitud de arco en el issue #78).
 */
public class Compara {

    /** Resultado de comparar dos imágenes: {@code distintos} píxeles que difieren, de {@code dibujo} que no son el fondo. */
    public record Resultado(int ancho, int alto, int distintos, int dibujo, int maxCanal, boolean mismoTamano) {
        public boolean iguales() { return mismoTamano && distintos == 0; }
        /** Porcentaje de píxeles distintos respecto del dibujo (0 si no hay dibujo). */
        public double porcentaje() { return dibujo == 0 ? 0 : 100.0 * distintos / dibujo; }
    }

    static int[] argb(BufferedImage im) {
        return im.getRGB(0, 0, im.getWidth(), im.getHeight(), null, 0, im.getWidth());
    }

    /** Compara dos imágenes; el «dibujo» son los píxeles que no son el color de la esquina superior izquierda en alguna de las dos. */
    public static Resultado compara(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight())
            return new Resultado(a.getWidth(), a.getHeight(), 0, 0, 0, false);
        int[] pa = argb(a), pb = argb(b);
        int fa = pa.length == 0 ? 0 : pa[0], fb = pb.length == 0 ? 0 : pb[0];
        int distintos = 0, dibujo = 0, max = 0;
        for (int i = 0; i < pa.length; i++) {
            if (pa[i] != fa || pb[i] != fb) dibujo++;
            if (pa[i] == pb[i]) continue;
            distintos++;
            for (int s = 0; s < 32; s += 8) max = Math.max(max, Math.abs(((pa[i] >>> s) & 255) - ((pb[i] >>> s) & 255)));
        }
        return new Resultado(a.getWidth(), a.getHeight(), distintos, dibujo, max, true);
    }

    /** Diferencia absoluta por canal (alfa incluido en el cálculo, opaca en el resultado) multiplicada por {@code factor}. */
    public static BufferedImage diferencia(BufferedImage a, BufferedImage b, int factor) {
        int w = Math.min(a.getWidth(), b.getWidth()), h = Math.min(a.getHeight(), b.getHeight());
        BufferedImage d = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int p = a.getRGB(x, y), q = b.getRGB(x, y), v = 0;
                for (int s = 0; s < 24; s += 8)
                    v |= Math.min(255, factor * Math.abs(((p >>> s) & 255) - ((q >>> s) & 255))) << s;
                d.setRGB(x, y, v);
            }
        return d;
    }

    /** Tríptico antes | después | diferencia (×{@code factor}), con cada panel reducido a lo sumo a {@code anchoPanel} de ancho. */
    public static BufferedImage triptico(BufferedImage a, BufferedImage b, int factor, int anchoPanel) {
        double e = Math.min(1.0, (double) anchoPanel / Math.max(1, a.getWidth()));
        int w = Math.max(1, (int) (a.getWidth() * e)), h = Math.max(1, (int) (a.getHeight() * e)), sep = 8;
        BufferedImage t = new BufferedImage(3 * w + 2 * sep, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = t.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, t.getWidth(), t.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        BufferedImage[] ps = { a, b, diferencia(a, b, factor) };
        for (int i = 0; i < 3; i++) g.drawImage(ps[i], i * (w + sep), 0, w, h, null);
        g.dispose();
        return t;
    }

    /** Imágenes PNG bajo {@code raiz} (o la propia imagen), por ruta relativa y ordenadas. */
    static Map<String, Path> imagenes(Path raiz) throws IOException {
        Map<String, Path> m = new TreeMap<>();
        if (Files.isRegularFile(raiz)) { m.put(raiz.getFileName().toString(), raiz); return m; }
        try (Stream<Path> s = Files.walk(raiz)) {
            s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".png"))
             .forEach(p -> m.put(raiz.relativize(p).toString().replace(File.separatorChar, '/'), p));
        }
        return m;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Uso: Compara <antes> <despues> [carpeta_de_salida] [factor]");
            System.exit(2);
        }
        Path antes = Path.of(args[0]), despues = Path.of(args[1]);
        Path salida = args.length > 2 ? Path.of(args[2]) : null;
        int factor = args.length > 3 ? Integer.parseInt(args[3]) : 8;
        Map<String, Path> ma = imagenes(antes), md = imagenes(despues);
        if (Files.isRegularFile(antes) && Files.isRegularFile(despues)) {       // dos archivos: se emparejan aunque se llamen distinto
            ma = Map.of(antes.getFileName().toString(), antes);
            md = Map.of(antes.getFileName().toString(), despues);
        }
        int iguales = 0, distintas = 0, faltan = 0;
        for (var e : ma.entrySet()) {
            Path pd = md.get(e.getKey());
            if (pd == null) { faltan++; System.out.printf("%-44s solo en antes%n", e.getKey()); continue; }
            BufferedImage a = ImageIO.read(e.getValue().toFile()), d = ImageIO.read(pd.toFile());
            Resultado r = compara(a, d);
            if (r.iguales()) { iguales++; continue; }
            distintas++;
            if (!r.mismoTamano())
                System.out.printf("%-44s distinto tamaño: %dx%d frente a %dx%d%n", e.getKey(), a.getWidth(), a.getHeight(), d.getWidth(), d.getHeight());
            else
                System.out.printf("%-44s %8d px distintos (%5.1f%% del dibujo), máx. por canal %d%n", e.getKey(), r.distintos(), r.porcentaje(), r.maxCanal());
            if (salida != null && r.mismoTamano()) {
                Path out = salida.resolve(e.getKey().replaceAll("(?i)\\.png$", "") + "_comparacion.png");
                Files.createDirectories(out.getParent());
                ImageIO.write(triptico(a, d, factor, 560), "png", out.toFile());
            }
        }
        for (String k : md.keySet()) if (!ma.containsKey(k)) { faltan++; System.out.printf("%-44s solo en después%n", k); }
        System.out.printf("%d imágenes comparadas: %d idénticas, %d distintas, %d sin pareja%n", iguales + distintas, iguales, distintas, faltan);
        if (distintas + faltan > 0) System.exit(1);
    }
}
