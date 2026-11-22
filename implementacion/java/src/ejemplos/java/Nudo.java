import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Trébol de nudo (nudo de tres cruces) como una sola cinta cerrada. En un nudo alternado la cinta pasa por encima y por
 * debajo de forma alterna a lo largo del recorrido; el TRAZO solo sabe "lo que va más tarde en el recorrido queda encima",
 * así que no puede dibujarlo. Escribe nudo.png (PLANO | TRAZO) para verlo.
 */
public class Nudo {
    static final int W = 700, H = 700, CX = 350, CY = 350;
    static final Mezcla RGB = Mezcla.RGB;
    static int color(double t) { return Color.HSBtoRGB((float) t, 0.6f, 0.9f); }
    static Degradado radio(int c) { return x -> RGB.mezclar(c, 0xFF1A1030, (float) (0.40 * x * x)); }

    static Grafo nudo(int n) {
        Grafo c = new Grafo().mezcla(RGB);
        Grafo.Nodo prev = null, primero = null;
        for (int i = 0; i < n; i++) {
            double t = 2 * Math.PI * i / n;
            double x = CX + 75 * (Math.sin(t) + 2 * Math.sin(2 * t)), y = CY + 75 * (Math.cos(t) - 2 * Math.cos(2 * t));
            int col = color((double) i / n);
            var nd = c.nodo(x, y, 14).centro(col).radio(radio(col));
            if (prev == null) primero = nd;
            else { final int a = color((double) (i - 1) / n), b = col; c.arista(prev, nd).espina(u -> RGB.mezclar(a, b, (float) u)); }
            prev = nd;
        }
        final int a = color((n - 1.0) / n), b = color(0);
        c.arista(prev, primero).espina(u -> RGB.mezclar(a, b, (float) u));
        return c;
    }

    static BufferedImage pinta(Solape s) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFFF4F1EA);
        nudo(36).render(im, Opciones.POR_DEFECTO.conSolape(s));
        return im;
    }

    public static void main(String[] args) throws Exception {
        BufferedImage hoja = new BufferedImage(W * 2, H, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = hoja.createGraphics();
        g.drawImage(pinta(Solape.PLANO), 0, 0, null);
        g.drawImage(pinta(Solape.TRAZO), W, 0, null);
        g.dispose();
        ImageIO.write(hoja, "png", new File("nudo.png"));
        System.out.println("nudo.png (PLANO | TRAZO)");
    }
}
