import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Estrella del bosque (Lithophragma): n pétalos, cada uno un lazo que sale de M, recorre el contorno de un pétalo con la
 * punta dentada en L lóbulos y vuelve a M. El radio del nodo marca la forma: ancho en los lóbulos, mínimo en las
 * muescas entre ellos. Escribe estrella.png con tres paneles de distinto número de pétalos, lóbulos y contraste de radios.
 */
public class Estrella {
    static final int W = 700, H = 700, CX = 350, CY = 350;
    static final Mezcla RGB = Mezcla.RGB;
    static int mezcla(int a, int b, double t) { return RGB.mezclar(a, b, Math.max(0, Math.min(1, t))); }

    static final int M = 0xFFEFE6A0;
    static int tono(int k, int n) { return Color.HSBtoRGB((float) (0.90 + 0.06 * Math.sin(2 * Math.PI * k / n)), 0.55f, 0.92f); }

    static int petalo(int k, int n, double t) {
        return mezcla(M, tono(k, n), Math.pow(Math.sin(Math.PI * t), 0.7));
    }

    static Degradado radio(int c) { return x -> mezcla(c, 0xFF4A1440, 0.40 * x * x); }

    /** n pétalos de L lóbulos; largo = distancia de la punta a M; ancho = semiancho de la punta; rt, rn, rb = radios de lóbulo, muesca y base. */
    static Grafo estrella(int n, int L, double largo, double ancho, double rt, double rn, double rb) {
        Grafo c = new Grafo().mezcla(RGB);
        var m = c.nodo(CX, CY, 12).centro(M).radio(radio(M));
        // contorno del pétalo en coordenadas locales (u hacia fuera, v lateral): base izq, lóbulos con muescas, base der
        java.util.List<double[]> pts = new java.util.ArrayList<>();   // {u, v, radio}
        pts.add(new double[] { 0.30 * largo, -0.30 * ancho, rb });
        for (int i = 0; i < L; i++) {
            double v = L == 1 ? 0 : -ancho + 2 * ancho * i / (L - 1);
            double borde = 1 - 0.10 * Math.abs(v) / ancho;            // los lóbulos laterales algo más cortos
            pts.add(new double[] { largo * borde, v, rt });
            if (i < L - 1) pts.add(new double[] { 0.82 * largo, v + ancho / (L - 1), rn });
        }
        pts.add(new double[] { 0.30 * largo, 0.30 * ancho, rb });
        int N = pts.size();
        for (int k = 0; k < n; k++) {
            double th = -Math.PI / 2 + 2 * Math.PI * k / n, ct = Math.cos(th), st = Math.sin(th);
            Grafo.Nodo prev = m;
            for (int j = 1; j <= N; j++) {
                double[] p = pts.get(j - 1);
                double x = CX + p[0] * ct - p[1] * st, y = CY + p[0] * st + p[1] * ct;
                int col = petalo(k, n, j / (N + 1.0));
                var nd = c.nodo(x, y, p[2]).centro(col).radio(radio(col));
                final int kk = k, jj = j;
                c.arista(prev, nd).espina(t -> petalo(kk, n, (jj - 1 + t) / (N + 1.0)));
                prev = nd;
            }
            final int kk = k;
            c.arista(prev, m).espina(t -> petalo(kk, n, (N + t) / (N + 1.0)));
        }
        return c;
    }

    static BufferedImage pinta(Grafo comp) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFFF4F1EA);
        comp.render(im, Opciones.POR_DEFECTO.conSolape(Solape.TRAZO));
        return im;
    }

    public static void main(String[] args) throws Exception {
        BufferedImage hoja = new BufferedImage(W * 3, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hoja.createGraphics();
        g.drawImage(pinta(estrella(5, 3, 235, 62, 19, 7, 11)), 0, 0, null);
        g.drawImage(pinta(estrella(7, 5, 235, 52, 11, 4, 8)), W, 0, null);
        g.drawImage(pinta(estrella(5, 3, 240, 70, 26, 4, 8)), 2 * W, 0, null);
        g.dispose();
        ImageIO.write(hoja, "png", new File("estrella.png"));
        System.out.println("estrella.png (5x3 | 7x5 | 5x3 contraste alto)");
    }
}
