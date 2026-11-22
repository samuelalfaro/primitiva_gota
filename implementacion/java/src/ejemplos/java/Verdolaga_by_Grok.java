import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Verdolaga (Grok): tallos ramificados rojos tipo coral, hojas carnosas verdes y florecitas amarillas.
 * Usa Nodo.radio / Arista.espina. Escribe verdolaga_by_grok.png.
 */
public class Verdolaga_by_Grok {
    static final int W = 720, H = 560;
    static final Mezcla RGB = Mezcla.RGB;

    static int mezcla(int a, int b, double t) { return RGB.mezclar(a, b, Math.max(0, Math.min(1, t))); }

    static final int ROJO1 = 0xFFC43A2A;
    static final int ROJO2 = 0xFFE05A3C;
    static final int ROJO3 = 0xFFF07A55;
    static final int VERDE1 = 0xFF3D8B4A;
    static final int VERDE2 = 0xFF6BB86A;
    static final int VERDE3 = 0xFF2A5E32;
    static final int AMAR1 = 0xFFFFE066;
    static final int AMAR2 = 0xFFFFC107;
    static final int AMAR3 = 0xFFE8A000;

    static Degradado radioTallo(int c) {
        return x -> mezcla(c, mezcla(c, 0xFF5A1810, 0.55), 0.35 * x * x);
    }
    static Degradado espinaTallo(int a, int b) { return x -> mezcla(a, b, x); }
    static Degradado radioHoja(int c) {
        return x -> mezcla(c, mezcla(c, VERDE3, 0.7), 0.55 * x * x);
    }
    static Degradado radioPetalo(int c) {
        return x -> mezcla(c, mezcla(c, AMAR3, 0.5), 0.4 * x * x);
    }

    static Composicion.Nodo segmento(Composicion c, Composicion.Nodo prev,
                                     double x, double y, double r, int col, int colPrev) {
        var nd = c.nodo(x, y, r).centro(col).radio(radioTallo(col));
        if (prev != null) c.arista(prev, nd).espina(espinaTallo(colPrev, col));
        return nd;
    }

    static Composicion.Nodo rama(Composicion c, Composicion.Nodo desde, double x0, double y0, double ang, double largo,
                                 double r0, double r1, int col0, int col1, int nSeg) {
        Composicion.Nodo prev = desde;
        int colPrev = col0;
        int i0 = desde == null ? 0 : 1;
        for (int i = i0; i < nSeg; i++) {
            double t = (double) i / (nSeg - 1);
            double ond = Math.sin(t * Math.PI * 1.4 + ang * 3) * (6 + 10 * t);
            double x = x0 + Math.cos(ang) * largo * t - Math.sin(ang) * ond;
            double y = y0 + Math.sin(ang) * largo * t + Math.cos(ang) * ond * 0.4;
            double r = r0 + (r1 - r0) * t;
            int col = mezcla(col0, col1, t);
            prev = segmento(c, prev, x, y, r, col, colPrev);
            colPrev = col;
        }
        return prev;
    }

    static void hoja(Composicion c, double x0, double y0, double ang, double largo, double rBase, double rPunta, int tono) {
        int c0 = mezcla(tono, VERDE1, 0.3);
        int c1 = mezcla(tono, VERDE2, 0.4);
        var a = c.nodo(x0, y0, rBase).centro(c0).radio(radioHoja(c0));
        double x1 = x0 + Math.cos(ang) * largo;
        double y1 = y0 + Math.sin(ang) * largo;
        var b = c.nodo(x1, y1, rPunta).centro(c1).radio(radioHoja(c1));
        c.arista(a, b).espina(x -> mezcla(c0, c1, x))
            .tiradores(new Gota.Vec(Math.cos(ang + 0.4) * largo * 0.35, Math.sin(ang + 0.4) * largo * 0.35),
                       new Gota.Vec(-Math.cos(ang) * largo * 0.15, -Math.sin(ang) * largo * 0.15));
    }

    static void flor(Composicion c, double cx, double cy, double escala) {
        int centro = AMAR2;
        var m = c.nodo(cx, cy, 2.2 * escala).centro(centro).radio(x -> mezcla(centro, AMAR3, 0.3 * x));
        for (int k = 0; k < 5; k++) {
            double th = -Math.PI / 2 + 2 * Math.PI * k / 5;
            double L = 9 * escala;
            double px = cx + Math.cos(th) * L;
            double py = cy + Math.sin(th) * L;
            int col = mezcla(AMAR1, AMAR2, 0.3 + 0.2 * (k % 2));
            var p = c.nodo(px, py, 3.5 * escala).centro(col).radio(radioPetalo(col));
            c.arista(m, p).espina(x -> mezcla(centro, col, x));
        }
    }

    static void fondo(BufferedImage im) {
        int[] pix = ((DataBufferInt) im.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < H; y++) {
            double t = y / (double) (H - 1);
            int col = mezcla(0xFFF7F2E8, 0xFFE8DFD0, t);
            for (int x = 0; x < W; x++) pix[y * W + x] = col;
        }
    }

    static double[] puntoRama(double x0, double y0, double ang, double largo, double t) {
        double ond = Math.sin(t * Math.PI * 1.4 + ang * 3) * (6 + 10 * t);
        return new double[] {
            x0 + Math.cos(ang) * largo * t - Math.sin(ang) * ond,
            y0 + Math.sin(ang) * largo * t + Math.cos(ang) * ond * 0.4
        };
    }

    static void colocarHojas(Composicion c, double x0, double y0, double ang, double largo, int n) {
        for (int i = 1; i <= n; i++) {
            double t = (i - 0.3) / (n + 0.4);
            double[] p = puntoRama(x0, y0, ang, largo, t);
            double lado = (i % 2 == 0) ? 1 : -1;
            double angHoja = ang + lado * Math.toRadians(55 + 15 * Math.sin(i * 1.7));
            double L = 28 + 10 * Math.sin(i * 2.1);
            double rB = 7.5 + 1.5 * Math.sin(i);
            double rP = 4.2 + 1.0 * Math.cos(i * 1.3);
            int tono = mezcla(VERDE1, VERDE2, 0.3 + 0.4 * ((i % 3) / 2.0));
            hoja(c, p[0], p[1], angHoja, L, rB, rP, tono);
            if (i % 3 != 0) {
                double ang2 = ang - lado * Math.toRadians(50 + 10 * Math.cos(i));
                hoja(c, p[0], p[1], ang2, L * 0.75, rB * 0.85, rP * 0.85, mezcla(tono, VERDE3, 0.15));
            }
        }
    }

    static BufferedImage dibuja() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        fondo(im);
        Composicion c = new Composicion().mezcla(RGB);

        double bx = 90, by = 430;
        var base = c.nodo(bx, by, 9).centro(ROJO1).radio(radioTallo(ROJO1));

        double ang0 = Math.toRadians(-28);
        rama(c, base, bx, by, ang0, 340, 9.0, 5.5, ROJO1, ROJO2, 8);
        double[] mid = puntoRama(bx, by, ang0, 340, 0.45);
        rama(c, null, mid[0], mid[1], ang0 + Math.toRadians(-38), 160, 5.5, 3.5, ROJO2, ROJO3, 5);
        rama(c, null, mid[0], mid[1], ang0 + Math.toRadians(32), 140, 5.0, 3.2, ROJO2, ROJO3, 5);

        double ang1 = Math.toRadians(12);
        rama(c, base, bx, by, ang1, 240, 7.5, 4.5, ROJO1, ROJO2, 6);
        double[] mid2 = puntoRama(bx, by, ang1, 240, 0.5);
        rama(c, null, mid2[0], mid2[1], ang1 + Math.toRadians(-40), 130, 4.5, 2.8, ROJO2, ROJO3, 4);
        rama(c, null, mid2[0], mid2[1], ang1 + Math.toRadians(28), 120, 4.2, 2.6, ROJO2, ROJO3, 4);

        double ang2 = Math.toRadians(-55);
        rama(c, base, bx, by, ang2, 200, 6.5, 3.5, ROJO1, ROJO3, 6);
        double[] mid3 = puntoRama(bx, by, ang2, 200, 0.55);
        rama(c, null, mid3[0], mid3[1], ang2 + Math.toRadians(40), 110, 3.8, 2.4, ROJO2, ROJO3, 4);

        rama(c, base, bx, by, Math.toRadians(155), 150, 6.0, 3.5, ROJO1, ROJO2, 5);
        rama(c, base, bx, by, Math.toRadians(95), 100, 5.0, 3.0, ROJO1, ROJO2, 4);

        // rama extra hacia la derecha
        double ang3 = Math.toRadians(-8);
        rama(c, base, bx, by, ang3, 300, 7.0, 4.0, ROJO1, ROJO2, 7);
        double[] mid4 = puntoRama(bx, by, ang3, 300, 0.5);
        rama(c, null, mid4[0], mid4[1], ang3 + Math.toRadians(-30), 150, 4.5, 2.8, ROJO2, ROJO3, 5);
        rama(c, null, mid4[0], mid4[1], ang3 + Math.toRadians(25), 140, 4.2, 2.6, ROJO2, ROJO3, 5);

        colocarHojas(c, bx, by, ang0, 340, 9);
        colocarHojas(c, mid[0], mid[1], ang0 + Math.toRadians(-38), 160, 6);
        colocarHojas(c, mid[0], mid[1], ang0 + Math.toRadians(32), 140, 6);
        colocarHojas(c, bx, by, ang1, 240, 8);
        colocarHojas(c, mid2[0], mid2[1], ang1 + Math.toRadians(-40), 130, 5);
        colocarHojas(c, mid2[0], mid2[1], ang1 + Math.toRadians(28), 120, 5);
        colocarHojas(c, bx, by, ang2, 200, 7);
        colocarHojas(c, mid3[0], mid3[1], ang2 + Math.toRadians(40), 110, 5);
        colocarHojas(c, bx, by, Math.toRadians(155), 150, 6);
        colocarHojas(c, bx, by, Math.toRadians(95), 100, 5);
        colocarHojas(c, bx, by, ang3, 300, 8);
        colocarHojas(c, mid4[0], mid4[1], ang3 + Math.toRadians(-30), 150, 5);
        colocarHojas(c, mid4[0], mid4[1], ang3 + Math.toRadians(25), 140, 5);

        flor(c, puntoRama(bx, by, ang0, 340, 1.0)[0], puntoRama(bx, by, ang0, 340, 1.0)[1], 1.0);
        flor(c, puntoRama(mid[0], mid[1], ang0 + Math.toRadians(-38), 160, 1.0)[0],
                puntoRama(mid[0], mid[1], ang0 + Math.toRadians(-38), 160, 1.0)[1], 0.85);
        flor(c, puntoRama(mid[0], mid[1], ang0 + Math.toRadians(32), 140, 1.0)[0],
                puntoRama(mid[0], mid[1], ang0 + Math.toRadians(32), 140, 1.0)[1], 0.8);
        flor(c, puntoRama(bx, by, ang1, 240, 1.0)[0], puntoRama(bx, by, ang1, 240, 1.0)[1], 0.95);
        flor(c, puntoRama(mid2[0], mid2[1], ang1 + Math.toRadians(-40), 130, 1.0)[0],
                puntoRama(mid2[0], mid2[1], ang1 + Math.toRadians(-40), 130, 1.0)[1], 0.75);
        flor(c, puntoRama(mid2[0], mid2[1], ang1 + Math.toRadians(28), 120, 1.0)[0],
                puntoRama(mid2[0], mid2[1], ang1 + Math.toRadians(28), 120, 1.0)[1], 0.7);
        flor(c, puntoRama(bx, by, ang2, 200, 1.0)[0], puntoRama(bx, by, ang2, 200, 1.0)[1], 0.9);
        flor(c, puntoRama(mid3[0], mid3[1], ang2 + Math.toRadians(40), 110, 1.0)[0],
                puntoRama(mid3[0], mid3[1], ang2 + Math.toRadians(40), 110, 1.0)[1], 0.7);
        double[] midFlor = puntoRama(bx, by, ang0, 340, 0.72);
        flor(c, midFlor[0] + 8, midFlor[1] - 12, 0.65);
        flor(c, puntoRama(bx, by, ang3, 300, 1.0)[0], puntoRama(bx, by, ang3, 300, 1.0)[1], 0.9);
        flor(c, puntoRama(mid4[0], mid4[1], ang3 + Math.toRadians(-30), 150, 1.0)[0],
                puntoRama(mid4[0], mid4[1], ang3 + Math.toRadians(-30), 150, 1.0)[1], 0.75);
        flor(c, puntoRama(mid4[0], mid4[1], ang3 + Math.toRadians(25), 140, 1.0)[0],
                puntoRama(mid4[0], mid4[1], ang3 + Math.toRadians(25), 140, 1.0)[1], 0.7);

        c.render(im, Opciones.POR_DEFECTO.conSolape(Solape.TRAZO));
        return im;
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(dibuja(), "png", new File("verdolaga_by_grok.png"));
        System.out.println("verdolaga_by_grok.png");
    }
}
