import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Ballena – Versión 3 (composicional)
 * Una sola Composicion para cuerpo + flukes + aletas.
 * Nodos compartidos (cola grado 3, centro y anterior grado 3).
 * Continuidad real de color y tangentes en las uniones.
 */
public class BallenaV3 {
    static final int W = 1000, H = 640;
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

        Composicion ballena = new Composicion().suavizado(2.8);

        // Cola (grado 3) → 3 sectores
        var cola = ballena.nodo(215, 360, 36)
                .centro(C_CUERPO).sectores(C_SOMBRA, C_SOMBRA, C_SOMBRA);

        var caudal = ballena.nodo(320, 325, 60)
                .centro(C_CUERPO).lados(C_SOMBRA, C_VIENTRE).ajusteLateral(0.35);

        // Centro (grado 3: caudal + anterior + dorsal)
        var centro = ballena.nodo(470, 298, 80)
                .centro(C_CUERPO).sectores(C_SOMBRA, C_VIENTRE, C_SOMBRA);

        // Anterior (grado 3: centro + cabeza + pectoral)
        var anterior = ballena.nodo(620, 292, 64)
                .centro(C_CUERPO).sectores(C_SOMBRA, C_VIENTRE, C_SOMBRA);

        var cabeza = ballena.nodo(770, 308, 40)
                .centro(C_CUERPO).sectores(C_VIENTRE).aspecto(1.42);

        ballena.arista(cola, caudal).tiradores(new Gota.Vec(50, -30), new Gota.Vec(-55, 10));
        ballena.arista(caudal, centro).tiradores(new Gota.Vec(70, 6),  new Gota.Vec(-68, -4));
        ballena.arista(centro, anterior).tiradores(new Gota.Vec(58, -3), new Gota.Vec(-52, 5));
        ballena.arista(anterior, cabeza).tiradores(new Gota.Vec(52, 8),  new Gota.Vec(-38, -10));

        // Flukes desde el nodo cola compartido
        var flukeS = ballena.nodo(100, 235, 14)
                .centro(C_CUERPO).sectores(C_PUNTA).aspecto(1.85);
        ballena.arista(cola, flukeS).tiradores(new Gota.Vec(-35, -48), new Gota.Vec(25, 20));

        var flukeI = ballena.nodo(105, 470, 13)
                .centro(C_CUERPO).sectores(C_PUNTA).aspecto(1.75);
        ballena.arista(cola, flukeI).tiradores(new Gota.Vec(-32, 45), new Gota.Vec(22, -16));

        // Aletas desde nodos del cuerpo
        var dorsal = ballena.nodo(490, 168, 7.5)
                .centro(C_CUERPO).sectores(C_PUNTA).aspecto(2.15);
        ballena.arista(centro, dorsal).tiradores(new Gota.Vec(8, -42), new Gota.Vec(-5, 14));

        var pectoral = ballena.nodo(520, 465, 12)
                .centro(C_CUERPO).sectores(C_PUNTA).aspecto(1.95);
        ballena.arista(anterior, pectoral).tiradores(new Gota.Vec(-25, 48), new Gota.Vec(12, -18));

        ballena.render(im);

        // Ojo
        Point2D.Double oe  = new Point2D.Double(705, 278);
        Point2D.Double oe2 = new Point2D.Double(705.6, 278);
        Gota.lineal(oe, 11, oe2, 11, Perfil.recto())
            .render(im, Colores.cuatroColores(0xFFFFFFFF, 0xFFFFFFFF, C_OJO, C_OJO, Mezcla.RGB));
        Point2D.Double pu  = new Point2D.Double(708, 276);
        Point2D.Double pu2 = new Point2D.Double(708.4, 276);
        Gota.lineal(pu, 5.0, pu2, 5.0, Perfil.recto())
            .render(im, Colores.cuatroColores(0xFF0A1A2A, 0xFF0A1A2A, 0xFF0A1A2A, 0xFF0A1A2A, Mezcla.RGB));

        // Boca
        Composicion boca = new Composicion().suavizado(1.1);
        var b0 = boca.nodo(755, 335, 4.8).color(C_SOMBRA);
        var b1 = boca.nodo(798, 350, 3.4).color(C_SOMBRA);
        boca.arista(b0, b1).tiradores(new Gota.Vec(24, 9), new Gota.Vec(-15, 2));
        boca.render(im);

        // Chorro
        dibujarChorro(im, 730, 205);

        System.out.printf("BallenaV3 render: %.2f s%n", (System.nanoTime() - t0) / 1e9);
        ImageIO.write(im, "png", new File("/home/workdir/artifacts/ballena_v3.png"));
    }

    static void dibujarChorro(BufferedImage im, double x, double y) {
        int agua = 0xFFB0D8EC, clara = 0xFFE0F2FA;
        Composicion c = new Composicion().suavizado(0.9);
        var a = c.nodo(x, y, 5.8).color(agua);
        var b = c.nodo(x - 3, y - 100, 2.6).color(clara);
        c.arista(a, b).tiradores(new Gota.Vec(0, -38), new Gota.Vec(2, 16));
        c.render(im);
        Composicion c2 = new Composicion().suavizado(0.9);
        var a2 = c2.nodo(x - 9, y + 6, 4.4).color(agua);
        var b2 = c2.nodo(x - 42, y - 82, 2.1).color(clara);
        c2.arista(a2, b2).tiradores(new Gota.Vec(-14, -32), new Gota.Vec(9, 14));
        c2.render(im);
        Composicion c3 = new Composicion().suavizado(0.9);
        var a3 = c3.nodo(x + 9, y + 6, 4.4).color(agua);
        var b3 = c3.nodo(x + 36, y - 80, 2.1).color(clara);
        c3.arista(a3, b3).tiradores(new Gota.Vec(14, -30), new Gota.Vec(-7, 13));
        c3.render(im);
    }
}
