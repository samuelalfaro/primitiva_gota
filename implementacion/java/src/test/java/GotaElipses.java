import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.Arrays;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Elipses (a = lambda·b). Oráculos: el algoritmo de círculos (lambda = 1 con el algoritmo de elipses forzado),
 * una solución analítica (barrido recto con radios constantes), fuerza bruta (unión de miles de elipses) y
 * una distancia a la frontera por muestreo denso.
 */
public class GotaElipses {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        System.out.printf("%-78s %s%s%n", n, ok ? "ok   " : "FALLA", d.isEmpty() ? "" : "  " + d);
    }

    static Point2D.Double p(double x, double y) { return new Point2D.Double(x, y); }
    static int[] datos(BufferedImage i) { return ((DataBufferInt) i.getRaster().getDataBuffer()).getData(); }

    public static void main(String[] args) throws Exception {
        distancia();
        equivalenciaConCirculos();
        barridoAnalitico();
        fuerzaBruta();
        degenerados();
        uvYRender();
        galeria();
        System.out.printf("%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }

    // ---------- distancia punto-elipse ----------

    static void distancia() {
        Random rnd = new Random(11);
        double peor = 0;
        for (int i = 0; i < 400; i++) {
            double a = 0.5 + rnd.nextDouble() * 40, b = 0.5 + rnd.nextDouble() * 40;
            double u = (rnd.nextDouble() - 0.5) * 120, v = (rnd.nextDouble() - 0.5) * 120;
            double mejor = Double.MAX_VALUE;
            int N = 200000;
            for (int k = 0; k < N; k++) {
                double th = 2 * Math.PI * k / N;
                mejor = Math.min(mejor, Math.hypot(a * Math.cos(th) - u, b * Math.sin(th) - v));
            }
            double signo = (u / a) * (u / a) + (v / b) * (v / b) < 1 ? -1 : 1;
            peor = Math.max(peor, Math.abs(Gota.distElipse(a, b, u, v) - signo * mejor));
        }
        comprueba("distancia punto-elipse (bisección) contra muestreo denso de la frontera (400 casos)", peor < 1e-5, String.format("peor error %.1e", peor));

        // puntos sobre los ejes y en el centro
        boolean ok = Math.abs(Gota.distElipse(10, 4, 0, 0) + 4) < 1e-12 && Math.abs(Gota.distElipse(10, 4, 13, 0) - 3) < 1e-12
                && Math.abs(Gota.distElipse(10, 4, 0, 7) - 3) < 1e-12 && Math.abs(Gota.distElipse(4, 10, 7, 0) - 3) < 1e-12
                && Math.abs(Gota.distElipse(5, 5, 8, 6) - 5) < 1e-12;
        comprueba("distancia en los ejes, en el centro y con radios iguales", ok, "");
    }

    // ---------- el algoritmo de elipses con lambda = 1 frente al de círculos ----------

    static Gota aleatoria(Random rnd, boolean forzar) {
        Point2D c1 = p(rnd.nextDouble() * 200, rnd.nextDouble() * 200), c2 = p(rnd.nextDouble() * 200, rnd.nextDouble() * 200);
        double r1 = rnd.nextInt(8) == 0 ? 0 : rnd.nextDouble() * 40, r2 = rnd.nextInt(8) == 0 ? 0 : rnd.nextDouble() * 40;
        double h = 0.5 + rnd.nextDouble() * 2.5;
        Gota.Vec v1 = new Gota.Vec((rnd.nextDouble() - 0.5) * 300, (rnd.nextDouble() - 0.5) * 300);
        Gota.Vec v2 = new Gota.Vec((rnd.nextDouble() - 0.5) * 300, (rnd.nextDouble() - 0.5) * 300);
        double tm = 0.05 + rnd.nextDouble() * 0.9, suav = rnd.nextInt(3) == 0 ? 1 : rnd.nextDouble();
        boolean medio = rnd.nextBoolean(), recta = rnd.nextBoolean();
        Gota.Perfil pf = medio ? Gota.Perfil.radioMedio(h * (r1 + r2) / 2, tm, suav) : Gota.Perfil.central(h);
        return aleatoriaCon(c1, r1, v1, c2, r2, v2, pf, recta, forzar);
    }

    static Gota aleatoriaCon(Point2D c1, double r1, Gota.Vec v1, Point2D c2, double r2, Gota.Vec v2, Gota.Perfil pf, boolean recta, boolean forzar) {
        if (forzar) pf = forzarElipses(pf);
        return recta ? Gota.lineal(c1, r1, c2, r2, pf) : Gota.curva(c1, r1, v1, c2, r2, v2, pf);
    }

    /** Perfil.forzarElipses es privado del paquete: se llama a través de reflexión mínima no hace falta, mismo paquete. */
    static Gota.Perfil forzarElipses(Gota.Perfil pf) { return pf.forzarElipses(); }

    static void equivalenciaConCirculos() {
        Random rnd = new Random(2024);
        int n = 0, difContiene = 0, difSdf = 0, difRho = 0;
        double peorSdf = 0, peorRho = 0;
        for (int gi = 0; gi < 120; gi++) {
            long semilla = rnd.nextLong();
            Gota gc = aleatoria(new Random(semilla), false), ge = aleatoria(new Random(semilla), true);
            if (!ge.esElipse() || gc.esElipse()) { comprueba("forzar elipses activa el algoritmo de elipses", false, ""); return; }
            var b = gc.bounds();
            Random pr = new Random(semilla ^ 1);
            for (int k = 0; k < 200; k++) {
                double x = b.getMinX() + pr.nextDouble() * b.getWidth(), y = b.getMinY() + pr.nextDouble() * b.getHeight();
                double sc = gc.sdf(x, y), se = ge.sdf(x, y);
                boolean cc = gc.contiene(x, y), ce = ge.contiene(x, y);
                n++;
                if (cc != ce && Math.abs(sc) > 1e-6) difContiene++;
                if (sc > 0.01) {   // fuera: distancia exacta en ambos
                    peorSdf = Math.max(peorSdf, Math.abs(sc - se));
                    if (Math.abs(sc - se) > 1e-5) difSdf++;
                }
                if (cc && ce) {   // dentro: mismo rho mínimo (la regla de t* es la misma)
                    double rc = gc.parametros(x, y).rho(), re = ge.parametros(x, y).rho();
                    peorRho = Math.max(peorRho, Math.abs(rc - re));
                    if (Math.abs(rc - re) > 1e-4 * Math.max(1, rc)) difRho++;
                }
            }
        }
        comprueba("elipses con aspecto 1 == círculos: pertenencia", difContiene == 0, n + " puntos, " + difContiene + " discrepancias");
        comprueba("elipses con aspecto 1 == círculos: sdf exterior (distancia exacta)", difSdf == 0, String.format("peor %.1e", peorSdf));
        comprueba("elipses con aspecto 1 == círculos: rho mínimo en el interior", difRho == 0, String.format("peor %.1e", peorRho));

        // render completo (PLANO y TRAZO) con un relleno que depende de t y rho
        Gota.Relleno rel = (t, rho) -> 0xFF000000 | ((int) (255 * Math.min(1, t)) << 16) | ((int) (255 * Math.min(1, rho)) << 8) | 0x40;
        int malos = 0, total = 0;
        for (int gi = 0; gi < 20; gi++) {
            long semilla = rnd.nextLong();
            for (Gota.Solape s : Gota.Solape.values()) {
                BufferedImage i1 = new BufferedImage(260, 260, BufferedImage.TYPE_INT_ARGB), i2 = new BufferedImage(260, 260, BufferedImage.TYPE_INT_ARGB);
                aleatoria(new Random(semilla), false).conSolape(s).render(i1, rel);
                aleatoria(new Random(semilla), true).conSolape(s).render(i2, rel);
                int[] a = datos(i1), c = datos(i2);
                for (int i = 0; i < a.length; i++) {
                    if (a[i] == 0 && c[i] == 0) continue;
                    total++;
                    boolean cerca = true;
                    for (int sh = 0; sh < 32; sh += 8) if (Math.abs(((a[i] >>> sh) & 0xFF) - ((c[i] >>> sh) & 0xFF)) > 6) cerca = false;
                    if (!cerca) malos++;
                }
            }
        }
        comprueba("render PLANO y TRAZO: elipses con aspecto 1 == círculos (tolerancia 6 niveles)", malos <= total / 500, malos + " de " + total + " píxeles");
    }

    // ---------- barrido analítico: elipse constante a lo largo de una recta ----------

    /** Frontera de la forma (a lo largo de x, de 0 a L): dos semielipses y dos rectas, muestreada. */
    static double[][] frontera(double L, double a, double b, int n) {
        double[][] pts = new double[4 * n + 2][];
        int k = 0;
        for (int i = 0; i <= n; i++) {   // semielipse izquierda, de arriba a abajo
            double th = Math.PI / 2 + Math.PI * i / n;
            pts[k++] = new double[] { a * Math.cos(th), b * Math.sin(th) };
        }
        for (int i = 0; i <= n; i++) {   // semielipse derecha
            double th = -Math.PI / 2 + Math.PI * i / n;
            pts[k++] = new double[] { L + a * Math.cos(th), b * Math.sin(th) };
        }
        for (int i = 0; i < n; i++) {    // lados rectos
            double x = L * i / n;
            pts[k++] = new double[] { x, b };
            pts[k++] = new double[] { x, -b };
        }
        return Arrays.copyOf(pts, k);
    }

    static boolean analitica(double x, double y, double L, double a, double b) {
        if (x < 0) return (x / a) * (x / a) + (y / b) * (y / b) <= 1;
        if (x > L) return ((x - L) / a) * ((x - L) / a) + (y / b) * (y / b) <= 1;
        return Math.abs(y) <= b;
    }

    static void barridoAnalitico() {
        double L = 100, b = 10;
        double[] lams = { 0.3, 1, 2, 3.5 };
        for (double lam : lams) {
            double a = lam * b;
            Gota g = Gota.lineal(p(50, 80), b, p(50 + L, 80), b, Gota.Perfil.central(1).aspecto(lam));
            Random rnd = new Random(7);
            int n = 0, malos = 0;
            double peorSdf = 0;
            for (int i = 0; i < 20000; i++) {
                double x = 50 - a - 12 + rnd.nextDouble() * (L + 2 * a + 24), y = 80 - b - 12 + rnd.nextDouble() * (2 * b + 24);
                double qx = x - 50, qy = y - 80;
                boolean esp = analitica(qx, qy, L, a, b);
                // distancia analítica a la forma: bandas rectas entre los centros, semielipses en los extremos
                double dist;
                if (qx < 0) dist = Gota.distElipse(a, b, qx, qy);
                else if (qx > L) dist = Gota.distElipse(a, b, qx - L, qy);
                else dist = Math.abs(qy) - b;
                n++;
                if (g.contiene(x, y) != esp && Math.abs(dist) > 1e-6) malos++;
                if (!esp) peorSdf = Math.max(peorSdf, Math.abs(g.sdf(x, y) - dist));
            }
            comprueba(String.format("barrido recto lambda = %.1f: pertenencia == solución analítica (20000 puntos)", lam), malos == 0, malos + " discrepancias");
            comprueba(String.format("barrido recto lambda = %.1f: sdf exterior == distancia a la frontera analítica", lam), peorSdf < 1e-6, String.format("peor %.1e", peorSdf));
        }
    }

    // ---------- fuerza bruta: unión de elipses con aspecto variable y caminos curvos ----------

    static boolean brutaContiene(Gota g, double x, double y, int N) {
        double h = 1e-6;
        for (int i = 0; i <= N; i++) {
            double t = i / (double) N;
            Point2D c = g.centro(t);
            double t0 = Math.max(0, t - h), t1 = Math.min(1, t + h);
            double dx = g.centro(t1).getX() - g.centro(t0).getX(), dy = g.centro(t1).getY() - g.centro(t0).getY();
            double n = Math.hypot(dx, dy);
            if (n < 1e-12) { dx = 1; dy = 0; n = 1; }
            double tx = dx / n, ty = dy / n;
            double qx = x - c.getX(), qy = y - c.getY();
            double u = qx * tx + qy * ty, v = qx * ty - qy * tx;
            double b = g.radio(t), a = g.radioTangente(t);
            if (b <= 0 || a <= 0) { if (qx * qx + qy * qy <= 0) return true; continue; }
            if ((u / a) * (u / a) + (v / b) * (v / b) <= 1) return true;
        }
        return false;
    }

    static void fuerzaBruta() {
        Random rnd = new Random(31);
        int n = 0, malos = 0, cerca = 0;
        for (int gi = 0; gi < 60; gi++) {
            Point2D c1 = p(rnd.nextDouble() * 160, rnd.nextDouble() * 160), c2 = p(rnd.nextDouble() * 160, rnd.nextDouble() * 160);
            double r1 = 4 + rnd.nextDouble() * 20, r2 = 4 + rnd.nextDouble() * 20;
            Gota.Vec v1 = new Gota.Vec((rnd.nextDouble() - 0.5) * 220, (rnd.nextDouble() - 0.5) * 220);
            Gota.Vec v2 = new Gota.Vec((rnd.nextDouble() - 0.5) * 220, (rnd.nextDouble() - 0.5) * 220);
            double l1 = Math.exp((rnd.nextDouble() - 0.5) * 2.4), l2 = Math.exp((rnd.nextDouble() - 0.5) * 2.4);
            Gota.Perfil pf = rnd.nextBoolean() ? Gota.Perfil.central(0.6 + rnd.nextDouble() * 1.6).aspecto(l1, l2)
                                               : Gota.Perfil.radioMedio((r1 + r2) / 2 * (0.4 + rnd.nextDouble() * 1.8), 0.15 + rnd.nextDouble() * 0.7, rnd.nextDouble()).aspecto(l1, l2);
            Gota g = rnd.nextInt(3) == 0 ? Gota.lineal(c1, r1, c2, r2, pf) : Gota.curva(c1, r1, v1, c2, r2, v2, pf);
            var bd = g.bounds();
            for (int k = 0; k < 250; k++) {
                double x = bd.getMinX() + rnd.nextDouble() * bd.getWidth(), y = bd.getMinY() + rnd.nextDouble() * bd.getHeight();
                boolean bruta = brutaContiene(g, x, y, 4000), es = g.contiene(x, y);
                n++;
                if (bruta != es) {
                    if (Math.abs(g.sdf(x, y)) < 0.1) cerca++; else malos++;   // la bruta discretiza t: solo vale lejos de la frontera
                }
            }
        }
        comprueba("elipses con aspecto variable y caminos curvos: pertenencia == unión de 4001 elipses", malos == 0 && cerca < n / 200, n + " puntos, " + malos + " errores, " + cerca + " a menos de 0.1 px del borde");
    }

    // ---------- degenerados ----------

    static void degenerados() {
        Gota.Perfil pe = Gota.Perfil.central(1).aspecto(3, 0.5);
        Gota[] gs = {
            Gota.lineal(p(100, 100), 20, p(100, 100), 12, pe),                                                      // centros coincidentes
            Gota.curva(p(40, 150), 15, new Gota.Vec(0, 0), p(200, 150), 10, new Gota.Vec(0, 0), pe),                 // tiradores nulos
            Gota.curva(p(40, 150), 15, new Gota.Vec(0, 0), p(200, 150), 10, new Gota.Vec(80, -80), pe),              // un tirador nulo
            Gota.curva(p(60, 240), 20, new Gota.Vec(0, -200), p(240, 240), 20, new Gota.Vec(0, -200), pe),           // arco
            Gota.curva(p(100, 150), 14, new Gota.Vec(120, 0), p(200, 150), 14, new Gota.Vec(-120, 0), pe),           // c'(1/2) = 0 exacto (cúspide en t = 1/2)
            Gota.curva(p(100, 150), 14, new Gota.Vec(100, 60), p(100, 150), 14, new Gota.Vec(100, 60), pe),          // lazo cerrado
            Gota.lineal(p(40, 150), 0, p(220, 150), 0, pe),                                                         // radios 0
            Gota.lineal(p(40, 150), 0, p(220, 150), 25, pe),                                                        // punta
        };
        String[] nombres = { "centros coincidentes", "tiradores nulos", "un tirador nulo", "arco", "cúspide exacta en t = 1/2", "lazo cerrado", "radios 0", "punta -> grueso" };
        for (int i = 0; i < gs.length; i++) {
            Gota g = gs[i];
            var b = g.bounds();
            BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
            g.render(img, Gota.Relleno.solido(0xFF336699));
            int cubiertos = 0;
            for (int px : datos(img)) if ((px >>> 24) != 0) cubiertos++;
            int caja = (int) (Math.min(300, b.getMaxX()) - Math.max(0, b.getMinX())) * (int) (Math.min(300, b.getMaxY()) - Math.max(0, b.getMinY()));
            // el fallo clásico (F = 0 para todo p) pintaría casi toda la caja
            boolean ok = cubiertos < 0.6 * caja;
            // frontera: contiene() y sdf() deben coincidir
            Random rnd = new Random(i);
            int incoherentes = 0, dentro = 0;
            for (int k = 0; k < 400; k++) {
                double x = b.getMinX() + rnd.nextDouble() * b.getWidth(), y = b.getMinY() + rnd.nextDouble() * b.getHeight();
                boolean c = g.contiene(x, y);
                double s = g.sdf(x, y);
                if (c) dentro++;
                if (c != (s <= 1e-6) && Math.abs(s) > 1e-3) incoherentes++;
            }
            comprueba("degenerado «" + nombres[i] + "»: sin colapso y contiene() coherente con sdf()", ok && incoherentes == 0,
                      cubiertos + " px cubiertos de " + caja + " de la caja, " + incoherentes + " incoherentes");
        }
    }

    // ---------- Uv y render ----------

    static void uvYRender() {
        Gota[] gs = {
            Gota.lineal(p(60, 150), 40, p(240, 150), 18, Gota.Perfil.central(1.2).aspecto(2, 0.6)),
            Gota.curva(p(60, 240), 30, new Gota.Vec(0, -200), p(240, 240), 15, new Gota.Vec(0, -200), Gota.Perfil.recto().aspecto(0.5, 2.5)),
        };
        for (int gi = 0; gi < gs.length; gi++) {
            Gota g = gs[gi];
            var b = g.bounds();
            int n = 0, fuera = 0, nan = 0;
            for (int y = (int) b.getMinY(); y < (int) b.getMaxY(); y += 2)
                for (int x = (int) b.getMinX(); x < (int) b.getMaxX(); x += 2) {
                    if (!g.contiene(x + 0.5, y + 0.5)) continue;
                    Gota.Uv uv = g.uv(x + 0.5, y + 0.5);
                    n++;
                    if (Double.isNaN(uv.u()) || Double.isNaN(uv.v())) nan++;
                    if (uv.u() < 0 || uv.u() > 1 || uv.v() < 0 || uv.v() > 1) fuera++;
                }
            comprueba("elipses (lambda variable) en la gota " + gi + ": uv finitas y en [0, 1]", n > 1000 && fuera == 0 && nan == 0, n + " píxeles");
        }

        // aspecto 1 forzado: la uv coincide con la de círculos (salvo la regularización de 1e-5 rad)
        Gota gc = Gota.lineal(p(60, 150), 50, p(240, 150), 20, Gota.Perfil.central(1.4));
        Gota ge = Gota.lineal(p(60, 150), 50, p(240, 150), 20, Gota.Perfil.central(1.4).forzarElipses());
        var b = gc.bounds();
        int n = 0, malos = 0;
        double peor = 0;
        for (int y = (int) b.getMinY(); y < (int) b.getMaxY(); y++)
            for (int x = (int) b.getMinX(); x < (int) b.getMaxX(); x++) {
                if (!gc.contiene(x + 0.5, y + 0.5)) continue;
                Gota.Uv a = gc.uv(x + 0.5, y + 0.5), c = ge.uv(x + 0.5, y + 0.5);
                double d = Math.max(Math.abs(a.u() - c.u()), Math.abs(a.v() - c.v()));
                n++; peor = Math.max(peor, d);
                if (d > 1e-3) malos++;
            }
        comprueba("uv con aspecto 1 forzado == uv de círculos", malos == 0, String.format("%d píxeles, diferencia máx %.1e", n, peor));

        exactitudUv();

        // aspecto, radio tangente y esElipse
        Gota g = Gota.lineal(p(0, 0), 10, p(100, 0), 20, Gota.Perfil.central(1).aspecto(1, 3));
        comprueba("radioTangente(t) = aspecto(t)·radio(t); extremos a = lambda·r", g.esElipse() && Math.abs(g.radioTangente(0) - 10) < 1e-12
                  && Math.abs(g.radioTangente(1) - 60) < 1e-12 && Math.abs(g.aspecto(0.5) - 2) < 1e-12, "");
        Gota h = Gota.lineal(p(0, 0), 10, p(100, 0), 20, Gota.Perfil.central(1).radiosTangentes(25, 30));
        comprueba("radiosTangentes(a1, a2): aspecto = a / r en cada extremo", Math.abs(h.radioTangente(0) - 25) < 1e-9 && Math.abs(h.radioTangente(1) - 30) < 1e-9, "");
        boolean lanza = false;
        try { Gota.lineal(p(0, 0), 10, p(100, 0), 20, Gota.Perfil.central(1).aspecto(Double.NaN, 1)); } catch (IllegalArgumentException e) { lanza = true; }
        comprueba("aspecto no finito lanza IllegalArgumentException", lanza, "");
        Gota cl = Gota.lineal(p(0, 0), 10, p(100, 0), 20, Gota.Perfil.central(1).aspecto(100, -3));
        comprueba("aspecto fuera de dominio se recorta a (0.05, 20)", Math.abs(cl.aspecto(0) - 20) < 1e-12 && Math.abs(cl.aspecto(1) - 0.05) < 1e-12, "");
    }


    // ---------- exactitud de la uv con aspecto distinto de 1 ----------

    static void exactitudUv() {
        Gota.Perfil[] pf = {
            Gota.Perfil.central(1.2).aspecto(2, 0.6), Gota.Perfil.recto().aspecto(0.5, 2.5), Gota.Perfil.central(0.8).aspecto(3, 3),
            Gota.Perfil.radioMedio(14, 0.35, 0.3).aspecto(1.8, 0.7),
        };
        int tot = 0, malContorno = 0, saltos = 0, lineas = 0, malExterior = 0;
        double peorContorno = 0, peorSalto = 0, peorExt = 0;
        for (Gota.Solape sol : Gota.Solape.values()) {
            for (int gi = 0; gi < 8; gi++) {
                Gota.Perfil p0 = pf[gi % 4];
                if (gi >= 4 && gi % 4 == 2) p0 = Gota.Perfil.central(0.8).aspecto(1.6, 1.6);   // arcos: con a >> radio de curvatura el contorno lo dibujan las puntas
                Gota g = (gi < 4 ? Gota.lineal(p(60, 150), 40, p(240, 150), 18, p0)
                                 : Gota.curva(p(60, 240), 30, new Gota.Vec(0, -200), p(240, 240), 15, new Gota.Vec(0, -200), p0)).conSolape(sol);
                // contorno del cuerpo: desde puntos del camino hacia fuera, u debe valer 0 o 1
                Random rnd = new Random(gi * 7 + 1);
                for (int k = 0; k < 60; k++) {
                    double t0 = 0.12 + rnd.nextDouble() * 0.76;
                    Point2D c = g.centro(t0);
                    double ang = rnd.nextDouble() * 2 * Math.PI;
                    double lo = 0, hi = 400;
                    double ax = c.getX(), ay = c.getY(), dx = Math.cos(ang), dy = Math.sin(ang);
                    if (!g.contiene(ax, ay) || g.contiene(ax + hi * dx, ay + hi * dy)) continue;
                    for (int it = 0; it < 60; it++) {
                        double mid = (lo + hi) / 2;
                        if (g.contiene(ax + mid * dx, ay + mid * dy)) lo = mid; else hi = mid;
                    }
                    double qx = ax + (lo - 1e-6) * dx, qy = ay + (lo - 1e-6) * dy;
                    Gota.Muestra m = g.parametros(qx, qy);
                    if (m.t() < 0.06 || m.t() > 0.94) continue;
                    Gota.Uv uv = g.uv(qx, qy);
                    double e = Math.min(Math.abs(uv.u()), Math.abs(uv.u() - 1));
                    tot++;
                    peorContorno = Math.max(peorContorno, e);
                    if (e > 0.03) malContorno++;
                }
                // tapas: v continua al cruzar el borde cuerpo/tapa y v = 0 (o 1) en el arco exterior
                for (int fin = 0; fin < 2; fin++) {
                    Point2D c = g.centro(fin);
                    double phi = g.angulo(fin), tx = Math.cos(phi), ty = Math.sin(phi), nx = ty, ny = -tx;
                    double b = g.radio(fin), a = g.radioTangente(fin);
                    if (b < 3) continue;
                    for (double sf : new double[] { -0.8, -0.5, -0.2, 0.05, 0.2, 0.5, 0.8 }) {   // sin S = 0 exacto: en TRAZO es el eje medio de la elipse
                        double S = sf * b, depOut = a * Math.sqrt(1 - sf * sf);
                        double previo = Double.NaN, tPrev = Double.NaN;
                        boolean viola = false;
                        for (double dep = depOut - 1e-3; dep > -depOut; dep -= 0.1) {
                            double A = fin == 0 ? -dep : dep;
                            double qx = c.getX() + A * tx + S * nx, qy = c.getY() + A * ty + S * ny;
                            if (!g.contiene(qx, qy)) { previo = Double.NaN; tPrev = Double.NaN; continue; }
                            Gota.Uv uv = g.uv(qx, qy);
                            double tn = g.parametros(qx, qy).t();
                            if (!Double.isNaN(tPrev) && Math.abs(tn - tPrev) > 0.02) previo = Double.NaN;   // costura del propio reparto (t*) entre capas: la uv la sigue
                            tPrev = tn;
                            if (Double.isNaN(previo) && dep > depOut - 0.01) {
                                double ext = fin == 0 ? uv.v() : 1 - uv.v();
                                peorExt = Math.max(peorExt, ext);
                                if (ext > 0.02) malExterior++;
                            }
                            if (!Double.isNaN(previo) && Math.abs(uv.v() - previo) > 0.06) { viola = true; peorSalto = Math.max(peorSalto, Math.abs(uv.v() - previo)); }
                            previo = uv.v();
                        }
                        lineas++;
                        if (viola) saltos++;
                    }
                }
            }
        }
        comprueba("elipses, uv del cuerpo: u = 0 o 1 en el contorno (" + tot + " puntos, PLANO y TRAZO)", tot > 200 && malContorno == 0, String.format("peor desvío %.1e", peorContorno));
        comprueba("elipses, uv de las tapas: v = 0 / 1 en el arco exterior", malExterior == 0, String.format("peor %.1e", peorExt));
        comprueba("elipses, uv de las tapas: v continua al cruzar el borde cuerpo/tapa (" + lineas + " líneas)", saltos == 0, String.format("%d líneas con salto, máx %.3f", saltos, peorSalto));
    }

    static void galeria() throws Exception {
        BufferedImage img = new BufferedImage(900, 600, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(datos(img), 0xFFFFFFFF);
        double[][] ls = { { 1, 1 }, { 0.5, 0.5 }, { 2, 2 }, { 0.4, 2.5 }, { 1, 3 }, { 3, 0.6 } };
        for (int i = 0; i < 6; i++) {
            double ox = (i % 3) * 300, oy = (i / 3) * 300;
            Gota g = i < 3 ? Gota.lineal(p(ox + 80, oy + 150), 30, p(ox + 220, oy + 150), 20, Gota.Perfil.recto().aspecto(ls[i][0], ls[i][1]))
                           : Gota.curva(p(ox + 70, oy + 230), 22, new Gota.Vec(0, -170), p(ox + 230, oy + 230), 14, new Gota.Vec(0, -170), Gota.Perfil.recto().aspecto(ls[i][0], ls[i][1]));
            g.render(img, Rellenos.cadena(0xFF1E1B4B, 0xFFFFE27A, 0xFF2563EB, 0xFF7C2D12));
        }
        ImageIO.write(img, "png", new File("gota_elipses.png"));
    }
}
