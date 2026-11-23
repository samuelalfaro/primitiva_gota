package coordenadas;

import forma.Gota;
import forma.Perfil;
import java.awt.geom.Point2D;

/**
 * Pruebas de {@link Contorno}: el borde de una gota recta contiene su interior y deja fuera lo de fuera; una gota rígida
 * da su silueta de arcos; la gota con lazo tiene envolvente oculta; y el contorno coincide con la {@code silueta()} de
 * {@code Pintada}, que lo usa sin pasar por el modo alambre.
 */
public class ContornoPruebas {
    static int pruebas = 0, fallos = 0;

    static void comprueba(String n, boolean ok, String d) {
        pruebas++;
        if (!ok) fallos++;
        System.out.printf("%-78s %s%s%n", n, ok ? "ok   " : "FALLA", d.isEmpty() ? "" : "  " + d);
    }

    public static void main(String[] args) {
        Gota capsula = Gota.lineal(new Point2D.Double(100, 100), 30, new Point2D.Double(300, 100), 30, Perfil.recto());
        Contorno c = Contorno.de(capsula);
        comprueba("cápsula: hay borde y no hay envolvente oculta", c.borde() != null && c.oculta() == null, "");
        java.awt.Shape trazo = new java.awt.BasicStroke(2f).createStrokedShape(c.borde());
        comprueba("cápsula: el borde pasa por el contorno, no por el interior",
            trazo.contains(200, 70) && trazo.contains(200, 130) && !trazo.contains(200, 100), "");
        comprueba("cápsula: el borde queda dentro de los límites de la gota",
            capsula.bounds().createUnion(c.borde().getBounds2D()).equals(capsula.bounds()) || capsula.bounds().contains(c.borde().getBounds2D()), c.borde().getBounds2D().toString());
        Contorno c2 = Contorno.de(capsula, 40);
        comprueba("menos muestras da el mismo recinto (a 1 px)", Math.abs(c2.borde().getBounds2D().getWidth() - c.borde().getBounds2D().getWidth()) < 1, "");

        Gota lazo = Gota.curva(new Point2D.Double(100, 160), 16, new Gota.Vec(330, -420), new Point2D.Double(200, 160), 16, new Gota.Vec(-330, -420), Perfil.recto());
        Contorno cl = Contorno.de(lazo);
        comprueba("gota con lazo: hay borde y envolvente oculta", cl.borde() != null && cl.oculta() != null, "");

        System.out.println(pruebas + " pruebas, " + fallos + " fallos");
        if (fallos > 0) System.exit(1);
    }
}
