import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Pulpo (Grok): cabeza bulbosa y ocho tentáculos ondulados.
 * Volumen en radio, color a lo largo de la espina, puntas con fade de alfa.
 * Escribe pulpo_by_grok.png.
 */
public class Pulpo_by_Grok {
    static final int W = 700, H = 780;
    static final Mezcla RGB = Mezcla.RGB;

    static int mezcla(int a, int b, double t) { return RGB.mezclar(a, b, Math.max(0, Math.min(1, t))); }
    static int conAlfa(int c, double k) {
        return ((int) Math.round((c >>> 24) * Math.max(0, Math.min(1, k)))) << 24 | (c & 0xFFFFFF);
    }

    static final int VIOLETA = 0xFF6B3FA0;
    static final int MORADO  = 0xFF8B5CF0;
    static final int ROSA    = 0xFFC084FC;
    static final int CREMA   = 0xFFE9D5FF;
    static final int OSCURO  = 0xFF2E1065;

    static Degradado radioCuerpo(int c) {
        return x -> mezcla(c, OSCURO, 0.50 * x * x);
    }
    static Degradado radioTentaculo(int c) {
        return x -> {
            int borde = mezcla(c, OSCURO, 0.65);
            return mezcla(c, borde, 0.45 * x * x);
        };
    }
    static Degradado radioPunta(int c) {
        return x -> {
            double a = Math.max(0, 1 - 0.85 * x * x);
            return conAlfa(mezcla(c, OSCURO, 0.35 * x), a);
        };
    }

    static void fondo(BufferedImage im) {
        int[] pix = ((DataBufferInt) im.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < H; y++) {
            double t = y / (double) (H - 1);
            int col = mezcla(0xFF0C1B33, 0xFF061018, t);
            for (int x = 0; x < W; x++) {
                double dx = (x - W * 0.5) / (W * 0.55);
                double dy = (y - H * 0.4) / (H * 0.55);
                double v = Math.min(1, dx * dx + dy * dy);
                pix[y * W + x] = mezcla(col, 0xFF02060C, 0.35 * v);
            }
        }
    }

    /** Tentáculo: cadena ondulada desde (x0,y0) en dirección ang, con largo y radios. */
    static void tentaculo(Grafo c, double x0, double y0, double ang, double largo,
                          double r0, double r1, int col0, int col1, int nSeg, double ondAmp) {
        Grafo.Nodo prev = null;
        int colPrev = col0;
        for (int i = 0; i < nSeg; i++) {
            double t = (double) i / (nSeg - 1);
            double ond = Math.sin(t * Math.PI * 2.2 + ang * 2.5) * ondAmp * (0.4 + 0.6 * t);
            double x = x0 + Math.cos(ang) * largo * t - Math.sin(ang) * ond;
            double y = y0 + Math.sin(ang) * largo * t + Math.cos(ang) * ond * 0.55;
            double r = r0 + (r1 - r0) * Math.pow(t, 0.85);
            int col = mezcla(col0, col1, t);
            Degradado rad = (i >= nSeg - 2) ? radioPunta(col) : radioTentaculo(col);
            var nd = c.nodo(x, y, r).centro(col).radio(rad);
            if (prev != null) {
                final int a = colPrev, b = col;
                c.arista(prev, nd).espina(x2 -> mezcla(a, b, x2));
            }
            prev = nd;
            colPrev = col;
        }
    }

    static void disco(BufferedImage im, double x, double y, double r, int centro, int borde) {
        java.awt.geom.Point2D.Double a = new java.awt.geom.Point2D.Double(x, y);
        java.awt.geom.Point2D.Double b = new java.awt.geom.Point2D.Double(x + 0.5, y);
        Plotter.raster(im).plot(Gota.lineal(a, r, b, r, Perfil.recto()),
            Colores.cuatroColores(centro, centro, borde, borde, Mezcla.RGB));
    }

    static BufferedImage dibuja() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        fondo(im);
        Grafo c = new Grafo().mezcla(RGB);

        // --- cabeza (manto) ---
        double cx = 350, cy = 250;
        var cabeza = c.nodo(cx, cy, 78).centro(MORADO).radio(radioCuerpo(MORADO));
        int colCima = mezcla(MORADO, CREMA, 0.25);
        var cima = c.nodo(cx - 8, cy - 72, 38).centro(colCima).radio(radioCuerpo(colCima));
        c.arista(cabeza, cima).espina(x -> mezcla(MORADO, colCima, x));
        var saco = c.nodo(cx + 6, cy + 70, 48).centro(VIOLETA).radio(radioCuerpo(VIOLETA));
        c.arista(cabeza, saco).espina(x -> mezcla(MORADO, VIOLETA, x));

        // --- ocho tentáculos ---
        double[] angs = {
            Math.toRadians(95), Math.toRadians(112), Math.toRadians(130), Math.toRadians(148),
            Math.toRadians(70), Math.toRadians(55), Math.toRadians(38), Math.toRadians(22)
        };
        double[] largos = { 320, 300, 280, 260, 310, 290, 270, 250 };
        double[] r0s = { 22, 20, 18, 16, 21, 19, 17, 15 };
        double[] r1s = { 4.5, 4.0, 3.5, 3.0, 4.2, 3.8, 3.4, 3.0 };
        double[] onds = { 28, 34, 40, 36, 30, 38, 32, 26 };
        int[] cols0 = { VIOLETA, MORADO, VIOLETA, MORADO, VIOLETA, MORADO, VIOLETA, MORADO };
        int[] cols1 = { ROSA, mezcla(ROSA, CREMA, 0.3), ROSA, mezcla(ROSA, 0xFFA855F7, 0.4),
                        ROSA, mezcla(ROSA, CREMA, 0.25), ROSA, mezcla(ROSA, 0xFFA855F7, 0.35) };

        for (int i = 0; i < 8; i++) {
            double bx = cx + Math.cos(angs[i]) * 35;
            double by = cy + 55 + Math.sin(angs[i]) * 15;
            tentaculo(c, bx, by, angs[i], largos[i], r0s[i], r1s[i], cols0[i], cols1[i], 9, onds[i]);
        }

        c.render(im, Opciones.POR_DEFECTO.conSolape(Solape.TRAZO));
        // ojos encima (discos, sin forzar consistencia de Funciones)
        disco(im, cx - 36, cy - 8, 15, CREMA, mezcla(CREMA, OSCURO, 0.35));
        disco(im, cx - 34, cy - 6, 7, OSCURO, OSCURO);
        disco(im, cx - 37, cy - 10, 2.4, 0xFFFFFFFF, 0xFFFFFFFF);
        disco(im, cx + 40, cy - 6, 15, CREMA, mezcla(CREMA, OSCURO, 0.35));
        disco(im, cx + 42, cy - 4, 7, OSCURO, OSCURO);
        disco(im, cx + 39, cy - 8, 2.4, 0xFFFFFFFF, 0xFFFFFFFF);
        return im;
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(dibuja(), "png", new File("pulpo_by_grok.png"));
        System.out.println("pulpo_by_grok.png");
    }
}
