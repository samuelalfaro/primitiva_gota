import geometria.composicion.Grafo;
import color.Degradado;
import color.Mezcla;
import color.Relleno;
import coordenadas.Opciones;
import forma.Gota;
import forma.Perfil;
import pintado.Plotter;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Pulpo: un dibujo con la API tal cual esta. El manto es un ciclo del Grafo con radios gordos y
 * color que aclara hacia la cuspide; los brazos son cadenas con radios que se afinan y un giro
 * que crece hacia la punta (rizo); los ojos y las burbujas, gotas rigidas casi circulares.
 * Fondo: gradiente vertical con un resplandor radial detras del animal.
 * Escribe pulpo_by_mistral.png.
 */
public class Pulpo_by_Mistral {

    static final int W = 800, H = 1000;

    static int mezcla(int a, int b, double t) { return Degradado.entre(a, b, Mezcla.LCH).en(t); }

    static BufferedImage fondo() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        int[] px = ((DataBufferInt) im.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < H; y++) {
            int base = mezcla(0xFF0D2A38, 0xFF04121C, y / (double) H);
            for (int x = 0; x < W; x++) {
                double d = Math.hypot(x - 400, y - 430) / 430;
                double g = Math.max(0, 1 - d);
                px[y * W + x] = mezcla(base, 0xFF145964, g * g * 0.55);
            }
        }
        return im;
    }

    /** Brazo: cadena de nodos con radios que se afinan y giro creciente hacia la punta. */
    static Grafo brazo(double x0, double y0, double dir, double giro, double[] radios, double[] pasos,
                       int cBase, int cTip) {
        Grafo g = new Grafo();
        double x = x0, y = y0;
        Grafo.Nodo prev = null;
        for (int i = 0; i < radios.length; i++) {
            double t = i / (double) (radios.length - 1);
            Grafo.Nodo n = g.nodo(x, y, radios[i]).color(mezcla(cBase, cTip, t));
            if (prev != null) g.arista(prev, n);
            prev = n;
            dir += giro * (0.3 + 0.7 * t);
            if (i < pasos.length) {
                x += Math.cos(dir) * pasos[i];
                y += Math.sin(dir) * pasos[i];
            }
        }
        return g.alisar();
    }

    static Grafo manto() {
        Grafo g = new Grafo();
        double[][] p = { { 262, 452, 44 }, { 232, 380, 42 }, { 285, 270, 34 }, { 400, 225, 30 },
                         { 515, 270, 34 }, { 568, 380, 42 }, { 538, 452, 44 }, { 400, 482, 42 } };
        Grafo.Nodo[] n = new Grafo.Nodo[p.length];
        for (int i = 0; i < p.length; i++) {
            double t = Math.max(0, Math.min(1, (482 - p[i][1]) / 257.0));
            n[i] = g.nodo(p[i][0], p[i][1], p[i][2]).color(mezcla(0xFF8E2438, 0xFFD95744, t));
        }
        for (int i = 0; i < n.length; i++) g.arista(n[i], n[(i + 1) % n.length]);
        return g.alisar();
    }

    /** Gota rigida casi circular (centros muy cerca): sirve de disco redondo. */
    static Gota disco(double x, double y, double r) {
        return Gota.lineal(new Point2D.Double(x - r * 0.15, y), r, new Point2D.Double(x + r * 0.15, y), r,
                Perfil.lineal());
    }

    public static void main(String[] args) throws Exception {
        BufferedImage im = fondo();
        Plotter p = Plotter.raster(im);

        double[] rLargo = { 26, 21, 16, 11, 7, 4 };
        double[] rCorto = { 22, 17, 12, 8, 5, 3 };
        double[] pasos = { 92, 84, 74, 62, 52 };
        double[] pasosCortos = { 70, 64, 56, 48, 40 };
        int cBase = 0xFFA83246, cTip = 0xFFE8825A;

        p.plot(brazo(272, 430, -Math.PI / 2 - 0.55, -0.55, rCorto, pasosCortos, cBase, cTip));
        p.plot(brazo(528, 430, -Math.PI / 2 + 0.55, 0.55, rCorto, pasosCortos, cBase, cTip));

        p.plot(manto());

        p.plot(brazo(296, 500, Math.PI / 2 + 0.85, 0.95, rLargo, pasos, cBase, cTip));
        p.plot(brazo(338, 518, Math.PI / 2 + 0.50, 0.78, rLargo, pasos, cBase, cTip));
        p.plot(brazo(372, 526, Math.PI / 2 + 0.22, 0.62, rCorto, pasosCortos, cBase, cTip));
        p.plot(brazo(428, 526, Math.PI / 2 - 0.22, -0.62, rCorto, pasosCortos, cBase, cTip));
        p.plot(brazo(462, 518, Math.PI / 2 - 0.50, -0.78, rLargo, pasos, cBase, cTip));
        p.plot(brazo(504, 500, Math.PI / 2 - 0.85, -0.95, rLargo, pasos, cBase, cTip));

        p.plot(disco(334, 462, 27), Relleno.solido(0xFFF2E7D2), Opciones.POR_DEFECTO);
        p.plot(disco(466, 462, 27), Relleno.solido(0xFFF2E7D2), Opciones.POR_DEFECTO);
        p.plot(disco(340, 467, 11), Relleno.solido(0xFF141419), Opciones.POR_DEFECTO);
        p.plot(disco(472, 467, 11), Relleno.solido(0xFF141419), Opciones.POR_DEFECTO);
        p.plot(disco(336, 460, 4), Relleno.solido(0xFFFFFFFF), Opciones.POR_DEFECTO);
        p.plot(disco(468, 460, 4), Relleno.solido(0xFFFFFFFF), Opciones.POR_DEFECTO);

        int burbuja = 0xFF9FCFD6;
        p.plot(disco(640, 330, 7), Relleno.solido(burbuja), Opciones.POR_DEFECTO);
        p.plot(disco(668, 258, 10), Relleno.solido(burbuja), Opciones.POR_DEFECTO);
        p.plot(disco(650, 195, 5), Relleno.solido(burbuja), Opciones.POR_DEFECTO);
        p.plot(disco(705, 360, 4), Relleno.solido(burbuja), Opciones.POR_DEFECTO);
        p.plot(disco(662, 130, 8), Relleno.solido(burbuja), Opciones.POR_DEFECTO);

        ImageIO.write(im, "png", new File("pulpo_by_mistral.png"));
        System.out.println("pulpo_by_mistral.png");
    }
}
