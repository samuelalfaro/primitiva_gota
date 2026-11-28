import color.Colores;
import color.Degradado;
import color.Mezcla;
import coordenadas.Opciones;
import forma.Gota;
import forma.Perfil;
import geometria.Geometria;
import geometria.Grupo;
import geometria.composicion.Grafo;
import geometria.composicion.Pintada;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import pintado.Plotter;

/**
 * Estudio #86: una nube de colores semitransparentes con los tres tipos de geometr&iacute;a (Gotas, un Grafo y un Grupo) a
 * distintas alturas y solapados en el centro, para ver c&oacute;mo conviven y se mezclan en la Rejilla. Con color opaco y
 * cobertura total solo se ve la capa de arriba y los errores de orden se esconden; con alfa 0x99 todas las capas pesan y el
 * orden por altura entre los tres tipos queda a la vista.
 * <p>
 * Variantes: A) el Plotter actual con un Grupo ra&iacute;z (el contrato de altura de hoy); B) la Rejilla N=1
 * (&iquest;equivalen?); C) la Rejilla N=3 con cobertura ESTRECHADA (antialiasing y coste); D) las mismas alturas
 * permutadas (&iquest;cambia la mezcla y sigue equivaliendo?); E) todas las alturas iguales con dos &oacute;rdenes de
 * aporte (el empate y su regla estable). Adem&aacute;s, mapa de profundidad de la nube (n&uacute;mero de capas con
 * cobertura &gt; 0 por p&iacute;xel).
 * <p>
 * Uso: java Nube_by_Mistral [ruta de la carpeta donde dejar los PNG]
 */
public class Nube_by_Mistral {
    static final int W = 800, H = 800, FONDO = 0xFFF4F1EA;
    static final int ALFA = 0x99000000;
    static final int ROJO = ALFA | 0xE03030, VERDE = ALFA | 0x30B050, AZUL = ALFA | 0x3050E0, MORADO = ALFA | 0x9040C0;

    static BufferedImage lienzo() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), FONDO);
        return im;
    }

    static Pintada disco(double x, double y, double r, int c, double altura) {
        return Pintada.de(Gota.lineal(new Point2D.Double(x, y), r, new Point2D.Double(x + 0.5, y), r, Perfil.recto()),
            Colores.cuatroColores(c, c, c, c, Mezcla.RGB)).conAltura(altura);
    }

    /** Onda de cinco nodos que atraviesa la zona com&uacute;n de las tres gotas. */
    static Grafo serpiente(double altura) {
        double[][] ns = { { 160, 270 }, { 290, 420 }, { 410, 400 }, { 540, 300 }, { 670, 430 } };
        Grafo s = new Grafo().mezcla(Mezcla.RGB);
        Grafo.Nodo[] nodos = new Grafo.Nodo[ns.length];
        for (int i = 0; i < ns.length; i++) nodos[i] = s.nodo(ns[i][0], ns[i][1], 24).centro(MORADO);
        for (int i = 0; i + 1 < ns.length; i++) s.arista(nodos[i], nodos[i + 1]).espina(Degradado.constante(MORADO));
        return s.altura(altura);
    }

    /** La nube: tres gotas grandes, la serpiente y un grupo de dos gotas peque&ntilde;as; las alturas vienen en el array. */
    static List<Geometria> nube(double[] a) {
        List<Geometria> l = new ArrayList<>();
        l.add(disco(300, 330, 150, ROJO, a[0]));
        l.add(disco(500, 330, 150, VERDE, a[1]));
        l.add(disco(400, 500, 150, AZUL, a[2]));
        l.add(serpiente(a[3]));
        l.add(new Grupo(disco(390, 410, 60, ROJO, 0), disco(455, 440, 55, VERDE, 2)).altura(a[4]).label("grupo"));
        return l;
    }

    static Grupo raiz(List<Geometria> escena) {
        Grupo g = new Grupo();
        for (Geometria x : escena) g.anade(x);
        return g;
    }

    static BufferedImage conPlotter(List<Geometria> escena) {
        BufferedImage im = lienzo();
        Plotter.raster(im).plot(raiz(escena));
        return im;
    }

    record Salida(BufferedImage im, Rejilla.Estadisticas e) {}

    static Salida conRejilla(int n, Rejilla.Cobertura cb, List<Geometria> escena) {
        Rejilla r = new Rejilla(n, 32, cb, Rejilla.Filtro.SRGB);
        for (Geometria g : escena) r.aporta(g);
        BufferedImage im = lienzo();
        return new Salida(im, r.prensa(im));
    }

    static int[] diferencias(BufferedImage a, BufferedImage b) {
        int[] r = new int[2];
        for (int j = 0; j < a.getHeight(); j++) for (int i = 0; i < a.getWidth(); i++) {
            int p = a.getRGB(i, j), q = b.getRGB(i, j);
            if (p == q) continue;
            r[0]++;
            for (int s : new int[] { 24, 16, 8, 0 }) r[1] = Math.max(r[1], Math.abs(((p >> s) & 255) - ((q >> s) & 255)));
        }
        return r;
    }

    /** Mapa de profundidad de la nube: n&uacute;mero de capas con cobertura &gt; 0 por p&iacute;xel, por consulta directa. */
    static void profundidad(List<Geometria> escena, File dir) throws Exception {
        int[] cuenta = new int[W * H], hist = new int[8];
        for (int j = 0; j < H; j++) for (int i = 0; i < W; i++) {
            int[] k = new int[1];
            raiz(escena).capas(i + 0.5, j + 0.5, Opciones.POR_DEFECTO, t -> { if (t.cobertura() > 0.001) k[0]++; });
            cuenta[j * W + i] = k[0];
            hist[Math.min(k[0], 7)]++;
        }
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        int[] rampa = { FONDO, 0xFFFFF3B0, 0xFFFFC65C, 0xFFF08A1C, 0xFFD0432A, 0xFF901F5A, 0xFF3B1D5E, 0xFF12081F };
        for (int j = 0; j < H; j++) for (int i = 0; i < W; i++) im.setRGB(i, j, rampa[Math.min(cuenta[j * W + i], 7)]);
        ImageIO.write(im, "png", new File(dir, "nube_profundidad.png"));
        long tocados = 0;
        for (int k = 1; k < 8; k++) tocados += hist[k];
        if (tocados == 0) { System.out.println("PROFUNDIDAD sin pixeles tocados"); return; }
        StringBuilder sb = new StringBuilder("PROFUNDIDAD pixeles tocados=" + tocados);
        for (int k = 1; k < 8; k++) if (hist[k] > 0) sb.append(String.format("  %s%d=%.2f%%", k == 7 ? ">=" : "", k, 100.0 * hist[k] / tocados));
        System.out.println(sb);
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : ".");
        dir.mkdirs();
        System.out.printf("== Nube semitransparente (alfa 0x99) %dx%d: 3 Gotas, 1 Grafo y 1 Grupo solapados%n", W, H);

        double[] alturas = { 0, 10, 20, 15, 5 };
        long t0 = System.nanoTime();
        BufferedImage A = conPlotter(nube(alturas));
        System.out.printf("A  Plotter con un Grupo raiz (hoy)                        %.0f ms%n", (System.nanoTime() - t0) / 1e6);
        ImageIO.write(A, "png", new File(dir, "nube_A_plotter.png"));

        Salida b = conRejilla(1, Rejilla.Cobertura.TAL_CUAL, nube(alturas));
        int[] dB = diferencias(A, b.im());
        System.out.printf("B  Rejilla N=1 TAL_CUAL     %.0f ms  dif. con A: %d px (canal hasta %d)  maxProf=%d aportes=%d%n",
            b.e().nanos() / 1e6, dB[0], dB[1], b.e().maxProfundidad(), b.e().aportes());
        ImageIO.write(b.im(), "png", new File(dir, "nube_B_rejilla_n1.png"));

        Salida c = conRejilla(3, Rejilla.Cobertura.ESTRECHADA, nube(alturas));
        int[] dC = diferencias(b.im(), c.im());
        System.out.printf("C  Rejilla N=3 ESTRECHADA   %.0f ms  dif. con B: %d px (canal hasta %d)  maxProf=%d aportes=%d%n",
            c.e().nanos() / 1e6, dC[0], dC[1], c.e().maxProfundidad(), c.e().aportes());
        ImageIO.write(c.im(), "png", new File(dir, "nube_C_rejilla_n3.png"));

        double[] permutadas = { 20, 5, 0, 15, 10 };
        Salida d = conRejilla(1, Rejilla.Cobertura.TAL_CUAL, nube(permutadas));
        BufferedImage aD = conPlotter(nube(permutadas));
        int[] dD = diferencias(b.im(), d.im()), dAD = diferencias(aD, d.im());
        System.out.printf("D  alturas permutadas       %.0f ms  dif. con B: %d px (canal hasta %d); con su Plotter: %d px (canal hasta %d)%n",
            d.e().nanos() / 1e6, dD[0], dD[1], dAD[0], dAD[1]);
        ImageIO.write(d.im(), "png", new File(dir, "nube_D_permutada.png"));

        double[] cero = { 0, 0, 0, 0, 0 };
        List<Geometria> e1s = nube(cero);
        Salida e1 = conRejilla(1, Rejilla.Cobertura.TAL_CUAL, e1s);
        List<Geometria> e2s = nube(cero);
        Collections.reverse(e2s);
        Salida e2 = conRejilla(1, Rejilla.Cobertura.TAL_CUAL, e2s);
        BufferedImage aE = conPlotter(e1s);
        int[] dE = diferencias(e1.im(), e2.im()), dAE = diferencias(aE, e1.im());
        System.out.printf("E1 alturas 0, orden de aporte          %.0f ms  dif. con A (alturas 0): %d px (canal hasta %d)%n", e1.e().nanos() / 1e6, dAE[0], dAE[1]);
        System.out.printf("E2 alturas 0, orden de aporte inverso  %.0f ms  dif. con E1: %d px (canal hasta %d)%n",
            e2.e().nanos() / 1e6, dE[0], dE[1]);
        ImageIO.write(e1.im(), "png", new File(dir, "nube_E1_empate.png"));
        ImageIO.write(e2.im(), "png", new File(dir, "nube_E2_empate_inverso.png"));

        profundidad(nube(alturas), dir);
    }
}
