package forma;

import common.Fuentes;
import pintado.PlotterAlambre;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import pintado.Trazos;

/**
 * Matriz de acercamiento de los centros, en modo alambre. Una gota de camino recto con radios fijos (una fila por pareja de
 * radios) cuyos centros se acercan columna a columna, desde separados (trayectoria > r1 + r2, la envolvente) hasta tocarse
 * (d = 0), pasando por la frontera d = r1 + r2 en la que la forma pasa a ser rígida (dos tapas y dos conectores). Las columnas
 * son la razón s = d / (r1 + r2): 1.25, 1.001 (justo por encima de la frontera), 1 (justo en ella), 0.75, 0.5, 0.25, 0.1 y 0.
 * Las filas incluyen radios iguales, distintos y con un radio 0 (con los dos centros en el mismo punto en la última columna).
 * Con los dos radios a 0 el caso es otro estudio y no está aquí.
 *
 * Cada celda anota el modo (envolvente o rígida), el área de la silueta y el cambio respecto a la columna anterior. Sirve para
 * ver y medir el paso de un modo a otro, no para imponerlo. Se resaltan (marco rojo y línea en la consola) dos cosas que no
 * cuentan como fallo: que el área cambie más de {@link #SALTO_MODO} entre las columnas 1.001 y 1 (el paso de envolvente a rígida,
 * que debería ser continuo), y que el área crezca al acercarse los centros. Fallan solo que la gota no se construya o no se trace,
 * que no deje huella y que, siendo rígida, el contorno del alambre no sea un trazo cerrado.
 *
 * Imagen: {@code suite_matriz_05_acercamiento.png}.
 */
public class SuiteMatriz_05_Acercamiento {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        if (!ok) System.out.printf("%-60s FALLA  %s%n", n, d);
    }

    static final double[] RAZONES = { 1.25, 1.001, 1.0, 0.75, 0.5, 0.25, 0.1, 0 };
    static final String[] ETIQUETAS = { "1.25", "1.001", "1", "0.75", "0.5", "0.25", "0.1", "0" };
    static final double[][] RADIOS = { { 2, 2 }, { 1, 3 }, { 3, 1 }, { 0.5, 3.5 }, { 0, 4 }, { 4, 0 } };

    static final double ESCALA = SuiteMatriz_01_Alambre.ESCALA;
    static final int CW = SuiteMatriz_01_Alambre.CW, CH = SuiteMatriz_01_Alambre.CH;
    static final int MARGEN_X = 70, MARGEN_Y = 30;
    /** Cambio relativo de área en el paso de envolvente a rígida (columnas 1.001 y 1) a partir del cual se resalta. */
    static final double SALTO_MODO = 0.01;
    static final Trazos.Ajustes AJUSTES = SuiteMatriz_01_Alambre.AJUSTES;

    /** Área de la silueta en unidades al cuadrado, contando píxeles de la máscara. */
    static double area(Gota g) {
        long n = 0;
        for (int y = 0; y < CH; y++)
            for (int x = 0; x < CW; x++)
                if (g.mascara().contiene(x + .5, y + .5)) n++;
        return n / (ESCALA * ESCALA);
    }

    public static void main(String[] args) throws Exception {
        int filas = RADIOS.length, cols = RAZONES.length;
        BufferedImage img = new BufferedImage(MARGEN_X + cols * CW, MARGEN_Y + filas * CH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, img.getWidth(), img.getHeight());
        g2.dispose();
        System.out.printf("%-22s %-6s %-9s %7s %6s %8s%n", "radios", "s", "modo", "d", "área", "Δ área");
        for (int f = 0; f < filas; f++) {
            double r1 = RADIOS[f][0], r2 = RADIOS[f][1], areaPrevia = -1;
            for (int c = 0; c < cols; c++) {
                double d = RAZONES[c] * (r1 + r2);
                String nombre = String.format("r1=%.1f r2=%.1f s=%s", r1, r2, ETIQUETAS[c]);
                int x0 = MARGEN_X + c * CW, y0 = MARGEN_Y + f * CH;
                BufferedImage celda = new BufferedImage(CW, CH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gc = celda.createGraphics();
                gc.setColor(SuiteMatriz_01_Alambre.FONDO);
                gc.fillRect(0, 0, CW, CH);
                gc.dispose();
                boolean rigida = false, salto = false;
                double area = 0, dif = 0;
                try {
                    Gota g = SuiteMatriz_01_Alambre.gota("recta", d, r1, r2, 90, CH * 0.5);
                    PlotterAlambre.sobre(celda, AJUSTES).plot(g);
                    int opacos = 0;
                    for (int y = 0; y < CH; y++)
                        for (int x = 0; x < CW; x++)
                            if ((celda.getRGB(x, y) & 0xFFFFFF) != (SuiteMatriz_01_Alambre.FONDO.getRGB() & 0xFFFFFF)) opacos++;
                    comprueba(nombre + ": deja huella", opacos > 0, "sin trazos");
                    rigida = g.arcos() != null;
                    if (rigida) SuiteMatriz_01_Alambre.contornoRigido(nombre, g);
                    area = area(g);
                    if (areaPrevia >= 0) {
                        dif = area - areaPrevia;
                        boolean frontera = RAZONES[c - 1] == 1.001 && RAZONES[c] == 1.0;
                        salto = areaPrevia > 0 && (frontera ? Math.abs(dif) > SALTO_MODO * areaPrevia : dif > 1e-9);
                    }
                    areaPrevia = area;
                } catch (RuntimeException e) {
                    comprueba(nombre + ": se construye y se traza", false, e.toString());
                }
                System.out.printf("%-22s %-6s %-9s %7.3f %6.2f %8s%s%n", String.format("%.1f / %.1f", r1, r2), ETIQUETAS[c],
                    rigida ? "rígida" : "envolv.", d, area, c == 0 ? "-" : String.format("%+.2f", dif), salto ? "   <-- RESALTE" : "");
                Graphics2D gg = img.createGraphics();
                gg.drawImage(celda, x0, y0, null);
                gg.setColor(salto ? Color.RED : new Color(0xCCCCCC));
                gg.drawRect(x0, y0, CW - 1, CH - 1);
                if (salto) gg.drawRect(x0 + 1, y0 + 1, CW - 3, CH - 3);
                gg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                if (Fuentes.hay()) {
                    gg.setColor(Color.DARK_GRAY);
                    gg.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                    gg.drawString(String.format("s=%s  d=%.2f  %s", ETIQUETAS[c], d, rigida ? "rígida" : "envolvente"), x0 + 8, y0 + 14);
                    gg.drawString(String.format("área %.2f%s", area, c == 0 ? "" : String.format("  Δ %+.2f", dif)), x0 + 8, y0 + 28);
                }
                gg.dispose();
            }
            Graphics2D gg = img.createGraphics();
            gg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (Fuentes.hay()) {
                gg.setColor(Color.DARK_GRAY);
                gg.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                gg.drawString(String.format("r1 = %.1f", r1), 6, MARGEN_Y + f * CH + 16);
                gg.drawString(String.format("r2 = %.1f", r2), 6, MARGEN_Y + f * CH + 32);
            }
            gg.dispose();
        }
        ImageIO.write(img, "png", new File("suite_matriz_05_acercamiento.png"));
        System.out.printf("%n%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }
}
