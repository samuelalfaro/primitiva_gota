import geometria.composicion.Grafo;
import pintado.Plotter;
import color.Mezcla;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Las minúsculas del abecedario de gotas (continúa Abecedario_by_Claude). Rejilla: ascendentes en y = 0, altura de la x en
 * y = 38, línea base en y = 100 y descendentes hasta y = 134. Cada letra, 10 gotas como máximo; solo cobertura.
 * Las letras con vértices (v, w, x, y, z) no se alisan: {@code alisar()} los redondearía.
 */
public class Minusculas_by_Claude {
    static final int NEGRO = 0xFF000000, BLANCO = 0xFFFFFFFF;

    record Letra(char c, boolean suave, double[][] nodos, int[][] aristas) {}

    static int[][] e(int... p) { int[][] r = new int[p.length / 2][]; for (int i = 0; i < r.length; i++) r[i] = new int[] { p[2 * i], p[2 * i + 1] }; return r; }

    static final Letra[] LETRAS = {
        new Letra('a', true, new double[][] { {40,40,5}, {22,38,5}, {7,69,7}, {22,100,5.5}, {40,72,6}, {42,98,6} }, e(0,1, 1,2, 2,3, 3,4, 0,4, 4,5)),
        new Letra('b', true, new double[][] { {10,4,5.5}, {10,48,6}, {28,38,5}, {46,69,7}, {28,100,5.5}, {10,96,6} }, e(0,1, 1,5, 1,2, 2,3, 3,4, 4,5)),
        new Letra('c', true, new double[][] { {44,46,5}, {30,37,5}, {12,54,7}, {12,84,7}, {30,101,5.5}, {44,92,5} }, e(0,1, 1,2, 2,3, 3,4, 4,5)),
        new Letra('d', true, new double[][] { {40,4,5.5}, {40,48,6}, {22,38,5}, {4,69,7}, {22,100,5.5}, {40,96,6} }, e(0,1, 1,5, 1,2, 2,3, 3,4, 4,5)),
        new Letra('e', true, new double[][] { {6,68,6}, {42,67,5.5}, {40,48,5.5}, {24,37,5}, {7,52,7}, {8,84,7}, {26,101,5.5}, {42,92,5} }, e(0,1, 1,2, 2,3, 3,4, 4,0, 0,5, 5,6, 6,7)),
        new Letra('f', true, new double[][] { {40,12,5}, {28,3,5.5}, {16,14,6}, {16,40,6}, {16,98,6}, {2,40,4.5}, {32,40,4.5} }, e(0,1, 1,2, 2,3, 3,4, 5,3, 3,6)),
        new Letra('g', true, new double[][] { {40,42,5}, {22,38,5}, {6,60,7}, {22,82,6}, {40,62,6}, {40,100,6}, {30,126,6}, {10,132,5.5}, {4,122,5} }, e(0,1, 1,2, 2,3, 3,4, 0,4, 4,5, 5,6, 6,7, 7,8)),
        new Letra('h', true, new double[][] { {8,4,5.5}, {8,50,6}, {8,98,6}, {24,38,5}, {42,56,6.5}, {42,98,6} }, e(0,1, 1,2, 1,3, 3,4, 4,5)),
        new Letra('i', true, new double[][] { {24,42,5}, {24,98,6}, {24,14,5.5}, {24,15,5.5} }, e(0,1, 2,3)),
        new Letra('j', true, new double[][] { {30,42,5}, {30,108,6}, {24,128,6}, {8,134,5.5}, {2,124,5}, {30,14,5.5}, {30,15,5.5} }, e(0,1, 1,2, 2,3, 3,4, 5,6)),
        new Letra('k', true, new double[][] { {8,4,5.5}, {8,70,6}, {8,98,6}, {40,40,5}, {42,98,6}, {22,56,5} }, e(0,1, 1,2, 3,5, 5,1, 5,4)),
        new Letra('l', true, new double[][] { {24,4,5.5}, {24,60,5.5}, {26,92,6}, {38,100,5} }, e(0,1, 1,2, 2,3)),
        new Letra('m', true, new double[][] { {6,42,5}, {6,62,6}, {6,98,6}, {22,38,5}, {38,54,6.5}, {38,66,6}, {38,98,6}, {54,38,5}, {70,54,6.5}, {70,98,6} }, e(0,1, 1,2, 1,3, 3,4, 4,5, 5,6, 5,7, 7,8, 8,9)),
        new Letra('n', true, new double[][] { {8,42,5}, {8,62,6}, {8,98,6}, {24,38,5}, {42,54,6.5}, {42,98,6} }, e(0,1, 1,2, 1,3, 3,4, 4,5)),
        new Letra('o', true, new double[][] { {25,37,5}, {44,56,7}, {44,82,7}, {25,101,5}, {6,82,7}, {6,56,7} }, e(0,1, 1,2, 2,3, 3,4, 4,5, 5,0)),
        new Letra('p', true, new double[][] { {10,40,5.5}, {10,48,6}, {10,92,6}, {10,134,5.5}, {28,38,5}, {46,68,7}, {28,100,5.5} }, e(0,1, 1,2, 2,3, 1,4, 4,5, 5,6, 6,2)),
        new Letra('q', true, new double[][] { {40,40,5.5}, {40,48,6}, {40,92,6}, {40,134,5.5}, {22,38,5}, {4,68,7}, {22,100,5.5} }, e(0,1, 1,2, 2,3, 1,4, 4,5, 5,6, 6,2)),
        new Letra('r', true, new double[][] { {8,42,5}, {8,62,6}, {8,98,6}, {22,40,5}, {40,38,4.5} }, e(0,1, 1,2, 1,3, 3,4)),
        new Letra('s', true, new double[][] { {38,46,5}, {26,37,5}, {10,41,5.5}, {7,55,6}, {22,68,5}, {36,78,5.5}, {41,90,6}, {28,101,5.5}, {10,98,5.5}, {4,90,5} }, e(0,1, 1,2, 2,3, 3,4, 4,5, 5,6, 6,7, 7,8, 8,9)),
        new Letra('t', true, new double[][] { {20,14,5.5}, {20,40,6}, {20,88,6}, {28,100,5.5}, {40,97,5}, {4,40,4.5}, {36,40,4.5} }, e(0,1, 1,2, 2,3, 3,4, 5,1, 1,6)),
        new Letra('u', true, new double[][] { {8,40,5}, {8,72,6.5}, {24,101,5.5}, {42,86,6}, {42,40,5}, {42,98,6} }, e(0,1, 1,2, 2,3, 4,3, 3,5)),
        new Letra('v', false, new double[][] { {4,40,5.5}, {25,100,5}, {25,100,5}, {46,40,5.5} }, e(0,1, 2,3)),
        new Letra('w', false, new double[][] { {3,40,5}, {15,100,5}, {15,100,5}, {27,52,5}, {27,52,5}, {39,100,5}, {39,100,5}, {51,40,5} }, e(0,1, 2,3, 4,5, 6,7)),
        new Letra('x', true, new double[][] { {6,40,5.5}, {44,100,5.5}, {44,40,5.5}, {6,100,5.5}, {25,70,4.5} }, e(0,4, 4,1, 2,4, 4,3)),
        new Letra('y', true, new double[][] { {44,40,5.5}, {30,84,5.5}, {12,134,5}, {5,40,5.5} }, e(0,1, 1,2, 3,1)),
        new Letra('z', false, new double[][] { {6,40,5}, {44,40,5}, {44,40,5}, {6,100,5}, {6,100,5}, {44,100,5} }, e(0,1, 2,3, 4,5)),
    };

    static Grafo grafo(Letra l, double ox, double oy, double k) {
        Grafo g = new Grafo().mezcla(Mezcla.RGB);
        List<Grafo.Nodo> ns = new ArrayList<>();
        for (double[] n : l.nodos()) ns.add(g.nodo(ox + n[0] * k, oy + n[1] * k, n[2] * k).color(NEGRO));
        for (int[] a : l.aristas()) g.arista(ns.get(a[0]), ns.get(a[1]));
        if (l.suave()) g.relajar().alisar();
        return g;
    }

    public static void main(String[] args) throws Exception {
        double k = 2.0, cw = 75 * k, ch = 150 * k;
        int cols = 9, filas = 3;
        BufferedImage im = new BufferedImage((int) (cols * cw + 40), (int) (filas * ch + 40), BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), BLANCO);
        StringBuilder plegadas = new StringBuilder();
        for (int i = 0; i < LETRAS.length; i++) {
            Letra l = LETRAS[i];
            Grafo g = grafo(l, 20 + (i % cols) * cw + 10, 20 + (i / cols) * ch + 10, k);
            int n = g.plegadas().size();
            if (n > 0) plegadas.append(l.c()).append('=').append(n).append(' ');
            Plotter.raster(im).plot(g);
        }
        System.out.println("plegadas: " + (plegadas.length() == 0 ? "ninguna" : plegadas));
        ImageIO.write(im, "png", new File("minusculas_by_claude.png"));
    }
}
