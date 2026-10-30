import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/** Ajuste de la mezcla polar normal -> contra en las tapas: 0.25, 0.5 (lineal) y 0.75. */
public class ArcoDemo {
    public static void main(String[] args) throws Exception {
        Colores.Lateral a = new Colores.Lateral(0xFFD7263D, 0xFFFFE9A8, 0xFF2D6CDF);
        Colores.Lateral b = new Colores.Lateral(0xFF1FA39A, 0xFFE8F5D0, 0xFFF28E2B);
        Gota g = Gota.lineal(new Point2D.Double(130, 120), 90, new Point2D.Double(310, 120), 60, Gota.Perfil.recto());
        BufferedImage out = new BufferedImage(1320, 250, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) out.getRaster().getDataBuffer()).getData(), 0xFFF4F1EA);
        double[] s = { 0.25, 0.5, 0.75 };
        for (int k = 0; k < 3; k++) {
            BufferedImage p = new BufferedImage(440, 250, BufferedImage.TYPE_INT_ARGB);
            java.util.Arrays.fill(((DataBufferInt) p.getRaster().getDataBuffer()).getData(), 0xFFF4F1EA);
            g.render(p, Colores.cadenaLateral(a.conAjusteArco(s[k]), b.conAjusteArco(s[k]), Mezcla.RGB));
            out.getGraphics().drawImage(p, k * 440, 0, null);
        }
        ImageIO.write(out, "png", new File("arco_ajuste.png"));
    }
}
