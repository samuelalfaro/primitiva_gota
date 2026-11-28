import color.Colores;
import color.Degradado;
import color.Mezcla;
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
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import pintado.Plotter;

/**
 * Estudio #86: una llama con humo, con los ingredientes de la nube (Gota, Grafo y Grupo) y la pila ordenada de aportes de
 * la Rejilla, que es lo que las tarjetas de los tiempos de OpenGL no daban: reordenar los aportes p&iacute;xel a p&iacute;xel.
 * El &laquo;add&raquo; de las explosiones se simula apilando capas claras semitransparentes (el n&uacute;cleo se acumula
 * hacia el blanco); el humo &laquo;multiplica&raquo; con capas oscuras que apagan lo de debajo; y la serpentina de humo es
 * un Grupo de tramos cada uno a su altura, de modo que pasa por delante de la llama abajo y por detr&aacute;s arriba,
 * cambiando de plano p&iacute;xel a p&iacute;xel a lo largo de ella.
 * <p>
 * Variantes: A) el Plotter actual con un Grupo ra&iacute;z; B) la Rejilla N=1; C) la Rejilla N=3 con cobertura ESTRECHADA
 * (la l&aacute;mina final). Uso: java Llama_by_Mistral [ruta de la carpeta donde dejar los PNG]
 */
public class Llama_by_Mistral {
    static final int W = 800, H = 800, FONDO = 0xFF0D1016;
    static final int NARANJA = 0xFFE8752C, HUMO = 0x48454A52, NUBE = 0x3830363E;
    static final Mezcla RGB = Mezcla.RGB;

    static BufferedImage lienzo() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), FONDO);
        return im;
    }

    static Pintada disco(double x, double y, double r, int c, double altura) {
        return Pintada.de(Gota.lineal(new Point2D.Double(x, y), r, new Point2D.Double(x + 0.5, y), r, Perfil.recto()),
            Colores.cuatroColores(c, c, c, c, RGB)).conAltura(altura);
    }

    /** La llama: un Grafo (tira de cinco nodos, base ancha y punta fina), cuerpo fusionado y color continuo. */
    static Grafo llama(double altura) {
        double[][] ns = { { 400, 645, 66 }, { 380, 565, 46 }, { 426, 487, 36 }, { 386, 408, 25 }, { 414, 332, 13 } };
        Grafo g = new Grafo().mezcla(RGB);
        Grafo.Nodo[] n = new Grafo.Nodo[ns.length];
        for (int i = 0; i < ns.length; i++) n[i] = g.nodo(ns[i][0], ns[i][1], ns[i][2]).centro(NARANJA);
        for (int i = 0; i + 1 < ns.length; i++) g.arista(n[i], n[i + 1]).espina(Degradado.constante(NARANJA));
        return g.altura(altura);
    }

    /** La serpentina de humo: tramos entre seis puntos, cada tramo a su altura (20, 17, 14, 11, 8). */
    static Grupo serpentina() {
        double[][] ws = { { 380, 505, 30 }, { 462, 425, 34 }, { 382, 335, 40 }, { 432, 245, 46 }, { 402, 155, 52 }, { 420, 80, 58 } };
        double[] alt = { 20, 17, 14, 11, 8 };
        Grupo g = new Grupo();
        for (int i = 0; i + 1 < ws.length; i++)
            g.anade(Pintada.de(Gota.lineal(new Point2D.Double(ws[i][0], ws[i][1]), ws[i][2],
                    new Point2D.Double(ws[i + 1][0], ws[i + 1][1]), ws[i + 1][2], Perfil.recto()),
                Colores.cuatroColores(HUMO, HUMO, HUMO, HUMO, RGB)).conAltura(alt[i]));
        return g.label("serpentina");
    }

    static List<Geometria> escena() {
        List<Geometria> l = new ArrayList<>();
        // estrellas: las apaga el humo que pasa por delante (la «sustracción»)
        double[][] es = { { 120, 120 }, { 680, 90 }, { 90, 420 }, { 720, 360 }, { 200, 60 }, { 600, 620 }, { 150, 700 }, { 650, 560 } };
        for (double[] e : es) l.add(disco(e[0], e[1], 1.6, 0xB0FFFFFF, 0));
        // brasas: el «add» ancho y tenue del suelo
        l.add(disco(400, 700, 190, 0x22FF8A2A, 1));
        l.add(disco(400, 690, 120, 0x30FFB84A, 1));
        // humo de fondo, entre las brasas y la llama
        l.add(new Grupo(disco(300, 210, 150, 0x30262B33, 0), disco(520, 170, 170, 0x30262B33, 0),
                        disco(430, 90, 140, 0x30262B33, 0)).altura(3).label("humo fondo"));
        l.add(llama(10));
        // núcleo caliente: el «add» apilado, capas claras que se acumulan hacia el blanco
        l.add(disco(400, 592, 82, 0x30FFC24A, 11));
        l.add(disco(400, 585, 55, 0x36FFE08A, 11));
        l.add(disco(400, 578, 32, 0x3AFFF6D8, 11));
        // chispas: pequeñas «explosiones» add
        Random rnd = new Random(7);
        for (int i = 0; i < 16; i++) {
            double x = 400 + (rnd.nextDouble() - 0.5) * 150;
            double y = 620 - Math.pow(rnd.nextDouble(), 1.6) * 300;
            int c = i % 3 == 0 ? 0x70FFEEB0 : 0x60FFD24A;
            l.add(disco(x, y, 3 + rnd.nextDouble() * 7, c, 12));
        }
        l.add(serpentina());
        // nubes frontales: el «multiplicado» final, apagan la serpentina y las estrellas
        l.add(new Grupo(disco(300, 120, 110, NUBE, 0), disco(500, 140, 130, NUBE, 0)).altura(25).label("nubes"));
        return l;
    }

    static BufferedImage conPlotter(List<Geometria> escena) {
        Grupo raiz = new Grupo();
        for (Geometria g : escena) raiz.anade(g);
        BufferedImage im = lienzo();
        Plotter.raster(im).plot(raiz);
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

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : ".");
        dir.mkdirs();
        System.out.printf("== Llama con humo %dx%d: %d geometrías (Gotas, un Grafo y dos Grupos)%n", W, H, escena().size());

        long t0 = System.nanoTime();
        BufferedImage A = conPlotter(escena());
        System.out.printf("A  Plotter con un Grupo raiz (hoy)   %.0f ms%n", (System.nanoTime() - t0) / 1e6);
        ImageIO.write(A, "png", new File(dir, "llama_A_plotter.png"));

        t0 = System.nanoTime();
        Salida b = conRejilla(1, Rejilla.Cobertura.TAL_CUAL, escena());
        int[] dB = diferencias(A, b.im());
        System.out.printf("B  Rejilla N=1 TAL_CUAL  %.0f ms  dif. con A: %d px (canal hasta %d)  maxProf=%d aportes=%d%n",
            (System.nanoTime() - t0) / 1e6, dB[0], dB[1], b.e().maxProfundidad(), b.e().aportes());
        ImageIO.write(b.im(), "png", new File(dir, "llama_B_rejilla_n1.png"));

        t0 = System.nanoTime();
        Salida c = conRejilla(3, Rejilla.Cobertura.ESTRECHADA, escena());
        int[] dC = diferencias(b.im(), c.im());
        System.out.printf("C  Rejilla N=3 ESTRECHADA  %.0f ms  dif. con B: %d px (canal hasta %d)  maxProf=%d aportes=%d%n",
            (System.nanoTime() - t0) / 1e6, dC[0], dC[1], c.e().maxProfundidad(), c.e().aportes());
        ImageIO.write(c.im(), "png", new File(dir, "llama_C_rejilla_n3.png"));
    }
}
