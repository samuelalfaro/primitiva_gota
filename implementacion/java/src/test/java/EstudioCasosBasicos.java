/**
 * Caso de estudio (rama estudio_solapes; no está en Todas): suite de casos básicos del solape, con camino regular
 * (sin cúspides: ver «Caso límite» en ESTUDIO_SOLAPES.md). Tres filas por familia: textura guía, signo de J por hebra
 * (verde: solape con J > 0 en todas las hebras; rojo: alguna hebra con J <= 0) y mapa de f (corte -Destudio.fcorte, 0.6).
 * Familias: alfa abierta (solo solape legítimo), u cerrada (solape legítimo + abanico de la curva cerrada),
 * omega sin cruce (solape sin cruce) y codo (compresión y, con radios distintos, pliegue).
 * Uso: EstudioCasosBasicos [familia: alfa|u|omega|codo|todas]. Escribe estudio_casos_basicos_<familia>.png.
 */
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public class EstudioCasosBasicos {
    static final double E = 34;                 // px por unidad
    static final int CW = 300, CH = 260;
    static final String[] FAMILIAS = {"alfa", "u", "omega", "codo"};

    /** Gota con centros y tiradores en unidades (y hacia arriba), origen en (ox, oy) px. */
    static Gota gota(double[] c1, double r1, double[] v1, double[] c2, double r2, double[] v2, double ox, double oy) {
        return Gota.curva(new Point2D.Double(ox + c1[0] * E, oy - c1[1] * E), r1 * E, new Gota.Vec(v1[0] * E, -v1[1] * E),
                          new Point2D.Double(ox + c2[0] * E, oy - c2[1] * E), r2 * E, new Gota.Vec(v2[0] * E, -v2[1] * E), Perfil.recto());
    }
    /** α abierta: lazo de tamaño k (evitar k = 2,0: cúspide). */
    static Gota alfa(double k, double ox, double oy) {
        return gota(new double[]{-1, 0}, 1, new double[]{k, 0.9 * k}, new double[]{1, 0}, 1, new double[]{-k, 0.9 * k}, ox, oy);
    }
    /** u cerrada: separación w de los extremos; el camino sube y vuelve. */
    static Gota u(double w, double ox, double oy) {
        return gota(new double[]{-w / 2, 0}, 1, new double[]{0, 3.2}, new double[]{w / 2, 0}, 1, new double[]{0, 3.2}, ox, oy);
    }
    /** ω sin cruce: C casi cerrada; phi = media apertura en grados. */
    static Gota omega(double phi, double ox, double oy) {
        double R = 2.4, p = Math.toRadians(phi), L = 1.9 * R * 0.55;
        return gota(new double[]{R * Math.cos(p), R * Math.sin(p)}, 1, new double[]{-L * Math.sin(p), L * Math.cos(p)},
                    new double[]{R * Math.cos(p), -R * Math.sin(p)}, 1, new double[]{-L * Math.sin(p), -L * Math.cos(p)}, ox, oy);
    }
    /** Codo: de (-2,5, 0) a (0, 2,5), tiradores a; radios r1, r2. */
    static Gota codo(double a, double r1, double r2, double ox, double oy) {
        return gota(new double[]{-2.5, 0}, r1, new double[]{a, 0}, new double[]{0, 2.5}, r2, new double[]{0, -a}, ox, oy);
    }

    record Caso(String etiqueta, Gota g) {}

    static Caso[] casos(String fam) {
        double ox = CW / 2.0;
        switch (fam) {
            case "alfa": { double[] ks = {1.2, 1.6, 2.4, 2.8}; Caso[] c = new Caso[ks.length];
                for (int i = 0; i < ks.length; i++) c[i] = new Caso("alfa k=" + ks[i], alfa(ks[i], ox, CH * 0.55)); return c; }
            case "u": { double[] ws = {2.4, 1.6, 1.0, 0.6}; Caso[] c = new Caso[ws.length];
                for (int i = 0; i < ws.length; i++) c[i] = new Caso("u w=" + ws[i], u(ws[i], ox, CH * 0.8)); return c; }
            case "omega": { double[] ph = {50, 35, 25, 15}; Caso[] c = new Caso[ph.length];
                for (int i = 0; i < ph.length; i++) c[i] = new Caso("omega phi=" + ph[i], omega(ph[i], ox - 30, CH / 2.0)); return c; }
            case "codo": { double[][] p = {{3.5, 1.6, 1.6}, {3.5, 2.0, 2.0}, {3.5, 1.6, 0.4}, {3.5, 2.0, 0.8}}; Caso[] c = new Caso[p.length];
                for (int i = 0; i < p.length; i++) c[i] = new Caso(String.format("codo a=%.1f r1=%.1f r2=%.1f", p[i][0], p[i][1], p[i][2]), codo(p[i][0], p[i][1], p[i][2], CW * 0.62, CH * 0.62)); return c; }
            default: throw new IllegalArgumentException(fam);
        }
    }

    static void familia(String fam) throws Exception {
        Caso[] cs = casos(fam); int paso = 2;
        Relleno rel = Texturas.tira(new EstudioUV.Guia()); Opciones op = SuiteMatriz_03_Colores.OPCIONES.conSolape(Solape.TRAZO);
        BufferedImage img = new BufferedImage(cs.length * CW, 3 * CH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics(); g2.setColor(Color.WHITE); g2.fillRect(0, 0, img.getWidth(), img.getHeight());
        System.out.println(fam + ": caso | px solape | % J<=0 | % f<=corte | % sin conexión");
        for (int i = 0; i < cs.length; i++) {
            Gota gt = cs[i].g();
            BufferedImage tex = EstudioUV.lienzo(CW, CH), sg = EstudioUV.lienzo(CW, CH), mf = EstudioUV.lienzo(CW, CH);
            Plotter.raster(tex).plot(gt, rel, op);
            double[] fm = EstudioMapaCorte.mapa(gt, CW, CH, 0.03, paso); long n = 0, nj = 0, nf = 0, nn = 0;
            for (int y = 0; y < CH; y++) for (int x = 0; x < CW; x++) {
                var hs = gt.mascara().hebras(x + .5, y + .5); if (hs.isEmpty()) continue;
                int k = 0; boolean neg = false;
                for (var h : hs) { if (h.cobertura() <= 0) continue; k++; if (EstudioSignoJ.Jh(gt, h.t(), x + .5, y + .5) <= 0) neg = true; }
                int cj = 0xEEEEEE, cm = 0xEEEEEE;
                if (k >= 2) {
                    n++; if (neg) nj++; cj = neg ? 0xC62828 : 0x2E7D32;
                    double v = fm[(y - y % paso) * CW + (x - x % paso)];
                    if (!Double.isNaN(v)) { cm = EstudioMapaCorte.color(v); if (Double.isInfinite(v) || v > EstudioMapaCorte.CORTE) nn++; else nf++; }
                }
                sg.setRGB(x, y, 0xFF000000 | cj); mf.setRGB(x, y, 0xFF000000 | cm);
            }
            System.out.printf("  %-28s %6d | %5.1f %% | %5.1f %% | %5.1f %%%n", cs[i].etiqueta(), n, n == 0 ? 0 : 100.0 * nj / n, n == 0 ? 0 : 100.0 * nf / n, n == 0 ? 0 : 100.0 * nn / n);
            BufferedImage[] v = {tex, sg, mf};
            for (int r = 0; r < 3; r++) {
                Graphics2D gc = v[r].createGraphics(); PlotterEsquema.plot(gc, gt); gc.dispose();
                g2.drawImage(v[r], i * CW, r * CH, null); g2.setColor(new Color(0xCCCCCC)); g2.drawRect(i * CW, r * CH, CW - 1, CH - 1);
            }
            g2.setColor(Color.DARK_GRAY); g2.drawString(cs[i].etiqueta(), i * CW + 8, 16);
        }
        g2.dispose(); ImageIO.write(img, "png", new java.io.File("estudio_casos_basicos_" + fam + ".png"));
    }

    public static void main(String[] a) throws Exception {
        String f = a.length > 0 ? a[0] : "todas";
        if (f.equals("todas")) for (String s : FAMILIAS) familia(s); else familia(f);
    }
}
