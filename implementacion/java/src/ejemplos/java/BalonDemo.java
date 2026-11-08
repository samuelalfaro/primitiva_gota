import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Balón de playa como prueba de la silueta nueva: dos círculos con el mismo centro y el mismo radio (d = 0), cuatro colores en
 * los extremos P1..P4 de la silueta, el mismo color en los dos centros, una textura y el modo MODULATE.
 * Columnas: 1 la gota existente con los mismos datos (centros iguales); 2-4 la silueta nueva, solo colores (RGB), colores x textura
 * (RGB) y colores x textura (LCH). Escribe balon.png.
 */
public class BalonDemo {
    static final int W = 360, H = 380, FONDO = 0xFFF4F1EA;
    static final int ROJO = 0xFFE53935, AMARILLO = 0xFFFDD835, AZUL = 0xFF1E88E5, VERDE = 0xFF43A047, CENTRO = 0xFFFFFFFF;

    /** Textura de prueba (u, v en [0,1]): blanco con anillos, radios y una rejilla para ver cómo se reparte. */
    static final Texturas.Textura TEX = (u, v) -> {
        double x = 2 * u - 1, y = 2 * v - 1, r = Math.hypot(x, y), a = Math.atan2(y, x);
        double anillo = Math.abs(((r * 5) % 1 + 1) % 1 - 0.5);
        double radio = Math.abs(((a / (2 * Math.PI) * 24) % 1 + 1) % 1 - 0.5);
        boolean linea = anillo > 0.45 || (radio > 0.46 && r > 0.1);
        boolean casilla = ((int) Math.floor(u * 8) + (int) Math.floor(v * 8)) % 2 == 0;
        int g = linea ? 0x55 : (casilla ? 0xFF : 0xE6);
        return 0xFF000000 | g << 16 | g << 8 | g;
    };

    /** Colores de la silueta con d = 0: abanico entre los colores de las cuatro puntas, hacia el color del centro con rho. */
    static int color(double dx, double dy, double R, double[] ang, int[] col, Mezcla m, double gamma) {
        double th = Math.atan2(dy, dx), rho = Math.min(1, Math.hypot(dx, dy) / R);
        int n = ang.length, k = 0;
        // sector (ang[i], ang[i+1]) que contiene th, con ang ordenados de forma creciente en (-π, π]
        for (int i = 0; i < n; i++) {
            double a0 = ang[i], a1 = ang[(i + 1) % n] + (i + 1 == n ? 2 * Math.PI : 0), t = th < a0 ? th + 2 * Math.PI : th;
            if (t >= a0 && t <= a1) { k = i; break; }
        }
        double a0 = ang[k], a1 = ang[(k + 1) % n] + (k + 1 == n ? 2 * Math.PI : 0), t = th < a0 ? th + 2 * Math.PI : th;
        double f = (t - a0) / (a1 - a0);
        int borde = m.mezclar(col[k], col[(k + 1) % n], f);
        return m.mezclar(CENTRO, borde, Math.pow(rho, gamma));
    }

    static BufferedImage nueva(double R, Mezcla m, boolean textura) {
        Point2D.Double c = new Point2D.Double(W / 2.0, H / 2.0 + 14);
        Silueta s = Silueta.de(c, R, c, R);
        BufferedImage mask = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = mask.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fill(s.forma());
        g.dispose();
        // P1, P2 (tapa 1), P3, P4 (tapa 2) → ángulos alrededor del centro, en orden creciente
        Point2D.Double[] e = s.extremos();
        int[] cp = { ROJO, VERDE, AMARILLO, AZUL };                     // P1 (izq., arriba), P2 (izq., abajo), P3 (dcha., arriba), P4 (dcha., abajo)
        Integer[] idx = { 0, 1, 2, 3 };
        java.util.Arrays.sort(idx, (p, q) -> Double.compare(Math.atan2(e[p].y - c.y, e[p].x - c.x), Math.atan2(e[q].y - c.y, e[q].x - c.x)));
        double[] ang = new double[4];
        int[] col = new int[4];
        for (int i = 0; i < 4; i++) { ang[i] = Math.atan2(e[idx[i]].y - c.y, e[idx[i]].x - c.x); col[i] = cp[idx[i]]; }
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int a = mask.getRGB(x, y) >>> 24;
                if (a == 0) continue;
                int cf = color(x + 0.5 - c.x, y + 0.5 - c.y, R, ang, col, m, 1.6);
                if (textura) {
                    int ct = TEX.muestrea((x + 0.5 - c.x) / (2 * R) + 0.5, (y + 0.5 - c.y) / (2 * R) + 0.5);
                    cf = ComposicionColor.aplica(ComposicionColor.Modo.MODULATE, cf, ct, 0xFF000000);
                }
                mask.setRGB(x, y, (a << 24) | (cf & 0xFFFFFF));
            }
        return mask;
    }

    static BufferedImage existente(double R) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Point2D.Double c = new Point2D.Double(W / 2.0, H / 2.0 + 14);
        try {
            Relleno col = Colores.cuatroColores(CENTRO, CENTRO, ROJO, AZUL);
            Relleno rel = ComposicionColor.combinar(col, Texturas.disco(TEX), ComposicionColor.Modo.MODULATE);
            Gota.lineal(c, R, c, R, Perfil.central(1.0)).render(img, rel);
        } catch (RuntimeException ex) {
            Graphics2D g = img.createGraphics();
            g.setColor(Color.RED);
            g.drawString("Gota con d = 0: " + ex, 10, 200);
            g.dispose();
        }
        return img;
    }

    public static void main(String[] args) throws Exception {
        double R = 120;
        BufferedImage[] cel = { existente(R), nueva(R, Mezcla.RGB, false), nueva(R, Mezcla.RGB, true), nueva(R, Mezcla.LCH, true) };
        String[] nom = { "Gota existente, d = 0", "Silueta: colores (RGB)", "Silueta: color x textura, RGB", "Silueta: color x textura, LCH" };
        BufferedImage out = new BufferedImage(4 * W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(FONDO));
        g.fillRect(0, 0, out.getWidth(), H);
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        for (int i = 0; i < 4; i++) {
            g.drawImage(cel[i], i * W, 0, null);
            g.setColor(new Color(0x3A3A3A));
            g.drawString(nom[i], i * W + 10, 20);
        }
        g.dispose();
        ImageIO.write(out, "png", new File("balon.png"));
    }
}
