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
        String[] nombres = { "RGB", "RGB_LINEAL", "HSL", "LCH" };
        Mezcla[] todas = { Mezcla.RGB, Mezcla.RGB_LINEAL, Mezcla.HSL, Mezcla.LCH };
        int[] prueba = { 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF, 0xFF000000,
                         0xFF808080, 0xFF2563EB, 0xFFFACC15, 0x80336699 };
        for (int i = 0; i < todas.length; i++) {
            int fallos = 0;
            for (int c : prueba)
                for (double u : new double[] { 0.13, 0.5, 0.87 })
                    if (!cercano(todas[i].mezclar(c, c, u), c)) fallos++;
            System.out.printf("%-16s identidad: %d fallos%n", nombres[i], fallos);
            if (fallos > 0) ok = false;
        }

        // Sin cortes de tono: entre tonos opuestos (rojo y cian, 180°) el color pasa por un gris en vez de saltar de un lado a otro de la rueda
        int rojo = 0xFFFF0000, cian = 0xFF00FFFF;
        for (int i = 2; i < 4; i++) {
            int maxPaso = 0;
            for (int k = 0; k < 400; k++) {
                int x = todas[i].mezclar(rojo, cian, k / 400.0), y = todas[i].mezclar(rojo, cian, (k + 1) / 400.0);
                for (int s = 0; s <= 16; s += 8) maxPaso = Math.max(maxPaso, Math.abs(((x >> s) & 255) - ((y >> s) & 255)));
            }
            System.out.printf("%-16s rojo -> cian: salto máximo entre pasos de 1/400: %d%n", nombres[i], maxPaso);
            if (maxPaso > 16) ok = false;   // el máximo cae en los extremos (curva sRGB); un corte real de tono daría un salto de decenas
        }
        ok &= comprueba("HSL rojo->cian, mitad: gris", Mezcla.HSL.mezclar(rojo, cian, 0.5), 0xFF808080);
        // y también cuando los tonos quedan a poco menos de 180° (rojo y cian desplazado 10°), donde el arco corto cambiaba de lado
        int cercano = 0xFF00FFE0;   // tono 170°
        int maxPaso = 0;
        for (int k = 0; k < 400; k++) {
            int x = Mezcla.LCH.mezclar(rojo, cercano, k / 400.0), y = Mezcla.LCH.mezclar(rojo, cercano, (k + 1) / 400.0);
            for (int s = 0; s <= 16; s += 8) maxPaso = Math.max(maxPaso, Math.abs(((x >> s) & 255) - ((y >> s) & 255)));
        }
        System.out.printf("LCH              rojo -> tono 170°: salto máximo entre pasos de 1/400: %d%n", maxPaso);
        if (maxPaso > 16) ok = false;   // el máximo cae en los extremos (curva sRGB); un corte real de tono daría un salto de decenas

        // Colores: centros azul y amarillo, bordes azul noche y ladrillo
        int a = 0xFF2563EB, b = 0xFFFACC15, aBorde = 0xFF1E1B4B, bBorde = 0xFF7C2D12;
        Relleno[] rellenos = {
            Colores.conGamma(a, b, aBorde, bBorde, 2.0, 0.7, Mezcla.RGB, Mezcla.RGB),
            Colores.conGamma(a, b, aBorde, bBorde, 2.0, 0.7, Mezcla.RGB_LINEAL, Mezcla.RGB_LINEAL),
            Colores.conGamma(a, b, aBorde, bBorde, 2.0, 0.7, Mezcla.HSL, Mezcla.HSL),
            Colores.conGamma(a, b, aBorde, bBorde, 2.0, 0.7, Mezcla.LCH, Mezcla.LCH),
        };

        BufferedImage img = new BufferedImage(1200, 600, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) img.getRaster().getDataBuffer()).getData(), 0xFFFFFFFF);
        for (int col = 0; col < 4; col++) {
            Plotter.raster(img).plot(Gota.lineal(p(col, 0, 70, 150), 60, p(col, 0, 235, 150), 25, Perfil.central(1.4)), rellenos[col]);
            Plotter.raster(img).plot(Gota.curva(p(col, 1, 60, 240), 35, new Gota.Vec(0, -200),
                       p(col, 1, 240, 240), 15, new Gota.Vec(0, -200), Perfil.central(1.0)), rellenos[col]);
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
