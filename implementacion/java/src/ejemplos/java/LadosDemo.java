import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/** La textura de referencia con fondos a cada lado del eje, sobre el caso básico (r 3 y 2, distancia 7) y una gota curva. */
public class LadosDemo {
    public static void main(String[] args) throws Exception {
        Texturas.Textura tex = Texturas.referenciaLados();
        Gota basica = Gota.lineal(new Point2D.Double(130, 130), 90, new Point2D.Double(130 + 7 * 30, 130), 60, Gota.Perfil.recto());
        Gota curva = Gota.curva(new Point2D.Double(70, 70), 30, new Gota.Vec(130, 0), new Point2D.Double(300, 250), 60,
                new Gota.Vec(0, -130), Gota.Perfil.recto());
        BufferedImage out = new BufferedImage(880, 330, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) out.getRaster().getDataBuffer()).getData(), 0xFFF4F1EA);
        Gota[] gs = { basica, curva };
        for (int k = 0; k < 2; k++) {
            BufferedImage p = new BufferedImage(440, 330, BufferedImage.TYPE_INT_ARGB);
            java.util.Arrays.fill(((DataBufferInt) p.getRaster().getDataBuffer()).getData(), 0xFFF4F1EA);
            gs[k].render(p, Rellenos.tira(tex));
            out.getGraphics().drawImage(p, k * 440, 0, null);
        }
        ImageIO.write(out, "png", new File("lados_demo.png"));
    }
}
