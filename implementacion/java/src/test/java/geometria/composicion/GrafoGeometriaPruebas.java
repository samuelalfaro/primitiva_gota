package geometria.composicion;

import coordenadas.Costura;
import coordenadas.Opciones;
import color.Texturas;
import color.Combinacion;
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
 * Pruebas de {@link Grafo} como {@link Geometria}: pintarlo con {@code Plotter.plot(grafo, op)} es estable con la memoria
 * por opciones (todos los solapes y costuras, con suavizado y con alturas por arista) y se invalida al modificar el grafo;
 * su silueta, sus límites y sus metadatos son coherentes con {@code contiene} y {@code sdf}; y la textura es del grafo.
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
        c.add(new Caso("cadena", GrafoPruebas.cadena()));
        c.add(new Caso("ciclo", GrafoPruebas.ciclo()));
        c.add(new Caso("estrella", GrafoPruebas.estrella(0)));
        c.add(new Caso("estrella con suavizado 12", GrafoPruebas.estrella(12)));
        c.add(new Caso("ocho (se cruza)", GrafoPruebas.ocho()));
        c.add(new Caso("cinta con rama", GrafoPruebas.cintaConRama()));
        c.add(new Caso("cruz, roja por encima (alturas)", GrafoPruebas.cruz(2.0, 1.0)));
        c.add(new Caso("cruz, azul por encima (alturas)", GrafoPruebas.cruz(1.0, 2.0)));
        c.add(new Caso("trébol de 12 nodos", GrafoPruebas.trebolDeNodos(12)));
        return c;
    }

    public static void main(String[] args) {
        for (Caso k : casos()) {
            for (Solape s : Solape.values()) for (Costura co : Costura.values()) {
                Opciones op = Opciones.POR_DEFECTO.conSolape(s).conCostura(co);
                BufferedImage a = lienzo(), b = lienzo();
                Plotter.raster(a).plot(k.grafo(), op);          // calcula la memoria por opciones
                Plotter.raster(b).plot(k.grafo(), op);          // y esta vez la usa
                int[] d = diferencias(a, b);
                comprueba(k.nombre() + " / " + s + " / " + co + ": pintado repetido, mismos píxeles", d[0] == 0 && diferencias(a, lienzo())[0] > 0, d[0] + " distintos, canal hasta " + d[1]);
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
        Grafo s12 = GrafoPruebas.estrella(12), s0 = GrafoPruebas.estrella(0);
        comprueba("bounds() se ensancha con el suavizado (12 px por lado)", Math.abs(s12.bounds().getWidth() - s0.bounds().getWidth() - 24) < 1e-9, "");

        // la memoria por opciones se invalida al modificar
        Grafo g = new Grafo();
        var p0 = g.nodo(80, 200, 14).centro(0xFFD94A38);
        var p1 = g.nodo(200, 200, 14).centro(0xFF2F6DB5);
        g.arista(p0, p1);
        BufferedImage a = lienzo(), b = lienzo();
        Plotter.raster(a).plot(g);
        g.arista(p1, g.nodo(300, 320, 10).centro(0xFF4FA36A));
        Plotter.raster(b).plot(g);
        Grafo nuevo = new Grafo();
        var q0 = nuevo.nodo(80, 200, 14).centro(0xFFD94A38);
        var q1 = nuevo.nodo(200, 200, 14).centro(0xFF2F6DB5);
        nuevo.arista(q0, q1);
        nuevo.arista(q1, nuevo.nodo(300, 320, 10).centro(0xFF4FA36A));
        BufferedImage c = lienzo();
        Plotter.raster(c).plot(nuevo);
        comprueba("al añadir una arista, el pintado coincide con el de un grafo nuevo igual", diferencias(b, c)[0] == 0 && diferencias(a, b)[0] > 0, "");

        // la textura es del Grafo y pasa por capas
        Texturas.Textura tex = Texturas.referenciaLados();
        for (Solape s : Solape.values()) {
            Opciones op = Opciones.POR_DEFECTO.conSolape(s);
            Grafo gt = GrafoPruebas.cadena();
            BufferedImage lat = lienzo(), t1 = lienzo(), t2 = lienzo(), rep = lienzo(), sin = lienzo(), md = lienzo(), dc = lienzo(), re = lienzo();
            Plotter.raster(lat).plot(gt, op);
            Plotter.raster(t1).plot(gt.textura(tex), op);
            Plotter.raster(t2).plot(gt, op);                     // sigue configurada
            Plotter.raster(re).plot(gt.textura(tex, Combinacion.Modo.REPLACE), op);
            Plotter.raster(md).plot(gt.textura(tex, Combinacion.Modo.MODULATE), op);
            Plotter.raster(dc).plot(gt.textura(tex, Combinacion.Modo.DECAL), op);
            Plotter.raster(sin).plot(gt.sinTextura(), op);
            comprueba("textura / " + s + ": cambia lo pintado y se queda configurada", diferencias(lat, t1)[0] > 0 && diferencias(t1, t2)[0] == 0, "");
            comprueba("textura / " + s + ": REPLACE == textura sola", diferencias(t1, re)[0] == 0, "");
            comprueba("textura / " + s + ": MODULATE y DECAL se distinguen de REPLACE", diferencias(re, md)[0] > 0 && diferencias(re, dc)[0] > 0, "");
            comprueba("textura / " + s + ": sinTextura() vuelve al color lateral", diferencias(lat, sin)[0] == 0, "");
        }
        Grafo gb = GrafoPruebas.cadena();
        BufferedImage u1 = lienzo(), u2 = lienzo(), u3 = lienzo();
        Plotter.raster(u1).plot(gb.textura(tex, Combinacion.Modo.BLEND, 0xFFFF8000));
        Plotter.raster(u2).plot(gb.textura(tex, Combinacion.Modo.BLEND, 0xFF0080FF));
        Plotter.raster(u3).plot(gb.textura(tex, Combinacion.Modo.BLEND, 0xFF0080FF));
        comprueba("BLEND: el color de entorno cambia el resultado", diferencias(u1, u2)[0] > 0 && diferencias(u2, u3)[0] == 0, "");
        boolean nulo = false, nuloModo = false;
        try { gb.textura(null); } catch (NullPointerException e) { nulo = true; }
        try { gb.textura(tex, null); } catch (NullPointerException e) { nuloModo = true; }
        comprueba("textura nula o modo nulo: NullPointerException", nulo && nuloModo, "");

        System.out.printf("%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }
}
