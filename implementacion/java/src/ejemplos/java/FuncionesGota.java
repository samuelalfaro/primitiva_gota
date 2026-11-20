import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Una sola gota con las cinco funciones de color distintas (normal1, contra1, espina, normal2, contra2), que solo
 * coinciden en los dos centros: pone en apuros el parche de Coons. Tres paneles: suave, con ondas y con escalones.
 * Escribe funciones_gota.png en el directorio actual.
 */
public class FuncionesGota {
    static final int W = 760, H = 280;
    static final Mezcla M = Mezcla.LCH;

    static int mezcla(int a, int b, double t) { return M.mezclar(a, b, Math.max(0, Math.min(1, t))); }

    static BufferedImage panel(Colores.Funciones f) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((java.awt.image.DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFFF4F1EA);
        Gota g = Gota.lineal(new Point2D.Double(150, 140), 100, new Point2D.Double(560, 140), 62, Perfil.recto());
        Plotter.raster(im).plot(g, Colores.cadenaLateral(f, M));
        return im;
    }

    public static void main(String[] args) throws Exception {
        int c1 = 0xFFF6D743, c2 = 0xFF3B1F7A;                         // los dos centros: amarillo y violeta
        int n1 = 0xFFE63946, k1 = 0xFF2A9D8F, n2 = 0xFF00B4D8, k2 = 0xFFFF7F51;

        // 1. suave: cada función con su propio ritmo
        Colores.Funciones a = new Colores.Funciones(
            x -> mezcla(c1, n1, x),                                       // normal1: lineal
            x -> mezcla(c1, k1, x * x),                                   // contra1: tarda y acelera
            x -> mezcla(c1, c2, x + 0.10 * Math.sin(6 * Math.PI * x)),    // espina: con una oscilación
            x -> mezcla(c2, n2, Math.sqrt(x)),                            // normal2: rápida y frena
            x -> mezcla(c2, k2, x * x * (3 - 2 * x)));                    // contra2: suavizada

        // 2. ondas: oscilaciones que no cambian los extremos
        Colores.Funciones b = new Colores.Funciones(
            x -> mezcla(c1, n1, x + 0.2 * Math.sin(5 * Math.PI * x)),
            x -> mezcla(c1, k1, Math.abs(Math.sin(3.5 * Math.PI * x))),
            x -> mezcla(c1, c2, x + 0.12 * Math.sin(8 * Math.PI * x)),
            x -> mezcla(c2, n2, Math.abs(Math.sin(2.5 * Math.PI * x))),
            x -> mezcla(c2, k2, 0.5 - 0.5 * Math.cos(7 * Math.PI * x)));

        // 3. escalones: cuatro funciones con saltos duros fuera del centro y una suave; todas arrancan del color del
        // centro con una rampa continua (w), para que el entorno del centro sea de ese color
        double w = 0.15;
        Colores.Funciones c = new Colores.Funciones(
            x -> x < w ? mezcla(c1, n1, x / w) : mezcla(n1, k1, Math.floor((x - w) / (1 - w) * 4) / 3.0),
            x -> x < w ? mezcla(c1, k1, x / w) : (x < 0.55 ? k1 : n2),
            x -> mezcla(c1, c2, x),
            x -> x < w ? mezcla(c2, n2, x / w) : (Math.floor((x - w) / (1 - w) * 5) % 2 == 0 ? n2 : k2),
            x -> x < w ? mezcla(c2, k2, x / w) : mezcla(k2, n1, (x - w) / (1 - w)));

        BufferedImage hoja = new BufferedImage(W, H * 3, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hoja.createGraphics();
        g.drawImage(panel(a), 0, 0, null);
        g.drawImage(panel(b), 0, H, null);
        g.drawImage(panel(c), 0, 2 * H, null);
        g.dispose();
        ImageIO.write(hoja, "png", new File("funciones_gota.png"));
        System.out.println("funciones_gota.png");
    }
}
