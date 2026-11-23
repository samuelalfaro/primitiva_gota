package coordenadas;

import color.Colores;
import color.Mezcla;
import color.Relleno;
import color.Texturas;
import forma.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import pintado.*;
import common.SuiteMatriz00;

/**
 * Textura de referencia de los estudios (rama estudio_pliegues_polar, issue #29): {@link GuiaAjedrez}, usada como {@code EstudioUV.Guia}.
 *
 * <p>Textura de 16 x 32 unidades definida por la distancia d a la espina (segmento entre los centros (8, 8) y (8, 24)):
 * <ul><li>d &lt;= 2 y 6 &lt; d &lt;= 8: sólido, con los colores de zona (pasteles por cuarto de v y lado de u).</li>
 *   <li>2 &lt; d &lt;= 6: degradado generado por la propia gota (recta, radio 6, centros (8, 8) y (8, 24)) y reutilizado como textura. Sus seis colores son los
 *     pasteles de las tapas con la saturación subida a 0,75 (extremos) y un gris neutro en el centro de cada nodo, interpolados en LCH por el arco corto del tono:
 *     el gris no tiene tono, así que cada lado interpola hacia él con el suyo y no hay saltos.</li>
 *   <li>Encima, ajedrez que oscurece un 10 % las celdas impares: celdas de 2 unidades en el cuerpo; en las tapas, anillos de 2 y sectores de 18° (10 por media tapa:
 *     el eje cae en un borde de sector y la alternancia cruza la costura); la tapa de arriba lleva desfase 1 para alternar con el cuerpo.</li></ul>
 *
 * <p>Qué delata: el ajedrez, la densidad de texels; el degradado, la continuidad de u/v; los sólidos, la pertenencia a cada zona.
 * Uso: EstudioGuia (genera estudio_guia_textura.png y estudio_guia_t3b_*.png).
 */
public class EstudioGuia {
    static final int[] IZQ = { 0xFFFFE0B2, 0xFFB3E5FC, 0xFFC8E6C9, 0xFFF8BBD0 }, DER = { 0xFFFF9E80, 0xFF80D8FF, 0xFF81C784, 0xFFCE93D8 };
    static final double SATURACION = 0.75, OSCURO = 0.10;
    static final int GRIS = 0xFFD0D0D0;

    /** Mismo matiz con la saturación subida al menos a sat (y brillo alto). */
    static int satura(int c, double sat) {
        float[] hsb = Color.RGBtoHSB((c >> 16) & 255, (c >> 8) & 255, c & 255, null);
        return 0xFF000000 | (Color.HSBtoRGB(hsb[0], (float) Math.max(hsb[1], sat), Math.max(hsb[2], 0.95f)) & 0xFFFFFF);
    }

    /** Textura de 16 x 32 unidades: la gota de radio 6 con centros (8, 8) y (8, 24), a 16 px por unidad, rellena con los seis colores de la guía. */
    static Texturas.Textura degradadoDeGota() {
        int k = 16, w = 16 * k, h = 32 * k;
        Relleno rel = Colores.cadenaLateral(
                new Colores.Lateral(satura(IZQ[0], SATURACION), GRIS, satura(DER[0], SATURACION)),      // nodo 1 (arriba): colores de la tapa de arriba
                new Colores.Lateral(satura(IZQ[3], SATURACION), GRIS, satura(DER[3], SATURACION)), Mezcla.LCH);
        BufferedImage im = EstudioUV.lienzo(w, h);
        for (int i = 0; i < w * h; i++) im.getRaster().getDataBuffer().setElem(i, 0);      // transparente fuera de la gota
        Gota g = Gota.lineal(new Point2D.Double(8 * k, 8 * k), 6 * k, new Point2D.Double(8 * k, 24 * k), 6 * k, Perfil.recto());
        Plotter.raster(im).plot(g, rel, SuiteMatriz00.OPCIONES);
        return Texturas.deImagen(im);
    }

    public static final class GuiaAjedrez implements Texturas.Textura {
        final Texturas.Textura grad = degradadoDeGota();
        @Override public double aspecto() { return 0.5; }
        @Override public int muestrea(double u, double v) {
            double x = u * 16, y = v * 32, dx = x - 8, dy = y - Math.max(8, Math.min(24, y)), d = Math.hypot(dx, dy);
            if (d > 8) return 0;                                                  // fuera de la cápsula
            int c = d > 2 && d <= 6 ? grad.muestrea(u, v) : (u < 0.5 ? IZQ : DER)[v < 0.25 ? 0 : v < 0.5 ? 1 : v < 0.75 ? 2 : 3];
            int par;
            if (y < 8 || y > 24) par = ((int) Math.floor(d / 2) + (int) Math.floor(Math.atan2(Math.abs(dy), dx) / Math.toRadians(18)) + (y < 8 ? 1 : 0)) & 1;
            else par = ((int) Math.floor(x / 2) + (int) Math.floor((y - 8) / 2)) & 1;
            if (par == 0) return c | 0xFF000000;
            int rgb = 0; for (int sh = 16; sh >= 0; sh -= 8) rgb |= (int) Math.round(((c >> sh) & 255) * (1 - OSCURO)) << sh;
            return 0xFF000000 | rgb;
        }
    }

    public static void main(String[] a) throws Exception {
        Texturas.Textura t = new GuiaAjedrez();
        int W = 300, H = 600;
        BufferedImage tx = EstudioUV.lienzo(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int c = t.muestrea((x + .5) / W, (y + .5) / H);
            if ((c >>> 24) != 0) tx.setRGB(x, y, 0xFF000000 | c);
        }
        ImageIO.write(tx, "png", new java.io.File("estudio_guia_textura.png"));
        double r = 1.39; EstudioPliegueDos.ajusta(r, new double[]{-0.28,-2.39,-2.98,1.88,-1.45,-1.6,-2.26,1.54}, "t3b");
        Gota g = EstudioPliegueDos.gota(r, r);
        Relleno rel = Texturas.tira(t);
        for (Costura cos : new Costura[]{Costura.ESPIGA, Costura.ORTOGONAL}) {
            Opciones base = Opciones.POR_DEFECTO.conCostura(cos);
            BufferedImage x1 = EstudioUV.lienzo(EstudioPliegueDos.CW, EstudioPliegueDos.CH), x2 = EstudioUV.lienzo(EstudioPliegueDos.CW, EstudioPliegueDos.CH);
            Plotter.raster(x1).plot(g, rel, base.conSolape(Solape.TRAZO));
            Plotter.raster(x2).plot(g, rel, base.conSolape(Solape.PLIEGUE));
            BufferedImage img = new BufferedImage(2 * EstudioPliegueDos.CW, EstudioPliegueDos.CH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = img.createGraphics(); g2.drawImage(x1, 0, 0, null); g2.drawImage(x2, EstudioPliegueDos.CW, 0, null);
            g2.setColor(Color.DARK_GRAY); g2.drawString("TRAZO", 6, 14); g2.drawString("PLIEGUE " + cos, EstudioPliegueDos.CW + 6, 14); g2.dispose();
            ImageIO.write(img, "png", new java.io.File("estudio_guia_t3b_" + cos.name().toLowerCase() + ".png"));
        }
    }
}
