import color.Colores;
import color.Degradado;
import color.Mezcla;
import coordenadas.Opciones;
import forma.Gota;
import forma.Perfil;
import geometria.composicion.Pintada;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.Arrays;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;
import javax.imageio.ImageIO;

/**
 * Estudio #86: pigmento en LCH (Oklab) sobre la Rejilla, sin tocar la API.
 * <p>
 * Cada gota usa las cinco funciones de {@code Colores.Funciones}: el color del centro es el pigmento y hacia el contorno se
 * modifica el croma conservando tono y luminosidad (en Oklab), y el alfa con una función aparte. Cuatro variantes más una de
 * referencia (color plano y alfa constante):
 * <ul>
 * <li>croma LAVADO: baja hacia el borde (el pigmento se aclara);</li>
 * <li>croma PIGMENTO: sube hacia el borde (el pigmento se acumula);</li>
 * <li>alfa CONSTANTE o alfa que SUBE hacia el borde (como el pigmento acumulado).</li>
 * </ul>
 * Mismas gotas, mismas alturas y misma Rejilla (N=3 estrechada, filtro sRGB) en todas. Escribe pigmento_*.png y una comparativa.
 */
public class PruebaPigmento {
    static final int W = 640, H = 640, FONDO = 0xFFF4F1EA, N = 16;

    // --- Oklab ---
    static double lin(double c) { return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }
    static double gam(double c) { c = Math.max(0, Math.min(1, c)); return c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055; }

    static double[] aOklab(int rgb) {
        double r = lin(((rgb >> 16) & 255) / 255.0), g = lin(((rgb >> 8) & 255) / 255.0), b = lin((rgb & 255) / 255.0);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[] { 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                              1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                              0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s };
    }

    static int deOklab(double L, double a, double b) {
        double l = L + 0.3963377774 * a + 0.2158037573 * b, m = L - 0.1055613458 * a - 0.0638541728 * b, s = L - 0.0894841775 * a - 1.2914855480 * b;
        l = l * l * l; m = m * m * m; s = s * s * s;
        double r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
        double g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
        double bb = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;
        return (int) Math.round(gam(r) * 255) << 16 | (int) Math.round(gam(g) * 255) << 8 | (int) Math.round(gam(bb) * 255);
    }

    /** Mismo tono y luminosidad, croma multiplicado por k. */
    static int conCroma(int rgb, double k) { double[] o = aOklab(rgb); return deOklab(o[0], o[1] * k, o[2] * k); }

    enum Croma { LAVADO(x -> 1 - 0.75 * x * x), PIGMENTO(x -> 0.35 + 0.65 * x * x);
        final DoubleUnaryOperator k; Croma(DoubleUnaryOperator k) { this.k = k; } }
    enum Alfa { CONSTANTE(x -> 1.0), SUBE(x -> 0.55 + 0.45 * x * x);
        final DoubleUnaryOperator k; Alfa(DoubleUnaryOperator k) { this.k = k; } }

    static Degradado pigmento(int rgb, double alfa0, Croma c, Alfa a) {
        return x -> {
            x = Math.max(0, Math.min(1, x));
            double ca = Math.max(0, Math.min(1, alfa0 * a.k.applyAsDouble(x)));
            return (int) Math.round(255 * ca) << 24 | conCroma(rgb, c.k.applyAsDouble(x));
        };
    }

    /** null en croma = referencia: color plano con alfa constante. */
    static Pintada gota(Random r, double alt, Croma croma, Alfa alfa) {
        double x = 90 + r.nextDouble() * (W - 180), y = 90 + r.nextDouble() * (H - 180), ang = r.nextDouble() * Math.PI, d = 30 + r.nextDouble() * 120;
        double r1 = 20 + r.nextDouble() * 40, r2 = 15 + r.nextDouble() * 40;
        int rgb = Color.HSBtoRGB((float) r.nextDouble(), (float) (0.55 + 0.4 * r.nextDouble()), (float) (0.7 + 0.3 * r.nextDouble())) & 0xFFFFFF;
        double a0 = 0.45 + 0.30 * r.nextDouble();
        Degradado f = croma == null ? Degradado.constante((int) Math.round(255 * a0) << 24 | rgb) : pigmento(rgb, a0, croma, alfa);
        return Pintada.de(Gota.lineal(new Point2D.Double(x, y), r1, new Point2D.Double(x + d * Math.cos(ang), y + d * Math.sin(ang)), r2, Perfil.recto()),
                Colores.cadenaLateral(new Colores.Funciones(f, f, Degradado.constante(f.en(0)), f, f), Mezcla.LCH)).conAltura(alt);
    }

    static BufferedImage pinta(Croma croma, Alfa alfa) {
        Random r = new Random(86);
        Rejilla rj = new Rejilla(3, 32, Rejilla.Cobertura.ESTRECHADA, Rejilla.Filtro.SRGB);
        for (int i = 0; i < N; i++) rj.aporta(gota(r, i * 1.3 + r.nextDouble() * 0.5, croma, alfa), Opciones.POR_DEFECTO);
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), FONDO);
        rj.prensa(im);
        return im;
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : ".");
        dir.mkdirs();
        String[] nom = new String[5];
        BufferedImage[] im = new BufferedImage[5];
        nom[0] = "referencia: color plano, alfa constante"; im[0] = pinta(null, null);
        int k = 1;
        for (Alfa a : Alfa.values()) for (Croma c : Croma.values()) { nom[k] = "croma " + c + ", alfa " + a; im[k++] = pinta(c, a); }
        for (int i = 0; i < 5; i++) ImageIO.write(im[i], "png", new File(dir, "pigmento_" + i + ".png"));
        int w = 420, h = 420 + 22;
        BufferedImage m = new BufferedImage(3 * w, 2 * h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = m.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, m.getWidth(), m.getHeight());
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        for (int i = 0; i < 5; i++) {
            int cx = (i % 3) * w, cy = (i / 3) * h;
            g.drawImage(im[i], cx, cy + 22, 420, 420, null);
            g.setColor(Color.DARK_GRAY); g.drawString(nom[i], cx + 8, cy + 15);
        }
        g.dispose();
        ImageIO.write(m, "png", new File(dir, "pigmento_comparacion.png"));
        System.out.println("PruebaPigmento -> " + dir.getAbsolutePath());
    }
}
