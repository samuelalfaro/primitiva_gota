import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Ballena – Versión 1 (primer intento)
 * Composiciones independientes para cada parte.
 * Aprendizaje: API básica de Composicion, lados vs sectores, tiradores.
 */
public class BallenaV1 {
    static final int W = 900, H = 600;
    static final int FONDO = 0xFFB3E5FC;

    static final int CENTRO = 0xFF90CAF9;
    static final int LADO1  = 0xFF1565C0;
    static final int LADO2  = 0xFF42A5F5;
    static final int VIENTRE= 0xFFE3F2FD;
    static final int OSCURO = 0xFF0D47A1;

    public static void main(String[] args) throws Exception {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        int[] pix = ((DataBufferInt) im.getRaster().getDataBuffer()).getData();
        java.util.Arrays.fill(pix, FONDO);

        long t0 = System.nanoTime();

        // Cuerpo (cadena simple)
        Composicion cuerpo = new Composicion().suavizado(2.5);
        var colaBase = cuerpo.nodo(190, 345, 32).centro(CENTRO).sectores(LADO1);
        var c1 = cuerpo.nodo(290, 315, 58).centro(CENTRO).lados(LADO1, VIENTRE).ajusteLateral(0.38);
        var c2 = cuerpo.nodo(430, 300, 72).centro(CENTRO).lados(LADO1, VIENTRE).ajusteLateral(0.42);
        var c3 = cuerpo.nodo(560, 295, 60).centro(CENTRO).lados(LADO1, VIENTRE).ajusteLateral(0.40);
        var cabeza = cuerpo.nodo(700, 310, 36).centro(CENTRO).sectores(VIENTRE);

        cuerpo.arista(colaBase, c1).tiradores(new Gota.Vec(45, -30), new Gota.Vec(-55, 8));
        cuerpo.arista(c1, c2).tiradores(new Gota.Vec(65, 5), new Gota.Vec(-60, -2));
        cuerpo.arista(c2, c3).tiradores(new Gota.Vec(55, -3), new Gota.Vec(-50, 5));
        cuerpo.arista(c3, cabeza).tiradores(new Gota.Vec(50, 8), new Gota.Vec(-35, -8));
        cuerpo.render(im);

        // Flukes independientes
        Composicion flukeSup = new Composicion().suavizado(1.8);
        var fs1 = flukeSup.nodo(185, 325, 24).centro(CENTRO).sectores(LADO1);
        var fs2 = flukeSup.nodo(95, 245, 13).centro(CENTRO).sectores(LADO1);
        flukeSup.arista(fs1, fs2).tiradores(new Gota.Vec(-35, -45), new Gota.Vec(25, 18));
        flukeSup.render(im);

        Composicion flukeInf = new Composicion().suavizado(1.8);
        var fi1 = flukeInf.nodo(185, 365, 22).centro(CENTRO).sectores(LADO1);
        var fi2 = flukeInf.nodo(100, 430, 12).centro(CENTRO).sectores(LADO1);
        flukeInf.arista(fi1, fi2).tiradores(new Gota.Vec(-30, 40), new Gota.Vec(20, -12));
        flukeInf.render(im);

        // Aletas independientes
        Composicion dorsal = new Composicion().suavizado(1.4);
        var d1 = dorsal.nodo(435, 238, 14).centro(CENTRO).sectores(LADO1);
        var d2 = dorsal.nodo(460, 180, 8).centro(CENTRO).sectores(LADO1);
        dorsal.arista(d1, d2).tiradores(new Gota.Vec(8, -35), new Gota.Vec(-8, 18));
        dorsal.render(im);

        Composicion pectoral = new Composicion().suavizado(1.6);
        var p1 = pectoral.nodo(525, 360, 20).centro(CENTRO).sectores(LADO1);
        var p2 = pectoral.nodo(490, 445, 13).centro(CENTRO).sectores(LADO1);
        pectoral.arista(p1, p2).tiradores(new Gota.Vec(-18, 45), new Gota.Vec(12, -18));
        pectoral.render(im);

        // Ojo
        Point2D.Double oe = new Point2D.Double(645, 278);
        Point2D.Double oe2 = new Point2D.Double(645.5, 278);
        Plotter.raster(im).plot(Gota.lineal(oe, 9.5, oe2, 9.5, Perfil.recto()), Colores.cuatroColores(0xFFFFFFFF, 0xFFFFFFFF, OSCURO, OSCURO, Mezcla.RGB));
        Point2D.Double pu = new Point2D.Double(648, 276);
        Point2D.Double pu2 = new Point2D.Double(648.4, 276);
        Plotter.raster(im).plot(Gota.lineal(pu, 4.2, pu2, 4.2, Perfil.recto()), Colores.cuatroColores(0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000, Mezcla.RGB));

        // Boca
        Composicion boca = new Composicion().suavizado(1.0);
        var b1 = boca.nodo(695, 330, 5).color(LADO1);
        var b2 = boca.nodo(730, 342, 3.5).color(LADO1);
        boca.arista(b1, b2).tiradores(new Gota.Vec(18, 6), new Gota.Vec(-12, 0));
        boca.render(im);

        System.out.printf("BallenaV1 render: %.2f s%n", (System.nanoTime() - t0) / 1e9);
        ImageIO.write(im, "png", new File("ballena_v1.png"));
    }
}
