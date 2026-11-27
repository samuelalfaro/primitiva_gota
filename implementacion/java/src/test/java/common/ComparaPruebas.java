package common;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Pruebas de {@link Compara}: iguales, distintas (conteo y máximo), tamaños distintos, tríptico y recorrido de carpetas. */
public class ComparaPruebas {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        System.out.printf("%-78s %s%s%n", n, ok ? "ok   " : "FALLA", d.isEmpty() ? "" : "  " + d);
    }

    static BufferedImage lienzo(int w, int h, int argb) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) im.setRGB(x, y, argb);
        return im;
    }

    public static void main(String[] args) throws Exception {
        BufferedImage a = lienzo(40, 30, 0xFFFFFFFF);
        for (int y = 5; y < 15; y++) for (int x = 5; x < 15; x++) a.setRGB(x, y, 0xFF204080);     // dibujo: 100 px
        BufferedImage b = lienzo(40, 30, 0xFFFFFFFF);
        for (int y = 5; y < 15; y++) for (int x = 5; x < 15; x++) b.setRGB(x, y, 0xFF204080);

        Compara.Resultado r = Compara.compara(a, b);
        comprueba("imágenes idénticas: 0 píxeles distintos", r.iguales() && r.distintos() == 0 && r.maxCanal() == 0 && r.dibujo() == 100, "");

        b.setRGB(6, 6, 0xFF204090);                       // un canal, +16
        b.setRGB(7, 7, 0xFF704080);                       // rojo +80
        r = Compara.compara(a, b);
        comprueba("dos píxeles distintos: cuenta y máximo por canal", r.distintos() == 2 && r.maxCanal() == 80 && !r.iguales(), r.distintos() + " " + r.maxCanal());
        comprueba("el porcentaje es respecto del dibujo (2 de 100)", Math.abs(r.porcentaje() - 2.0) < 1e-9, "");

        r = Compara.compara(a, lienzo(41, 30, 0xFFFFFFFF));
        comprueba("tamaños distintos: no son iguales", !r.mismoTamano() && !r.iguales(), "");

        BufferedImage d = Compara.diferencia(a, b, 8);
        comprueba("diferencia ×8: 16 → 128 en azul y 80 → 255 en rojo (saturado), 0 donde no hay diferencia",
                  (d.getRGB(6, 6) & 255) == 128 && ((d.getRGB(7, 7) >> 16) & 255) == 255 && (d.getRGB(20, 20) & 0xFFFFFF) == 0, "");
        BufferedImage t = Compara.triptico(a, b, 8, 20);
        comprueba("tríptico: tres paneles del ancho pedido con separación", t.getWidth() == 3 * 20 + 2 * 8 && t.getHeight() == 15, t.getWidth() + "x" + t.getHeight());

        Path base = Files.createTempDirectory("compara");
        Path ca = Files.createDirectories(base.resolve("antes/sub")), cd = Files.createDirectories(base.resolve("despues/sub"));
        ImageIO.write(a, "png", ca.resolve("x.png").toFile());
        ImageIO.write(b, "png", cd.resolve("x.png").toFile());
        ImageIO.write(a, "png", base.resolve("antes/igual.png").toFile());
        ImageIO.write(a, "png", base.resolve("despues/igual.png").toFile());
        var ia = Compara.imagenes(base.resolve("antes"));
        comprueba("imagenes(): recorre las subcarpetas con rutas relativas", ia.keySet().equals(java.util.Set.of("sub/x.png", "igual.png")), ia.keySet().toString());
        for (File f : base.toFile().listFiles()) borra(f);
        base.toFile().delete();

        System.out.println(pruebas + " pruebas, " + fallos + " fallos");
        if (fallos > 0) System.exit(1);
    }

    static void borra(File f) {
        File[] h = f.listFiles();
        if (h != null) for (File x : h) borra(x);
        f.delete();
    }
}
