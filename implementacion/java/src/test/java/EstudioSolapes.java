import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * Caso de estudio del solapamiento (rama estudio_solapes, ver ESTUDIO_SOLAPES.md). No es una suite de pruebas ni está en
 * {@link Todas}: dibuja, con las celdas de {@link SuiteMatriz_03_Colores}, cada forma con los solapes PLANO, TRAZO y PONDERADO
 * y cuenta los píxeles con salto de color entre vecinos. Uso: {@code EstudioSolapes [d] [umbral]} (por defecto d = 5, umbral 100);
 * el ancho de la banda del PONDERADO se cambia con {@code -Dsolape.k=...}. Deja {@code estudio_solapes_d<d>.png}.
 */
public class EstudioSolapes {
    static final String[] FORMAS = { "suave", "S", "ჲ" };
    static final Solape[] SOLAPES = { Solape.PLANO, Solape.TRAZO, Solape.PONDERADO };

    static BufferedImage lienzo() {
        BufferedImage c = new BufferedImage(SuiteMatriz_03_Colores.CW, SuiteMatriz_03_Colores.CH, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) c.getRaster().getDataBuffer()).getData(), 0xFFFFFFFF);
        return c;
    }

    /** Píxeles cubiertos con algún vecino (derecha o abajo, también cubierto) a más de {@code umbral} en algún canal. */
    static int saltos(BufferedImage im, int umbral) {
        int n = 0;
        for (int y = 1; y < im.getHeight() - 1; y++) for (int x = 1; x < im.getWidth() - 1; x++) {
            int p = im.getRGB(x, y);
            if (p == 0xFFFFFFFF) continue;
            for (int q : new int[] { im.getRGB(x + 1, y), im.getRGB(x, y + 1) }) {
                if (q == 0xFFFFFFFF) continue;
                int m = 0;
                for (int s = 0; s < 24; s += 8) m = Math.max(m, Math.abs(((p >> s) & 255) - ((q >> s) & 255)));
                if (m > umbral) { n++; break; }
            }
        }
        return n;
    }

    public static void main(String[] args) throws Exception {
        double d = args.length > 0 ? Double.parseDouble(args[0]) : 5;
        int umbral = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        int CW = SuiteMatriz_03_Colores.CW, CH = SuiteMatriz_03_Colores.CH, cols = SuiteMatriz_03_Colores.RADIOS.length;
        int mx = 150, my = 30;
        BufferedImage img = new BufferedImage(mx + cols * CW, my + FORMAS.length * SOLAPES.length * CH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        System.out.printf("Píxeles con salto de color entre vecinos (umbral %d), d = %.0f%n%-6s %-10s", umbral, d, "forma", "solape");
        for (double[] r : SuiteMatriz_03_Colores.RADIOS) System.out.printf(" %3.1f/%3.1f", r[0], r[1]);
        System.out.println();
        for (int f = 0; f < FORMAS.length; f++) {
            int[][] sj = new int[SOLAPES.length][cols];
            for (int c = 0; c < cols; c++) {
                double r1 = SuiteMatriz_03_Colores.RADIOS[c][0], r2 = SuiteMatriz_03_Colores.RADIOS[c][1];
                for (int m = 0; m < SOLAPES.length; m++) {
                    BufferedImage celda = lienzo();
                    Gota gota = SuiteMatriz_03_Colores.gota(FORMAS[f], d, r1, r2, 90, CH * 0.62);
                    Plotter.raster(celda).plot(gota, SuiteMatriz_03_Colores.RELLENO, SuiteMatriz_03_Colores.OPCIONES.conSolape(SOLAPES[m]));
                    int x0 = mx + c * CW, y0 = my + (SOLAPES.length * f + m) * CH;
                    g.drawImage(celda, x0, y0, null);
                    g.setColor(new Color(0xCCCCCC));
                    g.drawRect(x0, y0, CW - 1, CH - 1);
                    sj[m][c] = saltos(celda, umbral);
                }
            }
            for (int m = 0; m < SOLAPES.length; m++) {
                System.out.printf("%-6s %-10s", FORMAS[f], SOLAPES[m]);
                for (int c = 0; c < cols; c++) System.out.printf(" %7d", sj[m][c]);
                System.out.println();
                g.setColor(Color.DARK_GRAY);
                g.drawString(FORMAS[f] + " " + SOLAPES[m], 6, my + (SOLAPES.length * f + m) * CH + 16);
            }
        }
        g.setColor(Color.DARK_GRAY);
        for (int c = 0; c < cols; c++)
            g.drawString(String.format("r1 = %.1f   r2 = %.1f", SuiteMatriz_03_Colores.RADIOS[c][0], SuiteMatriz_03_Colores.RADIOS[c][1]), mx + c * CW + 8, 18);
        g.dispose();
        ImageIO.write(img, "png", new File("estudio_solapes_d" + (int) d + ".png"));
    }
}
