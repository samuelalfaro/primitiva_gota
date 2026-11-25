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
 * Abecedario mayúsculas + dígitos construido solo con la primitiva Gota
 * (cadenas de nodos de radio variable). Negro puro sobre blanco.
 * Espacio canónico ~80×100 por carácter. Máximo ~10-12 nodos por letra.
 * Primera versión (esquema visual → código). Escribe abecedario_mayusculas_by_grok.png.
 */
public class AbecedarioMayusculas_by_Grok {

    static final int W = 1120, H = 780;
    static final int NEGRO = 0xFF000000;
    static final Mezcla RGB = Mezcla.RGB;

    static final double CW = 80;   // cell width
    static final double CH = 110;  // cell height
    static final double OX0 = 40, OY0 = 40;

    static void fondoBlanco(BufferedImage im) {
        int[] pix = ((DataBufferInt) im.getRaster().getDataBuffer()).getData();
        java.util.Arrays.fill(pix, 0xFFFFFFFF);
    }

    /** Nodo con color negro y radio fijo (el grosor se controla con el valor de r). */
    static Grafo.Nodo n(Grafo g, double x, double y, double r) {
        return g.nodo(x, y, r).color(NEGRO);
    }

    // ---------- construcciones básicas ----------

    /** Tallo vertical. */
    static void tallo(Grafo g, double x, double y0, double y1, double r) {
        var a = n(g, x, y0, r * 0.85);
        var b = n(g, x, y1, r * 0.95);
        g.arista(a, b);
    }

    /** Curva suave de varios puntos (radios variables). */
    static void curva(Grafo g, double[] xs, double[] ys, double[] rs) {
        Grafo.Nodo prev = null;
        for (int i = 0; i < xs.length; i++) {
            var nd = n(g, xs[i], ys[i], rs[i]);
            if (prev != null) g.arista(prev, nd);
            prev = nd;
        }
    }

    // ---------- letras ----------

    static Grafo letraA(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.2);
        // dos diagonales + travesaño
        var top = n(g, ox + 40, oy + 12, 5.5);
        var izq = n(g, ox + 12, oy + 95, 6.5);
        var der = n(g, ox + 68, oy + 95, 6.5);
        g.arista(top, izq);
        g.arista(top, der);
        var m1 = n(g, ox + 28, oy + 58, 5.0);
        var m2 = n(g, ox + 52, oy + 58, 5.0);
        g.arista(m1, m2);
        return g;
    }

    static Grafo letraB(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.4);
        var t = n(g, ox + 18, oy + 12, 5.5);
        var m = n(g, ox + 18, oy + 52, 6.0);
        var b = n(g, ox + 18, oy + 95, 5.5);
        g.arista(t, m);
        g.arista(m, b);
        // bucles
        var r1 = n(g, ox + 48, oy + 22, 7.0);
        var r2 = n(g, ox + 55, oy + 38, 6.5);
        g.arista(t, r1); g.arista(r1, r2); g.arista(r2, m);
        var r3 = n(g, ox + 52, oy + 68, 7.5);
        var r4 = n(g, ox + 58, oy + 85, 6.5);
        g.arista(m, r3); g.arista(r3, r4); g.arista(r4, b);
        return g;
    }

    static Grafo letraC(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.5);
        curva(g,
            new double[]{ox+62, ox+38, ox+18, ox+22, ox+48, ox+68},
            new double[]{oy+22, oy+12, oy+42, oy+72, oy+95, oy+82},
            new double[]{5.5, 7.0, 7.5, 7.5, 7.0, 5.5});
        return g;
    }

    static Grafo letraD(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.3);
        var t = n(g, ox + 18, oy + 12, 5.5);
        var b = n(g, ox + 18, oy + 95, 5.5);
        g.arista(t, b);
        var r1 = n(g, ox + 48, oy + 18, 7.0);
        var r2 = n(g, ox + 62, oy + 48, 8.0);
        var r3 = n(g, ox + 48, oy + 88, 7.0);
        g.arista(t, r1); g.arista(r1, r2); g.arista(r2, r3); g.arista(r3, b);
        return g;
    }

    static Grafo letraE(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.0);
        var t = n(g, ox + 18, oy + 14, 5.5);
        var m = n(g, ox + 18, oy + 52, 5.5);
        var b = n(g, ox + 18, oy + 94, 5.5);
        g.arista(t, m); g.arista(m, b);
        g.arista(t, n(g, ox + 62, oy + 14, 5.0));
        g.arista(m, n(g, ox + 52, oy + 52, 5.0));
        g.arista(b, n(g, ox + 62, oy + 94, 5.0));
        return g;
    }

    static Grafo letraF(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.0);
        var t = n(g, ox + 18, oy + 14, 5.5);
        var m = n(g, ox + 18, oy + 52, 5.5);
        var b = n(g, ox + 18, oy + 94, 5.0);
        g.arista(t, m); g.arista(m, b);
        g.arista(t, n(g, ox + 62, oy + 14, 5.0));
        g.arista(m, n(g, ox + 52, oy + 52, 5.0));
        return g;
    }

    static Grafo letraG(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.5);
        curva(g,
            new double[]{ox+62, ox+38, ox+16, ox+20, ox+48, ox+68, ox+68, ox+48},
            new double[]{oy+24, oy+12, oy+42, oy+72, oy+96, oy+78, oy+58, oy+58},
            new double[]{5.5, 7.0, 7.5, 7.5, 7.0, 6.0, 5.5, 5.5});
        return g;
    }

    static Grafo letraH(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.0);
        tallo(g, ox + 18, oy + 12, oy + 95, 6.0);
        tallo(g, ox + 62, oy + 12, oy + 95, 6.0);
        var m1 = n(g, ox + 18, oy + 52, 5.5);
        var m2 = n(g, ox + 62, oy + 52, 5.5);
        g.arista(m1, m2);
        return g;
    }

    static Grafo letraI(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(0.8);
        tallo(g, ox + 40, oy + 12, oy + 95, 6.5);
        return g;
    }

    static Grafo letraJ(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.3);
        curva(g,
            new double[]{ox+55, ox+55, ox+48, ox+28, ox+14},
            new double[]{oy+12, oy+62, oy+92, oy+98, oy+78},
            new double[]{5.5, 6.0, 6.5, 6.5, 5.5});
        return g;
    }

    static Grafo letraK(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.1);
        tallo(g, ox + 18, oy + 12, oy + 95, 6.0);
        var m = n(g, ox + 22, oy + 52, 5.5);
        var t = n(g, ox + 65, oy + 12, 5.5);
        var b = n(g, ox + 65, oy + 95, 5.5);
        g.arista(m, t);
        g.arista(m, b);
        return g;
    }

    static Grafo letraL(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.0);
        var t = n(g, ox + 22, oy + 12, 5.5);
        var b = n(g, ox + 22, oy + 95, 6.0);
        var e = n(g, ox + 62, oy + 95, 5.5);
        g.arista(t, b); g.arista(b, e);
        return g;
    }

    static Grafo letraM(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.2);
        var i1 = n(g, ox + 10, oy + 95, 5.5);
        var i2 = n(g, ox + 18, oy + 12, 6.0);
        var mid = n(g, ox + 40, oy + 55, 5.5);
        var d2 = n(g, ox + 62, oy + 12, 6.0);
        var d1 = n(g, ox + 70, oy + 95, 5.5);
        g.arista(i1, i2); g.arista(i2, mid); g.arista(mid, d2); g.arista(d2, d1);
        return g;
    }

    static Grafo letraN(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.1);
        var i1 = n(g, ox + 16, oy + 95, 5.5);
        var i2 = n(g, ox + 16, oy + 12, 6.0);
        var d2 = n(g, ox + 64, oy + 95, 6.0);
        var d1 = n(g, ox + 64, oy + 12, 5.5);
        g.arista(i1, i2); g.arista(i2, d2); g.arista(d2, d1);
        return g;
    }

    static Grafo letraO(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.6);
        var a = n(g, ox + 40, oy + 14, 7.0);
        var b = n(g, ox + 62, oy + 40, 7.5);
        var c = n(g, ox + 40, oy + 92, 7.0);
        var d = n(g, ox + 18, oy + 40, 7.5);
        g.arista(a, b); g.arista(b, c); g.arista(c, d); g.arista(d, a);
        // refuerzo de ciclo
        g.arista(a, b);
        return g;
    }

    static Grafo letraP(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.3);
        var t = n(g, ox + 18, oy + 12, 5.5);
        var m = n(g, ox + 18, oy + 55, 6.0);
        var b = n(g, ox + 18, oy + 95, 5.0);
        g.arista(t, m); g.arista(m, b);
        var r1 = n(g, ox + 48, oy + 18, 7.0);
        var r2 = n(g, ox + 58, oy + 38, 6.5);
        g.arista(t, r1); g.arista(r1, r2); g.arista(r2, m);
        return g;
    }

    static Grafo letraQ(double ox, double oy) {
        Grafo g = letraO(ox, oy);
        // cola
        var c = n(g, ox + 48, oy + 78, 5.5);
        var q = n(g, ox + 68, oy + 98, 5.0);
        g.arista(c, q);
        return g;
    }

    static Grafo letraR(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.3);
        var t = n(g, ox + 18, oy + 12, 5.5);
        var m = n(g, ox + 18, oy + 52, 6.0);
        var b = n(g, ox + 18, oy + 95, 5.5);
        g.arista(t, m); g.arista(m, b);
        var r1 = n(g, ox + 48, oy + 18, 7.0);
        var r2 = n(g, ox + 56, oy + 38, 6.5);
        g.arista(t, r1); g.arista(r1, r2); g.arista(r2, m);
        var leg = n(g, ox + 62, oy + 95, 5.5);
        g.arista(m, leg);
        return g;
    }

    static Grafo letraS(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.5);
        curva(g,
            new double[]{ox+62, ox+38, ox+22, ox+40, ox+58, ox+42, ox+18},
            new double[]{oy+22, oy+12, oy+32, oy+52, oy+72, oy+95, oy+85},
            new double[]{5.5, 6.5, 6.5, 6.0, 6.5, 6.5, 5.5});
        return g;
    }

    static Grafo letraT(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.0);
        var topL = n(g, ox + 12, oy + 14, 5.0);
        var topR = n(g, ox + 68, oy + 14, 5.0);
        var mid = n(g, ox + 40, oy + 14, 6.0);
        var bot = n(g, ox + 40, oy + 95, 6.0);
        g.arista(topL, mid); g.arista(mid, topR); g.arista(mid, bot);
        return g;
    }

    static Grafo letraU(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.4);
        curva(g,
            new double[]{ox+16, ox+16, ox+28, ox+52, ox+64, ox+64},
            new double[]{oy+12, oy+62, oy+95, oy+95, oy+62, oy+12},
            new double[]{5.5, 6.5, 7.0, 7.0, 6.5, 5.5});
        return g;
    }

    static Grafo letraV(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.1);
        var i = n(g, ox + 14, oy + 12, 5.5);
        var m = n(g, ox + 40, oy + 95, 6.5);
        var d = n(g, ox + 66, oy + 12, 5.5);
        g.arista(i, m); g.arista(m, d);
        return g;
    }

    static Grafo letraW(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.2);
        var a = n(g, ox + 8,  oy + 12, 5.0);
        var b = n(g, ox + 22, oy + 95, 6.0);
        var c = n(g, ox + 40, oy + 48, 5.5);
        var d = n(g, ox + 58, oy + 95, 6.0);
        var e = n(g, ox + 72, oy + 12, 5.0);
        g.arista(a, b); g.arista(b, c); g.arista(c, d); g.arista(d, e);
        return g;
    }

    static Grafo letraX(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.1);
        var a = n(g, ox + 16, oy + 12, 5.5);
        var b = n(g, ox + 64, oy + 95, 5.5);
        var c = n(g, ox + 64, oy + 12, 5.5);
        var d = n(g, ox + 16, oy + 95, 5.5);
        g.arista(a, b); g.arista(c, d);
        return g;
    }

    static Grafo letraY(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.1);
        var a = n(g, ox + 16, oy + 12, 5.5);
        var m = n(g, ox + 40, oy + 52, 6.0);
        var c = n(g, ox + 64, oy + 12, 5.5);
        var b = n(g, ox + 40, oy + 95, 6.0);
        g.arista(a, m); g.arista(c, m); g.arista(m, b);
        return g;
    }

    static Grafo letraZ(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.0);
        var tL = n(g, ox + 14, oy + 14, 5.0);
        var tR = n(g, ox + 66, oy + 14, 5.5);
        var bL = n(g, ox + 14, oy + 94, 5.5);
        var bR = n(g, ox + 66, oy + 94, 5.0);
        g.arista(tL, tR); g.arista(tR, bL); g.arista(bL, bR);
        return g;
    }

    // ---------- dígitos ----------

    static Grafo dig0(double ox, double oy) {
        return letraO(ox, oy);
    }

    static Grafo dig1(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(0.9);
        tallo(g, ox + 42, oy + 12, oy + 95, 6.5);
        var top = n(g, ox + 28, oy + 28, 5.0);
        g.arista(top, n(g, ox + 42, oy + 12, 5.5));
        return g;
    }

    static Grafo dig2(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.4);
        curva(g,
            new double[]{ox+18, ox+40, ox+62, ox+58, ox+28, ox+16, ox+66},
            new double[]{oy+28, oy+12, oy+28, oy+52, oy+78, oy+95, oy+95},
            new double[]{5.5, 6.5, 6.5, 6.0, 6.0, 5.5, 5.5});
        return g;
    }

    static Grafo dig3(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.4);
        curva(g,
            new double[]{ox+18, ox+42, ox+60, ox+42, ox+60, ox+42, ox+18},
            new double[]{oy+22, oy+12, oy+32, oy+52, oy+78, oy+95, oy+85},
            new double[]{5.5, 6.5, 6.5, 5.5, 6.5, 6.5, 5.5});
        return g;
    }

    static Grafo dig4(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.1);
        var a = n(g, ox + 22, oy + 12, 5.5);
        var b = n(g, ox + 18, oy + 62, 6.0);
        var c = n(g, ox + 62, oy + 62, 5.5);
        var d = n(g, ox + 52, oy + 12, 5.5);
        var e = n(g, ox + 52, oy + 95, 6.0);
        g.arista(a, b); g.arista(b, c); g.arista(d, e);
        return g;
    }

    static Grafo dig5(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.3);
        var tL = n(g, ox + 58, oy + 14, 5.0);
        var tR = n(g, ox + 18, oy + 14, 5.5);
        var m  = n(g, ox + 22, oy + 48, 5.5);
        g.arista(tL, tR); g.arista(tR, m);
        curva(g,
            new double[]{ox+22, ox+48, ox+62, ox+48, ox+18},
            new double[]{oy+48, oy+42, oy+62, oy+92, oy+82},
            new double[]{5.5, 6.5, 6.5, 6.5, 5.5});
        return g;
    }

    static Grafo dig6(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.5);
        curva(g,
            new double[]{ox+58, ox+32, ox+16, ox+22, ox+48, ox+62, ox+48, ox+22},
            new double[]{oy+22, oy+12, oy+42, oy+78, oy+96, oy+72, oy+52, oy+52},
            new double[]{5.5, 6.5, 7.0, 7.0, 7.0, 6.5, 6.0, 6.0});
        return g;
    }

    static Grafo dig7(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.0);
        var tL = n(g, ox + 14, oy + 14, 5.0);
        var tR = n(g, ox + 66, oy + 14, 5.5);
        var b  = n(g, ox + 32, oy + 95, 6.0);
        g.arista(tL, tR); g.arista(tR, b);
        return g;
    }

    static Grafo dig8(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.5);
        // bucle superior
        var a = n(g, ox + 40, oy + 14, 6.0);
        var b = n(g, ox + 58, oy + 32, 6.5);
        var c = n(g, ox + 40, oy + 52, 5.5);
        var d = n(g, ox + 22, oy + 32, 6.5);
        g.arista(a, b); g.arista(b, c); g.arista(c, d); g.arista(d, a);
        // bucle inferior
        var e = n(g, ox + 58, oy + 72, 7.0);
        var f = n(g, ox + 40, oy + 95, 6.5);
        var h = n(g, ox + 22, oy + 72, 7.0);
        g.arista(c, e); g.arista(e, f); g.arista(f, h); g.arista(h, c);
        return g;
    }

    static Grafo dig9(double ox, double oy) {
        Grafo g = new Grafo().mezcla(RGB).suavizado(1.5);
        curva(g,
            new double[]{ox+22, ox+48, ox+64, ox+58, ox+32, ox+18, ox+32, ox+58},
            new double[]{oy+78, oy+95, oy+62, oy+28, oy+12, oy+32, oy+52, oy+52},
            new double[]{5.5, 6.5, 7.0, 7.0, 7.0, 6.5, 6.0, 6.0});
        return g;
    }

    // ---------- lámina ----------

    static Grafo letra(char c, double ox, double oy) {
        return switch (c) {
            case 'A' -> letraA(ox, oy);
            case 'B' -> letraB(ox, oy);
            case 'C' -> letraC(ox, oy);
            case 'D' -> letraD(ox, oy);
            case 'E' -> letraE(ox, oy);
            case 'F' -> letraF(ox, oy);
            case 'G' -> letraG(ox, oy);
            case 'H' -> letraH(ox, oy);
            case 'I' -> letraI(ox, oy);
            case 'J' -> letraJ(ox, oy);
            case 'K' -> letraK(ox, oy);
            case 'L' -> letraL(ox, oy);
            case 'M' -> letraM(ox, oy);
            case 'N' -> letraN(ox, oy);
            case 'O' -> letraO(ox, oy);
            case 'P' -> letraP(ox, oy);
            case 'Q' -> letraQ(ox, oy);
            case 'R' -> letraR(ox, oy);
            case 'S' -> letraS(ox, oy);
            case 'T' -> letraT(ox, oy);
            case 'U' -> letraU(ox, oy);
            case 'V' -> letraV(ox, oy);
            case 'W' -> letraW(ox, oy);
            case 'X' -> letraX(ox, oy);
            case 'Y' -> letraY(ox, oy);
            case 'Z' -> letraZ(ox, oy);
            case '0' -> dig0(ox, oy);
            case '1' -> dig1(ox, oy);
            case '2' -> dig2(ox, oy);
            case '3' -> dig3(ox, oy);
            case '4' -> dig4(ox, oy);
            case '5' -> dig5(ox, oy);
            case '6' -> dig6(ox, oy);
            case '7' -> dig7(ox, oy);
            case '8' -> dig8(ox, oy);
            case '9' -> dig9(ox, oy);
            default -> new Grafo();
        };
    }

    static BufferedImage dibuja() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        fondoBlanco(im);

        String fila1 = "ABCDEFGHI";
        String fila2 = "JKLMNOPQR";
        String fila3 = "STUVWXYZ ";
        String fila4 = "0123456789";

        String[] filas = { fila1, fila2, fila3, fila4 };
        Opciones opt = Opciones.POR_DEFECTO.conSolape(Solape.TRAZO);

        for (int row = 0; row < filas.length; row++) {
            String s = filas[row];
            for (int col = 0; col < s.length(); col++) {
                char c = s.charAt(col);
                if (c == ' ') continue;
                double ox = OX0 + col * CW;
                double oy = OY0 + row * CH;
                Grafo g = letra(c, ox, oy);
                Plotter.raster(im).plot(g, opt);
            }
        }
        return im;
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(dibuja(), "png", new File("abecedario_mayusculas_by_grok.png"));
        System.out.println("abecedario_mayusculas_by_grok.png");
    }
}
