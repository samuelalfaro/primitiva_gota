package common;

import forma.*;
import coordenadas.Solape;
import color.Relleno;
import color.Mezcla;
import color.Colores;
import coordenadas.Opciones;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import pintado.Plotter;

public class SuiteMatriz00 {

    public static final double[] SEPARACIONES = { 5, 4, 3 };
    public static final String[] FORMAS = { "recta", "suave", "S", "ჲ" };
    public static final double[][] RADIOS = {
        { 0, 4 }, { 0.5, 3.5 }, { 1, 3 }, { 1.5, 2.5 }, { 2, 2 },
        { 2.5, 1.5 }, { 3, 1 }, { 3.5, 0.5 }, { 4, 0 }
    };

    public static final double ESCALA = 22;          // píxeles por unidad
    public static final int CW = 300, CH = 260;      // tamaño de celda
    public static final int MARGEN_X = 70, MARGEN_Y = 30;

    // 6 anclas del ejemplo clásico (ARGB)
    public static final int AMARILLO = 0xFFFFFF00;
    public static final int CIAN     = 0xFF00FFFF;
    public static final int ROJO     = 0xFFFF0000;
    public static final int AZUL     = 0xFF0000FF;
    public static final int MAGENTA  = 0xFFFF00FF;
    public static final int VERDE    = 0xFF00FF00;

    /** Relleno de 6 colores en ESPIGA (por defecto). */
    public static final Relleno RELLENO = Colores.cadenaLateral(
            new Colores.Lateral(CIAN, MAGENTA, AMARILLO), // nodo 1: abajo / centro / arriba
            new Colores.Lateral(AZUL, VERDE,  ROJO),      // nodo 2
            Mezcla.RGB);

    public static final Opciones OPCIONES = Opciones.POR_DEFECTO.conSolape(Solape.TRAZO);  // ESPIGA + TRAZO + tira 1:2:1

    /** Vectores de control (relativos a cada centro) de cada forma; y hacia arriba. */
    public static Gota.Vec[] controles(String forma, double d) {
        return switch (forma) {
            case "suave" -> new Gota.Vec[] { new Gota.Vec(d / 3, d / 2), new Gota.Vec(-d / 3, d / 2) };
            case "S"     -> new Gota.Vec[] { new Gota.Vec(d / 3, d / 2), new Gota.Vec(-d / 3, -d / 2) };
            case "ჲ"     -> new Gota.Vec[] { new Gota.Vec(1.5 * d, 1.2 * d), new Gota.Vec(-1.5 * d, 1.2 * d) };
            default      -> null;
        };
    }

    public static Gota gota(String forma, double d, double r1, double r2, double ox, double oy) {
        Point2D c1 = new Point2D.Double(ox, oy), c2 = new Point2D.Double(ox + d * ESCALA, oy);
        Gota.Vec[] v = controles(forma, d);
        if (v == null) return Gota.lineal(c1, r1 * ESCALA, c2, r2 * ESCALA, Perfil.recto());
        return Gota.curva(c1, r1 * ESCALA, new Gota.Vec(v[0].x() * ESCALA, -v[0].y() * ESCALA),
                          c2, r2 * ESCALA, new Gota.Vec(v[1].x() * ESCALA, -v[1].y() * ESCALA), Perfil.recto());
    }

    /** Los dos modos de solape sobre el mismo fondo blanco, para compararlos píxel a píxel. */
    public static BufferedImage sobreBlanco(Gota g, Solape solape) {
        BufferedImage im = new BufferedImage(CW, CH, BufferedImage.TYPE_INT_ARGB);
        Plotter.raster(im).plot(g, RELLENO, OPCIONES.conSolape(solape));
        BufferedImage r = new BufferedImage(CW, CH, BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = r.createGraphics();
        gr.setColor(Color.WHITE);
        gr.fillRect(0, 0, CW, CH);
        gr.drawImage(im, 0, 0, null);
        gr.dispose();
        return r;
    }

}
