import geometria.composicion.Grafo;
import forma.Gota;
import pintado.Plotter;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Experimento: un abecedario dibujado solo con la primitiva Gota, en negro sobre blanco.
 * Cada glifo son datos (nodos con su radio mas aristas; menos de 12 gotas), la expresividad sale
 * del reparto de los radios y del relajar() y alisar() del Grafo. Escribe abecedario.png y, con
 * parametro "detalle", abecedario_detalle.png con unos glifos a mayore rama.
 */
public class Abecedario {

    static final int NEGRA = 0xFF000000;
    static final int ANCHO_CELDA = 150, ALTO_CELDA = 190;
    static final int COLS = 6;
    static final int ESCALA = 3;

    record N(double x, double y, double r) {}
    record A(int a, int b, boolean recta) {}
    record Glifo(double ancho, N[] nodos, A[] aristas) {}

    static Glifo glifo(double ancho, double[][] ns, int[][] as) {
        N[] nodos = new N[ns.length];
        for (int i = 0; i < ns.length; i++)
            nodos[i] = new N(ns[i][0], ns[i][1], ns[i].length > 2 ? ns[i][2] : 5);
        A[] aristas = new A[as.length];
        for (int i = 0; i < as.length; i++)
            aristas[i] = new A(as[i][0], as[i][1], as[i].length > 2 && as[i][2] != 0);
        return new Glifo(ancho, nodos, aristas);
    }

    static final Map<String, Glifo> GLIFOS = new LinkedHashMap<>();

    static void letras() {
        GLIFOS.put("A", glifo(84,
                new double[][] { { 42, 4, 6 }, { 0, 100, 4.5 }, { 84, 100, 4.5 }, { 10.5, 76, 6 }, { 73.5, 76, 6 } },
                new int[][] { { 0, 3 }, { 0, 4 }, { 3, 1 }, { 4, 2 }, { 3, 4 } }));
        GLIFOS.put("B", glifo(76,
                new double[][] { { 10, 0, 5 }, { 10, 52, 6 }, { 10, 100, 5 }, { 58, 3, 5 }, { 68, 26, 5 },
                                 { 68, 56, 5 }, { 58, 94, 5 } },
                new int[][] { { 0, 1, 1 }, { 1, 2, 1 }, { 0, 3 }, { 3, 4 }, { 4, 1 }, { 1, 5 }, { 5, 6 }, { 6, 2 } }));
        GLIFOS.put("C", glifo(80,
                new double[][] { { 71, 12, 4.5 }, { 16, 16, 5 }, { 2, 50, 5.5 }, { 16, 84, 5 }, { 68, 88, 4.5 } },
                new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 } }));
        GLIFOS.put("D", glifo(80,
                new double[][] { { 10, 0, 5 }, { 10, 50, 5.5 }, { 10, 100, 5 }, { 56, 4, 5 }, { 70, 50, 5.5 },
                                 { 56, 96, 5 } },
                new int[][] { { 0, 1, 1 }, { 1, 2, 1 }, { 0, 3 }, { 3, 4 }, { 4, 5 }, { 5, 2 } }));
        GLIFOS.put("E", glifo(76,
                new double[][] { { 10, 0, 5 }, { 10, 50, 5.5 }, { 10, 100, 5 }, { 58, 0, 4.5 }, { 46, 50, 4.5 },
                                 { 62, 100, 4.5 } },
                new int[][] { { 0, 1, 1 }, { 1, 2, 1 }, { 0, 3, 1 }, { 1, 4, 1 }, { 2, 5, 1 } }));
        GLIFOS.put("F", glifo(72,
                new double[][] { { 10, 0, 5 }, { 10, 50, 5.5 }, { 10, 100, 5 }, { 56, 0, 4.5 }, { 44, 50, 4.5 } },
                new int[][] { { 0, 1, 1 }, { 1, 2, 1 }, { 0, 3, 1 }, { 1, 4, 1 } }));
        GLIFOS.put("G", glifo(84,
                new double[][] { { 71, 10, 4.5 }, { 18, 15, 5 }, { 2, 50, 5.5 }, { 16, 83, 5 }, { 62, 95, 5 },
                                 { 80, 56, 5.5 }, { 42, 54, 5 } },
                new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 }, { 4, 5 }, { 5, 6 } }));
        GLIFOS.put("H", glifo(80,
                new double[][] { { 10, 0, 5 }, { 10, 50, 5.5 }, { 10, 100, 5 }, { 70, 0, 5 }, { 70, 50, 5.5 },
                                 { 70, 100, 5 } },
                new int[][] { { 0, 1, 1 }, { 1, 2, 1 }, { 3, 4, 1 }, { 4, 5, 1 }, { 1, 4, 1 } }));
        GLIFOS.put("I", glifo(20,
                new double[][] { { 10, 0, 5 }, { 10, 50, 5.5 }, { 10, 100, 4.5 } },
                new int[][] { { 0, 1, 1 }, { 1, 2, 1 } }));
        GLIFOS.put("J", glifo(62,
                new double[][] { { 42, 0, 4.5 }, { 42, 52, 5 }, { 38, 84, 5 }, { 24, 98, 5.5 }, { 8, 88, 4.5 } },
                new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 } }));
        GLIFOS.put("K", glifo(78,
                new double[][] { { 10, 0, 5 }, { 10, 52, 6 }, { 10, 100, 5 }, { 66, 0, 4.5 }, { 72, 100, 4.5 } },
                new int[][] { { 0, 1, 1 }, { 1, 2, 1 }, { 1, 3 }, { 1, 4 } }));
        GLIFOS.put("L", glifo(70,
                new double[][] { { 10, 0, 5 }, { 10, 100, 5.5 }, { 64, 100, 4.5 } },
                new int[][] { { 0, 1, 1 }, { 1, 2, 1 } }));
        GLIFOS.put("M", glifo(110,
                new double[][] { { 0, 100, 4.5 }, { 0, 0, 5 }, { 55, 68, 5.5 }, { 110, 0, 5 }, { 110, 100, 4.5 } },
                new int[][] { { 0, 1, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4, 1 } }));
        GLIFOS.put("N", glifo(82,
                new double[][] { { 0, 100, 4.5 }, { 0, 0, 5 }, { 82, 100, 5.5 }, { 82, 0, 5 } },
                new int[][] { { 0, 1, 1 }, { 1, 2 }, { 2, 3, 1 } }));
        GLIFOS.put("O", glifo(88,
                new double[][] { { 88, 50, 5.0 }, { 66, 93, 4.8 }, { 22, 93, 5.4 }, { 0, 50, 5.6 }, { 22, 7, 5.4 },
                                 { 66, 7, 4.8 } },
                new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 }, { 4, 5 }, { 5, 0 } }));
        GLIFOS.put("P", glifo(76,
                new double[][] { { 10, 0, 5 }, { 10, 58, 6 }, { 10, 100, 4.5 }, { 58, 4, 5 }, { 62, 30, 5.5 },
                                 { 48, 55, 5 } },
                new int[][] { { 0, 1, 1 }, { 1, 2, 1 }, { 0, 3 }, { 3, 4 }, { 4, 5 }, { 5, 1 } }));
        GLIFOS.put("Q", glifo(88,
                new double[][] { { 88, 50, 5.0 }, { 66, 93, 5.5 }, { 22, 93, 5.4 }, { 0, 50, 5.6 }, { 22, 7, 5.4 },
                                 { 66, 7, 4.8 }, { 86, 118, 4.5 } },
                new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 }, { 4, 5 }, { 5, 0 }, { 1, 6 } }));
        GLIFOS.put("R", glifo(78,
                new double[][] { { 10, 0, 5 }, { 10, 56, 6 }, { 10, 100, 4.5 }, { 56, 4, 5 }, { 60, 28, 5.5 },
                                 { 44, 53, 5 }, { 70, 100, 4.5 } },
                new int[][] { { 0, 1, 1 }, { 1, 2, 1 }, { 0, 3 }, { 3, 4 }, { 4, 5 }, { 5, 1 }, { 1, 6 } }));
        GLIFOS.put("S", glifo(80,
                new double[][] { { 72, 4, 4.5 }, { 20, 0, 5 }, { 2, 26, 5.5 }, { 46, 50, 5 }, { 78, 72, 5.5 },
                                 { 60, 98, 5 }, { 8, 80, 4.5 } },
                new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 }, { 4, 5 }, { 5, 6 } }));
        GLIFOS.put("T", glifo(78,
                new double[][] { { 6, 0, 4.5 }, { 39, 0, 5 }, { 72, 0, 4.5 }, { 39, 100, 5.5 } },
                new int[][] { { 0, 1, 1 }, { 1, 2, 1 }, { 1, 3, 1 } }));
        GLIFOS.put("U", glifo(82,
                new double[][] { { 8, 0, 4.5 }, { 8, 40, 5 }, { 18, 82, 5.5 }, { 41, 100, 5 }, { 64, 82, 5.5 },
                                 { 74, 40, 5 }, { 74, 0, 4.5 } },
                new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 }, { 4, 5 }, { 5, 6 } }));
        GLIFOS.put("V", glifo(82,
                new double[][] { { 0, 0, 4.5 }, { 41, 100, 5.5 }, { 82, 0, 4.5 } },
                new int[][] { { 0, 1 }, { 1, 2 } }));
        GLIFOS.put("W", glifo(110,
                new double[][] { { 0, 0, 4.5 }, { 26, 100, 5 }, { 55, 28, 5.5 }, { 84, 100, 5 }, { 110, 0, 4.5 } },
                new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 } }));
        GLIFOS.put("X", glifo(82,
                new double[][] { { 0, 0, 4.5 }, { 82, 100, 4.5 }, { 82, 0, 4.5 }, { 0, 100, 4.5 }, { 41, 50, 5.5 } },
                new int[][] { { 0, 4 }, { 4, 1 }, { 2, 4 }, { 4, 3 } }));
        GLIFOS.put("Y", glifo(80,
                new double[][] { { 0, 0, 4.5 }, { 80, 0, 4.5 }, { 40, 42, 6 }, { 40, 100, 5.5 } },
                new int[][] { { 0, 2 }, { 1, 2 }, { 2, 3 } }));
        GLIFOS.put("Z", glifo(80,
                new double[][] { { 2, 0, 5 }, { 78, 0, 5 }, { 2, 100, 5 }, { 78, 100, 4.5 } },
                new int[][] { { 0, 1, 1 }, { 1, 2 }, { 2, 3, 1 } }));
    }

    static void digitos() {
        GLIFOS.put("0", glifo(78,
                new double[][] { { 78, 50, 5.0 }, { 58.5, 93, 5.2 }, { 19.5, 93, 5.6 }, { 0, 50, 5.6 }, { 19.5, 7, 5.4 },
                                 { 58.5, 7, 4.8 } },
                new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 }, { 4, 5 }, { 5, 0 } }));
        GLIFOS.put("1", glifo(42,
                new double[][] { { 10, 18, 4.5 }, { 30, 0, 5 }, { 30, 50, 5.5 }, { 30, 100, 4.5 } },
                new int[][] { { 0, 1 }, { 1, 2, 1 }, { 2, 3, 1 } }));
        GLIFOS.put("2", glifo(82,
                new double[][] { { 4, 26, 4.5 }, { 26, 0, 5 }, { 66, 6, 5.5 }, { 80, 36, 4.5 }, { 2, 100, 5 },
                                 { 80, 100, 4.5 } },
                new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 }, { 4, 5, 1 } }));
        GLIFOS.put("3", glifo(80,
                new double[][] { { 8, 6, 4.5 }, { 50, 0, 5 }, { 78, 16, 5.5 }, { 42, 48, 5 }, { 80, 70, 5.5 },
                                 { 58, 99, 5 }, { 2, 76, 4.5 } },
                new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 }, { 4, 5 }, { 5, 6 } }));
        GLIFOS.put("4", glifo(84,
                new double[][] { { 58, 0, 5 }, { 0, 62, 4.5 }, { 84, 62, 5 }, { 58, 100, 4.5 } },
                new int[][] { { 0, 1 }, { 1, 2, 1 }, { 0, 3, 1 } }));
        GLIFOS.put("5", glifo(78,
                new double[][] { { 74, 2, 4.5 }, { 10, 2, 5 }, { 12, 38, 5.5 }, { 58, 42, 5 }, { 78, 68, 5.5 },
                                 { 46, 99, 5 }, { 4, 78, 4.5 } },
                new int[][] { { 0, 1, 1 }, { 1, 2, 1 }, { 2, 3 }, { 3, 4 }, { 4, 5 }, { 5, 6 } }));
        GLIFOS.put("6", glifo(80,
                new double[][] { { 62, 0, 4.5 }, { 36, 12, 5 }, { 10, 54, 5.5 }, { 18, 86, 5 }, { 50, 100, 5 },
                                 { 78, 88, 5.5 }, { 86, 60, 5 }, { 66, 42, 5 } },
                new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 }, { 4, 5 }, { 5, 6 }, { 6, 7 }, { 7, 2 } }));
        GLIFOS.put("7", glifo(82,
                new double[][] { { 2, 0, 4.5 }, { 78, 0, 5 }, { 40, 100, 4.5 } },
                new int[][] { { 0, 1, 1 }, { 1, 2 } }));
        GLIFOS.put("8", glifo(84,
                new double[][] { { 42, 50, 5.5 }, { 26, 36, 5 }, { 24, 8, 5 }, { 44, 1, 5 }, { 62, 6, 5 }, { 62, 34, 5 },
                                 { 18, 70, 5 }, { 26, 98, 5 }, { 56, 99, 5 }, { 70, 70, 5 } },
                new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 }, { 4, 5 }, { 5, 0 }, { 0, 6 }, { 6, 7 }, { 7, 8 },
                              { 8, 9 }, { 9, 0 } }));
        GLIFOS.put("9", glifo(78,
                new double[][] { { 72, 46, 5.5 }, { 64, 8, 5 }, { 20, 4, 5 }, { 4, 34, 5.5 }, { 22, 58, 5 },
                                 { 70, 100, 4.5 } },
                new int[][] { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 }, { 4, 0 }, { 0, 5 } }));
    }

    static Grafo construye(Glifo g, double dx, double dy) {
        Grafo c = new Grafo();
        Grafo.Nodo[] n = new Grafo.Nodo[g.nodos().length];
        for (int i = 0; i < n.length; i++)
            n[i] = c.nodo(dx + g.nodos()[i].x(), dy + g.nodos()[i].y(), g.nodos()[i].r()).color(NEGRA);
        for (A a : g.aristas()) {
            Grafo.Arista e = c.arista(n[a.a()], n[a.b()]);
            if (a.recta()) {
                N na = g.nodos()[a.a()], nb = g.nodos()[a.b()];
                e.tiradores(new Gota.Vec((nb.x() - na.x()) / 3, (nb.y() - na.y()) / 3),
                            new Gota.Vec((na.x() - nb.x()) / 3, (na.y() - nb.y()) / 3));
            }
        }
        return c.relajar().alisar();
    }

    static BufferedImage hoja(int ancho, int alto) {
        BufferedImage im = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFFFFFFFF);
        return im;
    }

    public static void main(String[] args) throws Exception {
        letras();
        digitos();

        boolean detalle = args.length > 0 && args[0].equals("detalle");
        if (detalle) {
            String[] muestra = args.length > 1
                    ? java.util.Arrays.copyOfRange(args, 1, args.length)
                    : new String[] { "R", "8", "S", "B" };
            int w = muestra.length * (ANCHO_CELDA * ESCALA + 40) + 40;
            BufferedImage im = hoja(w, ALTO_CELDA * ESCALA + 80);
            Plotter p = Plotter.raster(im);
            int x = 40;
            for (String s : muestra) {
                Glifo g = GLIFOS.get(s);
                p.plot(construye(g, x + 5 * ESCALA, 40));
                x += ANCHO_CELDA * ESCALA + 40;
            }
            ImageIO.write(im, "png", new File("abecedario_detalle.png"));
            System.out.println("abecedario_detalle.png");
            return;
        }

        int filas = (GLIFOS.size() + COLS - 1) / COLS;
        BufferedImage im = hoja(COLS * ANCHO_CELDA, filas * ALTO_CELDA);
        Plotter p = Plotter.raster(im);
        int i = 0;
        for (Map.Entry<String, Glifo> e : GLIFOS.entrySet()) {
            int col = i % COLS, fila = i / COLS;
            double dx = col * ANCHO_CELDA + (ANCHO_CELDA - e.getValue().ancho()) / 2;
            double dy = fila * ALTO_CELDA + 40;
            p.plot(construye(e.getValue(), dx, dy));
            i++;
        }
        ImageIO.write(im, "png", new File("abecedario.png"));
        System.out.println("abecedario.png (" + GLIFOS.size() + " glifos)");
    }
}
