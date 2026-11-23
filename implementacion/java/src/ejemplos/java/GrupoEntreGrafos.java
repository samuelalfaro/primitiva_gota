import color.Mezcla;
import composicion.Grafo;
import composicion.Grupo;
import coordenadas.Opciones;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;
import pintado.Plotter;

/**
 * Tres composiciones sin relación topológica que se cruzan (una cinta roja, una azul y una verde con un lazo) reunidas en
 * un {@link Grupo}. Con la misma altura va encima la que se añadió después, como el orden de pintado de siempre; con
 * {@code Grafo.altura(double)} la que sube queda encima aunque se añada antes. Escribe grupo_entre_grafos.png con tres
 * paneles: sin alturas | el rojo arriba | el verde arriba y el azul en medio.
 * <p>
 * Límite: la altura de un grafo es la misma en todos sus píxeles, así que dos composiciones no pueden alternar por encima y
 * por debajo entre sí (un eslabón de cadena necesita altura por píxel, la fase 2 de la altura).
 */
public class GrupoEntreGrafos {
    static final int W = 420, H = 420, FONDO = 0xFFF4F1EA;
    static final int ROJO = 0xFFE8283A, AZUL = 0xFF2850E8, VERDE = 0xFF2E9E5B;

    static Grafo rojo() {
        Grafo c = new Grafo().mezcla(Mezcla.RGB);
        c.arista(c.nodo(60, 100, 16).centro(ROJO), c.nodo(360, 320, 16).centro(ROJO));
        return c.label("rojo");
    }

    static Grafo azul() {
        Grafo c = new Grafo().mezcla(Mezcla.RGB);
        c.arista(c.nodo(340, 60, 16).centro(AZUL), c.nodo(80, 360, 16).centro(AZUL));
        return c.label("azul");
    }

    static Grafo verde() {
        Grafo c = new Grafo().mezcla(Mezcla.RGB);
        var a = c.nodo(60, 300, 14).centro(VERDE);
        var b = c.nodo(210, 240, 14).centro(VERDE);
        var d = c.nodo(360, 130, 14).centro(VERDE);
        c.arista(a, b);
        c.arista(b, d);
        return c.label("verde");
    }

    static BufferedImage panel(Grafo r, Grafo a, Grafo v) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), FONDO);
        Plotter.raster(im).plot(new Grupo(r, a, v), Opciones.POR_DEFECTO);
        return im;
    }

    public static void main(String[] args) throws Exception {
        BufferedImage hoja = new BufferedImage(3 * W, H, BufferedImage.TYPE_INT_ARGB);
        var g = hoja.createGraphics();
        g.drawImage(panel(rojo(), azul(), verde()), 0, 0, null);
        g.drawImage(panel(rojo().altura(1), azul(), verde()), W, 0, null);
        g.drawImage(panel(rojo(), azul().altura(1), verde().altura(2)), 2 * W, 0, null);
        g.dispose();
        ImageIO.write(hoja, "png", new File("grupo_entre_grafos.png"));
        System.out.println("grupo_entre_grafos.png");
    }
}
