package composicion;

import coordenadas.Costura;
import coordenadas.Opciones;
import coordenadas.Solape;
import geometria.Geometria;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import pintado.Plotter;

/**
 * Pruebas de {@link Grafo} como {@link Geometria}: pintarlo con {@code Plotter.plot(grafo, op)} da los mismos píxeles que
 * {@code grafo.render(dst, op)} (el color lateral de siempre), con todos los solapes y costuras, con suavizado y con
 * alturas por arista; y su silueta, sus límites y sus metadatos son coherentes con {@code contiene} y {@code sdf}.
 */
public class GrafoGeometriaPruebas {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        System.out.printf("%-78s %s%s%n", n, ok ? "ok   " : "FALLA", d.isEmpty() ? "" : "  " + d);
    }

    static final int W = 420, H = 420, FONDO = 0xFFF4F1EA;

    static BufferedImage lienzo() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), FONDO);
        return im;
    }

    /** Píxeles distintos entre dos lienzos y la mayor diferencia en un canal. */
    static int[] diferencias(BufferedImage a, BufferedImage b) {
        int[] x = ((DataBufferInt) a.getRaster().getDataBuffer()).getData(), y = ((DataBufferInt) b.getRaster().getDataBuffer()).getData();
        int n = 0, max = 0;
        for (int i = 0; i < x.length; i++) if (x[i] != y[i]) {
            n++;
            for (int s = 0; s < 32; s += 8) max = Math.max(max, Math.abs(((x[i] >> s) & 255) - ((y[i] >> s) & 255)));
        }
        return new int[] { n, max };
    }

    record Caso(String nombre, Grafo grafo) {}

    static List<Caso> casos() {
        List<Caso> c = new ArrayList<>();
        c.add(new Caso("cadena", ComposicionPruebas.cadena()));
        c.add(new Caso("ciclo", ComposicionPruebas.ciclo()));
        c.add(new Caso("estrella", ComposicionPruebas.estrella(0)));
        c.add(new Caso("estrella con suavizado 12", ComposicionPruebas.estrella(12)));
        c.add(new Caso("ocho (se cruza)", ComposicionPruebas.ocho()));
        c.add(new Caso("cinta con rama", ComposicionPruebas.cintaConRama()));
        c.add(new Caso("cruz, roja por encima (alturas)", ComposicionPruebas.cruz(2.0, 1.0)));
        c.add(new Caso("cruz, azul por encima (alturas)", ComposicionPruebas.cruz(1.0, 2.0)));
        c.add(new Caso("trébol de 12 nodos", ComposicionPruebas.trebolDeNodos(12)));
        return c;
    }

    public static void main(String[] args) {
        for (Caso k : casos()) {
            for (Solape s : Solape.values()) for (Costura co : Costura.values()) {
                Opciones op = Opciones.POR_DEFECTO.conSolape(s).conCostura(co);
                BufferedImage a = lienzo(), b = lienzo();
                k.grafo().render(a, op);
                Plotter.raster(b).plot(k.grafo(), op);
                int[] d = diferencias(a, b);
                comprueba(k.nombre() + " / " + s + " / " + co + ": mismos píxeles", d[0] == 0, d[0] + " distintos, canal hasta " + d[1]);
            }
        }

        // límites, silueta y metadatos
        for (Caso k : casos()) {
            Grafo g = k.grafo().label(k.nombre()).altura(3);
            Shape forma = g.silueta();
            var gb = g.bounds();
            var bb = forma.getBounds2D();
            comprueba(k.nombre() + ": la silueta cabe en bounds()", gb.getMinX() - 1.5 <= bb.getMinX() && gb.getMaxX() + 1.5 >= bb.getMaxX()
                    && gb.getMinY() - 1.5 <= bb.getMinY() && gb.getMaxY() + 1.5 >= bb.getMaxY(), bb + " vs " + gb);
            Random r = new Random(11);
            int n = 4000, mal = 0;
            for (int i = 0; i < n; i++) {
                double x = gb.getMinX() + r.nextDouble() * gb.getWidth(), y = gb.getMinY() + r.nextDouble() * gb.getHeight();
                if (forma.contains(x, y) != g.contiene(x, y)) mal++;
            }
            comprueba(k.nombre() + ": la silueta coincide con contiene() (<= 1 % de discrepancias)", mal <= n / 100, mal + "/" + n);
            comprueba(k.nombre() + ": label y altura", k.nombre().equals(g.label()) && g.altura() == 3, "");
        }
        Grafo s12 = ComposicionPruebas.estrella(12), s0 = ComposicionPruebas.estrella(0);
        comprueba("bounds() se ensancha con el suavizado (12 px por lado)", Math.abs(s12.bounds().getWidth() - s0.bounds().getWidth() - 24) < 1e-9, "");

        // la memoria por opciones se invalida al modificar
        Grafo g = new Grafo();
        var p0 = g.nodo(80, 200, 14).centro(0xFFD94A38);
        var p1 = g.nodo(200, 200, 14).centro(0xFF2F6DB5);
        g.arista(p0, p1);
        BufferedImage a = lienzo(), b = lienzo();
        Plotter.raster(a).plot(g, Opciones.POR_DEFECTO);
        g.arista(p1, g.nodo(300, 320, 10).centro(0xFF4FA36A));
        Plotter.raster(b).plot(g, Opciones.POR_DEFECTO);
        BufferedImage c = lienzo();
        g.render(c, Opciones.POR_DEFECTO);
        comprueba("al añadir una arista, plot sigue coincidiendo con render", diferencias(b, c)[0] == 0 && diferencias(a, b)[0] > 0, "");

        System.out.printf("%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }
}
