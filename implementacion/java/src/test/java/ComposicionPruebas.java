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
        var n0 = c.nodo(60, 330, 14).centro(aclara(0xFFE8523A)).aspecto(2.4).sectores(0xFFE8523A);
        var n1 = c.nodo(150, 210, 30).centro(aclara(0xFFF2A33A)).lados(0xFFE8523A, 0xFF2D6CDF);
        var n2 = c.nodo(260, 190, 24).centro(aclara(0xFF4FA36B)).lados(0xFFF2A33A, 0xFF1FA39A);
        var n3 = c.nodo(360, 80, 12).centro(aclara(0xFF3A7FD6)).aspecto(0.45).sectores(0xFF3A7FD6);
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
        double[] lam = { 2.4, 0.5, 1.8 };
        int[] col = { 0xFFE8523A, 0xFF4FA36B, 0xFF3A7FD6 };
        for (int k = 0; k < 3; k++) {
            double a = Math.toRadians(ang[k]);
            var punta = c.nodo(210 + Math.cos(a) * dist[k], 225 + Math.sin(a) * dist[k], rr[k])
                    .color(col[k]).aspecto(lam[k]).sectores(col[k]);
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
        comprueba("aspecto != 1 en un nodo de grado 2 se rechaza", lanza(IllegalStateException.class, () -> {
            Composicion c = new Composicion();
            var a = c.nodo(0, 0, 10); var b = c.nodo(100, 0, 10).aspecto(2); var d = c.nodo(200, 0, 10);
            c.arista(a, b); c.arista(b, d);
            c.gotas();
        }), "");
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
        comprueba("lados() en un nodo de grado 1 se rechaza", lanza(IllegalStateException.class, () -> {
            Composicion c = new Composicion();
            var a = c.nodo(0, 0, 10).lados(1, 2); var b = c.nodo(100, 0, 10);
            c.arista(a, b);
            c.gotas();
        }), "");
        Composicion ch = cadena();
        List<Gota> g = ch.gotas();
        comprueba("cadena: grados de gota 12 / 6 / 12 (solo las terminales son elípticas)",
                g.get(0).esElipse() && !g.get(1).esElipse() && g.get(2).esElipse(), "");
        Composicion ci = ciclo();
        comprueba("ciclo O: las tres gotas circulares (grado 6)", ci.gotas().stream().noneMatch(Gota::esElipse), "");
        Composicion y = estrella(0);
        comprueba("estrella Y: tres gotas elípticas (las puntas), centro circular",
                y.gotas().stream().allMatch(Gota::esElipse) && y.grado(y.nodos().get(0)) == 3, "");
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
                        if (!g.contiene(x, y)) todos = false;
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
                double nx = ty, ny = -tx, lam = g.aspecto(t0);
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
                comprueba(nombre + ": tapa del nodo " + n.x() + "," + n.y() + " (λ=" + lam + ") es la elipse exacta", dentro && fuera, "");
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

    static void quiebros() {
        Composicion ch = cadena();
        double q1 = ch.quiebro(ch.nodos().get(1)), q2 = ch.quiebro(ch.nodos().get(2));
        comprueba("cadena: quiebro < 0.5° en las dos uniones", q1 < 0.5 && q2 < 0.5, String.format("%.2f° / %.2f°", q1, q2));
        Composicion sin = cadena().ajustarPendientes(false);
        double s1 = sin.quiebro(sin.nodos().get(1));
        comprueba("cadena: sin el ajuste el quiebro de la unión 1 es mayor que con él", s1 > q1 + 1, String.format("%.2f° frente a %.2f°", s1, q1));
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
        comprueba("ocho: dos gotas circulares sobre un único nodo de grado 4",
                g.size() == 2 && c.grado(n) == 4 && g.stream().noneMatch(Gota::esElipse), "");
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

    public static void main(String[] args) throws Exception {
        reglas();
        anillo();
        quiebros();
        discos("cadena", cadena());
        discos("ciclo O", ciclo());
        discos("estrella Y", estrella(18));
        tapas("cadena", cadena());
        tapas("estrella Y", estrella(18));
        coherencia();
        invalidacion();
        tiradoresExplicitos();
        pruebaOcho();
        galeria();
        System.out.printf("%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }
}
