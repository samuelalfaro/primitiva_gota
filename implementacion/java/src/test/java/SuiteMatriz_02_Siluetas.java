import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Matriz de siluetas con color sólido. Filas: separación horizontal d = 5, 4, 3 de dos centros de radio 2, con la espina
 * recta, en Bézier suave, en S y en ჲ (un bucle). Columnas: r1 crece de 0 a 4 y r2 decrece de 4 a 0 (r1 + r2 = 4): cada extremo se anula una vez.
 * Deja la imagen en {@code silueta_matriz.png}; las comprobaciones son mínimas (la gota se construye y deja huella).
 */
public class SuiteMatriz_02_Siluetas {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        if (!ok) System.out.printf("%-60s FALLA  %s%n", n, d);
    }

    static final double[] SEPARACIONES = { 5, 4, 3 };
    static final String[] FORMAS = { "recta", "suave", "S", "ჲ" };
    static final double[][] RADIOS = { { 0, 4 }, { 0.5, 3.5 }, { 1, 3 }, { 1.5, 2.5 }, { 2, 2 }, { 2.5, 1.5 }, { 3, 1 }, { 3.5, 0.5 }, { 4, 0 } };

    static final double ESCALA = 22;          // píxeles por unidad
    static final int CW = 300, CH = 260;      // tamaño de celda
    static final int MARGEN_X = 70, MARGEN_Y = 30;

    /** Vectores de control (relativos a cada centro) de cada forma, en unidades del dibujo; y hacia arriba. */
    static Gota.Vec[] controles(String forma, double d) {
        return switch (forma) {
            case "suave" -> new Gota.Vec[] { new Gota.Vec(d / 3, d / 2), new Gota.Vec(-d / 3, d / 2) };
            case "S"     -> new Gota.Vec[] { new Gota.Vec(d / 3, d / 2), new Gota.Vec(-d / 3, -d / 2) };
            case "ჲ"     -> new Gota.Vec[] { new Gota.Vec(1.5 * d, 1.2 * d), new Gota.Vec(-1.5 * d, 1.2 * d) };
            default      -> null;
        };
    }

    static Gota gota(String forma, double d, double r1, double r2, double ox, double oy) {
        Point2D c1 = new Point2D.Double(ox, oy), c2 = new Point2D.Double(ox + d * ESCALA, oy);
        Gota.Vec[] v = controles(forma, d);
        if (v == null) return Gota.lineal(c1, r1 * ESCALA, c2, r2 * ESCALA, Perfil.recto());
        return Gota.curva(c1, r1 * ESCALA, new Gota.Vec(v[0].x() * ESCALA, -v[0].y() * ESCALA),
                          c2, r2 * ESCALA, new Gota.Vec(v[1].x() * ESCALA, -v[1].y() * ESCALA), Perfil.recto());
    }

    /** Área de la silueta curva, contando píxeles en una ventana de 500 x 500. */
    static int area(SiluetaCurva s) {
        int n = 0;
        for (int y = 0; y < 500; y++) for (int x = 0; x < 500; x++) if (s.contiene(x + .5, y + .5)) n++;
        return n;
    }

    /** Un radio nulo se comporta como un epsilon: mismo área que con un radio pequeño, en los dos extremos y para cada forma. */
    static void radioNulo() {
        double S = ESCALA, d = 5;   // d > |r1 - r2|: fuera del caso de círculos contenidos
        for (String forma : FORMAS) {
            Gota.Vec[] v = controles(forma, d);
            for (int extremo = 0; extremo < 2; extremo++) {
                double r1 = extremo == 0 ? 0 : 4, r2 = extremo == 0 ? 4 : 0, e1 = extremo == 0 ? 1e-3 : 4, e2 = extremo == 0 ? 4 : 1e-3;
                String nombre = String.format("radio nulo = epsilon (%s, r1=%.0f, r2=%.0f)", forma, r1, r2);
                try {
                    int a0 = area(silueta(v, d, r1 * S, r2 * S)), a1 = area(silueta(v, d, e1 * S, e2 * S));
                    comprueba(nombre, Math.abs(a0 - a1) <= 0.002 * a1, "área " + a0 + " frente a " + a1);
                } catch (RuntimeException ex) {
                    comprueba(nombre, false, ex.toString());
                }
            }
        }
        boolean lanza = false;
        try { silueta(null, d, 0, 0); } catch (IllegalArgumentException ex) { lanza = true; }
        comprueba("dos radios nulos se rechazan", lanza, "");
    }

    /**
     * Zona anidada y concéntrica: los ángulos salen de la misma energía en todo el rango de d. Con conector ELIPSE y sin
     * excepciones, y con dos círculos idénticos y d = 0, psi = 45° (la X).
     */
    static void anidados() {
        double[][] radios = { { 2, 2 }, { 1, 3 }, { 0.5, 3.5 }, { 3.5, 0.5 } };
        double[] ds = { 5, 4, 3.5, 3.2, 3, 2.9, 2.5, 2, 1, 0.5, 0.1, 0 };
        for (double[] r : radios) {
            int noElipse = 0, falla = 0;
            for (double d : ds) {
                try {
                    Silueta s = Silueta.de(new Point2D.Double(0, 0), r[0], new Point2D.Double(d, 0), r[1]);
                    if (d < r[0] + r[1] && s.conector() != Silueta.Conector.ELIPSE) noElipse++;
                } catch (RuntimeException e) {
                    falla++;
                }
            }
            String n = String.format("anidados (r1=%.1f, r2=%.1f): conector ELIPSE sin excepciones", r[0], r[1]);
            comprueba(n, noElipse == 0 && falla == 0, noElipse + " sin elipse, " + falla + " excepciones");
        }
        Silueta x = Silueta.de(new Point2D.Double(0, 0), 2, new Point2D.Double(0, 0), 2);
        comprueba("círculos idénticos con d = 0: psi = 45°",
                Math.abs(Math.toDegrees(x.angulos().psi1()) - 45) < 0.5 && Math.abs(Math.toDegrees(x.angulos().psi2()) - 45) < 0.5,
                Math.toDegrees(x.angulos().psi1()) + ", " + Math.toDegrees(x.angulos().psi2()));
    }

    /**
     * Continuidad en d = 0: los ángulos con centros coincidentes difieren poco de los de d = 0,001, también con un radio nulo
     * (el arranque de la X en (pi/4, pi/4) caía antes en un mínimo falso con radios muy distintos).
     */
    static void continuidadEnCero() {
        double[][] radios = { { 2, 2 }, { 1, 3 }, { 3, 1 }, { 0.5, 3.5 }, { 3.5, 0.5 }, { 0, 4 }, { 4, 0 } };
        for (double[] r : radios) {
            Silueta s0 = Silueta.de(new Point2D.Double(0, 0), r[0], new Point2D.Double(0, 0), r[1]);
            Silueta s1 = Silueta.de(new Point2D.Double(0, 0), r[0], new Point2D.Double(0.001, 0), r[1]);
            double d1 = Math.abs(Math.toDegrees(s0.angulos().psi1() - s1.angulos().psi1()));
            double d2 = Math.abs(Math.toDegrees(s0.angulos().psi2() - s1.angulos().psi2()));
            comprueba(String.format("continuidad en d = 0 (r1=%.1f, r2=%.1f)", r[0], r[1]), d1 < 3 && d2 < 3, String.format("%.1f° y %.1f° de diferencia", d1, d2));
        }
    }

    static SiluetaCurva silueta(Gota.Vec[] v, double d, double r1, double r2) {
        double S = ESCALA, dd = d * S, ox = 120, oy = 250;
        Point2D h1 = v == null ? new Point2D.Double(ox + dd / 3, oy) : new Point2D.Double(ox + v[0].x() * S, oy - v[0].y() * S);
        Point2D h2 = v == null ? new Point2D.Double(ox + 2 * dd / 3, oy) : new Point2D.Double(ox + dd + v[1].x() * S, oy - v[1].y() * S);
        return SiluetaCurva.de(new Point2D.Double(ox, oy), h1, h2, new Point2D.Double(ox + dd, oy), r1, r2);
    }

    public static void main(String[] args) throws Exception {
        int filas = SEPARACIONES.length * FORMAS.length, cols = RADIOS.length;
        int w = MARGEN_X + cols * CW, h = MARGEN_Y + filas * CH;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, w, h);
        g2.dispose();

        int negro = 0xFF000000;
        int fila = 0;
        for (String forma : FORMAS) {
            for (double d : SEPARACIONES) {
                for (int c = 0; c < cols; c++) {
                    double r1 = RADIOS[c][0], r2 = RADIOS[c][1];
                    int x0 = MARGEN_X + c * CW, y0 = MARGEN_Y + fila * CH;
                    BufferedImage celda = new BufferedImage(CW, CH, BufferedImage.TYPE_INT_ARGB);
                    String nombre = String.format("%s d=%.0f r1=%.1f r2=%.1f", forma, d, r1, r2);
                    try {
                        Gota g = gota(forma, d, r1, r2, 90, CH * 0.62);
                        Plotter.raster(celda).plot(g, Relleno.solido(negro));
                        int opacos = 0;
                        for (int y = 0; y < CH; y++) for (int x = 0; x < CW; x++) if ((celda.getRGB(x, y) >>> 24) != 0) opacos++;
                        comprueba(nombre + ": deja huella", opacos > 0, "sin píxeles");
                    } catch (RuntimeException e) {
                        comprueba(nombre + ": se construye y se pinta", false, e.toString());
                    }
                    Graphics2D gg = img.createGraphics();
                    gg.drawImage(celda, x0, y0, null);
                    gg.setColor(new Color(0xCCCCCC));
                    gg.drawRect(x0, y0, CW - 1, CH - 1);
                    gg.dispose();
                }
                Graphics2D gg = img.createGraphics();
                gg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                gg.setColor(Color.DARK_GRAY);
                gg.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                gg.drawString(forma, 6, MARGEN_Y + fila * CH + 16);
                gg.drawString("d = " + (int) d, 6, MARGEN_Y + fila * CH + 32);
                gg.dispose();
                fila++;
            }
        }
        Graphics2D gg = img.createGraphics();
        gg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        gg.setColor(Color.DARK_GRAY);
        gg.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int c = 0; c < cols; c++)
            gg.drawString(String.format("r1 = %.1f   r2 = %.1f", RADIOS[c][0], RADIOS[c][1]), MARGEN_X + c * CW + 8, 18);
        gg.dispose();
        ImageIO.write(img, "png", new File("suite_matriz_02_siluetas.png"));

        radioNulo();
        anidados();
        continuidadEnCero();
        System.out.printf("%n%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }
}
