import color.Colores;
import color.Mezcla;
import coordenadas.Opciones;
import forma.Gota;
import forma.Perfil;
import geometria.Geometria;
import geometria.composicion.Pintada;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Prueba del prototipo {@link Rejilla} (estudio #86):
 * 1) la costura de Costura086 con la Rejilla real (tres tratamientos de cobertura);
 * 2) la lámina Sol_by_Claude con la Rejilla N=1 frente al Plotter actual (¿equivalen?) y con N=3 (tiempo y aportes por franja).
 * Uso: java PruebaRejilla [ruta de la carpeta donde dejar los PNG]
 */
public class PruebaRejilla {
    static final int ROJO = 0xFFE03030, AZUL = 0xFF3050E0, FONDO = 0xFFFFFFFF;

    static BufferedImage lienzo(int w, int h) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), FONDO);
        return im;
    }

    static Pintada capsula(double x0, double x1, double y, double r, int c) {
        return Pintada.de(Gota.lineal(new Point2D.Double(x0, y), r, new Point2D.Double(x1, y), r, Perfil.recto()),
                Colores.cuatroColores(c, c, c, c, Mezcla.RGB));
    }

    static double canal(int c, int s) { return (c >> s) & 255; }

    static void costura() {
        double[] fs = { 0.1, 0.25, 0.5, 0.75, 0.9 };
        System.out.println("== 1. Costura con la Rejilla real (error máximo en la fila del borde, 0..255)");
        for (Rejilla.Cobertura cb : Rejilla.Cobertura.values()) {
            System.out.println("-- cobertura " + cb);
            System.out.printf("%-6s", "N \\ f");
            for (double f : fs) System.out.printf("%10.2f", f);
            System.out.println();
            for (int n : new int[] { 1, 2, 3, 4 }) {
                System.out.printf("%-6d", n);
                for (double f : fs) {
                    BufferedImage im = lienzo(120, 100);
                    double yb = 50 + f;
                    new Rejilla(n, 16, cb, Rejilla.Filtro.SRGB).aporta(capsula(15, 105, yb - 15, 15, ROJO)).aporta(capsula(15, 105, yb + 15, 15, AZUL)).prensa(im);
                    double peor = 0;
                    for (int x = 30; x < 90; x++) {
                        int p = im.getRGB(x, 50);
                        for (int s : new int[] { 16, 8, 0 })
                            peor = Math.max(peor, Math.abs(canal(p, s) - (f * canal(ROJO, s) + (1 - f) * canal(AZUL, s))));
                    }
                    System.out.printf("%10.1f", peor);
                }
                System.out.println();
            }
        }
    }

    static int[] diferencias(BufferedImage a, BufferedImage b) {
        int[] d = new int[2];
        for (int j = 0; j < a.getHeight(); j++) for (int i = 0; i < a.getWidth(); i++) {
            int p = a.getRGB(i, j), q = b.getRGB(i, j);
            if (p == q) continue;
            d[0]++;
            for (int s : new int[] { 24, 16, 8, 0 }) d[1] = Math.max(d[1], (int) Math.abs(canal(p, s) - canal(q, s)));
        }
        return d;
    }

    static void sol(File dir) throws Exception {
        System.out.println("== 2. Sol_by_Claude: Rejilla frente al Plotter actual");
        long t0 = System.nanoTime();
        BufferedImage ref = Sol_by_Claude.dibuja();
        System.out.printf("Plotter actual: %.0f ms%n", (System.nanoTime() - t0) / 1e6);
        for (int n : new int[] { 1, 3 }) for (Rejilla.Cobertura cb : n == 1 ? new Rejilla.Cobertura[] { Rejilla.Cobertura.TAL_CUAL } : new Rejilla.Cobertura[] { Rejilla.Cobertura.TAL_CUAL, Rejilla.Cobertura.ESTRECHADA, Rejilla.Cobertura.BINARIA }) {
            BufferedImage im = lienzo(Sol_by_Claude.W, Sol_by_Claude.H);
            Rejilla r = new Rejilla(n, 32, cb, Rejilla.Filtro.SRGB);
            for (Geometria g : Sol_by_Claude.escena()) r.aporta(g);
            Rejilla.Estadisticas e = r.prensa(im);
            int[] d = diferencias(ref, im);
            System.out.printf("N=%d %-10s %6.0f ms  aportes=%d  máx. profundidad=%d  máx. aportes/franja=%d (~%.1f MB a 24 B)  dif. con el Plotter: %d px, canal hasta %d%n",
                    n, cb, e.nanos() / 1e6, e.aportes(), e.maxProfundidad(), e.maxAportesFranja(), e.maxAportesFranja() * 24 / 1e6, d[0], d[1]);
            ImageIO.write(im, "png", new File(dir, "sol_rejilla_n" + n + "_" + cb.name().toLowerCase() + ".png"));
        }
        ImageIO.write(ref, "png", new File(dir, "sol_plotter.png"));
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : ".");
        dir.mkdirs();
        costura();
        sol(dir);
    }
}
