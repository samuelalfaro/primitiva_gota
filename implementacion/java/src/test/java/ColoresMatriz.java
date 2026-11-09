import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Matriz de siluetas con la cadena lateral de 6 colores (modo ESPIGA).
 *
 * Filas: separación horizontal d = 5, 4, 3 de dos centros, con la espina recta,
 * en Bézier suave, en S y en ჲ (un bucle).
 * Columnas: r1 crece de 0 a 4 y r2 decrece de 4 a 0 (r1 + r2 = 4).
 *
 * Colores (orientación ESPIGA, camino L→R, N apunta abajo):
 *   nodo 1 (izquierda):  contra=amarillo (arriba), centro=magenta, normal=cian (abajo)
 *   nodo 2 (derecha):    contra=rojo     (arriba), centro=verde,   normal=azul (abajo)
 *
 * Deja la imagen en {@code colores_matriz.png}. Las comprobaciones son mínimas
 * (la gota se construye, se pinta y deja huella de color).
 */
public class ColoresMatriz {
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

    // 6 anclas del ejemplo clásico (ARGB)
    static final int AMARILLO = 0xFFFFFF00;
    static final int CIAN     = 0xFF00FFFF;
    static final int ROJO     = 0xFFFF0000;
    static final int AZUL     = 0xFF0000FF;
    static final int MAGENTA  = 0xFFFF00FF;
    static final int VERDE    = 0xFF00FF00;

    /** Relleno de 6 colores en ESPIGA (por defecto). */
    static final Relleno RELLENO = Colores.cadenaLateral(
            new Colores.Lateral(AMARILLO, MAGENTA, CIAN),   // nodo 1: arriba / centro / abajo
            new Colores.Lateral(ROJO,     VERDE,   AZUL),   // nodo 2
            Mezcla.RGB);

    static final Opciones OPCIONES = Opciones.POR_DEFECTO;  // ESPIGA + PLANO + tira 1:2:1

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
                    String nombre = String.format("%s d=%.0f r1=%.1f r2=%.1f", forma, d, r1, r2);
                    try {
                        Gota g = gota(forma, d, r1, r2, 90, CH * 0.62);
                        Plotter.raster(celda).plot(g, RELLENO, OPCIONES);
                        int opacos = 0;
                        for (int y = 0; y < CH; y++)
                            for (int x = 0; x < CW; x++)
                                if ((celda.getRGB(x, y) >>> 24) != 0) opacos++;
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
            gg.drawString(String.format("r1 = %.1f   r2 = %.1f", RADIOS[c][0], RADIOS[c][1]),
                    MARGEN_X + c * CW + 8, 18);
        gg.dispose();

        ImageIO.write(img, "png", new File("colores_matriz.png"));

        System.out.printf("%n%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }
}
