package common;

import coordenadas.Coordenadas;
import coordenadas.Opciones;
import forma.Gota;
import forma.Perfil;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

/**
 * Mapa de la parametrización de una gota: pinta, en cada píxel de su interior, la diferencia {@code fraccion(t) - t} entre
 * la longitud de arco recorrida hasta la t del píxel (como fracción del camino) y la propia t de la Bézier. Es cero donde la t
 * avanza a ritmo constante respecto de la longitud (en una recta uniforme, en toda la gota) y se aleja de cero donde acelera o
 * frena: es lo que separa {@code Tira.EN_T} de {@code Tira.POR_DEFECTO} (v por longitud de arco), y lo que dibujó la diferencia
 * entre las dos en el issue #78.
 *
 * <p>Escala divergente: gris neutro = 0, rojo = {@code fraccion(t) > t} (la longitud va por delante de t), azul = {@code fraccion(t) < t};
 * saturado en {@code ±tope}. Útil para ver qué tiradores dejan una parametrización desigual o para comprobar que un cambio en las
 * curvas no la empeora. Uso de la demostración: {@code MapaParametrizacion <salida.png>} (una hoja con varias gotas) y una tabla por
 * consola con el desvío máximo y medio de cada una.
 */
public class MapaParametrizacion {

    /** El mapa y sus medidas: desvío máximo y medio de |fraccion(t) - t| sobre los píxeles interiores, y cuántos eran. */
    public record Mapa(BufferedImage imagen, double maximo, double medio, int pixeles) {}

    /** Gris neutro (0), rojo (+) y azul (−), saturado en ±1. */
    static int color(double v) {
        double a = Math.min(1, Math.abs(v));
        int g = 0xB0, destino = v >= 0 ? 0xD02020 : 0x2040D0;
        int rgb = 0;
        for (int s = 16; s >= 0; s -= 8) {
            int c0 = g, c1 = (destino >> s) & 255;
            rgb |= (int) Math.round(c0 + (c1 - c0) * a) << s;
        }
        return 0xFF000000 | rgb;
    }

    /** Mapa de la gota con el ancho dado (el alto sigue su proporción) y un margen de {@code margen} píxeles; {@code tope} es el desvío que satura el color. */
    public static Mapa mapa(Gota g, int ancho, int margen, double tope) {
        Rectangle2D b = g.bounds();
        double e = (ancho - 2.0 * margen) / b.getWidth();
        int alto = (int) Math.ceil(b.getHeight() * e + 2.0 * margen);
        BufferedImage im = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = im.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, ancho, alto);
        g2.dispose();
        double max = 0, suma = 0;
        int n = 0;
        for (int j = 0; j < alto; j++)
            for (int i = 0; i < ancho; i++) {
                double x = b.getX() + (i + 0.5 - margen) / e, y = b.getY() + (j + 0.5 - margen) / e;
                if (!g.mascara().contiene(x, y)) continue;
                double t = Coordenadas.parametros(g, x, y, Opciones.POR_DEFECTO).t();
                double d = g.fraccion(t) - t;
                im.setRGB(i, j, color(d / tope));
                max = Math.max(max, Math.abs(d));
                suma += Math.abs(d);
                n++;
            }
        return new Mapa(im, max, n == 0 ? 0 : suma / n, n);
    }

    static Gota gota(double x1, double y1, double r1, Gota.Vec v1, double x2, double y2, double r2, Gota.Vec v2) {
        Point2D.Double c1 = new Point2D.Double(x1, y1), c2 = new Point2D.Double(x2, y2);
        return v1 == null ? Gota.lineal(c1, r1, c2, r2, Perfil.recto()) : Gota.curva(c1, r1, v1, c2, r2, v2, Perfil.recto());
    }

    /** Las gotas de la demostración, con su nombre, de la recta a la curva más desigual. */
    static Object[][] demo() {
        return new Object[][] {
            { "recta", gota(40, 100, 30, null, 360, 100, 30, null) },
            { "curva suave (tiradores iguales y cortos)", gota(40, 160, 30, new Gota.Vec(40, -40), 360, 60, 30, new Gota.Vec(40, 40)) },
            { "tirador largo en un extremo", gota(40, 160, 30, new Gota.Vec(300, -30), 360, 60, 30, new Gota.Vec(20, 40)) },
            { "tiradores largos en los dos", gota(40, 160, 30, new Gota.Vec(200, -250), 360, 60, 20, new Gota.Vec(200, 250)) },
        };
    }

    public static void main(String[] args) throws Exception {
        Object[][] gs = demo();
        Mapa[] ms = new Mapa[gs.length];
        int w = 0, h = 0;
        for (int i = 0; i < gs.length; i++) {
            ms[i] = mapa((Gota) gs[i][1], 480, 12, 0.25);
            w = Math.max(w, ms[i].imagen().getWidth());
            h += ms[i].imagen().getHeight();
            System.out.printf("%-44s desvío máx. %.3f, medio %.3f%n", gs[i][0], ms[i].maximo(), ms[i].medio());
        }
        if (args.length > 0) {
            BufferedImage hoja = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = hoja.createGraphics();
            int y = 0;
            for (Mapa m : ms) { g2.drawImage(m.imagen(), 0, y, null); y += m.imagen().getHeight(); }
            g2.dispose();
            ImageIO.write(hoja, "png", new File(args[0]));
        }
    }
}
