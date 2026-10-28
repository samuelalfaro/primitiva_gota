import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/** Estrella Y y cadena con el modelo único: color lateral (RGB y LCH) y textura de referencia sobre las gotas. */
public class EstrellaTapas {
    static int[] datos(BufferedImage i) { return ((DataBufferInt) i.getRaster().getDataBuffer()).getData(); }

    public static void main(String[] args) throws Exception {
        int W = 440, H = 400, FONDO = 0xFFF4F1EA;
        Texturas.Textura tex = Texturas.referencia();
        Composicion[] col = {
                ComposicionPruebas.estrella(0).mezcla(Rellenos.RGB),
                ComposicionPruebas.estrella(18).mezcla(Rellenos.LCH),
                ComposicionPruebas.cadena().mezcla(Rellenos.RGB_LINEAL) };
        Composicion[] txt = { ComposicionPruebas.estrella(0), ComposicionPruebas.cadena() };
        BufferedImage out = new BufferedImage(W * 3, H * 2, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(datos(out), FONDO);
        for (int c = 0; c < 3; c++) {
            BufferedImage p = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            java.util.Arrays.fill(datos(p), FONDO);
            col[c].render(p);
            out.getGraphics().drawImage(p, c * W, 0, null);
        }
        for (int c = 0; c < 2; c++) {
            BufferedImage p = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            java.util.Arrays.fill(datos(p), FONDO);
            txt[c].render(p, tex);
            out.getGraphics().drawImage(p, c * W, H, null);
        }
        ImageIO.write(out, "png", new File("estrella_tapas.png"));
        System.out.println("ok");
    }
}
