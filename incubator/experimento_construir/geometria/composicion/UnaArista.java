package geometria.composicion;

import forma.Gota;
import color.Relleno;
import color.Colores;
import color.Mezcla;
import pintado.Plotter;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/**
 * ¿Un Grafo de una arista pinta lo mismo que su gota? Se compara contra dos rellenos:
 * el de dosColores (mezcla por t) y el lateral exacto que el propio Grafo construye (Grafo.rellenoDe, visibilidad de paquete).
 * Vive en el paquete geometria.composicion solo para poder llamar a ese método.
 */
public class UnaArista {
    static BufferedImage lienzo() {
        BufferedImage im = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFFFFFFFF);
        return im;
    }
    static int[] datos(BufferedImage im) { return ((DataBufferInt) im.getRaster().getDataBuffer()).getData(); }

    static void compara(String etiqueta, BufferedImage a, BufferedImage b) {
        int[] da = datos(a), db = datos(b); int dif = 0, max = 0, sil = 0;
        for (int i = 0; i < da.length; i++) {
            if ((da[i] == 0xFFFFFFFF) != (db[i] == 0xFFFFFFFF)) sil++;
            if (da[i] != db[i]) {
                dif++;
                for (int s = 0; s < 32; s += 8) max = Math.max(max, Math.abs(((da[i] >> s) & 255) - ((db[i] >> s) & 255)));
            }
        }
        System.out.printf("   %-26s silueta distinta: %d, píxeles distintos: %d, máx. por canal: %d%n", etiqueta, sil, dif, max);
    }

    static void caso(String nombre, Grafo g, Relleno dos) {
        System.out.println(nombre);
        BufferedImage base = lienzo(), r1 = lienzo(), r2 = lienzo();
        Plotter.raster(base).plot(g);
        Plotter.raster(r1).plot(Pintada.de(g.gotas().get(0), dos));
        Plotter.raster(r2).plot(Pintada.de(g.gotas().get(0), g.rellenoDe(0)));
        compara("vs dosColores", base, r1);
        compara("vs relleno del Grafo", base, r2);
        BufferedImage pg = lienzo();
        g.reparto(Grafo.Reparto.POR_GOTA);
        Plotter.raster(pg).plot(g);
        compara("Grafo POR_GOTA vs dosColores", pg, r1);
        g.reparto(Grafo.Reparto.GLOBAL);
    }

    public static void main(String[] a) {
        int R = 0xFFD02020, B = 0xFF2040D0, N = 0xFF000000;
        Grafo g1 = new Grafo().mezcla(Mezcla.RGB);
        g1.arista(g1.nodo(100, 150, 40).color(N), g1.nodo(300, 150, 40).color(N));
        caso("negro, radios iguales, recta", g1, Colores.dosColores(N, N));

        Grafo g2 = new Grafo().mezcla(Mezcla.RGB);
        g2.arista(g2.nodo(100, 150, 40).color(R), g2.nodo(300, 150, 40).color(B));
        caso("dos colores, radios iguales, recta", g2, Colores.dosColores(R, B));

        Grafo g3 = new Grafo().mezcla(Mezcla.RGB);
        g3.arista(g3.nodo(80, 150, 50).color(R), g3.nodo(320, 150, 15).color(B));
        caso("dos colores, radios 50 y 15, recta", g3, Colores.dosColores(R, B));

        Grafo g4 = new Grafo().mezcla(Mezcla.RGB);
        var n1 = g4.nodo(80, 220, 30).color(R); var n2 = g4.nodo(320, 80, 20).color(B);
        g4.arista(n1, n2).tiradores(new Gota.Vec(100, -150), new Gota.Vec(100, 100));
        caso("dos colores, curva con tiradores", g4, Colores.dosColores(R, B));

        Grafo g5 = new Grafo().mezcla(Mezcla.RGB).ajustarPendientes(false);
        var m1 = g5.nodo(80, 220, 30).color(R); var m2 = g5.nodo(320, 80, 20).color(B);
        g5.arista(m1, m2).tiradores(new Gota.Vec(100, -150), new Gota.Vec(100, 100));
        caso("curva, ajustarPendientes(false)", g5, Colores.dosColores(R, B));
    }
}
