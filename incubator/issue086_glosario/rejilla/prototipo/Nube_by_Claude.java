import coordenadas.Opciones;
import geometria.Geometria;
import geometria.Grupo;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Nube_by_Claude — lámina surgida del estudio #86 (sin retoques).
 * <p>
 * Es la nube de la {@link PruebaNube} (Gotas, Grafos y Grupos semitransparentes a distintas alturas, semilla 86) prensada con la
 * Rejilla N=3. Nadie buscó el aspecto de acuarela: sale de sumar el alfa de cada aporte, el orden por altura en cada muestra y los
 * bordes suavizados por el sobremuestreo.
 * <p>
 * Vive junto a la Rejilla (prototipo, solo en la rama de la #86); el PNG está también en {@code arte/}.
 * Escribe nube_by_claude.png.
 */
public class Nube_by_Claude {
    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : ".");
        dir.mkdirs();
        Random r = new Random(86);
        List<Geometria> raices = new ArrayList<>();
        for (int i = 0; i < 7; i++) raices.add(PruebaNube.gota(r, i * 1.9 + r.nextDouble()));
        for (int i = 0; i < 7; i++) raices.add(PruebaNube.grafo(r, i * 1.9 + 0.95 + r.nextDouble() * 0.5));
        for (int i = 0; i < 4; i++) raices.add(PruebaNube.grupo(r, 2 + i * 3.1 + r.nextDouble(), 0, new ArrayList<>(), i == 3));

        BufferedImage im = PruebaNube.lienzo();
        new Rejilla(3, 32, Rejilla.Cobertura.ESTRECHADA, Rejilla.Filtro.SRGB)
                .aporta(new Grupo(raices.toArray(new Geometria[0])), Opciones.POR_DEFECTO).prensa(im);
        File png = new File(dir, "nube_by_claude.png");
        ImageIO.write(im, "png", png);
        System.out.println("Nube_by_Claude -> " + png.getAbsolutePath());
    }
}
