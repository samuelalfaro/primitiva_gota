import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Pruebas de {@link PlotterEsquema}: el esquema se dibuja sobre una imagen (deja huella, pinta los dos centros y
 * respeta el contrato de los plotters) en la batería de formas y radios de las suites de matriz, incluidos los
 * radios 0 y los lazos. Deja la imagen {@code plotter_esquema.png} como catálogo.
 */
public class PlotterEsquemaPruebas {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        if (!ok) System.out.printf("%-60s FALLA  %s%n", n, d);
    }

    static final String[] FORMAS = { "recta", "suave", "S", "ჲ" };
    static final double[][] RADIOS = { { 0, 4 }, { 1, 3 }, { 2, 2 }, { 3, 1 }, { 4, 0 } };
    static final int CW = SuiteMatriz_01_Alambre.CW, CH = SuiteMatriz_01_Alambre.CH;
    static final int TINTA = 0x12234A;

    static BufferedImage lienzo(int w, int h) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return im;
    }

    static boolean distintoDeBlanco(BufferedImage im) {
        for (int y = 0; y < im.getHeight(); y++)
            for (int x = 0; x < im.getWidth(); x++)
                if ((im.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) return true;
        return false;
    }

    public static void main(String[] args) throws Exception {
        BufferedImage cat = lienzo(70 + RADIOS.length * CW, 30 + FORMAS.length * CH);
        for (int f = 0; f < FORMAS.length; f++) {
            for (int c = 0; c < RADIOS.length; c++) {
                double r1 = RADIOS[c][0], r2 = RADIOS[c][1];
                String nombre = String.format("%s r1=%.1f r2=%.1f", FORMAS[f], r1, r2);
                BufferedImage celda = lienzo(CW, CH);
                try {
                    Gota g = SuiteMatriz_01_Alambre.gota(FORMAS[f], 4, r1, r2, 90, CH * 0.62);
                    Plotter.raster(celda).plot(g, Relleno.solido(0xFFE3E0D6));
                    BufferedImage base = new BufferedImage(CW, CH, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D gb = base.createGraphics();
                    gb.drawImage(celda, 0, 0, null);
                    gb.dispose();
                    PlotterEsquema.sobre(celda).plot(g);
                    boolean cambia = false;
                    for (int y = 0; y < CH && !cambia; y++)
                        for (int x = 0; x < CW; x++)
                            if (celda.getRGB(x, y) != base.getRGB(x, y)) { cambia = true; break; }
                    comprueba(nombre + ": deja huella sobre el dibujo", cambia, "sin cambios");
                    for (int e = 0; e < 2; e++) {
                        int x = (int) Math.round(g.cx(e)), y = (int) Math.round(g.cy(e));
                        comprueba(nombre + ": centro " + (e + 1) + " pintado",
                                  (celda.getRGB(x, y) & 0xFFFFFF) == TINTA, Integer.toHexString(celda.getRGB(x, y)));
                    }
                    // por Graphics2D ajeno: mismo resultado que sobre(imagen)
                    BufferedImage otra = lienzo(CW, CH);
                    Plotter.raster(otra).plot(g, Relleno.solido(0xFFE3E0D6));
                    Graphics2D go = otra.createGraphics();
                    PlotterEsquema.plot(go, g);
                    go.dispose();
                    boolean igual = true;
                    for (int y = 0; y < CH && igual; y++)
                        for (int x = 0; x < CW; x++)
                            if (otra.getRGB(x, y) != celda.getRGB(x, y)) { igual = false; break; }
                    comprueba(nombre + ": Graphics2D y sobre(imagen) coinciden", igual, "difieren");
                } catch (RuntimeException e) {
                    comprueba(nombre + ": se dibuja", false, e.toString());
                }
                Graphics2D gg = cat.createGraphics();
                gg.drawImage(celda, 70 + c * CW, 30 + f * CH, null);
                gg.setColor(new Color(0xCCCCCC));
                gg.drawRect(70 + c * CW, 30 + f * CH, CW - 1, CH - 1);
                if (f == 0) {
                    gg.setColor(Color.DARK_GRAY);
                    gg.drawString(String.format("r1 = %.1f   r2 = %.1f", r1, r2), 70 + c * CW + 8, 18);
                }
                if (c == 0) {
                    gg.setColor(Color.DARK_GRAY);
                    gg.drawString(FORMAS[f], 6, 30 + f * CH + 16);
                }
                gg.dispose();
            }
        }
        boolean lanza = false;
        try {
            PlotterEsquema.sobre(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));
        } catch (IllegalArgumentException e) { lanza = true; }
        comprueba("sobre(imagen): exige TYPE_INT_ARGB", lanza, "no lanza");
        ImageIO.write(cat, "png", new File("plotter_esquema.png"));
        System.out.printf("%n%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }
}
