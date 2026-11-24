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
 * Lámina libre de Claude: una golondrina en vuelo vista desde arriba. Estrena dos técnicas:
 * <ul>
 * <li><b>Perfil pequeño-grande-pequeño</b> (idea de Samuel): un ala o una aleta es estrecha en la raíz, se ensancha y
 *     vuelve a estrecharse hacia la punta. Una cadena de nodos con el radio grande en el medio lo da sin más (con dos
 *     nodos solo sale base ancha y punta fina, que fue el error recurrente).</li>
 * <li><b>Radios exagerados + {@link Grafo#alisar()}</b>: se ponen los radios sin miedo a los quiebros y es la función
 *     de ayuda la que da continuidad (G2) al camino de la cadena. El programa imprime las aristas {@code plegadas()} de
 *     cada cadena como diagnóstico.</li>
 * </ul>
 * Escribe golondrina_by_claude.png.
 */
public class Golondrina_by_Claude {
    static final int W = 860, H = 760;
    static final double CX = 430;
    static final Mezcla RGB = Mezcla.RGB;

    static int mezcla(int a, int b, double t) { return RGB.mezclar(a, b, Math.max(0, Math.min(1, t))); }

    static final int AZUL = 0xFF1B2E5C, AZUL_CLARO = 0xFF3F66B5, CASTANO = 0xFF9A3B1E, BORDE = 0xFF060B1A;

    static Degradado volumen(int c) { return x -> mezcla(c, BORDE, 0.75 * x * x); }

    static void disco(BufferedImage im, double x, double y, double r, int centro, int borde) {
        Point2D.Double a = new Point2D.Double(x, y), b = new Point2D.Double(x + 0.5, y);
        Plotter.raster(im).plot(Gota.lineal(a, r, b, r, Perfil.recto()), Colores.cuatroColores(centro, centro, borde, borde, Mezcla.RGB));
    }

    static void fondo(BufferedImage im) {
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFF7FB6E0);
        Point2D.Double a = new Point2D.Double(W * 0.5, 240), b = new Point2D.Double(W * 0.5 + 0.5, 240);
        Plotter.raster(im).plot(Gota.lineal(a, 800, b, 800, Perfil.recto()),
            Colores.cuatroColores(0xFFE9F4FB, 0xFFE9F4FB, 0xFF3F86BD, 0xFF3F86BD, Mezcla.RGB));
    }

    /** Cadena de nodos {x, y, radio, k}: el color pasa de c0 a c1 según k (0..1) y se alisa el camino. */
    static Grafo cadena(String nombre, double[][] p, int c0, int c1) {
        Grafo g = new Grafo().mezcla(RGB);
        Grafo.Nodo prev = null;
        int ca = 0;
        for (double[] q : p) {
            int col = mezcla(c0, c1, q[3]);
            var nd = g.nodo(q[0], q[1], q[2]).centro(col).radio(volumen(col));
            if (prev != null) { final int a = ca; g.arista(prev, nd).espina(u -> mezcla(a, col, u)); }
            prev = nd; ca = col;
        }
        g.alisar();
        System.out.println(nombre + ": aristas plegadas = " + g.plegadas().size());
        return g;
    }

    static BufferedImage dibuja() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        fondo(im);
        // cola bifurcada: dos puntas finas desde la base de la cola
        for (int lado = -1; lado <= 1; lado += 2)
            Plotter.raster(im).plot(cadena("cola" + lado, new double[][] {
                { CX + lado * 4, 470, 11, 0 }, { CX + lado * 22, 560, 14, 0.5 }, { CX + lado * 52, 660, 2.5, 1 } }, AZUL, AZUL_CLARO));
        // alas: raíz estrecha, codo muy grueso (radios exagerados), muñeca media y punta fina barrida hacia atrás
        for (int lado = -1; lado <= 1; lado += 2)
            Plotter.raster(im).plot(cadena("ala" + lado, new double[][] {
                { CX + lado * 22, 318, 9, 0 }, { CX + lado * 125, 285, 50, 0.3 }, { CX + lado * 245, 325, 30, 0.6 },
                { CX + lado * 330, 415, 13, 0.85 }, { CX + lado * 380, 520, 2.5, 1 } }, AZUL_CLARO, AZUL));
        // cuerpo: cabeza, cuello, pecho, vientre, base de la cola
        Plotter.raster(im).plot(cadena("cuerpo", new double[][] {
            { CX, 185, 17, 0 }, { CX, 230, 21, 0.2 }, { CX, 305, 27, 0.5 }, { CX, 395, 20, 0.8 }, { CX, 470, 10, 1 } }, AZUL_CLARO, AZUL));
        // garganta castaña y frente
        disco(im, CX, 196, 12, CASTANO, mezcla(CASTANO, BORDE, 0.5));
        // ojos
        for (int lado = -1; lado <= 1; lado += 2) disco(im, CX + lado * 11, 178, 3.2, 0xFF050810, 0xFF050810);
        // pico
        Grafo pico = new Grafo().mezcla(RGB);
        var a = pico.nodo(CX, 168, 6).centro(0xFF2A2018).radio(volumen(0xFF2A2018));
        var b = pico.nodo(CX, 142, 1.5).centro(0xFF2A2018).radio(volumen(0xFF2A2018));
        pico.arista(a, b);
        Plotter.raster(im).plot(pico);
        return im;
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(dibuja(), "png", new File("golondrina_by_claude.png"));
        System.out.println("golondrina_by_claude.png");
    }
}
