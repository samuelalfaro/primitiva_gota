import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;

/**
 * Pruebas de {@code SiluetaCurva} (la silueta recta doblada a lo largo de una Bézier): (1) con el camino recto coincide con
 * {@code Silueta}; (2) sin esquinas y simple con giros moderados; (3) frente a la unión de círculos del camino en δ = L/(r1 + r2) = 0,99
 * (valores del estudio en Python); (4) el aviso de pliegue; (5) orientación y escala.
 */
public class SiluetaCurvaPruebas {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        System.out.printf("%-84s %s%s%n", n, ok ? "ok   " : "FALLA", d.isEmpty() ? "" : "  " + d);
    }

    /** Camino cúbico de c1 = (0, 0) a c2 sobre el eje x, con tangentes a -giro/2 y +giro/2, escalado a la longitud de arco pedida. */
    static Point2D.Double[] camino(double giroGrados, double largo) {
        double th = Math.toRadians(giroGrados);
        double[] t1 = { Math.cos(-th / 2), Math.sin(-th / 2) }, t2 = { Math.cos(th / 2), Math.sin(th / 2) };
        double h = 1.0 / 3 / Math.max(Math.cos(th / 2), 0.35);
        Point2D.Double[] p = { new Point2D.Double(0, 0), new Point2D.Double(h * t1[0], h * t1[1]),
            new Point2D.Double(1 - h * t2[0], -h * t2[1]), new Point2D.Double(1, 0) };
        double l = 0, px = 0, py = 0;
        for (int i = 1; i <= 2000; i++) {
            double t = i / 2000.0, u = 1 - t;
            double x = u * u * u * p[0].x + 3 * u * u * t * p[1].x + 3 * u * t * t * p[2].x + t * t * t * p[3].x;
            double y = u * u * u * p[0].y + 3 * u * u * t * p[1].y + 3 * u * t * t * p[2].y + t * t * t * p[3].y;
            l += Math.hypot(x - px, y - py);
            px = x;
            py = y;
        }
        double e = largo / l;
        for (Point2D.Double q : p) q.setLocation(q.x * e, q.y * e);
        return p;
    }

    static SiluetaCurva curva(double r1, double r2, double delta, double giro) {
        var p = camino(giro, delta * (r1 + r2));
        return SiluetaCurva.de(p[0], p[1], p[2], p[3], r1, r2);
    }

    /** Fracción del área de la unión de círculos (radio lineal en t) que no coincide con la forma. */
    static double dif(java.awt.Shape forma, Point2D.Double[] p, double r1, double r2) {
        var b = forma.getBounds2D();
        int n = 260;
        double x0 = Math.min(b.getMinX(), -r1) - 5, x1 = Math.max(b.getMaxX(), p[3].x + r2) + 5, y0 = b.getMinY() - 5, y1 = b.getMaxY() + 5;
        int m = 600;
        double[] cx = new double[m + 1], cy = new double[m + 1], rr = new double[m + 1];
        for (int i = 0; i <= m; i++) {
            double t = (double) i / m, u = 1 - t;
            cx[i] = u * u * u * p[0].x + 3 * u * u * t * p[1].x + 3 * u * t * t * p[2].x + t * t * t * p[3].x;
            cy[i] = u * u * u * p[0].y + 3 * u * u * t * p[1].y + 3 * u * t * t * p[2].y + t * t * t * p[3].y;
            rr[i] = r1 + (r2 - r1) * t;
        }
        int xo = 0, env = 0;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                double x = x0 + (x1 - x0) * (i + 0.5) / n, y = y0 + (y1 - y0) * (j + 0.5) / n;
                boolean e = false;
                for (int k = 0; k <= m && !e; k += 2) e = Math.hypot(x - cx[k], y - cy[k]) <= rr[k];
                boolean f = forma.contains(x, y);
                if (e) env++;
                if (e != f) xo++;
            }
        return (double) xo / env;
    }

    static void recto() {
        System.out.println("-- (1) camino recto = silueta recta");
        double[][] c = { { 72, 36, 60 }, { 72, 36, 150 }, { 60, 60, 50 }, { 4, 1.5, 2 } };
        for (double[] f : c) {
            var cu = SiluetaCurva.de(new Point2D.Double(10, 20), new Point2D.Double(10 + f[2] / 3, 20), new Point2D.Double(10 + 2 * f[2] / 3, 20),
                new Point2D.Double(10 + f[2], 20), f[0], f[1]);
            var s = Silueta.de(new Point2D.Double(10, 20), f[0], new Point2D.Double(10 + f[2], 20), f[1]);
            double a = SiluetaPruebas.area(cu.forma()), b = SiluetaPruebas.area(s.forma());
            double dmax = 0;
            var e1 = cu.extremos();
            var e2 = s.extremos();
            for (int i = 0; i < 4; i++) dmax = Math.max(dmax, e1[i].distance(e2[i]));
            comprueba(String.format("r1 = %s, r2 = %s, d = %s: mismo área y mismos extremos", f[0], f[1], f[2]),
                Math.abs(a / b - 1) < 2e-4 && dmax < 1e-6 * (f[0] + f[1]) && Math.abs(cu.longitud() - f[2]) < 1e-6 * f[2] && !cu.plegada(),
                String.format("área %.2e, extremos %.1e", a / b - 1, dmax));
        }
    }

    static void sinEsquinas() {
        System.out.println("-- (2) sin esquinas y simple con giros moderados");
        double[][] c = { { 72, 36, 0.6, 30 }, { 72, 36, 0.9, 60 }, { 72, 36, 0.99, 75 }, { 60, 60, 0.9, 60 }, { 60, 60, 0.99, 60 }, { 36, 72, 0.9, 45 } };
        for (double[] f : c) {
            var cu = curva(f[0], f[1], f[2], f[3]);
            Path2D.Double p = cu.forma();
            double g = SiluetaPruebas.giroMax(p, 1e-4 * (f[0] + f[1]));
            comprueba(String.format("r1 = %s, r2 = %s, δ = %s, giro %s°: giro máximo entre aristas < 5°, simple, sin pliegue", f[0], f[1], f[2], f[3]),
                g < 5 && new Area(p).isSingular() && !cu.plegada(), String.format("%.2f°, pliegue %.2f", g, cu.pliegue()));
        }
    }

    static void conLaUnion() {
        System.out.println("-- (3) frente a la unión de círculos del camino (δ = 0,99; valores del estudio en Python)");
        // r1, r2, giro, tope (Python: 72/36 → 0,4 / 1,0 / 1,9 / 2,8 %; 60/60 → 0,9 / 0,7 / 0,7 / 0,8 %)
        double[][] c = { { 72, 36, 0, 1.5 }, { 72, 36, 30, 2.5 }, { 72, 36, 60, 3.5 }, { 72, 36, 90, 4.5 }, { 60, 60, 0, 2 }, { 60, 60, 30, 2 }, { 60, 60, 60, 2 }, { 60, 60, 90, 2 } };
        for (double[] f : c) {
            var p = camino(f[2], 0.99 * (f[0] + f[1]));
            var cu = SiluetaCurva.de(p[0], p[1], p[2], p[3], f[0], f[1]);
            double d = dif(cu.forma(), p, f[0], f[1]);
            comprueba(String.format("r1 = %s, r2 = %s, giro %s°: diferencia de área < %s %%", f[0], f[1], f[2], f[3]), d * 100 < f[3], String.format("%.2f %%", d * 100));
        }
    }

    static void pliegues() {
        System.out.println("-- (4) aviso de pliegue");
        comprueba("60/60, δ = 0,5, giro 90°: se pliega (semianchura > radio de curvatura)", curva(60, 60, 0.5, 90).plegada(), String.format("pliegue %.2f", curva(60, 60, 0.5, 90).pliegue()));
        comprueba("60/60, δ = 0,99, giro 30°: no se pliega", !curva(60, 60, 0.99, 30).plegada(), "");
        comprueba("camino recto: pliegue = 1", Math.abs(curva(72, 36, 0.6, 0).pliegue() - 1) < 1e-9, "");
        // el aviso es coherente con la geometría: si no se pliega, el contorno doblado es simple
        boolean coherente = true;
        for (double d : new double[] { 0.3, 0.6, 0.9 })
            for (double g : new double[] { 30, 60, 90, 120 }) {
                var cu = curva(72, 36, d, g);
                if (!cu.plegada()) coherente &= new Area(cu.forma()).isSingular();
            }
        comprueba("si pliegue() > 0 el contorno es simple (72/36, δ de 0,3 a 0,9, giros de 30° a 120°)", coherente, "");
        // con d >= r1 + r2 el conector es una recta: su interior también cuenta (antes solo se miraban los vértices del contorno)
        boolean coherenteRecta = true;
        String mal = "";
        for (double[] rr : new double[][] { { 72, 36 }, { 60, 60 }, { 36, 72 } })
            for (double d : new double[] { 1.0, 1.5, 2.0, 3.0 })
                for (double g : new double[] { 30, 60, 90, 120, 150 }) {
                    var cu = curva(rr[0], rr[1], d, g);
                    if (!cu.plegada() && !new Area(cu.forma()).isSingular()) { coherenteRecta = false; mal = String.format("%s/%s, δ %s, giro %s°, pliegue %.3f", rr[0], rr[1], d, g, cu.pliegue()); }
                }
        comprueba("si pliegue() > 0 el contorno es simple también con conector recto (δ de 1 a 3, giros de 30° a 150°)", coherenteRecta, mal);
    }

    static void escalaYOrientacion() {
        System.out.println("-- (5) escala y orientación");
        var a = curva(72, 36, 0.9, 60);
        var p = camino(60, 0.9 * 108);
        double ang = Math.toRadians(37), cs = Math.cos(ang), sn = Math.sin(ang);
        Point2D.Double[] q = new Point2D.Double[4];
        for (int i = 0; i < 4; i++) q[i] = new Point2D.Double(5 + cs * p[i].x - sn * p[i].y, 7 + sn * p[i].x + cs * p[i].y);
        var b = SiluetaCurva.de(q[0], q[1], q[2], q[3], 72, 36);
        comprueba("girar y trasladar el camino no cambia el área", Math.abs(SiluetaPruebas.area(a.forma()) / SiluetaPruebas.area(b.forma()) - 1) < 1e-6, "");
        var pe = camino(60, 0.9 * 108 / 10);
        var c = SiluetaCurva.de(pe[0], pe[1], pe[2], pe[3], 7.2, 3.6);
        double ra = SiluetaPruebas.area(a.forma()) / SiluetaPruebas.area(c.forma()) / 100;
        comprueba("escala ×0,1: el área escala con el cuadrado", Math.abs(ra - 1) < 1e-3, String.format("%.2e", ra - 1));
        var e = a.extremos();
        var c1 = new Point2D.Double(0, 0);
        comprueba("P1 y P2 siguen sobre el círculo 1 (tolerancia 3 %)", Math.abs(e[0].distance(c1) / 72 - 1) < 0.03 && Math.abs(e[1].distance(c1) / 72 - 1) < 0.03,
            String.format("%.2f %%, %.2f %%", 100 * (e[0].distance(c1) / 72 - 1), 100 * (e[1].distance(c1) / 72 - 1)));
        double rz = SiluetaPruebas.area(SiluetaCurva.de(new Point2D.Double(0, 0), new Point2D.Double(0, 0), new Point2D.Double(0, 0), new Point2D.Double(0, 0), 72, 36).forma())
            / SiluetaPruebas.area(Silueta.de(new Point2D.Double(0, 0), 72, new Point2D.Double(0, 0), 36).forma());
        comprueba("camino de longitud cero (centros iguales): como la silueta recta con tangente (1, 0)", Math.abs(rz - 1) < 2e-4, String.format("%.2e", rz - 1));
    }

    static SiluetaCurva relajada(double r1, double r2, double delta, double giro, SiluetaCurva.Relajo r) {
        var p = camino(giro, delta * (r1 + r2));
        return SiluetaCurva.de(p[0], p[1], p[2], p[3], r1, r2, Silueta.Conector.ELIPSE, r);
    }

    static void relajo() {
        System.out.println("-- (6) relajo de los conectores elípticos por una spline");
        var R = SiluetaCurva.Relajo.values();
        double a0 = SiluetaPruebas.area(relajada(72, 36, 0.9, 0, R[0]).forma());
        for (var r : R) {
            double a = SiluetaPruebas.area(relajada(72, 36, 0.9, 0, r).forma());
            comprueba("camino recto, relajo " + r + ": se conserva el arco elíptico (área a menos del 1 %)", Math.abs(a / a0 - 1) < 0.01, String.format("%.3f %%", 100 * (a / a0 - 1)));
        }
        double[][] c = { { 72, 36, 0.9, 30 }, { 72, 36, 0.9, 60 }, { 72, 36, 0.6, 60 }, { 60, 60, 0.9, 90 }, { 36, 72, 0.9, -45 } };
        for (double[] f : c)
            for (var r : R) {
                if (r == SiluetaCurva.Relajo.NINGUNO) continue;
                var cu = relajada(f[0], f[1], f[2], f[3], r);
                Path2D.Double p = cu.forma();
                double g = SiluetaPruebas.giroMax(p, 1e-4 * (f[0] + f[1]));
                boolean simple = new Area(p).isSingular();
                boolean pliegaInterior = r == SiluetaCurva.Relajo.EXTERIOR && cu.plegada();     // el interior sigue doblado y se pliega: es el efecto de la «torsión», con esquina
                comprueba(String.format("r1 = %s, r2 = %s, δ = %s, giro %s°, relajo %s: %s", f[0], f[1], f[2], f[3], r,
                    pliegaInterior ? "el interior sigue plegado (cúspide, esperada)" : "sin esquinas (< 8°)" + (r == SiluetaCurva.Relajo.AMBOS ? " y simple" : "")),
                    pliegaInterior || (g < 8 && (r != SiluetaCurva.Relajo.AMBOS || simple)), String.format("%.2f°, simple %b", g, simple));
            }
        for (double[] f : new double[][] { { 72, 36, 0.9, 60 }, { 60, 60, 0.9, 90 } }) {
            var p = camino(f[3], f[2] * (f[0] + f[1]));
            double d0 = dif(SiluetaCurva.de(p[0], p[1], p[2], p[3], f[0], f[1]).forma(), p, f[0], f[1]);
            double d4 = dif(relajada(f[0], f[1], f[2], f[3], SiluetaCurva.Relajo.AMBOS).forma(), p, f[0], f[1]);
            comprueba(String.format("%s/%s, δ = %s, giro %s°: relajar ambos se acerca más a la unión de círculos", f[0], f[1], f[2], f[3]), d4 < d0, String.format("%.2f %% → %.2f %%", 100 * d0, 100 * d4));
        }
    }

    static void relajoLocal() {
        System.out.println("-- (7) relajo LOCAL (curvatura local del camino)");
        var L = SiluetaCurva.Metodo.LOCAL;
        var p0 = camino(0, 0.9 * 108);
        double a0 = SiluetaPruebas.area(SiluetaCurva.de(p0[0], p0[1], p0[2], p0[3], 72, 36).forma());
        for (var r : SiluetaCurva.Relajo.values()) {
            double a = SiluetaPruebas.area(SiluetaCurva.de(p0[0], p0[1], p0[2], p0[3], 72, 36, Silueta.Conector.ELIPSE, r, L).forma());
            comprueba("camino recto, relajo LOCAL " + r + ": se conserva el arco elíptico (área a menos del 0,1 %)", Math.abs(a / a0 - 1) < 1e-3, String.format("%.4f %%", 100 * (a / a0 - 1)));
        }
        var p = camino(150, 0.9 * 120);
        double d0 = dif(SiluetaCurva.de(p[0], p[1], p[2], p[3], 60, 60).forma(), p, 60, 60);
        double d1 = dif(SiluetaCurva.de(p[0], p[1], p[2], p[3], 60, 60, Silueta.Conector.ELIPSE, SiluetaCurva.Relajo.AMBOS, L).forma(), p, 60, 60);
        comprueba("horquilla 60/60 (150°, δ = 0,9): relajar ambos se acerca a la unión de círculos (relleno no cero)", d1 <= d0, String.format("%.2f %% → %.2f %%", 100 * d0, 100 * d1));
        // un camino en S: el giro total es 0 pero el relajo LOCAL actúa
        var q = new Point2D.Double[] { new Point2D.Double(0, 0), new Point2D.Double(40, 80), new Point2D.Double(60, -80), new Point2D.Double(100, 0) };
        var sN = SiluetaCurva.de(q[0], q[1], q[2], q[3], 40, 30, Silueta.Conector.ELIPSE, SiluetaCurva.Relajo.NINGUNO, L);
        var sA = SiluetaCurva.de(q[0], q[1], q[2], q[3], 40, 30, Silueta.Conector.ELIPSE, SiluetaCurva.Relajo.AMBOS, L);
        double dN = dif(sN.forma(), q, 40, 30), dA = dif(sA.forma(), q, 40, 30);
        comprueba("camino en S: giro total ~ 0 y el relajo LOCAL acerca el relleno a la unión de círculos", Math.abs(sN.giro()) < 0.2 && dA < dN,
            String.format("giro %.1f°, %.2f %% → %.2f %%", Math.toDegrees(sN.giro()), 100 * dN, 100 * dA));
    }

    /** El contorno recto (aplanado y subdividido muy fino) llevado por el doblado punto a punto: la referencia exacta del contorno doblado. */
    static Path2D.Double referencia(SiluetaCurva cu) {
        Path2D.Double out = new Path2D.Double();
        double[] c = new double[6];
        double px = 0, py = 0, paso = 2e-3 * (cu.r1 + cu.r2);
        for (var it = cu.recta().formaLocal().getPathIterator(null, 2e-5 * (cu.r1 + cu.r2)); !it.isDone(); it.next()) {
            int t = it.currentSegment(c);
            if (t == java.awt.geom.PathIterator.SEG_CLOSE) { out.closePath(); continue; }
            if (t == java.awt.geom.PathIterator.SEG_MOVETO) {
                double[] q = cu.mapea(c[0], c[1]);
                out.moveTo(q[0], q[1]);
            } else {
                int m = Math.max(1, (int) Math.ceil(Math.hypot(c[0] - px, c[1] - py) / paso));
                for (int i = 1; i <= m; i++) {
                    double[] q = cu.mapea(px + (c[0] - px) * i / m, py + (c[1] - py) * i / m);
                    out.lineTo(q[0], q[1]);
                }
            }
            px = c[0];
            py = c[1];
        }
        return out;
    }

    static void pertenencia() {
        System.out.println("-- (6) pertenencia: unión de pies de perpendicular");
        double peorSin = 0, peorFalta = 0;
        int sinPliegue = 0, conPliegue = 0, huecos = 0;
        for (double[] rr : new double[][] { { 72, 36 }, { 60, 60 } })
            for (double d : new double[] { 0.3, 0.6, 0.9, 1.0, 1.5, 2.0 })
                for (double g : new double[] { 0, 30, 60, 90, 120, 150 }) {
                    var cu = curva(rr[0], rr[1], d, g);
                    double esc = Math.min(rr[0], rr[1]);
                    if (!cu.plegada()) {
                        sinPliegue++;
                        peorSin = Math.max(peorSin, SiluetaPruebas.discrepancia(referencia(cu), (x, y) -> cu.contiene(x, y) ? 1 : 0, esc, false));
                    } else {
                        conPliegue++;
                        // con pliegue la máscara es la unión: contiene todo lo que cubre el relleno, y puede cubrir más (huecos del winding)
                        peorFalta = Math.max(peorFalta, SiluetaPruebas.discrepancia(referencia(cu), (x, y) -> cu.contiene(x, y) ? 1 : 0, esc, true));
                        if (SiluetaPruebas.discrepancia(referencia(cu), (x, y) -> cu.contiene(x, y) ? 1 : 0, esc, false) > 1e-2) huecos++;
                    }
                }
        comprueba(String.format("sin pliegue (%d casos): contiene coincide con el relleno del contorno doblado (referencia: el contorno recto doblado punto a punto)", sinPliegue), peorSin < 1e-4, String.format("peor %.1e radios del borde", peorSin));
        comprueba(String.format("con pliegue (%d casos): contiene incluye todo lo que cubre el contorno doblado", conPliegue), peorFalta < 1e-4, String.format("peor %.1e radios; en %d el relleno con winding tiene huecos que la unión cubre", peorFalta, huecos));
        var p = camino(60, 0.9 * 108);
        var cu = SiluetaCurva.de(p[0], p[1], p[2], p[3], 72, 36);
        double[] w = new double[1];
        int pies = cu.pies(p[0].x, p[0].y, w);
        comprueba("el centro de la tapa 1 está dentro (al menos un pie) y sin pliegue el winding es 1", pies >= 1 && w[0] == 1 && cu.contiene(p[0].x, p[0].y), "pies " + pies + ", winding " + w[0]);
    }

    public static void main(String[] args) {
        recto();
        sinEsquinas();
        conLaUnion();
        pliegues();
        escalaYOrientacion();
        relajo();
        relajoLocal();
        pertenencia();
        System.out.printf("%n%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }
}
