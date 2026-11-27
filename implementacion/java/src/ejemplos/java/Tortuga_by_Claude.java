import geometria.composicion.Grafo;
import pintado.Plotter;
import color.Mezcla;
import color.Degradado;
import color.Colores;
import forma.Perfil;
import forma.Gota;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Lámina libre de Claude (primer intento, sin retoques): una tortuga marina vista desde arriba.
 * <ul>
 * <li>El caparazón es una gota rígida (dos radios casi iguales y centros cercanos: cuatro arcos) con el volumen en la
 *     función del radio; las placas son discos con degradado, en el reparto clásico (una columna central, dos laterales
 *     y una corona de placas marginales).</li>
 * <li>Las aletas son gotas de base ancha y punta fina con tiradores, para que curven hacia atrás como al nadar.</li>
 * <li>Cabeza y cuello, una cadena de dos nodos; las motas de piel, discos.</li>
 * </ul>
 * Escribe tortuga_by_claude.png.
 */
public class Tortuga_by_Claude {
    static final int W = 820, H = 720;
    static final double CX = 410, CY = 370;
    static final Mezcla RGB = Mezcla.RGB;

    static int mezcla(int a, int b, double t) { return RGB.mezclar(a, b, Math.max(0, Math.min(1, t))); }

    static final int PIEL = 0xFF8DB86B, PIEL_OSC = 0xFF3F6B3A, MOTA = 0xFFF0E6A8;
    static final int CAP = 0xFF9A7B2E, CAP_OSC = 0xFF3B2A12, PLACA = 0xFFC9A94A;

    static void disco(BufferedImage im, double x, double y, double r, int centro, int borde) {
        Point2D.Double a = new Point2D.Double(x, y), b = new Point2D.Double(x + 0.5, y);
        Plotter.raster(im).plot(Gota.lineal(a, r, b, r, Perfil.recto()), Colores.cuatroColores(centro, centro, borde, borde, Mezcla.RGB));
    }

    /** Aleta: base ancha en el cuerpo y punta fina, curvada por los tiradores (vector de salida y de llegada). */
    static void aleta(BufferedImage im, double x0, double y0, double r0, double x1, double y1, double r1, Gota.Vec sale, Gota.Vec llega) {
        Grafo g = new Grafo().mezcla(RGB);
        var a = g.nodo(x0, y0, r0).centro(PIEL).radio(x -> mezcla(PIEL, PIEL_OSC, 0.75 * x * x));
        var b = g.nodo(x1, y1, r1).centro(mezcla(PIEL, MOTA, 0.3)).radio(x -> mezcla(mezcla(PIEL, MOTA, 0.3), PIEL_OSC, 0.75 * x * x));
        g.arista(a, b).tiradores(sale, llega);
        Plotter.raster(im).plot(g);
    }

    static void fondo(BufferedImage im) {
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFF0C4A63);
        Point2D.Double a = new Point2D.Double(W * 0.5, 360), b = new Point2D.Double(W * 0.5 + 0.5, 360);
        Plotter.raster(im).plot(Gota.lineal(a, 700, b, 700, Perfil.recto()),
            Colores.cuatroColores(0xFF2FA3B5, 0xFF2FA3B5, 0xFF072B40, 0xFF072B40, Mezcla.RGB));
    }

    static BufferedImage dibuja() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        fondo(im);
        for (int lado = -1; lado <= 1; lado += 2) {
            // aletas delanteras (grandes, hacia atrás y afuera) y traseras (pequeñas)
            aleta(im, CX + lado * 80, CY - 95, 34, CX + lado * 300, CY - 150, 9, new Gota.Vec(lado * 120, -30), new Gota.Vec(lado * 70, -40));
            aleta(im, CX + lado * 62, CY + 130, 24, CX + lado * 165, CY + 215, 8, new Gota.Vec(lado * 40, 60), new Gota.Vec(lado * 40, 40));
        }
        // cola
        aleta(im, CX, CY + 160, 14, CX, CY + 245, 3, new Gota.Vec(0, 40), new Gota.Vec(0, 40));
        // cuello y cabeza
        Grafo c = new Grafo().mezcla(RGB);
        var cu = c.nodo(CX, CY - 140, 34).centro(PIEL).radio(x -> mezcla(PIEL, PIEL_OSC, 0.75 * x * x));
        var ca = c.nodo(CX, CY - 232, 31).centro(mezcla(PIEL, MOTA, 0.2)).radio(x -> mezcla(mezcla(PIEL, MOTA, 0.2), PIEL_OSC, 0.75 * x * x));
        c.arista(cu, ca);
        Plotter.raster(im).plot(c);
        // ojos y boca
        for (int lado = -1; lado <= 1; lado += 2) {
            disco(im, CX + lado * 19, CY - 240, 6.5, 0xFF16110A, 0xFF16110A);
            disco(im, CX + lado * 17.5, CY - 242, 1.8, 0xFFFFFFFF, 0xFFFFFFFF);
        }
        // caparazón: gota rígida
        Grafo cap = new Grafo().mezcla(RGB);
        var a = cap.nodo(CX, CY - 55, 112).centro(CAP).radio(x -> mezcla(CAP, CAP_OSC, 0.85 * x * x));
        var b = cap.nodo(CX, CY + 60, 118).centro(CAP).radio(x -> mezcla(CAP, CAP_OSC, 0.85 * x * x));
        cap.arista(a, b);
        Plotter.raster(im).plot(cap);
        // placas: columna central, laterales y corona marginal
        for (int i = 0; i < 3; i++) disco(im, CX, CY - 85 + i * 85, 36 - i * 1.5, PLACA, CAP_OSC);
        for (int lado = -1; lado <= 1; lado += 2)
            for (int i = 0; i < 2; i++) disco(im, CX + lado * 66, CY - 45 + i * 85, 31, PLACA, CAP_OSC);
        int n = 18;
        for (int i = 0; i < n; i++) {
            double ang = 2 * Math.PI * (i + 0.5) / n;
            disco(im, CX + 124 * Math.cos(ang) * 0.97, CY + 5 + 168 * Math.sin(ang), 13, mezcla(PLACA, 0xFFE6D07A, 0.4), CAP_OSC);
        }
        // motas de piel en las aletas
        for (int lado = -1; lado <= 1; lado += 2)
            for (int i = 0; i < 4; i++)
                disco(im, CX + lado * (150 + 36 * i), CY - 112 - 12 * i + 6 * (i % 2), 4 - 0.6 * i, MOTA, MOTA);
        return im;
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(dibuja(), "png", new File("tortuga_by_claude.png"));
        System.out.println("tortuga_by_claude.png");
    }
}
