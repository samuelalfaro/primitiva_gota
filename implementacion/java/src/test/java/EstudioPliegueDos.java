/**
 * Caso de estudio (rama estudio_pliegues_polar, issue #29; no está en Todas): cúbica con dos inflexiones (hasta tres lóbulos de curvatura),
 * con el código de pliegue actual SIN cambios, en ESPIGA y ORTOGONAL. Muestra qué cruce elige Pliegue y cuáles deja sin tratar.
 * Argumento opcional: r (radio, en unidades; por defecto 0,88). Escribe estudio_pliegue_dos_<costura>.png.
 */
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public class EstudioPliegueDos {
    static double E = 70, OX = 70, OY = 90;
    static int CW = 460, CH = 330;
    static double[][] P = {{0, 0}, {3.19, -1.72}, {-0.57, -0.49}, {4.74, -2.08}};
    static String ET = "dos";

    static Gota gota(double r1, double r2) {
        double ox = OX, oy = OY;
        Point2D c1 = new Point2D.Double(ox + P[0][0] * E, oy - P[0][1] * E), c2 = new Point2D.Double(ox + P[3][0] * E, oy - P[3][1] * E);
        Gota.Vec v1 = new Gota.Vec((P[1][0] - P[0][0]) * E, -(P[1][1] - P[0][1]) * E);
        Gota.Vec v2 = new Gota.Vec((P[2][0] - P[3][0]) * E, -(P[2][1] - P[3][1]) * E);
        return Gota.curva(c1, r1 * E, v1, c2, r2 * E, v2, Perfil.recto());
    }


    /** Ajusta el lienzo y la escala a la caja de los puntos de control (x0 y0 … x3 y3) más el radio r, y fija la etiqueta de los ficheros. */
    static void ajusta(double r, double[] pts, String et) {
        P = new double[4][2]; double mnx = 1e9, mny = 1e9, mxx = -1e9, mxy = -1e9;
        for (int i = 0; i < 4; i++) { P[i][0] = pts[2 * i]; P[i][1] = pts[2 * i + 1];
            mnx = Math.min(mnx, P[i][0]); mxx = Math.max(mxx, P[i][0]); mny = Math.min(mny, P[i][1]); mxy = Math.max(mxy, P[i][1]); }
        ET = et; E = 300 / (Math.max(mxx - mnx, mxy - mny) + 2 * r); CW = (int) ((mxx - mnx + 2 * r) * E + 20); CH = (int) ((mxy - mny + 2 * r) * E + 20);
        OX = 10 + (r - mnx) * E; OY = 10 + (r + mxy) * E;
    }

    public static void main(String[] args) throws Exception {
        double r = args.length > 0 ? Double.parseDouble(args[0]) : 0.88;
        if (args.length >= 10) {
            double[] pts = new double[8]; for (int i = 0; i < 8; i++) pts[i] = Double.parseDouble(args[1 + i]);
            ajusta(r, pts, args[9]);
        }
        Relleno rel = Texturas.tira(new EstudioUV.Guia());
        for (String cos : new String[]{"ESPIGA", "ORTOGONAL"}) {
            Opciones base = Opciones.POR_DEFECTO.conCostura(Costura.valueOf(cos));
            Gota g = gota(r, r);
            BufferedImage a = EstudioUV.lienzo(CW, CH), b = EstudioUV.lienzo(CW, CH);
            Plotter.raster(a).plot(g, rel, base.conSolape(Solape.TRAZO));
            Plotter.raster(b).plot(g, rel, base.conSolape(Solape.PLIEGUE));
            java.util.List<Pliegue> zs = Coordenadas.de(g, base.conSolape(Solape.PLIEGUE)).zonasPliegue();
            System.out.printf("%s r=%.2f | zonas: %d%n", cos, r, zs.size());
            for (Pliegue z : zs) System.out.printf("    ta=%.3f tb=%.3f lado=%d X=(%.2f,%.2f)%n", z.ta, z.tb, z.lado, (z.xx - OX) / E, (OY - z.xy) / E);
            BufferedImage img = new BufferedImage(2 * CW, CH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics(); g2.setColor(Color.WHITE); g2.fillRect(0, 0, 2 * CW, CH);
            BufferedImage[] v = {a, b};
            for (int i = 0; i < 2; i++) {
                Graphics2D gc = v[i].createGraphics(); PlotterAlambre.plot(gc, g, Trazos.Ajustes.ESQUEMA);
                for (Pliegue z : zs) { gc.setColor(Color.WHITE); gc.fillOval((int) z.xx - 3, (int) z.xy - 3, 7, 7); gc.setColor(Color.BLACK); gc.drawOval((int) z.xx - 3, (int) z.xy - 3, 7, 7); }
                gc.dispose();
                g2.drawImage(v[i], i * CW, 0, null); g2.setColor(new Color(0xCCCCCC)); g2.drawRect(i * CW, 0, CW - 1, CH - 1);
            }
            g2.setColor(Color.DARK_GRAY); g2.drawString("TRAZO", 8, 16); g2.drawString("PLIEGUE  " + cos, CW + 8, 16);
            g2.dispose(); ImageIO.write(img, "png", new java.io.File("estudio_pliegue_" + ET + "_" + cos.toLowerCase() + ".png"));
        }
    }
}
