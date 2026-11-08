import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Caminos complejos que fuerzan el pliegue (S, horquilla, lazo, caracol), con el relajo LOCAL (según la curvatura local del camino):
 * columnas 1 tal como está, 2 relaja el exterior, 3 el interior, 4 ambos. Rojo claro: los círculos del camino. Regla del trazo: negro = visible (los círculos posteriores tapan el contorno anterior), gris punteado = tapado. Escribe silueta_trazo.png.
 */
public class SiluetaTrazoDemo {
    static final int W = 320, H = 300, FONDO = 0xFFF4F1EA, TEXTO = 0xFF3A3A3A;
    static final String[] NOMBRES = { "1: tal como está", "2: relaja el exterior", "3: relaja el interior", "4: relaja ambos" };
    static final SiluetaCurva.Relajo[] RELAJOS = { SiluetaCurva.Relajo.NINGUNO, SiluetaCurva.Relajo.EXTERIOR, SiluetaCurva.Relajo.INTERIOR, SiluetaCurva.Relajo.AMBOS };

    record Caso(String nombre, double[] ctl, double r1, double r2, double delta) {}

    static Point2D.Double[] escala(double[] c, double largo) {
        Point2D.Double[] p = new Point2D.Double[4];
        for (int i = 0; i < 4; i++) p[i] = new Point2D.Double(c[2 * i], c[2 * i + 1]);
        double l = 0, px = 0, py = 0;
        for (int i = 1; i <= 4000; i++) {
            double t = i / 4000.0, u = 1 - t;
            double x = u * u * u * p[0].x + 3 * u * u * t * p[1].x + 3 * u * t * t * p[2].x + t * t * t * p[3].x;
            double y = u * u * u * p[0].y + 3 * u * u * t * p[1].y + 3 * u * t * t * p[2].y + t * t * t * p[3].y;
            l += Math.hypot(x - px, y - py);
            px = x;
            py = y;
        }
        for (Point2D.Double q : p) q.setLocation(q.x * largo / l, q.y * largo / l);
        return p;
    }

    static void celda(Graphics2D g, Caso c, int col) {
        Point2D.Double[] p = escala(c.ctl(), c.delta() * (c.r1() + c.r2()));
        SiluetaCurva cu = SiluetaCurva.de(p[0], p[1], p[2], p[3], c.r1(), c.r2(), Silueta.Conector.ELIPSE, RELAJOS[col], SiluetaCurva.Metodo.LOCAL);
        Path2D.Double forma = cu.forma();
        Rectangle2D b = SiluetaCurva.de(p[0], p[1], p[2], p[3], c.r1(), c.r2()).forma().getBounds2D().createUnion(forma.getBounds2D());
        double esc = Math.min((W - 30) / (b.getWidth() + 20), (H - 60) / (b.getHeight() + 20));
        AffineTransform at = new AffineTransform();
        at.translate(W / 2.0, H / 2.0 + 14);
        at.scale(esc, esc);
        at.translate(-b.getCenterX(), -b.getCenterY());
        g.setColor(new Color(0xC0, 0x39, 0x2B, 0x70));
        g.setStroke(new BasicStroke(0.6f));
        for (int i = 0; i <= 400; i += 8) {
            double t = i / 400.0, u = 1 - t;
            double x = u * u * u * p[0].x + 3 * u * u * t * p[1].x + 3 * u * t * t * p[2].x + t * t * t * p[3].x;
            double y = u * u * u * p[0].y + 3 * u * u * t * p[1].y + 3 * u * t * t * p[2].y + t * t * t * p[3].y;
            double r = c.r1() + (c.r2() - c.r1()) * t;
            g.draw(at.createTransformedShape(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r)));
        }
        SiluetaCurva.Trazo tz = cu.trazo();
        g.setColor(new Color(0x1A, 0x1A, 0x22, 0x14));
        g.fill(at.createTransformedShape(forma));
        g.setColor(new Color(0x90, 0x90, 0xA0));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[] { 3f, 3f }, 0f));
        g.draw(at.createTransformedShape(tz.oculto()));
        g.setColor(new Color(0x111118));
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(at.createTransformedShape(tz.visible()));
        g.setStroke(new BasicStroke(1.2f));
        Path2D.Double camino = new Path2D.Double();
        camino.moveTo(p[0].x, p[0].y);
        camino.curveTo(p[1].x, p[1].y, p[2].x, p[2].y, p[3].x, p[3].y);
        g.setColor(new Color(0x2980B9));
        g.draw(at.createTransformedShape(camino));
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(0xE67E22));
        for (Point2D.Double q : cu.extremos()) {
            Point2D a = at.transform(q, null);
            g.fill(new Ellipse2D.Double(a.getX() - 2.5, a.getY() - 2.5, 5, 5));
        }
        g.setColor(new Color(TEXTO));
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString(c.nombre() + " — " + NOMBRES[col], 10, 18);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.drawString(String.format("%s/%s, δ = %s, giro total %.0f°", SiluetaCurvaDemo.fmt(c.r1()), SiluetaCurvaDemo.fmt(c.r2()), SiluetaCurvaDemo.fmt(c.delta()), Math.toDegrees(cu.giro())), 10, 33);
    }

    public static void main(String[] args) throws Exception {
        Caso[] casos = {
            new Caso("S", new double[] { 0, 0, 0.4, 0.8, 0.6, -0.8, 1, 0 }, 60, 40, 1.1),
            new Caso("horquilla", new double[] { 0, 0, 0.1, -1.1, 0.9, -1.1, 1, 0 }, 60, 60, 0.9),
            new Caso("lazo", new double[] { 0, 0, 1.5, 0.9, -0.5, 0.9, 1, 0 }, 50, 40, 1.3),
            new Caso("caracol", new double[] { 0, 0, 0.9, 0, 1.0, 0.9, 0.3, 0.8 }, 70, 25, 1.2) };
        BufferedImage img = new BufferedImage(4 * W, casos.length * H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(FONDO));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int i = 0; i < casos.length; i++)
            for (int j = 0; j < 4; j++) {
                Graphics2D gc = (Graphics2D) g.create(j * W, i * H, W, H);
                celda(gc, casos[i], j);
                gc.dispose();
            }
        g.dispose();
        ImageIO.write(img, "png", new File("silueta_trazo.png"));
    }
}
