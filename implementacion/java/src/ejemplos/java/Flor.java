import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/** Una flor con tallo y una hoja, dibujada solo con la API de Composicion (sin tocar la primitiva). */
public class Flor {
    static final int W = 500, H = 700, FONDO = 0xFFF4F1EA;

    static int aclara(int c, double a) {
        int r = 0xFF000000;
        for (int s = 16; s >= 0; s -= 8) r |= (int) Math.round(255 * a + ((c >> s) & 255) * (1 - a)) << s;
        return r;
    }

    /** Tallo: base - nudo - cima, con la misma tangente a los dos lados del nudo (G1). */
    static Composicion tallo() {
        Composicion c = new Composicion();
        var base = c.nodo(258, 665, 8).centro(0xFF9CCC65).sectores(0xFF33691E);
        var nudo = c.nodo(246, 500, 9).centro(0xFFC5E1A5).lados(0xFF33691E, 0xFF7CB342);
        var cima = c.nodo(250, 250, 8).centro(0xFF9CCC65).sectores(0xFF558B2F);
        double ux = 0.03, uy = -1, ln = Math.hypot(ux, uy);
        ux /= ln; uy /= ln;
        double l1 = 55, l2 = 80;
        c.arista(base, nudo).tiradores(null, new Gota.Vec(-ux * l1, -uy * l1));
        c.arista(nudo, cima).tiradores(new Gota.Vec(ux * l2, uy * l2), null);
        return c;
    }

    /** Seis pétalos anchos y solapados de un nodo central; cada pétalo es una gota con la punta elíptica. */
    static Composicion flor(double rPetalo) {
        Composicion c = new Composicion();
        double cx = 250, cy = 235;
        var centro = c.nodo(cx, cy, 24).centro(0xFFFFD54F)
                .sectores(0xFFFF6FA5, 0xFFFF8FB8, 0xFFE63C8A, 0xFFFF7BAA, 0xFFD81B60, 0xFFF06292);
        int[] tono = { 0xFFE91E63, 0xFFF06292, 0xFFD81B60, 0xFFEC407A, 0xFFC2185B, 0xFFF48FB1 };
        for (int k = 0; k < 6; k++) {
            double ang = 60 * k, dist = 104, curva = 18;   // sin pétalo vertical: ninguno queda alineado con el tallo
            double a = Math.toRadians(ang);
            var punta = c.nodo(cx + Math.cos(a) * dist, cy + Math.sin(a) * dist, rPetalo).centro(aclara(tono[k], 0.6))
                    .aspecto(1.3).sectores(tono[k]);
            double d = dist / 3, b = Math.toRadians(ang + 180 + curva);
            c.arista(centro, punta).tiradores(null, new Gota.Vec(Math.cos(b) * d, Math.sin(b) * d));
        }
        return c;
    }

    /** El corazón de la flor: un disco de la primitiva con centro amarillo y borde naranja. */
    static void corazon(BufferedImage im) {
        java.awt.geom.Point2D.Double a = new java.awt.geom.Point2D.Double(250, 235), b = new java.awt.geom.Point2D.Double(250.5, 235);
        Gota.lineal(a, 15, b, 15, Gota.Perfil.recto()).render(im, Colores.cuatroColores(0xFFFFF59D, 0xFFFFF59D, 0xFFFF9800, 0xFFFF9800, Mezcla.RGB));
    }

    static BufferedImage dibuja(double rPetalo) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), FONDO);
        // la hoja nace en el nudo del tallo, sube a la derecha, y se pinta antes que el tallo para que éste tape su base
        Composicion hoja = Hoja.limbo(new Hoja.Tr(246, 505, Math.toRadians(-48), 0.34), 3.6);
        hoja.render(im);
        Hoja.venas(hoja, 1.0, 0.55).render(im);
        tallo().render(im);
        flor(rPetalo).render(im);
        corazon(im);
        return im;
    }

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        BufferedImage a = dibuja(39);
        System.out.printf("render: %.1f s%n", (System.nanoTime() - t0) / 1e9);
        ImageIO.write(a, "png", new File("flor.png"));
    }
}
