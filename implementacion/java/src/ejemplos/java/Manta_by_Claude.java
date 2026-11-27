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
 * Lámina libre de Claude: una manta raya vista desde arriba. Es la lección de la tortuga: con solo dos nodos y tiradores,
 * una aleta no se deja curvar hacia atrás; con un nodo intermedio por tramo sí.
 * <ul>
 * <li>Cada ala es una cadena de cuatro nodos (cuerpo, codo, muñeca, punta) cuyos radios decrecen: la curva barrida hacia
 *     atrás sale de la posición de los nodos, y el borde, de la función del radio.</li>
 * <li>La cola es una cadena larga de radios mínimos; los lóbulos cefálicos, dos gotas pequeñas delante.</li>
 * <li>Las manchas dorsales son discos; el fondo, un degradado azul con un destello de luz.</li>
 * </ul>
 * Escribe manta_by_claude.png.
 */
public class Manta_by_Claude {
    static final int W = 860, H = 760;
    static final double CX = 430;
    static final Mezcla RGB = Mezcla.RGB;

    static int mezcla(int a, int b, double t) { return RGB.mezclar(a, b, Math.max(0, Math.min(1, t))); }

    static final int LOMO = 0xFF3B5A8C, LOMO_CLARO = 0xFF6F93C4, BORDE = 0xFF111E3A;

    static Degradado volumen(int c) { return x -> mezcla(c, BORDE, 0.8 * x * x); }

    static void disco(BufferedImage im, double x, double y, double r, int centro, int borde) {
        Point2D.Double a = new Point2D.Double(x, y), b = new Point2D.Double(x + 0.5, y);
        Plotter.raster(im).plot(Gota.lineal(a, r, b, r, Perfil.recto()), Colores.cuatroColores(centro, centro, borde, borde, Mezcla.RGB));
    }

    static void fondo(BufferedImage im) {
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFF04182B);
        Point2D.Double a = new Point2D.Double(W * 0.5, 280), b = new Point2D.Double(W * 0.5 + 0.5, 280);
        Plotter.raster(im).plot(Gota.lineal(a, 760, b, 760, Perfil.recto()),
            Colores.cuatroColores(0xFF1F7A99, 0xFF1F7A99, 0xFF031020, 0xFF031020, Mezcla.RGB));
    }

    /** Cadena de nodos {x, y, radio} con color que pasa de LOMO_CLARO (primer nodo) a LOMO (último). */
    static Grafo cadena(double[][] p, int c0, int c1) {
        Grafo g = new Grafo().mezcla(RGB);
        Grafo.Nodo prev = null;
        int n = p.length;
        for (int i = 0; i < n; i++) {
            int col = mezcla(c0, c1, (double) i / (n - 1));
            var nd = g.nodo(p[i][0], p[i][1], p[i][2]).centro(col).radio(volumen(col));
            if (prev != null) {
                final int ca = mezcla(c0, c1, (double) (i - 1) / (n - 1));
                g.arista(prev, nd).espina(u -> mezcla(ca, col, u));
            }
            prev = nd;
        }
        return g;
    }

    static BufferedImage dibuja() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        fondo(im);
        // cola
        Plotter.raster(im).plot(cadena(new double[][] { { CX, 430, 9 }, { CX + 6, 540, 6 }, { CX - 8, 640, 4 }, { CX + 4, 735, 2 } }, 0xFF2C4673, 0xFF1A2B4C));
        // alas (la de la izquierda es el espejo de la de la derecha)
        for (int lado = -1; lado <= 1; lado += 2) {
            double[][] ala = {
                { CX + lado * 30, 300, 62 }, { CX + lado * 150, 255, 46 }, { CX + lado * 270, 285, 30 },
                { CX + lado * 350, 365, 15 }, { CX + lado * 395, 450, 3 } };
            Plotter.raster(im).plot(cadena(ala, LOMO_CLARO, LOMO));
        }
        // cuerpo y cabeza
        Plotter.raster(im).plot(cadena(new double[][] { { CX, 235, 44 }, { CX, 330, 62 }, { CX, 410, 34 } }, LOMO_CLARO, LOMO));
        // lóbulos cefálicos
        for (int lado = -1; lado <= 1; lado += 2) {
            Grafo g = new Grafo().mezcla(RGB);
            var a = g.nodo(CX + lado * 26, 205, 12).centro(LOMO_CLARO).radio(volumen(LOMO_CLARO));
            var b = g.nodo(CX + lado * 40, 150, 6).centro(LOMO_CLARO).radio(volumen(LOMO_CLARO));
            g.arista(a, b).tiradores(new Gota.Vec(lado * 5, -40), new Gota.Vec(lado * 15, -25));
            Plotter.raster(im).plot(g);
        }
        // ojos y manchas dorsales
        for (int lado = -1; lado <= 1; lado += 2) {
            disco(im, CX + lado * 52, 232, 7, 0xFF050A14, 0xFF050A14);
            disco(im, CX + lado * 50.5, 230, 1.8, 0xFFFFFFFF, 0xFFFFFFFF);
        }
        double[][] m = { { 0, 290, 9 }, { 0, 345, 11 }, { 0, 395, 7 }, { 70, 292, 8 }, { 130, 268, 10 }, { 200, 262, 8 },
                         { 105, 312, 6 }, { 165, 292, 7 }, { 255, 288, 6 }, { 315, 335, 4 } };
        for (double[] p : m) {
            disco(im, CX + p[0], p[1], p[2], 0xFFDCE8F5, 0xFF9DB8D8);
            if (p[0] != 0) disco(im, CX - p[0], p[1], p[2], 0xFFDCE8F5, 0xFF9DB8D8);
        }
        return im;
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(dibuja(), "png", new File("manta_by_claude.png"));
        System.out.println("manta_by_claude.png");
    }
}
