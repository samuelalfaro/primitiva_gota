import pintado.Plotter;
import composicion.Grafo;
import color.Mezcla;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Árbol paramétrico construido con la API de Composicion.
 *
 * El generador decide la estructura vegetal; Gota y Composicion
 * se ocupan de representar las ramas.
 *
 * Las bifurcaciones comparten el nodo de nacimiento. No se intenta
 * resolver aquí el solapamiento entre ramas.
 */
public final class ArbolParametrico {

    static final int W = 800, H = 700;
    static final int FONDO = 0xFFF5F0E6;
    static final int MADERA_BASE = 0xFF70452F;
    static final int MADERA_PUNTA = 0xFFB17A50;

    /** Parámetros independientes de la API geométrica. */
    static final class Parametros {
        double altura = 270;
        double radio = 13;
        int niveles = 5;
        int ramas = 2;
        double reduccionLongitud = 0.70;
        double reduccionRadio = 0.66;
        double apertura = Math.toRadians(55);
        double variacionAngular = Math.toRadians(9);
        double variacionLongitud = 0.12;
        long semilla = 20261121;
    }

    private final Parametros p;
    private final Random azar;
    private final Grafo composicion = new Grafo();

    private ArbolParametrico(Parametros parametros) {
        this.p = parametros;
        this.azar = new Random(parametros.semilla);
    }

    private static int mezcla(int a, int b, double t) {
        return Mezcla.RGB.mezclar(a, b, Math.max(0, Math.min(1, t)));
    }

    private int color(int nivel) {
        double t = p.niveles == 0 ? 0 : (double) nivel / p.niveles;
        return mezcla(MADERA_BASE, MADERA_PUNTA, t);
    }

    private Grafo.Nodo nodo(double x, double y, double radio, int nivel) {
        return composicion.nodo(x, y, Math.max(0.8, radio)).centro(color(nivel));
    }

    private double aleatorio(double amplitud) {
        return (azar.nextDouble() * 2 - 1) * amplitud;
    }

    /**
     * Crea una rama desde un nodo existente y, al llegar a su extremo,
     * genera recursivamente sus ramas hijas.
     */
    private void crecer(
            Grafo.Nodo origen,
            double x, double y,
            double angulo,
            double longitud,
            double radio,
            int nivel) {

        double anguloReal = angulo + aleatorio(p.variacionAngular);
        double xFin = x + Math.cos(anguloReal) * longitud;
        double yFin = y + Math.sin(anguloReal) * longitud;

        double radioFin = Math.max(0.8, radio * p.reduccionRadio);
        Grafo.Nodo fin = nodo(xFin, yFin, radioFin, nivel + 1);

        // Tiradores automáticos: la geometría de la curva pertenece a Composicion.
        composicion.arista(origen, fin)
                .espina(t -> mezcla(color(nivel), color(nivel + 1), t));

        if (nivel >= p.niveles) return;

        for (int i = 0; i < p.ramas; i++) {
            double posicion = p.ramas == 1
                    ? 0
                    : (double) i / (p.ramas - 1) - 0.5;

            double anguloHija = anguloReal
                    + posicion * p.apertura
                    + aleatorio(p.variacionAngular * 0.45);

            double longitudHija = longitud * p.reduccionLongitud
                    * (1 + aleatorio(p.variacionLongitud));

            crecer(fin, xFin, yFin, anguloHija,
                    longitudHija, radioFin, nivel + 1);
        }
    }

    static Grafo crear(Parametros p) {
        ArbolParametrico arbol = new ArbolParametrico(p);
        double x = W * 0.5;
        double y = H - 45;

        Grafo.Nodo raiz = arbol.nodo(x, y, p.radio, 0);
        arbol.crecer(raiz, x, y, -Math.PI / 2,
                p.altura, p.radio, 0);

        return arbol.composicion;
    }

    static BufferedImage dibujar(Parametros p) {
        BufferedImage imagen = new BufferedImage(
                W, H, BufferedImage.TYPE_INT_ARGB);

        int[] pixeles = ((DataBufferInt) imagen.getRaster()
                .getDataBuffer()).getData();
        java.util.Arrays.fill(pixeles, FONDO);

        Plotter.raster(imagen).plot(crear(p));
        return imagen;
    }

    public static void main(String[] args) throws Exception {
        Parametros p = new Parametros();

        // Ejemplos de variación paramétrica:
        // p.niveles = 6;
        // p.ramas = 2;
        // p.apertura = Math.toRadians(65);
        // p.semilla = 42;

        ImageIO.write(dibujar(p), "png",
                new File("arbol_parametrico.png"));
        System.out.println("arbol_parametrico.png");
    }
}
