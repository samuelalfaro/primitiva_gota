import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
/**
 * Estudio (rama estudio_pliegues_polar): textura de referencia "informativa" GuiaAjedrez. Tres capas:
 * Textura de 16 x 32 unidades definida por la distancia d a la espina (segmento entre los centros (8, 8) y (8, 24)): d <= 2 sólido, 2 < d <= 6 degradado
 * de la propia gota (radio 6, vuelto a usar como textura), 6 < d <= 8 sólido; los sólidos son los colores de zona de {@link Texturas#referenciaLados()}; encima, ajedrez.
 * Uso: EstudioGuia  (genera estudio_guia_textura.png y estudio_guia_t3b_*.png)
 */
public class EstudioGuia {
    /** Textura de 16 x 32 unidades: degradado de una gota recta de radio 6 con centros en (8, 8) y (8, 24), a 16 px por unidad. */
    static Texturas.Textura degradadoDeGota() {
        int k = 16, w = 16 * k, h = 32 * k;
        BufferedImage im = EstudioUV.lienzo(w, h);
        for (int i = 0; i < w * h; i++) im.getRaster().getDataBuffer().setElem(i, 0);      // transparente fuera de la gota
        Gota g = Gota.lineal(new Point2D.Double(8 * k, 8 * k), 6 * k, new Point2D.Double(8 * k, 24 * k), 6 * k, Perfil.recto());
        Plotter.raster(im).plot(g, SuiteMatriz_03_Colores.RELLENO, SuiteMatriz_03_Colores.OPCIONES);
        return Texturas.deImagen(im);
    }

    /**
     * Distancia d a la espina (segmento entre los centros), en unidades de una textura de 16 x 32:
     * d <= 2 núcleo sólido (la banda central de 4 de ancho y el seminúcleo de las tapas), 2 < d <= 6 degradado de la gota, 6 < d <= 8 aro exterior sólido.
     * Ajedrez al final, en todo: celdas de 2 unidades (cuerpo cuadrado, tapas en anillos de 2 y sectores de 18° (10 por media tapa: par, así el eje cae en un borde de sector y la alternancia continúa la del cuerpo; un giro de 180° ya no invierte la paridad)).
     */
    static final class GuiaAjedrez implements Texturas.Textura {
        static final int[] IZQ = { 0xFFFFE0B2, 0xFFB3E5FC, 0xFFC8E6C9, 0xFFF8BBD0 }, DER = { 0xFFFF9E80, 0xFF80D8FF, 0xFF81C784, 0xFFCE93D8 };
        final Texturas.Textura grad = degradadoDeGota();
        final double claro, oscuro;
        GuiaAjedrez(double claro, double oscuro) { this.claro = claro; this.oscuro = oscuro; }
        @Override public double aspecto() { return 0.5; }
        @Override public int muestrea(double u, double v) {
            double x = u * 16, y = v * 32, dx = x - 8, dy = y - Math.max(8, Math.min(24, y)), d = Math.hypot(dx, dy);
            if (d > 8) return 0;                                                  // fuera de la cápsula
            int c;
            if (d > 2 && d <= 6) c = Mezcla.RGB.mezclar(grad.muestrea(u, v), 0xFFFFFFFF, claro);
            else c = (u < 0.5 ? IZQ : DER)[v < 0.25 ? 0 : v < 0.5 ? 1 : v < 0.75 ? 2 : 3];
            int par;
            if (y < 8 || y > 24) par = ((int) Math.floor(d / 2) + (int) Math.floor(Math.atan2(Math.abs(dy), dx) / Math.toRadians(18)) + (y < 8 ? 1 : 0)) & 1;   // tapa de arriba con desfase 1: alterna con la primera fila del cuerpo (la de abajo ya alterna con la última)
            else par = ((int) Math.floor(x / 2) + (int) Math.floor((y - 8) / 2)) & 1;
            if (par == 0) return c | 0xFF000000;
            int rgb = 0; for (int sh = 16; sh >= 0; sh -= 8) rgb |= (int) Math.round(((c >> sh) & 255) * (1 - oscuro)) << sh;
            return 0xFF000000 | rgb;
        }
    }

    public static void main(String[] a) throws Exception {
        Texturas.Textura t = new GuiaAjedrez(0.30, 0.10);
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
