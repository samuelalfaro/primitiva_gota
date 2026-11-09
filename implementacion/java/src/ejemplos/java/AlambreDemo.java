import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import java.io.File;

/** Modo alambre de varias gotas: un PNG con todas y un SVG por gota. Las gotas son las de los casos típicos y degenerados. */
public class AlambreDemo {

    private static Point2D p(double x, double y) { return new Point2D.Double(x, y); }

    /** Silueta con los extremos, sin leyendas. */
    static final Alambre.Ajustes SILUETA = Alambre.Ajustes.POR_DEFECTO.conEtiquetas(false);
    /** Además, la envolvente oculta, los contactos P1..P4, los radios de los extremos y sus nombres. */
    static final Alambre.Ajustes COMPLETO = Alambre.Ajustes.POR_DEFECTO.conOcultas(true).conContactos(true).conRadios(true);

    record Caso(String nombre, Gota gota) {}

    public static void main(String[] args) throws Exception {
        Perfil recto = Perfil.recto();
        List<Caso> casos = List.of(
            new Caso("radios_distintos", Gota.lineal(p(90, 110), 60, p(330, 110), 30, recto)),
            new Caso("curva", Gota.curva(p(90, 150), 40, new Gota.Vec(0, -140), p(330, 150), 24, new Gota.Vec(0, 140), recto)),
            new Caso("tapa_contenida", Gota.lineal(p(180, 110), 72, p(198, 110), 18, recto)),
            new Caso("centros_iguales", Gota.lineal(p(210, 110), 72, p(210, 110), 36, recto)),
            new Caso("circulos_identicos", Gota.lineal(p(210, 110), 60, p(210, 110), 60, recto)),
            new Caso("centros_iguales_engorda", Gota.lineal(p(210, 110), 48, p(210, 110), 48, Perfil.radioMedio(72))),
            new Caso("ida_y_vuelta", Gota.curva(p(90, 110), 40, new Gota.Vec(300, 0), p(90, 110), 40, new Gota.Vec(300, 0), recto)));

        imagen(casos, SILUETA, "alambre", true);
        imagen(casos, COMPLETO, "alambre_completo", false);
    }

    /** Una imagen con todos los casos y, si se pide, un SVG por caso. */
    static void imagen(List<Caso> casos, Alambre.Ajustes ajustes, String nombre, boolean svg) throws Exception {
        int w = 420, h = 230, cols = 2, filas = (casos.size() + cols - 1) / cols;
        BufferedImage img = new BufferedImage(w * cols, h * filas, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0xF4F1EA));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int i = 0; i < casos.size(); i++) {
            Caso c = casos.get(i);
            var celda = (Graphics2D) g.create(i % cols * w, i / cols * h, w, h);
            new PlotterAlambre(celda, ajustes).plot(c.gota());
            celda.setColor(new Color(0x333333));
            celda.drawString(c.nombre(), 8, 16);
            celda.dispose();
            if (svg) {
                var trazos = Alambre.trazos(c.gota(), ajustes);
                Svg.escribe(trazos, c.gota().bounds(), Path.of(nombre + "_" + c.nombre() + ".svg"));
            }
        }
        g.dispose();
        ImageIO.write(img, "png", new File(nombre + ".png"));
    }
}
