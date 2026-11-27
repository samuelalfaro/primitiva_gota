import geometria.composicion.Grafo;
import pintado.Plotter;
import color.Mezcla;
import color.Degradado;
import color.Colores;
import forma.Perfil;
import forma.Gota;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Lámina libre de Claude (la regla del repo es firmar la clase con _by_Autor): una mariposa monarca, de nuevo sobre el
 * reparto de paquetes de #64. Todo son gotas.
 * <ul>
 * <li>Cada ala es un abanico de gotas que sale del tórax: base ancha pegada al cuerpo y punta fina en el borde. El borde
 *     negro de la monarca sale solo de la función del radio (naranja en el centro, negro hacia el contorno).</li>
 * <li>Las venas son gotas finas encima, del tórax al borde; las motas blancas, discos con el degradado del contorno.</li>
 * <li>El cuerpo es una cadena de tres nodos (cabeza, tórax, abdomen) y las antenas, dos gotas con tiradores.</li>
 * </ul>
 * Escribe monarca_by_claude.png.
 */
public class Monarca_by_Claude {
    static final int W = 820, H = 700;
    static final double CX = 410, CY = 330;
    static final Mezcla RGB = Mezcla.RGB;

    static int mezcla(int a, int b, double t) { return RGB.mezclar(a, b, Math.max(0, Math.min(1, t))); }

    static final int NARANJA = 0xFFEE7A1C, AMBAR = 0xFFF5A623, NEGRO = 0xFF140C0A, BLANCO = 0xFFFBF4E6;

    /** Volumen del ala: naranja vivo en el centro que se aclara hacia ámbar y se ensombrece un poco en el contorno. */
    static Degradado ala(int centro) {
        return x -> mezcla(mezcla(centro, AMBAR, 0.6 * x), 0xFF9C3F0C, 0.45 * x * x * x);
    }

    /** Gota del tórax (x0, y0) al borde (x1, y1), con radios r0 y r1 y color de centro c. */
    static Grafo rayo(double x0, double y0, double r0, double x1, double y1, double r1, int c, Degradado fr, int cBorde) {
        Grafo g = new Grafo().mezcla(RGB);
        var a = g.nodo(x0, y0, r0).centro(c).radio(fr);
        var b = g.nodo(x1, y1, r1).centro(cBorde).radio(x -> cBorde);
        g.arista(a, b);
        return g;
    }

    /** Abanico de rayos. Primero una capa negra algo más larga y ancha (el borde de la monarca) y encima la naranja. */
    static void ala(BufferedImage im, int lado, double a0, double a1, double largo, int rayos, double r0, double r1, int c) {
        for (int capa = 0; capa < 2; capa++)
            for (int i = 0; i < rayos; i++) {
                double k = (double) i / (rayos - 1);
                double ang = Math.toRadians(a0 + (a1 - a0) * k);
                double l = largo * (0.82 + 0.18 * Math.sin(Math.PI * (0.15 + 0.7 * k)));
                double x0 = CX + lado * 14, y0 = CY + 6 * Math.sin(ang);
                if (capa == 0) {
                    Plotter.raster(im).plot(rayo(x0, y0, r0 + 6, CX + lado * (l + 8) * Math.cos(ang), CY + (l + 8) * Math.sin(ang), r1 + 7, NEGRO, x -> NEGRO, NEGRO));
                } else {
                    int col = mezcla(c, AMBAR, 0.35 * k);
                    Plotter.raster(im).plot(rayo(x0, y0, r0, CX + lado * (l - 14) * Math.cos(ang), CY + (l - 14) * Math.sin(ang), r1 * 0.8, col, ala(col), 0xFF9C3F0C));
                }
            }
    }

    static void venas(BufferedImage im, int lado, double a0, double a1, double largo, int n) {
        for (int i = 0; i < n; i++) {
            double k = (double) i / (n - 1);
            double ang = Math.toRadians(a0 + (a1 - a0) * k);
            double l = largo * (0.80 + 0.18 * Math.sin(Math.PI * (0.15 + 0.7 * k)));
            Grafo g = new Grafo().mezcla(RGB);
            var a = g.nodo(CX + lado * 12, CY, 3.2).centro(NEGRO).radio(x -> NEGRO);
            var b = g.nodo(CX + lado * l * Math.cos(ang), CY + l * Math.sin(ang), 1.2).centro(NEGRO).radio(x -> NEGRO);
            g.arista(a, b);
            Plotter.raster(im).plot(g);
        }
    }

    static void disco(BufferedImage im, double x, double y, double r, int centro, int borde) {
        Point2D.Double a = new Point2D.Double(x, y), b = new Point2D.Double(x + 0.5, y);
        Plotter.raster(im).plot(Gota.lineal(a, r, b, r, Perfil.recto()), Colores.cuatroColores(centro, centro, borde, borde, Mezcla.RGB));
    }

    static void motas(BufferedImage im, int lado, double a0, double a1, double largo, int n, double r) {
        for (int i = 0; i < n; i++) {
            double k = (double) i / (n - 1);
            double ang = Math.toRadians(a0 + (a1 - a0) * k);
            double l = largo * (0.80 + 0.18 * Math.sin(Math.PI * (0.15 + 0.7 * k))) * 0.97;
            disco(im, CX + lado * l * Math.cos(ang), CY + l * Math.sin(ang), r * (0.8 + 0.4 * Math.sin(Math.PI * k)), BLANCO, BLANCO);
        }
    }

    static void fondo(BufferedImage im) {
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFF2F5D3A);
        Point2D.Double a = new Point2D.Double(W * 0.5, 320), b = new Point2D.Double(W * 0.5 + 0.5, 320);
        Plotter.raster(im).plot(Gota.lineal(a, 620, b, 620, Perfil.recto()),
            Colores.cuatroColores(0xFFBFD8A0, 0xFFBFD8A0, 0xFF1C3A28, 0xFF1C3A28, Mezcla.RGB));
    }

    static BufferedImage dibuja() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        fondo(im);
        for (int lado = -1; lado <= 1; lado += 2) {
            // alas inferiores (detrás), luego las superiores
            ala(im, lado, 18, 80, 215, 9, 26, 16, NARANJA);
            ala(im, lado, -80, -4, 300, 13, 34, 20, NARANJA);
        }
        for (int lado = -1; lado <= 1; lado += 2) {
            venas(im, lado, 22, 78, 215, 6);
            venas(im, lado, -78, -8, 300, 9);
            motas(im, lado, 22, 78, 215, 6, 5.5);
            motas(im, lado, -70, -10, 300, 10, 6.5);
        }
        // cuerpo: cabeza, tórax, abdomen
        Grafo c = new Grafo().mezcla(RGB);
        var h = c.nodo(CX, CY - 52, 13).centro(0xFF2A201C).radio(x -> mezcla(0xFF2A201C, NEGRO, x));
        var t = c.nodo(CX, CY - 12, 17).centro(0xFF2A201C).radio(x -> mezcla(0xFF2A201C, NEGRO, x));
        var ab = c.nodo(CX, CY + 150, 7).centro(0xFF2A201C).radio(x -> mezcla(0xFF2A201C, NEGRO, x));
        c.arista(h, t);
        c.arista(t, ab);
        Plotter.raster(im).plot(c);
        // motas del cuerpo
        for (int i = 0; i < 5; i++) disco(im, CX - 7 + (i % 2) * 14, CY - 36 + i * 11, 2.2, BLANCO, BLANCO);
        // antenas
        for (int lado = -1; lado <= 1; lado += 2) {
            Grafo an = new Grafo().mezcla(RGB);
            var a = an.nodo(CX + lado * 5, CY - 60, 2.2).centro(NEGRO).radio(x -> NEGRO);
            var b = an.nodo(CX + lado * 70, CY - 170, 4.2).centro(NEGRO).radio(x -> NEGRO);
            an.arista(a, b).tiradores(new Gota.Vec(lado * 12, -60), new Gota.Vec(lado * 40, -20));
            Plotter.raster(im).plot(an);
        }
        return im;
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(dibuja(), "png", new File("monarca_by_claude.png"));
        System.out.println("monarca_by_claude.png");
    }
}
