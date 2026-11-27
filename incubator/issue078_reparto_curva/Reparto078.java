import geometria.composicion.Grafo;
import geometria.composicion.Pintada;
import forma.Gota;
import color.Colores;
import color.Mezcla;
import coordenadas.Opciones;
import coordenadas.Tira;
import pintado.Plotter;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/**
 * Issue #78: ¿por qué un Grafo de una arista curva pinta distinto con GLOBAL y con POR_GOTA?
 * Hipótesis: GLOBAL reparte v por longitud de arco (Cortes.porLongitud = true) y POR_GOTA con la tira por defecto es lineal en t.
 * Se pinta la gota suelta con las dos variantes de tira y se compara con el Grafo en cada reparto. Solo API pública.
 */
public class Reparto078 {
    static BufferedImage lienzo() {
        BufferedImage im = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(datos(im), 0xFFFFFFFF);
        return im;
    }
    static int[] datos(BufferedImage im) { return ((DataBufferInt) im.getRaster().getDataBuffer()).getData(); }

    static String dif(BufferedImage a, BufferedImage b) {
        int[] da = datos(a), db = datos(b); int n = 0, max = 0;
        for (int i = 0; i < da.length; i++) if (da[i] != db[i]) {
            n++;
            for (int s = 0; s < 32; s += 8) max = Math.max(max, Math.abs(((da[i] >> s) & 255) - ((db[i] >> s) & 255)));
        }
        return String.format("%6d px, máx %2d", n, max);
    }

    static BufferedImage grafo(Grafo g, Grafo.Reparto r) {
        BufferedImage im = lienzo();
        Plotter.raster(im).plot(g.reparto(r));
        return im;
    }
    static BufferedImage gota(Grafo g, Tira t) {
        BufferedImage im = lienzo();
        Gota gota = g.gotas().get(0);
        Plotter.raster(im).plot(Pintada.de(gota, Colores.dosColores(R, B)), Opciones.POR_DEFECTO.conTira(t));
        return im;
    }

    static BufferedImage lateral(Grafo g, Colores.Lateral a, Colores.Lateral b, Tira t) {
        BufferedImage im = lienzo();
        Plotter.raster(im).plot(Pintada.de(g.gotas().get(0), Colores.cadenaLateral(a, b, Mezcla.RGB)), Opciones.POR_DEFECTO.conTira(t));
        return im;
    }

    static final int R = 0xFFD02020, B = 0xFF2040D0;

    static void caso(String nombre, Grafo g) {
        BufferedImage glob = grafo(g, Grafo.Reparto.GLOBAL), pg = grafo(g, Grafo.Reparto.POR_GOTA);
        BufferedImage enT = gota(g, Tira.EN_T), enS = gota(g, Tira.POR_DEFECTO);
        Colores.Lateral lr = Colores.Lateral.uniforme(R), lb = Colores.Lateral.uniforme(B);
        BufferedImage latT = lateral(g, lr, lb, Tira.EN_T), latS = lateral(g, lr, lb, Tira.POR_DEFECTO);
        System.out.println(nombre);
        System.out.println("   --- con el relleno lateral del propio Grafo (cadenaLateral) ---");
        System.out.println("   Grafo POR_GOTA vs gota, cadenaLateral, Tira.EN_T (v lineal en t):   " + dif(pg, latT));
        System.out.println("   Grafo GLOBAL   vs gota, cadenaLateral, Tira.POR_DEFECTO (v por s):      " + dif(glob, latS));
        System.out.println("   Grafo GLOBAL   vs gota, cadenaLateral, Tira.EN_T (v lineal en t):   " + dif(glob, latT));
        System.out.println("   Grafo POR_GOTA vs gota, cadenaLateral, Tira.POR_DEFECTO (v por s):      " + dif(pg, latS));
        System.out.println("   --- con dosColores (mezcla por t, sin v) ---");
        System.out.println("   Grafo POR_GOTA vs gota con Tira.EN_T (lineal en t):     " + dif(pg, enT));
        System.out.println("   Grafo GLOBAL   vs gota con Tira.EN_T (lineal en t):     " + dif(glob, enT));
        System.out.println("   Grafo GLOBAL   vs gota con Tira.POR_DEFECTO (longitud de arco):         " + dif(glob, enS));
        System.out.println("   Grafo POR_GOTA vs gota con Tira.POR_DEFECTO (longitud de arco):         " + dif(pg, enS));
    }

    /** Tres paneles: GLOBAL, POR_GOTA y la diferencia absoluta por canal ×8. */
    static void imagen(String nombre, Grafo g) throws Exception {
        BufferedImage glob = grafo(g, Grafo.Reparto.GLOBAL), pg = grafo(g, Grafo.Reparto.POR_GOTA);
        BufferedImage out = new BufferedImage(1200, 300, BufferedImage.TYPE_INT_ARGB), d = lienzo();
        int[] x = datos(glob), y = datos(pg), z = datos(d);
        for (int i = 0; i < x.length; i++) {
            int v = 0xFF000000;
            for (int s = 0; s < 24; s += 8) v |= Math.min(255, 8 * Math.abs(((x[i] >> s) & 255) - ((y[i] >> s) & 255))) << s;
            z[i] = v;
        }
        java.awt.Graphics2D g2 = out.createGraphics();
        g2.drawImage(glob, 0, 0, null); g2.drawImage(pg, 400, 0, null); g2.drawImage(d, 800, 0, null);
        g2.dispose();
        javax.imageio.ImageIO.write(out, "png", new java.io.File(nombre));
    }

    public static void main(String[] a) throws Exception {
        Grafo g1 = new Grafo().mezcla(Mezcla.RGB);
        g1.arista(g1.nodo(80, 150, 50).color(R), g1.nodo(320, 150, 15).color(B));
        caso("recta, radios 50 y 15", g1);

        Grafo g2 = new Grafo().mezcla(Mezcla.RGB);
        g2.arista(g2.nodo(80, 220, 30).color(R), g2.nodo(320, 80, 20).color(B)).tiradores(new Gota.Vec(100, -150), new Gota.Vec(100, 100));
        caso("curva con tiradores", g2);

        Grafo g3 = new Grafo().mezcla(Mezcla.RGB);
        g3.arista(g3.nodo(80, 220, 30).color(R), g3.nodo(320, 80, 20).color(B)).tiradores(new Gota.Vec(30, -30), new Gota.Vec(30, 30));
        caso("curva suave (tiradores cortos)", g3);
        if (a.length > 0) imagen(a[0], g2);
    }
}
