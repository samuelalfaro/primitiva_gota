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
 * Cintas (Grok) — estudio #86 / Rejilla.
 * Varias cintas suaves que se entrecruzan. Cada cinta es una (o dos) gota(s)
 * larga(s) continua(s) con altura constante; la Rejilla ordena los cruces y
 * suaviza los bordes. No se pretende silueta libre: solo tubos que pasan unos
 * por encima de otros. Sirve para ver profundidad real de solapes y el límite
 * de “todo es tubo”.
 * Escribe cintas_by_grok.png.
 */
public class Cintas_by_Grok {
    static final int W = 780, H = 720;
    static final Mezcla RGB = Mezcla.RGB;

    static int mezcla(int a, int b, double t) {
        return RGB.mezclar(a, b, Math.max(0, Math.min(1, t)));
    }

    static int conAlfa(int c, double k) {
        k = Math.max(0, Math.min(1, k));
        return ((int) Math.round((c >>> 24) * k)) << 24 | (c & 0xFFFFFF);
    }

    static final int[] TINTAS = {
        0xFFE8B4C8,
        0xFF9B7EBD,
        0xFF5B8FA8,
        0xFFD4A574,
        0xFF7A9E7E,
        0xFFC47B8A,
        0xFF6B8CAE,
        0xFFB8956C
    };

    static Degradado radioCinta(int base) {
        return x -> {
            double u = x * x;
            int borde = mezcla(base, 0xFF2A2030, 0.42);
            int c = mezcla(base, borde, 0.50 * u);
            double a = 1.0 - 0.30 * u * u;
            return conAlfa(c, a);
        };
    }

    static void fondo(BufferedImage im) {
        int[] pix = ((DataBufferInt) im.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < H; y++) {
            double ty = y / (double) (H - 1);
            int fila = mezcla(0xFF1A1520, 0xFF0C0A10, ty);
            for (int x = 0; x < W; x++) {
                double dx = (x - W * 0.5) / (W * 0.6);
                double dy = (y - H * 0.5) / (H * 0.6);
                double v = Math.min(1, dx * dx + dy * dy);
                pix[y * W + x] = mezcla(fila, 0xFF060508, 0.4 * v);
            }
        }
    }

    static Geometria cintaSimple(double x0, double y0, double x1, double y1,
                                 double cx, double cy,
                                 double r0, double r1, int color, double altura) {
        Point2D.Double a = new Point2D.Double(x0, y0);
        Point2D.Double b = new Point2D.Double(x1, y1);
        double vx1 = (cx - x0) * 0.9, vy1 = (cy - y0) * 0.9;
        double vx2 = (cx - x1) * 0.9, vy2 = (cy - y1) * 0.9;
        Gota g = Gota.curva(a, r0, new Gota.Vec(vx1, vy1),
                            b, r1, new Gota.Vec(vx2, vy2), Perfil.recto());
        return Pintada.de(g, Colores.cadenaLateral(
                new Colores.Funciones(
                        radioCinta(color), radioCinta(color),
                        Degradado.constante(color),
                        radioCinta(color), radioCinta(color)),
                RGB)).conAltura(altura);
    }

    static void aportarCintaDos(Rejilla rej,
                                double x0, double y0, double x1, double y1, double x2, double y2,
                                double c1x, double c1y, double c2x, double c2y,
                                double r0, double r1, double r2,
                                int color, double altura) {
        rej.aporta(cintaSimple(x0, y0, x1, y1, c1x, c1y, r0, r1, color, altura), Opciones.POR_DEFECTO);
        rej.aporta(cintaSimple(x1, y1, x2, y2, c2x, c2y, r1, r2, color, altura), Opciones.POR_DEFECTO);
    }

    public static void main(String[] args) throws Exception {
        File outDir = new File(args.length > 0 ? args[0] : ".");
        outDir.mkdirs();

        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        fondo(im);

        Rejilla rej = new Rejilla(2, 28, Rejilla.Cobertura.ESTRECHADA, Rejilla.Filtro.LINEAL);

        aportarCintaDos(rej,
                30, 300, 380, 260, 750, 340,
                200, 220, 560, 380,
                11, 18, 10, TINTAS[0], 22);

        aportarCintaDos(rej,
                60, 50, 320, 280, 720, 620,
                140, 180, 520, 480,
                10, 16, 9, TINTAS[1], 38);

        aportarCintaDos(rej,
                480, 30, 540, 340, 500, 690,
                560, 160, 480, 520,
                9, 15, 8, TINTAS[2], 18);

        aportarCintaDos(rej,
                20, 500, 360, 560, 740, 480,
                160, 620, 560, 440,
                12, 17, 11, TINTAS[3], 42);

        aportarCintaDos(rej,
                80, 140, 400, 380, 700, 200,
                220, 280, 560, 320,
                13, 19, 10, TINTAS[4], 28);

        aportarCintaDos(rej,
                720, 40, 400, 300, 80, 580,
                580, 140, 220, 460,
                10, 15, 9, TINTAS[5], 33);

        aportarCintaDos(rej,
                40, 400, 300, 420, 700, 360,
                160, 460, 520, 340,
                7, 11, 6, TINTAS[6], 58);

        aportarCintaDos(rej,
                180, 80, 420, 200, 650, 520,
                280, 120, 560, 380,
                14, 20, 12, TINTAS[7], 12);

        rej.aporta(cintaSimple(200, 180, 580, 520, 420, 280,
                8, 9, mezcla(TINTAS[0], 0xFFFFFFFF, 0.35), 70),
                Opciones.POR_DEFECTO);

        long t0 = System.nanoTime();
        Rejilla.Estadisticas est = rej.prensa(im);
        double seg = (System.nanoTime() - t0) / 1e9;

        File png = new File(outDir, "cintas_by_grok.png");
        ImageIO.write(im, "PNG", png);

        System.out.printf("Cintas_by_Grok -> %s%n", png.getAbsolutePath());
        System.out.printf("  N=2 ESTRECHADA LINEAL  %.2f s  muestras=%d  aportes=%d  maxProf=%d%n",
                seg, est.muestras(), est.aportes(), est.maxProfundidad());
    }
}
