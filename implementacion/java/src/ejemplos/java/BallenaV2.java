import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Ballena – Versión 2 (refinamiento artístico)
 * Mejores proporciones, curvatura S, chorro de agua, paleta más coherente.
 * Sigue usando composiciones separadas, pero con más cuidado formal.
 */
public class BallenaV2 {
    static final int W = 1000, H = 620;
    static final int FONDO = 0xFFB8E0F0;

    static final int C_CUERPO  = 0xFF7EB6D9;
    static final int C_VIENTRE = 0xFFE8F4FC;
    static final int C_SOMBRA  = 0xFF3A7CA5;
    static final int C_PUNTA   = 0xFF5A9BC4;
    static final int C_OJO     = 0xFF1A3A5C;

    public static void main(String[] args) throws Exception {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        int[] pix = ((DataBufferInt) im.getRaster().getDataBuffer()).getData();
        java.util.Arrays.fill(pix, FONDO);

        long t0 = System.nanoTime();

        Composicion cuerpo = new Composicion().suavizado(3.0);
        var n0 = cuerpo.nodo(210, 355, 34).centro(C_CUERPO).sectores(C_SOMBRA);
        var n1 = cuerpo.nodo(310, 320, 58).centro(C_CUERPO).lados(C_SOMBRA, C_VIENTRE).ajusteLateral(0.36);
        var n2 = cuerpo.nodo(460, 295, 78).centro(C_CUERPO).lados(C_SOMBRA, C_VIENTRE).ajusteLateral(0.40);
        var n3 = cuerpo.nodo(610, 290, 62).centro(C_CUERPO).lados(C_SOMBRA, C_VIENTRE).ajusteLateral(0.38);
        var n4 = cuerpo.nodo(760, 305, 38).centro(C_CUERPO).sectores(C_VIENTRE);

        cuerpo.arista(n0, n1).tiradores(new Gota.Vec(55, -35), new Gota.Vec(-60, 12));
        cuerpo.arista(n1, n2).tiradores(new Gota.Vec(70, 8),  new Gota.Vec(-65, -5));
        cuerpo.arista(n2, n3).tiradores(new Gota.Vec(60, -4), new Gota.Vec(-55, 6));
        cuerpo.arista(n3, n4).tiradores(new Gota.Vec(55, 10), new Gota.Vec(-40, -12));
        cuerpo.render(im);

        // Flukes
        Composicion flukeS = new Composicion().suavizado(2.0);
        var fs0 = flukeS.nodo(200, 330, 26).centro(C_CUERPO).sectores(C_SOMBRA);
        var fs1 = flukeS.nodo(95, 230, 14).centro(C_CUERPO).sectores(C_PUNTA);
        flukeS.arista(fs0, fs1).tiradores(new Gota.Vec(-40, -50), new Gota.Vec(28, 22));
        flukeS.render(im);

        Composicion flukeI = new Composicion().suavizado(2.0);
        var fi0 = flukeI.nodo(200, 380, 24).centro(C_CUERPO).sectores(C_SOMBRA);
        var fi1 = flukeI.nodo(100, 465, 13).centro(C_CUERPO).sectores(C_PUNTA);
        flukeI.arista(fi0, fi1).tiradores(new Gota.Vec(-35, 48), new Gota.Vec(25, -18));
        flukeI.render(im);

        // Dorsal
        Composicion dorsal = new Composicion().suavizado(1.6);
        var d0 = dorsal.nodo(455, 225, 13).centro(C_CUERPO).sectores(C_SOMBRA);
        var d1 = dorsal.nodo(485, 165, 7).centro(C_CUERPO).sectores(C_PUNTA);
        dorsal.arista(d0, d1).tiradores(new Gota.Vec(10, -38), new Gota.Vec(-6, 16));
        dorsal.render(im);

        // Pectoral
        Composicion pectoral = new Composicion().suavizado(1.8);
        var p0 = pectoral.nodo(555, 360, 19).centro(C_CUERPO).sectores(C_SOMBRA);
        var p1 = pectoral.nodo(510, 460, 12).centro(C_CUERPO).sectores(C_PUNTA);
        pectoral.arista(p0, p1).tiradores(new Gota.Vec(-22, 50), new Gota.Vec(14, -20));
        pectoral.render(im);

        // Ojo
        Point2D.Double oe  = new Point2D.Double(695, 275);
        Point2D.Double oe2 = new Point2D.Double(695.6, 275);
        Plotter.raster(im).plot(Gota.lineal(oe, 10.5, oe2, 10.5, Perfil.recto()), Colores.cuatroColores(0xFFFFFFFF, 0xFFFFFFFF, C_OJO, C_OJO, Mezcla.RGB));
        Point2D.Double pu  = new Point2D.Double(698, 273);
        Point2D.Double pu2 = new Point2D.Double(698.4, 273);
        Plotter.raster(im).plot(Gota.lineal(pu, 4.8, pu2, 4.8, Perfil.recto()), Colores.cuatroColores(0xFF0A1A2A, 0xFF0A1A2A, 0xFF0A1A2A, 0xFF0A1A2A, Mezcla.RGB));

        // Boca
        Composicion boca = new Composicion().suavizado(1.2);
        var b0 = boca.nodo(745, 330, 4.5).color(C_SOMBRA);
        var b1 = boca.nodo(785, 345, 3.2).color(C_SOMBRA);
        boca.arista(b0, b1).tiradores(new Gota.Vec(22, 8), new Gota.Vec(-14, 2));
        boca.render(im);

        // Chorro
        dibujarChorro(im, 720, 210);

        System.out.printf("BallenaV2 render: %.2f s%n", (System.nanoTime() - t0) / 1e9);
        ImageIO.write(im, "png", new File("ballena_v2.png"));
    }

    static void dibujarChorro(BufferedImage im, double x, double y) {
        int agua = 0xFFB0D8EC, clara = 0xFFE0F2FA;
        Composicion c = new Composicion().suavizado(0.9);
        var a = c.nodo(x, y, 5.5).color(agua);
        var b = c.nodo(x - 4, y - 95, 2.8).color(clara);
        c.arista(a, b).tiradores(new Gota.Vec(0, -35), new Gota.Vec(2, 18));
        c.render(im);
        Composicion c2 = new Composicion().suavizado(0.9);
        var a2 = c2.nodo(x - 8, y + 5, 4.2).color(agua);
        var b2 = c2.nodo(x - 38, y - 80, 2.2).color(clara);
        c2.arista(a2, b2).tiradores(new Gota.Vec(-12, -30), new Gota.Vec(8, 15));
        c2.render(im);
        Composicion c3 = new Composicion().suavizado(0.9);
        var a3 = c3.nodo(x + 8, y + 5, 4.2).color(agua);
        var b3 = c3.nodo(x + 32, y - 78, 2.2).color(clara);
        c3.arista(a3, b3).tiradores(new Gota.Vec(12, -28), new Gota.Vec(-6, 14));
        c3.render(im);
    }
}
