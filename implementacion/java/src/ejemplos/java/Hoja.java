import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Una hoja con nervaduras, solo con la API: el limbo es una composición de dos gotas con la vena central marcada por
 * el ajuste lateral, y las venas son otra composición (nervio central y ramas curvadas hacia la punta) pintada encima.
 * La hoja se define en sus coordenadas y se coloca con una semejanza (posición de la base, ángulo y escala).
 */
public class Hoja {
    static final int W = 500, H = 700, FONDO = 0xFFF4F1EA;
    static final double BX = 226, BY = 655;    // base de la hoja en sus coordenadas
    static final double EJE = Math.atan2(70 - 655, 318 - 226);   // dirección base -> punta

    /** Semejanza: lleva la base de la hoja a (x, y), gira su eje hasta el ángulo dado (radianes, y hacia abajo) y escala. */
    record Tr(double x, double y, double ang, double esc) {
        double cos() { return Math.cos(ang - EJE); }
        double sin() { return Math.sin(ang - EJE); }
        double X(double px, double py) { double dx = px - BX, dy = py - BY; return x + esc * (dx * cos() - dy * sin()); }
        double Y(double px, double py) { double dx = px - BX, dy = py - BY; return y + esc * (dx * sin() + dy * cos()); }
        static Tr identidad() { return new Tr(BX, BY, EJE, 1); }
    }

    static Composicion limbo(Tr t, double radioBase) {
        Composicion c = new Composicion();
        var base = c.nodo(t.X(226, 655), t.Y(226, 655), radioBase).centro(0xFFC5E1A5).sectores(0xFF4E8F34);
        var medio = c.nodo(t.X(262, 410), t.Y(262, 410), 112 * t.esc()).centro(0xFFE6EE9C).lados(0xFF1B5E20, 0xFF9CCC65).ajusteLateral(0.3);
        var punta = c.nodo(t.X(318, 70), t.Y(318, 70), 5 * t.esc()).centro(0xFFDCEDC8).aspecto(3.2).sectores(0xFF5E9B38).ajusteLateral(0.3);
        c.arista(base, medio);
        c.arista(medio, punta);
        return c;
    }

    /**
     * Venas: nervio central siguiendo el eje del limbo y pares de ramas que salen a unos 62° y acaban a unos 22° del
     * eje (curvadas hacia la punta). El largo sigue al ancho de la hoja (radio del limbo). rv0 y rv1 son los radios del nervio.
     */
    static Composicion venas(Composicion limbo, double rv0, double rv1) {
        List<Gota> gs = limbo.gotas();
        Composicion c = new Composicion().suavizado(1.5);
        int vena = 0xFFEFF7C2;
        int n = 9;
        Point2D[] pt = new Point2D[n];
        double[] rad = new double[n], tx = new double[n], ty = new double[n];
        for (int i = 0; i < n; i++) {
            double s = 0.04 + 0.90 * i / (n - 1);
            Gota g = s < 0.5 ? gs.get(0) : gs.get(1);
            double t = s < 0.5 ? s / 0.5 : (s - 0.5) / 0.5;
            pt[i] = g.centro(t);
            rad[i] = g.radio(t);
            Point2D a = g.centro(Math.max(0, t - 0.01)), b = g.centro(Math.min(1, t + 0.01));
            double d = Math.hypot(b.getX() - a.getX(), b.getY() - a.getY());
            tx[i] = (b.getX() - a.getX()) / d;
            ty[i] = (b.getY() - a.getY()) / d;
        }
        Composicion.Nodo[] m = new Composicion.Nodo[n];
        for (int i = 0; i < n; i++) m[i] = c.nodo(pt[i].getX(), pt[i].getY(), rv0 + (rv1 - rv0) * i / (n - 1)).color(vena);
        for (int i = 0; i + 1 < n; i++) c.arista(m[i], m[i + 1]);
        double ini = Math.toRadians(62), fin = Math.toRadians(22), med = Math.toRadians(45);
        for (int i = 1; i < n - 1; i++)
            for (int lado = -1; lado <= 1; lado += 2) {
                double largo = 0.80 * rad[i] / Math.sin(med);
                if (i > n - 3) largo *= 0.7;
                double[] u0 = dir(tx[i], ty[i], ini, lado), u1 = dir(tx[i], ty[i], fin, lado), um = dir(tx[i], ty[i], med, lado);
                var f = c.nodo(pt[i].getX() + um[0] * largo, pt[i].getY() + um[1] * largo, rv1 * 0.7).color(vena);
                double d = largo / 3;
                c.arista(m[i], f).tiradores(new Gota.Vec(u0[0] * d, u0[1] * d), new Gota.Vec(-u1[0] * d, -u1[1] * d));
            }
        return c;
    }

    /** Dirección a un ángulo del eje (t) hacia el lado (+1 / -1) de la normal. */
    static double[] dir(double tx, double ty, double ang, int lado) {
        double ca = Math.cos(ang), sa = Math.sin(ang) * lado;
        return new double[] { tx * ca - ty * sa, tx * sa + ty * ca };
    }

    public static void main(String[] args) throws Exception {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), FONDO);
        long t0 = System.nanoTime();
        Composicion l = limbo(Tr.identidad(), 6);
        l.render(im);
        venas(l, 2.4, 0.9).render(im);
        System.out.printf("render: %.1f s%n", (System.nanoTime() - t0) / 1e9);
        ImageIO.write(im, "png", new File("hoja.png"));
    }
}
