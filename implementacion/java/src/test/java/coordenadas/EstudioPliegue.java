package coordenadas;

/**
 * Caso de estudio (rama estudio_pliegues_polar, issue #29; no está en Todas): el codo (costura ESPIGA por defecto o ORTOGONAL, argumento) con el solape TRAZO
 * (arriba, con el pliegue) y con Solape.PLIEGUE (abajo: la zona de pliegue en polares con centro en el cruce del contorno interior).
 * Escribe estudio_pliegue_polar_<costura>.png y, por caso, el cruce (ta, tb, X) y las diferencias de color entre los dos solapes.
 */
import color.Relleno;
import color.Texturas;
import forma.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import pintado.*;

public class EstudioPliegue {
    static final double E = EstudioCasosBasicos.E;
    static final int CW = 300, CH = 260;

    public static void main(String[] args) throws Exception {
        double[][] p = {{3.5, 1.6, 1.6}, {3.5, 1.6, 1.0}, {3.5, 1.0, 1.6}, {2.5, 1.6, 1.6}, {2.5, 1.6, 1.0}};
        Relleno rel = Texturas.tira(new EstudioUV.Guia());
        String cos = args.length > 0 ? args[0].toUpperCase() : "ESPIGA";
        Opciones base = Opciones.POR_DEFECTO.conCostura(Costura.valueOf(cos));
        BufferedImage img = new BufferedImage(p.length * CW, 2 * CH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics(); g2.setColor(Color.WHITE); g2.fillRect(0, 0, img.getWidth(), img.getHeight());
        System.out.println("caso | zona de pliegue | px cambiados | % de la gota");
        for (int i = 0; i < p.length; i++) {
            Gota g = EstudioCasosBasicos.codo(p[i][0], p[i][1], p[i][2], CW * 0.62, CH * 0.62);
            String et = String.format("codo a=%.1f r1=%.1f r2=%.1f", p[i][0], p[i][1], p[i][2]);
            BufferedImage a = EstudioUV.lienzo(CW, CH), b = EstudioUV.lienzo(CW, CH);
            Plotter.raster(a).plot(g, rel, base.conSolape(Solape.TRAZO));
            Plotter.raster(b).plot(g, rel, base.conSolape(Solape.PLIEGUE));
            long dentro = 0, cambia = 0;
            for (int y = 0; y < CH; y++) for (int x = 0; x < CW; x++) {
                if (!g.mascara().contiene(x + .5, y + .5)) continue;
                dentro++;
                if (a.getRGB(x, y) != b.getRGB(x, y)) cambia++;
            }
            Pliegue z = Coordenadas.de(g, base.conSolape(Solape.PLIEGUE)).zonaPliegue();
            System.out.printf("  %-24s | %s | %6d | %5.1f %%%n", et, z == null ? "ninguna" : z, cambia, 100.0 * cambia / Math.max(1, dentro));
            BufferedImage[] v = {a, b};
            for (int r = 0; r < 2; r++) {
                Graphics2D gc = v[r].createGraphics(); PlotterAlambre.plot(gc, g, Trazos.Ajustes.ESQUEMA);
                if (z != null) { gc.setColor(Color.WHITE); gc.fillOval((int) z.xx - 3, (int) z.xy - 3, 7, 7); gc.setColor(Color.BLACK); gc.drawOval((int) z.xx - 3, (int) z.xy - 3, 7, 7); }
                gc.dispose();
                g2.drawImage(v[r], i * CW, r * CH, null); g2.setColor(new Color(0xCCCCCC)); g2.drawRect(i * CW, r * CH, CW - 1, CH - 1);
            }
            g2.setColor(Color.DARK_GRAY); g2.drawString(et + (z == null ? "" : String.format("  [ta %.2f, tb %.2f]", z.ta, z.tb)), i * CW + 8, 16);
        }
        g2.drawString("TRAZO", 8, CH - 6); g2.drawString("PLIEGUE", 8, 2 * CH - 6);
        g2.dispose(); ImageIO.write(img, "png", new java.io.File("estudio_pliegue_polar_" + cos.toLowerCase() + ".png"));
    }
}
