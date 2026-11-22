import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Trébol (nudo de tres cruces) con {@link Composicion.Arista#altura}. Un círculo de nodos cerrado sobre sí mismo no
 * puede alternar por encima / por debajo con el orden del recorrido (TRAZO da U U U O O O); con z = -sin 3t en cada
 * arista, sí. Los nodos van separados más de r1+r2 (si no, la gota es rígida y recta: ver {@link Gota}). Escribe nudo_altura.png con dos paneles: TRAZO sin altura | TRAZO con altura.
 */
public class NudoAltura {
    static final int W = 700, H = 700, N = 30;
    static final Mezcla RGB = Mezcla.RGB;

    static int color(double t) {
        return Color.HSBtoRGB((float) (t / (2 * Math.PI)), 0.75f, 0.95f) | 0xFF000000;
    }

    static Grafo trebol(boolean conAltura) {
        Grafo c = new Grafo().mezcla(RGB);
        Grafo.Nodo[] n = new Grafo.Nodo[N];
        for (int i = 0; i < N; i++) {
            double t = 2 * Math.PI * i / N;
            double x = W / 2.0 + 96 * (Math.sin(t) + 2 * Math.sin(2 * t));
            double y = H / 2.0 + 96 * (Math.cos(t) - 2 * Math.cos(2 * t));
            n[i] = c.nodo(x, y, 17).centro(color(t));
        }
        for (int i = 0; i < N; i++) {
            final double t0 = 2 * Math.PI * i / N, dt = 2 * Math.PI / N;
            var e = c.arista(n[i], n[(i + 1) % N]);
            if (conAltura) e.altura(x -> -Math.sin(3 * (t0 + x * dt)));
        }
        return c;
    }

    static BufferedImage pinta(boolean conAltura) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFFF4F1EA);
        trebol(conAltura).render(im, Opciones.POR_DEFECTO.conSolape(Solape.TRAZO));
        return im;
    }

    public static void main(String[] args) throws Exception {
        BufferedImage hoja = new BufferedImage(2 * W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hoja.createGraphics();
        g.drawImage(pinta(false), 0, 0, null);
        g.drawImage(pinta(true), W, 0, null);
        g.dispose();
        ImageIO.write(hoja, "png", new File("nudo_altura.png"));
        System.out.println("nudo_altura.png (TRAZO | TRAZO + altura)");
    }
}
