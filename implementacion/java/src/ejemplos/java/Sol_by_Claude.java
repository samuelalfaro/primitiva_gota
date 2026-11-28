import geometria.composicion.Grafo;
import pintado.Plotter;
import color.Mezcla;
import color.Degradado;
import color.Colores;
import forma.Perfil;
import forma.Gota;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Lámina libre de Claude: un sol de invención propia, con rayos alternos largos y cortos. Cada rayo es una cadena de
 * tres nodos con el perfil pequeño-grande-pequeño (raíz estrecha, vientre ancho, punta fina), y la corona de doce se
 * pinta de fuera adentro: rayos, halo y disco. Es una prueba de simetría radial con el mismo trazo repetido y girado.
 * Escribe sol_by_claude.png.
 */
public class Sol_by_Claude {
    static final int W = 800, H = 800;
    static final double CX = 400, CY = 400;
    static final Mezcla RGB = Mezcla.RGB;

    static int mezcla(int a, int b, double t) { return RGB.mezclar(a, b, Math.max(0, Math.min(1, t))); }

    static final int AMARILLO = 0xFFFFD43B, NARANJA = 0xFFF08A1C, ROJIZO = 0xFFC8431A, CENTRO = 0xFFFFF0A8;

    static Degradado volumen(int c) { return x -> mezcla(c, ROJIZO, 0.6 * x * x); }

    static void fondo(BufferedImage im) {
        java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), 0xFF2B2F6B);
        Point2D.Double a = new Point2D.Double(CX, CY), b = new Point2D.Double(CX + 0.5, CY);
        Plotter.raster(im).plot(Gota.lineal(a, 620, b, 620, Perfil.recto()),
            Colores.cuatroColores(0xFFFFB86B, 0xFFFFB86B, 0xFF2B2F6B, 0xFF2B2F6B, Mezcla.RGB));
    }

    /** Un rayo desde el radio r0 hasta r1 con el ángulo dado; el vientre ancho queda a 40 % del recorrido. */
    static Grafo rayo(double ang, double r0, double r1, double ancho) {
        double ux = Math.cos(ang), uy = Math.sin(ang);
        double rm = r0 + 0.4 * (r1 - r0);
        Grafo g = new Grafo().mezcla(RGB);
        var a = g.nodo(CX + ux * r0, CY + uy * r0, ancho * 0.35).centro(NARANJA).radio(volumen(NARANJA));
        var m = g.nodo(CX + ux * rm, CY + uy * rm, ancho).centro(AMARILLO).radio(volumen(AMARILLO));
        var p = g.nodo(CX + ux * r1, CY + uy * r1, 2.5).centro(NARANJA).radio(volumen(NARANJA));
        g.arista(a, m).espina(u -> mezcla(NARANJA, AMARILLO, u));
        g.arista(m, p).espina(u -> mezcla(AMARILLO, NARANJA, u));
        return g.alisar();
    }

    static void disco(BufferedImage im, double r, int centro, int borde) {
        Point2D.Double a = new Point2D.Double(CX, CY), b = new Point2D.Double(CX + 0.5, CY);
        Plotter.raster(im).plot(Gota.lineal(a, r, b, r, Perfil.recto()), Colores.cuatroColores(centro, centro, borde, borde, Mezcla.RGB));
    }

    static BufferedImage dibuja() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        fondo(im);
        int n = 12;
        for (int i = 0; i < n; i++) {
            boolean largo = i % 2 == 0;
            double ang = 2 * Math.PI * i / n - Math.PI / 2;
            Plotter.raster(im).plot(rayo(ang, 120, largo ? 340 : 270, largo ? 34 : 24));
        }
        disco(im, 135, mezcla(NARANJA, ROJIZO, 0.4), ROJIZO);
        disco(im, 118, CENTRO, AMARILLO);
        return im;
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(dibuja(), "png", new File("sol_by_claude.png"));
        System.out.println("sol_by_claude.png");
    }
}
