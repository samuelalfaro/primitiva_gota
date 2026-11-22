import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Estrella del bosque como árbol: cada pétalo es un tallo que se ensancha y se bifurca en tres lóbulos gruesos (nodo de
 * bifurcación de grado 4); sin lazos de vuelta a M, así que no hay salto de cierre. Escribe estrella_arbol.png:
 * 5 pétalos separados, 8 pétalos con lóbulos que se solapan, y esos 8 con un botón sobre M.
 */
public class EstrellaArbol {
    static final int W = 700, H = 700, CX = 350, CY = 350;
    static final Mezcla RGB = Mezcla.RGB;
    static int mezcla(int a, int b, double t) { return RGB.mezclar(a, b, Math.max(0, Math.min(1, t))); }
    static final int M = 0xFFEFE6A0;
    static int tono(int k, int n) { return Color.HSBtoRGB((float) (0.90 + 0.05 * Math.sin(2 * Math.PI * k / n)), 0.50f, 0.95f); }
    static Degradado radio(int c) { return x -> mezcla(c, 0xFF4A1440, 0.40 * x * x); }

    /** largo = distancia de los lóbulos a M; ancho = separación lateral de los lóbulos; rl = radio del lóbulo. */
    static Grafo estrella(int n, double largo, double ancho, double rl) {
        Grafo c = new Grafo().mezcla(RGB);
        var m = c.nodo(CX, CY, 9).centro(M).radio(radio(M));
        for (int k = 0; k < n; k++) {
            double th = -Math.PI / 2 + 2 * Math.PI * k / n, ct = Math.cos(th), st = Math.sin(th);
            int cl = tono(k, n), cm = mezcla(M, cl, 0.35), cf = mezcla(M, cl, 0.6);
            var s1 = c.nodo(CX + 0.35 * largo * ct, CY + 0.35 * largo * st, 5).centro(cm).radio(radio(cm));
            var f = c.nodo(CX + 0.62 * largo * ct, CY + 0.62 * largo * st, 7).centro(cf).radio(radio(cf));
            c.arista(m, s1).espina(x -> mezcla(M, cm, x));
            c.arista(s1, f).espina(x -> mezcla(cm, cf, x));
            for (int i = -1; i <= 1; i++) {
                double u = largo * (1 - 0.06 * Math.abs(i)), v = i * ancho;
                var l = c.nodo(CX + u * ct - v * st, CY + u * st + v * ct, rl).centro(cl).radio(radio(cl));
                c.arista(f, l).espina(x -> mezcla(cf, cl, x));
            }
        }
        return c;
    }

    static BufferedImage pinta(Grafo comp, boolean boton) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFFF4F1EA);
        comp.render(im, Opciones.POR_DEFECTO.conSolape(Solape.TRAZO));
        if (boton) {
            Graphics2D g = im.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double r = 20;
            g.setPaint(new RadialGradientPaint(new Point2D.Double(CX - 5, CY - 5), (float) (r * 1.2), new float[] { 0f, 0.7f, 1f },
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
        g.drawImage(pinta(estrella(5, 235, 52, 24), false), 0, 0, null);
        g.drawImage(pinta(estrella(8, 235, 58, 24), false), W, 0, null);
        g.drawImage(pinta(estrella(8, 235, 58, 24), true), 2 * W, 0, null);
        g.dispose();
        ImageIO.write(hoja, "png", new File("estrella_arbol.png"));
        System.out.println("estrella_arbol.png (5 | 8 | 8 + boton)");
    }
}
