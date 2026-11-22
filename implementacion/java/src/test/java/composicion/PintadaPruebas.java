package composicion;

import color.Colores;
import color.Relleno;
import coordenadas.Costura;
import coordenadas.Opciones;
import coordenadas.Solape;
import forma.Gota;
import forma.Perfil;
import geometria.Geometria;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;
import pintado.Plotter;

/**
 * Pruebas de {@link Pintada} (una gota con relleno como {@link Geometria}): pintar la geometría con
 * {@code Plotter.plot(Geometria, Opciones)} da exactamente los mismos píxeles que {@code plot(Gota, Relleno, Opciones)},
 * y la silueta, los límites y los metadatos son coherentes con la máscara de la gota.
 */
public class PintadaPruebas {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        System.out.printf("%-78s %s%s%n", n, ok ? "ok   " : "FALLA", d.isEmpty() ? "" : "  " + d);
    }

    static final int W = 300, H = 240, FONDO = 0xFFF4F1EA;

    static BufferedImage lienzo() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), FONDO);
        return im;
    }

    static int[] datos(BufferedImage im) { return ((DataBufferInt) im.getRaster().getDataBuffer()).getData(); }

    /** Número de píxeles distintos entre los dos lienzos. */
    static int diferencias(BufferedImage a, BufferedImage b) {
        int[] x = datos(a), y = datos(b);
        int n = 0;
        for (int i = 0; i < x.length; i++) if (x[i] != y[i]) n++;
        return n;
    }

    record Caso(String nombre, Gota gota, Relleno relleno) {}

    static List<Caso> casos() {
        List<Caso> c = new ArrayList<>();
        int rojo = 0xFFD94A38, azul = 0xFF2F6DB5, verde = 0xFF4FA36A, ambar = 0x99F2A83B;
        c.add(new Caso("recta, dos colores", Gota.lineal(new Point2D.Double(70, 120), 30, new Point2D.Double(230, 120), 18, Perfil.recto()),
                Colores.dosColores(rojo, azul)));
        c.add(new Caso("rígida (d <= r1 + r2), cadena", Gota.lineal(new Point2D.Double(120, 120), 40, new Point2D.Double(180, 120), 35, Perfil.recto()),
                Colores.cadena(rojo, azul, verde, ambar)));
        c.add(new Caso("curva con tiradores, translúcida", Gota.curva(new Point2D.Double(60, 170), 22, new Gota.Vec(120, -160), new Point2D.Double(240, 80), 14, new Gota.Vec(100, 120), Perfil.recto()),
                Colores.dosColores(ambar, azul)));
        c.add(new Caso("lazo que se cruza (TRAZO apila vueltas)", Gota.curva(new Point2D.Double(100, 160), 16, new Gota.Vec(330, -420), new Point2D.Double(200, 160), 16, new Gota.Vec(-330, -420), Perfil.recto()),
                Colores.dosColores(0xAAD94A38, 0xAA2F6DB5)));
        return c;
    }

    public static void main(String[] args) {
        for (Caso k : casos()) {
            for (Solape s : Solape.values()) for (Costura co : Costura.values()) {
                Opciones op = Opciones.POR_DEFECTO.conSolape(s).conCostura(co);
                BufferedImage a = lienzo(), b = lienzo();
                Plotter.raster(a).plot(k.gota(), k.relleno(), op);
                Plotter.raster(b).plot(Pintada.de(k.gota(), k.relleno()), op);
                int d = diferencias(a, b);
                comprueba(k.nombre() + " / " + s + " / " + co + ": mismos píxeles", d == 0, d + " distintos");
            }
        }

        // silueta, límites y metadatos
        for (Caso k : casos()) {
            Pintada p = Pintada.de(k.gota(), k.relleno()).conLabel(k.nombre()).conAltura(2.5);
            Shape forma = p.silueta();
            var bb = forma.getBounds2D();
            var gb = p.bounds();
            comprueba(k.nombre() + ": la silueta cabe en bounds()", gb.getMinX() - 1.5 <= bb.getMinX() && gb.getMaxX() + 1.5 >= bb.getMaxX()
                    && gb.getMinY() - 1.5 <= bb.getMinY() && gb.getMaxY() + 1.5 >= bb.getMaxY(), bb + " vs " + gb);
            int dentro = 0, total = 0;
            for (double t = 0.1; t <= 0.9; t += 0.1) {
                total++;
                if (forma.getBounds2D().contains(k.gota().cx(t), k.gota().cy(t))) dentro++;
            }
            comprueba(k.nombre() + ": los centros del camino caen dentro de los límites de la silueta", dentro == total, dentro + "/" + total);
            comprueba(k.nombre() + ": label y altura", k.nombre().equals(p.label()) && p.altura() == 2.5 && Pintada.de(k.gota(), k.relleno()).altura() == 0
                    && Pintada.de(k.gota(), k.relleno()).label() == null, "");
        }

        // capas: en PLANO una por píxel; en TRAZO, al menos una donde hay lazo
        Caso lazo = casos().get(3);
        Pintada pl = Pintada.de(lazo.gota(), lazo.relleno());
        int maxPlano = 0, maxTrazo = 0;
        for (int j = 0; j < H; j += 3) for (int i = 0; i < W; i += 3) {
            int[] n = { 0 };
            pl.capas(i + 0.5, j + 0.5, Opciones.POR_DEFECTO.conSolape(Solape.PLANO), t -> n[0]++);
            maxPlano = Math.max(maxPlano, n[0]);
            n[0] = 0;
            pl.capas(i + 0.5, j + 0.5, Opciones.POR_DEFECTO.conSolape(Solape.TRAZO), t -> n[0]++);
            maxTrazo = Math.max(maxTrazo, n[0]);
        }
        comprueba("PLANO entrega como mucho una capa por píxel", maxPlano <= 1, "máx " + maxPlano);
        comprueba("TRAZO entrega varias capas donde el camino se cruza", maxTrazo >= 2, "máx " + maxTrazo);

        System.out.printf("%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }
}
