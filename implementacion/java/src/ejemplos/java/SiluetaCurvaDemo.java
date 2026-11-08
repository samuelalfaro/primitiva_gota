import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Silueta doblada ({@code SiluetaCurva}): filas = casos (72/36 con δ = 0,6 y 0,9; 60/60 con δ = 0,9), columnas = giro del camino.
 * En rojo claro, los círculos del camino (su unión es la envolvente); en azul, el camino; en naranja, los extremos P1..P4. Si el contorno se pliega
 * (pliegue() <= 0) se marca. Escribe silueta_curva.png en el directorio actual.
 */
public class SiluetaCurvaDemo {
    static final int W = 300, H = 270, FONDO = 0xFFF4F1EA, TEXTO = 0xFF3A3A3A;

    static Point2D.Double[] camino(double giroGrados, double largo) {
        double th = Math.toRadians(giroGrados);
        double[] t1 = { Math.cos(-th / 2), Math.sin(-th / 2) }, t2 = { Math.cos(th / 2), Math.sin(th / 2) };
        double h = 1.0 / 3 / Math.max(Math.cos(th / 2), 0.35);
        Point2D.Double[] p = { new Point2D.Double(0, 0), new Point2D.Double(h * t1[0], h * t1[1]),
            new Point2D.Double(1 - h * t2[0], -h * t2[1]), new Point2D.Double(1, 0) };
        double l = 0, px = 0, py = 0;
        for (int i = 1; i <= 2000; i++) {
            double t = i / 2000.0, u = 1 - t;
            double x = u * u * u * p[0].x + 3 * u * u * t * p[1].x + 3 * u * t * t * p[2].x + t * t * t * p[3].x;
            double y = u * u * u * p[0].y + 3 * u * u * t * p[1].y + 3 * u * t * t * p[2].y + t * t * t * p[3].y;
            l += Math.hypot(x - px, y - py);
            px = x;
            py = y;
        }
        for (Point2D.Double q : p) q.setLocation(q.x * largo / l, q.y * largo / l);
        return p;
    }

    static void celda(Graphics2D g, double r1, double r2, double delta, double giro) {
        Point2D.Double[] p = camino(giro, delta * (r1 + r2));
        SiluetaCurva cu = SiluetaCurva.de(p[0], p[1], p[2], p[3], r1, r2);
        Path2D.Double forma = cu.forma();
        Rectangle2D b = forma.getBounds2D();
        double esc = Math.min((W - 30) / (b.getWidth() + 30), (H - 60) / (b.getHeight() + 30));
        AffineTransform at = new AffineTransform();
        at.translate(W / 2.0, H / 2.0 + 14);
        at.scale(esc, esc);
        at.translate(-b.getCenterX(), -b.getCenterY());
        // círculos del camino (su unión es la envolvente de la gota)
        g.setColor(new Color(0xC0, 0x39, 0x2B, 0x90));
        g.setStroke(new BasicStroke(0.8f));
        for (int i = 0; i <= 300; i += 12) {
            double t = i / 300.0, u = 1 - t;
            double x = u * u * u * p[0].x + 3 * u * u * t * p[1].x + 3 * u * t * t * p[2].x + t * t * t * p[3].x;
            double y = u * u * u * p[0].y + 3 * u * u * t * p[1].y + 3 * u * t * t * p[2].y + t * t * t * p[3].y;
            double r = r1 + (r2 - r1) * t;
            g.draw(at.createTransformedShape(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r)));
        }
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(0x1A, 0x1A, 0x22, 0x14));
        g.fill(at.createTransformedShape(forma));
        g.setColor(new Color(cu.plegada() ? 0xC0392B : 0x222230));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(at.createTransformedShape(forma));
        g.setStroke(new BasicStroke(1f));
        Path2D.Double camino = new Path2D.Double();
        camino.moveTo(p[0].x, p[0].y);
        camino.curveTo(p[1].x, p[1].y, p[2].x, p[2].y, p[3].x, p[3].y);
        g.setColor(new Color(0x2980B9));
        g.draw(at.createTransformedShape(camino));
        g.setColor(new Color(0xE67E22));
        for (Point2D.Double q : cu.extremos()) {
            Point2D a = at.transform(q, null);
            g.fill(new Ellipse2D.Double(a.getX() - 2.5, a.getY() - 2.5, 5, 5));
        }
        g.setColor(new Color(TEXTO));
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString(String.format("%s/%s, δ = %s, giro %.0f°", fmt(r1), fmt(r2), fmt(delta), giro), 10, 18);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.drawString(String.format("psi = (%.1f°, %.1f°)  pliegue %.2f%s", Math.toDegrees(cu.recta().angulos().psi1()), Math.toDegrees(cu.recta().angulos().psi2()),
            cu.pliegue(), cu.plegada() ? "  ¡PLEGADA!" : ""), 10, 33);
    }

    static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v).replace('.', ','); }

    public static void main(String[] args) throws Exception {
        double[][] filas = { { 72, 36, 0.6 }, { 72, 36, 0.9 }, { 60, 60, 0.9 } };
        double[] giros = { 0, 30, 60, 90 };
        BufferedImage img = new BufferedImage(giros.length * W, filas.length * H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(FONDO));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int i = 0; i < filas.length; i++)
            for (int j = 0; j < giros.length; j++) {
                Graphics2D gc = (Graphics2D) g.create(j * W, i * H, W, H);
                celda(gc, filas[i][0], filas[i][1], filas[i][2], giros[j]);
                gc.dispose();
            }
        g.dispose();
        ImageIO.write(img, "png", new File("silueta_curva.png"));
    }
}
