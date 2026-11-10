import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Matriz de gotas en modo alambre. Misma batería que {@link SiluetaMatriz} y {@link ColoresMatriz}:
 *
 * Filas: separación horizontal d = 5, 4, 3 de dos centros, con la espina recta,
 * en Bézier suave, en S y en ჲ (un bucle).
 * Columnas: r1 crece de 0 a 4 y r2 decrece de 4 a 0 (r1 + r2 = 4).
 *
 * Cada celda se dibuja con {@link PlotterAlambre}: contorno, pocas costillas grises,
 * radios de las dos tapas, corte central (tm) y envolvente oculta. Sin etiquetas.
 *
 * Deja la imagen en {@code alambre_matriz.png}. Las comprobaciones son mínimas
 * (la gota se construye, se traza y deja huella).
 */
public class SuiteMatriz_01_Alambre {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        if (!ok) System.out.printf("%-60s FALLA  %s%n", n, d);
    }

    static final double[] SEPARACIONES = { 5, 4, 3 };
    static final String[] FORMAS = { "recta", "suave", "S", "ჲ" };
    static final double[][] RADIOS = {
        { 0, 4 }, { 0.5, 3.5 }, { 1, 3 }, { 1.5, 2.5 }, { 2, 2 },
        { 2.5, 1.5 }, { 3, 1 }, { 3.5, 0.5 }, { 4, 0 }
    };

    static final double ESCALA = 22;          // píxeles por unidad
    static final int CW = 300, CH = 260;      // tamaño de celda
    static final int MARGEN_X = 70, MARGEN_Y = 30;

    /** Fondo de celda (mismo tono que AlambreDemo). */
    static final Color FONDO = new Color(0xF4F1EA);

    /**
     * Contorno, pocas costillas, radios de las dos tapas, corte central (tm)
     * y envolvente oculta. Tiradores en gris suave. Sin etiquetas ni contactos.
     */
    static final Trazos.Ajustes AJUSTES = Trazos.Ajustes.POR_DEFECTO
            .conEtiquetas(false)
            .conOcultas(false)
            .conSecciones(2)     // Sin círculos intermedios
            .conRadios(true)     // r1 y r2 de las tapas
            .conContactos(true);

    /** Vectores de control (relativos a cada centro) de cada forma; y hacia arriba. */
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

    /** Ancho del viewBox de un SVG (tercer número del atributo). */
    static double anchoVista(String svg) {
        int i = svg.indexOf("viewBox=\"") + 9;
        return Double.parseDouble(svg.substring(i, svg.indexOf('"', i)).split(" ")[2]);
    }

    /** PlotterSvg: documento completo, vista que crece con las gotas y ciclo de vida del plotter. */
    static void svg() {
        Gota a = gota("suave", 5, 2, 2, 90, 100), b = gota("S", 3, 1, 3, 400, 100);
        java.io.StringWriter w1 = new java.io.StringWriter(), w2 = new java.io.StringWriter();
        try (PlotterSvg ps = new PlotterSvg(w1, AJUSTES)) { ps.plot(a); }
        try (PlotterSvg ps = new PlotterSvg(w2, AJUSTES)) { ps.plot(a); ps.plot(b); }
        String s1 = w1.toString(), s2 = w2.toString();
        comprueba("PlotterSvg: documento completo", s1.startsWith("<svg ") && s1.contains("class=\"contorno\"") && s1.trim().endsWith("</svg>"), s1.substring(0, Math.min(80, s1.length())));
        comprueba("PlotterSvg: la vista abarca todas las gotas", anchoVista(s2) > anchoVista(s1), anchoVista(s1) + " frente a " + anchoVista(s2));
        PlotterSvg vacio = new PlotterSvg(new java.io.StringWriter());
        boolean lanza = false;
        try { vacio.close(); } catch (IllegalStateException e) { lanza = true; }
        comprueba("PlotterSvg: sin gotas no hay documento", lanza, "no lanza");
        PlotterSvg cerrado = new PlotterSvg(new java.io.StringWriter());
        cerrado.plot(a);
        cerrado.close();
        cerrado.close();
        lanza = false;
        try { cerrado.plot(b); } catch (IllegalStateException e) { lanza = true; }
        comprueba("PlotterSvg: cerrar dos veces vale, dibujar cerrado no", lanza, "no lanza");
    }

    public static void main(String[] args) throws Exception {
        int filas = SEPARACIONES.length * FORMAS.length, cols = RADIOS.length;
        int w = MARGEN_X + cols * CW, h = MARGEN_Y + filas * CH;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, w, h);
        g2.dispose();

        int fila = 0;
        for (String forma : FORMAS) {
            for (double d : SEPARACIONES) {
                for (int c = 0; c < cols; c++) {
                    double r1 = RADIOS[c][0], r2 = RADIOS[c][1];
                    int x0 = MARGEN_X + c * CW, y0 = MARGEN_Y + fila * CH;
                    BufferedImage celda = new BufferedImage(CW, CH, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D gc = celda.createGraphics();
                    gc.setColor(FONDO);
                    gc.fillRect(0, 0, CW, CH);
                    gc.dispose();

                    String nombre = String.format("%s d=%.0f r1=%.1f r2=%.1f", forma, d, r1, r2);
                    try {
                        Gota g = gota(forma, d, r1, r2, 90, CH * 0.62);
                        PlotterAlambre.sobre(celda, AJUSTES).plot(g);
                        int opacos = 0;
                        for (int y = 0; y < CH; y++)
                            for (int x = 0; x < CW; x++)
                                if ((celda.getRGB(x, y) >>> 24) != 0
                                        && (celda.getRGB(x, y) & 0xFFFFFF) != (FONDO.getRGB() & 0xFFFFFF))
                                    opacos++;
                        comprueba(nombre + ": deja huella", opacos > 0, "sin trazos");
                    } catch (RuntimeException e) {
                        comprueba(nombre + ": se construye y se traza", false, e.toString());
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
            gg.drawString(String.format("r1 = %.1f   r2 = %.1f", RADIOS[c][0], RADIOS[c][1]),
                    MARGEN_X + c * CW + 8, 18);
        gg.dispose();

        ImageIO.write(img, "png", new File("suite_matriz_01_alambre.png"));

        svg();

        System.out.printf("%n%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }
}
