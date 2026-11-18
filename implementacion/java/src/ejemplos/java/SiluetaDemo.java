import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Silueta de tapas y conector elíptico o biarco (clase {@code Silueta}) para los casos de la tabla del documento de formulación: de dos
 * círculos separados (la silueta de siempre) a círculos idénticos (la X). En naranja, los radios libres a los extremos de los
 * arcos (P1..P4). Escribe silueta_elipses.png (o silueta_biarcos.png con el argumento «biarco») en el directorio actual.
 */
public class SiluetaDemo {
    record Caso(String titulo, double r1, double r2, double d) {}

    static Silueta.Conector conector = Silueta.Conector.ELIPSE;

    static final int W = 300, H = 250, FONDO = 0xFFF4F1EA, TEXTO = 0xFF3A3A3A;

    static List<Caso> casos() {
        return List.of(
            new Caso("72/36, d = 150 (A)", 72, 36, 150), new Caso("72/36, d = 108 = r1 + r2", 72, 36, 108),
            new Caso("72/36, d = 80", 72, 36, 80), new Caso("72/36, d = 60", 72, 36, 60),
            new Caso("72/36, d = 36 = r1 - r2", 72, 36, 36), new Caso("72/36, d = 20", 72, 36, 20),
            new Caso("72/36, d = 4", 72, 36, 4), new Caso("72/36, d = 0", 72, 36, 0),
            new Caso("tu caso: 4/1,5, d = 2", 4, 1.5, 2), new Caso("1/0,9, d = 0", 1, 0.9, 0),
            new Caso("radios iguales 60, d = 120", 60, 60, 120), new Caso("radios iguales 60, d = 50", 60, 60, 50),
            new Caso("idénticos, d = 0 (la X)", 60, 60, 0), new Caso("36/72, d = 30 (r1 < r2)", 36, 72, 30));
    }

    static void celda(Graphics2D g, Caso c) {
        Silueta s = Silueta.de(new Point2D.Double(0, 0), c.r1(), new Point2D.Double(c.d(), 0), c.r2(), 1, 0, conector);
        Path2D.Double forma = s.forma();
        Rectangle2D b = new Rectangle2D.Double(-c.r1(), -c.r1(), c.d() + c.r1() + c.r2(), 2 * c.r1()).createUnion(forma.getBounds2D());
        double esc = Math.min((W - 40) / b.getWidth(), (H - 70) / b.getHeight());
        AffineTransform at = new AffineTransform();
        at.translate(W / 2.0, H / 2.0 + 14);
        at.scale(esc, -esc);                          // y matemático hacia arriba
        at.translate(-b.getCenterX(), -b.getCenterY());
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(0xBBBBBB));
        g.draw(at.createTransformedShape(new Ellipse2D.Double(-c.r1(), -c.r1(), 2 * c.r1(), 2 * c.r1())));
        g.draw(at.createTransformedShape(new Ellipse2D.Double(c.d() - c.r2(), -c.r2(), 2 * c.r2(), 2 * c.r2())));
        g.setColor(new Color(0x1A, 0x1A, 0x22, 0x14));
        g.fill(at.createTransformedShape(forma));
        g.setColor(new Color(0x222230));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(at.createTransformedShape(forma));
        g.setStroke(new BasicStroke(1f));
        Point2D.Double[] p = s.extremos();
        double[] cx = { 0, 0, c.d(), c.d() };
        for (int i = 0; i < 4; i++) {
            Point2D a = at.transform(new Point2D.Double(cx[i], 0), null), q = at.transform(p[i], null);
            g.setColor(new Color(0xE67E22));
            g.draw(new Line2D.Double(a, q));
            g.fill(new Ellipse2D.Double(q.getX() - 2.5, q.getY() - 2.5, 5, 5));
        }
        Point2D a1 = at.transform(new Point2D.Double(0, 0), null), a2 = at.transform(new Point2D.Double(c.d(), 0), null);
        g.setColor(new Color(0xC0392B));
        g.fill(new Ellipse2D.Double(a1.getX() - 3, a1.getY() - 3, 6, 6));
        g.setColor(new Color(0x1F5FA8));
        g.fill(new Ellipse2D.Double(a2.getX() - 3, a2.getY() - 3, 6, 6));
        g.setColor(new Color(TEXTO));
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString(c.titulo(), 10, 18);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.drawString(String.format("psi1 = %.1f°, psi2 = %.1f°  [%s]", Math.toDegrees(s.angulos().psi1()), Math.toDegrees(s.angulos().psi2()), s.conector().name().toLowerCase()), 10, 33);
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equalsIgnoreCase("biarco")) conector = Silueta.Conector.BIARCO;
        List<Caso> casos = casos();
        int cols = 4, filas = (casos.size() + cols - 1) / cols;
        BufferedImage img = new BufferedImage(cols * W, filas * H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(FONDO));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int i = 0; i < casos.size(); i++) {
            Graphics2D gc = (Graphics2D) g.create((i % cols) * W, (i / cols) * H, W, H);
            celda(gc, casos.get(i));
            gc.dispose();
        }
        g.dispose();
        ImageIO.write(img, "png", new File(conector == Silueta.Conector.ELIPSE ? "silueta_elipses.png" : "silueta_biarcos.png"));
    }
}
