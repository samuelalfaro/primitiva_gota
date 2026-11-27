package color;

import coordenadas.Punto;
import pintado.Plotter;
import coordenadas.Solape;
import color.Relleno;
import color.Mezcla;
import color.Degradado;
import color.Colores;
import coordenadas.Opciones;
import coordenadas.Costura;
import coordenadas.Cortes;
import coordenadas.Tira;
import coordenadas.Coordenadas;
import forma.Perfil;
import forma.Gota;
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
        degradados();
        funciones();
        cadenaFunciones();
        equivalencias();
        anclas();
        unificacion();
        tira();
        galeria();
        csv();
        System.out.printf("%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }

    // ---------- Degradado: f(x) -> ARGB ----------

    static void degradados() {
        int a = 0xFFFF0000, b = 0xFF0000FF;
        Degradado d = Degradado.entre(a, b, Mezcla.RGB);
        comprueba("Degradado.entre: extremos y recorte fuera de [0, 1]",
                d.en(0) == a && d.en(1) == b && d.en(-3) == a && d.en(7) == b, "");
        comprueba("Degradado.entre = Mezcla en cada x",
                d.en(0.3) == Mezcla.RGB.mezclar(a, b, 0.3) && d.en(0.77) == Mezcla.RGB.mezclar(a, b, 0.77), "");
        boolean ok = true;
        for (double s : new double[]{0, 0.2, 0.5, 0.8, 1})
            for (double x = 0; x <= 1; x += 0.125)
                ok &= d.ajustado(s).en(x) == Mezcla.RGB.mezclar(a, b, s == 0.5 ? x : Colores.transicion(x, s));
        comprueba("ajustado(s) = deformar x con la transición de siempre", ok, "");
        comprueba("una lambda propia es un Degradado", ((Degradado) x -> x < 0.5 ? a : b).en(0.9) == b, "");
        boolean lanza = false;
        try { d.ajustado(1.5); } catch (IllegalArgumentException e) { lanza = true; }
        comprueba("ajustado(s) rechaza s fuera de [0, 1]", lanza, "");
    }

    // ---------- Funciones: cinco funciones del esquema (•~~•) ----------

    static void funciones() {
        int c1 = 0xFFFF0000, c2 = 0xFF0000FF, n1 = 0xFF00FF00, k1 = 0xFFFFFF00, n2 = 0xFF00FFFF, k2 = 0xFFFF00FF;
        Colores.Funciones f = Colores.Funciones.de(c1, n1, k1, c2, n2, k2, Mezcla.RGB);
        comprueba("Funciones (6 colores): cada radio va del centro al contorno",
                f.normal1().en(0) == c1 && f.normal1().en(1) == n1 && f.contra1().en(1) == k1
                && f.normal2().en(0) == c2 && f.normal2().en(1) == n2 && f.contra2().en(1) == k2, "");
        comprueba("Funciones (6 colores): la espina va de un centro al otro",
                f.espina().en(0) == c1 && f.espina().en(1) == c2, "");
        Colores.Funciones dos = Colores.Funciones.de(c1, c2, Mezcla.RGB);
        comprueba("Funciones (2 colores): todo es de color constante en cada nodo",
                dos.normal1().en(0.7) == c1 && dos.contra2().en(0.2) == c2
                && dos.espina().en(0.5) == Mezcla.RGB.mezclar(c1, c2, 0.5), "");
        boolean lanza = false; String msg = "";
        try { new Colores.Funciones(f.normal1(), f.contra1(), f.espina(), Degradado.constante(k2), f.contra2()); }
        catch (IllegalArgumentException e) { lanza = true; msg = e.getMessage(); }
        comprueba("Funciones: un radio que no sale del color del centro lanza IllegalArgumentException", lanza, msg);
        comprueba("Funciones: el mensaje dice qué esquina falla", msg.contains("centro 2") && msg.contains("normal2(0)"), msg);
        // cada uno de los cuatro radios, normal y contra, debe salir del color de su centro
        Degradado malo = Degradado.constante(0xFF123456);
        String[] radio = {"normal1", "contra1", "normal2", "contra2"};
        String[] centroDe = {"centro 1", "centro 1", "centro 2", "centro 2"};
        for (int i = 0; i < 4; i++) {
            Degradado[] d = {f.normal1(), f.contra1(), f.normal2(), f.contra2()};
            d[i] = malo;
            String m = "";
            try { new Colores.Funciones(d[0], d[1], f.espina(), d[2], d[3]); }
            catch (IllegalArgumentException e) { m = e.getMessage(); }
            comprueba("Funciones: " + radio[i] + " fuera del color del centro se rechaza y se nombra",
                    m.startsWith(centroDe[i]) && m.contains(radio[i] + "(0)"), m);
        }
        boolean casi = true;
        try { new Colores.Funciones(f.normal1(), f.contra1(), f.espina(), x -> c2 - 1, f.contra2()); }
        catch (IllegalArgumentException e) { casi = false; }
        comprueba("Funciones: tolera 1/255 por canal", casi, "");
        boolean nulo = false;
        try { new Colores.Funciones(null, f.contra1(), f.espina(), f.normal2(), f.contra2()); }
        catch (NullPointerException e) { nulo = true; }
        comprueba("Funciones: rechaza funciones nulas", nulo, "");
    }

    // ---------- cadenaLateral(Funciones) frente a la cadena lateral de siempre ----------

    static void cadenaFunciones() throws Exception {
        int c1 = 0xFFE9C46A, n1 = 0xFF2A9D8F, k1 = 0xFFE76F51, c2 = 0xFFF4A261, n2 = 0xFF264653, k2 = 0xFF8AB17D;
        Relleno viejo = Colores.cadenaLateral(new Colores.Lateral(k1, c1, n1), new Colores.Lateral(k2, c2, n2), Mezcla.RGB);
        Relleno nuevo = Colores.cadenaLateral(Colores.Funciones.de(c1, n1, k1, c2, n2, k2, Mezcla.RGB));
        Gota g = Gota.lineal(new java.awt.geom.Point2D.Double(40, 60), 22, new java.awt.geom.Point2D.Double(200, 130), 14, Perfil.recto());
        java.awt.image.BufferedImage a = new java.awt.image.BufferedImage(260, 200, 2), b = new java.awt.image.BufferedImage(260, 200, 2);
        Plotter.raster(a).plot(g, viejo);
        Plotter.raster(b).plot(g, nuevo);
        int dif = 0, distintos = 0;
        for (int y = 0; y < 200; y++) for (int x = 0; x < 260; x++) {
            int p = a.getRGB(x, y), q = b.getRGB(x, y);
            int dm = 0;
            for (int sh = 0; sh <= 24; sh += 8) dm = Math.max(dm, Math.abs(((p >>> sh) & 255) - ((q >>> sh) & 255)));
            if (dm > 0) distintos++;
            dif = Math.max(dif, dm);
        }
        comprueba("cadenaLateral(Funciones) con la espina lineal da lo mismo que la cadena lateral de siempre",
                dif <= 1, "dif. máx. " + dif + "/255 en " + distintos + " px");

        // una espina no lineal solo cambia el color donde debe: en la espina (x = 0) vale exactamente esa función
        Degradado onda = x -> Mezcla.RGB.mezclar(c1, c2, x * x);
        Colores.Funciones q = new Colores.Funciones(Degradado.entre(c1, n1, Mezcla.RGB), Degradado.entre(c1, k1, Mezcla.RGB),
                onda, Degradado.entre(c2, n2, Mezcla.RGB), Degradado.entre(c2, k2, Mezcla.RGB));
        Relleno r = Colores.cadenaLateral(q);
        double v = 0.5, vc = 0.25 + (0.75 - 0.25) * v;
        int enEspina = r.argb(Punto.de(new Coordenadas.Muestra(0.5, 0, 0, 0), new Coordenadas.Uv(0.5, vc)));
        int esperado = onda.en(v);
        int dm = 0;
        for (int sh = 0; sh <= 24; sh += 8) dm = Math.max(dm, Math.abs(((enEspina >>> sh) & 255) - ((esperado >>> sh) & 255)));
        comprueba("con espina no lineal, el centro del cuerpo toma el color de la espina", dm <= 1, "dif. " + dm);
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
        comprueba("Tira.POR_DEFECTO: v1 = 1/4 y v3 = 3/4",
                  d.c1() == 0.25 && d.c3() == 0.75, String.format("%.4f %.4f", d.c1(), d.c3()));
        // longitud de arco: en el camino recto con velocidad constante fraccion(t) = t; en una curva con tiradores desiguales es creciente y no lineal
        boolean lin = true;
        for (int i = 0; i <= 10; i++) lin &= Math.abs(g.fraccion(i / 10.0) - i / 10.0) < 1e-3;
        comprueba("Camino.fraccion: camino recto uniforme da fraccion(t) = t", lin && g.fraccion(0) == 0 && Math.abs(g.fraccion(1) - 1) < 1e-12, "");
        Gota gc = Gota.curva(p(60.5, 150.5), 30, new Gota.Vec(150, 0), p(240.5, 150.5), 30, new Gota.Vec(10, 0), Perfil.radioMedio(30, 0.5, 1));
        boolean crece = true, noLin = false;
        for (int i = 1; i <= 100; i++) {
            crece &= gc.fraccion(i / 100.0) > gc.fraccion((i - 1) / 100.0);
            noLin |= Math.abs(gc.fraccion(i / 100.0) - i / 100.0) > 0.05;
        }
        comprueba("Camino.fraccion: creciente de 0 a 1 y distinta de t con tiradores desiguales", crece && noLin && Math.abs(gc.fraccion(1) - 1) < 1e-12, "");
        Cortes cl = Tira.porLongitud(Tira.EN_T).cortes(gc);
        comprueba("Tira.porLongitud: mismos c1 y c3 que EN_T y marca porLongitud", cl.porLongitud() && cl.c1() == 0.25 && cl.c3() == 0.75
                  && !Tira.EN_T.cortes(gc).porLongitud(), "");
        comprueba("Tira.POR_DEFECTO es EN_T con v por longitud de arco", Tira.POR_DEFECTO.cortes(gc).equals(cl), "");
        // v del cuerpo: lineal en la longitud de arco por defecto y en t con Tira.EN_T
        Point2D mc = gc.centro(0.5);
        double fl = gc.fraccion(0.5), vt = Coordenadas.uv(gc, mc.getX(), mc.getY(), Opciones.POR_DEFECTO.conTira(Tira.EN_T)).v(), vl = Coordenadas.uv(gc, mc.getX(), mc.getY(), Opciones.POR_DEFECTO).v();
        comprueba("v del cuerpo en t = 0.5: 0.5 con EN_T y 0.25 + 0.5·fraccion por defecto (longitud)", Math.abs(vt - 0.5) < 1e-2 && Math.abs(vl - (0.25 + 0.5 * fl)) < 1e-2 && Math.abs(vt - vl) > 0.02, String.format("%.3f %.3f fr=%.3f", vt, vl, fl));
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
        Point2D c = g.centro(0.15);   // en el cuerpo: v difiere entre repartos
        double vd = Coordenadas.uv(g, c.getX(), c.getY(), Opciones.POR_DEFECTO).v(), vp = Coordenadas.uv(g, c.getX(), c.getY(), Opciones.POR_DEFECTO.conTira(Tira.pesos(1, 8, 1))).v();
        comprueba("la tira cambia la v de un punto del cuerpo y no la pertenencia", Math.abs(vd - vp) > 0.05 && g.mascara().contiene(c.getX(), c.getY()), String.format("v = %.3f / %.3f", vd, vp));
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
