import color.Colores;
import color.Mezcla;
import forma.Gota;
import forma.Perfil;
import pintado.Plotter;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Estudio #86, pregunta 2: la costura entre dos geometrías de borde común.
 * <p>
 * Dos cápsulas (gotas de radios iguales con los centros separados: borde recto) se apilan con el borde común en y = 120 + f,
 * con f fraccionario. Cada fila de píxeles que cruza el borde recibe la cobertura c de la roja (de arriba) y 1 − c de la azul.
 * Con cobertura escalar y composición «over» el fondo se cuela: queda (1 − c)·c de fondo en la costura (conflation).
 * Con sobremuestreo N×N (se pinta la escena a N× con el plotter actual y se promedia en cajas N×N) la costura debería
 * acercarse al color ideal c·rojo + (1 − c)·azul.
 * <p>
 * Uso: java Costura086 (imprime, por N y por f, el error máximo de la fila del borde respecto al ideal, en niveles 0..255).
 */
public class Costura086 {
    static final int ROJO = 0xFFE03030, AZUL = 0xFF3050E0, FONDO = 0xFFFFFFFF;
    static final int W = 120, H = 100;                          // imagen final (la escena ocupa x 10..110, y 20..80)

    static BufferedImage lienzo(int w, int h) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), FONDO);
        return im;
    }

    static void capsula(BufferedImage im, double x0, double x1, double y, double r, int color) {
        Plotter.raster(im).plot(Gota.lineal(new Point2D.Double(x0, y), r, new Point2D.Double(x1, y), r, Perfil.recto()),
                Colores.cuatroColores(color, color, color, color, Mezcla.RGB));
    }

    /** Escena a escala n (coordenadas y radios por n), con el borde común en y = 50 + f. */
    static BufferedImage escena(int n, double f) {
        BufferedImage im = lienzo(W * n, H * n);
        double yb = 50 + f;
        capsula(im, 15 * n, 105 * n, (yb - 15) * n, 15 * n, ROJO);
        capsula(im, 15 * n, 105 * n, (yb + 15) * n, 15 * n, AZUL);
        return im;
    }

    static BufferedImage promedia(BufferedImage im, int n) {
        if (n == 1) return im;
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int j = 0; j < H; j++) for (int i = 0; i < W; i++) {
            double a = 0, r = 0, g = 0, b = 0;
            for (int q = 0; q < n; q++) for (int p = 0; p < n; p++) {
                int c = im.getRGB(i * n + p, j * n + q);
                a += c >>> 24; r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255;
            }
            int k = n * n;
            out.setRGB(i, j, (int) Math.round(a / k) << 24 | (int) Math.round(r / k) << 16 | (int) Math.round(g / k) << 8 | (int) Math.round(b / k));
        }
        return out;
    }

    static double canal(int c, int s) { return (c >> s) & 255; }

    public static void main(String[] args) {
        double[] fs = { 0.1, 0.25, 0.5, 0.75, 0.9 };
        System.out.println("error máximo en la fila del borde (niveles 0..255, solo el canal rojo+verde+azul peor) y fondo colado");
        System.out.printf("%-6s", "N \\ f");
        for (double f : fs) System.out.printf("%12.2f", f);
        System.out.println();
        for (int n : new int[] { 1, 2, 3, 4 }) {
            System.out.printf("%-6d", n);
            for (double f : fs) {
                BufferedImage im = promedia(escena(n, f), n);
                int fila = 50;                                  // la fila de píxeles [50, 51) contiene el borde en 50 + f
                double c = f;                                   // parte de la fila que ocupa la roja (por encima del borde, en y < 50 + f)
                double peor = 0;
                for (int x = 30; x < 90; x++) {
                    int p = im.getRGB(x, fila);
                    for (int s : new int[] { 16, 8, 0 }) {
                        double ideal = c * canal(ROJO, s) + (1 - c) * canal(AZUL, s);
                        peor = Math.max(peor, Math.abs(canal(p, s) - ideal));
                    }
                }
                System.out.printf("%12.1f", peor);
            }
            System.out.println();
        }
    }
}
