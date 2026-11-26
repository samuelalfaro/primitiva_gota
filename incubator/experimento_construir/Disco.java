import forma.Gota;
import forma.Perfil;
import geometria.composicion.Pintada;
import color.Colores;
import pintado.Plotter;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import javax.imageio.ImageIO;
import java.io.File;

/** Gota de centros coincidentes y radios iguales: ¿círculo, y con qué orientación de coordenadas? */
public class Disco {
    static BufferedImage lienzo() {
        BufferedImage im = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFFFFFFFF);
        return im;
    }
    public static void main(String[] a) throws Exception {
        int R = 0xFFD02020, B = 0xFF2040D0, V = 0xFF20A040, Y = 0xFFE0C020;
        Point2D.Double p = new Point2D.Double(150, 150);
        Gota g = Gota.lineal(p, 100, new Point2D.Double(150, 150), 100, Perfil.recto());
        Gota h = Gota.lineal(p, 100, new Point2D.Double(150.5, 150), 100, Perfil.recto());
        BufferedImage im = lienzo(), im2 = lienzo();
        Plotter.raster(im).plot(Pintada.de(g, Colores.cuatroColores(R, B, V, Y, color.Mezcla.RGB)));
        Plotter.raster(im2).plot(Pintada.de(h, Colores.cuatroColores(R, B, V, Y, color.Mezcla.RGB)));
        int[] d = ((DataBufferInt) im.getRaster().getDataBuffer()).getData(), e = ((DataBufferInt) im2.getRaster().getDataBuffer()).getData();
        int tinta = 0, area = 0, dif = 0, sil = 0;
        for (int i = 0; i < d.length; i++) { if (d[i] != 0xFFFFFFFF) area++; if ((d[i] == 0xFFFFFFFF) != (e[i] == 0xFFFFFFFF)) sil++; if (d[i] != e[i]) dif++; }
        System.out.printf("área gota coincidente: %d (círculo ideal: %.0f); silueta distinta frente a separación 0.5: %d; color distinto: %d%n", area, Math.PI * 100 * 100, sil, dif);
        for (double[] q : new double[][] { {250,150}, {50,150}, {150,50}, {150,250} })
            System.out.printf("pixel (%.0f,%.0f): %08X%n", q[0], q[1], d[(int) q[1] * 300 + (int) q[0]]);
        ImageIO.write(im, "png", new File("disco_coincidente.png"));
        ImageIO.write(im2, "png", new File("disco_separado.png"));
    }
}
