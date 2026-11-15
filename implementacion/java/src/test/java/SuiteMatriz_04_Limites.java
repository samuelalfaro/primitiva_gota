import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Matriz de casos límite. Cada fila es un eje: un parámetro que se acerca a una frontera de las reglas (trayectoria
 * = r1 + r2, d = |r1 - r2|, radio nulo, radios iguales con d -> 0, longitud de los tiradores, r2 -> 4). Las columnas son los valores del
 * parámetro. Sirve para medir las decisiones, no para imponerlas: cada celda anota el área, si la gota es rígida y el
 * número de píxeles que cambian respecto a la columna anterior. Cuando el paso del parámetro es pequeño (<= {@link #PASO_JUZGADO})
 * y la silueta cambia más de {@link #SALTO} del área, se marca como salto (marco rojo en la segunda imagen y línea en la
 * consola). Los saltos no cuentan como fallo: son los hallazgos de la suite. Fallan solo la construcción y que la gota no deje huella.
 *
 * Imágenes: {@code suite_matriz_04_limites.png} (silueta con el salto marcado) y {@code suite_matriz_04_limites_color.png}.
 */
public class SuiteMatriz_04_Limites {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        if (!ok) System.out.printf("%-60s FALLA  %s%n", n, d);
    }

    static final double ESCALA = SuiteMatriz_02_Siluetas.ESCALA;
    static final int CW = SuiteMatriz_02_Siluetas.CW, CH = SuiteMatriz_02_Siluetas.CH;
    static final int MARGEN_X = 250, MARGEN_Y = 30;
    /** Un paso del parámetro de hasta esto se considera "continuo": la silueta no debería saltar. */
    static final double PASO_JUZGADO = 1.5e-3;
    /** Fracción del área de la silueta que puede cambiar en un paso juzgado antes de marcarlo (el borde antialiasing son ~0.05 %). */
    static final double SALTO = 0.003;

    /** Un parámetro del eje y cómo construir la gota con él. */
    record Eje(String nombre, String forma, double[] valores, String[] etiquetas, Construye construye) {}

    interface Construye { Gota gota(double v, double ox, double oy); }

    static Gota.Vec[] controles(String forma, double d, double k) {
        Gota.Vec[] v = SuiteMatriz_02_Siluetas.controles(forma, d);
        return v == null ? null : new Gota.Vec[] { new Gota.Vec(v[0].x() * k, v[0].y() * k), new Gota.Vec(v[1].x() * k, v[1].y() * k) };
    }

    /** Gota con separación d, radios (en unidades), forma de espina y factor k sobre los tiradores de la forma. */
    static Gota gota(String forma, double d, double r1, double r2, double k, double ox, double oy) {
        double S = ESCALA;
        Point2D c1 = new Point2D.Double(ox, oy), c2 = new Point2D.Double(ox + d * S, oy);
        Gota.Vec[] v = controles(forma, d, k);
        if (v == null) return Gota.lineal(c1, r1 * S, c2, r2 * S, Perfil.recto());
        return Gota.curva(c1, r1 * S, new Gota.Vec(v[0].x() * S, -v[0].y() * S),
                          c2, r2 * S, new Gota.Vec(v[1].x() * S, -v[1].y() * S), Perfil.recto());
    }

    static String etiqueta(double v) {
        if (v == 0) return "0";
        double a = Math.abs(v);
        return a < 1e-2 ? String.format("%.0e", v).replace("e-0", "e-") : String.format("%.4g", v);
    }

    static Eje eje(String nombre, String forma, double[] valores, Construye c) {
        String[] et = new String[valores.length];
        for (int i = 0; i < et.length; i++) et[i] = etiqueta(valores[i]);
        return new Eje(nombre, forma, valores, et, c);
    }

    static List<Eje> ejes() {
        List<Eje> l = new ArrayList<>();
        double[] delta = { 0.5, 0.1, 1e-2, 1e-3, 1e-7, 0 };
        for (String f : new String[] { "recta", "suave", "S" })
            l.add(eje("r1=0, r2=4-δ, d=4", f, delta, (v, ox, oy) -> gota(f, 4, 0, 4 - v, 1, ox, oy)));
        double[] sobre = { 0.1, 1e-2, 1e-3, 1e-7, 0, -1e-7, -1e-3, -1e-2 };
        l.add(eje("r1=1, r2=3, d=4+δ (frontera r1+r2)", "recta", sobre, (v, ox, oy) -> gota("recta", 4 + v, 1, 3, 1, ox, oy)));
        l.add(eje("r1=0, r2=4, d=4+δ (frontera r1+r2)", "recta", sobre, (v, ox, oy) -> gota("recta", 4 + v, 0, 4, 1, ox, oy)));
        double[] contenido = { 0.5, 0.1, 1e-2, 1e-4, 0, -1e-4, -1e-2, -0.5 };
        l.add(eje("r1=1, r2=3, d=2+δ (frontera |r1-r2|)", "recta", contenido, (v, ox, oy) -> gota("recta", 2 + v, 1, 3, 1, ox, oy)));
        double[] radio = { 1e-1, 1e-2, 1e-3, 1e-6, 1e-9, 0 };
        for (String f : new String[] { "recta", "suave", "S" })
            l.add(eje("r1 pequeño, r2=4, d=5", f, radio, (v, ox, oy) -> gota(f, 5, v, 4, 1, ox, oy)));
        double[] dcero = { 1, 0.1, 1e-2, 1e-3, 1e-6, 0 };
        l.add(eje("r1=r2=2, d → 0", "recta", dcero, (v, ox, oy) -> gota("recta", v, 2, 2, 1, ox, oy)));
        l.add(eje("r1=0.5, r2=3.5, d → 0", "recta", dcero, (v, ox, oy) -> gota("recta", v, 0.5, 3.5, 1, ox, oy)));
        double[] tir = { 0.02, 0.1, 0.3, 0.7, 1, 1.5 };
        for (String f : new String[] { "suave", "S" })
            l.add(eje("r1=1, r2=3, d=5, tiradores × k", f, tir, (v, ox, oy) -> gota(f, 5, 1, 3, v, ox, oy)));
        return l;
    }

    static BufferedImage lienzo() {
        BufferedImage c = new BufferedImage(CW, CH, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) c.getRaster().getDataBuffer()).getData(), 0xFFFFFFFF);
        return c;
    }

    static boolean[] mascara(Gota g) {
        boolean[] m = new boolean[CW * CH];
        for (int y = 0; y < CH; y++) for (int x = 0; x < CW; x++) m[y * CW + x] = g.mascara().contiene(x + .5, y + .5);
        return m;
    }

    static void texto(Graphics2D g, String s, int x, int y) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.drawString(s, x, y);
    }

    public static void main(String[] args) throws Exception {
        List<Eje> ejes = ejes();
        int cols = 0;
        for (Eje e : ejes) cols = Math.max(cols, e.valores().length);
        int w = MARGEN_X + cols * CW, h = MARGEN_Y + ejes.size() * CH;
        BufferedImage sil = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), col = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (BufferedImage im : new BufferedImage[] { sil, col }) {
            Graphics2D g = im.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.dispose();
        }
        List<String> saltos = new ArrayList<>(), degeneradas = new ArrayList<>();
        System.out.printf("%-42s %-6s %9s %7s %6s %8s%n", "eje", "forma", "param", "rígida", "área", "px Δ");
        int fila = 0;
        for (Eje e : ejes) {
            boolean[] previa = null;
            double areaPrevia = 0;
            for (int c = 0; c < e.valores().length; c++) {
                double v = e.valores()[c];
                int x0 = MARGEN_X + c * CW, y0 = MARGEN_Y + fila * CH;
                String nombre = String.format("%s [%s] %s", e.nombre(), e.forma(), e.etiquetas()[c]);
                BufferedImage celda = lienzo(), celdaCol = lienzo();
                boolean rigida = false;
                double area = 0, ratio = 0;
                long dif = -1;
                try {
                    Gota g = e.construye().gota(v, 90, CH * 0.62);
                    Plotter.raster(celda).plot(g, Relleno.solido(0xFF000000));
                    int opacos = 0;
                    for (int y = 0; y < CH; y++) for (int x = 0; x < CW; x++) if ((celda.getRGB(x, y) & 0xFF) < 128) opacos++;
                    comprueba(nombre + ": deja huella", opacos > 0, "sin píxeles");
                    Gota gc = e.construye().gota(v, 90, CH * 0.62);
                    Plotter.raster(celdaCol).plot(gc, SuiteMatriz_03_Colores.RELLENO, SuiteMatriz_03_Colores.OPCIONES);
                    rigida = g.silueta() != null;
                    ratio = rigida ? 0 : SuiteMatriz_02_Siluetas.ratioMax(g);
                    boolean[] m = mascara(g);
                    int n = 0;
                    for (boolean b : m) if (b) n++;
                    area = n / (ESCALA * ESCALA);
                    if (previa != null) {
                        dif = 0;
                        for (int i = 0; i < m.length; i++) if (m[i] != previa[i]) dif++;
                    }
                    previa = m;
                } catch (RuntimeException ex) {
                    comprueba(nombre + ": se construye y se pinta", false, ex.toString());
                    previa = null;
                }
                boolean casiDegenerada = !e.forma().equals("recta") && ratio >= 0.99;
                if (casiDegenerada) degeneradas.add(nombre + String.format(" (ratio %.2f)", ratio));
                boolean salto = false;
                if (dif >= 0 && Math.abs(v - e.valores()[c - 1]) <= PASO_JUZGADO && dif > SALTO * areaPrevia * ESCALA * ESCALA) {
                    salto = true;
                    saltos.add(String.format("%s: %d px (%.2f %% del área) respecto a %s", nombre, dif, 100 * dif / (areaPrevia * ESCALA * ESCALA), e.etiquetas()[c - 1]));
                }
                System.out.printf("%-42s %-6s %9s %7b %6.2f %8s%s%n", e.nombre(), e.forma(), e.etiquetas()[c], rigida, area, dif < 0 ? "-" : Long.toString(dif), salto ? "   <-- SALTO" : "");
                areaPrevia = area;
                for (BufferedImage im : new BufferedImage[] { sil, col }) {
                    Graphics2D gg = im.createGraphics();
                    gg.drawImage(im == sil ? celda : celdaCol, x0, y0, null);
                    gg.setColor(new Color(0xCCCCCC));
                    gg.drawRect(x0, y0, CW - 1, CH - 1);
                    if (salto) {
                        gg.setColor(new Color(0xD32F2F));
                        gg.setStroke(new BasicStroke(3));
                        gg.drawRect(x0 + 2, y0 + 2, CW - 5, CH - 5);
                    }
                    if (casiDegenerada) {
                        gg.setColor(new Color(0xF57C00));
                        gg.setStroke(new BasicStroke(3));
                        gg.drawRect(x0 + 6, y0 + 6, CW - 13, CH - 13);
                    }
                    if (rigida) {
                        gg.setColor(new Color(0x2E7D32));
                        gg.fillRect(x0 + CW - 14, y0 + 6, 8, 8);
                    }
                    gg.dispose();
                    gg = im.createGraphics();
                    texto(gg, e.etiquetas()[c], x0 + 8, y0 + 16);
                    texto(gg, String.format("área %.2f%s%s", area, dif < 0 ? "" : "   Δ " + dif + " px", rigida ? "" : String.format("   ratio %.2f", ratio)), x0 + 8, y0 + 30);
                    gg.dispose();
                }
            }
            for (BufferedImage im : new BufferedImage[] { sil, col }) {
                Graphics2D gg = im.createGraphics();
                texto(gg, e.nombre(), 4, MARGEN_Y + fila * CH + 16);
                texto(gg, e.forma(), 4, MARGEN_Y + fila * CH + 30);
                gg.dispose();
            }
            fila++;
        }
        for (BufferedImage im : new BufferedImage[] { sil, col }) {
            Graphics2D gg = im.createGraphics();
            texto(gg, "marco rojo: la silueta cambia más del " + (SALTO * 100) + " % del área con un paso del parámetro de " + PASO_JUZGADO
                    + " o menos.   Cuadro verde: forma rígida (trayectoria <= r1 + r2).   Marco naranja: espina curva con ratio |r'|/|c'| >= 0.99 (envolvente casi degenerada).", MARGEN_X, 18);
            gg.dispose();
        }
        ImageIO.write(sil, "png", new File("suite_matriz_04_limites.png"));
        ImageIO.write(col, "png", new File("suite_matriz_04_limites_color.png"));
        System.out.printf("%nSaltos detectados: %d%n", saltos.size());
        for (String s : saltos) System.out.println("  " + s);
        System.out.printf("Envolvente casi degenerada (espina curva, ratio >= 0.99): %d%n", degeneradas.size());
        for (String s : degeneradas) System.out.println("  " + s);
        System.out.printf("%n%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }
}
