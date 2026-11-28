import pintado.Plotter;
import geometria.Geometria;
import geometria.Grupo;
import geometria.composicion.Grafo;
import coordenadas.Solape;
import color.Mezcla;
import coordenadas.Opciones;
import forma.Gota;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Escarabajo (Gemini): cuerpo abombado, antenas y seis patitas con la API actual.
 * Escribe escarabajo_by_gemini.png.
 */
public class Escarabajo_by_Gemini {
    static final int W = 720, H = 520;
    static final Mezcla RGB = Mezcla.RGB;

    static int mezcla(int a, int b, double t) {
        return RGB.mezclar(a, b, Math.max(0.0, Math.min(1.0, t)));
    }

    static final int CAP_CLARO = 0xFF5B8C5A;
    static final int CAP_OSCURO = 0xFF23422A;
    static final int PATA_A = 0xFF4A2E2B;
    static final int PATA_B = 0xFF8C5230;
    static final int OJO = 0xFFF7D046;

    static void fondo(BufferedImage im) {
        int[] pix = ((DataBufferInt) im.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < H; y++) {
            double t = y / (double) (H - 1);
            int col = mezcla(0xFFF6F1E3, 0xFFE5DEC9, t);
            for (int x = 0; x < W; x++) pix[y * W + x] = col;
        }
    }

    static void pata(Grafo c, double x0, double y0, double x1, double y1,
                     double r0, double r1, double curvaX, double curvaY) {
        var a = c.nodo(x0, y0, r0).centro(PATA_A).radio(x -> mezcla(PATA_A, PATA_B, x));
        var b = c.nodo(x1, y1, r1).centro(PATA_B).radio(x -> mezcla(PATA_B, PATA_A, 0.4 * x));
        c.arista(a, b)
            .espina(x -> mezcla(PATA_A, PATA_B, x))
            .tiradores(new Gota.Vec(curvaX, curvaY), new Gota.Vec(-curvaX * 0.4, -curvaY * 0.4));
    }

    static void antena(Grafo c, double x0, double y0, double dx, double dy) {
        var base = c.nodo(x0, y0, 3.0).centro(PATA_A).radio(x -> mezcla(PATA_A, PATA_B, x));
        var punta = c.nodo(x0 + dx, y0 + dy, 2.2).centro(PATA_B).radio(x -> mezcla(PATA_B, OJO, 0.35 * x));
        c.arista(base, punta)
            .espina(x -> mezcla(PATA_A, PATA_B, x))
            .tiradores(new Gota.Vec(dx * 0.5, dy * 0.8), new Gota.Vec(-dx * 0.2, -dy * 0.2));
    }

    static BufferedImage dibuja() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        fondo(im);
        Partes partes = new Partes();
        Grafo c = partes.nueva();

        double cx = 360, cy = 265;

        // Patas traseras, medias y delanteras (arriba y abajo)
        pata(partes.nueva(), cx - 55, cy - 28, cx - 125, cy - 115, 7.5, 3.0, -25, -45);
        pata(partes.nueva(), cx - 55, cy + 28, cx - 125, cy + 115, 7.5, 3.0, -25, 45);

        pata(partes.nueva(), cx, cy - 32, cx - 15, cy - 125, 7.0, 2.8, -10, -45);
        pata(partes.nueva(), cx, cy + 32, cx - 15, cy + 125, 7.0, 2.8, -10, 45);

        pata(partes.nueva(), cx + 55, cy - 26, cx + 105, cy - 105, 6.5, 2.5, 25, -35);
        pata(partes.nueva(), cx + 55, cy + 26, cx + 105, cy + 105, 6.5, 2.5, 25, 35);

        // Antenas hacia la derecha
        antena(partes.nueva(), cx + 95, cy - 14, 65, -45);
        antena(partes.nueva(), cx + 95, cy + 14, 65, 45);

        // Cuerpo principal de cola (izquierda) a cabeza (derecha)
        var cola = c.nodo(cx - 105, cy, 18.0).centro(CAP_OSCURO).radio(x -> mezcla(CAP_OSCURO, CAP_CLARO, 0.4 * x));
        var centro = c.nodo(cx, cy, 46.0).centro(CAP_CLARO).radio(x -> mezcla(CAP_CLARO, CAP_OSCURO, 0.3 * x));
        var cabeza = c.nodo(cx + 98, cy, 24.0).centro(CAP_CLARO).radio(x -> mezcla(CAP_CLARO, CAP_OSCURO, 0.25 * x));

        c.arista(cola, centro)
            .espina(x -> mezcla(CAP_OSCURO, CAP_CLARO, x))
            .tiradores(new Gota.Vec(35, -12), new Gota.Vec(-35, 12));
        c.arista(centro, cabeza)
            .espina(x -> CAP_CLARO)
            .tiradores(new Gota.Vec(35, 10), new Gota.Vec(-30, -10));

        // Dos pequeños ojos amarillos: un nodo suelto es un Grafo mínimo (un disco)
        partes.nueva().nodo(cx + 84, cy - 12, 4.5).centro(OJO);
        partes.nueva().nodo(cx + 84, cy + 12, 4.5).centro(OJO);

        Plotter.raster(im).plot(partes.grupo(), Opciones.POR_DEFECTO.conSolape(Solape.TRAZO));
        return im;
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(dibuja(), "png", new File("escarabajo_by_gemini.png"));
        System.out.println("escarabajo_by_gemini.png");
    }

    /**
     * Un Grafo es conexo: cada parte sin unir es un Grafo propio y el dibujo es el Grupo de todas. Se agrupan al revés de como
     * se crean porque, en un único Grafo con Solape.TRAZO, quedaba encima la parte creada antes y en un Grupo queda encima la última.
     */
    static final class Partes {
        private final java.util.List<Grafo> lista = new java.util.ArrayList<>();
        Grafo nueva() { Grafo g = new Grafo().mezcla(RGB); lista.add(g); return g; }
        Grupo grupo() {
            java.util.List<Grafo> alReves = new java.util.ArrayList<>(lista);
            java.util.Collections.reverse(alReves);
            return new Grupo(alReves.toArray(new Geometria[0]));
        }
    }
}
