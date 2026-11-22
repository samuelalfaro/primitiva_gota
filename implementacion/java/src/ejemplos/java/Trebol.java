import composicion.Grafo;
import coordenadas.Solape;
import color.Mezcla;
import color.Degradado;
import coordenadas.Opciones;
import forma.Gota;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Y lobulada: un tallo (nodo 0 a M) y tres pétalos que salen de M y vuelven a M (M a Z_M), todo en una sola
 * Composicion. El nodo M tiene grado 7, así que el color va por funciones (Nodo.radio y Arista.espina), no por
 * sectores. Cada pétalo recorre su lazo con un color que sale del de M, hace un viaje por su tono y vuelve. Escribe trebol.png.
 */
public class Trebol {
    static final int W = 700, H = 760;
    static final Mezcla RGB = Mezcla.RGB;
    static int mezcla(int a, int b, double t) { return RGB.mezclar(a, b, Math.max(0, Math.min(1, t))); }

    static final int M = 0xFFCFE8A0;           // color del centro del nodo M (verde claro)

    /** Color del pétalo k en la posición t del lazo (0 y 1 = M): sale de M, viaja por su tono y vuelve. */
    static int petalo(int k, double t) {
        int[] tono = { 0xFFE94F7B, 0xFFF2A33A, 0xFF4F7BE9 };
        double w = Math.pow(Math.sin(Math.PI * t), 0.8);
        return mezcla(M, tono[k], w);
    }

    static Degradado radio(int c) { return x -> mezcla(c, 0xFF12301A, 0.45 * x * x); }

    static Grafo trebol() {
        Grafo c = new Grafo().mezcla(RGB);
        int base = 0xFF2E7D32;
        var n0 = c.nodo(350, 700, 9).centro(base).radio(radio(base));
        var m = c.nodo(350, 430, 20).centro(M).radio(radio(M));
        c.arista(n0, m).tiradores(null, new Gota.Vec(0, -60)).espina(x -> mezcla(base, M, x));
        double[] angulo = { -150, -90, -30 };
        for (int k = 0; k < 3; k++) {
            double th = Math.toRadians(angulo[k]);
            double cx = 350 + 118 * Math.cos(th), cy = 430 + 118 * Math.sin(th);
            Grafo.Nodo prev = m;
            for (int j = 1; j <= 3; j++) {
                double fi = th + Math.PI + Math.toRadians(-60 + 0) + (j - 1) * Math.toRadians(120) + Math.toRadians(0);
                fi = th + Math.PI + Math.toRadians(60 + 120 * (j - 1));
                double px = cx + 78 * Math.cos(fi), py = cy + 78 * Math.sin(fi);
                int col = petalo(k, j / 4.0);
                var nd = c.nodo(px, py, 22 - 3 * (j == 2 ? 0 : 1)).centro(col).radio(radio(col));
                final int kk = k, jj = j;
                c.arista(prev, nd).espina(x -> petalo(kk, (jj - 1 + x) / 4.0));
                prev = nd;
            }
            final int kk = k;
            c.arista(prev, m).espina(x -> petalo(kk, (3 + x) / 4.0));
        }
        return c;
    }

    static BufferedImage pinta(Solape solape) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFFF4F1EA);
        trebol().render(im, Opciones.POR_DEFECTO.conSolape(solape));
        return im;
    }

    public static void main(String[] args) throws Exception {
        BufferedImage hoja = new BufferedImage(W * 2, H, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = hoja.createGraphics();
        g.drawImage(pinta(Solape.PLANO), 0, 0, null);
        g.drawImage(pinta(Solape.TRAZO), W, 0, null);
        g.dispose();
        ImageIO.write(hoja, "png", new File("trebol.png"));
        System.out.println("trebol.png (PLANO | TRAZO)");
    }
}
