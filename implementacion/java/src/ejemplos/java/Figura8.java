import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import javax.imageio.ImageIO;
import java.io.File;

public class Figura8 {
    static Composicion ocho(double rC, double rT, double sol) {
        Composicion c = new Composicion().suavizado(0);
        double a = 70, b = 55;
        var C = c.nodo(240, 150, rC).centro(0xFFFBF3E0).sectores(0xFF3A7FD6, 0xFF4FA36B, 0xFFE8523A, 0xFF8E44AD);
        var L = c.nodo(380, 150, rT).centro(0xFFFBF3E0).lados(0xFF4FA36B, 0xFFF2C14E);   // dentro del lóbulo, fuera
        var R = c.nodo(100, 150, rT).centro(0xFFFBF3E0).lados(0xFFF2C14E, 0xFF8E44AD);
        // Lóbulo derecho: C -> L por arriba, L -> C por abajo. Lóbulo izquierdo: C -> R por arriba, R -> C por abajo.
        c.arista(C, L).tiradores(new Gota.Vec(a * 0.6, -b * 0.6), new Gota.Vec(0, -60));
        c.arista(L, C).tiradores(new Gota.Vec(0, 60), new Gota.Vec(a * 0.6, b * 0.6));
        c.arista(C, R).tiradores(new Gota.Vec(-a * 0.6, -b * 0.6), new Gota.Vec(0, -60));
        c.arista(R, C).tiradores(new Gota.Vec(0, 60), new Gota.Vec(-a * 0.6, b * 0.6));
        return c;
    }
    public static void main(String[] x) throws Exception {
        BufferedImage out = new BufferedImage(960, 300, BufferedImage.TYPE_INT_ARGB);
        int i = 0;
        for (double[] rr : new double[][] { {18, 18}, {26, 14} }) {
            Composicion c = ocho(rr[0], rr[1], 0);
            BufferedImage img = new BufferedImage(480, 300, BufferedImage.TYPE_INT_ARGB);
            java.util.Arrays.fill(((DataBufferInt) img.getRaster().getDataBuffer()).getData(), 0xFFF4F1EA);
            long t0 = System.nanoTime();
            c.render(img);
            System.out.printf("r=%s render %.0f ms; centro dentro=%b hueco derecho=%b hueco izquierdo=%b%n", java.util.Arrays.toString(rr),
                (System.nanoTime() - t0) / 1e6, c.contiene(240, 150), !c.contiene(310, 150), !c.contiene(170, 150));
            out.getGraphics().drawImage(img, i++ * 480, 0, null);
        }
        ImageIO.write(out, "png", new File("ocho.png"));
    }
}
