import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * Comparativa del sentido del tono. Columnas: HSL corto, HSL antihorario, LCH corto, LCH antihorario.
 * Filas: forma recta y arco. En todas, centro→borde usa tono corto.
 */
public class GotaColorDemo {

    static Point2D.Double p(int col, int fila, double x, double y) {
        return new Point2D.Double(col * 300 + x, fila * 300 + y);
    }

    public static void main(String[] args) throws Exception {
        boolean ok = true;

        // Identidad: mezclar(c, c, u) debe devolver c (tolerancia de 1 por canal)
        String[] nombres = { "RGB", "RGB_LINEAL", "HSL corto", "HSL horario", "HSL antihorario",
                             "HSL largo", "LCH corto", "LCH horario", "LCH antihorario", "LCH largo" };
        Rellenos.Mezcla[] todas = {
            Rellenos.RGB, Rellenos.RGB_LINEAL,
            Rellenos.hsl(Rellenos.Tono.CORTO), Rellenos.hsl(Rellenos.Tono.HORARIO),
            Rellenos.hsl(Rellenos.Tono.ANTIHORARIO), Rellenos.hsl(Rellenos.Tono.LARGO),
            Rellenos.lch(Rellenos.Tono.CORTO), Rellenos.lch(Rellenos.Tono.HORARIO),
            Rellenos.lch(Rellenos.Tono.ANTIHORARIO), Rellenos.lch(Rellenos.Tono.LARGO),
        };
        int[] prueba = { 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF, 0xFF000000,
                         0xFF808080, 0xFF2563EB, 0xFFFACC15, 0x80336699 };
        for (int i = 0; i < todas.length; i++) {
            // LARGO entre tonos iguales da una vuelta completa por definición: no cumple identidad
            if (nombres[i].endsWith("largo")) continue;
            int fallos = 0;
            for (int c : prueba)
                for (double u : new double[] { 0.13, 0.5, 0.87 })
                    if (!cercano(todas[i].mezclar(c, c, u), c)) fallos++;
            System.out.printf("%-16s identidad: %d fallos%n", nombres[i], fallos);
            if (fallos > 0) ok = false;
        }

        // Sentido del tono: rojo (0°) a azul (240°), punto medio en HSL
        int rojo = 0xFFFF0000, azul = 0xFF0000FF, magenta = 0xFFFF00FF, verde = 0xFF00FF00;
        ok &= comprueba("corto      -> magenta", Rellenos.hsl(Rellenos.Tono.CORTO).mezclar(rojo, azul, 0.5), magenta);
        ok &= comprueba("largo      -> verde  ", Rellenos.hsl(Rellenos.Tono.LARGO).mezclar(rojo, azul, 0.5), verde);
        ok &= comprueba("horario    -> verde  ", Rellenos.hsl(Rellenos.Tono.HORARIO).mezclar(rojo, azul, 0.5), verde);
        ok &= comprueba("antihorario-> magenta", Rellenos.hsl(Rellenos.Tono.ANTIHORARIO).mezclar(rojo, azul, 0.5), magenta);

        // Colores: centros azul y amarillo, bordes azul noche y ladrillo
        int a = 0xFF2563EB, b = 0xFFFACC15, aBorde = 0xFF1E1B4B, bBorde = 0xFF7C2D12;
        Gota.Relleno[] rellenos = {
            Rellenos.conGamma(a, b, aBorde, bBorde, 2.0, 0.7, Rellenos.HSL, Rellenos.HSL),
            Rellenos.conGamma(a, b, aBorde, bBorde, 2.0, 0.7,
                              Rellenos.hsl(Rellenos.Tono.ANTIHORARIO), Rellenos.HSL),
            Rellenos.conGamma(a, b, aBorde, bBorde, 2.0, 0.7, Rellenos.LCH, Rellenos.LCH),
            Rellenos.conGamma(a, b, aBorde, bBorde, 2.0, 0.7,
                              Rellenos.lch(Rellenos.Tono.ANTIHORARIO), Rellenos.LCH),
        };

        BufferedImage img = new BufferedImage(1200, 600, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) img.getRaster().getDataBuffer()).getData(), 0xFFFFFFFF);
        for (int col = 0; col < 4; col++) {
            Gota.lineal(p(col, 0, 70, 150), 60, p(col, 0, 235, 150), 25, Gota.Perfil.central(1.4)).render(img, rellenos[col]);
            Gota.curva(p(col, 1, 60, 240), 35, new Gota.Vec(0, -200),
                       p(col, 1, 240, 240), 15, new Gota.Vec(0, -200), Gota.Perfil.central(1.0)).render(img, rellenos[col]);
        }
        ImageIO.write(img, "png", new File("gota_tonos.png"));
        System.out.println(ok ? "OK" : "HAY FALLOS");
        if (!ok) System.exit(1);
    }

    static boolean comprueba(String nombre, int obtenido, int esperado) {
        boolean bien = cercano(obtenido, esperado);
        System.out.printf("%s: %08X (esperado %08X) %s%n", nombre, obtenido, esperado, bien ? "ok" : "FALLO");
        return bien;
    }

    static boolean cercano(int x, int y) {
        for (int s = 0; s < 32; s += 8)
            if (Math.abs(((x >>> s) & 0xFF) - ((y >>> s) & 0xFF)) > 1) return false;
        return true;
    }
}
