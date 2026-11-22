import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Topografías con ciclos, con color por funciones (Nodo.radio, Arista.espina), cada una en PLANO (arriba) y TRAZO (abajo):
 * A. el 8 como Y lobulada sin palo: nodo 0 de grado 4 con dos pétalos (0 a b Z0 y 0 c d Z0);
 * B. el 8 como un círculo que se cruza a sí mismo: ciclo de nodos de grado 2 y un cruce sin nodo;
 * C. la Y que se cierra en otro nodo: un tallo, una lente de M a J por dos ramas y una cola. Escribe ochos.png.
 */
public class Ochos {
    static final int W = 520, H = 520;
    static final Mezcla RGB = Mezcla.RGB;
    static int mezcla(int a, int b, double t) { return RGB.mezclar(a, b, Math.max(0, Math.min(1, t))); }
    static Degradado radio(int c) { return x -> mezcla(c, 0xFF14202B, 0.45 * x * x); }

    static Grafo.Nodo nd(Grafo c, double x, double y, double r, int col) {
        return c.nodo(x, y, r).centro(col).radio(radio(col));
    }

    /** Paleta coseno periódica (Quilez): t y t + 1 dan el mismo color. */
    static int ciclo(double t) {
        double[] d = { 0.00, 0.33, 0.67 };
        int[] o = new int[3];
        for (int i = 0; i < 3; i++) o[i] = (int) Math.round(255 * Math.max(0, Math.min(1, 0.55 + 0.45 * Math.cos(2 * Math.PI * (t + d[i])))));
        return 0xFF000000 | o[0] << 16 | o[1] << 8 | o[2];
    }

    // ---------- A: nodo 0 con dos pétalos ----------
    static final int M = 0xFFF2E6B0;

    static Grafo ochoDePetalos() {
        Grafo c = new Grafo().mezcla(RGB);
        var n0 = nd(c, 260, 260, 20, M);
        int[] tono = { 0xFFE94F7B, 0xFF4F7BE9 };
        double[] ang = { -90, 90 };
        for (int k = 0; k < 2; k++) {
            double th = Math.toRadians(ang[k]);
            double cx = 260 + 112 * Math.cos(th), cy = 260 + 112 * Math.sin(th);
            final int kk = k;
            java.util.function.DoubleFunction<Integer> col = t -> mezcla(M, tono[kk], Math.pow(Math.sin(Math.PI * t), 0.8));
            Grafo.Nodo prev = n0;
            for (int j = 1; j <= 3; j++) {
                double fi = th + Math.PI + Math.toRadians(60 + 120 * (j - 1));
                final int jj = j;
                var nj = nd(c, cx + 80 * Math.cos(fi), cy + 80 * Math.sin(fi), 22, col.apply(j / 4.0));
                c.arista(prev, nj).espina(x -> col.apply((jj - 1 + x) / 4.0));
                prev = nj;
            }
            c.arista(prev, n0).espina(x -> col.apply((3 + x) / 4.0));
        }
        return c;
    }

    // ---------- B: el círculo que se cruza (lemniscata) ----------
    static Grafo lemniscata() {
        Grafo c = new Grafo().mezcla(RGB);
        int n = 10;
        Grafo.Nodo primero = null;
        Grafo.Nodo prev = null;
        for (int j = 0; j < n; j++) {
            double t = (j + 0.5) * 2 * Math.PI / n, d = 1 + Math.sin(t) * Math.sin(t);
            double x = 260 + 215 * Math.cos(t) / d, y = 260 + 215 * Math.sin(t) * Math.cos(t) / d * 1.35;
            var nj = nd(c, x, y, 21, ciclo((double) j / n));
            if (prev != null) { final int jj = j; c.arista(prev, nj).espina(u -> ciclo((jj - 1 + u) / n)); }
            else primero = nj;
            prev = nj;
        }
        c.arista(prev, primero).espina(u -> ciclo((n - 1 + u) / n));          // cierre Z0
        return c;
    }

    // ---------- C: la Y que se cierra en otro nodo ----------
    static Grafo lente() {
        Grafo c = new Grafo().mezcla(RGB);
        int verde = 0xFF2E7D32, mc = 0xFFCFE8A0, jc = 0xFFF2E6B0, cola = 0xFFD98E2B, rosa = 0xFFE94F7B, azul = 0xFF4F7BE9;
        var n0 = nd(c, 260, 500, 9, verde);
        var m = nd(c, 260, 390, 20, mc);
        var a = nd(c, 130, 285, 22, mezcla(mc, rosa, 0.8));
        var b = nd(c, 390, 285, 22, mezcla(mc, azul, 0.8));
        var j = nd(c, 260, 170, 20, jc);
        var fin = nd(c, 260, 40, 8, cola);
        c.arista(n0, m).espina(x -> mezcla(verde, mc, x));
        c.arista(m, a);
        c.arista(a, j);
        c.arista(m, b);
        c.arista(b, j);
        c.arista(j, fin).espina(x -> mezcla(jc, cola, x));
        return c;
    }

    static BufferedImage pinta(Grafo c, Solape s) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFFF4F1EA);
        c.render(im, Opciones.POR_DEFECTO.conSolape(s));
        return im;
    }

    public static void main(String[] args) throws Exception {
        BufferedImage hoja = new BufferedImage(W * 3, H * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hoja.createGraphics();
        Grafo[] cs = { ochoDePetalos(), lemniscata(), lente() };
        for (int i = 0; i < 3; i++) {
            g.drawImage(pinta(cs[i], Solape.PLANO), i * W, 0, null);
            g.drawImage(pinta(cs[i], Solape.TRAZO), i * W, H, null);
        }
        g.dispose();
        ImageIO.write(hoja, "png", new File("ochos.png"));
        System.out.println("ochos.png");
    }
}
