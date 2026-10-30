import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;

/**
 * Banco de regresión de Gota. Sin dependencias: main que sale con código 1 si algo falla.
 * Cubre los hallazgos de la revisión externa: coordenadas no finitas, render sobre subimágenes,
 * clasificación junto al contorno, pertenencia aleatoria contra fuerza bruta, alfa y paralelismo.
 */
public class GotaRegresion {

    static int pruebas = 0, fallos = 0;

    static void comprueba(String nombre, boolean ok, String detalle) {
        pruebas++;
        if (!ok) fallos++;
        System.out.printf("%-64s %s%s%n", nombre, ok ? "ok   " : "FALLA", detalle.isEmpty() ? "" : "  " + detalle);
    }

    static Point2D.Double p(double x, double y) { return new Point2D.Double(x, y); }

    static int[] datos(BufferedImage img) { return ((DataBufferInt) img.getRaster().getDataBuffer()).getData(); }

    public static void main(String[] args) throws Exception {
        noFinitas();
        subimagen();
        contorno();
        contornoCurvo();
        coberturaSoloEnBorde();
        aleatorias();
        alfaYParalelo();
        System.out.printf("%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }

    // ---------- 1. Coordenadas y parámetros no finitos ----------

    static void noFinitas() {
        Gota g = Gota.lineal(p(0, 0), 10, p(40, 0), 10, Gota.Perfil.central(1));
        double[] malos = { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY };
        boolean cont = true, sdf = true;
        StringBuilder det = new StringBuilder();
        for (double m : malos) {
            boolean a = g.contiene(m, 0), b = g.contiene(0, m), c = g.contiene(m, m);
            if (a || b || c) { cont = false; det.append(String.format("contiene(%s,..)=%b,%b,%b ", m, a, b, c)); }
            if (g.sdf(m, 0) != Double.POSITIVE_INFINITY || g.sdf(0, m) != Double.POSITIVE_INFINITY) sdf = false;
        }
        comprueba("contiene() rechaza coordenadas no finitas", cont, det.toString());
        comprueba("sdf() devuelve +infinito con coordenadas no finitas", sdf, "");

        Runnable[] malas = {
            () -> Gota.lineal(p(Double.NaN, 0), 10, p(40, 0), 10, Gota.Perfil.central(1)),
            () -> Gota.lineal(p(0, 0), Double.NaN, p(40, 0), 10, Gota.Perfil.central(1)),
            () -> Gota.lineal(p(0, 0), 10, p(Double.POSITIVE_INFINITY, 0), 10, Gota.Perfil.central(1)),
            () -> Gota.lineal(p(0, 0), 10, p(40, 0), 10, Gota.Perfil.central(Double.NaN)),
            () -> Gota.curva(p(0, 0), 10, new Gota.Vec(Double.NaN, 0), p(40, 0), 10, new Gota.Vec(0, 0), Gota.Perfil.central(1)),
        };
        int lanzan = 0;
        for (Runnable r : malas) {
            try { r.run(); } catch (IllegalArgumentException e) { lanzan++; }
        }
        comprueba("parámetros no finitos lanzan IllegalArgumentException", lanzan == malas.length,
                  lanzan + " de " + malas.length);
    }

    // ---------- 2. Render sobre una subimagen ----------

    static void subimagen() {
        int W = 60, H = 40, sx = 13, sy = 9, sw = 30, sh = 20;
        BufferedImage base = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        int[] antes = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int c = 0xFF000000 | (x * 4) << 16 | (y * 6) << 8 | 0x77;   // centinela distinto en cada píxel
                base.setRGB(x, y, c);
                antes[y * W + x] = c;
            }
        }
        Gota g = Gota.lineal(p(7, 10), 6, p(23, 10), 4, Gota.Perfil.central(1));
        int rojo = 0xFFFF0000;
        BufferedImage ref = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
        g.render(ref, Gota.Relleno.solido(rojo));
        BufferedImage sub = base.getSubimage(sx, sy, sw, sh);
        g.render(sub, Gota.Relleno.solido(rojo));

        int fuera = 0, mal = 0, cubiertos = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int ahora = base.getRGB(x, y), previo = antes[y * W + x];
                boolean enSub = x >= sx && x < sx + sw && y >= sy && y < sy + sh;
                if (!enSub) {
                    if (ahora != previo) fuera++;
                    continue;
                }
                int a = ref.getRGB(x - sx, y - sy) >>> 24;
                if (a == 255) { cubiertos++; if (ahora != rojo) mal++; }
                else if (a == 0 && ahora != previo) mal++;
            }
        }
        comprueba("render() sobre getSubimage(): no altera píxeles ajenos", fuera == 0, fuera + " píxeles ajenos alterados");
        comprueba("render() sobre getSubimage(): igual que sobre una imagen suelta",
                  mal == 0 && cubiertos > 100, mal + " píxeles distintos, " + cubiertos + " cubiertos");
    }

    // ---------- 3. Contorno: cápsula recta de radio constante, con SDF analítica ----------

    static void contorno() {
        Gota g = Gota.lineal(p(0, 0), 10, p(40, 0), 10, Gota.Perfil.central(1));   // central = 1 y r1 = r2: radio constante
        List<double[]> normales = new ArrayList<>();           // {cx, cy, nx, ny}: centro y normal exterior
        for (double ang : new double[] { 15, 45, 75, 90 }) {
            double r = Math.toRadians(ang);
            normales.add(new double[] { 0, 0, -Math.cos(r), -Math.sin(r) });    // tapa izquierda
            normales.add(new double[] { 40, 0, Math.cos(r), -Math.sin(r) });    // tapa derecha
        }
        for (double x : new double[] { 1, 10, 20, 30, 39 }) normales.add(new double[] { x, 0, 0, -1 });  // tramo recto

        double[] deltas = { 1e-2, 1e-4, 1e-6, 1e-8, 1e-10, 1e-12 };
        boolean ok = true;
        StringBuilder tabla = new StringBuilder();
        for (double d : deltas) {
            int malFuera = 0, malDentro = 0;
            for (double[] n : normales) {
                if (g.contiene(n[0] + (10 + d) * n[2], n[1] + (10 + d) * n[3])) malFuera++;
                if (!g.contiene(n[0] + (10 - d) * n[2], n[1] + (10 - d) * n[3])) malDentro++;
            }
            tabla.append(String.format("δ=%.0e: fuera mal %d/%d, dentro mal %d/%d%n    ",
                    d, malFuera, normales.size(), malDentro, normales.size()));
            if (d >= 1e-6 && (malFuera > 0 || malDentro > 0)) ok = false;
        }
        comprueba("contorno: exacto hasta δ = 1e-6 px (cápsula analítica)", ok, "\n    " + tabla.toString().trim());
    }

    // ---------- 3b. Frontera de contiene() frente a sdf() en curvas y lazos ----------

    /** Geometría aleatoria (rectas y curvas, con lazos y radios cero); el consumo de rnd es siempre el mismo. */
    static Gota aleatoria(Random rnd) {
        Point2D c1 = p(rnd.nextDouble() * 200, rnd.nextDouble() * 200);
        Point2D c2 = p(rnd.nextDouble() * 200, rnd.nextDouble() * 200);
        double r1 = rnd.nextInt(8) == 0 ? 0 : rnd.nextDouble() * 40;
        double r2 = rnd.nextInt(8) == 0 ? 0 : rnd.nextDouble() * 40;
        double h = 0.5 + rnd.nextDouble() * 2.5;
        Gota.Vec v1 = new Gota.Vec((rnd.nextDouble() - 0.5) * 300, (rnd.nextDouble() - 0.5) * 300);
        Gota.Vec v2 = new Gota.Vec((rnd.nextDouble() - 0.5) * 300, (rnd.nextDouble() - 0.5) * 300);
        double tm = 0.02 + rnd.nextDouble() * 0.96, suav = rnd.nextInt(4) == 0 ? 1 : rnd.nextDouble();
        boolean rec = rnd.nextBoolean(), medio = rnd.nextBoolean();   // consumo fijo de rnd
        // la mitad de las gotas usa el perfil nuevo (rm absoluto, tm desplazado, suavizado < 1; rm puede quedar por debajo del recorte)
        Gota.Perfil pf = medio ? Gota.Perfil.radioMedio(h * (r1 + r2) / 2 * (rnd.nextInt(6) == 0 ? 0.2 : 1), tm, suav) : Gota.Perfil.central(h);
        return rec ? Gota.lineal(c1, r1, c2, r2, pf) : Gota.curva(c1, r1, v1, c2, r2, v2, pf);
    }

    /**
     * Desde un centro (dentro) hacia un punto lejano (fuera) se biseca contiene() hasta la frontera;
     * ahí sdf() debe valer 0. Es una comprobación a escala de 1e-13 px de dos algoritmos independientes
     * (subdivisión de Bernstein y minimización numérica) sobre curvas con lazos.
     */
    static void contornoCurvo() {
        Random rnd = new Random(77);
        double peor = 0;
        int n = 0;
        String det = "";
        for (int gi = 0; gi < 120; gi++) {
            Gota g = aleatoria(rnd);
            var b = g.bounds();
            for (int k = 0; k < 4; k++) {
                double t0 = rnd.nextDouble(), ang = rnd.nextDouble() * 2 * Math.PI;
                Point2D c = g.centro(t0);
                if (g.radio(t0) < 1) continue;
                double far = 2 * Math.hypot(b.getWidth(), b.getHeight()) + 10;
                double ax = c.getX(), ay = c.getY();
                double bx = ax + far * Math.cos(ang), by = ay + far * Math.sin(ang);
                if (!g.contiene(ax, ay) || g.contiene(bx, by)) continue;
                double lo = 0, hi = 1;
                for (int it = 0; it < 80; it++) {
                    double mid = (lo + hi) / 2;
                    if (g.contiene(ax + (bx - ax) * mid, ay + (by - ay) * mid)) lo = mid; else hi = mid;
                }
                double qx = ax + (bx - ax) * lo, qy = ay + (by - ay) * lo;
                double s = Math.abs(g.sdf(qx, qy));
                n++;
                if (s > peor) { peor = s; det = String.format("geometría %d, frontera (%.4f, %.4f)", gi, qx, qy); }
            }
        }
        comprueba("contorno curvo: frontera de contiene() == sdf() = 0", n > 100 && peor < 1e-4,
                  String.format("%d puntos de frontera; peor |sdf| = %.2e (%s)", n, peor, det));
    }

    // ---------- 3c. El antialiasing solo aparece en la banda del borde, también con círculos solapados ----------

    static void coberturaSoloEnBorde() {
        Gota[] gs = {
            Gota.lineal(p(70, 70), 30, p(90, 70), 30, Gota.Perfil.central(1)),                                   // muy solapados
            Gota.curva(p(40, 150), 12, new Gota.Vec(80, -100), p(150, 150), 9, new Gota.Vec(-80, -100), Gota.Perfil.central(1.0)),  // lazo
            Gota.lineal(p(100, 100), 40, p(100, 100), 25, Gota.Perfil.central(1)),                               // concéntricos
        };
        int mal = 0, parciales = 0;
        String primero = "";
        for (int gi = 0; gi < gs.length; gi++) {
            BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
            gs[gi].render(img, Gota.Relleno.solido(0xFFFFFFFF));
            for (int y = 0; y < 200; y++) {
                for (int x = 0; x < 200; x++) {
                    int a = img.getRGB(x, y) >>> 24;
                    int dentro = 0;
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++)
                            if (gs[gi].contiene(x + 0.5 + dx, y + 0.5 + dy)) dentro++;
                    if (a > 0 && a < 255) parciales++;
                    boolean error = (dentro == 9 && a != 255) || (dentro == 0 && a != 0);
                    if (error) {
                        mal++;
                        if (primero.isEmpty()) primero = String.format("forma %d, píxel (%d, %d), alfa %d, vecinos dentro %d/9", gi, x, y, a, dentro);
                    }
                }
            }
        }
        comprueba("antialiasing: alfa 255 en el interior, 0 en el exterior, parcial solo en el borde",
                  mal == 0 && parciales > 50, mal + " píxeles erróneos; " + parciales + " parciales. " + primero);
    }

    // ---------- 4. Pertenencia aleatoria contra fuerza bruta ----------

    static void aleatorias() {
        Random rnd = new Random(20261024L);
        int geoms = 150, puntos = 200, N = 20000;
        long total = 0, dentro = 0, cerca = 0, duros = 0, signoMal = 0;
        String primero = "";
        for (int gi = 0; gi < geoms; gi++) {
            Gota g = aleatoria(rnd);
            double[] cx = new double[N + 1], cy = new double[N + 1], rr = new double[N + 1];
            for (int i = 0; i <= N; i++) {
                double t = i / (double) N;
                Point2D c = g.centro(t);
                cx[i] = c.getX(); cy[i] = c.getY();
                double r = g.radio(t);
                rr[i] = r * r;
            }
            var b = g.bounds();
            for (int k = 0; k < puntos; k++) {
                double x = b.getMinX() + rnd.nextDouble() * b.getWidth();
                double y = b.getMinY() + rnd.nextDouble() * b.getHeight();
                boolean a = g.contiene(x, y), bf = false;
                for (int i = 0; i <= N && !bf; i++) {
                    double dx = x - cx[i], dy = y - cy[i];
                    bf = dx * dx + dy * dy <= rr[i];
                }
                total++;
                if (a) dentro++;
                double s = g.sdf(x, y);
                if ((s < 0) != a && Math.abs(s) > 1e-3) signoMal++;
                if (a != bf) {
                    if (Math.abs(s) < 0.01) cerca++;           // dentro del error de muestreo de la referencia
                    else {
                        duros++;
                        if (primero.isEmpty())
                            primero = String.format("geometría %d, punto (%.3f, %.3f), contiene=%b, fuerza bruta=%b, sdf=%.4f",
                                    gi, x, y, a, bf, s);
                    }
                }
            }
        }
        String det = String.format("%d puntos (%d dentro); discrepancias a menos de 0.01 px del borde: %d; mayores: %d; signo sdf distinto: %d %s",
                total, dentro, cerca, duros, signoMal, primero);
        comprueba("pertenencia aleatoria (150 geometrías con lazos) vs fuerza bruta", duros == 0 && signoMal == 0, det);
    }

    // ---------- 5. Alfa y paralelismo ----------

    static int refSrcOver(int dst, int src) {
        double sa = (src >>> 24) / 255.0, da = (dst >>> 24) / 255.0;
        double oa = sa + da * (1 - sa);
        if (oa <= 0) return 0;
        int out = (int) Math.round(oa * 255) << 24;
        for (int s = 16; s >= 0; s -= 8) {
            double c = (((src >> s) & 0xFF) * sa + ((dst >> s) & 0xFF) * da * (1 - sa)) / oa;
            out |= (int) Math.round(c) << s;
        }
        return out;
    }

    static boolean cercano(int x, int y) {
        for (int s = 0; s < 32; s += 8)
            if (Math.abs(((x >>> s) & 0xFF) - ((y >>> s) & 0xFF)) > 1) return false;
        return true;
    }

    static BufferedImage fondoRuido(int w, int h, long semilla) {
        Random r = new Random(semilla);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] d = datos(img);
        for (int i = 0; i < d.length; i++) d[i] = r.nextInt();   // alfa y color arbitrarios
        return img;
    }

    static void alfaYParalelo() throws Exception {
        Gota g = Gota.lineal(p(20, 20), 12, p(25, 20), 12, Gota.Perfil.central(1));
        int[] fondos = { 0x00000000, 0x00FF00FF, 0x80336699, 0xFF336699, 0x40FFFFFF };
        int[] fuentes = { 0xFFFF8040, 0x80FF8040, 0x10204060 };
        int malos = 0;
        String primero = "";
        for (int f : fondos) {
            for (int s : fuentes) {
                BufferedImage img = new BufferedImage(50, 40, BufferedImage.TYPE_INT_ARGB);
                Arrays.fill(datos(img), f);
                g.render(img, Gota.Relleno.solido(s));
                int got = img.getRGB(20, 20), exp = refSrcOver(f, s);
                if (!cercano(got, exp)) {
                    malos++;
                    if (primero.isEmpty()) primero = String.format("fondo %08X, fuente %08X: %08X, esperado %08X", f, s, got, exp);
                }
            }
        }
        comprueba("composición alfa (src-over) contra la fórmula de referencia", malos == 0, malos + " casos de 15. " + primero);

        Gota curva = Gota.curva(p(30, 150), 25, new Gota.Vec(0, -120), p(150, 150), 15, new Gota.Vec(0, -120), Gota.Perfil.central(1.3));
        Gota.Relleno rel = Colores.conGamma(0xFFFFE27A, 0xFF2563EB, 0xFFC2410C, 0xFF1E1B4B, 2.0, 0.7);
        boolean iguales = true;
        for (Gota gg : new Gota[] { curva, curva.conSolape(Gota.Solape.TRAZO) }) {
            BufferedImage a1 = fondoRuido(200, 180, 7), a2 = fondoRuido(200, 180, 7);
            gg.render(a1, rel);
            ForkJoinPool una = new ForkJoinPool(1);
            try { una.submit(() -> gg.render(a2, rel)).get(); } finally { una.shutdown(); }
            if (!Arrays.equals(datos(a1), datos(a2))) iguales = false;
        }
        comprueba("render paralelo == render con un solo hilo (PLANO y TRAZO, fondo con ruido)", iguales, "");
    }
}
