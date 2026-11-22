import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Dalia: n pétalos que salen del centro M y vuelven a él (nodo de grado 2n), con el radio variando a lo largo del pétalo
 * (estrecho cerca de M, ancho en la punta) y el tono variando de un pétalo a otro. Escribe dalia.png con tres paneles:
 * 8 pétalos con TRAZO, 8 con un botón sobre M y 13 con botón.
 */
public class Dalia {
    static final int W = 700, H = 700, CX = 350, CY = 350;
    static final Mezcla RGB = Mezcla.RGB;
    static int mezcla(int a, int b, double t) { return RGB.mezclar(a, b, Math.max(0, Math.min(1, t))); }

    static final int M = 0xFFFFF0B0;           // centro (crema)
    static final double DIST = 150;            // distancia de M al centro del lazo de cada pétalo

    static int tono(int k, int n) { return Color.HSBtoRGB((float) (0.93 + 0.20 * k / n) % 1f, 0.62f, 0.92f); }

    static int petalo(int k, int n, double t) {
        double w = Math.pow(Math.sin(Math.PI * t), 0.8);
        return mezcla(M, tono(k, n), w);
    }

    static Degradado radio(int c) { return x -> mezcla(c, 0xFF3A1236, 0.40 * x * x); }

    static Grafo dalia(int n) {
        Grafo c = new Grafo().mezcla(RGB);
        var m = c.nodo(CX, CY, 14).centro(M).radio(radio(M));
        double lr = DIST * Math.sin(Math.PI / n) * 1.25;        // radio del lazo
        double[] rad = { 0.55 * 0.35 * lr, 0.35 * lr, 0.55 * 0.35 * lr };   // estrecho - ancho (punta) - estrecho
        for (int k = 0; k < n; k++) {
            double th = Math.toRadians(-90) + 2 * Math.PI * k / n;
            double cx = CX + DIST * Math.cos(th), cy = CY + DIST * Math.sin(th);
            Grafo.Nodo prev = m;
            for (int j = 1; j <= 3; j++) {
                double fi = th + Math.PI + Math.toRadians(60 + 120 * (j - 1));
                double px = cx + lr * Math.cos(fi), py = cy + lr * Math.sin(fi);
                int col = petalo(k, n, j / 4.0);
                var nd = c.nodo(px, py, rad[j - 1]).centro(col).radio(radio(col));
                final int kk = k, jj = j;
                c.arista(prev, nd).espina(x -> petalo(kk, n, (jj - 1 + x) / 4.0));
                prev = nd;
            }
            final int kk = k;
            c.arista(prev, m).espina(x -> petalo(kk, n, (3 + x) / 4.0));
        }
        return c;
    }

    static BufferedImage pinta(int n, boolean boton) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFFF4F1EA);
        dalia(n).render(im, Opciones.POR_DEFECTO.conSolape(Solape.TRAZO));
        if (boton) {
            Graphics2D g = im.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double r = 26;
            g.setPaint(new RadialGradientPaint(new Point2D.Double(CX - 6, CY - 6), (float) (r * 1.2), new float[] { 0f, 0.7f, 1f },
                    new Color[] { new Color(0xFFF3B0), new Color(0xE8B13A), new Color(0x9A6A1E) }));
            g.fill(new Ellipse2D.Double(CX - r, CY - r, 2 * r, 2 * r));
            g.setColor(new Color(0x6B4513)); g.setStroke(new BasicStroke(1.5f));
            g.draw(new Ellipse2D.Double(CX - r, CY - r, 2 * r, 2 * r));
            g.dispose();
        }
        return im;
    }

    public static void main(String[] args) throws Exception {
        BufferedImage hoja = new BufferedImage(W * 3, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hoja.createGraphics();
        g.drawImage(pinta(8, false), 0, 0, null);
        g.drawImage(pinta(8, true), W, 0, null);
        g.drawImage(pinta(13, true), 2 * W, 0, null);
        g.dispose();
        ImageIO.write(hoja, "png", new File("dalia.png"));
        System.out.println("dalia.png (8 TRAZO | 8 + boton | 13 + boton)");
    }
}
