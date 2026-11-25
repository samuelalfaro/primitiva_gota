import geometria.Geometria;
import geometria.Grupo;
import geometria.composicion.Grafo;
import geometria.composicion.Pintada;
import forma.Gota;
import forma.Perfil;
import color.Colores;
import java.awt.geom.Point2D;
import pintado.Plotter;
import color.Mezcla;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Dígitos y mayúsculas del abecedario de gotas (continúa Abecedario_by_Claude y Minusculas_by_Claude). Rejilla de 56 x 100
 * (y hacia abajo, línea base en 100), 10 gotas como máximo por carácter, solo cobertura. Los vértices vivos (A, K, L, M, N,
 * V, W, Z, 4, 5, 7...) se hacen con nodos coincidentes, de modo que {@code alisar()} no los redondea.
 */
public class Mayusculas_by_Claude {
    static final int NEGRO = 0xFF000000, BLANCO = 0xFFFFFFFF;

    record Letra(char c, double[][] nodos, int[][] aristas) {}

    static int[][] e(int... p) { int[][] r = new int[p.length / 2][]; for (int i = 0; i < r.length; i++) r[i] = new int[] { p[2 * i], p[2 * i + 1] }; return r; }
    static double[][] n(double... p) { double[][] r = new double[p.length / 3][]; for (int i = 0; i < r.length; i++) r[i] = new double[] { p[3 * i], p[3 * i + 1], p[3 * i + 2] }; return r; }

    static final Letra[] LETRAS = {
        new Letra('0', n(28,5,5.5, 50,28,8, 50,72,8, 28,95,5.5, 6,72,8, 6,28,8), e(0,1, 1,2, 2,3, 3,4, 4,5, 5,0)),
        new Letra('1', n(10,28,5, 30,6,5.5, 30,50,6, 30,94,6.5), e(0,1, 1,2, 2,3)),
        new Letra('2', n(6,26,5.5, 18,8,6, 36,6,6, 50,24,7, 44,46,6, 6,94,6.5, 6,94,6.5, 50,94,6), e(0,1, 1,2, 2,3, 3,4, 4,5, 6,7)),
        new Letra('3', n(6,20,5.5, 24,5,6, 44,20,7, 38,42,6, 20,50,5.5, 42,60,7, 50,76,7.5, 38,94,6, 18,96,6, 4,82,5.5), e(0,1, 1,2, 2,3, 3,4, 4,5, 5,6, 6,7, 7,8, 8,9)),
        new Letra('4', n(38,6,6, 38,70,6.5, 38,96,6.5, 4,70,5.5, 4,70,5.5, 54,70,5.5), e(0,1, 1,2, 0,3, 4,1, 1,5)),
        new Letra('5', n(46,6,5.5, 12,6,5.5, 12,6,5.5, 10,44,6, 26,38,6, 46,56,7.5, 48,76,7.5, 32,95,6, 10,92,6), e(0,1, 2,3, 3,4, 4,5, 5,6, 6,7, 7,8)),
        new Letra('6', n(46,14,5, 28,4,6, 10,30,7, 6,68,7.5, 26,46,5.5, 50,70,8, 28,96,5.5), e(0,1, 1,2, 2,3, 3,4, 4,5, 5,6, 6,3)),
        new Letra('7', n(6,8,5.5, 50,8,5.5, 50,8,5.5, 36,52,6, 20,96,6.5), e(0,1, 2,3, 3,4)),
        new Letra('8', n(28,50,5, 46,34,7, 28,5,5.5, 10,34,7, 8,70,7.5, 28,95,5.5, 48,70,7.5), e(0,1, 1,2, 2,3, 3,0, 0,4, 4,5, 5,6, 6,0)),
        new Letra('9', n(10,86,5, 28,96,6, 46,70,7, 50,34,7.5, 30,54,5.5, 6,30,8, 28,4,5.5), e(0,1, 1,2, 2,3, 3,4, 4,5, 5,6, 6,3)),
        new Letra('A', n(4,96,7, 14,66,5.5, 28,6,4.5, 42,66,5.5, 52,96,7, 28,66,4.5), e(0,1, 1,2, 2,3, 3,4, 1,5, 5,3)),
        new Letra('B', n(10,6,6, 10,50,6, 10,94,6, 28,6,5.5, 44,24,7.5, 26,50,5.5, 28,52,5.5, 50,74,8.5, 28,94,5.5), e(0,1, 1,2, 0,3, 3,4, 4,5, 5,1, 1,6, 6,7, 7,8, 8,2)),
        new Letra('C', n(48,22,5.5, 30,5,6, 10,26,7.5, 6,50,8, 10,74,7.5, 30,95,6, 48,78,5.5), e(0,1, 1,2, 2,3, 3,4, 4,5, 5,6)),
        new Letra('D', n(10,6,6, 10,50,6, 10,94,6, 34,10,6, 50,38,8, 50,66,8, 34,92,6), e(0,1, 1,2, 0,3, 3,4, 4,5, 5,6, 6,2)),
        new Letra('E', n(10,6,6, 10,50,6, 10,94,6, 48,6,5.5, 38,50,5.5, 48,94,5.5), e(0,1, 1,2, 0,3, 1,4, 2,5)),
        new Letra('F', n(10,6,6, 10,50,6, 10,94,6, 48,6,5.5, 38,50,5.5), e(0,1, 1,2, 0,3, 1,4)),
        new Letra('G', n(48,22,5.5, 30,5,6, 10,26,7.5, 6,50,8, 10,74,7.5, 30,95,6, 48,80,6, 48,56,5.5, 30,56,5), e(0,1, 1,2, 2,3, 3,4, 4,5, 5,6, 6,7, 7,8)),
        new Letra('H', n(8,6,6, 8,50,6, 8,94,6, 48,6,6, 48,50,6, 48,94,6), e(0,1, 1,2, 3,4, 4,5, 1,4)),
        new Letra('I', n(28,6,6, 28,50,5.5, 28,94,6), e(0,1, 1,2)),
        new Letra('J', n(36,6,6, 36,70,6.5, 30,90,6.5, 14,96,6, 4,84,5.5), e(0,1, 1,2, 2,3, 3,4)),
        new Letra('K', n(8,6,6, 8,54,6, 8,94,6, 46,6,5.5, 26,34,5, 48,94,6), e(0,1, 1,2, 3,4, 4,1, 4,5)),
        new Letra('L', n(8,6,6, 8,94,6, 8,94,6, 48,94,6), e(0,1, 2,3)),
        new Letra('M', n(6,94,6, 6,6,6, 6,6,6, 28,62,6, 28,62,6, 50,6,6, 50,6,6, 50,94,6), e(0,1, 2,3, 4,5, 6,7)),
        new Letra('N', n(6,94,6, 6,6,6, 6,6,6, 50,94,6, 50,94,6, 50,6,6), e(0,1, 2,3, 4,5)),
        new Letra('O', n(28,5,5, 52,28,8.5, 52,72,8.5, 28,95,5, 4,72,8.5, 4,28,8.5), e(0,1, 1,2, 2,3, 3,4, 4,5, 5,0)),
        new Letra('P', n(10,6,6, 10,50,6, 10,94,6, 32,6,5.5, 48,26,7.5, 30,48,5.5), e(0,1, 1,2, 0,3, 3,4, 4,5, 5,1)),
        new Letra('Q', n(28,5,5, 52,28,8.5, 52,72,8.5, 28,95,5, 4,72,8.5, 4,28,8.5, 36,76,5, 54,98,5.5), e(0,1, 1,2, 2,3, 3,4, 4,5, 5,0, 6,7)),
        new Letra('R', n(10,6,6, 10,50,6, 10,94,6, 32,6,5.5, 48,26,7.5, 30,48,5.5, 50,94,6.5), e(0,1, 1,2, 0,3, 3,4, 4,5, 5,1, 5,6)),
        new Letra('S', n(52,16,6, 38,5,5.5, 18,9,6.5, 10,26,7, 24,44,6, 42,56,6, 51,74,7, 42,92,6, 22,96,5.5, 8,86,6), e(0,1, 1,2, 2,3, 3,4, 4,5, 5,6, 6,7, 7,8, 8,9)),
        new Letra('T', n(4,6,5.5, 28,6,5.5, 52,6,5.5, 28,50,6, 28,94,6.5), e(0,1, 1,2, 1,3, 3,4)),
        new Letra('U', n(8,6,6, 8,70,7, 28,95,6, 48,70,7, 48,6,6), e(0,1, 1,2, 2,3, 3,4)),
        new Letra('V', n(4,6,6, 28,96,5, 28,96,5, 52,6,6), e(0,1, 2,3)),
        new Letra('W', n(2,6,6, 14,96,5, 14,96,5, 28,36,5, 28,36,5, 42,96,5, 42,96,5, 54,6,6), e(0,1, 2,3, 4,5, 6,7)),
        new Letra('X', n(6,6,6, 50,94,6, 50,6,6, 6,94,6, 28,50,5), e(0,4, 4,1, 2,4, 4,3)),
        new Letra('Y', n(6,6,6, 28,50,5.5, 50,6,6, 28,94,6.5), e(0,1, 2,1, 1,3)),
        new Letra('Z', n(6,6,6, 50,6,6, 50,6,6, 6,94,6, 6,94,6, 50,94,6), e(0,1, 2,3, 4,5)),
    };

    static Grafo grafo(Letra l, double ox, double oy, double k) {
        Grafo g = new Grafo().mezcla(Mezcla.RGB);
        List<Grafo.Nodo> ns = new ArrayList<>();
        for (double[] nd : l.nodos()) ns.add(g.nodo(ox + nd[0] * k, oy + nd[1] * k, nd[2] * k).color(NEGRO));
        for (int[] a : l.aristas()) g.arista(ns.get(a[0]), ns.get(a[1]));
        return g.relajar().alisar();
    }

    /**
     * La geometría del carácter. El cero lleva la barra oblicua de «cero cortado» (para distinguirlo de la O): una gota
     * simple, sin grafo, pintada de negro y agrupada con el óvalo en un {@link Grupo}. Así el grupo une una geometría de
     * grafo y una de gota suelta.
     */
    static Geometria geometria(Letra l, double ox, double oy, double k) {
        Grafo g = grafo(l, ox, oy, k);
        if (l.c() != '0') return g;
        Gota barra = Gota.lineal(new Point2D.Double(ox + 14 * k, oy + 82 * k), 4.5 * k,
                                 new Point2D.Double(ox + 42 * k, oy + 18 * k), 4.5 * k, Perfil.recto());
        return new Grupo(g, Pintada.de(barra, Colores.dosColores(NEGRO, NEGRO))).label("cero");
    }

    public static void main(String[] args) throws Exception {
        double k = 1.8, cw = 70 * k, ch = 120 * k;
        int cols = 12, filas = 3;
        BufferedImage im = new BufferedImage((int) (cols * cw + 40), (int) (filas * ch + 40), BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), BLANCO);
        StringBuilder plegadas = new StringBuilder();
        for (int i = 0; i < LETRAS.length; i++) {
            Letra l = LETRAS[i];
            Grafo g = grafo(l, 20 + (i % cols) * cw + 10, 20 + (i / cols) * ch + 10, k);
            int m = g.plegadas().size();
            if (m > 0) plegadas.append(l.c()).append('=').append(m).append(' ');
            Plotter.raster(im).plot(geometria(l, 20 + (i % cols) * cw + 10, 20 + (i / cols) * ch + 10, k));
        }
        System.out.println("plegadas: " + (plegadas.length() == 0 ? "ninguna" : plegadas));
        ImageIO.write(im, "png", new File("mayusculas_by_claude.png"));
    }
}
