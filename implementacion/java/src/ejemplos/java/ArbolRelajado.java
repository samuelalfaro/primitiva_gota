import pintado.Plotter;
import geometria.composicion.Grafo;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * El {@link ArbolParametrico} con las ayudas de Grafo: por defecto | {@link Grafo#relajar()} |
 * {@link Grafo#relajar()} + {@link Grafo#alisar()}. En cada bifurcación (nodo de grado 3) relajar() hace
 * que el tronco continúe con tangente suave por la hija que mejor sigue su dirección; la otra sale lateral.
 * Escribe arbol_relajado.png y, por la salida estándar, las aristas rígidas y plegadas de cada modo.
 */
public class ArbolRelajado {
    public static void main(String[] args) throws Exception {
        ArbolParametrico.Parametros p = new ArbolParametrico.Parametros();
        if (args.length > 0) p.semilla = Long.parseLong(args[0]);
        int W = ArbolParametrico.W, H = ArbolParametrico.H;
        BufferedImage hoja = new BufferedImage(3 * W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hoja.createGraphics();
        String[] nombre = { "por defecto", "relajar()", "relajar() + alisar()" };
        for (int m = 0; m < 3; m++) {
            Grafo c = ArbolParametrico.crear(p);
            if (m >= 1) c.relajar();
            if (m >= 2) c.alisar();
            BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            java.util.Arrays.fill(((DataBufferInt) im.getRaster().getDataBuffer()).getData(), ArbolParametrico.FONDO);
            Plotter.raster(im).plot(c);
            g.drawImage(im, m * W, 0, null);
            System.out.printf("%-22s %d aristas, %d rígidas, %d plegadas%n", nombre[m], c.gotas().size(), c.rigidas().size(), c.plegadas().size());
        }
        g.dispose();
        ImageIO.write(hoja, "png", new File("arbol_relajado.png"));
    }
}
