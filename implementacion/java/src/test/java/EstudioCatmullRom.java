/**
 * Exploración de curvas de Catmull-Rom frente a la Bézier cúbica de la espina.
 *
 * Catmull-Rom interpola los puntos de control (pasa por ellos) y es C1 local.
 * La espina de Gota es una Bézier cúbica: solo interpola los extremos; los dos
 * tiradores tiran de la tangente sin estar sobre la curva.
 *
 * Filas:
 *   1. Polilínea de control + Catmull-Rom (uniforme) con muestras equiespaciadas en t
 *   2. Misma CR con muestras equiespaciadas en longitud de arco
 *   3. Bézier cúbica "equivalente" (extremos + tiradores a 1/3 del chord) en t
 *   4. Misma Bézier por longitud de arco
 *
 * Columnas: tres formas (S suave, S marcada, lazo tipo ჲ) definidas por 4 puntos.
 *
 * Uso: EstudioCatmullRom
 * Escribe estudio/catmull_rom.png
 */
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class EstudioCatmullRom {

    static final int CW = 320, CH = 240;
    static final int N_SAMPLES = 14;
    static final int ARC_RES = 400;

    static final double[][][] FORMAS = {
        { {40,120}, {100,60}, {180,180}, {260,100} },
        { {40,140}, {90,40}, {200,200}, {280,60} },
        { {60,160}, {220,40}, {220,200}, {80,100} },
    };
    static final String[] NOMBRES = { "S suave", "S marcada", "lazo (ჲ)" };

    public static void main(String[] args) throws Exception {
        String[] filas = {
            "CR \u00b7 t equidistante",
            "CR \u00b7 longitud de arco",
            "B\u00e9zier \u00b7 t equidistante",
            "B\u00e9zier \u00b7 longitud de arco"
        };
        int W = 90 + FORMAS.length * CW;
        int H = 28 + filas.length * CH;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, W, H);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (int col = 0; col < FORMAS.length; col++) {
            double[][] pts = FORMAS[col];
            double[][] bezier = aBezier(pts);

            for (int row = 0; row < 4; row++) {
                BufferedImage cell = new BufferedImage(CW, CH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = cell.createGraphics();
                g.setColor(new Color(0xFAFAFA));
                g.fillRect(0, 0, CW, CH);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                boolean cr = row < 2;
                boolean porArco = (row % 2) == 1;

                g.setColor(new Color(0xB0BEC5));
                g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        10f, new float[]{4f, 4f}, 0f));
                for (int i = 0; i < pts.length - 1; i++)
                    g.draw(new Line2D.Double(pts[i][0], pts[i][1], pts[i+1][0], pts[i+1][1]));

                Path2D path = cr ? pathCR(pts) : pathBezier(bezier);
                g.setColor(new Color(255,255,255,180));
                g.setStroke(new BasicStroke(4.5f));
                g.draw(path);
                g.setColor(cr ? new Color(0x1565C0) : new Color(0x6A1B9A));
                g.setStroke(new BasicStroke(2.0f));
                g.draw(path);

                double[][] samples = porArco
                    ? (cr ? samplesCRArc(pts, N_SAMPLES) : samplesBezierArc(bezier, N_SAMPLES))
                    : (cr ? samplesCRt(pts, N_SAMPLES) : samplesBeziert(bezier, N_SAMPLES));

                for (int i = 0; i < samples.length; i++) {
                    double u = i / (double) (samples.length - 1);
                    int alpha = (int) (80 + 160 * (1 - 4*u*(1-u)));
                    Color c = new Color(0x00, 0x96, 0x88, Math.min(255, alpha));
                    double r = 3.2;
                    g.setColor(Color.WHITE);
                    g.fill(new Ellipse2D.Double(samples[i][0]-r-1.2, samples[i][1]-r-1.2, 2*(r+1.2), 2*(r+1.2)));
                    g.setColor(c);
                    g.fill(new Ellipse2D.Double(samples[i][0]-r, samples[i][1]-r, 2*r, 2*r));
                }

                for (int i = 0; i < pts.length; i++) {
                    double r = (i == 0 || i == pts.length-1) ? 5.0 : 4.0;
                    g.setColor(Color.WHITE);
                    g.fill(new Ellipse2D.Double(pts[i][0]-r-1.5, pts[i][1]-r-1.5, 2*(r+1.5), 2*(r+1.5)));
                    g.setColor((i == 0 || i == pts.length-1) ? new Color(0x212121) : new Color(0xF9A825));
                    g.fill(new Ellipse2D.Double(pts[i][0]-r, pts[i][1]-r, 2*r, 2*r));
                }

                if (!cr) {
                    g.setColor(new Color(0xAB47BC));
                    g.setStroke(new BasicStroke(1.0f));
                    g.draw(new Line2D.Double(bezier[0][0], bezier[0][1], bezier[1][0], bezier[1][1]));
                    g.draw(new Line2D.Double(bezier[2][0], bezier[2][1], bezier[3][0], bezier[3][1]));
                    for (int i = 1; i <= 2; i++) {
                        g.setColor(Color.WHITE);
                        g.fill(new Ellipse2D.Double(bezier[i][0]-3.5, bezier[i][1]-3.5, 7, 7));
                        g.setColor(new Color(0x8E24AA));
                        g.fill(new Ellipse2D.Double(bezier[i][0]-2.5, bezier[i][1]-2.5, 5, 5));
                    }
                }

                g.dispose();
                int x0 = 90 + col * CW, y0 = 28 + row * CH;
                g2.drawImage(cell, x0, y0, null);
                g2.setColor(new Color(0xCFD8DC));
                g2.drawRect(x0, y0, CW-1, CH-1);
            }
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(NOMBRES[col], 90 + col * CW + 10, 18);
        }
        for (int row = 0; row < filas.length; row++) {
            g2.setColor(Color.DARK_GRAY);
            g2.setFont(g2.getFont().deriveFont(11f));
            String[] parts = filas[row].split(" \u00b7 ");
            g2.drawString(parts[0], 6, 28 + row * CH + 20);
            if (parts.length > 1) g2.drawString(parts[1], 6, 28 + row * CH + 36);
        }
        g2.setColor(new Color(0x666666));
        g2.setFont(g2.getFont().deriveFont(10f));
        g2.drawString("negro/\u00e1mbar = puntos de control   azul = Catmull-Rom   morado = B\u00e9zier c\u00fabica   cian = muestras", 6, H - 6);
        g2.dispose();

        File out = new File("estudio/catmull_rom.png");
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Escrito " + out.getPath());
    }

    static double[] catmull(double[] p0, double[] p1, double[] p2, double[] p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        double x = 0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t
                + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2
                + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3);
        double y = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t
                + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2
                + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3);
        return new double[] { x, y };
    }

    static Path2D pathCR(double[][] pts) {
        Path2D p = new Path2D.Double();
        boolean first = true;
        for (int i = 0; i < pts.length - 1; i++) {
            double[] a = pts[Math.max(0, i - 1)];
            double[] b = pts[i];
            double[] c = pts[i + 1];
            double[] d = pts[Math.min(pts.length - 1, i + 2)];
            for (int k = 0; k <= 40; k++) {
                double[] q = catmull(a, b, c, d, k / 40.0);
                if (first) { p.moveTo(q[0], q[1]); first = false; }
                else p.lineTo(q[0], q[1]);
            }
        }
        return p;
    }

    static double[][] samplesCRt(double[][] pts, int n) {
        double[][] out = new double[n][];
        int segs = pts.length - 1;
        for (int i = 0; i < n; i++) {
            double u = i / (double) (n - 1) * segs;
            int s = Math.min(segs - 1, (int) u);
            double t = u - s;
            double[] a = pts[Math.max(0, s - 1)];
            double[] b = pts[s];
            double[] c = pts[s + 1];
            double[] d = pts[Math.min(pts.length - 1, s + 2)];
            out[i] = catmull(a, b, c, d, t);
        }
        return out;
    }

    static double[][] denseCR(double[][] pts) {
        java.util.List<double[]> list = new java.util.ArrayList<>();
        for (int i = 0; i < pts.length - 1; i++) {
            double[] a = pts[Math.max(0, i - 1)];
            double[] b = pts[i];
            double[] c = pts[i + 1];
            double[] d = pts[Math.min(pts.length - 1, i + 2)];
            int m = ARC_RES / (pts.length - 1);
            for (int k = 0; k < m; k++) list.add(catmull(a, b, c, d, k / (double) m));
        }
        list.add(pts[pts.length - 1]);
        return list.toArray(new double[0][]);
    }

    static double[][] samplesCRArc(double[][] pts, int n) {
        double[][] dense = denseCR(pts);
        double[] s = new double[dense.length];
        for (int i = 1; i < dense.length; i++)
            s[i] = s[i - 1] + Math.hypot(dense[i][0] - dense[i-1][0], dense[i][1] - dense[i-1][1]);
        double total = s[s.length - 1];
        double[][] out = new double[n][];
        for (int i = 0; i < n; i++) {
            double target = total * i / (n - 1);
            int lo = 0, hi = s.length - 1;
            while (lo + 1 < hi) {
                int mid = (lo + hi) >>> 1;
                if (s[mid] < target) lo = mid; else hi = mid;
            }
            double ds = s[hi] - s[lo];
            double u = ds > 1e-12 ? (target - s[lo]) / ds : 0;
            out[i] = new double[] {
                dense[lo][0] + u * (dense[hi][0] - dense[lo][0]),
                dense[lo][1] + u * (dense[hi][1] - dense[lo][1])
            };
        }
        return out;
    }

    static double[][] aBezier(double[][] pts) {
        double[] p0 = pts[0], p1 = pts[1], p2 = pts[2], p3 = pts[3];
        double[] t0 = { (p1[0] - p0[0]), (p1[1] - p0[1]) };
        double[] t3 = { (p3[0] - p2[0]), (p3[1] - p2[1]) };
        double L = Math.hypot(p3[0]-p0[0], p3[1]-p0[1]) / 3.0;
        double n0 = Math.hypot(t0[0], t0[1]), n3 = Math.hypot(t3[0], t3[1]);
        if (n0 < 1e-9) { t0[0] = p3[0]-p0[0]; t0[1] = p3[1]-p0[1]; n0 = Math.hypot(t0[0], t0[1]); }
        if (n3 < 1e-9) { t3[0] = p3[0]-p0[0]; t3[1] = p3[1]-p0[1]; n3 = Math.hypot(t3[0], t3[1]); }
        return new double[][] {
            p0,
            { p0[0] + t0[0] / n0 * L, p0[1] + t0[1] / n0 * L },
            { p3[0] - t3[0] / n3 * L, p3[1] - t3[1] / n3 * L },
            p3
        };
    }

    static double[] bezier(double[][] b, double t) {
        double u = 1 - t;
        return new double[] {
            u*u*u*b[0][0] + 3*u*u*t*b[1][0] + 3*u*t*t*b[2][0] + t*t*t*b[3][0],
            u*u*u*b[0][1] + 3*u*u*t*b[1][1] + 3*u*t*t*b[2][1] + t*t*t*b[3][1]
        };
    }

    static Path2D pathBezier(double[][] b) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i <= 80; i++) {
            double[] q = bezier(b, i / 80.0);
            if (i == 0) p.moveTo(q[0], q[1]); else p.lineTo(q[0], q[1]);
        }
        return p;
    }

    static double[][] samplesBeziert(double[][] b, int n) {
        double[][] out = new double[n][];
        for (int i = 0; i < n; i++) out[i] = bezier(b, i / (double) (n - 1));
        return out;
    }

    static double[][] samplesBezierArc(double[][] b, int n) {
        double[][] dense = new double[ARC_RES + 1][];
        double[] s = new double[ARC_RES + 1];
        dense[0] = bezier(b, 0);
        for (int i = 1; i <= ARC_RES; i++) {
            dense[i] = bezier(b, i / (double) ARC_RES);
            s[i] = s[i-1] + Math.hypot(dense[i][0]-dense[i-1][0], dense[i][1]-dense[i-1][1]);
        }
        double total = s[ARC_RES];
        double[][] out = new double[n][];
        for (int i = 0; i < n; i++) {
            double target = total * i / (n - 1);
            int lo = 0, hi = ARC_RES;
            while (lo + 1 < hi) {
                int mid = (lo + hi) >>> 1;
                if (s[mid] < target) lo = mid; else hi = mid;
            }
            double ds = s[hi] - s[lo];
            double u = ds > 1e-12 ? (target - s[lo]) / ds : 0;
            out[i] = new double[] {
                dense[lo][0] + u * (dense[hi][0] - dense[lo][0]),
                dense[lo][1] + u * (dense[hi][1] - dense[lo][1])
            };
        }
        return out;
    }
}
