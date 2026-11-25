import geometria.composicion.Grafo;
import pintado.Plotter;
import coordenadas.Solape;
import coordenadas.Opciones;
import color.Mezcla;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Abecedario minúsculas a–z construido solo con la primitiva Gota
 * (cadenas de nodos de radio variable). Negro puro sobre blanco.
 * Espacio canónico ~70×100 (x-height ~58). Máximo ~10-12 nodos por letra.
 * Primera versión (esquema visual → código). Escribe abecedario_minusculas_by_grok.png.
 */
public class AbecedarioMinusculas_by_Grok {

    static final int W = 1120, H = 520;
    static final int NEGRO = 0xFF000000;
    static final Mezcla RGB = Mezcla.RGB;

    static final double CW = 70;
    static final double CH = 110;
    static final double OX0 = 40, OY0 = 30;
    static final double XH = 58; // x-height aproximada

    static void fondoBlanco(BufferedImage im) {
        int[] pix = ((DataBufferInt) im.getRaster().getDataBuffer()).getData();
        java.util.Arrays.fill(pix, 0xFFFFFFFF);
    }

    static Grafo.Nodo n(Grafo g, double x, double y, double r) {
        return g.nodo(x, y, r).color(NEGRO);
    }

    static void curva(Grafo g, double[] xs, double[] ys, double[] rs) {
        Grafo.Nodo prev = null;
        for (int i = 0; i < xs.length; i++) {
            var nd = n(g, xs[i], ys[i], rs[i]);
            if (prev != null) g.arista(prev, nd);
            prev = nd;
        }
    }

    // ---------- minúsculas ----------

    static Grafo letraA(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.3);
        // bucle + fuste
        var a = n(g, ox + 38, oy + 22, 5.5);
        var b = n(g, ox + 18, oy + 42, 6.0);
        var c = n(g, ox + 22, oy + 62, 5.5);
        var d = n(g, ox + 42, oy + 58, 5.0);
        g.arista(a, b); g.arista(b, c); g.arista(c, d); g.arista(d, a);
        var f = n(g, ox + 48, oy + 22, 5.0);
        var bot = n(g, ox + 48, oy + 78, 5.5);
        g.arista(f, bot);
        return g;
    }

    static Grafo letraB(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.2);
        var top = n(g, ox + 18, oy + 8, 5.0);
        var m = n(g, ox + 18, oy + 48, 5.5);
        var bot = n(g, ox + 18, oy + 78, 5.5);
        g.arista(top, m); g.arista(m, bot);
        var r1 = n(g, ox + 42, oy + 38, 6.0);
        var r2 = n(g, ox + 48, oy + 58, 5.5);
        g.arista(m, r1); g.arista(r1, r2); g.arista(r2, bot);
        return g;
    }

    static Grafo letraC(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.4);
        curva(g,
            new double[]{ox+52, ox+32, ox+16, ox+20, ox+42, ox+55},
            new double[]{oy+28, oy+18, oy+42, oy+62, oy+78, oy+68},
            new double[]{5.0, 6.0, 6.5, 6.5, 6.0, 5.0});
        return g;
    }

    static Grafo letraD(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.2);
        var top = n(g, ox + 48, oy + 8, 5.0);
        var m = n(g, ox + 48, oy + 48, 5.5);
        var bot = n(g, ox + 48, oy + 78, 5.5);
        g.arista(top, m); g.arista(m, bot);
        var l1 = n(g, ox + 22, oy + 38, 6.0);
        var l2 = n(g, ox + 18, oy + 58, 5.5);
        g.arista(m, l1); g.arista(l1, l2); g.arista(l2, bot);
        return g;
    }

    static Grafo letraE(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.3);
        curva(g,
            new double[]{ox+52, ox+28, ox+16, ox+28, ox+48, ox+52, ox+28},
            new double[]{oy+32, oy+18, oy+42, oy+58, oy+48, oy+68, oy+78},
            new double[]{5.0, 6.0, 6.5, 5.5, 5.5, 5.5, 5.5});
        return g;
    }

    static Grafo letraF(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.1);
        var top = n(g, ox + 42, oy + 8, 4.5);
        var m = n(g, ox + 28, oy + 28, 5.5);
        var bot = n(g, ox + 28, oy + 78, 5.5);
        g.arista(top, m); g.arista(m, bot);
        var bar = n(g, ox + 48, oy + 42, 4.5);
        g.arista(m, bar);
        return g;
    }

    static Grafo letraG(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.4);
        // bucle + descendente
        var a = n(g, ox + 42, oy + 28, 5.5);
        var b = n(g, ox + 22, oy + 42, 6.0);
        var c = n(g, ox + 28, oy + 62, 5.5);
        var d = n(g, ox + 48, oy + 58, 5.0);
        g.arista(a, b); g.arista(b, c); g.arista(c, d); g.arista(d, a);
        var desc = n(g, ox + 52, oy + 78, 5.0);
        var cola = n(g, ox + 38, oy + 95, 4.5);
        g.arista(d, desc); g.arista(desc, cola);
        return g;
    }

    static Grafo letraH(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.0);
        var t = n(g, ox + 18, oy + 8, 5.0);
        var m = n(g, ox + 18, oy + 48, 5.5);
        var b = n(g, ox + 18, oy + 78, 5.5);
        g.arista(t, m); g.arista(m, b);
        var r1 = n(g, ox + 42, oy + 42, 5.5);
        var r2 = n(g, ox + 48, oy + 78, 5.5);
        g.arista(m, r1); g.arista(r1, r2);
        return g;
    }

    static Grafo letraI(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(0.8);
        n(g, ox + 28, oy + 12, 4.5); // punto
        var t = n(g, ox + 28, oy + 28, 5.0);
        var b = n(g, ox + 28, oy + 78, 5.5);
        g.arista(t, b);
        return g;
    }

    static Grafo letraJ(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.2);
        n(g, ox + 38, oy + 12, 4.5);
        curva(g,
            new double[]{ox+38, ox+38, ox+32, ox+16, ox+10},
            new double[]{oy+28, oy+62, oy+82, oy+88, oy+72},
            new double[]{5.0, 5.5, 5.5, 5.5, 4.5});
        return g;
    }

    static Grafo letraK(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.1);
        var t = n(g, ox + 18, oy + 8, 5.0);
        var m = n(g, ox + 18, oy + 48, 5.5);
        var b = n(g, ox + 18, oy + 78, 5.5);
        g.arista(t, m); g.arista(m, b);
        var up = n(g, ox + 48, oy + 22, 5.0);
        var dn = n(g, ox + 48, oy + 78, 5.0);
        g.arista(m, up); g.arista(m, dn);
        return g;
    }

    static Grafo letraL(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(0.9);
        var t = n(g, ox + 28, oy + 8, 5.0);
        var b = n(g, ox + 28, oy + 78, 5.5);
        g.arista(t, b);
        return g;
    }

    static Grafo letraM(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.1);
        var l = n(g, ox + 12, oy + 78, 5.0);
        var lm = n(g, ox + 16, oy + 28, 5.5);
        var mid = n(g, ox + 32, oy + 48, 5.0);
        var rm = n(g, ox + 48, oy + 28, 5.5);
        var r = n(g, ox + 52, oy + 78, 5.0);
        g.arista(l, lm); g.arista(lm, mid); g.arista(mid, rm); g.arista(rm, r);
        return g;
    }

    static Grafo letraN(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.0);
        var t = n(g, ox + 18, oy + 28, 5.0);
        var m = n(g, ox + 18, oy + 48, 5.5);
        var b = n(g, ox + 18, oy + 78, 5.5);
        g.arista(t, m); g.arista(m, b);
        var r1 = n(g, ox + 42, oy + 38, 5.5);
        var r2 = n(g, ox + 48, oy + 78, 5.5);
        g.arista(m, r1); g.arista(r1, r2);
        return g;
    }

    static Grafo letraO(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.5);
        var a = n(g, ox + 32, oy + 22, 6.0);
        var b = n(g, ox + 50, oy + 42, 6.5);
        var c = n(g, ox + 32, oy + 72, 6.0);
        var d = n(g, ox + 14, oy + 42, 6.5);
        g.arista(a, b); g.arista(b, c); g.arista(c, d); g.arista(d, a);
        g.arista(a, b);
        return g;
    }

    static Grafo letraP(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.2);
        var t = n(g, ox + 18, oy + 28, 5.0);
        var m = n(g, ox + 18, oy + 48, 5.5);
        var b = n(g, ox + 18, oy + 95, 5.0);
        g.arista(t, m); g.arista(m, b);
        var r1 = n(g, ox + 42, oy + 32, 6.0);
        var r2 = n(g, ox + 46, oy + 52, 5.5);
        g.arista(t, r1); g.arista(r1, r2); g.arista(r2, m);
        return g;
    }

    static Grafo letraQ(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.2);
        var t = n(g, ox + 48, oy + 28, 5.0);
        var m = n(g, ox + 48, oy + 48, 5.5);
        var b = n(g, ox + 48, oy + 95, 5.0);
        g.arista(t, m); g.arista(m, b);
        var l1 = n(g, ox + 22, oy + 32, 6.0);
        var l2 = n(g, ox + 18, oy + 52, 5.5);
        g.arista(t, l1); g.arista(l1, l2); g.arista(l2, m);
        return g;
    }

    static Grafo letraR(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.1);
        var t = n(g, ox + 18, oy + 28, 5.0);
        var m = n(g, ox + 18, oy + 48, 5.5);
        var b = n(g, ox + 18, oy + 78, 5.5);
        g.arista(t, m); g.arista(m, b);
        var r = n(g, ox + 42, oy + 38, 5.5);
        g.arista(m, r);
        return g;
    }

    static Grafo letraS(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.4);
        curva(g,
            new double[]{ox+48, ox+28, ox+16, ox+32, ox+48, ox+32, ox+14},
            new double[]{oy+28, oy+18, oy+36, oy+48, oy+62, oy+78, oy+68},
            new double[]{5.0, 5.5, 5.5, 5.0, 5.5, 5.5, 5.0});
        return g;
    }

    static Grafo letraT(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.0);
        var top = n(g, ox + 18, oy + 18, 5.0);
        var m = n(g, ox + 32, oy + 28, 5.5);
        var bot = n(g, ox + 32, oy + 78, 5.5);
        g.arista(top, m); g.arista(m, bot);
        var bar = n(g, ox + 48, oy + 28, 4.5);
        g.arista(m, bar);
        return g;
    }

    static Grafo letraU(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.3);
        curva(g,
            new double[]{ox+14, ox+14, ox+22, ox+42, ox+50, ox+50},
            new double[]{oy+28, oy+58, oy+78, oy+78, oy+58, oy+28},
            new double[]{5.0, 5.5, 6.0, 6.0, 5.5, 5.0});
        return g;
    }

    static Grafo letraV(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.0);
        var i = n(g, ox + 12, oy + 28, 5.0);
        var m = n(g, ox + 32, oy + 78, 5.5);
        var d = n(g, ox + 52, oy + 28, 5.0);
        g.arista(i, m); g.arista(m, d);
        return g;
    }

    static Grafo letraW(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.1);
        var a = n(g, ox + 8, oy + 28, 4.5);
        var b = n(g, ox + 18, oy + 78, 5.5);
        var c = n(g, ox + 32, oy + 48, 5.0);
        var d = n(g, ox + 46, oy + 78, 5.5);
        var e = n(g, ox + 56, oy + 28, 4.5);
        g.arista(a, b); g.arista(b, c); g.arista(c, d); g.arista(d, e);
        return g;
    }

    static Grafo letraX(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.0);
        var a = n(g, ox + 14, oy + 28, 5.0);
        var b = n(g, ox + 48, oy + 78, 5.0);
        var c = n(g, ox + 48, oy + 28, 5.0);
        var d = n(g, ox + 14, oy + 78, 5.0);
        g.arista(a, b); g.arista(c, d);
        return g;
    }

    static Grafo letraY(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.1);
        var a = n(g, ox + 14, oy + 28, 5.0);
        var m = n(g, ox + 32, oy + 52, 5.5);
        var c = n(g, ox + 50, oy + 28, 5.0);
        var b = n(g, ox + 32, oy + 95, 5.0);
        g.arista(a, m); g.arista(c, m); g.arista(m, b);
        return g;
    }

    static Grafo letraZ(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.0);
        var tL = n(g, ox + 14, oy + 28, 4.5);
        var tR = n(g, ox + 50, oy + 28, 5.0);
        var bL = n(g, ox + 14, oy + 78, 5.0);
        var bR = n(g, ox + 50, oy + 78, 4.5);
        g.arista(tL, tR); g.arista(tR, bL); g.arista(bL, bR);
        return g;
    }

    static Grafo letra(char c, double ox, double oy) {
        return switch (c) {
            case 'a' -> letraA(ox, oy);
            case 'b' -> letraB(ox, oy);
            case 'c' -> letraC(ox, oy);
            case 'd' -> letraD(ox, oy);
            case 'e' -> letraE(ox, oy);
            case 'f' -> letraF(ox, oy);
            case 'g' -> letraG(ox, oy);
            case 'h' -> letraH(ox, oy);
            case 'i' -> letraI(ox, oy);
            case 'j' -> letraJ(ox, oy);
            case 'k' -> letraK(ox, oy);
            case 'l' -> letraL(ox, oy);
            case 'm' -> letraM(ox, oy);
            case 'n' -> letraN(ox, oy);
            case 'o' -> letraO(ox, oy);
            case 'p' -> letraP(ox, oy);
            case 'q' -> letraQ(ox, oy);
            case 'r' -> letraR(ox, oy);
            case 's' -> letraS(ox, oy);
            case 't' -> letraT(ox, oy);
            case 'u' -> letraU(ox, oy);
            case 'v' -> letraV(ox, oy);
            case 'w' -> letraW(ox, oy);
            case 'x' -> letraX(ox, oy);
            case 'y' -> letraY(ox, oy);
            case 'z' -> letraZ(ox, oy);
            default -> new Grafo();
        };
    }

    static BufferedImage dibuja() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        fondoBlanco(im);

        String fila1 = "abcdefghijklm";
        String fila2 = "nopqrstuvwxyz";

        String[] filas = { fila1, fila2 };
        Opciones opt = Opciones.POR_DEFECTO.conSolape(Solape.TRAZO);

        for (int row = 0; row < filas.length; row++) {
            String s = filas[row];
            for (int col = 0; col < s.length(); col++) {
                char c = s.charAt(col);
                double ox = OX0 + col * CW;
                double oy = OY0 + row * CH;
                Grafo g = letra(c, ox, oy);
                Plotter.raster(im).plot(g, opt);
            }
        }
        return im;
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(dibuja(), "png", new File("abecedario_minusculas_by_grok.png"));
        System.out.println("abecedario_minusculas_by_grok.png");
    }
}
