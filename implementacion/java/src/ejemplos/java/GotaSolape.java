import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * Comparativa de solape. Fila superior: PLANO (por defecto). Fila inferior: TRAZO.
 * Columnas: lazo grueso, lazo fino, círculo contenido, forma hinchada.
 * Además comprueba que la máscara (el alfa) es la misma en los dos modos.
 */
public class GotaSolape {

    static Point2D.Double p(int col, int fila, double x, double y) {
        return new Point2D.Double(col * 300 + x, fila * 300 + y);
    }

    static Gota crea(int caso, int col, int fila) {
        return switch (caso) {
            case 0 -> Gota.curva(p(col, fila, 80, 230), 40, new Gota.Vec(180, -230),
                                 p(col, fila, 220, 230), 30, new Gota.Vec(-180, -230), Gota.Perfil.central(1.0));
            case 1 -> Gota.curva(p(col, fila, 80, 230), 14, new Gota.Vec(180, -230),
                                 p(col, fila, 220, 230), 10, new Gota.Vec(-180, -230), Gota.Perfil.central(1.0));
            case 2 -> Gota.lineal(p(col, fila, 120, 150), 70, p(col, fila, 160, 150), 15, Gota.Perfil.central(1.0));
            default -> Gota.lineal(p(col, fila, 60, 150), 50, p(col, fila, 240, 150), 20, Gota.Perfil.central(1.8));
        };
    }

    public static void main(String[] args) throws Exception {
        String[] nombres = { "lazo grueso", "lazo fino", "circulo contenido", "hinchada h=1.8" };
        // centros amarillo -> azul para distinguir el principio del camino del final
        Gota.Relleno rel = Rellenos.cuatroColores(0xFFFFE27A, 0xFF2563EB, 0xFFC2410C, 0xFF1E1B4B);

        BufferedImage img = new BufferedImage(1200, 600, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) img.getRaster().getDataBuffer()).getData(), 0xFFFFFFFF);
        for (int c = 0; c < 4; c++) {
            crea(c, c, 0).render(img, rel);
            crea(c, c, 1).conSolape(Gota.Solape.TRAZO).render(img, rel);
        }
        ImageIO.write(img, "png", new File("gota_solape.png"));

        // Máscara: el alfa debe coincidir entre modos (en transparente, una celda por caso)
        boolean ok = true;
        for (int c = 0; c < 4; c++) {
            BufferedImage a = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
            BufferedImage b = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
            Gota g = crea(c, 0, 0);
            long t0 = System.nanoTime();
            g.render(a, rel);
            double msPlano = (System.nanoTime() - t0) / 1e6;
            t0 = System.nanoTime();
            g.conSolape(Gota.Solape.TRAZO).render(b, rel);
            double msTrazo = (System.nanoTime() - t0) / 1e6;
            int[] da = ((DataBufferInt) a.getRaster().getDataBuffer()).getData();
            int[] db = ((DataBufferInt) b.getRaster().getDataBuffer()).getData();
            int opacos = 0, difs = 0, maxDif = 0;
            for (int i = 0; i < da.length; i++) {
                int d = Math.abs((da[i] >>> 24) - (db[i] >>> 24));
                maxDif = Math.max(maxDif, d);
                if (d > 64) difs++;
                if ((da[i] >>> 24) == 255) opacos++;
            }
            boolean bien = difs <= 5;
            System.out.printf("%-18s PLANO %6.1f ms | TRAZO %6.1f ms | opacos=%6d  difs alfa>64: %d (max %d)  %s%n",
                    nombres[c], msPlano, msTrazo, opacos, difs, maxDif, bien ? "ok" : "REVISAR");
            ok &= bien;
        }
        System.out.println(ok ? "OK" : "HAY DISCREPANCIAS");
    }
}
