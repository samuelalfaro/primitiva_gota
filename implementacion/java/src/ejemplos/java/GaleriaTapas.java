import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/** Galería de tapas: textura de referencia y color lateral, tira con tapas de semidisco (y discoCapsula como referencia), en una gota gruesa y en la estrella. */
public class GaleriaTapas {
    static Point2D.Double p(double x, double y) { return new Point2D.Double(x, y); }

    public static void main(String[] args) throws Exception {
        Texturas.Textura tex = Texturas.referencia();
        int W = 440, H = 400, FONDO = 0xFFF4F1EA;
        Gota gruesa = Gota.curva(p(80, 90), 34, new Gota.Vec(130, 0), p(300, 290), 62, new Gota.Vec(0, -130), Gota.Perfil.recto());
        Rellenos.Lateral a = new Rellenos.Lateral(0xFFD7263D, 0xFFFFE9A8, 0xFF2D6CDF);
        Rellenos.Lateral b = new Rellenos.Lateral(0xFF1FA39A, 0xFFE8F5D0, 0xFFF28E2B);
        Composicion estT = ComposicionPruebas.estrella(0);

        Object[][] filas = {
                { "gruesa-textura", new Gota.Relleno[] { Rellenos.tira(tex), Rellenos.discoCapsula(tex), null } },
                { "gruesa-color", new Gota.Relleno[] {
                        Rellenos.cadenaLateral(a, b, Rellenos.RGB),
                        Rellenos.cadenaLateral(a, b, Rellenos.RGB_LINEAL),
                        Rellenos.cadenaLateral(a, b, Rellenos.LCH) } },
                { "estrella-textura", new Gota.Relleno[] { Rellenos.tira(tex), null, null } },
        };
        BufferedImage out = new BufferedImage(W * 3, H * 3, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) out.getRaster().getDataBuffer()).getData(), FONDO);
        Graphics2D g2 = out.createGraphics();
        for (int f = 0; f < 3; f++) {
            Gota.Relleno[] rs = (Gota.Relleno[]) filas[f][1];
            for (int c = 0; c < 3; c++) {
                if (rs[c] == null) continue;
                BufferedImage panel = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
                java.util.Arrays.fill(((DataBufferInt) panel.getRaster().getDataBuffer()).getData(), FONDO);
                if (f < 2) gruesa.render(panel, rs[c]);
                else for (Gota g : estT.gotas()) g.render(panel, rs[c]);
                g2.drawImage(panel, c * W, f * H, null);
            }
        }
        g2.dispose();
        ImageIO.write(out, "png", new File("galeria_tapas.png"));
        System.out.println("ok");
    }
}
