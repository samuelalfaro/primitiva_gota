import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Dibujo libre solo con la API de Composicion: una serpiente enroscada, en una sola cadena de nodos cuyo camino se cruza consigo mismo
 * (las vueltas se tapan unas a otras con el solape TRAZO: lo posterior tapa lo anterior) y que acaba en un cuello que se levanta.
 */
public class SerpienteEnroscada {
    static final int W = 760, H = 700;
    static final int LOMO = 0xFF5E7F1E, LOMO2 = 0xFF8FB339, VIENTRE = 0xFFEFDDA8;

    static double suave(double a, double b, double x) { double t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); }

    static void disco(BufferedImage im, double x, double y, double r, int centro, int borde) {
        Point2D.Double a = new Point2D.Double(x, y), b = new Point2D.Double(x + 0.5, y);
        Gota.lineal(a, r, b, r, Perfil.recto()).render(im, Colores.cuatroColores(centro, centro, borde, borde, Mezcla.RGB));
    }

    /** Puntos del camino: la espiral cónica en vista tres cuartos y, al final, el cuello en S hasta la cabeza. */
    static List<double[]> camino(double cx, double cy, int paso) {
        List<double[]> p = new ArrayList<>();
        double vueltas = 3.0, th0 = Math.PI * 0.8, th1 = th0 + vueltas * 2 * Math.PI;
        int n = (int) (vueltas * 2 * Math.PI * 150 / paso);
        for (int i = 0; i <= n; i++) {
            double th = th0 + (th1 - th0) * i / n, k = (th - th0) / (th1 - th0);
            double R = 190 - 80 * k;
            p.add(new double[] { cx + R * Math.cos(th), cy - 120 * k * (vueltas / 3) + 0.36 * R * Math.sin(th) - 58 * (th - th0) / (2 * Math.PI) });
        }
        double[] e = p.get(p.size() - 1);
        double[][] cuello = { { -14, -50 }, { 6, -105 }, { 52, -150 }, { 96, -168 } };
        for (double[] c : cuello) p.add(new double[] { e[0] + c[0], e[1] + c[1] });
        return p;
    }

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFFE9D3A5);
        disco(im, 380, 330, 600, 0xFFF6E7C1, 0xFFC9A468);              // el suelo: una gota enorme
        disco(im, 380, 585, 235, 0x50000000, 0x00000000);               // una sombra suave bajo el enroscado
        List<double[]> pts = camino(380, 545, 1);
        // remuestreo por longitud de arco
        List<double[]> nodos = new ArrayList<>();
        double sp = 15, acum = 0;
        nodos.add(pts.get(0));
        for (int i = 1; i < pts.size(); i++) {
            acum += Math.hypot(pts.get(i)[0] - pts.get(i - 1)[0], pts.get(i)[1] - pts.get(i - 1)[1]);
            if (acum >= sp) { nodos.add(pts.get(i)); acum = 0; }
        }
        // el último punto (la cabeza) siempre
        double[] u = nodos.get(nodos.size() - 1), z = pts.get(pts.size() - 1);
        if (Math.hypot(z[0] - u[0], z[1] - u[1]) > sp * 0.6) nodos.add(z); else nodos.set(nodos.size() - 1, z);
        int n = nodos.size();
        Composicion c = new Composicion();
        Composicion.Nodo prev = null;
        for (int i = 0; i < n; i++) {
            double t = (double) i / (n - 1);
            double r = 3 + 27 * suave(0.0, 0.22, t) - 6 * suave(0.88, 0.96, t);
            if (i == n - 1) r = 26;
            var nd = c.nodo(nodos.get(i)[0], nodos.get(i)[1], r).centro(LOMO2);
            if (i == 0) nd.sectores(LOMO);
            else if (i == n - 1) nd.sectores(LOMO);
            else nd.lados(VIENTRE, LOMO).ajusteLateral(0.45);
            if (prev != null) c.arista(prev, nd);
            prev = nd;
        }
        System.out.println("nodos: " + n);
        Opciones op = Opciones.POR_DEFECTO.conSolape(args.length > 0 && args[0].equals("plano") ? Solape.PLANO : Solape.TRAZO);
        c.render(im, op);
        // cabeza: ojos y lengua
        double[] h = nodos.get(n - 1), h0 = nodos.get(n - 3);
        double ang = Math.atan2(h[1] - h0[1], h[0] - h0[0]), ca = Math.cos(ang), sa = Math.sin(ang);
        for (int s = -1; s <= 1; s += 2) {
            double ex = h[0] + ca * 3 - sa * 13 * s, ey = h[1] + sa * 3 + ca * 13 * s;
            disco(im, ex, ey, 6.5, 0xFFFFE066, 0xFFE0A800);
            disco(im, ex + ca * 1.5, ey + sa * 1.5, 2.8, 0xFF111111, 0xFF000000);
        }
        Composicion lengua = new Composicion();
        var l0 = lengua.nodo(h[0] + ca * 30, h[1] + sa * 30, 2.2).color(0xFFD32F2F);
        var l1 = lengua.nodo(h[0] + ca * 62, h[1] + sa * 62, 1.8).color(0xFFD32F2F);
        var l2 = lengua.nodo(h[0] + ca * 82 - sa * 13, h[1] + sa * 82 + ca * 13, 1.2).color(0xFFD32F2F);
        var l3 = lengua.nodo(h[0] + ca * 82 + sa * 13, h[1] + sa * 82 - ca * 13, 1.2).color(0xFFD32F2F);
        lengua.arista(l0, l1);
        lengua.arista(l1, l2);
        lengua.arista(l1, l3);
        lengua.render(im);
        ImageIO.write(im, "png", new File(args.length > 0 && args[0].equals("plano") ? "enroscada_plano.png" : "enroscada.png"));
        System.out.printf("render: %.1f s%n", (System.nanoTime() - t0) / 1e9);
    }
}
