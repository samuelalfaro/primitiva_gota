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
 * Experimento: los arcos elípticos de la silueta con camino curvo, y su relajo por una spline por P1, el contacto de la envolvente con el
 * círculo medio y P2 ({@code SiluetaCurva.Relajo}). Columnas: 1 tal como está, 2 relaja solo el conector exterior a la curva, 3 solo el interior,
 * 4 ambos. Filas: gotas distintas. Rojo claro: los círculos del camino. Escribe silueta_relajo.png en el directorio actual.
 */
public class SiluetaRelajoDemo {
    static final int W = 300, H = 280, FONDO = 0xFFF4F1EA, TEXTO = 0xFF3A3A3A;
    static final String[] NOMBRES = { "1: tal como está", "2: relaja el exterior", "3: relaja el interior", "4: relaja ambos" };
    static final SiluetaCurva.Relajo[] RELAJOS = { SiluetaCurva.Relajo.NINGUNO, SiluetaCurva.Relajo.EXTERIOR, SiluetaCurva.Relajo.INTERIOR, SiluetaCurva.Relajo.AMBOS };

    static void celda(Graphics2D g, double r1, double r2, double delta, double giro, int caso) {
        Point2D.Double[] p = SiluetaCurvaDemo.camino(giro, delta * (r1 + r2));
        SiluetaCurva cu = SiluetaCurva.de(p[0], p[1], p[2], p[3], r1, r2, Silueta.Conector.ELIPSE, RELAJOS[caso]);
        Path2D.Double forma = cu.forma();
        SiluetaCurva ref = SiluetaCurva.de(p[0], p[1], p[2], p[3], r1, r2);
        Rectangle2D b = ref.forma().getBounds2D().createUnion(forma.getBounds2D());
        double esc = Math.min((W - 30) / (b.getWidth() + 20), (H - 60) / (b.getHeight() + 20));
        AffineTransform at = new AffineTransform();
        at.translate(W / 2.0, H / 2.0 + 14);
        at.scale(esc, esc);
        at.translate(-b.getCenterX(), -b.getCenterY());
        g.setColor(new Color(0xC0, 0x39, 0x2B, 0x80));
        g.setStroke(new BasicStroke(0.7f));
        for (int i = 0; i <= 300; i += 12) {
            double t = i / 300.0, u = 1 - t;
            double x = u * u * u * p[0].x + 3 * u * u * t * p[1].x + 3 * u * t * t * p[2].x + t * t * t * p[3].x;
            double y = u * u * u * p[0].y + 3 * u * u * t * p[1].y + 3 * u * t * t * p[2].y + t * t * t * p[3].y;
            double r = r1 + (r2 - r1) * t;
            g.draw(at.createTransformedShape(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r)));
        }
        g.setColor(new Color(0x1A, 0x1A, 0x22, 0x14));
        g.fill(at.createTransformedShape(forma));
        g.setColor(new Color(0x222230));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(at.createTransformedShape(forma));
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(0xE67E22));
        for (Point2D.Double q : cu.extremos()) {
            Point2D a = at.transform(q, null);
            g.fill(new Ellipse2D.Double(a.getX() - 2.5, a.getY() - 2.5, 5, 5));
        }
        g.setColor(new Color(TEXTO));
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString(NOMBRES[caso], 10, 18);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.drawString(String.format("%s/%s, δ = %s, giro %.0f°", SiluetaCurvaDemo.fmt(r1), SiluetaCurvaDemo.fmt(r2), SiluetaCurvaDemo.fmt(delta), giro), 10, 33);
    }

    public static void main(String[] args) throws Exception {
        double[][] filas = { { 72, 36, 0.9, 60 }, { 72, 36, 0.6, 60 }, { 60, 60, 0.9, 90 }, { 72, 36, 0.9, 30 } };
        BufferedImage img = new BufferedImage(4 * W, filas.length * H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(FONDO));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int i = 0; i < filas.length; i++)
            for (int j = 0; j < 4; j++) {
                Graphics2D gc = (Graphics2D) g.create(j * W, i * H, W, H);
                celda(gc, filas[i][0], filas[i][1], filas[i][2], filas[i][3], j);
                gc.dispose();
            }
        g.dispose();
        ImageIO.write(img, "png", new File("silueta_relajo.png"));
    }
}
