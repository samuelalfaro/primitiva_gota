import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Nodo de grado 3 (una rama sale de una cinta): sin {@link Grafo#relajar()} cada arista sale hacia su vecino y
 * la cinta hace un quiebro en el nodo; con él, la cinta pasa suave y la rama sale como siempre.
 * Escribe relajar_demo.png: sin relajar | con relajar().
 */
public class RelajarDemo {
    static final int W = 520, H = 480;

    static Grafo cinta(boolean relaja) {
        Grafo c = new Grafo().mezcla(Mezcla.RGB);
        int[] col = { 0xFFE8523A, 0xFFF2A33A, 0xFF4FA36B, 0xFF2D6CDF, 0xFF8E44AD, 0xFFD7263D };
        double[][] p = { { 40, 330 }, { 150, 150 }, { 260, 250 }, { 370, 360 }, { 480, 170 }, { 235, 430 } };
        var n = new Grafo.Nodo[p.length];
        for (int i = 0; i < p.length; i++) n[i] = c.nodo(p[i][0], p[i][1], 16).centro(col[i]);
        c.arista(n[0], n[1]); c.arista(n[1], n[2]); c.arista(n[2], n[3]); c.arista(n[3], n[4]);
        c.arista(n[2], n[5]);
        return relaja ? c.relajar() : c;
    }

    static BufferedImage pinta(boolean relaja) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFFF4F1EA);
        cinta(relaja).render(im);
        return im;
    }

    public static void main(String[] args) throws Exception {
        BufferedImage hoja = new BufferedImage(2 * W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hoja.createGraphics();
        g.drawImage(pinta(false), 0, 0, null);
        g.drawImage(pinta(true), W, 0, null);
        g.dispose();
        ImageIO.write(hoja, "png", new File("relajar_demo.png"));
        System.out.println("relajar_demo.png (sin relajar | relajar())");
    }
}
