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
public class ComposicionPruebas {
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

    static Composicion cadena() {
        Composicion c = new Composicion();
        var n0 = c.nodo(60, 330, 14).centro(aclara(0xFFE8523A)).sectores(0xFFE8523A);
        var n1 = c.nodo(150, 210, 30).centro(aclara(0xFFF2A33A)).lados(0xFFE8523A, 0xFF2D6CDF);
        var n2 = c.nodo(260, 190, 24).centro(aclara(0xFF4FA36B)).lados(0xFFF2A33A, 0xFF1FA39A);
        var n3 = c.nodo(360, 80, 12).centro(aclara(0xFF3A7FD6)).sectores(0xFF3A7FD6);
        c.arista(n0, n1);
        c.arista(n1, n2);
        c.arista(n2, n3);
        return c;
    }

    static Composicion ciclo() {
        Composicion c = new Composicion();
        var a = c.nodo(210, 70, 22).centro(aclara(0xFFE8523A)).lados(0xFFE8523A, 0xFF2D6CDF);
        var b = c.nodo(350, 300, 30).centro(aclara(0xFF4FA36B)).lados(0xFFF2A33A, 0xFF8E44AD);
        var d = c.nodo(70, 320, 18).centro(aclara(0xFF3A7FD6)).lados(0xFFD7263D, 0xFF1FA39A);
        c.arista(a, b);
        c.arista(b, d);
        c.arista(d, a);
        return c;
    }

    static Composicion estrella(double suave) {
        Composicion c = new Composicion().suavizado(suave);
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

    static BufferedImage pinta(Composicion c) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        int[] d = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        java.util.Arrays.fill(d, FONDO);
        c.render(img);
        return img;
    }

    static int[] datos(BufferedImage i) { return ((DataBufferInt) i.getRaster().getDataBuffer()).getData(); }

    /** Las tres composiciones con la API (sin prototipo de referencia: ya no hay otro modelo de color con el que compararlas). */
    static void galeria() throws Exception {
        Composicion[] cs = { cadena(), ciclo(), estrella(18) };
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
            Composicion c = new Composicion();
            var a = c.nodo(0, 0, 10);
            c.arista(a, a).tiradores(new Gota.Vec(50, 0), null);
            c.gotas();
        }), "");
        comprueba("nodo de otra composición se rechaza", lanza(IllegalArgumentException.class, () -> {
            Composicion c1 = new Composicion(), c2 = new Composicion();
            c1.arista(c1.nodo(0, 0, 5), c2.nodo(10, 0, 5));
        }), "");
        comprueba("nodo sin aristas se rechaza", lanza(IllegalStateException.class, () -> {
            Composicion c = new Composicion();
            c.arista(c.nodo(0, 0, 5), c.nodo(10, 0, 5));
            c.nodo(50, 50, 5);
            c.gotas();
        }), "");
        comprueba("sectores con número distinto al de ramas se rechaza", lanza(IllegalStateException.class, () -> {
            Composicion c = new Composicion();
            var a = c.nodo(0, 0, 10).sectores(1, 2, 3); var b = c.nodo(100, 0, 10);
            c.arista(a, b);
            c.gotas();
        }), "");
        String msg = "";
        try {
            Composicion c = new Composicion();
            var a = c.nodo(0, 0, 10).sectores(1, 2, 3); var b = c.nodo(100, 0, 10); var d = c.nodo(0, 100, 10);
            c.arista(a, b); c.arista(a, d);
            c.gotas();
        } catch (IllegalStateException e) { msg = e.getMessage(); }
        comprueba("el error de sectores dice cuántas ramas hay, en qué orden y hacia qué nodos", msg.contains("recibió 3 colores") && msg.contains("2 ramas")
                && msg.contains("ángulo de salida creciente") && msg.contains("1.ª sale a -45° (arista hacia el nodo 1)") && msg.contains("2.ª sale a 135° (arista hacia el nodo 2)"), msg);
        comprueba("lados() en un nodo de grado 1 se rechaza", lanza(IllegalStateException.class, () -> {
            Composicion c = new Composicion();
            var a = c.nodo(0, 0, 10).lados(1, 2); var b = c.nodo(100, 0, 10);
            c.arista(a, b);
            c.gotas();
        }), "");
    }

    // ---------- geometría ----------

    static boolean unionContiene(Composicion c, double x, double y) { return c.contiene(x, y); }

    static void discos(String nombre, Composicion c) {
        boolean todos = true, unidos = true;
        for (Composicion.Nodo n : c.nodos()) {
            if (c.grado(n) == 1) continue;
            for (Composicion.Arista e : c.aristas()) {
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

    static void tapas(String nombre, Composicion c) {
        for (Composicion.Arista e : c.aristas()) {
            for (int lado = 0; lado < 2; lado++) {
                Composicion.Nodo n = lado == 0 ? e.a() : e.b();
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
        Composicion c = new Composicion();
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

    static BufferedImage conV(Composicion c, Opciones op) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        c.render(img, V_ROJO, op);
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
        Composicion ch = cadena();
        List<Composicion.Arista> es = ch.aristas();
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

        // La misma tira con las aristas escritas al revés: mismo orden, mismos cortes, las gotas van en el sentido del recorrido
        Composicion rv = new Composicion();
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
        Composicion ci = ciclo();
        List<Composicion.Arista> ce = ci.aristas();
        comprueba("círculo: solo la última arista es un cierre (Z0)", !ci.cierra(ce.get(0)) && !ci.cierra(ce.get(1)) && ci.cierra(ce.get(2)), "");
        Cortes c0 = ci.cortes(ce.get(0), op), c2 = ci.cortes(ce.get(2), op);
        comprueba("círculo: el cuerpo usa la banda [1/4, 3/4] de la textura y la v queda en una costura al cerrar", Math.abs(c0.c1() - 0.25) < 1e-9 && Math.abs(c2.c3() - 0.75) < 1e-9, String.format("%.4f %.4f", c0.c1(), c2.c3()));
        BufferedImage imC = conV(ci, op);
        int sc = Math.max(saltoV(imC, ci.gotas().get(0), ci.gotas().get(1)), saltoV(imC, ci.gotas().get(1), ci.gotas().get(2)));
        comprueba("círculo: la v pintada no salta en los nodos que no son el cierre", sc <= 6, "salto " + sc);

        // Y: tronco 0 M y dos ramas M N1, M N2
        Composicion y = new Composicion();
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
        Composicion o = new Composicion();
        var n0 = o.nodo(210, 200, 16).centro(0xFFF2A33A).sectores(0xFFD7263D, 0xFF2D6CDF, 0xFFF2C14E, 0xFF4FA36B);
        var pa = o.nodo(110, 100, 16).color(0xFFE8523A);
        var pb = o.nodo(110, 300, 16).color(0xFF8E44AD);
        var pc = o.nodo(310, 300, 16).color(0xFF3A7FD6);
        var pd = o.nodo(310, 100, 16).color(0xFF1FA39A);
        o.arista(n0, pa); o.arista(pa, pb); var cierra1 = o.arista(pb, n0);
        o.arista(n0, pc); o.arista(pc, pd); var cierra2 = o.arista(pd, n0);
        boolean cierres = true;
        for (Composicion.Arista e : o.aristas()) cierres &= o.cierra(e) == (e == cierra1 || e == cierra2);
        comprueba("8 (dos pétalos en el nodo 0): los dos pétalos terminan con un cierre Z0", cierres && o.orden(n0) == 0, "");
        boolean sinError = true;
        try {
            for (Solape sl : Solape.values()) { BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB); o.render(im, V_ROJO, op.conSolape(sl)); }
        } catch (RuntimeException ex) { sinError = false; }
        comprueba("8: se pinta con PLANO y con TRAZO", sinError, "");

        // Solape por s: dos gotas que se cruzan sin nodo en el cruce (círculo que se cruza) y TRAZO deja encima la de mayor s
        Composicion x = new Composicion();
        var x0 = x.nodo(80, 100, 20).color(0xFFE8523A);
        var x1 = x.nodo(340, 300, 20).color(0xFF2D6CDF);
        var x2 = x.nodo(340, 100, 20).color(0xFF4FA36B);
        var x3 = x.nodo(80, 300, 20).color(0xFFF2A33A);
        x.arista(x0, x1); x.arista(x1, x2); x.arista(x2, x3);
        Gota gx0 = x.gotas().get(0), gx2 = x.gotas().get(2);
        Composicion.Arista ex0 = x.aristas().get(0), ex2 = x.aristas().get(2);
        Cortes kx0 = x.cortes(ex0, op), kx2 = x.cortes(ex2, op);
        Opciones ox0 = op.conTira(Tira.fija(kx0.c1(), kx0.c3())), ox2 = op.conTira(Tira.fija(kx2.c1(), kx2.c3()));
        BufferedImage ixT = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB), ixP = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        x.render(ixT, V_ROJO, op.conSolape(Solape.TRAZO));
        x.render(ixP, V_ROJO, op.conSolape(Solape.PLANO));
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

    static void quiebros() {
        Composicion ch = cadena();
        double q1 = ch.quiebro(ch.nodos().get(1)), q2 = ch.quiebro(ch.nodos().get(2));
        comprueba("cadena: quiebro < 0.5° en las dos uniones", q1 < 0.5 && q2 < 0.5, String.format("%.2f° / %.2f°", q1, q2));
        Composicion sin = cadena().ajustarPendientes(false);
        double s1 = sin.quiebro(sin.nodos().get(1));
        comprueba("cadena: sin el ajuste el quiebro de la unión 1 es mayor que con él", s1 > q1 + 1, String.format("%.2f° frente a %.2f°", s1, q1));
        // el ajuste de pendientes es del radio: no mueve los centros (los tiradores siguen siendo d/3)
        Composicion con = cadena(), sin2 = cadena().ajustarPendientes(false);
        boolean igual = true;
        for (int i = 0; i < con.gotas().size(); i++)
            for (double t : new double[] { 0.2, 0.5, 0.8 })
                igual &= con.gotas().get(i).centro(t).distance(sin2.gotas().get(i).centro(t)) < 1e-9;
        comprueba("cadena: el ajuste de pendientes no mueve los centros de las gotas", igual, "");
        // radio con un máximo en un nodo: cada gota es monótona y la pendiente en el nodo es 0
        Composicion mx = new Composicion();
        var m0 = mx.nodo(60, 200, 10); var m1 = mx.nodo(160, 200, 40); var m2 = mx.nodo(260, 200, 10);
        mx.arista(m0, m1); mx.arista(m1, m2);
        Gota ga = mx.gotas().get(0), gb = mx.gotas().get(1);
        boolean mono = true;
        for (int i = 0; i < 100; i++) { mono &= ga.rad((i + 1) / 100.0) >= ga.rad(i / 100.0) - 1e-9 && gb.rad((i + 1) / 100.0) <= gb.rad(i / 100.0) + 1e-9; }
        comprueba("radio con máximo en el nodo central: gotas monótonas, r' = 0 y quiebro < 0.5°",
                  mono && Math.abs(ga.drad(1)) < 1e-9 && Math.abs(gb.drad(0)) < 1e-9 && mx.quiebro(m1) < 0.5,
                  String.format("r'=%.4f / %.4f, quiebro %.2f°", ga.drad(1), gb.drad(0), mx.quiebro(m1)));
        Composicion ci = ciclo();
        boolean ok = true;
        StringBuilder sb = new StringBuilder();
        for (Composicion.Nodo n : ci.nodos()) { double q = ci.quiebro(n); sb.append(String.format("%.2f° ", q)); if (q >= 1) ok = false; }
        comprueba("ciclo O: quiebro < 1° en las tres uniones", ok, sb.toString());
    }

    // ---------- contiene / sdf / suavizado / invalidación ----------

    static void coherencia() {
        Random rnd = new Random(7);
        Composicion c = estrella(0);
        var bb = c.bounds();
        int mal = 0, n = 3000;
        for (int i = 0; i < n; i++) {
            double x = bb.getMinX() + rnd.nextDouble() * bb.getWidth(), y = bb.getMinY() + rnd.nextDouble() * bb.getHeight();
            double s = c.sdf(x, y);
            if (Math.abs(s) > 1e-6 && (s < 0) != c.contiene(x, y)) mal++;
        }
        comprueba("sdf < 0 equivale a contiene (unión dura), 3000 puntos", mal == 0, "discrepancias " + mal);

        Composicion suave = estrella(18), dura = estrella(0);
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
        Composicion c = new Composicion();
        var a = c.nodo(50, 50, 10).color(0xFFFF0000);
        var b = c.nodo(150, 50, 10).color(0xFF0000FF);
        c.arista(a, b);
        int n1 = c.gotas().size();
        boolean antes = c.contiene(150, 150);
        var d = c.nodo(150, 150, 10).color(0xFF00FF00);
        c.arista(b, d);
        comprueba("modificar la composición reconstruye las gotas", n1 == 1 && c.gotas().size() == 2, "");
        comprueba("tras añadir un nodo la unión cubre el nuevo extremo", !antes && c.contiene(150, 150), "");
        Composicion f = new Composicion();
        var centro = f.nodo(210, 225, 34).color(0xFFF2A33A);
        double a0 = Math.toRadians(-100);
        var punta = f.nodo(210 + Math.cos(a0) * 150, 225 + Math.sin(a0) * 150, 12).color(0xFFE8523A);
        f.arista(centro, punta);
        f.nodo(0, 0, 1);
        comprueba("nodo suelto (sin aristas) se rechaza al construir", lanza(IllegalStateException.class, f::gotas), "");
    }

    static void tiradoresExplicitos() {
        Composicion auto = new Composicion(), man = new Composicion();
        for (int k = 0; k < 2; k++) {
            Composicion c = k == 0 ? auto : man;
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

    static Composicion ocho() {
        Composicion c = new Composicion();
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
        Composicion c = ocho();
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
        Composicion c = cadena();
        BufferedImage a = lienzo(), b = lienzo();
        c.render(a);
        c.render(b, Opciones.POR_DEFECTO);
        comprueba("render(dst) == render(dst, Opciones.POR_DEFECTO)", java.util.Arrays.equals(datos(a), datos(b)), "");

        Texturas.Textura tex = Texturas.referenciaLados();
        BufferedImage e = lienzo(), o = lienzo();
        c.render(e, tex, Opciones.POR_DEFECTO);
        c.render(o, tex, Opciones.POR_DEFECTO.conCostura(Costura.ORTOGONAL));
        int[] de = datos(e), dor = datos(o);
        int difs = 0;
        for (int i = 0; i < de.length; i++) if (de[i] != dor[i]) difs++;
        comprueba("la costura de las opciones llega a la composición con textura (ESPIGA != ORTOGONAL)", difs > 100, difs + " píxeles distintos");

        BufferedImage t = lienzo();
        c.render(t, tex, Opciones.POR_DEFECTO.conSolape(Solape.TRAZO));
        comprueba("la composición se renderiza con solape TRAZO", datos(t) != null && !java.util.Arrays.equals(datos(t), datos(lienzo())), "");
        comprueba("opciones nulas: NullPointerException", lanza(NullPointerException.class, () -> c.render(lienzo(), (Opciones) null)), "");

        colorYTextura(c, tex);
    }

    /** Color y textura en una sola pasada (ComposicionColor.Modo). */
    static void colorYTextura(Composicion c, Texturas.Textura tex) {
        Opciones op = Opciones.POR_DEFECTO;
        BufferedImage r = lienzo(), t = lienzo();
        c.render(r, tex, ComposicionColor.Modo.REPLACE, op);
        c.render(t, tex, op);
        comprueba("render(tex, REPLACE) == render(tex)", java.util.Arrays.equals(datos(r), datos(t)), "");

        Texturas.Textura blanca = new Texturas.Textura() {
            @Override public double aspecto() { return 0.5; }
            @Override public int muestrea(double u, double v) { return 0xFFFFFFFF; }
        };
        BufferedImage m = lienzo(), solo = lienzo();
        c.render(m, blanca, ComposicionColor.Modo.MODULATE, op);
        c.render(solo, op);
        int difs = 0;
        for (int i = 0; i < datos(m).length; i++) if (datos(m)[i] != datos(solo)[i]) difs++;
        comprueba("MODULATE con textura blanca == color solo", difs == 0, difs + " píxeles distintos");

        Texturas.Textura clara = new Texturas.Textura() {
            @Override public double aspecto() { return 0.5; }
            @Override public int muestrea(double u, double v) { return 0x00FFFFFF; }
        };
        BufferedImage d = lienzo();
        c.render(d, clara, ComposicionColor.Modo.DECAL, op);
        difs = 0;
        for (int i = 0; i < datos(d).length; i++) if (datos(d)[i] != datos(solo)[i]) difs++;
        comprueba("DECAL con textura transparente == color solo", difs == 0, difs + " píxeles distintos");

        BufferedImage mod = lienzo();
        c.render(mod, tex, ComposicionColor.Modo.MODULATE, op);
        comprueba("MODULATE con la referencia != REPLACE", !java.util.Arrays.equals(datos(mod), datos(r)), "");
        comprueba("modo nulo: NullPointerException", lanza(NullPointerException.class, () -> c.render(lienzo(), tex, (ComposicionColor.Modo) null, op)), "");
    }

    public static void main(String[] args) throws Exception {
        reglas();
        anillo();
        quiebros();
        topografias();
        discos("cadena", cadena());
        discos("ciclo O", ciclo());
        discos("estrella Y", estrella(18));
        tapas("cadena", cadena());
        tapas("estrella Y", estrella(18));
        coherencia();
        invalidacion();
        tiradoresExplicitos();
        pruebaOcho();
        opciones();
        galeria();
        System.out.printf("%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }
}
