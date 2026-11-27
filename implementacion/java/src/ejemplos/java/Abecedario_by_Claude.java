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
 * Experimento: un abecedario de gotas, sin fuentes del sistema. Cada letra es un grafo pequeño (10 gotas como máximo) de
 * nodos {x, y, radio} en una rejilla de 60 x 100 (y hacia abajo, línea base en 100); la expresividad sale del radio de
 * cada nodo (trazo grueso, fino, rematado), no de añadir gotas. Solo cobertura: negro sobre blanco.
 * <p>
 * Datos de una letra: nodos, aristas (pares de índices; un índice repetido al final cierra) y si se relaja/alisa.
 * Escribe abecedario_by_claude.png y escribe por consola las aristas plegadas de cada letra.
 */
public class Abecedario_by_Claude {
    static final int NEGRO = 0xFF000000, BLANCO = 0xFFFFFFFF;

    record Letra(char c, double[][] nodos, int[][] aristas) {}

    static final Letra I = new Letra('I',
        new double[][] { {30, 6, 7}, {30, 50, 5.5}, {30, 94, 7} },
        new int[][] { {0, 1}, {1, 2} });

    static final Letra O = new Letra('O',
        new double[][] { {30, 6, 5}, {54, 28, 8.5}, {54, 72, 8.5}, {30, 94, 5}, {6, 72, 8.5}, {6, 28, 8.5} },
        new int[][] { {0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 0} });

    static final Letra S = new Letra('S',
        new double[][] { {52, 16, 6}, {38, 5, 5.5}, {18, 9, 6.5}, {10, 26, 7}, {24, 44, 6},
                         {42, 56, 6}, {51, 74, 7}, {42, 92, 6}, {22, 96, 5.5}, {8, 86, 6} },
        new int[][] { {0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}, {6, 7}, {7, 8}, {8, 9} });

    static final Letra A = new Letra('A',
        new double[][] { {6, 96, 7}, {14, 66, 5.5}, {30, 6, 4}, {46, 66, 5.5}, {54, 96, 7}, {30, 66, 4} },
        new int[][] { {0, 1}, {1, 2}, {2, 3}, {3, 4}, {1, 5}, {5, 3} });

    static final Letra OCHO = new Letra('8',
        new double[][] { {30, 50, 5}, {48, 34, 7}, {30, 5, 5.5}, {12, 34, 7}, {10, 70, 7.5}, {30, 95, 5.5}, {50, 70, 7.5} },
        new int[][] { {0, 1}, {1, 2}, {2, 3}, {3, 0}, {0, 4}, {4, 5}, {5, 6}, {6, 0} });

    /** El grafo de la letra, con origen (ox, oy) y escala k. */
    static Grafo grafo(Letra l, double ox, double oy, double k, boolean suave) {
        Grafo g = new Grafo().mezcla(Mezcla.RGB);
        List<Grafo.Nodo> ns = new ArrayList<>();
        for (double[] n : l.nodos()) ns.add(g.nodo(ox + n[0] * k, oy + n[1] * k, n[2] * k).color(NEGRO));
        for (int[] e : l.aristas()) g.arista(ns.get(e[0]), ns.get(e[1]));
        if (suave) g.relajar().alisar();
        return g;
    }

    public static void main(String[] args) throws Exception {
        Letra[] letras = { I, O, S, A, OCHO };
        double k = 3.2;
        int W = (int) (letras.length * (60 * k + 50) + 50), H = (int) (100 * k + 100);
        for (boolean suave : new boolean[] { false, true }) {
            BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), BLANCO);
            for (int i = 0; i < letras.length; i++) {
                Grafo g = grafo(letras[i], 50 + i * (60 * k + 50), 50, k, suave);
                System.out.println((suave ? "suave " : "recto ") + letras[i].c() + ": plegadas = " + g.plegadas().size() + ", rigidas = " + g.rigidas().size());
                Plotter.raster(im).plot(g);
            }
            ImageIO.write(im, "png", new File(suave ? "abecedario_suave.png" : "abecedario_recto.png"));
        }
    }
}
