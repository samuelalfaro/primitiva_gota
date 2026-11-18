import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Cadena axial de color: propiedades de la función de transición u(t, s), equivalencias entre
 * configuraciones, anclas, comparación de f con la tira, galería de ajustes y vectores de u en CSV.
 */
public class GotaCadena {

    static int pruebas = 0, fallos = 0;

    static void comprueba(String nombre, boolean ok, String detalle) {
        pruebas++;
        if (!ok) fallos++;
        System.out.printf("%-66s %s%s%n", nombre, ok ? "ok   " : "FALLA", detalle.isEmpty() ? "" : "  " + detalle);
    }

    static Point2D.Double p(double x, double y) { return new Point2D.Double(x, y); }

    static int[] datos(BufferedImage img) { return ((DataBufferInt) img.getRaster().getDataBuffer()).getData(); }

    public static void main(String[] args) throws Exception {
        transicion();
        equivalencias();
        anclas();
        unificacion();
        tmManda();
        tira();
        galeria();
        csv();
        System.out.printf("%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }

    // ---------- u(t, s) ----------

    static void transicion() {
        int N = 1000;
        double e = 0;
        for (int i = 0; i <= N; i++) e = Math.max(e, Math.abs(Colores.transicion(i / (double) N, 0.5) - i / (double) N));
        comprueba("u(t, 0.5) = t: el neutro es lineal", e < 1e-12, String.format("error %.1e", e));

        boolean ext = true;
        for (int j = 0; j <= N; j++) {
            double s = j / (double) N;
            if (Colores.transicion(0, s) != 0 || Colores.transicion(1, s) != 1) ext = false;
        }
        comprueba("u(0, s) = 0 y u(1, s) = 1 para todo s (también los saltos)", ext, "");

        boolean mono = true;
        for (int j = 0; j <= 100; j++) {
            double s = j / 100.0, prev = -1;
            for (int i = 0; i <= N; i++) {
                double u = Colores.transicion(i / (double) N, s);
                if (u < prev - 1e-12) mono = false;
                prev = u;
            }
        }
        comprueba("u crece con t para todo s", mono, "");

        boolean monoS = true;
        for (int i = 1; i < N; i++) {
            double t = i / (double) N, prev = 2;
            for (int j = 0; j <= N; j++) {
                double u = Colores.transicion(t, j / (double) N);
                if (u > prev + 1e-12) monoS = false;
                prev = u;
            }
        }
        comprueba("u decrece con s para todo t (s alto retrasa, s bajo adelanta)", monoS, "");

        double sim = 0;
        for (int j = 0; j <= 100; j++)
            for (int i = 0; i <= 100; i++) {
                double s = j / 100.0, t = i / 100.0;
                sim = Math.max(sim, Math.abs(Colores.transicion(t, s) - (1 - Colores.transicion(1 - t, 1 - s))));
            }
        comprueba("simetría u(t, s) = 1 - u(1 - t, 1 - s) (adelantar y retrasar son espejos)", sim < 1e-12,
                  String.format("error %.1e", sim));

        double salto = 0;
        for (int i = 0; i <= 100; i++) {
            double t = i / 100.0;
            salto = Math.max(salto, Math.abs(Colores.transicion(t, 0.5 - 1e-9) - Colores.transicion(t, 0.5 + 1e-9)));
        }
        comprueba("continuidad al cruzar el neutro s = 0.5", salto < 1e-6, String.format("salto máximo %.1e", salto));

        double a = Colores.transicion(0.5, 1e-9), b = Colores.transicion(0.5, 1 - 1e-9);
        comprueba("continuidad en los extremos: u(1/2, s→0) → 1 y u(1/2, s→1) → 0", a > 0.999 && b < 0.001,
                  String.format("u(1/2, 1e-9) = %.6f, u(1/2, 1 - 1e-9) = %.6f", a, b));
    }

    // ---------- Equivalencias ----------

    static BufferedImage lienzo(int w, int h) { return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); }

    static Coordenadas.Muestra mu() { return new Coordenadas.Muestra(0.5, 0, 0, 0); }

    static int maxDif(int[] a, int[] b) {
        int m = 0;
        for (int i = 0; i < a.length; i++)
            for (int s = 0; s < 32; s += 8) m = Math.max(m, Math.abs(((a[i] >>> s) & 0xFF) - ((b[i] >>> s) & 0xFF)));
        return m;
    }

    static void equivalencias() {
        int a = 0xFF2563EB, b = 0xFFFACC15;
        Gota[] gs = {
            Gota.lineal(p(60, 150), 50, p(240, 150), 20, Perfil.recto()),
            Gota.lineal(p(60, 150), 50, p(240, 150), 20, Perfil.central(1.8)),
            Gota.curva(p(60, 240), 35, new Gota.Vec(0, -200), p(240, 240), 15, new Gota.Vec(0, -200), Perfil.recto()),
            Gota.lineal(p(60, 150), 50, p(240, 150), 20, Perfil.radioMedio(10, 0.3, 0.2)),
        };
        int peor = 0;
        for (Gota g : gs) {
            for (Mezcla m : new Mezcla[] { Mezcla.RGB, Mezcla.LCH }) {
                BufferedImage i1 = lienzo(300, 300), i2 = lienzo(300, 300);
                Plotter.raster(i1).plot(g, Colores.dosColores(a, b, m));
                Plotter.raster(i2).plot(g, Colores.dosColores(a, b, 0.5, m));
                peor = Math.max(peor, maxDif(datos(i1), datos(i2)));
            }
        }
        comprueba("dosColores(a, b) == dosColores(a, b, 0.5) (RGB y LCH, también con tm movido)", peor <= 1,
                  "dif. máx " + peor + " nivel");

        Relleno r0 = Colores.dosColores(a, b, 0.0).para(gs[0]), r1 = Colores.dosColores(a, b, 1.0).para(gs[0]);
        Coordenadas.Uv tapa1 = new Coordenadas.Uv(0.5, 0.1), tapa2 = new Coordenadas.Uv(0.5, 0.9), medio = new Coordenadas.Uv(0.5, 0.5);
        boolean ok = r0.argb(mu(), tapa1) == a && r0.argb(mu(), tapa2) == b && r1.argb(mu(), tapa1) == a
                && r1.argb(mu(), tapa2) == b && r0.argb(mu(), medio) == b && r1.argb(mu(), medio) == a;
        comprueba("dosColores con ajuste 0 y 1: tapas de color constante; el salto cae en el tramo central", ok, "");

        boolean lanza = false;
        try { Colores.dosColores(a, b, 0.5).argb(0.5, 0.5); } catch (UnsupportedOperationException e) { lanza = true; }
        boolean lambdaOk = Colores.dosColores(a, b).argb(0.5, 0.5) != 0;
        comprueba("dosColores(a, b) admite argb(t, rho); la variante con ajuste exige la coordenada v", lanza && lambdaOk, "");
    }

    // ---------- Anclas y continuidad ----------

    static boolean cercano(int x, int y, int tol) {
        for (int s = 0; s < 32; s += 8)
            if (Math.abs(((x >>> s) & 0xFF) - ((y >>> s) & 0xFF)) > tol) return false;
        return true;
    }

    static void anclas() {
        int e1 = 0xFF1E1B4B, c1 = 0xFFFFE27A, c2 = 0xFF2563EB, e2 = 0xFF7C2D12;
        Gota g = Gota.lineal(p(60, 150), 50, p(240, 150), 20, Perfil.recto());
        double[] ajustes = { 0.5, 0.2, 0.8, 0.0, 1.0 };
        boolean exactas = true, continuas = true;
        String det = "";
        for (double s : ajustes) {
            Relleno r = Colores.cadena(e1, c1, c2, e2, s, s, s, Mezcla.RGB).para(g);
            boolean ok = r.argb(mu(), new Coordenadas.Uv(0.5, 0)) == e1
                    && r.argb(mu(), new Coordenadas.Uv(0.5, 0.25)) == c1
                    && r.argb(mu(), new Coordenadas.Uv(0.5, 0.75)) == c2
                    && r.argb(mu(), new Coordenadas.Uv(0.5, 1)) == e2;
            if (!ok) { exactas = false; det += " s=" + s; }
            if (s > 0 && s < 1) {   // con los saltos deliberados no hay continuidad en el ancla
                if (!cercano(r.argb(mu(), new Coordenadas.Uv(0.5, 0.25 - 1e-7)), c1, 1)) continuas = false;
                if (!cercano(r.argb(mu(), new Coordenadas.Uv(0.5, 0.75 + 1e-7)), c2, 1)) continuas = false;
                if (!cercano(r.argb(mu(), new Coordenadas.Uv(0.5, 0.25 + 1e-7)), c1, 1)) continuas = false;
            }
        }
        comprueba("anclas exactas en v = 0, 1/4, 3/4, 1 (todos los ajustes, incluidos 0 y 1)", exactas, det);
        comprueba("continuidad a ambos lados de las anclas con ajustes en (0, 1)", continuas, "");
    }

    // ---------- Unificación con la tira ----------

    static void unificacion() {
        int e1 = 0xFF1E1B4B, c1 = 0xFFFFE27A, c2 = 0xFF2563EB, e2 = 0xFF7C2D12;
        // cortes no uniformes: v1 = 0.1, v3 = 0.9 (pesos 1 : 8 : 1 -> v1 = 0.1, v3 = 0.9)
        Gota g = Gota.lineal(p(60.5, 150.5), 50, p(240.5, 150.5), 20, Perfil.recto());
        Opciones op = Opciones.POR_DEFECTO.conTira(Tira.pesos(1, 8, 1));
        Cortes cortes = op.tira().cortes(g);
        BufferedImage img = lienzo(300, 300);
        Plotter.raster(img).plot(g, Colores.cadena(e1, c1, c2, e2), op);
        int pix1 = datos(img)[150 * 300 + 60], pix2 = datos(img)[150 * 300 + 240];   // centros exactos de los círculos
        comprueba("la cadena sigue los cortes de la tira: el color de cada centro es exacto (v1 = " +
                  String.format("%.3f", cortes.c1()) + ", v3 = " + String.format("%.3f", cortes.c3()) + ")",
                  pix1 == c1 && pix2 == c2, String.format("%08X %08X", pix1, pix2));


        // en TRAZO también: el color de v es el mismo que el de la textura
        BufferedImage img3 = lienzo(300, 300);
        Plotter.raster(img3).plot(g, Colores.cadena(e1, c1, c2, e2), op.conSolape(Solape.TRAZO));
        comprueba("modo TRAZO: mismo color exacto en los centros", datos(img3)[150 * 300 + 60] == c1 && datos(img3)[150 * 300 + 240] == c2, "");
    }

    static void tira() {
        Gota g = Gota.lineal(p(60.5, 150.5), 50, p(240.5, 150.5), 20, Perfil.radioMedio(30, 0.3, 0.5));
        Cortes d = Tira.POR_DEFECTO.cortes(g);
        comprueba("Tira.POR_DEFECTO: v1 = 1/4, v3 = 3/4 y v2 = v1 + (v3 - v1)·tm",
                  d.c1() == 0.25 && d.c3() == 0.75 && d.c2() == 0.25 + 0.5 * g.tm(), String.format("%.4f %.4f %.4f", d.c1(), d.c2(), d.c3()));
        Cortes p = Tira.pesos(1, 8, 1).cortes(g);
        comprueba("Tira.pesos(1, 8, 1): v1 = 0.1 y v3 = 0.9", Math.abs(p.c1() - 0.1) < 1e-12 && Math.abs(p.c3() - 0.9) < 1e-12, "");
        boolean lanza = true;
        for (double[] w : new double[][] { { 0, 1, 1 }, { 1, -1, 1 }, { 1, 1, Double.NaN }, { 1, Double.POSITIVE_INFINITY, 1 } })
            try { Tira.pesos(w[0], w[1], w[2]); lanza = false; } catch (IllegalArgumentException e) { }
        comprueba("Tira.pesos rechaza pesos no positivos o no finitos", lanza, "");
        // gota recta de radios 50 y 20 y largo 180: pesos 50 : 180 : 20
        Cortes q = Tira.proporcional().cortes(g);
        double total = 50 + 180 + 20;
        comprueba("Tira.proporcional: tapas y cuerpo en proporción a sus longitudes",
                  Math.abs(q.c1() - 50 / total) < 1e-9 && Math.abs(q.c3() - 230 / total) < 1e-9, String.format("%.4f %.4f", q.c1(), q.c3()));
        comprueba("Opciones.conTira cambia solo la tira", Opciones.POR_DEFECTO.conTira(Tira.proporcional()).costura() == Costura.ESPIGA
                  && Opciones.POR_DEFECTO.conTira(Tira.proporcional()).solape() == Solape.PLANO, "");
        boolean nulo = false;
        try { Opciones.POR_DEFECTO.conTira(null); } catch (NullPointerException e) { nulo = true; }
        comprueba("opciones con tira nula: NullPointerException", nulo, "");
        // el reparto cambia las coordenadas sin tocar la forma
        Point2D c = g.centro(0.15);   // en la primera mitad del cuerpo (tm = 0.3): v difiere entre repartos
        double vd = Coordenadas.uv(g, c.getX(), c.getY(), Opciones.POR_DEFECTO).v(), vp = Coordenadas.uv(g, c.getX(), c.getY(), Opciones.POR_DEFECTO.conTira(Tira.pesos(1, 8, 1))).v();
        comprueba("la tira cambia la v de un punto del cuerpo y no la pertenencia", Math.abs(vd - vp) > 0.05 && g.mascara().contiene(c.getX(), c.getY()), String.format("v = %.3f / %.3f", vd, vp));
    }

    static void tmManda() {
        boolean ok = true;
        String det = "";
        for (double tm : new double[] { 0.1, 0.3, 0.5, 0.8 }) {
            Gota g = Gota.lineal(p(60.5, 150.5), 50, p(240.5, 150.5), 20, Perfil.radioMedio(30, tm, 0.5));
            Opciones op = Opciones.POR_DEFECTO.conTira(Tira.pesos(1, 8, 1));
            Cortes k = op.tira().cortes(g);
            Point2D c = g.centro(g.tm());
            double v = Coordenadas.uv(g, c.getX(), c.getY(), op).v(), esperado = k.c1() + (k.c3() - k.c1()) * g.tm();
            if (Math.abs(k.c2() - esperado) > 1e-12 || Math.abs(v - esperado) > 1e-3) { ok = false; det += String.format(" tm=%.1f v=%.4f esp=%.4f", tm, v, esperado); }
        }
        comprueba("tm manda: el corte central v2 = v1 + (v3 - v1)·tm y el centro en t = tm tiene v = v2", ok, det);
    }

    // ---------- Galería ----------

    static void galeria() throws Exception {
        int e1 = 0xFF1E1B4B, c1 = 0xFFFFE27A, c2 = 0xFF2563EB, e2 = 0xFF7C2D12;
        double[][] ajustes = {
            { 0.5, 0.5, 0.5 }, { 0.5, 0.2, 0.5 }, { 0.5, 0.8, 0.5 },
            { 0.5, 0.0, 0.5 }, { 0.5, 1.0, 0.5 }, { 0.2, 0.5, 0.8 },
        };
        BufferedImage img = lienzo(900, 600);
        Arrays.fill(datos(img), 0xFFFFFFFF);
        for (int i = 0; i < ajustes.length; i++) {
            double ox = (i % 3) * 300, oy = (i / 3) * 300;
            Gota g = Gota.lineal(p(ox + 60, oy + 150), 50, p(ox + 240, oy + 150), 25, Perfil.central(1.2));
            Plotter.raster(img).plot(g, Colores.cadena(e1, c1, c2, e2, ajustes[i][0], ajustes[i][1], ajustes[i][2]));
        }
        ImageIO.write(img, "png", new File("gota_cadena.png"));
    }

    // ---------- Vectores de u(t, s) ----------

    static void csv() throws Exception {
        double[] ss = { 0, 0.1, 0.25, 0.4, 0.5, 0.6, 0.75, 0.9, 1 };
        try (PrintWriter w = new PrintWriter(new File("transicion_u.csv"), "UTF-8")) {
            w.println("t,s,u");
            for (double s : ss)
                for (int i = 0; i <= 20; i++) {
                    double t = i / 20.0;
                    w.println(String.format(Locale.ROOT, "%.2f,%.2f,%.12f", t, s, Colores.transicion(t, s)));
                }
        }
    }
}
