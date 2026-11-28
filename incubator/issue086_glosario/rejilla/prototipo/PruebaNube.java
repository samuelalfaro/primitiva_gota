import color.Colores;
import color.Mezcla;
import coordenadas.Opciones;
import forma.Gota;
import forma.Perfil;
import geometria.Geometria;
import geometria.Grupo;
import geometria.composicion.Grafo;
import geometria.composicion.Pintada;
import pintado.Plotter;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Estudio #86: prueba de convivencia de alturas entre Gotas, Grafos y Grupos.
 * <p>
 * Una nube de formas semitransparentes de los tres tipos, con alturas distintas (y Grupos, también anidados, que desplazan las
 * de sus hijos). Se comprueba:
 * <ol>
 * <li>ORÁCULO: la nube entera dentro de un único Grupo raíz, pintada con el Plotter actual (el Grupo ya ordena por altura en cada
 *     punto, sumando los desplazamientos de los Grupos).</li>
 * <li>La Rejilla (N = 1) con las hojas aportadas en órdenes distintos: debe coincidir con el oráculo en todos (el orden de aporte
 *     no importa si las alturas son distintas).</li>
 * <li>La Rejilla con el árbol de Grupos tal cual.</li>
 * <li>El Plotter actual con una llamada a {@code plot} por hoja, en órdenes distintos: el orden de las llamadas manda y NO coincide.</li>
 * <li>Rejilla N = 3 (cobertura estrechada): solo difiere en los bordes.</li>
 * </ol>
 */
public class PruebaNube {
    static final int W = 640, H = 640, FONDO = 0xFFF4F1EA;

    record Hoja(Geometria g, double altura, String tipo) {}

    static BufferedImage lienzo() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), FONDO);
        return im;
    }

    static int color(Random r) {
        int rgb = Color.HSBtoRGB((float) r.nextDouble(), (float) (0.55 + 0.4 * r.nextDouble()), (float) (0.7 + 0.3 * r.nextDouble())) & 0xFFFFFF;
        int a = (int) Math.round(255 * (0.45 + 0.30 * r.nextDouble()));
        return a << 24 | rgb;
    }

    static Pintada gota(Random r, double alt) {
        double x = 90 + r.nextDouble() * (W - 180), y = 90 + r.nextDouble() * (H - 180), ang = r.nextDouble() * Math.PI, d = 30 + r.nextDouble() * 120;
        double r1 = 20 + r.nextDouble() * 40, r2 = 15 + r.nextDouble() * 40;
        int c = color(r);
        return Pintada.de(Gota.lineal(new Point2D.Double(x, y), r1, new Point2D.Double(x + d * Math.cos(ang), y + d * Math.sin(ang)), r2, Perfil.recto()),
                Colores.cuatroColores(c, c, c, c, Mezcla.RGB)).conAltura(alt);
    }

    static Grafo grafo(Random r, double alt) {
        Grafo g = new Grafo().mezcla(Mezcla.RGB);
        double x = 90 + r.nextDouble() * (W - 180), y = 90 + r.nextDouble() * (H - 180);
        Grafo.Nodo prev = null;
        for (int i = 0; i < 3; i++) {
            Grafo.Nodo nd = g.nodo(Math.max(40, Math.min(W - 40, x + (r.nextDouble() - 0.5) * 220)), Math.max(40, Math.min(H - 40, y + (r.nextDouble() - 0.5) * 220)), 12 + r.nextDouble() * 22).centro(color(r));
            if (prev != null) g.arista(prev, nd);
            prev = nd;
        }
        return g.altura(alt);
    }

    /** Un Grupo con 2-3 hijos (Gotas y Grafos, a veces otro Grupo) con alturas propias y un desplazamiento del grupo. Registra las hojas con su altura efectiva. */
    static Grupo grupo(Random r, double desplaza, double base, List<Hoja> hojas, boolean anidado) {
        Grupo gr = new Grupo();
        int n = 2 + r.nextInt(2);
        for (int i = 0; i < n; i++) {
            double h = base + (i + r.nextDouble() * 0.5) * 0.7;                // alturas propias del hijo, dentro del grupo
            if (anidado && i == 0) {
                double d2 = 3 + r.nextDouble() * 2;
                gr.anade(grupo(r, d2, h, hojas, false));
                // las hojas del anidado ya se han registrado con su desplazamiento propio; falta sumar el de este grupo (abajo)
            } else if (r.nextBoolean()) { Pintada p = gota(r, h); gr.anade(p); hojas.add(new Hoja(p, h, "gota en Grupo")); }
            else { Grafo g = grafo(r, h); gr.anade(g); hojas.add(new Hoja(g, h, "grafo en Grupo")); }
        }
        gr.altura(desplaza);
        for (int k = hojas.size() - 1, c = 0; c < n && k >= 0; k--) { /* se ajustan abajo con el recorrido */ c++; }
        return gr;
    }

    /** Altura efectiva de cada hoja: se recorre el árbol sumando los desplazamientos de los Grupos. */
    static void efectivas(Geometria g, double acum, java.util.Map<Geometria, Double> out) {
        if (g instanceof Grupo gr) { final double a = acum + gr.altura(); gr.acepta(new geometria.Visitante() {
            @Override public void hoja(Geometria h) { if (h != gr) out.put(h, a + h.altura()); }
        }); }
        else out.put(g, acum + g.altura());
    }

    static void anade(List<Geometria> hojas, Geometria g) { hojas.add(g); }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : ".");
        dir.mkdirs();
        Random r = new Random(86);
        // la nube: hojas sueltas de los tres tipos y Grupos (uno anidado), con alturas distintas
        List<Geometria> raices = new ArrayList<>();
        List<Hoja> info = new ArrayList<>();
        for (int i = 0; i < 7; i++) { double h = i * 1.9 + r.nextDouble(); Pintada p = gota(r, h); raices.add(p); info.add(new Hoja(p, h, "gota")); }
        for (int i = 0; i < 7; i++) { double h = i * 1.9 + 0.95 + r.nextDouble() * 0.5; Grafo g = grafo(r, h); raices.add(g); info.add(new Hoja(g, h, "grafo")); }
        for (int i = 0; i < 4; i++) raices.add(grupo(r, 2 + i * 3.1 + r.nextDouble(), 0, new ArrayList<>(), i == 3));

        // hojas con altura efectiva (se recorre cada raíz; un Grupo suma sus desplazamientos)
        java.util.Map<Geometria, Double> ef = new java.util.IdentityHashMap<>();
        for (Geometria g : raices) efectivas(g, 0, ef);
        List<Geometria> hojas = new ArrayList<>(ef.keySet());
        hojas.sort((a, b) -> Double.compare(ef.get(a), ef.get(b)));
        double minSep = Double.MAX_VALUE;
        for (int i = 1; i < hojas.size(); i++) minSep = Math.min(minSep, ef.get(hojas.get(i)) - ef.get(hojas.get(i - 1)));
        int nGotas = 0, nGrafos = 0;
        for (Geometria g : hojas) { if (g instanceof Pintada) nGotas++; else if (g instanceof Grafo) nGrafos++; }
        System.out.printf("nube: %d hojas (%d gotas, %d grafos), %d raíces (7 gotas + 7 grafos + 4 grupos, uno con otro anidado); alturas efectivas de %.2f a %.2f, separación mínima %.3f%n",
                hojas.size(), nGotas, nGrafos, raices.size(), ef.get(hojas.get(0)), ef.get(hojas.get(hojas.size() - 1)), minSep);

        Opciones op = Opciones.POR_DEFECTO;
        // 1. oráculo: todo en un Grupo raíz, Plotter actual
        BufferedImage orac = lienzo();
        Grupo raiz = new Grupo(raices.toArray(new Geometria[0]));
        Plotter.raster(orac).plot(raiz, op);
        ImageIO.write(orac, "png", new File(dir, "nube_oraculo.png"));

        // 2. Rejilla N=1 con las hojas en varios órdenes
        // las alturas hay que dárselas ya efectivas: se aporta cada raíz tal cual (Grupo y hojas) en órdenes distintos
        for (int p = 0; p < 4; p++) {
            List<Geometria> ord = new ArrayList<>(raices);
            Collections.shuffle(ord, new Random(1000 + p));
            BufferedImage im = lienzo();
            Rejilla rj = new Rejilla(1, 32, Rejilla.Cobertura.TAL_CUAL, Rejilla.Filtro.SRGB);
            for (Geometria g : ord) rj.aporta(g, op);
            rj.prensa(im);
            int[] d = dif(orac, im);
            System.out.printf("Rejilla N=1, raíces en orden %d: %d px distintos del oráculo (canal hasta %d)%n", p, d[0], d[1]);
        }
        // 3. Plotter actual, una llamada por raíz, en órdenes distintos
        for (int p = 0; p < 4; p++) {
            List<Geometria> ord = new ArrayList<>(raices);
            Collections.shuffle(ord, new Random(1000 + p));
            BufferedImage im = lienzo();
            for (Geometria g : ord) Plotter.raster(im).plot(g, op);
            int[] d = dif(orac, im);
            System.out.printf("Plotter, una llamada por raíz en orden %d: %d px distintos del oráculo (canal hasta %d)%n", p, d[0], d[1]);
            if (p == 0) ImageIO.write(im, "png", new File(dir, "nube_plotter_orden0.png"));
        }
        // 4. Rejilla N=3
        BufferedImage n3 = lienzo();
        Rejilla.Estadisticas e = new Rejilla(3, 32, Rejilla.Cobertura.ESTRECHADA, Rejilla.Filtro.SRGB).aporta(raiz, op).prensa(n3);
        int[] d3 = dif(orac, n3);
        System.out.printf("Rejilla N=3 estrechada: %d px distintos del oráculo (canal hasta %d), profundidad máxima %d, %.0f ms%n", d3[0], d3[1], e.maxProfundidad(), e.nanos() / 1e6);
        ImageIO.write(n3, "png", new File(dir, "nube_rejilla_n3.png"));
    }

    static int[] dif(BufferedImage a, BufferedImage b) {
        int[] r = new int[2];
        for (int j = 0; j < a.getHeight(); j++) for (int i = 0; i < a.getWidth(); i++) {
            int p = a.getRGB(i, j), q = b.getRGB(i, j);
            if (p == q) continue;
            r[0]++;
            for (int s : new int[] { 24, 16, 8, 0 }) r[1] = Math.max(r[1], Math.abs(((p >> s) & 255) - ((q >> s) & 255)));
        }
        return r;
    }
}
