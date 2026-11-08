import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Pruebas de la silueta de tapas y biarcos ({@code Silueta}): (1) la tabla de referencia del documento de formulación;
 * (2) con d >= r1 + r2, la silueta es la envolvente convexa de los dos círculos; (3) círculos idénticos: el círculo exacto;
 * (4) simetría, escala y orientación; (5) continuidad al variar d y la razón de radios; (6) sin esquinas (G1).
 */
public class SiluetaPruebas {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        System.out.printf("%-78s %s%s%n", n, ok ? "ok   " : "FALLA", d.isEmpty() ? "" : "  " + d);
    }

    static double deg(double r) { return Math.toDegrees(r); }

    /** Conector con el que se ejecuta la batería (se corre una vez con cada uno). */
    static Silueta.Conector C = Silueta.Conector.ELIPSE;

    static Silueta.Angulos ang(double r1, double r2, double d) { return Silueta.angulos(r1, r2, d, C); }

    static Silueta de(Point2D c1, double r1, Point2D c2, double r2) { return Silueta.de(c1, r1, c2, r2, 1, 0, C); }

    static Silueta de(Point2D c1, double r1, Point2D c2, double r2, double tx, double ty) { return Silueta.de(c1, r1, c2, r2, tx, ty, C); }

    /** Área de la forma aplanada (fórmula del área de Gauss). */
    static double area(java.awt.Shape s) {
        double a = 0, px = 0, py = 0, mx = 0, my = 0;
        double[] c = new double[6];
        for (PathIterator it = s.getPathIterator(null, 1e-3); !it.isDone(); it.next()) {
            int t = it.currentSegment(c);
            if (t == PathIterator.SEG_MOVETO) { mx = px = c[0]; my = py = c[1]; }
            else if (t == PathIterator.SEG_LINETO) { a += px * c[1] - c[0] * py; px = c[0]; py = c[1]; }
            else if (t == PathIterator.SEG_CLOSE) { a += px * my - mx * py; px = mx; py = my; }
        }
        return Math.abs(a) / 2;
    }

    /** Área de la envolvente convexa de dos círculos (muestreo fino, cadena monótona). */
    static double areaHull(double r1, double r2, double d) {
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i < 4000; i++) {
            double t = 2 * Math.PI * i / 4000;
            pts.add(new double[] { r1 * Math.cos(t), r1 * Math.sin(t) });
            pts.add(new double[] { d + r2 * Math.cos(t), r2 * Math.sin(t) });
        }
        pts.sort((p, q) -> p[0] != q[0] ? Double.compare(p[0], q[0]) : Double.compare(p[1], q[1]));
        List<double[]> h = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            int base = h.size();
            for (double[] p : pts) {
                while (h.size() >= base + 2 && cruz(h.get(h.size() - 2), h.get(h.size() - 1), p) <= 0) h.remove(h.size() - 1);
                h.add(p);
            }
            h.remove(h.size() - 1);
            java.util.Collections.reverse(pts);
        }
        double a = 0;
        for (int i = 0; i < h.size(); i++) {
            double[] p = h.get(i), q = h.get((i + 1) % h.size());
            a += p[0] * q[1] - q[0] * p[1];
        }
        return Math.abs(a) / 2;
    }

    static double cruz(double[] o, double[] a, double[] b) { return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]); }

    // r1, r2, d, psi1, psi2 (grados): tabla del documento de formulación
    static final double[][] TABLA = {
        { 72, 36, 150, 103.89, 76.11 }, { 72, 36, 108, 109.47, 70.53 }, { 72, 36, 100, 106.78, 65.26 }, { 72, 36, 80, 99.09, 51.69 },
        { 72, 36, 60, 78.07, 37.46 }, { 72, 36, 45, 58.03, 31.40 }, { 72, 36, 36, 49.56, 29.18 }, { 72, 36, 20, 38.15, 26.70 },
        { 72, 36, 4, 28.76, 26.17 }, { 72, 36, 0, 26.48, 26.48 }, { 4, 1.5, 2, 45.69, 24.38 }, { 1, 0.9, 0, 42.66, 42.66 },
        { 1, 0.99, 0, 44.97, 44.97 }, { 60, 60, 0, 45.00, 45.00 }, { 60, 60, 50, 59.59, 59.59 }, { 60, 60, 100, 80.64, 80.64 },
        { 60, 60, 120, 90.00, 90.00 } };

    // lo mismo con el conector elíptico (validado contra la implementación en Python, gota_silueta.py)
    static final double[][] TABLA_E = {
        { 72, 36, 150, 103.89, 76.11 }, { 72, 36, 108, 109.47, 70.53 }, { 72, 36, 100, 106.70, 65.33 }, { 72, 36, 80, 98.46, 52.04 },
        { 72, 36, 60, 77.17, 38.41 }, { 72, 36, 45, 58.18, 32.50 }, { 72, 36, 36, 49.99, 30.25 }, { 72, 36, 20, 38.79, 27.63 },
        { 72, 36, 4, 29.47, 26.93 }, { 72, 36, 0, 27.19, 27.19 }, { 4, 1.5, 2, 46.26, 25.26 }, { 1, 0.9, 0, 43.07, 43.07 },
        { 1, 0.99, 0, 45.00, 45.00 }, { 60, 60, 0, 45.00, 45.00 }, { 60, 60, 50, 59.59, 59.59 }, { 60, 60, 100, 80.64, 80.64 },
        { 60, 60, 120, 90.00, 90.00 } };

    static void tabla() {
        System.out.println("-- (1) tabla de referencia");
        for (double[] f : C == Silueta.Conector.ELIPSE ? TABLA_E : TABLA) {
            var a = ang(f[0], f[1], f[2]);
            double e1 = Math.abs(deg(a.psi1()) - f[3]), e2 = Math.abs(deg(a.psi2()) - f[4]);
            comprueba(String.format("r1 = %s, r2 = %s, d = %s", f[0], f[1], f[2]), e1 < 0.06 && e2 < 0.06,
                String.format("psi = (%.2f, %.2f)°, error (%.3f, %.3f)°", deg(a.psi1()), deg(a.psi2()), e1, e2));
        }
    }

    static void envolvente() {
        System.out.println("-- (2) con d >= r1 + r2 es la envolvente convexa");
        double[][] c = { { 72, 36, 150 }, { 72, 36, 108 }, { 60, 60, 120 }, { 60, 60, 200 }, { 10, 40, 80 } };
        for (double[] f : c) {
            var s = de(new Point2D.Double(0, 0), f[0], new Point2D.Double(f[2], 0), f[1]);
            double a = area(s.forma()), h = areaHull(f[0], f[1], f[2]);
            comprueba(String.format("área r1 = %s, r2 = %s, d = %s", f[0], f[1], f[2]), Math.abs(a / h - 1) < 2e-4, String.format("silueta %.2f, envolvente %.2f", a, h));
        }
    }

    static void identicos() {
        System.out.println("-- (3) círculos idénticos");
        var s = de(new Point2D.Double(10, 20), 60, new Point2D.Double(10, 20), 60);
        double a = area(s.forma()), ref = Math.PI * 3600;
        comprueba("círculo exacto (área)", Math.abs(a / ref - 1) < 2e-4, String.format("%.2f frente a %.2f", a, ref));
        comprueba("la X: psi1 = psi2 = 45°", Math.abs(deg(s.angulos().psi1()) - 45) < 0.01 && Math.abs(deg(s.angulos().psi2()) - 45) < 0.01, "");
        Path2D.Double f = s.forma();
        boolean todos = true;
        for (double t = 0; t < 2 * Math.PI; t += 0.01) todos &= f.contains(10 + 59.5 * Math.cos(t), 20 + 59.5 * Math.sin(t));
        comprueba("contiene el círculo de radio 59,5", todos, "");
        Point2D.Double[] p = s.extremos();
        boolean radios = true;
        for (Point2D.Double q : p) radios &= Math.abs(q.distance(10, 20) - 60) < 1e-9;
        comprueba("los cuatro extremos están a r del centro", radios, "");
    }

    static void invarianzas() {
        System.out.println("-- (4) simetría, escala y orientación");
        double[][] c = { { 72, 36, 20 }, { 72, 36, 60 }, { 4, 1.5, 2 }, { 1, 0.95, 0.3 } };
        for (double[] f : c) {
            var a = ang(f[0], f[1], f[2]);
            var b = ang(f[1], f[0], f[2]);
            var e = ang(f[0] * 0.1, f[1] * 0.1, f[2] * 0.1);
            double ds = Math.max(Math.abs(a.psi1() - b.psi2()), Math.abs(a.psi2() - b.psi1()));
            double de = Math.max(Math.abs(a.psi1() - e.psi1()), Math.abs(a.psi2() - e.psi2()));
            comprueba(String.format("simetría r1 = %s, r2 = %s, d = %s", f[0], f[1], f[2]), deg(ds) < 1e-3, String.format("%.2e°", deg(ds)));
            comprueba(String.format("escala   r1 = %s, r2 = %s, d = %s", f[0], f[1], f[2]), deg(de) < 1e-3, String.format("%.2e°", deg(de)));
        }
        var s0 = de(new Point2D.Double(0, 0), 72, new Point2D.Double(40, 0), 36);
        double ang = Math.toRadians(37);
        var s1 = de(new Point2D.Double(5, 7), 72, new Point2D.Double(5 + 40 * Math.cos(ang), 7 + 40 * Math.sin(ang)), 36);
        comprueba("el área no depende de la orientación", Math.abs(area(s0.forma()) / area(s1.forma()) - 1) < 1e-9, "");
        var c0 = de(new Point2D.Double(0, 0), 72, new Point2D.Double(0, 0), 36, 0, 1);
        var c1 = de(new Point2D.Double(0, 0), 72, new Point2D.Double(0, 0), 36, 1, 0);
        comprueba("con centros iguales la tangente explícita gira la silueta", Math.abs(area(c0.forma()) / area(c1.forma()) - 1) < 1e-9
            && c0.forma().getBounds2D().getWidth() != c1.forma().getBounds2D().getWidth(), "");
        var sinT = de(new Point2D.Double(0, 0), 72, new Point2D.Double(0, 0), 36);
        comprueba("sin tangente explícita, T es el eje x (la tapa 2, más pequeña, hacia +x)",
            sinT.forma().getBounds2D().getMaxX() < 72 - 1e-6 && Math.abs(sinT.forma().getBounds2D().getMinX() + 72) < 1e-6, "");
    }

    static double saltoMax(List<double[]> v) {
        double m = 0;
        for (int i = 1; i < v.size(); i++) m = Math.max(m, Math.max(Math.abs(v.get(i)[0] - v.get(i - 1)[0]), Math.abs(v.get(i)[1] - v.get(i - 1)[1])));
        return deg(m);
    }

    static void continuidad() {
        System.out.println("-- (5) continuidad");
        List<double[]> a = new ArrayList<>();
        for (int i = 0; i <= 260; i++) {
            double d = i == 0 ? 0.001 : 0.5 * i;
            var x = ang(72, 36, d);
            a.add(new double[] { x.psi1(), x.psi2() });
        }
        comprueba("72/36, d de 0 a 130 (261 puntos): salto máximo entre vecinos < 1,2°", saltoMax(a) < 1.2, String.format("%.2f°", saltoMax(a)));
        List<double[]> b = new ArrayList<>();
        for (int i = 0; i <= 180; i++) {
            var x = ang(1, 0.1 + 0.005 * i, 0.001);
            b.add(new double[] { x.psi1(), x.psi2() });
        }
        comprueba("r2/r1 de 0,1 a 1, d = 0,001 (181 puntos): salto máximo < 0,5°", saltoMax(b) < 0.5, String.format("%.2f°", saltoMax(b)));
        List<double[]> c = new ArrayList<>();
        for (int i = 0; i <= 260; i++) {
            var x = ang(60, 60, i == 0 ? 0.001 : 0.5 * i);
            c.add(new double[] { x.psi1(), x.psi2() });
        }
        comprueba("60/60, d de 0 a 130 (261 puntos): salto máximo < 0,5°", saltoMax(c) < 0.5, String.format("%.2f°", saltoMax(c)));
        var x1 = ang(72, 36, 107.999);
        var x2 = ang(72, 36, 108.001);
        comprueba("continuo en d = r1 + r2 (fin de la afinidad)", deg(Math.abs(x1.psi1() - x2.psi1())) < 0.01 && deg(Math.abs(x1.psi2() - x2.psi2())) < 0.01, "");
        // los ángulos de A son los del contacto de la tangente común
        var t = ang(72, 36, 150);
        comprueba("d >= r1 + r2: cos(psi2) = (r1 - r2)/d y psi1 = pi - psi2", Math.abs(Math.cos(t.psi2()) - 36.0 / 150) < 1e-12 && Math.abs(t.psi1() - (Math.PI - t.psi2())) < 1e-12, "");
    }

    /** Giro máximo entre aristas consecutivas de la silueta aplanada: sin esquinas, tiene que ser pequeño. */
    static double giroMax(Path2D s, double plana) {
        List<double[]> p = new ArrayList<>();
        double[] c = new double[6];
        for (PathIterator it = s.getPathIterator(null, plana); !it.isDone(); it.next()) {
            int t = it.currentSegment(c);
            if (t == PathIterator.SEG_MOVETO || t == PathIterator.SEG_LINETO) p.add(new double[] { c[0], c[1] });
        }
        double m = 0;
        int n = p.size();
        for (int i = 0; i < n; i++) {
            double[] a = p.get(i), b = p.get((i + 1) % n), d = p.get((i + 2) % n);
            double u1 = b[0] - a[0], v1 = b[1] - a[1], u2 = d[0] - b[0], v2 = d[1] - b[1];
            if (Math.hypot(u1, v1) < 1e-12 || Math.hypot(u2, v2) < 1e-12) continue;
            m = Math.max(m, Math.abs(Math.atan2(u1 * v2 - v1 * u2, u1 * u2 + v1 * v2)));
        }
        return deg(m);
    }

    static void sinEsquinas() {
        System.out.println("-- (6) sin esquinas (G1 en los empalmes)");
        double[][] c = { { 72, 36, 150 }, { 72, 36, 60 }, { 72, 36, 20 }, { 72, 36, 0 }, { 4, 1.5, 2 }, { 60, 60, 50 }, { 60, 60, 0 }, { 36, 72, 30 } };
        for (double[] f : c) {
            var s = de(new Point2D.Double(0, 0), f[0], new Point2D.Double(f[2], 0), f[1]);
            double g = giroMax(s.forma(), 1e-4 * (f[0] + f[1]));
            comprueba(String.format("r1 = %s, r2 = %s, d = %s: giro máximo entre aristas < 5°", f[0], f[1], f[2]), g < 5, String.format("%.2f°", g));
        }
    }

    static void forma() {
        System.out.println("-- (7) la forma");
        double[][] c = { { 72, 36, 150 }, { 72, 36, 60 }, { 72, 36, 20 }, { 72, 36, 0 }, { 4, 1.5, 2 }, { 60, 60, 50 }, { 36, 72, 30 } };
        for (double[] f : c) {
            var s = de(new Point2D.Double(0, 0), f[0], new Point2D.Double(f[2], 0), f[1]);
            Path2D.Double p = s.forma();
            boolean dentro = p.contains(0, 0) && p.contains(f[2], 0);
            Area ar = new Area(p);
            comprueba(String.format("r1 = %s, r2 = %s, d = %s: contiene los dos centros y es simple", f[0], f[1], f[2]), dentro && ar.isSingular(), "");
            var e = s.extremos();
            boolean sobre = Math.abs(e[0].distance(0, 0) - f[0]) < 1e-9 && Math.abs(e[1].distance(0, 0) - f[0]) < 1e-9
                && Math.abs(e[2].distance(f[2], 0) - f[1]) < 1e-9 && Math.abs(e[3].distance(f[2], 0) - f[1]) < 1e-9;
            comprueba("   P1, P2 sobre el círculo 1 y P3, P4 sobre el 2", sobre, "");
        }
    }

    static void conector() {
        System.out.println("-- (8) qué conector se usa");
        boolean todasElipse = true, rectas = true;
        for (int i = 0; i <= 120; i++) {
            double d = i == 0 ? 0.001 : 0.9 * i;                       // 72/36: d de 0 a 108 (r1 + r2 = 108)
            var s = de(new Point2D.Double(0, 0), 72, new Point2D.Double(d, 0), 36);
            if (d < 108) todasElipse &= s.conector() == Silueta.Conector.ELIPSE;
            else rectas &= s.conector() == Silueta.Conector.RECTA;
        }
        if (C == Silueta.Conector.ELIPSE) {
            comprueba("72/36, d < r1 + r2: siempre elipse, sin recurrir al biarco (los extremos se mueven)", todasElipse, "");
            var id = de(new Point2D.Double(0, 0), 60, new Point2D.Double(0, 0), 60);
            comprueba("círculos idénticos: la elipse es el círculo (silueta con radio 60 en todo el contorno)", sobreCirculo(id.forma(), 60), "");
        }
        comprueba("d >= r1 + r2: recta (envolvente convexa)", rectas, "");
    }

    static boolean sobreCirculo(Path2D s, double r) {
        double[] c = new double[6];
        double m = 0;
        for (PathIterator it = s.getPathIterator(null, 1e-4); !it.isDone(); it.next()) {
            int t = it.currentSegment(c);
            if (t == PathIterator.SEG_MOVETO || t == PathIterator.SEG_LINETO) m = Math.max(m, Math.abs(Math.hypot(c[0], c[1]) - r));
        }
        return m < 1e-6 * r + 2e-3;
    }

    public static void main(String[] args) {
        for (Silueta.Conector c : new Silueta.Conector[] { Silueta.Conector.ELIPSE, Silueta.Conector.BIARCO }) {
            C = c;
            System.out.printf("%n===== conector %s =====%n", c);
            tabla();
            envolvente();
            identicos();
            invarianzas();
            continuidad();
            sinEsquinas();
            forma();
            conector();
        }
        System.out.printf("%n%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }
}
