import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Los tres modos de costura de la gota ({@link Costura}) sobre cuatro gotas, con una
 * textura de referencia (líneas transversales en el cuerpo y radios en las tapas) y con un degradado.
 * Genera dos imágenes: costuras_circulos_texturas y costuras_circulos_degradados. Cada una tiene un modo por columna
 * (ORTOGONAL, ESPIGA, SUAVIZADO) y una gota por fila.
 * Solo usa la API pública de la biblioteca.
 */
public class CosturasDemo {
    static Point2D.Double p(double x, double y) { return new Point2D.Double(x, y); }

    /** Textura de referencia: la de lados distintos con líneas rojas transversales en el cuerpo y radios en las tapas. */
    static final class Guia implements Texturas.Textura {
        final Texturas.Textura base = Texturas.referenciaLados();
        static final double MEDIO = 0.012, SUAVE = 0.004;
        static final int ROJO = 0xFFC62828;
        @Override public double aspecto() { return 0.5; }
        static double acota(double x) { return x < 0 ? 0 : x > 1 ? 1 : x; }
        @Override public int muestrea(double u, double v) {
            int c = base.muestrea(u, v);
            if ((c >>> 24) == 0) return c;
            double y = 2 * v, l = 0;
            if (y < 0.5 || y > 1.5) {                          // tapas: un radio cada 30 grados
                double dx = u - 0.5, dy = y < 0.5 ? 0.5 - y : y - 1.5, d = Math.hypot(dx, dy);
                if (d > 0.03 && d <= 0.5) {
                    double th = Math.atan2(dy, dx);
                    for (int k = 1; k <= 5; k++) l = Math.max(l, acota(0.5 + (MEDIO - d * Math.abs(Math.sin(th - k * Math.PI / 6))) / SUAVE));
                }
            } else {                                           // cuerpo: siete líneas de v constante
                for (int m = 1; m <= 7; m++) l = Math.max(l, acota(0.5 + (MEDIO - Math.abs(y - (0.5 + m / 8.0))) / SUAVE));
            }
            int rgb = 0;
            for (int s = 16; s >= 0; s -= 8) rgb |= (int) Math.round(((c >> s) & 255) * (1 - l) + ((ROJO >> s) & 255) * l) << s;
            return (c & 0xFF000000) | rgb;
        }
    }

    static final int FONDO = 0xFFF4F1EA, TEXTO = 0xFF3A3A3A;

    record Caso(String titulo, String descripcion, Gota gota) {}

    // ---------- Las gotas ----------

    /** Círculos, a 40 px por unidad: r = 2 y d = 5 con radios iguales, y r1 = 1, r2 = 3 con d = 5. Rectas y con la Bézier deformada. */
    static Caso[] circulos() {
        Perfil recto = Perfil.recto();
        double u = 40, r = 2 * u, d = 5 * u;
        return new Caso[] {
            new Caso("Radios iguales", "r = 2, d = 5. Las costillas son perpendiculares\ny los tres modos coinciden.",
                     Gota.lineal(p(110, 150), r, p(110 + d, 150), r, recto)),
            new Caso("Radios iguales, Bézier deformada", "Con la curva, las líneas se abren en abanico\npor el lado exterior.",
                     Gota.curva(p(110, 185), r, new Gota.Vec(66, -50), p(110 + d, 185), r, new Gota.Vec(-66, -50), recto)),
            new Caso("Radios distintos", "r1 = 1, r2 = 3, d = 5. Aparecen las cuñas:\nlos modos se reparten distinto el cuerpo y las tapas.",
                     Gota.lineal(p(70, 150), u, p(70 + d, 150), 3 * u, recto)),
            new Caso("Radios distintos, Bézier deformada", "Las mismas cuñas con la Bézier deformada.",
                     Gota.curva(p(70, 190), u, new Gota.Vec(66, -45), p(70 + d, 190), 3 * u, new Gota.Vec(-66, -45), recto)),
        };
    }

    // ---------- Las hojas ----------

    static final Costura[] MODOS = Costura.values();
    static final String[] DESC_MODO = {
        "Líneas de t constante perpendiculares al camino.\nTapas: semidiscos.",
        "Líneas contorno - eje - contorno en recta (espiga):\nla costilla es el radio al punto de tangencia.\nTapas: sectores polares. Es el modo por defecto.",
        "Como la espiga, pero cada mitad es un Hermite\n(normal en el eje, costilla en el contorno):\nsin picos en el eje."
    };

    static void hoja(String archivo, String titulo, Caso[] casos, int w, int h, Relleno relleno) throws Exception {
        int ml = 310, mt = 135;
        BufferedImage hoja = new BufferedImage(ml + 3 * w, mt + casos.length * h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hoja.createGraphics();
        g.setColor(new Color(FONDO)); g.fillRect(0, 0, hoja.getWidth(), hoja.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(TEXTO));
        g.setFont(new Font("SansSerif", Font.BOLD, 20)); g.drawString(titulo, 14, 30);
        for (int c = 0; c < 3; c++) {
            g.setFont(new Font("SansSerif", Font.BOLD, 16)); g.drawString(MODOS[c].name(), ml + c * w + 10, 62);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12)); texto(g, DESC_MODO[c], ml + c * w + 10, 80);
        }
        for (int f = 0; f < casos.length; f++) {
            g.setColor(new Color(TEXTO));
            g.setFont(new Font("SansSerif", Font.BOLD, 13)); texto(g, casos[f].titulo(), 14, mt + f * h + h / 2 - 20);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12)); texto(g, casos[f].descripcion(), 14, mt + f * h + h / 2 + 18);
            for (int c = 0; c < 3; c++) {
                BufferedImage celda = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gc = celda.createGraphics();
                gc.setColor(new Color(FONDO)); gc.fillRect(0, 0, w, h); gc.dispose();
                casos[f].gota().render(celda, relleno, Opciones.POR_DEFECTO.conCostura(MODOS[c]));
                g.drawImage(celda, ml + c * w, mt + f * h, null);
            }
        }
        g.dispose();
        ImageIO.write(hoja, "png", new File(archivo));
    }

    /** Escribe un texto de varias líneas (separadas por \n); la primera línea queda en y. */
    static void texto(Graphics2D g, String s, int x, int y) {
        int lh = g.getFontMetrics().getHeight();
        for (String l : s.split("\n")) { g.drawString(l, x, y); y += lh; }
    }

    public static void main(String[] args) throws Exception {
        Relleno texturas = Texturas.tira(new Guia());
        Relleno degradado = Colores.cadenaLateral(
                new Colores.Lateral(0xFFD7263D, 0xFFFFE9A8, 0xFF2D6CDF),     // contra, centro y normal del primer círculo
                new Colores.Lateral(0xFF1FA39A, 0xFFE8F5D0, 0xFFF28E2B),     // y del segundo
                Mezcla.RGB);
        hoja("costuras_circulos_texturas.png", "Modos de costura: texturas, círculos", circulos(), 440, 330, texturas);
        hoja("costuras_circulos_degradados.png", "Modos de costura: degradados, círculos", circulos(), 440, 330, degradado);
        System.out.println("OK: costuras_circulos_texturas.png, costuras_circulos_degradados.png");
    }
}
