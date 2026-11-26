package geometria.composicion;

import pintado.Plotter;
import coordenadas.Solape;
import color.Mezcla;
import color.Texturas;
import color.Combinacion;
import color.Degradado;
import coordenadas.Opciones;
import coordenadas.Costura;
import coordenadas.Cortes;
import coordenadas.Tira;
import coordenadas.Coordenadas;
import forma.Gota;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Pruebas de la composición: (1) las tres composiciones construidas con la API; (2) reglas y errores de la API; (3) geometría (discos de los nodos, tapas elípticas exactas,
 * anillo de dos nodos, quiebros); (4) coherencia de contiene/sdf y del suavizado; (5) invalidación al modificar.
 */
public class GrafoPruebas {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        System.out.printf("%-78s %s%s%n", n, ok ? "ok   " : "FALLA", d.isEmpty() ? "" : "  " + d);
    }

    static final int W = 420, H = 420, FONDO = 0xFFF4F1EA;

    // ---------- las tres composiciones con la API ----------

    /** Mezcla del color c con blanco (35 %). */
    static int aclara(int c) {
        int r = 0xFF000000;
        for (int s = 16; s >= 0; s -= 8) r |= (int) Math.round(255 * 0.65 + ((c >> s) & 255) * 0.35) << s;
        return r;
    }

    public static Grafo cadena() {
        Grafo c = new Grafo();
        var n0 = c.nodo(60, 330, 14).centro(aclara(0xFFE8523A)).sectores(0xFFE8523A);
        var n1 = c.nodo(150, 210, 30).centro(aclara(0xFFF2A33A)).lados(0xFFE8523A, 0xFF2D6CDF);
        var n2 = c.nodo(260, 190, 24).centro(aclara(0xFF4FA36B)).lados(0xFFF2A33A, 0xFF1FA39A);
        var n3 = c.nodo(360, 80, 12).centro(aclara(0xFF3A7FD6)).sectores(0xFF3A7FD6);
        c.arista(n0, n1);
        c.arista(n1, n2);
        c.arista(n2, n3);
        return c;
    }

    public static Grafo ciclo() {
        Grafo c = new Grafo();
        var a = c.nodo(210, 70, 22).centro(aclara(0xFFE8523A)).lados(0xFFE8523A, 0xFF2D6CDF);
        var b = c.nodo(350, 300, 30).centro(aclara(0xFF4FA36B)).lados(0xFFF2A33A, 0xFF8E44AD);
        var d = c.nodo(70, 320, 18).centro(aclara(0xFF3A7FD6)).lados(0xFFD7263D, 0xFF1FA39A);
        c.arista(a, b);
        c.arista(b, d);
        c.arista(d, a);
        return c;
    }

    public static Grafo estrella(double suave) {
        Grafo c = new Grafo().suavizado(suave);
        var centro = c.nodo(210, 225, 34).centro(aclara(0xFFF2A33A)).sectores(0xFFD7263D, 0xFF2D6CDF, 0xFFF2C14E);
        double[] ang = { -100, 20, 140 }, dist = { 150, 160, 150 }, curva = { 28, -25, 30 }, rr = { 12, 15, 10 };
        int[] col = { 0xFFE8523A, 0xFF4FA36B, 0xFF3A7FD6 };
        for (int k = 0; k < 3; k++) {
            double a = Math.toRadians(ang[k]);
            var punta = c.nodo(210 + Math.cos(a) * dist[k], 225 + Math.sin(a) * dist[k], rr[k])
                    .color(col[k]).sectores(col[k]);
            double d = dist[k] / 3, b = Math.toRadians(ang[k] + 180 + curva[k]);
            c.arista(centro, punta).tiradores(null, new Gota.Vec(Math.cos(b) * d, Math.sin(b) * d));
        }
        return c;
    }

    static BufferedImage pinta(Grafo c) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        int[] d = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        java.util.Arrays.fill(d, FONDO);
        Plotter.raster(img).plot(c);
        return img;
    }

    static int[] datos(BufferedImage i) { return ((DataBufferInt) i.getRaster().getDataBuffer()).getData(); }

    /** Las tres composiciones con la API (sin prototipo de referencia: ya no hay otro modelo de color con el que compararlas). */
    static void galeria() throws Exception {
        Grafo[] cs = { cadena(), ciclo(), estrella(18) };
        String[] n = { "cadena", "ciclo O", "estrella Y suave" };
        BufferedImage fila = new BufferedImage(W * 3, H, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 3; i++) {
            long t0 = System.nanoTime();
            BufferedImage im = pinta(cs[i]);
            System.out.printf("   render %s: %.0f ms%n", n[i], (System.nanoTime() - t0) / 1e6);
            fila.getGraphics().drawImage(im, i * W, 0, null);
        }
        ImageIO.write(fila, "png", new File("composicion.png"));
        comprueba("las tres composiciones se renderizan (composicion.png)", true, "");
    }

    // ---------- reglas y errores ----------

    static boolean lanza(Class<? extends Throwable> t, Runnable r) {
        try { r.run(); } catch (Throwable e) { return t.isInstance(e); }
        return false;
    }

    static void reglas() {
        comprueba("un lazo sin los dos tiradores se rechaza al construir", lanza(IllegalStateException.class, () -> {
            Grafo c = new Grafo();
            var a = c.nodo(0, 0, 10);
            c.arista(a, a).tiradores(new Gota.Vec(50, 0), null);
            c.gotas();
        }), "");
        comprueba("nodo de otra composición se rechaza", lanza(IllegalArgumentException.class, () -> {
            Grafo c1 = new Grafo();
            Grafo c2 = new Grafo();
            c1.arista(c1.nodo(0, 0, 5), c2.nodo(10, 0, 5));
        }), "");
        comprueba("nodo sin aristas se rechaza", lanza(IllegalStateException.class, () -> {
            Grafo c = new Grafo();
            c.arista(c.nodo(0, 0, 5), c.nodo(10, 0, 5));
            c.nodo(50, 50, 5);
            c.gotas();
        }), "");
        comprueba("sectores con número distinto al de ramas se rechaza", lanza(IllegalStateException.class, () -> {
            Grafo c = new Grafo();
            var a = c.nodo(0, 0, 10).sectores(1, 2, 3); var b = c.nodo(100, 0, 10);
            c.arista(a, b);
            c.gotas();
        }), "");
        String msg = "";
        try {
            Grafo c = new Grafo();
            var a = c.nodo(0, 0, 10).sectores(1, 2, 3); var b = c.nodo(100, 0, 10); var d = c.nodo(0, 100, 10);
            c.arista(a, b); c.arista(a, d);
            c.gotas();
        } catch (IllegalStateException e) { msg = e.getMessage(); }
        comprueba("el error de sectores dice cuántas ramas hay, en qué orden y hacia qué nodos", msg.contains("recibió 3 colores") && msg.contains("2 ramas")
                && msg.contains("ángulo de salida creciente") && msg.contains("1.ª sale a -45° (arista hacia el nodo 1)") && msg.contains("2.ª sale a 135° (arista hacia el nodo 2)"), msg);
        comprueba("lados() en un nodo de grado 1 se rechaza", lanza(IllegalStateException.class, () -> {
            Grafo c = new Grafo();
            var a = c.nodo(0, 0, 10).lados(1, 2); var b = c.nodo(100, 0, 10);
            c.arista(a, b);
            c.gotas();
        }), "");
    }

    // ---------- geometría ----------

    static boolean unionContiene(Grafo c, double x, double y) { return c.contiene(x, y); }

    static void discos(String nombre, Grafo c) {
        boolean todos = true, unidos = true;
        for (Grafo.Nodo n : c.nodos()) {
            if (c.grado(n) == 1) continue;
            for (Grafo.Arista e : c.aristas()) {
                if (e.a() != n && e.b() != n) continue;
                Gota g = c.gota(e);
                for (int s = 0; s < 72; s++) {
                    double a = Math.toRadians(s * 5);
                    for (double f : new double[] { 0.97, 0.5 }) {
                        double x = n.x() + Math.cos(a) * n.radio() * f, y = n.y() + Math.sin(a) * n.radio() * f;
                        if (!g.mascara().contiene(x, y)) todos = false;
                        if (!c.contiene(x, y)) unidos = false;
                    }
                }
            }
        }
        comprueba(nombre + ": el disco de cada nodo con ramas está entero en cada gota incidente", todos, "");
        comprueba(nombre + ": el disco de cada nodo está dentro de la unión", unidos, "");
    }

    static void tapas(String nombre, Grafo c) {
        for (Grafo.Arista e : c.aristas()) {
            for (int lado = 0; lado < 2; lado++) {
                Grafo.Nodo n = lado == 0 ? e.a() : e.b();
                if (c.grado(n) != 1) continue;
                Gota g = c.gota(e);
                double t0 = lado == 0 ? 0 : 1, t1 = lado == 0 ? 1e-4 : 1 - 1e-4;
                Point2D p0 = g.centro(t0), p1 = g.centro(t1);
                double l = Math.hypot(p1.getX() - p0.getX(), p1.getY() - p0.getY());
                double tx = (p1.getX() - p0.getX()) / l, ty = (p1.getY() - p0.getY()) / l;   // hacia el cuerpo
                double nx = ty, ny = -tx, lam = 1;
                boolean dentro = true, fuera = true;
                for (int s = 0; s < 90; s++) {
                    double th = Math.PI / 2 + Math.PI * s / 89.0;
                    if (Math.cos(th) > -0.5) continue;
                    double ca = Math.cos(th) * lam * n.radio(), sa = Math.sin(th) * n.radio();
                    for (double f : new double[] { 0.985, 1.015 }) {
                        double x = n.x() + tx * ca * f + nx * sa * f, y = n.y() + ty * ca * f + ny * sa * f;
                        boolean in = c.contiene(x, y);
                        if (f < 1 && !in) dentro = false;
                        if (f > 1 && in) fuera = false;
                    }
                }
                comprueba(nombre + ": tapa del nodo " + n.x() + "," + n.y() + " es el círculo exacto", dentro && fuera, "");
            }
        }
    }

    static void anillo() {
        Grafo c = new Grafo();
        var a = c.nodo(100, 200, 25).color(0xFFE8523A);
        var b = c.nodo(300, 200, 25).color(0xFF3A7FD6);
        c.arista(a, b);
        c.arista(b, a);
        boolean ok = c.gotas().size() == 2;
        comprueba("anillo de dos nodos: se construye con dos aristas paralelas", ok, "");
        comprueba("anillo: tiene agujero en el centro", !c.contiene(200, 200), "");
        comprueba("anillo: las dos mitades están pintadas (arriba y abajo)", c.contiene(200, 150) && c.contiene(200, 250), "");
        discos("anillo", c);
        double qa = c.quiebro(a), qb = c.quiebro(b);
        comprueba("anillo: quiebro del contorno < 1° en los dos nodos", qa < 1 && qb < 1, String.format("%.2f° / %.2f°", qa, qb));
    }

    // ---------- Topografías: orden del recorrido, v global y solape por s ----------

    /** Textura que codifica v en el canal rojo: permite leer la v con que se pinta cada píxel. */
    static final Texturas.Textura V_ROJO = (u, v) -> 0xFF000000 | ((int) Math.round(Math.max(0, Math.min(1, v)) * 255) << 16);

    /** Rojo (v · 255) del píxel bajo el punto, o -1 si no está pintado. */
    static int rojo(BufferedImage img, java.awt.geom.Point2D p) {
        int x = (int) Math.floor(p.getX()), y = (int) Math.floor(p.getY());
        int c = img.getRGB(x, y);
        return (c >>> 24) < 200 ? -1 : (c >> 16) & 255;
    }

    /** Pinta con la textura y deja el grafo como estaba (sin textura). */
    public static void pintaConTextura(Grafo c, BufferedImage im, Texturas.Textura tex, Opciones op) {
        Plotter.raster(im).plot(c.textura(tex), op);
        c.sinTextura();
    }

    /** Igual, combinada con el color lateral. */
    public static void pintaConTextura(Grafo c, BufferedImage im, Texturas.Textura tex, Combinacion.Modo modo, Opciones op) {
        Plotter.raster(im).plot(c.textura(tex, modo), op);
        c.sinTextura();
    }

    static BufferedImage conV(Grafo c, Opciones op) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        pintaConTextura(c, img, V_ROJO, op);
        return img;
    }

    /** Salto de v (en rojo) entre el final de la gota a y el principio de la gota b, medidos a 3 px del nodo sobre la espina. */
    static int saltoV(BufferedImage img, Gota a, Gota b) {
        double da = 3 / a.longitud(), db = 3 / b.longitud();
        int ra = rojo(img, a.centro(1 - da)), rb = rojo(img, b.centro(db));
        return ra < 0 || rb < 0 ? 999 : Math.abs(ra - rb);
    }

    static void topografias() {
        Opciones op = Opciones.POR_DEFECTO;
        // Tira: 0 1 2 3
        Grafo ch = cadena();
        List<Grafo.Arista> es = ch.aristas();
        double acum = 0;
        boolean ord = ch.orden(ch.nodos().get(0)) == 0;
        for (int i = 0; i < es.size(); i++) {
            acum += ch.gota(es.get(i)).longitud();
            ord &= Math.abs(ch.orden(ch.nodos().get(i + 1)) - acum) < 1e-9 && !ch.cierra(es.get(i));
        }
        comprueba("tira: s de cada nodo = longitud acumulada desde el 0 y ningún cierre", ord, "");
        boolean cont = Math.abs(ch.cortes(es.get(0), op).c1() - 0.25) < 1e-9 && Math.abs(ch.cortes(es.get(2), op).c3() - 0.75) < 1e-9;
        for (int i = 0; i + 1 < es.size(); i++) cont &= Math.abs(ch.cortes(es.get(i), op).c3() - ch.cortes(es.get(i + 1), op).c1()) < 1e-9;
        comprueba("tira: la v global va de 1/4 a 3/4 y cada gota empieza donde acaba la anterior", cont, "");
        BufferedImage imT = conV(ch, op);
        int sj = Math.max(saltoV(imT, ch.gotas().get(0), ch.gotas().get(1)), saltoV(imT, ch.gotas().get(1), ch.gotas().get(2)));
        comprueba("tira: la v pintada no salta en los nodos (máx. salto <= 6/255)", sj <= 6, "salto " + sj);

        // Reparto POR_GOTA: cada gota vuelve a usar su tira, v se reinicia en cada arista y el orden no cambia
        Grafo pg = cadena().reparto(Grafo.Reparto.POR_GOTA);
        boolean tiras = true;
        for (int i = 0; i < 3; i++) {
            Cortes kk = pg.cortes(pg.aristas().get(i), op);
            tiras &= Math.abs(kk.c1() - 0.25) < 1e-9 && Math.abs(kk.c3() - 0.75) < 1e-9 && !kk.porLongitud();
        }
        comprueba("reparto POR_GOTA: cada gota usa los cortes de su tira (1/4, 3/4) y la v por t", tiras, "");
        BufferedImage imG = conV(pg, op);
        int sg = Math.min(saltoV(imG, pg.gotas().get(0), pg.gotas().get(1)), saltoV(imG, pg.gotas().get(1), pg.gotas().get(2)));
        comprueba("reparto POR_GOTA: la v pintada salta en los nodos (se reinicia en cada arista)", sg > 60, "salto " + sg);
        comprueba("reparto POR_GOTA: el orden (s) es el mismo", Math.abs(pg.orden(pg.nodos().get(3)) - ch.orden(ch.nodos().get(3))) < 1e-9, "");
        BufferedImage imGc = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB), imG0 = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Plotter.raster(imGc).plot(pg);
        Plotter.raster(imG0).plot(ch);
        int difMax = 0;
        for (int py = 0; py < H; py++) for (int px = 0; px < W; px++) {
            int c1 = imGc.getRGB(px, py), c2 = imG0.getRGB(px, py);
            for (int sh = 0; sh <= 24; sh += 8) difMax = Math.max(difMax, Math.abs(((c1 >> sh) & 255) - ((c2 >> sh) & 255)));
        }
        comprueba("el reparto apenas cambia el color sin textura (el degradado va por longitud en GLOBAL y por t en POR_GOTA)", difMax <= 6, "dif. máx. " + difMax + "/255");

        // La misma tira con las aristas escritas al revés: mismo orden, mismos cortes, las gotas van en el sentido del recorrido
        Grafo rv = new Grafo();
        var r0 = rv.nodo(60, 330, 14).color(0xFFE8523A);
        var r1 = rv.nodo(150, 210, 30).color(0xFFF2A33A);
        var r2 = rv.nodo(260, 190, 24).color(0xFF4FA36B);
        var r3 = rv.nodo(360, 80, 12).color(0xFF3A7FD6);
        rv.arista(r1, r0); rv.arista(r2, r1); rv.arista(r3, r2);
        boolean mismo = true;
        for (int i = 0; i < 3; i++) {
            mismo &= Math.abs(rv.cortes(rv.aristas().get(i), op).c1() - ch.cortes(es.get(i), op).c1()) < 1e-9;
            mismo &= rv.gota(rv.aristas().get(i)).centro(0).distance(ch.nodos().get(i).x(), ch.nodos().get(i).y()) < 1e-9;
        }
        comprueba("tira escrita al revés: misma v global y la gota va del nodo 0 hacia fuera", mismo, "");
        comprueba("tira escrita al revés: el contorno sigue sin quebrarse (< 0.5°)", rv.quiebro(r1) < 0.5 && rv.quiebro(r2) < 0.5,
                  String.format("%.2f° / %.2f°", rv.quiebro(r1), rv.quiebro(r2)));
        BufferedImage imR = conV(rv, op);
        int sr = Math.max(saltoV(imR, rv.gotas().get(0), rv.gotas().get(1)), saltoV(imR, rv.gotas().get(1), rv.gotas().get(2)));
        comprueba("tira escrita al revés: la v pintada no salta en los nodos", sr <= 6, "salto " + sr);

        // Círculo: 0 1 2 Z0
        Grafo ci = ciclo();
        List<Grafo.Arista> ce = ci.aristas();
        comprueba("círculo: solo la última arista es un cierre (Z0)", !ci.cierra(ce.get(0)) && !ci.cierra(ce.get(1)) && ci.cierra(ce.get(2)), "");
        Cortes c0 = ci.cortes(ce.get(0), op), c2 = ci.cortes(ce.get(2), op);
        comprueba("círculo: el cuerpo usa la banda [1/4, 3/4] de la textura y la v queda en una costura al cerrar", Math.abs(c0.c1() - 0.25) < 1e-9 && Math.abs(c2.c3() - 0.75) < 1e-9, String.format("%.4f %.4f", c0.c1(), c2.c3()));
        BufferedImage imC = conV(ci, op);
        int sc = Math.max(saltoV(imC, ci.gotas().get(0), ci.gotas().get(1)), saltoV(imC, ci.gotas().get(1), ci.gotas().get(2)));
        comprueba("círculo: la v pintada no salta en los nodos que no son el cierre", sc <= 6, "salto " + sc);

        // Y: tronco 0 M y dos ramas M N1, M N2
        Grafo y = new Grafo();
        var y0 = y.nodo(60, 330, 14).color(0xFFE8523A);
        var ym = y.nodo(180, 220, 28).centro(0xFFF2A33A).sectores(0xFFD7263D, 0xFF2D6CDF, 0xFFF2C14E);
        var y1 = y.nodo(330, 90, 12).color(0xFF3A7FD6);
        var y2 = y.nodo(340, 330, 14).color(0xFF4FA36B);
        var tronco = y.arista(y0, ym);
        var rama1 = y.arista(ym, y1);
        var rama2 = y.arista(ym, y2);
        double sM = y.orden(ym);
        boolean mismaS = Math.abs(sM - y.gota(tronco).longitud()) < 1e-9
                && Math.abs(y.orden(y1) - (sM + y.gota(rama1).longitud())) < 1e-9 && Math.abs(y.orden(y2) - (sM + y.gota(rama2).longitud())) < 1e-9;
        comprueba("Y: cada rama sigue contando desde la s del cruce", mismaS, "");
        comprueba("Y: las dos ramas empiezan en la v con que acaba el tronco",
                  Math.abs(y.cortes(rama1, op).c1() - y.cortes(tronco, op).c3()) < 1e-9 && Math.abs(y.cortes(rama2, op).c1() - y.cortes(tronco, op).c3()) < 1e-9, "");
        BufferedImage imY = conV(y, op);
        int sy = Math.max(saltoV(imY, y.gota(tronco), y.gota(rama1)), saltoV(imY, y.gota(tronco), y.gota(rama2)));
        comprueba("Y: la v pintada no salta del tronco a ninguna rama", sy <= 8, "salto " + sy);

        // Y lobulada sin palo (el 8): dos pétalos 0 a b Z0 y 0 c d Z0 con el nodo 0 de grado 4
        Grafo o = new Grafo();
        var n0 = o.nodo(210, 200, 16).centro(0xFFF2A33A).sectores(0xFFD7263D, 0xFF2D6CDF, 0xFFF2C14E, 0xFF4FA36B);
        var pa = o.nodo(110, 100, 16).color(0xFFE8523A);
        var pb = o.nodo(110, 300, 16).color(0xFF8E44AD);
        var pc = o.nodo(310, 300, 16).color(0xFF3A7FD6);
        var pd = o.nodo(310, 100, 16).color(0xFF1FA39A);
        o.arista(n0, pa); o.arista(pa, pb); var cierra1 = o.arista(pb, n0);
        o.arista(n0, pc); o.arista(pc, pd); var cierra2 = o.arista(pd, n0);
        boolean cierres = true;
        for (Grafo.Arista e : o.aristas()) cierres &= o.cierra(e) == (e == cierra1 || e == cierra2);
        comprueba("8 (dos pétalos en el nodo 0): los dos pétalos terminan con un cierre Z0", cierres && o.orden(n0) == 0, "");
        boolean sinError = true;
        try {
            for (Solape sl : Solape.values()) { BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB); pintaConTextura(o, im, V_ROJO, op.conSolape(sl)); }
        } catch (RuntimeException ex) { sinError = false; }
        comprueba("8: se pinta con PLANO y con TRAZO", sinError, "");

        // Solape por s: dos gotas que se cruzan sin nodo en el cruce (círculo que se cruza) y TRAZO deja encima la de mayor s
        Grafo x = new Grafo();
        var x0 = x.nodo(80, 100, 20).color(0xFFE8523A);
        var x1 = x.nodo(340, 300, 20).color(0xFF2D6CDF);
        var x2 = x.nodo(340, 100, 20).color(0xFF4FA36B);
        var x3 = x.nodo(80, 300, 20).color(0xFFF2A33A);
        x.arista(x0, x1); x.arista(x1, x2); x.arista(x2, x3);
        Gota gx0 = x.gotas().get(0), gx2 = x.gotas().get(2);
        Grafo.Arista ex0 = x.aristas().get(0);
        Grafo.Arista ex2 = x.aristas().get(2);
        Cortes kx0 = x.cortes(ex0, op), kx2 = x.cortes(ex2, op);
        Opciones ox0 = op.conTira(Tira.fija(kx0.c1(), kx0.c3())), ox2 = op.conTira(Tira.fija(kx2.c1(), kx2.c3()));
        BufferedImage ixT = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB), ixP = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        pintaConTextura(x, ixT, V_ROJO, op.conSolape(Solape.TRAZO));
        pintaConTextura(x, ixP, V_ROJO, op.conSolape(Solape.PLANO));
        int pruebas = 0, aciertos = 0, distintos = 0;
        for (int py = 0; py < H; py += 3)
            for (int px = 0; px < W; px += 3) {
                double qx = px + 0.5, qy = py + 0.5;
                if (gx0.distancia(qx, qy) > -4 || gx2.distancia(qx, qy) > -4) continue;       // dentro de las dos gotas
                double v0 = Coordenadas.uv(gx0, qx, qy, ox0.conSolape(Solape.TRAZO)).v(), v2 = Coordenadas.uv(gx2, qx, qy, ox2.conSolape(Solape.TRAZO)).v();
                if (Math.abs(v0 - v2) < 0.05) continue;
                pruebas++;
                int r = (ixT.getRGB(px, py) >> 16) & 255;
                if (Math.abs(r - Math.max(v0, v2) * 255) <= 4) aciertos++;
                if (Math.abs(((ixP.getRGB(px, py) >> 16) & 255) - r) > 4) distintos++;
            }
        comprueba("cruce de dos tramos sin nodo: con TRAZO manda siempre el de mayor s (mayor v) en el solape",
                  pruebas > 20 && aciertos == pruebas, String.format("%d de %d píxeles solapados; PLANO difiere en %d", aciertos, pruebas, distintos));
    }

    // ---------- funciones de color: Nodo.radio/radios y Arista.espina ----------

    static final int FA = 0xFFE8523A, FB = 0xFF2D6CDF, FC = 0xFF4FA36B;

    /** Cadena de tres nodos con un color por nodo; el nodo 0 de más, y la arista (n1, n2) escrita al derecho o al revés. */
    static Grafo trio(boolean alReves, java.util.function.Consumer<Grafo.Arista> sobreArista01) {
        Grafo c = new Grafo();
        var n0 = c.nodo(70, 300, 20).color(FA);
        var n1 = c.nodo(200, 170, 30).color(FB);
        var n2 = c.nodo(340, 90, 18).color(FC);
        var e = alReves ? c.arista(n1, n0) : c.arista(n0, n1);
        sobreArista01.accept(e);
        c.arista(n1, n2);
        return c;
    }

    static int difMax(BufferedImage a, BufferedImage b) {
        int[] p = datos(a), q = datos(b);
        int m = 0;
        for (int i = 0; i < p.length; i++)
            for (int sh = 0; sh <= 24; sh += 8) m = Math.max(m, Math.abs(((p[i] >>> sh) & 255) - ((q[i] >>> sh) & 255)));
        return m;
    }

    static void funcionesColor() {
        Mezcla m = Mezcla.LCH;                                  // la de Grafo por defecto
        BufferedImage base = pinta(trio(false, e -> {}));
        BufferedImage conFunciones = pinta(trio(false, e -> e.espina(Degradado.entre(FA, FB, m))));
        comprueba("la espina lineal explícita da lo mismo que sin ella", difMax(base, conFunciones) <= 1,
                "dif. máx. " + difMax(base, conFunciones));

        // una espina no lineal escrita al revés (b -> a) debe verse igual que al derecho
        Degradado onda = x -> m.mezclar(FA, FB, x * x);
        Degradado ondaAlReves = x -> onda.en(1 - x);
        BufferedImage recto = pinta(trio(false, e -> e.espina(onda)));
        BufferedImage revés = pinta(trio(true, e -> e.espina(ondaAlReves)));
        int d = difMax(recto, revés);
        comprueba("una espina no lineal escrita al revés se ve igual", d <= 2, "dif. máx. " + d);
        comprueba("una espina no lineal cambia la imagen", difMax(base, recto) > 8, "dif. máx. " + difMax(base, recto));

        // radio(f) simétrico: un anillo de color en cada nodo, sin tocar el color del centro
        Grafo c = new Grafo();
        var a = c.nodo(100, 210, 40).centro(FA).radio(x -> m.mezclar(FA, FB, Math.abs(Math.sin(x * Math.PI * 3))));
        var b = c.nodo(320, 210, 40).color(FC);
        c.arista(a, b);
        boolean construye = true;
        try { c.gotas(); } catch (RuntimeException e) { construye = false; }
        comprueba("radio(f) construye sin error", construye, "");
        BufferedImage anillos = pinta(c);
        comprueba("radio(f) pinta algo distinto del color plano",
                (datos(anillos)[210 * W + 100 + 20] != datos(anillos)[210 * W + 100]), "");

        // errores: función que no sale del color del centro; radios() fuera del grado 2
        Grafo mal = new Grafo();
        var n0 = mal.nodo(100, 200, 30).centro(FA).radio(x -> 0xFF000000);
        var n1 = mal.nodo(300, 200, 30).color(FB);
        mal.arista(n0, n1);
        comprueba("una función que no empieza en el color del centro lanza IllegalArgumentException",
                lanza(IllegalArgumentException.class, mal::gotas), "");
        Grafo y = new Grafo();
        var c0 = y.nodo(200, 200, 20).color(FA).radios(Degradado.constante(FA), Degradado.constante(FA));
        y.arista(c0, y.nodo(300, 200, 10).color(FB));
        y.arista(c0, y.nodo(100, 200, 10).color(FB));
        y.arista(c0, y.nodo(200, 100, 10).color(FB));
        comprueba("radios() en un nodo de grado 3 lanza IllegalStateException", lanza(IllegalStateException.class, y::gotas), "");
    }

    static void quiebros() {
        Grafo ch = cadena();
        double q1 = ch.quiebro(ch.nodos().get(1)), q2 = ch.quiebro(ch.nodos().get(2));
        comprueba("cadena: quiebro < 0.5° en las dos uniones", q1 < 0.5 && q2 < 0.5, String.format("%.2f° / %.2f°", q1, q2));
        Grafo sin = cadena().ajustarPendientes(false);
        double s1 = sin.quiebro(sin.nodos().get(1));
        comprueba("cadena: sin el ajuste el quiebro de la unión 1 es mayor que con él", s1 > q1 + 1, String.format("%.2f° frente a %.2f°", s1, q1));
        // el ajuste de pendientes es del radio: no mueve los centros (los tiradores siguen siendo d/3)
        Grafo con = cadena();
        Grafo sin2 = cadena().ajustarPendientes(false);
        boolean igual = true;
        for (int i = 0; i < con.gotas().size(); i++)
            for (double t : new double[] { 0.2, 0.5, 0.8 })
                igual &= con.gotas().get(i).centro(t).distance(sin2.gotas().get(i).centro(t)) < 1e-9;
        comprueba("cadena: el ajuste de pendientes no mueve los centros de las gotas", igual, "");
        // radio con un máximo en un nodo: cada gota es monótona y la pendiente en el nodo es 0
        Grafo mx = new Grafo();
        var m0 = mx.nodo(60, 200, 10); var m1 = mx.nodo(160, 200, 40); var m2 = mx.nodo(260, 200, 10);
        mx.arista(m0, m1); mx.arista(m1, m2);
        Gota ga = mx.gotas().get(0), gb = mx.gotas().get(1);
        boolean mono = true;
        for (int i = 0; i < 100; i++) { mono &= ga.rad((i + 1) / 100.0) >= ga.rad(i / 100.0) - 1e-9 && gb.rad((i + 1) / 100.0) <= gb.rad(i / 100.0) + 1e-9; }
        comprueba("radio con máximo en el nodo central: gotas monótonas, r' = 0 y quiebro < 0.5°",
                  mono && Math.abs(ga.drad(1)) < 1e-9 && Math.abs(gb.drad(0)) < 1e-9 && mx.quiebro(m1) < 0.5,
                  String.format("r'=%.4f / %.4f, quiebro %.2f°", ga.drad(1), gb.drad(0), mx.quiebro(m1)));
        Grafo ci = ciclo();
        boolean ok = true;
        StringBuilder sb = new StringBuilder();
        for (Grafo.Nodo n : ci.nodos()) { double q = ci.quiebro(n); sb.append(String.format("%.2f° ", q)); if (q >= 1) ok = false; }
        comprueba("ciclo O: quiebro < 1° en las tres uniones", ok, sb.toString());
    }

    // ---------- contiene / sdf / suavizado / invalidación ----------

    static void coherencia() {
        Random rnd = new Random(7);
        Grafo c = estrella(0);
        var bb = c.bounds();
        int mal = 0, n = 3000;
        for (int i = 0; i < n; i++) {
            double x = bb.getMinX() + rnd.nextDouble() * bb.getWidth(), y = bb.getMinY() + rnd.nextDouble() * bb.getHeight();
            double s = c.sdf(x, y);
            if (Math.abs(s) > 1e-6 && (s < 0) != c.contiene(x, y)) mal++;
        }
        comprueba("sdf < 0 equivale a contiene (unión dura), 3000 puntos", mal == 0, "discrepancias " + mal);

        Grafo suave = estrella(18);
        Grafo dura = estrella(0);
        int menos = 0, mas = 0, cambiaContiene = 0;
        for (int i = 0; i < n; i++) {
            double x = bb.getMinX() + rnd.nextDouble() * bb.getWidth(), y = bb.getMinY() + rnd.nextDouble() * bb.getHeight();
            double a = suave.sdf(x, y), b = dura.sdf(x, y);
            if (a > b + 1e-9) menos++;
            if (a < b - 1e-9) mas++;
            if (suave.contiene(x, y) != dura.contiene(x, y)) cambiaContiene++;
        }
        comprueba("el suavizado nunca aleja la sdf de la unión (solo la reduce en los cruces)", menos == 0 && mas > 0, "reducidos " + mas);
        comprueba("contiene no depende del suavizado (es la unión dura)", cambiaContiene == 0, "");
    }

    static void invalidacion() {
        Grafo c = new Grafo();
        var a = c.nodo(50, 50, 10).color(0xFFFF0000);
        var b = c.nodo(150, 50, 10).color(0xFF0000FF);
        c.arista(a, b);
        int n1 = c.gotas().size();
        boolean antes = c.contiene(150, 150);
        var d = c.nodo(150, 150, 10).color(0xFF00FF00);
        c.arista(b, d);
        comprueba("modificar la composición reconstruye las gotas", n1 == 1 && c.gotas().size() == 2, "");
        comprueba("tras añadir un nodo la unión cubre el nuevo extremo", !antes && c.contiene(150, 150), "");
        Grafo f = new Grafo();
        var centro = f.nodo(210, 225, 34).color(0xFFF2A33A);
        double a0 = Math.toRadians(-100);
        var punta = f.nodo(210 + Math.cos(a0) * 150, 225 + Math.sin(a0) * 150, 12).color(0xFFE8523A);
        f.arista(centro, punta);
        f.nodo(0, 0, 1);
        comprueba("nodo suelto (sin aristas) se rechaza al construir", lanza(IllegalStateException.class, f::gotas), "");
    }

    /** Un único nodo sin aristas es un disco: una gota de centros coincidentes y radios iguales. */
    static void discoSuelto() {
        final int ROJO = 0xFFD02020;
        Grafo c = new Grafo();
        c.nodo(150, 100, 40).color(ROJO);
        List<Gota> gs = c.gotas();
        comprueba("nodo suelto: una única gota", gs.size() == 1, "");
        boolean dentro = c.contiene(150, 100) && c.contiene(150 + 39, 100) && c.contiene(150, 100 - 39) && c.contiene(150 - 39, 100);
        boolean fuera = !c.contiene(150 + 41, 100) && !c.contiene(150, 100 + 41) && !c.contiene(150 + 30, 100 + 30);
        comprueba("nodo suelto: la silueta es el círculo de su radio", dentro && fuera, "");
        comprueba("nodo suelto: sdf del borde y del centro", Math.abs(c.sdf(150 + 40, 100)) < 1e-3 && Math.abs(c.sdf(150, 100) + 40) < 1e-3, "");
        int[] d = datos(pinta(c));
        comprueba("nodo suelto: se pinta de su color", d[100 * W + 150] == ROJO && d[100 * W + 150 + 30] == ROJO && d[10 * W + 10] == FONDO, "");
        Grafo sinColor = new Grafo();
        sinColor.nodo(150, 100, 40);
        comprueba("nodo suelto sin color: se pinta con el gris por defecto", datos(pinta(sinColor))[100 * W + 150] == 0xFFCCCCCC, "");
        comprueba("nodo suelto con lados() se rechaza", lanza(IllegalStateException.class, () -> {
            Grafo g = new Grafo();
            g.nodo(0, 0, 10).lados(0xFF000000, 0xFFFFFFFF);
            g.gotas();
        }), "");
        comprueba("nodo suelto con sectores() se rechaza", lanza(IllegalStateException.class, () -> {
            Grafo g = new Grafo();
            g.nodo(0, 0, 10).sectores(0xFF000000);
            g.gotas();
        }), "");
        comprueba("composición vacía se rechaza", lanza(IllegalStateException.class, () -> new Grafo().gotas()), "");
        comprueba("dos nodos sin aristas se rechazan", lanza(IllegalStateException.class, () -> {
            Grafo g = new Grafo();
            g.nodo(0, 0, 10);
            g.nodo(50, 0, 10);
            g.gotas();
        }), "");
        Grafo crece = new Grafo();
        var a = crece.nodo(50, 50, 10).color(ROJO);
        comprueba("nodo suelto: añadir una arista deja de ser disco", crece.gotas().size() == 1, "");
        crece.arista(a, crece.nodo(150, 50, 10).color(ROJO));
        comprueba("nodo suelto que recibe una arista se reconstruye como cinta", crece.gotas().size() == 1 && crece.contiene(100, 50) && crece.gotas().get(0).longitud() > 50, "");
    }

    static void tiradoresExplicitos() {
        Grafo auto = new Grafo();
        Grafo man = new Grafo();
        for (int k = 0; k < 2; k++) {
            Grafo c = k == 0 ? auto : man;
            var a = c.nodo(0, 0, 10).color(1);
            var b = c.nodo(200, 0, 10).color(2);
            var e = c.arista(a, b);
            if (k == 1) e.tiradores(new Gota.Vec(0, 80), new Gota.Vec(0, 80));
        }
        Point2D pa = auto.gotas().get(0).centro(0.5), pm = man.gotas().get(0).centro(0.5);
        comprueba("tiradores explícitos cambian la curva (y los automáticos dan un trazo recto)",
                Math.abs(pa.getY()) < 1e-9 && pm.getY() > 30, String.format("y automática %.3f, explícita %.1f", pa.getY(), pm.getY()));
    }


    // ---------- el ocho: dos lazos que comparten un único nodo ----------

    static Grafo ocho() {
        Grafo c = new Grafo();
        double s = Math.sqrt(0.5), L = 200;
        // Ramas del nodo en orden de ángulo: arriba-izq (-135°), arriba-der (-45°), abajo-der (45°), abajo-izq (135°).
        // Sectores: arriba = interior del lazo superior, derecha = exterior, abajo = interior del inferior, izquierda = exterior.
        var n = c.nodo(210, 210, 20).centro(aclara(0xFFF2A33A)).sectores(0xFFD7263D, 0xFF2D6CDF, 0xFFF28E2B, 0xFF1FA39A);
        // Lazo superior: sale hacia arriba-derecha (u) y vuelve llegando hacia abajo-derecha (v).
        c.arista(n, n).tiradores(new Gota.Vec(s * L, -s * L), new Gota.Vec(-s * L, -s * L));
        // Lazo inferior: sale con v y vuelve llegando con u, así cada final es tangente al inicio del otro.
        c.arista(n, n).tiradores(new Gota.Vec(s * L, s * L), new Gota.Vec(-s * L, s * L));
        return c;
    }

    static double[] dir(Point2D a, Point2D b) {
        double l = Math.hypot(b.getX() - a.getX(), b.getY() - a.getY());
        return new double[] { (b.getX() - a.getX()) / l, (b.getY() - a.getY()) / l };
    }

    static void pruebaOcho() throws Exception {
        Grafo c = ocho();
        var n = c.nodos().get(0);
        List<Gota> g = c.gotas();
        comprueba("ocho: dos gotas sobre un único nodo de grado 4",
                g.size() == 2 && c.grado(n) == 4, "");
        double[] fin1 = dir(g.get(0).centro(1 - 1e-4), g.get(0).centro(1));
        double[] ini2 = dir(g.get(1).centro(0), g.get(1).centro(1e-4));
        double[] fin2 = dir(g.get(1).centro(1 - 1e-4), g.get(1).centro(1));
        double[] ini1 = dir(g.get(0).centro(0), g.get(0).centro(1e-4));
        double d1 = fin1[0] * ini2[0] + fin1[1] * ini2[1], d2 = fin2[0] * ini1[0] + fin2[1] * ini1[1];
        comprueba("ocho: el final de cada lazo es tangente al inicio del otro (producto escalar ~ 1)",
                d1 > 0.99999 && d2 > 0.99999, String.format("%.7f / %.7f", d1, d2));
        comprueba("ocho: el disco del nodo está dentro de la unión", c.contiene(210, 210) && c.contiene(210 + 19, 210) && c.contiene(210, 210 - 19), "");
        comprueba("ocho: cada lazo tiene su agujero", !c.contiene(210, 150) && !c.contiene(210, 270), "");
        comprueba("ocho: la parte alta de cada lazo está pintada", c.contiene(210, 104) && c.contiene(210, 316), "");
        BufferedImage img = pinta(c);
        ImageIO.write(img, "png", new File("composicion_ocho.png"));
    }

    // ---------- opciones de pintado ----------

    static BufferedImage lienzo() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(datos(img), FONDO);
        return img;
    }

    static void opciones() {
        Grafo c = cadena();
        BufferedImage a = lienzo(), b = lienzo();
        Plotter.raster(a).plot(c);
        Plotter.raster(b).plot(c, Opciones.POR_DEFECTO);
        comprueba("render(dst) == render(dst, Opciones.POR_DEFECTO)", java.util.Arrays.equals(datos(a), datos(b)), "");

        Texturas.Textura tex = Texturas.referenciaLados();
        BufferedImage e = lienzo(), o = lienzo();
        pintaConTextura(c, e, tex, Opciones.POR_DEFECTO);
        pintaConTextura(c, o, tex, Opciones.POR_DEFECTO.conCostura(Costura.ORTOGONAL));
        int[] de = datos(e), dor = datos(o);
        int difs = 0;
        for (int i = 0; i < de.length; i++) if (de[i] != dor[i]) difs++;
        comprueba("la costura de las opciones llega a la composición con textura (ESPIGA != ORTOGONAL)", difs > 100, difs + " píxeles distintos");

        BufferedImage t = lienzo();
        pintaConTextura(c, t, tex, Opciones.POR_DEFECTO.conSolape(Solape.TRAZO));
        comprueba("la composición se renderiza con solape TRAZO", datos(t) != null && !java.util.Arrays.equals(datos(t), datos(lienzo())), "");
        comprueba("opciones nulas: NullPointerException", lanza(NullPointerException.class, () -> Plotter.raster(lienzo()).plot(c, (Opciones) null)), "");

        colorYTextura(c, tex);
    }

    /** Color y textura en una sola pasada (Combinacion.Modo). */
    static void colorYTextura(Grafo c, Texturas.Textura tex) {
        Opciones op = Opciones.POR_DEFECTO;
        BufferedImage r = lienzo(), t = lienzo();
        pintaConTextura(c, r, tex, Combinacion.Modo.REPLACE, op);
        pintaConTextura(c, t, tex, op);
        comprueba("render(tex, REPLACE) == render(tex)", java.util.Arrays.equals(datos(r), datos(t)), "");

        Texturas.Textura blanca = new Texturas.Textura() {
            @Override public double aspecto() { return 0.5; }
            @Override public int muestrea(double u, double v) { return 0xFFFFFFFF; }
        };
        BufferedImage m = lienzo(), solo = lienzo();
        pintaConTextura(c, m, blanca, Combinacion.Modo.MODULATE, op);
        Plotter.raster(solo).plot(c, op);
        int difs = 0;
        for (int i = 0; i < datos(m).length; i++) if (datos(m)[i] != datos(solo)[i]) difs++;
        comprueba("MODULATE con textura blanca == color solo", difs == 0, difs + " píxeles distintos");

        Texturas.Textura clara = new Texturas.Textura() {
            @Override public double aspecto() { return 0.5; }
            @Override public int muestrea(double u, double v) { return 0x00FFFFFF; }
        };
        BufferedImage d = lienzo();
        pintaConTextura(c, d, clara, Combinacion.Modo.DECAL, op);
        difs = 0;
        for (int i = 0; i < datos(d).length; i++) if (datos(d)[i] != datos(solo)[i]) difs++;
        comprueba("DECAL con textura transparente == color solo", difs == 0, difs + " píxeles distintos");

        BufferedImage mod = lienzo();
        pintaConTextura(c, mod, tex, Combinacion.Modo.MODULATE, op);
        comprueba("MODULATE con la referencia != REPLACE", !java.util.Arrays.equals(datos(mod), datos(r)), "");
        comprueba("modo nulo: NullPointerException", lanza(NullPointerException.class, () -> c.textura(tex, null)), "");
    }

    // ---------- altura de las aristas ----------

    /** Dos cintas que se cruzan en (210, 210): roja de (150,150) a (360,360) (s = 85 en el cruce) y azul de (360,60) a (60,360) (s = 212): sin altura gana la azul. */
    static Grafo cruz(Double alturaRoja, Double alturaAzul) {
        final int ROJO = 0xFFE8283A, AZUL = 0xFF2850E8;
        Grafo c = new Grafo().mezcla(Mezcla.RGB);
        var r = c.arista(c.nodo(150, 150, 14).centro(ROJO), c.nodo(360, 360, 14).centro(ROJO));
        var a = c.arista(c.nodo(360, 60, 14).centro(AZUL), c.nodo(60, 360, 14).centro(AZUL));
        if (alturaRoja != null) r.altura(x -> alturaRoja);
        if (alturaAzul != null) a.altura(x -> alturaAzul);
        return c;
    }

    static boolean rojoEncima(Grafo c) {
        BufferedImage im = lienzo();
        Plotter.raster(im).plot(c, Opciones.POR_DEFECTO.conSolape(Solape.TRAZO));
        int p = datos(im)[210 * W + 210];
        return ((p >> 16) & 255) > (p & 255);
    }

    static void alturas() {
        comprueba("altura: sin declarar manda la mayor s del recorrido (la azul, encima)", !rojoEncima(cruz(null, null)), "");
        comprueba("altura: la arista roja más alta queda encima", rojoEncima(cruz(1.0, 0.0)), "");
        comprueba("altura: la arista azul más alta queda encima", !rojoEncima(cruz(0.0, 1.0)), "");
        comprueba("altura: la declarada queda por encima de la no declarada (roja)", rojoEncima(cruz(-5.0, null)), "");
        comprueba("altura: con alturas iguales manda el orden del recorrido", !rojoEncima(cruz(2.0, 2.0)), "");
        Grafo c = new Grafo();
        var n0 = c.nodo(60, 60, 10); var n1 = c.nodo(200, 80, 10); var n2 = c.nodo(340, 60, 10);
        c.arista(n0, n1).altura(x -> 0);
        c.arista(n1, n2).altura(x -> 1);
        comprueba("altura: alturas distintas en un nodo compartido: IllegalArgumentException", lanza(IllegalArgumentException.class, () -> Plotter.raster(lienzo()).plot(c)), "");
        comprueba("altura: nula: NullPointerException", lanza(NullPointerException.class, () -> c.arista(n0, n2).altura(null)), "");
    }

    // ---------- paso y relajar ----------

    /** Cinta de cuatro nodos con una rama en el nodo 2 (grado 3). Las aristas se crean en este orden: 0-1, 1-2, 2-3, 2-4. */
    static Grafo cintaConRama() {
        Grafo c = new Grafo();
        double[][] p = { { 40, 330 }, { 150, 150 }, { 260, 250 }, { 370, 360 }, { 235, 430 } };
        var n = new Grafo.Nodo[p.length];
        for (int i = 0; i < p.length; i++) n[i] = c.nodo(p[i][0], p[i][1], 16).centro(0xFF4FA36B);
        c.arista(n[0], n[1]); c.arista(n[1], n[2]); c.arista(n[2], n[3]); c.arista(n[2], n[4]);
        return c;
    }

    /** Ángulo (grados) entre la tangente de llegada de la gota 1 y la de salida de la gota 2 en el nodo 2. */
    static double quiebroCinta(Grafo c) {
        var g = c.gotas();
        double e = 1e-4;
        Point2D a1 = g.get(1).centro(1 - e), a2 = g.get(1).centro(1), b1 = g.get(2).centro(0), b2 = g.get(2).centro(e);
        double ta = Math.atan2(a2.getY() - a1.getY(), a2.getX() - a1.getX()), tb = Math.atan2(b2.getY() - b1.getY(), b2.getX() - b1.getX());
        return Math.abs(Math.toDegrees(Math.atan2(Math.sin(tb - ta), Math.cos(tb - ta))));
    }

    static void pasos() {
        double sin = quiebroCinta(cintaConRama());
        comprueba("relajar: sin relajar la cinta quiebra en el nodo de grado 3", sin > 1, String.format("%.2f°", sin));
        Grafo r = cintaConRama().relajar();
        double con = quiebroCinta(r);
        comprueba("relajar: con relajar() la cinta es G1 en el nodo (< 0,05°)", con < 0.05, String.format("%.4f°", con));
        comprueba("relajar: idempotente (segunda llamada sin error ni cambios)", Math.abs(quiebroCinta(r.relajar()) - con) < 1e-9, "");
        // la rama (gota 3) sigue saliendo hacia su vecino: igual que sin relajar
        Point2D rs = r.gotas().get(3).centro(0), rt = r.gotas().get(3).centro(1e-4);
        Point2D ss = cintaConRama().gotas().get(3).centro(0), st = cintaConRama().gotas().get(3).centro(1e-4);
        comprueba("relajar: la rama sin pareja no cambia", rt.distance(st) < 1e-9 && rs.distance(ss) < 1e-9, "");
        comprueba("relajar con umbral 179°: no empareja nada (cinta a menos de 179°)", quiebroCinta(cintaConRama().relajar(179.9)) > 1, "");
        // paso manual a otra pareja, mismos efectos que relajar() en la pareja natural
        Grafo m = new Grafo();
        var a = m.nodo(0, 0, 10).centro(0xFF000000); var b = m.nodo(120, 80, 10).centro(0xFF000000); var d = m.nodo(240, 0, 10).centro(0xFF000000);
        var ab = m.arista(a, b); var bd = m.arista(b, d);
        comprueba("paso: arista de otra composición o que no llega: IllegalArgumentException", lanza(IllegalArgumentException.class, () -> b.paso(ab, m.arista(a, d))), "");
        comprueba("paso: la misma arista dos veces: IllegalArgumentException", lanza(IllegalArgumentException.class, () -> b.paso(ab, ab)), "");
        comprueba("paso: arista nula: NullPointerException", lanza(NullPointerException.class, () -> b.paso(ab, null)), "");
        b.paso(ab, bd);
        comprueba("paso: la arista ya emparejada en el nodo: IllegalArgumentException", lanza(IllegalArgumentException.class, () -> b.paso(ab, bd)), "");
    }

    static void rigidas() {
        Grafo c = new Grafo();
        var a = c.nodo(0, 0, 20).centro(0xFF000000); var b = c.nodo(30, 0, 20).centro(0xFF000000);
        var d = c.nodo(130, 0, 20).centro(0xFF000000); var e = c.nodo(150, 10, 20).centro(0xFF000000);
        var ab = c.arista(a, b); var bd = c.arista(b, d); var de = c.arista(d, e);
        List<Grafo.Arista> r = c.rigidas();
        comprueba("rigidas: detecta las gotas de trayectoria <= r1 + r2 (la 1.ª y la 3.ª)", r.size() == 2 && r.contains(ab) && r.contains(de) && !r.contains(bd), r.size() + " rígidas");
        Grafo s = new Grafo();
        var x = s.nodo(0, 0, 10).centro(0xFF000000); var y = s.nodo(60, 0, 10).centro(0xFF000000);
        s.arista(x, y);
        comprueba("rigidas: lista vacía si todas superan r1 + r2", s.rigidas().isEmpty(), "");
    }

    // ---------- alisar (G2) ----------

    static double curvaturaConSigno(Point2D a, Point2D b, Point2D c) {
        double cross = (b.getX() - a.getX()) * (c.getY() - a.getY()) - (b.getY() - a.getY()) * (c.getX() - a.getX());
        return 2 * cross / (a.distance(b) * b.distance(c) * c.distance(a));
    }

    /** Salto máximo de curvatura entre la llegada y la salida de las gotas consecutivas g[i] -> g[i+1] (cíclico si cierra), relativo a la curvatura máxima de la cadena. */
    static double saltoCurvatura(List<Gota> g, boolean cierra) {
        double e = 1e-3, salto = 0, kmax = 1e-9;
        for (int i = 0; i < g.size() - (cierra ? 0 : 1); i++) {
            Gota p = g.get(i), q = g.get((i + 1) % g.size());
            double kp = curvaturaConSigno(p.centro(1 - 2 * e), p.centro(1 - e), p.centro(1));
            double kq = curvaturaConSigno(q.centro(0), q.centro(e), q.centro(2 * e));
            salto = Math.max(salto, Math.abs(kp - kq));
            kmax = Math.max(kmax, Math.max(Math.abs(kp), Math.abs(kq)));
        }
        return salto / kmax;
    }

    static Grafo trebolDeNodos(int N) {
        Grafo c = new Grafo();
        var n = new Grafo.Nodo[N];
        for (int i = 0; i < N; i++) {
            double t = 2 * Math.PI * i / N;
            n[i] = c.nodo(210 + 48 * (Math.sin(t) + 2 * Math.sin(2 * t)), 210 + 48 * (Math.cos(t) - 2 * Math.cos(2 * t)), 8).centro(0xFF336699);
        }
        for (int i = 0; i < N; i++) c.arista(n[i], n[(i + 1) % N]);
        return c;
    }

    static void alisar() {
        double antes = saltoCurvatura(trebolDeNodos(30).gotas(), true);
        double despues = saltoCurvatura(trebolDeNodos(30).alisar().gotas(), true);
        comprueba("alisar: sin alisar la curvatura salta en los nodos (> 5 %)", antes > 0.05, String.format("%.1f %%", 100 * antes));
        comprueba("alisar: con alisar() la curvatura es continua en los nodos (< 1 %) en una cadena cerrada", despues < 0.01, String.format("%.3f %%", 100 * despues));
        // cadena abierta: curvatura nula en los extremos (spline natural) y continua dentro
        Grafo o = new Grafo();
        double[][] p = { { 40, 300 }, { 120, 150 }, { 220, 120 }, { 320, 220 }, { 400, 330 } };
        var n = new Grafo.Nodo[p.length];
        for (int i = 0; i < p.length; i++) n[i] = o.nodo(p[i][0], p[i][1], 10).centro(0xFF336699);
        for (int i = 0; i + 1 < p.length; i++) o.arista(n[i], n[i + 1]);
        var g = o.alisar().gotas();
        double e = 1e-3, k0 = curvaturaConSigno(g.get(0).centro(0), g.get(0).centro(e), g.get(0).centro(2 * e));
        comprueba("alisar: cadena abierta, curvatura nula en el extremo (natural)", Math.abs(k0) < 1e-3, String.format("%.5f", k0));
        comprueba("alisar: cadena abierta, curvatura continua en los nodos interiores (< 1 %)", saltoCurvatura(g, false) < 0.01, String.format("%.3f %%", 100 * saltoCurvatura(g, false)));
        // idempotente
        Grafo c = trebolDeNodos(30).alisar();
        Point2D m1 = c.gotas().get(5).centro(0.5);
        Point2D m2 = c.alisar().gotas().get(5).centro(0.5);
        comprueba("alisar: idempotente", m1.distance(m2) < 1e-9, "");
        // un tirador explícito queda fuera y manda
        Grafo x = new Grafo();
        var a = x.nodo(40, 200, 10).centro(0xFF336699); var b = x.nodo(160, 120, 10).centro(0xFF336699);
        var d = x.nodo(280, 200, 10).centro(0xFF336699); var f = x.nodo(400, 120, 10).centro(0xFF336699);
        x.arista(a, b); x.arista(b, d).tiradores(new Gota.Vec(10, 60), new Gota.Vec(-10, 60)); x.arista(d, f);
        Point2D sin = x.gotas().get(1).centro(0.5);
        Point2D con = x.alisar().gotas().get(1).centro(0.5);
        comprueba("alisar: la arista con tiradores explícitos no cambia", sin.distance(con) < 1e-9, "");
        // junto con relajar(): la cinta con rama sigue G1 en el nodo de grado 3 y renderiza
        Grafo r = cintaConRama().relajar().alisar();
        comprueba("alisar + relajar: la cinta es G1 en el nodo de grado 3 (< 0,05°)", quiebroCinta(r) < 0.05, String.format("%.4f°", quiebroCinta(r)));
        comprueba("alisar + relajar: renderiza", datos(pintaEn(r)) != null, "");
        Grafo anillo = new Grafo();
        var u = anillo.nodo(100, 100, 12).centro(0xFF336699); var w = anillo.nodo(300, 100, 12).centro(0xFF336699);
        anillo.arista(u, w); anillo.arista(w, u);
        Point2D ant = anillo.gotas().get(0).centro(0.5);
        comprueba("alisar: el anillo de dos nodos se deja como está", ant.distance(anillo.alisar().gotas().get(0).centro(0.5)) < 1e-9, "");
    }

    static BufferedImage pintaEn(Grafo c) {
        BufferedImage im = lienzo();
        Plotter.raster(im).plot(c);
        return im;
    }

    public static void main(String[] args) throws Exception {
        alisar();
        rigidas();
        pasos();
        alturas();
        reglas();
        anillo();
        quiebros();
        topografias();
        funcionesColor();
        discos("cadena", cadena());
        discos("ciclo O", ciclo());
        discos("estrella Y", estrella(18));
        tapas("cadena", cadena());
        tapas("estrella Y", estrella(18));
        coherencia();
        invalidacion();
        tiradoresExplicitos();
        discoSuelto();
        pruebaOcho();
        opciones();
        galeria();
        System.out.printf("%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }
}
