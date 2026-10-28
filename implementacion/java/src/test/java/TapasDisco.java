import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Tapas de semidisco (el único mapeo de la tira, en textura y en color): la tapa de la tira se lleva a un semidisco con (u, w) -> (l, a).
 * Identidad con la cápsula de la textura de referencia, color en (s, th) y casado con el cuerpo en la costura.
 */
public class TapasDisco {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        System.out.printf("%-84s %s%s%n", n, ok ? "ok   " : "FALLA", d.isEmpty() ? "" : "  " + d);
    }

    static Point2D.Double p(double x, double y) { return new Point2D.Double(x, y); }
    static int[] datos(BufferedImage i) { return ((DataBufferInt) i.getRaster().getDataBuffer()).getData(); }

    static int maxCanal(int a, int b) {
        int m = 0;
        for (int s = 0; s <= 24; s += 8) m = Math.max(m, Math.abs(((a >> s) & 255) - ((b >> s) & 255)));
        return m;
    }

    static final Rellenos.Lateral N1 = new Rellenos.Lateral(0xFFD7263D, 0xFFFFE9A8, 0xFF2D6CDF);
    static final Rellenos.Lateral N2 = new Rellenos.Lateral(0xFF1FA39A, 0xFFE8F5D0, 0xFFF28E2B);
    static final int EXT = 0xFF222222;

    static Gota recta(double cx, double cy, double r) {
        return Gota.lineal(p(cx, cy), r, p(cx, cy + 2 * r), r, Gota.Perfil.recto());
    }

    // ---------- Textura ----------

    static void identidadTextura() {
        Texturas.Textura tex = Texturas.referencia();
        double r = 40, cx = 100, cy = 100;
        Gota g = recta(cx, cy, r);
        BufferedImage im = new BufferedImage(220, 300, BufferedImage.TYPE_INT_ARGB);
        g.render(im, Rellenos.tira(tex));
        int[] d = datos(im);
        int n = 0, mal = 0, maxE = 0, nTapas = 0;
        for (int j = 0; j < 300; j++)
            for (int i = 0; i < 220; i++) {
                double x = i + 0.5, y = j + 0.5;
                if (g.sdf(x, y) > -2) continue;
                n++;
                if (y < cy || y > cy + 2 * r) nTapas++;
                int esperado = tex.muestrea((x - (cx - r)) / (2 * r), (y - (cy - r)) / (4 * r));
                int e = maxCanal(d[j * 220 + i], esperado);
                maxE = Math.max(maxE, e);
                if (e > 2) mal++;
            }
        comprueba("tira: la gota recta reproduce la textura idéntica, cuerpo y tapas", mal == 0,
                "píxeles " + n + " (tapas " + nTapas + "), distintos " + mal + ", máx " + maxE);
        // y coincide con el mapeo en disco
        BufferedImage di = new BufferedImage(220, 300, BufferedImage.TYPE_INT_ARGB);
        g.render(di, Rellenos.discoCapsula(tex));
        int[] dd = datos(di);
        int dif = 0, borde = 0;
        for (int j = 0; j < 300; j++)
            for (int i = 0; i < 220; i++) {
                int k = j * 220 + i;
                if (maxCanal(dd[k], d[k]) <= 2) continue;
                if (g.sdf(i + 0.5, j + 0.5) < 0) dif++; else borde++;
            }
        comprueba("tira coincide con discoCapsula en el interior de la gota recta", dif == 0,
                "distintos dentro " + dif + "; en la franja de borde (sdf > 0, la tira acota u a la silueta) " + borde);
    }

    // ---------- Color ----------

    static void identidadColor() {
        double r = 40, cx = 100, cy = 100;
        Gota g = recta(cx, cy, r);
        Gota.Relleno rel = Rellenos.cadenaLateral(N1, N2, Rellenos.RGB);
        BufferedImage im = new BufferedImage(220, 300, BufferedImage.TYPE_INT_ARGB);
        g.render(im, rel);
        int[] d = datos(im);
        int n = 0, mal = 0, maxE = 0;
        for (int j = 0; j < 300; j++)
            for (int i = 0; i < 220; i++) {
                double px = i + 0.5, py = j + 0.5;
                if (g.sdf(px, py) > -2) continue;
                Gota.Muestra m = g.parametros(px, py);
                int esperado;
                if (py < cy) esperado = N1.enDisco(m.lateral(), -m.axial(), Rellenos.RGB);
                else if (py > cy + 2 * r) esperado = N2.enDisco(m.lateral(), m.axial(), Rellenos.RGB);
                else continue;
                n++;
                int e = maxCanal(d[j * 220 + i], esperado);
                maxE = Math.max(maxE, e);
                if (e > 2) mal++;
            }
        comprueba("color: las tapas rectas siguen (s, th) de la posición real", mal == 0, "píxeles " + n + ", distintos " + mal + ", máx " + maxE);
        // propiedades del semidisco
        Rellenos.Mezcla M = Rellenos.RGB;
        boolean ok = N1.enDisco(0, 0, M) == N1.centro()
                && N1.enDisco(1, 0, M) == N1.normal()
                && N1.enDisco(-1, 0, M) == N1.contra()
                && N1.enDisco(0.5, 0, M) == N1.en(0.75, M)
                && N1.enDisco(-0.5, 0, M) == N1.en(0.25, M);
        comprueba("semidisco: centro, normal y contra en el arco, y a = 0 es el cuerpo", ok, "");
        comprueba("semidisco: la punta (a = 1) es la media de normal y contra",
                N1.enDisco(0, 1, M) == M.mezclar(N1.normal(), N1.contra(), 0.5), "");
    }

    static void ajusteLateral() {
        Rellenos.Mezcla M = Rellenos.RGB;
        Rellenos.Lateral fino = N1.conAjuste(0.25);
        boolean ok = true;
        for (int i = 0; i <= 20; i++) {
            double l = -1 + 2 * i / 20.0;
            if (maxCanal(fino.enDisco(l, 0, M), fino.en((l + 1) / 2, M)) > 1) ok = false;   // el cuerpo y la tapa casan con ajuste
        }
        comprueba("ajuste lateral: el cuerpo y la tapa casan en la costura con ajuste 0.25", ok, "");
        // con ajuste < 0.5 el punto medio de la mezcla está más cerca del centro: a t = 0.25 ya es la mezcla al 50 %
        int medio = M.mezclar(N1.centro(), N1.normal(), 0.5);
        int a = fino.en(0.5 + 0.5 * 0.25, M), b = N1.en(0.5 + 0.5 * 0.25, M);
        comprueba("ajuste lateral 0.25: a t = 0.25 el color es la mezcla al 50 % (con 0.5 es la del 25 %)",
                maxCanal(a, medio) <= 1 && maxCanal(b, medio) > 10, String.format("%08X frente a %08X", a, b));
        comprueba("ajuste lateral 0.5 no cambia nada (es el valor por defecto)", N1.conAjuste(0.5).equals(N1), "");
        boolean lanza = false;
        try { N1.conAjuste(1.5); } catch (IllegalArgumentException e) { lanza = true; }
        comprueba("ajuste lateral fuera de [0, 1] se rechaza", lanza, "");
        // ajuste del arco polar normal -> contra
        Rellenos.Lateral arco = N1.conAjusteArco(0.25);
        boolean okArco = true;
        for (int i = 0; i <= 20; i++) {
            double l = -1 + 2 * i / 20.0;
            if (maxCanal(arco.enDisco(l, 0, M), arco.en((l + 1) / 2, M)) > 1) okArco = false;   // la costura no cambia
        }
        comprueba("ajuste del arco: el cuerpo y la tapa siguen casando en la costura", okArco, "");
        int punta = arco.enDisco(0, 1, M);
        int esperado = M.mezclar(N1.normal(), N1.contra(), Math.pow(0.5, 2 * 0.25));
        comprueba("ajuste del arco 0.25: la punta es la mezcla al 71 % (0.5^(2s)), no la media",
                maxCanal(punta, esperado) <= 1 && maxCanal(punta, N1.enDisco(0, 1, M)) > 10,
                String.format("%08X frente a %08X", punta, N1.enDisco(0, 1, M)));
        comprueba("ajuste del arco 0.5 no cambia nada", N1.conAjusteArco(0.5).equals(N1), "");
        boolean lanza2 = false;
        try { N1.conAjusteArco(-0.1); } catch (IllegalArgumentException e) { lanza2 = true; }
        comprueba("ajuste del arco fuera de [0, 1] se rechaza", lanza2, "");
    }

    // ---------- Costura ----------

    static void costura() {
        Gota g = Gota.curva(p(60, 70), 24, new Gota.Vec(110, 0), p(230, 240), 58, new Gota.Vec(0, -120),
                Gota.Perfil.recto().aspecto(1.6, 0.6));
        Gota.Relleno rel0 = Rellenos.cadenaLateral(N1, N2, Rellenos.LCH).para(g);
        double c1 = g.corte1(), c3 = g.corte3();
        int maxA = 0, maxB = 0;
        Gota.Muestra m0 = new Gota.Muestra(0, 0.5, 0, 0);
        for (int i = 0; i <= 200; i++) {
            double u = i / 200.0;
            maxA = Math.max(maxA, maxCanal(rel0.argb(m0, new Gota.Uv(u, c1)), rel0.argb(m0, new Gota.Uv(u, c1 - 1e-9))));
            maxB = Math.max(maxB, maxCanal(rel0.argb(m0, new Gota.Uv(u, c3)), rel0.argb(m0, new Gota.Uv(u, c3 + 1e-9))));
        }
        comprueba("costura: el color casa en v1 y v3 para todo u (elipses con pendiente de radio)", maxA <= 1 && maxB <= 1, "saltos " + maxA + " / " + maxB);
        // la textura de referencia es discontinua en los cortes a propósito (cuatro fondos): se usa una continua
        Texturas.Textura tex = (u, v) -> 0xFF000000 | ((int) (255 * Math.min(1, Math.max(0, u))) << 16)
                | ((int) (255 * Math.min(1, Math.max(0, v))) << 8) | (int) (255 * Math.min(1, Math.max(0, 0.5 * u + 0.5 * v)));
        Gota.Relleno tr = Rellenos.tira(tex).para(g);
        int mt = 0;
        for (int i = 0; i <= 200; i++) {
            double u = i / 200.0;
            mt = Math.max(mt, maxCanal(tr.argb(m0, new Gota.Uv(u, g.corte1())), tr.argb(m0, new Gota.Uv(u, g.corte1() - 1e-9))));
            mt = Math.max(mt, maxCanal(tr.argb(m0, new Gota.Uv(u, g.corte3())), tr.argb(m0, new Gota.Uv(u, g.corte3() + 1e-9))));
        }
        comprueba("costura (textura): sin salto en v1 y v3", mt <= 2, "salto máx " + mt);

        // en la imagen real: saltos entre píxeles vecinos a ambos lados de la costura
        int W = 320, H = 320;
        for (Gota gg : new Gota[] { g, Gota.curva(p(60, 70), 24, new Gota.Vec(110, 0), p(230, 240), 58, new Gota.Vec(0, -120), Gota.Perfil.recto()) }) {
            BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            gg.render(im, Rellenos.cadenaLateral(N1, N2, Rellenos.RGB));
            int[] d = datos(im);
            int mx = 0;
            for (int j = 0; j < H; j++)
                for (int i = 0; i + 1 < W; i++) {
                    if (gg.sdf(i + 0.5, j + 0.5) > -3 || gg.sdf(i + 1.5, j + 0.5) > -3) continue;
                    double va = gg.uv(i + 0.5, j + 0.5).v(), vb = gg.uv(i + 1.5, j + 0.5).v();
                    boolean costura = (va < gg.corte1()) != (vb < gg.corte1()) || (va > gg.corte3()) != (vb > gg.corte3());
                    if (costura) mx = Math.max(mx, maxCanal(d[j * W + i], d[j * W + i + 1]));
                }
            comprueba("imagen " + (gg.esElipse() ? "elíptica" : "circular") + " con pendiente de radio: salto en la costura <= 40",
                    mx <= 40, "salto máx entre vecinos " + mx);
        }
    }

    // ---------- Caso básico: r1 = 3, r2 = 2, distancia 7 (a escala E), seis colores, sin extremo ----------

    static void casoBasico() throws Exception {
        double E = 40, r1 = 3 * E, r2 = 2 * E, d = 7 * E, cx1 = 150, cy = 170;
        Gota g = Gota.lineal(p(cx1, cy), r1, p(cx1 + d, cy), r2, Gota.Perfil.recto());
        Rellenos.Mezcla M = Rellenos.RGB;
        Gota.Relleno rel = Rellenos.cadenaLateral(N1, N2, M);
        BufferedImage im = new BufferedImage(560, 340, BufferedImage.TYPE_INT_ARGB);
        g.render(im, rel);
        int[] px = datos(im);
        // tangente exterior del lado +N (arriba en pantalla, N = (0, -1)): n·(C2 - C1) = r1 - r2 con n = (1/7, -sqrt(1 - 1/49))
        double nx = (r1 - r2) / d, ny = -Math.sqrt(1 - nx * nx);
        double[][] c = { { cx1, cy }, { cx1 + d, cy } };
        double[] r = { r1, r2 };
        Rellenos.Lateral[] lat = { N1, N2 };
        boolean ok = true;
        StringBuilder det = new StringBuilder();
        for (int k = 0; k < 2; k++) {
            for (int lado = 0; lado < 2; lado++) {     // 0: +N (normal), 1: -N (contra)
                double sg = lado == 0 ? 1 : -1;
                double qx = c[k][0] + (r[k] - 2) * nx, qy = c[k][1] + sg * (r[k] - 2) * ny;   // junto al punto de tangencia
                int col = px[(int) qy * 560 + (int) qx];
                int esp = M.mezclar(lat[k].centro(), lado == 0 ? lat[k].normal() : lat[k].contra(), (r[k] - 2) / r[k]);
                int e = maxCanal(col, esp);
                if (e > 14) ok = false;
                det.append(String.format(" %s%d:%d", lado == 0 ? "N" : "K", k + 1, e));
            }
            // la punta (extremo de cada círculo): la media de normal y contra, mezclada con el centro por s
            double sgn = k == 0 ? -1 : 1, s = (r[k] - 2) / r[k];
            int col = px[(int) cy * 560 + (int) (c[k][0] + sgn * (r[k] - 2))];
            int esp = M.mezclar(lat[k].centro(), M.mezclar(lat[k].normal(), lat[k].contra(), 0.5), s);
            int e = maxCanal(col, esp);
            if (e > 14) ok = false;
            det.append(String.format(" e%d:%d", k + 1, e));
        }
        comprueba("caso básico (r 3 y 2, distancia 7): colores junto a las tangencias y las puntas (error <= 14)", ok, det.toString().trim());
        // imagen: sola y con los radios hasta los puntos de tangencia (donde la tapa polar da paso a las secciones cuadriláteras)
        BufferedImage out = new BufferedImage(1120, 340, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(datos(out), 0xFFF4F1EA);
        java.awt.Graphics2D g2 = out.createGraphics();
        g2.drawImage(im, 0, 0, null);
        g2.drawImage(im, 560, 0, null);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new java.awt.Color(0, 0, 0, 150));
        g2.setStroke(new java.awt.BasicStroke(1.2f, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER, 10, new float[] { 5, 4 }, 0));
        for (int k = 0; k < 2; k++)
            for (int lado = -1; lado <= 1; lado += 2) {
                double sg = lado;
                g2.drawLine(560 + (int) c[k][0], (int) c[k][1], 560 + (int) (c[k][0] + r[k] * nx), (int) (c[k][1] + sg * r[k] * ny));
            }
        g2.drawLine(560 + (int) c[0][0], (int) cy, 560 + (int) c[1][0], (int) cy);
        g2.dispose();
        ImageIO.write(out, "png", new File("caso_basico.png"));
    }

    // ---------- Composición ----------

    static void composicion() throws Exception {
        Composicion[] cs = { ComposicionPruebas.cadena(), ComposicionPruebas.estrella(0) };
        int W = 440, H = 400, FONDO = 0xFFF4F1EA;
        BufferedImage out = new BufferedImage(W * 2, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(datos(out), FONDO);
        for (int c = 0; c < 2; c++) {
            BufferedImage panel = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            java.util.Arrays.fill(datos(panel), FONDO);
            cs[c].render(panel);
            out.getGraphics().drawImage(panel, c * W, 0, null);
        }
        // textura sobre la composición: misma silueta que con color (la gota de menor rho manda en cada píxel)
        // (opaca: la de referencia es transparente fuera de la cápsula y cambiaría la cobertura)
        Texturas.Textura tex = (u, v) -> 0xFF000000 | ((int) (255 * Math.min(1, Math.max(0, u))) << 8);
        for (int c = 0; c < 2; c++) {
            BufferedImage a = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB), b = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            cs[c].render(a);
            cs[c].render(b, tex);
            int[] da = datos(a), db = datos(b);
            int difAlfa = 0, pintados = 0;
            for (int k = 0; k < da.length; k++) {
                if ((da[k] >>> 24) != (db[k] >>> 24)) difAlfa++;
                if ((db[k] >>> 24) != 0) pintados++;
            }
            comprueba("composición " + (c == 0 ? "cadena" : "estrella") + ": render con textura tiene la misma silueta que con color",
                    difAlfa == 0 && pintados > 1000, "píxeles " + pintados + ", alfa distinta " + difAlfa);
        }
        boolean lanza = false;
        try { cs[0].render(new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB), null); } catch (NullPointerException e) { lanza = true; }
        comprueba("composición: render con textura null se rechaza", lanza, "");
        ImageIO.write(out, "png", new File("tapas_disco.png"));
        comprueba("composición: cadena y estrella se renderizan con tapas de semidisco (tapas_disco.png)", true, "");
    }

    public static void main(String[] args) throws Exception {
        identidadTextura();
        identidadColor();
        ajusteLateral();
        costura();
        casoBasico();
        composicion();
        System.out.printf("%n%d pruebas, %d fallos%n", pruebas, fallos);
        if (fallos > 0) System.exit(1);
    }
}
