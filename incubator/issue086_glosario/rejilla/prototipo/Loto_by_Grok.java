import color.Colores;
import color.Degradado;
import color.Mezcla;
import coordenadas.Opciones;
import forma.Gota;
import forma.Perfil;
import geometria.Geometria;
import geometria.composicion.Pintada;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Loto (Grok) — estudio #86 / Rejilla.
 * Vista cenital de un loto: muchos pétalos independientes (cada uno una Pintada con su altura)
 * se aportan a la Rejilla. El orden por altura y el sobremuestreo dan solapes suaves y
 * contornos limpios entre pétalos que se cruzan.
 * Fondo de agua oscura + un disco central. Escribe loto_by_grok.png.
 */
public class Loto_by_Grok {
    static final int W = 720, H = 720;
    static final double CX = W * 0.5, CY = H * 0.5;
    static final Mezcla RGB = Mezcla.RGB;

    static int mezcla(int a, int b, double t) {
        return RGB.mezclar(a, b, Math.max(0, Math.min(1, t)));
    }

    static int conAlfa(int c, double k) {
        k = Math.max(0, Math.min(1, k));
        return ((int) Math.round((c >>> 24) * k)) << 24 | (c & 0xFFFFFF);
    }

    static final int ROSA_OSCURO = 0xFFB84E6C;
    static final int ROSA       = 0xFFE090A8;
    static final int ROSA_CLARO = 0xFFF2C4D0;
    static final int CREMA      = 0xFFFFF0F3;
    static final int CENTRO     = 0xFFFFE8C8;
    static final int AMARILLO   = 0xFFFFD78A;

    static Degradado radioPetalo(int base) {
        return x -> {
            double u = x * x;
            int borde = mezcla(base, ROSA_OSCURO, 0.28);
            int c = mezcla(base, borde, 0.50 * u);
            double a = 1.0 - 0.42 * u * u;
            return conAlfa(c, a);
        };
    }

    static void fondoAgua(BufferedImage im) {
        int[] pix = ((DataBufferInt) im.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double dx = (x - CX) / (W * 0.55);
                double dy = (y - CY) / (H * 0.55);
                double r = Math.sqrt(dx * dx + dy * dy);
                double t = Math.min(1, r);
                int agua = mezcla(0xFF0A2A3A, 0xFF041820, t);
                int halo = mezcla(0xFF1A4A5A, agua, Math.min(1, r * 1.35));
                pix[y * W + x] = halo;
            }
        }
    }

    static Geometria petalo(double ang, double r0, double r1, double anchoBase, double anchoPunta,
                            int color, double altura, double curva) {
        double ux = Math.cos(ang), uy = Math.sin(ang);
        double nx = -uy, ny = ux;

        Point2D.Double a = new Point2D.Double(
                CX + ux * r0 + nx * curva * 0.12,
                CY + uy * r0 + ny * curva * 0.12);
        Point2D.Double b = new Point2D.Double(
                CX + ux * r1 + nx * curva,
                CY + uy * r1 + ny * curva);

        double len = r1 - r0;
        Gota.Vec v1 = new Gota.Vec(
                ux * len * 0.48 + nx * curva * 0.6,
                uy * len * 0.48 + ny * curva * 0.6);
        Gota.Vec v2 = new Gota.Vec(
                -ux * len * 0.38 + nx * curva * 0.3,
                -uy * len * 0.38 + ny * curva * 0.3);

        Gota g = Gota.curva(a, anchoBase, v1, b, anchoPunta, v2, Perfil.recto());
        return Pintada.de(g, Colores.cadenaLateral(
                new Colores.Funciones(
                        radioPetalo(color), radioPetalo(color),
                        Degradado.constante(color),
                        radioPetalo(color), radioPetalo(color)),
                RGB)).conAltura(altura);
    }

    static Geometria disco(double r, int centro, int borde, double altura) {
        Point2D.Double a = new Point2D.Double(CX, CY);
        Point2D.Double b = new Point2D.Double(CX + 0.4, CY);
        Gota g = Gota.lineal(a, r, b, r, Perfil.recto());
        return Pintada.de(g, Colores.cuatroColores(centro, centro, borde, borde, RGB))
                .conAltura(altura);
    }

    static java.util.List<Geometria> escena() {
        java.util.List<Geometria> l = new java.util.ArrayList<>();

        int nExt = 8;
        for (int i = 0; i < nExt; i++) {
            double ang = 2 * Math.PI * i / nExt - Math.PI / 2 + 0.10;
            double curva = 26 * Math.sin(i * 1.5 + 0.4);
            l.add(petalo(ang, 42, 255, 58, 26, ROSA_OSCURO, 8 + i * 0.5, curva));
        }

        int nMed = 8;
        for (int i = 0; i < nMed; i++) {
            double ang = 2 * Math.PI * i / nMed - Math.PI / 2 + 0.35;
            double curva = -18 * Math.cos(i * 1.3);
            l.add(petalo(ang, 38, 220, 50, 22, ROSA, 28 + i * 0.55, curva));
        }

        int nInt = 7;
        for (int i = 0; i < nInt; i++) {
            double ang = 2 * Math.PI * i / nInt - Math.PI / 2 + 0.06;
            double curva = 14 * Math.sin(i * 1.9 + 0.5);
            l.add(petalo(ang, 34, 180, 42, 18, ROSA_CLARO, 52 + i * 0.65, curva));
        }

        int nCerc = 6;
        for (int i = 0; i < nCerc; i++) {
            double ang = 2 * Math.PI * i / nCerc - Math.PI / 2 + 0.28;
            l.add(petalo(ang, 30, 130, 32, 14, CREMA, 78 + i * 0.8, 8 * Math.sin(i * 1.2)));
        }

        for (int i = 0; i < 5; i++) {
            double ang = 2 * Math.PI * i / 5 - Math.PI / 2 + 0.50;
            l.add(petalo(ang, 48, 148, 28, 12, mezcla(ROSA_CLARO, CREMA, 0.45), 66 + i * 0.5, 6));
        }

        l.add(disco(48, mezcla(AMARILLO, CENTRO, 0.35), ROSA_OSCURO, 100));
        l.add(disco(26, CENTRO, AMARILLO, 112));

        return l;
    }

    public static void main(String[] args) throws Exception {
        File outDir = new File(args.length > 0 ? args[0] : ".");
        outDir.mkdirs();

        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        fondoAgua(im);

        Rejilla rej = new Rejilla(2, 32, Rejilla.Cobertura.ESTRECHADA, Rejilla.Filtro.LINEAL);
        for (Geometria g : escena()) {
            rej.aporta(g, Opciones.POR_DEFECTO);
        }

        long t0 = System.nanoTime();
        Rejilla.Estadisticas est = rej.prensa(im);
        double seg = (System.nanoTime() - t0) / 1e9;

        File png = new File(outDir, "loto_by_grok.png");
        ImageIO.write(im, "PNG", png);

        System.out.printf("Loto_by_Grok -> %s%n", png.getAbsolutePath());
        System.out.printf("  N=2 ESTRECHADA LINEAL  %.2f s  muestras=%d  aportes=%d  maxProf=%d%n",
                seg, est.muestras(), est.aportes(), est.maxProfundidad());
    }
}
