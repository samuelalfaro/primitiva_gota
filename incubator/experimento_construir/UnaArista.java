import geometria.composicion.Grafo;
import geometria.composicion.Pintada;
import forma.Gota;
import color.Colores;
import color.Mezcla;
import pintado.Plotter;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/** ¿Un Grafo de una arista pinta lo mismo que su gota con el relleno equivalente? */
public class UnaArista {
    static BufferedImage lienzo() {
        BufferedImage im = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFFFFFFFF);
        return im;
    }
    static int[] datos(BufferedImage im) { return ((DataBufferInt) im.getRaster().getDataBuffer()).getData(); }

    static void caso(String nombre, Grafo g, java.util.function.Function<Gota, Pintada> pint) {
        BufferedImage a = lienzo(), b = lienzo();
        Plotter.raster(a).plot(g);
        Plotter.raster(b).plot(pint.apply(g.gotas().get(0)));
        int[] da = datos(a), db = datos(b); int dif = 0, max = 0;
        for (int i = 0; i < da.length; i++) if (da[i] != db[i]) {
            dif++;
            for (int s = 0; s < 32; s += 8) max = Math.max(max, Math.abs(((da[i] >> s) & 255) - ((db[i] >> s) & 255)));
        }
        int mask = 0; for (int i = 0; i < da.length; i++) if ((da[i] == 0xFFFFFFFF) != (db[i] == 0xFFFFFFFF)) mask++;
        System.out.printf("   (silueta distinta en %d píxeles)%n", mask);
        System.out.printf("%-48s píxeles distintos: %d, diferencia máxima por canal: %d%n", nombre, dif, max);
    }

    public static void main(String[] a) {
        int R = 0xFFD02020, B = 0xFF2040D0, N = 0xFF000000;
        Grafo g1 = new Grafo().mezcla(Mezcla.RGB);
        g1.arista(g1.nodo(100, 150, 40).color(N), g1.nodo(300, 150, 40).color(N));
        caso("negro, radios iguales, recta", g1, g -> Pintada.de(g, Colores.dosColores(N, N)));

        Grafo g2 = new Grafo().mezcla(Mezcla.RGB);
        g2.arista(g2.nodo(100, 150, 40).color(R), g2.nodo(300, 150, 40).color(B));
        caso("dos colores, radios iguales, recta", g2, g -> Pintada.de(g, Colores.dosColores(R, B)));

        Grafo g3 = new Grafo().mezcla(Mezcla.RGB);
        g3.arista(g3.nodo(80, 150, 50).color(R), g3.nodo(320, 150, 15).color(B));
        caso("dos colores, radios 50 y 15, recta", g3, g -> Pintada.de(g, Colores.dosColores(R, B)));

        Grafo g4 = new Grafo().mezcla(Mezcla.RGB);
        var n1 = g4.nodo(80, 220, 30).color(R); var n2 = g4.nodo(320, 80, 20).color(B);
        g4.arista(n1, n2).tiradores(new Gota.Vec(100, -150), new Gota.Vec(100, 100));
        caso("dos colores, curva con tiradores", g4, g -> Pintada.de(g, Colores.dosColores(R, B)));
        Grafo g5 = new Grafo().mezcla(Mezcla.RGB).ajustarPendientes(false);
        var m1 = g5.nodo(80, 220, 30).color(R); var m2 = g5.nodo(320, 80, 20).color(B);
        g5.arista(m1, m2).tiradores(new Gota.Vec(100, -150), new Gota.Vec(100, 100));
        caso("curva, ajustarPendientes(false)", g5, g -> Pintada.de(g, Colores.dosColores(R, B)));
    }
}
