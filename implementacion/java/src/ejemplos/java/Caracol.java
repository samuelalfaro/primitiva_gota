import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/** Dibujo libre solo con la API de Composicion: un caracol sobre una hoja (concha en espiral como cadena de nodos de radio decreciente). */
public class Caracol {
    static final int W = 640, H = 480, FONDO = 0xFFF4F1EA;

    static Composicion cuerpo() {
        Composicion c = new Composicion();
        var cola = c.nodo(150, 396, 3).centro(0xFFE8C9A0).sectores(0xFFB98B5E);
        var lomo = c.nodo(262, 392, 21).centro(0xFFF3DDB8).lados(0xFFB98B5E, 0xFFE8C9A0).ajusteLateral(0.35);
        var cuello = c.nodo(376, 372, 25).centro(0xFFF3DDB8).lados(0xFFB98B5E, 0xFFE8C9A0).ajusteLateral(0.35);
        var cabeza = c.nodo(452, 330, 27).centro(0xFFF7E6C8).aspecto(1.15).sectores(0xFFC99B6B);
        c.arista(cola, lomo).tiradores(new Gota.Vec(40, 0), null);
        c.arista(lomo, cuello);
        c.arista(cuello, cabeza).tiradores(new Gota.Vec(30, -2), new Gota.Vec(-4, 34));
        return c;
    }

    static Composicion antena(double x0, double y0, double x1, double y1, double vx, double vy) {
        Composicion c = new Composicion();
        var a = c.nodo(x0, y0, 5).color(0xFFD9AE80);
        var b = c.nodo(x1, y1, 8).centro(0xFFF1D7B0).sectores(0xFF8D5B3A);
        c.arista(a, b).tiradores(new Gota.Vec(vx, vy), null);
        return c;
    }

    /** Concha: espiral logarítmica de nodos, del grande exterior al pequeño interior, con sectores alternos. */
    static Composicion concha(double cx, double cy) {
        Composicion c = new Composicion();
        int[][] tono = { { 0xFFD2691E, 0xFFE9954A, 0xFF8B4513 }, { 0xFFB8541A, 0xFFF0B070, 0xFF7A3A12 } };
        Composicion.Nodo prev = null;
        int n = 7;
        for (int i = 0; i < n; i++) {
            double th = Math.PI * 0.95 + i * 0.95, rho = 70 * Math.exp(-0.27 * (th - Math.PI * 0.95));
            double r = 14 + 40 * Math.exp(-0.27 * i * 0.95 * 1.25);
            int[] t = tono[i % 2];
            var nd = c.nodo(cx + rho * Math.cos(th) * 0.9, cy - rho * Math.sin(th) * 0.9, r).centro(t[1]);
            if (i == 0 || i == n - 1) nd.sectores(t[0]); else nd.lados(t[0], t[2]);
            if (prev != null) c.arista(prev, nd);
            prev = nd;
        }
        return c;
    }

    static BufferedImage dibuja() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), FONDO);
        Composicion hoja = Hoja.limbo(new Hoja.Tr(30, 440, Math.toRadians(-4), 0.9), 3.6);
        hoja.render(im);
        Hoja.venas(hoja, 1.0, 0.55).render(im);
        cuerpo().render(im);
        antena(458, 308, 484, 236, 4, -30).render(im);
        antena(440, 306, 432, 232, -6, -30).render(im);
        concha(300, 300).render(im);
        // ojos
        for (double[] o : new double[][] { { 484, 236 }, { 432, 232 } }) {
            java.awt.geom.Point2D.Double a = new java.awt.geom.Point2D.Double(o[0] + 1, o[1] - 1), b = new java.awt.geom.Point2D.Double(o[0] + 1.5, o[1] - 1);
            Gota.lineal(a, 3, b, 3, Perfil.recto()).render(im, Colores.cuatroColores(0xFF444444, 0xFF444444, 0xFF111111, 0xFF111111, Mezcla.RGB));
        }
        return im;
    }

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        ImageIO.write(dibuja(), "png", new File("caracol.png"));
        System.out.printf("render: %.1f s%n", (System.nanoTime() - t0) / 1e9);
    }
}
