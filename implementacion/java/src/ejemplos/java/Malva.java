import pintado.Plotter;
import geometria.composicion.Grafo;
import coordenadas.Solape;
import color.Mezcla;
import color.Degradado;
import coordenadas.Opciones;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Malva: cinco pétalos que salen del centro M y vuelven a él (nodo de grado 10), en un solo Grafo. Los pétalos
 * se solapan entre sí como en la flor real. Escribe malva.png con tres paneles: PLANO, TRAZO y TRAZO con un botón
 * dibujado encima de M (un adorno del ejemplo, no una regla de la composición: tapa las uniones como salgan).
 */
public class Malva {
    static final int W = 700, H = 700, CX = 350, CY = 350;
    static final Mezcla RGB = Mezcla.RGB;
    static int mezcla(int a, int b, double t) { return RGB.mezclar(a, b, Math.max(0, Math.min(1, t))); }

    static final int M = 0xFFF6E7A8;           // centro de la flor (crema)
    static final int[] TONO = { 0xFFD94F9B, 0xFFB45AD6, 0xFFE87AA8, 0xFF9B6BE0, 0xFFE0558A };

    /** Color del pétalo k en la posición t del lazo (0 y 1 = M). */
    static int petalo(int k, double t) {
        double w = Math.pow(Math.sin(Math.PI * t), 0.8);
        return mezcla(M, TONO[k], w);
    }

    static Degradado radio(int c) { return x -> mezcla(c, 0xFF3A1236, 0.40 * x * x); }

    static Grafo malva() {
        Grafo c = new Grafo().mezcla(RGB);
        var m = c.nodo(CX, CY, 20).centro(M).radio(radio(M));
        for (int k = 0; k < 5; k++) {
            double th = Math.toRadians(-90 + 72 * k);
            double cx = CX + 128 * Math.cos(th), cy = CY + 128 * Math.sin(th);
            Grafo.Nodo prev = m;
            for (int j = 1; j <= 3; j++) {
                double fi = th + Math.PI + Math.toRadians(60 + 120 * (j - 1));
                double px = cx + 82 * Math.cos(fi), py = cy + 82 * Math.sin(fi);
                int col = petalo(k, j / 4.0);
                var nd = c.nodo(px, py, 22 - 3 * (j == 2 ? 0 : 1)).centro(col).radio(radio(col));
                final int kk = k, jj = j;
                c.arista(prev, nd).espina(x -> petalo(kk, (jj - 1 + x) / 4.0));
                prev = nd;
            }
            final int kk = k;
            c.arista(prev, m).espina(x -> petalo(kk, (3 + x) / 4.0));
        }
        return c;
    }

    static BufferedImage pinta(Solape solape, boolean boton) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFFF4F1EA);
        Plotter.raster(im).plot(malva(), Opciones.POR_DEFECTO.conSolape(solape));
        if (boton) {
            Graphics2D g = im.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double r = 36;
            g.setPaint(new RadialGradientPaint(new Point2D.Double(CX - 8, CY - 8), (float) r * 1.2f, new float[] { 0f, 0.7f, 1f },
                    new Color[] { new Color(0xFFF3B0), new Color(0xE8B13A), new Color(0x9A6A1E) }));
            g.fill(new Ellipse2D.Double(CX - r, CY - r, 2 * r, 2 * r));
            g.setColor(new Color(0x6B4513)); g.setStroke(new BasicStroke(1.5f));
            g.draw(new Ellipse2D.Double(CX - r, CY - r, 2 * r, 2 * r));
            g.setColor(new Color(0x7A4E12));
            for (int[] o : new int[][] { { -7, -7 }, { 7, -7 }, { -7, 7 }, { 7, 7 } }) g.fill(new Ellipse2D.Double(CX + o[0] - 2.5, CY + o[1] - 2.5, 5, 5));
            g.dispose();
        }
        return im;
    }

    public static void main(String[] args) throws Exception {
        BufferedImage hoja = new BufferedImage(W * 3, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hoja.createGraphics();
        g.drawImage(pinta(Solape.PLANO, false), 0, 0, null);
        g.drawImage(pinta(Solape.TRAZO, false), W, 0, null);
        g.drawImage(pinta(Solape.TRAZO, true), 2 * W, 0, null);
        g.dispose();
        ImageIO.write(hoja, "png", new File("malva.png"));
        System.out.println("malva.png (PLANO | TRAZO | TRAZO + boton)");
    }
}
